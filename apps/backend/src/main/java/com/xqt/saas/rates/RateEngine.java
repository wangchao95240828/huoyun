package com.xqt.saas.rates;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import com.xqt.saas.rates.RateQuoteResponse.BreakdownLine;
import com.xqt.saas.rates.RateQuoteResponse.MatchEvidence;
import com.xqt.saas.rates.RateQuoteResponse.Quote;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 运费试算引擎。对照 acc/config/Freight.php::getFee（350-660 行）的核心规则：
 *
 *   1. 价格优先级：客户专属 > 客户组价 > 普通销售价（独立查 AP 表得成本价）
 *   2. 邮编优先级：精确邮编 > 邮编段/regex > 国家默认
 *   3. 渠道账号限制：单票件数 / 单票重量 / 当日票量
 *   4. 货物限制：电池（内置/干电池）/ 敏感货 / 仿牌 / 国家黑白名单 / 邮编黑白名单
 *   5. 多段计费：unit_price × kg / first+continued / 固定费 / 件计费
 *   6. 偏远费：按 remote_rate_rules 配置（替代 ACC 写死的 15%/25%）
 *   7. 佣金：rate_commission_rules 客户 > 组 > 服务 > 渠道 优先级匹配
 *
 * 输出包含销售价 + 成本价（找得到 AP rate card 时）+ 命中证据 + 阻拦原因。
 */
@Service
public class RateEngine {
    private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final int MONEY_SCALE = 2;
    private static final BigDecimal CBM_TO_CM3 = new BigDecimal("1000000");
    // Fallback zone：当 ups_zone_mappings 表查不到 dest_prefix → 用 US-Z005（中区）兜底。
    // 美西 91745 origin 的 ZIP→Ground zone 映射在 070 migration 灌了 905 条。
    private static final String DEFAULT_ZONE = "US-Z005";

    // UPS 账号 J602B0 绑定的发件地址 1451 W Holt Blvd, Ontario CA 91762 → origin prefix '917'
    // TODO: 后续按 channel/account 配置 origin，不再硬编码
    private static final String UPS_ORIGIN_PREFIX = "917";

    private final RateRepository repository;
    private final JdbcTemplate jdbc;
    private final CarrierRuleEngine carrierRules;

    public RateEngine(RateRepository repository, JdbcTemplate jdbc, CarrierRuleEngine carrierRules) {
        this.repository = repository;
        this.jdbc = jdbc;
        this.carrierRules = carrierRules;
    }

    @Transactional(readOnly = true)
    public Quote quote(String tenantId, RateQuoteRequest request) {
        if (request == null) {
            throw ApiException.badRequest("rate quote request is required");
        }
        requireNonBlank("channelCode", request.channelCode());
        requireNonBlank("countryCode", request.countryCode());
        if (request.weightKg() == null || request.weightKg().signum() <= 0) {
            throw ApiException.badRequest("weightKg must be positive");
        }
        setTenant(tenantId);

        Map<String, Object> channel = repository.findChannelByCode(tenantId, request.channelCode());
        if (channel == null) {
            throw ApiException.notFound("channel not found: " + request.channelCode());
        }
        if (Boolean.FALSE.equals(channel.get("active"))) {
            throw ApiException.badRequest("channel is disabled: " + request.channelCode());
        }
        String channelId = (String) channel.get("id");

        List<String> blockers = new ArrayList<>();
        List<String> restrictionsChecked = new ArrayList<>();

        // ─── 货物限制 / 渠道账号黑白名单 ───
        Map<String, Object> restrict = repository.findServiceRestriction(
            tenantId, channelId, request.channelAccountCode(), request.serviceCode());
        if (restrict != null) {
            restrictionsChecked.add(String.valueOf(restrict.get("id")));
            checkBatteryRestriction(restrict, request.batteryType(), blockers);
            checkSpecialRestriction(restrict, request.specialType(), blockers);
            checkCountryRestriction(restrict, request.countryCode(), blockers);
            checkPostalRestriction(restrict, request.postalCode(), blockers);
        }

        // ─── 计算可计费重量 ───
        BigDecimal volumetric = volumetricWeight(request.volumeCbm(), toBigDecimal(channel.get("dim_factor")));
        BigDecimal chargeable = request.weightKg().max(volumetric).setScale(3, RoundingMode.HALF_UP);

        // ─── 渠道账号限额检查 ───
        if (request.channelAccountCode() != null && !request.channelAccountCode().isBlank()) {
            Map<String, Object> limit = repository.findChannelAccountLimit(
                tenantId, channelId, request.channelAccountCode(), request.chargeDate());
            if (limit != null) {
                checkChannelAccountLimits(limit, request.pieces(), chargeable, blockers);
            }
        }

        // ─── 偏远判定 + 阻塞禁运 ───
        String remoteLevel = repository.findRemoteLevel(
            tenantId, channelId, request.countryCode(), request.postalCode());
        if ("EMBARGO".equals(remoteLevel)) {
            blockers.add("目的地 " + request.countryCode() + " " + request.postalCode() + " 在禁运地区");
        }

        // ─── 价格匹配（客户专属 > 组价 > 普通价）───
        String chosenRateCardId = null;
        String customerRateMatched = null;
        String groupRateMatched = null;

        Map<String, Object> custCard = repository.findCustomerSpecificRateCard(
            tenantId, request.customerId(), channelId, request.serviceCode(), request.chargeDate());
        Map<String, Object> groupCard = request.customerGroupId() == null ? null
            : repository.findCustomerGroupRateCard(tenantId, request.customerGroupId(),
                channelId, request.serviceCode(), request.chargeDate());

        if (custCard != null) {
            chosenRateCardId = (String) custCard.get("rate_card_id");
            customerRateMatched = (String) custCard.get("id");
        } else if (groupCard != null) {
            chosenRateCardId = (String) groupCard.get("rate_card_id");
            groupRateMatched = (String) groupCard.get("id");
        } else {
            Map<String, Object> baseCard = repository.findActiveRateCard(
                tenantId, channelId, "AR", request.currency(), request.chargeDate());
            if (baseCard == null) {
                throw ApiException.notFound(
                    "渠道 [" + request.channelCode() + "] 在 " + request.chargeDate()
                    + " 没有 " + request.currency() + " 币种的有效销售价表。"
                    + "请到「数据管理 → 销售价格」补充该渠道+币种的价表，或检查价表生效日期");
            }
            chosenRateCardId = (String) baseCard.get("id");
        }

        // ─── 解析 UPS zone（按 dest_prefix 查 ups_zone_mappings）───
        String resolvedZone = repository.resolveZoneByPostcode(
            tenantId, UPS_ORIGIN_PREFIX, request.postalCode());
        if (resolvedZone == null) resolvedZone = DEFAULT_ZONE;

        // ─── 取价表行（含邮编精确匹配优先级）───
        Map<String, Object> tier = repository.findTier(
            tenantId, chosenRateCardId, resolvedZone, chargeable, request.postalCode());
        if (tier == null) {
            throw ApiException.notFound(
                "渠道 [" + request.channelCode() + "] 的价表不包含此重量段：" + chargeable + " kg / 区域 "
                + resolvedZone + " (邮编 " + request.postalCode() + ")。"
                + "请确认价表是否覆盖目的地区域及重量梯度");
        }
        String postalPriorityLabel = formatPostalPriority(tier);

        // ─── ACC FreightClass L1056-1057：单件最小计费重提升 ───
        // 实重 < tier.min_item_kg 时提升 chargeable 到 min_item_kg 参与计费（不拦截，是修正）
        BigDecimal minItemKg = toBigDecimal(tier.get("min_item_kg"));
        if (minItemKg != null && minItemKg.signum() > 0 && chargeable.compareTo(minItemKg) < 0) {
            chargeable = minItemKg;
        }

        // ─── 多段计费 ───
        BigDecimal freight = calculateFreight(tier, chargeable, request.pieces(), request.volumeCbm());

        // ─── 燃油 ───
        String yearMonth = request.chargeDate().format(YEAR_MONTH);
        BigDecimal fuelRate = repository.findFuelRate(tenantId, channelId, yearMonth);
        BigDecimal fuelAmount = freight.multiply(fuelRate).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        // ─── 偏远费（按规则表）───
        BigDecimal surchargeAmount = BigDecimal.ZERO;
        String remoteRuleId = null;
        Map<String, Object> remoteRule = repository.findRemoteRateRule(
            tenantId, channelId, remoteLevel, request.chargeDate());
        if (remoteRule != null) {
            remoteRuleId = (String) remoteRule.get("id");
            surchargeAmount = applyRemoteRule(remoteRule, freight);
        }

        // ─── 电池/带电附加费（对应 ACC Channel_Account.BatteryA/B/C）───
        BigDecimal batteryAmount = BigDecimal.ZERO;
        Integer batteryType = request.batteryType() == null ? 0 : request.batteryType();
        Map<String, Object> chAcctSurcharges = null;
        if (batteryType > 0 && request.channelAccountCode() != null) {
            chAcctSurcharges = repository.findChannelAccountSurcharges(
                tenantId, request.channelAccountCode());
            if (chAcctSurcharges != null) {
                BigDecimal fee = switch (batteryType) {
                    case 1 -> toBigDecimal(chAcctSurcharges.get("battery_a_fee"));
                    case 2 -> toBigDecimal(chAcctSurcharges.get("battery_b_fee"));
                    case 3 -> toBigDecimal(chAcctSurcharges.get("battery_c_fee"));
                    default -> BigDecimal.ZERO;
                };
                if (fee != null) batteryAmount = fee.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            }
        }

        // ─── 处理费（对应 ACC Channel_Account.Fee 一次性账号操作费）───
        BigDecimal processingAmount = BigDecimal.ZERO;
        if (request.channelAccountCode() != null) {
            if (chAcctSurcharges == null) {
                chAcctSurcharges = repository.findChannelAccountSurcharges(
                    tenantId, request.channelAccountCode());
            }
            if (chAcctSurcharges != null) {
                BigDecimal pf = toBigDecimal(chAcctSurcharges.get("processing_fee"));
                if (pf != null && pf.signum() > 0) {
                    processingAmount = pf.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
                }
            }
        }

        // ─── 申报价值保险（对应 ACC FreightClass 保险段）───
        BigDecimal insuranceAmount = BigDecimal.ZERO;
        String insuranceRateId = null;
        Map<String, Object> insRate = repository.findInsuranceRate(
            tenantId, channelId, request.currency(), request.chargeDate());
        if (insRate != null && request.declaredValue() != null
            && request.declaredValue().signum() > 0) {
            insuranceRateId = (String) insRate.get("id");
            insuranceAmount = applyInsuranceRate(insRate, request.declaredValue());
        }

        // ─── 佣金（仅销售价生成；成本价侧不算佣金）───
        BigDecimal commission = BigDecimal.ZERO;
        String commissionRuleId = null;
        Map<String, Object> commRule = repository.findCommissionRule(
            tenantId, request.customerId(), request.customerGroupId(),
            channelId, request.serviceCode(), request.chargeDate());
        if (commRule != null) {
            commissionRuleId = (String) commRule.get("id");
            commission = applyCommissionRule(commRule, freight, fuelAmount, surchargeAmount);
        }

        BigDecimal total = freight.add(fuelAmount).add(surchargeAmount).add(insuranceAmount)
            .add(batteryAmount).add(processingAmount)
            .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        // ─── 成本价（AP 侧，独立查表）───
        BigDecimal costFreight = null;
        BigDecimal costFuel = null;
        BigDecimal costSurcharge = null;
        BigDecimal costInsurance = null;
        BigDecimal costBattery = null;
        BigDecimal costProcessing = null;
        BigDecimal costTotal = null;
        String costRateCardId = null;
        Map<String, Object> apCard = repository.findActiveRateCard(
            tenantId, channelId, "AP", request.currency(), request.chargeDate());
        if (apCard != null) {
            costRateCardId = (String) apCard.get("id");
            Map<String, Object> apTier = repository.findTier(
                tenantId, costRateCardId, resolvedZone, chargeable, request.postalCode());
            if (apTier != null) {
                costFreight = calculateFreight(apTier, chargeable, request.pieces(), request.volumeCbm());
                costFuel = costFreight.multiply(fuelRate).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
                costSurcharge = remoteRule == null ? BigDecimal.ZERO
                    : applyRemoteRule(remoteRule, costFreight);
                costInsurance = insuranceAmount;  // 保险按申报价值计算，AR/AP 同额
                costBattery = batteryAmount;       // 电池费同 AR（账号级固定费，不区分销售/成本）
                costProcessing = processingAmount; // 处理费同 AR（账号级一次性费）
                costTotal = costFreight.add(costFuel).add(costSurcharge).add(costInsurance)
                    .add(costBattery).add(costProcessing)
                    .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            }
        }

        // ─── breakdown ───
        List<BreakdownLine> breakdown = new ArrayList<>();
        breakdown.add(new BreakdownLine("FREIGHT", "基础运费", freight));
        if (fuelAmount.signum() != 0) {
            breakdown.add(new BreakdownLine("FUEL", "燃油附加费", fuelAmount));
        }
        if (surchargeAmount.signum() != 0) {
            breakdown.add(new BreakdownLine("REMOTE", "偏远附加费", surchargeAmount));
        }
        if (insuranceAmount.signum() != 0) {
            breakdown.add(new BreakdownLine("INSURANCE", "申报价值保险", insuranceAmount));
        }
        if (batteryAmount.signum() != 0) {
            breakdown.add(new BreakdownLine("BATTERY", "带电附加费", batteryAmount));
        }
        if (processingAmount.signum() != 0) {
            breakdown.add(new BreakdownLine("PROCESSING", "处理费", processingAmount));
        }
        if (commission.signum() != 0) {
            breakdown.add(new BreakdownLine("COMMISSION", "佣金", commission));
        }

        MatchEvidence evidence = new MatchEvidence(
            chosenRateCardId,
            (String) tier.get("id"),
            costRateCardId,
            customerRateMatched,
            groupRateMatched,
            request.channelAccountCode(),
            remoteRuleId,
            commissionRuleId,
            postalPriorityLabel,
            List.copyOf(restrictionsChecked)
        );

        // ════════ W1: itemized charges[] (跟 UPS/FedEx/DHL Rate API 同构) ════════
        List<RateQuoteResponse.ChargeItem> charges = buildChargesItemized(
            (String) channel.get("code"), request, freight, fuelAmount, surchargeAmount,
            insuranceAmount, batteryAmount, processingAmount, commission);

        return new Quote(
            (String) channel.get("code"),
            (String) channel.get("name"),
            request.currency(),
            remoteLevel,
            request.weightKg().setScale(3, RoundingMode.HALF_UP),
            volumetric,
            chargeable,
            freight,
            fuelAmount,
            surchargeAmount,
            insuranceAmount,
            batteryAmount,
            processingAmount,
            commission,
            total,
            costFreight,
            costFuel,
            costSurcharge,
            costInsurance,
            costBattery,
            costProcessing,
            costTotal,
            fuelRate,
            evidence,
            List.copyOf(breakdown),
            List.copyOf(blockers),
            charges                                    // W1 新增
        );
    }

    /**
     * W1+W2: 构造 itemized charges[] 数组.
     * 跟 UPS Surcharges / FedEx Surcharges / DHL Charges 同构, 月底对账逐行比对.
     *
     * W2 增强:
     *   - Indicators (residential/signature/saturday/etc) 真触发 CarrierRuleEngine
     *     拉数据驱动的 ACCESSORIAL 规则 (Residential 5.65 USD/Signature 7.50/etc)
     *   - 燃油走 CarrierRuleEngine.computeFuel (UPS 公式: (base+白名单 accessorials)×fuel_pct)
     *     替代旧 rate_card 静态 fuelRate
     */
    private List<RateQuoteResponse.ChargeItem> buildChargesItemized(
            String channelCode, RateQuoteRequest req,
            BigDecimal freight, BigDecimal fuel, BigDecimal surcharge,
            BigDecimal insurance, BigDecimal battery, BigDecimal processing,
            BigDecimal commission) {
        List<RateQuoteResponse.ChargeItem> out = new ArrayList<>();
        String currency = req.currency();

        // 1. 基础运费
        if (freight != null && freight.signum() > 0) {
            out.add(RateQuoteResponse.ChargeItem.of(
                "BASE", "FREIGHT", "基础运费", freight, currency,
                "rate_card_lines × chargeable_weight"));
        }

        // 2. W2: Indicator 触发 → CarrierRuleEngine 真算
        // channel.code "UPS-GROUND-US" → carrier "UPS" + productClass "GROUND_US"
        String carrier = inferCarrier(channelCode);
        String productClass = inferProductClass(channelCode);
        List<RateQuoteResponse.ChargeItem> indicatorSurcharges = List.of();
        if (carrier != null) {
            try {
                indicatorSurcharges = carrierRules.computeSurcharges(carrier, productClass, req);
                out.addAll(indicatorSurcharges);
            } catch (Exception ex) {
                // CarrierRuleEngine 失败不阻断, 走 rate_card 老路径
            }
        }

        // 3. W2: 燃油 — 优先用 CarrierRuleEngine, fallback 用 rate_card 静态
        BigDecimal carrierFuel = BigDecimal.ZERO;
        if (carrier != null && freight != null && freight.signum() > 0) {
            try {
                RateQuoteResponse.ChargeItem fuelItem = carrierRules.computeFuel(
                    carrier, productClass, freight, indicatorSurcharges, currency);
                if (fuelItem != null && fuelItem.amount() != null && fuelItem.amount().signum() > 0) {
                    out.add(fuelItem);
                    carrierFuel = fuelItem.amount();
                }
            } catch (Exception ex) {
                // 失败回退用 rate_card 老 fuelRate
            }
        }
        // Fallback: 如果 CarrierRuleEngine 没出燃油, 用 rate_card 的 fuel
        if (carrierFuel.signum() == 0 && fuel != null && fuel.signum() > 0) {
            out.add(RateQuoteResponse.ChargeItem.of(
                "FUEL", "FUEL", "燃油附加费", fuel, currency,
                "rate_card 静态 fuelRate fallback"));
        }

        // 4. 其它金额
        if (surcharge != null && surcharge.signum() > 0) {
            out.add(RateQuoteResponse.ChargeItem.of(
                "SURCHARGE", "MISC", "杂项附加费", surcharge, currency,
                "rate_card surcharge"));
        }
        if (insurance != null && insurance.signum() > 0) {
            out.add(RateQuoteResponse.ChargeItem.of(
                "INSURANCE", "INS", "保险费", insurance, currency,
                "declared_value × insurance_rate"));
        }
        if (battery != null && battery.signum() > 0) {
            out.add(RateQuoteResponse.ChargeItem.of(
                "BATTERY", "BAT", "电池附加费", battery, currency,
                "channel_account.battery_*_fee"));
        }
        if (processing != null && processing.signum() > 0) {
            out.add(RateQuoteResponse.ChargeItem.of(
                "OTHER", "PROC", "操作费", processing, currency,
                "channel_account.processing_fee"));
        }
        if (commission != null && commission.signum() > 0) {
            out.add(new RateQuoteResponse.ChargeItem(
                "OTHER", "COMMISSION", "佣金", commission, currency,
                "rate_card commission rule",
                List.of(), false, false));
        }
        return out;
    }

    /**
     * 从 xqt-saas channel.code 推断 carrier (CarrierRuleEngine 用)
     *   UPS-GROUND-US / UPS-GROUND-US-RES / EU-AIR-UPS → UPS
     *   US-GROUND-FEDEX / US-HOMEDELIVERY-FEDEX-A → FEDEX
     *   EU-DHL-EXPRESS → DHL
     */
    private String inferCarrier(String channelCode) {
        if (channelCode == null) return null;
        String upper = channelCode.toUpperCase();
        if (upper.contains("UPS")) return "UPS";
        if (upper.contains("FEDEX")) return "FEDEX";
        if (upper.contains("DHL")) return "DHL";
        if (upper.contains("EMS")) return "EMS";
        return null;
    }

    /**
     * 推断 product_class
     *   UPS-GROUND-US / UPS-GROUND-US-RES → GROUND_US
     *   EU-AIR-UPS → INTERNATIONAL
     *   FedEx 同理
     */
    private String inferProductClass(String channelCode) {
        if (channelCode == null) return null;
        String upper = channelCode.toUpperCase();
        if (upper.contains("GROUND") && (upper.contains("-US") || upper.contains("US-"))) return "GROUND_US";
        if (upper.contains("AIR") || upper.contains("EU-") || upper.contains("INTL")) return "INTERNATIONAL";
        return null;
    }

    // ───────────────────── helpers ─────────────────────

    private BigDecimal calculateFreight(Map<String, Object> tier, BigDecimal chargeable,
                                         int pieces, BigDecimal volumeCbm) {
        String type = (String) tier.getOrDefault("calculation_type", "PER_KG");
        BigDecimal minAmount = toBigDecimal(tier.get("min_amount"));
        // 任务 S3 A4：按箱最低计费重 / 最低计费金额
        BigDecimal minWeightPerBox = toBigDecimal(tier.get("min_weight_per_box"));
        BigDecimal minAmountPerBox = toBigDecimal(tier.get("min_amount_per_box"));

        // A4 第 1 步：单箱最低重量上浮 chargeable
        if (minWeightPerBox != null && minWeightPerBox.signum() > 0 && pieces > 0) {
            BigDecimal floorWeight = minWeightPerBox.multiply(new BigDecimal(pieces));
            if (chargeable.compareTo(floorWeight) < 0) {
                chargeable = floorWeight;
            }
        }

        BigDecimal freight;
        switch (type) {
            case "FIRST_CONTINUED" -> {
                BigDecimal firstWeight = toBigDecimal(tier.get("first_weight_kg"));
                BigDecimal firstAmount = toBigDecimal(tier.get("first_amount"));
                BigDecimal stepKg = toBigDecimal(tier.get("continued_step_kg"));
                BigDecimal contUnit = toBigDecimal(tier.get("continued_unit_price"));
                if (firstWeight == null || firstAmount == null || stepKg == null || contUnit == null) {
                    throw ApiException.badRequest("FIRST_CONTINUED tier missing fields");
                }
                if (chargeable.compareTo(firstWeight) <= 0) {
                    freight = firstAmount;
                } else {
                    BigDecimal overWeight = chargeable.subtract(firstWeight);
                    BigDecimal steps = overWeight.divide(stepKg, 0, RoundingMode.CEILING);
                    freight = firstAmount.add(steps.multiply(contUnit));
                }
            }
            case "TIER_FLAT" -> {
                BigDecimal fixed = toBigDecimal(tier.get("fixed_amount"));
                if (fixed == null) throw ApiException.badRequest("TIER_FLAT tier missing fixed_amount");
                freight = fixed;
            }
            case "PER_PIECE" -> {
                BigDecimal unit = toBigDecimal(tier.get("unit_price"));
                freight = unit.multiply(new BigDecimal(pieces));
            }
            case "PER_CBM" -> {
                // 任务 S3 A6：按立方米计费
                BigDecimal unit = toBigDecimal(tier.get("unit_price"));
                BigDecimal vol = volumeCbm == null ? BigDecimal.ZERO : volumeCbm;
                if (vol.signum() <= 0) {
                    throw ApiException.badRequest("PER_CBM tier requires volumeCbm > 0");
                }
                freight = unit.multiply(vol);
            }
            default -> {
                BigDecimal unit = toBigDecimal(tier.get("unit_price"));
                freight = unit.multiply(chargeable);
            }
        }
        freight = freight.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        if (minAmount != null && minAmount.signum() > 0 && freight.compareTo(minAmount) < 0) {
            freight = minAmount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        // A4 第 2 步：单箱最低金额上浮 freight
        if (minAmountPerBox != null && minAmountPerBox.signum() > 0 && pieces > 0) {
            BigDecimal floorAmount = minAmountPerBox.multiply(new BigDecimal(pieces))
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            if (freight.compareTo(floorAmount) < 0) {
                freight = floorAmount;
            }
        }
        return freight;
    }

    /**
     * 任务 S3 A1：根据申报品名扫描 product_keyword_rules 命中的附加费。
     * 独立调用方法，避免破坏 quote() 的现有契约。Service 层可在 Submit 时调用。
     *
     * @param freight 当前 freight 金额，用于 PCT 类型规则计算
     * @return 附加费 BreakdownLine 列表（code = fee_code，amount 已按规则算出）
     */
    public List<RateQuoteResponse.BreakdownLine> applyKeywordSurcharges(String tenantId,
                                                                          List<String> declarationNames,
                                                                          BigDecimal freight,
                                                                          java.time.LocalDate chargeDate) {
        if (declarationNames == null || declarationNames.isEmpty()) return List.of();
        List<Map<String, Object>> rules = repository.findKeywordSurcharges(
            tenantId, declarationNames, chargeDate == null ? java.time.LocalDate.now() : chargeDate);
        List<RateQuoteResponse.BreakdownLine> out = new ArrayList<>();
        for (Map<String, Object> r : rules) {
            String feeCode = (String) r.get("fee_code");
            String unit = (String) r.get("charge_unit");
            BigDecimal amount;
            if ("PCT".equals(unit)) {
                BigDecimal rate = toBigDecimal(r.get("rate"));
                if (rate == null) continue;
                amount = freight.multiply(rate).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            } else {
                BigDecimal fixed = toBigDecimal(r.get("amount"));
                if (fixed == null) continue;
                amount = fixed.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            }
            out.add(new RateQuoteResponse.BreakdownLine(feeCode, "品名附加费: " + feeCode, amount));
        }
        return out;
    }

    /**
     * 申报价值保险计算 — 对应 ACC FreightClass 中按声明价值计费的段。
     * 公式：保险费 = max(declared × rate, min_fee)，free_coverage 以下不计费。
     */
    private BigDecimal applyInsuranceRate(Map<String, Object> rule, BigDecimal declaredValue) {
        BigDecimal free = toBigDecimal(rule.get("free_coverage"));
        BigDecimal billable = declaredValue;
        if (free != null && free.signum() > 0) {
            billable = declaredValue.subtract(free);
            if (billable.signum() <= 0) return BigDecimal.ZERO;
        }
        BigDecimal rate = toBigDecimal(rule.get("rate"));
        BigDecimal minFee = toBigDecimal(rule.get("min_fee"));
        BigDecimal fee = billable.multiply(rate == null ? BigDecimal.ZERO : rate)
            .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        if (minFee != null && fee.compareTo(minFee) < 0) {
            return minFee.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        return fee;
    }

    private BigDecimal applyRemoteRule(Map<String, Object> rule, BigDecimal base) {
        String type = (String) rule.get("rate_type");
        BigDecimal min = toBigDecimal(rule.get("min_amount"));
        BigDecimal value;
        if ("PERCENT".equals(type)) {
            BigDecimal rate = toBigDecimal(rule.get("rate"));
            value = base.multiply(rate == null ? BigDecimal.ZERO : rate)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        } else {
            BigDecimal fixed = toBigDecimal(rule.get("fixed_amount"));
            value = fixed == null ? BigDecimal.ZERO
                : fixed.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        if (min != null && min.signum() > 0 && value.compareTo(min) < 0) {
            return min.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        return value;
    }

    private BigDecimal applyCommissionRule(Map<String, Object> rule, BigDecimal freight,
                                            BigDecimal fuel, BigDecimal surcharge) {
        if ("FIXED".equals(rule.get("rule_type"))) {
            BigDecimal fixed = toBigDecimal(rule.get("fixed_amount"));
            return fixed == null ? BigDecimal.ZERO
                : fixed.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        BigDecimal rate = toBigDecimal(rule.get("rate"));
        if (rate == null) return BigDecimal.ZERO;
        BigDecimal basis = freight.add(fuel).add(surcharge);
        return basis.multiply(rate).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private void checkBatteryRestriction(Map<String, Object> restrict, int batteryType, List<String> blockers) {
        if (batteryType == 0) return;
        if (Boolean.FALSE.equals(restrict.get("battery_allowed"))) {
            blockers.add("此渠道不接受带电池货物");
        } else if (batteryType == 1 && Boolean.FALSE.equals(restrict.get("battery_built_in_allowed"))) {
            blockers.add("此渠道不接受内置电池");
        } else if (batteryType == 2 && Boolean.FALSE.equals(restrict.get("battery_dry_allowed"))) {
            blockers.add("此渠道不接受干电池");
        }
    }

    private void checkSpecialRestriction(Map<String, Object> restrict, int specialType, List<String> blockers) {
        if (specialType == 5 && Boolean.FALSE.equals(restrict.get("brand_allowed"))) {
            blockers.add("此渠道不接受仿牌商品");
        }
        if (Boolean.FALSE.equals(restrict.get("sensitive_allowed")) && specialType > 0 && specialType != 5) {
            blockers.add("此渠道不接受敏感货物");
        }
    }

    private void checkCountryRestriction(Map<String, Object> restrict, String countryCode, List<String> blockers) {
        String allowedCountry = (String) restrict.get("country_code");
        if (allowedCountry == null || allowedCountry.isBlank()) return;
        boolean isBlacklist = Boolean.TRUE.equals(restrict.get("country_blacklist"));
        boolean hit = allowedCountry.equalsIgnoreCase(countryCode);
        if (isBlacklist && hit) {
            blockers.add("目的国 " + countryCode + " 在此渠道黑名单");
        } else if (!isBlacklist && !hit) {
            blockers.add("目的国 " + countryCode + " 不在此渠道白名单");
        }
    }

    private void checkPostalRestriction(Map<String, Object> restrict, String postalCode, List<String> blockers) {
        String pattern = (String) restrict.get("postal_pattern");
        if (pattern == null || pattern.isBlank() || postalCode == null) return;
        if (!postalCode.matches(pattern)) {
            blockers.add("邮编 " + postalCode + " 不符合此渠道允许范围");
        }
    }

    private void checkChannelAccountLimits(Map<String, Object> limit, int pieces,
                                            BigDecimal weight, List<String> blockers) {
        Integer maxCount = (Integer) limit.get("max_count");
        Integer maxPiece = (Integer) limit.get("max_piece");
        BigDecimal maxWeight = toBigDecimal(limit.get("max_weight"));
        Integer countUsed = (Integer) limit.get("count_used");
        Integer pieceUsed = (Integer) limit.get("piece_used");
        BigDecimal weightUsed = toBigDecimal(limit.get("weight_used"));

        if (maxCount != null && maxCount > 0
                && countUsed != null && countUsed + 1 > maxCount) {
            blockers.add("渠道账号当日单数已达上限 (" + countUsed + "/" + maxCount + ")");
        }
        if (maxPiece != null && maxPiece > 0
                && pieceUsed != null && pieceUsed + pieces > maxPiece) {
            blockers.add("渠道账号当日件数已达上限 (" + (pieceUsed + pieces) + "/" + maxPiece + ")");
        }
        if (maxWeight != null && maxWeight.signum() > 0
                && weightUsed != null && weightUsed.add(weight).compareTo(maxWeight) > 0) {
            blockers.add("渠道账号当日重量已达上限 (" + weightUsed.add(weight) + "/" + maxWeight + " kg)");
        }
    }

    private String formatPostalPriority(Map<String, Object> tier) {
        Object pp = tier.get("postal_priority");
        if (pp == null) return "0";
        return String.valueOf(pp);
    }

    private BigDecimal volumetricWeight(BigDecimal volumeCbm, BigDecimal dimFactor) {
        if (volumeCbm == null || volumeCbm.signum() <= 0 || dimFactor == null || dimFactor.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return volumeCbm.multiply(CBM_TO_CM3).divide(dimFactor, 3, RoundingMode.HALF_UP);
    }

    private void setTenant(String tenantId) {
        jdbc.queryForObject("select set_config('app.current_tenant_id', ?, true)", String.class, tenantId);
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        return new BigDecimal(value.toString());
    }

    private void requireNonBlank(String name, String value) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest(name + " is required");
        }
    }
}

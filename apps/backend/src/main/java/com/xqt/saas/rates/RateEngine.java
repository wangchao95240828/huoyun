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
    private static final String DEFAULT_ZONE = "ZONE_A";

    private final RateRepository repository;
    private final JdbcTemplate jdbc;

    public RateEngine(RateRepository repository, JdbcTemplate jdbc) {
        this.repository = repository;
        this.jdbc = jdbc;
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
            blockers.add("destination is in embargo zone");
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
                throw ApiException.notFound("no active AR rate card for channel " + request.channelCode()
                    + " currency " + request.currency() + " on " + request.chargeDate());
            }
            chosenRateCardId = (String) baseCard.get("id");
        }

        // ─── 取价表行（含邮编精确匹配优先级）───
        Map<String, Object> tier = repository.findTier(
            tenantId, chosenRateCardId, DEFAULT_ZONE, chargeable, request.postalCode());
        if (tier == null) {
            throw ApiException.notFound("no rate tier covers " + chargeable
                + " kg in zone " + DEFAULT_ZONE);
        }
        String postalPriorityLabel = formatPostalPriority(tier);

        // ─── 多段计费 ───
        BigDecimal freight = calculateFreight(tier, chargeable, request.pieces());

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

        BigDecimal total = freight.add(fuelAmount).add(surchargeAmount)
            .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        // ─── 成本价（AP 侧，独立查表）───
        BigDecimal costFreight = null;
        BigDecimal costFuel = null;
        BigDecimal costSurcharge = null;
        BigDecimal costTotal = null;
        String costRateCardId = null;
        Map<String, Object> apCard = repository.findActiveRateCard(
            tenantId, channelId, "AP", request.currency(), request.chargeDate());
        if (apCard != null) {
            costRateCardId = (String) apCard.get("id");
            Map<String, Object> apTier = repository.findTier(
                tenantId, costRateCardId, DEFAULT_ZONE, chargeable, request.postalCode());
            if (apTier != null) {
                costFreight = calculateFreight(apTier, chargeable, request.pieces());
                costFuel = costFreight.multiply(fuelRate).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
                costSurcharge = remoteRule == null ? BigDecimal.ZERO
                    : applyRemoteRule(remoteRule, costFreight);
                costTotal = costFreight.add(costFuel).add(costSurcharge)
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
            commission,
            total,
            costFreight,
            costFuel,
            costSurcharge,
            costTotal,
            fuelRate,
            evidence,
            List.copyOf(breakdown),
            List.copyOf(blockers)
        );
    }

    // ───────────────────── helpers ─────────────────────

    private BigDecimal calculateFreight(Map<String, Object> tier, BigDecimal chargeable, int pieces) {
        String type = (String) tier.getOrDefault("calculation_type", "PER_KG");
        BigDecimal minAmount = toBigDecimal(tier.get("min_amount"));
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
            default -> {
                BigDecimal unit = toBigDecimal(tier.get("unit_price"));
                freight = unit.multiply(chargeable);
            }
        }
        freight = freight.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        if (minAmount != null && minAmount.signum() > 0 && freight.compareTo(minAmount) < 0) {
            freight = minAmount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        return freight;
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
            blockers.add("battery not allowed");
        } else if (batteryType == 1 && Boolean.FALSE.equals(restrict.get("battery_built_in_allowed"))) {
            blockers.add("built-in battery not allowed");
        } else if (batteryType == 2 && Boolean.FALSE.equals(restrict.get("battery_dry_allowed"))) {
            blockers.add("dry battery not allowed");
        }
    }

    private void checkSpecialRestriction(Map<String, Object> restrict, int specialType, List<String> blockers) {
        if (specialType == 5 && Boolean.FALSE.equals(restrict.get("brand_allowed"))) {
            blockers.add("brand-name (counterfeit) not allowed");
        }
        if (Boolean.FALSE.equals(restrict.get("sensitive_allowed")) && specialType > 0 && specialType != 5) {
            blockers.add("sensitive goods not allowed");
        }
    }

    private void checkCountryRestriction(Map<String, Object> restrict, String countryCode, List<String> blockers) {
        String allowedCountry = (String) restrict.get("country_code");
        if (allowedCountry == null || allowedCountry.isBlank()) return;
        boolean isBlacklist = Boolean.TRUE.equals(restrict.get("country_blacklist"));
        boolean hit = allowedCountry.equalsIgnoreCase(countryCode);
        if (isBlacklist && hit) {
            blockers.add("country " + countryCode + " is blacklisted");
        } else if (!isBlacklist && !hit) {
            blockers.add("country " + countryCode + " is not in whitelist");
        }
    }

    private void checkPostalRestriction(Map<String, Object> restrict, String postalCode, List<String> blockers) {
        String pattern = (String) restrict.get("postal_pattern");
        if (pattern == null || pattern.isBlank() || postalCode == null) return;
        if (!postalCode.matches(pattern)) {
            blockers.add("postal " + postalCode + " is not allowed for this service");
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
            blockers.add("channel account max_count exceeded (" + countUsed + "/" + maxCount + ")");
        }
        if (maxPiece != null && maxPiece > 0
                && pieceUsed != null && pieceUsed + pieces > maxPiece) {
            blockers.add("channel account max_piece exceeded (" + (pieceUsed + pieces) + "/" + maxPiece + ")");
        }
        if (maxWeight != null && maxWeight.signum() > 0
                && weightUsed != null && weightUsed.add(weight).compareTo(maxWeight) > 0) {
            blockers.add("channel account max_weight exceeded (" + weightUsed.add(weight) + "/" + maxWeight + ")");
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

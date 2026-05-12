package com.xqt.saas.rates;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import com.xqt.saas.rates.RateQuoteResponse.BreakdownLine;
import com.xqt.saas.rates.RateQuoteResponse.Quote;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 运费试算引擎。第一阶段只覆盖单段计费 + 燃油 + 偏远固定加成。
 *
 * 与 ACC config/Freight.php::getFee 的差异（已确认按路线图简化，不做 1:1 复刻）：
 *  - 不支持成本价 / 客户组价 / 客户专属价 / 佣金，统一查 rate_cards.side='AR'。
 *  - 不支持渠道账号限量 (MaxCount/MaxPiece/MaxWeight)。
 *  - 不支持电池 / 仿牌前置过滤，旧字段进 metadata，未来由风控模块判断。
 *  - 偏远附加按 remote_level 固定百分比，不读取 charge_rules.condition_json。
 */
@Service
public class RateEngine {
    private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final int MONEY_SCALE = 2;
    private static final BigDecimal CBM_TO_CM3 = new BigDecimal("1000000");
    private static final BigDecimal REMOTE_RATE = new BigDecimal("0.15");
    private static final BigDecimal SUPER_REMOTE_RATE = new BigDecimal("0.25");
    private static final String DEFAULT_ZONE = "ZONE_A";
    private static final String CHANNEL_FIELD_ACTIVE = "active";
    private static final String CHANNEL_FIELD_ID = "id";
    private static final String CHANNEL_FIELD_CODE = "code";
    private static final String CHANNEL_FIELD_NAME = "name";
    private static final String CHANNEL_FIELD_DIM = "dim_factor";

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
        if (Boolean.FALSE.equals(channel.get(CHANNEL_FIELD_ACTIVE))) {
            throw ApiException.badRequest("channel is disabled: " + request.channelCode());
        }

        BigDecimal volumetric = volumetricWeight(request.volumeCbm(), toBigDecimal(channel.get(CHANNEL_FIELD_DIM)));
        BigDecimal chargeable = request.weightKg().max(volumetric).setScale(3, RoundingMode.HALF_UP);

        Map<String, Object> rateCard = repository.findActiveRateCard(
            tenantId, (String) channel.get(CHANNEL_FIELD_ID), "AR", request.currency(), request.chargeDate());
        if (rateCard == null) {
            throw ApiException.notFound("no active AR rate card for channel " + request.channelCode()
                + " currency " + request.currency() + " on " + request.chargeDate());
        }

        String remoteLevel = repository.findRemoteLevel(
            tenantId, (String) channel.get(CHANNEL_FIELD_ID), request.countryCode(), request.postalCode());
        String zoneCode = DEFAULT_ZONE;

        Map<String, Object> tier = repository.findTier(
            tenantId, (String) rateCard.get(CHANNEL_FIELD_ID), zoneCode, chargeable);
        if (tier == null) {
            throw ApiException.notFound("no rate tier covers " + chargeable + " kg in zone " + zoneCode);
        }
        BigDecimal unitPrice = toBigDecimal(tier.get("unit_price"));
        BigDecimal minAmount = toBigDecimal(tier.get("min_amount"));

        BigDecimal freight = unitPrice.multiply(chargeable).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        if (minAmount != null && minAmount.signum() > 0 && freight.compareTo(minAmount) < 0) {
            freight = minAmount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }

        String yearMonth = request.chargeDate().format(YEAR_MONTH);
        BigDecimal fuelRate = repository.findFuelRate(tenantId, (String) channel.get(CHANNEL_FIELD_ID), yearMonth);
        BigDecimal fuelAmount = freight.multiply(fuelRate).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        BigDecimal remoteRate = remoteRateFor(remoteLevel);
        BigDecimal surchargeAmount = freight.multiply(remoteRate).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        BigDecimal total = freight.add(fuelAmount).add(surchargeAmount).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        List<BreakdownLine> breakdown = new ArrayList<>();
        breakdown.add(new BreakdownLine("FREIGHT", "基础运费", freight));
        if (fuelAmount.signum() != 0) {
            breakdown.add(new BreakdownLine("FUEL", "燃油附加费", fuelAmount));
        }
        if (surchargeAmount.signum() != 0) {
            breakdown.add(new BreakdownLine("REMOTE", "偏远附加费", surchargeAmount));
        }

        return new Quote(
            (String) channel.get(CHANNEL_FIELD_CODE),
            (String) channel.get(CHANNEL_FIELD_NAME),
            request.currency(),
            remoteLevel,
            request.weightKg().setScale(3, RoundingMode.HALF_UP),
            volumetric,
            chargeable,
            freight,
            fuelAmount,
            surchargeAmount,
            total,
            fuelRate,
            List.copyOf(breakdown)
        );
    }

    private BigDecimal volumetricWeight(BigDecimal volumeCbm, BigDecimal dimFactor) {
        if (volumeCbm == null || volumeCbm.signum() <= 0 || dimFactor == null || dimFactor.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return volumeCbm.multiply(CBM_TO_CM3).divide(dimFactor, 3, RoundingMode.HALF_UP);
    }

    private BigDecimal remoteRateFor(String level) {
        if (level == null) {
            return BigDecimal.ZERO;
        }
        switch (level) {
            case "REMOTE":
                return REMOTE_RATE;
            case "SUPER_REMOTE":
                return SUPER_REMOTE_RATE;
            case "EMBARGO":
                throw ApiException.badRequest("destination is in embargo zone");
            default:
                return BigDecimal.ZERO;
        }
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
        return new BigDecimal(value.toString());
    }

    private void requireNonBlank(String name, String value) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest(name + " is required");
        }
    }
}

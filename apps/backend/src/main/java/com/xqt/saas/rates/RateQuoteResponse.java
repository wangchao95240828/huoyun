package com.xqt.saas.rates;

import java.math.BigDecimal;
import java.util.List;

public final class RateQuoteResponse {
    private RateQuoteResponse() {
    }

    /**
     * 对应 ACC 旧响应：
     *   { Name, Currency, Weight, Freight, Fuel, Surcharge, SubTotal }
     * 在 ACC 字段语义之外补充 chargeableWeight 来源 (actual / volumetric) 和明细，方便审计。
     */
    public record Quote(
        String channelCode,
        String channelName,
        String currency,
        String remoteLevel,
        BigDecimal actualWeightKg,
        BigDecimal volumetricWeightKg,
        BigDecimal chargeableWeightKg,
        BigDecimal freight,
        BigDecimal fuelAmount,
        BigDecimal surchargeAmount,
        BigDecimal totalAmount,
        BigDecimal fuelRate,
        List<BreakdownLine> breakdown
    ) {
    }

    public record BreakdownLine(
        String code,
        String name,
        BigDecimal amount
    ) {
    }
}

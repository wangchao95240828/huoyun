package com.xqt.saas.rates;

import java.math.BigDecimal;
import java.util.List;

public final class RateQuoteResponse {
    private RateQuoteResponse() {
    }

    /**
     * 报价结果。包含销售价 + 成本价（如果引擎能拿到）+ 命中证据。
     *
     * 对应 ACC 旧响应：
     *   { Name, Currency, Weight, Freight, Fuel, Surcharge, SubTotal, Brokerage }
     *
     * 在 ACC 字段语义之外补充：
     *   - chargeableWeight 的来源（actual / volumetric）
     *   - commission（佣金，旧字段 Brokerage）
     *   - cost* 一组成本价输出（旧 ACC ProductType=1 单独调用得到）
     *   - matched 一组命中规则证据（rateCardId / customerRateMatched / 等）
     *   - blockers 一组拦截原因（电池不允许 / 限重超 / 邮编禁运 等）
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
        BigDecimal insuranceAmount,
        BigDecimal batteryAmount,
        BigDecimal processingAmount,
        BigDecimal commission,
        BigDecimal totalAmount,
        BigDecimal costFreight,
        BigDecimal costFuel,
        BigDecimal costSurcharge,
        BigDecimal costInsurance,
        BigDecimal costBattery,
        BigDecimal costProcessing,
        BigDecimal costTotal,
        BigDecimal fuelRate,
        MatchEvidence matched,
        List<BreakdownLine> breakdown,
        List<String> blockers
    ) {
    }

    /**
     * 命中证据：方便审计为什么选了某条价表行 / 哪个佣金规则 / 哪个限制阻拦了。
     */
    public record MatchEvidence(
        String rateCardId,
        String rateCardLineId,
        String costRateCardId,
        String customerRateMatched,
        String groupRateMatched,
        String channelAccountCode,
        String remoteRuleId,
        String commissionRuleId,
        String postalPriority,
        List<String> restrictionsChecked
    ) {
    }

    public record BreakdownLine(
        String code,
        String name,
        BigDecimal amount
    ) {
    }
}

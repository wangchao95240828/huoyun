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
        List<String> blockers,
        // W1: itemized charges (跟 UPS/FedEx/DHL Rate API 响应同构, 月底对账用)
        List<ChargeItem> charges
    ) {
        public Quote {
            if (charges == null) charges = List.of();
        }

        /** 26 参兼容构造 (旧 callsite 不传 charges) */
        public Quote(String channelCode, String channelName, String currency, String remoteLevel,
                     BigDecimal actualWeightKg, BigDecimal volumetricWeightKg, BigDecimal chargeableWeightKg,
                     BigDecimal freight, BigDecimal fuelAmount, BigDecimal surchargeAmount,
                     BigDecimal insuranceAmount, BigDecimal batteryAmount, BigDecimal processingAmount,
                     BigDecimal commission, BigDecimal totalAmount,
                     BigDecimal costFreight, BigDecimal costFuel, BigDecimal costSurcharge,
                     BigDecimal costInsurance, BigDecimal costBattery, BigDecimal costProcessing,
                     BigDecimal costTotal, BigDecimal fuelRate, MatchEvidence matched,
                     List<BreakdownLine> breakdown, List<String> blockers) {
            this(channelCode, channelName, currency, remoteLevel, actualWeightKg, volumetricWeightKg,
                 chargeableWeightKg, freight, fuelAmount, surchargeAmount, insuranceAmount,
                 batteryAmount, processingAmount, commission, totalAmount,
                 costFreight, costFuel, costSurcharge, costInsurance, costBattery, costProcessing,
                 costTotal, fuelRate, matched, breakdown, blockers, List.of());
        }
    }

    /**
     * 单一费用项 (跟 carrier API 响应里的 Surcharges[] 同构).
     *
     * UPS Surcharge {Code, Description, Amount}
     * FedEx Surcharge {SurchargeType, Description, Amount, Level}
     * DHL Charge {ChargeType, ChargeAmount}
     *
     * 字段映射:
     *   type: BASE / FUEL / RESIDENTIAL / OVERSIZE / LARGE_PACKAGE / ADDITIONAL_HANDLING
     *         / SIGNATURE / SATURDAY / REMOTE / DEMAND / DDU / VAT / INSURANCE / BATTERY / OTHER
     *   code: 承运商原始码 (UPS '270' = Large Package, FedEx 'FUEL', DHL 'FF')
     *   basis: 计算依据 (e.g. "freight × 17.5%" / "fixed 5.95 USD" / "weight 1.5kg × 0.42/kg")
     *   appliedTo: 如果 fuel surcharge, 列举叠加在哪些 charge 上 (审计用)
     */
    public record ChargeItem(
        String type,                  // 大类
        String code,                  // carrier 原始码 (留空表示 xqt-saas 自定义)
        String name,                  // 显示名 ("燃油附加费" / "Residential Surcharge")
        BigDecimal amount,            // 金额
        String currency,
        String basis,                 // 计算依据 (人读, 调试用)
        java.util.List<String> appliedTo,  // 燃油叠加在哪些 charge codes 上
        Boolean billable,             // 是否对客户收费 (false = 内部成本)
        Boolean taxable               // 是否计税基数
    ) {
        public ChargeItem {
            if (appliedTo == null) appliedTo = java.util.List.of();
            if (billable == null) billable = true;
            if (taxable == null) taxable = false;
        }

        /** 简便构造 (大部分简单 surcharge 用) */
        public static ChargeItem of(String type, String name, BigDecimal amount, String currency) {
            return new ChargeItem(type, null, name, amount, currency, null, java.util.List.of(), true, false);
        }

        /** 带 carrier 原始码的构造 */
        public static ChargeItem of(String type, String code, String name, BigDecimal amount, String currency, String basis) {
            return new ChargeItem(type, code, name, amount, currency, basis, java.util.List.of(), true, false);
        }
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

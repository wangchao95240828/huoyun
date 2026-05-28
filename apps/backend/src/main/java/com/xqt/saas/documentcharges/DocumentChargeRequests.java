package com.xqt.saas.documentcharges;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class DocumentChargeRequests {
    private DocumentChargeRequests() {
    }

    /** 把订单的已估算 AR 行翻成正式 CONFIRMED 费用（旧 ACC: Submit 后跑 Charge::Confirm）。 */
    public record GenerateFromOrder(
        String orderId,
        Boolean includeAp,            // 默认 true：同时把 AP ESTIMATED 也 CONFIRM
        Map<String, Object> overrides // 可选字段覆盖
    ) {
    }

    /** 客户账单生成：按日期段聚合该客户已审核 AR 费用为一张账单。 */
    public record GenerateCustomerInvoice(
        String customerId,
        LocalDate dateFrom,
        LocalDate dateTo,
        String currency,              // 多币种时分账单；默认 CNY
        String templateCode,          // 默认 'STANDARD'
        List<String> chargeIds        // 显式选行（覆盖日期筛选）
    ) {
    }

    /** 收款核销账单。支持部分核销和反核销（amount 为负）。 */
    public record SettleCustomerInvoice(
        String invoiceId,
        BigDecimal amount,
        String currency,
        String paymentMethod,         // 'BANK_TRANSFER' / 'CASH' / 'BALANCE'
        String bankAccountId,
        String referenceNo,
        String remark
    ) {
    }

    /** 供应商账单生成：按日期段聚合 AP 已审费用。 */
    public record GeneratePartnerInvoice(
        String partnerId,
        LocalDate dateFrom,
        LocalDate dateTo,
        String currency,
        List<String> chargeIds
    ) {
    }

    /** 供应商账单付款（含部分付/反核销）。 */
    public record SettlePartnerInvoice(
        String invoiceId,
        BigDecimal amount,
        String currency,
        String bankAccountId,
        String referenceNo,
        String remark
    ) {
    }
}

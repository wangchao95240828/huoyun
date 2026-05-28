package com.xqt.saas.documentcharges;

import java.math.BigDecimal;
import java.util.List;

public final class DocumentChargeResponses {
    private DocumentChargeResponses() {
    }

    public record GenerateResult(
        String orderId,
        int arCount,
        int apCount,
        BigDecimal arTotal,
        BigDecimal apTotal,
        BigDecimal profitEstimate,
        List<String> chargeIds
    ) {
    }

    public record InvoiceResult(
        String invoiceId,
        String invoiceNo,
        String currency,
        BigDecimal totalAmount,
        int lineCount,
        List<String> chargeIds
    ) {
    }

    public record SettleResult(
        String invoiceId,
        String invoiceNo,
        BigDecimal paidAmount,
        BigDecimal unpaidAmount,
        String writeoffStatus,         // 'UNPAID' / 'PARTIAL' / 'PAID'
        String paymentId
    ) {
    }

    public record VoidResult(
        String chargeId,
        BigDecimal refundedToBalance
    ) {
    }
}

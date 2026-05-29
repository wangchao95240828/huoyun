package com.xqt.saas.finance.feed;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;

class SandboxPaymentGatewayTest {

    private final SandboxPaymentGateway gw = new SandboxPaymentGateway();

    private PaymentGateway.ChargeRequest req(BigDecimal amount, Map<String, Object> extra) {
        return new PaymentGateway.ChargeRequest(
            "t1", "CUST-001", "ORD-1", amount, "CNY", "test", extra);
    }

    @Test
    void chargeReturnsSuccessByDefault() {
        var res = gw.charge(req(new BigDecimal("100.00"), null));
        assertThat(res.status()).isEqualTo("SUCCESS");
        assertThat(res.externalChargeId()).startsWith("SBX-PAY-");
        assertThat(res.paymentUrl()).contains(res.externalChargeId());
        assertThat(res.qrCode()).contains(res.externalChargeId());
    }

    @Test
    void simulatePendingControlsStatus() {
        var res = gw.charge(req(BigDecimal.TEN, Map.of("simulate", "PENDING")));
        assertThat(res.status()).isEqualTo("PENDING");
        // PENDING 不进 paid → query 也是 PENDING
        var q = gw.query("t1", res.externalChargeId());
        assertThat(q.status()).isEqualTo("PENDING");
        assertThat(q.paidAmount()).isEqualByComparingTo("0");
    }

    @Test
    void successChargeIsQueryableAsSuccess() {
        var res = gw.charge(req(new BigDecimal("88.88"), null));
        var q = gw.query("t1", res.externalChargeId());
        assertThat(q.status()).isEqualTo("SUCCESS");
        assertThat(q.paidAmount()).isEqualByComparingTo("88.88");
    }

    @Test
    void refundDecreasesPaidAndSuccessOnExactMatch() {
        var res = gw.charge(req(new BigDecimal("200.00"), null));
        var refund = gw.refund(new PaymentGateway.RefundRequest(
            "t1", res.externalChargeId(), new BigDecimal("200.00"), "test"));
        assertThat(refund.status()).isEqualTo("SUCCESS");
        assertThat(refund.externalRefundId()).startsWith("SBX-REFUND-");
        // 全额退后 paid 应 0
        assertThat(gw.query("t1", res.externalChargeId()).paidAmount()).isEqualByComparingTo("0");
    }

    @Test
    void partialRefundLeavesRemaining() {
        var res = gw.charge(req(new BigDecimal("100.00"), null));
        gw.refund(new PaymentGateway.RefundRequest(
            "t1", res.externalChargeId(), new BigDecimal("30.00"), ""));
        assertThat(gw.query("t1", res.externalChargeId()).paidAmount()).isEqualByComparingTo("70.00");
    }

    @Test
    void refundUnknownChargeFails() {
        var refund = gw.refund(new PaymentGateway.RefundRequest(
            "t1", "SBX-PAY-NOT-EXIST", BigDecimal.TEN, ""));
        assertThat(refund.status()).isEqualTo("FAILED");
    }

    @Test
    void codeIsSandbox() {
        assertThat(gw.code()).isEqualTo("SANDBOX");
    }
}

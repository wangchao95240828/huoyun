package com.xqt.saas.finance.feed;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 支付网关抽象。生产对接：
 *   - 支付宝：Alipay Open API（trade.create / trade.query）
 *   - 微信支付：Wechat Pay V3 API
 *   - 银联：UnionPay 全渠道
 *   - 国际：Stripe / PayPal
 *
 * 当前默认 {@link SandboxPaymentGateway}。接真实 provider 时另写一个 bean，
 * 通过配置 {@code app.payment.gateway=alipay/wechat/...} 选择激活。
 */
public interface PaymentGateway {

    String code();

    /**
     * 创建支付订单（用户应付一笔款时调用）。
     */
    ChargeResult charge(ChargeRequest request);

    /**
     * 查询支付状态（异步回调到达前/网络抖动时主动查）。
     */
    ChargeStatus query(String tenantId, String externalChargeId);

    /**
     * 退款（部分/全额）。
     */
    RefundResult refund(RefundRequest request);

    // ─── DTOs ───

    record ChargeRequest(
        String tenantId,
        String customerRef,
        String orderNo,
        BigDecimal amount,
        String currency,
        String description,
        Map<String, Object> extra
    ) {}

    record ChargeResult(
        String externalChargeId,
        String paymentUrl,        // 用户跳转的支付页 URL（如 alipay h5）
        String qrCode,            // 二维码内容（如 wechat native pay）
        String status,            // PENDING / SUCCESS / FAILED
        Map<String, Object> raw
    ) {}

    record ChargeStatus(
        String externalChargeId,
        String status,            // PENDING / SUCCESS / FAILED / REFUNDED
        BigDecimal paidAmount,
        Map<String, Object> raw
    ) {}

    record RefundRequest(
        String tenantId,
        String externalChargeId,
        BigDecimal amount,
        String reason
    ) {}

    record RefundResult(
        String externalRefundId,
        String status,            // SUCCESS / PENDING / FAILED
        Map<String, Object> raw
    ) {}
}

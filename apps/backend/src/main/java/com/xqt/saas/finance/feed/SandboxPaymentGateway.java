package com.xqt.saas.finance.feed;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

/**
 * 默认 sandbox 支付网关：本地内存模拟一次支付往返。
 * 生产替换为 Alipay / Wechat / UnionPay / Stripe 等真实实现。
 */
@Component
public class SandboxPaymentGateway implements PaymentGateway {
    private static final AtomicLong SEQ = new AtomicLong(1);
    private final Map<String, BigDecimal> PAID = new ConcurrentHashMap<>();

    @Override
    public String code() {
        return "SANDBOX";
    }

    @Override
    public ChargeResult charge(ChargeRequest req) {
        long n = SEQ.incrementAndGet();
        String id = "SBX-PAY-" + String.format("%012d", n);
        // sandbox：所有 charge 立即 SUCCESS（如需测试 PENDING/FAILED 路径，
        // 业务侧用 ChargeRequest.extra.simulate=PENDING/FAILED 控制）
        String simulate = req.extra() == null ? null : (String) req.extra().get("simulate");
        String status = simulate == null ? "SUCCESS" : simulate.toUpperCase();
        if ("SUCCESS".equals(status)) {
            PAID.put(id, req.amount());
        }
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("provider", "SANDBOX");
        raw.put("seq", n);
        raw.put("requested_at", java.time.Instant.now().toString());
        return new ChargeResult(
            id,
            "https://sandbox.payment.example.com/pay/" + id,
            "sbx://qr/" + id,
            status,
            raw);
    }

    @Override
    public ChargeStatus query(String tenantId, String externalChargeId) {
        BigDecimal paid = PAID.getOrDefault(externalChargeId, BigDecimal.ZERO);
        String status = paid.signum() > 0 ? "SUCCESS" : "PENDING";
        return new ChargeStatus(externalChargeId, status, paid,
            Map.of("provider", "SANDBOX"));
    }

    @Override
    public RefundResult refund(RefundRequest req) {
        BigDecimal paid = PAID.get(req.externalChargeId());
        if (paid == null || paid.compareTo(req.amount()) < 0) {
            return new RefundResult("SBX-REFUND-FAIL-" + SEQ.incrementAndGet(),
                "FAILED", Map.of("reason", "no_or_insufficient_charge"));
        }
        BigDecimal remaining = paid.subtract(req.amount());
        if (remaining.signum() == 0) {
            PAID.remove(req.externalChargeId());
        } else {
            PAID.put(req.externalChargeId(), remaining);
        }
        long n = SEQ.incrementAndGet();
        return new RefundResult(
            "SBX-REFUND-" + String.format("%012d", n),
            "SUCCESS",
            Map.of("provider", "SANDBOX", "refunded_amount", req.amount()));
    }
}

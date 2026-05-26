package com.xqt.saas.customerapi;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

/**
 * 默认占位渠道适配器：返回本地生成的子单号，便于在没有接真实渠道时跑通 Submit 状态机。
 * 真实渠道实现接入后，可以把 {@link CarrierGateway} bean 替换为路由器实现。
 */
@Component
public class NoopCarrierGateway implements CarrierGateway {
    private static final AtomicLong COUNTER = new AtomicLong();

    @Override
    public String gatewayKey() { return "NOOP"; }

    @Override
    public Issuance submit(SubmitContext ctx) {
        long seq = COUNTER.incrementAndGet();
        String suffix = ctx.orderNo() + "-" + String.format("%06d", seq % 1_000_000);
        String tracking = "NOOP-" + suffix;
        String master = "NOOP-M-" + String.format("%010d", seq);
        return new Issuance(tracking, master, Map.of(
            "provider", "NOOP",
            "submittedAt", java.time.Instant.now().toString()
        ));
    }
}

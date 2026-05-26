package com.xqt.saas.customerapi;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

/**
 * FedEx 风格演示 gateway。生成 12 位纯数字跟踪号（FedEx 经典 Ground 格式）。
 */
@Component
public class DemoFedexCarrierGateway implements CarrierGateway {
    private static final AtomicLong COUNTER = new AtomicLong(700_000_000L);

    @Override
    public String gatewayKey() { return "FEDEX_DEMO"; }

    @Override
    public Issuance submit(SubmitContext ctx) {
        long seq = COUNTER.incrementAndGet();
        // FedEx Ground 12 位数字
        String tracking = String.format("%012d", seq % 1_000_000_000_000L);
        String master = String.format("%012d", (seq + 1) % 1_000_000_000_000L);
        return new Issuance(tracking, master, Map.of(
            "provider", "FEDEX_DEMO",
            "service", "GROUND",
            "submittedAt", java.time.Instant.now().toString()
        ));
    }
}

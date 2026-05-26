package com.xqt.saas.customerapi;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

/**
 * UPS 风格的演示 gateway。生成符合 UPS 1Z 跟踪号格式的子单号：1Z + 6 位 shipper + 8 位 sequence。
 * 用于演示 CarrierGatewayRegistry 按 channel 路由的能力。真接 UPS REST API 替换此实现即可。
 */
@Component
public class DemoUpsCarrierGateway implements CarrierGateway {
    private static final AtomicLong COUNTER = new AtomicLong(100000);

    @Override
    public String gatewayKey() { return "UPS_DEMO"; }

    @Override
    public Issuance submit(SubmitContext ctx) {
        long seq = COUNTER.incrementAndGet();
        // UPS 1Z 格式：1Z + 6 位 shipper（这里用 customer code 前 6 位 padded）+ 2 位服务级别 + 8 位 sequence
        String shipper = padOrCut(ctx.customerCode(), 6);
        String tracking = String.format("1Z%s01%08d", shipper, seq % 100_000_000);
        String master = String.format("1Z%sM0%08d", shipper, seq % 100_000_000);
        return new Issuance(tracking, master, Map.of(
            "provider", "UPS_DEMO",
            "service", "STANDARD",
            "submittedAt", java.time.Instant.now().toString()
        ));
    }

    private String padOrCut(String s, int len) {
        if (s == null) s = "ANON";
        s = s.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        if (s.length() >= len) return s.substring(0, len);
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < len) sb.append('0');
        return sb.toString();
    }
}

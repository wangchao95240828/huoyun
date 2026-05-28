package com.xqt.saas.customerapi;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

/**
 * Sandbox 渠道取号适配器。
 *
 * 比 Demo gateway 更接近真实 provider：模拟一次 provider HTTP 往返，
 * 把"请求报文"和"响应报文"完整存入 {@link Issuance#raw}（即 charges/shipment 的 evidence），
 * 复刻 ACC 在线下单插件保存 provider 交互证据的行为。
 *
 * 由 acc_channel_accounts.provider_code='SANDBOX' 驱动（见 CarrierGatewayRegistry）。
 * 真实 UPS/FedEx/EDI adapter 按此模板新建，把模拟往返换成真实 HTTP 调用即可。
 */
@Component
public class SandboxCarrierGateway implements CarrierGateway {
    private static final AtomicLong SEQ = new AtomicLong(1);
    private static final String ENDPOINT = "https://sandbox.carrier.example.com/v1/shipments";

    /** 模拟 provider 那边的活跃单号集合，cancel 时从中移除。生产 adapter 应调真实 HTTP。 */
    private static final java.util.Set<String> ACTIVE = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @Override
    public String gatewayKey() {
        return "SANDBOX";
    }

    /** 任务 S2：模拟向 provider 发 cancel HTTP。本地是从 ACTIVE 集合移除。 */
    @Override
    public boolean cancel(String tenantId, String masterTrackingNo) {
        if (masterTrackingNo == null || masterTrackingNo.isBlank()) return false;
        return ACTIVE.remove(masterTrackingNo);
    }

    @Override
    public Issuance submit(SubmitContext ctx) {
        long seq = SEQ.incrementAndGet();
        // 模拟向 provider 发出的请求报文
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("endpoint", ENDPOINT);
        request.put("method", "POST");
        request.put("orderNo", ctx.orderNo());
        request.put("customerRef", ctx.customerRef());
        request.put("channelCode", ctx.channelCode());
        request.put("destinationCountry", ctx.country());
        request.put("weightKg", ctx.weightKg());
        request.put("pieces", ctx.piece());
        request.put("receiver", ctx.receiver());

        // 模拟 provider 返回：sandbox 单号 SBX + 时间序列
        String tracking = String.format("SBX%013d", seq + System.currentTimeMillis() % 1_000_000);
        String master = "SBXM" + tracking.substring(3);
        // 记录到活跃单号集合（cancel 时反向移除）
        ACTIVE.add(master);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "ACCEPTED");
        response.put("trackingNumber", tracking);
        response.put("masterTrackingNumber", master);
        response.put("labelAvailable", true);
        response.put("respondedAt", java.time.Instant.now().toString());

        // evidence：请求 + 响应 完整留痕，便于事后对账/排障（文档要求）
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("provider", "SANDBOX");
        raw.put("request", request);
        raw.put("response", response);

        return new Issuance(tracking, master, raw);
    }
}

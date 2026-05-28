package com.xqt.saas.labels;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

/**
 * Sandbox 面单 provider：比 Noop 更接近真实——模拟一次 provider HTTP 往返，
 * 把 request + response 报文存到 LabelArtifact.extra（写入 label_files.evidence），
 * 复刻 ACC 在线下单插件保存面单 provider 交互证据的行为。
 *
 * 由 acc_channel_accounts.provider_code='SANDBOX' 驱动（见 LabelGatewayRegistry）。
 */
@Component
public class SandboxLabelGateway implements LabelGateway {
    private static final AtomicLong SEQ = new AtomicLong(1);
    private static final String ENDPOINT = "https://sandbox.carrier.example.com/v1/labels";

    @Override
    public LabelArtifact print(PrintContext ctx) {
        long seq = SEQ.incrementAndGet();
        String tracking = String.format("SBX-LBL-%010d", seq);
        String sub = String.format("SBX-SUB-%010d", seq);
        boolean wantZpl = "ZPL".equalsIgnoreCase(ctx.requestedType());

        // 模拟 provider 请求
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("endpoint", ENDPOINT);
        request.put("method", "POST");
        request.put("shipmentNo", ctx.shipmentNo());
        request.put("channelCode", ctx.channelCode());
        request.put("country", ctx.countryCode());
        request.put("format", wantZpl ? "ZPL" : "PDF");

        // 模拟 provider 响应
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "OK");
        response.put("mainTrackingNumber", tracking);
        response.put("subTrackingNumber", sub);
        response.put("format", wantZpl ? "ZPL" : "PDF");
        response.put("respondedAt", java.time.Instant.now().toString());

        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("provider", "SANDBOX");
        raw.put("request", request);
        raw.put("response", response);

        byte[] content;
        String type;
        String ext;
        if (wantZpl) {
            String zpl = "^XA^FO50,50^A0N,40,40^FDSBX " + ctx.shipmentNo() + "^FS^XZ";
            content = zpl.getBytes(StandardCharsets.UTF_8);
            type = "ZPL";
            ext = "zpl";
        } else {
            // Sandbox 不实现完整 PDF 生成（生产 adapter 会从 provider 拉真实面单 PDF）；
            // 这里产一段 placeholder bytes 标识是 sandbox 出的非真实 PDF
            content = ("%SANDBOX_LABEL " + tracking + " " + ctx.shipmentNo() + "\n")
                .getBytes(StandardCharsets.US_ASCII);
            type = "PDF";
            ext = "pdf";
        }
        return new LabelArtifact(content, type, ext, tracking, List.of(sub), raw);
    }
}

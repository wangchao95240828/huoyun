package com.xqt.saas.customerapi;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xqt.saas.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Karrio 多承运商网关适配 — 把 xqt-saas 的 SubmitContext 翻译成 Karrio REST API,
 * 一次性接入 UPS/FedEx/DHL/SF Express/YunExpress 等 30+ 承运商.
 *
 * 部署: Karrio 独立 docker-compose 跑在 :5002 (默认), 通过 application.yml 配 base URL + API token.
 *
 * 路由触发: acc_channel_accounts.provider_code='KARRIO' (任何渠道账号)
 *
 * Karrio API:
 *   POST {base}/v1/shipments       创建 shipment + 取号 + 拉面单
 *   POST {base}/v1/rating          只报价不下单
 *   GET  {base}/v1/tracking/:id    跟踪
 *
 * Auth: Authorization: Token <api_token>
 */
@Component
public class KarrioCarrierGateway implements CarrierGateway {
    private static final Logger LOGGER = LoggerFactory.getLogger(KarrioCarrierGateway.class);

    private final HttpClient http;
    private final ObjectMapper json;
    private final JdbcTemplate jdbc;
    private final String baseUrl;
    private final String defaultToken;
    private final int timeoutSec;

    public KarrioCarrierGateway(JdbcTemplate jdbc, ObjectMapper json,
                                 @Value("${karrio.base-url:http://localhost:5002}") String baseUrl,
                                 @Value("${karrio.api-token:}") String defaultToken,
                                 @Value("${karrio.timeout-sec:30}") int timeoutSec) {
        this.jdbc = jdbc;
        this.json = json;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.defaultToken = defaultToken;
        this.timeoutSec = timeoutSec;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    @Override
    public String gatewayKey() { return "KARRIO"; }

    @Override
    public Issuance submit(SubmitContext ctx) {
        // 1. 查渠道账号 → 拿 Karrio 的 carrier_id + service_code + api_token override
        Map<String, Object> chAcct = lookupChannelAccount(ctx.tenantId(), ctx.channelCode());
        String carrierId = strOr(chAcct.get("api_key"),  // 用 api_key 字段存 Karrio carrier_id
            inferCarrierIdFromChannel(ctx.channelCode()));
        String serviceCode = strOr(chAcct.get("endpoint_url"),  // 复用 endpoint_url 字段存 service
            inferServiceFromChannel(ctx.channelCode()));
        String token = strOr(chAcct.get("api_secret"), defaultToken);

        if (token == null || token.isBlank()) {
            throw ApiException.badRequest(
                "Karrio API token 未配置 (acc_channel_accounts.api_secret 或 karrio.api-token)");
        }
        if (carrierId == null) {
            throw ApiException.badRequest(
                "渠道 [" + ctx.channelCode() + "] 未关联 Karrio carrier_id (写到 acc_channel_accounts.api_key)");
        }

        // 2. 构造 Karrio shipment 请求体
        Map<String, Object> body = buildShipmentRequest(ctx, carrierId, serviceCode);

        // 3. POST /v1/shipments — 同时取号 + 出面单
        Map<String, Object> resp = httpPost("/v1/shipments", token, body);

        // 4. 解析 response
        String trackingNo = strOr(resp.get("tracking_number"), null);
        if (trackingNo == null) {
            // 失败 → Karrio 返 messages 数组
            Object messages = resp.get("messages");
            throw ApiException.badRequest(
                "Karrio 提交失败: " + (messages == null ? resp.toString() : messages.toString()));
        }
        String labelB64 = strOr(resp.get("label"), null);
        String shipmentId = strOr(resp.get("id"), null);

        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("provider", "KARRIO");
        raw.put("carrier_id", carrierId);
        raw.put("service", serviceCode);
        raw.put("shipment_id", shipmentId);
        raw.put("karrio_raw", resp);
        if (labelB64 != null) {
            raw.put("label_base64", labelB64);
            raw.put("label_format", "PDF");
        }
        return new Issuance(trackingNo, shipmentId == null ? trackingNo : shipmentId, raw);
    }

    @Override
    public boolean cancel(String tenantId, String masterTrackingNo) {
        // Karrio shipment cancel: POST /v1/shipments/{id}/cancel
        if (masterTrackingNo == null) return false;
        try {
            httpPost("/v1/shipments/" + masterTrackingNo + "/cancel", defaultToken, Map.of());
            return true;
        } catch (Exception ex) {
            LOGGER.warn("Karrio cancel failed for {}: {}", masterTrackingNo, ex.getMessage());
            return false;
        }
    }

    // ─── helpers ──────────────────────────────

    private Map<String, Object> buildShipmentRequest(SubmitContext ctx, String carrierId, String service) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("carrier_ids", List.of(carrierId));
        if (service != null) req.put("service", service);

        // shipper (默认仓库 — 业务里走 sender 配置, 这里先占位)
        req.put("shipper", Map.of(
            "company_name", "XQT Warehouse",
            "address_line1", "100 Demo Warehouse Ave",
            "city", "Los Angeles",
            "state_code", "CA",
            "postal_code", "90001",
            "country_code", "US",
            "person_name", "Warehouse Manager",
            "phone_number", "3105550000"
        ));

        // recipient (从 SubmitContext.receiver 映射)
        Map<String, Object> r = ctx.receiver();
        Map<String, Object> recipient = new LinkedHashMap<>();
        recipient.put("company_name", strOr(r.get("company"), ""));
        recipient.put("person_name", strOr(r.get("name"), ""));
        recipient.put("phone_number", strOr(r.get("phone"), ""));
        recipient.put("address_line1", strOr(r.get("address"), ""));
        recipient.put("city", strOr(r.get("city"), ""));
        recipient.put("state_code", strOr(r.get("province"), ""));
        recipient.put("postal_code", strOr(r.get("postcode"), strOr(r.get("areaCode"), "")));
        recipient.put("country_code", strOr(r.get("country"), ctx.country()));
        req.put("recipient", recipient);

        // parcels (从 packageList 映射, 没有就走 SubmitContext.weightKg 单包裹)
        List<Map<String, Object>> parcels = new java.util.ArrayList<>();
        if (ctx.packageList() != null && !ctx.packageList().isEmpty()) {
            for (Map<String, Object> pkg : ctx.packageList()) {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("weight", asDouble(pkg.get("weight"), 1.0));
                p.put("weight_unit", "KG");
                if (pkg.get("length") != null) {
                    p.put("dimension_unit", "CM");
                    p.put("length", asDouble(pkg.get("length"), 0));
                    p.put("width", asDouble(pkg.get("width"), 0));
                    p.put("height", asDouble(pkg.get("height"), 0));
                }
                p.put("packaging_type", "your_packaging");
                parcels.add(p);
            }
        } else {
            parcels.add(Map.of(
                "weight", ctx.weightKg() == null ? 1.0 : ctx.weightKg().doubleValue(),
                "weight_unit", "KG",
                "packaging_type", "your_packaging"
            ));
        }
        req.put("parcels", parcels);

        // 报关 (如果是国际)
        if (ctx.country() != null && !"US".equalsIgnoreCase(ctx.country())) {
            Map<String, Object> customs = new LinkedHashMap<>();
            customs.put("content_type", "merchandise");
            customs.put("incoterm", "DDP");
            req.put("customs", customs);
        }

        // 标签格式
        req.put("label_type", "PDF");
        req.put("metadata", Map.of("order_no", ctx.orderNo(), "customer", ctx.customerCode()));
        return req;
    }

    private Map<String, Object> lookupChannelAccount(String tenantId, String channelCode) {
        try {
            return jdbc.queryForMap("""
                SELECT a.api_key, a.api_secret, a.endpoint_url, a.account_no
                  FROM acc_channel_accounts a
                  JOIN channels c ON c.id = a.channel_id
                 WHERE a.tenant_id = ?::uuid AND c.code = ?
                   AND a.is_active = true AND a.provider_code = 'KARRIO'
                 ORDER BY a.created_at DESC LIMIT 1
                """, tenantId, channelCode);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    /** 从 channel.code 推断 Karrio carrier_id — 仅 fallback */
    private String inferCarrierIdFromChannel(String code) {
        if (code == null) return null;
        String upper = code.toUpperCase();
        if (upper.contains("UPS")) return "ups_default";
        if (upper.contains("FEDEX")) return "fedex_default";
        if (upper.contains("DHL")) return "dhl_express_default";
        if (upper.contains("SF") || upper.contains("SFEXPRESS")) return "sfexpress_default";
        if (upper.contains("YUN")) return "yunexpress_default";
        return null;
    }

    private String inferServiceFromChannel(String code) {
        if (code == null) return null;
        String upper = code.toUpperCase();
        if (upper.contains("GROUND")) return "ups_ground";
        if (upper.contains("EXPRESS_SAVER")) return "ups_express_saver";
        if (upper.contains("AIR")) return "ups_worldwide_express";
        return null;
    }

    private Map<String, Object> httpPost(String path, String token, Map<String, Object> body) {
        try {
            String url = baseUrl + path;
            String reqBody = json.writeValueAsString(body);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSec))
                .header("Authorization", "Token " + token)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(reqBody))
                .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            String respBody = resp.body();
            if (resp.statusCode() >= 400) {
                throw ApiException.badRequest(
                    "Karrio HTTP " + resp.statusCode() + ": " + truncate(respBody, 500));
            }
            return json.readValue(respBody, new TypeReference<>() {});
        } catch (ApiException ex) { throw ex; }
          catch (Exception ex) {
            throw ApiException.badRequest("Karrio HTTP 调用失败: " + ex.getMessage());
        }
    }

    private static String strOr(Object o, String fallback) {
        if (o == null) return fallback;
        String s = o.toString().trim();
        return s.isEmpty() ? fallback : s;
    }
    private static double asDouble(Object o, double fb) {
        if (o == null) return fb;
        try { return Double.parseDouble(o.toString()); } catch (Exception ex) { return fb; }
    }
    private static String truncate(String s, int max) {
        return s == null ? "" : (s.length() > max ? s.substring(0, max) + "..." : s);
    }
}

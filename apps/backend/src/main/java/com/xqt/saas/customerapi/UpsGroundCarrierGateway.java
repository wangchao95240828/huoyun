package com.xqt.saas.customerapi;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xqt.saas.common.ApiException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * UPS REST API 真实下单 gateway。
 *
 * 路由：acc_channel_accounts.provider_code = 'UPS'
 *
 * UPS API 流程：
 *   1. OAuth2 client_credentials → access_token (有效期 ~4h, 缓存)
 *   2. POST /api/shipments/v2403/ship 提交 shipment
 *      返回 shipmentNumber + tracking + label base64
 *
 * 凭证存储：acc_channel_accounts {api_key, api_secret, endpoint_url, remark (jsonb)}
 * remark JSON 含：shipper_name/phone/address1/city/province/country/postcode + service_type
 */
@Component
public class UpsGroundCarrierGateway implements CarrierGateway {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15)).build();

    // tenant -> {token, expiresAt}
    private final Map<String, TokenCache> tokenCache = new ConcurrentHashMap<>();

    public UpsGroundCarrierGateway(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public String gatewayKey() { return "UPS"; }

    @Override
    public Issuance submit(SubmitContext ctx) {
        UpsCreds creds = loadCreds(ctx.tenantId(), ctx.channelCode());
        if (creds == null) {
            throw ApiException.badRequest("UPS 渠道账号未配置: " + ctx.channelCode());
        }

        try {
            String token = getAccessToken(ctx.tenantId(), creds);
            Map<String, Object> shipReq = buildShipmentRequest(ctx, creds);
            String body = json.writeValueAsString(shipReq);

            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(creds.endpointUrl + "/shipments/v2403/ship"))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + token)
                .header("transId", java.util.UUID.randomUUID().toString())
                .header("transactionSrc", "XQT-ACC")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                System.err.println("[UpsGroundCarrierGateway] ship failed " + resp.statusCode()
                    + " body=" + resp.body().substring(0, Math.min(500, resp.body().length())));
                throw ApiException.badRequest("UPS API 返回 " + resp.statusCode() + ": "
                    + resp.body().substring(0, Math.min(200, resp.body().length())));
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> respMap = json.readValue(resp.body(), Map.class);
            return parseUpsResponse(respMap);

        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw ApiException.badRequest("UPS 下单失败: " + ex.getMessage());
        }
    }

    private String getAccessToken(String tenantId, UpsCreds creds) throws Exception {
        TokenCache cached = tokenCache.get(tenantId);
        if (cached != null && cached.expiresAt.isAfter(Instant.now().plusSeconds(60))) {
            return cached.token;
        }
        // OAuth2 endpoint: replace /api with /security/v1/oauth/token
        String oauthUrl = creds.endpointUrl.replace("/api", "/security/v1/oauth/token");
        String basic = Base64.getEncoder().encodeToString(
            (creds.clientId + ":" + creds.clientSecret).getBytes(StandardCharsets.UTF_8));

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(oauthUrl))
            .timeout(Duration.ofSeconds(15))
            .header("Authorization", "Basic " + basic)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("x-merchant-id", creds.accountNo == null ? "" : creds.accountNo)
            .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials"))
            .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw ApiException.badRequest("UPS OAuth 失败 " + resp.statusCode() + ": "
                + resp.body().substring(0, Math.min(300, resp.body().length())));
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> tokenResp = json.readValue(resp.body(), Map.class);
        String token = (String) tokenResp.get("access_token");
        long ttl = parseLong(tokenResp.get("expires_in"), 14400);
        tokenCache.put(tenantId, new TokenCache(token, Instant.now().plusSeconds(ttl)));
        return token;
    }

    /** 构造 UPS shipment v2403 请求体 */
    private Map<String, Object> buildShipmentRequest(SubmitContext ctx, UpsCreds creds) {
        Map<String, Object> shipper = new LinkedHashMap<>();
        shipper.put("Name", creds.shipperName);
        shipper.put("ShipperNumber", creds.accountNo);
        shipper.put("Phone", Map.of("Number", creds.shipperPhone == null ? "" : creds.shipperPhone));
        Map<String, Object> shipperAddr = new LinkedHashMap<>();
        shipperAddr.put("AddressLine", List.of(creds.shipperAddress1 == null ? "" : creds.shipperAddress1));
        shipperAddr.put("City", creds.shipperCity);
        shipperAddr.put("StateProvinceCode", creds.shipperProvince);
        shipperAddr.put("PostalCode", creds.shipperPostcode);
        shipperAddr.put("CountryCode", creds.shipperCountry);
        shipper.put("Address", shipperAddr);

        Map<String, Object> recv = ctx.receiver() == null ? Map.of() : ctx.receiver();
        Map<String, Object> shipTo = new LinkedHashMap<>();
        shipTo.put("Name", strOr(recv.get("name"), recv.get("consignee"), "Receiver"));
        shipTo.put("Phone", Map.of("Number", strOr(recv.get("phone"), "0000000000")));
        Map<String, Object> shipToAddr = new LinkedHashMap<>();
        // 表单普遍用 address1 (+ 可选 address2)；旧字段 address 留作 fallback
        String line1 = strOr(recv.get("address1"), recv.get("address"), "");
        String line2 = strOr(recv.get("address2"), "");
        List<String> addrLines = line2.isEmpty() ? List.of(line1) : List.of(line1, line2);
        shipToAddr.put("AddressLine", addrLines);
        shipToAddr.put("City", strOr(recv.get("city"), ""));
        shipToAddr.put("StateProvinceCode", strOr(recv.get("province"), recv.get("state"), ""));
        shipToAddr.put("PostalCode", strOr(recv.get("postcode"), recv.get("zip"), ""));
        shipToAddr.put("CountryCode", strOr(recv.get("country"), ctx.country() == null ? "US" : ctx.country()));
        shipTo.put("Address", shipToAddr);

        Map<String, Object> service = Map.of(
            "Code", upsServiceCode(creds.serviceType),
            "Description", creds.serviceType == null ? "Ground" : creds.serviceType
        );

        // Package — UPS US 国内单要求 LBS+IN（KGS 只能配 CM；混用会被 120548 拒绝）
        BigDecimal wKg = ctx.weightKg() == null ? BigDecimal.ONE : ctx.weightKg();
        BigDecimal wLbs = wKg.multiply(new BigDecimal("2.20462"))
            .setScale(1, java.math.RoundingMode.HALF_UP);
        Map<String, Object> pkg = new LinkedHashMap<>();
        pkg.put("Packaging", Map.of("Code", "02", "Description", "Customer Supplied Package"));
        pkg.put("PackageWeight", Map.of(
            "UnitOfMeasurement", Map.of("Code", "LBS"),
            "Weight", wLbs.toPlainString()
        ));

        // Shipment
        Map<String, Object> shipment = new LinkedHashMap<>();
        shipment.put("Description", "XQT export");
        shipment.put("Shipper", shipper);
        shipment.put("ShipTo", shipTo);
        shipment.put("Service", service);
        shipment.put("Package", List.of(pkg));
        shipment.put("PaymentInformation", Map.of(
            "ShipmentCharge", Map.of(
                "Type", "01",
                "BillShipper", Map.of("AccountNumber", creds.accountNo)
            )
        ));

        // Label spec
        Map<String, Object> labelSpec = Map.of(
            "LabelImageFormat", Map.of("Code", "PDF", "Description", "PDF"),
            "LabelStockSize", Map.of("Height", "6", "Width", "4")
        );

        Map<String, Object> shipReq = new LinkedHashMap<>();
        shipReq.put("ShipmentRequest", Map.of(
            "Request", Map.of("RequestOption", "validate"),
            "Shipment", shipment,
            "LabelSpecification", labelSpec
        ));
        return shipReq;
    }

    /** UPS 服务码映射. */
    private static String upsServiceCode(String serviceType) {
        if (serviceType == null) return "03";  // Ground
        String s = serviceType.toLowerCase();
        if (s.contains("next") || s.contains("1day")) return "01";
        if (s.contains("2day")) return "02";
        if (s.contains("3day")) return "12";
        if (s.contains("ground") || s.contains("接地")) return "03";
        if (s.contains("standard")) return "11";
        if (s.contains("worldwide express")) return "07";
        return "03";
    }

    @SuppressWarnings("unchecked")
    private Issuance parseUpsResponse(Map<String, Object> resp) {
        Map<String, Object> shipResp = (Map<String, Object>) resp.get("ShipmentResponse");
        if (shipResp == null) {
            throw ApiException.badRequest("UPS 响应缺 ShipmentResponse");
        }
        Map<String, Object> results = (Map<String, Object>) shipResp.get("ShipmentResults");
        if (results == null) {
            throw ApiException.badRequest("UPS 响应缺 ShipmentResults");
        }
        String masterTracking = (String) results.get("ShipmentIdentificationNumber");
        // PackageResults: 子包裹 tracking
        Object pkgResults = results.get("PackageResults");
        String pkgTracking = null;
        String labelBase64 = null;
        if (pkgResults instanceof List<?> lst && !lst.isEmpty()) {
            Map<String, Object> first = (Map<String, Object>) lst.get(0);
            pkgTracking = (String) first.get("TrackingNumber");
            Map<String, Object> labelImage = (Map<String, Object>) first.get("ShippingLabel");
            if (labelImage != null) labelBase64 = (String) labelImage.get("GraphicImage");
        } else if (pkgResults instanceof Map<?, ?> m) {
            Map<String, Object> first = (Map<String, Object>) m;
            pkgTracking = (String) first.get("TrackingNumber");
            Map<String, Object> labelImage = (Map<String, Object>) first.get("ShippingLabel");
            if (labelImage != null) labelBase64 = (String) labelImage.get("GraphicImage");
        }

        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("provider", "UPS");
        raw.put("shipment_id", masterTracking);
        if (labelBase64 != null) {
            // ACC 对齐：createOrder 成功立即落盘 label。完整 base64 透传给 service 层，
            // service 用 LabelStorage 存盘 + INSERT label_files 后会把这个 key 抹掉，
            // 避免 cartons.carrier_evidence 字段被塞进几百 KB 的 base64。
            raw.put("label_format", "PDF");
            raw.put("label_base64", labelBase64);
            raw.put("label_base64_len", labelBase64.length());
        }
        raw.put("submittedAt", Instant.now().toString());
        return new Issuance(pkgTracking, masterTracking, raw);
    }

    private UpsCreds loadCreds(String tenantId, String channelCode) {
        try {
            Map<String, Object> row = jdbc.queryForMap("""
                SELECT a.api_key, a.api_secret, a.account_no, a.endpoint_url, a.remark
                FROM acc_channel_accounts a
                JOIN channels ch ON ch.id = a.channel_id
                WHERE a.tenant_id = ?::uuid AND ch.code = ?
                  AND a.is_active = true AND a.provider_code = 'UPS'
                ORDER BY a.created_at DESC LIMIT 1
                """, tenantId, channelCode);

            UpsCreds c = new UpsCreds();
            c.clientId = (String) row.get("api_key");
            c.clientSecret = (String) row.get("api_secret");
            c.accountNo = (String) row.get("account_no");
            c.endpointUrl = (String) row.get("endpoint_url");
            String remarkStr = (String) row.get("remark");
            if (remarkStr != null && !remarkStr.isBlank()) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> m = json.readValue(remarkStr, Map.class);
                    c.serviceType = (String) m.get("service_type");
                    c.shipperName = (String) m.get("shipper_name");
                    c.shipperPhone = (String) m.get("shipper_phone");
                    c.shipperAddress1 = (String) m.get("shipper_address1");
                    c.shipperCity = (String) m.get("shipper_city");
                    c.shipperProvince = (String) m.get("shipper_province");
                    c.shipperCountry = (String) m.get("shipper_country");
                    c.shipperPostcode = (String) m.get("shipper_postcode");
                } catch (Exception ignored) {}
            }
            return c;
        } catch (DataAccessException ex) {
            return null;
        }
    }

    private static long parseLong(Object o, long def) {
        if (o instanceof Number n) return n.longValue();
        if (o instanceof String s) try { return Long.parseLong(s); } catch (Exception ignored) {}
        return def;
    }

    private static String strOr(Object... opts) {
        for (Object o : opts) {
            if (o != null && !o.toString().isBlank()) return o.toString();
        }
        return "";
    }

    static class TokenCache {
        final String token;
        final Instant expiresAt;
        TokenCache(String token, Instant expiresAt) { this.token = token; this.expiresAt = expiresAt; }
    }

    static class UpsCreds {
        String clientId, clientSecret, accountNo, endpointUrl;
        String serviceType;
        String shipperName, shipperPhone;
        String shipperAddress1, shipperCity, shipperProvince, shipperCountry, shipperPostcode;
    }
}

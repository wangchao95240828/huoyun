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
        if (creds.endpointUrl == null || creds.endpointUrl.isBlank()) {
            throw ApiException.badRequest(
                "UPS 渠道[" + ctx.channelCode() + "]缺 endpoint_url 配置。"
                + "请到「API 对接中心 → 渠道账号」补 endpoint_url=https://wwwcie.ups.com/api (sandbox) 或 https://onlinetools.ups.com/api (生产)，"
                + "并填 api_key/api_secret/account_no");
        }
        if (creds.clientId == null || creds.clientId.isBlank()
                || creds.clientSecret == null || creds.clientSecret.isBlank()) {
            throw ApiException.badRequest(
                "UPS 渠道[" + ctx.channelCode() + "]缺 api_key / api_secret，请补全 UPS OAuth 凭证");
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
                String errBody = resp.body();
                System.err.println("[UpsGroundCarrierGateway] ship failed " + resp.statusCode()
                    + " body=" + errBody.substring(0, Math.min(500, errBody.length())));
                throw ApiException.badRequest(localizeUpsError(resp.statusCode(), errBody));
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> respMap = json.readValue(resp.body(), Map.class);
            Issuance issuance = parseUpsResponse(respMap);

            // ACC UPS_New.php L1850-1856: TrackNo 必须以 "1Z" + Account 开头
            // 不一致 → 留 warning 在 raw（不阻断，因为单已在 UPS 落库，阻断会留 orphan）
            verifyTrackingPrefix(issuance, creds);
            return issuance;

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
        shipToAddr.put("PostalCode", strOr(recv.get("postcode"), recv.get("zip"), recv.get("areaCode"), recv.get("postal_code"), ""));
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

        // ════════ Package.Dimensions (cm → in, 让 UPS 自己算体积重 vs 实重) ════════
        // 从 packageList 第一行 (或汇总) 取长宽高 cm
        java.util.List<Map<String, Object>> pkgList =
            ctx.packageList() == null ? java.util.List.of() : ctx.packageList();
        BigDecimal maxL = BigDecimal.ZERO, maxW = BigDecimal.ZERO, maxH = BigDecimal.ZERO;
        for (Map<String, Object> p : pkgList) {
            BigDecimal L = asBigDecimal(p.get("length"));
            BigDecimal W = asBigDecimal(p.get("width"));
            BigDecimal H = asBigDecimal(p.get("height"));
            if (L != null && L.signum() > 0) maxL = maxL.max(L);
            if (W != null && W.signum() > 0) maxW = maxW.max(W);
            if (H != null && H.signum() > 0) maxH = maxH.max(H);
        }
        if (maxL.signum() > 0 && maxW.signum() > 0 && maxH.signum() > 0) {
            // cm → in (1 in = 2.54 cm)
            BigDecimal lIn = maxL.divide(new BigDecimal("2.54"), 1, java.math.RoundingMode.HALF_UP);
            BigDecimal wIn = maxW.divide(new BigDecimal("2.54"), 1, java.math.RoundingMode.HALF_UP);
            BigDecimal hIn = maxH.divide(new BigDecimal("2.54"), 1, java.math.RoundingMode.HALF_UP);
            // UPS Ground 超大件校验:
            //   单边 ≤ 108 in (~274 cm), girth(2W+2H) + length ≤ 165 in (~419 cm)
            BigDecimal longest = lIn.max(wIn).max(hIn);
            if (longest.compareTo(new BigDecimal("108")) > 0) {
                throw com.xqt.saas.common.ApiException.badRequest(
                    "包裹最长边 " + longest + " in > UPS Ground 上限 108 in (~274 cm), 请拆分");
            }
            BigDecimal girthPlusLength = lIn.add(wIn.multiply(new BigDecimal("2"))).add(hIn.multiply(new BigDecimal("2")));
            if (girthPlusLength.compareTo(new BigDecimal("165")) > 0) {
                throw com.xqt.saas.common.ApiException.badRequest(
                    "包裹长 + 2(宽+高) = " + girthPlusLength + " in > UPS Ground 上限 165 in (~419 cm), 请拆分");
            }
            pkg.put("Dimensions", Map.of(
                "UnitOfMeasurement", Map.of("Code", "IN"),
                "Length", lIn.toPlainString(),
                "Width", wIn.toPlainString(),
                "Height", hIn.toPlainString()
            ));
        }

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

    /**
     * UPS API 错误码 → 中文消息映射（ACC UPS_New.php L313-334 同款表）。
     * 解析响应里的 errors[].code，匹配映射就用中文；不匹配就用 UPS 返回的英文 message。
     */
    @SuppressWarnings("unchecked")
    private String localizeUpsError(int httpStatus, String body) {
        // 映射表（ACC L313-334 + 实测追加 120202 / 120100 等）
        Map<String, String> CN = Map.ofEntries(
            Map.entry("120100", "发件人姓名格式错误"),
            Map.entry("120202", "收件人地址行格式错误（Address Line 缺失或非法）"),
            Map.entry("120213", "收件人电话号码少于 10 位"),
            Map.entry("120307", "发件人邮编超长或格式错误"),
            Map.entry("121036", "包裹超出 70kg 重量上限"),
            Map.entry("121210", "所选 UPS 服务在该地区/账号不可用"),
            Map.entry("128039", "申报产品种类过多（UPS 限 ≤ 100）"),
            Map.entry("128097", "进口商地址第 2 行无效"),
            Map.entry("128100", "进口商州/省代码无效"),
            Map.entry("128101", "收件人邮编格式无效"),
            Map.entry("128115", "进口商电话格式无效或缺失"),
            Map.entry("120021", "该订单已提交过，不能重复创建（UPS 拒重复提交）")
        );

        try {
            Map<String, Object> parsed = json.readValue(body, Map.class);
            Map<String, Object> response = (Map<String, Object>) parsed.get("response");
            if (response != null && response.get("errors") instanceof List<?> errs && !errs.isEmpty()) {
                Map<String, Object> first = (Map<String, Object>) errs.get(0);
                String code = (String) first.get("code");
                String msg = (String) first.get("message");
                String cn = CN.get(code);
                if (cn != null) {
                    return "UPS 拒单 [" + code + "]：" + cn + (msg == null ? "" : "（" + msg + "）");
                }
                return "UPS 拒单 [" + code + "]：" + (msg == null ? "未知错误" : msg);
            }
        } catch (Exception ignored) {
            // body 不是 JSON 或结构不对 → 退化
        }
        return "UPS API 返回 " + httpStatus + "：" + body.substring(0, Math.min(200, body.length()));
    }

    /**
     * ACC UPS_New.php L1850-1856 对齐：tracking 必须 1Z+Account 开头。
     * 不一致只 warning（已下单成功，阻断会留 orphan tracking）。
     */
    @SuppressWarnings("unchecked")
    private void verifyTrackingPrefix(Issuance issuance, UpsCreds creds) {
        if (creds.accountNo == null || creds.accountNo.isBlank()) return;
        String expectedPrefix = "1Z" + creds.accountNo.toUpperCase();
        String tracking = issuance.carrierTrackingNo();
        if (tracking == null) return;
        if (!tracking.toUpperCase().startsWith(expectedPrefix)) {
            String warn = "tracking '" + tracking + "' 不匹配预期前缀 '" + expectedPrefix
                + "' — 渠道账号 " + creds.accountNo + " 可能配置错乱";
            System.err.println("[UpsGroundCarrierGateway] WARN " + warn);
            if (issuance.raw() != null) {
                ((Map<String, Object>) issuance.raw()).put("tracking_prefix_warning", warn);
            }
        }
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
                SELECT a.api_key, a.api_secret, a.account_no, a.endpoint_url, a.remark,
                       a.shipper_company, a.shipper_name, a.shipper_phone,
                       a.shipper_address1, a.shipper_city, a.shipper_state,
                       a.shipper_country2, a.shipper_postcode, a.ups_service_type
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

            // 优先级 1: 表里 shipper_* 字段直接读 (ACC 模式 — 渠道账号挂托运人)
            c.shipperName = nonBlank((String) row.get("shipper_company"),
                                      (String) row.get("shipper_name"));
            c.shipperPhone = (String) row.get("shipper_phone");
            c.shipperAddress1 = (String) row.get("shipper_address1");
            c.shipperCity = (String) row.get("shipper_city");
            c.shipperProvince = (String) row.get("shipper_state");
            c.shipperCountry = (String) row.get("shipper_country2");
            c.shipperPostcode = (String) row.get("shipper_postcode");
            c.serviceType = (String) row.get("ups_service_type");

            // 优先级 2 (向后兼容): 老 remark JSON 字段
            String remarkStr = (String) row.get("remark");
            if (remarkStr != null && !remarkStr.isBlank() && remarkStr.startsWith("{")) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> m = json.readValue(remarkStr, Map.class);
                    if (c.serviceType == null) c.serviceType = (String) m.get("service_type");
                    if (c.shipperName == null) c.shipperName = (String) m.get("shipper_name");
                    if (c.shipperPhone == null) c.shipperPhone = (String) m.get("shipper_phone");
                    if (c.shipperAddress1 == null) c.shipperAddress1 = (String) m.get("shipper_address1");
                    if (c.shipperCity == null) c.shipperCity = (String) m.get("shipper_city");
                    if (c.shipperProvince == null) c.shipperProvince = (String) m.get("shipper_province");
                    if (c.shipperCountry == null) c.shipperCountry = (String) m.get("shipper_country");
                    if (c.shipperPostcode == null) c.shipperPostcode = (String) m.get("shipper_postcode");
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

    /** 返第一个非空 string, 全空返 null. */
    private static String nonBlank(String... opts) {
        for (String s : opts) {
            if (s != null && !s.isBlank()) return s;
        }
        return null;
    }

    private static String strOr(Object... opts) {
        for (Object o : opts) {
            if (o != null && !o.toString().isBlank()) return o.toString();
        }
        return "";
    }

    private static BigDecimal asBigDecimal(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return new BigDecimal(n.toString());
        if (o instanceof String s) try { return new BigDecimal(s.trim()); } catch (Exception ignored) {}
        return null;
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

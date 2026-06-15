package com.xqt.saas.acc;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xqt.saas.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 自动轮询承运商物流接口。
 *
 *   POST /api/acc/tracking/poll-ups?trackingNo=1Z...
 *     Header: X-Ingest-Token: ...
 *     用 acc_channel_accounts 里已配置的 UPS OAuth 凭证调 UPS Track API,
 *     直接落 tracking_events (复用 ingest controller 的 normalize 逻辑)。
 *
 *   POST /api/acc/tracking/poll-ups-batch
 *     body: { "trackingNos": ["1Z...", ...] }
 *     批量轮询，给 runner.py 用。
 */
@RestController
@RequestMapping("/api/acc/tracking")
public class AccTrackingPollController {
    private static final Logger LOGGER = LoggerFactory.getLogger(AccTrackingPollController.class);

    private final JdbcTemplate jdbc;
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json = new ObjectMapper();

    @Value("${xqt.tracking.ingest-token:}")
    private String ingestToken;

    @Value("${xqt.tracking.tenant-id:2bda8c16-7b19-4ce6-ab71-9584f5a140ed}")
    private String defaultTenantId;

    public AccTrackingPollController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostMapping("/poll-ups")
    public Map<String, Object> pollUps(
        @RequestHeader(value = "X-Ingest-Token", required = false) String token,
        @RequestParam String trackingNo
    ) {
        requireToken(token);
        return pollOne(trackingNo);
    }

    @PostMapping("/poll-ups-batch")
    public Map<String, Object> pollUpsBatch(
        @RequestHeader(value = "X-Ingest-Token", required = false) String token,
        @org.springframework.web.bind.annotation.RequestBody Map<String, Object> body
    ) {
        requireToken(token);
        Object raw = body.get("trackingNos");
        if (!(raw instanceof List<?>)) throw ApiException.badRequest("trackingNos 必须为数组");
        List<?> list = (List<?>) raw;
        int ok = 0, fail = 0;
        java.util.List<Map<String, Object>> details = new java.util.ArrayList<>();
        for (Object o : list) {
            String tn = o == null ? null : o.toString();
            if (tn == null || tn.isBlank()) continue;
            try {
                Map<String, Object> r = pollOne(tn);
                ok++;
                details.add(r);
            } catch (Exception ex) {
                fail++;
                Map<String, Object> errRow = new LinkedHashMap<>();
                errRow.put("trackingNo", tn);
                errRow.put("error", ex.getMessage());
                details.add(errRow);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", ok);
        out.put("fail", fail);
        out.put("total", list.size());
        out.put("details", details);
        return out;
    }

    private void requireToken(String token) {
        if (ingestToken != null && !ingestToken.isBlank()) {
            if (token == null || !token.equals(ingestToken)) {
                throw ApiException.unauthorized("invalid X-Ingest-Token");
            }
        }
    }

    /** 单 trackingNo 轮询 UPS Track API → 落 tracking_events。 */
    private Map<String, Object> pollOne(String trackingNo) {
        // 1) 找 UPS 凭证（任一 active UPS 渠道账号）
        Map<String, Object> creds;
        try {
            creds = jdbc.queryForMap("""
                SELECT a.api_key, a.api_secret, a.account_no, a.endpoint_url
                  FROM acc_channel_accounts a
                 WHERE a.tenant_id = ?::uuid
                   AND a.is_active = true
                   AND a.provider_code = 'UPS'
                   AND a.api_key IS NOT NULL
                   AND a.api_secret IS NOT NULL
                   AND a.endpoint_url IS NOT NULL
                 ORDER BY a.created_at DESC LIMIT 1
                """, defaultTenantId);
        } catch (DataAccessException ex) {
            throw ApiException.badRequest("没找到可用的 UPS 渠道账号(acc_channel_accounts.provider_code='UPS')");
        }
        String clientId = (String) creds.get("api_key");
        String clientSecret = (String) creds.get("api_secret");
        String accountNo = (String) creds.get("account_no");
        String endpointUrl = (String) creds.get("endpoint_url");

        try {
            // 2) OAuth client_credentials 拿 access token
            String oauthUrl = endpointUrl.replace("/api", "/security/v1/oauth/token");
            String basic = Base64.getEncoder().encodeToString(
                (clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
            HttpRequest tokenReq = HttpRequest.newBuilder()
                .uri(URI.create(oauthUrl))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Basic " + basic)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("x-merchant-id", accountNo == null ? "" : accountNo)
                .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials"))
                .build();
            HttpResponse<String> tokenResp = http.send(tokenReq, HttpResponse.BodyHandlers.ofString());
            if (tokenResp.statusCode() != 200) {
                throw new RuntimeException("UPS OAuth " + tokenResp.statusCode() + ": "
                    + tokenResp.body().substring(0, Math.min(200, tokenResp.body().length())));
            }
            JsonNode tokenJ = json.readTree(tokenResp.body());
            String accessToken = tokenJ.path("access_token").asText();

            // 3) Track API: GET /api/track/v1/details/{trackingNo}
            String trackUrl = endpointUrl.replaceAll("/$", "") + "/track/v1/details/" + trackingNo;
            HttpRequest trackReq = HttpRequest.newBuilder()
                .uri(URI.create(trackUrl + "?locale=en_US&returnSignature=false&returnMilestones=false"))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + accessToken)
                .header("transId", java.util.UUID.randomUUID().toString())
                .header("transactionSrc", "XQT-ACC")
                .GET()
                .build();
            HttpResponse<String> trackResp = http.send(trackReq, HttpResponse.BodyHandlers.ofString());
            if (trackResp.statusCode() != 200) {
                throw new RuntimeException("UPS Track " + trackResp.statusCode() + ": "
                    + trackResp.body().substring(0, Math.min(300, trackResp.body().length())));
            }

            // 4) 解析 UPS 响应 → 落 tracking_events
            JsonNode root = json.readTree(trackResp.body());
            JsonNode shipments = root.path("trackResponse").path("shipment");
            int inserted = 0, skipped = 0;
            String shipmentId = findShipmentIdByTn(trackingNo);
            for (JsonNode shipment : shipments) {
                for (JsonNode pkg : shipment.path("package")) {
                    for (JsonNode activity : pkg.path("activity")) {
                        // UPS 时间格式: date=20260603, time=123848
                        String date = activity.path("date").asText("");
                        String time = activity.path("time").asText("");
                        if (date.length() != 8 || time.length() != 6) continue;
                        String iso = String.format("%s-%s-%sT%s:%s:%sZ",
                            date.substring(0,4), date.substring(4,6), date.substring(6,8),
                            time.substring(0,2), time.substring(2,4), time.substring(4,6));
                        String status = activity.path("status").path("description").asText("");
                        if (status.isBlank()) status = activity.path("status").path("type").asText("UNKNOWN");
                        JsonNode loc = activity.path("location").path("address");
                        String location = String.join(", ",
                            stripBlank(loc.path("city").asText()),
                            stripBlank(loc.path("stateProvince").asText()),
                            stripBlank(loc.path("country").asText())
                        ).replaceAll(",\\s*,", ",").replaceAll("(^,\\s*)|(,\\s*$)", "");
                        boolean delivered = "D".equalsIgnoreCase(activity.path("status").path("type").asText(""));

                        // 幂等
                        Integer dup = jdbc.queryForObject(
                            "SELECT count(*) FROM tracking_events"
                            + " WHERE tenant_id=?::uuid AND tracking_no=? AND event_time=?::timestamptz AND raw_status=?",
                            Integer.class, defaultTenantId, trackingNo, iso, status);
                        if (dup != null && dup > 0) { skipped++; continue; }
                        try {
                            jdbc.update("""
                                INSERT INTO tracking_events
                                    (tenant_id, shipment_id, tracking_no, event_time,
                                     raw_status, normalized_status, location, source, raw_payload)
                                VALUES (?::uuid, ?::uuid, ?, ?::timestamptz,
                                        ?, ?::tracking_status, ?, 'CARRIER_API', '{}'::jsonb)
                                """, defaultTenantId, shipmentId, trackingNo, iso,
                                     status, AccTrackingIngestController_normalize(status, delivered),
                                     location.isBlank() ? null : location);
                            inserted++;
                            if (delivered && shipmentId != null) {
                                jdbc.update("""
                                    UPDATE shipments SET status='DELIVERED'::shipment_status,
                                                          delivered_at = COALESCE(delivered_at, ?::timestamptz)
                                     WHERE id=?::uuid AND status<>'CLOSED'
                                    """, iso, shipmentId);
                            }
                        } catch (DataAccessException ex) {
                            LOGGER.warn("ups ingest event failed tn={} status={} err={}",
                                trackingNo, status, ex.getMostSpecificCause().getMessage());
                            skipped++;
                        }
                    }
                }
            }

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("trackingNo", trackingNo);
            out.put("carrier", "UPS");
            out.put("shipmentId", shipmentId);
            out.put("inserted", inserted);
            out.put("skipped", skipped);
            return out;
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw ApiException.badRequest("UPS 轮询失败: " + ex.getMessage());
        }
    }

    private String findShipmentIdByTn(String tn) {
        try {
            return jdbc.queryForObject("""
                SELECT c.shipment_id::text FROM cartons c
                 WHERE c.tenant_id = ?::uuid
                   AND (c.tracking_no = ? OR c.carrier_master_tracking_no = ?)
                 ORDER BY c.carton_no LIMIT 1
                """, String.class, defaultTenantId, tn, tn);
        } catch (DataAccessException ex) { return null; }
    }

    private static String stripBlank(String s) { return s == null ? "" : s.trim(); }

    /** 复刻 IngestController 的 normalize 逻辑 — 避免循环依赖。 */
    static String AccTrackingIngestController_normalize(String rawStatus, boolean delivered) {
        if (delivered) return "DELIVERED";
        if (rawStatus == null) return "IN_TRANSIT";
        String s = rawStatus.toLowerCase();
        if (s.contains("delivered")) return "DELIVERED";
        if (s.contains("out for delivery")) return "OUT_FOR_DELIVERY";
        if (s.contains("exception") || s.contains("delay")) return "EXCEPTION";
        if (s.contains("returned")) return "RETURNED";
        return "IN_TRANSIT";
    }
}

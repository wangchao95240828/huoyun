package com.xqt.saas.acc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 外部爬虫（FedEx / UPS / DHL 等）推送物流轨迹的接收端点。
 *
 *   POST /api/acc/tracking/ingest
 *     headers: X-Ingest-Token: <env XQT_TRACKING_INGEST_TOKEN>
 *     body: {
 *       "carrier": "FEDEX",                 // 必填
 *       "tracking_number": "870076729148",  // 必填
 *       "status": "Delivered",
 *       "service": "FedEx Ground",
 *       "ship_datetime": "2026-05-28T00:00:00Z",
 *       "delivery_datetime": "2026-06-03T12:38:48-04:00",
 *       "signed_by": "CCINDY",
 *       "weight_kg": 18.87,
 *       "scan_history": [
 *         { "datetime": "...", "status": "Picked up", "location": "WALNUT, CA", "delivered": false },
 *         ...
 *       ]
 *     }
 *
 *  落 tracking_events + 更新 shipment.delivered_at（如已签收）。
 *  幂等：(tracking_no, event_time, raw_status) 视为 unique，重复推不会落多条。
 */
@RestController
@RequestMapping("/api/acc/tracking")
public class AccTrackingIngestController {
    private static final Logger LOGGER = LoggerFactory.getLogger(AccTrackingIngestController.class);

    private final JdbcTemplate jdbc;

    /** 服务到服务 token；爬虫脚本 .env 配同一个。空字符串 = 鉴权关闭（仅 dev）。 */
    @Value("${xqt.tracking.ingest-token:}")
    private String ingestToken;

    /** 默认 tenant（单租户部署）。 */
    @Value("${xqt.tracking.tenant-id:2bda8c16-7b19-4ce6-ab71-9584f5a140ed}")
    private String defaultTenantId;

    public AccTrackingIngestController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostMapping("/ingest")
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> ingest(
        @RequestHeader(value = "X-Ingest-Token", required = false) String token,
        @RequestBody Map<String, Object> body
    ) {
        if (ingestToken != null && !ingestToken.isBlank()) {
            if (token == null || !token.equals(ingestToken)) {
                throw ApiException.unauthorized("invalid X-Ingest-Token");
            }
        }

        String trackingNo = strOr(body.get("tracking_number"), body.get("trackingNo"));
        if (trackingNo == null || trackingNo.isBlank()) {
            throw ApiException.badRequest("tracking_number 必填");
        }
        String carrier = strOrDefault(body.get("carrier"), "FEDEX").toUpperCase();
        Object scanHistory = body.get("scan_history");
        if (!(scanHistory instanceof List<?>)) {
            throw ApiException.badRequest("scan_history 必须为数组");
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> events = (List<Map<String, Object>>) scanHistory;

        // 找该 tracking_no 关联的 shipment（cartons.tracking_no / carrier_master_tracking_no）
        String shipmentId = null;
        try {
            shipmentId = jdbc.queryForObject("""
                SELECT c.shipment_id::text FROM cartons c
                 WHERE c.tenant_id = ?::uuid
                   AND (c.tracking_no = ? OR c.carrier_master_tracking_no = ?)
                 ORDER BY c.created_at DESC LIMIT 1
                """, String.class, defaultTenantId, trackingNo, trackingNo);
        } catch (DataAccessException ignored) {
            // 找不到也可写 tracking_events，shipment_id=NULL 留待后续匹配
        }

        int inserted = 0;
        int skipped = 0;
        for (Map<String, Object> e : events) {
            String dt = strOr(e.get("datetime"), e.get("event_time"));
            String rawStatus = strOr(e.get("status"), e.get("raw_status"));
            String location = strOr(e.get("location"), null);
            Boolean delivered = Boolean.TRUE.equals(e.get("delivered"));
            if (dt == null || rawStatus == null) continue;
            String normalized = normalize(rawStatus, delivered);
            // 幂等：同 tracking_no + event_time + raw_status 不重复
            Integer dup = jdbc.queryForObject(
                "SELECT count(*) FROM tracking_events"
                + " WHERE tenant_id=?::uuid AND tracking_no=? AND event_time=?::timestamptz AND raw_status=?",
                Integer.class, defaultTenantId, trackingNo, dt, rawStatus);
            if (dup != null && dup > 0) { skipped++; continue; }
            try {
                jdbc.update("""
                    INSERT INTO tracking_events
                        (tenant_id, shipment_id, tracking_no, event_time,
                         raw_status, normalized_status, location, source, raw_payload)
                    VALUES
                        (?::uuid, ?::uuid, ?, ?::timestamptz,
                         ?, ?::tracking_status, ?, 'CARRIER_API', '{}'::jsonb)
                    """, defaultTenantId, shipmentId, trackingNo, dt,
                         rawStatus, normalized, location);
                inserted++;
            } catch (DataAccessException ex) {
                LOGGER.warn("ingest event failed tn={} status={} err={}",
                    trackingNo, rawStatus, ex.getMostSpecificCause().getMessage());
                skipped++;
            }
        }

        // 整单 status 同步：若有任一 DELIVERED 事件，更新 shipment.delivered_at + status
        String deliveryDt = strOr(body.get("delivery_datetime"), null);
        if (shipmentId != null && deliveryDt != null) {
            jdbc.update("""
                UPDATE shipments
                   SET status = 'DELIVERED'::shipment_status,
                       delivered_at = COALESCE(delivered_at, ?::timestamptz)
                 WHERE id = ?::uuid AND status <> 'CLOSED'
                """, deliveryDt, shipmentId);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("trackingNo", trackingNo);
        out.put("carrier", carrier);
        out.put("shipmentId", shipmentId);
        out.put("inserted", inserted);
        out.put("skipped", skipped);
        out.put("totalEvents", events.size());
        return out;
    }

    /** FedEx 原始事件文本 → tracking_status enum 标准化映射。 */
    private static String normalize(String rawStatus, boolean delivered) {
        if (delivered) return "DELIVERED";
        if (rawStatus == null) return "IN_TRANSIT";
        String s = rawStatus.toLowerCase();
        if (s.contains("delivered")) return "DELIVERED";
        if (s.contains("on fedex vehicle") || s.contains("out for delivery")
            || s.contains("with delivery courier")) return "OUT_FOR_DELIVERY";
        if (s.contains("exception") || s.contains("delay") || s.contains("delivery exception")
            || s.contains("unable to deliver")) return "EXCEPTION";
        if (s.contains("returned") || s.contains("return to sender")) return "RETURNED";
        if (s.contains("picked up") || s.contains("arrived") || s.contains("departed")
            || s.contains("left fedex") || s.contains("in transit") || s.contains("at local")
            || s.contains("at fedex")) return "IN_TRANSIT";
        return "IN_TRANSIT";
    }

    private static String strOr(Object a, Object b) {
        if (a != null && !a.toString().isBlank()) return a.toString();
        if (b != null && !b.toString().isBlank()) return b.toString();
        return null;
    }
    private static String strOrDefault(Object a, String def) {
        String v = a == null ? null : a.toString();
        return v == null || v.isBlank() ? def : v;
    }
}

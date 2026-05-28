package com.xqt.saas.tracking;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 多源轨迹聚合（对应 ACC Express_Process / Transit_Process / Stowage_Process /
 * Online_TrackNo / Express_TrackNo 五源统一时间线）。
 *
 * 新系统对应来源：
 *   1. tracking_events   — Express_Process + 子单号事件（carrier_api 来源）
 *   2. acc_stowage_steps — Stowage_Process（通过 stowage_id → cartons → shipment）
 *   3. acc_dispatches    — 上门揽收事件（按 customer + 时间窗弱关联）
 *   4. acc_transits      — 转运事件（暂按 transit_no 反查 cartons）
 *
 * 客户视角（toPublic）剥离 operator 和 internal remark；内部视角全字段返回。
 */
@Service
public class TrackingAggregator {
    private final JdbcTemplate jdbc;

    public TrackingAggregator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 按 shipment id 聚合该运单全部源的事件。 */
    public List<TrackingEvent> aggregateByShipment(String tenantId, String shipmentId) {
        List<TrackingEvent> events = new ArrayList<>();
        events.addAll(fromTrackingEvents(tenantId, "shipment_id = ?::uuid", shipmentId));
        events.addAll(fromStowageSteps(tenantId, shipmentId));
        events.addAll(fromDispatches(tenantId, shipmentId));
        events.addAll(fromTransits(tenantId, shipmentId));
        events.sort(Comparator.comparing(TrackingEvent::eventTime,
            Comparator.nullsLast(Comparator.naturalOrder())));
        return events;
    }

    /** 按 tracking_no 聚合（客户端用，按子单号查）。 */
    public List<TrackingEvent> aggregateByTrackingNo(String tenantId, String trackingNo) {
        List<TrackingEvent> events = new ArrayList<>();
        events.addAll(fromTrackingEvents(tenantId, "tracking_no = ?", trackingNo));
        // 通过 tracking_no → cartons → shipment_id 再扩到其它源
        String shipmentId = resolveShipmentByTracking(tenantId, trackingNo);
        if (shipmentId != null) {
            events.addAll(fromStowageSteps(tenantId, shipmentId));
            events.addAll(fromDispatches(tenantId, shipmentId));
            events.addAll(fromTransits(tenantId, shipmentId));
        }
        events.sort(Comparator.comparing(TrackingEvent::eventTime,
            Comparator.nullsLast(Comparator.naturalOrder())));
        return events;
    }

    /** 客户视图：剥敏感字段。 */
    public List<TrackingEvent> toPublic(List<TrackingEvent> events) {
        return events.stream().map(TrackingEvent::toPublic).toList();
    }

    // ───────────────── 各源查询 ─────────────────

    private List<TrackingEvent> fromTrackingEvents(String tenantId, String filter, Object filterVal) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id::text AS id, event_time, raw_status, normalized_status::text AS status, "
              + " location, source::text AS src, raw_payload::text AS payload "
              + "FROM tracking_events "
              + "WHERE tenant_id = ?::uuid AND " + filter,
                tenantId, filterVal);
            List<TrackingEvent> out = new ArrayList<>();
            for (Map<String, Object> r : rows) {
                out.add(new TrackingEvent(
                    "tracking_events",
                    (String) r.get("id"),
                    toOdt(r.get("event_time")),
                    (String) r.get("status"),
                    (String) r.get("location"),
                    (String) r.get("raw_status"),
                    null,
                    null,
                    TrackingEvent.Visibility.INTERNAL  // payload 仅内部，但 message 公开
                ));
            }
            return out;
        } catch (DataAccessException ex) {
            return List.of();
        }
    }

    private List<TrackingEvent> fromStowageSteps(String tenantId, String shipmentId) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT DISTINCT s.id::text AS id, s.created_at, s.name, s.status, "
              + " s.location, s.remark, s.audit_name "
              + "FROM acc_stowage_steps s "
              + "JOIN cartons c ON c.stowage_id = s.stowage_id "
              + "WHERE s.tenant_id = ?::uuid AND c.shipment_id = ?::uuid "
              + "ORDER BY s.created_at",
                tenantId, shipmentId);
            List<TrackingEvent> out = new ArrayList<>();
            for (Map<String, Object> r : rows) {
                out.add(new TrackingEvent(
                    "stowage_steps",
                    (String) r.get("id"),
                    toOdt(r.get("created_at")),
                    statusOf(r.get("status")),
                    (String) r.get("location"),
                    "配载: " + r.get("name"),
                    (String) r.get("audit_name"),
                    (String) r.get("remark"),
                    TrackingEvent.Visibility.INTERNAL
                ));
            }
            return out;
        } catch (DataAccessException ex) {
            return List.of();
        }
    }

    private List<TrackingEvent> fromDispatches(String tenantId, String shipmentId) {
        // 上门揽收：通过 shipment.customer_id + dispatch.customer_id 关联，
        // 取该客户 dispatch 中 pick_date 在 shipment 创建日 ±3 天的（弱关联避免误关联其它单）。
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT d.id::text AS id, d.created_at, d.status, d.pick_address, "
              + " d.contact_name, d.remark "
              + "FROM acc_dispatches d "
              + "JOIN shipments s ON s.customer_id = d.customer_id "
              + "WHERE d.tenant_id = ?::uuid AND s.id = ?::uuid "
              + "  AND d.pick_date BETWEEN (s.created_at::date - 3) AND (s.created_at::date + 3) "
              + "ORDER BY d.created_at",
                tenantId, shipmentId);
            List<TrackingEvent> out = new ArrayList<>();
            for (Map<String, Object> r : rows) {
                out.add(new TrackingEvent(
                    "dispatches",
                    (String) r.get("id"),
                    toOdt(r.get("created_at")),
                    statusOf(r.get("status")),
                    (String) r.get("pick_address"),
                    "上门揽收",
                    (String) r.get("contact_name"),
                    (String) r.get("remark"),
                    TrackingEvent.Visibility.INTERNAL
                ));
            }
            return out;
        } catch (DataAccessException ex) {
            return List.of();
        }
    }

    private List<TrackingEvent> fromTransits(String tenantId, String shipmentId) {
        // 转运：当前 acc_transits 无直接 shipment 关联键。简化策略：
        // 通过 cartons.tracking_no 模糊匹配 transits.transit_no（旧 ACC Transit_Process 用单号关联）。
        // 暂时返回空列表，等业务层补关联键后再启用。
        // 接入方法预留，便于未来 join 表落地。
        try {
            return List.of();
        } catch (DataAccessException ex) {
            return List.of();
        }
    }

    private String resolveShipmentByTracking(String tenantId, String trackingNo) {
        try {
            return jdbc.queryForObject(
                "SELECT shipment_id::text FROM cartons WHERE tenant_id = ?::uuid AND tracking_no = ? LIMIT 1",
                String.class, tenantId, trackingNo);
        } catch (DataAccessException ex) {
            return null;
        }
    }

    private OffsetDateTime toOdt(Object v) {
        if (v == null) return null;
        if (v instanceof OffsetDateTime odt) return odt;
        if (v instanceof java.sql.Timestamp ts) return ts.toInstant().atOffset(java.time.ZoneOffset.UTC);
        return null;
    }

    private String statusOf(Object v) {
        return v == null ? null : v.toString();
    }
}

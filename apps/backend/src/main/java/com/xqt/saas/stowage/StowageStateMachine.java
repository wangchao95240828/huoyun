package com.xqt.saas.stowage;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 任务 S5：配载状态机自动联动。
 *
 * 对应 ACC `Stowage_Process` 状态推进：
 *   - 装箱单 CONFIRMED  → tracking_events.normalized_status=IN_TRANSIT + shipments.status=IN_TRANSIT
 *   - 转运    IN_TRANSIT → tracking_events.normalized_status=IN_TRANSIT（按 to_port 推进相关 shipments）
 *   - 派送    OUT_FOR_DELIVERY → tracking_events.normalized_status=OUT_FOR_DELIVERY
 *   - 派送    DONE/DELIVERED → tracking_events.normalized_status=DELIVERED
 *                              + shipments.status=DELIVERED
 *                              + publish ShipmentDeliveredEvent（触发利润结算）
 *
 * 设计要点：
 *   1. shipment 关联策略：
 *      - stowages → 通过 cartons.stowage_id 反查 shipments（M:N 投影 distinct）
 *      - dispatches → 通过 dispatch.customer_id × pick_date ±3 天匹配 shipments
 *      - transits → 通过 acc_transit_items 关联 stowage_id 再级联 shipments
 *   2. tracking_events 写 source='MANUAL'（业务侧手工状态推进），raw_status 用 ACC 节点名
 *   3. 所有 DB 异常 catch 后吞掉，业务方法不抛错（业务连续性优于 100% 一致性）
 */
@Service
public class StowageStateMachine {
    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher events;

    public StowageStateMachine(JdbcTemplate jdbc, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.events = events;
    }

    /**
     * 装箱单确认 → 关联 shipments 推进到 IN_TRANSIT + 写 tracking_events。
     */
    @Transactional
    public int onStowageConfirmed(String tenantId, String stowageId) {
        try {
            List<Map<String, Object>> shipments = jdbc.queryForList("""
                SELECT DISTINCT s.id::text AS shipment_id, s.shipment_no, s.tenant_id::text AS tenant_id
                FROM cartons c
                JOIN shipments s ON s.id = c.shipment_id
                WHERE c.stowage_id = ?::uuid
                """, stowageId);
            for (Map<String, Object> sh : shipments) {
                writeTrackingEvent(tenantId, (String) sh.get("shipment_id"),
                    (String) sh.get("shipment_no"), "STOWAGE_CONFIRMED", "IN_TRANSIT", null);
                advanceShipmentStatus((String) sh.get("shipment_id"), "IN_TRANSIT",
                    new String[]{"DRAFT", "ORDERED", "IN_WAREHOUSE", "MEASURED", "BOOKED"});
            }
            return shipments.size();
        } catch (DataAccessException ex) {
            return 0;
        }
    }

    /**
     * 转运起运 → 通过 acc_transit_items 关联 stowage_id → cartons → shipments，
     *   每票 fanout 一条 normalized_status=IN_TRANSIT tracking 事件。
     * 不强制改 shipments.status（一般 stowage 阶段已推进到 IN_TRANSIT）。
     */
    @Transactional
    public int onTransitInTransit(String tenantId, String transitId) {
        try {
            List<Map<String, Object>> shipments = jdbc.queryForList("""
                SELECT DISTINCT s.id::text AS shipment_id, s.shipment_no
                FROM acc_transit_items ti
                JOIN cartons c ON c.stowage_id = ti.stowage_id
                JOIN shipments s ON s.id = c.shipment_id
                WHERE ti.transit_id = ?::uuid
                """, transitId);
            for (Map<String, Object> sh : shipments) {
                writeTrackingEvent(tenantId, (String) sh.get("shipment_id"),
                    (String) sh.get("shipment_no"), "TRANSIT_DEPARTED", "IN_TRANSIT", null);
            }
            return shipments.size();
        } catch (DataAccessException ex) {
            return 0;
        }
    }

    /**
     * 派送出库（OUT_FOR_DELIVERY 业务态）→ 仅写 tracking_events。
     */
    @Transactional
    public int onDispatchOutForDelivery(String tenantId, String dispatchId) {
        try {
            List<Map<String, Object>> shipments = findShipmentsForDispatch(dispatchId);
            for (Map<String, Object> sh : shipments) {
                writeTrackingEvent(tenantId, (String) sh.get("shipment_id"),
                    (String) sh.get("shipment_no"), "DISPATCH_OUT", "OUT_FOR_DELIVERY", null);
            }
            return shipments.size();
        } catch (DataAccessException ex) {
            return 0;
        }
    }

    /**
     * 派送完成（dispatch DONE = 妥投）→ tracking_events DELIVERED
     *   + shipments.status=DELIVERED + publish ShipmentDeliveredEvent。
     */
    @Transactional
    public int onDispatchDelivered(String tenantId, String dispatchId) {
        try {
            List<Map<String, Object>> shipments = findShipmentsForDispatch(dispatchId);
            OffsetDateTime now = OffsetDateTime.now();
            for (Map<String, Object> sh : shipments) {
                String shipmentId = (String) sh.get("shipment_id");
                String shipmentNo = (String) sh.get("shipment_no");
                writeTrackingEvent(tenantId, shipmentId, shipmentNo, "DELIVERED", "DELIVERED", null);
                advanceShipmentStatus(shipmentId, "DELIVERED",
                    new String[]{"IN_TRANSIT", "OUT_FOR_DELIVERY", "BOOKED"});
                events.publishEvent(new ShipmentDeliveredEvent(tenantId, shipmentId, shipmentNo, now));
            }
            return shipments.size();
        } catch (DataAccessException ex) {
            return 0;
        }
    }

    // ─── helpers ───

    private List<Map<String, Object>> findShipmentsForDispatch(String dispatchId) {
        // dispatch.customer_id × pick_date ±3 天 → shipments
        return jdbc.queryForList("""
            SELECT DISTINCT s.id::text AS shipment_id, s.shipment_no
            FROM acc_dispatches d
            JOIN shipments s ON s.customer_id = d.customer_id
                AND s.tenant_id = d.tenant_id
                AND s.ordered_at::date BETWEEN d.pick_date - 3 AND d.pick_date + 3
            WHERE d.id = ?::uuid
            """, dispatchId);
    }

    private void writeTrackingEvent(String tenantId, String shipmentId, String trackingNoFallback,
                                     String rawStatus, String normalizedStatus, String location) {
        jdbc.update("""
            INSERT INTO tracking_events (
              tenant_id, shipment_id, tracking_no, event_time,
              raw_status, normalized_status, location, source, raw_payload
            ) VALUES (
              ?::uuid, ?::uuid, ?, now(),
              ?, ?::tracking_status, ?, 'MANUAL', '{"trigger":"stowage_state_machine"}'::jsonb
            )
            """, tenantId, shipmentId, trackingNoFallback,
            rawStatus, normalizedStatus, location);
    }

    private void advanceShipmentStatus(String shipmentId, String newStatus, String[] fromStatuses) {
        // 仅当当前 status 在 fromStatuses 列表里才推进，避免回退；fromStatuses 是写死字典值非用户输入。
        String fromIn = "'" + String.join("','", fromStatuses) + "'";
        jdbc.update(
            "UPDATE shipments SET status = ?::shipment_status "
            + "WHERE id = ?::uuid AND status::text IN (" + fromIn + ")",
            newStatus, shipmentId);
    }
}

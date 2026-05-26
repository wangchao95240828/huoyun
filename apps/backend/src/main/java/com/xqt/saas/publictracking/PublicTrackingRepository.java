package com.xqt.saas.publictracking;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 公开 Track 复刻 ACC api/Track.php：旧系统单租户，无 customer 过滤、无 tenant 过滤。
 * 新平台多租户，这里用 service_role 跨租户查 shipments/cartons + tracking_events。
 * 单号即凭证：能猜到的攻击面 = 旧 ACC 时已经存在的攻击面，不引入新风险。
 */
@Repository
public class PublicTrackingRepository {
    private final JdbcTemplate jdbc;

    public PublicTrackingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> findShipmentByTrackingOrNo(String trackingOrNo) {
        enableServiceRole();
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT
              s.id::text       AS shipment_id,
              s.tenant_id::text AS tenant_id,
              s.shipment_no,
              s.customer_ref,
              s.status::text   AS status,
              s.destination_country,
              (
                SELECT c.tracking_no FROM cartons c
                WHERE c.shipment_id = s.id AND c.tracking_no IS NOT NULL
                ORDER BY c.carton_no LIMIT 1
              ) AS first_tracking_no
            FROM shipments s
            WHERE s.shipment_no = ?
               OR s.customer_ref = ?
               OR EXISTS (
                 SELECT 1 FROM cartons c
                 WHERE c.shipment_id = s.id AND c.tracking_no = ?
               )
            LIMIT 1
            """, trackingOrNo, trackingOrNo, trackingOrNo);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> findTrackingEvents(String shipmentId) {
        enableServiceRole();
        return jdbc.queryForList("""
            SELECT
              event_time,
              raw_status,
              normalized_status::text  AS normalized_status,
              location,
              source::text             AS source,
              tracking_no
            FROM tracking_events
            WHERE shipment_id = ?::uuid
            ORDER BY event_time
            """, shipmentId);
    }

    private void enableServiceRole() {
        jdbc.queryForObject("select set_config('app.service_role', 'true', true)", String.class);
    }
}

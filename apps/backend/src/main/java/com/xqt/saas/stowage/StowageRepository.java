package com.xqt.saas.stowage;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class StowageRepository {
    private final JdbcTemplate jdbc;

    public StowageRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 对应 ACC: `Select Id from Stowage_Category where Name=?`. */
    public String findCategoryIdByName(String tenantId, String name) {
        List<String> rows = jdbc.queryForList("""
            SELECT id::text FROM stowage_categories
            WHERE tenant_id = ?::uuid AND name = ? AND active = true
            LIMIT 1
            """, String.class, tenantId, name);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 对应 `Select * from Stowage where No=?`：返回主单 + 客户归属。 */
    public Map<String, Object> findStowageByNo(String tenantId, String stowageNo) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT id::text AS id, customer_id::text AS customer_id,
                   stowage_no, the_date, stowage_type, category_id::text AS category_id,
                   count_value, piece_value, quantity_value,
                   weight_value, volume_value, declared_value, tax_amount,
                   departure_time, arrival_time, remark, add_name, add_time, modify_time
            FROM stowages
            WHERE tenant_id = ?::uuid AND stowage_no = ?
            LIMIT 1
            """, tenantId, stowageNo);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 对应 ACC: `Select a.Customer,b.Id,b.Stowage,b.TrackNo from Online_Package a inner join Online_Package_Item b ...`
     * 新模型：用 cartons.tracking_no + shipments.customer_id + cartons.stowage_id 等价。
     */
    public List<Map<String, Object>> findCartonsByTrackingNos(String tenantId, List<String> trackingNos) {
        if (trackingNos == null || trackingNos.isEmpty()) {
            return List.of();
        }
        String[] arr = trackingNos.toArray(new String[0]);
        return jdbc.queryForList("""
            SELECT
              c.id::text         AS carton_id,
              c.tracking_no,
              c.stowage_id::text AS stowage_id,
              s.customer_id::text AS customer_id,
              s.shipment_no
            FROM cartons c
            JOIN shipments s ON s.id = c.shipment_id
            WHERE c.tenant_id = ?::uuid
              AND c.tracking_no = ANY (?)
            """, tenantId, arr);
    }

    /** 对应 ACC oldItems 查询：当前 stowage 关联但不在新 items 列表中的 cartons。 */
    public List<String> findCartonsToDetach(String tenantId, String stowageId, List<String> keepCartonIds) {
        if (stowageId == null) return List.of();
        if (keepCartonIds == null || keepCartonIds.isEmpty()) {
            return jdbc.queryForList("""
                SELECT id::text FROM cartons
                WHERE tenant_id = ?::uuid AND stowage_id = ?::uuid
                """, String.class, tenantId, stowageId);
        }
        String[] arr = keepCartonIds.toArray(new String[0]);
        return jdbc.queryForList("""
            SELECT id::text FROM cartons
            WHERE tenant_id = ?::uuid
              AND stowage_id = ?::uuid
              AND NOT (id = ANY (?::uuid[]))
            """, String.class, tenantId, stowageId, arr);
    }

    @Transactional(rollbackFor = Exception.class)
    public String insertStowage(String tenantId, String customerId, String stowageNo,
                                String theDate, int type, String categoryId,
                                Integer count, Integer piece, Integer quantity,
                                BigDecimal weight, BigDecimal volume,
                                BigDecimal declaredValue, BigDecimal taxAmount,
                                String departureTime, String departurePortId,
                                String arrivalTime, String arrivalPortId,
                                String remark, String addName,
                                String addTime, String modifyTime) {
        return jdbc.queryForObject("""
            INSERT INTO stowages (
              tenant_id, customer_id, stowage_no, the_date,
              stowage_type, category_id,
              count_value, piece_value, quantity_value,
              weight_value, volume_value, declared_value, tax_amount,
              departure_time, departure_port_id,
              arrival_time, arrival_port_id,
              remark, add_name, add_time, modify_time
            ) VALUES (
              ?::uuid, ?::uuid, ?, ?::date,
              ?, ?::uuid,
              ?, ?, ?,
              ?, ?, ?, ?,
              ?::timestamptz, ?::uuid,
              ?::timestamptz, ?::uuid,
              ?, ?, ?::timestamptz, ?::timestamptz
            )
            RETURNING id::text
            """, String.class,
            tenantId, customerId, stowageNo, theDate,
            type, categoryId,
            count, piece, quantity,
            weight, volume, declaredValue, taxAmount,
            departureTime, departurePortId,
            arrivalTime, arrivalPortId,
            remark, addName, addTime, modifyTime);
    }

    @Transactional(rollbackFor = Exception.class)
    public int updateStowage(String stowageId, String theDate, int type, String categoryId,
                             Integer count, Integer piece, Integer quantity,
                             BigDecimal weight, BigDecimal volume,
                             BigDecimal declaredValue, BigDecimal taxAmount,
                             String departureTime, String departurePortId,
                             String arrivalTime, String arrivalPortId,
                             String remark, String addName,
                             String addTime, String modifyTime) {
        return jdbc.update("""
            UPDATE stowages SET
              the_date = ?::date,
              stowage_type = ?,
              category_id = ?::uuid,
              count_value = ?,
              piece_value = ?,
              quantity_value = ?,
              weight_value = ?,
              volume_value = ?,
              declared_value = ?,
              tax_amount = ?,
              departure_time = ?::timestamptz,
              departure_port_id = ?::uuid,
              arrival_time = ?::timestamptz,
              arrival_port_id = ?::uuid,
              remark = ?,
              add_name = ?,
              add_time = ?::timestamptz,
              modify_time = ?::timestamptz,
              updated_at = now()
            WHERE id = ?::uuid
            """,
            theDate, type, categoryId,
            count, piece, quantity,
            weight, volume, declaredValue, taxAmount,
            departureTime, departurePortId,
            arrivalTime, arrivalPortId,
            remark, addName, addTime, modifyTime,
            stowageId);
    }

    @Transactional(rollbackFor = Exception.class)
    public int detachCartons(String tenantId, List<String> cartonIds) {
        if (cartonIds == null || cartonIds.isEmpty()) return 0;
        String[] arr = cartonIds.toArray(new String[0]);
        return jdbc.update("""
            UPDATE cartons SET stowage_id = NULL
            WHERE tenant_id = ?::uuid AND id = ANY (?::uuid[])
            """, tenantId, arr);
    }

    @Transactional(rollbackFor = Exception.class)
    public int attachCartons(String tenantId, String stowageId, List<String> cartonIds) {
        if (cartonIds == null || cartonIds.isEmpty()) return 0;
        String[] arr = cartonIds.toArray(new String[0]);
        return jdbc.update("""
            UPDATE cartons SET stowage_id = ?::uuid
            WHERE tenant_id = ?::uuid AND id = ANY (?::uuid[])
            """, stowageId, tenantId, arr);
    }

    public String findPortIdByName(String tenantId, String name) {
        if (name == null || name.isBlank()) return null;
        List<String> rows = jdbc.queryForList("""
            SELECT id::text FROM stowage_ports
            WHERE tenant_id = ?::uuid AND name = ?
            LIMIT 1
            """, String.class, tenantId, name);
        return rows.isEmpty() ? null : rows.get(0);
    }
}

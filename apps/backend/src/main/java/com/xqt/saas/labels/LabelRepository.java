package com.xqt.saas.labels;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class LabelRepository {
    private final JdbcTemplate jdbc;

    public LabelRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 对应 doLabel 的主查询：按客户 + No 找 shipment 及对应渠道。
     * Submit 之后 shipments 才有数据；filter 通过 customer_id 强约束客户隔离。
     */
    public List<Map<String, Object>> findShipmentsByNos(String tenantId, String customerId, List<String> nos) {
        if (nos == null || nos.isEmpty()) {
            return List.of();
        }
        String[] arr = nos.toArray(new String[0]);
        return jdbc.queryForList("""
            SELECT
              s.id::text       AS shipment_id,
              s.shipment_no,
              s.customer_ref,
              s.status::text   AS status,
              s.destination_country,
              s.channel_id::text AS channel_id,
              ch.code           AS channel_code,
              (
                SELECT c.tracking_no FROM cartons c
                WHERE c.shipment_id = s.id AND c.tracking_no IS NOT NULL
                ORDER BY c.carton_no LIMIT 1
              ) AS first_tracking_no
            FROM shipments s
            LEFT JOIN channels ch ON ch.id = s.channel_id
            WHERE s.tenant_id = ?::uuid
              AND s.customer_id = ?::uuid
              AND (s.customer_ref = ANY (?) OR s.shipment_no = ANY (?))
            """, tenantId, customerId, arr, arr);
    }

    /** 对应 doLabel 的 method=1 缓存路径：先查是否已生成过；返回最近的一份。 */
    public Map<String, Object> findLatestLabelFile(String tenantId, String shipmentId, String labelType) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT id::text AS id, tracking_no, label_type, file_hash, file_ext,
                   storage_path, file_size, created_at
            FROM label_files
            WHERE tenant_id = ?::uuid
              AND shipment_id = ?::uuid
              AND label_type = ?
            ORDER BY created_at DESC
            LIMIT 1
            """, tenantId, shipmentId, labelType);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 落 label_files 元数据（无 evidence，向后兼容）。 */
    @Transactional(rollbackFor = Exception.class)
    public String insertLabelFile(String tenantId, String shipmentId, String trackingNo,
                                  String labelType, String fileHash, String fileExt,
                                  String storagePath, int fileSize, String source) {
        return insertLabelFile(tenantId, shipmentId, trackingNo, labelType, fileHash,
            fileExt, storagePath, fileSize, source, null);
    }

    /** 带 provider evidence 的 insertLabelFile：保存 label provider 的 request/response。 */
    @Transactional(rollbackFor = Exception.class)
    public String insertLabelFile(String tenantId, String shipmentId, String trackingNo,
                                  String labelType, String fileHash, String fileExt,
                                  String storagePath, int fileSize, String source,
                                  String evidenceJson) {
        return jdbc.queryForObject("""
            INSERT INTO label_files (
              tenant_id, shipment_id, tracking_no, label_type,
              file_hash, file_ext, storage_path, file_size, source, evidence
            ) VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, ?, coalesce(?::jsonb, '{}'::jsonb))
            RETURNING id::text
            """, String.class,
            tenantId, shipmentId, trackingNo, labelType,
            fileHash, fileExt, storagePath, fileSize, source, evidenceJson);
    }

    /**
     * 对应 doLabel 里把 Plugin->TrackNoList 写 Express_TrackNo / Online_TrackNo：
     * 在新模型里 carton.tracking_no 承担子单号角色，按 carton_no 顺序填入。
     * 当渠道返回的子单号比已有 carton 多时，自动补 carton。
     */
    @Transactional(rollbackFor = Exception.class)
    public void upsertSubTrackingNumbers(String tenantId, String shipmentId,
                                         List<String> subTrackingNos,
                                         String masterTrackingNo) {
        if (subTrackingNos == null || subTrackingNos.isEmpty()) {
            return;
        }
        List<Map<String, Object>> existing = jdbc.queryForList("""
            SELECT id::text AS id, carton_no
            FROM cartons
            WHERE tenant_id = ?::uuid AND shipment_id = ?::uuid
            ORDER BY carton_no
            """, tenantId, shipmentId);

        int idx = 0;
        for (Map<String, Object> row : existing) {
            if (idx >= subTrackingNos.size()) break;
            jdbc.update("""
                UPDATE cartons
                SET tracking_no = ?, carrier_master_tracking_no = ?
                WHERE id = ?::uuid
                """, subTrackingNos.get(idx), masterTrackingNo, row.get("id"));
            idx++;
        }
        // 多余的子单号补 carton 行
        int nextNo = existing.size() + 1;
        for (; idx < subTrackingNos.size(); idx++, nextNo++) {
            String cartonNo = String.format("%03d", nextNo);
            jdbc.update("""
                INSERT INTO cartons (
                  tenant_id, shipment_id, carton_no, actual_weight_kg,
                  tracking_no, carrier_master_tracking_no
                ) VALUES (?::uuid, ?::uuid, ?, 0, ?, ?)
                """, tenantId, shipmentId, cartonNo,
                subTrackingNos.get(idx), masterTrackingNo);
        }
    }

    /** 对应 doLabel 末尾的 Express_Status 日志：在新模型里写 tracking_events。 */
    @Transactional(rollbackFor = Exception.class)
    public void insertLabelEvent(String tenantId, String shipmentId, String trackingNo,
                                 String rawStatus, String normalizedStatus) {
        jdbc.update("""
            INSERT INTO tracking_events (
              tenant_id, shipment_id, tracking_no,
              event_time, raw_status, normalized_status, source, raw_payload
            ) VALUES (?::uuid, ?::uuid, ?, now(), ?, ?::tracking_status, 'SYSTEM', '{}'::jsonb)
            """, tenantId, shipmentId, trackingNo == null ? "" : trackingNo,
            rawStatus, normalizedStatus);
    }

    /** 对应 ACC `Change` / `Change_No` 的三段查找：CARRIER → CUSTOMER → 直接。 */
    public List<Map<String, Object>> findRelabelMaps(String tenantId, String oldNo) {
        return jdbc.queryForList("""
            SELECT old_no, new_no, scope, status
            FROM relabel_no_map
            WHERE tenant_id = ?::uuid AND old_no = ?
            ORDER BY case scope when 'CARRIER' then 0 else 1 end
            """, tenantId, oldNo);
    }

    /** 是否存在 new_no 命中拦截状态。对应 ACC `Select Status from Change where newNo=...`。 */
    public Map<String, Object> findRelabelByNewNo(String tenantId, String newNo) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT old_no, new_no, scope, status
            FROM relabel_no_map
            WHERE tenant_id = ?::uuid AND new_no = ?
            LIMIT 1
            """, tenantId, newNo);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 根据子单号反查 shipment。对应 ACC `Express_TrackNo` 反查。 */
    public Map<String, Object> findShipmentByTrackingNo(String tenantId, String trackingNo) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT s.id::text AS shipment_id, s.shipment_no, s.customer_ref,
                   c.tracking_no, c.carton_no
            FROM cartons c
            JOIN shipments s ON s.id = c.shipment_id
            WHERE c.tenant_id = ?::uuid AND c.tracking_no = ?
            LIMIT 1
            """, tenantId, trackingNo);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 对应 ACC getNewLabel.php 按索引取文件：列出 shipment 的所有标签文件。 */
    public List<Map<String, Object>> listLabelFilesForShipment(String tenantId, String shipmentId) {
        return jdbc.queryForList("""
            SELECT id::text AS id, tracking_no, label_type, file_hash, file_ext,
                   storage_path, file_size, created_at
            FROM label_files
            WHERE tenant_id = ?::uuid AND shipment_id = ?::uuid
            ORDER BY created_at, id
            """, tenantId, shipmentId);
    }

    /** 对应 ACC getNewLabel.php 找子单号在 shipment 中的序号。 */
    public List<String> listCartonTrackingNos(String tenantId, String shipmentId) {
        return jdbc.queryForList("""
            SELECT tracking_no FROM cartons
            WHERE tenant_id = ?::uuid AND shipment_id = ?::uuid AND tracking_no IS NOT NULL
            ORDER BY carton_no
            """, String.class, tenantId, shipmentId);
    }
}

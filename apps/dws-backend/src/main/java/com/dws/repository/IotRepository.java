package com.dws.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Repository
public class IotRepository {

    private static final Logger logger = LoggerFactory.getLogger(IotRepository.class);

    private final JdbcTemplate jdbc;

    public IotRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private void setTenant(String tenantId) {
        jdbc.queryForObject("select set_config('app.current_tenant_id', ?, true)", String.class, tenantId);
    }

    public Map<String, Object> findCartonByItemNumber(String tenantId, String itemNumber) {
        setTenant(tenantId);
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT
                c.id::text AS carton_id,
                c.carton_no,
                c.actual_weight_kg,
                c.length_cm,
                c.width_cm,
                c.height_cm,
                s.id::text AS shipment_id,
                s.shipment_no,
                s.status::text AS shipment_status,
                s.destination_country,
                s.destination_postal_code,
                ch.code AS channel_code,
                ch.name AS channel_name
            FROM cartons c
            JOIN shipments s ON s.id = c.shipment_id
            LEFT JOIN channels ch ON ch.id = s.channel_id
            WHERE c.tenant_id = ?::uuid
              AND c.carton_no = ?
            LIMIT 1
            """, tenantId, itemNumber);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public int countCartonsByShipment(String tenantId, String shipmentId) {
        setTenant(tenantId);
        Integer count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM cartons
            WHERE tenant_id = ?::uuid AND shipment_id = ?::uuid
            """, Integer.class, tenantId, shipmentId);
        return count != null ? count : 0;
    }

    public int countScannedCartonsByShipment(String tenantId, String shipmentId) {
        setTenant(tenantId);
        Integer count = jdbc.queryForObject("""
            SELECT COUNT(DISTINCT carton_id) FROM scan_events
            WHERE tenant_id = ?::uuid
              AND shipment_id = ?::uuid
              AND scan_type = 'PICKUP'
            """, Integer.class, tenantId, shipmentId);
        return count != null ? count : 0;
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateCartonDimensions(String tenantId, String cartonId,
                                       BigDecimal weightKg, BigDecimal lengthCm,
                                       BigDecimal widthCm, BigDecimal heightCm) {
        setTenant(tenantId);
        jdbc.update("""
            UPDATE cartons
            SET actual_weight_kg = ?,
                length_cm = ?,
                width_cm = ?,
                height_cm = ?
            WHERE tenant_id = ?::uuid AND id = ?::uuid
            """, weightKg, lengthCm, widthCm, heightCm, tenantId, cartonId);
    }

    @Transactional(rollbackFor = Exception.class)
    public String insertScanEvent(String tenantId, String shipmentId, String cartonId,
                                  String scanType, String scanResult, String deviceCode,
                                  String picUrl, String picBase64) {
        setTenant(tenantId);
        logger.info("插入扫描事件: tenantId={}, shipmentId={}, cartonId={}, scanType={}", tenantId, shipmentId, cartonId, scanType);
        String scanEventId = jdbc.queryForObject("""
            INSERT INTO scan_events (
                tenant_id, shipment_id, carton_id, scan_type, scan_result,
                scanned_at, device_code, raw_payload
            ) VALUES (?::uuid, ?::uuid, ?::uuid, ?, ?, now(), ?, ?::jsonb)
            RETURNING id::text
            """, String.class,
            tenantId, shipmentId, cartonId, scanType, scanResult, deviceCode,
            buildPayloadJson(picUrl, picBase64));
        logger.info("插入扫描事件成功: scanEventId={}", scanEventId);
        return scanEventId;
    }

    private String buildPayloadJson(String picUrl, String picBase64) {
        StringBuilder json = new StringBuilder("{\"source\":\"IOT\"");
        if (picUrl != null && !picUrl.isEmpty()) {
            json.append(",\"pic_url\":\"").append(escapeJson(picUrl)).append("\"");
        }
        if (picBase64 != null && !picBase64.isEmpty()) {
            json.append(",\"pic_base64\":true");
        }
        json.append("}");
        return json.toString();
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public boolean hasRecentScanEvent(String tenantId, String cartonId, String scanType, int withinSeconds) {
        Integer count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM scan_events
            WHERE tenant_id = ?::uuid
              AND carton_id = ?::uuid
              AND scan_type = ?
              AND scanned_at > now() - interval '? seconds'
            """, Integer.class, tenantId, cartonId, scanType, withinSeconds);
        return count != null && count > 0;
    }

    public Map<String, Object> findShipmentByNumber(String tenantId, String shipmentNo) {
        setTenant(tenantId);
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT
                s.id::text AS shipment_id,
                s.shipment_no,
                s.status::text AS shipment_status,
                s.destination_country,
                s.destination_postal_code,
                ch.code AS channel_code,
                ch.name AS channel_name
            FROM shipments s
            LEFT JOIN channels ch ON ch.id = s.channel_id
            WHERE s.tenant_id = ?::uuid
              AND s.shipment_no = ?
            LIMIT 1
            """, tenantId, shipmentNo);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void updateShipmentMeasuredAt(String tenantId, String shipmentId) {
        setTenant(tenantId);
        jdbc.update("""
            UPDATE shipments
            SET measured_at = now()
            WHERE tenant_id = ?::uuid AND id = ?::uuid
            """, tenantId, shipmentId);
    }

    @Transactional(rollbackFor = Exception.class)
    public String findOrCreateShipment(String tenantId, String customerId, String shipmentNo) {
        setTenant(tenantId);
        logger.info("查找或创建运单: tenantId={}, customerId={}, shipmentNo={}", tenantId, customerId, shipmentNo);
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT id::text AS shipment_id FROM shipments
            WHERE tenant_id = ?::uuid AND shipment_no = ?
            LIMIT 1
            """, tenantId, shipmentNo);
        if (!rows.isEmpty()) {
            String shipmentId = (String) rows.get(0).get("shipment_id");
            logger.info("找到现有运单: shipmentId={}", shipmentId);
            return shipmentId;
        }
        String shipmentId = jdbc.queryForObject("""
            INSERT INTO shipments (tenant_id, customer_id, shipment_no, status, service_mode, source)
            VALUES (?::uuid, ?::uuid, ?, 'DRAFT', 'CARGO', 'LOCAL')
            RETURNING id::text
            """, String.class, tenantId, customerId, shipmentNo);
        logger.info("创建新运单成功: shipmentId={}", shipmentId);
        return shipmentId;
    }

    @Transactional(rollbackFor = Exception.class)
    public String findOrCreateCarton(String tenantId, String shipmentId, String cartonNo,
                                     BigDecimal weightKg, BigDecimal lengthCm,
                                     BigDecimal widthCm, BigDecimal heightCm) {
        setTenant(tenantId);
        logger.info("查找或创建箱号: tenantId={}, shipmentId={}, cartonNo={}, weight={}, length={}, width={}, height={}", 
            tenantId, shipmentId, cartonNo, weightKg, lengthCm, widthCm, heightCm);
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT id::text AS carton_id FROM cartons
            WHERE tenant_id = ?::uuid AND shipment_id = ?::uuid AND carton_no = ?
            LIMIT 1
            """, tenantId, shipmentId, cartonNo);
        if (!rows.isEmpty()) {
            String cartonId = (String) rows.get(0).get("carton_id");
            logger.info("找到现有箱号: cartonId={}", cartonId);
            return cartonId;
        }
        String cartonId = jdbc.queryForObject("""
            INSERT INTO cartons (tenant_id, shipment_id, carton_no, actual_weight_kg, length_cm, width_cm, height_cm)
            VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, ?)
            RETURNING id::text
            """, String.class, tenantId, shipmentId, cartonNo, weightKg, lengthCm, widthCm, heightCm);
        logger.info("创建新箱号成功: cartonId={}", cartonId);
        return cartonId;
    }

    @Transactional(rollbackFor = Exception.class)
    public String findOrCreateDefaultCustomer(String tenantId) {
        logger.info("查找或创建默认客户: tenantId={}", tenantId);
        ensureTenantExists(tenantId);
        setTenant(tenantId);
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT id::text AS customer_id FROM customers
            WHERE tenant_id = ?::uuid
            LIMIT 1
            """, tenantId);
        if (!rows.isEmpty()) {
            String customerId = (String) rows.get(0).get("customer_id");
            logger.info("找到现有客户: customerId={}", customerId);
            return customerId;
        }
        String customerId = jdbc.queryForObject("""
            INSERT INTO customers (tenant_id, code, name, account_mode, default_currency)
            VALUES (?::uuid, 'IOT_DEFAULT', 'IOT_DEFAULT', 'MONTHLY', 'CNY')
            RETURNING id::text
            """, String.class, tenantId);
        logger.info("创建新客户成功: customerId={}", customerId);
        return customerId;
    }

    private void ensureTenantExists(String tenantId) {
        logger.info("检查或创建租户: tenantId={}", tenantId);
        Integer count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM tenants WHERE id = ?::uuid
            """, Integer.class, tenantId);
        if (count == null || count == 0) {
            logger.info("租户不存在，创建新租户: tenantId={}", tenantId);
            jdbc.update("""
                INSERT INTO tenants (id, code, name)
                VALUES (?::uuid, 'IOT_TENANT', 'IOT_TENANT')
                """, tenantId);
            logger.info("创建租户成功: tenantId={}", tenantId);
        } else {
            logger.info("租户已存在: tenantId={}, count={}", tenantId, count);
        }
    }
}

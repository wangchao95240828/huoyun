package com.xqt.saas.acc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.JsonSupport;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ACC 客服中心 → 收货 → DWS 扫描流水 + 重量差异。
 *
 *   GET /api/acc/dws-scans          → DWS 推送的全部流水（含 NOT_FOUND）
 *   GET /api/acc/dws-discrepancies → DWS 实测重 vs cartons 制单录入重 差值 > 0.5kg 的箱
 *
 * 两个 endpoint 都返回 aggregations，前端 footer 显示合计。
 */
@RestController
public class AccDwsScansController {
    private final JdbcTemplate jdbc;
    private final JsonSupport json;

    public AccDwsScansController(JdbcTemplate jdbc, JsonSupport json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @GetMapping("/api/acc/dws-scans")
    @RequestMapping("/api/acc/dws-scans")
    public Map<String, Object> listScans(
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer pageSize,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String dateFrom,
        @RequestParam(required = false) String dateTo,
        @RequestParam(required = false) String status      // OK / NOT_FOUND / DUP / ERROR
    ) {
        try {
            int limit = AccPaging.pageSize(pageSize);
            int offset = AccPaging.offset(page, pageSize);
            String search = keyword == null || keyword.isBlank() ? null : "%" + keyword + "%";

            Long total = jdbc.queryForObject(
                "SELECT count(*) FROM acc_dws_scans"
                + " WHERE (?::text IS NULL OR item_number ILIKE ? OR shipment_number ILIKE ?)"
                + "   AND (?::date IS NULL OR scanned_at >= ?::date)"
                + "   AND (?::date IS NULL OR scanned_at < (?::date + 1))"
                + "   AND (?::text IS NULL OR status = ?)",
                Long.class, search, search, search, dateFrom, dateFrom, dateTo, dateTo, status, status);

            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id::text AS id, item_number, shipment_number, action,"
                + "       weight_kg, length_cm, width_cm, height_cm,"
                + "       volume_weight, chargeable_kg, pic_url,"
                + "       status, info, scanned_at, created_at"
                + " FROM acc_dws_scans"
                + " WHERE (?::text IS NULL OR item_number ILIKE ? OR shipment_number ILIKE ?)"
                + "   AND (?::date IS NULL OR scanned_at >= ?::date)"
                + "   AND (?::date IS NULL OR scanned_at < (?::date + 1))"
                + "   AND (?::text IS NULL OR status = ?)"
                + " ORDER BY scanned_at DESC LIMIT ? OFFSET ?",
                search, search, search, dateFrom, dateFrom, dateTo, dateTo, status, status, limit, offset);

            // 聚合：总扫描数 + 总重量
            java.math.BigDecimal sumWeight = jdbc.queryForObject(
                "SELECT coalesce(sum(weight_kg), 0) FROM acc_dws_scans"
                + " WHERE (?::text IS NULL OR item_number ILIKE ? OR shipment_number ILIKE ?)"
                + "   AND (?::date IS NULL OR scanned_at >= ?::date)"
                + "   AND (?::date IS NULL OR scanned_at < (?::date + 1))"
                + "   AND (?::text IS NULL OR status = ?)"
                + "   AND status = 'OK'",
                java.math.BigDecimal.class, search, search, search, dateFrom, dateFrom, dateTo, dateTo, status, status);
            Map<String, Object> agg = new LinkedHashMap<>();
            agg.put("weightKg", sumWeight);

            return AccPaging.result(rows.stream().map(this::projectScan).toList(),
                total == null ? 0 : total, agg);
        } catch (DataAccessException ex) {
            return AccPaging.result(List.of(), 0);
        }
    }

    @GetMapping("/api/acc/dws-discrepancies")
    @RequestMapping("/api/acc/dws-discrepancies")
    public Map<String, Object> listDiscrepancies(
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer pageSize,
        @RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "0.5") String threshold    // kg
    ) {
        try {
            int limit = AccPaging.pageSize(pageSize);
            int offset = AccPaging.offset(page, pageSize);
            String search = keyword == null || keyword.isBlank() ? null : "%" + keyword + "%";
            java.math.BigDecimal thr;
            try { thr = new java.math.BigDecimal(threshold); }
            catch (Exception e) { thr = new java.math.BigDecimal("0.5"); }

            Long total = jdbc.queryForObject(
                "SELECT count(DISTINCT d.carton_id) FROM acc_dws_scans d"
                + " JOIN cartons c ON c.id = d.carton_id"
                + " WHERE d.status = 'OK' AND d.action IN ('pickup','update')"
                + "   AND abs(d.weight_kg - c.actual_weight_kg) >= ?"
                + "   AND (?::text IS NULL OR d.item_number ILIKE ? OR d.shipment_number ILIKE ?)",
                Long.class, thr, search, search, search);

            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT DISTINCT ON (d.carton_id)"
                + "       d.id::text       AS id,"
                + "       d.item_number, d.shipment_number,"
                + "       d.weight_kg     AS dws_weight,"
                + "       c.actual_weight_kg AS acc_weight,"
                + "       (d.weight_kg - c.actual_weight_kg) AS diff,"
                + "       d.chargeable_kg AS dws_chargeable,"
                + "       c.chargeable_weight_kg AS acc_chargeable,"
                + "       s.shipment_no, s.destination_country,"
                + "       cu.name AS customer_name,"
                + "       d.scanned_at"
                + " FROM acc_dws_scans d"
                + " JOIN cartons c   ON c.id = d.carton_id"
                + " JOIN shipments s ON s.id = c.shipment_id"
                + " LEFT JOIN customers cu ON cu.id = s.customer_id"
                + " WHERE d.status = 'OK' AND d.action IN ('pickup','update')"
                + "   AND abs(d.weight_kg - c.actual_weight_kg) >= ?"
                + "   AND (?::text IS NULL OR d.item_number ILIKE ? OR d.shipment_number ILIKE ?)"
                + " ORDER BY d.carton_id, d.scanned_at DESC"
                + " LIMIT ? OFFSET ?",
                thr, search, search, search, limit, offset);

            return AccPaging.result(rows.stream().map(this::projectDiff).toList(),
                total == null ? 0 : total);
        } catch (DataAccessException ex) {
            return AccPaging.result(List.of(), 0);
        }
    }

    private Map<String, Object> projectScan(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("itemNumber", row.get("item_number"));
        out.put("shipmentNumber", row.get("shipment_number"));
        out.put("action", row.get("action"));
        out.put("weightKg", row.get("weight_kg"));
        out.put("lengthCm", row.get("length_cm"));
        out.put("widthCm", row.get("width_cm"));
        out.put("heightCm", row.get("height_cm"));
        out.put("volumeWeight", row.get("volume_weight"));
        out.put("chargeableKg", row.get("chargeable_kg"));
        out.put("picUrl", row.get("pic_url"));
        out.put("status", row.get("status"));
        out.put("info", row.get("info"));
        out.put("scannedAt", json.value(row.get("scanned_at")));
        return out;
    }

    private Map<String, Object> projectDiff(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("itemNumber", row.get("item_number"));
        out.put("shipmentNumber", row.get("shipment_number"));
        out.put("customerName", row.get("customer_name"));
        out.put("destinationCountry", row.get("destination_country"));
        out.put("dwsWeight", row.get("dws_weight"));
        out.put("accWeight", row.get("acc_weight"));
        out.put("diff", row.get("diff"));
        out.put("dwsChargeable", row.get("dws_chargeable"));
        out.put("accChargeable", row.get("acc_chargeable"));
        out.put("scannedAt", json.value(row.get("scanned_at")));
        return out;
    }
}

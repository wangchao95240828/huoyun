package com.xqt.saas.acc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import com.xqt.saas.common.BranchAccessFilter;
import com.xqt.saas.common.JsonSupport;
import com.xqt.saas.framework.cascade.CascadeChecker;
import com.xqt.saas.framework.fieldgate.FieldGate;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/acc/forecasts")
public class AccForecastsController {
    private static final String TABLE = "acc_forecasts";

    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final CascadeChecker cascadeChecker;
    private final FieldGate fieldGate;

        private final BranchAccessFilter branchAccess;

public AccForecastsController(JdbcTemplate jdbc, JsonSupport json,
                                   CascadeChecker cascadeChecker, FieldGate fieldGate,
                                  BranchAccessFilter branchAccess) {
        this.jdbc = jdbc;
        this.json = json;
        this.cascadeChecker = cascadeChecker;
        this.fieldGate = fieldGate;
            this.branchAccess = branchAccess;
    }

    @GetMapping
    public Map<String, Object> list(
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer pageSize,
        @RequestParam(required = false) String keyword
    ) {
        try {
            int limit = AccPaging.pageSize(pageSize);
            int offset = AccPaging.offset(page, pageSize);
            String search = keyword == null || keyword.isBlank() ? null : "%" + keyword + "%";
            var access = branchAccess.forCurrentViaCustomer("f");
            java.util.List<Object> countParams = new java.util.ArrayList<>(java.util.Arrays.asList(search, search));
            countParams.addAll(access.params());
            long total = json.value(jdbc.queryForObject(
                "SELECT count(*) FROM acc_forecasts f"
                + " WHERE (?::text IS NULL OR f.forecast_no ILIKE ?)"
                + access.sql(),
                Long.class, countParams.toArray())) instanceof Number n ? n.longValue() : 0;
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT f.id::text AS id, f.forecast_no, f.customer_id::text AS customer_id,"
                + "       f.channel_id::text AS channel_id, f.package_count, f.weight, f.volume,"
                + "       f.origin, f.destination, f.forecast_date, f.status, f.remark,"
                + "       c.name AS customer_name, ch.name AS channel_name,"
                + "       f.audit_status, f.audited_at, f.audit_name, f.created_at"
                + " FROM acc_forecasts f"
                + " LEFT JOIN customers c ON c.id = f.customer_id"
                + " LEFT JOIN channels ch ON ch.id = f.channel_id"
                + " WHERE (?::text IS NULL OR f.forecast_no ILIKE ?)"
                + access.sql()
                + " ORDER BY f.forecast_date DESC"
                + " LIMIT ? OFFSET ?",
                buildForecastsListParams(search, access, limit, offset));
            return AccPaging.result(rows.stream().map(this::project).toList(), total);
        } catch (DataAccessException ex) {
            return AccPaging.result(List.of(), 0);
        }
    }

    @GetMapping("/{id}/raw")
    public Map<String, Object> raw(@PathVariable String id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM acc_forecasts WHERE id = ?::uuid LIMIT 1", id);
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String forecastNo = (String) body.get("forecastNo");
        if (forecastNo == null || forecastNo.isBlank()) {
            throw ApiException.badRequest("预报单号必填");
        }
        if (body.get("customerId") == null || body.get("customerId").toString().isBlank()) {
            throw ApiException.badRequest("请选择客户");
        }
        Object pc = body.get("packageCount");
        if (pc instanceof Number pn && pn.intValue() <= 0) {
            throw ApiException.badRequest("件数必须大于零");
        }
        Object w = body.get("weight");
        if (w instanceof Number wn && wn.doubleValue() <= 0) {
            throw ApiException.badRequest("重量必须大于零");
        }
        String id = jdbc.queryForObject("""
            INSERT INTO acc_forecasts (tenant_id, forecast_no, customer_id, channel_id,
                                       package_count, weight, volume, origin, destination,
                                       forecast_date, status, remark)
            VALUES (current_setting('app.current_tenant_id')::uuid, ?, ?::uuid, ?::uuid,
                    ?::int, ?::numeric, ?::numeric, ?::text, ?::text,
                    ?::date, ?::text, ?::text)
            RETURNING id::text
            """, String.class, forecastNo, body.get("customerId"), body.get("channelId"),
            body.get("packageCount"), body.get("weight"), body.get("volume"),
            body.get("origin"), body.get("destination"),
            body.get("forecastDate"), body.get("status"), body.get("remark"));
        return Map.of("id", id, "forecastNo", forecastNo);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String currentAudit = jdbc.queryForObject(
            "SELECT audit_status FROM acc_forecasts WHERE id = ?::uuid", String.class, id);
        FieldGate.FilterResult gate = fieldGate.filterAllowedFields(TABLE, currentAudit, body);
        if (!gate.rejected().isEmpty() && gate.allowed().isEmpty()) {
            throw ApiException.badRequest("预报已审核，字段不可修改: " + String.join(",", gate.rejected())
                + "；请先反审");
        }
        Map<String, Object> allowed = gate.allowed();
        jdbc.update("""
            UPDATE acc_forecasts SET
              package_count = coalesce(?::int, package_count),
              weight = coalesce(?::numeric, weight),
              volume = coalesce(?::numeric, volume),
              status = coalesce(?::text, status),
              remark = coalesce(?::text, remark)
            WHERE id = ?::uuid
            """, allowed.get("packageCount"), allowed.get("weight"),
            allowed.get("volume"), (String) allowed.get("status"),
            (String) allowed.get("remark"), id);
        return Map.of("id", id, "rejectedFields", gate.rejected());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        String auditStatus = jdbc.queryForObject(
            "SELECT audit_status FROM acc_forecasts WHERE id = ?::uuid", String.class, id);
        if ("AUDITED".equals(auditStatus)) {
            throw ApiException.badRequest("预报已审核，不能删除，请先反审");
        }
        cascadeChecker.checkBeforeDelete(TABLE, id);
        jdbc.update("DELETE FROM acc_forecasts WHERE id = ?::uuid", id);
        return Map.of("id", id, "deleted", true);
    }

    private static Object[] buildForecastsListParams(String search,
                                                       BranchAccessFilter.AccessClause access,
                                                       int limit, int offset) {
        java.util.List<Object> params = new java.util.ArrayList<>(java.util.Arrays.asList(search, search));
        params.addAll(access.params());
        params.add(limit);
        params.add(offset);
        return params.toArray();
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("forecastNo", row.get("forecast_no"));
        out.put("customerId", row.get("customer_id"));
        out.put("customerName", row.get("customer_name"));
        out.put("channelId", row.get("channel_id"));
        out.put("channelName", row.get("channel_name"));
        out.put("packageCount", row.get("package_count"));
        out.put("weight", row.get("weight"));
        out.put("volume", row.get("volume"));
        out.put("origin", row.get("origin"));
        out.put("destination", row.get("destination"));
        out.put("forecastDate", row.get("forecast_date"));
        out.put("status", row.get("status"));
        out.put("remark", row.get("remark"));
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        out.put("auditName", row.get("audit_name"));
        return out;
    }

    // ════════ P0-D6: 客户预报差异分析 (ACC Forecast_Package 对齐) ════════
    //   GET /forecasts/diff?customerId=&dateFrom=&dateTo=
    //   返回: 每个 forecast 实际到货 vs 预报的 piece_count / weight / volume 差异 +
    //         未到货 (forecast 有但 shipment 没建) + 多到货 (shipment 多于 forecast).

    @GetMapping("/diff")
    public Map<String, Object> diff(
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {
        try {
            // 取预报 + 关联同期 shipments 聚合实际值
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT f.id::text AS forecast_id, f.forecast_no, f.customer_id::text AS customer_id,
                       c.code AS customer_code, c.name AS customer_name,
                       f.forecast_date, f.package_count AS forecast_pieces,
                       f.weight AS forecast_weight, f.volume AS forecast_volume,
                       f.origin, f.destination,
                       coalesce((
                         SELECT count(*) FROM cartons ct
                         JOIN shipments s ON s.id = ct.shipment_id
                         WHERE s.customer_id = f.customer_id
                           AND s.created_at >= f.forecast_date::timestamptz
                           AND s.created_at < (f.forecast_date + 7)::timestamptz
                           AND (f.destination IS NULL OR s.destination_country = f.destination)
                       ), 0) AS actual_pieces,
                       coalesce((
                         SELECT sum(ct.actual_weight_kg) FROM cartons ct
                         JOIN shipments s ON s.id = ct.shipment_id
                         WHERE s.customer_id = f.customer_id
                           AND s.created_at >= f.forecast_date::timestamptz
                           AND s.created_at < (f.forecast_date + 7)::timestamptz
                           AND (f.destination IS NULL OR s.destination_country = f.destination)
                       ), 0) AS actual_weight
                FROM acc_forecasts f
                LEFT JOIN customers c ON c.id = f.customer_id
                WHERE (?::uuid IS NULL OR f.customer_id = ?::uuid)
                  AND (?::date IS NULL OR f.forecast_date >= ?::date)
                  AND (?::date IS NULL OR f.forecast_date <= ?::date)
                ORDER BY f.forecast_date DESC
                """, customerId, customerId, dateFrom, dateFrom, dateTo, dateTo);
            List<Map<String, Object>> out = new java.util.ArrayList<>();
            int totalForecastPieces = 0, totalActualPieces = 0, undeliveredCount = 0, overDeliveredCount = 0;
            for (Map<String, Object> r : rows) {
                int fp = r.get("forecast_pieces") instanceof Number n ? n.intValue() : 0;
                int ap = r.get("actual_pieces") instanceof Number n2 ? n2.intValue() : 0;
                java.math.BigDecimal fw = r.get("forecast_weight") instanceof java.math.BigDecimal bd ? bd : java.math.BigDecimal.ZERO;
                java.math.BigDecimal aw = r.get("actual_weight") instanceof java.math.BigDecimal bd2 ? bd2 : java.math.BigDecimal.ZERO;
                Map<String, Object> o = new LinkedHashMap<>();
                o.put("forecastId", r.get("forecast_id"));
                o.put("forecastNo", r.get("forecast_no"));
                o.put("customerCode", r.get("customer_code"));
                o.put("customerName", r.get("customer_name"));
                o.put("forecastDate", json.value(r.get("forecast_date")));
                o.put("destination", r.get("destination"));
                o.put("forecastPieces", fp);
                o.put("actualPieces", ap);
                o.put("piecesDiff", ap - fp);
                o.put("forecastWeight", fw);
                o.put("actualWeight", aw);
                o.put("weightDiff", aw.subtract(fw));
                String label = ap == 0 ? "未到货"
                    : (ap < fp ? "少到货" : (ap > fp ? "多到货" : "对齐"));
                o.put("status", label);
                out.add(o);
                totalForecastPieces += fp; totalActualPieces += ap;
                if (ap == 0) undeliveredCount++;
                if (ap > fp) overDeliveredCount++;
            }
            return Map.of("data", out, "total", out.size(),
                "summary", Map.of(
                    "totalForecastPieces", totalForecastPieces,
                    "totalActualPieces", totalActualPieces,
                    "undeliveredCount", undeliveredCount,
                    "overDeliveredCount", overDeliveredCount));
        } catch (DataAccessException ex) {
            return Map.of("data", List.of(), "total", 0, "error", ex.getMessage());
        }
    }
}

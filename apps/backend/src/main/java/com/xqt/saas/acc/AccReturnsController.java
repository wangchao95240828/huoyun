package com.xqt.saas.acc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiException;
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

/** /api/acc/returns — 前端列：expressNo / customerName / reason / amount / status / addTime。
 * 来源：return_orders + shipments + customers join。amount 暂无字段，先空。 */
@RestController
@RequestMapping("/api/acc/returns")
public class AccReturnsController {
    private static final String TABLE = "return_orders";
    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final CascadeChecker cascadeChecker;
    private final FieldGate fieldGate;

    public AccReturnsController(JdbcTemplate jdbc, JsonSupport json,
                                CascadeChecker cascadeChecker, FieldGate fieldGate) {
        this.jdbc = jdbc;
        this.json = json;
        this.cascadeChecker = cascadeChecker;
        this.fieldGate = fieldGate;
    }

    @GetMapping
    public Map<String, Object> list(
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer pageSize,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String dateFrom,
        @RequestParam(required = false) String dateTo
    ) {
        try {
            int limit = AccPaging.pageSize(pageSize);
            int offset = AccPaging.offset(page, pageSize);
            String search = keyword == null || keyword.isBlank() ? null : "%" + keyword + "%";
            Long total = jdbc.queryForObject("""
                SELECT count(*) FROM return_orders r
                LEFT JOIN shipments s ON s.id = r.original_shipment_id
                WHERE (?::text IS NULL OR r.return_no ILIKE ? OR s.shipment_no ILIKE ?)
                  AND (?::date IS NULL OR r.created_at >= ?::date)
                  AND (?::date IS NULL OR r.created_at < (?::date + 1))
                """, Long.class, search, search, search, dateFrom, dateFrom, dateTo, dateTo);
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT
                  r.id::text       AS id,
                  r.return_no,
                  r.status,
                  r.reason,
                  r.created_at,
                  r.refund_amount,
                  r.compensate_amount,
                  r.currency,
                  s.shipment_no,
                  s.customer_ref,
                  c.name           AS customer_name,
                  r.audit_status, r.audited_at, r.audit_name
                FROM return_orders r
                LEFT JOIN shipments s ON s.id = r.original_shipment_id
                LEFT JOIN customers c ON c.id = s.customer_id
                WHERE (?::text IS NULL OR r.return_no ILIKE ? OR s.shipment_no ILIKE ?)
                  AND (?::date IS NULL OR r.created_at >= ?::date)
                  AND (?::date IS NULL OR r.created_at < (?::date + 1))
                ORDER BY r.created_at DESC
                LIMIT ? OFFSET ?
                """, search, search, search, dateFrom, dateFrom, dateTo, dateTo, limit, offset);
            return AccPaging.result(rows.stream().map(this::project).toList(), total == null ? 0 : total);
        } catch (DataAccessException ex) {
            return AccPaging.result(List.of(), 0);
        }
    }

    @GetMapping("/{id}/raw")
    public Map<String, Object> raw(@PathVariable String id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM return_orders WHERE id = ?::uuid LIMIT 1", id);
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        if (body.get("return_no") == null || body.get("return_no").toString().isBlank()) {
            throw ApiException.badRequest("退件单号必填");
        }
        if (body.get("original_shipment_id") == null
            || body.get("original_shipment_id").toString().isBlank()) {
            throw ApiException.badRequest("请选择原快件");
        }
        Object refund = body.get("refund_amount");
        if (refund instanceof Number rn && rn.doubleValue() < 0) {
            throw ApiException.badRequest("退款金额不能为负");
        }
        Object comp = body.get("compensate_amount");
        if (comp instanceof Number cn && cn.doubleValue() < 0) {
            throw ApiException.badRequest("补偿金额不能为负");
        }
        String id = jdbc.queryForObject("""
            INSERT INTO return_orders (
              tenant_id, return_no, original_shipment_id, status, reason,
              refund_amount, compensate_amount, currency
            )
            VALUES (current_setting('app.current_tenant_id')::uuid, ?, ?::uuid, ?, ?,
                    coalesce(?, 0), coalesce(?, 0), coalesce(?, 'CNY'))
            RETURNING id::text
            """, String.class,
            body.get("return_no"),
            body.get("original_shipment_id") == null ? null : body.get("original_shipment_id").toString(),
            body.getOrDefault("status", "PENDING"),
            body.get("reason"),
            asMoney(body.get("refund_amount")),
            asMoney(body.get("compensate_amount")),
            body.get("currency"));
        return Map.of("id", id);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String currentAudit = jdbc.queryForObject(
            "SELECT audit_status FROM return_orders WHERE id = ?::uuid", String.class, id);
        FieldGate.FilterResult gate = fieldGate.filterAllowedFields(TABLE, currentAudit, body);
        if (!gate.rejected().isEmpty() && gate.allowed().isEmpty()) {
            throw ApiException.badRequest("退件已审核，字段不可修改: " + String.join(",", gate.rejected())
                + "；请先反审");
        }
        Map<String, Object> allowed = gate.allowed();
        jdbc.update("""
            UPDATE return_orders SET
              return_no         = coalesce(?, return_no),
              status            = coalesce(?, status),
              reason            = coalesce(?, reason),
              refund_amount     = coalesce(?, refund_amount),
              compensate_amount = coalesce(?, compensate_amount),
              currency          = coalesce(?, currency)
            WHERE id = ?::uuid
            """,
            (String) allowed.get("return_no"),
            (String) allowed.get("status"),
            (String) allowed.get("reason"),
            allowed.get("refund_amount") == null ? null : asMoney(allowed.get("refund_amount")),
            allowed.get("compensate_amount") == null ? null : asMoney(allowed.get("compensate_amount")),
            (String) allowed.get("currency"),
            id);
        return Map.of("id", id, "rejectedFields", gate.rejected());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        String auditStatus = jdbc.queryForObject(
            "SELECT audit_status FROM return_orders WHERE id = ?::uuid", String.class, id);
        if ("AUDITED".equals(auditStatus)) {
            throw ApiException.badRequest("退件已审核，不能删除，请先反审");
        }
        cascadeChecker.checkBeforeDelete(TABLE, id);
        jdbc.update("DELETE FROM return_orders WHERE id = ?::uuid", id);
        return Map.of("id", id, "deleted", true);
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("expressNo", row.get("customer_ref") != null
            ? row.get("customer_ref") : row.get("shipment_no"));
        out.put("returnNo", row.get("return_no"));
        out.put("customerName", row.get("customer_name"));
        out.put("reason", row.get("reason"));
        // amount = 退款 - 补收（正数代表退给客户，负数代表向客户补收）
        java.math.BigDecimal refund = asMoney(row.get("refund_amount"));
        java.math.BigDecimal compensate = asMoney(row.get("compensate_amount"));
        out.put("refundAmount", refund);
        out.put("compensateAmount", compensate);
        out.put("amount", refund.subtract(compensate));
        out.put("currency", row.get("currency"));
        out.put("status", row.get("status"));
        out.put("addTime", json.value(row.get("created_at")));
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        out.put("auditName", row.get("audit_name"));
        return out;
    }

    private static java.math.BigDecimal asMoney(Object v) {
        if (v == null) return java.math.BigDecimal.ZERO;
        if (v instanceof java.math.BigDecimal bd) return bd;
        if (v instanceof Number n) return new java.math.BigDecimal(n.toString());
        return new java.math.BigDecimal(v.toString());
    }
}

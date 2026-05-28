package com.xqt.saas.acc;

import java.math.BigDecimal;
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

/**
 * /api/acc/bills — 前端列：no / customerName / settlement / theDate / amount / paid / unpay
 * / quantity / status / salesman
 *
 * 来源：customer_invoices + customers join。paid 由 payments.amount 聚合（按 reference_no=invoice_no）。
 */
@RestController
@RequestMapping("/api/acc/bills")
public class AccBillsController {
    private static final String TABLE = "customer_invoices";

    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final CascadeChecker cascadeChecker;
    private final FieldGate fieldGate;

    public AccBillsController(JdbcTemplate jdbc, JsonSupport json,
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
                SELECT count(*) FROM customer_invoices i
                WHERE (?::text IS NULL OR i.invoice_no ILIKE ?)
                  AND (?::date IS NULL OR i.issued_at >= ?::date)
                  AND (?::date IS NULL OR i.issued_at < (?::date + 1))
                """, Long.class, search, search, dateFrom, dateFrom, dateTo, dateTo);

            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT
                  i.id::text       AS id,
                  i.invoice_no,
                  i.template_code,
                  i.currency,
                  i.total_amount,
                  i.status,
                  i.issued_at,
                  i.audit_status,
                  i.audited_at,
                  i.audit_name,
                  c.name           AS customer_name,
                  c.account_mode   AS settlement,
                  u.display_name   AS salesman_name,
                  (
                    SELECT coalesce(sum(p.amount), 0) FROM payments p
                    WHERE p.tenant_id = i.tenant_id AND p.reference_no = i.invoice_no
                  ) AS paid_amount,
                  (
                    SELECT count(*) FROM customer_invoice_lines l WHERE l.invoice_id = i.id
                  ) AS line_count
                FROM customer_invoices i
                LEFT JOIN customers c ON c.id = i.customer_id
                LEFT JOIN users u ON u.id = c.salesman_user_id
                WHERE (?::text IS NULL OR i.invoice_no ILIKE ?)
                  AND (?::date IS NULL OR i.issued_at >= ?::date)
                  AND (?::date IS NULL OR i.issued_at < (?::date + 1))
                ORDER BY i.issued_at DESC NULLS LAST, i.invoice_no
                LIMIT ? OFFSET ?
                """, search, search, dateFrom, dateFrom, dateTo, dateTo, limit, offset);
            return AccPaging.result(rows.stream().map(this::project).toList(),
                total == null ? 0 : total);
        } catch (DataAccessException ex) {
            return AccPaging.result(List.of(), 0);
        }
    }

    @GetMapping("/{id}/raw")
    public Map<String, Object> raw(@PathVariable String id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM customer_invoices WHERE id = ?::uuid LIMIT 1", id);
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    /** 对应前端 "查看账单明细" — bills/{id}/items 拉 customer_invoice_lines。 */
    @GetMapping("/{id}/items")
    public Map<String, Object> items(@PathVariable String id) {
        try {
            List<Map<String, Object>> lines = jdbc.queryForList("""
                SELECT id::text AS id, line_no, description, quantity, unit_price, line_total
                FROM customer_invoice_lines WHERE invoice_id = ?::uuid
                ORDER BY line_no
                """, id);
            return Map.of("data", json.rows(lines));
        } catch (DataAccessException ex) {
            return Map.of("data", List.of());
        }
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String invoiceNo = (String) body.get("invoice_no");
        Object customerId = body.get("customer_id");
        String currency = (String) body.getOrDefault("currency", "CNY");
        String id = jdbc.queryForObject("""
            INSERT INTO customer_invoices (
              tenant_id, customer_id, invoice_no, currency, status
            ) VALUES (
              current_setting('app.current_tenant_id')::uuid, ?::uuid, ?, ?, 'DRAFT'
            )
            RETURNING id::text
            """, String.class, customerId == null ? null : customerId.toString(), invoiceNo, currency);
        return Map.of("id", id, "invoice_no", invoiceNo);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        // 1) 字段闸：审核后金额/客户/币种锁定
        String currentAudit = jdbc.queryForObject(
            "SELECT audit_status FROM customer_invoices WHERE id = ?::uuid", String.class, id);
        FieldGate.FilterResult gate = fieldGate.filterAllowedFields(TABLE, currentAudit, body);
        if (!gate.rejected().isEmpty() && gate.allowed().isEmpty()) {
            throw ApiException.badRequest("账单已审核，字段不可修改: " + String.join(",", gate.rejected())
                + "；请先反审");
        }
        Map<String, Object> allowed = gate.allowed();
        jdbc.update("""
            UPDATE customer_invoices SET
              invoice_no   = coalesce(?, invoice_no),
              status       = coalesce(?, status),
              total_amount = coalesce(?, total_amount)
            WHERE id = ?::uuid
            """, (String) allowed.get("invoice_no"),
            (String) allowed.get("status"),
            allowed.get("total_amount") instanceof Number n ? new BigDecimal(n.toString()) : null,
            id);
        return Map.of("id", id, "rejectedFields", gate.rejected());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        // 级联校验：已关联收款的账单不允许删
        cascadeChecker.checkBeforeDelete(TABLE, id);
        jdbc.update("DELETE FROM customer_invoices WHERE id = ?::uuid", id);
        return Map.of("id", id, "deleted", true);
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        BigDecimal amount = (BigDecimal) row.get("total_amount");
        BigDecimal paid = (BigDecimal) row.get("paid_amount");
        BigDecimal unpay = (amount == null ? BigDecimal.ZERO : amount)
            .subtract(paid == null ? BigDecimal.ZERO : paid);
        out.put("id", row.get("id"));
        out.put("no", row.get("invoice_no"));
        out.put("customerName", row.get("customer_name"));
        out.put("settlement", row.get("settlement"));
        out.put("theDate", json.value(row.get("issued_at")));
        out.put("amount", amount);
        out.put("paid", paid);
        out.put("unpay", unpay);
        out.put("quantity", row.get("line_count"));
        out.put("status", row.get("status"));
        out.put("salesman", row.get("salesman_name") == null ? "" : row.get("salesman_name"));
        out.put("currency", row.get("currency"));
        // 审核流字段：前端用来显示"已审核"红色标记 + 决定能否点删除/反审按钮
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        out.put("auditName", row.get("audit_name"));
        return out;
    }
}

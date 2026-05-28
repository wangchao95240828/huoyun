package com.xqt.saas.acc;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import com.xqt.saas.common.JsonSupport;
import com.xqt.saas.framework.cascade.CascadeChecker;
import com.xqt.saas.framework.fieldgate.FieldGate;
import com.xqt.saas.framework.money.MoneySnapshotService;
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
 * /api/acc/payments — 前端列：no / supplierName / bankName / amount / theDate / auditName / remark
 * AP 付款，来源 partner_payments + partners。
 */
@RestController
@RequestMapping("/api/acc/payments")
public class AccPaymentsController {
    private static final String TABLE = "partner_payments";

    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final CascadeChecker cascadeChecker;
    private final FieldGate fieldGate;
    private final MoneySnapshotService moneySnapshotService;

    public AccPaymentsController(JdbcTemplate jdbc, JsonSupport json,
                                 CascadeChecker cascadeChecker, FieldGate fieldGate,
                                 MoneySnapshotService moneySnapshotService) {
        this.jdbc = jdbc;
        this.json = json;
        this.cascadeChecker = cascadeChecker;
        this.fieldGate = fieldGate;
        this.moneySnapshotService = moneySnapshotService;
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
                SELECT count(*) FROM partner_payments p
                WHERE (?::text IS NULL OR p.payment_no ILIKE ?)
                  AND (?::date IS NULL OR p.created_at >= ?::date)
                  AND (?::date IS NULL OR p.created_at < (?::date + 1))
                """, Long.class, search, search, dateFrom, dateFrom, dateTo, dateTo);

            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT
                  p.id::text       AS id,
                  p.payment_no,
                  p.currency,
                  p.amount,
                  p.payment_type,
                  p.reference_no,
                  p.status,
                  p.paid_at,
                  p.created_at,
                  p.audit_status,
                  p.audited_at,
                  p.audit_name,
                  pr.name           AS partner_name,
                  coalesce(fa.bank_name, fa.account_name) AS bank_name
                FROM partner_payments p
                LEFT JOIN partners pr ON pr.id = p.partner_id
                LEFT JOIN financial_accounts fa ON fa.id = p.financial_account_id
                WHERE (?::text IS NULL OR p.payment_no ILIKE ?)
                  AND (?::date IS NULL OR p.created_at >= ?::date)
                  AND (?::date IS NULL OR p.created_at < (?::date + 1))
                ORDER BY p.created_at DESC
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
            "SELECT * FROM partner_payments WHERE id = ?::uuid LIMIT 1", id);
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String paymentNo = (String) body.get("payment_no");
        Object partnerId = body.get("partner_id");
        BigDecimal amount = body.get("amount") instanceof Number n
            ? new BigDecimal(n.toString()) : BigDecimal.ZERO;
        String currency = (String) body.getOrDefault("currency", "CNY");
        String id = jdbc.queryForObject("""
            INSERT INTO partner_payments (
              tenant_id, partner_id, payment_no, currency, amount, status
            ) VALUES (
              current_setting('app.current_tenant_id')::uuid, ?::uuid, ?, ?, ?, 'DRAFT'
            )
            RETURNING id::text
            """, String.class,
            partnerId == null ? null : partnerId.toString(),
            paymentNo, currency, amount);
        // AP 付款的汇率快照：复刻 ACC 落库时 freeze 当时汇率
        moneySnapshotService.snapshot(TABLE, id, amount, currency);
        return Map.of("id", id, "payment_no", paymentNo);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String currentAudit = jdbc.queryForObject(
            "SELECT audit_status FROM partner_payments WHERE id = ?::uuid", String.class, id);
        FieldGate.FilterResult gate = fieldGate.filterAllowedFields(TABLE, currentAudit, body);
        if (!gate.rejected().isEmpty() && gate.allowed().isEmpty()) {
            throw ApiException.badRequest("付款已审核，字段不可修改: " + String.join(",", gate.rejected())
                + "；请先反审");
        }
        Map<String, Object> allowed = gate.allowed();
        jdbc.update("""
            UPDATE partner_payments SET
              payment_no = coalesce(?, payment_no),
              status     = coalesce(?, status)
            WHERE id = ?::uuid
            """, (String) allowed.get("payment_no"), (String) allowed.get("status"), id);
        return Map.of("id", id, "rejectedFields", gate.rejected());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        String auditStatus = jdbc.queryForObject(
            "SELECT audit_status FROM partner_payments WHERE id = ?::uuid", String.class, id);
        if ("AUDITED".equals(auditStatus)) {
            throw ApiException.badRequest("付款已审核，不能删除，请先反审");
        }
        cascadeChecker.checkBeforeDelete(TABLE, id);
        jdbc.update("DELETE FROM partner_payments WHERE id = ?::uuid", id);
        return Map.of("id", id, "deleted", true);
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("no", row.get("payment_no"));
        out.put("supplierName", row.get("partner_name"));
        // bankName 优先取关联的资金账户名；没绑定账户时回退到 payment_type
        out.put("bankName", row.get("bank_name") != null ? row.get("bank_name") : row.get("payment_type"));
        out.put("amount", row.get("amount"));
        out.put("theDate", json.value(row.get("paid_at") != null ? row.get("paid_at") : row.get("created_at")));
        out.put("remark", row.get("reference_no"));
        out.put("currency", row.get("currency"));
        out.put("status", row.get("status"));
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        out.put("auditName", row.get("audit_name"));
        return out;
    }
}

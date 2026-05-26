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

/** /api/acc/received-sms — 收款短信，半人工记录，含汇率快照。 */
@RestController
@RequestMapping("/api/acc/received-sms")
public class AccReceivedSmsController {
    private static final String TABLE = "acc_received_sms";
    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final CascadeChecker cascadeChecker;
    private final FieldGate fieldGate;
    private final MoneySnapshotService moneySnapshotService;

    public AccReceivedSmsController(JdbcTemplate jdbc, JsonSupport json,
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
        @RequestParam(required = false) String keyword
    ) {
        try {
            int limit = AccPaging.pageSize(pageSize);
            int offset = AccPaging.offset(page, pageSize);
            String search = keyword == null || keyword.isBlank() ? null : "%" + keyword + "%";
            Long total = jdbc.queryForObject("""
                SELECT count(*) FROM acc_received_sms
                WHERE (?::text IS NULL OR source_name ILIKE ? OR payer ILIKE ?)
                """, Long.class, search, search, search);
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT s.id::text AS id, s.source_name, s.account_no, s.payer, s.amount, s.currency,
                       s.sms_time, s.raw_content,
                       s.audit_status, s.audited_at, s.audit_name,
                       fa.account_name AS bank_name
                FROM acc_received_sms s
                LEFT JOIN financial_accounts fa ON fa.id = s.bank_account_id
                WHERE (?::text IS NULL OR s.source_name ILIKE ? OR s.payer ILIKE ?)
                ORDER BY s.sms_time DESC NULLS LAST, s.created_at DESC
                LIMIT ? OFFSET ?
                """, search, search, search, limit, offset);
            return AccPaging.result(rows.stream().map(this::project).toList(), total == null ? 0 : total);
        } catch (DataAccessException ex) {
            return AccPaging.result(List.of(), 0);
        }
    }

    @GetMapping("/{id}/raw")
    public Map<String, Object> raw(@PathVariable String id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM acc_received_sms WHERE id = ?::uuid LIMIT 1", id);
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        BigDecimal amount = body.get("amount") instanceof Number n
            ? new BigDecimal(n.toString()) : BigDecimal.ZERO;
        String currency = (String) body.getOrDefault("currency", "CNY");
        String id = jdbc.queryForObject("""
            INSERT INTO acc_received_sms (
              tenant_id, source_name, account_no, payer, amount, currency,
              sms_time, bank_account_id, raw_content
            ) VALUES (
              current_setting('app.current_tenant_id')::uuid, ?, ?, ?, ?, ?,
              ?::timestamptz, ?::uuid, ?
            )
            RETURNING id::text
            """, String.class,
            body.getOrDefault("name", body.get("source_name")),
            body.getOrDefault("account", body.get("account_no")),
            body.getOrDefault("pay", body.get("payer")),
            amount, currency,
            body.getOrDefault("time", body.get("sms_time")),
            body.get("bank_account_id"),
            body.get("raw_content"));
        moneySnapshotService.snapshot(TABLE, id, amount, currency);
        return Map.of("id", id);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String currentAudit = jdbc.queryForObject(
            "SELECT audit_status FROM acc_received_sms WHERE id = ?::uuid", String.class, id);
        FieldGate.FilterResult gate = fieldGate.filterAllowedFields(TABLE, currentAudit, body);
        if (!gate.rejected().isEmpty() && gate.allowed().isEmpty()) {
            throw ApiException.badRequest("收款短信已审核，字段不可修改: " + String.join(",", gate.rejected()) + "；请先反审");
        }
        Map<String, Object> allowed = gate.allowed();
        jdbc.update("UPDATE acc_received_sms SET raw_content = coalesce(?, raw_content) WHERE id = ?::uuid",
            (String) allowed.get("raw_content"), id);
        return Map.of("id", id, "rejectedFields", gate.rejected());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        String auditStatus = jdbc.queryForObject(
            "SELECT audit_status FROM acc_received_sms WHERE id = ?::uuid", String.class, id);
        if ("AUDITED".equals(auditStatus)) {
            throw ApiException.badRequest("收款短信已审核，不能删除，请先反审");
        }
        cascadeChecker.checkBeforeDelete(TABLE, id);
        jdbc.update("DELETE FROM acc_received_sms WHERE id = ?::uuid", id);
        return Map.of("id", id, "deleted", true);
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("name", row.get("source_name"));
        out.put("account", row.get("account_no"));
        out.put("pay", row.get("payer"));
        out.put("amount", row.get("amount"));
        out.put("currency", row.get("currency"));
        out.put("time", json.value(row.get("sms_time")));
        out.put("bankName", row.get("bank_name"));
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        out.put("auditName", row.get("audit_name"));
        return out;
    }
}

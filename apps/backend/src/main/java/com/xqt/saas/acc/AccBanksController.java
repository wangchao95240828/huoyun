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

/** /api/acc/banks — 复用 financial_accounts，只取 account_type='BANK' 的行作为银行账户视图。 */
@RestController
@RequestMapping("/api/acc/banks")
public class AccBanksController {
    private static final String TABLE = "financial_accounts";
    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final CascadeChecker cascadeChecker;
    private final FieldGate fieldGate;

    public AccBanksController(JdbcTemplate jdbc, JsonSupport json,
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
        @RequestParam(required = false) String keyword
    ) {
        try {
            int limit = AccPaging.pageSize(pageSize);
            int offset = AccPaging.offset(page, pageSize);
            String search = keyword == null || keyword.isBlank() ? null : "%" + keyword + "%";
            Long total = jdbc.queryForObject("""
                SELECT count(*) FROM financial_accounts
                WHERE account_type = 'BANK'
                  AND (?::text IS NULL OR account_name ILIKE ? OR bank_name ILIKE ?)
                """, Long.class, search, search, search);
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id::text AS id, account_name, bank_name, bank_account_no,
                       currency, balance, status, last_update, is_show,
                       audit_status, audited_at, audit_name
                FROM financial_accounts
                WHERE account_type = 'BANK'
                  AND (?::text IS NULL OR account_name ILIKE ? OR bank_name ILIKE ?)
                ORDER BY account_name
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
            "SELECT * FROM financial_accounts WHERE id = ?::uuid AND account_type='BANK' LIMIT 1", id);
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        BigDecimal deposit = body.get("deposit") instanceof Number n
            ? new BigDecimal(n.toString()) : BigDecimal.ZERO;
        String id = jdbc.queryForObject("""
            INSERT INTO financial_accounts (
              tenant_id, owner_type, account_type, account_name, bank_name,
              currency, balance, status, last_update, is_show
            ) VALUES (
              current_setting('app.current_tenant_id')::uuid, 'COMPANY', 'BANK',
              ?, ?, ?, ?, 'ACTIVE', now(), ?
            )
            RETURNING id::text
            """, String.class,
            body.get("name"),
            body.get("bank_name"),
            body.getOrDefault("currency", "CNY"),
            deposit,
            body.get("isShow") instanceof Boolean b ? b : true);
        return Map.of("id", id);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String currentAudit = jdbc.queryForObject(
            "SELECT audit_status FROM financial_accounts WHERE id = ?::uuid", String.class, id);
        FieldGate.FilterResult gate = fieldGate.filterAllowedFields(TABLE, currentAudit, body);
        if (!gate.rejected().isEmpty() && gate.allowed().isEmpty()) {
            throw ApiException.badRequest("银行账户已审核，字段不可修改: " + String.join(",", gate.rejected())
                + "；请先反审");
        }
        Map<String, Object> allowed = gate.allowed();
        jdbc.update("""
            UPDATE financial_accounts SET
              account_name = coalesce(?, account_name),
              balance      = coalesce(?, balance),
              is_show      = coalesce(?, is_show),
              last_update  = now()
            WHERE id = ?::uuid AND account_type='BANK'
            """,
            (String) allowed.get("name"),
            allowed.get("deposit") instanceof Number n ? new BigDecimal(n.toString()) : null,
            allowed.get("isShow") instanceof Boolean b ? b : null,
            id);
        return Map.of("id", id, "rejectedFields", gate.rejected());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        String auditStatus = jdbc.queryForObject(
            "SELECT audit_status FROM financial_accounts WHERE id = ?::uuid", String.class, id);
        if ("AUDITED".equals(auditStatus)) {
            throw ApiException.badRequest("银行账户已审核，不能删除，请先反审");
        }
        cascadeChecker.checkBeforeDelete(TABLE, id);
        jdbc.update("DELETE FROM financial_accounts WHERE id = ?::uuid AND account_type='BANK'", id);
        return Map.of("id", id, "deleted", true);
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("name", row.get("account_name"));
        out.put("bankName", row.get("bank_name"));
        out.put("accountNo", row.get("bank_account_no"));
        out.put("currency", row.get("currency"));
        out.put("deposit", row.get("balance"));
        out.put("remark", "");
        out.put("status", row.get("status"));
        out.put("lastUpdate", json.value(row.get("last_update")));
        out.put("isShow", row.get("is_show"));
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        out.put("auditName", row.get("audit_name"));
        return out;
    }
}

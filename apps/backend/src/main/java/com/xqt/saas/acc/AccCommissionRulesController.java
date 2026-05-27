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

@RestController
@RequestMapping("/api/acc/commission-rules")
public class AccCommissionRulesController {
    private static final String TABLE = "acc_commission_rules";

    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final CascadeChecker cascadeChecker;
    private final FieldGate fieldGate;

    public AccCommissionRulesController(JdbcTemplate jdbc, JsonSupport json,
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

            long total = json.value(jdbc.queryForObject(
                "SELECT count(*) FROM acc_commission_rules WHERE ?::text IS NULL OR name ILIKE ?",
                Long.class, search, search)) instanceof Number n ? n.longValue() : 0;
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id::text AS id, name, rule_type, percent, amount, sales, profit, remark,
                       audit_status, audited_at, audit_name, created_at
                FROM acc_commission_rules
                WHERE ?::text IS NULL OR name ILIKE ?
                ORDER BY name
                LIMIT ? OFFSET ?
                """, search, search, limit, offset);
            return AccPaging.result(rows.stream().map(this::project).toList(), total);
        } catch (DataAccessException ex) {
            return AccPaging.result(List.of(), 0);
        }
    }

    @GetMapping("/{id}/raw")
    public Map<String, Object> raw(@PathVariable String id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM acc_commission_rules WHERE id = ?::uuid LIMIT 1", id);
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        String id = jdbc.queryForObject("""
            INSERT INTO acc_commission_rules (tenant_id, name, rule_type, percent, amount, sales, profit, remark)
            VALUES (current_setting('app.current_tenant_id')::uuid, ?, ?::text, ?::numeric,
                    ?::numeric, ?::numeric, ?::numeric, ?::text)
            RETURNING id::text
            """, String.class, name,
            body.get("ruleType"), body.get("percent"), body.get("amount"),
            body.get("sales"), body.get("profit"), body.get("remark"));
        return Map.of("id", id, "name", name);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String currentAudit = jdbc.queryForObject(
            "SELECT audit_status FROM acc_commission_rules WHERE id = ?::uuid", String.class, id);
        FieldGate.FilterResult gate = fieldGate.filterAllowedFields(TABLE, currentAudit, body);
        if (!gate.rejected().isEmpty() && gate.allowed().isEmpty()) {
            throw ApiException.badRequest("提成规则已审核，字段不可修改: " + String.join(",", gate.rejected())
                + "；请先反审");
        }
        Map<String, Object> allowed = gate.allowed();
        jdbc.update("""
            UPDATE acc_commission_rules SET
              name = coalesce(?, name),
              rule_type = coalesce(?::text, rule_type),
              percent = coalesce(?::numeric, percent),
              amount = coalesce(?::numeric, amount),
              sales = coalesce(?::numeric, sales),
              profit = coalesce(?::numeric, profit),
              remark = coalesce(?::text, remark)
            WHERE id = ?::uuid
            """, (String) allowed.get("name"), (String) allowed.get("ruleType"),
            allowed.get("percent"), allowed.get("amount"),
            allowed.get("sales"), allowed.get("profit"), (String) allowed.get("remark"), id);
        return Map.of("id", id, "rejectedFields", gate.rejected());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        String auditStatus = jdbc.queryForObject(
            "SELECT audit_status FROM acc_commission_rules WHERE id = ?::uuid", String.class, id);
        if ("AUDITED".equals(auditStatus)) {
            throw ApiException.badRequest("提成规则已审核，不能删除，请先反审");
        }
        cascadeChecker.checkBeforeDelete(TABLE, id);
        jdbc.update("DELETE FROM acc_commission_rules WHERE id = ?::uuid", id);
        return Map.of("id", id, "deleted", true);
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("name", row.get("name"));
        out.put("ruleType", row.get("rule_type"));
        out.put("percent", row.get("percent"));
        out.put("amount", row.get("amount"));
        out.put("sales", row.get("sales"));
        out.put("profit", row.get("profit"));
        out.put("remark", row.get("remark"));
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        out.put("auditName", row.get("audit_name"));
        return out;
    }
}

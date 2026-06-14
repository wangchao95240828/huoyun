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

@RestController
@RequestMapping("/api/acc/commissions")
public class AccCommissionsController {
    private static final String TABLE = "acc_commissions";

    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final CascadeChecker cascadeChecker;
    private final FieldGate fieldGate;
    private final MoneySnapshotService moneySnapshotService;

    public AccCommissionsController(JdbcTemplate jdbc, JsonSupport json,
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

            long total = json.value(jdbc.queryForObject(
                "SELECT count(*) FROM acc_commissions WHERE ?::text IS NULL",
                Long.class, search)) instanceof Number n ? n.longValue() : 0;
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT c.id::text AS id, c.employee_id::text AS employee_id, c.rule_id::text AS rule_id,
                       c.the_month, c.amount, c.currency, c.sales_amount, c.profit_amount, c.status,
                       c.remark, e.name AS employee_name, e.emp_no,
                       r.name AS rule_name,
                       c.audit_status, c.audited_at, c.audit_name, c.created_at
                FROM acc_commissions c
                JOIN acc_employees e ON e.id = c.employee_id
                LEFT JOIN acc_commission_rules r ON r.id = c.rule_id
                WHERE ?::text IS NULL OR (e.name ILIKE ? OR e.emp_no ILIKE ? OR c.the_month ILIKE ?)
                ORDER BY c.the_month DESC, e.name
                LIMIT ? OFFSET ?
                """, search, search, search, search, limit, offset);
            return AccPaging.result(rows.stream().map(this::project).toList(), total);
        } catch (DataAccessException ex) {
            return AccPaging.result(List.of(), 0);
        }
    }

    @GetMapping("/{id}/raw")
    public Map<String, Object> raw(@PathVariable String id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM acc_commissions WHERE id = ?::uuid LIMIT 1", id);
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String employeeId = (String) body.get("employeeId");
        String theMonth = (String) body.get("theMonth");
        if (employeeId == null || employeeId.isBlank()) {
            throw ApiException.badRequest("请选择员工");
        }
        if (theMonth == null || !theMonth.matches("\\d{4}-\\d{2}")) {
            throw ApiException.badRequest("月份格式错误，应为 YYYY-MM");
        }
        Object amt = body.get("amount");
        if (amt instanceof Number an && an.doubleValue() <= 0) {
            throw ApiException.badRequest("提成金额必须大于零");
        }
        String id = jdbc.queryForObject("""
            INSERT INTO acc_commissions (tenant_id, employee_id, rule_id, the_month, amount, currency,
                                         sales_amount, profit_amount, status, remark)
            VALUES (current_setting('app.current_tenant_id')::uuid, ?::uuid, ?::uuid, ?, ?::numeric,
                    ?::text, ?::numeric, ?::numeric, ?::text, ?::text)
            RETURNING id::text
            """, String.class, employeeId, body.get("ruleId"), theMonth,
            body.get("amount"), body.get("currency"), body.get("salesAmount"),
            body.get("profitAmount"), body.get("status"), body.get("remark"));
        BigDecimal amount = body.get("amount") instanceof Number n
            ? BigDecimal.valueOf(n.doubleValue()) : null;
        String currency = body.get("currency") != null ? body.get("currency").toString() : "CNY";
        moneySnapshotService.snapshot(TABLE, id, amount, currency);
        return Map.of("id", id, "employeeId", employeeId, "theMonth", theMonth);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String currentAudit = jdbc.queryForObject(
            "SELECT audit_status FROM acc_commissions WHERE id = ?::uuid", String.class, id);
        FieldGate.FilterResult gate = fieldGate.filterAllowedFields(TABLE, currentAudit, body);
        if (!gate.rejected().isEmpty() && gate.allowed().isEmpty()) {
            throw ApiException.badRequest("提成已审核，字段不可修改: " + String.join(",", gate.rejected())
                + "；请先反审");
        }
        Map<String, Object> allowed = gate.allowed();
        jdbc.update("""
            UPDATE acc_commissions SET
              amount = coalesce(?::numeric, amount),
              sales_amount = coalesce(?::numeric, sales_amount),
              profit_amount = coalesce(?::numeric, profit_amount),
              status = coalesce(?::text, status),
              remark = coalesce(?::text, remark)
            WHERE id = ?::uuid
            """, allowed.get("amount"), allowed.get("salesAmount"),
            allowed.get("profitAmount"), (String) allowed.get("status"),
            (String) allowed.get("remark"), id);
        if (allowed.containsKey("amount")) {
            Map<String, Object> row = jdbc.queryForMap(
                "SELECT amount, currency FROM acc_commissions WHERE id = ?::uuid", id);
            BigDecimal newAmount = row.get("amount") instanceof Number n
                ? BigDecimal.valueOf(n.doubleValue()) : null;
            String currency = row.get("currency") != null ? row.get("currency").toString() : "CNY";
            moneySnapshotService.snapshot(TABLE, id, newAmount, currency);
        }
        return Map.of("id", id, "rejectedFields", gate.rejected());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        String auditStatus = jdbc.queryForObject(
            "SELECT audit_status FROM acc_commissions WHERE id = ?::uuid", String.class, id);
        if ("AUDITED".equals(auditStatus)) {
            throw ApiException.badRequest("提成已审核，不能删除，请先反审");
        }
        cascadeChecker.checkBeforeDelete(TABLE, id);
        jdbc.update("DELETE FROM acc_commissions WHERE id = ?::uuid", id);
        return Map.of("id", id, "deleted", true);
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("employeeId", row.get("employee_id"));
        out.put("employeeName", row.get("employee_name"));
        out.put("empNo", row.get("emp_no"));
        out.put("ruleId", row.get("rule_id"));
        out.put("ruleName", row.get("rule_name"));
        out.put("theMonth", row.get("the_month"));
        out.put("amount", row.get("amount"));
        out.put("currency", row.get("currency"));
        out.put("salesAmount", row.get("sales_amount"));
        out.put("profitAmount", row.get("profit_amount"));
        out.put("status", row.get("status"));
        out.put("remark", row.get("remark"));
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        out.put("auditName", row.get("audit_name"));
        return out;
    }
}

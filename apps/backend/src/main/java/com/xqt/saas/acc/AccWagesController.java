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
@RequestMapping("/api/acc/wages")
public class AccWagesController {
    private static final String TABLE = "acc_wages";

    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final CascadeChecker cascadeChecker;
    private final FieldGate fieldGate;
    private final MoneySnapshotService moneySnapshotService;

    public AccWagesController(JdbcTemplate jdbc, JsonSupport json,
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
                "SELECT count(*) FROM acc_wages WHERE ?::text IS NULL",
                Long.class, search)) instanceof Number n ? n.longValue() : 0;
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT w.id::text AS id, w.employee_id::text AS employee_id, w.the_month,
                       w.basic, w.bonus, w.commission, w.deduction, w.total, w.currency,
                       w.remark, e.name AS employee_name, e.emp_no,
                       w.audit_status, w.audited_at, w.audit_name, w.created_at
                FROM acc_wages w
                JOIN acc_employees e ON e.id = w.employee_id
                WHERE ?::text IS NULL OR (e.name ILIKE ? OR e.emp_no ILIKE ? OR w.the_month ILIKE ?)
                ORDER BY w.the_month DESC, e.name
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
            "SELECT * FROM acc_wages WHERE id = ?::uuid LIMIT 1", id);
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String employeeId = (String) body.get("employeeId");
        String theMonth = (String) body.get("theMonth");
        String id = jdbc.queryForObject("""
            INSERT INTO acc_wages (tenant_id, employee_id, the_month, basic, bonus, commission, deduction, total, currency, remark)
            VALUES (current_setting('app.current_tenant_id')::uuid, ?::uuid, ?, ?::numeric, ?::numeric,
                    ?::numeric, ?::numeric, ?::numeric, ?::text, ?::text)
            RETURNING id::text
            """, String.class, employeeId, theMonth,
            body.get("basic"), body.get("bonus"), body.get("commission"),
            body.get("deduction"), body.get("total"), body.get("currency"), body.get("remark"));
        BigDecimal total = body.get("total") instanceof Number n
            ? BigDecimal.valueOf(n.doubleValue()) : null;
        String currency = body.get("currency") != null ? body.get("currency").toString() : "CNY";
        moneySnapshotService.snapshot(TABLE, id, total, currency);
        return Map.of("id", id, "employeeId", employeeId, "theMonth", theMonth);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String currentAudit = jdbc.queryForObject(
            "SELECT audit_status FROM acc_wages WHERE id = ?::uuid", String.class, id);
        FieldGate.FilterResult gate = fieldGate.filterAllowedFields(TABLE, currentAudit, body);
        if (!gate.rejected().isEmpty() && gate.allowed().isEmpty()) {
            throw ApiException.badRequest("工资已审核，字段不可修改: " + String.join(",", gate.rejected())
                + "；请先反审");
        }
        Map<String, Object> allowed = gate.allowed();
        jdbc.update("""
            UPDATE acc_wages SET
              basic = coalesce(?::numeric, basic),
              bonus = coalesce(?::numeric, bonus),
              commission = coalesce(?::numeric, commission),
              deduction = coalesce(?::numeric, deduction),
              total = coalesce(?::numeric, total),
              remark = coalesce(?::text, remark)
            WHERE id = ?::uuid
            """, allowed.get("basic"), allowed.get("bonus"), allowed.get("commission"),
            allowed.get("deduction"), allowed.get("total"), allowed.get("remark"), id);
        if (allowed.containsKey("total")) {
            Map<String, Object> row = jdbc.queryForMap(
                "SELECT total, currency FROM acc_wages WHERE id = ?::uuid", id);
            BigDecimal newTotal = row.get("total") instanceof Number n
                ? BigDecimal.valueOf(n.doubleValue()) : null;
            String currency = row.get("currency") != null ? row.get("currency").toString() : "CNY";
            moneySnapshotService.snapshot(TABLE, id, newTotal, currency);
        }
        return Map.of("id", id, "rejectedFields", gate.rejected());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        String auditStatus = jdbc.queryForObject(
            "SELECT audit_status FROM acc_wages WHERE id = ?::uuid", String.class, id);
        if ("AUDITED".equals(auditStatus)) {
            throw ApiException.badRequest("工资已审核，不能删除，请先反审");
        }
        cascadeChecker.checkBeforeDelete(TABLE, id);
        jdbc.update("DELETE FROM acc_wages WHERE id = ?::uuid", id);
        return Map.of("id", id, "deleted", true);
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("employeeId", row.get("employee_id"));
        out.put("employeeName", row.get("employee_name"));
        out.put("empNo", row.get("emp_no"));
        out.put("theMonth", row.get("the_month"));
        out.put("basic", row.get("basic"));
        out.put("bonus", row.get("bonus"));
        out.put("commission", row.get("commission"));
        out.put("deduction", row.get("deduction"));
        out.put("total", row.get("total"));
        out.put("currency", row.get("currency"));
        out.put("remark", row.get("remark"));
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        out.put("auditName", row.get("audit_name"));
        return out;
    }
}

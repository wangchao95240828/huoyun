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
@RequestMapping("/api/acc/employees")
public class AccEmployeesController {
    private static final String TABLE = "acc_employees";

    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final CascadeChecker cascadeChecker;
    private final FieldGate fieldGate;

    public AccEmployeesController(JdbcTemplate jdbc, JsonSupport json,
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
                "SELECT count(*) FROM acc_employees WHERE ?::text IS NULL OR (emp_no ILIKE ? OR name ILIKE ?)",
                Long.class, search, search, search)) instanceof Number n ? n.longValue() : 0;
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id::text AS id, emp_no, name, gender, mobile, position, status,
                       entry_date, audit_status, audited_at, audit_name, created_at
                FROM acc_employees
                WHERE ?::text IS NULL OR (emp_no ILIKE ? OR name ILIKE ?)
                ORDER BY emp_no
                LIMIT ? OFFSET ?
                """, search, search, search, limit, offset);
            return AccPaging.result(rows.stream().map(this::project).toList(), total);
        } catch (DataAccessException ex) {
            return AccPaging.result(List.of(), 0);
        }
    }

    @GetMapping("/{id}/raw")
    public Map<String, Object> raw(@PathVariable String id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM acc_employees WHERE id = ?::uuid LIMIT 1", id);
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String empNo = (String) body.get("empNo");
        String name = (String) body.get("name");
        String id = jdbc.queryForObject("""
            INSERT INTO acc_employees (tenant_id, emp_no, name, gender, mobile, branch_id, department_id, position, status, entry_date)
            VALUES (current_setting('app.current_tenant_id')::uuid, ?, ?, ?::text, ?::text,
                    ?::uuid, ?::uuid, ?::text, ?::text, ?::date)
            RETURNING id::text
            """, String.class, empNo, name,
            body.get("gender"), body.get("mobile"),
            body.get("branchId"), body.get("departmentId"),
            body.get("position"), body.get("status"), body.get("entryDate"));
        return Map.of("id", id, "empNo", empNo, "name", name);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String currentAudit = jdbc.queryForObject(
            "SELECT audit_status FROM acc_employees WHERE id = ?::uuid", String.class, id);
        FieldGate.FilterResult gate = fieldGate.filterAllowedFields(TABLE, currentAudit, body);
        if (!gate.rejected().isEmpty() && gate.allowed().isEmpty()) {
            throw ApiException.badRequest("员工已审核，字段不可修改: " + String.join(",", gate.rejected())
                + "；请先反审");
        }
        Map<String, Object> allowed = gate.allowed();
        jdbc.update("""
            UPDATE acc_employees SET
              name = coalesce(?, name),
              gender = coalesce(?::text, gender),
              mobile = coalesce(?::text, mobile),
              position = coalesce(?::text, position),
              status = coalesce(?::text, status),
              entry_date = coalesce(?::date, entry_date)
            WHERE id = ?::uuid
            """, (String) allowed.get("name"), (String) allowed.get("gender"),
            (String) allowed.get("mobile"), (String) allowed.get("position"),
            (String) allowed.get("status"), (String) allowed.get("entryDate"), id);
        return Map.of("id", id, "rejectedFields", gate.rejected());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        String auditStatus = jdbc.queryForObject(
            "SELECT audit_status FROM acc_employees WHERE id = ?::uuid", String.class, id);
        if ("AUDITED".equals(auditStatus)) {
            throw ApiException.badRequest("员工已审核，不能删除，请先反审");
        }
        cascadeChecker.checkBeforeDelete(TABLE, id);
        jdbc.update("DELETE FROM acc_employees WHERE id = ?::uuid", id);
        return Map.of("id", id, "deleted", true);
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("empNo", row.get("emp_no"));
        out.put("name", row.get("name"));
        out.put("gender", row.get("gender"));
        out.put("mobile", row.get("mobile"));
        out.put("position", row.get("position"));
        out.put("status", row.get("status"));
        out.put("entryDate", row.get("entry_date"));
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        out.put("auditName", row.get("audit_name"));
        return out;
    }
}

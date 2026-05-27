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
@RequestMapping("/api/acc/attendances")
public class AccAttendancesController {
    private static final String TABLE = "acc_attendances";

    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final CascadeChecker cascadeChecker;
    private final FieldGate fieldGate;

    public AccAttendancesController(JdbcTemplate jdbc, JsonSupport json,
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
                "SELECT count(*) FROM acc_attendances WHERE ?::text IS NULL",
                Long.class, search)) instanceof Number n ? n.longValue() : 0;
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT a.id::text AS id, a.employee_id::text AS employee_id, a.the_date, a.status,
                       a.sign_in_time, a.sign_out_time, a.remark,
                       e.name AS employee_name, e.emp_no,
                       a.audit_status, a.audited_at, a.audit_name, a.created_at
                FROM acc_attendances a
                JOIN acc_employees e ON e.id = a.employee_id
                WHERE ?::text IS NULL OR (e.name ILIKE ? OR e.emp_no ILIKE ?)
                ORDER BY a.the_date DESC, e.name
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
            "SELECT * FROM acc_attendances WHERE id = ?::uuid LIMIT 1", id);
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String employeeId = (String) body.get("employeeId");
        String theDate = (String) body.get("theDate");
        String id = jdbc.queryForObject("""
            INSERT INTO acc_attendances (tenant_id, employee_id, the_date, status, sign_in_time, sign_out_time, remark)
            VALUES (current_setting('app.current_tenant_id')::uuid, ?::uuid, ?::date, ?::text, ?::time, ?::time, ?::text)
            RETURNING id::text
            """, String.class, employeeId, theDate,
            body.get("status"), body.get("signInTime"), body.get("signOutTime"), body.get("remark"));
        return Map.of("id", id, "employeeId", employeeId, "theDate", theDate);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String currentAudit = jdbc.queryForObject(
            "SELECT audit_status FROM acc_attendances WHERE id = ?::uuid", String.class, id);
        FieldGate.FilterResult gate = fieldGate.filterAllowedFields(TABLE, currentAudit, body);
        if (!gate.rejected().isEmpty() && gate.allowed().isEmpty()) {
            throw ApiException.badRequest("考勤已审核，字段不可修改: " + String.join(",", gate.rejected())
                + "；请先反审");
        }
        Map<String, Object> allowed = gate.allowed();
        jdbc.update("""
            UPDATE acc_attendances SET
              status = coalesce(?::text, status),
              sign_in_time = coalesce(?::time, sign_in_time),
              sign_out_time = coalesce(?::time, sign_out_time),
              remark = coalesce(?::text, remark)
            WHERE id = ?::uuid
            """, (String) allowed.get("status"), (String) allowed.get("signInTime"),
            (String) allowed.get("signOutTime"), (String) allowed.get("remark"), id);
        return Map.of("id", id, "rejectedFields", gate.rejected());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        String auditStatus = jdbc.queryForObject(
            "SELECT audit_status FROM acc_attendances WHERE id = ?::uuid", String.class, id);
        if ("AUDITED".equals(auditStatus)) {
            throw ApiException.badRequest("考勤已审核，不能删除，请先反审");
        }
        cascadeChecker.checkBeforeDelete(TABLE, id);
        jdbc.update("DELETE FROM acc_attendances WHERE id = ?::uuid", id);
        return Map.of("id", id, "deleted", true);
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("employeeId", row.get("employee_id"));
        out.put("employeeName", row.get("employee_name"));
        out.put("empNo", row.get("emp_no"));
        out.put("theDate", row.get("the_date"));
        out.put("status", row.get("status"));
        out.put("signInTime", row.get("sign_in_time"));
        out.put("signOutTime", row.get("sign_out_time"));
        out.put("remark", row.get("remark"));
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        out.put("auditName", row.get("audit_name"));
        return out;
    }
}

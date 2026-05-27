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
@RequestMapping("/api/acc/fund-persons")
public class AccFundPersonsController {
    private static final String TABLE = "acc_fund_persons";

    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final CascadeChecker cascadeChecker;
    private final FieldGate fieldGate;

    public AccFundPersonsController(JdbcTemplate jdbc, JsonSupport json,
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
                "SELECT count(*) FROM acc_fund_persons WHERE ?::text IS NULL",
                Long.class, search)) instanceof Number n ? n.longValue() : 0;
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT fp.id::text AS id, fp.fund_id::text AS fund_id, fp.employee_id::text AS employee_id,
                       fp.person_amount, fp.company_amount, fp.remark,
                       e.name AS employee_name, e.emp_no,
                       f.the_month,
                       fp.audit_status, fp.audited_at, fp.audit_name, fp.created_at
                FROM acc_fund_persons fp
                JOIN acc_employees e ON e.id = fp.employee_id
                JOIN acc_funds f ON f.id = fp.fund_id
                WHERE ?::text IS NULL OR (e.name ILIKE ? OR e.emp_no ILIKE ?)
                ORDER BY f.the_month DESC, e.name
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
            "SELECT * FROM acc_fund_persons WHERE id = ?::uuid LIMIT 1", id);
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String fundId = (String) body.get("fundId");
        String employeeId = (String) body.get("employeeId");
        String id = jdbc.queryForObject("""
            INSERT INTO acc_fund_persons (tenant_id, fund_id, employee_id, person_amount, company_amount, remark)
            VALUES (current_setting('app.current_tenant_id')::uuid, ?::uuid, ?::uuid,
                    ?::numeric, ?::numeric, ?::text)
            RETURNING id::text
            """, String.class, fundId, employeeId,
            body.get("personAmount"), body.get("companyAmount"), body.get("remark"));
        return Map.of("id", id, "fundId", fundId, "employeeId", employeeId);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String currentAudit = jdbc.queryForObject(
            "SELECT audit_status FROM acc_fund_persons WHERE id = ?::uuid", String.class, id);
        FieldGate.FilterResult gate = fieldGate.filterAllowedFields(TABLE, currentAudit, body);
        if (!gate.rejected().isEmpty() && gate.allowed().isEmpty()) {
            throw ApiException.badRequest("公积金人员已审核，字段不可修改: " + String.join(",", gate.rejected())
                + "；请先反审");
        }
        Map<String, Object> allowed = gate.allowed();
        jdbc.update("""
            UPDATE acc_fund_persons SET
              person_amount = coalesce(?::numeric, person_amount),
              company_amount = coalesce(?::numeric, company_amount),
              remark = coalesce(?::text, remark)
            WHERE id = ?::uuid
            """, allowed.get("personAmount"), allowed.get("companyAmount"),
            (String) allowed.get("remark"), id);
        return Map.of("id", id, "rejectedFields", gate.rejected());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        String auditStatus = jdbc.queryForObject(
            "SELECT audit_status FROM acc_fund_persons WHERE id = ?::uuid", String.class, id);
        if ("AUDITED".equals(auditStatus)) {
            throw ApiException.badRequest("公积金人员已审核，不能删除，请先反审");
        }
        cascadeChecker.checkBeforeDelete(TABLE, id);
        jdbc.update("DELETE FROM acc_fund_persons WHERE id = ?::uuid", id);
        return Map.of("id", id, "deleted", true);
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("fundId", row.get("fund_id"));
        out.put("employeeId", row.get("employee_id"));
        out.put("employeeName", row.get("employee_name"));
        out.put("empNo", row.get("emp_no"));
        out.put("theMonth", row.get("the_month"));
        out.put("personAmount", row.get("person_amount"));
        out.put("companyAmount", row.get("company_amount"));
        out.put("remark", row.get("remark"));
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        out.put("auditName", row.get("audit_name"));
        return out;
    }
}

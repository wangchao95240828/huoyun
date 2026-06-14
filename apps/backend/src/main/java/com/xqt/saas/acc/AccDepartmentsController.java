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

/**
 * /api/acc/departments — 前端列：name / branchName / remark
 * 来源 organizations WHERE org_type='department'，branchName 由 parent_id 自连接得到。
 */
@RestController
@RequestMapping("/api/acc/departments")
public class AccDepartmentsController {
    private static final String TABLE = "organizations";

    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final CascadeChecker cascadeChecker;
    private final FieldGate fieldGate;

    public AccDepartmentsController(JdbcTemplate jdbc, JsonSupport json,
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
                SELECT count(*) FROM organizations
                WHERE org_type = 'department'
                  AND (?::text IS NULL OR code ILIKE ? OR name ILIKE ?)
                """, Long.class, search, search, search);
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT d.id::text AS id, d.code, d.name, d.is_active,
                       d.audit_status, d.audited_at, d.audit_name,
                       p.name AS branch_name
                FROM organizations d
                LEFT JOIN organizations p ON p.id = d.parent_id
                WHERE d.org_type = 'department'
                  AND (?::text IS NULL OR d.code ILIKE ? OR d.name ILIKE ?)
                ORDER BY d.code
                LIMIT ? OFFSET ?
                """, search, search, search, limit, offset);
            return AccPaging.result(rows.stream().map(this::project).toList(),
                total == null ? 0 : total);
        } catch (DataAccessException ex) {
            return AccPaging.result(List.of(), 0);
        }
    }

    @GetMapping("/{id}/raw")
    public Map<String, Object> raw(@PathVariable String id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM organizations WHERE id = ?::uuid LIMIT 1", id);
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String code = (String) body.get("code");
        String name = (String) body.get("name");
        Object parentId = body.get("parent_id");
        if (code == null || code.isBlank()) throw ApiException.badRequest("部门编号必填");
        if (name == null || name.isBlank()) throw ApiException.badRequest("部门名称必填");
        String id = jdbc.queryForObject("""
            INSERT INTO organizations (tenant_id, parent_id, code, name, org_type)
            VALUES (current_setting('app.current_tenant_id')::uuid, ?::uuid, ?, ?, 'department')
            RETURNING id::text
            """, String.class,
            parentId == null ? null : parentId.toString(), code, name);
        return Map.of("id", id, "code", code, "name", name);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String currentAudit = jdbc.queryForObject(
            "SELECT audit_status FROM organizations WHERE id = ?::uuid AND org_type='department'",
            String.class, id);
        FieldGate.FilterResult gate = fieldGate.filterAllowedFields(TABLE, currentAudit, body);
        if (!gate.rejected().isEmpty() && gate.allowed().isEmpty()) {
            throw ApiException.badRequest("部门已审核，字段不可修改: " + String.join(",", gate.rejected())
                + "；请先反审");
        }
        Map<String, Object> allowed = gate.allowed();
        jdbc.update("""
            UPDATE organizations SET
              code      = coalesce(?, code),
              name      = coalesce(?, name),
              parent_id = coalesce(?::uuid, parent_id)
            WHERE id = ?::uuid AND org_type = 'department'
            """,
            (String) allowed.get("code"), (String) allowed.get("name"),
            allowed.get("parent_id") == null ? null : allowed.get("parent_id").toString(),
            id);
        return Map.of("id", id, "rejectedFields", gate.rejected());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        String auditStatus = jdbc.queryForObject(
            "SELECT audit_status FROM organizations WHERE id = ?::uuid AND org_type='department'",
            String.class, id);
        if ("AUDITED".equals(auditStatus)) {
            throw ApiException.badRequest("部门已审核，不能删除，请先反审");
        }
        cascadeChecker.checkBeforeDelete(TABLE, id);
        jdbc.update("DELETE FROM organizations WHERE id = ?::uuid AND org_type = 'department'", id);
        return Map.of("id", id, "deleted", true);
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("name", row.get("name"));
        out.put("code", row.get("code"));
        out.put("branchName", row.get("branch_name"));
        out.put("remark", "");
        out.put("isActive", row.get("is_active"));
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        out.put("auditName", row.get("audit_name"));
        return out;
    }
}

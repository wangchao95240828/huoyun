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
 * /api/acc/branches — 前端 ACC tab "acc-branches"，列：name / code / contact / phone / address / remark
 * 来源 organizations WHERE org_type='branch'。contact/phone/address/remark 新模型未建模，先空值。
 */
@RestController
@RequestMapping("/api/acc/branches")
public class AccBranchesController {
    private static final String TABLE = "organizations";

    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final CascadeChecker cascadeChecker;
    private final FieldGate fieldGate;

    public AccBranchesController(JdbcTemplate jdbc, JsonSupport json,
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
                WHERE org_type IN ('branch', 'hq')
                  AND (?::text IS NULL OR code ILIKE ? OR name ILIKE ?)
                """, Long.class, search, search, search);
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id::text AS id, code, name, org_type, is_active,
                       contact, phone, address, remark,
                       audit_status, audited_at, audit_name
                FROM organizations
                WHERE org_type IN ('branch', 'hq')
                  AND (?::text IS NULL OR code ILIKE ? OR name ILIKE ?)
                ORDER BY org_type, code
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
        if (code == null || code.isBlank()) throw ApiException.badRequest("分公司编号必填");
        if (name == null || name.isBlank()) throw ApiException.badRequest("分公司名称必填");
        String id = jdbc.queryForObject("""
            INSERT INTO organizations (
              tenant_id, code, name, org_type, contact, phone, address, remark
            ) VALUES (
              current_setting('app.current_tenant_id')::uuid, ?, ?, 'branch', ?, ?, ?, ?
            )
            RETURNING id::text
            """, String.class, code, name,
            body.get("contact"), body.get("phone"), body.get("address"), body.get("remark"));
        return Map.of("id", id, "code", code, "name", name);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String currentAudit = jdbc.queryForObject(
            "SELECT audit_status FROM organizations WHERE id = ?::uuid AND org_type IN ('branch','hq')",
            String.class, id);
        FieldGate.FilterResult gate = fieldGate.filterAllowedFields(TABLE, currentAudit, body);
        if (!gate.rejected().isEmpty() && gate.allowed().isEmpty()) {
            throw ApiException.badRequest("分支机构已审核，字段不可修改: " + String.join(",", gate.rejected())
                + "；请先反审");
        }
        Map<String, Object> allowed = gate.allowed();
        jdbc.update("""
            UPDATE organizations SET
              code    = coalesce(?, code),
              name    = coalesce(?, name),
              contact = coalesce(?, contact),
              phone   = coalesce(?, phone),
              address = coalesce(?, address),
              remark  = coalesce(?, remark)
            WHERE id = ?::uuid AND org_type IN ('branch', 'hq')
            """,
            (String) allowed.get("code"), (String) allowed.get("name"),
            (String) allowed.get("contact"), (String) allowed.get("phone"),
            (String) allowed.get("address"), (String) allowed.get("remark"), id);
        return Map.of("id", id, "rejectedFields", gate.rejected());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        String auditStatus = jdbc.queryForObject(
            "SELECT audit_status FROM organizations WHERE id = ?::uuid AND org_type IN ('branch','hq')",
            String.class, id);
        if ("AUDITED".equals(auditStatus)) {
            throw ApiException.badRequest("分支机构已审核，不能删除，请先反审");
        }
        cascadeChecker.checkBeforeDelete(TABLE, id);
        jdbc.update("DELETE FROM organizations WHERE id = ?::uuid AND org_type IN ('branch', 'hq')", id);
        return Map.of("id", id, "deleted", true);
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("name", row.get("name"));
        out.put("code", row.get("code"));
        out.put("contact", row.get("contact") == null ? "" : row.get("contact"));
        out.put("phone", row.get("phone") == null ? "" : row.get("phone"));
        out.put("address", row.get("address") == null ? "" : row.get("address"));
        // remark 字段优先用 organizations.remark，回退到 org_type（旧行为）
        out.put("remark", row.get("remark") == null ? row.get("org_type") : row.get("remark"));
        out.put("isActive", row.get("is_active"));
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        out.put("auditName", row.get("audit_name"));
        return out;
    }
}

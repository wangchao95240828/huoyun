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

/** /api/acc/countries — 前端列：cn / name / code / isOpen。 */
@RestController
@RequestMapping("/api/acc/countries")
public class AccCountriesController {
    private static final String TABLE = "countries";
    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final CascadeChecker cascadeChecker;
    private final FieldGate fieldGate;

    public AccCountriesController(JdbcTemplate jdbc, JsonSupport json,
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
                SELECT count(*) FROM countries
                WHERE (?::text IS NULL OR code ILIKE ? OR en_name ILIKE ? OR cn_name ILIKE ?)
                """, Long.class, search, search, search, search);
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id::text AS id, code, code3, cn_name, en_name, name_tw, name_hk,
                       phone, sort_order, parent_id::text AS parent_id, is_open,
                       audit_status, audited_at, audit_name
                FROM countries
                WHERE (?::text IS NULL OR code ILIKE ? OR en_name ILIKE ? OR cn_name ILIKE ?)
                ORDER BY sort_order, code
                LIMIT ? OFFSET ?
                """, search, search, search, search, limit, offset);
            return AccPaging.result(rows.stream().map(this::project).toList(), total == null ? 0 : total);
        } catch (DataAccessException ex) {
            return AccPaging.result(List.of(), 0);
        }
    }

    @GetMapping("/{id}/raw")
    public Map<String, Object> raw(@PathVariable String id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM countries WHERE id = ?::uuid LIMIT 1", id);
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String id = jdbc.queryForObject("""
            INSERT INTO countries (tenant_id, code, code3, cn_name, en_name, is_open)
            VALUES (current_setting('app.current_tenant_id')::uuid, ?, ?, ?, ?, ?)
            RETURNING id::text
            """, String.class,
            body.get("code"), body.get("code3"),
            body.getOrDefault("cn", body.get("cn_name")),
            body.getOrDefault("name", body.get("en_name")),
            body.get("isOpen") instanceof Boolean b ? b : true);
        return Map.of("id", id);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String currentAudit = jdbc.queryForObject(
            "SELECT audit_status FROM countries WHERE id = ?::uuid", String.class, id);
        FieldGate.FilterResult gate = fieldGate.filterAllowedFields(TABLE, currentAudit, body);
        if (!gate.rejected().isEmpty() && gate.allowed().isEmpty()) {
            throw ApiException.badRequest("国家已审核，字段不可修改: " + String.join(",", gate.rejected())
                + "；请先反审");
        }
        Map<String, Object> allowed = gate.allowed();
        jdbc.update("""
            UPDATE countries SET
              code     = coalesce(?, code),
              cn_name  = coalesce(?, cn_name),
              en_name  = coalesce(?, en_name),
              is_open  = coalesce(?, is_open)
            WHERE id = ?::uuid
            """,
            (String) allowed.get("code"),
            (String) allowed.getOrDefault("cn", allowed.get("cn_name")),
            (String) allowed.getOrDefault("name", allowed.get("en_name")),
            allowed.get("isOpen") instanceof Boolean b ? b : null,
            id);
        return Map.of("id", id, "rejectedFields", gate.rejected());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        String auditStatus = jdbc.queryForObject(
            "SELECT audit_status FROM countries WHERE id = ?::uuid", String.class, id);
        if ("AUDITED".equals(auditStatus)) {
            throw ApiException.badRequest("国家已审核，不能删除，请先反审");
        }
        cascadeChecker.checkBeforeDelete(TABLE, id);
        jdbc.update("DELETE FROM countries WHERE id = ?::uuid", id);
        return Map.of("id", id, "deleted", true);
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("cn", row.get("cn_name"));
        out.put("name", row.get("en_name"));
        out.put("code", row.get("code"));
        out.put("code3", row.get("code3"));
        out.put("tw", row.get("name_tw"));
        out.put("hk", row.get("name_hk"));
        out.put("phone", row.get("phone"));
        out.put("sortOrder", row.get("sort_order"));
        out.put("parentId", row.get("parent_id"));
        out.put("isOpen", row.get("is_open"));
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        out.put("auditName", row.get("audit_name"));
        return out;
    }
}

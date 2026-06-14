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

/** /api/acc/districts — 前端列：name / cn / code2 / code3 / phone（旧 ACC 是国家代码+区号；新表用层级行政区，做最简映射）。 */
@RestController
@RequestMapping("/api/acc/districts")
public class AccDistrictsController {
    private static final String TABLE = "districts";
    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final CascadeChecker cascadeChecker;
    private final FieldGate fieldGate;

    public AccDistrictsController(JdbcTemplate jdbc, JsonSupport json,
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
                SELECT count(*) FROM districts
                WHERE (?::text IS NULL OR code ILIKE ? OR name ILIKE ?)
                """, Long.class, search, search, search);
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id::text AS id, code, name, level, parent_id::text AS parent_id,
                       audit_status, audited_at, audit_name
                FROM districts
                WHERE (?::text IS NULL OR code ILIKE ? OR name ILIKE ?)
                ORDER BY level, code
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
            "SELECT * FROM districts WHERE id = ?::uuid LIMIT 1", id);
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        Object code = body.get("code");
        Object name = body.getOrDefault("name", body.get("cn"));
        if (code == null || code.toString().isBlank()) throw ApiException.badRequest("行政区代码必填");
        if (name == null || name.toString().isBlank()) throw ApiException.badRequest("行政区名称必填");
        Object lvl = body.get("level");
        if (lvl instanceof Number ln && (ln.intValue() < 1 || ln.intValue() > 5)) {
            throw ApiException.badRequest("行政区级别必须在 1-5 之间");
        }
        String id = jdbc.queryForObject("""
            INSERT INTO districts (tenant_id, code, name, level, parent_id)
            VALUES (current_setting('app.current_tenant_id')::uuid, ?, ?, ?, ?::uuid)
            RETURNING id::text
            """, String.class,
            code, name,
            lvl instanceof Number n ? n.intValue() : 1,
            body.get("parent_id"));
        return Map.of("id", id);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String currentAudit = jdbc.queryForObject(
            "SELECT audit_status FROM districts WHERE id = ?::uuid", String.class, id);
        FieldGate.FilterResult gate = fieldGate.filterAllowedFields(TABLE, currentAudit, body);
        if (!gate.rejected().isEmpty() && gate.allowed().isEmpty()) {
            throw ApiException.badRequest("行政区已审核，字段不可修改: " + String.join(",", gate.rejected())
                + "；请先反审");
        }
        Map<String, Object> allowed = gate.allowed();
        jdbc.update("""
            UPDATE districts SET
              code = coalesce(?, code),
              name = coalesce(?, name)
            WHERE id = ?::uuid
            """,
            (String) allowed.get("code"),
            (String) allowed.getOrDefault("name", allowed.get("cn")),
            id);
        return Map.of("id", id, "rejectedFields", gate.rejected());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        String auditStatus = jdbc.queryForObject(
            "SELECT audit_status FROM districts WHERE id = ?::uuid", String.class, id);
        if ("AUDITED".equals(auditStatus)) {
            throw ApiException.badRequest("行政区已审核，不能删除，请先反审");
        }
        cascadeChecker.checkBeforeDelete(TABLE, id);
        jdbc.update("DELETE FROM districts WHERE id = ?::uuid", id);
        return Map.of("id", id, "deleted", true);
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("name", row.get("name"));    // 旧 ACC 这个是英文名；新模型只一个 name
        out.put("cn", row.get("name"));
        out.put("code2", row.get("code"));
        out.put("code3", row.get("code"));
        out.put("phone", "");                // 旧 ACC 的电话区号，新模型未建模
        out.put("level", row.get("level"));
        out.put("parentId", row.get("parent_id"));
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        out.put("auditName", row.get("audit_name"));
        return out;
    }
}

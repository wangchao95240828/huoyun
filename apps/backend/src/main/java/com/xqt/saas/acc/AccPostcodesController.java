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

/** /api/acc/postcodes — 前端列：postcode / country / province / city。 */
@RestController
@RequestMapping("/api/acc/postcodes")
public class AccPostcodesController {
    private static final String TABLE = "postcodes";
    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final CascadeChecker cascadeChecker;
    private final FieldGate fieldGate;

    public AccPostcodesController(JdbcTemplate jdbc, JsonSupport json,
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
                SELECT count(*) FROM postcodes
                WHERE (?::text IS NULL OR postcode ILIKE ? OR country_code ILIKE ? OR region ILIKE ?)
                """, Long.class, search, search, search, search);
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id::text AS id, country_code, postcode, region, city, state_code,
                       audit_status, audited_at, audit_name
                FROM postcodes
                WHERE (?::text IS NULL OR postcode ILIKE ? OR country_code ILIKE ? OR region ILIKE ?)
                ORDER BY country_code, postcode
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
            "SELECT * FROM postcodes WHERE id = ?::uuid LIMIT 1", id);
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        Object country = body.getOrDefault("country", body.get("country_code"));
        Object postcode = body.get("postcode");
        if (country == null || country.toString().isBlank()) {
            throw ApiException.badRequest("国家代码必填");
        }
        // ACC 派生：国家代码必须是 2 个大写字母
        if (!country.toString().matches("[A-Z]{2}")) {
            throw ApiException.badRequest("国家代码必须为 2 个大写字母 (ISO 3166-1 alpha-2)");
        }
        if (postcode == null || postcode.toString().isBlank()) {
            throw ApiException.badRequest("邮编必填");
        }
        String id = jdbc.queryForObject("""
            INSERT INTO postcodes (tenant_id, country_code, postcode, region, city, state_code)
            VALUES (current_setting('app.current_tenant_id')::uuid, ?, ?, ?, ?, ?)
            RETURNING id::text
            """, String.class,
            country,
            postcode,
            body.getOrDefault("province", body.get("region")),
            body.get("city"),
            body.get("state_code"));
        return Map.of("id", id);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String currentAudit = jdbc.queryForObject(
            "SELECT audit_status FROM postcodes WHERE id = ?::uuid", String.class, id);
        FieldGate.FilterResult gate = fieldGate.filterAllowedFields(TABLE, currentAudit, body);
        if (!gate.rejected().isEmpty() && gate.allowed().isEmpty()) {
            throw ApiException.badRequest("邮编已审核，字段不可修改: " + String.join(",", gate.rejected())
                + "；请先反审");
        }
        Map<String, Object> allowed = gate.allowed();
        jdbc.update("""
            UPDATE postcodes SET
              country_code = coalesce(?, country_code),
              postcode     = coalesce(?, postcode),
              region       = coalesce(?, region),
              city         = coalesce(?, city)
            WHERE id = ?::uuid
            """,
            (String) allowed.getOrDefault("country", allowed.get("country_code")),
            (String) allowed.get("postcode"),
            (String) allowed.getOrDefault("province", allowed.get("region")),
            (String) allowed.get("city"),
            id);
        return Map.of("id", id, "rejectedFields", gate.rejected());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        String auditStatus = jdbc.queryForObject(
            "SELECT audit_status FROM postcodes WHERE id = ?::uuid", String.class, id);
        if ("AUDITED".equals(auditStatus)) {
            throw ApiException.badRequest("邮编已审核，不能删除，请先反审");
        }
        cascadeChecker.checkBeforeDelete(TABLE, id);
        jdbc.update("DELETE FROM postcodes WHERE id = ?::uuid", id);
        return Map.of("id", id, "deleted", true);
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("postcode", row.get("postcode"));
        out.put("country", row.get("country_code"));
        out.put("province", row.get("region"));
        out.put("city", row.get("city"));
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        out.put("auditName", row.get("audit_name"));
        return out;
    }
}

package com.xqt.saas.acc;

import java.time.LocalDate;
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

/** /api/acc/remotes — 前端列：postcode / country / supplierName / type。来源 remote_zones + channels（作为 supplierName 代偿）。 */
@RestController
@RequestMapping("/api/acc/remotes")
public class AccRemotesController {
    private static final String TABLE = "remote_zones";

    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final CascadeChecker cascadeChecker;
    private final FieldGate fieldGate;

    public AccRemotesController(JdbcTemplate jdbc, JsonSupport json,
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
                SELECT count(*) FROM remote_zones rz
                LEFT JOIN channels cn ON cn.id = rz.channel_id
                WHERE (?::text IS NULL OR rz.postal_code_pattern ILIKE ? OR rz.country_code ILIKE ? OR cn.name ILIKE ?)
                """, Long.class, search, search, search, search);
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT rz.id::text AS id, rz.postal_code_pattern, rz.country_code,
                       rz.level::text AS level, rz.version, rz.effective_from,
                       rz.audit_status, rz.audited_at, rz.audit_name,
                       cn.name AS channel_name
                FROM remote_zones rz
                LEFT JOIN channels cn ON cn.id = rz.channel_id
                WHERE (?::text IS NULL OR rz.postal_code_pattern ILIKE ? OR rz.country_code ILIKE ? OR cn.name ILIKE ?)
                ORDER BY rz.country_code, rz.postal_code_pattern
                LIMIT ? OFFSET ?
                """, search, search, search, search, limit, offset);
            return AccPaging.result(rows.stream().map(this::project).toList(),
                total == null ? 0 : total);
        } catch (DataAccessException ex) {
            return AccPaging.result(List.of(), 0);
        }
    }

    @GetMapping("/{id}/raw")
    public Map<String, Object> raw(@PathVariable String id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM remote_zones WHERE id = ?::uuid LIMIT 1", id);
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        Object channelId = body.get("channel_id");
        String pattern = (String) body.getOrDefault("postcode", body.get("postal_code_pattern"));
        String country = (String) body.getOrDefault("country", body.get("country_code"));
        String level = (String) body.getOrDefault("type", body.getOrDefault("level", "REMOTE"));
        String id = jdbc.queryForObject("""
            INSERT INTO remote_zones (
              tenant_id, channel_id, version, country_code, postal_code_pattern, level, effective_from
            ) VALUES (
              current_setting('app.current_tenant_id')::uuid, ?::uuid, 'v1', ?, ?, ?::remote_level, ?::date
            )
            RETURNING id::text
            """, String.class,
            channelId == null ? null : channelId.toString(),
            country, pattern, level, LocalDate.now().toString());
        return Map.of("id", id);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String currentAudit = jdbc.queryForObject(
            "SELECT audit_status FROM remote_zones WHERE id = ?::uuid", String.class, id);
        FieldGate.FilterResult gate = fieldGate.filterAllowedFields(TABLE, currentAudit, body);
        if (!gate.rejected().isEmpty() && gate.allowed().isEmpty()) {
            throw ApiException.badRequest("偏远邮编已审核，字段不可修改: " + String.join(",", gate.rejected())
                + "；请先反审");
        }
        Map<String, Object> allowed = gate.allowed();
        jdbc.update("""
            UPDATE remote_zones SET
              country_code        = coalesce(?, country_code),
              postal_code_pattern = coalesce(?, postal_code_pattern),
              level               = coalesce(?::remote_level, level)
            WHERE id = ?::uuid
            """,
            (String) allowed.getOrDefault("country", allowed.get("country_code")),
            (String) allowed.getOrDefault("postcode", allowed.get("postal_code_pattern")),
            (String) allowed.getOrDefault("type", allowed.get("level")),
            id);
        return Map.of("id", id, "rejectedFields", gate.rejected());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        String auditStatus = jdbc.queryForObject(
            "SELECT audit_status FROM remote_zones WHERE id = ?::uuid", String.class, id);
        if ("AUDITED".equals(auditStatus)) {
            throw ApiException.badRequest("偏远邮编已审核，不能删除，请先反审");
        }
        cascadeChecker.checkBeforeDelete(TABLE, id);
        jdbc.update("DELETE FROM remote_zones WHERE id = ?::uuid", id);
        return Map.of("id", id, "deleted", true);
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("postcode", row.get("postal_code_pattern"));
        out.put("country", row.get("country_code"));
        out.put("supplierName", row.get("channel_name"));   // 旧 ACC 用物流商；新模型只挂在 channel 上
        out.put("type", row.get("level"));
        out.put("version", row.get("version"));
        out.put("effectiveFrom", json.value(row.get("effective_from")));
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        out.put("auditName", row.get("audit_name"));
        return out;
    }
}

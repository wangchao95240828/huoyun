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
@RequestMapping("/api/acc/stowage-steps")
public class AccStowageStepsController {
    private static final String TABLE = "acc_stowage_steps";

    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final CascadeChecker cascadeChecker;
    private final FieldGate fieldGate;

    public AccStowageStepsController(JdbcTemplate jdbc, JsonSupport json,
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
                "SELECT count(*) FROM acc_stowage_steps WHERE ?::text IS NULL OR name ILIKE ?",
                Long.class, search, search)) instanceof Number n ? n.longValue() : 0;
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT ss.id::text AS id, ss.stowage_id::text AS stowage_id, ss.step_order,
                       ss.name, ss.location, ss.status, ss.remark,
                       s.stowage_no,
                       ss.audit_status, ss.audited_at, ss.audit_name, ss.created_at
                FROM acc_stowage_steps ss
                LEFT JOIN stowages s ON s.id = ss.stowage_id
                WHERE ?::text IS NULL OR ss.name ILIKE ?
                ORDER BY ss.stowage_id, ss.step_order
                LIMIT ? OFFSET ?
                """, search, search, limit, offset);
            return AccPaging.result(rows.stream().map(this::project).toList(), total);
        } catch (DataAccessException ex) {
            return AccPaging.result(List.of(), 0);
        }
    }

    @GetMapping("/{id}/raw")
    public Map<String, Object> raw(@PathVariable String id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM acc_stowage_steps WHERE id = ?::uuid LIMIT 1", id);
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        String id = jdbc.queryForObject("""
            INSERT INTO acc_stowage_steps (tenant_id, stowage_id, step_order, name, location, status, remark)
            VALUES (current_setting('app.current_tenant_id')::uuid, ?::uuid, ?::int, ?, ?::text, ?::text, ?::text)
            RETURNING id::text
            """, String.class, body.get("stowageId"), body.get("stepOrder"), name,
            body.get("location"), body.get("status"), body.get("remark"));
        return Map.of("id", id, "name", name);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String currentAudit = jdbc.queryForObject(
            "SELECT audit_status FROM acc_stowage_steps WHERE id = ?::uuid", String.class, id);
        FieldGate.FilterResult gate = fieldGate.filterAllowedFields(TABLE, currentAudit, body);
        if (!gate.rejected().isEmpty() && gate.allowed().isEmpty()) {
            throw ApiException.badRequest("配载步骤已审核，字段不可修改: " + String.join(",", gate.rejected())
                + "；请先反审");
        }
        Map<String, Object> allowed = gate.allowed();
        jdbc.update("""
            UPDATE acc_stowage_steps SET
              name = coalesce(?, name),
              step_order = coalesce(?::int, step_order),
              location = coalesce(?::text, location),
              status = coalesce(?::text, status),
              remark = coalesce(?::text, remark)
            WHERE id = ?::uuid
            """, (String) allowed.get("name"), allowed.get("stepOrder"),
            (String) allowed.get("location"), (String) allowed.get("status"),
            (String) allowed.get("remark"), id);
        return Map.of("id", id, "rejectedFields", gate.rejected());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        String auditStatus = jdbc.queryForObject(
            "SELECT audit_status FROM acc_stowage_steps WHERE id = ?::uuid", String.class, id);
        if ("AUDITED".equals(auditStatus)) {
            throw ApiException.badRequest("配载步骤已审核，不能删除，请先反审");
        }
        cascadeChecker.checkBeforeDelete(TABLE, id);
        jdbc.update("DELETE FROM acc_stowage_steps WHERE id = ?::uuid", id);
        return Map.of("id", id, "deleted", true);
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("stowageId", row.get("stowage_id"));
        out.put("stowageNo", row.get("stowage_no"));
        out.put("stepOrder", row.get("step_order"));
        out.put("name", row.get("name"));
        out.put("location", row.get("location"));
        out.put("status", row.get("status"));
        out.put("remark", row.get("remark"));
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        out.put("auditName", row.get("audit_name"));
        return out;
    }
}

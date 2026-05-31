package com.xqt.saas.acc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import com.xqt.saas.common.BranchAccessFilter;
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

/** /api/acc/asks — 问题件，前端列：expressNo / content / source / type / status / addName / addTime。 */
@RestController
@RequestMapping("/api/acc/asks")
public class AccAsksController {
    private static final String TABLE = "acc_asks";
    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final CascadeChecker cascadeChecker;
    private final FieldGate fieldGate;

        private final BranchAccessFilter branchAccess;

public AccAsksController(JdbcTemplate jdbc, JsonSupport json,
                             CascadeChecker cascadeChecker, FieldGate fieldGate,
                                  BranchAccessFilter branchAccess) {
        this.jdbc = jdbc;
        this.json = json;
        this.cascadeChecker = cascadeChecker;
        this.fieldGate = fieldGate;
            this.branchAccess = branchAccess;
    }

    @GetMapping
    public Map<String, Object> list(
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer pageSize,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String dateFrom,
        @RequestParam(required = false) String dateTo
    ) {
        try {
            int limit = AccPaging.pageSize(pageSize);
            int offset = AccPaging.offset(page, pageSize);
            String search = keyword == null || keyword.isBlank() ? null : "%" + keyword + "%";
            Long total = jdbc.queryForObject("""
                SELECT count(*) FROM acc_asks a
                LEFT JOIN shipments s ON s.id = a.shipment_id
                WHERE (?::text IS NULL OR a.customer_ref ILIKE ? OR s.shipment_no ILIKE ?)
                  AND (?::date IS NULL OR a.created_at >= ?::date)
                  AND (?::date IS NULL OR a.created_at < (?::date + 1))
                """, Long.class, search, search, search, dateFrom, dateFrom, dateTo, dateTo);
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT a.id::text AS id, a.customer_ref, a.content, a.source, a.ask_type, a.status,
                       a.add_name, a.created_at,
                       a.audit_status, a.audited_at, a.audit_name,
                       s.shipment_no
                FROM acc_asks a
                LEFT JOIN shipments s ON s.id = a.shipment_id
                WHERE (?::text IS NULL OR a.customer_ref ILIKE ? OR s.shipment_no ILIKE ?)
                  AND (?::date IS NULL OR a.created_at >= ?::date)
                  AND (?::date IS NULL OR a.created_at < (?::date + 1))
                ORDER BY a.created_at DESC
                LIMIT ? OFFSET ?
                """, search, search, search, dateFrom, dateFrom, dateTo, dateTo, limit, offset);
            return AccPaging.result(rows.stream().map(this::project).toList(), total == null ? 0 : total);
        } catch (DataAccessException ex) {
            return AccPaging.result(List.of(), 0);
        }
    }

    @GetMapping("/{id}/raw")
    public Map<String, Object> raw(@PathVariable String id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM acc_asks WHERE id = ?::uuid LIMIT 1", id);
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String id = jdbc.queryForObject("""
            INSERT INTO acc_asks (
              tenant_id, shipment_id, customer_ref, content, source, ask_type, status, add_name
            ) VALUES (
              current_setting('app.current_tenant_id')::uuid, ?::uuid, ?, ?, ?, ?, ?, ?
            )
            RETURNING id::text
            """, String.class,
            body.get("shipment_id"),
            body.getOrDefault("expressNo", body.get("customer_ref")),
            body.get("content"),
            body.get("source"),
            body.getOrDefault("type", body.get("ask_type")),
            body.getOrDefault("status", "OPEN"),
            body.getOrDefault("addName", body.get("add_name")));
        return Map.of("id", id);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String currentAudit = jdbc.queryForObject(
            "SELECT audit_status FROM acc_asks WHERE id = ?::uuid", String.class, id);
        FieldGate.FilterResult gate = fieldGate.filterAllowedFields(TABLE, currentAudit, body);
        if (!gate.rejected().isEmpty() && gate.allowed().isEmpty()) {
            throw ApiException.badRequest("问题件已审核，字段不可修改: " + String.join(",", gate.rejected())
                + "；请先反审");
        }
        Map<String, Object> allowed = gate.allowed();
        jdbc.update("""
            UPDATE acc_asks SET
              content = coalesce(?, content),
              status  = coalesce(?, status)
            WHERE id = ?::uuid
            """,
            (String) allowed.get("content"),
            (String) allowed.get("status"),
            id);
        return Map.of("id", id, "rejectedFields", gate.rejected());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        String auditStatus = jdbc.queryForObject(
            "SELECT audit_status FROM acc_asks WHERE id = ?::uuid", String.class, id);
        if ("AUDITED".equals(auditStatus)) {
            throw ApiException.badRequest("问题件已审核，不能删除，请先反审");
        }
        cascadeChecker.checkBeforeDelete(TABLE, id);
        jdbc.update("DELETE FROM acc_asks WHERE id = ?::uuid", id);
        return Map.of("id", id, "deleted", true);
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("expressNo", row.get("customer_ref") != null
            ? row.get("customer_ref") : row.get("shipment_no"));
        out.put("content", row.get("content"));
        out.put("source", row.get("source"));
        out.put("type", row.get("ask_type"));
        out.put("status", row.get("status"));
        out.put("addName", row.get("add_name"));
        out.put("addTime", json.value(row.get("created_at")));
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        out.put("auditName", row.get("audit_name"));
        return out;
    }
}

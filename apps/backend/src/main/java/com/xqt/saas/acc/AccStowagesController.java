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
@RequestMapping("/api/acc/stowages")
public class AccStowagesController {
    private static final String TABLE = "stowages";

    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final CascadeChecker cascadeChecker;
    private final FieldGate fieldGate;
    private final com.xqt.saas.stowage.StowageStateMachine stateMachine;

    public AccStowagesController(JdbcTemplate jdbc, JsonSupport json,
                                  CascadeChecker cascadeChecker, FieldGate fieldGate,
                                  com.xqt.saas.stowage.StowageStateMachine stateMachine) {
        this.jdbc = jdbc;
        this.json = json;
        this.cascadeChecker = cascadeChecker;
        this.fieldGate = fieldGate;
        this.stateMachine = stateMachine;
    }

    @GetMapping
    public Map<String, Object> list(
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer pageSize,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String status
    ) {
        try {
            int limit = AccPaging.pageSize(pageSize);
            int offset = AccPaging.offset(page, pageSize);
            String search = keyword == null || keyword.isBlank() ? null : "%" + keyword + "%";
            String statusFilter = "EXCEPTION".equals(status) ? " AND s.status = 'EXCEPTION'" : "";

            long total = json.value(jdbc.queryForObject(
                "SELECT count(*) FROM stowages s WHERE (?::text IS NULL OR s.stowage_no ILIKE ?)" + statusFilter,
                Long.class, search, search)) instanceof Number n ? n.longValue() : 0;
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT s.id::text AS id, s.stowage_no, s.stowage_date, s.status, s.category_id::text AS category_id,"
                + "       s.customer_id::text AS customer_id, s.departure_port_id::text AS departure_port_id,"
                + "       s.arrival_port_id::text AS arrival_port_id, s.remark,"
                + "       sc.name AS category_name, c.name AS customer_name,"
                + "       dp.name AS departure_port_name, ap.name AS arrival_port_name,"
                + "       s.audit_status, s.audited_at, s.audit_name, s.created_at"
                + " FROM stowages s"
                + " LEFT JOIN stowage_categories sc ON sc.id = s.category_id"
                + " LEFT JOIN customers c ON c.id = s.customer_id"
                + " LEFT JOIN stowage_ports dp ON dp.id = s.departure_port_id"
                + " LEFT JOIN stowage_ports ap ON ap.id = s.arrival_port_id"
                + " WHERE (?::text IS NULL OR s.stowage_no ILIKE ?)"
                + statusFilter
                + " ORDER BY s.stowage_date DESC"
                + " LIMIT ? OFFSET ?",
                search, search, limit, offset);
            return AccPaging.result(rows.stream().map(this::project).toList(), total);
        } catch (DataAccessException ex) {
            return AccPaging.result(List.of(), 0);
        }
    }

    @GetMapping("/{id}/raw")
    public Map<String, Object> raw(@PathVariable String id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM stowages WHERE id = ?::uuid LIMIT 1", id);
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String stowageNo = (String) body.get("stowageNo");
        String id = jdbc.queryForObject("""
            INSERT INTO stowages (tenant_id, stowage_no, stowage_date, status, category_id,
                                  customer_id, departure_port_id, arrival_port_id, remark)
            VALUES (current_setting('app.current_tenant_id')::uuid, ?, ?::date, ?::text,
                    ?::uuid, ?::uuid, ?::uuid, ?::uuid, ?::text)
            RETURNING id::text
            """, String.class, stowageNo, body.get("stowageDate"), body.get("status"),
            body.get("categoryId"), body.get("customerId"),
            body.get("departurePortId"), body.get("arrivalPortId"), body.get("remark"));
        return Map.of("id", id, "stowageNo", stowageNo);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String currentAudit = jdbc.queryForObject(
            "SELECT audit_status FROM stowages WHERE id = ?::uuid", String.class, id);
        FieldGate.FilterResult gate = fieldGate.filterAllowedFields(TABLE, currentAudit, body);
        if (!gate.rejected().isEmpty() && gate.allowed().isEmpty()) {
            throw ApiException.badRequest("配载已审核，字段不可修改: " + String.join(",", gate.rejected())
                + "；请先反审");
        }
        Map<String, Object> allowed = gate.allowed();
        jdbc.update("""
            UPDATE stowages SET
              status = coalesce(?::text, status),
              remark = coalesce(?::text, remark)
            WHERE id = ?::uuid
            """, (String) allowed.get("status"), (String) allowed.get("remark"), id);
        // 任务 S5：CONFIRMED 时联动 tracking_events + shipments.status
        if ("CONFIRMED".equals(allowed.get("status"))) {
            try {
                String tenantId = jdbc.queryForObject(
                    "SELECT tenant_id::text FROM stowages WHERE id = ?::uuid", String.class, id);
                stateMachine.onStowageConfirmed(tenantId, id);
            } catch (DataAccessException ignored) {
                // state machine 失败不应阻断 stowage 更新
            }
        }
        return Map.of("id", id, "rejectedFields", gate.rejected());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        String auditStatus = jdbc.queryForObject(
            "SELECT audit_status FROM stowages WHERE id = ?::uuid", String.class, id);
        if ("AUDITED".equals(auditStatus)) {
            throw ApiException.badRequest("配载已审核，不能删除，请先反审");
        }
        cascadeChecker.checkBeforeDelete(TABLE, id);
        jdbc.update("DELETE FROM stowages WHERE id = ?::uuid", id);
        return Map.of("id", id, "deleted", true);
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("stowageNo", row.get("stowage_no"));
        out.put("stowageDate", row.get("stowage_date"));
        out.put("status", row.get("status"));
        out.put("categoryId", row.get("category_id"));
        out.put("categoryName", row.get("category_name"));
        out.put("customerId", row.get("customer_id"));
        out.put("customerName", row.get("customer_name"));
        out.put("departurePortId", row.get("departure_port_id"));
        out.put("departurePortName", row.get("departure_port_name"));
        out.put("arrivalPortId", row.get("arrival_port_id"));
        out.put("arrivalPortName", row.get("arrival_port_name"));
        out.put("remark", row.get("remark"));
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        out.put("auditName", row.get("audit_name"));
        return out;
    }
}

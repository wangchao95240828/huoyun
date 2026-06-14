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

@RestController
@RequestMapping("/api/acc/dispatches")
public class AccDispatchesController {
    private static final String TABLE = "acc_dispatches";

    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final CascadeChecker cascadeChecker;
    private final FieldGate fieldGate;
    private final com.xqt.saas.stowage.StowageStateMachine stateMachine;

        private final BranchAccessFilter branchAccess;

public AccDispatchesController(JdbcTemplate jdbc, JsonSupport json,
                                    CascadeChecker cascadeChecker, FieldGate fieldGate,
                                    com.xqt.saas.stowage.StowageStateMachine stateMachine,
                                  BranchAccessFilter branchAccess) {
        this.jdbc = jdbc;
        this.json = json;
        this.cascadeChecker = cascadeChecker;
        this.fieldGate = fieldGate;
        this.stateMachine = stateMachine;
            this.branchAccess = branchAccess;
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
            var access = branchAccess.forCurrentViaCustomer("d");
            java.util.List<Object> countParams = new java.util.ArrayList<>(java.util.Arrays.asList(search, search));
            countParams.addAll(access.params());
            long total = json.value(jdbc.queryForObject(
                "SELECT count(*) FROM acc_dispatches d"
                + " WHERE (?::text IS NULL OR d.dispatch_no ILIKE ?)"
                + access.sql(),
                Long.class, countParams.toArray())) instanceof Number n ? n.longValue() : 0;
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT d.id::text AS id, d.dispatch_no, d.customer_id::text AS customer_id,"
                + "       d.contact_name, d.contact_mobile, d.pick_address, d.pick_date,"
                + "       d.pick_time_range, d.package_count, d.weight, d.status, d.remark,"
                + "       c.name AS customer_name,"
                + "       d.audit_status, d.audited_at, d.audit_name, d.created_at"
                + " FROM acc_dispatches d"
                + " LEFT JOIN customers c ON c.id = d.customer_id"
                + " WHERE (?::text IS NULL OR d.dispatch_no ILIKE ?)"
                + access.sql()
                + " ORDER BY d.pick_date DESC"
                + " LIMIT ? OFFSET ?",
                buildDispatchesListParams(search, access, limit, offset));
            return AccPaging.result(rows.stream().map(this::project).toList(), total);
        } catch (DataAccessException ex) {
            return AccPaging.result(List.of(), 0);
        }
    }

    @GetMapping("/{id}/raw")
    public Map<String, Object> raw(@PathVariable String id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM acc_dispatches WHERE id = ?::uuid LIMIT 1", id);
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String dispatchNo = (String) body.get("dispatchNo");
        if (dispatchNo == null || dispatchNo.isBlank()) {
            throw ApiException.badRequest("派件单号必填");
        }
        if (body.get("customerId") == null || body.get("customerId").toString().isBlank()) {
            throw ApiException.badRequest("请选择客户");
        }
        Object pc = body.get("packageCount");
        if (pc instanceof Number pn && pn.intValue() <= 0) {
            throw ApiException.badRequest("件数必须大于零");
        }
        String id = jdbc.queryForObject("""
            INSERT INTO acc_dispatches (tenant_id, dispatch_no, customer_id, contact_name,
                                        contact_mobile, pick_address, pick_date, pick_time_range,
                                        package_count, weight, status, remark)
            VALUES (current_setting('app.current_tenant_id')::uuid, ?, ?::uuid, ?::text,
                    ?::text, ?::text, ?::date, ?::text,
                    ?::int, ?::numeric, ?::text, ?::text)
            RETURNING id::text
            """, String.class, dispatchNo, body.get("customerId"),
            body.get("contactName"), body.get("contactMobile"),
            body.get("pickAddress"), body.get("pickDate"), body.get("pickTimeRange"),
            body.get("packageCount"), body.get("weight"),
            body.get("status"), body.get("remark"));
        return Map.of("id", id, "dispatchNo", dispatchNo);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String currentAudit = jdbc.queryForObject(
            "SELECT audit_status FROM acc_dispatches WHERE id = ?::uuid", String.class, id);
        FieldGate.FilterResult gate = fieldGate.filterAllowedFields(TABLE, currentAudit, body);
        if (!gate.rejected().isEmpty() && gate.allowed().isEmpty()) {
            throw ApiException.badRequest("揽收已审核，字段不可修改: " + String.join(",", gate.rejected())
                + "；请先反审");
        }
        Map<String, Object> allowed = gate.allowed();
        jdbc.update("""
            UPDATE acc_dispatches SET
              contact_name = coalesce(?::text, contact_name),
              contact_mobile = coalesce(?::text, contact_mobile),
              pick_address = coalesce(?::text, pick_address),
              status = coalesce(?::text, status),
              remark = coalesce(?::text, remark)
            WHERE id = ?::uuid
            """, (String) allowed.get("contactName"), (String) allowed.get("contactMobile"),
            (String) allowed.get("pickAddress"), (String) allowed.get("status"),
            (String) allowed.get("remark"), id);
        // 任务 S5：派送状态推进时联动 tracking_events + shipments
        String newStatus = (String) allowed.get("status");
        if ("DONE".equals(newStatus) || "PICKED".equals(newStatus)) {
            try {
                String tenantId = jdbc.queryForObject(
                    "SELECT tenant_id::text FROM acc_dispatches WHERE id = ?::uuid", String.class, id);
                if ("PICKED".equals(newStatus)) {
                    stateMachine.onDispatchOutForDelivery(tenantId, id);
                } else {
                    stateMachine.onDispatchDelivered(tenantId, id);
                }
            } catch (DataAccessException ignored) {
                // state machine 失败不应阻断 dispatch 更新
            }
        }
        return Map.of("id", id, "rejectedFields", gate.rejected());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        String auditStatus = jdbc.queryForObject(
            "SELECT audit_status FROM acc_dispatches WHERE id = ?::uuid", String.class, id);
        if ("AUDITED".equals(auditStatus)) {
            throw ApiException.badRequest("揽收已审核，不能删除，请先反审");
        }
        cascadeChecker.checkBeforeDelete(TABLE, id);
        jdbc.update("DELETE FROM acc_dispatches WHERE id = ?::uuid", id);
        return Map.of("id", id, "deleted", true);
    }

    private static Object[] buildDispatchesListParams(String search,
                                                       BranchAccessFilter.AccessClause access,
                                                       int limit, int offset) {
        java.util.List<Object> params = new java.util.ArrayList<>(java.util.Arrays.asList(search, search));
        params.addAll(access.params());
        params.add(limit);
        params.add(offset);
        return params.toArray();
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("dispatchNo", row.get("dispatch_no"));
        out.put("customerId", row.get("customer_id"));
        out.put("customerName", row.get("customer_name"));
        out.put("contactName", row.get("contact_name"));
        out.put("contactMobile", row.get("contact_mobile"));
        out.put("pickAddress", row.get("pick_address"));
        out.put("pickDate", row.get("pick_date"));
        out.put("pickTimeRange", row.get("pick_time_range"));
        out.put("packageCount", row.get("package_count"));
        out.put("weight", row.get("weight"));
        out.put("status", row.get("status"));
        out.put("remark", row.get("remark"));
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        out.put("auditName", row.get("audit_name"));
        return out;
    }
}

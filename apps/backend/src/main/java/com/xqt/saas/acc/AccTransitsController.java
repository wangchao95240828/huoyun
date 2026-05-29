package com.xqt.saas.acc;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import com.xqt.saas.common.JsonSupport;
import com.xqt.saas.framework.cascade.CascadeChecker;
import com.xqt.saas.framework.fieldgate.FieldGate;
import com.xqt.saas.framework.money.MoneySnapshotService;
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
@RequestMapping("/api/acc/transits")
public class AccTransitsController {
    private static final String TABLE = "acc_transits";

    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final CascadeChecker cascadeChecker;
    private final FieldGate fieldGate;
    private final MoneySnapshotService moneySnapshotService;
    private final com.xqt.saas.stowage.StowageStateMachine stateMachine;

    public AccTransitsController(JdbcTemplate jdbc, JsonSupport json,
                                  CascadeChecker cascadeChecker, FieldGate fieldGate,
                                  MoneySnapshotService moneySnapshotService,
                                  com.xqt.saas.stowage.StowageStateMachine stateMachine) {
        this.jdbc = jdbc;
        this.json = json;
        this.cascadeChecker = cascadeChecker;
        this.fieldGate = fieldGate;
        this.moneySnapshotService = moneySnapshotService;
        this.stateMachine = stateMachine;
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
                "SELECT count(*) FROM acc_transits WHERE ?::text IS NULL OR transit_no ILIKE ?",
                Long.class, search, search)) instanceof Number n ? n.longValue() : 0;
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT t.id::text AS id, t.transit_no, t.shipping_line, t.vessel, t.voyage,
                       t.etd, t.eta, t.tariff, t.cost, t.currency, t.status, t.remark,
                       t.from_port_id::text AS from_port_id, t.to_port_id::text AS to_port_id,
                       fp.name AS from_port_name, tp.name AS to_port_name,
                       t.audit_status, t.audited_at, t.audit_name, t.created_at
                FROM acc_transits t
                LEFT JOIN stowage_ports fp ON fp.id = t.from_port_id
                LEFT JOIN stowage_ports tp ON tp.id = t.to_port_id
                WHERE ?::text IS NULL OR t.transit_no ILIKE ?
                ORDER BY t.created_at DESC
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
            "SELECT * FROM acc_transits WHERE id = ?::uuid LIMIT 1", id);
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String transitNo = (String) body.get("transitNo");
        String id = jdbc.queryForObject("""
            INSERT INTO acc_transits (tenant_id, transit_no, from_port_id, to_port_id,
                                      shipping_line, vessel, voyage, etd, eta,
                                      tariff, cost, currency, status, remark)
            VALUES (current_setting('app.current_tenant_id')::uuid, ?, ?::uuid, ?::uuid,
                    ?::text, ?::text, ?::text, ?::date, ?::date,
                    ?::numeric, ?::numeric, ?::text, ?::text, ?::text)
            RETURNING id::text
            """, String.class, transitNo, body.get("fromPortId"), body.get("toPortId"),
            body.get("shippingLine"), body.get("vessel"), body.get("voyage"),
            body.get("etd"), body.get("eta"), body.get("tariff"), body.get("cost"),
            body.get("currency"), body.get("status"), body.get("remark"));
        BigDecimal cost = body.get("cost") instanceof Number n
            ? BigDecimal.valueOf(n.doubleValue()) : null;
        String currency = body.get("currency") != null ? body.get("currency").toString() : "CNY";
        moneySnapshotService.snapshot(TABLE, id, cost, currency);
        return Map.of("id", id, "transitNo", transitNo);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String currentAudit = jdbc.queryForObject(
            "SELECT audit_status FROM acc_transits WHERE id = ?::uuid", String.class, id);
        FieldGate.FilterResult gate = fieldGate.filterAllowedFields(TABLE, currentAudit, body);
        if (!gate.rejected().isEmpty() && gate.allowed().isEmpty()) {
            throw ApiException.badRequest("转运已审核，字段不可修改: " + String.join(",", gate.rejected())
                + "；请先反审");
        }
        Map<String, Object> allowed = gate.allowed();
        jdbc.update("""
            UPDATE acc_transits SET
              status = coalesce(?::text, status),
              tariff = coalesce(?::numeric, tariff),
              cost = coalesce(?::numeric, cost),
              remark = coalesce(?::text, remark)
            WHERE id = ?::uuid
            """, (String) allowed.get("status"), allowed.get("tariff"),
            allowed.get("cost"), (String) allowed.get("remark"), id);
        if (allowed.containsKey("cost")) {
            Map<String, Object> row = jdbc.queryForMap(
                "SELECT cost, currency FROM acc_transits WHERE id = ?::uuid", id);
            BigDecimal newCost = row.get("cost") instanceof Number n
                ? BigDecimal.valueOf(n.doubleValue()) : null;
            String currency = row.get("currency") != null ? row.get("currency").toString() : "CNY";
            moneySnapshotService.snapshot(TABLE, id, newCost, currency);
        }
        // 任务 S5：IN_TRANSIT 起运时按 acc_transit_items 联动各 shipment 写 tracking_events
        if ("IN_TRANSIT".equals(allowed.get("status"))) {
            try {
                String tenantId = jdbc.queryForObject(
                    "SELECT tenant_id::text FROM acc_transits WHERE id = ?::uuid", String.class, id);
                stateMachine.onTransitInTransit(tenantId, id);
            } catch (org.springframework.dao.DataAccessException ignored) {
                // state machine 失败不阻断 transit 更新
            }
        }
        return Map.of("id", id, "rejectedFields", gate.rejected());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        String auditStatus = jdbc.queryForObject(
            "SELECT audit_status FROM acc_transits WHERE id = ?::uuid", String.class, id);
        if ("AUDITED".equals(auditStatus)) {
            throw ApiException.badRequest("转运已审核，不能删除，请先反审");
        }
        cascadeChecker.checkBeforeDelete(TABLE, id);
        jdbc.update("DELETE FROM acc_transits WHERE id = ?::uuid", id);
        return Map.of("id", id, "deleted", true);
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("transitNo", row.get("transit_no"));
        out.put("fromPortId", row.get("from_port_id"));
        out.put("fromPortName", row.get("from_port_name"));
        out.put("toPortId", row.get("to_port_id"));
        out.put("toPortName", row.get("to_port_name"));
        out.put("shippingLine", row.get("shipping_line"));
        out.put("vessel", row.get("vessel"));
        out.put("voyage", row.get("voyage"));
        out.put("etd", row.get("etd"));
        out.put("eta", row.get("eta"));
        out.put("tariff", row.get("tariff"));
        out.put("cost", row.get("cost"));
        out.put("currency", row.get("currency"));
        out.put("status", row.get("status"));
        out.put("remark", row.get("remark"));
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        out.put("auditName", row.get("audit_name"));
        return out;
    }
}

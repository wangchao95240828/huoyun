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
 * /api/acc/shipments — 前端列：no / channelName / supplierName / country
 * / totalPiece / totalWeight / totalCharge / totalCost / auditName / addTime
 *
 * 来源：shipments + channels + cartons 聚合。supplierName / auditName 暂空（新模型未建模）。
 */
@RestController
@RequestMapping("/api/acc/shipments")
public class AccShipmentsController {
    private static final String TABLE = "shipments";

    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final CascadeChecker cascadeChecker;
    private final FieldGate fieldGate;

    public AccShipmentsController(JdbcTemplate jdbc, JsonSupport json,
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
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String dateFrom,
        @RequestParam(required = false) String dateTo
    ) {
        try {
            int limit = AccPaging.pageSize(pageSize);
            int offset = AccPaging.offset(page, pageSize);
            String search = keyword == null || keyword.isBlank() ? null : "%" + keyword + "%";

            Long total = jdbc.queryForObject("""
                SELECT count(*) FROM shipments s
                WHERE (?::text IS NULL OR (s.shipment_no ILIKE ? OR s.customer_ref ILIKE ?))
                  AND (?::date IS NULL OR s.created_at >= ?::date)
                  AND (?::date IS NULL OR s.created_at < (?::date + 1))
                """, Long.class, search, search, search, dateFrom, dateFrom, dateTo, dateTo);

            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT
                  s.id::text       AS id,
                  s.shipment_no,
                  s.customer_ref,
                  s.status::text   AS status,
                  s.destination_country,
                  s.created_at,
                  s.audit_status,
                  s.audited_at,
                  s.audit_name,
                  ch.name          AS channel_name,
                  -- 物流商：从最新生效的 channel_cost_policies 拿 carrier 名
                  (
                    SELECT car.name FROM channel_cost_policies ccp
                    LEFT JOIN carriers car ON car.id = ccp.carrier_id
                    WHERE ccp.tenant_id = s.tenant_id
                      AND ccp.channel_id = s.channel_id
                      AND ccp.effective_from <= current_date
                      AND (ccp.effective_to IS NULL OR ccp.effective_to >= current_date)
                    ORDER BY ccp.effective_from DESC
                    LIMIT 1
                  ) AS supplier_name,
                  (SELECT count(*) FROM cartons c WHERE c.shipment_id = s.id) AS piece_count,
                  (SELECT coalesce(sum(c.actual_weight_kg), 0) FROM cartons c WHERE c.shipment_id = s.id) AS total_weight,
                  (
                    SELECT coalesce(sum(ch2.amount), 0) FROM charges ch2
                    WHERE ch2.shipment_id = s.id AND ch2.side = 'AR'
                      AND ch2.settlement_status <> 'VOID'
                  ) AS total_charge,
                  (
                    SELECT coalesce(sum(ch3.amount), 0) FROM charges ch3
                    WHERE ch3.shipment_id = s.id AND ch3.side = 'AP'
                      AND ch3.settlement_status <> 'VOID'
                  ) AS total_cost
                FROM shipments s
                LEFT JOIN channels ch ON ch.id = s.channel_id
                WHERE (?::text IS NULL OR (s.shipment_no ILIKE ? OR s.customer_ref ILIKE ?))
                  AND (?::date IS NULL OR s.created_at >= ?::date)
                  AND (?::date IS NULL OR s.created_at < (?::date + 1))
                ORDER BY s.created_at DESC
                LIMIT ? OFFSET ?
                """, search, search, search, dateFrom, dateFrom, dateTo, dateTo, limit, offset);
            return AccPaging.result(rows.stream().map(this::project).toList(),
                total == null ? 0 : total);
        } catch (DataAccessException ex) {
            return AccPaging.result(List.of(), 0);
        }
    }

    @GetMapping("/{id}/raw")
    public Map<String, Object> raw(@PathVariable String id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM shipments WHERE id = ?::uuid LIMIT 1", id);
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    /** 前端原页面的 "查看装箱清单"，对应 ACC `shipments/{id}/items`。返回该 shipment 下所有 cartons + declarations。 */
    @GetMapping("/{id}/items")
    public Map<String, Object> items(@PathVariable String id) {
        try {
            List<Map<String, Object>> cartons = jdbc.queryForList("""
                SELECT id::text AS id, carton_no, tracking_no, carrier_master_tracking_no,
                       actual_weight_kg, chargeable_weight_kg, cbm
                FROM cartons WHERE shipment_id = ?::uuid
                ORDER BY carton_no
                """, id);
            List<Map<String, Object>> declares = jdbc.queryForList("""
                SELECT id::text AS id, item_name, material, hs_code, quantity, value_amount
                FROM declarations WHERE shipment_id = ?::uuid
                """, id);
            return Map.of("cartons", json.rows(cartons), "declarations", json.rows(declares));
        } catch (DataAccessException ex) {
            return Map.of("cartons", List.of(), "declarations", List.of());
        }
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String shipmentNo = (String) body.get("shipment_no");
        Object customerId = body.get("customer_id");
        String id = jdbc.queryForObject("""
            INSERT INTO shipments (tenant_id, customer_id, shipment_no, status)
            VALUES (current_setting('app.current_tenant_id')::uuid, ?::uuid, ?, 'DRAFT')
            RETURNING id::text
            """, String.class, customerId == null ? null : customerId.toString(), shipmentNo);
        return Map.of("id", id, "shipment_no", shipmentNo);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String currentAudit = jdbc.queryForObject(
            "SELECT audit_status FROM shipments WHERE id = ?::uuid", String.class, id);
        FieldGate.FilterResult gate = fieldGate.filterAllowedFields(TABLE, currentAudit, body);
        if (!gate.rejected().isEmpty() && gate.allowed().isEmpty()) {
            throw ApiException.badRequest("运单已审核，字段不可修改: " + String.join(",", gate.rejected())
                + "；请先反审");
        }
        Map<String, Object> allowed = gate.allowed();
        jdbc.update("""
            UPDATE shipments SET
              shipment_no  = coalesce(?, shipment_no),
              customer_ref = coalesce(?, customer_ref)
            WHERE id = ?::uuid
            """, (String) allowed.get("shipment_no"),
            (String) allowed.get("customer_ref"), id);
        return Map.of("id", id, "rejectedFields", gate.rejected());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        String auditStatus = jdbc.queryForObject(
            "SELECT audit_status FROM shipments WHERE id = ?::uuid", String.class, id);
        if ("AUDITED".equals(auditStatus)) {
            throw ApiException.badRequest("运单已审核，不能删除，请先反审");
        }
        cascadeChecker.checkBeforeDelete(TABLE, id);
        jdbc.update("DELETE FROM shipments WHERE id = ?::uuid", id);
        return Map.of("id", id, "deleted", true);
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("no", row.get("shipment_no"));
        out.put("channelName", row.get("channel_name"));
        out.put("supplierName", row.get("supplier_name") == null ? "" : row.get("supplier_name"));
        out.put("country", row.get("destination_country"));
        out.put("totalPiece", row.get("piece_count"));
        out.put("totalWeight", row.get("total_weight"));
        out.put("totalCharge", row.get("total_charge"));
        out.put("totalCost", row.get("total_cost"));
        out.put("addTime", json.value(row.get("created_at")));
        out.put("status", row.get("status"));
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        out.put("auditName", row.get("audit_name"));
        return out;
    }
}

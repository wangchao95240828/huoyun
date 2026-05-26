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

/**
 * /api/acc/charges — AR 应收，前端列：expressNo / customerName / productName / country
 * / chargeWeight / type / amount / paid / theDate / auditName
 *
 * 来源：charges WHERE side='AR' + shipments + customers + channels + charge_items。
 */
@RestController
@RequestMapping("/api/acc/charges")
public class AccChargesController {
    private static final String TABLE = "charges";
    private static final String SIDE = "AR";

    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final CascadeChecker cascadeChecker;
    private final FieldGate fieldGate;
    private final MoneySnapshotService moneySnapshotService;

    public AccChargesController(JdbcTemplate jdbc, JsonSupport json,
                                CascadeChecker cascadeChecker, FieldGate fieldGate,
                                MoneySnapshotService moneySnapshotService) {
        this.jdbc = jdbc;
        this.json = json;
        this.cascadeChecker = cascadeChecker;
        this.fieldGate = fieldGate;
        this.moneySnapshotService = moneySnapshotService;
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
                SELECT count(*) FROM charges ch
                LEFT JOIN shipments s ON s.id = ch.shipment_id
                WHERE ch.side = 'AR'
                  AND (?::text IS NULL OR s.shipment_no ILIKE ? OR s.customer_ref ILIKE ?)
                  AND (?::date IS NULL OR ch.created_at >= ?::date)
                  AND (?::date IS NULL OR ch.created_at < (?::date + 1))
                """, Long.class, search, search, search, dateFrom, dateFrom, dateTo, dateTo);

            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT
                  ch.id::text       AS id,
                  ch.status,
                  ch.currency,
                  ch.amount,
                  ch.created_at,
                  ch.audit_status,
                  ch.audited_at,
                  ch.audit_name,
                  s.shipment_no,
                  s.customer_ref,
                  s.destination_country,
                  cu.name           AS customer_name,
                  cn.name           AS channel_name,
                  ci.name           AS charge_item_name,
                  ci.code           AS charge_item_code,
                  (
                    SELECT coalesce(sum(p.amount), 0) FROM payments p
                    WHERE p.tenant_id = ch.tenant_id
                      AND p.reference_no = s.shipment_no
                  ) AS paid_amount,
                  (
                    SELECT coalesce(sum(c.chargeable_weight_kg), sum(c.actual_weight_kg))
                    FROM cartons c WHERE c.shipment_id = s.id
                  ) AS charge_weight
                FROM charges ch
                LEFT JOIN shipments s    ON s.id = ch.shipment_id
                LEFT JOIN customers cu   ON cu.id = s.customer_id
                LEFT JOIN channels cn    ON cn.id = s.channel_id
                LEFT JOIN charge_items ci ON ci.id = ch.charge_item_id
                WHERE ch.side = 'AR'
                  AND (?::text IS NULL OR s.shipment_no ILIKE ? OR s.customer_ref ILIKE ?)
                  AND (?::date IS NULL OR ch.created_at >= ?::date)
                  AND (?::date IS NULL OR ch.created_at < (?::date + 1))
                ORDER BY ch.created_at DESC
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
            "SELECT * FROM charges WHERE id = ?::uuid AND side='AR' LIMIT 1", id);
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        Object shipmentId = body.get("shipment_id");
        Object chargeItemId = body.get("charge_item_id");
        BigDecimal amount = body.get("amount") instanceof Number n
            ? new BigDecimal(n.toString()) : BigDecimal.ZERO;
        String currency = (String) body.getOrDefault("currency", "CNY");
        String id = jdbc.queryForObject("""
            INSERT INTO charges (
              tenant_id, shipment_id, charge_item_id, side, status, currency, amount
            ) VALUES (
              current_setting('app.current_tenant_id')::uuid,
              ?::uuid, ?::uuid, ?::charge_side, 'DRAFT', ?, ?
            )
            RETURNING id::text
            """, String.class,
            shipmentId == null ? null : shipmentId.toString(),
            chargeItemId == null ? null : chargeItemId.toString(),
            SIDE, currency, amount);
        // 汇率快照：所有非 CNY 金额都在落库时 freeze rate
        moneySnapshotService.snapshot(TABLE, id, amount, currency);
        return Map.of("id", id);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        // 字段闸：审核后金额 / 币种 / shipment / charge_item 全部锁定
        String currentAudit = jdbc.queryForObject(
            "SELECT audit_status FROM charges WHERE id = ?::uuid AND side = ?::charge_side",
            String.class, id, SIDE);
        FieldGate.FilterResult gate = fieldGate.filterAllowedFields(TABLE, currentAudit, body);
        if (!gate.rejected().isEmpty() && gate.allowed().isEmpty()) {
            throw ApiException.badRequest("费用已审核，字段不可修改: " + String.join(",", gate.rejected())
                + "；请先反审");
        }
        Map<String, Object> allowed = gate.allowed();
        jdbc.update("""
            UPDATE charges SET
              amount = coalesce(?, amount),
              status = coalesce(?::charge_status, status)
            WHERE id = ?::uuid AND side = ?::charge_side
            """,
            allowed.get("amount") instanceof Number n ? new BigDecimal(n.toString()) : null,
            (String) allowed.get("status"),
            id, SIDE);
        // amount 改了的话刷新汇率快照
        if (allowed.containsKey("amount") && allowed.get("amount") instanceof Number n) {
            String currency = jdbc.queryForObject(
                "SELECT currency FROM charges WHERE id = ?::uuid", String.class, id);
            moneySnapshotService.snapshot(TABLE, id, new BigDecimal(n.toString()), currency);
        }
        return Map.of("id", id, "rejectedFields", gate.rejected());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        // 删除前级联校验 + 审核后锁定
        String auditStatus = jdbc.queryForObject(
            "SELECT audit_status FROM charges WHERE id = ?::uuid AND side = ?::charge_side",
            String.class, id, SIDE);
        if ("AUDITED".equals(auditStatus)) {
            throw ApiException.badRequest("费用已审核，不能删除，请先反审");
        }
        cascadeChecker.checkBeforeDelete(TABLE, id);
        jdbc.update("DELETE FROM charges WHERE id = ?::uuid AND side = ?::charge_side", id, SIDE);
        return Map.of("id", id, "deleted", true);
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("expressNo", row.get("customer_ref") != null
            ? row.get("customer_ref") : row.get("shipment_no"));
        out.put("customerName", row.get("customer_name"));
        out.put("productName", row.get("channel_name"));
        out.put("country", row.get("destination_country"));
        out.put("chargeWeight", row.get("charge_weight"));
        out.put("type", row.get("charge_item_name"));
        out.put("amount", row.get("amount"));
        out.put("paid", row.get("paid_amount"));
        out.put("theDate", json.value(row.get("created_at")));
        out.put("auditName", "");
        out.put("status", row.get("status"));
        out.put("currency", row.get("currency"));
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        out.put("auditName", row.get("audit_name"));
        return out;
    }
}

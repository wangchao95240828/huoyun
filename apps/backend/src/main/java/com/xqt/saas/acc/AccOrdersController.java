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
 * /api/acc/orders — 前端列：orderNo / trackNo / customerName / product / country
 * / piece / chargeWeight / sellCharge / costCharge / branch / addTime
 *
 * 数据来源：orders + customers join，cartons 聚合件数，shipments 一对一拿 country。
 * sellCharge = SUM(charges where side='AR' and settlement_status<>'VOID') by customer_ref join shipments；
 * costCharge = SUM(charges where side='AP' and settlement_status<>'VOID')；
 * branch = orders.branch_id → organizations.name。
 */
@RestController
@RequestMapping("/api/acc/orders")
public class AccOrdersController {
    private static final String TABLE = "orders";

    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final CascadeChecker cascadeChecker;
    private final FieldGate fieldGate;

    public AccOrdersController(JdbcTemplate jdbc, JsonSupport json,
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
        @RequestParam(required = false) String dateTo,
        @RequestParam(required = false) String status
    ) {
        try {
            int limit = AccPaging.pageSize(pageSize);
            int offset = AccPaging.offset(page, pageSize);
            String search = keyword == null || keyword.isBlank() ? null : "%" + keyword + "%";

            // status 过滤模式：
            //   "DRAFT"      → 未提交
            //   "CANCELLED"  → 取消订单（包括 CANCELED 拼写差异）
            //   "VOID"       → 作废订单
            //   "HISTORY"    → 历史制单（已 SUBMITTED / ACCEPTED / FULFILLING / 等非 DRAFT 状态）
            //   其他具体状态  → 直接 equals
            String statusMode = status == null || status.isBlank() ? null : status.toUpperCase();
            Long total = jdbc.queryForObject("""
                SELECT count(*) FROM orders o
                WHERE (?::text IS NULL OR (o.order_no ILIKE ? OR o.customer_ref ILIKE ?))
                  AND (?::date IS NULL OR o.created_at >= ?::date)
                  AND (?::date IS NULL OR o.created_at < (?::date + 1))
                  AND (
                    ?::text IS NULL
                    OR (?::text = 'HISTORY' AND o.status NOT IN ('DRAFT', 'CANCELLED', 'CANCELED', 'VOID'))
                    OR o.status = ?::text
                  )
                """, Long.class, search, search, search, dateFrom, dateFrom, dateTo, dateTo,
                statusMode, statusMode, statusMode);

            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT
                  o.id::text          AS id,
                  o.order_no,
                  o.customer_ref,
                  o.status,
                  o.created_at,
                  o.audit_status,
                  o.audited_at,
                  o.audit_name,
                  c.name              AS customer_name,
                  org.name            AS branch_name,
                  (
                    SELECT s.destination_country FROM shipments s
                    WHERE s.tenant_id = o.tenant_id AND s.customer_ref = o.customer_ref
                    LIMIT 1
                  ) AS country,
                  (
                    SELECT ct.tracking_no FROM cartons ct
                    JOIN shipments s2 ON s2.id = ct.shipment_id
                    WHERE s2.tenant_id = o.tenant_id AND s2.customer_ref = o.customer_ref
                      AND ct.tracking_no IS NOT NULL
                    ORDER BY ct.carton_no LIMIT 1
                  ) AS track_no,
                  (
                    SELECT count(*) FROM cartons ct
                    JOIN shipments s3 ON s3.id = ct.shipment_id
                    WHERE s3.tenant_id = o.tenant_id AND s3.customer_ref = o.customer_ref
                  ) AS piece_count,
                  (
                    SELECT coalesce(sum(ct.chargeable_weight_kg), sum(ct.actual_weight_kg))
                    FROM cartons ct
                    JOIN shipments s4 ON s4.id = ct.shipment_id
                    WHERE s4.tenant_id = o.tenant_id AND s4.customer_ref = o.customer_ref
                  ) AS charge_weight,
                  (
                    SELECT coalesce(sum(ch.amount), 0) FROM charges ch
                    JOIN shipments s5 ON s5.id = ch.shipment_id
                    WHERE s5.tenant_id = o.tenant_id AND s5.customer_ref = o.customer_ref
                      AND ch.side = 'AR' AND ch.settlement_status <> 'VOID'
                  ) AS sell_charge,
                  (
                    SELECT coalesce(sum(ch.amount), 0) FROM charges ch
                    JOIN shipments s6 ON s6.id = ch.shipment_id
                    WHERE s6.tenant_id = o.tenant_id AND s6.customer_ref = o.customer_ref
                      AND ch.side = 'AP' AND ch.settlement_status <> 'VOID'
                  ) AS cost_charge,
                  o.metadata
                FROM orders o
                LEFT JOIN customers c ON c.id = o.customer_id
                LEFT JOIN organizations org ON org.id = o.branch_id
                WHERE (?::text IS NULL OR (o.order_no ILIKE ? OR o.customer_ref ILIKE ?))
                  AND (?::date IS NULL OR o.created_at >= ?::date)
                  AND (?::date IS NULL OR o.created_at < (?::date + 1))
                  AND (
                    ?::text IS NULL
                    OR (?::text = 'HISTORY' AND o.status NOT IN ('DRAFT', 'CANCELLED', 'CANCELED', 'VOID'))
                    OR o.status = ?::text
                  )
                ORDER BY o.created_at DESC
                LIMIT ? OFFSET ?
                """, search, search, search, dateFrom, dateFrom, dateTo, dateTo,
                statusMode, statusMode, statusMode, limit, offset);
            return AccPaging.result(rows.stream().map(this::project).toList(),
                total == null ? 0 : total);
        } catch (DataAccessException ex) {
            return AccPaging.result(List.of(), 0);
        }
    }

    @GetMapping("/{id}/raw")
    public Map<String, Object> raw(@PathVariable String id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM orders WHERE id = ?::uuid LIMIT 1", id);
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String orderNo = (String) body.getOrDefault("order_no", body.get("orderNo"));
        String customerRef = (String) body.getOrDefault("customer_ref", body.get("customerRef"));
        Object customerId = body.get("customer_id");
        String id = jdbc.queryForObject("""
            INSERT INTO orders (
              tenant_id, order_no, customer_id, status, source, customer_ref
            ) VALUES (
              current_setting('app.current_tenant_id')::uuid,
              ?, ?::uuid, 'DRAFT', 'LOCAL', ?
            )
            RETURNING id::text
            """, String.class, orderNo, customerId == null ? null : customerId.toString(), customerRef);
        return Map.of("id", id, "order_no", orderNo);
    }

    /**
     * ACC「添加制单」完整表单。对照 ACC PHP `制单中心 → 添加制单` 的 7 个分区：
     * 基本信息 / 发货渠道 / 货物信息 / 发票信息 / 收件人 / 发件人 / 进口商 / 申报明细 / 装箱单明细。
     *
     * 一次 INSERT orders + N 条 declarations + 1 条 shipments + N 条 cartons，
     * 全部 metadata（包裹类型、电池、特殊货物、附加服务、shipper/shipTo 等）持久化到 orders.metadata，
     * 后续 PreSubmit / Submit / RateEngine 都能读到。
     */
    @PostMapping("/full")
    @SuppressWarnings("unchecked")
    public Map<String, Object> createFull(@RequestBody Map<String, Object> body) {
        String orderNo = strOrNull(body.get("orderNo"));
        String customerRef = strOrDefault(body.get("customerRef"), orderNo);
        String customerId = strOrNull(body.get("customerId"));
        if (orderNo == null || orderNo.isBlank()) {
            throw ApiException.badRequest("orderNo 必填");
        }
        if (customerId == null) {
            throw ApiException.badRequest("customerId 必填");
        }
        // 业务字段 → metadata.acc_compat（与 customer-api submit 同结构）
        Map<String, Object> accCompat = new LinkedHashMap<>();
        accCompat.put("product", strOrNull(body.get("product")));
        accCompat.put("channelAccount", strOrNull(body.get("channelAccount")));
        accCompat.put("packageType", strOrNull(body.get("packageType")));
        accCompat.put("batteryType", body.get("batteryType"));
        accCompat.put("specialType", body.get("specialType"));
        accCompat.put("labelType", strOrNull(body.get("labelType")));
        accCompat.put("country", strOrNull(body.get("country")));
        accCompat.put("weight", body.get("weight"));
        accCompat.put("piece", body.get("piece"));
        accCompat.put("volume", body.get("volume"));
        accCompat.put("currency", strOrDefault(body.get("currency"), "USD"));
        accCompat.put("declaredValue", body.get("declaredValue"));
        accCompat.put("services", body.get("services"));
        accCompat.put("remark", strOrNull(body.get("remark")));
        accCompat.put("receiver", body.get("receiver"));
        accCompat.put("shipper", body.get("shipper"));
        accCompat.put("shipTo", body.get("shipTo"));
        accCompat.put("declare", body.get("declare"));
        accCompat.put("packageList", body.get("packageList"));

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("acc_compat", accCompat);
        String metaJson = json.toJson(metadata);

        // 1) INSERT orders
        String orderId = jdbc.queryForObject("""
            INSERT INTO orders (
              tenant_id, order_no, customer_id, status, source, customer_ref, metadata
            ) VALUES (
              current_setting('app.current_tenant_id')::uuid,
              ?, ?::uuid, 'DRAFT', 'LOCAL', ?, ?::jsonb
            )
            RETURNING id::text
            """, String.class, orderNo, customerId, customerRef, metaJson);

        // 申报明细 + 装箱单明细 持久化到 orders.metadata.acc_compat（与 customer-api 同结构）。
        // Submit 阶段再 INSERT 到 declarations / cartons 表（需要 shipment_id 关联）。
        int declareCount = body.get("declare") instanceof List<?> dl ? dl.size() : 0;
        int packageCount = body.get("packageList") instanceof List<?> pl ? pl.size() : 0;

        return Map.of(
            "id", orderId,
            "orderNo", orderNo,
            "declarations", declareCount,
            "packageCount", packageCount,
            "status", "DRAFT"
        );
    }

    private static String strOrNull(Object o) {
        return o == null || o.toString().isBlank() ? null : o.toString();
    }
    private static String strOrDefault(Object o, String def) {
        String s = strOrNull(o);
        return s == null ? def : s;
    }
    private static Object asNumber(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return o;
        try { return new java.math.BigDecimal(o.toString()); } catch (Exception ignored) { return null; }
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String currentAudit = jdbc.queryForObject(
            "SELECT audit_status FROM orders WHERE id = ?::uuid", String.class, id);
        FieldGate.FilterResult gate = fieldGate.filterAllowedFields(TABLE, currentAudit, body);
        if (!gate.rejected().isEmpty() && gate.allowed().isEmpty()) {
            throw ApiException.badRequest("订单已审核，字段不可修改: " + String.join(",", gate.rejected())
                + "；请先反审");
        }
        Map<String, Object> allowed = gate.allowed();
        jdbc.update("""
            UPDATE orders SET
              order_no     = coalesce(?, order_no),
              customer_ref = coalesce(?, customer_ref),
              status       = coalesce(?, status)
            WHERE id = ?::uuid
            """,
            (String) allowed.get("order_no"),
            (String) allowed.get("customer_ref"),
            (String) allowed.get("status"),
            id);
        return Map.of("id", id, "rejectedFields", gate.rejected());
    }

    /**
     * 申请作废订单 — 对应 ACC 制单中心「申请作废」按钮。
     *
     * 仅把 audit_status 置 'PENDING' 进入"作废待审"队列；不立即改 status。
     * 后续走 /api/acc/orders/{id}/audit-biz → OrderVoidAuditSideEffect → status='VOID'。
     */
    @PostMapping("/{id}/request-void")
    public Map<String, Object> requestVoid(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        String reason = body == null ? null : strOrNull(body.get("reason"));
        int n = jdbc.update("""
            UPDATE orders
            SET audit_status = 'PENDING',
                metadata = jsonb_set(
                  coalesce(metadata, '{}'::jsonb),
                  '{acc_compat,void_request_reason}',
                  to_jsonb(coalesce(?::text, ''))
                )
            WHERE id = ?::uuid AND status NOT IN ('VOID', 'DRAFT')
            """, reason, id);
        if (n == 0) {
            throw ApiException.badRequest("订单状态不允许申请作废（DRAFT/VOID 已不需作废）");
        }
        return Map.of("id", id, "audit_status", "PENDING", "void_requested", true);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        String auditStatus = jdbc.queryForObject(
            "SELECT audit_status FROM orders WHERE id = ?::uuid", String.class, id);
        if ("AUDITED".equals(auditStatus)) {
            throw ApiException.badRequest("订单已审核，不能删除，请先反审");
        }
        cascadeChecker.checkBeforeDelete(TABLE, id);
        jdbc.update("DELETE FROM orders WHERE id = ?::uuid", id);
        return Map.of("id", id, "deleted", true);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("orderNo", row.get("customer_ref") != null ? row.get("customer_ref") : row.get("order_no"));
        out.put("trackNo", row.get("track_no"));
        out.put("customerName", row.get("customer_name"));
        // product 来自 metadata.acc_compat.product（API 下单时存在那里）
        String product = "";
        Object meta = row.get("metadata");
        if (meta != null) {
            String metaText = meta instanceof String s ? s : meta.toString();
            try {
                Map<String, Object> m = json.fromJson(metaText,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                Object acc = m == null ? null : m.get("acc_compat");
                if (acc instanceof Map<?, ?> accMap) {
                    Object p = ((Map<String, Object>) accMap).get("product");
                    if (p != null) product = p.toString();
                }
            } catch (RuntimeException ignored) {
                // metadata 解析失败时降级为空字符串
            }
        }
        out.put("product", product);
        out.put("country", row.get("country"));
        out.put("piece", row.get("piece_count"));
        out.put("chargeWeight", row.get("charge_weight"));
        out.put("sellCharge", row.get("sell_charge"));
        out.put("costCharge", row.get("cost_charge"));
        out.put("branch", row.get("branch_name") == null ? "" : row.get("branch_name"));
        out.put("addTime", json.value(row.get("created_at")));
        out.put("status", row.get("status"));
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        out.put("auditName", row.get("audit_name"));
        return out;
    }
}

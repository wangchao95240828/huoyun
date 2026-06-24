package com.xqt.saas.acc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import com.xqt.saas.common.JsonSupport;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ACC 制单中心批量操作 (对应 ACC Online.php 工具栏 + ExpressBatch.php 5 页面)
 *
 *  POST /api/acc/orders/batch-submit         批量提交（DRAFT→SUBMITTED）
 *  POST /api/acc/orders/batch-query          批量查询轨迹
 *  POST /api/acc/orders/batch-void           批量作废
 *  POST /api/acc/orders/batch-recharge       批量计费 / 重算
 *  POST /api/acc/orders/batch-merge          合并制单
 *  POST /api/acc/orders/batch-change-customer  批量变更客户
 *  POST /api/acc/orders/batch-update-tracking  批量更新转单号
 *  POST /api/acc/orders/batch-update-weight    批量更新计费重
 *  POST /api/acc/orders/batch-preview        预览（按 text 粘贴解析 → 返回匹配订单）
 *  POST /api/acc/orders/batch-track          批量追踪快递（含轨迹）
 */
@RestController
@RequestMapping("/api/acc/orders")
public class AccOrderBatchController {

    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final com.xqt.saas.framework.audit.AuditService auditService;
    private final AccOrdersController ordersController;

    public AccOrderBatchController(JdbcTemplate jdbc, JsonSupport json,
                                    com.xqt.saas.framework.audit.AuditService auditService,
                                    @org.springframework.context.annotation.Lazy AccOrdersController ordersController) {
        this.jdbc = jdbc;
        this.json = json;
        this.auditService = auditService;
        this.ordersController = ordersController;
    }

    /**
     * 预览：解析 textarea 输入，按 mode 返回匹配的订单。
     *   mode = update-tracking   → 每行 "运单号 空格 新转单号"
     *   mode = update-weight     → 每行 "运单号 空格 新计费重"
     *   mode = change-customer   → 每行 "运单号"
     *   mode = recharge          → 每行 "运单号"
     *   mode = track             → 每行 "运单号 或 转单号"
     *
     * 返回字段: id / orderNo / weight / country / product / status / trackNo / newValue
     */
    @PostMapping("/batch-preview")
    public Map<String, Object> batchPreview(@RequestBody Map<String, Object> body) {
        String mode = (String) body.getOrDefault("mode", "");
        String text = (String) body.getOrDefault("text", "");
        if (text.isBlank()) throw ApiException.badRequest("text 必填");

        java.util.List<Map<String, Object>> rows = new java.util.ArrayList<>();
        int parsed = 0, matched = 0, missing = 0;
        java.util.List<String> notFound = new java.util.ArrayList<>();

        for (String line : text.split("\\r?\\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            parsed++;
            // 第 1 列总是单号；第 2 列是新值（如有）
            String[] parts = line.split("\\s+", 2);
            String orderNoOrTrack = parts[0];
            String newValue = parts.length > 1 ? parts[1].trim() : null;

            Map<String, Object> row = lookupOrder(orderNoOrTrack);
            if (row == null) {
                missing++;
                notFound.add(orderNoOrTrack);
                continue;
            }
            matched++;
            row.put("newValue", newValue);
            row.put("mode", mode);
            rows.add(row);
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("rows", rows);
        resp.put("parsed", parsed);
        resp.put("matched", matched);
        resp.put("missing", missing);
        if (!notFound.isEmpty()) resp.put("notFound", notFound);
        return resp;
    }

    /** 单号或转单号 → orders 单行（用于预览表）。 */
    private Map<String, Object> lookupOrder(String key) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT o.id::text AS id, o.order_no, o.customer_ref AS track_no,"
                + "       o.status::text AS status, o.customer_id::text AS customer_id,"
                + "       c.name AS customer_name,"
                + "       (SELECT s.destination_country FROM shipments s"
                + "          WHERE s.tenant_id = o.tenant_id AND s.customer_ref = o.customer_ref"
                + "          LIMIT 1) AS country,"
                + "       (SELECT cn.name FROM shipments s"
                + "          JOIN channels cn ON cn.id = s.channel_id"
                + "          WHERE s.tenant_id = o.tenant_id AND s.customer_ref = o.customer_ref"
                + "          LIMIT 1) AS product,"
                + "       (SELECT coalesce(sum(ct.actual_weight_kg), 0) FROM cartons ct"
                + "          JOIN shipments s ON s.id = ct.shipment_id"
                + "          WHERE s.tenant_id = o.tenant_id AND s.customer_ref = o.customer_ref) AS weight"
                + " FROM orders o"
                + " LEFT JOIN customers c ON c.id = o.customer_id"
                + " WHERE o.order_no = ? OR o.customer_ref = ?"
                + " LIMIT 1", key, key);
            if (rows.isEmpty()) return null;
            Map<String, Object> r = rows.get(0);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("id", r.get("id"));
            out.put("orderNo", r.get("order_no"));
            out.put("trackNo", r.get("track_no"));
            out.put("status", r.get("status"));
            out.put("customerId", r.get("customer_id"));
            out.put("customerName", r.get("customer_name"));
            out.put("country", r.get("country"));
            out.put("product", r.get("product"));
            out.put("weight", r.get("weight"));
            return out;
        } catch (DataAccessException ex) {
            return null;
        }
    }

    /** 批量追踪：返回多笔订单的轨迹聚合（ACC 追踪快递页用）。 */
    @PostMapping("/batch-track")
    @SuppressWarnings("unchecked")
    public Map<String, Object> batchTrack(@RequestBody Map<String, Object> body) {
        List<String> ids = (List<String>) body.getOrDefault("ids", List.of());
        if (ids.isEmpty()) throw ApiException.badRequest("请至少选择一项");
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (String id : ids) {
            Map<String, Object> base = lookupOrder(id);
            if (base == null) {
                // 也许是 order_no 不是 uuid，再查一遍
                try {
                    String realId = jdbc.queryForObject(
                        "SELECT id::text FROM orders WHERE order_no = ? OR customer_ref = ? LIMIT 1",
                        String.class, id, id);
                    if (realId != null) base = lookupOrder(realId);
                } catch (DataAccessException ignored) {}
            }
            if (base == null) {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("id", id);
                r.put("notFound", true);
                rows.add(r);
                continue;
            }
            // 拉最近 5 条轨迹
            List<Map<String, Object>> events = jdbc.queryForList(
                "SELECT event_code, event_time, location, description"
                + " FROM tracking_events"
                + " WHERE order_id = ?::uuid OR shipment_id IN ("
                + "   SELECT shipment_id FROM shipment_order_links WHERE order_id = ?::uuid"
                + " )"
                + " ORDER BY event_time DESC LIMIT 5",
                base.get("id"), base.get("id"));
            base.put("events", events);
            rows.add(base);
        }
        return Map.of("rows", rows, "total", rows.size());
    }

    @PostMapping("/batch-submit")
    @SuppressWarnings("unchecked")
    public Map<String, Object> batchSubmit(@RequestBody Map<String, Object> body) {
        // ⚠ 旧版只 UPDATE status='SUBMITTED' 是 silent bug, 不调 UPS, 不建 shipment/charges/label.
        // 修复: 逐单走真 submit 流程 (跟单个 /orders/{id}/submit 同, 调 UPS API + 建 shipment + charges)
        List<String> ids = (List<String>) body.getOrDefault("ids", List.of());
        if (ids.isEmpty()) throw ApiException.badRequest("请至少选择一项");
        int submitted = 0, skipped = 0;
        java.util.List<Map<String, Object>> results = new java.util.ArrayList<>();
        java.util.List<Map<String, Object>> failures = new java.util.ArrayList<>();
        for (String id : ids) {
            try {
                @SuppressWarnings("rawtypes")
                Map r = (Map) ordersController.submit(id);
                results.add(r);
                submitted++;
            } catch (com.xqt.saas.common.ApiException ex) {
                failures.add(Map.of("id", id, "error", ex.getMessage(), "errorCode", "BAD_REQUEST"));
                skipped++;
            } catch (RuntimeException ex) {
                failures.add(Map.of("id", id, "error", ex.getMessage(), "errorCode", "INTERNAL_ERROR"));
                skipped++;
            }
        }
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("submitted", submitted);
        out.put("skipped", skipped);
        out.put("total", ids.size());
        out.put("results", results);
        out.put("failures", failures);
        return out;
    }

    @PostMapping("/batch-query")
    @SuppressWarnings("unchecked")
    public Map<String, Object> batchQuery(@RequestBody Map<String, Object> body) {
        List<String> ids = (List<String>) body.getOrDefault("ids", List.of());
        if (ids.isEmpty()) throw ApiException.badRequest("请至少选择一项");
        // 占位：实际生产应该调 carrier track API 拉最新事件
        List<Map<String, Object>> results = ids.stream().map(id -> {
            try {
                String orderNo = jdbc.queryForObject(
                    "SELECT order_no FROM orders WHERE id = ?::uuid", String.class, id);
                List<Map<String, Object>> events = jdbc.queryForList(
                    "SELECT event_code, event_time, location FROM tracking_events"
                    + " WHERE order_id = ?::uuid ORDER BY event_time DESC LIMIT 5", id);
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("id", id);
                r.put("orderNo", orderNo);
                r.put("events", events);
                return r;
            } catch (DataAccessException ex) {
                return Map.<String, Object>of("id", id, "error", ex.getMessage());
            }
        }).toList();
        return Map.of("data", results, "total", results.size());
    }

    @PostMapping("/batch-void")
    @SuppressWarnings("unchecked")
    @Transactional
    public Map<String, Object> batchVoid(@RequestBody Map<String, Object> body) {
        List<String> ids = (List<String>) body.getOrDefault("ids", List.of());
        if (ids.isEmpty()) throw ApiException.badRequest("请至少选择一项");
        String reason = (String) body.getOrDefault("reason", "批量作废");
        int voided = 0, skipped = 0;
        for (String id : ids) {
            int n = jdbc.update(
                "UPDATE orders SET"
                + "  metadata = coalesce(metadata,'{}'::jsonb) || jsonb_build_object("
                + "    'void_request', jsonb_build_object('reason', ?::text, 'requested_at', now()::text)),"
                + "  audit_status = 'UNAUDITED',"
                + "  updated_at = now()"
                + " WHERE id = ?::uuid AND status NOT IN ('CANCELLED','COMPLETED')",
                reason, id);
            if (n > 0) voided++; else skipped++;
        }
        return Map.of("voided", voided, "skipped", skipped, "total", ids.size());
    }

    /** 作废订单 → 批量审核（通过则真的把 orders.status 改 CANCELLED） */
    @PostMapping("/batch-audit-void")
    @SuppressWarnings("unchecked")
    @Transactional
    public Map<String, Object> batchAuditVoid(@RequestBody Map<String, Object> body) {
        List<String> ids = (List<String>) body.getOrDefault("ids", List.of());
        if (ids.isEmpty()) throw ApiException.badRequest("请至少选择一项");
        // P0-B8 修复: 走 AuditService.audit 触发 OrderVoidAuditSideEffect
        // 回滚客户预扣账户余额 + 反向 balance_ledger + charges 标 VOID.
        // 之前直接 SQL UPDATE 跳过副作用 → 客户欠款流水跟实际状态错位.
        String tenantId = jdbc.queryForObject(
            "SELECT current_setting('app.current_tenant_id')", String.class);
        String actor = jdbc.queryForObject(
            "SELECT current_setting('app.user_name', true)", String.class);
        if (actor == null || actor.isBlank()) actor = "system";
        int approved = 0, skipped = 0;
        for (String id : ids) {
            // 申请作废写 metadata.acc_compat.void_request_reason (跟 OrdersController.requestVoid 对齐)
            // 兼容旧 batchVoid 写的 metadata.void_request
            Integer ok = jdbc.queryForObject("""
                SELECT count(*) FROM orders WHERE id = ?::uuid
                  AND (metadata->'void_request' IS NOT NULL
                       OR metadata #>> '{acc_compat,void_request_reason}' IS NOT NULL)
                  AND audit_status <> 'AUDITED'
                """, Integer.class, id);
            if (ok == null || ok == 0) { skipped++; continue; }
            try {
                auditService.audit("orders", id, tenantId, actor);
                // 副作用 (OrderVoidAuditSideEffect) 已经把 status 改 CANCELLED + 反向 ledger
                jdbc.update("""
                    UPDATE orders SET
                      metadata = coalesce(metadata,'{}'::jsonb) || jsonb_build_object(
                        'void_approved_at', now()::text)
                    WHERE id = ?::uuid
                    """, id);
                approved++;
            } catch (Exception ex) {
                skipped++;
            }
        }
        return Map.of("approved", approved, "skipped", skipped, "total", ids.size());
    }

    /** 作废订单 → 批量恢复（撤销作废申请；如已审核则把 CANCELLED 改回 DRAFT/SUBMITTED） */
    @PostMapping("/batch-restore")
    @SuppressWarnings("unchecked")
    @Transactional
    public Map<String, Object> batchRestore(@RequestBody Map<String, Object> body) {
        List<String> ids = (List<String>) body.getOrDefault("ids", List.of());
        if (ids.isEmpty()) throw ApiException.badRequest("请至少选择一项");
        int restored = 0, skipped = 0;
        for (String id : ids) {
            int n = jdbc.update(
                "UPDATE orders SET"
                + "  status = CASE WHEN status = 'CANCELLED' THEN"
                + "    coalesce((metadata->>'status_before_void'), 'DRAFT')"
                + "  ELSE status END,"
                + "  audit_status = 'PENDING',"
                + "  metadata = (coalesce(metadata,'{}'::jsonb) - 'void_request') - 'void_approved_at',"
                + "  updated_at = now()"
                + " WHERE id = ?::uuid"
                + "   AND (metadata->'void_request' IS NOT NULL OR status = 'CANCELLED')",
                id);
            if (n > 0) restored++; else skipped++;
        }
        return Map.of("restored", restored, "skipped", skipped, "total", ids.size());
    }

    /** 彻底删除（取消订单 + 作废订单页都用，物理删 DRAFT/CANCELLED 状态） */
    @PostMapping("/batch-hard-delete")
    @SuppressWarnings("unchecked")
    @Transactional
    public Map<String, Object> batchHardDelete(@RequestBody Map<String, Object> body) {
        List<String> ids = (List<String>) body.getOrDefault("ids", List.of());
        if (ids.isEmpty()) throw ApiException.badRequest("请至少选择一项");
        int deleted = 0, skipped = 0;
        for (String id : ids) {
            int n = jdbc.update(
                "DELETE FROM orders WHERE id = ?::uuid"
                + " AND status IN ('DRAFT','CANCELLED','VOID')", id);
            if (n > 0) deleted++; else skipped++;
        }
        return Map.of("deleted", deleted, "skipped", skipped, "total", ids.size());
    }

    @PostMapping("/batch-recharge")
    @SuppressWarnings("unchecked")
    @Transactional
    public Map<String, Object> batchRecharge(@RequestBody Map<String, Object> body) {
        List<String> ids = (List<String>) body.getOrDefault("ids", List.of());
        if (ids.isEmpty()) throw ApiException.badRequest("请至少选择一项");
        boolean applyCurrentTime = Boolean.TRUE.equals(body.get("applyCurrentTime"));
        boolean overrideAudited = Boolean.TRUE.equals(body.get("overrideAudited"));
        int rerated = 0, skipped = 0;
        for (String id : ids) {
            int n = jdbc.update(
                "UPDATE orders SET"
                + "  metadata = coalesce(metadata,'{}'::jsonb) || jsonb_build_object("
                + "    'recharge_requested_at', now()::text,"
                + "    'recharge_apply_current_time', ?::boolean,"
                + "    'recharge_override_audited', ?::boolean),"
                + "  updated_at = now()"
                + " WHERE id = ?::uuid", applyCurrentTime, overrideAudited, id);
            // 把 charges 标 DRAFT 等重算; overrideAudited=true 时连已审的也重算
            if (overrideAudited) {
                jdbc.update(
                    "UPDATE charges SET status='DRAFT', audit_status='UNAUDITED'"
                    + " WHERE shipment_id IN ("
                    + "   SELECT shipment_id FROM shipment_order_links WHERE order_id = ?::uuid"
                    + " )", id);
            } else {
                jdbc.update(
                    "UPDATE charges SET status='DRAFT'"
                    + " WHERE shipment_id IN ("
                    + "   SELECT shipment_id FROM shipment_order_links WHERE order_id = ?::uuid"
                    + " ) AND audit_status <> 'AUDITED'", id);
            }
            if (n > 0) rerated++; else skipped++;
        }
        return Map.of("rerated", rerated, "skipped", skipped, "total", ids.size());
    }

    @PostMapping("/batch-merge")
    @SuppressWarnings("unchecked")
    @Transactional
    public Map<String, Object> batchMerge(@RequestBody Map<String, Object> body) {
        List<String> ids = (List<String>) body.getOrDefault("ids", List.of());
        // ACC CBill.php L185: 请至少选择两个同客户的账号进行合并
        if (ids.size() < 2) throw ApiException.badRequest("请至少选择 2 个同客户的订单进行合并");
        // ACC CBill.php L2266: 合并账单只能合并同一个客户的
        List<String> distinctCustomers = jdbc.queryForList("""
            SELECT DISTINCT customer_id::text FROM orders WHERE id = ANY(?::uuid[])
            """, String.class, (Object) ids.toArray(new String[0]));
        if (distinctCustomers.size() > 1) {
            throw ApiException.badRequest("合并订单只能合并同一个客户的");
        }
        // 有已审核 charge 的不能合并
        List<String> hasAudited = jdbc.queryForList("""
            SELECT DISTINCT o.order_no FROM orders o
              JOIN charges c ON c.order_id = o.id
             WHERE o.id = ANY(?::uuid[]) AND c.audit_status='AUDITED'
            """, String.class, (Object) ids.toArray(new String[0]));
        if (!hasAudited.isEmpty()) {
            throw ApiException.badRequest("含有已审核费用的订单不能合并: " + String.join(",", hasAudited));
        }
        // 用第 1 个作为主单，其余 metadata.merged_into = 主单 id, 自身 status=CANCELLED
        String masterId = ids.get(0);
        int merged = 0;
        for (int i = 1; i < ids.size(); i++) {
            String id = ids.get(i);
            int n = jdbc.update(
                "UPDATE orders SET status='CANCELLED', updated_at=now(),"
                + "  metadata = coalesce(metadata,'{}'::jsonb) || jsonb_build_object("
                + "    'merged_into', ?::text)"
                + " WHERE id = ?::uuid AND status NOT IN ('CANCELLED','COMPLETED')",
                masterId, id);
            if (n > 0) merged++;
        }
        // 主单 metadata 加 merged_from 列表
        jdbc.update(
            "UPDATE orders SET"
            + "  metadata = coalesce(metadata,'{}'::jsonb) || jsonb_build_object("
            + "    'merged_from', ?::jsonb)"
            + " WHERE id = ?::uuid",
            json.toJson(ids.subList(1, ids.size())), masterId);
        return Map.of("master", masterId, "merged", merged, "total", ids.size());
    }

    @PostMapping("/batch-change-customer")
    @SuppressWarnings("unchecked")
    @Transactional
    public Map<String, Object> batchChangeCustomer(@RequestBody Map<String, Object> body) {
        List<String> ids = (List<String>) body.getOrDefault("ids", List.of());
        String newCustomerId = (String) body.get("customerId");
        // ACC ExpressBatch.php L1789: 请至少选择一项
        if (ids.isEmpty()) throw ApiException.badRequest("请至少选择一项");
        if (newCustomerId == null || newCustomerId.isBlank()) {
            // ACC L1563/L1799: 找不到指定的客户
            throw ApiException.badRequest("找不到指定的客户，请选择");
        }
        // ACC L1799: 客户必须存在
        Integer custExists = jdbc.queryForObject(
            "SELECT count(*) FROM customers WHERE id = ?::uuid", Integer.class, newCustomerId);
        if (custExists == null || custExists == 0) {
            throw ApiException.badRequest("找不到指定的客户: " + newCustomerId);
        }
        // ACC ExpressBatch.php L1818: 部分快件存在已审核的费用
        List<String> badOrders = jdbc.queryForList("""
            SELECT o.order_no FROM orders o
              JOIN charges ch ON ch.order_id = o.id
             WHERE o.id = ANY(?::uuid[]) AND ch.audit_status = 'AUDITED'
            """, String.class, (Object) ids.toArray(new String[0]));
        if (!badOrders.isEmpty()) {
            throw ApiException.badRequest(
                "部分快件存在已审核的费用，无法更换客户: " + String.join(",", badOrders));
        }
        // ACC L1820: 检测客户没变化（避免无意义操作）
        Integer sameCount = jdbc.queryForObject("""
            SELECT count(*) FROM orders
             WHERE id = ANY(?::uuid[]) AND customer_id = ?::uuid
            """, Integer.class, (Object) ids.toArray(new String[0]), newCustomerId);
        if (sameCount != null && sameCount == ids.size()) {
            throw ApiException.badRequest("所选快件的客户没有变化，无需操作");
        }
        int updated = 0;
        for (String id : ids) {
            int n = jdbc.update(
                "UPDATE orders SET customer_id = ?::uuid, updated_at = now()"
                + " WHERE id = ?::uuid AND status IN ('DRAFT','SUBMITTED')",
                newCustomerId, id);
            if (n > 0) updated++;
        }
        return Map.of("updated", updated, "total", ids.size());
    }

    @PostMapping("/batch-update-tracking")
    @SuppressWarnings("unchecked")
    @Transactional
    public Map<String, Object> batchUpdateTracking(@RequestBody Map<String, Object> body) {
        List<Map<String, Object>> rows = (List<Map<String, Object>>) body.getOrDefault("rows", List.of());
        if (rows.isEmpty()) throw ApiException.badRequest("请至少添加一票件");
        // 行级校验 — 对齐 ACC Orders.php L1770-L1780
        java.util.Map<String, Integer> seen = new java.util.HashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> r = rows.get(i);
            int line = i + 1;
            String id = (String) r.get("id");
            String tn = (String) r.get("trackingNo");
            // ACC L1770
            if (tn == null || tn.isBlank()) {
                throw ApiException.badRequest("第 " + line + " 行：追踪号不能为空");
            }
            // ACC L1772：批量内重复检测（大小写不敏感）
            String key = tn.toLowerCase();
            Integer prev = seen.put(key, line);
            if (prev != null) {
                throw ApiException.badRequest("第 " + line + " 行：追踪号与第 " + prev + " 行重复");
            }
            // ACC L1246/L1252：跨快件占用
            if (id != null) {
                Integer dup = jdbc.queryForObject("""
                    SELECT count(*) FROM cartons ct
                      JOIN shipment_order_links sol ON sol.shipment_id = ct.shipment_id
                     WHERE (ct.tracking_no = ? OR ct.carrier_master_tracking_no = ?)
                       AND sol.order_id <> ?::uuid
                    """, Integer.class, tn, tn, id);
                if (dup != null && dup > 0) {
                    throw ApiException.badRequest("第 " + line + " 行：追踪号 [" + tn + "] 已被其它快件占用");
                }
            }
        }
        int updated = 0;
        for (Map<String, Object> r : rows) {
            String id = (String) r.get("id");
            String newTrackingNo = (String) r.get("trackingNo");
            if (id == null) continue;
            int n = jdbc.update(
                "UPDATE shipments SET customer_ref = ?"
                + " WHERE id IN ("
                + "   SELECT shipment_id FROM shipment_order_links WHERE order_id = ?::uuid"
                + " )", newTrackingNo, id);
            if (n > 0) updated++;
        }
        return Map.of("updated", updated, "total", rows.size());
    }

    @PostMapping("/batch-update-weight")
    @SuppressWarnings("unchecked")
    @Transactional
    public Map<String, Object> batchUpdateWeight(@RequestBody Map<String, Object> body) {
        List<Map<String, Object>> rows = (List<Map<String, Object>>) body.getOrDefault("rows", List.of());
        if (rows.isEmpty()) throw ApiException.badRequest("请至少添加一票件");
        // 行级校验 — 对齐 ACC Orders.php L1774-L1780
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> r = rows.get(i);
            int line = i + 1;
            String id = (String) r.get("id");
            if (id == null || id.isBlank()) {
                throw ApiException.badRequest("第 " + line + " 行：订单 ID 不能为空");
            }
            // ACC L1774：实重必须为大于0的数字
            Object w = r.get("chargeableKg");
            if (!(w instanceof Number wn) || wn.doubleValue() <= 0) {
                throw ApiException.badRequest("第 " + line + " 行：实重必须为大于 0 的数字");
            }
            // ACC L1776/L1778/L1780：长度/宽度/高度要么为空，要么大于 0
            String[] dimensions = {"length", "width", "height"};
            String[] zhNames    = {"长度", "宽度", "高度"};
            for (int d = 0; d < 3; d++) {
                Object val = r.get(dimensions[d]);
                if (val != null && val.toString().length() > 0) {
                    if (!(val instanceof Number dn) || dn.doubleValue() <= 0) {
                        throw ApiException.badRequest(
                            "第 " + line + " 行：" + zhNames[d] + "要么为空，要么必须为大于 0 的数字");
                    }
                }
            }
        }
        int updated = 0;
        for (Map<String, Object> r : rows) {
            String id = (String) r.get("id");
            Object w = r.get("chargeableKg");
            jdbc.update(
                "UPDATE orders SET"
                + "  metadata = coalesce(metadata,'{}'::jsonb) || jsonb_build_object("
                + "    'manual_chargeable_kg', ?::numeric),"
                + "  updated_at = now()"
                + " WHERE id = ?::uuid",
                ((Number) w).doubleValue(), id);
            updated++;
        }
        return Map.of("updated", updated, "total", rows.size());
    }
}

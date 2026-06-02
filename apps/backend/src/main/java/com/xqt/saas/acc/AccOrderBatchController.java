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
 *  POST /api/acc/orders/batch-query          批量查询轨迹（占位返回 stub）
 *  POST /api/acc/orders/batch-void           批量作废（写 audit_events VOID 申请）
 *  POST /api/acc/orders/batch-recharge       批量计费 / 重算（再跑 RateEngine）
 *  POST /api/acc/orders/batch-merge          合并制单（多单合一）
 *  POST /api/acc/orders/batch-change-customer  批量变更客户
 *  POST /api/acc/orders/batch-update-tracking  批量更新转单号
 *  POST /api/acc/orders/batch-update-weight    批量更新计费重
 */
@RestController
@RequestMapping("/api/acc/orders")
public class AccOrderBatchController {

    private final JdbcTemplate jdbc;
    private final JsonSupport json;

    public AccOrderBatchController(JdbcTemplate jdbc, JsonSupport json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @PostMapping("/batch-submit")
    @SuppressWarnings("unchecked")
    @Transactional
    public Map<String, Object> batchSubmit(@RequestBody Map<String, Object> body) {
        List<String> ids = (List<String>) body.getOrDefault("ids", List.of());
        if (ids.isEmpty()) throw ApiException.badRequest("ids 必填");
        int updated = 0, skipped = 0;
        for (String id : ids) {
            int n = jdbc.update(
                "UPDATE orders SET status='SUBMITTED', submitted_at=now(), updated_at=now()"
                + " WHERE id = ?::uuid AND status='DRAFT'", id);
            if (n > 0) updated++; else skipped++;
        }
        return Map.of("submitted", updated, "skipped", skipped, "total", ids.size());
    }

    @PostMapping("/batch-query")
    @SuppressWarnings("unchecked")
    public Map<String, Object> batchQuery(@RequestBody Map<String, Object> body) {
        List<String> ids = (List<String>) body.getOrDefault("ids", List.of());
        if (ids.isEmpty()) throw ApiException.badRequest("ids 必填");
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
        if (ids.isEmpty()) throw ApiException.badRequest("ids 必填");
        String reason = (String) body.getOrDefault("reason", "批量作废");
        int voided = 0, skipped = 0;
        for (String id : ids) {
            // 把 metadata.void_request 标上, 等审核通过再实际作废
            int n = jdbc.update(
                "UPDATE orders SET"
                + "  metadata = coalesce(metadata,'{}'::jsonb) || jsonb_build_object("
                + "    'void_request', jsonb_build_object('reason', ?::text, 'requested_at', now())),"
                + "  audit_status = 'UNAUDITED',"
                + "  updated_at = now()"
                + " WHERE id = ?::uuid AND status NOT IN ('CANCELLED','COMPLETED')",
                reason, id);
            if (n > 0) voided++; else skipped++;
        }
        return Map.of("voided", voided, "skipped", skipped, "total", ids.size());
    }

    @PostMapping("/batch-recharge")
    @SuppressWarnings("unchecked")
    @Transactional
    public Map<String, Object> batchRecharge(@RequestBody Map<String, Object> body) {
        List<String> ids = (List<String>) body.getOrDefault("ids", List.of());
        if (ids.isEmpty()) throw ApiException.badRequest("ids 必填");
        int rerated = 0, skipped = 0;
        for (String id : ids) {
            // 简化：标记需要重新计费，下次 Submit 时引擎会重新跑
            int n = jdbc.update(
                "UPDATE orders SET"
                + "  metadata = coalesce(metadata,'{}'::jsonb) || jsonb_build_object("
                + "    'recharge_requested_at', now()::text),"
                + "  updated_at = now()"
                + " WHERE id = ?::uuid", id);
            // 同时把 charges 标 DRAFT 等重算
            jdbc.update(
                "UPDATE charges SET status='DRAFT'"
                + " WHERE shipment_id IN ("
                + "   SELECT shipment_id FROM shipment_order_links WHERE order_id = ?::uuid"
                + " )", id);
            if (n > 0) rerated++; else skipped++;
        }
        return Map.of("rerated", rerated, "skipped", skipped, "total", ids.size());
    }

    @PostMapping("/batch-merge")
    @SuppressWarnings("unchecked")
    @Transactional
    public Map<String, Object> batchMerge(@RequestBody Map<String, Object> body) {
        List<String> ids = (List<String>) body.getOrDefault("ids", List.of());
        if (ids.size() < 2) throw ApiException.badRequest("至少选 2 个订单合并");
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
        if (ids.isEmpty() || newCustomerId == null) throw ApiException.badRequest("ids 和 customerId 必填");
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
        if (rows.isEmpty()) throw ApiException.badRequest("rows 必填");
        int updated = 0;
        for (Map<String, Object> r : rows) {
            String id = (String) r.get("id");
            String newTrackingNo = (String) r.get("trackingNo");
            if (id == null || newTrackingNo == null) continue;
            // 更新关联的 shipment.customer_ref（作为外部 tracking 号载体）
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
        if (rows.isEmpty()) throw ApiException.badRequest("rows 必填");
        int updated = 0;
        for (Map<String, Object> r : rows) {
            String id = (String) r.get("id");
            Object w = r.get("chargeableKg");
            if (id == null || !(w instanceof Number n)) continue;
            // 找 shipment 下所有 cartons 等比例分配，或直接更新 cartons.chargeable_weight_kg 第一行
            // 简化处理：写 metadata，等核算用
            jdbc.update(
                "UPDATE orders SET"
                + "  metadata = coalesce(metadata,'{}'::jsonb) || jsonb_build_object("
                + "    'manual_chargeable_kg', ?::numeric),"
                + "  updated_at = now()"
                + " WHERE id = ?::uuid",
                n.doubleValue(), id);
            updated++;
        }
        return Map.of("updated", updated, "total", rows.size());
    }
}

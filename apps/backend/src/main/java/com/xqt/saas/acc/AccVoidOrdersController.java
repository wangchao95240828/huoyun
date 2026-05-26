package com.xqt.saas.acc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.JsonSupport;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * /api/acc/void-orders — 订单作废视图。只读，是 orders WHERE status='CANCELLED' 的快捷查询。
 * 前端列：No / TrackNo / CustomerName / Status / Amount / Paid / AddName / AddTime（大写驼峰，沿用旧 ACC）。
 */
@RestController
@RequestMapping("/api/acc/void-orders")
public class AccVoidOrdersController {
    private final JdbcTemplate jdbc;
    private final JsonSupport json;

    public AccVoidOrdersController(JdbcTemplate jdbc, JsonSupport json) {
        this.jdbc = jdbc;
        this.json = json;
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
                SELECT count(*) FROM orders o
                WHERE o.status = 'CANCELLED'
                  AND (?::text IS NULL OR o.order_no ILIKE ? OR o.customer_ref ILIKE ?)
                  AND (?::date IS NULL OR o.created_at >= ?::date)
                  AND (?::date IS NULL OR o.created_at < (?::date + 1))
                """, Long.class, search, search, search, dateFrom, dateFrom, dateTo, dateTo);
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT o.id::text   AS id,
                       o.order_no, o.customer_ref, o.status, o.created_at,
                       c.name      AS customer_name,
                       (
                         SELECT ct.tracking_no FROM cartons ct
                         JOIN shipments s ON s.id = ct.shipment_id
                         WHERE s.customer_ref = o.customer_ref AND s.tenant_id = o.tenant_id
                           AND ct.tracking_no IS NOT NULL
                         LIMIT 1
                       ) AS track_no,
                       (
                         SELECT coalesce(sum(ch.amount), 0) FROM charges ch
                         JOIN shipments s2 ON s2.id = ch.shipment_id
                         WHERE s2.customer_ref = o.customer_ref AND s2.tenant_id = o.tenant_id
                           AND ch.side = 'AR'
                       ) AS amount,
                       (
                         SELECT coalesce(sum(p.amount), 0) FROM payments p
                         WHERE p.tenant_id = o.tenant_id AND p.reference_no = o.order_no
                       ) AS paid
                FROM orders o
                LEFT JOIN customers c ON c.id = o.customer_id
                WHERE o.status = 'CANCELLED'
                  AND (?::text IS NULL OR o.order_no ILIKE ? OR o.customer_ref ILIKE ?)
                  AND (?::date IS NULL OR o.created_at >= ?::date)
                  AND (?::date IS NULL OR o.created_at < (?::date + 1))
                ORDER BY o.created_at DESC
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
            "SELECT * FROM orders WHERE id = ?::uuid LIMIT 1", id);
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        // 旧 ACC 字段大写驼峰（前端 accColumns 也用大写）
        out.put("id", row.get("id"));
        out.put("No", row.get("customer_ref") != null ? row.get("customer_ref") : row.get("order_no"));
        out.put("TrackNo", row.get("track_no"));
        out.put("CustomerName", row.get("customer_name"));
        out.put("Status", row.get("status"));
        out.put("Amount", row.get("amount"));
        out.put("Paid", row.get("paid"));
        out.put("AddName", "");                  // 新模型没有操作员字段，留空
        out.put("AddTime", json.value(row.get("created_at")));
        return out;
    }
}

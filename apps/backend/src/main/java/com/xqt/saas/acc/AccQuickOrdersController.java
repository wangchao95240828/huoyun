package com.xqt.saas.acc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.JsonSupport;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * /api/acc/quick-orders — orders 表的简化视图（quick-order 入口或全量简化展示）。
 * 只读，无 audit 流。
 */
@RestController
@RequestMapping("/api/acc/quick-orders")
public class AccQuickOrdersController {

    private final JdbcTemplate jdbc;
    private final JsonSupport json;

    public AccQuickOrdersController(JdbcTemplate jdbc, JsonSupport json) {
        this.jdbc = jdbc;
        this.json = json;
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

            long total = json.value(jdbc.queryForObject("""
                SELECT count(*) FROM orders
                WHERE (?::text IS NULL OR (customer_ref ILIKE ? OR customer_name ILIKE ?))
                  AND order_entry_type = 'QUICK_ORDER'
                """, Long.class, search, search, search)) instanceof Number n ? n.longValue() : 0;
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT o.id::text AS id, o.customer_ref, o.customer_name, o.customer_id::text AS customer_id,
                       o.channel_id::text AS channel_id, o.order_date, o.status,
                       o.origin, o.destination, o.package_count, o.weight,
                       o.audit_status, o.audited_at, o.audit_name, o.created_at
                FROM orders o
                WHERE (?::text IS NULL OR (o.customer_ref ILIKE ? OR o.customer_name ILIKE ?))
                  AND o.order_entry_type = 'QUICK_ORDER'
                ORDER BY o.created_at DESC
                LIMIT ? OFFSET ?
                """, search, search, search, limit, offset);
            return AccPaging.result(rows.stream().map(this::project).toList(), total);
        } catch (DataAccessException ex) {
            return AccPaging.result(List.of(), 0);
        }
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("customerRef", row.get("customer_ref"));
        out.put("customerName", row.get("customer_name"));
        out.put("customerId", row.get("customer_id"));
        out.put("channelId", row.get("channel_id"));
        out.put("orderDate", row.get("order_date"));
        out.put("status", row.get("status"));
        out.put("origin", row.get("origin"));
        out.put("destination", row.get("destination"));
        out.put("packageCount", row.get("package_count"));
        out.put("weight", row.get("weight"));
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        out.put("auditName", row.get("audit_name"));
        return out;
    }
}

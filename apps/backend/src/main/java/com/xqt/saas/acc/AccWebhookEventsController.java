package com.xqt.saas.acc;

import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * /api/acc/webhook-events —— 出站投递事件查看（只读 + 手动重试）。
 */
@RestController
@RequestMapping("/api/acc/webhook-events")
public class AccWebhookEventsController {
    private final JdbcTemplate jdbc;

    public AccWebhookEventsController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public Map<String, Object> list(
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer pageSize,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String eventType,
        @RequestParam(required = false) String dateFrom,
        @RequestParam(required = false) String dateTo
    ) {
        int limit = AccPaging.pageSize(pageSize);
        int offset = AccPaging.offset(page, pageSize);

        Long total = jdbc.queryForObject("""
            SELECT count(*) FROM webhook_events
            WHERE (?::text IS NULL OR status = ?)
              AND (?::text IS NULL OR event_type = ?)
              AND (?::date IS NULL OR created_at >= ?::date)
              AND (?::date IS NULL OR created_at < (?::date + 1))
            """, Long.class, status, status, eventType, eventType,
            dateFrom, dateFrom, dateTo, dateTo);

        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT e.id::text AS id,
                   e.event_type, e.status, e.attempt_count,
                   e.next_attempt_at, e.last_response_code, e.last_response_body,
                   e.last_error, e.created_at, e.delivered_at,
                   ep.url AS endpoint_url,
                   c.code AS customer_code, c.name AS customer_name
            FROM webhook_events e
            LEFT JOIN webhook_endpoints ep ON ep.id = e.endpoint_id
            LEFT JOIN customers c ON c.id = ep.customer_id
            WHERE (?::text IS NULL OR e.status = ?)
              AND (?::text IS NULL OR e.event_type = ?)
              AND (?::date IS NULL OR e.created_at >= ?::date)
              AND (?::date IS NULL OR e.created_at < (?::date + 1))
            ORDER BY e.created_at DESC
            LIMIT ? OFFSET ?
            """, status, status, eventType, eventType,
            dateFrom, dateFrom, dateTo, dateTo, limit, offset);

        return Map.of("data", rows, "total", total == null ? 0 : total);
    }

    @GetMapping("/{id}/payload")
    public Map<String, Object> payload(@PathVariable String id) {
        Map<String, Object> row;
        try {
            row = jdbc.queryForMap(
                "SELECT id::text AS id, payload FROM webhook_events WHERE id = ?::uuid", id);
        } catch (org.springframework.dao.DataAccessException ex) {
            throw ApiException.notFound("事件不存在");
        }
        return row;
    }

    /** 手动重试：把 DEAD / FAILED / SUCCESS 改回 PENDING，next_attempt_at=now，让 dispatcher 立即重发。 */
    @PostMapping("/{id}/retry")
    public Map<String, Object> retry(@PathVariable String id) {
        int n = jdbc.update("""
            UPDATE webhook_events SET
              status = 'PENDING',
              next_attempt_at = now(),
              last_error = NULL
            WHERE id = ?::uuid
            """, id);
        if (n == 0) throw ApiException.notFound("事件不存在");
        return Map.of("id", id, "requeued", true);
    }
}

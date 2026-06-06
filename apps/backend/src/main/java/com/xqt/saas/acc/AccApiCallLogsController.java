package com.xqt.saas.acc;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * /api/acc/api-call-logs —— 入站 API 调用流水（CustomerApiAuthFilter 触发后落库）。
 * 只读端点：列表（默认按 created_at desc）+ 简单统计。
 */
@RestController
@RequestMapping("/api/acc/api-call-logs")
public class AccApiCallLogsController {
    private final JdbcTemplate jdbc;

    public AccApiCallLogsController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public Map<String, Object> list(
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer pageSize,
        @RequestParam(required = false) String accessKey,
        @RequestParam(required = false) String endpoint,
        @RequestParam(required = false) Integer httpStatus,
        @RequestParam(required = false) String dateFrom,
        @RequestParam(required = false) String dateTo
    ) {
        int limit = AccPaging.pageSize(pageSize);
        int offset = AccPaging.offset(page, pageSize);
        String akPat = accessKey == null || accessKey.isBlank() ? null : "%" + accessKey + "%";
        String epPat = endpoint == null || endpoint.isBlank() ? null : "%" + endpoint + "%";

        Long total = jdbc.queryForObject("""
            SELECT count(*) FROM api_call_logs
            WHERE (?::text IS NULL OR access_key ILIKE ?)
              AND (?::text IS NULL OR endpoint ILIKE ?)
              AND (?::int IS NULL OR http_status = ?)
              AND (?::date IS NULL OR created_at >= ?::date)
              AND (?::date IS NULL OR created_at < (?::date + 1))
            """, Long.class,
            akPat, akPat, epPat, epPat, httpStatus, httpStatus,
            dateFrom, dateFrom, dateTo, dateTo);

        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT l.id::text AS id,
                   l.access_key, l.endpoint, l.method, l.http_status,
                   l.error_code, l.duration_ms, l.ip::text AS ip,
                   l.user_agent, l.request_id, l.request_summary,
                   l.created_at,
                   c.name AS owner_name, c.code AS owner_code
            FROM api_call_logs l
            LEFT JOIN api_credentials ac ON ac.id = l.credential_id
            LEFT JOIN customers c ON c.id = ac.owner_id
            WHERE (?::text IS NULL OR l.access_key ILIKE ?)
              AND (?::text IS NULL OR l.endpoint ILIKE ?)
              AND (?::int IS NULL OR l.http_status = ?)
              AND (?::date IS NULL OR l.created_at >= ?::date)
              AND (?::date IS NULL OR l.created_at < (?::date + 1))
            ORDER BY l.created_at DESC
            LIMIT ? OFFSET ?
            """,
            akPat, akPat, epPat, epPat, httpStatus, httpStatus,
            dateFrom, dateFrom, dateTo, dateTo, limit, offset);

        return Map.of("data", rows, "total", total == null ? 0 : total);
    }

    @GetMapping("/stats")
    public Map<String, Object> stats(@RequestParam(required = false) Integer days) {
        int d = days == null || days <= 0 ? 7 : Math.min(days, 90);
        Map<String, Object> byStatus = jdbc.queryForMap("""
            SELECT
              count(*) FILTER (WHERE http_status BETWEEN 200 AND 299) AS ok,
              count(*) FILTER (WHERE http_status BETWEEN 400 AND 499) AS client_err,
              count(*) FILTER (WHERE http_status >= 500) AS server_err,
              count(*) AS total,
              coalesce(avg(duration_ms)::int, 0) AS avg_ms,
              coalesce(percentile_cont(0.95) WITHIN GROUP (ORDER BY duration_ms)::int, 0) AS p95_ms
            FROM api_call_logs WHERE created_at >= now() - (? || ' days')::interval
            """, String.valueOf(d));
        List<Map<String, Object>> byEndpoint = jdbc.queryForList("""
            SELECT endpoint, count(*) AS n,
                   coalesce(avg(duration_ms)::int, 0) AS avg_ms
            FROM api_call_logs WHERE created_at >= now() - (? || ' days')::interval
            GROUP BY endpoint ORDER BY n DESC LIMIT 10
            """, String.valueOf(d));
        return Map.of("byStatus", byStatus, "byEndpoint", byEndpoint, "days", d);
    }
}

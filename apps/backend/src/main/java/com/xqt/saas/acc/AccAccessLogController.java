package com.xqt.saas.acc;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 访问日志 — 对齐 ACC Access.php (登录访问日志查看)。
 *
 *   GET /api/acc/access-log/recent?limit=50  最近登录尝试
 *   GET /api/acc/access-log/by-user?userId=  某账号历史
 *   GET /api/acc/access-log/failed?since=24  失败统计
 */
@RestController
@RequestMapping("/api/acc/access-log")
public class AccAccessLogController {
    private final JdbcTemplate jdbc;

    public AccAccessLogController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/recent")
    public Map<String, Object> recent(@RequestParam(defaultValue = "50") int limit) {
        int max = Math.min(limit, 500);
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT id::text, username, success, failure_reason, ip::text, user_agent,
                   created_at, user_id::text
              FROM auth_login_events
             ORDER BY created_at DESC LIMIT ?
            """, max);
        return Map.of("data", rows, "total", rows.size());
    }

    @GetMapping("/by-user")
    public Map<String, Object> byUser(@RequestParam String userId,
                                       @RequestParam(defaultValue = "100") int limit) {
        int max = Math.min(limit, 500);
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT id::text, success, failure_reason, ip::text, user_agent, created_at
              FROM auth_login_events
             WHERE user_id = ?::uuid
             ORDER BY created_at DESC LIMIT ?
            """, userId, max);
        return Map.of("data", rows, "total", rows.size());
    }

    @GetMapping("/failed")
    public Map<String, Object> failed(@RequestParam(defaultValue = "24") int sinceHours) {
        int max = Math.min(sinceHours, 720);
        List<Map<String, Object>> stats = jdbc.queryForList("""
            SELECT username, count(*) AS failed_count,
                   max(created_at) AS last_attempt,
                   array_agg(DISTINCT ip::text) AS ips
              FROM auth_login_events
             WHERE success = false
               AND created_at >= now() - (? || ' hours')::interval
             GROUP BY username
             ORDER BY failed_count DESC LIMIT 100
            """, String.valueOf(max));
        return Map.of("data", stats, "total", stats.size(),
            "since_hours", max);
    }
}

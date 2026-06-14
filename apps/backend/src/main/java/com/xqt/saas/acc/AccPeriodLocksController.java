package com.xqt.saas.acc;

import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 期间锁 — 月结后锁定期间不允许追溯改单。
 *
 *   POST /api/acc/period-locks  body: { period, type, reason }
 *   DELETE /api/acc/period-locks/{period}     解锁
 *   GET /api/acc/period-locks                 当前锁定列表
 *   GET /api/acc/period-locks/check?date=YYYY-MM-DD  查询日期是否被锁
 */
@RestController
@RequestMapping("/api/acc/period-locks")
public class AccPeriodLocksController {
    private final JdbcTemplate jdbc;

    public AccPeriodLocksController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public Map<String, Object> list() {
        List<Map<String, Object>> data = jdbc.queryForList("""
            SELECT id::text, period, period_type, locked_at, locked_by::text, reason
              FROM acc_period_locks ORDER BY period DESC
            """);
        return Map.of("data", data, "total", data.size());
    }

    @PostMapping
    public Map<String, Object> lock(@RequestBody Map<String, Object> body) {
        String period = (String) body.get("period");
        String type = body.getOrDefault("type", "MONTH").toString();
        String reason = (String) body.get("reason");
        if (period == null) throw ApiException.badRequest("period 必填，格式 YYYY-MM 或 YYYY");
        if ("MONTH".equals(type) && !period.matches("\\d{4}-\\d{2}")) {
            throw ApiException.badRequest("MONTH 类型 period 应为 YYYY-MM");
        }
        if ("YEAR".equals(type) && !period.matches("\\d{4}")) {
            throw ApiException.badRequest("YEAR 类型 period 应为 YYYY");
        }
        try {
            String id = jdbc.queryForObject("""
                INSERT INTO acc_period_locks (period, period_type, reason)
                VALUES (?, ?, ?)
                RETURNING id::text
                """, String.class, period, type, reason);
            return Map.of("id", id, "period", period, "locked", true);
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            throw ApiException.badRequest("该期间已锁定: " + period);
        }
    }

    @DeleteMapping("/{period}")
    public Map<String, Object> unlock(@PathVariable String period) {
        int n = jdbc.update("DELETE FROM acc_period_locks WHERE period = ?", period);
        if (n == 0) throw ApiException.notFound("该期间未锁定: " + period);
        return Map.of("period", period, "unlocked", true);
    }

    @GetMapping("/check")
    public Map<String, Object> check(@org.springframework.web.bind.annotation.RequestParam String date) {
        Boolean locked = jdbc.queryForObject(
            "SELECT acc_is_period_locked(?::date)", Boolean.class, date);
        return Map.of("date", date, "locked", locked != null && locked);
    }
}

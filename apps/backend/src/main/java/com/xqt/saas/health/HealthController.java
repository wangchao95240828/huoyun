package com.xqt.saas.health;

import java.time.OffsetDateTime;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    private final JdbcTemplate jdbc;

    public HealthController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        Integer database = jdbc.queryForObject("select 1", Integer.class);
        return Map.of(
            "ok", true,
            "service", "xqt-backend",
            "time", OffsetDateTime.now().toString(),
            "database", database != null && database == 1 ? "ok" : "unknown"
        );
    }
}

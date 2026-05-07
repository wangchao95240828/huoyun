package com.xqt.saas.health;

import java.time.OffsetDateTime;

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
    public HealthResponse health() {
        Integer database = jdbc.queryForObject("select 1", Integer.class);
        return new HealthResponse(
            true,
            "xqt-backend",
            OffsetDateTime.now().toString(),
            database != null && database == 1 ? "ok" : "unknown"
        );
    }
}

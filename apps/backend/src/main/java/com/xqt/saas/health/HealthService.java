package com.xqt.saas.health;

import java.time.OffsetDateTime;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class HealthService {
    private final JdbcTemplate jdbc;

    public HealthService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

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

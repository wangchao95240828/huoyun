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
 * /api/acc/zones — 价格分区聚合视图，从 rate_card_lines.zone_code 去重聚合。
 * 只读，无 audit 流。
 */
@RestController
@RequestMapping("/api/acc/zones")
public class AccZonesController {

    private final JdbcTemplate jdbc;
    private final JsonSupport json;

    public AccZonesController(JdbcTemplate jdbc, JsonSupport json) {
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
                SELECT count(*) FROM (
                  SELECT DISTINCT zone_code FROM rate_card_lines WHERE zone_code IS NOT NULL
                ) sub WHERE ?::text IS NULL OR zone_code ILIKE ?
                """, Long.class, search, search)) instanceof Number n ? n.longValue() : 0;
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT zone_code, count(*)::int AS line_count
                FROM rate_card_lines
                WHERE zone_code IS NOT NULL
                  AND (?::text IS NULL OR zone_code ILIKE ?)
                GROUP BY zone_code
                ORDER BY zone_code
                LIMIT ? OFFSET ?
                """, search, search, limit, offset);
            return AccPaging.result(rows.stream().map(this::project).toList(), total);
        } catch (DataAccessException ex) {
            return AccPaging.result(List.of(), 0);
        }
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("zone_code"));
        out.put("zoneCode", row.get("zone_code"));
        out.put("lineCount", row.get("line_count"));
        out.put("auditStatus", "UNAUDITED");
        out.put("auditedAt", null);
        out.put("auditName", "");
        return out;
    }
}

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
 * /api/acc/products — 价格表聚合视图，join rate_cards + service_channel_links + services。
 * 只读，无 audit 流（价格表由 rate_cards 本身管理）。
 */
@RestController
@RequestMapping("/api/acc/products")
public class AccProductsController {

    private final JdbcTemplate jdbc;
    private final JsonSupport json;

    public AccProductsController(JdbcTemplate jdbc, JsonSupport json) {
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
                SELECT count(*) FROM rate_cards rc
                LEFT JOIN channels ch ON ch.id = rc.channel_id
                WHERE ?::text IS NULL OR (rc.name ILIKE ? OR ch.name ILIKE ?)
                """, Long.class, search, search, search)) instanceof Number n ? n.longValue() : 0;
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT rc.id::text AS id, rc.code, rc.name, rc.channel_id::text AS channel_id,
                       ch.name AS channel_name, rc.is_open,
                       string_agg(DISTINCT s.name, ', ') AS service_names
                FROM rate_cards rc
                LEFT JOIN channels ch ON ch.id = rc.channel_id
                LEFT JOIN service_channel_links scl ON scl.channel_id = rc.channel_id
                LEFT JOIN services s ON s.id = scl.service_id
                WHERE ?::text IS NULL OR (rc.name ILIKE ? OR ch.name ILIKE ?)
                GROUP BY rc.id, rc.code, rc.name, rc.channel_id, ch.name, rc.is_open
                ORDER BY rc.name
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
        out.put("code", row.get("code"));
        out.put("name", row.get("name"));
        out.put("channelId", row.get("channel_id"));
        out.put("channelName", row.get("channel_name"));
        out.put("supplierName", "");
        out.put("serviceNames", row.get("service_names"));
        out.put("isOpen", row.get("is_open"));
        out.put("auditStatus", "UNAUDITED");
        out.put("auditedAt", null);
        out.put("auditName", "");
        return out;
    }
}

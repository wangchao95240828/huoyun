package com.xqt.saas.acc;

import java.math.BigDecimal;
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
 * /api/acc/profits — 利润查询。前端列：no / customerName / productName / country
 * / chargeWeight / channelWeight / revenue / cost / profit / theDate
 *
 * 数据来源：按 shipment 聚合 charges 表 AR / AP 差值。视图聚合，无 CRUD。
 * 另外 `/summary?dateFrom=&dateTo=&groupBy=` 给前端 "利润汇总" 弹窗用。
 */
@RestController
@RequestMapping("/api/acc/profits")
public class AccProfitsController {
    private final JdbcTemplate jdbc;
    private final JsonSupport json;

    public AccProfitsController(JdbcTemplate jdbc, JsonSupport json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @GetMapping
    public Map<String, Object> list(
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer pageSize,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String dateFrom,
        @RequestParam(required = false) String dateTo
    ) {
        try {
            int limit = AccPaging.pageSize(pageSize);
            int offset = AccPaging.offset(page, pageSize);
            String search = keyword == null || keyword.isBlank() ? null : "%" + keyword + "%";

            // 每个 shipment 一行：AR/AP sum + 客户/渠道/国家。计费重/渠道重都来自 cartons。
            Long total = jdbc.queryForObject("""
                SELECT count(DISTINCT s.id) FROM shipments s
                WHERE (?::text IS NULL OR s.shipment_no ILIKE ? OR s.customer_ref ILIKE ?)
                  AND (?::date IS NULL OR s.created_at >= ?::date)
                  AND (?::date IS NULL OR s.created_at < (?::date + 1))
                  AND EXISTS (SELECT 1 FROM charges ch WHERE ch.shipment_id = s.id)
                """, Long.class, search, search, search, dateFrom, dateFrom, dateTo, dateTo);

            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT
                  s.id::text       AS id,
                  s.shipment_no,
                  s.customer_ref,
                  s.destination_country,
                  s.created_at,
                  cu.name           AS customer_name,
                  cn.name           AS channel_name,
                  coalesce((
                    SELECT sum(c.chargeable_weight_kg) FROM cartons c WHERE c.shipment_id = s.id
                  ), 0) AS charge_weight,
                  coalesce((
                    SELECT sum(c.actual_weight_kg) FROM cartons c WHERE c.shipment_id = s.id
                  ), 0) AS channel_weight,
                  coalesce((
                    SELECT sum(ch.amount) FROM charges ch
                    WHERE ch.shipment_id = s.id AND ch.side = 'AR'
                  ), 0) AS revenue,
                  coalesce((
                    SELECT sum(ch.amount) FROM charges ch
                    WHERE ch.shipment_id = s.id AND ch.side = 'AP'
                  ), 0) AS cost
                FROM shipments s
                LEFT JOIN customers cu ON cu.id = s.customer_id
                LEFT JOIN channels cn  ON cn.id = s.channel_id
                WHERE (?::text IS NULL OR s.shipment_no ILIKE ? OR s.customer_ref ILIKE ?)
                  AND (?::date IS NULL OR s.created_at >= ?::date)
                  AND (?::date IS NULL OR s.created_at < (?::date + 1))
                  AND EXISTS (SELECT 1 FROM charges ch WHERE ch.shipment_id = s.id)
                ORDER BY s.created_at DESC
                LIMIT ? OFFSET ?
                """, search, search, search, dateFrom, dateFrom, dateTo, dateTo, limit, offset);
            return AccPaging.result(rows.stream().map(this::project).toList(),
                total == null ? 0 : total);
        } catch (DataAccessException ex) {
            return AccPaging.result(List.of(), 0);
        }
    }

    /**
     * /summary?dateFrom=&dateTo=&groupBy={customer|channel|country|day}
     * 前端 "利润汇总" 弹窗使用，返回 { groups: [{ key, label, revenue, cost, profit, count }] }
     */
    @GetMapping("/summary")
    public Map<String, Object> summary(
        @RequestParam(required = false) String dateFrom,
        @RequestParam(required = false) String dateTo,
        @RequestParam(defaultValue = "customer") String groupBy
    ) {
        String groupCol = switch (groupBy) {
            case "channel" -> "cn.name";
            case "country" -> "s.destination_country";
            case "day" -> "to_char(s.created_at, 'YYYY-MM-DD')";
            default -> "cu.name";
        };
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT
                  """ + groupCol + """
                                  AS group_label,
                  count(DISTINCT s.id) AS shipment_count,
                  coalesce(sum(CASE WHEN ch.side='AR' THEN ch.amount END), 0) AS revenue,
                  coalesce(sum(CASE WHEN ch.side='AP' THEN ch.amount END), 0) AS cost
                FROM shipments s
                LEFT JOIN charges ch ON ch.shipment_id = s.id
                LEFT JOIN customers cu ON cu.id = s.customer_id
                LEFT JOIN channels cn  ON cn.id = s.channel_id
                WHERE (?::date IS NULL OR s.created_at >= ?::date)
                  AND (?::date IS NULL OR s.created_at < (?::date + 1))
                GROUP BY """ + groupCol + """

                ORDER BY revenue DESC NULLS LAST
                LIMIT 200
                """, dateFrom, dateFrom, dateTo, dateTo);
            List<Map<String, Object>> groups = rows.stream().map(r -> {
                Map<String, Object> out = new LinkedHashMap<>();
                BigDecimal rev = (BigDecimal) r.get("revenue");
                BigDecimal cost = (BigDecimal) r.get("cost");
                out.put("label", r.get("group_label"));
                out.put("count", r.get("shipment_count"));
                out.put("revenue", rev);
                out.put("cost", cost);
                out.put("profit", rev.subtract(cost));
                return out;
            }).toList();
            return Map.of("groupBy", groupBy, "groups", groups);
        } catch (DataAccessException ex) {
            return Map.of("groupBy", groupBy, "groups", List.of());
        }
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        BigDecimal revenue = (BigDecimal) row.get("revenue");
        BigDecimal cost = (BigDecimal) row.get("cost");
        out.put("id", row.get("id"));
        out.put("no", row.get("customer_ref") != null
            ? row.get("customer_ref") : row.get("shipment_no"));
        out.put("customerName", row.get("customer_name"));
        out.put("productName", row.get("channel_name"));
        out.put("country", row.get("destination_country"));
        out.put("chargeWeight", row.get("charge_weight"));
        out.put("channelWeight", row.get("channel_weight"));
        out.put("revenue", revenue);
        out.put("cost", cost);
        out.put("profit", revenue.subtract(cost));
        out.put("theDate", json.value(row.get("created_at")));
        return out;
    }
}

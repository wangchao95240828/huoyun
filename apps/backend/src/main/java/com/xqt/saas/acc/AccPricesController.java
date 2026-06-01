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
 * ACC 基础信息 → 运费管理 的 3 个价格视图：
 *   /api/acc/sales-prices     → finance_price_maintenance.price_type=2 (销售员底价)
 *   /api/acc/customer-prices  → finance_price_maintenance.price_type=2 with user_name (按客户)
 *   /api/acc/published-prices → finance_price_maintenance.price_type=1 (公布价)
 *
 * 复用同一张表，只是 price_type 过滤不同，避免再造数据模型。
 */
@RestController
public class AccPricesController {
    private final JdbcTemplate jdbc;
    private final JsonSupport json;

    public AccPricesController(JdbcTemplate jdbc, JsonSupport json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @GetMapping("/api/acc/sales-prices")
    public Map<String, Object> salesPrices(
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer pageSize,
        @RequestParam(required = false) String keyword
    ) {
        return queryByType(2, false, page, pageSize, keyword);
    }

    @GetMapping("/api/acc/customer-prices")
    public Map<String, Object> customerPrices(
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer pageSize,
        @RequestParam(required = false) String keyword
    ) {
        return queryByType(2, true, page, pageSize, keyword);
    }

    @GetMapping("/api/acc/published-prices")
    public Map<String, Object> publishedPrices(
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer pageSize,
        @RequestParam(required = false) String keyword
    ) {
        return queryByType(1, false, page, pageSize, keyword);
    }

    private Map<String, Object> queryByType(int priceType, boolean requireUserName,
                                            Integer page, Integer pageSize, String keyword) {
        try {
            int limit = AccPaging.pageSize(pageSize);
            int offset = AccPaging.offset(page, pageSize);
            String search = keyword == null || keyword.isBlank() ? null : "%" + keyword + "%";
            String userNameClause = requireUserName ? " AND user_name IS NOT NULL " : "";
            Long total = jdbc.queryForObject(
                "SELECT count(*) FROM finance_price_maintenance"
                + " WHERE price_type = ? "
                + userNameClause
                + "   AND (?::text IS NULL OR name ILIKE ? OR service ILIKE ?)",
                Long.class, priceType, search, search, search);
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, name, service, receive_area, priority, user_level, user_name,"
                + "       min_weight, max_weight, zip_prefix, price_type, status,"
                + "       create_by, create_time, update_by, update_time"
                + " FROM finance_price_maintenance"
                + " WHERE price_type = ? "
                + userNameClause
                + "   AND (?::text IS NULL OR name ILIKE ? OR service ILIKE ?)"
                + " ORDER BY create_time DESC"
                + " LIMIT ? OFFSET ?",
                priceType, search, search, search, limit, offset);
            return AccPaging.result(rows.stream().map(this::project).toList(),
                total == null ? 0 : total);
        } catch (DataAccessException ex) {
            return AccPaging.result(List.of(), 0);
        }
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", String.valueOf(row.get("id")));
        out.put("name", row.get("name"));
        out.put("service", row.get("service"));
        out.put("receiveArea", row.get("receive_area"));
        out.put("priority", row.get("priority"));
        out.put("userLevel", row.get("user_level"));
        out.put("userName", row.get("user_name"));
        out.put("minWeight", row.get("min_weight"));
        out.put("maxWeight", row.get("max_weight"));
        out.put("zipPrefix", row.get("zip_prefix"));
        out.put("priceType", row.get("price_type"));
        out.put("status", row.get("status"));
        out.put("createdBy", row.get("create_by"));
        out.put("createdAt", json.value(row.get("create_time")));
        return out;
    }
}

package com.xqt.saas.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.JsonSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 前端 App.vue 老的 /api/finance/dashboard + /api/finance/branches 直接对接到这里。
 * 不走 ApiResponse 包装，因为前端是按裸 JSON 解析的 (fetchOptionalJson)。
 */
@RestController
@RequestMapping("/api/finance")
public class DashboardController {
    private final JdbcTemplate jdbc;
    private final JsonSupport json;

    public DashboardController(JdbcTemplate jdbc, JsonSupport json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        long orderCount = countOrFallback("SELECT count(*) FROM orders");
        long shipmentCount = countOrFallback("SELECT count(*) FROM shipments");
        BigDecimal receivable = sumOrZero("""
            SELECT coalesce(sum(amount), 0) FROM customer_invoices
            """);
        BigDecimal payable = sumOrZero("""
            SELECT coalesce(sum(amount), 0) FROM payments
            """);
        BigDecimal profit = receivable.subtract(payable);

        LocalDate today = LocalDate.now();
        return Map.ofEntries(
            Map.entry("acc", Map.of(
                "label", "ACC 制单",
                "revenue", BigDecimal.ZERO, "cost", BigDecimal.ZERO,
                "profit", BigDecimal.ZERO, "orderCount", 0,
                "byBranch", List.of()
            )),
            Map.entry("xqt", Map.of(
                "label", "新智慧卖货",
                "revenue", BigDecimal.ZERO, "shipmentCount", 0,
                "invoiceCount", 0, "paid", BigDecimal.ZERO, "unpaid", BigDecimal.ZERO
            )),
            Map.entry("local", Map.of(
                "label", "新平台总览",
                "orders", orderCount,
                "shipments", shipmentCount,
                "receivable", receivable,
                "payable", payable,
                "profit", profit
            )),
            Map.entry("combined", Map.of(
                "totalRevenue", receivable,
                "totalCost", payable,
                "totalProfit", profit
            )),
            Map.entry("flows", List.of()),
            Map.entry("tracking", Map.of(
                "summary", Map.of(
                    "activeShipments", 0,
                    "exceptionCount", 0,
                    "deliveredToday", 0,
                    "trackedShipments", 0,
                    "destinationCountries", 0
                ),
                "routes", List.of()
            )),
            Map.entry("period", Map.of(
                "from", today.withDayOfMonth(1).toString(),
                "to", today.toString()
            ))
        );
    }

    /**
     * 给老 App.vue 用的 health 形状（upstreams.postgres/acc/xqt）。
     * /api/health 走 ApiResponse 包装，shape 不一样，前端 dashboard 不直接消费。
     */
    @GetMapping("/dashboard/health")
    public Map<String, Object> dashboardHealth() {
        boolean pgOk;
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            pgOk = true;
        } catch (org.springframework.dao.DataAccessException ex) {
            pgOk = false;
        }
        return Map.of(
            "ok", pgOk,
            "upstreams", Map.of(
                "postgres", Map.of("available", true, "connected", pgOk),
                // ACC / XQT 旧上游目前没有真接入；先标 available=false 不影响 UI 显示
                "acc", Map.of("available", false, "connected", false),
                "xqt", Map.of("available", false, "connected", false)
            )
        );
    }

    @GetMapping("/branches")
    public Map<String, Object> branches() {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id::text AS id, code, name, org_type, is_active
                FROM organizations
                ORDER BY org_type, code
                """);
            return Map.of("data", json.rows(rows));
        } catch (org.springframework.dao.DataAccessException ex) {
            return Map.of("data", List.of());
        }
    }

    private long countOrFallback(String sql) {
        try {
            Long v = jdbc.queryForObject(sql, Long.class);
            return v == null ? 0 : v;
        } catch (org.springframework.dao.DataAccessException ex) {
            return 0;
        }
    }

    private BigDecimal sumOrZero(String sql) {
        try {
            BigDecimal v = jdbc.queryForObject(sql, BigDecimal.class);
            return v == null ? BigDecimal.ZERO : v;
        } catch (org.springframework.dao.DataAccessException ex) {
            return BigDecimal.ZERO;
        }
    }
}

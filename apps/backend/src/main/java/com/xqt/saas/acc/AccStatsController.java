package com.xqt.saas.acc;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * /api/acc/stats — ACC 顶栏统计。
 *
 * 走 AccTenantTxFilter 的事务，所有查询通过 app.current_tenant_id + RLS 自动过滤租户，
 * 因此 SQL 里不需要显式 WHERE tenant_id。
 *
 * 失败时返回 0，避免前端首屏报错；具体单项失败不影响其它项。
 */
@RestController
@RequestMapping("/api/acc/stats")
public class AccStatsController {
    private final JdbcTemplate jdbc;

    public AccStatsController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public Map<String, Object> stats() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("orderCount", scalarLong("SELECT count(*) FROM orders"));
        out.put("customerCount", scalarLong("SELECT count(*) FROM customers"));
        out.put("supplierCount", scalarLong("SELECT count(*) FROM partners"));
        out.put("channelCount", scalarLong("SELECT count(*) FROM channels WHERE active = true"));
        out.put("shipmentCount", scalarLong("SELECT count(*) FROM shipments"));
        out.put("todayOrderCount", scalarLong(
            "SELECT count(*) FROM orders WHERE created_at >= current_date"));
        out.put("pendingAuditOrderCount", scalarLong(
            "SELECT count(*) FROM orders WHERE audit_status = 'PENDING'"));
        out.put("unpaidInvoiceCount", scalarLong(
            "SELECT count(*) FROM customer_invoices WHERE writeoff_status IN ('UNPAID', 'PARTIAL')"));
        out.put("unpaidPartnerInvoiceCount", scalarLong(
            "SELECT count(*) FROM partner_invoices WHERE writeoff_status IN ('UNPAID', 'PARTIAL')"));

        BigDecimal revenue = scalarMoney(
            "SELECT coalesce(sum(amount), 0) FROM charges "
            + "WHERE side = 'AR' AND audit_status = 'AUDITED' AND settlement_status <> 'VOID'");
        BigDecimal cost = scalarMoney(
            "SELECT coalesce(sum(amount), 0) FROM charges "
            + "WHERE side = 'AP' AND audit_status = 'AUDITED' AND settlement_status <> 'VOID'");
        out.put("revenue", revenue);
        out.put("cost", cost);
        out.put("profit", revenue.subtract(cost));
        return out;
    }

    private long scalarLong(String sql) {
        try {
            Long v = jdbc.queryForObject(sql, Long.class);
            return v == null ? 0L : v;
        } catch (DataAccessException ex) {
            return 0L;
        }
    }

    private BigDecimal scalarMoney(String sql) {
        try {
            BigDecimal v = jdbc.queryForObject(sql, BigDecimal.class);
            return v == null ? BigDecimal.ZERO : v;
        } catch (DataAccessException ex) {
            return BigDecimal.ZERO;
        }
    }
}

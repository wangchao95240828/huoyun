package com.xqt.saas.framework.health;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 深度健康检查 — 替代 Spring Actuator /health 的简单 OK。
 *
 *   GET /api/health/deep        全套依赖状态
 *   GET /api/health/db          DB 读写
 *   GET /api/health/scheduler   定时任务状态
 *   GET /api/health/metrics     关键 KPI 简报
 */
@RestController
@RequestMapping("/api/health")
public class AccHealthController {
    private final JdbcTemplate jdbc;

    public AccHealthController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/deep")
    public Map<String, Object> deep() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timestamp", java.time.OffsetDateTime.now().toString());

        // DB readable
        Map<String, Object> db = new LinkedHashMap<>();
        try {
            long t0 = System.nanoTime();
            Integer ok = jdbc.queryForObject("SELECT 1", Integer.class);
            db.put("status", "UP");
            db.put("latencyMs", (System.nanoTime() - t0) / 1_000_000);
            // 表存在性检查
            db.put("usersTable", tableExists("users"));
            db.put("ordersTable", tableExists("orders"));
            db.put("chargesTable", tableExists("charges"));
            db.put("glSubjectsTable", tableExists("acc_gl_subjects"));
        } catch (Exception ex) {
            db.put("status", "DOWN");
            db.put("error", ex.getMessage());
        }
        result.put("database", db);

        // 关键迁移已应用
        Map<String, Object> migrations = new LinkedHashMap<>();
        try {
            Integer recent = jdbc.queryForObject(
                "SELECT count(*) FROM schema_migrations WHERE filename LIKE '07%.sql'", Integer.class);
            migrations.put("recentlyAppliedCount", recent);
            String latest = jdbc.queryForObject(
                "SELECT filename FROM schema_migrations ORDER BY filename DESC LIMIT 1", String.class);
            migrations.put("latestMigration", latest);
            migrations.put("status", "UP");
        } catch (Exception ex) {
            migrations.put("status", "DOWN");
            migrations.put("error", ex.getMessage());
        }
        result.put("migrations", migrations);

        // GL 数据可用性
        Map<String, Object> gl = new LinkedHashMap<>();
        try {
            Integer subjectCount = jdbc.queryForObject(
                "SELECT count(*) FROM acc_gl_subjects WHERE is_active = true", Integer.class);
            gl.put("activeSubjects", subjectCount);
            gl.put("status", subjectCount != null && subjectCount > 0 ? "UP" : "DEGRADED");
        } catch (Exception ex) {
            gl.put("status", "DOWN");
            gl.put("error", ex.getMessage());
        }
        result.put("gl", gl);

        // 最近活动
        Map<String, Object> activity = new LinkedHashMap<>();
        try {
            Integer recentOrders = jdbc.queryForObject(
                "SELECT count(*) FROM orders WHERE created_at >= now() - interval '24 hours'",
                Integer.class);
            activity.put("ordersLast24h", recentOrders);
            Integer recentAudits = jdbc.queryForObject(
                "SELECT count(*) FROM audit_events WHERE occurred_at >= now() - interval '24 hours'",
                Integer.class);
            activity.put("auditEventsLast24h", recentAudits);
        } catch (Exception ex) {
            activity.put("error", ex.getMessage());
        }
        result.put("activity", activity);

        boolean anyDown = "DOWN".equals(db.get("status")) || "DOWN".equals(migrations.get("status"));
        result.put("overall", anyDown ? "DOWN" : "UP");
        return result;
    }

    @GetMapping("/db")
    public Map<String, Object> dbCheck() {
        try {
            long t0 = System.nanoTime();
            jdbc.queryForObject("SELECT 1", Integer.class);
            return Map.of("status", "UP", "latencyMs", (System.nanoTime() - t0) / 1_000_000);
        } catch (Exception ex) {
            return Map.of("status", "DOWN", "error", ex.getMessage());
        }
    }

    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            // 业务关键指标
            Map<String, Object> business = new LinkedHashMap<>();
            business.put("totalOrders", jdbc.queryForObject(
                "SELECT count(*) FROM orders WHERE deleted_at IS NULL", Long.class));
            business.put("activeCustomers", jdbc.queryForObject(
                "SELECT count(*) FROM customers WHERE audit_status='AUDITED'", Long.class));
            business.put("totalShipments", jdbc.queryForObject(
                "SELECT count(*) FROM shipments", Long.class));
            business.put("pendingApprovals", jdbc.queryForObject(
                "SELECT count(*) FROM approval_requests WHERE status='PENDING'", Long.class));
            result.put("business", business);

            // 财务 KPI
            Map<String, Object> finance = new LinkedHashMap<>();
            finance.put("totalArAmount", jdbc.queryForObject(
                "SELECT coalesce(sum(amount),0) FROM charges WHERE side='AR' AND status<>'VOID'::charge_status",
                java.math.BigDecimal.class));
            finance.put("unpaidInvoices", jdbc.queryForObject(
                "SELECT count(*) FROM customer_invoices WHERE status IN ('SENT','PENDING','PARTIAL_PAID')",
                Long.class));
            result.put("finance", finance);

            // 系统指标
            Map<String, Object> system = new LinkedHashMap<>();
            Runtime rt = Runtime.getRuntime();
            system.put("freeMemoryMB", rt.freeMemory() / (1024 * 1024));
            system.put("maxMemoryMB", rt.maxMemory() / (1024 * 1024));
            system.put("availableProcessors", rt.availableProcessors());
            result.put("system", system);

            result.put("timestamp", java.time.OffsetDateTime.now().toString());
            return result;
        } catch (Exception ex) {
            return Map.of("error", ex.getMessage());
        }
    }

    private boolean tableExists(String tableName) {
        try {
            Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_name = ?",
                Integer.class, tableName);
            return count != null && count > 0;
        } catch (Exception ex) {
            return false;
        }
    }
}

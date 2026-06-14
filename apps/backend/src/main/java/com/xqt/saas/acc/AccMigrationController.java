package com.xqt.saas.acc;

import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ACC MySQL → xqt-saas PostgreSQL 数据迁移工具。
 *
 *   POST /api/acc/migration/test-connection   body: {host, port, user, password, database}
 *   POST /api/acc/migration/import-customers  body: {...mysql config, batchSize?}
 *   POST /api/acc/migration/import-orders     同上
 *   POST /api/acc/migration/import-charges    同上
 *
 * 仅 ADMIN 可访问。导入幂等：根据 ACC No 字段匹配，不重复创建。
 */
@RestController
@RequestMapping("/api/acc/migration")
@PreAuthorize("hasRole('ADMIN')")
public class AccMigrationController {
    private static final Logger LOGGER = LoggerFactory.getLogger(AccMigrationController.class);
    private final JdbcTemplate jdbc;

    public AccMigrationController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostMapping("/test-connection")
    public Map<String, Object> testConnection(@RequestBody Map<String, Object> body) {
        String url = buildJdbcUrl(body);
        String user = strOf(body.get("user"));
        String pass = strOf(body.get("password"));
        try (var conn = DriverManager.getConnection(url, user, pass)) {
            try (var stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT VERSION() AS v")) {
                rs.next();
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("ok", true);
                result.put("mysqlVersion", rs.getString("v"));
                // 检查关键 ACC 表
                Map<String, Long> tables = new LinkedHashMap<>();
                for (String t : new String[]{"yt_Customer", "yt_Express", "yt_Express_Charge", "yt_User"}) {
                    try (var s = conn.createStatement();
                         ResultSet trs = s.executeQuery("SELECT COUNT(*) FROM " + t)) {
                        trs.next();
                        tables.put(t, trs.getLong(1));
                    } catch (Exception ex) {
                        tables.put(t, -1L);
                    }
                }
                result.put("tables", tables);
                return result;
            }
        } catch (Exception ex) {
            throw ApiException.badRequest("连接 MySQL 失败: " + ex.getMessage());
        }
    }

    @PostMapping("/import-customers")
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importCustomers(@RequestBody Map<String, Object> body) {
        String url = buildJdbcUrl(body);
        String user = strOf(body.get("user"));
        String pass = strOf(body.get("password"));
        int batchSize = body.get("batchSize") instanceof Number n ? n.intValue() : 100;
        int imported = 0, skipped = 0;
        java.util.List<String> errors = new java.util.ArrayList<>();
        try (var conn = DriverManager.getConnection(url, user, pass)) {
            try (var stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                     "SELECT Id, `No`, `Name`, `Contacts`, `Mobile`, `Phone`, `Email`, `Address`, `Currency`"
                     + " FROM yt_Customer LIMIT " + batchSize)) {
                jdbc.execute("SELECT set_config('app.current_tenant_id', '2bda8c16-7b19-4ce6-ab71-9584f5a140ed', true)");
                while (rs.next()) {
                    String code = rs.getString("No");
                    String name = rs.getString("Name");
                    if (code == null || name == null) { skipped++; continue; }
                    try {
                        Integer dup = jdbc.queryForObject(
                            "SELECT count(*) FROM customers WHERE code = ?", Integer.class, code);
                        if (dup != null && dup > 0) { skipped++; continue; }
                        jdbc.update("""
                            INSERT INTO customers (tenant_id, code, name, contacts, mobile, phone, email, address, default_currency)
                            VALUES (current_setting('app.current_tenant_id')::uuid, ?, ?, ?, ?, ?, ?, ?, ?)
                            """, code, name, rs.getString("Contacts"), rs.getString("Mobile"),
                                 rs.getString("Phone"), rs.getString("Email"), rs.getString("Address"),
                                 rs.getString("Currency") == null ? "CNY" : rs.getString("Currency"));
                        imported++;
                    } catch (Exception ex) {
                        errors.add(code + ": " + ex.getMessage());
                        skipped++;
                    }
                }
            }
        } catch (Exception ex) {
            throw ApiException.badRequest("MySQL 读取失败: " + ex.getMessage());
        }
        return Map.of("imported", imported, "skipped", skipped, "errors", errors);
    }

    @PostMapping("/import-orders")
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importOrders(@RequestBody Map<String, Object> body) {
        String url = buildJdbcUrl(body);
        String user = strOf(body.get("user"));
        String pass = strOf(body.get("password"));
        int batchSize = body.get("batchSize") instanceof Number n ? n.intValue() : 50;
        int imported = 0, skipped = 0;
        java.util.List<String> errors = new java.util.ArrayList<>();
        try (var conn = DriverManager.getConnection(url, user, pass)) {
            try (var stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                     "SELECT e.Id, e.`No`, e.TrackNo, e.Customer AS customer_acc_id,"
                     + " e.Materials, e.DeclaredValue, e.Postcode, e.AddTime,"
                     + " c.No AS customer_no FROM yt_Express e"
                     + " LEFT JOIN yt_Customer c ON c.Id = e.Customer"
                     + " ORDER BY e.AddTime DESC LIMIT " + batchSize)) {
                jdbc.execute("SELECT set_config('app.current_tenant_id', '2bda8c16-7b19-4ce6-ab71-9584f5a140ed', true)");
                while (rs.next()) {
                    String orderNo = rs.getString("No");
                    String customerNo = rs.getString("customer_no");
                    if (orderNo == null || customerNo == null) { skipped++; continue; }
                    try {
                        String customerId = jdbc.queryForObject(
                            "SELECT id::text FROM customers WHERE code = ?", String.class, customerNo);
                        if (customerId == null) {
                            errors.add(orderNo + ": customer " + customerNo + " 不存在");
                            skipped++; continue;
                        }
                        Integer dup = jdbc.queryForObject(
                            "SELECT count(*) FROM orders WHERE order_no = ?", Integer.class, orderNo);
                        if (dup != null && dup > 0) { skipped++; continue; }
                        jdbc.update("""
                            INSERT INTO orders (tenant_id, order_no, customer_id, status, source,
                                                customer_ref, materials_en, postcode, metadata)
                            VALUES (current_setting('app.current_tenant_id')::uuid, ?, ?::uuid, 'COMPLETED', 'ACC',
                                    ?, ?, ?, jsonb_build_object('migrated_from_acc', true,
                                                                  'acc_id', ?::text,
                                                                  'declared_value', ?::numeric))
                            """, orderNo, customerId, rs.getString("TrackNo"),
                                 rs.getString("Materials"), rs.getString("Postcode"),
                                 rs.getString("customer_acc_id"), rs.getDouble("DeclaredValue"));
                        imported++;
                    } catch (Exception ex) {
                        errors.add(orderNo + ": " + ex.getMessage());
                        skipped++;
                    }
                }
            }
        } catch (Exception ex) {
            throw ApiException.badRequest("MySQL 读取失败: " + ex.getMessage());
        }
        return Map.of("imported", imported, "skipped", skipped, "errors", errors);
    }

    private String buildJdbcUrl(Map<String, Object> body) {
        String host = strOf(body.get("host"));
        int port = body.get("port") instanceof Number n ? n.intValue() : 3306;
        String database = strOf(body.get("database"));
        if (host == null || database == null) {
            throw ApiException.badRequest("host / database 必填");
        }
        return "jdbc:mysql://" + host + ":" + port + "/" + database
            + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    }

    private String strOf(Object o) {
        return o == null ? null : o.toString();
    }
}

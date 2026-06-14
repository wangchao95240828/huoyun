package com.xqt.saas.framework.scope;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.xqt.saas.auth.AuthPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * 数据范围过滤 — 对齐 ACC AdminClass.php L3184/L3257：
 *
 * 非 ADMIN 用户根据登录账号的绑定关系自动添加 SQL 过滤条件：
 *  - bound_customer_id ≠ null → 只能看自己客户的数据
 *  - bound_employee_id ≠ null → 只能看自己作为业务员对接的客户/订单
 *  - bound_supplier_id ≠ null → 只能看自己作为供应商关联的成本/付款
 *
 * 用法：
 *   String filter = dataScope.customerFilter("c.id");  // → "AND c.id IN (...)" 或 ""
 *   sql += filter;
 */
@Service
public class DataScope {
    private final JdbcTemplate jdbc;

    /** 5 秒缓存避免每次查询命中 DB。生产可改 Caffeine。 */
    private static class CacheEntry {
        UserBindings bindings;
        long expiresAt;
    }

    public record UserBindings(
        boolean isAdmin,
        String boundCustomerId,
        String boundEmployeeId,
        String boundSupplierId,
        String branchId
    ) {}

    private final java.util.concurrent.ConcurrentHashMap<String, CacheEntry> cache = new java.util.concurrent.ConcurrentHashMap<>();

    @Autowired
    public DataScope(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 取当前登录用户的绑定关系。admin 短路返回。 */
    public UserBindings current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal principal)) {
            return new UserBindings(false, null, null, null, null);
        }
        // ADMIN 跳过过滤
        if (principal.roles().contains("ADMIN")) {
            return new UserBindings(true, null, null, null, principal.branchId());
        }
        // 缓存查询
        CacheEntry entry = cache.get(principal.userId());
        long now = System.currentTimeMillis();
        if (entry != null && entry.expiresAt > now) {
            return entry.bindings;
        }
        UserBindings b;
        try {
            Map<String, Object> row = jdbc.queryForMap("""
                SELECT bound_customer_id::text AS bc,
                       bound_employee_id::text AS be,
                       bound_supplier_id::text AS bs,
                       branch_id::text AS br
                  FROM users WHERE id = ?::uuid
                """, principal.userId());
            b = new UserBindings(false,
                (String) row.get("bc"),
                (String) row.get("be"),
                (String) row.get("bs"),
                (String) row.get("br"));
        } catch (Exception ex) {
            b = new UserBindings(false, null, null, null, principal.branchId());
        }
        CacheEntry ne = new CacheEntry();
        ne.bindings = b;
        ne.expiresAt = now + TimeUnit.SECONDS.toMillis(5);
        cache.put(principal.userId(), ne);
        return b;
    }

    /**
     * 客户数据范围过滤 SQL 片段。fieldExpr 是要过滤的 customer_id 列（含表别名）。
     * 返回 "" 或 " AND <fieldExpr> = ..." / " AND <fieldExpr> IN (...)"
     */
    public String customerFilter(String fieldExpr) {
        UserBindings b = current();
        if (b.isAdmin) return "";
        // 客户portal 用户：只看自己
        if (b.boundCustomerId != null) {
            return " AND " + fieldExpr + " = '" + b.boundCustomerId + "'::uuid";
        }
        // 业务员：只看自己对接的客户（通过 customers.salesman_id）
        if (b.boundEmployeeId != null) {
            return " AND " + fieldExpr + " IN ("
                 + "SELECT id FROM customers WHERE salesman_id = '" + b.boundEmployeeId + "'::uuid)";
        }
        // 供应商账号：只看自己作为 partner 关联的客户（通过 charges/shipments）
        if (b.boundSupplierId != null) {
            return " AND 1=0"; // 供应商账号不应能看到客户列表
        }
        // 普通员工：按 branch 过滤
        if (b.branchId != null) {
            return " AND EXISTS (SELECT 1 FROM customers c WHERE c.id = " + fieldExpr
                 + " AND c.branch_id = '" + b.branchId + "'::uuid)";
        }
        return "";
    }

    /**
     * 订单数据范围过滤 SQL 片段。fieldExpr 是 orders.customer_id 列。
     */
    public String orderFilter(String customerIdExpr, String branchIdExpr) {
        UserBindings b = current();
        if (b.isAdmin) return "";
        if (b.boundCustomerId != null) {
            return " AND " + customerIdExpr + " = '" + b.boundCustomerId + "'::uuid";
        }
        if (b.boundEmployeeId != null) {
            return " AND " + customerIdExpr + " IN ("
                 + "SELECT id FROM customers WHERE salesman_id = '" + b.boundEmployeeId + "'::uuid)";
        }
        if (b.boundSupplierId != null) {
            return " AND 1=0";
        }
        if (b.branchId != null && branchIdExpr != null) {
            return " AND " + branchIdExpr + " = '" + b.branchId + "'::uuid";
        }
        return "";
    }

    /**
     * 供应商单据过滤（AP 侧）。
     */
    public String supplierFilter(String partnerIdExpr) {
        UserBindings b = current();
        if (b.isAdmin) return "";
        if (b.boundSupplierId != null) {
            return " AND " + partnerIdExpr + " = '" + b.boundSupplierId + "'::uuid";
        }
        // 非供应商账号查供应商单据 → 业务员/客户账号不应该看
        if (b.boundCustomerId != null) {
            return " AND 1=0";
        }
        return "";
    }

    /** 让外部测试用例可强制刷新缓存。 */
    public void invalidate(String userId) {
        cache.remove(userId);
    }
}

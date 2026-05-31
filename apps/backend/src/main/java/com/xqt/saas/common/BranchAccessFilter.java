package com.xqt.saas.common;

import java.util.List;

import com.xqt.saas.auth.AuthPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 应用层 RBAC 过滤（替代 migration 048 移除的 RLS policy）。
 *
 * 角色矩阵：
 *   ADMIN / FINANCE        → 无过滤，全 tenant 可见
 *   BRANCH_MANAGER         → WHERE branch_id = current_user.branch_id（如果 branch_id NULL，回退到无过滤，避免零行 trap）
 *   SALESMAN               → WHERE customer_id IN (SELECT id FROM customers WHERE salesman_user_id = current_user.id)
 *   其他角色 / 无角色      → 无过滤
 *
 * 用法：
 *   AccessClause c = branchAccess.forCurrent("o");   // o 是 orders 表别名
 *   String sql = "SELECT * FROM orders o WHERE 1=1 " + c.sql();
 *   jdbc.queryForList(sql, c.params().toArray());
 */
@Component
public class BranchAccessFilter {

    /**
     * 给"按 branch_id 隔离"的表（orders / shipments / charges）用。
     */
    public AccessClause forCurrent(String tableAlias) {
        AuthPrincipal p = current();
        if (p == null) return AccessClause.empty();

        List<String> roles = p.roles();
        if (roles.contains("ADMIN") || roles.contains("FINANCE_MANAGER")
            || roles.contains("FINANCE")) {
            return AccessClause.empty();
        }
        if (roles.contains("BRANCH_MANAGER")) {
            if (p.branchId() == null || p.branchId().isBlank()) {
                return AccessClause.empty(); // 容错回退
            }
            return new AccessClause(
                " AND " + tableAlias + ".branch_id = ?::uuid",
                List.of(p.branchId())
            );
        }
        if (roles.contains("SALESMAN")) {
            return new AccessClause(
                " AND " + tableAlias + ".customer_id IN ("
                + " SELECT id FROM customers WHERE salesman_user_id = ?::uuid)",
                List.of(p.userId())
            );
        }
        return AccessClause.empty();
    }

    /**
     * 给只有 customer_id（无 branch_id）的业务表用：通过 customer_id 关联 customers 表做过滤。
     *   - BRANCH_MANAGER → customer_id IN (SELECT id FROM customers WHERE branch_id = ?)
     *   - SALESMAN       → customer_id IN (SELECT id FROM customers WHERE salesman_user_id = ?)
     */
    public AccessClause forCurrentViaCustomer(String tableAlias) {
        AuthPrincipal p = current();
        if (p == null) return AccessClause.empty();
        List<String> roles = p.roles();
        if (roles.contains("ADMIN") || roles.contains("FINANCE_MANAGER")
            || roles.contains("FINANCE")) {
            return AccessClause.empty();
        }
        if (roles.contains("BRANCH_MANAGER")) {
            if (p.branchId() == null || p.branchId().isBlank()) {
                return AccessClause.empty();
            }
            return new AccessClause(
                " AND " + tableAlias + ".customer_id IN ("
                + " SELECT id FROM customers WHERE branch_id = ?::uuid)",
                List.of(p.branchId())
            );
        }
        if (roles.contains("SALESMAN")) {
            return new AccessClause(
                " AND " + tableAlias + ".customer_id IN ("
                + " SELECT id FROM customers WHERE salesman_user_id = ?::uuid)",
                List.of(p.userId())
            );
        }
        return AccessClause.empty();
    }

    /**
     * 给 customers 表本身用（区别于按 customer_id 关联的业务表）。
     */
    public AccessClause forCustomers(String tableAlias) {
        AuthPrincipal p = current();
        if (p == null) return AccessClause.empty();

        List<String> roles = p.roles();
        if (roles.contains("ADMIN") || roles.contains("FINANCE_MANAGER")
            || roles.contains("FINANCE")) {
            return AccessClause.empty();
        }
        if (roles.contains("BRANCH_MANAGER")) {
            if (p.branchId() == null || p.branchId().isBlank()) {
                return AccessClause.empty();
            }
            return new AccessClause(
                " AND " + tableAlias + ".branch_id = ?::uuid",
                List.of(p.branchId())
            );
        }
        if (roles.contains("SALESMAN")) {
            return new AccessClause(
                " AND " + tableAlias + ".salesman_user_id = ?::uuid",
                List.of(p.userId())
            );
        }
        return AccessClause.empty();
    }

    private AuthPrincipal current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal p)) return null;
        return p;
    }

    public record AccessClause(String sql, List<Object> params) {
        public static AccessClause empty() {
            return new AccessClause("", List.of());
        }
    }
}

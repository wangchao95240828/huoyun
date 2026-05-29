package com.xqt.saas.common;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.xqt.saas.auth.AuthPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 任务 S7：setTenant 同时设置 user_id + user_role + user_branch_id 三个 GUC，
 * 给 customers / shipments / orders 上的 RESTRICTIVE 策略提供过滤上下文。
 */
class RequestContextTest {

    private AuthPrincipal salesman(String userId, String tenantId) {
        return new AuthPrincipal(userId, tenantId, "T1", "alice", "Alice",
            List.of("SALESMAN"), List.of(), 0L, "jti-1", null);
    }

    private AuthPrincipal admin(String userId, String tenantId) {
        return new AuthPrincipal(userId, tenantId, "T1", "boss", "Boss",
            List.of("ADMIN"), List.of(), 0L, "jti-2", null);
    }

    private AuthPrincipal branchManager(String userId, String tenantId, String branchId) {
        return new AuthPrincipal(userId, tenantId, "T1", "ben", "Ben",
            List.of("BRANCH_MANAGER"), List.of(), 0L, "jti-3", branchId);
    }

    @Test
    void setTenantWritesTenantUserAndRoleSessionVars() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(eq("select set_config('app.current_tenant_id', ?, true)"),
            eq(String.class), eq("ten-1"))).thenReturn("ten-1");
        when(jdbc.queryForObject(eq("select set_config('app.user_id', ?, true)"),
            eq(String.class), eq("u-alice"))).thenReturn("u-alice");
        when(jdbc.queryForObject(eq("select set_config('app.user_role', ?, true)"),
            eq(String.class), eq("SALESMAN"))).thenReturn("SALESMAN");
        when(jdbc.queryForObject(eq("select set_config('app.user_branch_id', ?, true)"),
            eq(String.class), eq(""))).thenReturn("");

        new RequestContext(jdbc).setTenant(salesman("u-alice", "ten-1"));

        verify(jdbc, times(1)).queryForObject(
            eq("select set_config('app.current_tenant_id', ?, true)"), eq(String.class), eq("ten-1"));
        verify(jdbc, times(1)).queryForObject(
            eq("select set_config('app.user_id', ?, true)"), eq(String.class), eq("u-alice"));
        verify(jdbc, times(1)).queryForObject(
            eq("select set_config('app.user_role', ?, true)"), eq(String.class), eq("SALESMAN"));
        verify(jdbc, times(1)).queryForObject(
            eq("select set_config('app.user_branch_id', ?, true)"), eq(String.class), eq(""));
    }

    @Test
    void adminRoleIsSetForAdminPrincipal() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(eq("select set_config('app.user_role', ?, true)"),
            eq(String.class), eq("ADMIN"))).thenReturn("ADMIN");
        // 其他 set_config 默认返回 null（Mockito 默认值）→ 仍能执行

        new RequestContext(jdbc).setTenant(admin("u-boss", "ten-1"));

        verify(jdbc, times(1)).queryForObject(
            eq("select set_config('app.user_role', ?, true)"), eq(String.class), eq("ADMIN"));
    }

    @Test
    void emptyRolesUsesEmptyStringRoleForBackwardCompat() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AuthPrincipal noRole = new AuthPrincipal("u-x", "ten-1", "T1", "x", "X",
            List.of(), List.of(), 0L, "jti-x", null);

        new RequestContext(jdbc).setTenant(noRole);

        // role 设空字符串 → policy IS NULL/'' 分支 → 不限制（向后兼容）
        verify(jdbc, times(1)).queryForObject(
            eq("select set_config('app.user_role', ?, true)"), eq(String.class), eq(""));
    }

    // 任务 S7 收口：BRANCH_MANAGER 携带真实 branchId 时写入 app.user_branch_id
    @Test
    void branchManagerPrincipalWritesRealBranchId() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);

        new RequestContext(jdbc).setTenant(branchManager("u-ben", "ten-1", "branch-shanghai"));

        verify(jdbc, times(1)).queryForObject(
            eq("select set_config('app.user_role', ?, true)"),
            eq(String.class), eq("BRANCH_MANAGER"));
        verify(jdbc, times(1)).queryForObject(
            eq("select set_config('app.user_branch_id', ?, true)"),
            eq(String.class), eq("branch-shanghai"));
    }

    // 不带 branchId 时仍设空字符串（policy 走 fallback）
    @Test
    void principalWithoutBranchIdWritesEmptyString() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);

        new RequestContext(jdbc).setTenant(salesman("u-alice", "ten-1"));

        verify(jdbc, times(1)).queryForObject(
            eq("select set_config('app.user_branch_id', ?, true)"),
            eq(String.class), eq(""));
    }
}

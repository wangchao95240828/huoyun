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
            List.of("SALESMAN"), List.of(), 0L, "jti-1");
    }

    private AuthPrincipal admin(String userId, String tenantId) {
        return new AuthPrincipal(userId, tenantId, "T1", "boss", "Boss",
            List.of("ADMIN"), List.of(), 0L, "jti-2");
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
            List.of(), List.of(), 0L, "jti-x");

        new RequestContext(jdbc).setTenant(noRole);

        // role 设空字符串 → policy IS NULL/'' 分支 → 不限制（向后兼容）
        verify(jdbc, times(1)).queryForObject(
            eq("select set_config('app.user_role', ?, true)"), eq(String.class), eq(""));
    }
}

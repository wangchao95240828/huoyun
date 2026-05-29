package com.xqt.saas.common;

import com.xqt.saas.auth.AuthPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class RequestContext {
    private final JdbcTemplate jdbc;

    public RequestContext(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public AuthPrincipal principal(Authentication authentication) {
        return (AuthPrincipal) authentication.getPrincipal();
    }

    public void setTenant(AuthPrincipal principal) {
        jdbc.queryForObject("select set_config('app.current_tenant_id', ?, true)", String.class, principal.tenantId());
        // 任务 S7：user 级 RLS 上下文（user_id + role + branch_id）
        // 取首个 role 作为主 role；空时设空字符串 → policy 视为 "no user context" → 向后兼容不限制
        String primaryRole = principal.roles() == null || principal.roles().isEmpty()
            ? "" : principal.roles().get(0);
        jdbc.queryForObject("select set_config('app.user_id', ?, true)",
            String.class, principal.userId() == null ? "" : principal.userId());
        jdbc.queryForObject("select set_config('app.user_role', ?, true)",
            String.class, primaryRole == null ? "" : primaryRole);
        // 任务 S7 收口：principal.branchId 已从 users.branch_id JOIN 取出
        jdbc.queryForObject("select set_config('app.user_branch_id', ?, true)",
            String.class, principal.branchId() == null ? "" : principal.branchId());
    }
}

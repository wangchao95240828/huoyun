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
    }
}

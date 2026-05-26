package com.xqt.saas.acc;

import java.io.IOException;

import com.xqt.saas.auth.AuthPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 把 /api/acc/** 的整个请求包在一个 PG 事务里，开头 set_config('app.current_tenant_id', ..., true)。
 *
 * 为什么需要：
 *   - ACC controller 里的 INSERT 用 `current_setting('app.current_tenant_id')::uuid` 取 tenant_id
 *   - 必须在 SAME postgres 连接 上先 set_config 再 INSERT
 *   - JdbcTemplate 每次调用从 Hikari 拿一个 connection，不保证同一个 → set_config 设了立刻没了
 *   - 用 TransactionTemplate.execute 包住请求 → 整个请求所有 JDBC 都用一个连接、一个事务
 *   - set_config(...,'true') = LOCAL 范围，事务结束自动清掉，不会污染连接池里的下一个请求
 *
 * 不在 /api/customer-api/** 上生效（那一套有自己的鉴权+tenant 流程）；
 * /api/auth/** 不需要 tenant（登录前）。
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class AccTenantTxFilter extends OncePerRequestFilter {
    private final TransactionTemplate transactionTemplate;
    private final JdbcTemplate jdbc;

    public AccTenantTxFilter(PlatformTransactionManager txManager, JdbcTemplate jdbc) {
        this.transactionTemplate = new TransactionTemplate(txManager);
        this.jdbc = jdbc;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // 只在我们提供的 ACC retrofit 路径生效，避免影响其它已经自管理事务的模块
        return !(path.startsWith("/api/acc/")
            || path.startsWith("/api/finance/dashboard")
            || path.startsWith("/api/finance/branches"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal principal)) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            transactionTemplate.execute(status -> {
                jdbc.queryForObject(
                    "select set_config('app.current_tenant_id', ?, true)",
                    String.class, principal.tenantId());
                try {
                    filterChain.doFilter(request, response);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
                return null;
            });
        } catch (RuntimeException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof IOException io) throw io;
            if (cause instanceof ServletException se) throw se;
            throw ex;
        }
    }
}

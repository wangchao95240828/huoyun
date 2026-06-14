package com.xqt.saas.framework.audit;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import com.xqt.saas.auth.AuthPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * 写操作全局审计 Interceptor — 对齐合规要求.
 *
 * 拦截 POST/PUT/DELETE 请求，落 acc_write_audit:
 *   - 用户 + IP + UA
 *   - 请求 path + 响应状态
 *   - 耗时
 *
 * 排除：login / forgot-password / acc/dws（已有其它审计）
 */
@Component
public class WriteAuditInterceptor implements HandlerInterceptor {
    private static final Logger LOGGER = LoggerFactory.getLogger(WriteAuditInterceptor.class);
    private static final String ATTR_START = "_acc_audit_start";

    private static final Set<String> EXCLUDED_PATHS = new HashSet<>(java.util.List.of(
        "/api/auth/login",
        "/api/auth/forgot-password",
        "/api/auth/reset-password",
        "/api/acc/dws",
        "/api/customer-register",
        "/actuator"
    ));

    private final JdbcTemplate jdbc;

    public WriteAuditInterceptor(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(ATTR_START, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                 Object handler, Exception ex) {
        String method = request.getMethod();
        if (!"POST".equals(method) && !"PUT".equals(method) && !"DELETE".equals(method) && !"PATCH".equals(method)) {
            return;
        }
        String path = request.getRequestURI();
        for (String excl : EXCLUDED_PATHS) {
            if (path.startsWith(excl)) return;
        }
        Long start = (Long) request.getAttribute(ATTR_START);
        long duration = start == null ? 0 : System.currentTimeMillis() - start;

        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String userId = null, userName = null, tenantId = null;
            if (auth != null && auth.getPrincipal() instanceof AuthPrincipal p) {
                userId = p.userId();
                userName = p.username();
                tenantId = p.tenantId();
            }
            String ip = request.getRemoteAddr();
            String ua = request.getHeader("user-agent");
            jdbc.update("""
                INSERT INTO acc_write_audit (tenant_id, user_id, user_name,
                                              method, path, response_status,
                                              duration_ms, ip, user_agent)
                VALUES (
                  coalesce(?::uuid, '2bda8c16-7b19-4ce6-ab71-9584f5a140ed'::uuid),
                  ?::uuid, ?, ?, ?, ?::int, ?::int, ?::inet, ?
                )
                """, tenantId, userId, userName, method, path,
                     response.getStatus(), (int) duration, ip, ua);
        } catch (Exception logEx) {
            LOGGER.warn("Write audit log failed: {}", logEx.getMessage());
        }
    }
}

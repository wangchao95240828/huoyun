package com.xqt.saas.auth;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.xqt.saas.common.ApiResponse;
import com.xqt.saas.common.ErrorCode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class BearerAuthFilter extends OncePerRequestFilter {
    private static final String AUTHORIZATION_PREFIX = "Bearer ";

    private final TokenService tokenService;
    private final AuthService authService;
    private final ObjectMapper objectMapper;

    public BearerAuthFilter(TokenService tokenService, AuthService authService, ObjectMapper objectMapper) {
        this.tokenService = tokenService;
        this.authService = authService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
            || "/api/auth/login".equals(path)
            || "/api/auth/forgot-password".equals(path)
            || "/api/auth/reset-password".equals(path)
            || "/api/customer-register".equals(path)
            || "/api/health".equals(path)
            || path.startsWith("/api/customer-api/")
            || path.startsWith("/api/device/scale/")
            || "/api/acc/dws".equals(path)        // DWS 用自己的 md5 token 校验
            || path.startsWith("/actuator/health")
            || path.startsWith("/v3/api-docs")
            || path.startsWith("/swagger-ui")
            || "/swagger-ui.html".equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(AUTHORIZATION_PREFIX)) {
            unauthorized(response, "Missing authorization token");
            return;
        }

        AuthPrincipal principal;
        try {
            principal = tokenService.verify(authorization.substring(AUTHORIZATION_PREFIX.length()).trim());
            authService.validateSession(principal);
            List<SimpleGrantedAuthority> authorities = new ArrayList<>();
            principal.permissions().forEach(code -> authorities.add(new SimpleGrantedAuthority(code)));
            principal.roles().forEach(code -> authorities.add(new SimpleGrantedAuthority("ROLE_" + code)));
            SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities)
            );
        } catch (RuntimeException ex) {
            SecurityContextHolder.clearContext();
            unauthorized(response, "Invalid authorization token");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void unauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(ErrorCode.UNAUTHORIZED, message));
    }
}

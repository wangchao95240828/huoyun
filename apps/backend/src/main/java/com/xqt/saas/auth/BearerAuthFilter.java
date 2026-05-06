package com.xqt.saas.auth;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class BearerAuthFilter extends OncePerRequestFilter {
    private final TokenService tokenService;
    private final AuthService authService;

    public BearerAuthFilter(TokenService tokenService, AuthService authService) {
        this.tokenService = tokenService;
        this.authService = authService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
            || "/api/auth/login".equals(path)
            || "/api/health".equals(path)
            || path.startsWith("/actuator/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            unauthorized(response, "Missing authorization token");
            return;
        }

        AuthPrincipal principal;
        try {
            principal = tokenService.verify(authorization.substring("Bearer ".length()).trim());
            authService.validateSession(principal);
            List<SimpleGrantedAuthority> authorities = new ArrayList<>();
            principal.permissions().forEach(code -> authorities.add(new SimpleGrantedAuthority(code)));
            principal.roles().forEach(code -> authorities.add(new SimpleGrantedAuthority("ROLE_" + code)));
            SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities)
            );
        } catch (Exception ex) {
            SecurityContextHolder.clearContext();
            unauthorized(response, "Invalid authorization token");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void unauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"ok\":false,\"error\":\"" + message + "\"}");
    }
}

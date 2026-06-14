package com.xqt.saas.framework.ratelimit;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 简单的令牌桶限流 — DDoS / 暴力破解防护。
 *
 * 默认：每 IP 每分钟 600 请求
 * /api/auth/login 每 IP 每分钟 10 请求（防暴力破解）
 * /api/customer-register 每 IP 每分钟 3 请求
 *
 * 配置 rate-limit.enabled=false 关闭。
 */
@Component
@Order(1)
public class RateLimitFilter implements Filter {

    private static class Bucket {
        AtomicInteger count = new AtomicInteger(0);
        volatile long windowStart = System.currentTimeMillis();
    }

    @Value("${rate-limit.enabled:true}")
    private boolean enabled;

    @Value("${rate-limit.default-per-minute:600}")
    private int defaultLimit;

    @Value("${rate-limit.login-per-minute:10}")
    private int loginLimit;

    @Value("${rate-limit.register-per-minute:3}")
    private int registerLimit;

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private static final long WINDOW_MS = 60_000;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
        throws IOException, ServletException {
        if (!enabled || !(request instanceof HttpServletRequest req)) {
            chain.doFilter(request, response);
            return;
        }
        String path = req.getRequestURI();
        // 健康检查不限流
        if (path.startsWith("/api/health") || path.startsWith("/actuator")) {
            chain.doFilter(request, response);
            return;
        }
        String ip = clientIp(req);
        int limit = limitForPath(path);
        String key = ip + "::" + (limit == defaultLimit ? "default" : path);
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket());

        long now = System.currentTimeMillis();
        synchronized (bucket) {
            if (now - bucket.windowStart > WINDOW_MS) {
                bucket.windowStart = now;
                bucket.count.set(0);
            }
            int n = bucket.count.incrementAndGet();
            if (n > limit) {
                HttpServletResponse resp = (HttpServletResponse) response;
                resp.setStatus(429);
                resp.setHeader("Retry-After", String.valueOf(WINDOW_MS / 1000));
                resp.setContentType("application/json");
                resp.getWriter().write(
                    "{\"ok\":false,\"error\":\"请求过于频繁，请稍后再试\",\"errorCode\":\"RATE_LIMITED\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private int limitForPath(String path) {
        if (path.equals("/api/auth/login")) return loginLimit;
        if (path.equals("/api/auth/forgot-password")) return loginLimit;
        if (path.equals("/api/auth/reset-password")) return loginLimit;
        if (path.equals("/api/customer-register")) return registerLimit;
        return defaultLimit;
    }

    private String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}

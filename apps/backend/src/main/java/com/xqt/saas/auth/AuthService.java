package com.xqt.saas.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Array;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class AuthService {
    private final JdbcTemplate jdbc;
    private final PasswordHasher passwordHasher;
    private final TokenService tokenService;

    public AuthService(JdbcTemplate jdbc, PasswordHasher passwordHasher, TokenService tokenService) {
        this.jdbc = jdbc;
        this.passwordHasher = passwordHasher;
        this.tokenService = tokenService;
    }

    @Transactional
    public LoginResponse login(LoginRequest request, String ip, String userAgent) {
        setServiceRole();

        String tenantCode = isBlank(request.tenantCode()) ? "xqt" : request.tenantCode();
        String username = request.username().trim();
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT
              t.id AS tenant_id,
              t.code AS tenant_code,
              u.id AS user_id,
              u.username,
              u.email,
              u.display_name,
              u.password_hash,
              u.status,
              u.locked_until,
              COALESCE(array_agg(DISTINCT r.code) FILTER (WHERE r.code IS NOT NULL), ARRAY[]::text[]) AS roles,
              COALESCE(array_agg(DISTINCT p.code) FILTER (WHERE p.code IS NOT NULL), ARRAY[]::text[]) AS permissions
            FROM tenants t
            JOIN users u ON u.tenant_id = t.id
            LEFT JOIN user_roles ur ON ur.tenant_id = t.id AND ur.user_id = u.id
            LEFT JOIN roles r ON r.tenant_id = t.id AND r.id = ur.role_id
            LEFT JOIN role_permissions rp ON rp.tenant_id = t.id AND rp.role_id = r.id
            LEFT JOIN permissions p ON p.tenant_id = t.id AND p.id = rp.permission_id
            WHERE t.code = ?
              AND (u.username = ? OR u.email = ?)
            GROUP BY t.id, t.code, u.id, u.username, u.email, u.display_name, u.password_hash, u.status, u.locked_until
            LIMIT 1
            """, tenantCode, username, username);

        if (rows.isEmpty()) {
            logLogin(null, null, username, false, "USER_NOT_FOUND", ip, userAgent);
            throw new ResponseStatusException(UNAUTHORIZED, "用户名或密码错误");
        }

        Map<String, Object> row = rows.get(0);
        String tenantId = string(row.get("tenant_id"));
        String userId = string(row.get("user_id"));
        String status = string(row.get("status"));
        Timestamp lockedUntil = (Timestamp) row.get("locked_until");

        if (!"ACTIVE".equals(status)) {
            logLogin(tenantId, userId, username, false, "USER_NOT_ACTIVE", ip, userAgent);
            throw new ResponseStatusException(UNAUTHORIZED, "用户不可用");
        }
        if (lockedUntil != null && lockedUntil.toInstant().isAfter(Instant.now())) {
            logLogin(tenantId, userId, username, false, "USER_LOCKED", ip, userAgent);
            throw new ResponseStatusException(UNAUTHORIZED, "用户已临时锁定");
        }
        if (!passwordHasher.verify(request.password(), string(row.get("password_hash")))) {
            jdbc.update("""
                UPDATE users
                SET failed_login_count = failed_login_count + 1,
                    locked_until = CASE WHEN failed_login_count + 1 >= 5 THEN now() + interval '15 minutes' ELSE locked_until END
                WHERE id = ?::uuid
                """, userId);
            logLogin(tenantId, userId, username, false, "BAD_PASSWORD", ip, userAgent);
            throw new ResponseStatusException(UNAUTHORIZED, "用户名或密码错误");
        }

        AuthPrincipal base = new AuthPrincipal(
            userId,
            tenantId,
            string(row.get("tenant_code")),
            isBlank(string(row.get("username"))) ? string(row.get("email")) : string(row.get("username")),
            string(row.get("display_name")),
            textArray(row.get("roles")),
            textArray(row.get("permissions")),
            0,
            ""
        );
        TokenService.IssuedToken issued = tokenService.issue(base);

        jdbc.update("""
            INSERT INTO user_sessions (tenant_id, user_id, session_hash, ip, user_agent, expires_at)
            VALUES (?::uuid, ?::uuid, ?, ?::inet, ?, to_timestamp(?))
            """,
            tenantId,
            userId,
            sessionHash(issued.payload().jti()),
            emptyToNull(ip),
            userAgent,
            issued.payload().exp()
        );
        jdbc.update("""
            UPDATE users
            SET last_login_at = now(), failed_login_count = 0, locked_until = NULL
            WHERE id = ?::uuid
            """, userId);
        logLogin(tenantId, userId, username, true, null, ip, userAgent);

        return new LoginResponse(true, issued.token(), issued.expiresIn(), issued.payload());
    }

    @Transactional(readOnly = true)
    public void validateSession(AuthPrincipal principal) {
        setServiceRole();
        Integer count = jdbc.queryForObject("""
            SELECT count(*)::int
            FROM user_sessions
            WHERE tenant_id = ?::uuid
              AND user_id = ?::uuid
              AND session_hash = ?
              AND revoked_at IS NULL
              AND expires_at > now()
            """, Integer.class, principal.tenantId(), principal.userId(), sessionHash(principal.jti()));
        if (count == null || count == 0) {
            throw new ResponseStatusException(UNAUTHORIZED, "Session expired");
        }
    }

    @Transactional
    public void logout(AuthPrincipal principal) {
        setServiceRole();
        jdbc.update("""
            UPDATE user_sessions
            SET revoked_at = now()
            WHERE tenant_id = ?::uuid
              AND user_id = ?::uuid
              AND session_hash = ?
            """, principal.tenantId(), principal.userId(), sessionHash(principal.jti()));
    }

    private void logLogin(String tenantId, String userId, String username, boolean success, String reason, String ip, String userAgent) {
        jdbc.update("""
            INSERT INTO auth_login_events (tenant_id, user_id, username, success, failure_reason, ip, user_agent)
            VALUES (?::uuid, ?::uuid, ?, ?, ?, ?::inet, ?)
            """, tenantId, userId, username, success, reason, emptyToNull(ip), userAgent);
    }

    private void setServiceRole() {
        jdbc.queryForObject("select set_config('app.service_role', 'true', true)", String.class);
    }

    private List<String> textArray(Object value) {
        try {
            if (value == null) {
                return Collections.emptyList();
            }
            if (value instanceof String[] values) {
                return Arrays.asList(values);
            }
            if (value instanceof Array array) {
                Object raw = array.getArray();
                if (raw instanceof String[] values) {
                    return Arrays.asList(values);
                }
            }
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
        return Collections.emptyList();
    }

    private String sessionHash(String jti) {
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256").digest(jti.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash session id", ex);
        }
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    private String string(Object value) {
        return value == null ? "" : value.toString();
    }

    private String emptyToNull(String value) {
        return isBlank(value) ? null : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

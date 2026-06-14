package com.xqt.saas.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Array;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthService.class);
    private static final String DEFAULT_TENANT_CODE = "xqt";
    private static final String FAILURE_BAD_PASSWORD = "BAD_PASSWORD";
    private static final String FAILURE_USER_LOCKED = "USER_LOCKED";
    private static final String FAILURE_USER_NOT_ACTIVE = "USER_NOT_ACTIVE";
    private static final String FAILURE_USER_NOT_FOUND = "USER_NOT_FOUND";
    private static final String FIELD_DISPLAY_NAME = "display_name";
    private static final String FIELD_EMAIL = "email";
    private static final String FIELD_LOCKED_UNTIL = "locked_until";
    private static final String FIELD_PASSWORD_HASH = "password_hash";
    private static final String FIELD_PERMISSIONS = "permissions";
    private static final String FIELD_ROLES = "roles";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_TENANT_CODE = "tenant_code";
    private static final String FIELD_TENANT_ID = "tenant_id";
    private static final String FIELD_USERNAME = "username";
    private static final String FIELD_USER_ID = "user_id";
    private static final String STATUS_ACTIVE = "ACTIVE";

    private final JdbcTemplate jdbc;
    private final PasswordHasher passwordHasher;
    private final TokenService tokenService;

    public AuthService(JdbcTemplate jdbc, PasswordHasher passwordHasher, TokenService tokenService) {
        this.jdbc = jdbc;
        this.passwordHasher = passwordHasher;
        this.tokenService = tokenService;
    }

    @Transactional(rollbackFor = Exception.class, noRollbackFor = com.xqt.saas.common.ApiException.class)
    public LoginResponse login(LoginRequest request, String ip, String userAgent) {
        setServiceRole();

        String tenantCode = isBlank(request.tenantCode()) ? DEFAULT_TENANT_CODE : request.tenantCode();
        String username = request.username().trim();
        List<Map<String, Object>> rows = findLoginRows(tenantCode, username);

        if (rows.isEmpty()) {
            logLogin(null, null, username, false, FAILURE_USER_NOT_FOUND, ip, userAgent);
            throw ApiException.unauthorized("用户名或密码错误");
        }

        Map<String, Object> row = rows.get(0);
        String tenantId = string(row.get(FIELD_TENANT_ID));
        String userId = string(row.get(FIELD_USER_ID));

        validateStatus(row, tenantId, userId, username, ip, userAgent);
        validatePassword(request.password(), row, tenantId, userId, username, ip, userAgent);

        AuthPrincipal base = loginPrincipal(row, tenantId, userId);
        TokenService.IssuedToken issued = tokenService.issue(base);

        createSession(tenantId, userId, issued, ip, userAgent);
        markLoginSuccess(userId);
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
            throw ApiException.unauthorized("Session expired");
        }
    }

    @Transactional(rollbackFor = Exception.class)
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

    private List<Map<String, Object>> findLoginRows(String tenantCode, String username) {
        return jdbc.queryForList("""
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
              u.branch_id::text AS branch_id,
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
            GROUP BY t.id, t.code, u.id, u.username, u.email, u.display_name, u.password_hash, u.status, u.locked_until, u.branch_id
            LIMIT 1
            """, tenantCode, username, username);
    }

    private void validateStatus(Map<String, Object> row, String tenantId, String userId, String username, String ip, String userAgent) {
        String status = string(row.get(FIELD_STATUS));
        Timestamp lockedUntil = (Timestamp) row.get(FIELD_LOCKED_UNTIL);

        if (!STATUS_ACTIVE.equals(status)) {
            logLogin(tenantId, userId, username, false, FAILURE_USER_NOT_ACTIVE, ip, userAgent);
            throw ApiException.unauthorized("用户不可用");
        }
        if (lockedUntil != null && lockedUntil.toInstant().isAfter(Instant.now())) {
            logLogin(tenantId, userId, username, false, FAILURE_USER_LOCKED, ip, userAgent);
            throw ApiException.unauthorized("用户已临时锁定");
        }
    }

    private void validatePassword(
        String password,
        Map<String, Object> row,
        String tenantId,
        String userId,
        String username,
        String ip,
        String userAgent
    ) {
        if (passwordHasher.verify(password, string(row.get(FIELD_PASSWORD_HASH)))) {
            return;
        }

        jdbc.update("""
            UPDATE users
            SET failed_login_count = failed_login_count + 1,
                locked_until = CASE WHEN failed_login_count + 1 >= 5 THEN now() + interval '15 minutes' ELSE locked_until END
            WHERE id = ?::uuid
            """, userId);
        logLogin(tenantId, userId, username, false, FAILURE_BAD_PASSWORD, ip, userAgent);
        throw ApiException.unauthorized("用户名或密码错误");
    }

    private AuthPrincipal loginPrincipal(Map<String, Object> row, String tenantId, String userId) {
        String username = string(row.get(FIELD_USERNAME));
        String branchId = string(row.get("branch_id"));
        return new AuthPrincipal(
            userId,
            tenantId,
            string(row.get(FIELD_TENANT_CODE)),
            isBlank(username) ? string(row.get(FIELD_EMAIL)) : username,
            string(row.get(FIELD_DISPLAY_NAME)),
            textArray(row.get(FIELD_ROLES)),
            textArray(row.get(FIELD_PERMISSIONS)),
            0,
            "",
            isBlank(branchId) ? null : branchId
        );
    }

    private void createSession(String tenantId, String userId, TokenService.IssuedToken issued, String ip, String userAgent) {
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
    }

    /**
     * ACC User.php 邮箱重置：生成 token 存 metadata，1 小时过期。
     * 实际生产应发邮件；此处把 token 写入 users.metadata.reset_token + 日志。
     */
    @Transactional(rollbackFor = Exception.class)
    public void requestPasswordReset(String tenantCode, String email) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT u.id::text AS user_id FROM users u
                  JOIN tenants t ON t.id = u.tenant_id
                 WHERE t.code = ? AND u.email = ? AND u.deleted_at IS NULL
                """, tenantCode, email);
            if (rows.isEmpty()) return; // 不泄露邮箱存在与否
            String userId = (String) rows.get(0).get("user_id");
            String token = java.util.UUID.randomUUID().toString().replace("-", "");
            jdbc.update("""
                UPDATE users
                   SET metadata = coalesce(metadata, '{}'::jsonb) || jsonb_build_object(
                       'reset_token', ?::text,
                       'reset_expires_at', (now() + interval '1 hour')::text)
                 WHERE id = ?::uuid
                """, token, userId);
            // 在生产环境改为邮件发送；这里日志输出供调试
            org.slf4j.LoggerFactory.getLogger(AuthService.class)
                .info("PASSWORD_RESET token for email={} userId={}: {}", email, userId, token);
        } catch (Exception ex) {
            // 静默吞 — 不暴露用户存在与否
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(String token, String newPassword) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT id::text AS user_id, metadata
              FROM users
             WHERE metadata->>'reset_token' = ?
               AND (metadata->>'reset_expires_at')::timestamptz > now()
               AND deleted_at IS NULL
            """, token);
        if (rows.isEmpty()) {
            throw com.xqt.saas.common.ApiException.badRequest("重置链接已失效或不存在");
        }
        String userId = (String) rows.get(0).get("user_id");
        String hash = passwordHasher.hash(newPassword);
        jdbc.update("""
            UPDATE users
               SET password_hash = ?,
                   failed_login_count = 0,
                   locked_until = NULL,
                   metadata = (metadata - 'reset_token') - 'reset_expires_at'
             WHERE id = ?::uuid
            """, hash, userId);
    }

    private void markLoginSuccess(String userId) {
        jdbc.update("""
            UPDATE users
            SET last_login_at = now(), failed_login_count = 0, locked_until = NULL
            WHERE id = ?::uuid
            """, userId);
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
        } catch (SQLException ex) {
            LOGGER.debug("Unable to read SQL array from login query", ex);
            return Collections.emptyList();
        }
        return Collections.emptyList();
    }

    private String sessionHash(String jti) {
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256").digest(jti.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
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

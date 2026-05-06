package com.xqt.saas.admin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.xqt.saas.auth.AuthPrincipal;
import com.xqt.saas.auth.PasswordHasher;
import com.xqt.saas.common.AuditService;
import com.xqt.saas.common.JsonSupport;
import com.xqt.saas.common.RequestContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final JdbcTemplate jdbc;
    private final RequestContext context;
    private final JsonSupport json;
    private final AuditService auditService;
    private final PasswordHasher passwordHasher;

    public AdminController(
        JdbcTemplate jdbc,
        RequestContext context,
        JsonSupport json,
        AuditService auditService,
        PasswordHasher passwordHasher
    ) {
        this.jdbc = jdbc;
        this.context = context;
        this.json = json;
        this.auditService = auditService;
        this.passwordHasher = passwordHasher;
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('admin.user.read')")
    @Transactional(readOnly = true)
    public Map<String, Object> users(Authentication authentication) {
        AuthPrincipal auth = context.principal(authentication);
        context.setTenant(auth);
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT
              u.id,
              u.username,
              u.email,
              u.display_name,
              u.role_code,
              u.status,
              u.last_login_at,
              u.created_at,
              u.updated_at,
              u.deleted_at,
              COALESCE(array_agg(DISTINCT r.code) FILTER (WHERE r.code IS NOT NULL), ARRAY[]::text[]) AS roles
            FROM users u
            LEFT JOIN user_roles ur ON ur.tenant_id = u.tenant_id AND ur.user_id = u.id
            LEFT JOIN roles r ON r.tenant_id = u.tenant_id AND r.id = ur.role_id
            WHERE u.tenant_id = ?::uuid
              AND u.deleted_at IS NULL
            GROUP BY u.id
            ORDER BY u.created_at DESC
            """, auth.tenantId());
        List<Map<String, Object>> items = json.rows(rows);
        return Map.of("ok", true, "items", items, "data", items);
    }

    @PostMapping("/users")
    @PreAuthorize("hasAuthority('admin.user.write')")
    @Transactional
    public Map<String, Object> createUser(Authentication authentication, @Valid @RequestBody UserSaveRequest request) {
        AuthPrincipal auth = context.principal(authentication);
        context.setTenant(auth);
        if (isBlank(request.password())) {
            throw new ResponseStatusException(BAD_REQUEST, "password is required");
        }

        String userId = jdbc.queryForObject("""
            INSERT INTO users (
              tenant_id, username, email, display_name, role_code, password_hash, status, created_by, updated_by
            )
            VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?::uuid, ?::uuid)
            RETURNING id::text
            """,
            String.class,
            auth.tenantId(),
            request.username(),
            request.email(),
            request.displayName(),
            primaryRole(request.roleCodes()),
            passwordHasher.hash(request.password()),
            nonBlank(request.status(), "ACTIVE"),
            auth.userId(),
            auth.userId()
        );
        replaceUserRoles(auth, userId, roleCodesOrPrimary(request.roleCodes()));
        Map<String, Object> after = findUser(auth, userId);
        auditService.log(auth, "user", userId, "CREATE", null, after);
        return Map.of("ok", true, "item", after, "data", after);
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasAuthority('admin.user.write')")
    @Transactional
    public Map<String, Object> updateUser(
        Authentication authentication,
        @PathVariable("id") String userId,
        @RequestBody UserSaveRequest request
    ) {
        AuthPrincipal auth = context.principal(authentication);
        context.setTenant(auth);
        Map<String, Object> before = findUser(auth, userId);
        if (before == null) {
            throw new ResponseStatusException(NOT_FOUND, "user not found");
        }

        String passwordHash = isBlank(request.password()) ? null : passwordHasher.hash(request.password());
        jdbc.update("""
            UPDATE users
            SET username = COALESCE(NULLIF(?, ''), username),
                email = COALESCE(NULLIF(?, ''), email),
                display_name = COALESCE(NULLIF(?, ''), display_name),
                role_code = COALESCE(NULLIF(?, ''), role_code),
                password_hash = COALESCE(?, password_hash),
                status = COALESCE(NULLIF(?, ''), status),
                updated_by = ?::uuid
            WHERE tenant_id = ?::uuid
              AND id = ?::uuid
              AND deleted_at IS NULL
            """,
            nullToBlank(request.username()),
            nullToBlank(request.email()),
            nullToBlank(request.displayName()),
            request.roleCodes() == null || request.roleCodes().isEmpty() ? "" : primaryRole(request.roleCodes()),
            passwordHash,
            nullToBlank(request.status()),
            auth.userId(),
            auth.tenantId(),
            userId
        );
        if (request.roleCodes() != null) {
            replaceUserRoles(auth, userId, roleCodesOrPrimary(request.roleCodes()));
        }

        Map<String, Object> after = findUser(auth, userId);
        auditService.log(auth, "user", userId, "UPDATE", before, after);
        return Map.of("ok", true, "item", after, "data", after);
    }

    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasAuthority('admin.user.write')")
    @Transactional
    public Map<String, Object> deleteUser(Authentication authentication, @PathVariable("id") String userId) {
        AuthPrincipal auth = context.principal(authentication);
        context.setTenant(auth);
        if (auth.userId().equals(userId)) {
            throw new ResponseStatusException(BAD_REQUEST, "cannot delete current user");
        }
        Map<String, Object> before = findUser(auth, userId);
        if (before == null) {
            throw new ResponseStatusException(NOT_FOUND, "user not found");
        }

        jdbc.update("""
            UPDATE users
            SET status = 'DISABLED',
                deleted_at = now(),
                deleted_by = ?::uuid,
                updated_by = ?::uuid
            WHERE tenant_id = ?::uuid
              AND id = ?::uuid
              AND deleted_at IS NULL
            """, auth.userId(), auth.userId(), auth.tenantId(), userId);
        auditService.log(auth, "user", userId, "DELETE", before, Map.of("id", userId, "deleted", true));
        return Map.of("ok", true);
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('admin.role.read')")
    @Transactional(readOnly = true)
    public Map<String, Object> roles(Authentication authentication) {
        AuthPrincipal auth = context.principal(authentication);
        context.setTenant(auth);
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT
              r.id,
              r.code,
              r.name,
              r.description,
              r.system_role,
              r.status,
              r.created_at,
              r.updated_at,
              COALESCE(array_agg(DISTINCT p.code) FILTER (WHERE p.code IS NOT NULL), ARRAY[]::text[]) AS permissions
            FROM roles r
            LEFT JOIN role_permissions rp ON rp.tenant_id = r.tenant_id AND rp.role_id = r.id
            LEFT JOIN permissions p ON p.tenant_id = r.tenant_id AND p.id = rp.permission_id
            WHERE r.tenant_id = ?::uuid
              AND r.deleted_at IS NULL
            GROUP BY r.id
            ORDER BY r.system_role DESC, r.code
            """, auth.tenantId());
        List<Map<String, Object>> items = json.rows(rows);
        return Map.of("ok", true, "items", items, "data", items);
    }

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('admin.role.write')")
    @Transactional
    public Map<String, Object> createRole(Authentication authentication, @Valid @RequestBody RoleSaveRequest request) {
        AuthPrincipal auth = context.principal(authentication);
        context.setTenant(auth);
        String roleId = jdbc.queryForObject("""
            INSERT INTO roles (tenant_id, code, name, description, status, created_by, updated_by, metadata)
            VALUES (?::uuid, ?, ?, ?, ?, ?::uuid, ?::uuid, ?::jsonb)
            RETURNING id::text
            """,
            String.class,
            auth.tenantId(),
            request.code(),
            request.name(),
            request.description(),
            nonBlank(request.status(), "ACTIVE"),
            auth.userId(),
            auth.userId(),
            json.toJson(request.metadata())
        );
        if (request.permissionCodes() != null) {
            replaceRolePermissions(auth, roleId, request.permissionCodes());
        }
        Map<String, Object> after = findRole(auth, roleId);
        auditService.log(auth, "role", roleId, "CREATE", null, after);
        return Map.of("ok", true, "item", after, "data", after);
    }

    @PutMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('admin.role.write')")
    @Transactional
    public Map<String, Object> updateRole(
        Authentication authentication,
        @PathVariable("id") String roleId,
        @RequestBody RoleSaveRequest request
    ) {
        AuthPrincipal auth = context.principal(authentication);
        context.setTenant(auth);
        Map<String, Object> before = findRole(auth, roleId);
        if (before == null) {
            throw new ResponseStatusException(NOT_FOUND, "role not found");
        }

        jdbc.update("""
            UPDATE roles
            SET name = COALESCE(NULLIF(?, ''), name),
                description = ?,
                status = COALESCE(NULLIF(?, ''), status),
                metadata = ?::jsonb,
                updated_by = ?::uuid
            WHERE tenant_id = ?::uuid
              AND id = ?::uuid
              AND deleted_at IS NULL
            """,
            nullToBlank(request.name()),
            request.description(),
            nullToBlank(request.status()),
            json.toJson(request.metadata()),
            auth.userId(),
            auth.tenantId(),
            roleId
        );
        if (request.permissionCodes() != null) {
            replaceRolePermissions(auth, roleId, request.permissionCodes());
        }
        Map<String, Object> after = findRole(auth, roleId);
        auditService.log(auth, "role", roleId, "UPDATE", before, after);
        return Map.of("ok", true, "item", after, "data", after);
    }

    @PutMapping("/roles/{id}/permissions")
    @PreAuthorize("hasAuthority('admin.role.write')")
    @Transactional
    public Map<String, Object> setRolePermissions(
        Authentication authentication,
        @PathVariable("id") String roleId,
        @RequestBody RolePermissionsRequest request
    ) {
        AuthPrincipal auth = context.principal(authentication);
        context.setTenant(auth);
        Map<String, Object> before = findRole(auth, roleId);
        if (before == null) {
            throw new ResponseStatusException(NOT_FOUND, "role not found");
        }
        replaceRolePermissions(auth, roleId, safeList(request.permissionCodes()));
        Map<String, Object> after = findRole(auth, roleId);
        auditService.log(auth, "role", roleId, "SET_PERMISSIONS", before, after);
        return Map.of("ok", true, "item", after, "data", after);
    }

    @DeleteMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('admin.role.write')")
    @Transactional
    public Map<String, Object> deleteRole(Authentication authentication, @PathVariable("id") String roleId) {
        AuthPrincipal auth = context.principal(authentication);
        context.setTenant(auth);
        Map<String, Object> before = findRole(auth, roleId);
        if (before == null) {
            throw new ResponseStatusException(NOT_FOUND, "role not found");
        }
        Boolean systemRole = jdbc.queryForObject("""
            SELECT system_role
            FROM roles
            WHERE tenant_id = ?::uuid AND id = ?::uuid
            """, Boolean.class, auth.tenantId(), roleId);
        if (Boolean.TRUE.equals(systemRole)) {
            throw new ResponseStatusException(BAD_REQUEST, "system role cannot be deleted");
        }
        jdbc.update("""
            UPDATE roles
            SET status = 'ARCHIVED',
                deleted_at = now(),
                deleted_by = ?::uuid,
                updated_by = ?::uuid
            WHERE tenant_id = ?::uuid
              AND id = ?::uuid
              AND deleted_at IS NULL
            """, auth.userId(), auth.userId(), auth.tenantId(), roleId);
        auditService.log(auth, "role", roleId, "DELETE", before, Map.of("id", roleId, "deleted", true));
        return Map.of("ok", true);
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAnyAuthority('admin.role.read','admin.permission.read')")
    @Transactional(readOnly = true)
    public Map<String, Object> permissions(Authentication authentication) {
        AuthPrincipal auth = context.principal(authentication);
        context.setTenant(auth);
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT id, code, name, resource, action, description, status, created_at, updated_at
            FROM permissions
            WHERE tenant_id = ?::uuid
              AND deleted_at IS NULL
            ORDER BY resource, action, code
            """, auth.tenantId());
        List<Map<String, Object>> items = json.rows(rows);
        return Map.of("ok", true, "items", items, "data", items);
    }

    @GetMapping("/audit-logs")
    @PreAuthorize("hasAnyAuthority('admin.audit.read','ROLE_ADMIN')")
    @Transactional(readOnly = true)
    public Map<String, Object> auditLogs(
        Authentication authentication,
        @RequestParam(value = "entityType", required = false) String entityType,
        @RequestParam(value = "action", required = false) String action,
        @RequestParam(value = "page", required = false, defaultValue = "1") int page,
        @RequestParam(value = "pageSize", required = false, defaultValue = "50") int pageSize
    ) {
        AuthPrincipal auth = context.principal(authentication);
        context.setTenant(auth);
        int limit = Math.max(1, Math.min(pageSize, 100));
        int offset = Math.max(0, page - 1) * limit;
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT
              a.id,
              a.entity_type,
              a.entity_id,
              a.action,
              a.before_data,
              a.after_data,
              a.created_at,
              u.display_name AS actor_name,
              u.username AS actor_username
            FROM audit_logs a
            LEFT JOIN users u ON u.tenant_id = a.tenant_id AND u.id = a.actor_id
            WHERE a.tenant_id = ?::uuid
              AND (?::text IS NULL OR a.entity_type = ?)
              AND (?::text IS NULL OR a.action = ?)
            ORDER BY a.created_at DESC
            LIMIT ? OFFSET ?
            """,
            auth.tenantId(),
            emptyToNull(entityType),
            emptyToNull(entityType),
            emptyToNull(action),
            emptyToNull(action),
            limit,
            offset
        );
        List<Map<String, Object>> items = json.rows(rows);
        return Map.of("ok", true, "items", items, "data", items, "page", page, "pageSize", limit);
    }

    private Map<String, Object> findUser(AuthPrincipal auth, String userId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT
              u.id,
              u.username,
              u.email,
              u.display_name,
              u.role_code,
              u.status,
              u.last_login_at,
              u.created_at,
              u.updated_at,
              COALESCE(array_agg(DISTINCT r.code) FILTER (WHERE r.code IS NOT NULL), ARRAY[]::text[]) AS roles
            FROM users u
            LEFT JOIN user_roles ur ON ur.tenant_id = u.tenant_id AND ur.user_id = u.id
            LEFT JOIN roles r ON r.tenant_id = u.tenant_id AND r.id = ur.role_id
            WHERE u.tenant_id = ?::uuid
              AND u.id = ?::uuid
              AND u.deleted_at IS NULL
            GROUP BY u.id
            """, auth.tenantId(), userId);
        return rows.isEmpty() ? null : json.row(rows.get(0));
    }

    private Map<String, Object> findRole(AuthPrincipal auth, String roleId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT
              r.id,
              r.code,
              r.name,
              r.description,
              r.system_role,
              r.status,
              r.created_at,
              r.updated_at,
              COALESCE(array_agg(DISTINCT p.code) FILTER (WHERE p.code IS NOT NULL), ARRAY[]::text[]) AS permissions
            FROM roles r
            LEFT JOIN role_permissions rp ON rp.tenant_id = r.tenant_id AND rp.role_id = r.id
            LEFT JOIN permissions p ON p.tenant_id = r.tenant_id AND p.id = rp.permission_id
            WHERE r.tenant_id = ?::uuid
              AND r.id = ?::uuid
              AND r.deleted_at IS NULL
            GROUP BY r.id
            """, auth.tenantId(), roleId);
        return rows.isEmpty() ? null : json.row(rows.get(0));
    }

    private void replaceUserRoles(AuthPrincipal auth, String userId, List<String> roleCodes) {
        jdbc.update("""
            DELETE FROM user_roles
            WHERE tenant_id = ?::uuid
              AND user_id = ?::uuid
            """, auth.tenantId(), userId);
        for (String roleCode : roleCodes) {
            List<Integer> inserted = jdbc.queryForList("""
                WITH target_role AS (
                  SELECT id
                  FROM roles
                  WHERE tenant_id = ?::uuid
                    AND code = ?
                    AND deleted_at IS NULL
                    AND status = 'ACTIVE'
                )
                INSERT INTO user_roles (tenant_id, user_id, role_id, granted_by)
                SELECT ?::uuid, ?::uuid, id, ?::uuid
                FROM target_role
                ON CONFLICT DO NOTHING
                RETURNING 1
                """,
                Integer.class,
                auth.tenantId(),
                roleCode,
                auth.tenantId(),
                userId,
                auth.userId()
            );
            if (inserted.isEmpty()) {
                throw new ResponseStatusException(BAD_REQUEST, "role not found: " + roleCode);
            }
        }
    }

    private void replaceRolePermissions(AuthPrincipal auth, String roleId, List<String> permissionCodes) {
        jdbc.update("""
            DELETE FROM role_permissions
            WHERE tenant_id = ?::uuid
              AND role_id = ?::uuid
            """, auth.tenantId(), roleId);
        for (String permissionCode : permissionCodes) {
            List<Integer> inserted = jdbc.queryForList("""
                WITH target_permission AS (
                  SELECT id
                  FROM permissions
                  WHERE tenant_id = ?::uuid
                    AND code = ?
                    AND deleted_at IS NULL
                    AND status = 'ACTIVE'
                )
                INSERT INTO role_permissions (tenant_id, role_id, permission_id)
                SELECT ?::uuid, ?::uuid, id
                FROM target_permission
                ON CONFLICT DO NOTHING
                RETURNING 1
                """,
                Integer.class,
                auth.tenantId(),
                permissionCode,
                auth.tenantId(),
                roleId
            );
            if (inserted.isEmpty()) {
                throw new ResponseStatusException(BAD_REQUEST, "permission not found: " + permissionCode);
            }
        }
    }

    private List<String> roleCodesOrPrimary(List<String> roleCodes) {
        return roleCodes == null || roleCodes.isEmpty() ? List.of("OPERATOR") : safeList(roleCodes);
    }

    private String primaryRole(List<String> roleCodes) {
        return roleCodes == null || roleCodes.isEmpty() ? "OPERATOR" : roleCodes.get(0);
    }

    private List<String> safeList(List<String> values) {
        if (values == null) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (!isBlank(value)) {
                result.add(value.trim());
            }
        }
        return result;
    }

    private String nonBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private String emptyToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record UserSaveRequest(
        @NotBlank String username,
        @NotBlank String email,
        @NotBlank String displayName,
        String password,
        String status,
        List<String> roleCodes
    ) {
    }

    public record RoleSaveRequest(
        @NotBlank String code,
        @NotBlank String name,
        String description,
        String status,
        List<String> permissionCodes,
        Map<String, Object> metadata
    ) {
    }

    public record RolePermissionsRequest(List<String> permissionCodes) {
    }
}

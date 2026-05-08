package com.xqt.saas.admin;

import java.util.List;
import java.util.Map;

import com.xqt.saas.common.JsonSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminRepository {
    private final JdbcTemplate jdbc;
    private final JsonSupport json;

    public AdminRepository(JdbcTemplate jdbc, JsonSupport json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public List<Map<String, Object>> findUsers(String tenantId) {
        return json.rows(jdbc.queryForList("""
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
            """, tenantId));
    }

    public Map<String, Object> findUser(String tenantId, String userId) {
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
            """, tenantId, userId);
        return rows.isEmpty() ? null : json.row(rows.get(0));
    }

    public String createUser(
        String tenantId,
        String actorId,
        UserSaveRequest request,
        String primaryRole,
        String passwordHash,
        String status
    ) {
        return jdbc.queryForObject("""
            INSERT INTO users (
              tenant_id, username, email, display_name, role_code, password_hash, status, created_by, updated_by
            )
            VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?::uuid, ?::uuid)
            RETURNING id::text
            """,
            String.class,
            tenantId,
            request.username(),
            request.email(),
            request.displayName(),
            primaryRole,
            passwordHash,
            status,
            actorId,
            actorId
        );
    }

    public void updateUser(String tenantId, String actorId, String userId, UserSaveRequest request, String primaryRole, String passwordHash) {
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
            primaryRole,
            passwordHash,
            nullToBlank(request.status()),
            actorId,
            tenantId,
            userId
        );
    }

    public void deleteUser(String tenantId, String actorId, String userId) {
        jdbc.update("""
            UPDATE users
            SET status = 'DISABLED',
                deleted_at = now(),
                deleted_by = ?::uuid,
                updated_by = ?::uuid
            WHERE tenant_id = ?::uuid
              AND id = ?::uuid
              AND deleted_at IS NULL
            """, actorId, actorId, tenantId, userId);
    }

    public void deleteUserRoles(String tenantId, String userId) {
        jdbc.update("""
            DELETE FROM user_roles
            WHERE tenant_id = ?::uuid
              AND user_id = ?::uuid
            """, tenantId, userId);
    }

    public boolean grantUserRole(String tenantId, String actorId, String userId, String roleCode) {
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
            tenantId,
            roleCode,
            tenantId,
            userId,
            actorId
        );
        return !inserted.isEmpty();
    }

    public List<Map<String, Object>> findRoles(String tenantId) {
        return json.rows(jdbc.queryForList("""
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
            """, tenantId));
    }

    public Map<String, Object> findRole(String tenantId, String roleId) {
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
            """, tenantId, roleId);
        return rows.isEmpty() ? null : json.row(rows.get(0));
    }

    public String createRole(String tenantId, String actorId, RoleSaveRequest request, String status, String metadata) {
        return jdbc.queryForObject("""
            INSERT INTO roles (tenant_id, code, name, description, status, created_by, updated_by, metadata)
            VALUES (?::uuid, ?, ?, ?, ?, ?::uuid, ?::uuid, ?::jsonb)
            RETURNING id::text
            """,
            String.class,
            tenantId,
            request.code(),
            request.name(),
            request.description(),
            status,
            actorId,
            actorId,
            metadata
        );
    }

    public void updateRole(String tenantId, String actorId, String roleId, RoleSaveRequest request, String metadata) {
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
            metadata,
            actorId,
            tenantId,
            roleId
        );
    }

    public Boolean isSystemRole(String tenantId, String roleId) {
        return jdbc.queryForObject("""
            SELECT system_role
            FROM roles
            WHERE tenant_id = ?::uuid AND id = ?::uuid
            """, Boolean.class, tenantId, roleId);
    }

    public void deleteRole(String tenantId, String actorId, String roleId) {
        jdbc.update("""
            UPDATE roles
            SET status = 'ARCHIVED',
                deleted_at = now(),
                deleted_by = ?::uuid,
                updated_by = ?::uuid
            WHERE tenant_id = ?::uuid
              AND id = ?::uuid
              AND deleted_at IS NULL
            """, actorId, actorId, tenantId, roleId);
    }

    public void deleteRolePermissions(String tenantId, String roleId) {
        jdbc.update("""
            DELETE FROM role_permissions
            WHERE tenant_id = ?::uuid
              AND role_id = ?::uuid
            """, tenantId, roleId);
    }

    public boolean grantRolePermission(String tenantId, String roleId, String permissionCode) {
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
            tenantId,
            permissionCode,
            tenantId,
            roleId
        );
        return !inserted.isEmpty();
    }

    public List<Map<String, Object>> findPermissions(String tenantId) {
        return json.rows(jdbc.queryForList("""
            SELECT id, code, name, resource, action, description, status, created_at, updated_at
            FROM permissions
            WHERE tenant_id = ?::uuid
              AND deleted_at IS NULL
            ORDER BY resource, action, code
            """, tenantId));
    }

    public List<Map<String, Object>> findAuditLogs(String tenantId, String entityType, String action, int limit, int offset) {
        return json.rows(jdbc.queryForList("""
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
            tenantId,
            entityType,
            entityType,
            action,
            action,
            limit,
            offset
        ));
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }
}

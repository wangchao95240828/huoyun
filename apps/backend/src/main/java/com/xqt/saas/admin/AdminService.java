package com.xqt.saas.admin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.xqt.saas.auth.AuthPrincipal;
import com.xqt.saas.auth.PasswordHasher;
import com.xqt.saas.common.ApiException;
import com.xqt.saas.common.AuditService;
import com.xqt.saas.common.CommandResponse;
import com.xqt.saas.common.ItemResponse;
import com.xqt.saas.common.JsonSupport;
import com.xqt.saas.common.ListResponse;
import com.xqt.saas.common.PageResponse;
import com.xqt.saas.common.RequestContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String DEFAULT_ROLE = "OPERATOR";
    private static final String STATUS_ACTIVE = "ACTIVE";

    private final AdminRepository repository;
    private final RequestContext context;
    private final JsonSupport json;
    private final AuditService auditService;
    private final PasswordHasher passwordHasher;

    public AdminService(
        AdminRepository repository,
        RequestContext context,
        JsonSupport json,
        AuditService auditService,
        PasswordHasher passwordHasher
    ) {
        this.repository = repository;
        this.context = context;
        this.json = json;
        this.auditService = auditService;
        this.passwordHasher = passwordHasher;
    }

    @Transactional(readOnly = true)
    public ListResponse<UserView> users(AuthPrincipal auth) {
        context.setTenant(auth);
        List<UserView> items = repository.findUsers(auth.tenantId()).stream().map(this::userView).toList();
        return new ListResponse<>(items);
    }

    @Transactional(rollbackFor = Exception.class)
    public ItemResponse<UserView> createUser(AuthPrincipal auth, UserSaveRequest request) {
        context.setTenant(auth);
        validateUserRequest(auth, request, null);
        if (isBlank(request.password())) {
            throw ApiException.badRequest("请输入初始密码");
        }

        String userId = repository.createUser(
            auth.tenantId(),
            auth.userId(),
            request,
            primaryRole(request.roleCodes()),
            passwordHasher.hash(request.password()),
            nonBlank(request.status(), STATUS_ACTIVE)
        );
        replaceUserRoles(auth, userId, roleCodesOrPrimary(request.roleCodes()));
        Map<String, Object> after = requireUser(auth, userId);
        auditService.log(auth, "user", userId, "CREATE", null, after);
        return new ItemResponse<>(userView(after));
    }

    @Transactional(rollbackFor = Exception.class)
    public ItemResponse<UserView> updateUser(AuthPrincipal auth, String userId, UserSaveRequest request) {
        context.setTenant(auth);
        if (request == null) {
            throw ApiException.badRequest("request body is required");
        }

        Map<String, Object> before = repository.findUser(auth.tenantId(), userId);
        if (before == null) {
            throw ApiException.notFound("找不到该用户");
        }
        validateUserRequest(auth, request, before);

        String passwordHash = isBlank(request.password()) ? null : passwordHasher.hash(request.password());
        String primaryRole = request.roleCodes() == null || request.roleCodes().isEmpty() ? "" : primaryRole(request.roleCodes());
        repository.updateUser(auth.tenantId(), auth.userId(), userId, request, primaryRole, passwordHash);
        if (request.roleCodes() != null) {
            replaceUserRoles(auth, userId, roleCodesOrPrimary(request.roleCodes()));
        }

        Map<String, Object> after = requireUser(auth, userId);
        auditService.log(auth, "user", userId, "UPDATE", before, after);
        return new ItemResponse<>(userView(after));
    }

    @Transactional(rollbackFor = Exception.class)
    public CommandResponse deleteUser(AuthPrincipal auth, String userId) {
        context.setTenant(auth);
        if (auth.userId().equals(userId)) {
            throw ApiException.badRequest("不能删除当前登录用户");
        }
        Map<String, Object> before = repository.findUser(auth.tenantId(), userId);
        if (before == null) {
            throw ApiException.notFound("找不到该用户");
        }
        // ACC User.php L721: 对方等级不比你低，你无法删除对方
        Integer myGrade = currentUserGrade(auth);
        Integer targetGrade = parseGrade(before.get("user_grade"));
        if (myGrade != null && targetGrade != null && targetGrade >= myGrade) {
            throw ApiException.badRequest("对方等级不比你低，你无法删除对方");
        }

        repository.deleteUser(auth.tenantId(), auth.userId(), userId);
        auditService.log(auth, "user", userId, "DELETE", before, Map.of("id", userId, "deleted", true));
        return CommandResponse.ok();
    }

    /**
     * ACC User.php 全套用户保存校验：密码 / 等级层级 / 绑定互斥。
     * before=null 时为创建场景。
     */
    private void validateUserRequest(AuthPrincipal auth, UserSaveRequest req, Map<String, Object> before) {
        // ACC L181/L505: 密码长度 >= 5
        if (!isBlank(req.password())) {
            if (req.password().length() < 5) {
                throw ApiException.badRequest("密码长度要大于 5");
            }
            // ACC L185/L517: 两次密码一致
            if (req.confirmPassword() != null && !req.password().equals(req.confirmPassword())) {
                throw ApiException.badRequest("两次输入的密码不一致");
            }
        }
        // ACC L536/L539/L542: 绑定互斥
        int boundCount = 0;
        if (!isBlank(req.boundCustomerId())) boundCount++;
        if (!isBlank(req.boundSupplierId())) boundCount++;
        if (!isBlank(req.boundEmployeeId())) boundCount++;
        if (boundCount > 1) {
            if (!isBlank(req.boundEmployeeId()) && !isBlank(req.boundCustomerId())) {
                throw ApiException.badRequest("不能同时绑定员工和客户");
            }
            if (!isBlank(req.boundEmployeeId()) && !isBlank(req.boundSupplierId())) {
                throw ApiException.badRequest("不能同时绑定员工和服务商");
            }
            if (!isBlank(req.boundCustomerId()) && !isBlank(req.boundSupplierId())) {
                throw ApiException.badRequest("不能同时绑定客户和服务商");
            }
        }
        // ACC L532/L601/L615: 只能设置比自己低的等级
        Integer myGrade = currentUserGrade(auth);
        if (req.grade() != null && myGrade != null && req.grade() >= myGrade) {
            throw ApiException.badRequest("你只能设置比你低的等级");
        }
        // ACC L147/L619: 不能修改等级 >= 你的用户
        if (before != null) {
            Integer targetGrade = parseGrade(before.get("user_grade"));
            if (myGrade != null && targetGrade != null && targetGrade >= myGrade
                && !auth.userId().equals((String) before.get("id"))) {
                throw ApiException.badRequest("该用户等级不比你低，你无法修改其资料");
            }
        }
    }

    private Integer currentUserGrade(AuthPrincipal auth) {
        try {
            Map<String, Object> me = repository.findUser(auth.tenantId(), auth.userId());
            return me == null ? null : parseGrade(me.get("user_grade"));
        } catch (Exception ex) { return null; }
    }

    private Integer parseGrade(Object raw) {
        if (raw == null) return null;
        try { return Integer.parseInt(raw.toString()); } catch (Exception ex) { return null; }
    }

    @Transactional(readOnly = true)
    public ListResponse<RoleView> roles(AuthPrincipal auth) {
        context.setTenant(auth);
        List<RoleView> items = repository.findRoles(auth.tenantId()).stream().map(this::roleView).toList();
        return new ListResponse<>(items);
    }

    @Transactional(rollbackFor = Exception.class)
    public ItemResponse<RoleView> createRole(AuthPrincipal auth, RoleSaveRequest request) {
        context.setTenant(auth);
        String roleId = repository.createRole(
            auth.tenantId(),
            auth.userId(),
            request,
            nonBlank(request.status(), STATUS_ACTIVE),
            json.toJson(request.metadata())
        );
        if (request.permissionCodes() != null) {
            replaceRolePermissions(auth, roleId, request.permissionCodes());
        }
        Map<String, Object> after = requireRole(auth, roleId);
        auditService.log(auth, "role", roleId, "CREATE", null, after);
        return new ItemResponse<>(roleView(after));
    }

    @Transactional(rollbackFor = Exception.class)
    public ItemResponse<RoleView> updateRole(AuthPrincipal auth, String roleId, RoleSaveRequest request) {
        context.setTenant(auth);
        if (request == null) {
            throw ApiException.badRequest("request body is required");
        }
        Map<String, Object> before = repository.findRole(auth.tenantId(), roleId);
        if (before == null) {
            throw ApiException.notFound("role not found");
        }

        repository.updateRole(auth.tenantId(), auth.userId(), roleId, request, json.toJson(request.metadata()));
        if (request.permissionCodes() != null) {
            replaceRolePermissions(auth, roleId, request.permissionCodes());
        }
        Map<String, Object> after = requireRole(auth, roleId);
        auditService.log(auth, "role", roleId, "UPDATE", before, after);
        return new ItemResponse<>(roleView(after));
    }

    @Transactional(rollbackFor = Exception.class)
    public ItemResponse<RoleView> setRolePermissions(AuthPrincipal auth, String roleId, RolePermissionsRequest request) {
        context.setTenant(auth);
        Map<String, Object> before = repository.findRole(auth.tenantId(), roleId);
        if (before == null) {
            throw ApiException.notFound("role not found");
        }
        List<String> permissionCodes = request == null ? List.of() : safeList(request.permissionCodes());
        replaceRolePermissions(auth, roleId, permissionCodes);
        Map<String, Object> after = requireRole(auth, roleId);
        auditService.log(auth, "role", roleId, "SET_PERMISSIONS", before, after);
        return new ItemResponse<>(roleView(after));
    }

    @Transactional(rollbackFor = Exception.class)
    public CommandResponse deleteRole(AuthPrincipal auth, String roleId) {
        context.setTenant(auth);
        Map<String, Object> before = repository.findRole(auth.tenantId(), roleId);
        if (before == null) {
            throw ApiException.notFound("role not found");
        }
        if (Boolean.TRUE.equals(repository.isSystemRole(auth.tenantId(), roleId))) {
            throw ApiException.badRequest("system role cannot be deleted");
        }
        repository.deleteRole(auth.tenantId(), auth.userId(), roleId);
        auditService.log(auth, "role", roleId, "DELETE", before, Map.of("id", roleId, "deleted", true));
        return CommandResponse.ok();
    }

    @Transactional(readOnly = true)
    public ListResponse<PermissionView> permissions(AuthPrincipal auth) {
        context.setTenant(auth);
        List<PermissionView> items = repository.findPermissions(auth.tenantId()).stream().map(this::permissionView).toList();
        return new ListResponse<>(items);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogView> auditLogs(AuthPrincipal auth, String entityType, String action, int page, int pageSize) {
        context.setTenant(auth);
        int safePage = Math.max(1, page);
        int limit = Math.max(1, Math.min(pageSize <= 0 ? DEFAULT_PAGE_SIZE : pageSize, MAX_PAGE_SIZE));
        int offset = (safePage - 1) * limit;
        List<AuditLogView> items = repository
            .findAuditLogs(auth.tenantId(), emptyToNull(entityType), emptyToNull(action), limit, offset)
            .stream()
            .map(this::auditLogView)
            .toList();
        return new PageResponse<>(items, safePage, limit);
    }

    private Map<String, Object> requireUser(AuthPrincipal auth, String userId) {
        Map<String, Object> user = repository.findUser(auth.tenantId(), userId);
        if (user == null) {
            throw ApiException.notFound("user not found");
        }
        return user;
    }

    private Map<String, Object> requireRole(AuthPrincipal auth, String roleId) {
        Map<String, Object> role = repository.findRole(auth.tenantId(), roleId);
        if (role == null) {
            throw ApiException.notFound("role not found");
        }
        return role;
    }

    private UserView userView(Map<String, Object> row) {
        Map<String, Object> mapped = json.row(row);
        return new UserView(
            text(mapped, "id"),
            text(mapped, "username"),
            text(mapped, "email"),
            text(mapped, "display_name"),
            text(mapped, "role_code"),
            text(mapped, "status"),
            text(mapped, "last_login_at"),
            text(mapped, "created_at"),
            text(mapped, "updated_at"),
            textList(mapped.get("roles"))
        );
    }

    private RoleView roleView(Map<String, Object> row) {
        Map<String, Object> mapped = json.row(row);
        return new RoleView(
            text(mapped, "id"),
            text(mapped, "code"),
            text(mapped, "name"),
            text(mapped, "description"),
            Boolean.TRUE.equals(mapped.get("system_role")),
            text(mapped, "status"),
            text(mapped, "created_at"),
            text(mapped, "updated_at"),
            textList(mapped.get("permissions"))
        );
    }

    private PermissionView permissionView(Map<String, Object> row) {
        Map<String, Object> mapped = json.row(row);
        return new PermissionView(
            text(mapped, "id"),
            text(mapped, "code"),
            text(mapped, "name"),
            text(mapped, "resource"),
            text(mapped, "action"),
            text(mapped, "description"),
            text(mapped, "status"),
            text(mapped, "created_at"),
            text(mapped, "updated_at")
        );
    }

    private AuditLogView auditLogView(Map<String, Object> row) {
        Map<String, Object> mapped = json.row(row);
        return new AuditLogView(
            text(mapped, "id"),
            text(mapped, "entity_type"),
            text(mapped, "entity_id"),
            text(mapped, "action"),
            mapped.get("before_data"),
            mapped.get("after_data"),
            text(mapped, "created_at"),
            text(mapped, "actor_name"),
            text(mapped, "actor_username")
        );
    }

    private void replaceUserRoles(AuthPrincipal auth, String userId, List<String> roleCodes) {
        repository.deleteUserRoles(auth.tenantId(), userId);
        for (String roleCode : roleCodes) {
            if (!repository.grantUserRole(auth.tenantId(), auth.userId(), userId, roleCode)) {
                throw ApiException.badRequest("role not found: " + roleCode);
            }
        }
    }

    private void replaceRolePermissions(AuthPrincipal auth, String roleId, List<String> permissionCodes) {
        repository.deleteRolePermissions(auth.tenantId(), roleId);
        for (String permissionCode : safeList(permissionCodes)) {
            if (!repository.grantRolePermission(auth.tenantId(), roleId, permissionCode)) {
                throw ApiException.badRequest("permission not found: " + permissionCode);
            }
        }
    }

    private List<String> roleCodesOrPrimary(List<String> roleCodes) {
        return roleCodes == null || roleCodes.isEmpty() ? List.of(DEFAULT_ROLE) : safeList(roleCodes);
    }

    private String primaryRole(List<String> roleCodes) {
        return roleCodes == null || roleCodes.isEmpty() ? DEFAULT_ROLE : roleCodes.get(0);
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

    private String emptyToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private String text(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? "" : value.toString();
    }

    private List<String> textList(Object value) {
        if (value instanceof List<?> values) {
            return values.stream().map(String::valueOf).toList();
        }
        if (value instanceof String[] values) {
            return List.of(values);
        }
        return List.of();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

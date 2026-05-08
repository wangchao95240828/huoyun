package com.xqt.saas.admin;

import com.xqt.saas.auth.AuthPrincipal;
import com.xqt.saas.common.ApiResponse;
import com.xqt.saas.common.CommandResponse;
import com.xqt.saas.common.ItemResponse;
import com.xqt.saas.common.ListResponse;
import com.xqt.saas.common.PageResponse;
import com.xqt.saas.common.RequestContext;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService adminService;
    private final RequestContext context;

    public AdminController(AdminService adminService, RequestContext context) {
        this.adminService = adminService;
        this.context = context;
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('admin.user.read')")
    public ApiResponse<ListResponse<UserView>> users(Authentication authentication) {
        return ApiResponse.ok(adminService.users(principal(authentication)));
    }

    @PostMapping("/users")
    @PreAuthorize("hasAuthority('admin.user.write')")
    public ApiResponse<ItemResponse<UserView>> createUser(
        Authentication authentication,
        @Valid @RequestBody UserSaveRequest request
    ) {
        return ApiResponse.ok(adminService.createUser(principal(authentication), request));
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasAuthority('admin.user.write')")
    public ApiResponse<ItemResponse<UserView>> updateUser(
        Authentication authentication,
        @PathVariable("id") String userId,
        @RequestBody UserSaveRequest request
    ) {
        return ApiResponse.ok(adminService.updateUser(principal(authentication), userId, request));
    }

    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasAuthority('admin.user.write')")
    public ApiResponse<CommandResponse> deleteUser(Authentication authentication, @PathVariable("id") String userId) {
        return ApiResponse.ok(adminService.deleteUser(principal(authentication), userId));
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('admin.role.read')")
    public ApiResponse<ListResponse<RoleView>> roles(Authentication authentication) {
        return ApiResponse.ok(adminService.roles(principal(authentication)));
    }

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('admin.role.write')")
    public ApiResponse<ItemResponse<RoleView>> createRole(
        Authentication authentication,
        @Valid @RequestBody RoleSaveRequest request
    ) {
        return ApiResponse.ok(adminService.createRole(principal(authentication), request));
    }

    @PutMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('admin.role.write')")
    public ApiResponse<ItemResponse<RoleView>> updateRole(
        Authentication authentication,
        @PathVariable("id") String roleId,
        @RequestBody RoleSaveRequest request
    ) {
        return ApiResponse.ok(adminService.updateRole(principal(authentication), roleId, request));
    }

    @PutMapping("/roles/{id}/permissions")
    @PreAuthorize("hasAuthority('admin.role.write')")
    public ApiResponse<ItemResponse<RoleView>> setRolePermissions(
        Authentication authentication,
        @PathVariable("id") String roleId,
        @RequestBody RolePermissionsRequest request
    ) {
        return ApiResponse.ok(adminService.setRolePermissions(principal(authentication), roleId, request));
    }

    @DeleteMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('admin.role.write')")
    public ApiResponse<CommandResponse> deleteRole(Authentication authentication, @PathVariable("id") String roleId) {
        return ApiResponse.ok(adminService.deleteRole(principal(authentication), roleId));
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAnyAuthority('admin.role.read','admin.permission.read')")
    public ApiResponse<ListResponse<PermissionView>> permissions(Authentication authentication) {
        return ApiResponse.ok(adminService.permissions(principal(authentication)));
    }

    @GetMapping("/audit-logs")
    @PreAuthorize("hasAnyAuthority('admin.audit.read','ROLE_ADMIN')")
    public ApiResponse<PageResponse<AuditLogView>> auditLogs(
        Authentication authentication,
        @RequestParam(value = "entityType", required = false) String entityType,
        @RequestParam(value = "action", required = false) String action,
        @RequestParam(value = "page", required = false, defaultValue = "1") int page,
        @RequestParam(value = "pageSize", required = false, defaultValue = "50") int pageSize
    ) {
        return ApiResponse.ok(adminService.auditLogs(principal(authentication), entityType, action, page, pageSize));
    }

    private AuthPrincipal principal(Authentication authentication) {
        return context.principal(authentication);
    }
}

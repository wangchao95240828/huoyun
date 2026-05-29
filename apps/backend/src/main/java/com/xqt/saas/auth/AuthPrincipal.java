package com.xqt.saas.auth;

import java.util.List;

public record AuthPrincipal(
    String userId,
    String tenantId,
    String tenantCode,
    String username,
    String displayName,
    List<String> roles,
    List<String> permissions,
    long exp,
    String jti,
    String branchId
) {
    public AuthPrincipal {
        roles = roles == null ? List.of() : List.copyOf(roles);
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }

    /** 兼容旧 9 参构造（无 branchId）；现有测试与历史代码沿用此路径。 */
    public AuthPrincipal(String userId, String tenantId, String tenantCode,
                          String username, String displayName,
                          List<String> roles, List<String> permissions,
                          long exp, String jti) {
        this(userId, tenantId, tenantCode, username, displayName,
            roles, permissions, exp, jti, null);
    }
}

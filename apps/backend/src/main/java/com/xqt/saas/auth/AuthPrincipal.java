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
    String jti
) {
    public AuthPrincipal {
        roles = roles == null ? List.of() : List.copyOf(roles);
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }
}

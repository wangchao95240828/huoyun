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
}

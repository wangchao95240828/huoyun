package com.xqt.saas.admin;

import java.util.List;

public record UserView(
    String id,
    String username,
    String email,
    String displayName,
    String roleCode,
    String status,
    String lastLoginAt,
    String createdAt,
    String updatedAt,
    List<String> roles
) {
    public UserView {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
}

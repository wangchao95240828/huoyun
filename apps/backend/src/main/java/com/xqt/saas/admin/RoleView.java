package com.xqt.saas.admin;

import java.util.List;

public record RoleView(
    String id,
    String code,
    String name,
    String description,
    boolean systemRole,
    String status,
    String createdAt,
    String updatedAt,
    List<String> permissions
) {
    public RoleView {
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }
}

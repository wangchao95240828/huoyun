package com.xqt.saas.admin;

import java.util.List;

public record RolePermissionsRequest(List<String> permissionCodes) {
    public RolePermissionsRequest {
        permissionCodes = permissionCodes == null ? List.of() : List.copyOf(permissionCodes);
    }
}

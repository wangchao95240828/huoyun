package com.xqt.saas.admin;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;

public record RoleSaveRequest(
    @NotBlank String code,
    @NotBlank String name,
    String description,
    String status,
    List<String> permissionCodes,
    Map<String, Object> metadata
) {
    public RoleSaveRequest {
        permissionCodes = permissionCodes == null ? List.of() : List.copyOf(permissionCodes);
        metadata = metadata == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    public Map<String, Object> metadata() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}

package com.xqt.saas.admin;

import java.util.List;

import jakarta.validation.constraints.NotBlank;

public record UserSaveRequest(
    @NotBlank String username,
    @NotBlank String email,
    @NotBlank String displayName,
    String password,
    String status,
    List<String> roleCodes
) {
    public UserSaveRequest {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
    }
}

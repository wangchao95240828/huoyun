package com.xqt.saas.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    String tenantCode,
    @NotBlank String username,
    @NotBlank String password
) {
}

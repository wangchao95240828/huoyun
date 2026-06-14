package com.xqt.saas.admin;

import java.util.List;

import jakarta.validation.constraints.NotBlank;

public record UserSaveRequest(
    @NotBlank String username,
    @NotBlank String email,
    @NotBlank String displayName,
    String password,
    String confirmPassword,
    String status,
    List<String> roleCodes,
    // ACC User.php 字段：等级 + 绑定（互斥三选一）
    Integer grade,
    String boundCustomerId,
    String boundSupplierId,
    String boundEmployeeId
) {
    public UserSaveRequest {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
    }
}

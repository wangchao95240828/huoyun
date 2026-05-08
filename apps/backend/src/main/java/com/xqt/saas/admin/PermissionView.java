package com.xqt.saas.admin;

public record PermissionView(
    String id,
    String code,
    String name,
    String resource,
    String action,
    String description,
    String status,
    String createdAt,
    String updatedAt
) {
}

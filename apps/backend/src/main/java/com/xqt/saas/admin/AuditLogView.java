package com.xqt.saas.admin;

public record AuditLogView(
    String id,
    String entityType,
    String entityId,
    String action,
    Object beforeData,
    Object afterData,
    String createdAt,
    String actorName,
    String actorUsername
) {
}

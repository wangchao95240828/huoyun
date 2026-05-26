package com.xqt.saas.framework.audit;

/**
 * 审计事件动作。对应 ACC `audit-biz` / `undo-biz` / `batch-audit` 端点 + 普通 CRUD。
 */
public enum AuditAction {
    AUDIT,
    UNDO_AUDIT,
    BATCH_AUDIT,
    CREATE,
    UPDATE,
    DELETE
}

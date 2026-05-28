package com.xqt.saas.framework.audit;

/**
 * 审核副作用钩子。
 *
 * 复刻 ACC「审核通过即入账」的行为：单据审核后触发对应的财务/费用/利润副作用
 * （如调账/退款/返利审核通过后写资金流水）。
 *
 * AuditService 在 audit / undoAudit 成功后，遍历所有注册的 side effect，
 * 对 supports() 命中的执行 onAudited / onUndone。side effect 由 Spring 自动注入，
 * 无实现时为空列表，不影响纯审核流程。
 *
 * 实现要点：
 *  - 与审核在同一事务内执行，抛异常会回滚审核（保证审核与副作用原子）。
 *  - 找不到必要业务数据（如资金账户）时应静默跳过，不应阻断审核。
 */
public interface AuditSideEffect {
    /** 是否关心该底层表。 */
    boolean supports(String table);

    /** 审核通过后执行。 */
    void onAudited(String table, String entityId, String tenantId, String actorName);

    /** 反审核后执行（冲正）。默认不做事。 */
    default void onUndone(String table, String entityId, String tenantId, String actorName) {
    }
}

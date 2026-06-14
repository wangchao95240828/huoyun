package com.xqt.saas.framework.approval;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 多级审批服务 — 对齐 ACC 多级审批流。
 *
 * 概念：
 *  - "审批策略 (approval_policies)" 定义 resource+action 需要的审批人数 + 阈值
 *  - "审批请求 (approval_requests)" 是某具体单据要求审批的实例
 *  - "审批决定 (approval_decisions)" 是各级审批人的批示
 *
 * 用法：
 *   POST /api/admin/approval/policies   → 配置策略
 *   POST /api/admin/approval/requests   → 业务侧提交请求
 *   POST /api/admin/approval/{id}/decide → 审批人批示
 *
 * 业务规则：
 *  - amount >= 50k 自动需要 2 级审批
 *  - amount >= 500k 自动需要 3 级审批
 *  - 同审批人不能重复批示
 *  - 任一拒绝整请求失败
 *  - 收齐 min_approvals 个 APPROVE 则整请求 APPROVED
 */
@Service
public class MultiStageApprovalService {
    private static final BigDecimal THRESHOLD_2 = new BigDecimal("50000");
    private static final BigDecimal THRESHOLD_3 = new BigDecimal("500000");

    private final JdbcTemplate jdbc;

    public MultiStageApprovalService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 根据金额自动决定需要的审批级数。 */
    public int requiredStages(BigDecimal amount) {
        if (amount == null) return 1;
        if (amount.compareTo(THRESHOLD_3) >= 0) return 3;
        if (amount.compareTo(THRESHOLD_2) >= 0) return 2;
        return 1;
    }

    @Transactional(rollbackFor = Exception.class)
    public String submitRequest(String resource, String action, String resourceId,
                                  BigDecimal amount, String currency, String tenantId, String userId) {
        int stages = requiredStages(amount);
        // target_id 是单 uuid，service 接 comma-separated → 落 resource_id（text）
        String requestId = jdbc.queryForObject("""
            INSERT INTO approval_requests (
              tenant_id, resource, action, target_id, resource_id, status, required_count,
              requester_id, payload, reason
            ) VALUES (?::uuid, ?, ?, NULL, ?, 'PENDING', ?, ?::uuid,
              jsonb_build_object('amount', ?::numeric, 'currency', ?::text),
              '自动触发')
            RETURNING id::text
            """, String.class, tenantId, resource, action, resourceId, stages, userId,
                 amount, currency);
        return requestId;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> decide(String requestId, String decision, String comment,
                                       String tenantId, String userId) {
        if (!"APPROVE".equals(decision) && !"REJECT".equals(decision)) {
            throw ApiException.badRequest("decision 必须为 APPROVE 或 REJECT");
        }
        Map<String, Object> req;
        try {
            req = jdbc.queryForMap("""
                SELECT status, required_count, requester_id::text AS requester_id, resource, action
                  FROM approval_requests WHERE id = ?::uuid AND tenant_id = ?::uuid
                """, requestId, tenantId);
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            throw ApiException.notFound("找不到该审批请求");
        }
        if (!"PENDING".equals(req.get("status"))) {
            throw ApiException.badRequest("该审批请求已 " + req.get("status") + "，不能再批示");
        }
        if (userId.equals(req.get("requester_id"))) {
            throw ApiException.badRequest("不能审批自己提交的请求");
        }
        // 同审批人不能重复
        Integer dup = jdbc.queryForObject(
            "SELECT count(*) FROM approval_decisions WHERE request_id = ?::uuid AND decided_by = ?::uuid",
            Integer.class, requestId, userId);
        if (dup != null && dup > 0) {
            throw ApiException.badRequest("您已批示过该请求");
        }
        // 插入决定
        jdbc.update("""
            INSERT INTO approval_decisions (request_id, decided_by, decision, comment)
            VALUES (?::uuid, ?::uuid, ?, ?)
            """, requestId, userId, decision, comment);

        // 判定整请求状态
        if ("REJECT".equals(decision)) {
            jdbc.update("UPDATE approval_requests SET status='REJECTED', resolved_at=now() WHERE id=?::uuid",
                requestId);
            return Map.of("status", "REJECTED", "by", userId);
        }
        int requiredCount = ((Number) req.get("required_count")).intValue();
        Integer approveCount = jdbc.queryForObject("""
            SELECT count(*) FROM approval_decisions
             WHERE request_id = ?::uuid AND decision = 'APPROVE'
            """, Integer.class, requestId);
        if (approveCount != null && approveCount >= requiredCount) {
            jdbc.update("UPDATE approval_requests SET status='APPROVED', resolved_at=now() WHERE id=?::uuid",
                requestId);
            return Map.of("status", "APPROVED", "stagesCompleted", approveCount);
        }
        return Map.of("status", "PENDING",
            "approvalsSoFar", approveCount,
            "required", requiredCount);
    }

    public List<Map<String, Object>> listPending(String tenantId) {
        return jdbc.queryForList("""
            SELECT id::text, resource, action, resource_id, required_count,
                   payload, requester_id::text AS requester_id, created_at,
                   (SELECT count(*) FROM approval_decisions WHERE request_id = approval_requests.id) AS decision_count
              FROM approval_requests
             WHERE tenant_id = ?::uuid AND status = 'PENDING'
             ORDER BY created_at DESC LIMIT 100
            """, tenantId);
    }
}

package com.xqt.saas.framework.approval;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.xqt.saas.auth.AuthPrincipal;
import com.xqt.saas.common.ApiException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审批流端点 — 对齐 ACC 多级审批。
 *
 *   POST /api/admin/approval/requests             — 业务侧创建请求
 *   POST /api/admin/approval/{id}/decide          — 审批人决定
 *   GET  /api/admin/approval/pending              — 待审清单
 */
@RestController
@RequestMapping("/api/admin/approval")
public class ApprovalController {
    private final MultiStageApprovalService service;

    public ApprovalController(MultiStageApprovalService service) {
        this.service = service;
    }

    @PostMapping("/requests")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> submit(@RequestBody Map<String, Object> body, Authentication auth) {
        AuthPrincipal p = (AuthPrincipal) auth.getPrincipal();
        String resource = (String) body.get("resource");
        String action = (String) body.get("action");
        String resourceId = (String) body.get("resourceId");
        if (resource == null || action == null || resourceId == null) {
            throw ApiException.badRequest("resource/action/resourceId 必填");
        }
        BigDecimal amount = body.get("amount") instanceof Number n
            ? new BigDecimal(n.toString()) : null;
        String currency = body.get("currency") == null ? "CNY" : body.get("currency").toString();
        String id = service.submitRequest(resource, action, resourceId, amount, currency,
            p.tenantId(), p.userId());
        return Map.of("id", id, "requiredStages", service.requiredStages(amount));
    }

    @PostMapping("/{id}/decide")
    @PreAuthorize("hasAuthority('finance.adjust.approve') or hasRole('ADMIN')")
    public Map<String, Object> decide(@PathVariable String id,
                                       @RequestBody Map<String, Object> body,
                                       Authentication auth) {
        AuthPrincipal p = (AuthPrincipal) auth.getPrincipal();
        String decision = body.get("decision") == null ? null : body.get("decision").toString();
        String comment = body.get("comment") == null ? null : body.get("comment").toString();
        return service.decide(id, decision, comment, p.tenantId(), p.userId());
    }

    @GetMapping("/pending")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> pending(Authentication auth) {
        AuthPrincipal p = (AuthPrincipal) auth.getPrincipal();
        List<Map<String, Object>> data = service.listPending(p.tenantId());
        return Map.of("data", data, "total", data.size());
    }
}

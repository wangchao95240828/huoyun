package com.xqt.saas.acc;

import java.util.List;
import java.util.Map;

import com.xqt.saas.auth.AuthPrincipal;
import com.xqt.saas.common.ApiException;
import com.xqt.saas.common.BranchAccessFilter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客户 portal 专属入口 — 对齐 ACC Online.php / OnlineCancel.php。
 *
 *   GET  /api/customer-portal/dashboard       客户余额 + 本月制单
 *   GET  /api/customer-portal/orders          自己的订单
 *   GET  /api/customer-portal/balance         自己的可打单余额
 *   POST /api/customer-portal/cancel-order    客户作废申请
 *
 * 所有端点要求 user 必须 bound_customer_id 不为空（限制为客户portal 账号）。
 */
@RestController
@RequestMapping("/api/customer-portal")
public class AccCustomerPortalController {
    private final JdbcTemplate jdbc;
    private final BranchAccessFilter branchAccess;

    public AccCustomerPortalController(JdbcTemplate jdbc, BranchAccessFilter branchAccess) {
        this.jdbc = jdbc;
        this.branchAccess = branchAccess;
    }

    private String currentCustomerId(Authentication auth) {
        AuthPrincipal p = (AuthPrincipal) auth.getPrincipal();
        try {
            String cid = jdbc.queryForObject(
                "SELECT bound_customer_id::text FROM users WHERE id = ?::uuid",
                String.class, p.userId());
            if (cid == null || cid.isBlank()) {
                throw ApiException.unauthorized("当前账号未绑定客户，无法访问 portal");
            }
            return cid;
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            throw ApiException.unauthorized("账号无效");
        }
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(Authentication auth) {
        String cid = currentCustomerId(auth);
        Map<String, Object> stats = new java.util.LinkedHashMap<>();
        // 本月制单数
        Integer monthOrders = jdbc.queryForObject("""
            SELECT count(*) FROM orders
             WHERE customer_id = ?::uuid AND created_at >= date_trunc('month', now())
            """, Integer.class, cid);
        stats.put("monthOrders", monthOrders == null ? 0 : monthOrders);
        // 余额（按 USD）
        List<Map<String, Object>> balances = jdbc.queryForList("""
            SELECT currency, balance FROM financial_accounts
             WHERE owner_type='CUSTOMER' AND owner_id = ?::uuid
            """, cid);
        stats.put("balances", balances);
        // 未支付账单
        Integer unpaidInvoices = jdbc.queryForObject("""
            SELECT count(*) FROM customer_invoices
             WHERE customer_id = ?::uuid AND status IN ('SENT', 'PARTIAL_PAID', 'PENDING')
            """, Integer.class, cid);
        stats.put("unpaidInvoices", unpaidInvoices == null ? 0 : unpaidInvoices);
        return stats;
    }

    @GetMapping("/orders")
    public Map<String, Object> myOrders(Authentication auth) {
        String cid = currentCustomerId(auth);
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT id::text, order_no, status::text, created_at, postcode,
                   item_type, materials_en, declared_value
              FROM orders WHERE customer_id = ?::uuid
             ORDER BY created_at DESC LIMIT 100
            """, cid);
        return Map.of("data", rows, "total", rows.size());
    }

    @GetMapping("/balance")
    public Map<String, Object> myBalance(Authentication auth) {
        String cid = currentCustomerId(auth);
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT currency, balance, account_name FROM financial_accounts
             WHERE owner_type='CUSTOMER' AND owner_id = ?::uuid
            """, cid);
        return Map.of("data", rows, "total", rows.size());
    }

    /** 客户提交作废申请 — 对齐 ACC OnlineCancel.php */
    @PostMapping("/cancel-order")
    public Map<String, Object> cancelOrder(@RequestBody Map<String, Object> body, Authentication auth) {
        String cid = currentCustomerId(auth);
        String orderId = (String) body.get("orderId");
        String reason = (String) body.get("reason");
        if (orderId == null || orderId.isBlank()) {
            throw ApiException.badRequest("orderId 必填");
        }
        if (reason == null || reason.isBlank()) {
            throw ApiException.badRequest("请输入作废原因");
        }
        // 校验订单是客户自己的
        Map<String, Object> ord;
        try {
            ord = jdbc.queryForMap("""
                SELECT status::text AS status, customer_id::text AS customer_id, audit_status
                  FROM orders WHERE id = ?::uuid
                """, orderId);
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            throw ApiException.notFound("快件不存在");
        }
        if (!cid.equals(ord.get("customer_id"))) {
            throw ApiException.unauthorized("您没有权限操作该快件");
        }
        String status = (String) ord.get("status");
        if ("CANCELLED".equals(status) || "COMPLETED".equals(status)) {
            throw ApiException.badRequest("该快件状态不允许作废");
        }
        if ("PENDING".equals(ord.get("audit_status"))) {
            // 可能已经申请过
            String existing = jdbc.queryForObject("""
                SELECT metadata #>> '{acc_compat,void_request_reason}' FROM orders WHERE id = ?::uuid
                """, String.class, orderId);
            if (existing != null && !existing.isBlank()) {
                throw ApiException.badRequest("该快件已有作废申请，请勿重复操作");
            }
        }
        jdbc.update("""
            UPDATE orders SET audit_status = 'PENDING',
                              metadata = coalesce(metadata, '{}'::jsonb)
                                  || jsonb_build_object('acc_compat', jsonb_build_object('void_request_reason', ?::text))
             WHERE id = ?::uuid
            """, reason, orderId);
        return Map.of("ok", true, "orderId", orderId, "voidRequested", true);
    }
}

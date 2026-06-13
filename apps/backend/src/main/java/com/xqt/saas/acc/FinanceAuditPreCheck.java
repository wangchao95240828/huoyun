package com.xqt.saas.acc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import com.xqt.saas.framework.audit.AuditSideEffect;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 财务流水审核前置校验，对齐 ACC Charge.php / Cost.php / Pay.php / Received.php 业务规则。
 *
 * 支持表：charges / customer_invoices / partner_payments
 */
@Component
public class FinanceAuditPreCheck implements AuditSideEffect {
    private final JdbcTemplate jdbc;

    public FinanceAuditPreCheck(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean supports(String table) {
        return "charges".equals(table)
            || "customer_invoices".equals(table)
            || "partner_payments".equals(table);
    }

    @Override
    public void onAudited(String table, String entityId, String tenantId, String actorName) {
        // 副作用为空：审核入账的资金流转由 financeWorkbench / settlementWorkbench 处理
    }

    @Override
    public void beforeAudit(String table, String entityId, String tenantId, String actorName) {
        switch (table) {
            case "charges" -> checkCharge(entityId);
            case "customer_invoices" -> checkInvoice(entityId);
            case "partner_payments" -> checkPartnerPayment(entityId);
        }
    }

    /**
     * ACC Charge.php 审核校验：
     * L1324 找不到绑定的订单 / L1326 找不到客户 / L1328 找不到币种 /
     * L1385 同一票快件不能两种币种。
     */
    private void checkCharge(String chargeId) {
        Map<String, Object> ch;
        try {
            ch = jdbc.queryForMap("""
                SELECT side::text AS side, amount, currency,
                       order_id::text AS order_id, customer_id::text AS customer_id,
                       shipment_id::text AS shipment_id, status::text AS status
                  FROM charges WHERE id = ?::uuid
                """, chargeId);
        } catch (EmptyResultDataAccessException ex) {
            throw ApiException.notFound("找不到该费用单");
        }
        if ("VOID".equals(ch.get("status"))) {
            throw ApiException.badRequest("已作废的费用单无法审核");
        }
        if ("AR".equals(ch.get("side"))) {
            if (ch.get("order_id") == null) {
                throw ApiException.badRequest("找不到该费用绑定的快件订单");
            }
            if (ch.get("customer_id") == null) {
                throw ApiException.badRequest("找不到快件订单绑定的客户");
            }
        }
        String currency = (String) ch.get("currency");
        if (currency == null || currency.length() != 3) {
            throw ApiException.badRequest("找不到费用结算货币");
        }
        BigDecimal amount = (BigDecimal) ch.get("amount");
        if (amount == null || amount.signum() <= 0) {
            throw ApiException.badRequest("费用金额必须大于零");
        }
        // ACC L1385: 同一票快件不能有两种不同的收费币种
        String shipmentId = (String) ch.get("shipment_id");
        String side = (String) ch.get("side");
        if (shipmentId != null) {
            List<String> otherCurrencies = jdbc.queryForList("""
                SELECT DISTINCT currency FROM charges
                 WHERE shipment_id = ?::uuid AND side = ?::charge_side
                   AND id <> ?::uuid AND status <> 'VOID'::charge_status
                """, String.class, shipmentId, side, chargeId);
            for (String other : otherCurrencies) {
                if (!currency.equals(other)) {
                    throw ApiException.badRequest(
                        "同一票快件不能有两种不同的收费币种: " + currency + " vs " + other);
                }
            }
        }
    }

    /**
     * 客户账单审核校验：找不到客户 / 找不到币种 / 金额必须大于零。
     */
    private void checkInvoice(String invoiceId) {
        Map<String, Object> inv;
        try {
            inv = jdbc.queryForMap("""
                SELECT customer_id::text AS customer_id, currency, total_amount,
                       status::text AS status
                  FROM customer_invoices WHERE id = ?::uuid
                """, invoiceId);
        } catch (EmptyResultDataAccessException ex) {
            throw ApiException.notFound("找不到该客户账单");
        }
        if ("VOID".equals(inv.get("status"))) {
            throw ApiException.badRequest("已作废的账单无法审核");
        }
        if (inv.get("customer_id") == null) {
            throw ApiException.badRequest("找不到该账单绑定的客户");
        }
        String currency = (String) inv.get("currency");
        if (currency == null || currency.length() != 3) {
            throw ApiException.badRequest("找不到账单结算货币");
        }
        BigDecimal total = (BigDecimal) inv.get("total_amount");
        if (total == null || total.signum() <= 0) {
            throw ApiException.badRequest("账单金额必须大于零");
        }
    }

    /**
     * 供应商付款审核校验：ACC Pay.php L1147 / L1194 / L1519。
     */
    private void checkPartnerPayment(String paymentId) {
        Map<String, Object> pay;
        try {
            pay = jdbc.queryForMap("""
                SELECT partner_id::text AS partner_id, currency, amount,
                       status::text AS status, financial_account_id::text AS account_id
                  FROM partner_payments WHERE id = ?::uuid
                """, paymentId);
        } catch (EmptyResultDataAccessException ex) {
            throw ApiException.notFound("找不到该付款单");
        }
        if ("VOID".equals(pay.get("status"))) {
            throw ApiException.badRequest("已作废的付款单无法审核");
        }
        if (pay.get("partner_id") == null) {
            throw ApiException.badRequest("找不到该付款单绑定的供应商");
        }
        String currency = (String) pay.get("currency");
        if (currency == null || currency.length() != 3) {
            throw ApiException.badRequest("找不到付款货币");
        }
        BigDecimal amount = (BigDecimal) pay.get("amount");
        if (amount == null || amount.signum() <= 0) {
            throw ApiException.badRequest("付款金额必须大于零");
        }
    }
}

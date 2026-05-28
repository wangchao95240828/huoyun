package com.xqt.saas.documentcharges;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.JsonSupport;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class DocumentChargeRepository {
    private final JdbcTemplate jdbc;
    private final JsonSupport json;

    public DocumentChargeRepository(JdbcTemplate jdbc, JsonSupport json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** 把订单下所有 ESTIMATED 状态的 charges 推进到 CONFIRMED。 */
    @Transactional(rollbackFor = Exception.class)
    public int confirmEstimatedCharges(String tenantId, String orderId, String side) {
        return jdbc.update("""
            UPDATE charges ch
            SET status = 'CONFIRMED'
            FROM shipments sh
            JOIN orders o ON o.customer_ref = sh.customer_ref AND o.tenant_id = sh.tenant_id
            WHERE ch.shipment_id = sh.id
              AND ch.tenant_id = ?::uuid
              AND ch.status = 'ESTIMATED'
              AND ch.side = ?::charge_side
              AND o.id = ?::uuid
            """, tenantId, side, orderId);
    }

    /**
     * 查可计入账单的 AR 费用：审核通过 + 未核销/部分核销 + 属于该客户 + 指定日期段。
     */
    public List<Map<String, Object>> findBillableArCharges(String tenantId, String customerId,
                                                            LocalDate dateFrom, LocalDate dateTo,
                                                            String currency) {
        return jdbc.queryForList("""
            SELECT ch.id::text AS id, ch.amount, ch.paid_amount, ch.unpaid_amount,
                   ch.currency, ch.shipment_id::text AS shipment_id,
                   ch.audit_status, ch.settlement_status,
                   sh.customer_id::text AS customer_id
            FROM charges ch
            LEFT JOIN shipments sh ON sh.id = ch.shipment_id
            WHERE ch.tenant_id = ?::uuid
              AND ch.side = 'AR'
              AND ch.audit_status = 'AUDITED'
              AND ch.settlement_status <> 'VOID'
              AND ch.settlement_status <> 'SETTLED'
              AND (sh.customer_id = ?::uuid OR ch.customer_id = ?::uuid)
              AND (?::char(3) IS NULL OR ch.currency = ?)
              AND ch.created_at >= ?
              AND ch.created_at < ? + interval '1 day'
            ORDER BY ch.created_at
            """, tenantId, customerId, customerId, currency, currency, dateFrom, dateTo);
    }

    public List<Map<String, Object>> findChargesByIds(String tenantId, List<String> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        String inList = "(" + String.join(",", java.util.Collections.nCopies(ids.size(), "?::uuid")) + ")";
        Object[] params = new Object[ids.size() + 1];
        params[0] = tenantId;
        for (int i = 0; i < ids.size(); i++) params[i + 1] = ids.get(i);
        return jdbc.queryForList(
            "SELECT id::text AS id, amount, paid_amount, unpaid_amount, currency, "
            + "shipment_id::text AS shipment_id, audit_status, settlement_status "
            + "FROM charges WHERE tenant_id = ?::uuid AND id IN " + inList, params);
    }

    /** 生成客户账单单号：YYYYMMDD-HHMM-XXXX 随机。 */
    public String nextInvoiceNo(String tenantId, String prefix) {
        return prefix + java.time.LocalDateTime.now().format(
            java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
    }

    @Transactional(rollbackFor = Exception.class)
    public String insertCustomerInvoice(String tenantId, String customerId, String invoiceNo,
                                        String currency, BigDecimal total, String templateCode) {
        return jdbc.queryForObject("""
            INSERT INTO customer_invoices (
              tenant_id, customer_id, invoice_no, template_code, currency,
              total_amount, status, paid_amount, unpaid_amount, writeoff_status
            ) VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, 'CONFIRMED', 0, ?, 'UNPAID')
            RETURNING id::text
            """, String.class, tenantId, customerId, invoiceNo,
            templateCode == null ? "STANDARD" : templateCode, currency, total, total);
    }

    @Transactional(rollbackFor = Exception.class)
    public void insertCustomerInvoiceLine(String tenantId, String invoiceId, String chargeId,
                                          String shipmentId, BigDecimal amount) {
        jdbc.update("""
            INSERT INTO customer_invoice_lines (tenant_id, invoice_id, charge_id, shipment_id, amount)
            VALUES (?::uuid, ?::uuid, ?::uuid, ?::uuid, ?)
            """, tenantId, invoiceId, chargeId, shipmentId, amount);
    }

    @Transactional(rollbackFor = Exception.class)
    public int markChargesBilled(String tenantId, List<String> chargeIds, String invoiceNo) {
        if (chargeIds == null || chargeIds.isEmpty()) return 0;
        String inList = "(" + String.join(",", java.util.Collections.nCopies(chargeIds.size(), "?::uuid")) + ")";
        Object[] params = new Object[chargeIds.size() + 2];
        params[0] = invoiceNo;
        params[1] = tenantId;
        for (int i = 0; i < chargeIds.size(); i++) params[i + 2] = chargeIds.get(i);
        return jdbc.update(
            "UPDATE charges SET source_ref = ? "
            + "WHERE tenant_id = ?::uuid AND id IN " + inList, params);
    }

    /** 收款核销：写 payments + 联动 invoice paid_amount/writeoff_status + charges paid_amount。 */
    @Transactional(rollbackFor = Exception.class)
    public String insertPayment(String tenantId, String customerId, BigDecimal amount,
                                String currency, String referenceNo) {
        return jdbc.queryForObject("""
            INSERT INTO payments (tenant_id, customer_id, currency, amount, received_at, reference_no)
            VALUES (?::uuid, ?::uuid, ?, ?, now(), ?)
            RETURNING id::text
            """, String.class, tenantId, customerId, currency, amount, referenceNo);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> applyPaymentToInvoice(String tenantId, String invoiceId,
                                                     BigDecimal amount) {
        Map<String, Object> inv = findInvoice(tenantId, invoiceId);
        if (inv == null) return null;
        BigDecimal currentPaid = (BigDecimal) inv.get("paid_amount");
        BigDecimal total = (BigDecimal) inv.get("total_amount");
        BigDecimal newPaid = currentPaid.add(amount);
        BigDecimal newUnpaid = total.subtract(newPaid);
        String status = newPaid.compareTo(total) >= 0 ? "PAID"
            : newPaid.signum() > 0 ? "PARTIAL" : "UNPAID";
        jdbc.update("""
            UPDATE customer_invoices
            SET paid_amount = ?, unpaid_amount = ?, writeoff_status = ?, updated_at = now()
            WHERE id = ?::uuid AND tenant_id = ?::uuid
            """, newPaid, newUnpaid.max(BigDecimal.ZERO), status, invoiceId, tenantId);
        // 按行比例分摊到 charges
        List<Map<String, Object>> lines = jdbc.queryForList("""
            SELECT charge_id::text AS charge_id, amount
            FROM customer_invoice_lines
            WHERE invoice_id = ?::uuid AND tenant_id = ?::uuid AND charge_id IS NOT NULL
            """, invoiceId, tenantId);
        for (Map<String, Object> line : lines) {
            BigDecimal lineAmount = (BigDecimal) line.get("amount");
            BigDecimal share = lineAmount.multiply(amount).divide(total, 2,
                java.math.RoundingMode.HALF_UP);
            jdbc.update("""
                UPDATE charges SET paid_amount = paid_amount + ?
                WHERE id = ?::uuid AND tenant_id = ?::uuid
                """, share, line.get("charge_id"), tenantId);
        }
        inv.put("paid_amount", newPaid);
        inv.put("unpaid_amount", newUnpaid.max(BigDecimal.ZERO));
        inv.put("writeoff_status", status);
        return inv;
    }

    public Map<String, Object> findInvoice(String tenantId, String invoiceId) {
        try {
            return jdbc.queryForMap("""
                SELECT id::text AS id, invoice_no, currency,
                       total_amount, paid_amount, unpaid_amount, writeoff_status, customer_id::text AS customer_id
                FROM customer_invoices
                WHERE tenant_id = ?::uuid AND id = ?::uuid
                """, tenantId, invoiceId);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    /** 利润聚合：按 by 维度（branch/customer/channel/month）+ 日期段。 */
    public List<Map<String, Object>> aggregateProfit(String tenantId, String groupBy,
                                                     LocalDate dateFrom, LocalDate dateTo) {
        String dim = switch (groupBy == null ? "" : groupBy) {
            case "customer" -> "coalesce(sh.customer_id::text, ch.customer_id::text)";
            case "channel" -> "sh.channel_id::text";
            case "branch" -> "ch.branch_id::text";
            case "month" -> "to_char(ch.created_at, 'YYYY-MM')";
            default -> "to_char(ch.created_at, 'YYYY-MM-DD')";
        };
        try {
            return jdbc.queryForList("""
                SELECT %s AS dim,
                       sum(CASE WHEN ch.side = 'AR' THEN ch.amount ELSE 0 END) AS revenue,
                       sum(CASE WHEN ch.side = 'AP' THEN ch.amount ELSE 0 END) AS cost,
                       sum(CASE WHEN ch.side = 'AR' THEN ch.amount ELSE -ch.amount END) AS profit,
                       count(distinct ch.shipment_id) AS shipment_count
                FROM charges ch
                LEFT JOIN shipments sh ON sh.id = ch.shipment_id
                WHERE ch.tenant_id = ?::uuid
                  AND ch.audit_status = 'AUDITED'
                  AND ch.settlement_status <> 'VOID'
                  AND ch.created_at >= ?
                  AND ch.created_at < ? + interval '1 day'
                GROUP BY %s
                ORDER BY profit DESC
                """.formatted(dim, dim), tenantId, dateFrom, dateTo);
        } catch (DataAccessException ex) {
            return List.of();
        }
    }

    /** Void：把 charge 置 VOID 并退还余额（若曾从余额扣过）。返回退款金额（用于审计）。 */
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal voidChargeAndRefund(String tenantId, String chargeId) {
        Map<String, Object> ch;
        try {
            ch = jdbc.queryForMap("""
                SELECT id::text AS id, amount, paid_amount, evidence::text AS evidence_text
                FROM charges
                WHERE tenant_id = ?::uuid AND id = ?::uuid
                """, tenantId, chargeId);
        } catch (EmptyResultDataAccessException ex) {
            return BigDecimal.ZERO;
        }
        BigDecimal refund = BigDecimal.ZERO;
        String evidenceText = (String) ch.get("evidence_text");
        if (evidenceText != null && !evidenceText.isBlank()) {
            Map<String, Object> evidence = json.fromJson(evidenceText,
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            if (Boolean.TRUE.equals(evidence.get("prepaid"))) {
                String acctId = (String) evidence.get("balance_account_id");
                if (acctId != null) {
                    BigDecimal amount = (BigDecimal) ch.get("amount");
                    jdbc.update(
                        "UPDATE financial_accounts SET balance = balance + ? WHERE id = ?::uuid",
                        amount, acctId);
                    refund = amount;
                }
            }
        }
        jdbc.update("UPDATE charges SET status = 'VOID' WHERE id = ?::uuid", chargeId);
        return refund;
    }
}

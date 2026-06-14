package com.xqt.saas.acc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import com.xqt.saas.framework.audit.AuditSideEffect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * customer_invoices + partner_payments audit → 自动写 GL 凭证。
 *
 *   customer_invoice audit:
 *     借: 应收账款 (1121)      金额
 *     贷: 主营业务收入 (5001)  金额
 *
 *   partner_payment audit:
 *     借: 应付账款 (2202)      金额
 *     贷: 银行存款 (1002)      金额
 */
@Component
public class FinanceAuditGlSideEffect implements AuditSideEffect {
    private static final Logger LOGGER = LoggerFactory.getLogger(FinanceAuditGlSideEffect.class);
    private final JdbcTemplate jdbc;

    public FinanceAuditGlSideEffect(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean supports(String table) {
        return "customer_invoices".equals(table) || "partner_payments".equals(table);
    }

    @Override
    public void onAudited(String table, String entityId, String tenantId, String actorName) {
        try {
            Boolean locked = jdbc.queryForObject(
                "SELECT acc_is_period_locked(current_date)", Boolean.class);
            if (Boolean.TRUE.equals(locked)) return;

            BigDecimal amount;
            String currency;
            String debitCode, creditCode, description;
            try {
                if ("customer_invoices".equals(table)) {
                    Map<String, Object> inv = jdbc.queryForMap(
                        "SELECT total_amount, currency FROM customer_invoices WHERE id=?::uuid", entityId);
                    amount = (BigDecimal) inv.get("total_amount");
                    currency = (String) inv.get("currency");
                    debitCode = "1121";
                    creditCode = "5001";
                    description = "Invoice audit auto-voucher";
                } else {
                    Map<String, Object> pay = jdbc.queryForMap(
                        "SELECT amount, currency FROM partner_payments WHERE id=?::uuid", entityId);
                    amount = (BigDecimal) pay.get("amount");
                    currency = (String) pay.get("currency");
                    debitCode = "2202";
                    creditCode = "1002";
                    description = "Payment audit auto-voucher";
                }
            } catch (DataAccessException ex) { return; }
            if (amount == null || amount.signum() <= 0) return;

            String debitSubId = jdbc.queryForObject(
                "SELECT id::text FROM acc_gl_subjects WHERE code = ?", String.class, debitCode);
            String creditSubId = jdbc.queryForObject(
                "SELECT id::text FROM acc_gl_subjects WHERE code = ?", String.class, creditCode);
            if (debitSubId == null || creditSubId == null) return;

            String voucherNo = "V-AUTO-" + LocalDate.now().toString().replace("-", "")
                + "-" + entityId.substring(0, 6);
            String voucherId;
            try {
                voucherId = jdbc.queryForObject("""
                    INSERT INTO acc_gl_vouchers (voucher_no, the_date, description, source_type, source_id, status)
                    VALUES (?, current_date, ?, ?, ?::uuid, 'POSTED')
                    RETURNING id::text
                    """, String.class, voucherNo, description + " (" + entityId + ")", table, entityId);
            } catch (org.springframework.dao.DuplicateKeyException ex) { return; }

            jdbc.update("""
                INSERT INTO acc_gl_voucher_lines (voucher_id, subject_id, direction, amount, currency, line_no)
                VALUES (?::uuid, ?::uuid, 'DEBIT', ?, ?, 1)
                """, voucherId, debitSubId, amount, currency);
            jdbc.update("""
                INSERT INTO acc_gl_voucher_lines (voucher_id, subject_id, direction, amount, currency, line_no)
                VALUES (?::uuid, ?::uuid, 'CREDIT', ?, ?, 2)
                """, voucherId, creditSubId, amount, currency);

            LOGGER.info("Auto-voucher {} POSTED for {} {}", voucherNo, table, entityId);
        } catch (Exception ex) {
            LOGGER.warn("FinanceAuditGlSideEffect failed: {}", ex.getMessage());
        }
    }

    @Override
    public void onUndone(String table, String entityId, String tenantId, String actorName) {
        try {
            int n = jdbc.update("""
                UPDATE acc_gl_vouchers SET status='VOID'
                 WHERE source_type = ? AND source_id = ?::uuid AND status = 'POSTED'
                """, table, entityId);
            if (n == 0) {
                LOGGER.info("No POSTED voucher to VOID for {} {}", table, entityId);
            }
        } catch (Exception ex) {
            LOGGER.warn("Voucher VOID failed for {} {}: {}", table, entityId, ex.getMessage());
        }
    }
}

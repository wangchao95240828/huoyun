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
 * charges 审核 → 自动写 GL 凭证。
 *
 *   AR audit:
 *     借: 应收账款 (1121)         金额
 *     贷: 主营业务收入 (5001)     金额
 *
 *   AP audit:
 *     借: 主营业务成本 (5401)     金额
 *     贷: 应付账款 (2202)         金额
 *
 * 仅在期间未锁定时写入；写入后凭证直接 POSTED 状态。
 * 找不到对应科目或期间已锁，静默跳过（不阻断审核）。
 */
@Component
public class ChargeAuditGlSideEffect implements AuditSideEffect {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChargeAuditGlSideEffect.class);

    private final JdbcTemplate jdbc;

    public ChargeAuditGlSideEffect(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean supports(String table) {
        return "charges".equals(table);
    }

    @Override
    public void onAudited(String table, String entityId, String tenantId, String actorName) {
        try {
            Map<String, Object> ch;
            try {
                ch = jdbc.queryForMap("""
                    SELECT side::text AS side, amount, currency,
                           order_id::text AS order_id
                      FROM charges WHERE id = ?::uuid
                    """, entityId);
            } catch (DataAccessException ex) { return; }

            BigDecimal amount = (BigDecimal) ch.get("amount");
            if (amount == null || amount.signum() <= 0) return;
            String currency = (String) ch.get("currency");
            String side = (String) ch.get("side");

            // 期间锁检查
            Boolean locked = jdbc.queryForObject(
                "SELECT acc_is_period_locked(current_date)", Boolean.class);
            if (Boolean.TRUE.equals(locked)) {
                LOGGER.info("Skip auto-voucher: current period locked");
                return;
            }

            String debitCode, creditCode;
            String description;
            if ("AR".equals(side)) {
                debitCode = "1121";  // 应收账款
                creditCode = "5001"; // 主营业务收入
                description = "Charge AR audit auto-voucher (charge " + entityId + ")";
            } else if ("AP".equals(side)) {
                debitCode = "5401";  // 主营业务成本
                creditCode = "2202"; // 应付账款
                description = "Charge AP audit auto-voucher (charge " + entityId + ")";
            } else { return; }

            String debitSubId = jdbc.queryForObject(
                "SELECT id::text FROM acc_gl_subjects WHERE code = ?", String.class, debitCode);
            String creditSubId = jdbc.queryForObject(
                "SELECT id::text FROM acc_gl_subjects WHERE code = ?", String.class, creditCode);
            if (debitSubId == null || creditSubId == null) {
                LOGGER.warn("Missing GL subjects {} or {}", debitCode, creditCode);
                return;
            }

            String voucherNo = "V-AUTO-" + LocalDate.now().toString().replace("-", "")
                + "-" + entityId.substring(0, 6);
            String voucherId = jdbc.queryForObject("""
                INSERT INTO acc_gl_vouchers (
                  voucher_no, the_date, description, source_type, source_id, status
                ) VALUES (?, current_date, ?, 'charges', ?::uuid, 'POSTED')
                ON CONFLICT DO NOTHING
                RETURNING id::text
                """, String.class, voucherNo, description, entityId);
            if (voucherId == null) return;

            jdbc.update("""
                INSERT INTO acc_gl_voucher_lines (voucher_id, subject_id, direction, amount, currency, line_no)
                VALUES (?::uuid, ?::uuid, 'DEBIT', ?, ?, 1)
                """, voucherId, debitSubId, amount, currency);
            jdbc.update("""
                INSERT INTO acc_gl_voucher_lines (voucher_id, subject_id, direction, amount, currency, line_no)
                VALUES (?::uuid, ?::uuid, 'CREDIT', ?, ?, 2)
                """, voucherId, creditSubId, amount, currency);

            LOGGER.info("Auto-voucher {} POSTED for {} charge {}", voucherNo, side, entityId);
        } catch (Exception ex) {
            LOGGER.warn("ChargeAuditGlSideEffect failed: {}", ex.getMessage());
        }
    }

    @Override
    public void onUndone(String table, String entityId, String tenantId, String actorName) {
        // 反审 → 凭证标 VOID
        try {
            jdbc.update("""
                UPDATE acc_gl_vouchers SET status = 'VOID'
                 WHERE source_type = 'charges' AND source_id = ?::uuid AND status = 'POSTED'
                """, entityId);
        } catch (Exception ex) {
            LOGGER.warn("Voucher void failed: {}", ex.getMessage());
        }
    }
}

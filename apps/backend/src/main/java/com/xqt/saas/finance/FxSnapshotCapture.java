package com.xqt.saas.finance;

import java.math.BigDecimal;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 任务 S6：资金动作 fx 快照捕获。
 *
 * balance_ledger 11 类 biz_type（PREPAY / PREPAY_RELEASE / RECEIPT / PAYMENT /
 * REFUND / ADJUST / REBATE / FINE / REPARATION / VOID / FX_DIFF）写入时调用本组件；
 * 若 ledger.currency != tenant.base_currency → 自动 INSERT fx_rate_snapshots 一条，
 * 便于事后对账重现。
 *
 * 设计：
 *   - 本组件**只负责快照写入**，不做汇率转换计算（金额按原币入账）
 *   - 找不到当前汇率时 fallback rate=1.0 + source="MISSING_RATE"，让审计能看到漏配
 *   - DB 异常静默吞掉（fx 快照失败不应阻断 ledger 主路径）
 */
@Component
public class FxSnapshotCapture {
    private static final String DEFAULT_BASE = "CNY";
    private final JdbcTemplate jdbc;

    public FxSnapshotCapture(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 资金动作触发时调用：若需要写 fx 快照，写入并返回 snapshot id（否则返回 null）。 */
    public String captureForLedger(String tenantId, String currency,
                                    String bizType, String sourceType, String sourceRef) {
        try {
            String base = lookupBaseCurrency(tenantId);
            if (currency == null || base.equalsIgnoreCase(currency)) {
                return null;
            }
            BigDecimal rate = lookupCurrentRate(tenantId, currency, base);
            String source = rate == null ? "MISSING_RATE" : "AUTO_LEDGER";
            BigDecimal effectiveRate = rate == null ? BigDecimal.ONE : rate;
            return jdbc.queryForObject("""
                INSERT INTO fx_rate_snapshots
                  (tenant_id, from_currency, to_currency, rate, source,
                   biz_type, source_type, source_ref)
                VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id::text
                """, String.class, tenantId, currency, base, effectiveRate, source,
                bizType, sourceType, sourceRef);
        } catch (DataAccessException ex) {
            return null;
        }
    }

    private String lookupBaseCurrency(String tenantId) {
        try {
            String base = jdbc.queryForObject(
                "SELECT base_currency FROM tenants WHERE id = ?::uuid", String.class, tenantId);
            return base == null || base.isBlank() ? DEFAULT_BASE : base;
        } catch (EmptyResultDataAccessException ex) {
            return DEFAULT_BASE;
        }
    }

    private BigDecimal lookupCurrentRate(String tenantId, String from, String to) {
        try {
            return jdbc.queryForObject("""
                SELECT rate FROM exchange_rates
                WHERE tenant_id = ?::uuid
                  AND from_currency = ? AND to_currency = ?
                ORDER BY rate_date DESC LIMIT 1
                """, BigDecimal.class, tenantId, from, to);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }
}

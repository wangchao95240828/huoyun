package com.xqt.saas.framework.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 复刻 ACC charges / payments 落库时锁定当时汇率的行为。
 *
 * ACC 旧 PHP 用 Currency.Decimal/Rate 把外币换算成 CNY 后存 charges.amount_cny；
 * 但 Currency 表的 Rate 可能被运营改，导致日后翻账金额漂移。这里给每笔金额一个快照：
 *   sourceAmount + sourceCurrency + rate + targetAmount + snapshotted_at
 * 主审计 / 利润聚合一律读快照，不再实时 join 当前汇率。
 */
@Service
public class MoneySnapshotService {
    public static final String BASE_CURRENCY = "CNY";

    private final JdbcTemplate jdbc;

    public MoneySnapshotService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 为一条业务单据的金额建立汇率快照。
     * 若 sourceCurrency == BASE_CURRENCY，rate=1，targetAmount=sourceAmount。
     * 若不存在汇率，rate=null，需业务层兜底。
     */
    public Snapshot snapshot(String entityType, String entityId,
                             BigDecimal sourceAmount, String sourceCurrency) {
        if (sourceAmount == null) {
            return new Snapshot(null, null, sourceCurrency, BASE_CURRENCY, null);
        }
        BigDecimal rate;
        if (BASE_CURRENCY.equalsIgnoreCase(sourceCurrency)) {
            rate = BigDecimal.ONE;
        } else {
            rate = lookupRate(sourceCurrency, BASE_CURRENCY);
            if (rate == null) {
                // 没有汇率配置时返回 null 让业务层决定（拒绝/警告/跳过）
                return new Snapshot(null, sourceAmount, sourceCurrency, BASE_CURRENCY, null);
            }
        }
        BigDecimal targetAmount = sourceAmount.multiply(rate).setScale(4, RoundingMode.HALF_UP);
        try {
            jdbc.update("""
                INSERT INTO exchange_rate_snapshots (
                  tenant_id, entity_type, entity_id, source_currency, target_currency,
                  rate, source_amount, target_amount
                ) VALUES (
                  current_setting('app.current_tenant_id')::uuid, ?, ?, ?, ?, ?, ?, ?
                )
                ON CONFLICT (tenant_id, entity_type, entity_id, source_currency, target_currency)
                DO UPDATE SET rate = EXCLUDED.rate,
                              source_amount = EXCLUDED.source_amount,
                              target_amount = EXCLUDED.target_amount,
                              snapshotted_at = now()
                """, entityType, entityId, sourceCurrency, BASE_CURRENCY,
                rate, sourceAmount, targetAmount);
        } catch (DataAccessException ex) {
            // 快照失败不阻塞业务（与 ACC 一致：业务记录优先，快照可后台补）
        }
        return new Snapshot(rate, sourceAmount, sourceCurrency, BASE_CURRENCY, targetAmount);
    }

    public Snapshot lookup(String entityType, String entityId, String sourceCurrency) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT rate, source_amount, target_amount, source_currency, target_currency
                FROM exchange_rate_snapshots
                WHERE entity_type = ? AND entity_id = ? AND source_currency = ?
                LIMIT 1
                """, entityType, entityId, sourceCurrency);
            if (rows.isEmpty()) return null;
            Map<String, Object> row = rows.get(0);
            return new Snapshot(
                (BigDecimal) row.get("rate"),
                (BigDecimal) row.get("source_amount"),
                (String) row.get("source_currency"),
                (String) row.get("target_currency"),
                (BigDecimal) row.get("target_amount")
            );
        } catch (DataAccessException ex) {
            return null;
        }
    }

    /**
     * 查 finance_currency_exchange 拿当前汇率。
     * 该表 schema：(code, application_scenario, rate, effective_from) — 所有汇率都是 to CNY。
     * `to` 参数当前必须是 CNY（BASE_CURRENCY）；非 CNY 目标币种返回 null。
     */
    private BigDecimal lookupRate(String from, String to) {
        if (!BASE_CURRENCY.equalsIgnoreCase(to)) {
            return null;
        }
        try {
            return jdbc.queryForObject("""
                SELECT rate FROM finance_currency_exchange
                WHERE code = ? AND effective_from <= now()
                ORDER BY effective_from DESC
                LIMIT 1
                """, BigDecimal.class, from);
        } catch (DataAccessException ex) {
            return null;
        }
    }

    public record Snapshot(
        BigDecimal rate,
        BigDecimal sourceAmount,
        String sourceCurrency,
        String targetCurrency,
        BigDecimal targetAmount
    ) {
    }
}

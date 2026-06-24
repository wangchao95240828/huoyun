package com.xqt.saas.rates;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xqt.saas.common.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * W3 deep-research 落地 — EasyPost 模式: Rate 快照 + rerate + 月度对账.
 *
 * "Rate 会过期, 应该存 quote 快照而不是把 cache 当实时."
 *
 * 三个核心能力:
 *   1. snapshot(req, quote)  — 报价后落盘, TTL = min(下周一 0:00, 24h)
 *   2. rerate(quoteId)        — 用同 request 重算, 旧 quote 标 RE_RATED 链到新
 *   3. monthlyReconcile()     — 每月 5 号跑, 对比 quote vs 真实 charges, delta > 1% 告警
 */
@Service
public class RateQuoteSnapshotService {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(RateQuoteSnapshotService.class);
    private final JdbcTemplate jdbc;
    private final RateEngine rateEngine;
    private final ObjectMapper json;

    public RateQuoteSnapshotService(JdbcTemplate jdbc, RateEngine rateEngine, ObjectMapper json) {
        this.jdbc = jdbc;
        this.rateEngine = rateEngine;
        this.json = json;
    }

    /** 落 quote 快照, 返回 quoteId. */
    @Transactional
    public String snapshot(String tenantId, String customerId, String orderId,
                           RateQuoteRequest req, RateQuoteResponse.Quote quote) {
        try {
            String requestJson = json.writeValueAsString(req);
            String responseJson = json.writeValueAsString(quote);
            Instant expiresAt = computeExpiry();
            // PG 类型推断对 null::uuid 失败, 用 CASE 显式 cast
            return jdbc.queryForObject("""
                INSERT INTO rate_quotes
                  (tenant_id, customer_id, order_id, request_json, response_json,
                   channel_code, currency, total_amount, expires_at, fuel_pct, status)
                VALUES (?::uuid,
                        CASE WHEN ? IS NULL THEN NULL ELSE ?::uuid END,
                        CASE WHEN ? IS NULL THEN NULL ELSE ?::uuid END,
                        ?::jsonb, ?::jsonb,
                        ?, ?, ?, ?, ?, 'ACTIVE')
                RETURNING id::text
                """, String.class,
                tenantId,
                customerId, customerId,
                orderId, orderId,
                requestJson, responseJson,
                quote.channelCode(), req.currency(), quote.totalAmount(),
                java.sql.Timestamp.from(expiresAt), quote.fuelRate());
        } catch (Exception ex) {
            // 落盘失败不阻断报价主流程
            LOGGER.warn("rate_quotes snapshot failed (non-blocking): {}", ex.getMessage());
            return null;
        }
    }

    /**
     * rerate: 用同 request 重新算一次, 旧 quote 标 RE_RATED.
     * EasyPost 语义: regenerate rates without recreating shipment.
     */
    @Transactional
    public RateQuoteResponse.Quote rerate(String tenantId, String quoteId) {
        Map<String, Object> old = findActiveQuote(tenantId, quoteId);
        if (old == null) throw ApiException.notFound("quote 不存在或已使用/已过期: " + quoteId);
        RateQuoteRequest req;
        try {
            req = json.readValue(old.get("request_json").toString(), RateQuoteRequest.class);
        } catch (Exception ex) {
            throw ApiException.badRequest("quote request 解析失败: " + ex.getMessage());
        }
        // 用同 tenant 重算
        RateQuoteResponse.Quote newQuote = rateEngine.quote(tenantId, req);
        // 新落 quote
        String newId = snapshot(tenantId,
            (String) old.get("customer_id"), (String) old.get("order_id"),
            req, newQuote);
        // 旧标 RE_RATED + 链到新
        jdbc.update("""
            UPDATE rate_quotes SET status = 'RE_RATED', rerate_to_id = ?::uuid
             WHERE id = ?::uuid
            """, newId, quoteId);
        return newQuote;
    }

    /** TTL: min(下周一 0:00 UTC, 24h) — 跟 UPS 周一燃油生效一致. */
    private Instant computeExpiry() {
        Instant now = Instant.now();
        // 24h
        Instant in24h = now.plus(Duration.ofHours(24));
        // 下周一 0:00 UTC
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate nextMonday = today.with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.MONDAY));
        Instant mondayMidnight = nextMonday.atStartOfDay(ZoneOffset.UTC).toInstant();
        return in24h.isBefore(mondayMidnight) ? in24h : mondayMidnight;
    }

    private Map<String, Object> findActiveQuote(String tenantId, String quoteId) {
        try {
            return jdbc.queryForMap("""
                SELECT id::text, customer_id::text, order_id::text, request_json::text, response_json::text,
                       channel_code, currency, total_amount, expires_at, status
                  FROM rate_quotes
                 WHERE tenant_id = ?::uuid AND id = ?::uuid
                   AND status = 'ACTIVE'
                """, tenantId, quoteId);
        } catch (DataAccessException ex) {
            return null;
        }
    }

    // ════════ W3 月度对账 cron ════════

    /**
     * 月度对账: 每月 5 号 02:00 自动跑.
     * 比对上月所有已 USED quote 的 total_amount vs 真实 charges 累计:
     *   |quote - actual| / actual <= 1% → MATCHED
     *   1% < delta <= 5% → DRIFT_MINOR (记录)
     *   delta > 5% → DRIFT_MAJOR (告警)
     */
    @Scheduled(cron = "0 0 2 5 * ?")
    @Transactional
    public void monthlyReconcile() {
        // 拉本月所有 USED quote
        LocalDate firstDayLastMonth = LocalDate.now().withDayOfMonth(1).minusMonths(1);
        LocalDate firstDayThisMonth = LocalDate.now().withDayOfMonth(1);
        List<Map<String, Object>> quotes = jdbc.queryForList("""
            SELECT id::text, tenant_id::text, order_id::text, total_amount, currency
              FROM rate_quotes
             WHERE status = 'USED'
               AND reconcile_status = 'PENDING'
               AND quoted_at >= ? AND quoted_at < ?
            """, java.sql.Date.valueOf(firstDayLastMonth), java.sql.Date.valueOf(firstDayThisMonth));

        int matched = 0, minorDrift = 0, majorDrift = 0, notUsed = 0;
        for (Map<String, Object> q : quotes) {
            String quoteId = (String) q.get("id");
            String orderId = (String) q.get("order_id");
            BigDecimal quoted = (BigDecimal) q.get("total_amount");
            if (orderId == null) {
                jdbc.update("UPDATE rate_quotes SET reconcile_status='NOT_USED', reconcile_at=now() WHERE id=?::uuid", quoteId);
                notUsed++;
                continue;
            }
            // 拉订单关联 charges 累计 (charges AR 已审, 同币种)
            BigDecimal actual;
            try {
                actual = jdbc.queryForObject("""
                    SELECT coalesce(sum(amount), 0)
                      FROM charges
                     WHERE order_id = ?::uuid AND side = 'AR'
                       AND audit_status = 'AUDITED'
                       AND status <> 'VOID'::charge_status
                       AND currency = ?
                    """, BigDecimal.class, orderId, q.get("currency"));
            } catch (DataAccessException ex) {
                continue;
            }
            if (actual == null || actual.signum() == 0) {
                jdbc.update("UPDATE rate_quotes SET reconcile_status='NOT_USED', reconcile_at=now() WHERE id=?::uuid", quoteId);
                notUsed++;
                continue;
            }
            BigDecimal delta = quoted.subtract(actual);
            BigDecimal deltaPct = delta.abs()
                .multiply(new BigDecimal("100"))
                .divide(actual, 4, RoundingMode.HALF_UP);
            String status;
            if (deltaPct.compareTo(new BigDecimal("1")) <= 0) { status = "MATCHED"; matched++; }
            else if (deltaPct.compareTo(new BigDecimal("5")) <= 0) { status = "DRIFT_MINOR"; minorDrift++; }
            else { status = "DRIFT_MAJOR"; majorDrift++; }
            jdbc.update("""
                UPDATE rate_quotes
                   SET reconcile_status = ?, reconcile_at = now(),
                       reconcile_actual = ?, reconcile_delta = ?, reconcile_delta_pct = ?
                 WHERE id = ?::uuid
                """, status, actual, delta, deltaPct, quoteId);
        }
        // 落审计日志 (用 acc_write_audit 兜底, 实际可用 acc_settlement_audit_log)
        try {
            jdbc.update("""
                INSERT INTO acc_write_audit (tenant_id, user_id, user_name, action, table_name, payload_json)
                VALUES (current_setting('app.current_tenant_id')::uuid, '00000000-0000-0000-0000-000000000000'::uuid,
                        'cron-monthly-reconcile', 'RECONCILE', 'rate_quotes',
                        jsonb_build_object('matched', ?, 'minor_drift', ?, 'major_drift', ?, 'not_used', ?, 'month', ?))
                """, matched, minorDrift, majorDrift, notUsed, firstDayLastMonth.toString());
        } catch (DataAccessException ignored) {}
    }
}

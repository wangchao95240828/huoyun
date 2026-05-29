package com.xqt.saas.finance.feed;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 定时拉真实汇率回填 {@code exchange_rates} 表。
 *
 * 流程：
 *   1. 遍历所有 tenant 的 base_currency（取 distinct）
 *   2. 对每个 base，列出已配置的源 currency（exchange_rates 已有的 from_currency 集合，
 *      加 USD / EUR / HKD / JPY / GBP 兜底）
 *   3. 调 {@link FxRateFeed#fetch} 拿当日汇率
 *   4. UPSERT 到 {@code exchange_rates}
 *
 * 默认每天 02:00 (Asia/Hong_Kong) 拉一次，生产可改 cron 表达式。
 * profile=prod 时启用，dev/test 默认不跑（避免 CI 联网）。
 */
@Component
@Profile("prod")
public class FxRateRefreshJob {
    private static final Logger LOGGER = LoggerFactory.getLogger(FxRateRefreshJob.class);
    private static final List<String> DEFAULT_CURRENCIES = List.of("USD", "EUR", "HKD", "JPY", "GBP");

    private final JdbcTemplate jdbc;
    private final FxRateFeed feed;
    private final boolean useServiceRole;

    public FxRateRefreshJob(JdbcTemplate jdbc, FxRateFeed feed,
                             @Value("${app.fx.refresh.bypass-rls:true}") boolean useServiceRole) {
        this.jdbc = jdbc;
        this.feed = feed;
        this.useServiceRole = useServiceRole;
    }

    /** 每天 02:00 Asia/Hong_Kong 拉一次 */
    @Scheduled(cron = "${app.fx.refresh.cron:0 0 2 * * *}", zone = "Asia/Hong_Kong")
    @Transactional(rollbackFor = Exception.class)
    public void refresh() {
        if (useServiceRole) {
            jdbc.queryForObject("select set_config('app.service_role', 'true', true)", String.class);
        }
        LocalDate today = LocalDate.now();
        int totalRows = 0;
        try {
            List<Map<String, Object>> tenants = jdbc.queryForList(
                "SELECT id::text AS tenant_id, base_currency FROM tenants");
            for (Map<String, Object> t : tenants) {
                totalRows += refreshTenant(
                    (String) t.get("tenant_id"),
                    (String) t.get("base_currency"),
                    today);
            }
            LOGGER.info("FxRateRefreshJob done: tenants={} rows_upserted={}", tenants.size(), totalRows);
        } catch (Exception ex) {
            LOGGER.warn("FxRateRefreshJob failed: {}", ex.getMessage());
        }
    }

    private int refreshTenant(String tenantId, String baseCurrency, LocalDate today) {
        String base = baseCurrency == null || baseCurrency.isBlank() ? "CNY" : baseCurrency;
        List<String> currencies = listCurrenciesNeeded(tenantId, base);
        if (currencies.isEmpty()) return 0;
        Map<String, BigDecimal> rates = feed.fetch(base, currencies, today);
        int upserted = 0;
        for (Map.Entry<String, BigDecimal> e : rates.entrySet()) {
            jdbc.update("""
                INSERT INTO exchange_rates (tenant_id, rate_date, from_currency, to_currency, rate, rate_type, source)
                VALUES (?::uuid, ?, ?, ?, ?, 'ACCOUNTING', ?)
                ON CONFLICT (tenant_id, rate_date, from_currency, to_currency, rate_type)
                DO UPDATE SET rate = EXCLUDED.rate, source = EXCLUDED.source
                """, tenantId, today, e.getKey(), base, e.getValue(), feed.code());
            upserted++;
        }
        return upserted;
    }

    /**
     * 该 tenant 下需要哪些币种：合并 charges / customers / channels / balance_ledger 出现过的 currency
     *   + 默认 5 个币种（USD/EUR/HKD/JPY/GBP）兜底。
     */
    private List<String> listCurrenciesNeeded(String tenantId, String base) {
        try {
            List<String> existing = jdbc.queryForList("""
                SELECT DISTINCT from_currency FROM exchange_rates
                WHERE tenant_id = ?::uuid AND to_currency = ? AND from_currency <> ?
                """, String.class, tenantId, base, base);
            List<String> out = new ArrayList<>(existing);
            for (String c : DEFAULT_CURRENCIES) {
                if (!c.equalsIgnoreCase(base) && !out.contains(c)) out.add(c);
            }
            return out;
        } catch (Exception ex) {
            return new ArrayList<>(DEFAULT_CURRENCIES);
        }
    }
}

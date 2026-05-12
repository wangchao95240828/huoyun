package com.xqt.saas.rates;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RateRepository {
    private final JdbcTemplate jdbc;

    public RateRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> findChannelByCode(String tenantId, String channelCode) {
        try {
            return jdbc.queryForMap("""
                SELECT id::text AS id, code, name, dim_factor, primary_uom::text AS primary_uom, active
                FROM channels
                WHERE tenant_id = ?::uuid AND code = ?
                """, tenantId, channelCode);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    public Map<String, Object> findActiveRateCard(String tenantId, String channelId, String side,
                                                   String currency, LocalDate chargeDate) {
        try {
            return jdbc.queryForMap("""
                SELECT id::text AS id, version, currency, status,
                       effective_from, effective_to
                FROM rate_cards
                WHERE tenant_id = ?::uuid
                  AND channel_id = ?::uuid
                  AND side = ?::charge_side
                  AND currency = ?
                  AND status = 'ACTIVE'
                  AND effective_from <= ?
                  AND (effective_to IS NULL OR effective_to >= ?)
                ORDER BY effective_from DESC
                LIMIT 1
                """, tenantId, channelId, side, currency, chargeDate, chargeDate);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    public Map<String, Object> findTier(String tenantId, String rateCardId, String zoneCode,
                                         BigDecimal chargeableWeightKg) {
        try {
            return jdbc.queryForMap("""
                SELECT id::text AS id, zone_code, weight_from, weight_to,
                       uom::text AS uom, unit_price, min_amount
                FROM rate_card_lines
                WHERE tenant_id = ?::uuid
                  AND rate_card_id = ?::uuid
                  AND zone_code = ?
                  AND weight_from <= ?
                  AND (weight_to IS NULL OR weight_to > ?)
                ORDER BY weight_from DESC
                LIMIT 1
                """, tenantId, rateCardId, zoneCode, chargeableWeightKg, chargeableWeightKg);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    public BigDecimal findFuelRate(String tenantId, String channelId, String yearMonth) {
        try {
            return jdbc.queryForObject("""
                SELECT rate
                FROM fuel_surcharge_rates
                WHERE tenant_id = ?::uuid
                  AND channel_id = ?::uuid
                  AND year_month = ?
                """, BigDecimal.class, tenantId, channelId, yearMonth);
        } catch (EmptyResultDataAccessException ex) {
            return BigDecimal.ZERO;
        }
    }

    public String findRemoteLevel(String tenantId, String channelId, String countryCode, String postalCode) {
        if (countryCode == null || countryCode.isBlank()) {
            return "NONE";
        }
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT level::text AS level, postal_code_pattern
            FROM remote_zones
            WHERE tenant_id = ?::uuid
              AND channel_id = ?::uuid
              AND country_code = ?
            ORDER BY effective_from DESC
            """, tenantId, channelId, countryCode);
        if (rows.isEmpty()) {
            return "NONE";
        }
        String code = postalCode == null ? "" : postalCode.trim();
        for (Map<String, Object> row : rows) {
            String pattern = (String) row.get("postal_code_pattern");
            String level = (String) row.get("level");
            if (pattern == null || pattern.isBlank()) {
                return level;
            }
            if (!code.isEmpty() && code.matches(pattern)) {
                return level;
            }
        }
        return "NONE";
    }
}

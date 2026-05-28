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

    /**
     * 客户专属价：对应 ACC `Product_Customer`。
     * 返回最高 priority 命中的 rate_card_id；服务码可空（null = 适用所有服务）。
     */
    public Map<String, Object> findCustomerSpecificRateCard(String tenantId, String customerId,
                                                            String channelId, String serviceCode,
                                                            LocalDate chargeDate) {
        if (customerId == null) return null;
        try {
            return jdbc.queryForMap("""
                SELECT crc.id::text AS id, crc.rate_card_id::text AS rate_card_id, crc.priority
                FROM customer_rate_cards crc
                INNER JOIN rate_cards rc ON rc.id = crc.rate_card_id
                WHERE crc.tenant_id = ?::uuid
                  AND crc.customer_id = ?::uuid
                  AND crc.active = true
                  AND (crc.channel_id IS NULL OR crc.channel_id = ?::uuid)
                  AND (crc.service_code IS NULL OR crc.service_code = ?)
                  AND crc.effective_from <= ?
                  AND (crc.effective_to IS NULL OR crc.effective_to >= ?)
                  AND rc.status = 'ACTIVE'
                ORDER BY crc.priority DESC, crc.effective_from DESC
                LIMIT 1
                """, tenantId, customerId, channelId, serviceCode, chargeDate, chargeDate);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    /** 客户组价：对应 ACC `Product_Group`。 */
    public Map<String, Object> findCustomerGroupRateCard(String tenantId, String customerGroupId,
                                                         String channelId, String serviceCode,
                                                         LocalDate chargeDate) {
        if (customerGroupId == null) return null;
        try {
            return jdbc.queryForMap("""
                SELECT cgrc.id::text AS id, cgrc.rate_card_id::text AS rate_card_id, cgrc.priority
                FROM customer_group_rate_cards cgrc
                INNER JOIN rate_cards rc ON rc.id = cgrc.rate_card_id
                WHERE cgrc.tenant_id = ?::uuid
                  AND cgrc.customer_group_id = ?::uuid
                  AND cgrc.active = true
                  AND (cgrc.channel_id IS NULL OR cgrc.channel_id = ?::uuid)
                  AND (cgrc.service_code IS NULL OR cgrc.service_code = ?)
                  AND cgrc.effective_from <= ?
                  AND (cgrc.effective_to IS NULL OR cgrc.effective_to >= ?)
                  AND rc.status = 'ACTIVE'
                ORDER BY cgrc.priority DESC, cgrc.effective_from DESC
                LIMIT 1
                """, tenantId, customerGroupId, channelId, serviceCode, chargeDate, chargeDate);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    /**
     * 多段计费查询：按 zone + 重量带 找到一条价表行。
     * 同时返回 calculation_type / first_weight / first_amount / continued_unit_price 等多段字段。
     * 邮编精确匹配时给更高 postal_priority，调用方按 priority 合并选最优。
     */
    public Map<String, Object> findTier(String tenantId, String rateCardId, String zoneCode,
                                         BigDecimal chargeableWeightKg, String postalCode) {
        try {
            // 先找精确邮编匹配（postal_priority > 0 且 pattern 命中）
            if (postalCode != null && !postalCode.isBlank()) {
                List<Map<String, Object>> hits = jdbc.queryForList("""
                    SELECT id::text AS id, zone_code, weight_from, weight_to,
                           uom::text AS uom, unit_price, min_amount,
                           calculation_type, first_weight_kg, first_amount,
                           continued_step_kg, continued_unit_price, fixed_amount,
                           postal_code_pattern, postal_priority
                    FROM rate_card_lines
                    WHERE tenant_id = ?::uuid
                      AND rate_card_id = ?::uuid
                      AND (zone_code = ? OR zone_code IS NULL)
                      AND weight_from <= ?
                      AND (weight_to IS NULL OR weight_to > ?)
                      AND postal_code_pattern IS NOT NULL
                      AND postal_priority > 0
                    ORDER BY postal_priority DESC, weight_from DESC
                    """, tenantId, rateCardId, zoneCode, chargeableWeightKg, chargeableWeightKg);
                for (Map<String, Object> row : hits) {
                    String pattern = (String) row.get("postal_code_pattern");
                    if (pattern != null && postalCode.matches(pattern)) {
                        return row;
                    }
                }
            }
            // 兜底：无邮编精确匹配，按 zone + weight 取
            return jdbc.queryForMap("""
                SELECT id::text AS id, zone_code, weight_from, weight_to,
                       uom::text AS uom, unit_price, min_amount,
                       calculation_type, first_weight_kg, first_amount,
                       continued_step_kg, continued_unit_price, fixed_amount,
                       postal_code_pattern, postal_priority
                FROM rate_card_lines
                WHERE tenant_id = ?::uuid
                  AND rate_card_id = ?::uuid
                  AND (zone_code = ? OR zone_code IS NULL)
                  AND weight_from <= ?
                  AND (weight_to IS NULL OR weight_to > ?)
                  AND (postal_priority = 0 OR postal_priority IS NULL)
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

    /**
     * 偏远费率规则：按级别 + 渠道 + 生效日查询。
     * 优先取 channel_id 匹配的；没找到再 fallback 到 channel_id IS NULL 的全租户规则。
     */
    public Map<String, Object> findRemoteRateRule(String tenantId, String channelId,
                                                  String level, LocalDate chargeDate) {
        if (level == null || "NONE".equals(level) || "EMBARGO".equals(level)) {
            return null;
        }
        try {
            return jdbc.queryForMap("""
                SELECT id::text AS id, rate_type, rate, fixed_amount, min_amount
                FROM remote_rate_rules
                WHERE tenant_id = ?::uuid
                  AND active = true
                  AND level = ?
                  AND (channel_id IS NULL OR channel_id = ?::uuid)
                  AND effective_from <= ?
                  AND (effective_to IS NULL OR effective_to >= ?)
                ORDER BY (channel_id IS NULL), effective_from DESC
                LIMIT 1
                """, tenantId, level, channelId, chargeDate, chargeDate);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    /**
     * 渠道账号限额：单票件数 / 重量 / 当日票量。
     * 返回 max_count / max_piece / max_weight 以及当日已用量（count/piece/weight）。
     */
    public Map<String, Object> findChannelAccountLimit(String tenantId, String channelId,
                                                       String accountCode, LocalDate usageDate) {
        if (accountCode == null || accountCode.isBlank()) return null;
        try {
            return jdbc.queryForMap("""
                SELECT lim.id::text AS id,
                       lim.max_count, lim.max_piece, lim.max_weight,
                       coalesce(usg.count, 0)   AS count_used,
                       coalesce(usg.piece, 0)   AS piece_used,
                       coalesce(usg.weight, 0)  AS weight_used
                FROM channel_account_limits lim
                LEFT JOIN channel_account_daily_usage usg
                       ON usg.tenant_id = lim.tenant_id
                      AND usg.account_code = lim.account_code
                      AND usg.channel_id = ?::uuid
                      AND usg.usage_date = ?
                WHERE lim.tenant_id = ?::uuid
                  AND (lim.channel_id IS NULL OR lim.channel_id = ?::uuid)
                  AND lim.account_code = ?
                  AND lim.active = true
                LIMIT 1
                """, channelId, usageDate, tenantId, channelId, accountCode);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    /**
     * 服务限制：电池 / 敏感 / 仿牌 / 国家 / 邮编 黑白名单。
     * 取适用此 channel + service 的第一条规则，未配规则时返回 null（即默认允许）。
     */
    public Map<String, Object> findServiceRestriction(String tenantId, String channelId,
                                                       String accountCode, String serviceCode) {
        try {
            return jdbc.queryForMap("""
                SELECT id::text AS id,
                       battery_allowed, battery_built_in_allowed, battery_dry_allowed,
                       sensitive_allowed, brand_allowed,
                       country_code, country_blacklist, postal_pattern
                FROM service_restrictions
                WHERE tenant_id = ?::uuid
                  AND active = true
                  AND (channel_id IS NULL OR channel_id = ?::uuid)
                  AND (account_code IS NULL OR account_code = ?)
                  AND (service_code IS NULL OR service_code = ?)
                ORDER BY
                  (channel_id IS NULL),
                  (account_code IS NULL),
                  (service_code IS NULL)
                LIMIT 1
                """, tenantId, channelId, accountCode, serviceCode);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    /**
     * 任务 S3 A1：查命中品名关键词的附加费规则。
     * declarationNames 是订单申报明细的品名集合。返回按 fee_code 分组、每组 priority 最高的一条。
     */
    public List<Map<String, Object>> findKeywordSurcharges(String tenantId,
                                                            List<String> declarationNames,
                                                            LocalDate chargeDate) {
        if (declarationNames == null || declarationNames.isEmpty()) return List.of();
        // 用 DISTINCT ON 按 fee_code 取 priority 最高的一条
        try {
            return jdbc.queryForList("""
                SELECT DISTINCT ON (fee_code)
                  id, keyword, match_type, fee_code, charge_unit,
                  amount, rate, currency, priority
                FROM product_keyword_rules
                WHERE tenant_id = ?::uuid
                  AND is_active = true
                  AND (effective_from IS NULL OR effective_from <= ?)
                  AND (effective_to IS NULL OR effective_to >= ?)
                  AND EXISTS (
                    SELECT 1 FROM unnest(?::text[]) AS dn(name)
                    WHERE
                      (match_type = 'EXACT' AND lower(dn.name) = lower(keyword))
                      OR (match_type = 'CONTAINS' AND lower(dn.name) LIKE '%' || lower(keyword) || '%')
                      OR (match_type = 'PREFIX' AND lower(dn.name) LIKE lower(keyword) || '%')
                  )
                ORDER BY fee_code, priority DESC
                """, tenantId, chargeDate, chargeDate,
                declarationNames.toArray(new String[0]));
        } catch (org.springframework.dao.DataAccessException ex) {
            return List.of();
        }
    }

    /**
     * 佣金规则：客户 > 客户组 > 服务 > 渠道。
     * 命中后返回 rule_type / rate / fixed_amount。
     */
    public Map<String, Object> findCommissionRule(String tenantId, String customerId,
                                                  String customerGroupId, String channelId,
                                                  String serviceCode, LocalDate chargeDate) {
        try {
            return jdbc.queryForMap("""
                SELECT id::text AS id, rule_type, rate, fixed_amount
                FROM rate_commission_rules
                WHERE tenant_id = ?::uuid
                  AND active = true
                  AND effective_from <= ?
                  AND (effective_to IS NULL OR effective_to >= ?)
                  AND (customer_id IS NULL OR customer_id = ?::uuid)
                  AND (customer_group_id IS NULL OR customer_group_id = ?::uuid)
                  AND (channel_id IS NULL OR channel_id = ?::uuid)
                  AND (service_code IS NULL OR service_code = ?)
                ORDER BY
                  (customer_id IS NULL),
                  (customer_group_id IS NULL),
                  (channel_id IS NULL),
                  (service_code IS NULL),
                  effective_from DESC
                LIMIT 1
                """, tenantId, chargeDate, chargeDate, customerId, customerGroupId,
                channelId, serviceCode);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }
}

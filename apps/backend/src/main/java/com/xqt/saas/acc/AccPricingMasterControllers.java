package com.xqt.saas.acc;

import com.xqt.saas.common.ApiException;
import com.xqt.saas.common.JsonSupport;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/**
 * M6 + M7 + M8: 价格分区 / 附加费规则 / 保险费率主表 Controller.
 * 3 个内嵌 @RestController 共用一个外类, 减少文件数.
 */
public class AccPricingMasterControllers {

    // ════════ M6: zones + zone_countries ════════
    @RestController
    @RequestMapping("/api/acc/zones")
    public static class Zones {
        private final JdbcTemplate jdbc;
        private final JsonSupport json;
        public Zones(JdbcTemplate jdbc, JsonSupport json) { this.jdbc = jdbc; this.json = json; }

        @GetMapping
        public Map<String, Object> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
            int offset = Math.max(0, page - 1) * pageSize;
            String filter = keyword != null && !keyword.isBlank()
                ? "WHERE z.code ILIKE ? OR z.name ILIKE ?" : "";
            Object[] args = filter.isEmpty()
                ? new Object[]{pageSize, offset}
                : new Object[]{"%"+keyword+"%", "%"+keyword+"%", pageSize, offset};
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT z.id::text, z.code, z.name, z.pinyin, z.sort_order, z.remark,
                       z.audit_status, z.created_at,
                       coalesce(array_agg(zc.country_code) FILTER (WHERE zc.country_code IS NOT NULL), '{}') AS countries
                FROM zones z
                LEFT JOIN zone_countries zc ON zc.zone_id = z.id
                """ + filter + """
                GROUP BY z.id ORDER BY z.sort_order, z.code LIMIT ? OFFSET ?
                """, args);
            Long total = jdbc.queryForObject(
                "SELECT count(*) FROM zones z " + filter,
                Long.class, filter.isEmpty() ? new Object[0] : new Object[]{"%"+keyword+"%", "%"+keyword+"%"});
            return Map.of("data", rows.stream().map(r -> {
                Map<String, Object> o = new LinkedHashMap<>(r);
                o.put("created_at", json.value(r.get("created_at")));
                return o;
            }).toList(), "total", total);
        }

        @PostMapping
        @org.springframework.transaction.annotation.Transactional
        public Map<String, Object> create(@RequestBody Map<String, Object> body) {
            String code = (String) body.get("code"); String name = (String) body.get("name");
            if (code == null || code.isBlank()) throw ApiException.badRequest("code 必填");
            if (name == null || name.isBlank()) throw ApiException.badRequest("name 必填");
            String id = jdbc.queryForObject("""
                INSERT INTO zones (code, name, pinyin, sort_order, remark)
                VALUES (?, ?, ?, ?, ?) RETURNING id::text
                """, String.class, code, name, body.get("pinyin"),
                body.getOrDefault("sortOrder", 0), body.get("remark"));
            @SuppressWarnings("unchecked")
            List<String> countries = (List<String>) body.get("countries");
            if (countries != null) for (String c : countries) {
                if (c != null && c.length() == 2)
                    jdbc.update("INSERT INTO zone_countries (zone_id, country_code) VALUES (?::uuid, ?) ON CONFLICT DO NOTHING", id, c);
            }
            return Map.of("id", id);
        }

        @PutMapping("/{id}")
        @org.springframework.transaction.annotation.Transactional
        public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
            jdbc.update("""
                UPDATE zones SET code = coalesce(?, code), name = coalesce(?, name),
                                 pinyin = coalesce(?, pinyin), sort_order = coalesce(?, sort_order),
                                 remark = coalesce(?, remark)
                WHERE id = ?::uuid
                """, body.get("code"), body.get("name"), body.get("pinyin"),
                body.get("sortOrder"), body.get("remark"), id);
            @SuppressWarnings("unchecked")
            List<String> countries = (List<String>) body.get("countries");
            if (countries != null) {
                jdbc.update("DELETE FROM zone_countries WHERE zone_id = ?::uuid", id);
                for (String c : countries) {
                    if (c != null && c.length() == 2)
                        jdbc.update("INSERT INTO zone_countries (zone_id, country_code) VALUES (?::uuid, ?)", id, c);
                }
            }
            return Map.of("id", id, "updated", true);
        }

        @DeleteMapping("/{id}")
        public Map<String, Object> delete(@PathVariable String id) {
            int n = jdbc.update("DELETE FROM zones WHERE id = ?::uuid", id);
            if (n == 0) throw ApiException.notFound("分区不存在: " + id);
            return Map.of("id", id, "deleted", true);
        }

        /** 反查: 给定国家码 → 哪个 zone (RateEngine 用) */
        @GetMapping("/find-by-country")
        public Map<String, Object> findByCountry(@RequestParam String country) {
            try {
                Map<String, Object> r = jdbc.queryForMap("""
                    SELECT z.id::text, z.code, z.name FROM zones z
                    JOIN zone_countries zc ON zc.zone_id = z.id
                    WHERE zc.country_code = ?
                    LIMIT 1
                    """, country);
                return Map.of("zone", r);
            } catch (DataAccessException ex) {
                return Map.of("zone", (Object) null);
            }
        }
    }

    // ════════ M7: surcharges 附加费规则 ════════
    @RestController
    @RequestMapping("/api/acc/surcharges")
    public static class Surcharges {
        private final JdbcTemplate jdbc;
        private final JsonSupport json;
        public Surcharges(JdbcTemplate jdbc, JsonSupport json) { this.jdbc = jdbc; this.json = json; }

        @GetMapping
        public Map<String, Object> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String rateCardId,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
            int offset = Math.max(0, page - 1) * pageSize;
            StringBuilder where = new StringBuilder(" WHERE 1=1 ");
            List<Object> args = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                where.append(" AND (code ILIKE ? OR name ILIKE ?) ");
                args.add("%"+keyword+"%"); args.add("%"+keyword+"%");
            }
            if (rateCardId != null && !rateCardId.isBlank()) {
                where.append(" AND rate_card_id = ?::uuid ");
                args.add(rateCardId);
            }
            args.add(pageSize); args.add(offset);
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id::text, code, name, short_name, rate_card_id::text, fee_item_id::text,
                       method, operate, actual, formula, currency, active,
                       effective_from, effective_to, audit_status, created_at
                FROM surcharges
                """ + where + " ORDER BY code LIMIT ? OFFSET ?", args.toArray());
            args.subList(args.size()-2, args.size()).clear();
            Long total = jdbc.queryForObject("SELECT count(*) FROM surcharges " + where, Long.class, args.toArray());
            return Map.of("data", rows.stream().map(r -> {
                Map<String, Object> o = new LinkedHashMap<>(r);
                o.put("effective_from", json.value(r.get("effective_from")));
                o.put("effective_to", json.value(r.get("effective_to")));
                o.put("created_at", json.value(r.get("created_at")));
                return o;
            }).toList(), "total", total);
        }

        @PostMapping
        public Map<String, Object> create(@RequestBody Map<String, Object> body) {
            String code = (String) body.get("code"); String name = (String) body.get("name");
            String method = (String) body.get("method");
            if (code == null || code.isBlank()) throw ApiException.badRequest("code 必填");
            if (name == null || name.isBlank()) throw ApiException.badRequest("name 必填");
            if (method == null) throw ApiException.badRequest("method 必填 (FIXED/PER_KG/PER_PIECE/PERCENT_AR/PERCENT_VALUE/FORMULA)");
            if ("FORMULA".equals(method) && (body.get("formula") == null || ((String) body.get("formula")).isBlank()))
                throw ApiException.badRequest("method=FORMULA 时 formula 必填");
            String id = jdbc.queryForObject("""
                INSERT INTO surcharges (code, name, short_name, rate_card_id, fee_item_id,
                                        method, operate, actual, formula, currency, active)
                VALUES (?, ?, ?, ?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?)
                RETURNING id::text
                """, String.class, code, name, body.get("shortName"),
                body.get("rateCardId"), body.get("feeItemId"),
                method, body.getOrDefault("operate", "ADD"),
                body.getOrDefault("actual", 0), body.get("formula"),
                body.getOrDefault("currency", "CNY"), body.getOrDefault("active", true));
            return Map.of("id", id);
        }

        @PutMapping("/{id}")
        public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
            jdbc.update("""
                UPDATE surcharges SET
                  code = coalesce(?, code), name = coalesce(?, name), short_name = coalesce(?, short_name),
                  method = coalesce(?, method), operate = coalesce(?, operate),
                  actual = coalesce(?, actual), formula = coalesce(?, formula),
                  currency = coalesce(?, currency), active = coalesce(?, active)
                WHERE id = ?::uuid
                """, body.get("code"), body.get("name"), body.get("shortName"),
                body.get("method"), body.get("operate"), body.get("actual"),
                body.get("formula"), body.get("currency"), body.get("active"), id);
            return Map.of("id", id, "updated", true);
        }

        @DeleteMapping("/{id}")
        public Map<String, Object> delete(@PathVariable String id) {
            int n = jdbc.update("DELETE FROM surcharges WHERE id = ?::uuid", id);
            if (n == 0) throw ApiException.notFound("附加费规则不存在: " + id);
            return Map.of("id", id, "deleted", true);
        }
    }

    // ════════ M8: insurance_rates 保险费率 ════════
    @RestController
    @RequestMapping("/api/acc/insurance-rates")
    public static class InsuranceRates {
        private final JdbcTemplate jdbc;
        private final JsonSupport json;
        public InsuranceRates(JdbcTemplate jdbc, JsonSupport json) { this.jdbc = jdbc; this.json = json; }

        @GetMapping
        public Map<String, Object> list(
            @RequestParam(required = false) String channelId,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
            int offset = Math.max(0, page - 1) * pageSize;
            String filter = channelId != null && !channelId.isBlank()
                ? "WHERE channel_id = ?::uuid OR channel_id IS NULL" : "";
            Object[] args = filter.isEmpty()
                ? new Object[]{pageSize, offset}
                : new Object[]{channelId, pageSize, offset};
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id::text, channel_id::text, currency, rate, free_coverage,
                       min_fee, max_fee, max_coverage, effective_from, effective_to,
                       active, remark, created_at
                FROM insurance_rates
                """ + filter + " ORDER BY channel_id NULLS LAST, effective_from DESC LIMIT ? OFFSET ?", args);
            Long total = jdbc.queryForObject(
                "SELECT count(*) FROM insurance_rates " + filter,
                Long.class, filter.isEmpty() ? new Object[0] : new Object[]{channelId});
            return Map.of("data", rows.stream().map(r -> {
                Map<String, Object> o = new LinkedHashMap<>(r);
                o.put("effective_from", json.value(r.get("effective_from")));
                o.put("effective_to", json.value(r.get("effective_to")));
                o.put("created_at", json.value(r.get("created_at")));
                return o;
            }).toList(), "total", total);
        }

        @PostMapping
        public Map<String, Object> create(@RequestBody Map<String, Object> body) {
            if (body.get("rate") == null) throw ApiException.badRequest("rate (保险费率千分比) 必填");
            String id = jdbc.queryForObject("""
                INSERT INTO insurance_rates (channel_id, currency, rate, free_coverage,
                                              min_fee, max_fee, max_coverage,
                                              effective_from, effective_to, active, remark)
                VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?::date, ?::date, ?, ?)
                RETURNING id::text
                """, String.class, body.get("channelId"),
                body.getOrDefault("currency", "CNY"),
                body.get("rate"),
                body.getOrDefault("freeCoverage", 0),
                body.getOrDefault("minFee", 0), body.getOrDefault("maxFee", 0),
                body.getOrDefault("maxCoverage", 0),
                body.getOrDefault("effectiveFrom", java.time.LocalDate.now().toString()),
                body.get("effectiveTo"),
                body.getOrDefault("active", true),
                body.get("remark"));
            return Map.of("id", id);
        }

        @PutMapping("/{id}")
        public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
            jdbc.update("""
                UPDATE insurance_rates SET
                  rate = coalesce(?, rate),
                  free_coverage = coalesce(?, free_coverage),
                  min_fee = coalesce(?, min_fee), max_fee = coalesce(?, max_fee),
                  max_coverage = coalesce(?, max_coverage),
                  active = coalesce(?, active), remark = coalesce(?, remark)
                WHERE id = ?::uuid
                """, body.get("rate"), body.get("freeCoverage"),
                body.get("minFee"), body.get("maxFee"), body.get("maxCoverage"),
                body.get("active"), body.get("remark"), id);
            return Map.of("id", id, "updated", true);
        }

        @DeleteMapping("/{id}")
        public Map<String, Object> delete(@PathVariable String id) {
            int n = jdbc.update("DELETE FROM insurance_rates WHERE id = ?::uuid", id);
            if (n == 0) throw ApiException.notFound("保险费率不存在: " + id);
            return Map.of("id", id, "deleted", true);
        }

        /** 报价 endpoint: 给定 channel + 货值 + 币种 → 算保费 (RateEngine 接入) */
        @GetMapping("/quote")
        public Map<String, Object> quote(
            @RequestParam(required = false) String channelId,
            @RequestParam(defaultValue = "CNY") String currency,
            @RequestParam Double declaredValue) {
            if (declaredValue == null || declaredValue <= 0)
                throw ApiException.badRequest("declaredValue 必须 > 0");
            // 优先匹 channel 专属费率, 找不到回 NULL channel 兜底
            List<Map<String, Object>> rates = jdbc.queryForList("""
                SELECT rate, free_coverage, min_fee, max_fee, max_coverage
                FROM insurance_rates
                WHERE active = true
                  AND currency = ?
                  AND (channel_id = ?::uuid OR channel_id IS NULL)
                  AND effective_from <= current_date
                  AND (effective_to IS NULL OR effective_to >= current_date)
                ORDER BY channel_id NULLS LAST LIMIT 1
                """, currency, channelId);
            if (rates.isEmpty())
                return Map.of("quote", 0, "applicable", false, "reason", "无适用保险费率");
            Map<String, Object> r = rates.get(0);
            double rate = ((java.math.BigDecimal) r.get("rate")).doubleValue();
            double freeCoverage = ((java.math.BigDecimal) r.get("free_coverage")).doubleValue();
            double minFee = ((java.math.BigDecimal) r.get("min_fee")).doubleValue();
            double maxFee = ((java.math.BigDecimal) r.get("max_fee")).doubleValue();
            double maxCov = ((java.math.BigDecimal) r.get("max_coverage")).doubleValue();
            if (maxCov > 0 && declaredValue > maxCov)
                return Map.of("quote", 0, "applicable", false, "reason", "货值超过最高保额 " + maxCov);
            double coverable = Math.max(0, declaredValue - freeCoverage);
            double quote = coverable * rate / 1000.0;
            if (minFee > 0 && quote < minFee) quote = minFee;
            if (maxFee > 0 && quote > maxFee) quote = maxFee;
            return Map.of(
                "quote", Math.round(quote * 100) / 100.0,
                "applicable", true,
                "rate", rate, "freeCoverage", freeCoverage,
                "minFee", minFee, "maxFee", maxFee
            );
        }
    }
}

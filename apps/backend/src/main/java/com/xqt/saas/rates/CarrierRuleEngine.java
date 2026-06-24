package com.xqt.saas.rates;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * W1 deep-research 落地 — Carrier Rule 抽象服务.
 *
 * 调研要求:
 *   - UPS 燃油 = (base + sum(accessorial in 白名单)) × bucket_pct(week_start)
 *   - DIM divisor 可热更新 (不写死 139 / 6000)
 *   - Accessorial 固定附加费 (Residential/Saturday/Signature/etc) 数据驱动
 *   - 超大件/大包裹/超长 阈值数据驱动
 *
 * 用法 (在 RateEngine 调):
 *   CarrierRuleEngine ce = ...;
 *   List<ChargeItem> surcharges = ce.computeSurcharges("UPS", "GROUND_US",
 *       baseFreight, weightLb, indicators);
 *   ChargeItem fuel = ce.computeFuel("UPS", "GROUND_US", baseFreight, surcharges);
 *   List<ChargeItem> allCharges = [base, fuel, ...surcharges];
 */
@Service
public class CarrierRuleEngine {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    public CarrierRuleEngine(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 取当前生效的 DIM divisor (in³/lb 或 cm³/kg) */
    public BigDecimal getDimDivisor(String carrier, String productClass, String unit) {
        Map<String, Object> row = findActiveRule(carrier, productClass, "DIM_DIVISOR", null);
        if (row == null) {
            // Fallback: 6000 cm³/kg (UPS 国际标准, 等价 ~166 in³/lb)
            return "IN-LBS".equals(unit) ? new BigDecimal("166") : new BigDecimal("6000");
        }
        JsonNode v = parse(row.get("value_json"));
        return new BigDecimal(v.get("divisor").asText());
    }

    /** 取燃油百分比 (当前周) */
    public BigDecimal getFuelPercent(String carrier, String productClass) {
        // 用当前 ISO week 作为 key (e.g. "2026-W26")
        String weekKey = currentWeekKey();
        Map<String, Object> row = findActiveRule(carrier, productClass, "FUEL_BUCKET", weekKey);
        if (row == null) {
            // Fallback: 找该 product 最新一周
            row = findLatestRule(carrier, productClass, "FUEL_BUCKET");
        }
        if (row == null) return BigDecimal.ZERO;
        JsonNode v = parse(row.get("value_json"));
        return new BigDecimal(v.get("pct").asText());
    }

    /** 燃油作用范围白名单 (哪些 charge code 纳入燃油基数) */
    public Set<String> getFuelApplicableCodes(String carrier) {
        Map<String, Object> row = findActiveRule(carrier, null, "FUEL_APPLICABLE_CODES", null);
        if (row == null) return Set.of();
        JsonNode v = parse(row.get("value_json"));
        JsonNode codes = v.get("codes");
        Set<String> set = new HashSet<>();
        if (codes != null && codes.isArray()) codes.forEach(n -> set.add(n.asText()));
        return set;
    }

    /** 取固定附加费 (e.g. RESIDENTIAL) */
    public RateQuoteResponse.ChargeItem getAccessorial(String carrier, String productClass, String accessorialKey) {
        Map<String, Object> row = findActiveRule(carrier, productClass, "ACCESSORIAL", accessorialKey);
        if (row == null) return null;
        JsonNode v = parse(row.get("value_json"));
        return new RateQuoteResponse.ChargeItem(
            accessorialKey,
            v.has("code") ? v.get("code").asText() : null,
            v.has("name") ? v.get("name").asText() : accessorialKey,
            new BigDecimal(v.get("amount").asText()),
            v.has("currency") ? v.get("currency").asText() : "USD",
            v.has("basis") ? v.get("basis").asText() : "fixed",
            List.of(), true, false
        );
    }

    /** 计算所有触发的 surcharges (按 indicators) */
    public List<RateQuoteResponse.ChargeItem> computeSurcharges(
            String carrier, String productClass, RateQuoteRequest req) {
        List<RateQuoteResponse.ChargeItem> result = new ArrayList<>();
        if (Boolean.TRUE.equals(req.residentialAddress())) {
            addIfPresent(result, getAccessorial(carrier, productClass, "RESIDENTIAL"));
        }
        if (Boolean.TRUE.equals(req.largePackage())) {
            addIfPresent(result, getAccessorial(carrier, productClass, "LARGE_PACKAGE"));
        }
        if (Boolean.TRUE.equals(req.additionalHandling())) {
            addIfPresent(result, getAccessorial(carrier, productClass, "ADDITIONAL_HANDLING"));
        }
        if ("ADULT".equalsIgnoreCase(req.signatureType())) {
            addIfPresent(result, getAccessorial(carrier, productClass, "SIGNATURE_ADULT"));
        } else if ("STANDARD".equalsIgnoreCase(req.signatureType())) {
            addIfPresent(result, getAccessorial(carrier, productClass, "SIGNATURE_STANDARD"));
        }
        if (Boolean.TRUE.equals(req.saturdayDelivery())) {
            addIfPresent(result, getAccessorial(carrier, productClass, "SATURDAY_DELIVERY"));
        }
        return result;
    }

    /**
     * 计算燃油 charge (UPS 算法).
     *
     * fuel = (baseFreight + sum(surcharges where code in 白名单)) × fuel_pct
     */
    public RateQuoteResponse.ChargeItem computeFuel(String carrier, String productClass,
                                                     BigDecimal baseFreight,
                                                     List<RateQuoteResponse.ChargeItem> surcharges,
                                                     String currency) {
        BigDecimal fuelPct = getFuelPercent(carrier, productClass);
        if (fuelPct == null || fuelPct.signum() == 0) {
            return new RateQuoteResponse.ChargeItem(
                "FUEL", "FUEL", "燃油附加费 (无规则)", BigDecimal.ZERO, currency,
                "no rule", List.of(), true, false);
        }
        Set<String> applicable = getFuelApplicableCodes(carrier);
        BigDecimal fuelBase = baseFreight;
        List<String> appliedTo = new ArrayList<>();
        appliedTo.add("BASE");
        for (RateQuoteResponse.ChargeItem c : surcharges) {
            if (applicable.contains(c.type()) || applicable.contains(c.code())) {
                fuelBase = fuelBase.add(c.amount());
                appliedTo.add(c.code() != null ? c.code() : c.type());
            }
        }
        BigDecimal fuelAmount = fuelBase.multiply(fuelPct).setScale(2, RoundingMode.HALF_UP);
        String basis = String.format("(%s base+accessorials) × %.4f%%",
            fuelBase.toPlainString(), fuelPct.doubleValue() * 100);
        return new RateQuoteResponse.ChargeItem(
            "FUEL", "FUEL", "燃油附加费", fuelAmount, currency, basis,
            appliedTo, true, false);
    }

    /** 检测包裹是否超大件 (返回触发的 surcharge type, 或 null) */
    public String detectOversize(String carrier, String productClass,
                                  BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm,
                                  BigDecimal weightKg) {
        // 先检 OVERSIZE (绝对上限, 超就拒单)
        Map<String, Object> oversize = findActiveRule(carrier, productClass, "OVERSIZE_THRESHOLD", null);
        if (oversize != null) {
            JsonNode v = parse(oversize.get("value_json"));
            BigDecimal maxEdgeIn = new BigDecimal(v.get("max_edge_in").asText());
            BigDecimal maxGirthIn = new BigDecimal(v.get("max_girth_plus_length_in").asText());
            BigDecimal lIn = cmToIn(lengthCm), wIn = cmToIn(widthCm), hIn = cmToIn(heightCm);
            BigDecimal longest = lIn.max(wIn).max(hIn);
            BigDecimal girth = lIn.add(wIn.multiply(new BigDecimal("2"))).add(hIn.multiply(new BigDecimal("2")));
            if (longest.compareTo(maxEdgeIn) > 0 || girth.compareTo(maxGirthIn) > 0) {
                return "OVERSIZE";
            }
        }
        // 再检 LARGE_PACKAGE
        Map<String, Object> large = findActiveRule(carrier, productClass, "LARGE_PACKAGE_THRESHOLD", null);
        if (large != null) {
            JsonNode v = parse(large.get("value_json"));
            BigDecimal maxEdgeIn = new BigDecimal(v.get("max_edge_in").asText());
            BigDecimal maxGirthIn = new BigDecimal(v.get("max_girth_plus_length_in").asText());
            BigDecimal lIn = cmToIn(lengthCm), wIn = cmToIn(widthCm), hIn = cmToIn(heightCm);
            BigDecimal longest = lIn.max(wIn).max(hIn);
            BigDecimal girth = lIn.add(wIn.multiply(new BigDecimal("2"))).add(hIn.multiply(new BigDecimal("2")));
            if (longest.compareTo(maxEdgeIn) > 0 || girth.compareTo(maxGirthIn) > 0) {
                return "LARGE_PACKAGE";
            }
        }
        // 再检 Additional Handling
        Map<String, Object> addH = findActiveRule(carrier, productClass, "ADDITIONAL_HANDLING_THRESHOLD", null);
        if (addH != null) {
            JsonNode v = parse(addH.get("value_json"));
            BigDecimal maxEdgeIn = new BigDecimal(v.get("max_edge_in").asText());
            BigDecimal maxWeightLb = v.has("max_weight_lb")
                ? new BigDecimal(v.get("max_weight_lb").asText()) : new BigDecimal("50");
            BigDecimal lIn = cmToIn(lengthCm), wIn = cmToIn(widthCm), hIn = cmToIn(heightCm);
            BigDecimal longest = lIn.max(wIn).max(hIn);
            BigDecimal weightLb = weightKg.multiply(new BigDecimal("2.20462"));
            if (longest.compareTo(maxEdgeIn) > 0 || weightLb.compareTo(maxWeightLb) > 0) {
                return "ADDITIONAL_HANDLING";
            }
        }
        return null;
    }

    // ════════ helpers ════════

    private Map<String, Object> findActiveRule(String carrier, String productClass, String ruleType, String key) {
        try {
            return jdbc.queryForMap("""
                SELECT value_json, effective_from FROM carrier_rules
                 WHERE carrier = ?
                   AND (?::text IS NULL OR product_class = ?::text OR product_class IS NULL)
                   AND rule_type = ?
                   AND (?::text IS NULL OR key = ?::text OR key IS NULL)
                   AND active = true
                   AND (effective_from IS NULL OR effective_from <= now())
                   AND (effective_to IS NULL OR effective_to >= now())
                 ORDER BY
                   CASE WHEN product_class = ?::text THEN 0 ELSE 1 END,
                   priority DESC,
                   effective_from DESC
                 LIMIT 1
                """, carrier, productClass, productClass, ruleType, key, key, productClass);
        } catch (org.springframework.dao.DataAccessException ex) {
            return null;
        }
    }

    private Map<String, Object> findLatestRule(String carrier, String productClass, String ruleType) {
        try {
            return jdbc.queryForMap("""
                SELECT value_json FROM carrier_rules
                 WHERE carrier = ? AND (product_class = ? OR product_class IS NULL)
                   AND rule_type = ? AND active = true
                 ORDER BY effective_from DESC LIMIT 1
                """, carrier, productClass, ruleType);
        } catch (org.springframework.dao.DataAccessException ex) {
            return null;
        }
    }

    private JsonNode parse(Object jsonObj) {
        try {
            String s = jsonObj == null ? "{}" : jsonObj.toString();
            return json.readTree(s);
        } catch (Exception ex) {
            return json.createObjectNode();
        }
    }

    private static BigDecimal cmToIn(BigDecimal cm) {
        if (cm == null) return BigDecimal.ZERO;
        return cm.divide(new BigDecimal("2.54"), 1, RoundingMode.HALF_UP);
    }

    private static void addIfPresent(List<RateQuoteResponse.ChargeItem> list, RateQuoteResponse.ChargeItem item) {
        if (item != null) list.add(item);
    }

    private static String currentWeekKey() {
        java.time.LocalDate now = java.time.LocalDate.now();
        int week = now.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear());
        return String.format("%d-W%02d", now.getYear(), week);
    }
}

package com.xqt.saas.acc;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import com.xqt.saas.common.JsonSupport;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

/**
 * Sprint 1 剩余 P0/P1/P2 一次性实装:
 *   R-6  cost_pre_estimates   预估报价池 CRUD
 *   R-12 customer_rate_strategies  客户价格策略 CRUD
 *   R-13 charge_items         费用类目 CRUD (前端可建删, 不再只靠 PG)
 */
@RestController
public class AccSprint1RemainingController {
    private final JdbcTemplate jdbc;
    private final JsonSupport json;

    public AccSprint1RemainingController(JdbcTemplate jdbc, JsonSupport json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    // ─────────── R-13: charge_items CRUD ───────────
    @GetMapping("/api/acc/charge-items")
    public Map<String, Object> listChargeItems(@RequestParam(required = false) String category) {
        String cat = (category == null || category.isBlank()) ? null : category;
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT id::text, code, name, category, currency, default_amount, active, created_at
              FROM charge_items
             WHERE (?::text IS NULL OR category = ?::text)
             ORDER BY category, code
            """, cat, cat);
        return Map.of("data", rows, "total", rows.size());
    }

    @PostMapping("/api/acc/charge-items")
    public Map<String, Object> createChargeItem(@RequestBody Map<String, Object> body) {
        String code = str(body.get("code"));
        String name = str(body.get("name"));
        String category = str(body.get("category"));
        String currency = (String) body.getOrDefault("currency", "CNY");
        BigDecimal defaultAmt = body.get("defaultAmount") == null ? null
            : new BigDecimal(body.get("defaultAmount").toString());
        if (code == null || name == null || category == null) {
            throw ApiException.badRequest("code / name / category 必填");
        }
        if (!List.of("FREIGHT","SURCHARGE","TAX","DISCOUNT","OTHER").contains(category)) {
            throw ApiException.badRequest("category 必须是 FREIGHT/SURCHARGE/TAX/DISCOUNT/OTHER");
        }
        try {
            String id = jdbc.queryForObject("""
                INSERT INTO charge_items (tenant_id, code, name, category, currency, default_amount, active)
                VALUES (current_setting('app.current_tenant_id')::uuid, ?, ?, ?, ?, ?, true)
                RETURNING id::text
                """, String.class, code, name, category, currency, defaultAmt);
            return Map.of("id", id, "code", code, "ok", true);
        } catch (DataAccessException ex) {
            throw ApiException.badRequest("创建失败 (code 可能重复): " + ex.getMessage());
        }
    }

    @DeleteMapping("/api/acc/charge-items/{id}")
    public Map<String, Object> deleteChargeItem(@PathVariable String id) {
        Integer using = jdbc.queryForObject(
            "SELECT count(*) FROM charges WHERE charge_item_id = ?::uuid", Integer.class, id);
        if (using != null && using > 0) {
            // 软删: 标 inactive
            jdbc.update("UPDATE charge_items SET active = false WHERE id = ?::uuid", id);
            return Map.of("id", id, "softDeleted", true, "usedBy", using);
        }
        jdbc.update("DELETE FROM charge_items WHERE id = ?::uuid", id);
        return Map.of("id", id, "deleted", true);
    }

    // ─────────── R-12: customer_rate_strategies CRUD ───────────
    @GetMapping("/api/acc/customer-rate-strategies")
    public Map<String, Object> listStrategies(@RequestParam(required = false) String customerCode,
                                                @RequestParam(required = false) String channelCode) {
        String cc = (customerCode == null || customerCode.isBlank()) ? null : customerCode;
        String chc = (channelCode == null || channelCode.isBlank()) ? null : channelCode;
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT crs.id::text,
                   c.code AS customer_code, c.name AS customer_name,
                   ch.code AS channel_code, ch.name AS channel_name,
                   rc.id::text AS base_rate_card_id, rc.side AS rate_card_side,
                   crs.commission_rate, crs.floor_amount,
                   crs.effective_from, crs.effective_to, crs.active, crs.remark
              FROM customer_rate_strategies crs
              JOIN customers c ON c.id = crs.customer_id
              JOIN channels ch ON ch.id = crs.channel_id
              LEFT JOIN rate_cards rc ON rc.id = crs.base_rate_card_id
             WHERE (?::text IS NULL OR c.code = ?::text)
               AND (?::text IS NULL OR ch.code = ?::text)
             ORDER BY crs.created_at DESC
             LIMIT 200
            """, cc, cc, chc, chc);
        return Map.of("data", rows, "total", rows.size());
    }

    @PostMapping("/api/acc/customer-rate-strategies")
    public Map<String, Object> createStrategy(@RequestBody Map<String, Object> body) {
        String customerCode = str(body.get("customerCode"));
        String channelCode = str(body.get("channelCode"));
        Double commissionRate = body.get("commissionRate") == null ? null
            : Double.parseDouble(body.get("commissionRate").toString());
        BigDecimal floor = body.get("floorAmount") == null ? null
            : new BigDecimal(body.get("floorAmount").toString());
        String remark = str(body.get("remark"));
        if (customerCode == null || channelCode == null || commissionRate == null) {
            throw ApiException.badRequest("customerCode / channelCode / commissionRate 必填");
        }
        if (commissionRate <= 0 || commissionRate > 10) {
            throw ApiException.badRequest("commissionRate 必须 0-10 之间 (1.0=原价, 0.9=9 折, 1.1=加 10%)");
        }
        // 找客户 + 渠道 id
        String customerId = jdbc.queryForObject(
            "SELECT id::text FROM customers WHERE code = ?", String.class, customerCode);
        String channelId = jdbc.queryForObject(
            "SELECT id::text FROM channels WHERE code = ?", String.class, channelCode);
        // 默认基础表 = 渠道下 AR 主表
        String baseRateCardId = null;
        try {
            baseRateCardId = jdbc.queryForObject(
                "SELECT id::text FROM rate_cards WHERE channel_id = ?::uuid AND side = 'AR' AND active = true LIMIT 1",
                String.class, channelId);
        } catch (DataAccessException ignored) {}
        String id = jdbc.queryForObject("""
            INSERT INTO customer_rate_strategies (
              customer_id, channel_id, base_rate_card_id, commission_rate, floor_amount, remark
            ) VALUES (?::uuid, ?::uuid, ?::uuid, ?, ?, ?)
            RETURNING id::text
            """, String.class, customerId, channelId, baseRateCardId,
            commissionRate, floor, remark);
        return Map.of("id", id, "customerCode", customerCode, "channelCode", channelCode,
            "commissionRate", commissionRate, "ok", true);
    }

    @DeleteMapping("/api/acc/customer-rate-strategies/{id}")
    public Map<String, Object> deleteStrategy(@PathVariable String id) {
        jdbc.update("UPDATE customer_rate_strategies SET active = false WHERE id = ?::uuid", id);
        return Map.of("id", id, "deactivated", true);
    }

    // ─────────── R-6: cost_pre_estimates CRUD ───────────
    @GetMapping("/api/acc/cost-pre-estimates")
    public Map<String, Object> listPreEstimates(@RequestParam(required = false) String status,
                                                  @RequestParam(required = false) String channelCode) {
        String st = (status == null || status.isBlank()) ? null : status;
        String cc = (channelCode == null || channelCode.isBlank()) ? null : channelCode;
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT cpe.id::text, cpe.channel_code, c.code AS customer_code,
                   cpe.qty, cpe.unit_price, cpe.currency, cpe.target_weight_kg,
                   cpe.effective_date, cpe.reconciled_count, cpe.status, cpe.remark,
                   cpe.created_at
              FROM cost_pre_estimates cpe
              LEFT JOIN customers c ON c.id = cpe.customer_id
             WHERE (?::text IS NULL OR cpe.status = ?::text)
               AND (?::text IS NULL OR cpe.channel_code = ?::text)
             ORDER BY cpe.created_at DESC
             LIMIT 200
            """, st, st, cc, cc);
        return Map.of("data", rows, "total", rows.size());
    }

    @PostMapping("/api/acc/cost-pre-estimates")
    public Map<String, Object> createPreEstimate(@RequestBody Map<String, Object> body) {
        String channelCode = str(body.get("channelCode"));
        String customerCode = str(body.get("customerCode"));
        Integer qty = body.get("qty") == null ? null : Integer.parseInt(body.get("qty").toString());
        BigDecimal unitPrice = body.get("unitPrice") == null ? null
            : new BigDecimal(body.get("unitPrice").toString());
        String currency = (String) body.getOrDefault("currency", "USD");
        BigDecimal weight = body.get("targetWeightKg") == null ? null
            : new BigDecimal(body.get("targetWeightKg").toString());
        if (channelCode == null || qty == null || unitPrice == null) {
            throw ApiException.badRequest("channelCode / qty / unitPrice 必填");
        }
        String customerId = null;
        if (customerCode != null) {
            try {
                customerId = jdbc.queryForObject(
                    "SELECT id::text FROM customers WHERE code = ?", String.class, customerCode);
            } catch (DataAccessException ignored) {}
        }
        String channelId = null;
        try {
            channelId = jdbc.queryForObject(
                "SELECT id::text FROM channels WHERE code = ?", String.class, channelCode);
        } catch (DataAccessException ignored) {}
        String id = jdbc.queryForObject("""
            INSERT INTO cost_pre_estimates (
              channel_id, channel_code, customer_id, qty, unit_price, currency,
              target_weight_kg, remark, status
            ) VALUES (?::uuid, ?, ?::uuid, ?, ?, ?, ?, ?, 'ACTIVE')
            RETURNING id::text
            """, String.class, channelId, channelCode, customerId, qty, unitPrice,
            currency, weight, str(body.get("remark")));
        return Map.of("id", id, "qty", qty, "unitPrice", unitPrice, "ok", true);
    }

    private static String str(Object o) {
        if (o == null) return null;
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }
}

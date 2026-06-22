package com.xqt.saas.acc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import com.xqt.saas.common.JsonSupport;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * /api/acc/customer-rate-cards — 修 P0-D1: 客户专价绑定后台
 *
 * 表早就建好 (029_rate_cards.sql), RateEngine 取价优先级 (客户专价 > 组价 > 公布价)
 * 也已实现, 但**无 Controller 无 UI** → 管理员永远绑不上客户专价 → Engine 永远查空.
 * 这个 Controller 把 customer_rate_cards 暴露出来, 让 admin 在销售中心绑定客户↔费率表.
 *
 * 字段: customer_id 必填, channel_id 可空 (全渠道), service_code 可空 (全产品),
 *      rate_card_id 必填, priority 0-1000 (越小越优先), effective_from/to.
 */
@RestController
@RequestMapping("/api/acc/customer-rate-cards")
public class AccCustomerRateCardsController {
    private final JdbcTemplate jdbc;
    private final JsonSupport json;

    public AccCustomerRateCardsController(JdbcTemplate jdbc, JsonSupport json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @GetMapping
    public Map<String, Object> list(
        @RequestParam(required = false, defaultValue = "1") Integer page,
        @RequestParam(required = false, defaultValue = "50") Integer pageSize,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String customerId
    ) {
        try {
            int limit = Math.max(1, Math.min(200, pageSize));
            int offset = Math.max(0, (page - 1) * limit);
            String search = keyword == null || keyword.isBlank() ? null : "%" + keyword + "%";
            String custFilter = customerId == null || customerId.isBlank() ? null : customerId;
            Long total = jdbc.queryForObject("""
                SELECT count(*) FROM customer_rate_cards rc
                LEFT JOIN customers c ON c.id = rc.customer_id
                WHERE (?::text IS NULL OR c.name ILIKE ? OR c.code ILIKE ?)
                  AND (?::uuid IS NULL OR rc.customer_id = ?::uuid)
                """, Long.class, search, search, search, custFilter, custFilter);
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT rc.id::text AS id,
                       rc.customer_id::text AS customer_id,
                       c.code AS customer_code,
                       c.name AS customer_name,
                       rc.channel_id::text AS channel_id,
                       ch.name AS channel_name,
                       rc.service_code,
                       rc.rate_card_id::text AS rate_card_id,
                       rcd.name AS rate_card_name,
                       rcd.currency AS rate_card_currency,
                       rc.priority,
                       rc.effective_from, rc.effective_to,
                       rc.active, rc.created_at
                FROM customer_rate_cards rc
                LEFT JOIN customers c ON c.id = rc.customer_id
                LEFT JOIN channels ch ON ch.id = rc.channel_id
                LEFT JOIN rate_cards rcd ON rcd.id = rc.rate_card_id
                WHERE (?::text IS NULL OR c.name ILIKE ? OR c.code ILIKE ?)
                  AND (?::uuid IS NULL OR rc.customer_id = ?::uuid)
                ORDER BY rc.customer_id, rc.priority, rc.effective_from DESC
                LIMIT ? OFFSET ?
                """, search, search, search, custFilter, custFilter, limit, offset);
            return Map.of("data", rows.stream().map(this::project).toList(),
                          "total", total == null ? 0 : total);
        } catch (DataAccessException ex) {
            return Map.of("data", List.of(), "total", 0);
        }
    }

    @GetMapping("/{id}/raw")
    public Map<String, Object> raw(@PathVariable String id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM customer_rate_cards WHERE id = ?::uuid LIMIT 1", id);
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String customerId = str(body.getOrDefault("customer_id", body.get("customerId")));
        String rateCardId = str(body.getOrDefault("rate_card_id", body.get("rateCardId")));
        if (customerId == null) throw ApiException.badRequest("customer_id 必填");
        if (rateCardId == null) throw ApiException.badRequest("rate_card_id 必填 (绑哪张费率表)");
        // 校验外键存在
        Integer custOk = jdbc.queryForObject("SELECT count(*) FROM customers WHERE id = ?::uuid", Integer.class, customerId);
        if (custOk == null || custOk == 0) throw ApiException.badRequest("customer_id 不存在");
        Integer rcOk = jdbc.queryForObject("SELECT count(*) FROM rate_cards WHERE id = ?::uuid", Integer.class, rateCardId);
        if (rcOk == null || rcOk == 0) throw ApiException.badRequest("rate_card_id 不存在");
        String channelId = str(body.getOrDefault("channel_id", body.get("channelId")));
        String serviceCode = str(body.get("serviceCode") != null ? body.get("serviceCode") : body.get("service_code"));
        Integer priority = body.get("priority") instanceof Number p ? p.intValue() : 100;
        String effFrom = str(body.getOrDefault("effective_from", body.get("effectiveFrom")));
        String effTo = str(body.getOrDefault("effective_to", body.get("effectiveTo")));
        if (effFrom == null) effFrom = java.time.LocalDate.now().toString();
        try {
            String id = jdbc.queryForObject("""
                INSERT INTO customer_rate_cards
                  (tenant_id, customer_id, channel_id, service_code, rate_card_id,
                   priority, effective_from, effective_to, active)
                VALUES (current_setting('app.current_tenant_id')::uuid,
                        ?::uuid, ?::uuid, ?, ?::uuid, ?, ?::date, ?::date, true)
                RETURNING id::text
                """, String.class,
                customerId, channelId, serviceCode, rateCardId, priority, effFrom, effTo);
            return Map.of("id", id);
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            throw ApiException.badRequest("该客户在此渠道/产品下已绑定相同费率表, 请改优先级或先解绑");
        }
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        Integer priority = body.get("priority") instanceof Number p ? p.intValue() : null;
        String effFrom = str(body.getOrDefault("effective_from", body.get("effectiveFrom")));
        String effTo = str(body.getOrDefault("effective_to", body.get("effectiveTo")));
        Object activeRaw = body.get("active");
        Boolean active = activeRaw instanceof Boolean b ? b : null;
        jdbc.update("""
            UPDATE customer_rate_cards SET
              priority = coalesce(?, priority),
              effective_from = coalesce(?::date, effective_from),
              effective_to = coalesce(?::date, effective_to),
              active = coalesce(?, active)
            WHERE id = ?::uuid
            """, priority, effFrom, effTo, active, id);
        return Map.of("id", id);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        jdbc.update("DELETE FROM customer_rate_cards WHERE id = ?::uuid", id);
        return Map.of("id", id, "deleted", true);
    }

    private Map<String, Object> project(Map<String, Object> r) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("id", r.get("id"));
        o.put("customerId", r.get("customer_id"));
        o.put("customerCode", r.get("customer_code"));
        o.put("customerName", r.get("customer_name"));
        o.put("channelId", r.get("channel_id"));
        o.put("channelName", r.get("channel_name") == null ? "(全渠道)" : r.get("channel_name"));
        o.put("serviceCode", r.get("service_code") == null ? "(全产品)" : r.get("service_code"));
        o.put("rateCardId", r.get("rate_card_id"));
        o.put("rateCardName", r.get("rate_card_name"));
        o.put("rateCardCurrency", r.get("rate_card_currency"));
        o.put("priority", r.get("priority"));
        o.put("effectiveFrom", json.value(r.get("effective_from")));
        o.put("effectiveTo", json.value(r.get("effective_to")));
        o.put("active", r.get("active"));
        o.put("createdAt", json.value(r.get("created_at")));
        return o;
    }

    private static String str(Object o) {
        if (o == null) return null;
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }
}

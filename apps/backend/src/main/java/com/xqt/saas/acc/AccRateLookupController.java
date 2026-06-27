package com.xqt.saas.acc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

/**
 * 价目表查询 — 类似 ACC 老的"价格查询"。
 *
 * 列出所有 active 渠道, 每个渠道的所有 rate_card_lines, 可按 zone/重量过滤。
 *
 * GET /api/acc/rate-lookup/channels     — 列出有价表的渠道
 * GET /api/acc/rate-lookup/lines/{channelCode}  — 取该渠道全量阶梯
 * GET /api/acc/rate-lookup/quote-simulate?channelCode=&weightKg=&postalCode=&warehouseCode=
 *        — 调试: 不真生成 quote, 只查命中的 tier (避免污染 rate_quotes 表)
 */
@RestController
@RequestMapping("/api/acc/rate-lookup")
public class AccRateLookupController {
    private final JdbcTemplate jdbc;

    public AccRateLookupController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/channels")
    public Map<String, Object> channels() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT c.code, c.name, c.lane, c.last_mile_method,
                   COUNT(l.id) FILTER (WHERE rc.status='ACTIVE') AS active_lines,
                   COUNT(DISTINCT l.zone_code) FILTER (WHERE rc.status='ACTIVE') AS zone_count,
                   MIN(l.unit_price) FILTER (WHERE rc.status='ACTIVE' AND l.unit_price > 0) AS min_price,
                   MAX(l.unit_price) FILTER (WHERE rc.status='ACTIVE' AND l.unit_price > 0) AS max_price
              FROM channels c
         LEFT JOIN rate_cards rc ON rc.channel_id = c.id
         LEFT JOIN rate_card_lines l ON l.rate_card_id = rc.id
             WHERE c.active = true
          GROUP BY c.code, c.name, c.lane, c.last_mile_method
            HAVING COUNT(l.id) FILTER (WHERE rc.status='ACTIVE') > 0
          ORDER BY c.lane, c.code
            """);
        return Map.of("data", rows);
    }

    @GetMapping("/lines/{channelCode}")
    public Map<String, Object> lines(@PathVariable String channelCode,
                                      @RequestParam(required = false) String zoneCode,
                                      @RequestParam(required = false) String warehouseCode) {
        List<Object> args = new ArrayList<>();
        args.add(channelCode);
        StringBuilder sql = new StringBuilder("""
            SELECT l.zone_code,
                   l.warehouse_code,
                   l.weight_from, l.weight_to,
                   l.uom::text AS uom,
                   l.unit_price, l.fixed_amount,
                   l.calculation_type,
                   l.postal_code_pattern,
                   l.min_amount,
                   l.metadata,
                   rc.version,
                   rc.effective_from
              FROM rate_card_lines l
              JOIN rate_cards rc ON rc.id = l.rate_card_id
              JOIN channels c ON c.id = rc.channel_id
             WHERE c.code = ? AND rc.status = 'ACTIVE'
            """);
        if (zoneCode != null && !zoneCode.isBlank()) {
            sql.append(" AND l.zone_code = ?");
            args.add(zoneCode);
        }
        if (warehouseCode != null && !warehouseCode.isBlank()) {
            sql.append(" AND l.warehouse_code ~ ('(^|[、./, ])' || ? || '($|[、./, ])')");
            args.add(warehouseCode);
        }
        sql.append(" ORDER BY l.zone_code, l.weight_from LIMIT 500");

        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());

        Map<String, Object> chan;
        try {
            chan = jdbc.queryForMap(
                "SELECT code, name, lane FROM channels WHERE code = ?", channelCode);
        } catch (Exception ex) {
            chan = Map.of("code", channelCode);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("channel", chan);
        out.put("lines", rows);
        out.put("total", rows.size());
        return out;
    }

    /** 不污染 rate_quotes: 直接查 rate_card_lines 命中的 tier. */
    @GetMapping("/quote-simulate")
    public Map<String, Object> quoteSimulate(@RequestParam String channelCode,
                                              @RequestParam double weightKg,
                                              @RequestParam(required = false) String postalCode,
                                              @RequestParam(required = false) String warehouseCode,
                                              @RequestParam(required = false) String countryCode) {
        Map<String, Object> chan;
        try {
            chan = jdbc.queryForMap(
                "SELECT id::text AS id, code, name, lane FROM channels WHERE code = ? AND active = true",
                channelCode);
        } catch (Exception ex) {
            return Map.of("ok", false, "error", "渠道不存在或已停用: " + channelCode);
        }
        String channelId = (String) chan.get("id");
        String lane = (String) chan.get("lane");

        String zone = resolveZone(lane, countryCode, postalCode, warehouseCode, channelId);
        if (zone == null) {
            return Map.of("ok", false, "error", "zone 解析失败 (lane=" + lane + ")");
        }

        try {
            Map<String, Object> tier = jdbc.queryForMap("""
                SELECT l.id::text AS line_id, l.zone_code, l.warehouse_code,
                       l.weight_from, l.weight_to, l.unit_price, l.fixed_amount,
                       l.calculation_type, l.uom::text AS uom,
                       rc.version, rc.effective_from, rc.currency
                  FROM rate_card_lines l
                  JOIN rate_cards rc ON rc.id = l.rate_card_id
                 WHERE rc.channel_id = ?::uuid AND rc.status = 'ACTIVE'
                   AND l.zone_code = ?
                   AND l.weight_from <= ?
                   AND (l.weight_to IS NULL OR l.weight_to > ?)
                 ORDER BY l.weight_from DESC LIMIT 1
                """, channelId, zone, weightKg, weightKg);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", true);
            result.put("channel", chan);
            result.put("zone", zone);
            result.put("weightKg", weightKg);
            result.put("tier", tier);
            return result;
        } catch (Exception ex) {
            return Map.of("ok", false, "channel", chan, "zone", zone,
                "weightKg", weightKg,
                "error", "未找到匹配的价格段");
        }
    }

    private String resolveZone(String lane, String country, String postcode, String warehouse, String channelId) {
        if ("US-OCEAN-EXPRESS".equals(lane)) {
            if ("CA".equals(country)) return canadaZone(postcode);
            return oceanExpressZone(postcode);
        }
        if ("US-OCEAN-TRUCK".equals(lane)) {
            if ("CA".equals(country)) return canadaZone(postcode);
            // 卡派需 warehouse_code, 在 rate_card_lines 模糊查
            if (warehouse == null || warehouse.isBlank()) return null;
            try {
                return jdbc.queryForObject("""
                    SELECT l.zone_code FROM rate_card_lines l
                      JOIN rate_cards rc ON rc.id = l.rate_card_id
                     WHERE rc.channel_id = ?::uuid AND rc.status='ACTIVE'
                       AND l.warehouse_code IS NOT NULL
                       AND l.warehouse_code ~ ('(^|[、./, ])' || ? || '($|[、./, ])')
                     LIMIT 1
                    """, String.class, channelId, warehouse);
            } catch (Exception e) { return null; }
        }
        // US-LAST-MILE 用 ups_zone_mappings (origin 917 默认)
        if (postcode == null || postcode.length() < 3) return "US-Z005";
        try {
            return jdbc.queryForObject("""
                SELECT zone_code FROM ups_zone_mappings
                 WHERE origin_prefix = '917' AND dest_prefix = ? AND service='GROUND' LIMIT 1
                """, String.class, postcode.substring(0, 3));
        } catch (Exception e) { return "US-Z005"; }
    }

    private static String oceanExpressZone(String pc) {
        if (pc == null || pc.isBlank()) return "USM";
        char c = pc.charAt(0);
        if (c == '8' || c == '9') return "USW";
        if (c == '4' || c == '5' || c == '6' || c == '7') return "USM";
        return "USE";
    }

    private static String canadaZone(String pc) {
        if (pc == null || pc.isBlank()) return "多伦多";
        char c = Character.toUpperCase(pc.charAt(0));
        if (c == 'K') return "渥太华";
        if (c == 'V') return "温哥华";
        if (c == 'T') return "卡尔加里";
        return "多伦多";
    }
}

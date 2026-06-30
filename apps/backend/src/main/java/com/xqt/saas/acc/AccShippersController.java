package com.xqt.saas.acc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * 发件人 (Shipper) 管理 — 独立 tab.
 *
 * 数据来源: acc_channel_accounts.shipper_* 字段 (按渠道账号一对一挂)
 *
 * 业务设计:
 *   - 一个渠道账号 (如 J602B0) 配一套发件人
 *   - 不同渠道账号可配不同发件人 (例: 美国仓 vs 欧洲仓不同)
 *   - 提交订单时, RateEngine 自动按 channel_account 拉对应 shipper
 *
 * 接口:
 *   GET    /api/acc/shippers                  列表 (所有有 shipper 的渠道账号)
 *   GET    /api/acc/shippers/{id}             单个详情 (id = channel_account_id)
 *   PUT    /api/acc/shippers/{id}             更新该渠道账号的 shipper 信息
 */
@RestController
@RequestMapping("/api/acc/shippers")
public class AccShippersController {
    private final JdbcTemplate jdbc;

    public AccShippersController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public Map<String, Object> list(
        @RequestParam(required = false, defaultValue = "100") Integer pageSize,
        @RequestParam(required = false, defaultValue = "1") Integer page,
        @RequestParam(required = false) String keyword
    ) {
        int limit = Math.min(pageSize == null ? 100 : pageSize, 500);
        int offset = (Math.max(page == null ? 1 : page, 1) - 1) * limit;

        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        java.util.List<Object> args = new java.util.ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (a.account_no ILIKE ? OR a.shipper_company ILIKE ? OR c.code ILIKE ? OR c.name ILIKE ?) ");
            String kw = "%" + keyword + "%";
            args.add(kw); args.add(kw); args.add(kw); args.add(kw);
        }
        args.add(limit);
        args.add(offset);

        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT a.id::text                AS id,
                   a.account_no              AS account_no,
                   c.code                    AS channel_code,
                   c.name                    AS channel_name,
                   a.provider_code           AS provider_code,
                   a.shipper_company         AS shipper_company,
                   a.shipper_name            AS shipper_name,
                   a.shipper_phone           AS shipper_phone,
                   a.shipper_email           AS shipper_email,
                   a.shipper_address1        AS shipper_address1,
                   a.shipper_address2        AS shipper_address2,
                   a.shipper_city            AS shipper_city,
                   a.shipper_state           AS shipper_state,
                   a.shipper_country2        AS shipper_country2,
                   a.shipper_postcode        AS shipper_postcode,
                   a.ups_service_type        AS ups_service_type,
                   a.is_active               AS is_active,
                   CASE
                     WHEN a.shipper_company IS NULL OR a.shipper_company = '' THEN '未配置'
                     WHEN a.shipper_phone IS NULL OR a.shipper_phone = '' THEN '缺电话'
                     ELSE '已配'
                   END                       AS status
              FROM acc_channel_accounts a
         LEFT JOIN channels c ON c.id = a.channel_id
            """ + where + " ORDER BY (a.shipper_company IS NULL), c.code, a.account_no LIMIT ? OFFSET ?",
            args.toArray());

        Long total = jdbc.queryForObject(
            "SELECT COUNT(*) FROM acc_channel_accounts a LEFT JOIN channels c ON c.id = a.channel_id"
            + where, Long.class,
            args.subList(0, args.size() - 2).toArray());

        return Map.of("data", rows, "total", total == null ? 0 : total);
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable String id) {
        try {
            return jdbc.queryForMap("""
                SELECT a.id::text AS id, a.account_no, c.code AS channel_code, c.name AS channel_name,
                       a.shipper_company, a.shipper_name, a.shipper_phone, a.shipper_email,
                       a.shipper_address1, a.shipper_address2, a.shipper_city,
                       a.shipper_state, a.shipper_country2, a.shipper_postcode,
                       a.ups_service_type, a.export_type, a.return_service, a.weight_unit
                  FROM acc_channel_accounts a
             LEFT JOIN channels c ON c.id = a.channel_id
                 WHERE a.id = ?::uuid
                """, id);
        } catch (Exception ex) {
            throw ApiException.notFound("发件人记录不存在: " + id);
        }
    }

    /** 更新发件人信息 — id 是 channel_account.id. */
    @PutMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        // 只更新 shipper_* 字段, 不动 api_key / provider_code 等
        int n = jdbc.update("""
            UPDATE acc_channel_accounts SET
                shipper_company  = ?,
                shipper_name     = ?,
                shipper_phone    = ?,
                shipper_email    = ?,
                shipper_address1 = ?,
                shipper_address2 = ?,
                shipper_city     = ?,
                shipper_state    = ?,
                shipper_country2 = ?,
                shipper_postcode = ?,
                ups_service_type = COALESCE(?, ups_service_type),
                export_type      = COALESCE(?, export_type),
                return_service   = COALESCE(?, return_service),
                weight_unit      = COALESCE(?, weight_unit)
             WHERE id = ?::uuid
            """,
            str(body.get("shipper_company")),
            str(body.get("shipper_name")),
            str(body.get("shipper_phone")),
            str(body.get("shipper_email")),
            str(body.get("shipper_address1")),
            str(body.get("shipper_address2")),
            str(body.get("shipper_city")),
            str(body.get("shipper_state")),
            str(body.get("shipper_country2")),
            str(body.get("shipper_postcode")),
            str(body.get("ups_service_type")),
            str(body.get("export_type")),
            str(body.get("return_service")),
            str(body.get("weight_unit")),
            id);

        if (n == 0) throw ApiException.notFound("发件人记录不存在: " + id);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("id", id);
        result.put("updated", n);
        return result;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}

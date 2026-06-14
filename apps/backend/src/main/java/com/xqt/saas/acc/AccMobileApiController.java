package com.xqt.saas.acc;

import java.util.List;
import java.util.Map;

import com.xqt.saas.auth.AuthPrincipal;
import com.xqt.saas.common.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 移动 API — 对齐 ACC Mobile.php。
 *
 * 给移动端 APP 用的精简数据接口，主要是 GET 类查询，POST 类提交在客户端轻量化。
 * 与 customer-portal/ 类似但响应更紧凑（适合移动流量）。
 *
 *   GET  /api/mobile/me              当前用户简要
 *   GET  /api/mobile/track?no=xxx    扫码查询轨迹
 *   GET  /api/mobile/balance         自己余额（多币种）
 *   POST /api/mobile/quick-scan      DWS 扫码接力（手机扫码上传）
 */
@RestController
@RequestMapping("/api/mobile")
public class AccMobileApiController {
    private final JdbcTemplate jdbc;

    public AccMobileApiController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/me")
    public Map<String, Object> me(Authentication auth) {
        AuthPrincipal p = (AuthPrincipal) auth.getPrincipal();
        return Map.of(
            "userId", p.userId(),
            "username", p.username(),
            "displayName", p.displayName(),
            "roles", p.roles(),
            "tenantCode", p.tenantCode()
        );
    }

    @GetMapping("/track")
    public Map<String, Object> track(@org.springframework.web.bind.annotation.RequestParam String no) {
        if (no == null || no.isBlank()) {
            throw ApiException.badRequest("追踪号必填");
        }
        // 查 carton 找 shipment 找 events
        List<Map<String, Object>> events = jdbc.queryForList("""
            SELECT te.event_code, te.event_time, te.location, te.description
              FROM cartons ct
              JOIN tracking_events te ON te.shipment_id = ct.shipment_id
             WHERE ct.tracking_no = ? OR ct.carrier_master_tracking_no = ?
             ORDER BY te.event_time DESC LIMIT 50
            """, no, no);
        if (events.isEmpty()) {
            throw ApiException.notFound("找不到该追踪号的物流信息: " + no);
        }
        return Map.of("trackingNo", no, "events", events, "total", events.size());
    }

    @GetMapping("/balance")
    public Map<String, Object> balance(Authentication auth) {
        AuthPrincipal p = (AuthPrincipal) auth.getPrincipal();
        // 从 bound_customer_id 找余额
        String cid;
        try {
            cid = jdbc.queryForObject(
                "SELECT bound_customer_id::text FROM users WHERE id = ?::uuid",
                String.class, p.userId());
        } catch (Exception ex) {
            throw ApiException.unauthorized("账号无效");
        }
        if (cid == null || cid.isBlank()) {
            throw ApiException.unauthorized("当前账号未绑定客户");
        }
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT currency, balance, account_name FROM financial_accounts
             WHERE owner_type='CUSTOMER' AND owner_id = ?::uuid
            """, cid);
        return Map.of("data", rows);
    }

    @PostMapping("/quick-scan")
    public Map<String, Object> quickScan(@RequestBody Map<String, Object> body) {
        String itemNo = body.get("itemNo") == null ? null : body.get("itemNo").toString();
        if (itemNo == null || itemNo.isBlank()) {
            throw ApiException.badRequest("itemNo 必填");
        }
        // 简化版扫码：写入 acc_dws_scans 标 mobile 来源
        try {
            String id = jdbc.queryForObject("""
                INSERT INTO acc_dws_scans (
                  tenant_id, item_number, action, status, raw_payload
                ) VALUES (
                  current_setting('app.current_tenant_id')::uuid, ?, 'check', 'OK',
                  jsonb_build_object('source', 'mobile', 'received_at', now()::text)
                )
                RETURNING id::text
                """, String.class, itemNo);
            return Map.of("id", id, "itemNo", itemNo, "received", true);
        } catch (Exception ex) {
            throw ApiException.badRequest("扫码记录失败: " + ex.getMessage());
        }
    }
}

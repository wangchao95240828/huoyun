package com.xqt.saas.acc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import com.xqt.saas.common.JsonSupport;
import com.xqt.saas.framework.cascade.CascadeChecker;
import com.xqt.saas.framework.fieldgate.FieldGate;
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

@RestController
@RequestMapping("/api/acc/channel-accounts")
public class AccChannelAccountsController {
    private static final String TABLE = "acc_channel_accounts";

    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final CascadeChecker cascadeChecker;
    private final FieldGate fieldGate;

    public AccChannelAccountsController(JdbcTemplate jdbc, JsonSupport json,
                                         CascadeChecker cascadeChecker, FieldGate fieldGate) {
        this.jdbc = jdbc;
        this.json = json;
        this.cascadeChecker = cascadeChecker;
        this.fieldGate = fieldGate;
    }

    @GetMapping
    public Map<String, Object> list(
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer pageSize,
        @RequestParam(required = false) String keyword
    ) {
        try {
            int limit = AccPaging.pageSize(pageSize);
            int offset = AccPaging.offset(page, pageSize);
            String search = keyword == null || keyword.isBlank() ? null : "%" + keyword + "%";

            long total = json.value(jdbc.queryForObject(
                "SELECT count(*) FROM acc_channel_accounts WHERE ?::text IS NULL OR account_name ILIKE ?",
                Long.class, search, search)) instanceof Number n ? n.longValue() : 0;
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT ca.id::text AS id, ca.channel_id::text AS channel_id, ca.partner_id::text AS partner_id,
                       ca.account_no, ca.account_name, ca.endpoint_url, ca.is_active, ca.remark,
                       ch.name AS channel_name, p.name AS partner_name,
                       ca.audit_status, ca.audited_at, ca.audit_name, ca.created_at
                FROM acc_channel_accounts ca
                LEFT JOIN channels ch ON ch.id = ca.channel_id
                LEFT JOIN partners p ON p.id = ca.partner_id
                WHERE ?::text IS NULL OR ca.account_name ILIKE ?
                ORDER BY ca.account_name
                LIMIT ? OFFSET ?
                """, search, search, limit, offset);
            return AccPaging.result(rows.stream().map(this::project).toList(), total);
        } catch (DataAccessException ex) {
            return AccPaging.result(List.of(), 0);
        }
    }

    @GetMapping("/{id}/raw")
    public Map<String, Object> raw(@PathVariable String id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM acc_channel_accounts WHERE id = ?::uuid LIMIT 1", id);
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String accountName = (String) body.get("accountName");
        if (accountName == null || accountName.isBlank()) {
            throw ApiException.badRequest("账户名称必填");
        }
        Object chId = body.get("channel_id");
        if (chId == null || chId.toString().isBlank()) {
            throw ApiException.badRequest("请选择渠道");
        }
        // ACC ChannelAccount.php L218: 渠道未启用拦截
        Boolean active = jdbc.queryForObject(
            "SELECT active FROM channels WHERE id = ?::uuid", Boolean.class, chId.toString());
        if (Boolean.FALSE.equals(active)) {
            throw ApiException.badRequest("该渠道暂未启用");
        }
        String id = jdbc.queryForObject("""
            INSERT INTO acc_channel_accounts (tenant_id, channel_id, partner_id, account_no,
                                              account_name, api_key, api_secret, endpoint_url,
                                              is_active, remark)
            VALUES (current_setting('app.current_tenant_id')::uuid, ?::uuid, ?::uuid, ?::text,
                    ?, ?::text, ?::text, ?::text, ?::boolean, ?::text)
            RETURNING id::text
            """, String.class, body.get("channelId"), body.get("partnerId"),
            body.get("accountNo"), accountName, body.get("apiKey"), body.get("apiSecret"),
            body.get("endpointUrl"), body.get("isActive"), body.get("remark"));
        return Map.of("id", id, "accountName", accountName);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String currentAudit = jdbc.queryForObject(
            "SELECT audit_status FROM acc_channel_accounts WHERE id = ?::uuid", String.class, id);
        // R-5: processing_fee 即使账号已审核也可改 (业务上需求动态调整)
        // 从 body 把 processing_fee 提前抠出来, 不进 fieldGate 锁定检查
        Object processingFeeRaw = body.containsKey("processing_fee") ? body.get("processing_fee")
                                                                       : body.get("processingFee");
        Map<String, Object> bodyWithoutPF = new java.util.LinkedHashMap<>(body);
        bodyWithoutPF.remove("processing_fee");
        bodyWithoutPF.remove("processingFee");
        FieldGate.FilterResult gate = fieldGate.filterAllowedFields(TABLE, currentAudit, bodyWithoutPF);
        if (!gate.rejected().isEmpty() && gate.allowed().isEmpty() && processingFeeRaw == null) {
            throw ApiException.badRequest("渠道账号已审核，字段不可修改: " + String.join(",", gate.rejected())
                + "；请先反审");
        }
        Map<String, Object> allowed = gate.allowed();
        // R-5: 操作费 processing_fee 支持编辑 (允许 0, 不能负数)
        java.math.BigDecimal processingFee = null;
        if (processingFeeRaw != null) {
            try {
                processingFee = new java.math.BigDecimal(processingFeeRaw.toString());
                if (processingFee.signum() < 0) throw ApiException.badRequest("操作费不能为负数");
            } catch (NumberFormatException ex) {
                throw ApiException.badRequest("操作费必须为数字");
            }
        }
        jdbc.update("""
            UPDATE acc_channel_accounts SET
              account_name = coalesce(?, account_name),
              api_key = coalesce(?::text, api_key),
              api_secret = coalesce(?::text, api_secret),
              endpoint_url = coalesce(?::text, endpoint_url),
              is_active = coalesce(?::boolean, is_active),
              processing_fee = coalesce(?::numeric, processing_fee),
              remark = coalesce(?::text, remark)
            WHERE id = ?::uuid
            """, (String) allowed.get("accountName"), (String) allowed.get("apiKey"),
            (String) allowed.get("apiSecret"), (String) allowed.get("endpointUrl"),
            allowed.get("isActive"), processingFee, (String) allowed.get("remark"), id);
        return Map.of("id", id, "rejectedFields", gate.rejected());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        String auditStatus = jdbc.queryForObject(
            "SELECT audit_status FROM acc_channel_accounts WHERE id = ?::uuid", String.class, id);
        if ("AUDITED".equals(auditStatus)) {
            throw ApiException.badRequest("渠道账号已审核，不能删除，请先反审");
        }
        cascadeChecker.checkBeforeDelete(TABLE, id);
        jdbc.update("DELETE FROM acc_channel_accounts WHERE id = ?::uuid", id);
        return Map.of("id", id, "deleted", true);
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("channelId", row.get("channel_id"));
        out.put("channelName", row.get("channel_name"));
        out.put("partnerId", row.get("partner_id"));
        out.put("partnerName", row.get("partner_name"));
        out.put("accountNo", row.get("account_no"));
        out.put("accountName", row.get("account_name"));
        out.put("endpointUrl", row.get("endpoint_url"));
        out.put("isActive", row.get("is_active"));
        out.put("remark", row.get("remark"));
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        out.put("auditName", row.get("audit_name"));
        return out;
    }
}

package com.xqt.saas.acc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import com.xqt.saas.common.BranchAccessFilter;
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

/** /api/acc/detains — 扣件，前端列：no / customerName / type / status / reason / addName / addTime。 */
@RestController
@RequestMapping("/api/acc/detains")
public class AccDetainsController {
    private static final String TABLE = "acc_detains";
    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final CascadeChecker cascadeChecker;
    private final FieldGate fieldGate;

        private final BranchAccessFilter branchAccess;

public AccDetainsController(JdbcTemplate jdbc, JsonSupport json,
                                CascadeChecker cascadeChecker, FieldGate fieldGate,
                                  BranchAccessFilter branchAccess) {
        this.jdbc = jdbc;
        this.json = json;
        this.cascadeChecker = cascadeChecker;
        this.fieldGate = fieldGate;
            this.branchAccess = branchAccess;
    }

    @GetMapping
    public Map<String, Object> list(
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer pageSize,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String dateFrom,
        @RequestParam(required = false) String dateTo
    ) {
        try {
            int limit = AccPaging.pageSize(pageSize);
            int offset = AccPaging.offset(page, pageSize);
            String search = keyword == null || keyword.isBlank() ? null : "%" + keyword + "%";
            var access = branchAccess.forCurrentViaCustomer("d");
            java.util.List<Object> countParams = new java.util.ArrayList<>(java.util.Arrays.asList(
                search, search, search, dateFrom, dateFrom, dateTo, dateTo));
            countParams.addAll(access.params());
            Long total = jdbc.queryForObject(
                "SELECT count(*) FROM acc_detains d"
                + " LEFT JOIN customers c ON c.id = d.customer_id"
                + " WHERE (?::text IS NULL OR d.detain_no ILIKE ? OR c.name ILIKE ?)"
                + "   AND (?::date IS NULL OR d.created_at >= ?::date)"
                + "   AND (?::date IS NULL OR d.created_at < (?::date + 1))"
                + access.sql(),
                Long.class, countParams.toArray());
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT d.id::text AS id, d.detain_no, d.detain_type, d.status, d.reason,"
                + "       d.add_name, d.created_at,"
                + "       d.audit_status, d.audited_at, d.audit_name,"
                + "       c.name AS customer_name"
                + " FROM acc_detains d"
                + " LEFT JOIN customers c ON c.id = d.customer_id"
                + " WHERE (?::text IS NULL OR d.detain_no ILIKE ? OR c.name ILIKE ?)"
                + "   AND (?::date IS NULL OR d.created_at >= ?::date)"
                + "   AND (?::date IS NULL OR d.created_at < (?::date + 1))"
                + access.sql()
                + " ORDER BY d.created_at DESC"
                + " LIMIT ? OFFSET ?",
                buildDetainsListParams(search, dateFrom, dateTo, access, limit, offset));
            return AccPaging.result(rows.stream().map(this::project).toList(), total == null ? 0 : total);
        } catch (DataAccessException ex) {
            return AccPaging.result(List.of(), 0);
        }
    }

    @GetMapping("/{id}/raw")
    public Map<String, Object> raw(@PathVariable String id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM acc_detains WHERE id = ?::uuid LIMIT 1", id);
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        Object shipmentRaw = body.get("shipment_id");
        if (shipmentRaw == null || shipmentRaw.toString().isBlank()) {
            throw ApiException.badRequest("找不到关联快件");
        }
        String shipmentId = shipmentRaw.toString();
        // ACC Ask.php L1005/L1013: 该快件已放行/已退件/已申请退件，无法再扣件
        String shipStatus;
        try {
            shipStatus = jdbc.queryForObject(
                "SELECT status::text FROM shipments WHERE id = ?::uuid", String.class, shipmentId);
        } catch (org.springframework.dao.DataAccessException ex) {
            throw ApiException.notFound("找不到关联快件: " + shipmentId);
        }
        if ("DELIVERED".equals(shipStatus) || "RETURNED".equals(shipStatus) || "CANCELLED".equals(shipStatus)) {
            throw ApiException.badRequest("该快件已" + zhShipStatus(shipStatus) + "，无法扣件");
        }
        // 已存在未关闭的 detain 单 → 重复申请
        Integer activeDetainCount = jdbc.queryForObject("""
            SELECT count(*) FROM acc_detains
             WHERE shipment_id = ?::uuid
               AND status IN ('PENDING','HOLDING')
            """, Integer.class, shipmentId);
        if (activeDetainCount != null && activeDetainCount > 0) {
            throw ApiException.badRequest("该快件已有未处理的扣件单，请先关闭旧的");
        }
        String id = jdbc.queryForObject("""
            INSERT INTO acc_detains (
              tenant_id, detain_no, customer_id, shipment_id, detain_type, status, reason, add_name
            ) VALUES (
              current_setting('app.current_tenant_id')::uuid, ?, ?::uuid, ?::uuid, ?, ?, ?, ?
            )
            RETURNING id::text
            """, String.class,
            body.getOrDefault("no", body.get("detain_no")),
            body.get("customer_id"),
            shipmentId,
            body.getOrDefault("type", body.get("detain_type")),
            body.getOrDefault("status", "PENDING"),
            body.get("reason"),
            body.getOrDefault("addName", body.get("add_name")));
        return Map.of("id", id);
    }

    private static String zhShipStatus(String s) {
        return switch (s) {
            case "DELIVERED" -> "签收";
            case "RETURNED"  -> "退件";
            case "CANCELLED" -> "取消";
            default -> s;
        };
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String currentAudit = jdbc.queryForObject(
            "SELECT audit_status FROM acc_detains WHERE id = ?::uuid", String.class, id);
        FieldGate.FilterResult gate = fieldGate.filterAllowedFields(TABLE, currentAudit, body);
        if (!gate.rejected().isEmpty() && gate.allowed().isEmpty()) {
            throw ApiException.badRequest("扣件已审核，字段不可修改: " + String.join(",", gate.rejected())
                + "；请先反审");
        }
        Map<String, Object> allowed = gate.allowed();
        jdbc.update("""
            UPDATE acc_detains SET
              status = coalesce(?, status),
              reason = coalesce(?, reason)
            WHERE id = ?::uuid
            """,
            (String) allowed.get("status"),
            (String) allowed.get("reason"),
            id);
        return Map.of("id", id, "rejectedFields", gate.rejected());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        String auditStatus = jdbc.queryForObject(
            "SELECT audit_status FROM acc_detains WHERE id = ?::uuid", String.class, id);
        if ("AUDITED".equals(auditStatus)) {
            throw ApiException.badRequest("扣件已审核，不能删除，请先反审");
        }
        cascadeChecker.checkBeforeDelete(TABLE, id);
        jdbc.update("DELETE FROM acc_detains WHERE id = ?::uuid", id);
        return Map.of("id", id, "deleted", true);
    }

    private static Object[] buildDetainsListParams(String search, String dateFrom, String dateTo,
                                                     BranchAccessFilter.AccessClause access,
                                                     int limit, int offset) {
        java.util.List<Object> params = new java.util.ArrayList<>(java.util.Arrays.asList(
            search, search, search, dateFrom, dateFrom, dateTo, dateTo));
        params.addAll(access.params());
        params.add(limit);
        params.add(offset);
        return params.toArray();
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("no", row.get("detain_no"));
        out.put("customerName", row.get("customer_name"));
        out.put("type", row.get("detain_type"));
        out.put("status", row.get("status"));
        out.put("reason", row.get("reason"));
        out.put("addName", row.get("add_name"));
        out.put("addTime", json.value(row.get("created_at")));
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        out.put("auditName", row.get("audit_name"));
        return out;
    }

    // ════════ P0 异常审核-扣件 3 个端点 (对齐 ACC Detain.php) ════════
    //   POST /{id}/lock    锁定扣件 (doSaveLock): shipment.status=DETAINED + Ask 路由
    //   POST /{id}/release 放行 (doSaveUnlock): shipment.status=IN_TRANSIT + Ask 关闭
    //   POST /{id}/call    反馈 (doCall): 写 acc_ask_replies + 限频

    /** ① 锁定扣件 */
    @org.springframework.web.bind.annotation.PostMapping("/{id}/lock")
    @org.springframework.transaction.annotation.Transactional
    public java.util.Map<String, Object> lock(@org.springframework.web.bind.annotation.PathVariable String id) {
        java.util.Map<String, Object> d;
        try {
            d = jdbc.queryForMap(
                "SELECT shipment_id::text, status FROM acc_detains WHERE id = ?::uuid", id);
        } catch (org.springframework.dao.DataAccessException ex) {
            throw com.xqt.saas.common.ApiException.notFound("扣件不存在: " + id);
        }
        String shipmentId = (String) d.get("shipment_id");
        if (shipmentId == null) {
            throw com.xqt.saas.common.ApiException.badRequest("扣件没绑 shipment");
        }
        jdbc.update("UPDATE acc_detains SET status = 'PROCESSING' WHERE id = ?::uuid", id);
        jdbc.update("UPDATE shipments SET status = 'DETAINED'::shipment_status WHERE id = ?::uuid", shipmentId);
        // 关联建/更新 acc_asks (扣件类型)
        try {
            jdbc.update("""
                INSERT INTO acc_asks (tenant_id, shipment_id, content, source, ask_type, status, add_name, to_role)
                VALUES (current_setting('app.current_tenant_id')::uuid, ?::uuid,
                  ?, 'SYSTEM', 'DETAIN', 'OPEN', 'detain-bot', 'STAFF')
                """, shipmentId, "扣件锁定 detain_id=" + id);
        } catch (org.springframework.dao.DataAccessException ignored) {}
        return java.util.Map.of("id", id, "locked", true);
    }

    /** ② 放行扣件 */
    @org.springframework.web.bind.annotation.PostMapping("/{id}/release")
    @org.springframework.transaction.annotation.Transactional
    public java.util.Map<String, Object> release(@org.springframework.web.bind.annotation.PathVariable String id) {
        java.util.Map<String, Object> d;
        try {
            d = jdbc.queryForMap(
                "SELECT shipment_id::text FROM acc_detains WHERE id = ?::uuid", id);
        } catch (org.springframework.dao.DataAccessException ex) {
            throw com.xqt.saas.common.ApiException.notFound("扣件不存在: " + id);
        }
        String shipmentId = (String) d.get("shipment_id");
        jdbc.update("UPDATE acc_detains SET status = 'RESOLVED' WHERE id = ?::uuid", id);
        if (shipmentId != null) {
            jdbc.update("UPDATE shipments SET status = 'IN_TRANSIT'::shipment_status WHERE id = ?::uuid", shipmentId);
            // 关联关闭 acc_asks 扣件类型
            jdbc.update("""
                UPDATE acc_asks SET status = 'CLOSED'
                WHERE shipment_id = ?::uuid AND ask_type = 'DETAIN' AND status IN ('OPEN','PENDING')
                """, shipmentId);
        }
        return java.util.Map.of("id", id, "released", true);
    }

    /** ③ 反馈 (call) - 写 ask_reply 限频 1 小时 */
    @org.springframework.web.bind.annotation.PostMapping("/{id}/call")
    @org.springframework.transaction.annotation.Transactional
    public java.util.Map<String, Object> call(
            @org.springframework.web.bind.annotation.PathVariable String id,
            @org.springframework.web.bind.annotation.RequestBody java.util.Map<String, Object> body) {
        String content = (String) body.get("content");
        if (content == null || content.isBlank())
            throw com.xqt.saas.common.ApiException.badRequest("反馈内容必填");
        // 限频: 1 小时只能反馈 1 次 (ACC LastReply 对齐)
        Integer recentCount = jdbc.queryForObject("""
            SELECT count(*) FROM acc_ask_replies r
            JOIN acc_asks a ON a.id = r.ask_id
            WHERE a.shipment_id = (SELECT shipment_id FROM acc_detains WHERE id = ?::uuid)
              AND a.ask_type = 'DETAIN'
              AND r.created_at > now() - interval '1 hour'
            """, Integer.class, id);
        if (recentCount != null && recentCount > 0) {
            throw com.xqt.saas.common.ApiException.badRequest("1 小时内已反馈过, 请稍后再试");
        }
        // 找扣件对应 ask, 没有就先建一个
        java.util.Map<String, Object> d = jdbc.queryForMap(
            "SELECT shipment_id::text FROM acc_detains WHERE id = ?::uuid", id);
        String shipmentId = (String) d.get("shipment_id");
        String askId;
        try {
            askId = jdbc.queryForObject("""
                SELECT id::text FROM acc_asks
                WHERE shipment_id = ?::uuid AND ask_type = 'DETAIN' AND status IN ('OPEN','PENDING')
                ORDER BY created_at DESC LIMIT 1
                """, String.class, shipmentId);
        } catch (org.springframework.dao.DataAccessException ex) {
            askId = jdbc.queryForObject("""
                INSERT INTO acc_asks (tenant_id, shipment_id, content, source, ask_type, status, add_name)
                VALUES (current_setting('app.current_tenant_id')::uuid, ?::uuid,
                  '扣件反馈', 'STAFF', 'DETAIN', 'OPEN', 'detain-bot')
                RETURNING id::text
                """, String.class, shipmentId);
        }
        jdbc.update("""
            INSERT INTO acc_ask_replies (tenant_id, ask_id, source, add_name, content, to_role, handle)
            VALUES (current_setting('app.current_tenant_id')::uuid, ?::uuid, 'STAFF',
              ?, ?, 'CUSTOMER', 0)
            """, askId, body.getOrDefault("addName", "detain-bot"), content);
        return java.util.Map.of("id", id, "askId", askId, "fed", true);
    }
}

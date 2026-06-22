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

/** /api/acc/asks — 问题件，前端列：expressNo / content / source / type / status / addName / addTime。 */
@RestController
@RequestMapping("/api/acc/asks")
public class AccAsksController {
    private static final String TABLE = "acc_asks";
    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final CascadeChecker cascadeChecker;
    private final FieldGate fieldGate;

        private final BranchAccessFilter branchAccess;

public AccAsksController(JdbcTemplate jdbc, JsonSupport json,
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
        @RequestParam(required = false) String dateTo,
        @RequestParam(required = false) String status
    ) {
        try {
            int limit = AccPaging.pageSize(pageSize);
            int offset = AccPaging.offset(page, pageSize);
            String search = keyword == null || keyword.isBlank() ? null : "%" + keyword + "%";
            String statusFilter = buildAsksStatusFilter(status);
            var access = branchAccess.forCurrent("s");
            java.util.List<Object> countParams = new java.util.ArrayList<>(java.util.Arrays.asList(
                search, search, search, dateFrom, dateFrom, dateTo, dateTo));
            countParams.addAll(access.params());
            Long total = jdbc.queryForObject(
                "SELECT count(*) FROM acc_asks a"
                + " LEFT JOIN shipments s ON s.id = a.shipment_id"
                + " WHERE (?::text IS NULL OR a.customer_ref ILIKE ? OR s.shipment_no ILIKE ?)"
                + "   AND (?::date IS NULL OR a.created_at >= ?::date)"
                + "   AND (?::date IS NULL OR a.created_at < (?::date + 1))"
                + statusFilter
                + access.sql(),
                Long.class, countParams.toArray());
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT a.id::text AS id, a.customer_ref, a.content, a.source, a.ask_type, a.status,"
                + "       a.add_name, a.created_at, a.is_show,"
                + "       a.audit_status, a.audited_at, a.audit_name,"
                + "       s.shipment_no"
                + " FROM acc_asks a"
                + " LEFT JOIN shipments s ON s.id = a.shipment_id"
                + " WHERE (?::text IS NULL OR a.customer_ref ILIKE ? OR s.shipment_no ILIKE ?)"
                + "   AND (?::date IS NULL OR a.created_at >= ?::date)"
                + "   AND (?::date IS NULL OR a.created_at < (?::date + 1))"
                + statusFilter
                + access.sql()
                + " ORDER BY a.created_at DESC"
                + " LIMIT ? OFFSET ?",
                buildAsksListParams(search, dateFrom, dateTo, access, limit, offset));
            return AccPaging.result(rows.stream().map(this::project).toList(), total == null ? 0 : total);
        } catch (DataAccessException ex) {
            return AccPaging.result(List.of(), 0);
        }
    }

    @GetMapping("/{id}/raw")
    public Map<String, Object> raw(@PathVariable String id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM acc_asks WHERE id = ?::uuid LIMIT 1", id);
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        Object content = body.get("content");
        if (content == null || content.toString().isBlank()) {
            throw ApiException.badRequest("问题内容必填");
        }
        Object shipmentRaw = body.get("shipment_id");
        Object askTypeRaw = body.getOrDefault("type", body.get("ask_type"));
        // ACC Ask.php L600: 已存在未处理完成的同类型问题
        if (shipmentRaw != null && askTypeRaw != null) {
            Integer dup = jdbc.queryForObject("""
                SELECT count(*) FROM acc_asks
                 WHERE shipment_id = ?::uuid AND ask_type = ?
                   AND status NOT IN ('CLOSED','RESOLVED')
                """, Integer.class, shipmentRaw.toString(), askTypeRaw.toString());
            if (dup != null && dup > 0) {
                throw ApiException.badRequest(
                    "该快件已有未处理的同类型问题 [" + askTypeRaw + "]，请先关闭旧的");
            }
        }
        String id = jdbc.queryForObject("""
            INSERT INTO acc_asks (
              tenant_id, shipment_id, customer_ref, content, source, ask_type, status, add_name
            ) VALUES (
              current_setting('app.current_tenant_id')::uuid, ?::uuid, ?, ?, ?, ?, ?, ?
            )
            RETURNING id::text
            """, String.class,
            body.get("shipment_id"),
            body.getOrDefault("expressNo", body.get("customer_ref")),
            body.get("content"),
            body.get("source"),
            askTypeRaw,
            body.getOrDefault("status", "OPEN"),
            body.getOrDefault("addName", body.get("add_name")));
        return Map.of("id", id);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String currentAudit = jdbc.queryForObject(
            "SELECT audit_status FROM acc_asks WHERE id = ?::uuid", String.class, id);
        FieldGate.FilterResult gate = fieldGate.filterAllowedFields(TABLE, currentAudit, body);
        if (!gate.rejected().isEmpty() && gate.allowed().isEmpty()) {
            throw ApiException.badRequest("问题件已审核，字段不可修改: " + String.join(",", gate.rejected())
                + "；请先反审");
        }
        Map<String, Object> allowed = gate.allowed();
        jdbc.update("""
            UPDATE acc_asks SET
              content = coalesce(?, content),
              status  = coalesce(?, status)
            WHERE id = ?::uuid
            """,
            (String) allowed.get("content"),
            (String) allowed.get("status"),
            id);
        return Map.of("id", id, "rejectedFields", gate.rejected());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        String auditStatus = jdbc.queryForObject(
            "SELECT audit_status FROM acc_asks WHERE id = ?::uuid", String.class, id);
        if ("AUDITED".equals(auditStatus)) {
            throw ApiException.badRequest("问题件已审核，不能删除，请先反审");
        }
        cascadeChecker.checkBeforeDelete(TABLE, id);
        jdbc.update("DELETE FROM acc_asks WHERE id = ?::uuid", id);
        return Map.of("id", id, "deleted", true);
    }

    /** 客服中心 问题件子页过滤 (CUSTOMER/SUPPLIER 按 source；PROCESSING/PENDING/DONE 按 status) */
    private static String buildAsksStatusFilter(String status) {
        if (status == null || status.isBlank()) return "";
        return switch (status) {
            case "CUSTOMER"   -> " AND a.source = 'CUSTOMER'";
            case "SUPPLIER"   -> " AND a.source = 'SUPPLIER'";
            case "PROCESSING" -> " AND a.status = 'PROCESSING'";
            case "PENDING"    -> " AND a.status IN ('OPEN','PENDING')";
            case "DONE"       -> " AND a.status IN ('CLOSED','DONE')";
            default            -> "";
        };
    }

    private static Object[] buildAsksListParams(String search, String dateFrom, String dateTo,
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
        out.put("expressNo", row.get("customer_ref") != null
            ? row.get("customer_ref") : row.get("shipment_no"));
        out.put("content", row.get("content"));
        out.put("source", row.get("source"));
        out.put("type", row.get("ask_type"));
        out.put("status", row.get("status"));
        out.put("isShow", row.get("is_show"));
        out.put("addName", row.get("add_name"));
        out.put("addTime", json.value(row.get("created_at")));
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        out.put("auditName", row.get("audit_name"));
        return out;
    }

    // ════════ P0-C7+C8 修复: ACC Ask.php doReply 7 种 Handle 联动 ════════
    //   handle=0 普通回复(仅写 acc_ask_replies)
    //   handle=1 申请扣件 → 自动建 acc_detains 单
    //   handle=2 解除扣件 → acc_detains 改 RESOLVED
    //   handle=3 申请退件 → 自动建 return_orders 单
    //   handle=4 申请赔偿 → 自动建 acc_reparations 单
    //   handle=5 撤销赔偿 → acc_reparations 改 status=CANCELLED
    //   handle=6 关闭问题件 → acc_asks.status='CLOSED'

    /** GET /asks/{id}/replies — 问题件回复线索 */
    @org.springframework.web.bind.annotation.GetMapping("/{id}/replies")
    public java.util.Map<String, Object> listReplies(@org.springframework.web.bind.annotation.PathVariable String id) {
        java.util.List<java.util.Map<String, Object>> rows = jdbc.queryForList("""
            SELECT id::text, source, add_name, content, to_role, is_show, handle,
                   handle_ref_id::text, created_at
              FROM acc_ask_replies WHERE ask_id = ?::uuid
              ORDER BY created_at
            """, id);
        return java.util.Map.of("data", rows.stream().map(r -> {
            java.util.Map<String, Object> o = new java.util.LinkedHashMap<>();
            o.put("id", r.get("id"));
            o.put("source", r.get("source"));
            o.put("addName", r.get("add_name"));
            o.put("content", r.get("content"));
            o.put("toRole", r.get("to_role"));
            o.put("isShow", r.get("is_show"));
            o.put("handle", r.get("handle"));
            o.put("handleRefId", r.get("handle_ref_id"));
            o.put("createdAt", json.value(r.get("created_at")));
            return o;
        }).toList());
    }

    /** POST /asks/{id}/reply
     *  body: { content, source?, addName?, toRole?, isShow?, handle?(0-6) } */
    @org.springframework.web.bind.annotation.PostMapping("/{id}/reply")
    @org.springframework.transaction.annotation.Transactional
    public java.util.Map<String, Object> reply(
            @org.springframework.web.bind.annotation.PathVariable String id,
            @org.springframework.web.bind.annotation.RequestBody java.util.Map<String, Object> body) {
        // 1. 取 ask 上下文
        java.util.Map<String, Object> ask;
        try {
            ask = jdbc.queryForMap("""
                SELECT a.shipment_id::text AS shipment_id, a.customer_ref, a.status::text AS status,
                       a.tenant_id::text AS tenant_id, s.customer_id::text AS customer_id
                FROM acc_asks a LEFT JOIN shipments s ON s.id = a.shipment_id
                WHERE a.id = ?::uuid
                """, id);
        } catch (org.springframework.dao.DataAccessException ex) {
            throw com.xqt.saas.common.ApiException.notFound("问题件不存在: " + id);
        }
        String content = (String) body.get("content");
        if (content == null || content.isBlank())
            throw com.xqt.saas.common.ApiException.badRequest("回复内容必填");
        String source = (String) body.getOrDefault("source", "STAFF");
        String addName = (String) body.getOrDefault("addName", "system");
        String toRole = (String) body.get("toRole");
        Object isShowRaw = body.getOrDefault("isShow", true);
        Boolean isShow = isShowRaw instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(isShowRaw));
        int handle = body.get("handle") instanceof Number n ? n.intValue() : 0;

        // 2. handle 联动产生的关联 id (创建 detain / return / reparation)
        String handleRefId = null;
        String shipmentId = (String) ask.get("shipment_id");
        String customerRef = (String) ask.get("customer_ref");
        String tenantId = (String) ask.get("tenant_id");

        try {
            switch (handle) {
                case 1: // 申请扣件
                    if (shipmentId != null) {
                        handleRefId = jdbc.queryForObject("""
                            INSERT INTO acc_detains
                              (tenant_id, shipment_id, reason, status, add_name)
                            VALUES (?::uuid, ?::uuid, ?, 'OPEN', ?)
                            RETURNING id::text
                            """, String.class, tenantId, shipmentId,
                            "Ask reply 联动: " + content, addName);
                        jdbc.update("UPDATE shipments SET status = 'DETAINED'::shipment_status WHERE id = ?::uuid", shipmentId);
                    }
                    break;
                case 2: // 解除扣件
                    if (shipmentId != null) {
                        int n = jdbc.update("""
                            UPDATE acc_detains SET status = 'RESOLVED'
                            WHERE shipment_id = ?::uuid AND status IN ('OPEN','PENDING')
                            """, shipmentId);
                        if (n > 0) {
                            jdbc.update("UPDATE shipments SET status = 'IN_TRANSIT'::shipment_status WHERE id = ?::uuid", shipmentId);
                        }
                    }
                    break;
                case 3: // 申请退件
                    if (shipmentId != null) {
                        handleRefId = jdbc.queryForObject("""
                            INSERT INTO return_orders
                              (tenant_id, shipment_id, customer_ref, reason, status, refund_amount)
                            VALUES (?::uuid, ?::uuid, ?, ?, 'DRAFT', 0)
                            RETURNING id::text
                            """, String.class, tenantId, shipmentId, customerRef,
                            "Ask reply 联动: " + content);
                        jdbc.update("UPDATE shipments SET status = 'RETURNING'::shipment_status WHERE id = ?::uuid", shipmentId);
                    }
                    break;
                case 4: // 申请赔偿
                    if (shipmentId != null) {
                        java.math.BigDecimal applyAmount = body.get("applyAmount") instanceof Number na
                            ? new java.math.BigDecimal(na.toString()) : java.math.BigDecimal.ZERO;
                        String currency = (String) body.getOrDefault("currency", "CNY");
                        handleRefId = jdbc.queryForObject("""
                            INSERT INTO acc_reparations
                              (tenant_id, shipment_id, customer_ref, reason, apply_amount, currency, status, add_name)
                            VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, 'DRAFT', ?)
                            RETURNING id::text
                            """, String.class, tenantId, shipmentId, customerRef,
                            content, applyAmount, currency, addName);
                        jdbc.update("UPDATE shipments SET status = 'CLAIMING'::shipment_status WHERE id = ?::uuid", shipmentId);
                    }
                    break;
                case 5: // 撤销赔偿
                    if (shipmentId != null) {
                        jdbc.update("""
                            UPDATE acc_reparations SET status = 'CANCELLED'
                            WHERE shipment_id = ?::uuid AND status IN ('DRAFT','PENDING')
                            """, shipmentId);
                    }
                    break;
                case 6: // 关闭问题件
                    jdbc.update("UPDATE acc_asks SET status = 'CLOSED' WHERE id = ?::uuid", id);
                    if (toRole == null) toRole = "CLOSED";
                    break;
                default: // 0 普通回复, 不联动业务
                    break;
            }
        } catch (org.springframework.dao.DataAccessException ex) {
            // handle 联动失败不影响回复落库
        }

        // 3. 写 acc_ask_replies + 推路由
        String replyId = jdbc.queryForObject("""
            INSERT INTO acc_ask_replies
              (tenant_id, ask_id, source, add_name, content, to_role, is_show, handle, handle_ref_id)
            VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, ?::uuid)
            RETURNING id::text
            """, String.class, tenantId, id, source, addName, content, toRole, isShow, handle, handleRefId);
        if (toRole != null) {
            jdbc.update("UPDATE acc_asks SET to_role = ? WHERE id = ?::uuid", toRole, id);
        }

        return java.util.Map.of("id", replyId, "askId", id, "handleRefId", handleRefId == null ? "" : handleRefId);
    }
}

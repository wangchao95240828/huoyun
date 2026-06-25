package com.xqt.saas.acc;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import com.xqt.saas.common.BranchAccessFilter;
import com.xqt.saas.common.JsonSupport;
import com.xqt.saas.framework.cascade.CascadeChecker;
import com.xqt.saas.framework.fieldgate.FieldGate;
import com.xqt.saas.framework.money.MoneySnapshotService;
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
 * /api/acc/charges — AR 应收，前端列：expressNo / customerName / productName / country
 * / chargeWeight / type / amount / paid / theDate / auditName
 *
 * 来源：charges WHERE side='AR' + shipments + customers + channels + charge_items。
 */
@RestController
@RequestMapping("/api/acc/charges")
public class AccChargesController {
    private static final String TABLE = "charges";
    private static final String SIDE = "AR";

    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final CascadeChecker cascadeChecker;
    private final FieldGate fieldGate;
    private final MoneySnapshotService moneySnapshotService;

        private final BranchAccessFilter branchAccess;

public AccChargesController(JdbcTemplate jdbc, JsonSupport json,
                                CascadeChecker cascadeChecker, FieldGate fieldGate,
                                MoneySnapshotService moneySnapshotService,
                                  BranchAccessFilter branchAccess) {
        this.jdbc = jdbc;
        this.json = json;
        this.cascadeChecker = cascadeChecker;
        this.fieldGate = fieldGate;
        this.moneySnapshotService = moneySnapshotService;
            this.branchAccess = branchAccess;
    }

    @GetMapping
    public Map<String, Object> list(
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer pageSize,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String dateFrom,
        @RequestParam(required = false) String dateTo,
        @RequestParam(required = false) String status,
        // 多条件
        @RequestParam(required = false) String customerIds,
        @RequestParam(required = false) String currencies,
        @RequestParam(required = false) String sides,
        @RequestParam(required = false) String statuses,
        @RequestParam(required = false) String auditStatuses,
        @RequestParam(required = false) String settlementStatuses,
        @RequestParam(required = false) String createdFrom,
        @RequestParam(required = false) String createdTo,
        @RequestParam(required = false) String amountFrom,
        @RequestParam(required = false) String amountTo
    ) {
        try {
            int limit = AccPaging.pageSize(pageSize);
            int offset = AccPaging.offset(page, pageSize);
            String search = keyword == null || keyword.isBlank() ? null : "%" + keyword + "%";

            // 核算中心 子页过滤
            String statusFilter = buildChargesStatusFilter(status);

            // 多条件 adv-filter
            StringBuilder advFilter = new StringBuilder();
            java.util.List<Object> advParams = new java.util.ArrayList<>();
            java.util.List<String> cidList = AccOrdersController.splitCsv(customerIds);
            java.util.List<String> curList = AccOrdersController.splitCsv(currencies);
            java.util.List<String> sdList  = AccOrdersController.splitCsv(sides);
            java.util.List<String> stList  = AccOrdersController.splitCsv(statuses);
            java.util.List<String> auList  = AccOrdersController.splitCsv(auditStatuses);
            java.util.List<String> ssList  = AccOrdersController.splitCsv(settlementStatuses);
            if (!cidList.isEmpty()) {
                advFilter.append(" AND ch.customer_id::text IN (").append(AccOrdersController.qMarks(cidList.size())).append(")");
                advParams.addAll(cidList);
            }
            if (!curList.isEmpty()) {
                advFilter.append(" AND ch.currency IN (").append(AccOrdersController.qMarks(curList.size())).append(")");
                advParams.addAll(curList);
            }
            if (!sdList.isEmpty()) {
                advFilter.append(" AND ch.side::text IN (").append(AccOrdersController.qMarks(sdList.size())).append(")");
                advParams.addAll(sdList);
            }
            if (!stList.isEmpty()) {
                advFilter.append(" AND ch.status::text IN (").append(AccOrdersController.qMarks(stList.size())).append(")");
                advParams.addAll(stList);
            }
            if (!auList.isEmpty()) {
                advFilter.append(" AND ch.audit_status IN (").append(AccOrdersController.qMarks(auList.size())).append(")");
                advParams.addAll(auList);
            }
            if (!ssList.isEmpty()) {
                advFilter.append(" AND ch.settlement_status IN (").append(AccOrdersController.qMarks(ssList.size())).append(")");
                advParams.addAll(ssList);
            }
            if (createdFrom != null && !createdFrom.isBlank()) { advFilter.append(" AND ch.created_at >= ?::date"); advParams.add(createdFrom); }
            if (createdTo   != null && !createdTo.isBlank())   { advFilter.append(" AND ch.created_at < (?::date + 1)"); advParams.add(createdTo); }
            try {
                if (amountFrom != null && !amountFrom.isBlank()) { advFilter.append(" AND ch.amount >= ?"); advParams.add(new java.math.BigDecimal(amountFrom)); }
                if (amountTo   != null && !amountTo.isBlank())   { advFilter.append(" AND ch.amount <= ?"); advParams.add(new java.math.BigDecimal(amountTo)); }
            } catch (NumberFormatException ignored) {}
            String advFilterSql = advFilter.toString();

            // 当用户显式传 sides 时不强制 side='AR'（让 AR/AP 都能查）
            String baseSideFilter = sdList.isEmpty() ? " AND ch.side = 'AR'" : "";

            var access = branchAccess.forCurrent("ch");
            java.util.List<Object> countParams = new java.util.ArrayList<>(java.util.Arrays.asList(
                search, search, search, dateFrom, dateFrom, dateTo, dateTo));
            countParams.addAll(advParams);
            countParams.addAll(access.params());
            Long total = jdbc.queryForObject(
                "SELECT count(*) FROM charges ch"
                + " LEFT JOIN shipments s ON s.id = ch.shipment_id"
                + " WHERE 1=1"
                + baseSideFilter
                + "   AND (?::text IS NULL OR s.shipment_no ILIKE ? OR s.customer_ref ILIKE ?)"
                + "   AND (?::date IS NULL OR ch.created_at >= ?::date)"
                + "   AND (?::date IS NULL OR ch.created_at < (?::date + 1))"
                + statusFilter
                + advFilterSql
                + access.sql(),
                Long.class, countParams.toArray());

            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT
                  ch.id::text       AS id,
                  ch.status,
                  ch.currency,
                  ch.amount,
                  ch.created_at,
                  ch.audit_status,
                  ch.audited_at,
                  ch.audit_name,
                  s.shipment_no,
                  s.customer_ref,
                  s.destination_country,
                  cu.name           AS customer_name,
                  cn.name           AS channel_name,
                  ci.name           AS charge_item_name,
                  ci.code           AS charge_item_code,
                  (
                    SELECT coalesce(sum(p.amount), 0) FROM payments p
                    WHERE p.tenant_id = ch.tenant_id
                      AND p.reference_no = s.shipment_no
                  ) AS paid_amount,
                  (
                    SELECT coalesce(sum(c.chargeable_weight_kg), sum(c.actual_weight_kg))
                    FROM cartons c WHERE c.shipment_id = s.id
                  ) AS charge_weight
                FROM charges ch
                LEFT JOIN shipments s    ON s.id = ch.shipment_id
                LEFT JOIN customers cu   ON cu.id = s.customer_id
                LEFT JOIN channels cn    ON cn.id = s.channel_id
                LEFT JOIN charge_items ci ON ci.id = ch.charge_item_id
                WHERE 1=1
                """
                + baseSideFilter
                + " AND (?::text IS NULL OR s.shipment_no ILIKE ? OR s.customer_ref ILIKE ?)"
                + " AND (?::date IS NULL OR ch.created_at >= ?::date)"
                + " AND (?::date IS NULL OR ch.created_at < (?::date + 1))"
                + statusFilter
                + advFilterSql
                + access.sql()
                + " ORDER BY ch.created_at DESC LIMIT ? OFFSET ?",
                buildChargesListParamsWithAdv(search, dateFrom, dateTo, advParams, access, limit, offset));

            // 应收合计（ACC 应收应付模块）
            java.util.List<Object> sumParams = new java.util.ArrayList<>(java.util.Arrays.asList(
                search, search, search, dateFrom, dateFrom, dateTo, dateTo));
            sumParams.addAll(advParams);
            sumParams.addAll(access.params());
            java.math.BigDecimal sumAmount = jdbc.queryForObject(
                "SELECT coalesce(sum(ch.amount), 0) FROM charges ch"
                + " LEFT JOIN shipments s ON s.id = ch.shipment_id"
                + " WHERE 1=1"
                + baseSideFilter
                + "   AND (?::text IS NULL OR s.shipment_no ILIKE ? OR s.customer_ref ILIKE ?)"
                + "   AND (?::date IS NULL OR ch.created_at >= ?::date)"
                + "   AND (?::date IS NULL OR ch.created_at < (?::date + 1))"
                + statusFilter
                + advFilterSql
                + access.sql(),
                java.math.BigDecimal.class, sumParams.toArray());
            java.util.Map<String, Object> agg = new java.util.LinkedHashMap<>();
            agg.put("amount", sumAmount == null ? java.math.BigDecimal.ZERO : sumAmount);

            return AccPaging.result(rows.stream().map(this::project).toList(),
                total == null ? 0 : total, agg);
        } catch (DataAccessException ex) {
            return AccPaging.result(List.of(), 0);
        }
    }

    @GetMapping("/{id}/raw")
    public Map<String, Object> raw(@PathVariable String id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM charges WHERE id = ?::uuid AND side='AR' LIMIT 1", id);
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        Object shipmentId = body.get("shipment_id");
        Object chargeItemId = body.get("charge_item_id");
        if (shipmentId == null || shipmentId.toString().isBlank()) {
            throw ApiException.badRequest("请选择关联快件");
        }
        if (chargeItemId == null || chargeItemId.toString().isBlank()) {
            throw ApiException.badRequest("请选择费用类型");
        }
        // ACC Charge.php L586: 同一票快件不能两份同类型费用单
        Integer dup = jdbc.queryForObject("""
            SELECT count(*) FROM charges
             WHERE shipment_id = ?::uuid AND charge_item_id = ?::uuid
               AND status <> 'VOID'::charge_status
            """, Integer.class, shipmentId.toString(), chargeItemId.toString());
        if (dup != null && dup > 0) {
            throw ApiException.badRequest("该快件已有该类型费用单，同票不能重复创建");
        }
        BigDecimal amount = body.get("amount") instanceof Number n
            ? new BigDecimal(n.toString()) : BigDecimal.ZERO;
        if (amount.signum() <= 0) {
            throw ApiException.badRequest("费用金额必须大于零");
        }
        String currency = (String) body.getOrDefault("currency", "CNY");
        if (currency.length() != 3) {
            throw ApiException.badRequest("找不到费用结算货币");
        }
        String id = jdbc.queryForObject("""
            INSERT INTO charges (
              tenant_id, shipment_id, charge_item_id, side, status, currency, amount
            ) VALUES (
              current_setting('app.current_tenant_id')::uuid,
              ?::uuid, ?::uuid, ?::charge_side, 'DRAFT', ?, ?
            )
            RETURNING id::text
            """, String.class,
            shipmentId == null ? null : shipmentId.toString(),
            chargeItemId == null ? null : chargeItemId.toString(),
            SIDE, currency, amount);
        // 汇率快照：所有非 CNY 金额都在落库时 freeze rate
        moneySnapshotService.snapshot(TABLE, id, amount, currency);
        return Map.of("id", id);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        // 字段闸：审核后金额 / 币种 / shipment / charge_item 全部锁定
        String currentAudit = jdbc.queryForObject(
            "SELECT audit_status FROM charges WHERE id = ?::uuid AND side = ?::charge_side",
            String.class, id, SIDE);
        FieldGate.FilterResult gate = fieldGate.filterAllowedFields(TABLE, currentAudit, body);
        if (!gate.rejected().isEmpty() && gate.allowed().isEmpty()) {
            throw ApiException.badRequest("费用已审核，字段不可修改: " + String.join(",", gate.rejected())
                + "；请先反审");
        }
        Map<String, Object> allowed = gate.allowed();
        jdbc.update("""
            UPDATE charges SET
              amount = coalesce(?, amount),
              status = coalesce(?::charge_status, status)
            WHERE id = ?::uuid AND side = ?::charge_side
            """,
            allowed.get("amount") instanceof Number n ? new BigDecimal(n.toString()) : null,
            (String) allowed.get("status"),
            id, SIDE);
        // amount 改了的话刷新汇率快照
        if (allowed.containsKey("amount") && allowed.get("amount") instanceof Number n) {
            String currency = jdbc.queryForObject(
                "SELECT currency FROM charges WHERE id = ?::uuid", String.class, id);
            moneySnapshotService.snapshot(TABLE, id, new BigDecimal(n.toString()), currency);
        }
        return Map.of("id", id, "rejectedFields", gate.rejected());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        // 删除前级联校验 + 审核后锁定
        String auditStatus = jdbc.queryForObject(
            "SELECT audit_status FROM charges WHERE id = ?::uuid AND side = ?::charge_side",
            String.class, id, SIDE);
        if ("AUDITED".equals(auditStatus)) {
            throw ApiException.badRequest("费用已审核，不能删除，请先反审");
        }
        cascadeChecker.checkBeforeDelete(TABLE, id);
        jdbc.update("DELETE FROM charges WHERE id = ?::uuid AND side = ?::charge_side", id, SIDE);
        return Map.of("id", id, "deleted", true);
    }

    /**
     * R-8: AR 补收 — 改 charge 金额, 支持 DELTA (写差值) 或 OVERWRITE (作废原写新).
     * PATCH /api/acc/charges/{id}/restate  body: { newAmount, mode: DELTA|OVERWRITE, remark }
     */
    @org.springframework.web.bind.annotation.PatchMapping("/{id}/restate")
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public Map<String, Object> restate(@org.springframework.web.bind.annotation.PathVariable String id,
                                         @org.springframework.web.bind.annotation.RequestBody Map<String, Object> body) {
        java.math.BigDecimal newAmount;
        try {
            newAmount = new java.math.BigDecimal(body.get("newAmount").toString());
        } catch (Exception ex) {
            throw ApiException.badRequest("newAmount 必填且必须为数字");
        }
        if (newAmount.signum() <= 0) throw ApiException.badRequest("newAmount 必须大于零");
        String mode = String.valueOf(body.getOrDefault("mode", "DELTA")).toUpperCase();
        if (!"DELTA".equals(mode) && !"OVERWRITE".equals(mode)) {
            throw ApiException.badRequest("mode 必须是 DELTA 或 OVERWRITE");
        }
        String remark = body.get("remark") == null ? null : body.get("remark").toString();

        java.util.List<Map<String, Object>> origs = jdbc.queryForList("""
            SELECT id::text AS id_t, amount, currency, audit_status, side::text AS side_t,
                   shipment_id::text AS shipment_id,
                   charge_item_id::text AS charge_item_id,
                   customer_id::text AS customer_id,
                   order_id::text AS order_id
              FROM charges WHERE id = ?::uuid
            """, id);
        if (origs.isEmpty()) throw ApiException.notFound("找不到 charge: " + id);
        Map<String, Object> orig = origs.get(0);
        java.math.BigDecimal oldAmount = (java.math.BigDecimal) orig.get("amount");
        String currency = (String) orig.get("currency");
        String side = (String) orig.get("side_t");
        String shipmentId = (String) orig.get("shipment_id");
        String chargeItemId = (String) orig.get("charge_item_id");
        String customerId = (String) orig.get("customer_id");
        String orderId = (String) orig.get("order_id");

        Map<String, Object> evidence = new java.util.LinkedHashMap<>();
        evidence.put("source_charge_id", id);
        evidence.put("mode", mode);
        evidence.put("old_amount", oldAmount);
        evidence.put("new_amount", newAmount);
        evidence.put("remark", remark);
        evidence.put("via", "restate");

        if ("OVERWRITE".equals(mode)) {
            // 已审核需先反审 (反向 ledger), 否则不能 VOID
            if ("AUDITED".equals(orig.get("audit_status"))) {
                throw ApiException.badRequest("已审核 charge 不能 OVERWRITE, 请先反审或用 DELTA 模式");
            }
            jdbc.update("UPDATE charges SET status='VOID'::charge_status WHERE id=?::uuid", id);
            String newId = jdbc.queryForObject("""
                INSERT INTO charges (
                  tenant_id, shipment_id, charge_item_id, side, status, currency, amount,
                  evidence, customer_id, order_id
                ) VALUES (
                  current_setting('app.current_tenant_id')::uuid, ?::uuid, ?::uuid,
                  ?::charge_side, 'ESTIMATED', ?, ?, ?::jsonb, ?::uuid, ?::uuid
                ) RETURNING id::text
                """, String.class, shipmentId, chargeItemId, side, currency, newAmount,
                json.toJson(evidence), customerId, orderId);
            return Map.of("mode", "OVERWRITE", "voidedChargeId", id, "newChargeId", newId,
                "oldAmount", oldAmount, "newAmount", newAmount);
        } else {
            // DELTA 模式: 写差值 charge — 用 ADJUST charge_item 避免撞 unique key
            // (uq_charges_shipment_item_side 同 shipment 同 charge_item_id 只能 1 笔非 VOID)
            java.math.BigDecimal delta = newAmount.subtract(oldAmount);
            if (delta.signum() == 0) {
                return Map.of("mode", "DELTA", "delta", java.math.BigDecimal.ZERO,
                    "note", "金额相同, 无需补收");
            }
            // 找 ADJUST 类目, 找不到走 FREIGHT (但会撞 unique key, 此时建议用 OVERWRITE)
            String adjustItemId;
            java.util.List<String> adjustRows = jdbc.queryForList(
                "SELECT id::text FROM charge_items WHERE code='ADJUST' LIMIT 1", String.class);
            if (!adjustRows.isEmpty()) {
                adjustItemId = adjustRows.get(0);
            } else {
                throw ApiException.badRequest("找不到 ADJUST 调账类目, 请联系管理员配置 charge_items");
            }
            String deltaId = jdbc.queryForObject("""
                INSERT INTO charges (
                  tenant_id, shipment_id, charge_item_id, side, status, currency, amount,
                  evidence, customer_id, order_id
                ) VALUES (
                  current_setting('app.current_tenant_id')::uuid, ?::uuid, ?::uuid,
                  ?::charge_side, 'ESTIMATED', ?, ?, ?::jsonb, ?::uuid, ?::uuid
                ) RETURNING id::text
                """, String.class, shipmentId, adjustItemId, side, currency, delta,
                json.toJson(evidence), customerId, orderId);
            return Map.of("mode", "DELTA", "sourceChargeId", id, "deltaChargeId", deltaId,
                "oldAmount", oldAmount, "newAmount", newAmount, "delta", delta,
                "chargeItem", "ADJUST");
        }
    }

    /** 核算中心 子页过滤. */
    private static String buildChargesStatusFilter(String status) {
        if (status == null || status.isBlank()) return "";
        // PENDING (新建默认) 和 UNAUDITED 都算"待核": 新提交订单 charges 是 PENDING,
        // 手动反审过的 charges 是 UNAUDITED; 用户角度都是要审核的.
        return switch (status) {
            case "UNAUDITED"           -> " AND ch.audit_status IN ('UNAUDITED', 'PENDING')";
            case "HISTORY"             -> " AND ch.created_at < date_trunc('month', current_date)";
            case "RETURN_PENDING"      -> " AND ch.audit_status IN ('UNAUDITED', 'PENDING')"
                + " AND EXISTS (SELECT 1 FROM acc_returns r WHERE r.shipment_id = ch.shipment_id)";
            case "REPARATION_PENDING"  -> " AND ch.audit_status IN ('UNAUDITED', 'PENDING')"
                + " AND EXISTS (SELECT 1 FROM acc_reparations r WHERE r.shipment_id = ch.shipment_id)";
            default                     -> "";
        };
    }

    private static Object[] buildChargesListParams(String search, String dateFrom, String dateTo,
                                                    BranchAccessFilter.AccessClause access,
                                                    int limit, int offset) {
        java.util.List<Object> params = new java.util.ArrayList<>(java.util.Arrays.asList(
            search, search, search, dateFrom, dateFrom, dateTo, dateTo));
        params.addAll(access.params());
        params.add(limit);
        params.add(offset);
        return params.toArray();
    }

    /** 同上但中间插入 adv-filter 参数。 */
    private static Object[] buildChargesListParamsWithAdv(String search, String dateFrom, String dateTo,
                                                           java.util.List<Object> advParams,
                                                           BranchAccessFilter.AccessClause access,
                                                           int limit, int offset) {
        java.util.List<Object> params = new java.util.ArrayList<>(java.util.Arrays.asList(
            search, search, search, dateFrom, dateFrom, dateTo, dateTo));
        params.addAll(advParams);
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
        out.put("customerName", row.get("customer_name"));
        out.put("productName", row.get("channel_name"));
        out.put("country", row.get("destination_country"));
        out.put("chargeWeight", row.get("charge_weight"));
        out.put("type", row.get("charge_item_name"));
        out.put("amount", row.get("amount"));
        out.put("paid", row.get("paid_amount"));
        out.put("theDate", json.value(row.get("created_at")));
        out.put("auditName", "");
        out.put("status", row.get("status"));
        out.put("currency", row.get("currency"));
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        out.put("auditName", row.get("audit_name"));
        return out;
    }
}

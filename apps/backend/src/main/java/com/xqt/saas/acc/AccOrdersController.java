package com.xqt.saas.acc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import com.xqt.saas.common.BranchAccessFilter;
import com.xqt.saas.common.JsonSupport;
import com.xqt.saas.customerapi.CustomerApiPrincipal;
import com.xqt.saas.customerapi.CustomerApiService;
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

/**
 * /api/acc/orders — 前端列：orderNo / trackNo / customerName / product / country
 * / piece / chargeWeight / sellCharge / costCharge / branch / addTime
 *
 * 数据来源：orders + customers join，cartons 聚合件数，shipments 一对一拿 country。
 * sellCharge = SUM(charges where side='AR' and settlement_status<>'VOID') by customer_ref join shipments；
 * costCharge = SUM(charges where side='AP' and settlement_status<>'VOID')；
 * branch = orders.branch_id → organizations.name。
 */
@RestController
@RequestMapping("/api/acc/orders")
public class AccOrdersController {
    private static final String TABLE = "orders";

    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final CascadeChecker cascadeChecker;
    private final FieldGate fieldGate;
    private final BranchAccessFilter branchAccess;
    private final CustomerApiService customerApiService;

    public AccOrdersController(JdbcTemplate jdbc, JsonSupport json,
                               CascadeChecker cascadeChecker, FieldGate fieldGate,
                               BranchAccessFilter branchAccess,
                               CustomerApiService customerApiService) {
        this.jdbc = jdbc;
        this.json = json;
        this.cascadeChecker = cascadeChecker;
        this.fieldGate = fieldGate;
        this.branchAccess = branchAccess;
        this.customerApiService = customerApiService;
    }

    @GetMapping
    public Map<String, Object> list(
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer pageSize,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String dateFrom,
        @RequestParam(required = false) String dateTo,
        @RequestParam(required = false) String status,
        // ACC 高级搜索字段（22 个，对应 Online.php 搜索下拉）
        @RequestParam(required = false) String trackingNo,
        @RequestParam(required = false) String customerName,
        @RequestParam(required = false) String country,
        @RequestParam(required = false) String postcode,
        @RequestParam(required = false) String recipientName,
        @RequestParam(required = false) String recipientPhone,
        @RequestParam(required = false) String province,
        @RequestParam(required = false) String city,
        @RequestParam(required = false) String channelCode,
        @RequestParam(required = false) java.math.BigDecimal weightFrom,
        @RequestParam(required = false) java.math.BigDecimal weightTo,
        @RequestParam(required = false) java.math.BigDecimal declaredValueFrom,
        @RequestParam(required = false) java.math.BigDecimal declaredValueTo,
        // 补充 10 个 ACC 搜索字段
        @RequestParam(required = false) java.math.BigDecimal chargeWeightFrom,
        @RequestParam(required = false) java.math.BigDecimal chargeWeightTo,
        @RequestParam(required = false) java.math.BigDecimal feeFrom,
        @RequestParam(required = false) java.math.BigDecimal feeTo,
        @RequestParam(required = false) String recipientAddress,
        @RequestParam(required = false) String recipientHouseNo,
        @RequestParam(required = false) String remark,
        @RequestParam(required = false) String deliveryArea,    // 快件到达地区
        @RequestParam(required = false) String submittedFrom,
        @RequestParam(required = false) String submittedTo,
        @RequestParam(required = false) String addName,
        @RequestParam(required = false) String createdFrom,
        @RequestParam(required = false) String createdTo,
        @RequestParam(required = false) String updatedFrom,
        @RequestParam(required = false) String updatedTo
    ) {
        try {
            int limit = AccPaging.pageSize(pageSize);
            int offset = AccPaging.offset(page, pageSize);
            String search = keyword == null || keyword.isBlank() ? null : "%" + keyword + "%";
            String trackPat = trackingNo == null || trackingNo.isBlank() ? null : "%" + trackingNo + "%";
            String custNamePat = customerName == null || customerName.isBlank() ? null : "%" + customerName + "%";
            String recipientNamePat = recipientName == null || recipientName.isBlank() ? null : "%" + recipientName + "%";
            String recipientPhonePat = recipientPhone == null || recipientPhone.isBlank() ? null : "%" + recipientPhone + "%";
            String provincePat = province == null || province.isBlank() ? null : "%" + province + "%";
            String cityPat = city == null || city.isBlank() ? null : "%" + city + "%";
            String recipientAddressPat = recipientAddress == null || recipientAddress.isBlank() ? null : "%" + recipientAddress + "%";
            String recipientHouseNoPat = recipientHouseNo == null || recipientHouseNo.isBlank() ? null : "%" + recipientHouseNo + "%";
            String deliveryAreaPat = deliveryArea == null || deliveryArea.isBlank() ? null : "%" + deliveryArea + "%";

            // 高级过滤 EXISTS shipment 条件
            StringBuilder advFilter = new StringBuilder();
            java.util.List<Object> advParams = new java.util.ArrayList<>();
            boolean needShipmentJoin = trackPat != null || country != null || postcode != null
                || recipientNamePat != null || recipientPhonePat != null || provincePat != null
                || cityPat != null || channelCode != null || weightFrom != null || weightTo != null
                || declaredValueFrom != null || declaredValueTo != null
                || chargeWeightFrom != null || chargeWeightTo != null
                || recipientAddressPat != null || recipientHouseNoPat != null || deliveryAreaPat != null;
            if (needShipmentJoin) {
                advFilter.append(" AND EXISTS (SELECT 1 FROM shipments _s"
                    + " LEFT JOIN cartons _ct ON _ct.shipment_id = _s.id"
                    + " LEFT JOIN channels _cn ON _cn.id = _s.channel_id"
                    + " WHERE _s.tenant_id = o.tenant_id AND _s.customer_ref = o.customer_ref");
                if (trackPat != null)        { advFilter.append(" AND _ct.tracking_no ILIKE ?"); advParams.add(trackPat); }
                if (country != null)         { advFilter.append(" AND _s.destination_country = ?"); advParams.add(country); }
                if (postcode != null)        { advFilter.append(" AND _s.destination_postal_code ILIKE ?"); advParams.add("%" + postcode + "%"); }
                if (recipientNamePat != null){ advFilter.append(" AND _s.recipient_consignee ILIKE ?"); advParams.add(recipientNamePat); }
                if (recipientPhonePat != null){advFilter.append(" AND _s.recipient_phone ILIKE ?"); advParams.add(recipientPhonePat); }
                if (provincePat != null)     { advFilter.append(" AND _s.recipient_province ILIKE ?"); advParams.add(provincePat); }
                if (cityPat != null)         { advFilter.append(" AND _s.recipient_city ILIKE ?"); advParams.add(cityPat); }
                if (recipientAddressPat != null){advFilter.append(" AND _s.recipient_address ILIKE ?"); advParams.add(recipientAddressPat); }
                if (recipientHouseNoPat != null){advFilter.append(" AND _s.recipient_house_no ILIKE ?"); advParams.add(recipientHouseNoPat); }
                if (channelCode != null)     { advFilter.append(" AND _cn.code = ?"); advParams.add(channelCode); }
                if (weightFrom != null)      { advFilter.append(" AND _ct.actual_weight_kg >= ?"); advParams.add(weightFrom); }
                if (weightTo != null)        { advFilter.append(" AND _ct.actual_weight_kg <= ?"); advParams.add(weightTo); }
                if (chargeWeightFrom != null){ advFilter.append(" AND _ct.chargeable_weight_kg >= ?"); advParams.add(chargeWeightFrom); }
                if (chargeWeightTo != null)  { advFilter.append(" AND _ct.chargeable_weight_kg <= ?"); advParams.add(chargeWeightTo); }
                if (declaredValueFrom != null){advFilter.append(" AND _s.declared_value >= ?"); advParams.add(declaredValueFrom); }
                if (declaredValueTo != null) { advFilter.append(" AND _s.declared_value <= ?"); advParams.add(declaredValueTo); }
                if (deliveryAreaPat != null) { advFilter.append(" AND _s.recipient_area_code ILIKE ?"); advParams.add(deliveryAreaPat); }
                advFilter.append(")");
            }
            // 客户名过滤
            if (custNamePat != null) {
                advFilter.append(" AND EXISTS (SELECT 1 FROM customers _c WHERE _c.id = o.customer_id AND _c.name ILIKE ?)");
                advParams.add(custNamePat);
            }
            // 费用 EXISTS charges
            if (feeFrom != null || feeTo != null) {
                advFilter.append(" AND EXISTS (SELECT 1 FROM charges _ch JOIN shipments _s2 ON _s2.id = _ch.shipment_id"
                    + " WHERE _s2.tenant_id = o.tenant_id AND _s2.customer_ref = o.customer_ref AND _ch.side='AR'");
                if (feeFrom != null) { advFilter.append(" AND _ch.amount >= ?"); advParams.add(feeFrom); }
                if (feeTo != null)   { advFilter.append(" AND _ch.amount <= ?"); advParams.add(feeTo); }
                advFilter.append(")");
            }
            // 备注模糊
            if (remark != null && !remark.isBlank()) {
                advFilter.append(" AND (o.metadata->>'remark' ILIKE ? OR o.metadata->'acc_compat'->>'remark' ILIKE ?)");
                advParams.add("%" + remark + "%"); advParams.add("%" + remark + "%");
            }
            // 提交时间
            if (submittedFrom != null && !submittedFrom.isBlank()) {
                advFilter.append(" AND o.submitted_at >= ?::date"); advParams.add(submittedFrom);
            }
            if (submittedTo != null && !submittedTo.isBlank()) {
                advFilter.append(" AND o.submitted_at < (?::date + 1)"); advParams.add(submittedTo);
            }
            // 添加人
            if (addName != null && !addName.isBlank()) {
                advFilter.append(" AND EXISTS (SELECT 1 FROM users _u WHERE _u.id = o.created_by AND _u.display_name ILIKE ?)");
                advParams.add("%" + addName + "%");
            }
            // 添加时间 / 修改时间
            if (createdFrom != null && !createdFrom.isBlank()) { advFilter.append(" AND o.created_at >= ?::date"); advParams.add(createdFrom); }
            if (createdTo != null && !createdTo.isBlank())     { advFilter.append(" AND o.created_at < (?::date + 1)"); advParams.add(createdTo); }
            if (updatedFrom != null && !updatedFrom.isBlank()) { advFilter.append(" AND o.updated_at >= ?::date"); advParams.add(updatedFrom); }
            if (updatedTo != null && !updatedTo.isBlank())     { advFilter.append(" AND o.updated_at < (?::date + 1)"); advParams.add(updatedTo); }

            String advFilterSql = advFilter.toString();

            // status 过滤模式：DRAFT/CANCELLED/HISTORY/具体值
            String statusMode = status == null || status.isBlank() ? null : status.toUpperCase();
            // RBAC 分公司/销售可见性
            var access = branchAccess.forCurrent("o");

            java.util.List<Object> countParams = new java.util.ArrayList<>(java.util.Arrays.asList(
                search, search, search, dateFrom, dateFrom, dateTo, dateTo,
                statusMode, statusMode, statusMode, statusMode));
            countParams.addAll(advParams);
            countParams.addAll(access.params());
            Long total = jdbc.queryForObject(
                "SELECT count(*) FROM orders o"
                + " WHERE (?::text IS NULL OR (o.order_no ILIKE ? OR o.customer_ref ILIKE ?))"
                + "   AND (?::date IS NULL OR o.created_at >= ?::date)"
                + "   AND (?::date IS NULL OR o.created_at < (?::date + 1))"
                + "   AND ("
                + "     ?::text IS NULL"
                + "     OR (?::text = 'HISTORY' AND o.status NOT IN ('DRAFT', 'CANCELLED', 'CANCELED', 'VOID'))"
                + "     OR (?::text = 'VOID_AUDIT' AND (o.metadata->'void_request' IS NOT NULL))"
                + "     OR o.status = ?::text"
                + "   )"
                + advFilterSql
                + access.sql(),
                Long.class, countParams.toArray());

            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT
                  o.id::text          AS id,
                  o.order_no,
                  o.customer_ref,
                  o.status,
                  o.created_at,
                  o.audit_status,
                  o.audited_at,
                  o.audit_name,
                  c.name              AS customer_name,
                  org.name            AS branch_name,
                  (
                    SELECT s.destination_country FROM shipments s
                    WHERE s.tenant_id = o.tenant_id AND s.customer_ref = o.customer_ref
                    LIMIT 1
                  ) AS country,
                  (
                    SELECT ct.tracking_no FROM cartons ct
                    JOIN shipments s2 ON s2.id = ct.shipment_id
                    WHERE s2.tenant_id = o.tenant_id AND s2.customer_ref = o.customer_ref
                      AND ct.tracking_no IS NOT NULL
                    ORDER BY ct.carton_no LIMIT 1
                  ) AS track_no,
                  (
                    SELECT count(*) FROM cartons ct
                    JOIN shipments s3 ON s3.id = ct.shipment_id
                    WHERE s3.tenant_id = o.tenant_id AND s3.customer_ref = o.customer_ref
                  ) AS piece_count,
                  (
                    SELECT coalesce(sum(ct.chargeable_weight_kg), sum(ct.actual_weight_kg))
                    FROM cartons ct
                    JOIN shipments s4 ON s4.id = ct.shipment_id
                    WHERE s4.tenant_id = o.tenant_id AND s4.customer_ref = o.customer_ref
                  ) AS charge_weight,
                  (
                    SELECT coalesce(sum(ch.amount), 0) FROM charges ch
                    JOIN shipments s5 ON s5.id = ch.shipment_id
                    WHERE s5.tenant_id = o.tenant_id AND s5.customer_ref = o.customer_ref
                      AND ch.side = 'AR' AND ch.settlement_status <> 'VOID'
                  ) AS sell_charge,
                  (
                    SELECT coalesce(sum(ch.amount), 0) FROM charges ch
                    JOIN shipments s6 ON s6.id = ch.shipment_id
                    WHERE s6.tenant_id = o.tenant_id AND s6.customer_ref = o.customer_ref
                      AND ch.side = 'AP' AND ch.settlement_status <> 'VOID'
                  ) AS cost_charge,
                  o.metadata
                FROM orders o
                LEFT JOIN customers c ON c.id = o.customer_id
                LEFT JOIN organizations org ON org.id = o.branch_id
                WHERE 1=1
                """
                + " AND (?::text IS NULL OR (o.order_no ILIKE ? OR o.customer_ref ILIKE ?))"
                + " AND (?::date IS NULL OR o.created_at >= ?::date)"
                + " AND (?::date IS NULL OR o.created_at < (?::date + 1))"
                + " AND ("
                + "   ?::text IS NULL"
                + "   OR (?::text = 'HISTORY' AND o.status NOT IN ('DRAFT', 'CANCELLED', 'CANCELED', 'VOID'))"
                + "   OR (?::text = 'VOID_AUDIT' AND (o.metadata->'void_request' IS NOT NULL))"
                + "   OR o.status = ?::text"
                + " )"
                + advFilterSql
                + access.sql()
                + " ORDER BY o.created_at DESC"
                + " LIMIT ? OFFSET ?",
                buildListParams(search, dateFrom, dateTo, statusMode, advParams, access, limit, offset));
            return AccPaging.result(rows.stream().map(this::project).toList(),
                total == null ? 0 : total);
        } catch (DataAccessException ex) {
            return AccPaging.result(List.of(), 0);
        }
    }

    @GetMapping("/{id}/raw")
    public Map<String, Object> raw(@PathVariable String id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM orders WHERE id = ?::uuid LIMIT 1", id);
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String orderNo = (String) body.getOrDefault("order_no", body.get("orderNo"));
        String customerRef = (String) body.getOrDefault("customer_ref", body.get("customerRef"));
        Object customerId = body.get("customer_id");
        String id = jdbc.queryForObject("""
            INSERT INTO orders (
              tenant_id, order_no, customer_id, status, source, customer_ref
            ) VALUES (
              current_setting('app.current_tenant_id')::uuid,
              ?, ?::uuid, 'DRAFT', 'LOCAL', ?
            )
            RETURNING id::text
            """, String.class, orderNo, customerId == null ? null : customerId.toString(), customerRef);
        return Map.of("id", id, "order_no", orderNo);
    }

    /**
     * ACC「添加制单」完整表单。对照 ACC PHP `制单中心 → 添加制单` 的 7 个分区：
     * 基本信息 / 发货渠道 / 货物信息 / 发票信息 / 收件人 / 发件人 / 进口商 / 申报明细 / 装箱单明细。
     *
     * 一次 INSERT orders + N 条 declarations + 1 条 shipments + N 条 cartons，
     * 全部 metadata（包裹类型、电池、特殊货物、附加服务、shipper/shipTo 等）持久化到 orders.metadata，
     * 后续 PreSubmit / Submit / RateEngine 都能读到。
     */
    @PostMapping("/full")
    @SuppressWarnings("unchecked")
    public Map<String, Object> createFull(@RequestBody Map<String, Object> body) {
        String orderNo = strOrNull(body.get("orderNo"));
        String customerRef = strOrDefault(body.get("customerRef"), orderNo);
        String customerId = strOrNull(body.get("customerId"));
        if (orderNo == null || orderNo.isBlank()) {
            throw ApiException.badRequest("orderNo 必填");
        }
        if (customerId == null) {
            throw ApiException.badRequest("customerId 必填");
        }
        // 业务字段 → metadata.acc_compat（与 customer-api submit 同结构）
        Map<String, Object> accCompat = new LinkedHashMap<>();
        accCompat.put("product", strOrNull(body.get("product")));
        accCompat.put("channelAccount", strOrNull(body.get("channelAccount")));
        accCompat.put("packageType", strOrNull(body.get("packageType")));
        accCompat.put("batteryType", body.get("batteryType"));
        accCompat.put("specialType", body.get("specialType"));
        accCompat.put("labelType", strOrNull(body.get("labelType")));
        accCompat.put("country", strOrNull(body.get("country")));
        accCompat.put("weight", body.get("weight"));
        accCompat.put("piece", body.get("piece"));
        accCompat.put("volume", body.get("volume"));
        accCompat.put("currency", strOrDefault(body.get("currency"), "USD"));
        accCompat.put("declaredValue", body.get("declaredValue"));
        accCompat.put("freight", body.get("freight"));
        accCompat.put("insurance", body.get("insurance"));
        accCompat.put("materialsEn", strOrNull(body.get("materialsEn")));
        accCompat.put("materialsCn", strOrNull(body.get("materialsCn")));
        accCompat.put("batteryCode", strOrNull(body.get("batteryCode")));
        accCompat.put("services", body.get("services"));
        accCompat.put("remark", strOrNull(body.get("remark")));
        accCompat.put("receiver", body.get("receiver"));
        accCompat.put("shipper", body.get("shipper"));
        accCompat.put("shipTo", body.get("shipTo"));
        accCompat.put("declare", body.get("declare"));
        accCompat.put("packageList", body.get("packageList"));

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("acc_compat", accCompat);
        String metaJson = json.toJson(metadata);

        // 1) INSERT orders
        String orderId = jdbc.queryForObject("""
            INSERT INTO orders (
              tenant_id, order_no, customer_id, status, source, customer_ref, metadata
            ) VALUES (
              current_setting('app.current_tenant_id')::uuid,
              ?, ?::uuid, 'DRAFT', 'LOCAL', ?, ?::jsonb
            )
            RETURNING id::text
            """, String.class, orderNo, customerId, customerRef, metaJson);

        // 申报明细 + 装箱单明细 持久化到 orders.metadata.acc_compat（与 customer-api 同结构）。
        // Submit 阶段再 INSERT 到 declarations / cartons 表（需要 shipment_id 关联）。
        int declareCount = body.get("declare") instanceof List<?> dl ? dl.size() : 0;
        int packageCount = body.get("packageList") instanceof List<?> pl ? pl.size() : 0;

        return Map.of(
            "id", orderId,
            "orderNo", orderNo,
            "declarations", declareCount,
            "packageCount", packageCount,
            "status", "DRAFT"
        );
    }

    /**
     * 拼接 list 查询的参数数组：base 字段 + RBAC access 参数 + 分页 limit/offset。
     */
    private static Object[] buildListParams(String search, String dateFrom, String dateTo,
                                             String statusMode,
                                             java.util.List<Object> advParams,
                                             BranchAccessFilter.AccessClause access,
                                             int limit, int offset) {
        java.util.List<Object> params = new java.util.ArrayList<>(java.util.Arrays.asList(
            search, search, search,
            dateFrom, dateFrom, dateTo, dateTo,
            statusMode, statusMode, statusMode, statusMode));
        params.addAll(advParams);
        params.addAll(access.params());
        params.add(limit);
        params.add(offset);
        return params.toArray();
    }

    private static String strOrNull(Object o) {
        return o == null || o.toString().isBlank() ? null : o.toString();
    }
    private static String strOrDefault(Object o, String def) {
        String s = strOrNull(o);
        return s == null ? def : s;
    }
    private static Object asNumber(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return o;
        try { return new java.math.BigDecimal(o.toString()); } catch (Exception ignored) { return null; }
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String currentAudit = jdbc.queryForObject(
            "SELECT audit_status FROM orders WHERE id = ?::uuid", String.class, id);
        FieldGate.FilterResult gate = fieldGate.filterAllowedFields(TABLE, currentAudit, body);
        if (!gate.rejected().isEmpty() && gate.allowed().isEmpty()) {
            throw ApiException.badRequest("订单已审核，字段不可修改: " + String.join(",", gate.rejected())
                + "；请先反审");
        }
        Map<String, Object> allowed = gate.allowed();
        jdbc.update("""
            UPDATE orders SET
              order_no     = coalesce(?, order_no),
              customer_ref = coalesce(?, customer_ref),
              status       = coalesce(?, status)
            WHERE id = ?::uuid
            """,
            (String) allowed.get("order_no"),
            (String) allowed.get("customer_ref"),
            (String) allowed.get("status"),
            id);
        return Map.of("id", id, "rejectedFields", gate.rejected());
    }

    /**
     * 申请作废订单 — 对应 ACC 制单中心「申请作废」按钮。
     *
     * 仅把 audit_status 置 'PENDING' 进入"作废待审"队列；不立即改 status。
     * 后续走 /api/acc/orders/{id}/audit-biz → OrderVoidAuditSideEffect → status='VOID'。
     */
    @PostMapping("/{id}/request-void")
    public Map<String, Object> requestVoid(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        String reason = body == null ? null : strOrNull(body.get("reason"));
        int n = jdbc.update("""
            UPDATE orders
            SET audit_status = 'PENDING',
                metadata = jsonb_set(
                  coalesce(metadata, '{}'::jsonb),
                  '{acc_compat,void_request_reason}',
                  to_jsonb(coalesce(?::text, ''))
                )
            WHERE id = ?::uuid AND status NOT IN ('CANCELLED', 'DRAFT')
            """, reason, id);
        if (n == 0) {
            throw ApiException.badRequest("订单状态不允许申请作废（DRAFT/VOID 已不需作废）");
        }
        return Map.of("id", id, "audit_status", "PENDING", "void_requested", true);
    }

    /**
     * 制单中心「提交」按钮 — 对应 ACC act=Submit。
     * 走 CustomerApiService.submitOrder：DRAFT→SUBMITTED + RateEngine 算费 + CarrierGateway 取号 + 写 shipments/charges。
     * 管理员调用：从订单本身拿 tenant/customer 凑出 Principal，跳过 HMAC。
     */
    @PostMapping("/{id}/submit")
    public Map<String, Object> submit(@PathVariable String id) {
        Map<String, Object> ctx;
        try {
            ctx = jdbc.queryForMap(
                "SELECT o.tenant_id::text AS tenant_id, o.customer_id::text AS customer_id,"
                + " o.order_no, c.code AS customer_code"
                + " FROM orders o JOIN customers c ON c.id=o.customer_id"
                + " WHERE o.id = ?::uuid", id);
        } catch (DataAccessException ex) {
            throw ApiException.notFound("order not found: " + id);
        }
        CustomerApiPrincipal principal = new CustomerApiPrincipal(
            null,
            (String) ctx.get("tenant_id"),
            (String) ctx.get("customer_id"),
            (String) ctx.get("customer_code"),
            null, null
        );
        var r = customerApiService.submitOrder(principal, (String) ctx.get("order_no"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("orderNo", ctx.get("order_no"));
        out.put("submitted", true);
        out.put("result", r);
        return out;
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        String auditStatus = jdbc.queryForObject(
            "SELECT audit_status FROM orders WHERE id = ?::uuid", String.class, id);
        if ("AUDITED".equals(auditStatus)) {
            throw ApiException.badRequest("订单已审核，不能删除，请先反审");
        }
        cascadeChecker.checkBeforeDelete(TABLE, id);
        jdbc.update("DELETE FROM orders WHERE id = ?::uuid", id);
        return Map.of("id", id, "deleted", true);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("orderNo", row.get("customer_ref") != null ? row.get("customer_ref") : row.get("order_no"));
        out.put("trackNo", row.get("track_no"));
        out.put("customerName", row.get("customer_name"));
        // product 来自 metadata.acc_compat.product（API 下单时存在那里）
        String product = "";
        Object meta = row.get("metadata");
        if (meta != null) {
            String metaText = meta instanceof String s ? s : meta.toString();
            try {
                Map<String, Object> m = json.fromJson(metaText,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                Object acc = m == null ? null : m.get("acc_compat");
                if (acc instanceof Map<?, ?> accMap) {
                    Object p = ((Map<String, Object>) accMap).get("product");
                    if (p != null) product = p.toString();
                }
            } catch (RuntimeException ignored) {
                // metadata 解析失败时降级为空字符串
            }
        }
        out.put("product", product);
        out.put("country", row.get("country"));
        out.put("piece", row.get("piece_count"));
        out.put("chargeWeight", row.get("charge_weight"));
        out.put("sellCharge", row.get("sell_charge"));
        out.put("costCharge", row.get("cost_charge"));
        out.put("branch", row.get("branch_name") == null ? "" : row.get("branch_name"));
        out.put("addTime", json.value(row.get("created_at")));
        out.put("status", row.get("status"));
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        out.put("auditName", row.get("audit_name"));
        return out;
    }
}

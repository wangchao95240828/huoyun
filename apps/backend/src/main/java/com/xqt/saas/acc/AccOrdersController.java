package com.xqt.saas.acc;

import java.math.BigDecimal;
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
import org.springframework.transaction.annotation.Transactional;

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
    private final OrderNoGenerator orderNoGenerator;

    public AccOrdersController(JdbcTemplate jdbc, JsonSupport json,
                               CascadeChecker cascadeChecker, FieldGate fieldGate,
                               BranchAccessFilter branchAccess,
                               CustomerApiService customerApiService,
                               OrderNoGenerator orderNoGenerator) {
        this.jdbc = jdbc;
        this.json = json;
        this.cascadeChecker = cascadeChecker;
        this.fieldGate = fieldGate;
        this.orderNoGenerator = orderNoGenerator;
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
        @RequestParam(required = false) String updatedTo,
        // 高级多条件筛选（多值用 comma 分隔。对齐 ACC 大货运单页多选）
        @RequestParam(required = false) String customerIds,
        @RequestParam(required = false) String channelCodes,
        @RequestParam(required = false) String countries,
        @RequestParam(required = false) String statuses,
        @RequestParam(required = false) String auditStatuses,
        @RequestParam(required = false) String addNames,
        @RequestParam(required = false) String branchIds,
        @RequestParam(required = false) String sellerIds,
        @RequestParam(required = false) String servicerIds,
        @RequestParam(required = false) String warehouseCodes,
        @RequestParam(required = false) String carriers,
        @RequestParam(required = false) String vatNo,
        @RequestParam(required = false) String poNumber,
        @RequestParam(required = false) String amazonRef,
        @RequestParam(required = false) String binLocation,
        @RequestParam(required = false) String mainItem,
        @RequestParam(required = false) String deliveredFrom,
        @RequestParam(required = false) String deliveredTo,
        // 财务对账：missingCost=true 仅看 AR > 0 但 AP = 0 的订单（缺成本）
        @RequestParam(required = false) Boolean missingCost
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

            // ───── 多选筛选（comma 分隔）─────
            java.util.List<String> cidList   = splitCsv(customerIds);
            java.util.List<String> chList    = splitCsv(channelCodes);
            java.util.List<String> ctList    = splitCsv(countries);
            java.util.List<String> stList    = splitCsv(statuses);
            java.util.List<String> auList    = splitCsv(auditStatuses);
            java.util.List<String> anList    = splitCsv(addNames);
            if (!cidList.isEmpty()) {
                advFilter.append(" AND o.customer_id::text IN (").append(qMarks(cidList.size())).append(")");
                advParams.addAll(cidList);
            }
            if (!chList.isEmpty()) {
                advFilter.append(" AND EXISTS (SELECT 1 FROM shipments _scc"
                    + " JOIN channels _cnc ON _cnc.id = _scc.channel_id"
                    + " WHERE _scc.tenant_id = o.tenant_id AND _scc.customer_ref = o.customer_ref"
                    + " AND _cnc.code IN (").append(qMarks(chList.size())).append("))");
                advParams.addAll(chList);
            }
            if (!ctList.isEmpty()) {
                advFilter.append(" AND EXISTS (SELECT 1 FROM shipments _sct"
                    + " WHERE _sct.tenant_id = o.tenant_id AND _sct.customer_ref = o.customer_ref"
                    + " AND _sct.destination_country IN (").append(qMarks(ctList.size())).append("))");
                advParams.addAll(ctList);
            }
            if (!stList.isEmpty()) {
                advFilter.append(" AND o.status IN (").append(qMarks(stList.size())).append(")");
                advParams.addAll(stList);
            }
            if (!auList.isEmpty()) {
                advFilter.append(" AND o.audit_status IN (").append(qMarks(auList.size())).append(")");
                advParams.addAll(auList);
            }
            if (!anList.isEmpty()) {
                advFilter.append(" AND EXISTS (SELECT 1 FROM users _uan"
                    + " WHERE _uan.id = o.created_by AND _uan.display_name IN ("
                    + qMarks(anList.size()) + "))");
                advParams.addAll(anList);
            }
            // ──── 新增 5 个多选 + 6 个文本 + 1 个时间区间 ────
            java.util.List<String> brList = splitCsv(branchIds);
            java.util.List<String> slList = splitCsv(sellerIds);
            java.util.List<String> svList = splitCsv(servicerIds);
            java.util.List<String> whList = splitCsv(warehouseCodes);
            java.util.List<String> caList = splitCsv(carriers);
            if (!brList.isEmpty()) {
                advFilter.append(" AND o.branch_id::text IN (").append(qMarks(brList.size())).append(")");
                advParams.addAll(brList);
            }
            if (!slList.isEmpty()) {
                advFilter.append(" AND o.seller_id::text IN (").append(qMarks(slList.size())).append(")");
                advParams.addAll(slList);
            }
            if (!svList.isEmpty()) {
                advFilter.append(" AND o.servicer_id::text IN (").append(qMarks(svList.size())).append(")");
                advParams.addAll(svList);
            }
            if (!whList.isEmpty()) {
                advFilter.append(" AND EXISTS (SELECT 1 FROM shipments _swh WHERE _swh.tenant_id=o.tenant_id"
                    + " AND _swh.customer_ref=o.customer_ref"
                    + " AND _swh.destination_warehouse_code IN (").append(qMarks(whList.size())).append("))");
                advParams.addAll(whList);
            }
            if (!caList.isEmpty()) {
                // 承运快递通过 channel_cost_policies → carriers 表关联
                advFilter.append(" AND EXISTS (SELECT 1 FROM shipments _sca"
                    + " JOIN channel_cost_policies _ccp ON _ccp.channel_id=_sca.channel_id"
                    + " JOIN carriers _car ON _car.id=_ccp.carrier_id"
                    + " WHERE _sca.tenant_id=o.tenant_id AND _sca.customer_ref=o.customer_ref"
                    + " AND upper(_car.name) IN (").append(qMarks(caList.size())).append("))");
                for (String c : caList) advParams.add(c.toUpperCase());
            }
            // 文本字段（前端单值；recipientName 由原始上面 recipientNamePat 处理）
            if (vatNo != null && !vatNo.isBlank()) {
                advFilter.append(" AND EXISTS (SELECT 1 FROM shipments _svat WHERE _svat.tenant_id=o.tenant_id"
                    + " AND _svat.customer_ref=o.customer_ref"
                    + " AND (_svat.recipient_tax_no ILIKE ? OR _svat.shipper_tax_no ILIKE ? OR _svat.sold_to_tax_no ILIKE ?))");
                String pat = "%" + vatNo + "%";
                advParams.add(pat); advParams.add(pat); advParams.add(pat);
            }
            if (poNumber != null && !poNumber.isBlank()) {
                advFilter.append(" AND (o.customer_ref ILIKE ? OR o.metadata->'acc_compat'->>'poNumber' ILIKE ?)");
                advParams.add("%" + poNumber + "%"); advParams.add("%" + poNumber + "%");
            }
            if (amazonRef != null && !amazonRef.isBlank()) {
                advFilter.append(" AND (o.external_id ILIKE ? OR o.metadata->'acc_compat'->>'amazonRef' ILIKE ?)");
                advParams.add("%" + amazonRef + "%"); advParams.add("%" + amazonRef + "%");
            }
            if (binLocation != null && !binLocation.isBlank()) {
                advFilter.append(" AND o.metadata->'acc_compat'->>'binLocation' ILIKE ?");
                advParams.add("%" + binLocation + "%");
            }
            if (mainItem != null && !mainItem.isBlank()) {
                advFilter.append(" AND (o.metadata->'acc_compat'->>'materialsEn' ILIKE ?"
                    + " OR o.metadata->'acc_compat'->>'materialsCn' ILIKE ?)");
                advParams.add("%" + mainItem + "%"); advParams.add("%" + mainItem + "%");
            }
            if (deliveredFrom != null && !deliveredFrom.isBlank()) {
                advFilter.append(" AND EXISTS (SELECT 1 FROM shipments _sdf WHERE _sdf.tenant_id=o.tenant_id"
                    + " AND _sdf.customer_ref=o.customer_ref AND _sdf.delivered_at >= ?::date)");
                advParams.add(deliveredFrom);
            }
            if (deliveredTo != null && !deliveredTo.isBlank()) {
                advFilter.append(" AND EXISTS (SELECT 1 FROM shipments _sdt WHERE _sdt.tenant_id=o.tenant_id"
                    + " AND _sdt.customer_ref=o.customer_ref AND _sdt.delivered_at < (?::date + 1))");
                advParams.add(deliveredTo);
            }
            // 缺成本筛选：有 AR 但没有 AP（NOT VOID）
            if (Boolean.TRUE.equals(missingCost)) {
                advFilter.append(" AND EXISTS (SELECT 1 FROM charges _ar"
                    + " JOIN shipments _sar ON _sar.id = _ar.shipment_id"
                    + " WHERE _sar.tenant_id=o.tenant_id AND _sar.customer_ref=o.customer_ref"
                    + " AND _ar.side='AR' AND _ar.settlement_status <> 'VOID')");
                advFilter.append(" AND NOT EXISTS (SELECT 1 FROM charges _ap"
                    + " JOIN shipments _sap ON _sap.id = _ap.shipment_id"
                    + " WHERE _sap.tenant_id=o.tenant_id AND _sap.customer_ref=o.customer_ref"
                    + " AND _ap.side='AP' AND _ap.settlement_status <> 'VOID')");
            }

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

    /**
     * 订单财务详情：本单 charges + balance_ledger + 客户余额。
     * 给制单 UI 行内"看财务"按钮用，让 OP 知道这单扣了多少 / 客户余额够不够。
     */
    @GetMapping("/{id}/finance")
    public Map<String, Object> finance(@PathVariable String id) {
        // 1) 本单 charges
        List<Map<String, Object>> charges = jdbc.queryForList("""
            SELECT ch.id::text AS id, ch.side, ch.amount, ch.currency,
                   ch.status::text AS status, ch.audit_status, ch.settlement_status,
                   ch.paid_amount, ch.created_at,
                   ci.invoice_no AS invoice_no, ci.id::text AS invoice_id
              FROM charges ch
              LEFT JOIN customer_invoice_lines il ON il.charge_id = ch.id
              LEFT JOIN customer_invoices ci ON ci.id = il.invoice_id
             WHERE ch.order_id = ?::uuid
             ORDER BY ch.side, ch.created_at
            """, id);

        // 2) 本单相关 ledger
        List<Map<String, Object>> ledger = jdbc.queryForList("""
            SELECT created_at, biz_type::text AS biz_type, direction::text AS direction,
                   amount, balance_before, balance_after, remark
              FROM balance_ledger
             WHERE source_id IN (
                SELECT id FROM charges WHERE order_id = ?::uuid
             )
                OR (source_type = 'order' AND source_ref = (SELECT order_no FROM orders WHERE id = ?::uuid))
             ORDER BY created_at DESC LIMIT 30
            """, id, id);

        // 3) 客户余额（按订单 currency 拿）
        Map<String, Object> balance = Map.of();
        try {
            Map<String, Object> ord = jdbc.queryForMap("""
                SELECT customer_id::text AS customer_id,
                       metadata->'acc_compat'->>'currency' AS currency
                  FROM orders WHERE id = ?::uuid
                """, id);
            if (ord.get("customer_id") != null) {
                String currency = (String) ord.get("currency");
                if (currency == null || currency.isBlank()) currency = "USD";
                BigDecimal usable = BigDecimal.ZERO;
                List<BigDecimal> bs = jdbc.queryForList("""
                    SELECT balance FROM financial_accounts
                     WHERE owner_type='CUSTOMER' AND owner_id=?::uuid AND currency=? LIMIT 1
                    """, BigDecimal.class, ord.get("customer_id"), currency);
                if (!bs.isEmpty()) usable = bs.get(0);
                balance = Map.of(
                    "customerId", ord.get("customer_id"),
                    "currency", currency,
                    "usableBalance", usable
                );
            }
        } catch (Exception ignored) {}

        return Map.of(
            "charges", charges,
            "ledger", ledger,
            "balance", balance
        );
    }

    /**
     * ACC 风格订单详情聚合 — 一次拉够：基本/货物/收件/发件/进口商/申报/装箱/财务/跟踪/审计。
     * 前端直接 render 不用做多次 fetch。对应 ACC 旧版 「订单详情」 7 个分区。
     */
    @GetMapping("/{id}/detail")
    public Map<String, Object> detail(@PathVariable String id) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();

        // 1) 基本信息 + customer + branch join
        Map<String, Object> basic;
        try {
            basic = jdbc.queryForMap("""
                SELECT o.id::text AS id, o.order_no, o.status, o.source, o.customer_ref,
                       o.external_id, o.customer_id::text AS customer_id,
                       o.created_at, o.submitted_at, o.accepted_at, o.completed_at,
                       o.metadata,
                       c.name AS customer_name, c.code AS customer_code,
                       org.name AS branch_name
                  FROM orders o
                  LEFT JOIN customers c ON c.id = o.customer_id
                  LEFT JOIN organizations org ON org.id = o.branch_id
                 WHERE o.id = ?::uuid
                """, id);
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            throw ApiException.notFound("订单不存在");
        }
        // metadata 拆出来给货物/收件人/发件人/进口商/附加服务等 ACC 字段
        Object md = basic.remove("metadata");
        result.put("basic", json.row(basic));
        result.put("metadata", md);

        // 2) 相关 shipments
        List<Map<String, Object>> shipments = jdbc.queryForList("""
            SELECT s.id::text AS id, s.shipment_no, s.status::text AS status,
                   s.destination_country, s.destination_postal_code,
                   s.destination_warehouse_code, s.declared_value, s.declared_currency,
                   s.insured, s.ordered_at, s.warehouse_in_at, s.departed_at, s.delivered_at,
                   ch.name AS channel_name, ch.code AS channel_code
              FROM shipment_order_links sol
              JOIN shipments s ON s.id = sol.shipment_id
              LEFT JOIN channels ch ON ch.id = s.channel_id
             WHERE sol.order_id = ?::uuid
             ORDER BY s.shipment_no
            """, id);
        result.put("shipments", shipments);

        // 3) cartons + declarations 跨所有 shipments
        List<String> shipmentIds = shipments.stream()
            .map(s -> (String) s.get("id")).toList();
        List<Map<String, Object>> cartons = List.of();
        List<Map<String, Object>> declarations = List.of();
        if (!shipmentIds.isEmpty()) {
            String inClause = String.join(",", shipmentIds.stream().map(s -> "?").toList());
            Object[] args = shipmentIds.toArray();
            cartons = jdbc.queryForList(
                "SELECT s.shipment_no, c.carton_no, c.tracking_no, c.carrier_master_tracking_no,"
                + "       c.actual_weight_kg, c.chargeable_weight_kg, c.cbm,"
                + "       c.length_cm, c.width_cm, c.height_cm"
                + "  FROM cartons c JOIN shipments s ON s.id = c.shipment_id"
                + " WHERE c.shipment_id::text IN (" + inClause + ")"
                + " ORDER BY s.shipment_no, c.carton_no", args);
            declarations = jdbc.queryForList(
                "SELECT s.shipment_no, d.item_name, d.material, d.hs_code, d.quantity, d.value_amount"
                + "  FROM declarations d JOIN shipments s ON s.id = d.shipment_id"
                + " WHERE d.shipment_id::text IN (" + inClause + ")"
                + " ORDER BY s.shipment_no, d.item_name", args);
        }
        result.put("cartons", cartons);
        result.put("declarations", declarations);

        // 4) charges + invoice
        List<Map<String, Object>> charges = jdbc.queryForList("""
            SELECT ch.id::text AS id, ch.side, ch.amount, ch.currency,
                   ch.status::text AS status, ch.audit_status, ch.settlement_status,
                   ch.paid_amount, ch.created_at,
                   ci.invoice_no, ci.id::text AS invoice_id
              FROM charges ch
              LEFT JOIN customer_invoice_lines il ON il.charge_id = ch.id
              LEFT JOIN customer_invoices ci ON ci.id = il.invoice_id
             WHERE ch.order_id = ?::uuid
             ORDER BY ch.side, ch.created_at
            """, id);
        result.put("charges", charges);

        // 5) ledger
        List<Map<String, Object>> ledger = jdbc.queryForList("""
            SELECT created_at, biz_type::text AS biz_type, direction::text AS direction,
                   amount, balance_before, balance_after, remark
              FROM balance_ledger
             WHERE source_id IN (SELECT id FROM charges WHERE order_id = ?::uuid)
                OR (source_type = 'order' AND source_ref = (SELECT order_no FROM orders WHERE id = ?::uuid))
             ORDER BY created_at DESC LIMIT 50
            """, id, id);
        result.put("ledger", ledger);

        // 6) tracking events 跨所有 cartons
        List<Map<String, Object>> trackingEvents = List.of();
        if (!shipmentIds.isEmpty()) {
            String inClause = String.join(",", shipmentIds.stream().map(s -> "?").toList());
            trackingEvents = jdbc.queryForList(
                "SELECT event_time, raw_status, normalized_status::text AS normalized_status,"
                + "       location, source::text AS source, tracking_no"
                + "  FROM tracking_events"
                + " WHERE shipment_id::text IN (" + inClause + ")"
                + " ORDER BY event_time DESC LIMIT 100",
                shipmentIds.toArray());
        }
        result.put("trackingEvents", trackingEvents);

        // 7) audit_events for this order
        List<Map<String, Object>> auditHistory = jdbc.queryForList("""
            SELECT occurred_at, action, actor_name, remark
              FROM audit_events
             WHERE entity_type = 'orders' AND entity_id = ?
             ORDER BY occurred_at DESC LIMIT 50
            """, id);
        result.put("auditHistory", auditHistory);

        return result;
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
        String customerId = strOrNull(body.get("customerId"));
        if (customerId == null) {
            throw ApiException.badRequest("customerId 必填");
        }
        // ACC 对齐：前端不传 orderNo 就走 OrderNoGenerator 生成
        // (CompanyNo 风格：YYYYMMDD + 3 位大写字母，DB 唯一约束抢占防并发冲突)
        if (orderNo == null || orderNo.isBlank()) {
            orderNo = orderNoGenerator.generate("ORDER");
        }
        String customerRef = strOrDefault(body.get("customerRef"), orderNo);
        // 自定义字段校验 — 集成 AccCustomFieldsController
        Object customFieldsRaw = body.get("customFields");
        if (customFieldsRaw instanceof Map<?, ?> cfMap) {
            // 拉 orders 表的自定义字段定义
            List<Map<String, Object>> defs = jdbc.queryForList("""
                SELECT field_key, field_label, field_type, is_required
                  FROM acc_custom_fields WHERE table_name = 'orders' AND is_active = true
                """);
            for (Map<String, Object> def : defs) {
                String key = (String) def.get("field_key");
                String label = (String) def.get("field_label");
                Boolean required = (Boolean) def.get("is_required");
                Object v = cfMap.get(key);
                if (Boolean.TRUE.equals(required) && (v == null || v.toString().isBlank())) {
                    throw ApiException.badRequest("自定义字段 " + label + " 必填");
                }
            }
        }
        // 业务字段 → metadata.acc_compat（与 customer-api submit 同结构）
        Map<String, Object> accCompat = new LinkedHashMap<>();
        if (customFieldsRaw instanceof Map<?, ?> cf) {
            accCompat.put("customFields", cf);
        }
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
        accCompat.put("departurePort", strOrNull(body.get("departurePort")));
        accCompat.put("arrivalPort", strOrNull(body.get("arrivalPort")));
        accCompat.put("receiver", body.get("receiver"));
        accCompat.put("shipper", body.get("shipper"));
        accCompat.put("shipTo", body.get("shipTo"));
        accCompat.put("declare", body.get("declare"));
        accCompat.put("packageList", body.get("packageList"));

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("acc_compat", accCompat);
        String metaJson = json.toJson(metadata);

        // 1) INSERT orders — 同时落 ACC Express 表单的 8 个 header 字段为一等列
        String surchargeIdsJson;
        Object surchargesRaw = body.get("surchargeIds");
        if (surchargesRaw instanceof List<?>) {
            surchargeIdsJson = json.toJson(surchargesRaw);
        } else {
            surchargeIdsJson = "[]";
        }
        String orderId = jdbc.queryForObject("""
            INSERT INTO orders (
              tenant_id, order_no, customer_id, status, source, customer_ref, metadata,
              postcode, item_type, battery_type, special_type, materials_en,
              is_insurance, is_remote, surcharge_ids
            ) VALUES (
              current_setting('app.current_tenant_id')::uuid,
              ?, ?::uuid, 'DRAFT', 'LOCAL', ?, ?::jsonb,
              ?, ?, ?, ?, ?, ?, ?, ?::jsonb
            )
            RETURNING id::text
            """, String.class, orderNo, customerId, customerRef, metaJson,
                 strOrNull(body.get("postcode")),
                 strOrNull(body.get("itemType")),
                 mapBatteryType(body.get("batteryType")),
                 mapSpecialType(body.get("specialType")),
                 strOrNull(body.get("materialsEn")),
                 Boolean.TRUE.equals(body.get("isInsurance")),
                 Boolean.TRUE.equals(body.get("isRemote")),
                 surchargeIdsJson);

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

    /** "a,b,c" → [a,b,c]，空/null 返回空表。trim 每个 item 并丢空。 */
    static java.util.List<String> splitCsv(String s) {
        if (s == null || s.isBlank()) return java.util.List.of();
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String it : s.split(",")) {
            String t = it.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    /** n=3 → "?,?,?"。给 IN 子句用。 */
    static String qMarks(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(",");
            sb.append("?");
        }
        return sb.toString();
    }

    /**
     * ACC 兼容: batteryType 数字（0=不带电 1=内置 2=干电池）→ DB enum text。
     * 前端 select v-model.number 发数字，DB CHECK 要 NONE/PURE/BUILT_IN/MATCH。
     */
    private static String mapBatteryType(Object o) {
        if (o == null) return null;
        String s = o.toString().trim();
        if (s.isEmpty()) return null;
        return switch (s) {
            case "0" -> "NONE";
            case "1" -> "BUILT_IN";
            case "2" -> "PURE";        // 干电池 → PURE 锂电池
            case "3" -> "MATCH";       // 配套
            // 已经是 enum text 直接透传
            case "NONE", "PURE", "BUILT_IN", "MATCH" -> s;
            default -> null;            // 未知值 → NULL（避免 DB CHECK 阻塞）
        };
    }

    /**
     * ACC 兼容: specialType 数字（0=普货 1=特殊产品 2=港发件 3=报关件 4=纺织品 5=仿牌）→ DB enum text。
     * DB CHECK 接 STANDARD/CHEMICAL/LIQUID/MAGNETIC。
     */
    private static String mapSpecialType(Object o) {
        if (o == null) return null;
        String s = o.toString().trim();
        if (s.isEmpty()) return null;
        return switch (s) {
            case "0" -> "STANDARD";
            case "1" -> "CHEMICAL";    // 特殊产品 → CHEMICAL
            case "2", "3", "4", "5" -> "STANDARD"; // 港发件/报关/纺织/仿牌都走标准
            case "STANDARD", "CHEMICAL", "LIQUID", "MAGNETIC" -> s;
            default -> null;
        };
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
        // ACC OnlineCancel.php 全面对齐
        Map<String, Object> ord;
        try {
            ord = jdbc.queryForMap(
                "SELECT status::text AS status, audit_status FROM orders WHERE id = ?::uuid", id);
        } catch (DataAccessException ex) {
            throw ApiException.notFound("快件不存在");                  // L426/L578
        }
        String status = (String) ord.get("status");
        // L430/L582: 状态不允许作废
        if ("CANCELLED".equals(status)) {
            throw ApiException.badRequest("快件状态为 CANCELLED，无法再作废");
        }
        if ("COMPLETED".equals(status)) {
            throw ApiException.badRequest("快件已签收，无法作废");
        }
        if ("DRAFT".equals(status)) {
            throw ApiException.badRequest("DRAFT 状态请直接删除，无需走作废审批");
        }
        // L418: 已经有作废申请，请勿重复
        String existingReason = jdbc.queryForObject("""
            SELECT metadata #>> '{acc_compat,void_request_reason}' FROM orders WHERE id = ?::uuid
            """, String.class, id);
        if (existingReason != null && !existingReason.isBlank()
            && "PENDING".equals(ord.get("audit_status"))) {
            throw ApiException.badRequest("该快件已经有作废申请，请勿重复操作");
        }
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
    /**
     * 给订单手动添加 AP 成本费用行（对应"添加成本"按钮）。
     * body: { amount, currency, chargeItemCode?, remark? }
     * 落 charges 表 side=AP status=ESTIMATED，待核成本 tab 可见，可一审通过。
     */
    @PostMapping("/{id}/add-cost")
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> addCost(@PathVariable String id, @RequestBody Map<String, Object> body) {
        java.math.BigDecimal amount;
        try {
            amount = new java.math.BigDecimal(body.get("amount").toString());
        } catch (Exception ex) {
            throw ApiException.badRequest("amount 必填且必须为数字");
        }
        if (amount.signum() <= 0) throw ApiException.badRequest("成本金额必须大于零");
        String currency = strOrNull(body.get("currency"));
        if (currency == null) currency = "CNY";
        String chargeItemCode = strOrNull(body.get("chargeItemCode"));
        String remark = strOrNull(body.get("remark"));

        // 找该订单的 shipment（取号已落 shipment）；没有的话拒绝（订单需先 submit）
        String shipmentId;
        try {
            shipmentId = jdbc.queryForObject(
                "SELECT s.id::text FROM shipments s"
                + " JOIN shipment_order_links sol ON sol.shipment_id = s.id"
                + " WHERE sol.order_id = ?::uuid LIMIT 1",
                String.class, id);
        } catch (DataAccessException ex) {
            throw ApiException.badRequest("订单尚未生成 shipment，请先提交订单（DRAFT→SUBMITTED）");
        }
        String tenantId = jdbc.queryForObject("SELECT tenant_id::text FROM orders WHERE id=?::uuid",
            String.class, id);
        String customerId = jdbc.queryForObject("SELECT customer_id::text FROM orders WHERE id=?::uuid",
            String.class, id);
        // charge_item_id: 按 code 找，找不到走 default FREIGHT
        String chargeItemId = null;
        if (chargeItemCode != null) {
            try {
                chargeItemId = jdbc.queryForObject(
                    "SELECT id::text FROM charge_items WHERE tenant_id=?::uuid AND code=? LIMIT 1",
                    String.class, tenantId, chargeItemCode);
            } catch (DataAccessException ignored) {}
        }
        if (chargeItemId == null) {
            try {
                chargeItemId = jdbc.queryForObject(
                    "SELECT id::text FROM charge_items WHERE tenant_id=?::uuid"
                    + " ORDER BY (category='FREIGHT') DESC, code LIMIT 1",
                    String.class, tenantId);
            } catch (DataAccessException ignored) {}
        }
        if (chargeItemId == null) throw ApiException.badRequest("租户未配置任何 charge_item，请先在数据管理添加");

        String chargeId = jdbc.queryForObject("""
            INSERT INTO charges (
              tenant_id, shipment_id, charge_item_id, side, status, currency, amount,
              evidence, customer_id, order_id
            ) VALUES (
              ?::uuid, ?::uuid, ?::uuid, 'AP', 'ESTIMATED', ?, ?,
              jsonb_build_object('manual', true, 'addedBy', 'admin', 'remark', ?::text),
              ?::uuid, ?::uuid
            )
            RETURNING id::text
            """, String.class, tenantId, shipmentId, chargeItemId,
                 currency, amount, remark, customerId, id);
        return Map.of("id", chargeId, "amount", amount, "currency", currency,
                       "side", "AP", "status", "ESTIMATED",
                       "msg", "成本已添加 — 在「核算中心 → 待核成本」一审通过后即可付供应商");
    }

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

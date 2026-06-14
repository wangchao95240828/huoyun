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

/**
 * /api/acc/shipments — 前端列：no / channelName / supplierName / country
 * / totalPiece / totalWeight / totalCharge / totalCost / auditName / addTime
 *
 * 来源：shipments + channels + cartons 聚合。supplierName / auditName 暂空（新模型未建模）。
 */
@RestController
@RequestMapping("/api/acc/shipments")
public class AccShipmentsController {
    private static final String TABLE = "shipments";

    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final CascadeChecker cascadeChecker;
    private final FieldGate fieldGate;
    private final com.xqt.saas.tracking.TrackingAggregator trackingAggregator;

        private final BranchAccessFilter branchAccess;

public AccShipmentsController(JdbcTemplate jdbc, JsonSupport json,
                                  CascadeChecker cascadeChecker, FieldGate fieldGate,
                                  com.xqt.saas.tracking.TrackingAggregator trackingAggregator,
                                  BranchAccessFilter branchAccess) {
        this.jdbc = jdbc;
        this.json = json;
        this.cascadeChecker = cascadeChecker;
        this.fieldGate = fieldGate;
        this.trackingAggregator = trackingAggregator;
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

            // ACC 配载中心子页过滤：把高层语义 status 转 SQL where 片段
            String statusFilter = buildShipmentsStatusFilter(status);

            var access = branchAccess.forCurrent("s");
            java.util.List<Object> countParams = new java.util.ArrayList<>(java.util.Arrays.asList(
                search, search, search, dateFrom, dateFrom, dateTo, dateTo));
            countParams.addAll(access.params());
            Long total = jdbc.queryForObject(
                "SELECT count(*) FROM shipments s"
                + " WHERE (?::text IS NULL OR (s.shipment_no ILIKE ? OR s.customer_ref ILIKE ?))"
                + "   AND (?::date IS NULL OR s.created_at >= ?::date)"
                + "   AND (?::date IS NULL OR s.created_at < (?::date + 1))"
                + statusFilter
                + access.sql(),
                Long.class, countParams.toArray());

            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT
                  s.id::text       AS id,
                  s.shipment_no,
                  s.customer_ref,
                  s.status::text   AS status,
                  s.destination_country,
                  s.destination_postal_code,
                  s.created_at,
                  s.audit_status,
                  s.audited_at,
                  s.audit_name,
                  ch.name          AS channel_name,
                  s.materials_en, s.materials_cn,
                  s.battery_code, s.label_type, s.services,
                  s.recipient_company, s.recipient_consignee, s.recipient_phone,
                  s.recipient_province, s.recipient_city, s.recipient_tax_no,
                  s.recipient_address, s.recipient_house_no, s.recipient_area_code,
                  s.shipper_company, s.shipper_consignee, s.shipper_phone,
                  s.shipper_province, s.shipper_postcode, s.shipper_city,
                  s.shipper_tax_no, s.shipper_address,
                  s.sold_to_company, s.sold_to_consignee, s.sold_to_phone,
                  s.sold_to_province, s.sold_to_postcode, s.sold_to_city,
                  s.sold_to_tax_no, s.sold_to_address,
                  -- 物流商：从最新生效的 channel_cost_policies 拿 carrier 名
                  (
                    SELECT car.name FROM channel_cost_policies ccp
                    LEFT JOIN carriers car ON car.id = ccp.carrier_id
                    WHERE ccp.tenant_id = s.tenant_id
                      AND ccp.channel_id = s.channel_id
                      AND ccp.effective_from <= current_date
                      AND (ccp.effective_to IS NULL OR ccp.effective_to >= current_date)
                    ORDER BY ccp.effective_from DESC
                    LIMIT 1
                  ) AS supplier_name,
                  (SELECT count(*) FROM cartons c WHERE c.shipment_id = s.id) AS piece_count,
                  (SELECT coalesce(sum(c.actual_weight_kg), 0) FROM cartons c WHERE c.shipment_id = s.id) AS total_weight,
                  (
                    SELECT coalesce(sum(ch2.amount), 0) FROM charges ch2
                    WHERE ch2.shipment_id = s.id AND ch2.side = 'AR'
                      AND ch2.settlement_status <> 'VOID'
                  ) AS total_charge,
                  (
                    SELECT coalesce(sum(ch3.amount), 0) FROM charges ch3
                    WHERE ch3.shipment_id = s.id AND ch3.side = 'AP'
                      AND ch3.settlement_status <> 'VOID'
                  ) AS total_cost
                FROM shipments s
                LEFT JOIN channels ch ON ch.id = s.channel_id
                WHERE 1=1
                """
                + " AND (?::text IS NULL OR (s.shipment_no ILIKE ? OR s.customer_ref ILIKE ?))"
                + " AND (?::date IS NULL OR s.created_at >= ?::date)"
                + " AND (?::date IS NULL OR s.created_at < (?::date + 1))"
                + statusFilter
                + access.sql()
                + " ORDER BY s.created_at DESC"
                + " LIMIT ? OFFSET ?",
                buildListParams(search, dateFrom, dateTo, access, limit, offset));
            return AccPaging.result(rows.stream().map(this::project).toList(),
                total == null ? 0 : total);
        } catch (DataAccessException ex) {
            return AccPaging.result(List.of(), 0);
        }
    }

    /**
     * 配载中心子页过滤：把前端发的 status 关键字翻译成 SQL where 片段。
     * 注意：返回的片段以 " AND ..." 开头或空字符串，不带绑定参数（用 date_trunc/now() 内联）。
     */
    private static String buildShipmentsStatusFilter(String status) {
        if (status == null || status.isBlank()) return "";
        return switch (status) {
            case "TODAY"             -> " AND s.created_at::date = current_date";
            case "PICKUP_TODAY"      -> " AND s.created_at::date = current_date AND s.status <> 'DRAFT'";
            case "PICKUP_WEEK"       -> " AND s.created_at >= date_trunc('week', current_date)";
            case "INTRANSIT"         -> " AND s.status = 'IN_TRANSIT'";
            case "EXCEPTION"         -> " AND s.status = 'EXCEPTION'";
            case "DELIVERED_TODAY"   -> " AND s.status = 'DELIVERED' AND s.completed_at::date = current_date";
            default                   -> "";
        };
    }

    @GetMapping("/{id}/raw")
    public Map<String, Object> raw(@PathVariable String id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM shipments WHERE id = ?::uuid LIMIT 1", id);
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    /**
     * Provider evidence 聚合：cartons.carrier_evidence + label_files.evidence + charges.evidence。
     * 前端运单详情对话框展示用，便于客服/运维排障看到取号 / 面单 / 费用 provider 的实际报文。
     */
    @GetMapping("/{id}/evidence")
    public Map<String, Object> evidence(@PathVariable String id) {
        try {
            List<Map<String, Object>> carrier = jdbc.queryForList("""
                SELECT carton_no, tracking_no, carrier_master_tracking_no,
                       carrier_evidence::text AS carrier_evidence_text
                FROM cartons
                WHERE shipment_id = ?::uuid
                  AND carrier_evidence IS NOT NULL
                  AND carrier_evidence::text <> '{}'
                ORDER BY carton_no
                """, id);
            List<Map<String, Object>> labels = jdbc.queryForList("""
                SELECT id::text AS id, tracking_no, label_type, source, created_at,
                       evidence::text AS evidence_text
                FROM label_files
                WHERE shipment_id = ?::uuid
                  AND evidence IS NOT NULL
                  AND evidence::text <> '{}'
                ORDER BY created_at DESC
                """, id);
            List<Map<String, Object>> chargesEv = jdbc.queryForList("""
                SELECT id::text AS id, side::text AS side, status::text AS status,
                       amount, currency, evidence::text AS evidence_text
                FROM charges
                WHERE shipment_id = ?::uuid
                  AND evidence IS NOT NULL
                  AND evidence::text <> '{}'
                ORDER BY created_at
                """, id);
            return Map.of(
                "shipmentId", id,
                "carrier", parseEvidence(carrier, "carrier_evidence_text"),
                "labels", parseEvidence(labels, "evidence_text"),
                "charges", parseEvidence(chargesEv, "evidence_text")
            );
        } catch (DataAccessException ex) {
            return Map.of("shipmentId", id,
                "carrier", List.of(), "labels", List.of(), "charges", List.of());
        }
    }

    private List<Map<String, Object>> parseEvidence(List<Map<String, Object>> rows, String key) {
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> projected = new java.util.LinkedHashMap<>();
            for (var e : r.entrySet()) {
                if (e.getKey().equals(key)) continue;
                projected.put(e.getKey(), json.value(e.getValue()));
            }
            String evidenceText = (String) r.get(key);
            try {
                projected.put("evidence", json.fromJson(evidenceText,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}));
            } catch (RuntimeException ignored) {
                projected.put("evidence", Map.of("raw", evidenceText));
            }
            out.add(projected);
        }
        return out;
    }

    /**
     * 内部视角时间线（任务 S1）：UNION 5 源事件 + operator/internal remark 全字段。
     * 对应 ACC 旧系统 Express_Process / Stowage_Process / 上门揽收等综合查看。
     */
    @GetMapping("/{id}/timeline")
    public Map<String, Object> timeline(@PathVariable String id) {
        String tenantId = jdbc.queryForObject(
            "SELECT current_setting('app.current_tenant_id', true)", String.class);
        List<com.xqt.saas.tracking.TrackingEvent> events =
            trackingAggregator.aggregateByShipment(tenantId, id);
        return Map.of("shipmentId", id, "events", events);
    }

    /** 前端原页面的 "查看装箱清单"，对应 ACC `shipments/{id}/items`。返回该 shipment 下所有 cartons + declarations。 */
    @GetMapping("/{id}/items")
    public Map<String, Object> items(@PathVariable String id) {
        try {
            List<Map<String, Object>> cartons = jdbc.queryForList("""
                SELECT id::text AS id, carton_no, tracking_no, carrier_master_tracking_no,
                       actual_weight_kg, chargeable_weight_kg, cbm
                FROM cartons WHERE shipment_id = ?::uuid
                ORDER BY carton_no
                """, id);
            List<Map<String, Object>> declares = jdbc.queryForList("""
                SELECT id::text AS id, item_name, material, hs_code, quantity, value_amount
                FROM declarations WHERE shipment_id = ?::uuid
                """, id);
            return Map.of("cartons", json.rows(cartons), "declarations", json.rows(declares));
        } catch (DataAccessException ex) {
            return Map.of("cartons", List.of(), "declarations", List.of());
        }
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String shipmentNo = (String) body.get("shipment_no");
        Object customerId = body.get("customer_id");
        if (shipmentNo == null || shipmentNo.isBlank()) {
            throw ApiException.badRequest("出货单号必填");
        }
        if (customerId == null || customerId.toString().isBlank()) {
            throw ApiException.badRequest("请选择客户");
        }
        Integer dup = jdbc.queryForObject(
            "SELECT count(*) FROM shipments WHERE shipment_no = ?", Integer.class, shipmentNo);
        if (dup != null && dup > 0) {
            throw ApiException.badRequest("相同出货单号已存在: " + shipmentNo);
        }
        String id = jdbc.queryForObject("""
            INSERT INTO shipments (tenant_id, customer_id, shipment_no, status)
            VALUES (current_setting('app.current_tenant_id')::uuid, ?::uuid, ?, 'DRAFT')
            RETURNING id::text
            """, String.class, customerId == null ? null : customerId.toString(), shipmentNo);
        return Map.of("id", id, "shipment_no", shipmentNo);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String currentAudit = jdbc.queryForObject(
            "SELECT audit_status FROM shipments WHERE id = ?::uuid", String.class, id);
        FieldGate.FilterResult gate = fieldGate.filterAllowedFields(TABLE, currentAudit, body);
        if (!gate.rejected().isEmpty() && gate.allowed().isEmpty()) {
            throw ApiException.badRequest("运单已审核，字段不可修改: " + String.join(",", gate.rejected())
                + "；请先反审");
        }
        Map<String, Object> allowed = gate.allowed();
        jdbc.update("""
            UPDATE shipments SET
              shipment_no  = coalesce(?, shipment_no),
              customer_ref = coalesce(?, customer_ref)
            WHERE id = ?::uuid
            """, (String) allowed.get("shipment_no"),
            (String) allowed.get("customer_ref"), id);
        return Map.of("id", id, "rejectedFields", gate.rejected());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        String auditStatus = jdbc.queryForObject(
            "SELECT audit_status FROM shipments WHERE id = ?::uuid", String.class, id);
        if ("AUDITED".equals(auditStatus)) {
            throw ApiException.badRequest("运单已审核，不能删除，请先反审");
        }
        cascadeChecker.checkBeforeDelete(TABLE, id);
        jdbc.update("DELETE FROM shipments WHERE id = ?::uuid", id);
        return Map.of("id", id, "deleted", true);
    }

    private static Object[] buildListParams(String search, String dateFrom, String dateTo,
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
        out.put("no", row.get("shipment_no"));
        out.put("channelName", row.get("channel_name"));
        out.put("supplierName", row.get("supplier_name") == null ? "" : row.get("supplier_name"));
        out.put("country", row.get("destination_country"));
        out.put("postcode", row.get("destination_postal_code"));
        out.put("totalPiece", row.get("piece_count"));
        out.put("totalWeight", row.get("total_weight"));
        out.put("totalCharge", row.get("total_charge"));
        out.put("totalCost", row.get("total_cost"));
        // ACC 制单货物/标签信息
        out.put("materialsEn", row.get("materials_en"));
        out.put("materialsCn", row.get("materials_cn"));
        out.put("batteryCode", row.get("battery_code"));
        out.put("labelType", row.get("label_type"));
        out.put("services", row.get("services"));
        // 收件人
        out.put("recipientCompany", row.get("recipient_company"));
        out.put("recipientConsignee", row.get("recipient_consignee"));
        out.put("recipientPhone", row.get("recipient_phone"));
        out.put("recipientProvince", row.get("recipient_province"));
        out.put("recipientCity", row.get("recipient_city"));
        out.put("recipientTaxNo", row.get("recipient_tax_no"));
        out.put("recipientAddress", row.get("recipient_address"));
        out.put("recipientHouseNo", row.get("recipient_house_no"));
        out.put("recipientAreaCode", row.get("recipient_area_code"));
        // 发件人
        out.put("shipperCompany", row.get("shipper_company"));
        out.put("shipperConsignee", row.get("shipper_consignee"));
        out.put("shipperPhone", row.get("shipper_phone"));
        out.put("shipperProvince", row.get("shipper_province"));
        out.put("shipperPostcode", row.get("shipper_postcode"));
        out.put("shipperCity", row.get("shipper_city"));
        out.put("shipperTaxNo", row.get("shipper_tax_no"));
        out.put("shipperAddress", row.get("shipper_address"));
        // 进口商
        out.put("soldToCompany", row.get("sold_to_company"));
        out.put("soldToConsignee", row.get("sold_to_consignee"));
        out.put("soldToPhone", row.get("sold_to_phone"));
        out.put("soldToProvince", row.get("sold_to_province"));
        out.put("soldToPostcode", row.get("sold_to_postcode"));
        out.put("soldToCity", row.get("sold_to_city"));
        out.put("soldToTaxNo", row.get("sold_to_tax_no"));
        out.put("soldToAddress", row.get("sold_to_address"));
        out.put("addTime", json.value(row.get("created_at")));
        out.put("status", row.get("status"));
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        out.put("auditName", row.get("audit_name"));
        return out;
    }
}

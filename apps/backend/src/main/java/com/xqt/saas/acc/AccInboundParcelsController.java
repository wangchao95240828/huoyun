package com.xqt.saas.acc;

import java.math.BigDecimal;
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

/**
 * ACC 客服中心 → 收货 → 入仓预报 / 包裹管理。
 *
 * 收货流程：
 *   1. 客户/客服在此 tab 预报箱号 (status=PENDING)
 *   2. 包裹到仓过 DWS → status=SCANNED + 实测落地
 *   3. RateEngine 按表价生成 charges → status=CHARGED
 *   4. 出货 → status=SHIPPED
 */
@RestController
@RequestMapping("/api/acc/inbound-parcels")
public class AccInboundParcelsController {
    private static final String TABLE = "acc_inbound_parcels";
    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final CascadeChecker cascadeChecker;
    private final FieldGate fieldGate;

    public AccInboundParcelsController(JdbcTemplate jdbc, JsonSupport json,
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
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String dateFrom,
        @RequestParam(required = false) String dateTo,
        @RequestParam(required = false) String status
    ) {
        try {
            int limit = AccPaging.pageSize(pageSize);
            int offset = AccPaging.offset(page, pageSize);
            String search = keyword == null || keyword.isBlank() ? null : "%" + keyword + "%";
            Long total = jdbc.queryForObject(
                "SELECT count(*) FROM acc_inbound_parcels"
                + " WHERE (?::text IS NULL OR parcel_no ILIKE ? OR waybill_no ILIKE ? OR tracking_no ILIKE ?)"
                + "   AND (?::date IS NULL OR created_at >= ?::date)"
                + "   AND (?::date IS NULL OR created_at < (?::date + 1))"
                + "   AND (?::text IS NULL OR status = ?)",
                Long.class, search, search, search, search,
                dateFrom, dateFrom, dateTo, dateTo, status, status);
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT p.id::text AS id, p.parcel_no, p.waybill_no, p.tracking_no,"
                + "       p.customer_id::text AS customer_id, cu.name AS customer_name,"
                + "       p.channel_id::text AS channel_id, cn.name AS channel_name,"
                + "       p.expected_weight, p.actual_weight,"
                + "       p.length_cm, p.width_cm, p.height_cm,"
                + "       p.volume_weight, p.chargeable_kg, p.cbm,"
                + "       p.destination_country, p.destination_postal_code, p.zone,"
                + "       p.rate_amount, p.currency, p.status, p.pic_url,"
                + "       p.received_at, p.add_name,"
                + "       p.audit_status, p.audited_at, p.audit_name, p.created_at"
                + " FROM acc_inbound_parcels p"
                + " LEFT JOIN customers cu ON cu.id = p.customer_id"
                + " LEFT JOIN channels  cn ON cn.id = p.channel_id"
                + " WHERE (?::text IS NULL OR p.parcel_no ILIKE ? OR p.waybill_no ILIKE ? OR p.tracking_no ILIKE ?)"
                + "   AND (?::date IS NULL OR p.created_at >= ?::date)"
                + "   AND (?::date IS NULL OR p.created_at < (?::date + 1))"
                + "   AND (?::text IS NULL OR p.status = ?)"
                + " ORDER BY p.created_at DESC LIMIT ? OFFSET ?",
                search, search, search, search,
                dateFrom, dateFrom, dateTo, dateTo, status, status, limit, offset);

            BigDecimal sumWeight = jdbc.queryForObject(
                "SELECT coalesce(sum(actual_weight), 0) FROM acc_inbound_parcels"
                + " WHERE (?::text IS NULL OR parcel_no ILIKE ? OR waybill_no ILIKE ? OR tracking_no ILIKE ?)"
                + "   AND (?::date IS NULL OR created_at >= ?::date)"
                + "   AND (?::date IS NULL OR created_at < (?::date + 1))"
                + "   AND (?::text IS NULL OR status = ?)",
                BigDecimal.class, search, search, search, search,
                dateFrom, dateFrom, dateTo, dateTo, status, status);
            Map<String, Object> agg = new LinkedHashMap<>();
            agg.put("actualWeight", sumWeight);

            return AccPaging.result(rows.stream().map(this::project).toList(),
                total == null ? 0 : total, agg);
        } catch (DataAccessException ex) {
            return AccPaging.result(List.of(), 0);
        }
    }

    @GetMapping("/{id}/raw")
    public Map<String, Object> raw(@PathVariable String id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM acc_inbound_parcels WHERE id = ?::uuid LIMIT 1", id);
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String parcelNo = (String) body.get("parcelNo");
        if (parcelNo == null || parcelNo.isBlank()) {
            throw ApiException.badRequest("parcelNo 必填");
        }
        String id = jdbc.queryForObject(
            "INSERT INTO acc_inbound_parcels ("
            + "  tenant_id, parcel_no, waybill_no, tracking_no, customer_id, channel_id,"
            + "  expected_weight, destination_country, destination_postal_code, zone,"
            + "  status, add_name"
            + ") VALUES ("
            + "  (SELECT id FROM tenants WHERE code='xqt' LIMIT 1),"
            + "  ?, ?, ?, ?::uuid, ?::uuid,"
            + "  ?, ?, ?, ?,"
            + "  'PENDING', ?"
            + ") RETURNING id::text",
            String.class,
            parcelNo,
            body.get("waybillNo"),
            body.get("trackingNo"),
            body.get("customerId"),
            body.get("channelId"),
            body.get("expectedWeight") instanceof Number n ? new BigDecimal(n.toString()) : null,
            body.get("destinationCountry"),
            body.get("destinationPostalCode"),
            body.get("zone"),
            body.get("addName"));
        return Map.of("id", id, "parcelNo", parcelNo);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String currentAudit = jdbc.queryForObject(
            "SELECT audit_status FROM acc_inbound_parcels WHERE id = ?::uuid", String.class, id);
        FieldGate.FilterResult gate = fieldGate.filterAllowedFields(TABLE, currentAudit, body);
        if (!gate.rejected().isEmpty() && gate.allowed().isEmpty()) {
            throw ApiException.badRequest("已审核，字段不可修改: " + String.join(",", gate.rejected())
                + "；请先反审");
        }
        Map<String, Object> a = gate.allowed();
        jdbc.update(
            "UPDATE acc_inbound_parcels SET"
            + "  waybill_no              = coalesce(?, waybill_no),"
            + "  tracking_no             = coalesce(?, tracking_no),"
            + "  expected_weight         = coalesce(?, expected_weight),"
            + "  destination_country     = coalesce(?, destination_country),"
            + "  destination_postal_code = coalesce(?, destination_postal_code),"
            + "  zone                    = coalesce(?, zone),"
            + "  status                  = coalesce(?, status),"
            + "  updated_at              = now()"
            + " WHERE id = ?::uuid",
            (String) a.get("waybillNo"),
            (String) a.get("trackingNo"),
            a.get("expectedWeight") instanceof Number n ? new BigDecimal(n.toString()) : null,
            (String) a.get("destinationCountry"),
            (String) a.get("destinationPostalCode"),
            (String) a.get("zone"),
            (String) a.get("status"),
            id);
        return Map.of("id", id, "rejectedFields", gate.rejected());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        String auditStatus = jdbc.queryForObject(
            "SELECT audit_status FROM acc_inbound_parcels WHERE id = ?::uuid",
            String.class, id);
        if ("AUDITED".equals(auditStatus)) {
            throw ApiException.badRequest("已审核，不能删除，请先反审");
        }
        cascadeChecker.checkBeforeDelete(TABLE, id);
        jdbc.update("DELETE FROM acc_inbound_parcels WHERE id = ?::uuid", id);
        return Map.of("id", id, "deleted", true);
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("parcelNo", row.get("parcel_no"));
        out.put("waybillNo", row.get("waybill_no"));
        out.put("trackingNo", row.get("tracking_no"));
        out.put("customerId", row.get("customer_id"));
        out.put("customerName", row.get("customer_name"));
        out.put("channelName", row.get("channel_name"));
        out.put("expectedWeight", row.get("expected_weight"));
        out.put("actualWeight", row.get("actual_weight"));
        out.put("lengthCm", row.get("length_cm"));
        out.put("widthCm", row.get("width_cm"));
        out.put("heightCm", row.get("height_cm"));
        out.put("volumeWeight", row.get("volume_weight"));
        out.put("chargeableKg", row.get("chargeable_kg"));
        out.put("cbm", row.get("cbm"));
        out.put("destinationCountry", row.get("destination_country"));
        out.put("destinationPostalCode", row.get("destination_postal_code"));
        out.put("zone", row.get("zone"));
        out.put("rateAmount", row.get("rate_amount"));
        out.put("currency", row.get("currency"));
        out.put("status", row.get("status"));
        out.put("picUrl", row.get("pic_url"));
        out.put("receivedAt", json.value(row.get("received_at")));
        out.put("addName", row.get("add_name"));
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        out.put("auditName", row.get("audit_name"));
        out.put("createdAt", json.value(row.get("created_at")));
        return out;
    }
}

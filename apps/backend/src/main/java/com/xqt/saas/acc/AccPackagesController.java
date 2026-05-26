package com.xqt.saas.acc;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.JsonSupport;
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
 * /api/acc/packages — 装箱单。前端列：no / theDate / consignee / company / country
 * / piece / quantity / declaredValue / postcode
 *
 * 新模型里没有独立 Online_Package 表；每张 shipment 自带 cartons + declarations，相当于一个装箱单。
 * 收件人/邮编/公司从 orders.metadata.acc_compat.receiver 还原（API 下单时存在那里）。
 */
@RestController
@RequestMapping("/api/acc/packages")
public class AccPackagesController {
    private final JdbcTemplate jdbc;
    private final JsonSupport json;

    public AccPackagesController(JdbcTemplate jdbc, JsonSupport json) {
        this.jdbc = jdbc;
        this.json = json;
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

            Long total = jdbc.queryForObject("""
                SELECT count(*) FROM shipments s
                WHERE (?::text IS NULL OR s.shipment_no ILIKE ? OR s.customer_ref ILIKE ?)
                  AND (?::date IS NULL OR s.created_at >= ?::date)
                  AND (?::date IS NULL OR s.created_at < (?::date + 1))
                """, Long.class, search, search, search, dateFrom, dateFrom, dateTo, dateTo);

            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT
                  s.id::text                                  AS id,
                  s.shipment_no,
                  s.customer_ref,
                  s.destination_country,
                  s.destination_postal_code,
                  s.declared_value,
                  s.created_at,
                  (SELECT count(*) FROM cartons c WHERE c.shipment_id = s.id) AS piece_count,
                  (SELECT coalesce(sum(d.quantity), 0) FROM declarations d WHERE d.shipment_id = s.id)
                    AS declared_quantity,
                  o.metadata->'acc_compat'->'receiver'->>'Consignee' AS consignee,
                  o.metadata->'acc_compat'->'receiver'->>'Company'   AS company,
                  o.metadata->'acc_compat'->'receiver'->>'Postcode'  AS postcode
                FROM shipments s
                LEFT JOIN orders o ON o.tenant_id = s.tenant_id
                                  AND o.customer_ref = s.customer_ref
                WHERE (?::text IS NULL OR s.shipment_no ILIKE ? OR s.customer_ref ILIKE ?)
                  AND (?::date IS NULL OR s.created_at >= ?::date)
                  AND (?::date IS NULL OR s.created_at < (?::date + 1))
                ORDER BY s.created_at DESC
                LIMIT ? OFFSET ?
                """, search, search, search, dateFrom, dateFrom, dateTo, dateTo, limit, offset);
            return AccPaging.result(rows.stream().map(this::project).toList(),
                total == null ? 0 : total);
        } catch (DataAccessException ex) {
            return AccPaging.result(List.of(), 0);
        }
    }

    @GetMapping("/{id}/raw")
    public Map<String, Object> raw(@PathVariable String id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM shipments WHERE id = ?::uuid LIMIT 1", id);
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        // 包裹只是 shipments 的视图，新增等价于建 shipments
        String shipmentNo = (String) body.getOrDefault("no", body.get("shipment_no"));
        Object customerId = body.get("customer_id");
        BigDecimal declaredValue = body.get("declared_value") instanceof Number n
            ? new BigDecimal(n.toString()) : null;
        String id = jdbc.queryForObject("""
            INSERT INTO shipments (tenant_id, customer_id, shipment_no, status, declared_value)
            VALUES (current_setting('app.current_tenant_id')::uuid, ?::uuid, ?, 'DRAFT', ?)
            RETURNING id::text
            """, String.class,
            customerId == null ? null : customerId.toString(), shipmentNo, declaredValue);
        return Map.of("id", id, "no", shipmentNo);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        jdbc.update("""
            UPDATE shipments SET
              shipment_no    = coalesce(?, shipment_no),
              declared_value = coalesce(?, declared_value)
            WHERE id = ?::uuid
            """,
            (String) body.getOrDefault("no", body.get("shipment_no")),
            body.get("declared_value") instanceof Number n ? new BigDecimal(n.toString()) : null,
            id);
        return Map.of("id", id);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        jdbc.update("DELETE FROM shipments WHERE id = ?::uuid", id);
        return Map.of("id", id, "deleted", true);
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("no", row.get("customer_ref") != null
            ? row.get("customer_ref") : row.get("shipment_no"));
        out.put("theDate", json.value(row.get("created_at")));
        out.put("consignee", row.get("consignee"));
        out.put("company", row.get("company"));
        out.put("country", row.get("destination_country"));
        out.put("piece", row.get("piece_count"));
        out.put("quantity", row.get("declared_quantity"));
        out.put("declaredValue", row.get("declared_value"));
        out.put("postcode", row.get("postcode") != null
            ? row.get("postcode") : row.get("destination_postal_code"));
        return out;
    }
}

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

/**
 * /api/acc/channels — 对应前端 ACC tab "channels"。读 channels 表。
 * 前端列：name, code, isOpen, isDebug, remark；DB 没有 isDebug/remark，分别用 false / lane 兜底。
 */
@RestController
@RequestMapping("/api/acc/channels")
public class AccChannelsController {
    private static final String TABLE = "channels";

    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final CascadeChecker cascadeChecker;
    private final FieldGate fieldGate;

    public AccChannelsController(JdbcTemplate jdbc, JsonSupport json,
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

            Long total = jdbc.queryForObject(
                "SELECT count(*) FROM channels WHERE ?::text IS NULL OR (code ILIKE ? OR name ILIKE ?)",
                Long.class, search, search, search);
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id::text AS id, code, name, lane, last_mile_method, active,
                       primary_uom, dim_factor, fuel_required, has_fuel,
                       volume_modulus, weight_modulus,
                       limit_declare, limit_weight, limit_volume,
                       min_weight_total, max_weight_warn, max_length_warn,
                       limit_item_weight, min_item_weight, weight_ceil_unit,
                       split_ratio, min_split, weight_method, allow_types, is_shipping,
                       audit_status, audited_at, audit_name
                FROM channels
                WHERE ?::text IS NULL OR (code ILIKE ? OR name ILIKE ?)
                ORDER BY code
                LIMIT ? OFFSET ?
                """, search, search, search, limit, offset);
            return AccPaging.result(rows.stream().map(this::project).toList(),
                total == null ? 0 : total);
        } catch (DataAccessException ex) {
            return AccPaging.result(List.of(), 0);
        }
    }

    @GetMapping("/{id}/raw")
    public Map<String, Object> raw(@PathVariable String id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM channels WHERE id = ?::uuid LIMIT 1", id);
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String code = (String) body.get("code");
        String name = (String) body.get("name");
        String lane = body.get("remark") != null ? (String) body.get("remark") : "GENERIC";
        String lastMile = body.get("last_mile_method") != null
            ? (String) body.get("last_mile_method") : "CARRIER";
        String id = jdbc.queryForObject("""
            INSERT INTO channels (tenant_id, code, name, lane, last_mile_method, primary_uom)
            VALUES (current_setting('app.current_tenant_id')::uuid, ?, ?, ?, ?, 'KG')
            RETURNING id::text
            """, String.class, code, name, lane, lastMile);
        return Map.of("id", id, "code", code, "name", name);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String currentAudit = jdbc.queryForObject(
            "SELECT audit_status FROM channels WHERE id = ?::uuid", String.class, id);
        FieldGate.FilterResult gate = fieldGate.filterAllowedFields(TABLE, currentAudit, body);
        if (!gate.rejected().isEmpty() && gate.allowed().isEmpty()) {
            throw ApiException.badRequest("渠道已审核，字段不可修改: " + String.join(",", gate.rejected())
                + "；请先反审");
        }
        Map<String, Object> allowed = gate.allowed();
        jdbc.update("""
            UPDATE channels SET
              code = coalesce(?, code),
              name = coalesce(?, name),
              active = coalesce(?, active)
            WHERE id = ?::uuid
            """,
            (String) allowed.get("code"), (String) allowed.get("name"),
            allowed.get("active") instanceof Boolean b ? b : null, id);
        return Map.of("id", id, "rejectedFields", gate.rejected());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        String auditStatus = jdbc.queryForObject(
            "SELECT audit_status FROM channels WHERE id = ?::uuid", String.class, id);
        if ("AUDITED".equals(auditStatus)) {
            throw ApiException.badRequest("渠道已审核，不能删除，请先反审");
        }
        cascadeChecker.checkBeforeDelete(TABLE, id);
        jdbc.update("DELETE FROM channels WHERE id = ?::uuid", id);
        return Map.of("id", id, "deleted", true);
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("code", row.get("code"));
        out.put("name", row.get("name"));
        out.put("isOpen", row.get("active"));
        out.put("isDebug", false);
        out.put("remark", row.get("lane"));
        // ACC 产品配置 (Product.php?act=Add)
        out.put("primaryUom", row.get("primary_uom"));
        out.put("dimFactor", row.get("dim_factor"));
        out.put("fuelRequired", row.get("fuel_required"));
        out.put("hasFuel", row.get("has_fuel"));
        out.put("volumeModulus", row.get("volume_modulus"));
        out.put("weightModulus", row.get("weight_modulus"));
        out.put("limitDeclare", row.get("limit_declare"));
        out.put("limitWeight", row.get("limit_weight"));
        out.put("limitVolume", row.get("limit_volume"));
        out.put("minWeightTotal", row.get("min_weight_total"));
        out.put("maxWeightWarn", row.get("max_weight_warn"));
        out.put("maxLengthWarn", row.get("max_length_warn"));
        out.put("limitItemWeight", row.get("limit_item_weight"));
        out.put("minItemWeight", row.get("min_item_weight"));
        out.put("weightCeilUnit", row.get("weight_ceil_unit"));
        out.put("splitRatio", row.get("split_ratio"));
        out.put("minSplit", row.get("min_split"));
        out.put("weightMethod", row.get("weight_method"));
        out.put("allowTypes", row.get("allow_types"));
        out.put("isShipping", row.get("is_shipping"));
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        out.put("auditName", row.get("audit_name"));
        return out;
    }
}

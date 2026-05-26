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
 * /api/acc/fuels — 前端列：name / rate / startDate / endDate
 * 来源 fuel_surcharge_rates + channels.name 当 name；start/end 从 year_month 推断（当月 1 号 ~ 月末）。
 */
@RestController
@RequestMapping("/api/acc/fuels")
public class AccFuelsController {
    private static final String TABLE = "fuel_surcharge_rates";

    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final CascadeChecker cascadeChecker;
    private final FieldGate fieldGate;

    public AccFuelsController(JdbcTemplate jdbc, JsonSupport json,
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

            Long total = jdbc.queryForObject("""
                SELECT count(*) FROM fuel_surcharge_rates fs
                LEFT JOIN channels cn ON cn.id = fs.channel_id
                WHERE (?::text IS NULL OR cn.name ILIKE ? OR fs.year_month ILIKE ?)
                """, Long.class, search, search, search);
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT fs.id::text AS id, fs.year_month, fs.rate, fs.source,
                       fs.audit_status, fs.audited_at, fs.audit_name,
                       cn.name AS channel_name
                FROM fuel_surcharge_rates fs
                LEFT JOIN channels cn ON cn.id = fs.channel_id
                WHERE (?::text IS NULL OR cn.name ILIKE ? OR fs.year_month ILIKE ?)
                ORDER BY fs.year_month DESC, cn.name
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
            "SELECT * FROM fuel_surcharge_rates WHERE id = ?::uuid LIMIT 1", id);
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        Object channelId = body.get("channel_id");
        String yearMonth = (String) body.get("year_month");
        BigDecimal rate = body.get("rate") instanceof Number n
            ? new BigDecimal(n.toString()) : BigDecimal.ZERO;
        String id = jdbc.queryForObject("""
            INSERT INTO fuel_surcharge_rates (tenant_id, channel_id, year_month, rate)
            VALUES (current_setting('app.current_tenant_id')::uuid, ?::uuid, ?, ?)
            RETURNING id::text
            """, String.class,
            channelId == null ? null : channelId.toString(), yearMonth, rate);
        return Map.of("id", id);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String currentAudit = jdbc.queryForObject(
            "SELECT audit_status FROM fuel_surcharge_rates WHERE id = ?::uuid", String.class, id);
        FieldGate.FilterResult gate = fieldGate.filterAllowedFields(TABLE, currentAudit, body);
        if (!gate.rejected().isEmpty() && gate.allowed().isEmpty()) {
            throw ApiException.badRequest("燃油费率已审核，字段不可修改: " + String.join(",", gate.rejected())
                + "；请先反审");
        }
        Map<String, Object> allowed = gate.allowed();
        jdbc.update("""
            UPDATE fuel_surcharge_rates SET
              year_month = coalesce(?, year_month),
              rate       = coalesce(?, rate)
            WHERE id = ?::uuid
            """,
            (String) allowed.get("year_month"),
            allowed.get("rate") instanceof Number n ? new BigDecimal(n.toString()) : null,
            id);
        return Map.of("id", id, "rejectedFields", gate.rejected());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        String auditStatus = jdbc.queryForObject(
            "SELECT audit_status FROM fuel_surcharge_rates WHERE id = ?::uuid", String.class, id);
        if ("AUDITED".equals(auditStatus)) {
            throw ApiException.badRequest("燃油费率已审核，不能删除，请先反审");
        }
        cascadeChecker.checkBeforeDelete(TABLE, id);
        jdbc.update("DELETE FROM fuel_surcharge_rates WHERE id = ?::uuid", id);
        return Map.of("id", id, "deleted", true);
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        String yearMonth = (String) row.get("year_month");
        out.put("id", row.get("id"));
        out.put("name", row.get("channel_name") + " " + yearMonth);
        out.put("rate", row.get("rate"));
        out.put("startDate", yearMonth == null ? null : yearMonth + "-01");
        out.put("endDate", yearMonth == null ? null : yearMonth + "-31");
        out.put("source", row.get("source"));
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        out.put("auditName", row.get("audit_name"));
        return out;
    }
}

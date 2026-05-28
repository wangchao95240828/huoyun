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
 * /api/acc/currencies — 对应前端 ACC tab "currencies"。
 * 读 finance_currency 表（id BIGINT，注意与 UUID 表的区别）。
 * 前端列 symbol/rate/decimal 在新表里没建模，分别给 ''/1/2 兜底。
 */
@RestController
@RequestMapping("/api/acc/currencies")
public class AccCurrenciesController {
    private static final String TABLE = "finance_currency";

    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final CascadeChecker cascadeChecker;
    private final FieldGate fieldGate;

    public AccCurrenciesController(JdbcTemplate jdbc, JsonSupport json,
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
                "SELECT count(*) FROM finance_currency WHERE ?::text IS NULL OR (code ILIKE ? OR name ILIKE ?)",
                Long.class, search, search, search);
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT fc.id, fc.code, fc.name, fc.symbol, fc.decimal_places,
                       fc.audit_status, fc.audited_at, fc.audit_name,
                       -- 当日对 CNY 的最新汇率（找不到时 NULL，project 兜底为 1）
                       (
                         SELECT er.rate FROM exchange_rates er
                         WHERE er.tenant_id = fc.tenant_id
                           AND er.from_currency = fc.code
                           AND er.to_currency = 'CNY'
                         ORDER BY er.rate_date DESC
                         LIMIT 1
                       ) AS fx_rate
                FROM finance_currency fc
                WHERE ?::text IS NULL OR (fc.code ILIKE ? OR fc.name ILIKE ?)
                ORDER BY fc.code
                LIMIT ? OFFSET ?
                """, search, search, search, limit, offset);
            return AccPaging.result(rows.stream().map(this::project).toList(),
                total == null ? 0 : total);
        } catch (DataAccessException ex) {
            return AccPaging.result(List.of(), 0);
        }
    }

    @GetMapping("/{id}/raw")
    public Map<String, Object> raw(@PathVariable Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM finance_currency WHERE id = ? LIMIT 1", id);
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String code = (String) body.get("code");
        String name = (String) body.get("name");
        String symbol = (String) body.get("symbol");
        Object decimalPlaces = body.getOrDefault("decimal_places", body.get("decimal"));
        Long id = jdbc.queryForObject("""
            INSERT INTO finance_currency (
              tenant_id, created_at, created_by, updated_at, updated_by, code, name,
              symbol, decimal_places
            ) VALUES (
              current_setting('app.current_tenant_id')::uuid, now(), 'API', now(), 'API', ?, ?,
              ?, coalesce(?, 2)
            )
            RETURNING id
            """, Long.class, code, name, symbol,
            decimalPlaces instanceof Number n ? n.intValue() : null);
        return Map.of("id", id, "code", code, "name", name);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String currentAudit = jdbc.queryForObject(
            "SELECT audit_status FROM finance_currency WHERE id = ?", String.class, id);
        FieldGate.FilterResult gate = fieldGate.filterAllowedFields(TABLE, currentAudit, body);
        if (!gate.rejected().isEmpty() && gate.allowed().isEmpty()) {
            throw ApiException.badRequest("货币已审核，字段不可修改: " + String.join(",", gate.rejected())
                + "；请先反审");
        }
        Map<String, Object> allowed = gate.allowed();
        Object decimalPlaces = allowed.getOrDefault("decimal_places", allowed.get("decimal"));
        jdbc.update("""
            UPDATE finance_currency SET
              code = coalesce(?, code),
              name = coalesce(?, name),
              symbol = coalesce(?, symbol),
              decimal_places = coalesce(?, decimal_places),
              updated_at = now(),
              updated_by = 'API'
            WHERE id = ?
            """, (String) allowed.get("code"), (String) allowed.get("name"),
            (String) allowed.get("symbol"),
            decimalPlaces instanceof Number n ? n.intValue() : null, id);
        return Map.of("id", id, "rejectedFields", gate.rejected());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        String auditStatus = jdbc.queryForObject(
            "SELECT audit_status FROM finance_currency WHERE id = ?", String.class, id);
        if ("AUDITED".equals(auditStatus)) {
            throw ApiException.badRequest("货币已审核，不能删除，请先反审");
        }
        cascadeChecker.checkBeforeDelete(TABLE, String.valueOf(id));
        jdbc.update("DELETE FROM finance_currency WHERE id = ?", id);
        return Map.of("id", id, "deleted", true);
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("code", row.get("code"));
        out.put("name", row.get("name"));
        // 前端额外列：symbol/rate/decimal
        out.put("symbol", row.get("symbol") == null ? "" : row.get("symbol"));
        // CNY 对自己汇率恒为 1；其它币种无汇率数据时也兜底 1
        Object fx = row.get("fx_rate");
        out.put("rate", fx != null ? fx : ("CNY".equals(row.get("code")) ? 1 : 1));
        out.put("decimal", row.get("decimal_places") == null ? 2 : row.get("decimal_places"));
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        out.put("auditName", row.get("audit_name"));
        return out;
    }
}

package com.xqt.saas.acc;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiException;
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

@RestController
@RequestMapping("/api/acc/commissions")
public class AccCommissionsController {
    private static final String TABLE = "acc_commissions";

    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final CascadeChecker cascadeChecker;
    private final FieldGate fieldGate;
    private final MoneySnapshotService moneySnapshotService;

    public AccCommissionsController(JdbcTemplate jdbc, JsonSupport json,
                                     CascadeChecker cascadeChecker, FieldGate fieldGate,
                                     MoneySnapshotService moneySnapshotService) {
        this.jdbc = jdbc;
        this.json = json;
        this.cascadeChecker = cascadeChecker;
        this.fieldGate = fieldGate;
        this.moneySnapshotService = moneySnapshotService;
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

            long total = json.value(jdbc.queryForObject(
                "SELECT count(*) FROM acc_commissions WHERE ?::text IS NULL",
                Long.class, search)) instanceof Number n ? n.longValue() : 0;
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT c.id::text AS id, c.employee_id::text AS employee_id, c.rule_id::text AS rule_id,
                       c.the_month, c.amount, c.currency, c.sales_amount, c.profit_amount, c.status,
                       c.remark, e.name AS employee_name, e.emp_no,
                       r.name AS rule_name,
                       c.audit_status, c.audited_at, c.audit_name, c.created_at
                FROM acc_commissions c
                JOIN acc_employees e ON e.id = c.employee_id
                LEFT JOIN acc_commission_rules r ON r.id = c.rule_id
                WHERE ?::text IS NULL OR (e.name ILIKE ? OR e.emp_no ILIKE ? OR c.the_month ILIKE ?)
                ORDER BY c.the_month DESC, e.name
                LIMIT ? OFFSET ?
                """, search, search, search, search, limit, offset);
            return AccPaging.result(rows.stream().map(this::project).toList(), total);
        } catch (DataAccessException ex) {
            return AccPaging.result(List.of(), 0);
        }
    }

    @GetMapping("/{id}/raw")
    public Map<String, Object> raw(@PathVariable String id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM acc_commissions WHERE id = ?::uuid LIMIT 1", id);
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String employeeId = (String) body.get("employeeId");
        String theMonth = (String) body.get("theMonth");
        if (employeeId == null || employeeId.isBlank()) {
            throw ApiException.badRequest("请选择员工");
        }
        if (theMonth == null || !theMonth.matches("\\d{4}-\\d{2}")) {
            throw ApiException.badRequest("月份格式错误，应为 YYYY-MM");
        }
        Object amt = body.get("amount");
        if (amt instanceof Number an && an.doubleValue() <= 0) {
            throw ApiException.badRequest("提成金额必须大于零");
        }
        String id = jdbc.queryForObject("""
            INSERT INTO acc_commissions (tenant_id, employee_id, rule_id, the_month, amount, currency,
                                         sales_amount, profit_amount, status, remark)
            VALUES (current_setting('app.current_tenant_id')::uuid, ?::uuid, ?::uuid, ?, ?::numeric,
                    ?::text, ?::numeric, ?::numeric, ?::text, ?::text)
            RETURNING id::text
            """, String.class, employeeId, body.get("ruleId"), theMonth,
            body.get("amount"), body.get("currency"), body.get("salesAmount"),
            body.get("profitAmount"), body.get("status"), body.get("remark"));
        BigDecimal amount = body.get("amount") instanceof Number n
            ? BigDecimal.valueOf(n.doubleValue()) : null;
        String currency = body.get("currency") != null ? body.get("currency").toString() : "CNY";
        moneySnapshotService.snapshot(TABLE, id, amount, currency);
        return Map.of("id", id, "employeeId", employeeId, "theMonth", theMonth);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String currentAudit = jdbc.queryForObject(
            "SELECT audit_status FROM acc_commissions WHERE id = ?::uuid", String.class, id);
        FieldGate.FilterResult gate = fieldGate.filterAllowedFields(TABLE, currentAudit, body);
        if (!gate.rejected().isEmpty() && gate.allowed().isEmpty()) {
            throw ApiException.badRequest("提成已审核，字段不可修改: " + String.join(",", gate.rejected())
                + "；请先反审");
        }
        Map<String, Object> allowed = gate.allowed();
        jdbc.update("""
            UPDATE acc_commissions SET
              amount = coalesce(?::numeric, amount),
              sales_amount = coalesce(?::numeric, sales_amount),
              profit_amount = coalesce(?::numeric, profit_amount),
              status = coalesce(?::text, status),
              remark = coalesce(?::text, remark)
            WHERE id = ?::uuid
            """, allowed.get("amount"), allowed.get("salesAmount"),
            allowed.get("profitAmount"), (String) allowed.get("status"),
            (String) allowed.get("remark"), id);
        if (allowed.containsKey("amount")) {
            Map<String, Object> row = jdbc.queryForMap(
                "SELECT amount, currency FROM acc_commissions WHERE id = ?::uuid", id);
            BigDecimal newAmount = row.get("amount") instanceof Number n
                ? BigDecimal.valueOf(n.doubleValue()) : null;
            String currency = row.get("currency") != null ? row.get("currency").toString() : "CNY";
            moneySnapshotService.snapshot(TABLE, id, newAmount, currency);
        }
        return Map.of("id", id, "rejectedFields", gate.rejected());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        String auditStatus = jdbc.queryForObject(
            "SELECT audit_status FROM acc_commissions WHERE id = ?::uuid", String.class, id);
        if ("AUDITED".equals(auditStatus)) {
            throw ApiException.badRequest("提成已审核，不能删除，请先反审");
        }
        cascadeChecker.checkBeforeDelete(TABLE, id);
        jdbc.update("DELETE FROM acc_commissions WHERE id = ?::uuid", id);
        return Map.of("id", id, "deleted", true);
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("employeeId", row.get("employee_id"));
        out.put("employeeName", row.get("employee_name"));
        out.put("empNo", row.get("emp_no"));
        out.put("ruleId", row.get("rule_id"));
        out.put("ruleName", row.get("rule_name"));
        out.put("theMonth", row.get("the_month"));
        out.put("amount", row.get("amount"));
        out.put("currency", row.get("currency"));
        out.put("salesAmount", row.get("sales_amount"));
        out.put("profitAmount", row.get("profit_amount"));
        out.put("status", row.get("status"));
        out.put("remark", row.get("remark"));
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        out.put("auditName", row.get("audit_name"));
        return out;
    }

    // ════════ P0-B3 提成计算引擎 ════════
    //   POST /commissions/calculate?month=YYYY-MM
    //   按规则: SALES_PER_MILLE = sales * percent / 1000
    //         PROFIT_PER_MILLE = profit * percent / 1000
    //         PER_COUNT        = piece_count * percent
    //   规则优先级: customer-override (按 shipment.customer_id 匹配) > employee-default
    //   每票一行写 acc_commission_lines, 汇总写 acc_commissions.

    /** POST /commissions/calculate?month=2026-06[&employeeId=...] */
    @PostMapping("/calculate")
    @org.springframework.transaction.annotation.Transactional
    public Map<String, Object> calculate(
            @RequestParam String month,
            @RequestParam(required = false) String employeeId) {
        if (month == null || !month.matches("\\d{4}-\\d{2}")) {
            throw ApiException.badRequest("month 格式 YYYY-MM (如 2026-06)");
        }
        String monthStart = month + "-01";
        String monthEnd = java.time.LocalDate.parse(monthStart).plusMonths(1).toString();

        // 1. 找该月有出货的员工 (sales / servicer / salesman) — xqt-saas shipments.seller_id
        List<Map<String, Object>> employees;
        if (employeeId != null && !employeeId.isBlank()) {
            employees = jdbc.queryForList("""
                SELECT id::text, name FROM acc_employees WHERE id = ?::uuid
                """, employeeId);
        } else {
            employees = jdbc.queryForList("""
                SELECT DISTINCT e.id::text, e.name
                FROM acc_employees e
                WHERE e.id IN (
                  SELECT DISTINCT seller_id FROM shipments
                  WHERE created_at >= ?::date AND created_at < ?::date
                    AND seller_id IS NOT NULL
                )
                """, monthStart, monthEnd);
        }

        int empProcessed = 0;
        java.math.BigDecimal totalAmount = java.math.BigDecimal.ZERO;
        for (Map<String, Object> emp : employees) {
            String empId = (String) emp.get("id");
            // 删除该员工该月旧记录 (允许重算)
            jdbc.update("""
                DELETE FROM acc_commissions WHERE employee_id = ?::uuid AND the_month = ?
                """, empId, month);

            // 2. 取该员工该月所有 shipments (含 sales/profit/piece)
            List<Map<String, Object>> ships = jdbc.queryForList("""
                SELECT s.id::text AS ship_id, s.customer_id::text AS customer_id, s.shipment_no,
                       coalesce((
                         SELECT sum(coalesce(snap.target_amount, ch.amount))
                         FROM charges ch
                         LEFT JOIN exchange_rate_snapshots snap
                           ON snap.entity_type='charges' AND snap.entity_id=ch.id AND snap.target_currency='CNY'
                         WHERE ch.shipment_id = s.id AND ch.side='AR' AND ch.settlement_status<>'VOID'
                       ), 0) AS sales,
                       coalesce((
                         SELECT sum(coalesce(snap.target_amount, ch.amount))
                         FROM charges ch
                         LEFT JOIN exchange_rate_snapshots snap
                           ON snap.entity_type='charges' AND snap.entity_id=ch.id AND snap.target_currency='CNY'
                         WHERE ch.shipment_id = s.id AND ch.side='AP' AND ch.settlement_status<>'VOID'
                       ), 0) AS cost,
                       coalesce((SELECT count(*) FROM cartons WHERE shipment_id = s.id), 0) AS piece_count
                FROM shipments s
                WHERE s.seller_id = ?::uuid
                  AND s.created_at >= ?::date AND s.created_at < ?::date
                """, empId, monthStart, monthEnd);

            if (ships.isEmpty()) continue;

            // 3. 取员工默认规则 + 客户级覆盖规则
            Map<String, Object> defaultRule = findDefaultRule(empId);
            if (defaultRule == null) continue;

            // 4. 算每票贡献
            java.math.BigDecimal empAmount = java.math.BigDecimal.ZERO;
            java.math.BigDecimal totalSales = java.math.BigDecimal.ZERO;
            java.math.BigDecimal totalProfit = java.math.BigDecimal.ZERO;
            int totalPieces = 0;
            String commissionId = jdbc.queryForObject("""
                INSERT INTO acc_commissions
                  (employee_id, the_month, amount, currency, sales_amount, profit_amount,
                   piece_count, status, rule_method, rule_percent)
                VALUES (?::uuid, ?, 0, 'CNY', 0, 0, 0, 'PENDING', ?, ?)
                RETURNING id::text
                """, String.class, empId, month, defaultRule.get("method"), defaultRule.get("percent"));

            for (Map<String, Object> sh : ships) {
                String custId = (String) sh.get("customer_id");
                java.math.BigDecimal sales = (java.math.BigDecimal) sh.get("sales");
                java.math.BigDecimal cost = (java.math.BigDecimal) sh.get("cost");
                java.math.BigDecimal profit = sales.subtract(cost);
                int pieces = ((Number) sh.get("piece_count")).intValue();
                // 找客户级覆盖, 没就用默认
                Map<String, Object> rule = findCustomerRule(empId, custId);
                if (rule == null) rule = defaultRule;
                java.math.BigDecimal contribution = calcContribution(rule, sales, profit, pieces);

                jdbc.update("""
                    INSERT INTO acc_commission_lines
                      (commission_id, shipment_id, order_no, customer_id, sales_amount,
                       profit_amount, piece_count, contribution, rule_id)
                    VALUES (?::uuid, ?::uuid, ?, ?::uuid, ?, ?, ?, ?, ?::uuid)
                    """, commissionId, sh.get("ship_id"), sh.get("shipment_no"),
                    custId, sales, profit, pieces, contribution, rule.get("id"));

                empAmount = empAmount.add(contribution);
                totalSales = totalSales.add(sales);
                totalProfit = totalProfit.add(profit);
                totalPieces += pieces;
            }
            jdbc.update("""
                UPDATE acc_commissions
                SET amount = ?, sales_amount = ?, profit_amount = ?, piece_count = ?
                WHERE id = ?::uuid
                """, empAmount, totalSales, totalProfit, totalPieces, commissionId);
            empProcessed++;
            totalAmount = totalAmount.add(empAmount);
        }
        return Map.of("month", month, "employeesProcessed", empProcessed, "totalAmount", totalAmount);
    }

    /** 员工默认规则 (customer_id IS NULL, active, 时间窗有效) */
    @SuppressWarnings("rawtypes")
    private Map<String, Object> findDefaultRule(String empId) {
        try {
            return jdbc.queryForMap("""
                SELECT id::text, method, percent
                FROM acc_commission_rules
                WHERE employee_id = ?::uuid AND customer_id IS NULL AND active = true
                  AND (start_date IS NULL OR start_date <= current_date)
                  AND (end_date IS NULL OR end_date >= current_date)
                  AND method IS NOT NULL
                ORDER BY start_date DESC NULLS LAST LIMIT 1
                """, empId);
        } catch (DataAccessException ex) { return null; }
    }

    /** 客户级覆盖 (employee_id + customer_id 匹配, 时间窗有效) */
    private Map<String, Object> findCustomerRule(String empId, String custId) {
        if (custId == null) return null;
        try {
            return jdbc.queryForMap("""
                SELECT id::text, method, percent
                FROM acc_commission_rules
                WHERE employee_id = ?::uuid AND customer_id = ?::uuid AND active = true
                  AND (start_date IS NULL OR start_date <= current_date)
                  AND (end_date IS NULL OR end_date >= current_date)
                  AND method IS NOT NULL
                ORDER BY start_date DESC NULLS LAST LIMIT 1
                """, empId, custId);
        } catch (DataAccessException ex) { return null; }
    }

    private java.math.BigDecimal calcContribution(Map<String, Object> rule,
                                                    java.math.BigDecimal sales,
                                                    java.math.BigDecimal profit, int pieces) {
        String method = String.valueOf(rule.get("method"));
        java.math.BigDecimal percent = (java.math.BigDecimal) rule.get("percent");
        if (percent == null) return java.math.BigDecimal.ZERO;
        return switch (method) {
            case "SALES_PER_MILLE" -> sales.multiply(percent).divide(new java.math.BigDecimal("1000"), 2, java.math.RoundingMode.HALF_UP);
            case "PROFIT_PER_MILLE" -> profit.multiply(percent).divide(new java.math.BigDecimal("1000"), 2, java.math.RoundingMode.HALF_UP);
            case "PER_COUNT" -> percent.multiply(new java.math.BigDecimal(pieces));
            default -> java.math.BigDecimal.ZERO;
        };
    }

    /** GET /commissions/{id}/lines — 提成明细行 */
    @GetMapping("/{id}/lines")
    public Map<String, Object> lines(@PathVariable String id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT l.id::text, l.shipment_id::text, l.order_no, l.customer_id::text,
                   c.code AS customer_code, c.name AS customer_name,
                   l.sales_amount, l.profit_amount, l.piece_count, l.contribution
            FROM acc_commission_lines l
            LEFT JOIN customers c ON c.id = l.customer_id
            WHERE l.commission_id = ?::uuid
            ORDER BY l.contribution DESC
            """, id);
        return Map.of("data", rows.stream().map(r -> {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("shipmentId", r.get("shipment_id"));
            o.put("orderNo", r.get("order_no"));
            o.put("customerCode", r.get("customer_code"));
            o.put("customerName", r.get("customer_name"));
            o.put("salesAmount", r.get("sales_amount"));
            o.put("profitAmount", r.get("profit_amount"));
            o.put("pieceCount", r.get("piece_count"));
            o.put("contribution", r.get("contribution"));
            return o;
        }).toList(), "total", rows.size());
    }
}

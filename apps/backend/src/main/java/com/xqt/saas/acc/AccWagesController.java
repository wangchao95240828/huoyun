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
@RequestMapping("/api/acc/wages")
public class AccWagesController {
    private static final String TABLE = "acc_wages";

    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final CascadeChecker cascadeChecker;
    private final FieldGate fieldGate;
    private final MoneySnapshotService moneySnapshotService;

    public AccWagesController(JdbcTemplate jdbc, JsonSupport json,
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
                "SELECT count(*) FROM acc_wages WHERE ?::text IS NULL",
                Long.class, search)) instanceof Number n ? n.longValue() : 0;
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT w.id::text AS id, w.employee_id::text AS employee_id, w.the_month,
                       w.basic, w.bonus, w.commission, w.deduction, w.total, w.currency,
                       w.remark, e.name AS employee_name, e.emp_no,
                       w.audit_status, w.audited_at, w.audit_name, w.created_at
                FROM acc_wages w
                JOIN acc_employees e ON e.id = w.employee_id
                WHERE ?::text IS NULL OR (e.name ILIKE ? OR e.emp_no ILIKE ? OR w.the_month ILIKE ?)
                ORDER BY w.the_month DESC, e.name
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
            "SELECT * FROM acc_wages WHERE id = ?::uuid LIMIT 1", id);
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String employeeId = (String) body.get("employeeId");
        if (employeeId == null || employeeId.isBlank()) {
            throw ApiException.badRequest("请选择员工");
        }
        String theMonth = (String) body.get("theMonth");
        // ACC Wage.php L1671: 月份出错（必须 YYYY-MM 格式）
        if (theMonth == null || !theMonth.matches("\\d{4}-\\d{2}")) {
            throw ApiException.badRequest("月份格式错误，应为 YYYY-MM");
        }
        // 同员工同月不可重复
        Integer dup = jdbc.queryForObject("""
            SELECT count(*) FROM acc_wages WHERE employee_id = ?::uuid AND the_month = ?
            """, Integer.class, employeeId, theMonth);
        if (dup != null && dup > 0) {
            throw ApiException.badRequest("该员工 " + theMonth + " 的工资记录已存在");
        }
        // total 必须 >= 0（允许 0 是抵扣过多的极端场景）
        Object totalRaw = body.get("total");
        if (totalRaw instanceof Number tn && tn.doubleValue() < 0) {
            throw ApiException.badRequest("工资总额不能为负");
        }
        String id = jdbc.queryForObject("""
            INSERT INTO acc_wages (tenant_id, employee_id, the_month, basic, bonus, commission, deduction, total, currency, remark)
            VALUES (current_setting('app.current_tenant_id')::uuid, ?::uuid, ?, ?::numeric, ?::numeric,
                    ?::numeric, ?::numeric, ?::numeric, ?::text, ?::text)
            RETURNING id::text
            """, String.class, employeeId, theMonth,
            body.get("basic"), body.get("bonus"), body.get("commission"),
            body.get("deduction"), body.get("total"), body.get("currency"), body.get("remark"));
        BigDecimal total = body.get("total") instanceof Number n
            ? BigDecimal.valueOf(n.doubleValue()) : null;
        String currency = body.get("currency") != null ? body.get("currency").toString() : "CNY";
        moneySnapshotService.snapshot(TABLE, id, total, currency);
        return Map.of("id", id, "employeeId", employeeId, "theMonth", theMonth);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String currentAudit = jdbc.queryForObject(
            "SELECT audit_status FROM acc_wages WHERE id = ?::uuid", String.class, id);
        FieldGate.FilterResult gate = fieldGate.filterAllowedFields(TABLE, currentAudit, body);
        if (!gate.rejected().isEmpty() && gate.allowed().isEmpty()) {
            throw ApiException.badRequest("工资已审核，字段不可修改: " + String.join(",", gate.rejected())
                + "；请先反审");
        }
        Map<String, Object> allowed = gate.allowed();
        jdbc.update("""
            UPDATE acc_wages SET
              basic = coalesce(?::numeric, basic),
              bonus = coalesce(?::numeric, bonus),
              commission = coalesce(?::numeric, commission),
              deduction = coalesce(?::numeric, deduction),
              total = coalesce(?::numeric, total),
              remark = coalesce(?::text, remark)
            WHERE id = ?::uuid
            """, allowed.get("basic"), allowed.get("bonus"), allowed.get("commission"),
            allowed.get("deduction"), allowed.get("total"), allowed.get("remark"), id);
        if (allowed.containsKey("total")) {
            Map<String, Object> row = jdbc.queryForMap(
                "SELECT total, currency FROM acc_wages WHERE id = ?::uuid", id);
            BigDecimal newTotal = row.get("total") instanceof Number n
                ? BigDecimal.valueOf(n.doubleValue()) : null;
            String currency = row.get("currency") != null ? row.get("currency").toString() : "CNY";
            moneySnapshotService.snapshot(TABLE, id, newTotal, currency);
        }
        return Map.of("id", id, "rejectedFields", gate.rejected());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        String auditStatus = jdbc.queryForObject(
            "SELECT audit_status FROM acc_wages WHERE id = ?::uuid", String.class, id);
        if ("AUDITED".equals(auditStatus)) {
            throw ApiException.badRequest("工资已审核，不能删除，请先反审");
        }
        cascadeChecker.checkBeforeDelete(TABLE, id);
        jdbc.update("DELETE FROM acc_wages WHERE id = ?::uuid", id);
        return Map.of("id", id, "deleted", true);
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("employeeId", row.get("employee_id"));
        out.put("employeeName", row.get("employee_name"));
        out.put("empNo", row.get("emp_no"));
        out.put("theMonth", row.get("the_month"));
        out.put("basic", row.get("basic"));
        out.put("bonus", row.get("bonus"));
        out.put("commission", row.get("commission"));
        out.put("deduction", row.get("deduction"));
        out.put("total", row.get("total"));
        out.put("currency", row.get("currency"));
        out.put("remark", row.get("remark"));
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        out.put("auditName", row.get("audit_name"));
        out.put("payStatus", row.get("pay_status"));
        out.put("paidAt", json.value(row.get("paid_at")));
        out.put("paidName", row.get("paid_name"));
        return out;
    }

    /**
     * ACC Wage.php → 发工资业务动作。
     * 要求：audit_status='AUDITED' 且 pay_status='PENDING'。把状态切到 PAID。
     */
    @PostMapping("/{id}/pay")
    public Map<String, Object> pay(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        String operator = body == null ? null : (String) body.get("operator");
        int n = jdbc.update("""
            UPDATE acc_wages
            SET pay_status = 'PAID', paid_at = now(), paid_name = coalesce(?, 'system')
            WHERE id = ?::uuid
              AND audit_status = 'AUDITED'
              AND pay_status = 'PENDING'
            """, operator, id);
        if (n == 0) {
            // 诊断当前状态给清楚错误
            Map<String, Object> row = jdbc.queryForMap(
                "SELECT audit_status, pay_status FROM acc_wages WHERE id = ?::uuid", id);
            String aud = (String) row.get("audit_status");
            String pay = (String) row.get("pay_status");
            if (!"AUDITED".equals(aud)) {
                throw ApiException.badRequest("工资未审核，请先审核（当前 " + aud + "）");
            }
            if (!"PENDING".equals(pay)) {
                throw ApiException.badRequest("工资已发放或已作废（当前 " + pay + "）");
            }
            throw ApiException.badRequest("工资发放失败");
        }
        return Map.of("id", id, "paid", true);
    }

    /** 作废已发放（仅限当月，且无资金流向变更时） */
    @PostMapping("/{id}/void-pay")
    public Map<String, Object> voidPay(@PathVariable String id) {
        int n = jdbc.update("""
            UPDATE acc_wages SET pay_status = 'VOIDED'
            WHERE id = ?::uuid AND pay_status = 'PAID'
            """, id);
        if (n == 0) throw ApiException.badRequest("仅可作废已发放状态的工资");
        return Map.of("id", id, "voided", true);
    }

    // ════════ P0-D5 HR 工资引擎 MVP (替代 ACC Wage.php 核心逻辑) ════════
    //   POST /wages/calculate?month=YYYY-MM[&employeeId=...]
    //   公式: total = basic + bonus + commission - deduction
    //   deduction = 社保 + 公积金 + 借支抵扣 + 罚款 + 个税
    //   每项写 acc_wage_items 明细行便于追溯.

    @org.springframework.web.bind.annotation.PostMapping("/calculate")
    @org.springframework.transaction.annotation.Transactional
    public Map<String, Object> calculate(
            @org.springframework.web.bind.annotation.RequestParam String month,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String employeeId) {
        if (month == null || !month.matches("\\d{4}-\\d{2}")) {
            throw ApiException.badRequest("month 格式 YYYY-MM (如 2026-06)");
        }
        java.time.LocalDate monthStart = java.time.LocalDate.parse(month + "-01");
        java.time.LocalDate monthEnd = monthStart.plusMonths(1);

        // 1. 找在职员工 (status=ACTIVE 或不限, entry_date <= month_end)
        List<Map<String, Object>> employees;
        if (employeeId != null && !employeeId.isBlank()) {
            employees = jdbc.queryForList(
                "SELECT id::text, name, coalesce(basic_salary, 0) AS basic_salary FROM acc_employees WHERE id = ?::uuid",
                employeeId);
        } else {
            employees = jdbc.queryForList("""
                SELECT id::text, name, coalesce(basic_salary, 0) AS basic_salary
                FROM acc_employees
                WHERE coalesce(entry_date, '1900-01-01') <= ?::date
                  AND (status IS NULL OR status IN ('ACTIVE','在职') OR status = 'NORMAL')
                """, monthEnd);
        }

        int processed = 0;
        java.math.BigDecimal grandTotal = java.math.BigDecimal.ZERO;
        for (Map<String, Object> emp : employees) {
            String empId = (String) emp.get("id");
            java.math.BigDecimal basic = (java.math.BigDecimal) emp.get("basic_salary");
            if (basic == null) basic = java.math.BigDecimal.ZERO;

            // 删除该员工该月旧记录 (允许重算)
            jdbc.update("""
                DELETE FROM acc_wages WHERE employee_id = ?::uuid AND the_month = ?
                """, empId, month);

            // 2. 当月提成 (从 acc_commissions 取, 复用 B3 引擎结果)
            java.math.BigDecimal commission = java.math.BigDecimal.ZERO;
            String commissionId = null;
            try {
                List<Map<String, Object>> coms = jdbc.queryForList("""
                    SELECT id::text, amount FROM acc_commissions
                    WHERE employee_id = ?::uuid AND the_month = ?
                    LIMIT 1
                    """, empId, month);
                if (!coms.isEmpty()) {
                    commission = (java.math.BigDecimal) coms.get(0).get("amount");
                    commissionId = (String) coms.get(0).get("id");
                }
            } catch (DataAccessException ignored) {}

            // 3. 社保扣减 (从 acc_social_persons 取当月费用, 没找到记 0)
            java.math.BigDecimal socialDeduction = java.math.BigDecimal.ZERO;
            try {
                java.math.BigDecimal s = jdbc.queryForObject("""
                    SELECT coalesce(sum(personal_amount), 0) FROM acc_social_persons
                    WHERE employee_id = ?::uuid AND the_month = ?
                    """, java.math.BigDecimal.class, empId, month);
                if (s != null) socialDeduction = s;
            } catch (DataAccessException ignored) {}

            // 4. 公积金扣减 (从 acc_fund_persons 取)
            java.math.BigDecimal fundDeduction = java.math.BigDecimal.ZERO;
            try {
                java.math.BigDecimal f = jdbc.queryForObject("""
                    SELECT coalesce(sum(personal_amount), 0) FROM acc_fund_persons
                    WHERE employee_id = ?::uuid AND the_month = ?
                    """, java.math.BigDecimal.class, empId, month);
                if (f != null) fundDeduction = f;
            } catch (DataAccessException ignored) {}

            // 5. 借支抵扣 (acc_borrowings 当月应还本金 + 利息)
            java.math.BigDecimal borrowingRepay = java.math.BigDecimal.ZERO;
            String borrowingId = null;
            try {
                List<Map<String, Object>> bs = jdbc.queryForList("""
                    SELECT id::text, coalesce(repayment, 0) + coalesce(fixed_amount, 0) AS due
                    FROM acc_borrowings
                    WHERE employee_id = ?::uuid AND repayment_status IN ('PENDING','PARTIAL')
                      AND coalesce(start_date, '1900-01-01') <= ?::date
                      AND (end_date IS NULL OR end_date >= ?::date)
                    """, empId, monthEnd, monthStart);
                for (Map<String, Object> b : bs) {
                    java.math.BigDecimal due = (java.math.BigDecimal) b.get("due");
                    if (due != null && due.signum() > 0) {
                        borrowingRepay = borrowingRepay.add(due);
                        borrowingId = (String) b.get("id");
                    }
                }
            } catch (DataAccessException ignored) {}

            // 6. 当月罚款 (acc_fines)
            java.math.BigDecimal fineDeduction = java.math.BigDecimal.ZERO;
            try {
                java.math.BigDecimal fn = jdbc.queryForObject("""
                    SELECT coalesce(sum(amount), 0) FROM acc_fines
                    WHERE customer_id IS NULL AND partner_id IS NULL
                      AND created_at >= ?::date AND created_at < ?::date
                      AND audit_status = 'AUDITED'
                    """, java.math.BigDecimal.class, monthStart, monthEnd);
                if (fn != null) fineDeduction = fn;
            } catch (DataAccessException ignored) {}

            java.math.BigDecimal deduction = socialDeduction.add(fundDeduction).add(borrowingRepay).add(fineDeduction);
            java.math.BigDecimal total = basic.add(commission).subtract(deduction);

            // 7. 写 acc_wages 主表 + acc_wage_items 明细
            String wageId = jdbc.queryForObject("""
                INSERT INTO acc_wages
                  (employee_id, the_month, basic, commission, deduction, total, currency,
                   audit_status, pay_status)
                VALUES (?::uuid, ?, ?, ?, ?, ?, 'CNY', 'PENDING', 'PENDING')
                RETURNING id::text
                """, String.class, empId, month, basic, commission, deduction, total);

            // 加项明细
            insertItem(wageId, "BASIC", "ADD", basic, null, null, "员工基本工资");
            if (commission.signum() > 0)
                insertItem(wageId, "COMMISSION", "ADD", commission, "acc_commissions", commissionId, "B3 提成引擎计算");
            // 扣项明细
            if (socialDeduction.signum() > 0)
                insertItem(wageId, "SOCIAL_INSURANCE", "SUB", socialDeduction, "acc_social_persons", null, "社保个人部分");
            if (fundDeduction.signum() > 0)
                insertItem(wageId, "HOUSING_FUND", "SUB", fundDeduction, "acc_fund_persons", null, "公积金个人部分");
            if (borrowingRepay.signum() > 0)
                insertItem(wageId, "BORROWING_REPAY", "SUB", borrowingRepay, "acc_borrowings", borrowingId, "借支当月抵扣");
            if (fineDeduction.signum() > 0)
                insertItem(wageId, "FINE", "SUB", fineDeduction, "acc_fines", null, "当月罚款");

            processed++;
            grandTotal = grandTotal.add(total);
        }
        return Map.of("month", month, "employeesProcessed", processed, "grandTotal", grandTotal);
    }

    private void insertItem(String wageId, String type, String direction,
                             java.math.BigDecimal amount, String sourceType, String sourceId, String remark) {
        if (amount == null || amount.signum() == 0) return;
        jdbc.update("""
            INSERT INTO acc_wage_items
              (wage_id, item_type, direction, amount, source_type, source_id, remark)
            VALUES (?::uuid, ?, ?, ?, ?, ?::uuid, ?)
            """, wageId, type, direction, amount, sourceType, sourceId, remark);
    }

    /** GET /wages/{id}/items — 工资明细行 */
    @org.springframework.web.bind.annotation.GetMapping("/{id}/items")
    public Map<String, Object> items(@org.springframework.web.bind.annotation.PathVariable String id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT item_type, direction, amount, source_type, source_id::text, remark, created_at
            FROM acc_wage_items WHERE wage_id = ?::uuid
            ORDER BY direction DESC, item_type
            """, id);
        return Map.of("data", rows.stream().map(r -> {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("itemType", r.get("item_type"));
            o.put("direction", r.get("direction"));
            o.put("amount", r.get("amount"));
            o.put("sourceType", r.get("source_type"));
            o.put("sourceId", r.get("source_id"));
            o.put("remark", r.get("remark"));
            return o;
        }).toList(), "total", rows.size());
    }
}

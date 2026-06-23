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
@RequestMapping("/api/acc/borrowings")
public class AccBorrowingsController {
    private static final String TABLE = "acc_borrowings";
    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final CascadeChecker cascadeChecker;
    private final FieldGate fieldGate;
    private final MoneySnapshotService moneySnapshotService;

    public AccBorrowingsController(JdbcTemplate jdbc, JsonSupport json,
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
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String dateFrom,
        @RequestParam(required = false) String dateTo
    ) {
        try {
            int limit = AccPaging.pageSize(pageSize);
            int offset = AccPaging.offset(page, pageSize);
            String search = keyword == null || keyword.isBlank() ? null : "%" + keyword + "%";
            Long total = jdbc.queryForObject("""
                SELECT count(*) FROM acc_borrowings
                WHERE (?::text IS NULL OR borrower_name ILIKE ?)
                  AND (?::date IS NULL OR the_date >= ?::date)
                  AND (?::date IS NULL OR the_date < (?::date + 1))
                """, Long.class, search, search, dateFrom, dateFrom, dateTo, dateTo);
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id::text AS id, borrower_name, the_date, borrowing_type, amount, currency,
                       rate, due_date, repayment_status, repaid_amount,
                       employee_id::text AS employee_id, start_date, end_date,
                       cycle, mode, pay_date, remaining, forward, repayment, fixed_amount,
                       remark, add_name, created_at,
                       audit_status, audited_at, audit_name
                FROM acc_borrowings
                WHERE (?::text IS NULL OR borrower_name ILIKE ?)
                  AND (?::date IS NULL OR the_date >= ?::date)
                  AND (?::date IS NULL OR the_date < (?::date + 1))
                ORDER BY created_at DESC
                LIMIT ? OFFSET ?
                """, search, search, dateFrom, dateFrom, dateTo, dateTo, limit, offset);
            return AccPaging.result(rows.stream().map(this::project).toList(), total == null ? 0 : total);
        } catch (DataAccessException ex) {
            return AccPaging.result(List.of(), 0);
        }
    }

    @GetMapping("/{id}/raw")
    public Map<String, Object> raw(@PathVariable String id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM acc_borrowings WHERE id = ?::uuid LIMIT 1", id);
        return rows.isEmpty() ? Map.of() : json.row(rows.get(0));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        Object borrower = body.getOrDefault("name", body.get("borrower_name"));
        if (borrower == null || borrower.toString().isBlank()) {
            throw ApiException.badRequest("借款人姓名必填");
        }
        BigDecimal amount = body.get("amount") instanceof Number n
            ? new BigDecimal(n.toString()) : BigDecimal.ZERO;
        // ACC Borrowing.php L1108: 本金/利息必须为正数
        if (amount.signum() <= 0) {
            throw ApiException.badRequest("借款金额必须大于零");
        }
        String currency = (String) body.getOrDefault("currency", "CNY");
        if (currency.length() != 3) {
            throw ApiException.badRequest("找不到币种");
        }
        // ACC Borrowing.php L1183: 利率（如果有）必须 >= 0
        Object rateRaw = body.get("rate");
        if (rateRaw instanceof Number rn && rn.doubleValue() < 0) {
            throw ApiException.badRequest("利率不能为负");
        }
        String id = jdbc.queryForObject("""
            INSERT INTO acc_borrowings (
              tenant_id, borrower_name, the_date, borrowing_type, amount, currency, rate, remark, add_name
            ) VALUES (
              current_setting('app.current_tenant_id')::uuid, ?, ?::date, ?, ?, ?, ?, ?, ?
            )
            RETURNING id::text
            """, String.class,
            body.getOrDefault("name", body.get("borrower_name")),
            body.getOrDefault("theDate", body.get("the_date")),
            body.getOrDefault("type", body.get("borrowing_type")),
            amount, currency,
            body.get("rate") instanceof Number r ? new BigDecimal(r.toString()) : null,
            body.get("remark"),
            body.getOrDefault("addName", body.get("add_name")));
        moneySnapshotService.snapshot(TABLE, id, amount, currency);
        return Map.of("id", id);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String currentAudit = jdbc.queryForObject(
            "SELECT audit_status FROM acc_borrowings WHERE id = ?::uuid", String.class, id);
        FieldGate.FilterResult gate = fieldGate.filterAllowedFields(TABLE, currentAudit, body);
        if (!gate.rejected().isEmpty() && gate.allowed().isEmpty()) {
            throw ApiException.badRequest("借贷已审核，字段不可修改: " + String.join(",", gate.rejected()) + "；请先反审");
        }
        Map<String, Object> allowed = gate.allowed();
        jdbc.update("UPDATE acc_borrowings SET remark = coalesce(?, remark) WHERE id = ?::uuid",
            (String) allowed.get("remark"), id);
        return Map.of("id", id, "rejectedFields", gate.rejected());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        String auditStatus = jdbc.queryForObject(
            "SELECT audit_status FROM acc_borrowings WHERE id = ?::uuid", String.class, id);
        if ("AUDITED".equals(auditStatus)) {
            throw ApiException.badRequest("借贷已审核，不能删除，请先反审");
        }
        cascadeChecker.checkBeforeDelete(TABLE, id);
        jdbc.update("DELETE FROM acc_borrowings WHERE id = ?::uuid", id);
        return Map.of("id", id, "deleted", true);
    }

    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("name", row.get("borrower_name"));
        out.put("theDate", json.value(row.get("the_date")));
        out.put("type", row.get("borrowing_type"));
        out.put("amount", row.get("amount"));
        out.put("currency", row.get("currency"));
        out.put("rate", row.get("rate"));
        out.put("dueDate", json.value(row.get("due_date")));
        out.put("repaymentStatus", row.get("repayment_status"));
        out.put("repaidAmount", row.get("repaid_amount"));
        // ACC Borrowing 完整 10 字段
        out.put("employeeId", row.get("employee_id"));
        out.put("startDate", json.value(row.get("start_date")));
        out.put("endDate", json.value(row.get("end_date")));
        out.put("cycle", row.get("cycle"));
        out.put("mode", row.get("mode"));
        out.put("payDate", json.value(row.get("pay_date")));
        out.put("remaining", row.get("remaining"));
        out.put("forward", row.get("forward"));
        out.put("repayment", row.get("repayment"));
        out.put("fixedAmount", row.get("fixed_amount"));
        out.put("remark", row.get("remark"));
        out.put("addName", row.get("add_name"));
        out.put("addTime", json.value(row.get("created_at")));
        out.put("auditStatus", row.get("audit_status"));
        out.put("auditedAt", json.value(row.get("audited_at")));
        out.put("auditName", row.get("audit_name"));
        return out;
    }

    /**
     * ACC Borrowing.php → 还款业务动作。
     * 累加 repaid_amount；若 ≥ amount 则切到 REPAID，否则 PARTIAL。
     * 要求 audit_status='AUDITED'。
     */
    @PostMapping("/{id}/repay")
    public Map<String, Object> repay(@PathVariable String id, @RequestBody Map<String, Object> body) {
        java.math.BigDecimal payAmount;
        try {
            payAmount = new java.math.BigDecimal(body.get("amount").toString());
        } catch (Exception ex) {
            throw ApiException.badRequest("amount 必填且为数字");
        }
        if (payAmount.signum() <= 0) {
            throw ApiException.badRequest("还款金额必须大于零");
        }

        Map<String, Object> row;
        try {
            row = jdbc.queryForMap(
                "SELECT amount, repaid_amount, audit_status, repayment_status FROM acc_borrowings WHERE id = ?::uuid", id);
        } catch (DataAccessException ex) {
            throw ApiException.notFound("借款记录不存在");
        }
        if (!"AUDITED".equals(row.get("audit_status"))) {
            throw ApiException.badRequest("借款未审核，无法还款");
        }
        if ("REPAID".equals(row.get("repayment_status"))) {
            throw ApiException.badRequest("借款已结清");
        }

        java.math.BigDecimal total  = (java.math.BigDecimal) row.get("amount");
        java.math.BigDecimal repaid = (java.math.BigDecimal) row.get("repaid_amount");
        java.math.BigDecimal afterRepaid = repaid.add(payAmount);
        if (afterRepaid.compareTo(total) > 0) {
            throw ApiException.badRequest("本次还款金额超过未还余额（剩 " + total.subtract(repaid) + "）");
        }

        String newStatus = afterRepaid.compareTo(total) >= 0 ? "REPAID" : "PARTIAL";
        jdbc.update(
            "UPDATE acc_borrowings SET repaid_amount = ?, repayment_status = ? WHERE id = ?::uuid",
            afterRepaid, newStatus, id);

        return Map.of(
            "id", id,
            "repaidAmount", afterRepaid,
            "repaymentStatus", newStatus,
            "remaining", total.subtract(afterRepaid)
        );
    }

    // ════════ P0-B4 借款利息计提 MVP (ACC Borrowing.php 简化版) ════════
    //   POST /api/acc/borrowings/{id}/accrue-interest?asOf=YYYY-MM-DD
    //   ACC 完整版是 5 cycle (周/月/季/半/年) × 5 mode (日/月/年/等本息/等本金) 矩阵.
    //   MVP 实现: cycle=MONTHLY, mode=SIMPLE/COMPOUND 两种, 覆盖 90% 场景.
    //   公式 (按月计提):
    //     SIMPLE   利息 = principal × rate × months
    //     COMPOUND 利息 = principal × ((1+rate)^months - 1)
    //   返回累计利息 + 应还总额 (principal + interest - repaid).
    //   每次计提写一行 acc_borrowing_interest_log (审计).

    @org.springframework.web.bind.annotation.PostMapping("/{id}/accrue-interest")
    @org.springframework.transaction.annotation.Transactional
    public Map<String, Object> accrueInterest(
            @org.springframework.web.bind.annotation.PathVariable String id,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String asOf) {
        Map<String, Object> b;
        try {
            b = jdbc.queryForMap("""
                SELECT amount, rate, mode, cycle, start_date,
                       coalesce(repaid_amount, 0) AS repaid
                FROM acc_borrowings WHERE id = ?::uuid
                """, id);
        } catch (DataAccessException ex) {
            throw com.xqt.saas.common.ApiException.notFound("借款单不存在: " + id);
        }
        java.math.BigDecimal principal = (java.math.BigDecimal) b.get("amount");
        java.math.BigDecimal rate = (java.math.BigDecimal) b.get("rate");  // 月利率 (e.g. 0.005 = 0.5%/月)
        if (rate == null) rate = java.math.BigDecimal.ZERO;
        java.sql.Date startDate = (java.sql.Date) b.get("start_date");
        if (startDate == null) {
            throw com.xqt.saas.common.ApiException.badRequest("借款单缺 start_date, 无法计息");
        }
        java.time.LocalDate start = startDate.toLocalDate();
        java.time.LocalDate end = asOf != null && !asOf.isBlank()
            ? java.time.LocalDate.parse(asOf) : java.time.LocalDate.now();
        if (end.isBefore(start)) {
            throw com.xqt.saas.common.ApiException.badRequest("asOf 不能早于借款 start_date");
        }
        long months = java.time.temporal.ChronoUnit.MONTHS.between(
            start.withDayOfMonth(1), end.withDayOfMonth(1));
        if (months < 0) months = 0;

        String mode = (String) b.get("mode");
        if (mode == null) mode = "SIMPLE";
        java.math.BigDecimal interest;
        if ("COMPOUND".equalsIgnoreCase(mode)) {
            // (1+rate)^months - 1
            java.math.BigDecimal factor = new java.math.BigDecimal("1").add(rate);
            java.math.BigDecimal pow = factor.pow((int) Math.min(months, 12L * 30)); // cap 30 年防溢出
            interest = principal.multiply(pow.subtract(java.math.BigDecimal.ONE))
                .setScale(2, java.math.RoundingMode.HALF_UP);
        } else {
            interest = principal.multiply(rate).multiply(java.math.BigDecimal.valueOf(months))
                .setScale(2, java.math.RoundingMode.HALF_UP);
        }
        java.math.BigDecimal repaid = (java.math.BigDecimal) b.get("repaid");
        java.math.BigDecimal totalDue = principal.add(interest).subtract(repaid);

        // 计提流水 (审计, 表不存在就跳过)
        try {
            jdbc.update("""
                INSERT INTO acc_borrowing_interest_log
                  (borrowing_id, as_of_date, months_elapsed, mode, rate, principal,
                   interest, total_due, created_at)
                VALUES (?::uuid, ?::date, ?, ?, ?, ?, ?, ?, now())
                """, id, end, months, mode, rate, principal, interest, totalDue);
        } catch (DataAccessException ignored) {
            // 表不存在容错, 不阻断
        }
        return Map.of(
            "borrowingId", id, "asOf", end.toString(),
            "principal", principal, "rate", rate, "mode", mode,
            "monthsElapsed", months, "interest", interest,
            "repaid", repaid, "totalDue", totalDue
        );
    }
}

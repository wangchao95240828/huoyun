package com.xqt.saas.acc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 月结/年结 — 关账工作流。
 *
 *   POST /api/acc/period-closing/preview     body: { period }  预览本月利润 + 结转金额
 *   POST /api/acc/period-closing/close       body: { period }  生成结转凭证 + 锁定期间
 *   GET  /api/acc/period-closing/status?period=
 *
 * 流程：
 *  1. 计算月度 revenue - expense = net income
 *  2. 生成结转凭证: 借 各 revenue 科目, 贷 本年利润 (3103)
 *                  借 本年利润, 贷 各 expense 科目
 *  3. 锁定该月份期间
 */
@RestController
@RequestMapping("/api/acc/period-closing")
public class AccPeriodClosingController {
    private final JdbcTemplate jdbc;

    public AccPeriodClosingController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostMapping("/preview")
    public Map<String, Object> preview(@RequestBody Map<String, Object> body) {
        String period = (String) body.get("period");
        if (period == null || !period.matches("\\d{4}-\\d{2}")) {
            throw ApiException.badRequest("period 必须为 YYYY-MM 格式");
        }
        String from = period + "-01";
        String to = period + "-31";

        BigDecimal totalRev = totalForCategory("REVENUE", from, to);
        BigDecimal totalExp = totalForCategory("EXPENSE", from, to);
        BigDecimal netIncome = totalRev.subtract(totalExp);

        List<Map<String, Object>> revenueDetail = detailForCategory("REVENUE", from, to);
        List<Map<String, Object>> expenseDetail = detailForCategory("EXPENSE", from, to);

        Boolean alreadyLocked = jdbc.queryForObject(
            "SELECT count(*) > 0 FROM acc_period_locks WHERE period = ?", Boolean.class, period);

        return Map.of(
            "period", period,
            "totalRevenue", totalRev,
            "totalExpense", totalExp,
            "netIncome", netIncome,
            "revenueDetail", revenueDetail,
            "expenseDetail", expenseDetail,
            "alreadyLocked", Boolean.TRUE.equals(alreadyLocked)
        );
    }

    @PostMapping("/close")
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> close(@RequestBody Map<String, Object> body) {
        String period = (String) body.get("period");
        if (period == null || !period.matches("\\d{4}-\\d{2}")) {
            throw ApiException.badRequest("period 必须为 YYYY-MM 格式");
        }
        Boolean alreadyLocked = jdbc.queryForObject(
            "SELECT count(*) > 0 FROM acc_period_locks WHERE period = ?", Boolean.class, period);
        if (Boolean.TRUE.equals(alreadyLocked)) {
            throw ApiException.badRequest("该期间已关账: " + period);
        }
        String from = period + "-01";
        String to = period + "-31";

        // 本年利润科目 3103
        String netIncomeSubject = jdbc.queryForObject(
            "SELECT id::text FROM acc_gl_subjects WHERE code = '3103'", String.class);
        if (netIncomeSubject == null) {
            throw ApiException.badRequest("找不到'本年利润'科目 (3103)");
        }

        // 生成结转凭证
        String voucherNo = "V-CLOSE-" + period;
        String description = "月结结转 " + period;
        String voucherId = jdbc.queryForObject("""
            INSERT INTO acc_gl_vouchers (voucher_no, the_date, description, source_type, status)
            VALUES (?, ?::date, ?, 'PERIOD_CLOSING', 'POSTED')
            RETURNING id::text
            """, String.class, voucherNo, to, description);

        int lineNo = 1;
        BigDecimal totalRev = BigDecimal.ZERO;
        BigDecimal totalExp = BigDecimal.ZERO;

        // 借 各 revenue, 贷 本年利润
        for (Map<String, Object> r : detailForCategory("REVENUE", from, to)) {
            String subId = jdbc.queryForObject(
                "SELECT id::text FROM acc_gl_subjects WHERE code = ?",
                String.class, r.get("code"));
            BigDecimal amount = (BigDecimal) r.get("amount");
            if (subId != null && amount.signum() > 0) {
                jdbc.update("""
                    INSERT INTO acc_gl_voucher_lines (voucher_id, subject_id, direction, amount, line_no, remark)
                    VALUES (?::uuid, ?::uuid, 'DEBIT', ?, ?, ?)
                    """, voucherId, subId, amount, lineNo++, "结转 " + r.get("name"));
                totalRev = totalRev.add(amount);
            }
        }
        // 借 本年利润, 贷 各 expense
        for (Map<String, Object> e : detailForCategory("EXPENSE", from, to)) {
            String subId = jdbc.queryForObject(
                "SELECT id::text FROM acc_gl_subjects WHERE code = ?",
                String.class, e.get("code"));
            BigDecimal amount = (BigDecimal) e.get("amount");
            if (subId != null && amount.signum() > 0) {
                jdbc.update("""
                    INSERT INTO acc_gl_voucher_lines (voucher_id, subject_id, direction, amount, line_no, remark)
                    VALUES (?::uuid, ?::uuid, 'CREDIT', ?, ?, ?)
                    """, voucherId, subId, amount, lineNo++, "结转 " + e.get("name"));
                totalExp = totalExp.add(amount);
            }
        }
        BigDecimal netIncome = totalRev.subtract(totalExp);
        if (netIncome.signum() > 0) {
            // 收入 > 支出，把净利润 CREDIT 到 3103
            jdbc.update("""
                INSERT INTO acc_gl_voucher_lines (voucher_id, subject_id, direction, amount, line_no, remark)
                VALUES (?::uuid, ?::uuid, 'CREDIT', ?, ?, '本期净利润结转')
                """, voucherId, netIncomeSubject, netIncome, lineNo++);
        } else if (netIncome.signum() < 0) {
            jdbc.update("""
                INSERT INTO acc_gl_voucher_lines (voucher_id, subject_id, direction, amount, line_no, remark)
                VALUES (?::uuid, ?::uuid, 'DEBIT', ?, ?, '本期净亏损结转')
                """, voucherId, netIncomeSubject, netIncome.abs(), lineNo++);
        }

        // 锁定期间
        jdbc.update("""
            INSERT INTO acc_period_locks (period, period_type, reason)
            VALUES (?, 'MONTH', '月结')
            """, period);

        return Map.of(
            "period", period,
            "voucherNo", voucherNo,
            "voucherId", voucherId,
            "netIncome", netIncome,
            "locked", true
        );
    }

    @GetMapping("/status")
    public Map<String, Object> status(@RequestParam String period) {
        if (!period.matches("\\d{4}-\\d{2}")) {
            throw ApiException.badRequest("period 必须为 YYYY-MM 格式");
        }
        Map<String, Object> lock;
        try {
            lock = jdbc.queryForMap(
                "SELECT locked_at, locked_by::text, reason FROM acc_period_locks WHERE period = ?", period);
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            lock = null;
        }
        Map<String, Object> closing;
        try {
            closing = jdbc.queryForMap(
                "SELECT voucher_no, the_date FROM acc_gl_vouchers WHERE source_type='PERIOD_CLOSING' AND voucher_no='V-CLOSE-" + period + "'");
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            closing = null;
        }
        return Map.of(
            "period", period,
            "isLocked", lock != null,
            "lockInfo", lock != null ? lock : Map.of(),
            "closingVoucher", closing != null ? closing : Map.of()
        );
    }

    private BigDecimal totalForCategory(String category, String from, String to) {
        String direction = "REVENUE".equals(category) ? "CREDIT" : "DEBIT";
        BigDecimal v = jdbc.queryForObject("""
            SELECT coalesce(sum(CASE WHEN l.direction = ? THEN l.amount ELSE -l.amount END), 0)
              FROM acc_gl_subjects s
              JOIN acc_gl_voucher_lines l ON l.subject_id = s.id
              JOIN acc_gl_vouchers v ON v.id = l.voucher_id
             WHERE s.category = ? AND v.status = 'POSTED'
               AND v.the_date >= ?::date AND v.the_date <= ?::date
            """, BigDecimal.class, direction, category, from, to);
        return v == null ? BigDecimal.ZERO : v;
    }

    private List<Map<String, Object>> detailForCategory(String category, String from, String to) {
        String direction = "REVENUE".equals(category) ? "CREDIT" : "DEBIT";
        return jdbc.queryForList("""
            SELECT s.code, s.name,
                   sum(CASE WHEN l.direction = ? THEN l.amount ELSE -l.amount END) AS amount
              FROM acc_gl_subjects s
              JOIN acc_gl_voucher_lines l ON l.subject_id = s.id
              JOIN acc_gl_vouchers v ON v.id = l.voucher_id
             WHERE s.category = ? AND v.status = 'POSTED'
               AND v.the_date >= ?::date AND v.the_date <= ?::date
             GROUP BY s.code, s.name
             HAVING sum(CASE WHEN l.direction = ? THEN l.amount ELSE -l.amount END) > 0
             ORDER BY s.code
            """, direction, category, from, to, direction);
    }
}

package com.xqt.saas.acc;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GL 三大报表 — 对齐 ACC 财务报表。
 *
 *   GET /api/acc/gl/reports/income-statement?from=&to=    利润表
 *   GET /api/acc/gl/reports/balance-sheet?as-of=          资产负债表
 *   GET /api/acc/gl/reports/cash-flow?from=&to=           现金流量表
 *   GET /api/acc/gl/reports/trial-balance?as-of=          试算平衡表
 */
@RestController
@RequestMapping("/api/acc/gl/reports")
public class AccGlReportsController {
    private final JdbcTemplate jdbc;

    public AccGlReportsController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 利润表 = 收入 - 费用。 */
    @GetMapping("/income-statement")
    public Map<String, Object> incomeStatement(@RequestParam(required = false) String from,
                                                @RequestParam(required = false) String to) {
        validateDates(from, to);
        String dateFilter = " AND v.the_date >= ?::date AND v.the_date <= ?::date";
        // 按 category 汇总（REVENUE / EXPENSE）
        List<Map<String, Object>> revenue = jdbc.queryForList("""
            SELECT s.code, s.name,
                   sum(CASE WHEN l.direction='CREDIT' THEN l.amount ELSE -l.amount END) AS amount
              FROM acc_gl_subjects s
              JOIN acc_gl_voucher_lines l ON l.subject_id = s.id
              JOIN acc_gl_vouchers v ON v.id = l.voucher_id
             WHERE s.category = 'REVENUE' AND v.status = 'POSTED'
            """ + dateFilter + """
             GROUP BY s.code, s.name HAVING sum(CASE WHEN l.direction='CREDIT' THEN l.amount ELSE -l.amount END) <> 0
             ORDER BY s.code
            """, from, to);
        List<Map<String, Object>> expense = jdbc.queryForList("""
            SELECT s.code, s.name,
                   sum(CASE WHEN l.direction='DEBIT' THEN l.amount ELSE -l.amount END) AS amount
              FROM acc_gl_subjects s
              JOIN acc_gl_voucher_lines l ON l.subject_id = s.id
              JOIN acc_gl_vouchers v ON v.id = l.voucher_id
             WHERE s.category = 'EXPENSE' AND v.status = 'POSTED'
            """ + dateFilter + """
             GROUP BY s.code, s.name HAVING sum(CASE WHEN l.direction='DEBIT' THEN l.amount ELSE -l.amount END) <> 0
             ORDER BY s.code
            """, from, to);
        BigDecimal totalRev = revenue.stream().map(r -> (BigDecimal) r.get("amount"))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalExp = expense.stream().map(r -> (BigDecimal) r.get("amount"))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        // flat data for table rendering — 加 group + category 列
        List<Map<String, Object>> flat = new java.util.ArrayList<>();
        for (Map<String, Object> r : revenue) {
            Map<String, Object> row = new LinkedHashMap<>(r);
            row.put("group", "收入");
            flat.add(row);
        }
        Map<String, Object> sep1 = new LinkedHashMap<>();
        sep1.put("code", ""); sep1.put("name", "收入合计"); sep1.put("amount", totalRev); sep1.put("group", "—");
        flat.add(sep1);
        for (Map<String, Object> e : expense) {
            Map<String, Object> row = new LinkedHashMap<>(e);
            row.put("group", "支出");
            flat.add(row);
        }
        Map<String, Object> sep2 = new LinkedHashMap<>();
        sep2.put("code", ""); sep2.put("name", "支出合计"); sep2.put("amount", totalExp); sep2.put("group", "—");
        flat.add(sep2);
        Map<String, Object> sep3 = new LinkedHashMap<>();
        sep3.put("code", ""); sep3.put("name", "净利"); sep3.put("amount", totalRev.subtract(totalExp)); sep3.put("group", "==");
        flat.add(sep3);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("data", flat);
        result.put("total", flat.size());
        result.put("period", Map.of("from", from, "to", to));
        result.put("totalRevenue", totalRev);
        result.put("totalExpense", totalExp);
        result.put("netIncome", totalRev.subtract(totalExp));
        return result;
    }

    /** 资产负债表 = Assets / Liabilities / Equity 截至某日的余额。 */
    @GetMapping("/balance-sheet")
    public Map<String, Object> balanceSheet(@RequestParam(required = false) String asOf) {
        String filter = asOf == null || asOf.isBlank() ? ""
            : " AND v.the_date <= '" + asOf + "'::date";
        // ASSETS: DEBIT 余额
        List<Map<String, Object>> assets = jdbc.queryForList("""
            SELECT s.code, s.name,
                   sum(CASE WHEN l.direction='DEBIT' THEN l.amount ELSE -l.amount END) AS balance
              FROM acc_gl_subjects s
              LEFT JOIN acc_gl_voucher_lines l ON l.subject_id = s.id
              LEFT JOIN acc_gl_vouchers v ON v.id = l.voucher_id AND v.status = 'POSTED'""" + filter + """
             WHERE s.category = 'ASSET'
             GROUP BY s.code, s.name ORDER BY s.code
            """);
        // LIABILITIES: CREDIT 余额
        List<Map<String, Object>> liabilities = jdbc.queryForList("""
            SELECT s.code, s.name,
                   sum(CASE WHEN l.direction='CREDIT' THEN l.amount ELSE -l.amount END) AS balance
              FROM acc_gl_subjects s
              LEFT JOIN acc_gl_voucher_lines l ON l.subject_id = s.id
              LEFT JOIN acc_gl_vouchers v ON v.id = l.voucher_id AND v.status = 'POSTED'""" + filter + """
             WHERE s.category = 'LIABILITY'
             GROUP BY s.code, s.name ORDER BY s.code
            """);
        // EQUITY
        List<Map<String, Object>> equity = jdbc.queryForList("""
            SELECT s.code, s.name,
                   sum(CASE WHEN l.direction='CREDIT' THEN l.amount ELSE -l.amount END) AS balance
              FROM acc_gl_subjects s
              LEFT JOIN acc_gl_voucher_lines l ON l.subject_id = s.id
              LEFT JOIN acc_gl_vouchers v ON v.id = l.voucher_id AND v.status = 'POSTED'""" + filter + """
             WHERE s.category = 'EQUITY'
             GROUP BY s.code, s.name ORDER BY s.code
            """);
        BigDecimal totalA = sumBalance(assets);
        BigDecimal totalL = sumBalance(liabilities);
        BigDecimal totalE = sumBalance(equity);
        // flat data
        List<Map<String, Object>> flat = new java.util.ArrayList<>();
        for (Map<String, Object> r : assets) {
            Map<String, Object> row = new LinkedHashMap<>(r);
            row.put("group", "资产");
            flat.add(row);
        }
        flat.add(summary("资产合计", totalA, "—"));
        for (Map<String, Object> r : liabilities) {
            Map<String, Object> row = new LinkedHashMap<>(r);
            row.put("group", "负债");
            flat.add(row);
        }
        flat.add(summary("负债合计", totalL, "—"));
        for (Map<String, Object> r : equity) {
            Map<String, Object> row = new LinkedHashMap<>(r);
            row.put("group", "所有者权益");
            flat.add(row);
        }
        flat.add(summary("权益合计", totalE, "—"));
        flat.add(summary("差额", totalA.subtract(totalL.add(totalE)), "=="));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("data", flat);
        result.put("total", flat.size());
        result.put("asOf", asOf);
        result.put("totalAssets", totalA);
        result.put("totalLiabilities", totalL);
        result.put("totalEquity", totalE);
        result.put("balanced", totalA.compareTo(totalL.add(totalE)) == 0);
        return result;
    }

    private Map<String, Object> summary(String name, BigDecimal balance, String group) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("code", "");
        row.put("name", name);
        row.put("balance", balance);
        row.put("group", group);
        return row;
    }

    /** 现金流量表 = 经营/投资/融资三个类别的现金科目流水。 */
    @GetMapping("/cash-flow")
    public Map<String, Object> cashFlow(@RequestParam(required = false) String from,
                                         @RequestParam(required = false) String to) {
        validateDates(from, to);
        // 现金类科目：1001 库存现金, 1002 银行存款
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT v.the_date, v.voucher_no, v.description,
                   sum(CASE WHEN l.direction='DEBIT' THEN l.amount ELSE -l.amount END) AS net_flow,
                   v.source_type
              FROM acc_gl_vouchers v
              JOIN acc_gl_voucher_lines l ON l.voucher_id = v.id
              JOIN acc_gl_subjects s ON s.id = l.subject_id
             WHERE s.code IN ('1001','1002')
               AND v.status = 'POSTED'
               AND v.the_date >= ?::date AND v.the_date <= ?::date
             GROUP BY v.id, v.the_date, v.voucher_no, v.description, v.source_type
             HAVING sum(CASE WHEN l.direction='DEBIT' THEN l.amount ELSE -l.amount END) <> 0
             ORDER BY v.the_date
            """, from, to);
        BigDecimal totalInflow = rows.stream()
            .map(r -> (BigDecimal) r.get("net_flow"))
            .filter(v -> v.signum() > 0)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalOutflow = rows.stream()
            .map(r -> (BigDecimal) r.get("net_flow"))
            .filter(v -> v.signum() < 0)
            .reduce(BigDecimal.ZERO, BigDecimal::add).abs();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("period", Map.of("from", from, "to", to));
        result.put("data", rows);
        result.put("totalInflow", totalInflow);
        result.put("totalOutflow", totalOutflow);
        result.put("netCashFlow", totalInflow.subtract(totalOutflow));
        return result;
    }

    /** 试算平衡表 = 所有科目的借/贷汇总。 */
    @GetMapping("/trial-balance")
    public Map<String, Object> trialBalance(@RequestParam(required = false) String asOf) {
        String filter = asOf == null || asOf.isBlank() ? ""
            : " AND v.the_date <= '" + asOf + "'::date";
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT s.code, s.name, s.category,
                   coalesce(sum(CASE WHEN l.direction='DEBIT' THEN l.amount ELSE 0 END), 0) AS debit,
                   coalesce(sum(CASE WHEN l.direction='CREDIT' THEN l.amount ELSE 0 END), 0) AS credit
              FROM acc_gl_subjects s
              LEFT JOIN acc_gl_voucher_lines l ON l.subject_id = s.id
              LEFT JOIN acc_gl_vouchers v ON v.id = l.voucher_id AND v.status = 'POSTED'""" + filter + """
             GROUP BY s.code, s.name, s.category
             HAVING coalesce(sum(CASE WHEN l.direction='DEBIT' THEN l.amount ELSE 0 END), 0) <> 0
                 OR coalesce(sum(CASE WHEN l.direction='CREDIT' THEN l.amount ELSE 0 END), 0) <> 0
             ORDER BY s.code
            """);
        BigDecimal totalD = rows.stream().map(r -> (BigDecimal) r.get("debit"))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalC = rows.stream().map(r -> (BigDecimal) r.get("credit"))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("asOf", asOf);
        result.put("data", rows);
        result.put("totalDebit", totalD);
        result.put("totalCredit", totalC);
        result.put("balanced", totalD.compareTo(totalC) == 0);
        return result;
    }

    private void validateDates(String from, String to) {
        if (from == null || !from.matches("\\d{4}-\\d{2}-\\d{2}"))
            throw ApiException.badRequest("from 必须为 YYYY-MM-DD 格式");
        if (to == null || !to.matches("\\d{4}-\\d{2}-\\d{2}"))
            throw ApiException.badRequest("to 必须为 YYYY-MM-DD 格式");
        if (from.compareTo(to) > 0)
            throw ApiException.badRequest("from 不能晚于 to");
    }

    private BigDecimal sumBalance(List<Map<String, Object>> rows) {
        return rows.stream()
            .map(r -> r.get("balance") == null ? BigDecimal.ZERO : (BigDecimal) r.get("balance"))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}

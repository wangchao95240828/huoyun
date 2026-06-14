package com.xqt.saas.acc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 凭证 GL — 对齐 ACC 总账记账。
 *
 *   GET /api/acc/gl/subjects                  科目表
 *   GET /api/acc/gl/subjects/balances         科目余额（按方向汇总）
 *   POST /api/acc/gl/vouchers                 创建凭证（含 lines）
 *   POST /api/acc/gl/vouchers/{id}/post       过账（DRAFT→POSTED）
 *   GET /api/acc/gl/vouchers                  凭证列表
 */
@RestController
@RequestMapping("/api/acc/gl")
public class AccGlController {
    private final JdbcTemplate jdbc;

    public AccGlController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/subjects")
    public Map<String, Object> subjects() {
        List<Map<String, Object>> data = jdbc.queryForList("""
            SELECT id::text, code, name, category, level, is_balance_sheet, is_active
              FROM acc_gl_subjects WHERE is_active=true ORDER BY code
            """);
        return Map.of("data", data, "total", data.size());
    }

    @GetMapping("/subjects/balances")
    public Map<String, Object> balances() {
        List<Map<String, Object>> data = jdbc.queryForList(
            "SELECT * FROM v_gl_subject_balance ORDER BY code");
        return Map.of("data", data, "total", data.size());
    }

    @GetMapping("/vouchers")
    public Map<String, Object> listVouchers() {
        List<Map<String, Object>> data = jdbc.queryForList("""
            SELECT v.id::text, v.voucher_no, v.the_date, v.description,
                   v.source_type, v.source_id::text, v.status, v.audit_status,
                   v.audited_at, v.audit_name,
                   coalesce((SELECT sum(amount) FROM acc_gl_voucher_lines
                              WHERE voucher_id = v.id AND direction='DEBIT'), 0) AS debit_total
              FROM acc_gl_vouchers v ORDER BY v.the_date DESC, v.voucher_no DESC LIMIT 200
            """);
        return Map.of("data", data, "total", data.size());
    }

    @PostMapping("/vouchers")
    @Transactional(rollbackFor = Exception.class)
    @SuppressWarnings("unchecked")
    public Map<String, Object> createVoucher(@RequestBody Map<String, Object> body) {
        String voucherNo = (String) body.get("voucherNo");
        String theDate = (String) body.get("theDate");
        String description = (String) body.get("description");
        List<Map<String, Object>> lines = (List<Map<String, Object>>) body.getOrDefault("lines", List.of());
        if (voucherNo == null || voucherNo.isBlank()) throw ApiException.badRequest("凭证号必填");
        if (theDate == null) throw ApiException.badRequest("日期必填");
        if (lines.size() < 2) throw ApiException.badRequest("凭证至少需要 2 条分录");

        // ACC 期间锁拦截
        Boolean locked = jdbc.queryForObject(
            "SELECT acc_is_period_locked(?::date)", Boolean.class, theDate);
        if (Boolean.TRUE.equals(locked)) {
            throw ApiException.badRequest("该期间已锁定，无法新增凭证: " + theDate);
        }

        // 借贷平衡校验
        BigDecimal debit = BigDecimal.ZERO, credit = BigDecimal.ZERO;
        for (Map<String, Object> line : lines) {
            BigDecimal amt = new BigDecimal(line.get("amount").toString());
            if (amt.signum() <= 0) throw ApiException.badRequest("分录金额必须大于零");
            if ("DEBIT".equals(line.get("direction"))) debit = debit.add(amt);
            else if ("CREDIT".equals(line.get("direction"))) credit = credit.add(amt);
            else throw ApiException.badRequest("分录方向必须为 DEBIT 或 CREDIT");
        }
        if (debit.compareTo(credit) != 0) {
            throw ApiException.badRequest("借贷不平衡：DEBIT=" + debit + " CREDIT=" + credit);
        }

        String voucherId = jdbc.queryForObject("""
            INSERT INTO acc_gl_vouchers (voucher_no, the_date, description, status)
            VALUES (?, ?::date, ?, 'DRAFT')
            RETURNING id::text
            """, String.class, voucherNo, theDate, description);

        int lineNo = 1;
        for (Map<String, Object> line : lines) {
            jdbc.update("""
                INSERT INTO acc_gl_voucher_lines (voucher_id, subject_id, direction, amount, currency, remark, line_no)
                VALUES (?::uuid, ?::uuid, ?, ?::numeric, ?, ?, ?)
                """, voucherId, line.get("subjectId"), line.get("direction"),
                     line.get("amount"), line.getOrDefault("currency", "CNY"),
                     line.get("remark"), lineNo++);
        }
        return Map.of("id", voucherId, "voucherNo", voucherNo,
            "debitTotal", debit, "creditTotal", credit, "lineCount", lines.size());
    }

    @PostMapping("/vouchers/{id}/post")
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> post(@PathVariable String id) {
        Map<String, Object> v;
        try {
            v = jdbc.queryForMap("SELECT status, the_date FROM acc_gl_vouchers WHERE id=?::uuid", id);
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            throw ApiException.notFound("找不到该凭证");
        }
        if (!"DRAFT".equals(v.get("status"))) {
            throw ApiException.badRequest("凭证状态为 " + v.get("status") + "，无法过账");
        }
        Boolean locked = jdbc.queryForObject(
            "SELECT acc_is_period_locked(?::date)", Boolean.class, v.get("the_date"));
        if (Boolean.TRUE.equals(locked)) {
            throw ApiException.badRequest("该期间已锁定，无法过账");
        }
        jdbc.update("UPDATE acc_gl_vouchers SET status='POSTED' WHERE id=?::uuid", id);
        return Map.of("id", id, "posted", true);
    }
}

package com.xqt.saas.acc;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.xqt.saas.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SMS 自动核销 — 对齐 ACC 银行短信自动认款。
 *
 * 流程：
 *   1. 解析 raw_content 提取金额、付款人、账号
 *   2. 按 payer name 模糊匹配客户（contains/相似度）
 *   3. 按 amount 找未付/部分付 customer_invoice
 *   4. amount 精确匹配 → 自动 mark_paid
 *   5. amount 接近（±0.01 容差）→ 标 candidate 待人工确认
 *   6. 找不到匹配 → 转人工审核
 */
@Service
public class AccSmsReconcileService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AccSmsReconcileService.class);

    /** 常见银行短信里的金额提取正则。覆盖工行/招行/建行/中行/农行/交行通用格式。 */
    private static final Pattern[] AMOUNT_PATTERNS = {
        Pattern.compile("(?:转入|存入|收入|存款|进账|到账)([\\d,]+\\.?\\d*)\\s*元"),
        Pattern.compile("([\\d,]+\\.?\\d*)\\s*元.*?(?:转入|存入|收入|进账|到账)"),
        Pattern.compile("人民币([\\d,]+\\.?\\d*)\\s*元"),
        Pattern.compile("RMB([\\d,]+\\.?\\d*)"),
        Pattern.compile("CNY([\\d,]+\\.?\\d*)"),
        Pattern.compile("USD([\\d,]+\\.?\\d*)"),
    };

    /** 付款人提取（名字/公司名）。 */
    private static final Pattern[] PAYER_PATTERNS = {
        Pattern.compile("(?:付款方|来款人|对方户名|对方账户名)[:：]?\\s*([\\u4e00-\\u9fa5A-Za-z0-9\\s（）]+?)(?:[，。,]|$)"),
        Pattern.compile("(?:转入|存入)([\\u4e00-\\u9fa5A-Za-z]{2,30})\\s*\\d"),
        Pattern.compile("([\\u4e00-\\u9fa5]{2,4})\\s+(?:汇入|转入)"),
    };

    private final JdbcTemplate jdbc;

    public AccSmsReconcileService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 主入口：把一条 SMS parse 出结构化结果。 */
    public Map<String, Object> parseRaw(String rawContent) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("raw", rawContent);
        if (rawContent == null || rawContent.isBlank()) {
            result.put("ok", false);
            result.put("error", "raw_content 为空");
            return result;
        }

        BigDecimal amount = null;
        for (Pattern p : AMOUNT_PATTERNS) {
            Matcher m = p.matcher(rawContent);
            if (m.find()) {
                String numStr = m.group(1).replace(",", "");
                try {
                    amount = new BigDecimal(numStr);
                    break;
                } catch (NumberFormatException ex) { /* try next */ }
            }
        }
        result.put("parsedAmount", amount);

        String payer = null;
        for (Pattern p : PAYER_PATTERNS) {
            Matcher m = p.matcher(rawContent);
            if (m.find()) {
                payer = m.group(1).trim();
                break;
            }
        }
        result.put("parsedPayer", payer);

        // 推断币种
        String currency = "CNY";
        if (rawContent.matches(".*(USD|美元|US\\$).*")) currency = "USD";
        else if (rawContent.matches(".*(EUR|欧元|€).*")) currency = "EUR";
        else if (rawContent.matches(".*(HKD|港币).*")) currency = "HKD";
        result.put("parsedCurrency", currency);

        result.put("ok", amount != null);
        return result;
    }

    /** 把 acc_received_sms 表一条记录 → 自动匹配客户和发票 → 落账。 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> reconcile(String smsId) {
        Map<String, Object> sms;
        try {
            sms = jdbc.queryForMap("""
                SELECT raw_content, amount, currency, payer, sms_time
                  FROM acc_received_sms WHERE id = ?::uuid
                """, smsId);
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            throw ApiException.notFound("找不到该短信记录");
        }
        String rawContent = (String) sms.get("raw_content");
        BigDecimal amount = (BigDecimal) sms.get("amount");
        String currency = (String) sms.get("currency");
        String payer = (String) sms.get("payer");

        // 如果 payer/amount 为空，尝试从 raw 重新 parse
        if (payer == null || amount == null || amount.signum() <= 0) {
            Map<String, Object> parsed = parseRaw(rawContent);
            if (Boolean.TRUE.equals(parsed.get("ok"))) {
                if (amount == null || amount.signum() <= 0) amount = (BigDecimal) parsed.get("parsedAmount");
                if (payer == null || payer.isBlank()) payer = (String) parsed.get("parsedPayer");
                if (currency == null) currency = (String) parsed.get("parsedCurrency");
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("smsId", smsId);
        result.put("amount", amount);
        result.put("currency", currency);
        result.put("payer", payer);

        if (payer == null || payer.isBlank()) {
            result.put("status", "PENDING_MANUAL");
            result.put("reason", "无法识别付款人");
            return result;
        }
        if (amount == null || amount.signum() <= 0) {
            result.put("status", "PENDING_MANUAL");
            result.put("reason", "无法识别金额");
            return result;
        }

        // 模糊匹配客户：name ILIKE %payer% OR payer ILIKE %name%
        List<Map<String, Object>> customerCandidates = jdbc.queryForList("""
            SELECT id::text, code, name FROM customers
             WHERE name ILIKE ? OR ? ILIKE concat('%', name, '%')
             LIMIT 5
            """, "%" + payer + "%", payer);
        result.put("customerCandidates", customerCandidates);

        if (customerCandidates.isEmpty()) {
            result.put("status", "PENDING_MANUAL");
            result.put("reason", "找不到匹配客户");
            return result;
        }
        if (customerCandidates.size() > 1) {
            result.put("status", "PENDING_MANUAL");
            result.put("reason", "找到 " + customerCandidates.size() + " 个客户候选，需人工确认");
            return result;
        }

        String customerId = (String) customerCandidates.get(0).get("id");
        result.put("matchedCustomer", customerCandidates.get(0));

        // 找未付/部分付 invoice — 金额精确匹配优先
        List<Map<String, Object>> invoices = jdbc.queryForList("""
            SELECT id::text, invoice_no, total_amount, paid_amount, currency,
                   (total_amount - coalesce(paid_amount, 0)) AS unpaid
              FROM customer_invoices
             WHERE customer_id = ?::uuid AND currency = ?
               AND status IN ('SENT','PENDING','PARTIAL_PAID')
             ORDER BY abs((total_amount - coalesce(paid_amount, 0)) - ?) ASC
             LIMIT 3
            """, customerId, currency, amount);

        if (invoices.isEmpty()) {
            result.put("status", "PENDING_MANUAL");
            result.put("reason", "客户 " + customerCandidates.get(0).get("code")
                + " 无 " + currency + " 未付账单");
            return result;
        }

        // 取第一条（金额最接近）
        Map<String, Object> bestMatch = invoices.get(0);
        BigDecimal unpaid = (BigDecimal) bestMatch.get("unpaid");
        BigDecimal diff = unpaid.subtract(amount).abs();

        if (diff.compareTo(new BigDecimal("0.01")) <= 0) {
            // 精确匹配 — 自动核销
            String invoiceId = (String) bestMatch.get("id");
            jdbc.update("""
                UPDATE customer_invoices
                   SET paid_amount = coalesce(paid_amount, 0) + ?,
                       status = CASE
                         WHEN (coalesce(paid_amount, 0) + ?) >= total_amount THEN 'PAID'
                         ELSE 'PARTIAL_PAID'
                       END
                 WHERE id = ?::uuid
                """, amount, amount, invoiceId);
            // 写收款记录
            jdbc.update("""
                INSERT INTO acc_received_sms_reconcile_log (sms_id, invoice_id, amount, status, matched_at)
                VALUES (?::uuid, ?::uuid, ?, 'AUTO', now())
                ON CONFLICT DO NOTHING
                """, smsId, invoiceId, amount);
            // 标 SMS 已核销
            jdbc.update("UPDATE acc_received_sms SET audit_status = 'AUDITED', audited_at = now() WHERE id = ?::uuid",
                smsId);
            result.put("status", "MATCHED_AUTO");
            result.put("invoiceNo", bestMatch.get("invoice_no"));
            result.put("invoiceId", invoiceId);
            result.put("appliedAmount", amount);
            LOGGER.info("SMS {} auto-matched to invoice {} for amount {}", smsId, invoiceId, amount);
            return result;
        }
        // 接近但不精确 — 候选待确认
        result.put("status", "PENDING_MANUAL");
        result.put("reason", "金额差额 " + diff + " 超阈值，需人工确认");
        result.put("topCandidate", bestMatch);
        return result;
    }
}

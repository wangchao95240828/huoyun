package com.xqt.saas.acc;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
 * 税率管理 + 税额计算 — 对齐 ACC 税务处理。
 *
 *   GET  /api/acc/tax/rates                  税率列表
 *   POST /api/acc/tax/rates                  新增税率
 *   POST /api/acc/tax/calc                   计算税额 body: { amount, taxCode, type }
 *   GET  /api/acc/tax/summary?period=YYYY-MM 月度税额汇总
 */
@RestController
@RequestMapping("/api/acc/tax")
public class AccTaxController {
    private final JdbcTemplate jdbc;

    public AccTaxController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/rates")
    public Map<String, Object> rates() {
        ensureTable();
        List<Map<String, Object>> data = jdbc.queryForList("""
            SELECT id::text, code, name, rate, tax_type, country_code,
                   effective_from, effective_to, is_active
              FROM acc_tax_rates ORDER BY country_code, tax_type, code
            """);
        return Map.of("data", data, "total", data.size());
    }

    @PostMapping("/rates")
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createRate(@RequestBody Map<String, Object> body) {
        ensureTable();
        String code = (String) body.get("code");
        String name = (String) body.get("name");
        String taxType = (String) body.get("taxType");
        Object rateRaw = body.get("rate");
        String country = (String) body.get("countryCode");
        if (code == null || code.isBlank()) throw ApiException.badRequest("税率代码必填");
        if (name == null || name.isBlank()) throw ApiException.badRequest("税率名称必填");
        if (!List.of("VAT","SALES_TAX","GST","CUSTOMS_DUTY","INCOME_TAX","WITHHOLDING").contains(taxType)) {
            throw ApiException.badRequest("taxType 必须为 VAT/SALES_TAX/GST/CUSTOMS_DUTY/INCOME_TAX/WITHHOLDING");
        }
        BigDecimal rate;
        try { rate = new BigDecimal(rateRaw.toString()); }
        catch (Exception ex) { throw ApiException.badRequest("rate 必填且为数字"); }
        if (rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
            throw ApiException.badRequest("rate 必须在 0-1 (0%-100%) 之间");
        }
        if (country != null && !country.isBlank() && !country.matches("[A-Z]{2}")) {
            throw ApiException.badRequest("countryCode 必须为 ISO alpha-2");
        }
        try {
            String id = jdbc.queryForObject("""
                INSERT INTO acc_tax_rates (code, name, rate, tax_type, country_code, effective_from)
                VALUES (?, ?, ?, ?, ?, current_date)
                RETURNING id::text
                """, String.class, code, name, rate, taxType, country);
            return Map.of("id", id, "code", code);
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            throw ApiException.badRequest("税率代码已存在: " + code);
        }
    }

    @PostMapping("/calc")
    public Map<String, Object> calc(@RequestBody Map<String, Object> body) {
        Object amountRaw = body.get("amount");
        String taxCode = (String) body.get("taxCode");
        String includesTax = body.getOrDefault("includesTax", "false").toString();
        if (amountRaw == null) throw ApiException.badRequest("amount 必填");
        if (taxCode == null || taxCode.isBlank()) throw ApiException.badRequest("taxCode 必填");
        BigDecimal amount;
        try { amount = new BigDecimal(amountRaw.toString()); }
        catch (Exception ex) { throw ApiException.badRequest("amount 必须为数字"); }

        BigDecimal rate;
        try {
            rate = jdbc.queryForObject(
                "SELECT rate FROM acc_tax_rates WHERE code = ? AND is_active = true LIMIT 1",
                BigDecimal.class, taxCode);
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            throw ApiException.notFound("找不到税率代码: " + taxCode);
        }

        BigDecimal taxable, tax, total;
        if ("true".equalsIgnoreCase(includesTax)) {
            // 价税合计反算
            total = amount;
            taxable = total.divide(BigDecimal.ONE.add(rate), 2, RoundingMode.HALF_UP);
            tax = total.subtract(taxable);
        } else {
            taxable = amount;
            tax = taxable.multiply(rate).setScale(2, RoundingMode.HALF_UP);
            total = taxable.add(tax);
        }
        return Map.of(
            "taxCode", taxCode,
            "rate", rate,
            "taxableAmount", taxable,
            "taxAmount", tax,
            "totalAmount", total
        );
    }

    @GetMapping("/summary")
    public Map<String, Object> summary(@RequestParam String period) {
        if (!period.matches("\\d{4}-\\d{2}")) {
            throw ApiException.badRequest("period 必须为 YYYY-MM 格式");
        }
        // 这里仅是占位 — 实际生产需要在 invoice/payment 时落 tax_records 表，
        // 然后按 period 汇总。这里返回基本统计
        String from = period + "-01";
        String to = period + "-31";
        BigDecimal arTax = jdbc.queryForObject("""
            SELECT coalesce(sum((metadata->>'tax_amount')::numeric), 0)
              FROM customer_invoices
             WHERE issued_at >= ?::date AND issued_at <= ?::date
            """, BigDecimal.class, from, to);
        return Map.of("period", period, "arTax", arTax,
            "note", "占位实现 — 生产需建 tax_records 表");
    }

    private void ensureTable() {
        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS acc_tax_rates (
                    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                    tenant_id     uuid NOT NULL DEFAULT '2bda8c16-7b19-4ce6-ab71-9584f5a140ed'::uuid,
                    code          text NOT NULL,
                    name          text NOT NULL,
                    rate          numeric(7,4) NOT NULL,
                    tax_type      text NOT NULL,
                    country_code  text,
                    effective_from date NOT NULL DEFAULT current_date,
                    effective_to   date,
                    is_active     boolean NOT NULL DEFAULT true,
                    created_at    timestamptz NOT NULL DEFAULT now(),
                    CONSTRAINT acc_tax_rates_tenant_code_unique UNIQUE (tenant_id, code)
                )
                """);
        } catch (Exception ignored) {}
    }
}

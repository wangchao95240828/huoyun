package com.xqt.saas.acc;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * 客户账户 — 临时额度管理 + 余额查询.
 *
 * 业务公式:
 *   可用余额 = 总付款额 (CREDIT inflows)
 *           + 临时额度 (admin 手动设定)
 *           - 使用金额 (DEBIT outflows, 客户欠款)
 *
 * 接口:
 *   GET    /api/acc/customer-accounts                    所有客户账户清单
 *   GET    /api/acc/customer-accounts/{customerId}       单客户全币种账户 + 临时额度
 *   PUT    /api/acc/customer-accounts/{customerId}/temporary-credit  设定/调整临时额度
 *   GET    /api/acc/customer-accounts/{customerId}/ledger 该客户余额变动流水
 */
@RestController
@RequestMapping("/api/acc/customer-accounts")
public class AccCustomerAccountController {
    private final JdbcTemplate jdbc;

    public AccCustomerAccountController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 列表: 所有客户全币种余额账户. */
    @GetMapping
    public Map<String, Object> list(
        @RequestParam(required = false) String customerCode,
        @RequestParam(required = false) String currency,
        @RequestParam(required = false, defaultValue = "20") Integer pageSize,
        @RequestParam(required = false, defaultValue = "1") Integer page) {

        StringBuilder where = new StringBuilder(" WHERE fa.owner_type = 'CUSTOMER' ");
        java.util.List<Object> args = new java.util.ArrayList<>();
        if (customerCode != null && !customerCode.isBlank()) {
            where.append(" AND cu.code = ? ");
            args.add(customerCode);
        }
        if (currency != null && !currency.isBlank()) {
            where.append(" AND fa.currency = ? ");
            args.add(currency);
        }

        int limit = Math.min(pageSize == null ? 20 : pageSize, 500);
        int offset = (Math.max(page == null ? 1 : page, 1) - 1) * limit;

        // 余额 + 临时额度 + 累计付款 (CREDIT) + 累计使用 (DEBIT) 真实计算
        String sql = """
            SELECT fa.id::text AS account_id,
                   cu.id::text AS customer_id,
                   cu.code AS customer_code,
                   cu.name AS customer_name,
                   fa.currency,
                   fa.balance,
                   fa.temporary_credit,
                   COALESCE((SELECT SUM(amount) FROM balance_ledger WHERE account_id = fa.id AND direction = 'CREDIT'::balance_ledger_direction), 0) AS total_paid,
                   COALESCE((SELECT SUM(amount) FROM balance_ledger WHERE account_id = fa.id AND direction = 'DEBIT'::balance_ledger_direction), 0) AS total_used,
                   (fa.balance + fa.temporary_credit) AS available_credit
              FROM financial_accounts fa
              JOIN customers cu ON cu.id = fa.owner_id
            """ + where + " ORDER BY cu.code, fa.currency LIMIT ? OFFSET ?";
        args.add(limit);
        args.add(offset);

        List<Map<String, Object>> rows = jdbc.queryForList(sql, args.toArray());

        Long total = jdbc.queryForObject(
            "SELECT COUNT(*) FROM financial_accounts fa JOIN customers cu ON cu.id = fa.owner_id "
            + where, Long.class,
            args.subList(0, args.size() - 2).toArray());

        return Map.of("data", rows, "total", total == null ? 0 : total);
    }

    /** 单客户全币种账户. */
    @GetMapping("/{customerId}")
    public Map<String, Object> get(@PathVariable String customerId) {
        List<Map<String, Object>> accounts = jdbc.queryForList("""
            SELECT fa.id::text AS account_id,
                   fa.currency,
                   fa.balance,
                   fa.temporary_credit,
                   COALESCE((SELECT SUM(amount) FROM balance_ledger WHERE account_id = fa.id AND direction = 'CREDIT'::balance_ledger_direction), 0) AS total_paid,
                   COALESCE((SELECT SUM(amount) FROM balance_ledger WHERE account_id = fa.id AND direction = 'DEBIT'::balance_ledger_direction), 0) AS total_used,
                   (fa.balance + fa.temporary_credit) AS available_credit
              FROM financial_accounts fa
             WHERE fa.owner_type = 'CUSTOMER' AND fa.owner_id = ?::uuid
             ORDER BY fa.currency
            """, customerId);

        Map<String, Object> customer;
        try {
            customer = jdbc.queryForMap(
                "SELECT id::text, code, name FROM customers WHERE id = ?::uuid", customerId);
        } catch (org.springframework.dao.DataAccessException ex) {
            throw ApiException.notFound("找不到客户: " + customerId);
        }
        return Map.of("customer", customer, "accounts", accounts);
    }

    /**
     * 设置临时额度. admin 手动垫资场景:
     *   - 客户没付款 / 欠款情况, 临时给个额度让他能继续下单
     *   - PUT /api/acc/customer-accounts/{customerId}/temporary-credit
     *     body: { currency: "USD", amount: 500, remark: "客户预付到账前临时垫资" }
     *
     * 行为:
     *   1. 找/建该 (customer × currency) 的 financial_accounts 行
     *   2. 计算 delta = newAmount - oldTempCredit
     *   3. UPDATE financial_accounts SET temporary_credit = newAmount, balance = balance + delta
     *   4. 写 balance_ledger (biz_type='TEMP_CREDIT')
     */
    @PutMapping("/{customerId}/temporary-credit")
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> setTemporaryCredit(@PathVariable String customerId,
                                                   @RequestBody Map<String, Object> body) {
        String currency = (String) body.get("currency");
        if (currency == null || currency.isBlank()) {
            throw ApiException.badRequest("currency 必填 (USD/CNY/EUR...)");
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(body.get("amount").toString());
        } catch (Exception e) {
            throw ApiException.badRequest("amount 必填且必须为数字");
        }
        if (amount.signum() < 0) {
            throw ApiException.badRequest("amount 不能为负数 (取消额度请填 0)");
        }
        String remark = body.get("remark") == null ? "管理员手动调整临时额度" : body.get("remark").toString();

        String tenantId = jdbc.queryForObject(
            "SELECT current_setting('app.current_tenant_id')", String.class);

        // 找/建该客户该币种的 financial_accounts
        String accountId;
        BigDecimal oldCredit;
        BigDecimal oldBalance;
        List<Map<String, Object>> existing = jdbc.queryForList("""
            SELECT id::text, balance, temporary_credit
              FROM financial_accounts
             WHERE owner_type = 'CUSTOMER' AND owner_id = ?::uuid AND currency = ?
             LIMIT 1
            """, customerId, currency);

        if (existing.isEmpty()) {
            // 新建
            accountId = jdbc.queryForObject("""
                INSERT INTO financial_accounts (
                    tenant_id, owner_type, owner_id, account_name,
                    account_type, currency, balance, temporary_credit, source, is_show
                ) VALUES (
                    ?::uuid, 'CUSTOMER', ?::uuid, '客户预扣账户',
                    'CASH', ?, ?, ?, 'LOCAL', true
                ) RETURNING id::text
                """, String.class, tenantId, customerId, currency, amount, amount);
            oldCredit = BigDecimal.ZERO;
            oldBalance = BigDecimal.ZERO;
        } else {
            Map<String, Object> row = existing.get(0);
            accountId = (String) row.get("id");
            oldCredit = (BigDecimal) row.get("temporary_credit");
            oldBalance = (BigDecimal) row.get("balance");
            if (oldCredit == null) oldCredit = BigDecimal.ZERO;
            BigDecimal delta = amount.subtract(oldCredit);
            BigDecimal newBalance = oldBalance.add(delta);
            jdbc.update("""
                UPDATE financial_accounts
                   SET temporary_credit = ?, balance = ?
                 WHERE id = ?::uuid
                """, amount, newBalance, accountId);
        }

        BigDecimal delta = amount.subtract(oldCredit);
        String direction = delta.signum() >= 0 ? "CREDIT" : "DEBIT";
        BigDecimal absAmount = delta.abs();

        // 写 balance_ledger
        BigDecimal balBefore = oldBalance;
        BigDecimal balAfter = oldBalance.add(delta);
        if (delta.signum() != 0) {
            jdbc.update("""
                INSERT INTO balance_ledger (
                    account_id, owner_type, owner_id, biz_type, source_type, source_id, source_ref,
                    currency, direction, amount, balance_before, balance_after, operator, remark
                ) VALUES (
                    ?::uuid, 'CUSTOMER', ?::uuid, 'TEMP_CREDIT'::balance_ledger_biz_type,
                    'temporary_credit_adjust', NULL, ?,
                    ?, ?::balance_ledger_direction, ?, ?, ?, current_user, ?
                )
                """, accountId, customerId,
                "TEMP_CREDIT:" + oldCredit + "->" + amount,
                currency, direction, absAmount, balBefore, balAfter, remark);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("accountId", accountId);
        result.put("customerId", customerId);
        result.put("currency", currency);
        result.put("oldTemporaryCredit", oldCredit);
        result.put("newTemporaryCredit", amount);
        result.put("delta", delta);
        result.put("newBalance", balAfter);
        return result;
    }

    /** 单客户余额变动流水. */
    @GetMapping("/{customerId}/ledger")
    public Map<String, Object> ledger(@PathVariable String customerId,
                                       @RequestParam(required = false) String currency,
                                       @RequestParam(required = false, defaultValue = "50") Integer pageSize) {
        StringBuilder where = new StringBuilder(" WHERE bl.owner_type = 'CUSTOMER' AND bl.owner_id = ?::uuid ");
        java.util.List<Object> args = new java.util.ArrayList<>();
        args.add(customerId);
        if (currency != null && !currency.isBlank()) {
            where.append(" AND bl.currency = ? ");
            args.add(currency);
        }
        args.add(Math.min(pageSize == null ? 50 : pageSize, 500));

        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT bl.id::text,
                   bl.currency,
                   bl.direction::text AS direction,
                   bl.biz_type::text AS biz_type,
                   bl.amount,
                   bl.balance_before,
                   bl.balance_after,
                   bl.source_type,
                   bl.source_ref,
                   bl.remark,
                   bl.operator,
                   bl.created_at
              FROM balance_ledger bl
            """ + where + " ORDER BY bl.created_at DESC LIMIT ?", args.toArray());

        return Map.of("data", rows);
    }
}

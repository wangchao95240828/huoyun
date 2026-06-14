package com.xqt.saas.acc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.xqt.saas.common.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 期初余额管理 — 新系统启用 / 年初结转用。
 *
 *   POST /api/acc/opening-balance/account  body: {accountId, amount, asOf, remark}
 *   POST /api/acc/opening-balance/customer body: {customerId, amount, currency, asOf, remark}
 *   GET  /api/acc/opening-balance          列出所有期初记录
 *
 * 所有期初余额都会写一条 balance_ledger 标 source_type='OPENING_BALANCE'。
 */
@RestController
@RequestMapping("/api/acc/opening-balance")
public class AccOpeningBalanceController {
    private final JdbcTemplate jdbc;

    public AccOpeningBalanceController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostMapping("/account")
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> setAccountOpening(@RequestBody Map<String, Object> body) {
        String accountId = (String) body.get("accountId");
        Object amountRaw = body.get("amount");
        String asOf = body.get("asOf") == null ? null : body.get("asOf").toString();
        String remark = body.get("remark") == null ? "期初余额" : body.get("remark").toString();

        if (accountId == null || accountId.isBlank()) {
            throw ApiException.badRequest("accountId 必填");
        }
        BigDecimal amount;
        try { amount = new BigDecimal(amountRaw.toString()); }
        catch (Exception ex) { throw ApiException.badRequest("amount 必填且为数字"); }

        Map<String, Object> acc;
        try {
            acc = jdbc.queryForMap(
                "SELECT currency, balance, owner_type FROM financial_accounts WHERE id = ?::uuid",
                accountId);
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            throw ApiException.notFound("找不到该资金账户");
        }
        String currency = (String) acc.get("currency");
        BigDecimal currentBalance = (BigDecimal) acc.get("balance");

        // 直接覆盖余额（覆盖而非累加，因为是"期初"）
        jdbc.update("UPDATE financial_accounts SET balance = ?, last_update = now() WHERE id = ?::uuid",
            amount, accountId);

        // 写 ledger
        BigDecimal diff = amount.subtract(currentBalance);
        String direction = diff.signum() >= 0 ? "CREDIT" : "DEBIT";
        jdbc.execute("SELECT set_config('app.current_tenant_id', '2bda8c16-7b19-4ce6-ab71-9584f5a140ed', true)");
        jdbc.update("""
            INSERT INTO balance_ledger (
              account_id, owner_type, owner_id, biz_type,
              source_type, source_ref,
              currency, direction, amount, balance_before, balance_after, operator, remark
            ) VALUES (
              ?::uuid, ?, NULL, 'ADJUST'::balance_ledger_biz_type,
              'OPENING_BALANCE', ?,
              ?, ?::balance_ledger_direction, ?, ?, ?, current_user, ?
            )
            """, accountId, acc.get("owner_type"), asOf, currency,
                 direction, diff.abs(), currentBalance, amount,
                 remark + (asOf != null ? " (as of " + asOf + ")" : ""));

        return Map.of(
            "accountId", accountId,
            "previousBalance", currentBalance,
            "newBalance", amount,
            "adjustment", diff
        );
    }

    @PostMapping("/customer")
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> setCustomerOpening(@RequestBody Map<String, Object> body) {
        String customerId = (String) body.get("customerId");
        String currency = body.getOrDefault("currency", "USD").toString();
        Object amountRaw = body.get("amount");
        String asOf = body.get("asOf") == null ? null : body.get("asOf").toString();

        if (customerId == null) throw ApiException.badRequest("customerId 必填");
        if (currency.length() != 3) throw ApiException.badRequest("currency 必须 3 字母");
        BigDecimal amount;
        try { amount = new BigDecimal(amountRaw.toString()); }
        catch (Exception ex) { throw ApiException.badRequest("amount 必填且为数字"); }

        // 找/建客户影子账户
        jdbc.execute("SELECT set_config('app.current_tenant_id', '2bda8c16-7b19-4ce6-ab71-9584f5a140ed', true)");
        List<String> accIds = jdbc.queryForList("""
            SELECT id::text FROM financial_accounts
             WHERE owner_type='CUSTOMER' AND owner_id = ?::uuid AND currency = ?
             LIMIT 1
            """, String.class, customerId, currency);
        String accountId;
        if (accIds.isEmpty()) {
            accountId = jdbc.queryForObject("""
                INSERT INTO financial_accounts (tenant_id, owner_type, owner_id, account_name,
                                                account_type, currency, balance, source, is_show)
                VALUES (current_setting('app.current_tenant_id')::uuid, 'CUSTOMER', ?::uuid,
                        '客户期初账户', 'CASH', ?, ?, 'LOCAL', true)
                RETURNING id::text
                """, String.class, customerId, currency, amount);
            // 写 ledger
            jdbc.update("""
                INSERT INTO balance_ledger (
                  account_id, owner_type, owner_id, biz_type,
                  source_type, source_ref,
                  currency, direction, amount, balance_before, balance_after, operator, remark
                ) VALUES (
                  ?::uuid, 'CUSTOMER', ?::uuid, 'ADJUST'::balance_ledger_biz_type,
                  'OPENING_BALANCE', ?,
                  ?, 'CREDIT'::balance_ledger_direction, ?, 0, ?, current_user, ?
                )
                """, accountId, customerId, asOf, currency, amount, amount,
                     "客户期初余额" + (asOf != null ? " (as of " + asOf + ")" : ""));
        } else {
            accountId = accIds.get(0);
            BigDecimal currentBalance = jdbc.queryForObject(
                "SELECT balance FROM financial_accounts WHERE id = ?::uuid",
                BigDecimal.class, accountId);
            jdbc.update("UPDATE financial_accounts SET balance = ? WHERE id = ?::uuid",
                amount, accountId);
            BigDecimal diff = amount.subtract(currentBalance);
            jdbc.update("""
                INSERT INTO balance_ledger (
                  account_id, owner_type, owner_id, biz_type,
                  source_type, source_ref,
                  currency, direction, amount, balance_before, balance_after, operator, remark
                ) VALUES (
                  ?::uuid, 'CUSTOMER', ?::uuid, 'ADJUST'::balance_ledger_biz_type,
                  'OPENING_BALANCE', ?,
                  ?, ?::balance_ledger_direction, ?, ?, ?, current_user, ?
                )
                """, accountId, customerId, asOf, currency,
                     diff.signum() >= 0 ? "CREDIT" : "DEBIT", diff.abs(),
                     currentBalance, amount, "客户期初余额修正");
        }
        return Map.of("accountId", accountId, "amount", amount, "currency", currency);
    }

    @GetMapping
    public Map<String, Object> list() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT l.id::text, l.account_id::text, l.owner_type, l.owner_id::text,
                   l.currency, l.direction::text, l.amount, l.remark, l.created_at,
                   l.source_ref AS as_of
              FROM balance_ledger l
             WHERE l.source_type = 'OPENING_BALANCE'
             ORDER BY l.created_at DESC LIMIT 200
            """);
        return Map.of("data", rows, "total", rows.size());
    }
}

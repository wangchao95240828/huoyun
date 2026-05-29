package com.xqt.saas.acc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * acc_finance_txns 审核副作用：审核入账 / 反审冲正 写资金流水。
 */
class FinanceTxnAuditSideEffectTest {
    private static final String TENANT = "tenant-1";
    private JdbcTemplate jdbc;
    private com.xqt.saas.finance.FxSnapshotCapture fxCapture;
    private FinanceTxnAuditSideEffect effect;

    @BeforeEach
    void setup() {
        jdbc = mock(JdbcTemplate.class);
        fxCapture = mock(com.xqt.saas.finance.FxSnapshotCapture.class);
        effect = new FinanceTxnAuditSideEffect(jdbc, fxCapture);
    }

    /** queryForMap 真实返回的 row 含 null 值，Map.of 不允许 null，故用 HashMap。 */
    private Map<String, Object> txn(String side, String txnType, String customerId,
                                    String partnerId, BigDecimal amount, String txnNo) {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("side", side);
        m.put("txn_type", txnType);
        m.put("customer_id", customerId);
        m.put("partner_id", partnerId);
        m.put("amount", amount);
        m.put("currency", "CNY");
        m.put("txn_no", txnNo);
        return m;
    }

    @Test
    void supportsOnlyFinanceTxns() {
        assertThat(effect.supports("acc_finance_txns")).isTrue();
        assertThat(effect.supports("charges")).isFalse();
    }

    // 审核客户调账（+200）→ 客户账户 +200，写 ADJUST/CREDIT 流水
    @Test
    void auditCustomerAdjustWritesCreditLedger() {
        // 1) 查 txn
        when(jdbc.queryForMap(contains("FROM acc_finance_txns"), eq("txn-1"))).thenReturn(txn("CUSTOMER", "ADJUST", "cust-1", null, new BigDecimal("200.00"), "ADJ-001"));
        // 2) 找账户
        when(jdbc.queryForObject(contains("FROM financial_accounts"), eq(String.class),
            eq("CUSTOMER"), eq("cust-1"), eq("CNY"), eq("CNY"), eq("CNY"))).thenReturn("acct-c");
        // 3) 读余额
        when(jdbc.queryForObject(contains("SELECT balance"), eq(BigDecimal.class), eq("acct-c")))
            .thenReturn(new BigDecimal("1000"));

        effect.onAudited("acc_finance_txns", "txn-1", TENANT, "admin");

        // 余额 +200
        verify(jdbc, times(1)).update(contains("UPDATE financial_accounts"),
            eq(new BigDecimal("200.00")), eq("acct-c"));
        // 写流水：13 个占位符，验 biz=ADJUST(5)、direction=CREDIT(8)、before/after(10,11)
        verify(jdbc, times(1)).update(contains("INSERT INTO balance_ledger"),
            any(), any(), any(), any(),          // tenant, account, owner_type, owner_id
            eq("ADJUST"),                         // biz_type
            any(), any(),                         // source_ref, currency
            eq("CREDIT"),                         // direction
            eq(new BigDecimal("200.00")),         // amount(=delta.abs)
            eq(new BigDecimal("1000")), eq(new BigDecimal("1200.00")), // before, after
            any(), any());                        // operator, remark
    }

    // 反审客户调账 → 取反冲正，余额 -200，写 VOID/DEBIT
    @Test
    void undoCustomerAdjustWritesReversal() {
        when(jdbc.queryForMap(contains("FROM acc_finance_txns"), eq("txn-1"))).thenReturn(txn("CUSTOMER", "ADJUST", "cust-1", null, new BigDecimal("200.00"), "ADJ-001"));
        when(jdbc.queryForObject(contains("FROM financial_accounts"), eq(String.class),
            any(), any(), any(), any(), any())).thenReturn("acct-c");
        when(jdbc.queryForObject(contains("SELECT balance"), eq(BigDecimal.class), eq("acct-c")))
            .thenReturn(new BigDecimal("1200"));

        effect.onUndone("acc_finance_txns", "txn-1", TENANT, "admin");

        // 冲正：余额 -200
        verify(jdbc, times(1)).update(contains("UPDATE financial_accounts"),
            eq(new BigDecimal("-200.00")), eq("acct-c"));
        // 冲正流水：biz=VOID(5)、direction=DEBIT(8)
        verify(jdbc, times(1)).update(contains("INSERT INTO balance_ledger"),
            any(), any(), any(), any(),
            eq("VOID"),
            any(), any(),
            eq("DEBIT"),
            eq(new BigDecimal("200.00")),
            eq(new BigDecimal("1200")), eq(new BigDecimal("1000.00")),
            any(), any());
    }

    // 无资金账户 → 跳过流水（不阻断审核）
    @Test
    void skipsWhenNoOwnerAccount() {
        when(jdbc.queryForMap(contains("FROM acc_finance_txns"), eq("txn-1"))).thenReturn(txn("CUSTOMER", "REFUND", "cust-1", null, new BigDecimal("50"), "REF-001"));
        when(jdbc.queryForObject(contains("FROM financial_accounts"), eq(String.class),
            any(), any(), any(), any(), any()))
            .thenThrow(new org.springframework.dao.EmptyResultDataAccessException(1));

        effect.onAudited("acc_finance_txns", "txn-1", TENANT, "admin");

        verify(jdbc, never()).update(contains("INSERT INTO balance_ledger"), (Object[]) any());
    }

    // 金额为 0 → 不动账
    @Test
    void skipsWhenZeroAmount() {
        when(jdbc.queryForMap(contains("FROM acc_finance_txns"), eq("txn-1"))).thenReturn(txn("CUSTOMER", "ADJUST", "cust-1", null, BigDecimal.ZERO, "ADJ-0"));

        effect.onAudited("acc_finance_txns", "txn-1", TENANT, "admin");

        verify(jdbc, never()).update(anyString(), (Object[]) any());
    }

    // 任务 S6 收口：审核入账写 balance_ledger 后必须调 fxCapture（不漏 4 类 biz_type）
    @Test
    void auditCallsFxCaptureAfterLedgerWrite() {
        when(jdbc.queryForMap(contains("FROM acc_finance_txns"), eq("txn-x")))
            .thenReturn(txn("CUSTOMER", "REFUND", "cust-1", null, new BigDecimal("99.00"), "RFD-001"));
        // findOwnerAccount: 5 args (side, ownerId, currency, currency, currency)
        when(jdbc.queryForObject(contains("financial_accounts"), eq(String.class),
            any(Object[].class))).thenReturn("acct-1");
        // findAccountBalance: 1 arg
        when(jdbc.queryForObject(contains("SELECT balance FROM financial_accounts"),
            eq(BigDecimal.class), anyString())).thenReturn(new BigDecimal("1000.00"));

        effect.onAudited("acc_finance_txns", "txn-x", TENANT, "admin");

        verify(fxCapture, times(1)).captureForLedger(
            eq(TENANT), eq("CNY"), eq("REFUND"), eq("acc_finance_txns"), eq("RFD-001"));
    }
}

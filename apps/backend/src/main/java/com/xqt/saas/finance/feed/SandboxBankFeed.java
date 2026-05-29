package com.xqt.saas.finance.feed;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

/**
 * 默认 sandbox 银行对账 feed：返回 3 条虚拟流水，让对账模块端到端可跑。
 *
 * 真实 ICBC / CMB / 中行 adapter 实现时只需另写一个 {@link BankReconciliationFeed} bean
 * 并通过配置选择激活（参考 {@link com.xqt.saas.customerapi.CarrierGatewayRegistry} 模式）。
 */
@Component
public class SandboxBankFeed implements BankReconciliationFeed {
    private static final AtomicLong SEQ = new AtomicLong(1);

    @Override
    public String code() {
        return "SANDBOX";
    }

    @Override
    public List<BankStatementEntry> pullStatement(String tenantId, String accountNo,
                                                    LocalDate dateFrom, LocalDate dateTo) {
        if (accountNo == null || accountNo.isBlank()) return List.of();
        OffsetDateTime baseTime = dateFrom == null
            ? OffsetDateTime.now() : dateFrom.atStartOfDay().atZone(java.time.ZoneOffset.UTC).toOffsetDateTime();
        return List.of(
            entry(accountNo, baseTime, "CREDIT", new BigDecimal("12500.00"),
                "客户A货款", "客户A有限公司"),
            entry(accountNo, baseTime.plusHours(3), "DEBIT", new BigDecimal("780.50"),
                "渠道运费成本", "DHL Express"),
            entry(accountNo, baseTime.plusHours(7), "CREDIT", new BigDecimal("5300.25"),
                "客户B尾款", "客户B")
        );
    }

    private BankStatementEntry entry(String accountNo, OffsetDateTime time, String direction,
                                      BigDecimal amount, String purpose, String counterpartyName) {
        long n = SEQ.incrementAndGet();
        String ref = "SBX-BANK-" + String.format("%012d", n);
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("source", "SANDBOX");
        raw.put("account_no", accountNo);
        raw.put("seq", n);
        return new BankStatementEntry(
            ref, time, direction, amount, "CNY",
            null,  // sandbox 不模拟余额
            counterpartyName,
            "SBX-CP-" + n,
            purpose,
            raw);
    }
}

package com.xqt.saas.finance.feed;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 银行对账单 feed 抽象。生产对接：
 *   - 工商银行：网银管家批量回单接口
 *   - 招商银行：CMB U-Bank API
 *   - 中国银行：CBPS 现金管理
 *   - 国际：SWIFT MT940 / ISO20022 camt.053
 *
 * 当前默认 {@link SandboxBankFeed} 实现，运营在 acc_banks 配置真实凭证后切换。
 *
 * 抽象出来后，业务侧 {@code AccBillsController} / 对账模块只用本接口，
 * 不需关心具体 channel。增加新银行 = 新 bean，不改业务代码（{@link com.xqt.saas.customerapi.CarrierGateway} 模式）。
 */
public interface BankReconciliationFeed {

    /** Feed 标识（如 "SANDBOX" / "ICBC" / "CMB"）。 */
    String code();

    /**
     * 拉取一段时间内的银行流水。
     * @param tenantId  租户
     * @param accountNo 银行账号（运营在 acc_banks 配置过）
     * @param dateFrom  起始日（含）
     * @param dateTo    截止日（含）
     * @return 流水条目列表
     */
    List<BankStatementEntry> pullStatement(String tenantId, String accountNo,
                                            LocalDate dateFrom, LocalDate dateTo);

    /**
     * 银行流水条目：normalized 结构，覆盖 ACC 的 Bank_Detail 字段。
     */
    record BankStatementEntry(
        String externalRef,         // 银行流水号（去重 key）
        OffsetDateTime txnTime,     // 交易时间
        String direction,           // CREDIT 进 / DEBIT 出
        BigDecimal amount,          // 金额（绝对值）
        String currency,            // ISO 4217
        BigDecimal balanceAfter,    // 交易后余额（可空）
        String counterpartyName,    // 对方户名
        String counterpartyAccount, // 对方账号
        String purpose,             // 用途/摘要
        Map<String, Object> raw     // 原始响应保留（审计/排障）
    ) {}
}

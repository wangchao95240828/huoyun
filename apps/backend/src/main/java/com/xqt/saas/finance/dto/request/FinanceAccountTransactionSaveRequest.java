package com.xqt.saas.finance.dto.request;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 账户流水保存/更新请求参数
 */
@Data
public class FinanceAccountTransactionSaveRequest {

    /** 主键ID（更新时必填） */
    private Long id;

    /** 租户ID */
    private String tenantId;

    /** 流水号 */
    private String transactionNo;

    /** 账户ID */
    private Long accountId;

    /** 客户ID */
    private Long customerId;

    /** 类型：1.公司 2.客户 3.供应商 4.员工 */
    private Integer transactionType;

    /** 币种 */
    private String currency;

    /** 金额 */
    private BigDecimal amount;

    /** 手续费 */
    private BigDecimal fee;

    /** 入账金额 */
    private BigDecimal creditAmount;

    /** 备注 */
    private String remark;

    /** 支付时间 */
    private LocalDateTime paymentTime;
}

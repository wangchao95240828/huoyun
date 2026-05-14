package com.xqt.saas.finance.dto.request;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 财务流水保存/更新请求参数
 */
@Data
public class FinanceTransactionSaveRequest {

    /** 主键ID（更新时必填） */
    private Long id;

    /** 租户ID */
    private String tenantId;

    /** 流水号 */
    private String transactionNo;

    /** 用户 */
    private String userName;

    /** 公司账户 */
    private String companyAccount;

    /** 用户账户 */
    private String userAccount;

    /** 币种 */
    private String currency;

    /** 金额 */
    private BigDecimal amount;

    /** 手续费 */
    private BigDecimal fee;

    /** 类型：1客户充值 2客户提现 3支付供应商 4供应商退款 5经营收入 6经营支出 7工资发放 8提成发放 9内部转账 */
    private Integer type;

    /** 审核流水号 */
    private String auditTransactionNo;

    /** 支付状态 */
    private Integer paymentStatus;

    /** 是否已开票 */
    private Boolean invoiced;

    /** 账单 */
    private String billNo;

    /** 审核时间 */
    private LocalDateTime auditTime;

    /** 支付时间 */
    private LocalDateTime paymentTime;
}

package com.xqt.saas.finance.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 财务流水响应视图
 */
@Data
public class FinanceTransactionView {

    /** 主键ID */
    private Long id;

    /** 租户ID */
    private UUID tenantId;

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

    /** 创建者 */
    private String createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新者 */
    private String updateBy;

    /** 更新时间 */
    private LocalDateTime updateTime;
}

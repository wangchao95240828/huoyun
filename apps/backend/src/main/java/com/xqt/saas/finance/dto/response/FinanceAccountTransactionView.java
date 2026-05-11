package com.xqt.saas.finance.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 账户流水响应视图
 */
@Data
public class FinanceAccountTransactionView {

    /** 主键ID */
    private Long id;

    /** 租户ID */
    private UUID tenantId;

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

    /** 创建者 */
    private String createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新者 */
    private String updateBy;

    /** 更新时间 */
    private LocalDateTime updateTime;
}

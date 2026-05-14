package com.xqt.saas.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 账户流水实体类
 * 对应数据库表：finance_account_transaction
 */
@Data
@TableName("finance_account_transaction")
public class FinanceAccountTransaction {

    /** 主键ID，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 租户ID */
    @TableField("tenant_id")
    private UUID tenantId;

    /** 流水号 */
    @TableField("transaction_no")
    private String transactionNo;

    /** 账户ID */
    @TableField("account_id")
    private Long accountId;

    /** 客户ID */
    @TableField("customer_id")
    private Long customerId;

    /** 类型：1.公司 2.客户 3.供应商 4.员工 */
    @TableField("transaction_type")
    private Integer transactionType;

    /** 币种 */
    @TableField("currency")
    private String currency;

    /** 金额 */
    @TableField("amount")
    private BigDecimal amount;

    /** 手续费 */
    @TableField("fee")
    private BigDecimal fee;

    /** 入账金额 */
    @TableField("credit_amount")
    private BigDecimal creditAmount;

    /** 备注 */
    @TableField("remark")
    private String remark;

    /** 支付时间 */
    @TableField("payment_time")
    private LocalDateTime paymentTime;

    /** 创建者 */
    @TableField("create_by")
    private String createBy;

    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 更新者 */
    @TableField("update_by")
    private String updateBy;

    /** 更新时间 */
    @TableField("update_time")
    private LocalDateTime updateTime;
}

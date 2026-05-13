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
 * 财务流水实体类
 * 对应数据库表：finance_transaction
 */
@Data
@TableName("finance_transaction")
public class FinanceTransaction {

    /** 主键ID，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 租户ID */
    @TableField("tenant_id")
    private UUID tenantId;

    /** 流水号 */
    @TableField("transaction_no")
    private String transactionNo;

    /** 用户 */
    @TableField("user_name")
    private String userName;

    /** 公司账户 */
    @TableField("company_account")
    private String companyAccount;

    /** 用户账户 */
    @TableField("user_account")
    private String userAccount;

    /** 币种 */
    @TableField("currency")
    private String currency;

    /** 金额 */
    @TableField("amount")
    private BigDecimal amount;

    /** 手续费 */
    @TableField("fee")
    private BigDecimal fee;

    /** 类型：1客户充值 2客户提现 3支付供应商 4供应商退款 5经营收入 6经营支出 7工资发放 8提成发放 9内部转账 */
    @TableField("type")
    private Integer type;

    /** 审核流水号 */
    @TableField("audit_transaction_no")
    private String auditTransactionNo;

    /** 支付状态 */
    @TableField("payment_status")
    private Integer paymentStatus;

    /** 是否已开票 */
    @TableField("invoiced")
    private Boolean invoiced;

    /** 账单 */
    @TableField("bill_no")
    private String billNo;

    /** 审核时间 */
    @TableField("audit_time")
    private LocalDateTime auditTime;

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

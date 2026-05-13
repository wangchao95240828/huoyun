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
 * 客户流水实体类
 * 对应数据库表：finance_customer_transaction
 */
@Data
@TableName("finance_customer_transaction")
public class FinanceCustomerTransaction {

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

    /** 用户销售代表 */
    @TableField("user_sales_rep")
    private String userSalesRep;

    /** 账单 */
    @TableField("bill_no")
    private String billNo;

    /** 运单号 */
    @TableField("waybill_no")
    private String waybillNo;

    /** 提单号 */
    @TableField("bill_of_lading_no")
    private String billOfLadingNo;

    /** 转单号 */
    @TableField("transfer_no")
    private String transferNo;

    /** 单号 */
    @TableField("order_no")
    private String orderNo;

    /** 服务 */
    @TableField("service")
    private String service;

    /** 费用类型 */
    @TableField("fee_type")
    private String feeType;

    /** 数量 */
    @TableField("quantity")
    private BigDecimal quantity;

    /** 费用 */
    @TableField("fee")
    private BigDecimal fee;

    /** 汇率 */
    @TableField("exchange_rate")
    private BigDecimal exchangeRate;

    /** 本币费用 */
    @TableField("local_currency_fee")
    private BigDecimal localCurrencyFee;

    /** 审核：1.待审计 2.已审计 */
    @TableField("audit_status")
    private Integer auditStatus;

    /** 核销：1.待核销 2.已核销 */
    @TableField("write_off_status")
    private Integer writeOffStatus;

    /** 审批状态 */
    @TableField("approval_status")
    private Integer approvalStatus;

    /** 审批人 */
    @TableField("approver")
    private String approver;

    /** 自定义标识 */
    @TableField("custom_flag")
    private String customFlag;

    /** 备注 */
    @TableField("remark")
    private String remark;

    /** 核销时间 */
    @TableField("write_off_time")
    private LocalDateTime writeOffTime;

    /** 业务时间 */
    @TableField("business_time")
    private LocalDateTime businessTime;

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

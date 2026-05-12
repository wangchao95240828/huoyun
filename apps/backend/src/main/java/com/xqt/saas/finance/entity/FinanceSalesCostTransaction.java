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
 * 销售成本流水实体类
 * 对应数据库表：finance_sales_cost_transaction
 */
@Data
@TableName("finance_sales_cost_transaction")
public class FinanceSalesCostTransaction {

    /** 主键ID，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 租户ID */
    @TableField("tenant_id")
    private UUID tenantId;

    /** 流水号 */
    @TableField("transaction_no")
    private String transactionNo;

    /** 员工 */
    @TableField("employee")
    private String employee;

    /** 运单号 */
    @TableField("waybill_no")
    private String waybillNo;

    /** 转单号 */
    @TableField("transfer_no")
    private String transferNo;

    /** 费用类型 */
    @TableField("fee_type")
    private String feeType;

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

    /** 备注 */
    @TableField("remark")
    private String remark;

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

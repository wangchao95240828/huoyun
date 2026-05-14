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
 * 供应商账单实体类
 * 对应数据库表：finance_supplier_bill
 */
@Data
@TableName("finance_supplier_bill")
public class FinanceSupplierBill {

    /** 主键ID，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 租户ID */
    @TableField("tenant_id")
    private UUID tenantId;

    /** 单号 */
    @TableField("bill_no")
    private String billNo;

    /** 供应商 */
    @TableField("supplier")
    private String supplier;

    /** 币种 */
    @TableField("currency")
    private String currency;

    /** 状态：1.待审核 2.待核销 3.已核销 */
    @TableField("status")
    private Integer status;

    /** 流水总额 */
    @TableField("transaction_total")
    private BigDecimal transactionTotal;

    /** 账单金额 */
    @TableField("bill_amount")
    private BigDecimal billAmount;

    /** 自定义标识 */
    @TableField("custom_flag")
    private String customFlag;

    /** 账单日期 */
    @TableField("bill_date")
    private LocalDateTime billDate;

    /** 到期时间 */
    @TableField("due_date")
    private LocalDateTime dueDate;

    /** 核销时间 */
    @TableField("write_off_time")
    private LocalDateTime writeOffTime;

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

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
 * 销售提成单实体类
 * 对应数据库表：finance_sales_commission
 */
@Data
@TableName("finance_sales_commission")
public class FinanceSalesCommission {

    /** 主键ID，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 租户ID */
    @TableField("tenant_id")
    private UUID tenantId;

    /** 提成单号 */
    @TableField("commission_no")
    private String commissionNo;

    /** 销售 */
    @TableField("sales")
    private String sales;

    /** 币种 */
    @TableField("currency")
    private String currency;

    /** 提成单金额 */
    @TableField("commission_amount")
    private BigDecimal commissionAmount;

    /** 状态：1.待审核 2.已审核 3.已发放 */
    @TableField("status")
    private Integer status;

    /** 已支付 */
    @TableField("paid")
    private BigDecimal paid;

    /** 余款 */
    @TableField("balance")
    private BigDecimal balance;

    /** 备注 */
    @TableField("remark")
    private String remark;

    /** 账单日期 */
    @TableField("bill_date")
    private LocalDateTime billDate;

    /** 到期时间 */
    @TableField("due_date")
    private LocalDateTime dueDate;

    /** 审核时间 */
    @TableField("audit_time")
    private LocalDateTime auditTime;

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

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
 * 应收报表实体类
 * 对应数据库表：finance_receivable_report
 */
@Data
@TableName("finance_receivable_report")
public class FinanceReceivableReport {

    /** 主键ID，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 租户ID */
    @TableField("tenant_id")
    private UUID tenantId;

    /** 用户 */
    @TableField("user_name")
    private String userName;

    /** 币种 */
    @TableField("currency")
    private String currency;

    /** 营业额 */
    @TableField("turnover")
    private BigDecimal turnover;

    /** 已支付 */
    @TableField("paid_amount")
    private BigDecimal paidAmount;

    /** 待支付 */
    @TableField("pending_amount")
    private BigDecimal pendingAmount;

    /** 已出账单 */
    @TableField("issued_bill")
    private BigDecimal issuedBill;

    /** 待出账单 */
    @TableField("pending_bill")
    private BigDecimal pendingBill;

    /** 已付账单 */
    @TableField("paid_bill")
    private BigDecimal paidBill;

    /** 待付账单 */
    @TableField("pending_payment_bill")
    private BigDecimal pendingPaymentBill;

    /** 账户实际金额 */
    @TableField("actual_amount")
    private BigDecimal actualAmount;

    /** 客服代表 */
    @TableField("customer_service_rep")
    private String customerServiceRep;

    /** 销售代表 */
    @TableField("sales_rep")
    private String salesRep;

    /** 财务代表 */
    @TableField("finance_rep")
    private String financeRep;

    /** 结算方式 */
    @TableField("settlement_method")
    private String settlementMethod;

    /** 用户等级 */
    @TableField("user_level")
    private String userLevel;

    /** 用户备注 */
    @TableField("user_remark")
    private String userRemark;

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

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
 * 客户账单实体类
 * 对应数据库表：finance_customer_bill
 */
@Data
@TableName("finance_customer_bill")
public class FinanceCustomerBill {

    /** 主键ID，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 租户ID */
    @TableField("tenant_id")
    private UUID tenantId;

    /** 账单号 */
    @TableField("bill_no")
    private String billNo;

    /** 用户（结算方式） */
    @TableField("user_settle")
    private String userSettle;

    /** 分公司 */
    @TableField("branch_company")
    private String branchCompany;

    /** 币种 */
    @TableField("currency")
    private String currency;

    /** 账单金额 */
    @TableField("bill_amount")
    private BigDecimal billAmount;

    /** 已支付 */
    @TableField("paid_amount")
    private BigDecimal paidAmount;

    /** 余款 */
    @TableField("remaining_amount")
    private BigDecimal remainingAmount;

    /** 销售代表 */
    @TableField("sales_rep")
    private String salesRep;

    /** 客服代表 */
    @TableField("customer_service_rep")
    private String customerServiceRep;

    /** 财务代表 */
    @TableField("finance_rep")
    private String financeRep;

    /** 到期动作 */
    @TableField("due_action")
    private String dueAction;

    /** 账单确认 */
    @TableField("bill_confirmed")
    private Boolean billConfirmed;

    /** 状态：1.待审核 2.待核销 3.已核销 */
    @TableField("status")
    private Integer status;

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

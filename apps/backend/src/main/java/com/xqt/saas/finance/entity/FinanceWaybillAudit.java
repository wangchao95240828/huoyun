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
 * 运单审计实体类
 * 对应数据库表：finance_waybill_audit
 */
@Data
@TableName("finance_waybill_audit")
public class FinanceWaybillAudit {

    /** 主键ID，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 租户ID */
    @TableField("tenant_id")
    private UUID tenantId;

    /** 运单号 */
    @TableField("waybill_no")
    private String waybillNo;

    /** 用户 */
    @TableField("user_name")
    private String userName;

    /** 服务 */
    @TableField("service")
    private String service;

    /** 国家 */
    @TableField("country")
    private String country;

    /** 件数 */
    @TableField("piece_count")
    private Integer pieceCount;

    /** 实重 */
    @TableField("actual_weight")
    private BigDecimal actualWeight;

    /** 材重 */
    @TableField("volume_weight")
    private BigDecimal volumeWeight;

    /** 收费重 */
    @TableField("charge_weight")
    private BigDecimal chargeWeight;

    /** 供应商重量 */
    @TableField("supplier_weight")
    private BigDecimal supplierWeight;

    /** 状态：1已收货 2转运中 3已签收 4退件 */
    @TableField("status")
    private Integer status;

    /** 应收 */
    @TableField("receivable_amount")
    private BigDecimal receivableAmount;

    /** 应付 */
    @TableField("payable_amount")
    private BigDecimal payableAmount;

    /** 销售成本 */
    @TableField("sales_cost")
    private BigDecimal salesCost;

    /** 销售提成 */
    @TableField("sales_commission")
    private BigDecimal salesCommission;

    /** 毛利 */
    @TableField("gross_profit")
    private BigDecimal grossProfit;

    /** 客服代表 */
    @TableField("customer_service_rep")
    private String customerServiceRep;

    /** 销售代表 */
    @TableField("sales_rep")
    private String salesRep;

    /** 拣货时间 */
    @TableField("picking_time")
    private LocalDateTime pickingTime;

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

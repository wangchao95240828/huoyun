package com.xqt.saas.finance.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 运单审计响应视图
 */
@Data
public class FinanceWaybillAuditView {

    /** 主键ID */
    private Long id;

    /** 租户ID */
    private UUID tenantId;

    /** 运单号 */
    private String waybillNo;

    /** 用户 */
    private String userName;

    /** 服务 */
    private String service;

    /** 国家 */
    private String country;

    /** 件数 */
    private Integer pieceCount;

    /** 实重 */
    private BigDecimal actualWeight;

    /** 材重 */
    private BigDecimal volumeWeight;

    /** 收费重 */
    private BigDecimal chargeWeight;

    /** 供应商重量 */
    private BigDecimal supplierWeight;

    /** 状态：1已收货 2转运中 3已签收 4退件 */
    private Integer status;

    /** 应收 */
    private BigDecimal receivableAmount;

    /** 应付 */
    private BigDecimal payableAmount;

    /** 销售成本 */
    private BigDecimal salesCost;

    /** 销售提成 */
    private BigDecimal salesCommission;

    /** 毛利 */
    private BigDecimal grossProfit;

    /** 客服代表 */
    private String customerServiceRep;

    /** 销售代表 */
    private String salesRep;

    /** 拣货时间 */
    private LocalDateTime pickingTime;

    /** 创建者 */
    private String createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新者 */
    private String updateBy;

    /** 更新时间 */
    private LocalDateTime updateTime;
}

package com.xqt.saas.finance.dto.request;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 运单审计保存/更新请求参数
 */
@Data
public class FinanceWaybillAuditSaveRequest {

    /** 主键ID（更新时必填） */
    private Long id;

    /** 租户ID */
    private String tenantId;

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
}

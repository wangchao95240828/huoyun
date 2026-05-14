package com.xqt.saas.finance.dto.request;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 应收报表保存/更新请求参数
 */
@Data
public class FinanceReceivableReportSaveRequest {

    /** 主键ID（更新时必填） */
    private Long id;

    /** 租户ID */
    private String tenantId;

    /** 用户 */
    private String userName;

    /** 币种 */
    private String currency;

    /** 营业额 */
    private BigDecimal turnover;

    /** 已支付 */
    private BigDecimal paidAmount;

    /** 待支付 */
    private BigDecimal pendingAmount;

    /** 已出账单 */
    private BigDecimal issuedBill;

    /** 待出账单 */
    private BigDecimal pendingBill;

    /** 已付账单 */
    private BigDecimal paidBill;

    /** 待付账单 */
    private BigDecimal pendingPaymentBill;

    /** 账户实际金额 */
    private BigDecimal actualAmount;

    /** 客服代表 */
    private String customerServiceRep;

    /** 销售代表 */
    private String salesRep;

    /** 财务代表 */
    private String financeRep;

    /** 结算方式 */
    private String settlementMethod;

    /** 用户等级 */
    private String userLevel;

    /** 用户备注 */
    private String userRemark;
}

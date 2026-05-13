package com.xqt.saas.finance.dto.request;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 应付报表保存/更新请求参数
 */
@Data
public class FinancePayableReportSaveRequest {

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
}

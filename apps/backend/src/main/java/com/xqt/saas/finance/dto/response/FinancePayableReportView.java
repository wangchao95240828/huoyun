package com.xqt.saas.finance.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 应付报表响应视图
 */
@Data
public class FinancePayableReportView {

    /** 主键ID */
    private Long id;

    /** 租户ID */
    private UUID tenantId;

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

    /** 创建者 */
    private String createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新者 */
    private String updateBy;

    /** 更新时间 */
    private LocalDateTime updateTime;
}

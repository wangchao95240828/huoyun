package com.xqt.saas.finance.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 应收报表响应视图
 */
@Data
public class FinanceReceivableReportView {

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

    /** 创建者 */
    private String createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新者 */
    private String updateBy;

    /** 更新时间 */
    private LocalDateTime updateTime;
}

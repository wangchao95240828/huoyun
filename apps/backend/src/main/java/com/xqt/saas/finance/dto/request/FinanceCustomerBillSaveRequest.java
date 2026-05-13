package com.xqt.saas.finance.dto.request;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 客户账单保存/更新请求参数
 */
@Data
public class FinanceCustomerBillSaveRequest {

    /** 主键ID（更新时必填） */
    private Long id;

    /** 租户ID */
    private String tenantId;

    /** 账单号 */
    private String billNo;

    /** 用户（结算方式） */
    private String userSettle;

    /** 分公司 */
    private String branchCompany;

    /** 币种 */
    private String currency;

    /** 账单金额 */
    private BigDecimal billAmount;

    /** 已支付 */
    private BigDecimal paidAmount;

    /** 余款 */
    private BigDecimal remainingAmount;

    /** 销售代表 */
    private String salesRep;

    /** 客服代表 */
    private String customerServiceRep;

    /** 财务代表 */
    private String financeRep;

    /** 到期动作 */
    private String dueAction;

    /** 账单确认 */
    private Boolean billConfirmed;

    /** 状态：1.待审核 2.待核销 3.已核销 */
    private Integer status;

    /** 账单日期 */
    private LocalDateTime billDate;

    /** 到期时间 */
    private LocalDateTime dueDate;

    /** 核销时间 */
    private LocalDateTime writeOffTime;
}

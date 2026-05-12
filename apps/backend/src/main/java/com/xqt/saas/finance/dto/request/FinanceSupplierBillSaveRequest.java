package com.xqt.saas.finance.dto.request;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 供应商账单保存/更新请求参数
 */
@Data
public class FinanceSupplierBillSaveRequest {

    /** 主键ID（更新时必填） */
    private Long id;

    /** 租户ID */
    private String tenantId;

    /** 单号 */
    private String billNo;

    /** 供应商 */
    private String supplier;

    /** 币种 */
    private String currency;

    /** 状态：1.待审核 2.待核销 3.已核销 */
    private Integer status;

    /** 流水总额 */
    private BigDecimal transactionTotal;

    /** 账单金额 */
    private BigDecimal billAmount;

    /** 自定义标识 */
    private String customFlag;

    /** 账单日期 */
    private LocalDateTime billDate;

    /** 到期时间 */
    private LocalDateTime dueDate;

    /** 核销时间 */
    private LocalDateTime writeOffTime;
}

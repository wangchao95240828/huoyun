package com.xqt.saas.finance.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 供应商账单响应视图
 */
@Data
public class FinanceSupplierBillView {

    /** 主键ID */
    private Long id;

    /** 租户ID */
    private UUID tenantId;

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

    /** 创建者 */
    private String createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新者 */
    private String updateBy;

    /** 更新时间 */
    private LocalDateTime updateTime;
}

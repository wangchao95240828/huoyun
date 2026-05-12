package com.xqt.saas.finance.dto.request;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 销售成本流水保存/更新请求参数
 */
@Data
public class FinanceSalesCostTransactionSaveRequest {

    /** 主键ID（更新时必填） */
    private Long id;

    /** 租户ID */
    private String tenantId;

    /** 流水号 */
    private String transactionNo;

    /** 员工 */
    private String employee;

    /** 运单号 */
    private String waybillNo;

    /** 转单号 */
    private String transferNo;

    /** 费用类型 */
    private String feeType;

    /** 费用 */
    private BigDecimal fee;

    /** 汇率 */
    private BigDecimal exchangeRate;

    /** 本币费用 */
    private BigDecimal localCurrencyFee;

    /** 审核：1.待审计 2.已审计 */
    private Integer auditStatus;

    /** 备注 */
    private String remark;

    /** 业务时间 */
    private LocalDateTime businessTime;
}

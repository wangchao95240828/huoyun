package com.xqt.saas.finance.dto.request;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 客户流水保存/更新请求参数
 */
@Data
public class FinanceCustomerTransactionSaveRequest {

    /** 主键ID（更新时必填） */
    private Long id;

    /** 租户ID */
    private String tenantId;

    /** 流水号 */
    private String transactionNo;

    /** 用户 */
    private String userName;

    /** 用户销售代表 */
    private String userSalesRep;

    /** 账单 */
    private String billNo;

    /** 运单号 */
    private String waybillNo;

    /** 提单号 */
    private String billOfLadingNo;

    /** 转单号 */
    private String transferNo;

    /** 单号 */
    private String orderNo;

    /** 服务 */
    private String service;

    /** 费用类型 */
    private String feeType;

    /** 数量 */
    private BigDecimal quantity;

    /** 费用 */
    private BigDecimal fee;

    /** 汇率 */
    private BigDecimal exchangeRate;

    /** 本币费用 */
    private BigDecimal localCurrencyFee;

    /** 审核：1.待审计 2.已审计 */
    private Integer auditStatus;

    /** 核销：1.待核销 2.已核销 */
    private Integer writeOffStatus;

    /** 审批状态 */
    private Integer approvalStatus;

    /** 审批人 */
    private String approver;

    /** 自定义标识 */
    private String customFlag;

    /** 备注 */
    private String remark;

    /** 核销时间 */
    private LocalDateTime writeOffTime;

    /** 业务时间 */
    private LocalDateTime businessTime;
}

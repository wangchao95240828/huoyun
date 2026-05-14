package com.xqt.saas.finance.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 销售提成流水响应视图
 */
@Data
public class FinanceSalesCommissionTransactionView {

    /** 主键ID */
    private Long id;

    /** 租户ID */
    private UUID tenantId;

    /** 流水号 */
    private String transactionNo;

    /** 销售 */
    private String sales;

    /** 提成单号 */
    private String commissionNo;

    /** 运单号 */
    private String waybillNo;

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

    /** 创建者 */
    private String createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新者 */
    private String updateBy;

    /** 更新时间 */
    private LocalDateTime updateTime;
}

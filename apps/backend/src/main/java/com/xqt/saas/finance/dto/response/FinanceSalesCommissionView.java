package com.xqt.saas.finance.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 销售提成单响应视图
 */
@Data
public class FinanceSalesCommissionView {

    /** 主键ID */
    private Long id;

    /** 租户ID */
    private UUID tenantId;

    /** 提成单号 */
    private String commissionNo;

    /** 销售 */
    private String sales;

    /** 币种 */
    private String currency;

    /** 提成单金额 */
    private BigDecimal commissionAmount;

    /** 状态：1.待审核 2.已审核 3.已发放 */
    private Integer status;

    /** 已支付 */
    private BigDecimal paid;

    /** 余款 */
    private BigDecimal balance;

    /** 备注 */
    private String remark;

    /** 账单日期 */
    private LocalDateTime billDate;

    /** 到期时间 */
    private LocalDateTime dueDate;

    /** 审核时间 */
    private LocalDateTime auditTime;

    /** 创建者 */
    private String createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新者 */
    private String updateBy;

    /** 更新时间 */
    private LocalDateTime updateTime;
}

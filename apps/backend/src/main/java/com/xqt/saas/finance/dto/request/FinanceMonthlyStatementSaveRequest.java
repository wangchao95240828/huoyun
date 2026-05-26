package com.xqt.saas.finance.dto.request;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 月结单保存/更新请求参数
 */
@Data
public class FinanceMonthlyStatementSaveRequest {

    /** 主键ID（更新时必填） */
    private Long id;

    /** 租户ID */
    private String tenantId;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 应收（锁定/开启） */
    private Boolean receivable;

    /** 应付（锁定/开启） */
    private Boolean payable;

    /** 销售成本（锁定/开启） */
    private Boolean salesCost;

    /** 运单（锁定/开启） */
    private Boolean waybill;

    /** 提单（锁定/开启） */
    private Boolean billOfLading;

    /** 备注 */
    private String remark;
}

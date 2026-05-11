package com.xqt.saas.finance.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 月结单响应视图
 */
@Data
public class FinanceMonthlyStatementView {

    /** 主键ID */
    private Long id;

    /** 租户ID */
    private UUID tenantId;

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

    /** 创建者 */
    private String createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新者 */
    private String updateBy;

    /** 更新时间 */
    private LocalDateTime updateTime;
}

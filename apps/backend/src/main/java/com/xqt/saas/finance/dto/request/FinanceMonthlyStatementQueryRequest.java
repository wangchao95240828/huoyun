package com.xqt.saas.finance.dto.request;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 月结单查询请求参数
 */
@Data
public class FinanceMonthlyStatementQueryRequest {

    /** 页码 */
    private Long pageNum = 1L;

    /** 每页条数 */
    private Long pageSize = 10L;

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

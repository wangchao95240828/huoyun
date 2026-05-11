package com.xqt.saas.finance.dto.request;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FinanceMonthlyStatementQueryRequest {

    private Long pageNum = 1L;

    private Long pageSize = 10L;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Boolean receivable;

    private Boolean payable;

    private Boolean salesCost;

    private Boolean waybill;

    private Boolean billOfLading;

    private String remark;
}
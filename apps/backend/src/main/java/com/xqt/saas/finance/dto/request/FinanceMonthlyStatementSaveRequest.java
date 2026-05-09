package com.xqt.saas.finance.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FinanceMonthlyStatementSaveRequest {
    private Long id;

    private String tenantId;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Boolean receivable;

    private Boolean payable;

    private Boolean salesCost;

    private Boolean waybill;

    private Boolean billOfLading;

    private String remark;
}
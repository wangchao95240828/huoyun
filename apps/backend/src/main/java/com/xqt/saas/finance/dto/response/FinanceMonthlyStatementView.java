package com.xqt.saas.finance.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class FinanceMonthlyStatementView {

    private Long id;

    private UUID tenantId;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Boolean receivable;

    private Boolean payable;

    private Boolean salesCost;

    private Boolean waybill;

    private Boolean billOfLading;

    private String remark;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;
}
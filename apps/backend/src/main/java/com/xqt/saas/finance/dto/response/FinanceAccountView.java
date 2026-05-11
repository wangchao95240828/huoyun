package com.xqt.saas.finance.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class FinanceAccountView {

    private Long id;

    private UUID tenantId;

    private String accountName;

    private String currency;

    private BigDecimal balance;

    private String bankName;

    private Integer type;

    private Boolean visible;

    private String remark;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;
}
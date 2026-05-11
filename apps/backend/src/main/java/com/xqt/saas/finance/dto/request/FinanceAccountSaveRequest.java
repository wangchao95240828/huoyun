package com.xqt.saas.finance.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class FinanceAccountSaveRequest {
    private Long id;

    private String tenantId;

    @NotBlank(message = "账户名称不能为空")
    private String accountName;

    private String currency;

    private BigDecimal balance;

    private String bankName;

    @NotNull(message = "类型不能为空")
    private Integer type;

    private Boolean visible;

    private String remark;
}
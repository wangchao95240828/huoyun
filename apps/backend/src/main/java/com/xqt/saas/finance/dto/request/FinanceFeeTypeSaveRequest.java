package com.xqt.saas.finance.dto.request;

import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class FinanceFeeTypeSaveRequest {
    private Long id;
    private String tenantId;
    private String code;
    private String name;
    private String type;
    private BigDecimal price;
    private Boolean isShow;
}
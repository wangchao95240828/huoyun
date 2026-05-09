package com.xqt.saas.finance.dto.request;

import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class FinanceFeeTypeSaveRequest {
    private Long id;
    @NotBlank(message = "租户id不能为空")
    private Long tenantId;
    @NotBlank(message = "代码不能为空")
    private String code;
    @NotBlank(message = "名称不能为空")
    private String name;
    private String type;
    @NotNull(message = "价格不能为空")
    private BigDecimal price;
    private Boolean isShow;
}
package com.xqt.saas.finance.dto.request;

import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 费用类型保存/更新请求参数
 */
@Data
public class FinanceFeeTypeSaveRequest {

    /** 主键ID（更新时必填） */
    private Long id;

    /** 租户ID */
    private String tenantId;

    /** 费用代码 */
    private String code;

    /** 费用名称 */
    private String name;

    /** 类型 */
    private String type;

    /** 价格 */
    private BigDecimal price;

    /** 是否显示 */
    private Boolean isShow;
}

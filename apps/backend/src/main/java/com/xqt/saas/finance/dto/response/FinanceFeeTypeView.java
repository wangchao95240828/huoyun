package com.xqt.saas.finance.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 费用类型响应视图
 */
@Data
public class FinanceFeeTypeView {

    /** 主键ID */
    private Long id;

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

    /** 创建者 */
    private String createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新者 */
    private String updateBy;

    /** 更新时间 */
    private LocalDateTime updateTime;
}

package com.xqt.saas.finance.dto.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 费用类型Excel导入导出DTO
 */
@Data
public class FinanceFeeTypeExcelDTO {

    /** 费用代码 */
    @ExcelProperty(value = "代码", index = 0)
    private String code;

    /** 费用名称 */
    @ExcelProperty(value = "名称", index = 1)
    private String name;

    /** 类型 */
    @ExcelProperty(value = "类型", index = 2)
    private String type;

    /** 价格 */
    @ExcelProperty(value = "价格", index = 3)
    private BigDecimal price;

    /** 是否显示 */
    @ExcelProperty(value = "是否显示", index = 4)
    private Boolean isShow;
}

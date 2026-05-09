package com.xqt.saas.finance.dto.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class FinanceFeeTypeExcelDTO {

    @ExcelProperty(value = "代码", index = 0)
    private String code;

    @ExcelProperty(value = "名称", index = 1)
    private String name;

    @ExcelProperty(value = "类型", index = 2)
    private String type;

    @ExcelProperty(value = "价格", index = 3)
    private BigDecimal price;

    @ExcelProperty(value = "是否显示", index = 4)
    private Boolean isShow;
}
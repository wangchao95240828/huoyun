package com.xqt.saas.finance.dto.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 供应商账单Excel导出DTO
 */
@Data
public class FinanceSupplierBillExcelDTO {

    /** 单号 */
    @ExcelProperty(value = "单号", index = 0)
    private String billNo;

    /** 供应商 */
    @ExcelProperty(value = "供应商", index = 1)
    private String supplier;

    /** 币种 */
    @ExcelProperty(value = "币种", index = 2)
    private String currency;

    /** 状态 */
    @ExcelProperty(value = "状态", index = 3)
    private String statusText;

    /** 流水总额 */
    @ExcelProperty(value = "流水总额", index = 4)
    private BigDecimal transactionTotal;

    /** 账单金额 */
    @ExcelProperty(value = "账单金额", index = 5)
    private BigDecimal billAmount;

    /** 自定义标识 */
    @ExcelProperty(value = "自定义标识", index = 6)
    private String customFlag;

    /** 账单日期 */
    @ExcelProperty(value = "账单日期", index = 7)
    private LocalDateTime billDate;

    /** 到期时间 */
    @ExcelProperty(value = "到期时间", index = 8)
    private LocalDateTime dueDate;

    /** 核销时间 */
    @ExcelProperty(value = "核销时间", index = 9)
    private LocalDateTime writeOffTime;
}

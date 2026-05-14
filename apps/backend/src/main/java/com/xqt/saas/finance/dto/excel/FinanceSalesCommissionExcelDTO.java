package com.xqt.saas.finance.dto.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 销售提成单Excel导出DTO
 */
@Data
public class FinanceSalesCommissionExcelDTO {

    /** 提成单号 */
    @ExcelProperty(value = "提成单号", index = 0)
    private String commissionNo;

    /** 销售 */
    @ExcelProperty(value = "销售", index = 1)
    private String sales;

    /** 币种 */
    @ExcelProperty(value = "币种", index = 2)
    private String currency;

    /** 提成单金额 */
    @ExcelProperty(value = "提成单金额", index = 3)
    private BigDecimal commissionAmount;

    /** 状态 */
    @ExcelProperty(value = "状态", index = 4)
    private String statusText;

    /** 已支付 */
    @ExcelProperty(value = "已支付", index = 5)
    private BigDecimal paid;

    /** 余款 */
    @ExcelProperty(value = "余款", index = 6)
    private BigDecimal balance;

    /** 备注 */
    @ExcelProperty(value = "备注", index = 7)
    private String remark;

    /** 账单日期 */
    @ExcelProperty(value = "账单日期", index = 8)
    private LocalDateTime billDate;

    /** 到期时间 */
    @ExcelProperty(value = "到期时间", index = 9)
    private LocalDateTime dueDate;

    /** 审核时间 */
    @ExcelProperty(value = "审核时间", index = 10)
    private LocalDateTime auditTime;
}

package com.xqt.saas.finance.dto.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 客户账单Excel导入导出DTO
 */
@Data
public class FinanceCustomerBillExcelDTO {

    /** 账单号 */
    @ExcelProperty(value = "账单号", index = 0)
    private String billNo;

    /** 用户（结算方式） */
    @ExcelProperty(value = "用户（结算方式）", index = 1)
    private String userSettle;

    /** 分公司 */
    @ExcelProperty(value = "分公司", index = 2)
    private String branchCompany;

    /** 币种 */
    @ExcelProperty(value = "币种", index = 3)
    private String currency;

    /** 账单金额 */
    @ExcelProperty(value = "账单金额", index = 4)
    private BigDecimal billAmount;

    /** 已支付 */
    @ExcelProperty(value = "已支付", index = 5)
    private BigDecimal paidAmount;

    /** 余款 */
    @ExcelProperty(value = "余款", index = 6)
    private BigDecimal remainingAmount;

    /** 销售代表 */
    @ExcelProperty(value = "销售代表", index = 7)
    private String salesRep;

    /** 客服代表 */
    @ExcelProperty(value = "客服代表", index = 8)
    private String customerServiceRep;

    /** 财务代表 */
    @ExcelProperty(value = "财务代表", index = 9)
    private String financeRep;

    /** 到期动作 */
    @ExcelProperty(value = "到期动作", index = 10)
    private String dueAction;

    /** 账单确认 */
    @ExcelProperty(value = "账单确认", index = 11)
    private String billConfirmedText;

    /** 状态 */
    @ExcelProperty(value = "状态", index = 12)
    private String statusText;

    /** 账单日期 */
    @ExcelProperty(value = "账单日期", index = 13)
    private LocalDateTime billDate;

    /** 到期时间 */
    @ExcelProperty(value = "到期时间", index = 14)
    private LocalDateTime dueDate;

    /** 核销时间 */
    @ExcelProperty(value = "核销时间", index = 15)
    private LocalDateTime writeOffTime;
}

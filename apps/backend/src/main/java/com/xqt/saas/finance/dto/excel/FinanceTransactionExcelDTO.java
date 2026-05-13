package com.xqt.saas.finance.dto.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 财务流水Excel导入导出DTO
 */
@Data
public class FinanceTransactionExcelDTO {

    /** 流水号 */
    @ExcelProperty(value = "流水号", index = 0)
    private String transactionNo;

    /** 用户 */
    @ExcelProperty(value = "用户", index = 1)
    private String userName;

    /** 公司账户 */
    @ExcelProperty(value = "公司账户", index = 2)
    private String companyAccount;

    /** 用户账户 */
    @ExcelProperty(value = "用户账户", index = 3)
    private String userAccount;

    /** 币种 */
    @ExcelProperty(value = "币种", index = 4)
    private String currency;

    /** 金额 */
    @ExcelProperty(value = "金额", index = 5)
    private BigDecimal amount;

    /** 手续费 */
    @ExcelProperty(value = "手续费", index = 6)
    private BigDecimal fee;

    /** 类型 */
    @ExcelProperty(value = "类型", index = 7)
    private String typeText;

    /** 审核流水号 */
    @ExcelProperty(value = "审核流水号", index = 8)
    private String auditTransactionNo;

    /** 支付状态 */
    @ExcelProperty(value = "支付状态", index = 9)
    private String paymentStatusText;

    /** 是否已开票 */
    @ExcelProperty(value = "是否已开票", index = 10)
    private String invoicedText;

    /** 账单 */
    @ExcelProperty(value = "账单", index = 11)
    private String billNo;

    /** 审核时间 */
    @ExcelProperty(value = "审核时间", index = 12)
    private LocalDateTime auditTime;

    /** 支付时间 */
    @ExcelProperty(value = "支付时间", index = 13)
    private LocalDateTime paymentTime;
}

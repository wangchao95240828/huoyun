package com.xqt.saas.finance.dto.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 供应商流水Excel导入导出DTO
 */
@Data
public class FinanceSupplierTransactionExcelDTO {

    /** 流水号 */
    @ExcelProperty(value = "流水号", index = 0)
    private String transactionNo;

    /** 供应商 */
    @ExcelProperty(value = "供应商", index = 1)
    private String supplier;

    /** 账单 */
    @ExcelProperty(value = "账单", index = 2)
    private String billNo;

    /** 运单号 */
    @ExcelProperty(value = "运单号", index = 3)
    private String waybillNo;

    /** 运单销售代表 */
    @ExcelProperty(value = "运单销售代表", index = 4)
    private String waybillSales;

    /** 提单号 */
    @ExcelProperty(value = "提单号", index = 5)
    private String billOfLadingNo;

    /** 转单号 */
    @ExcelProperty(value = "转单号", index = 6)
    private String transferNo;

    /** 费用类型 */
    @ExcelProperty(value = "费用类型", index = 7)
    private String feeType;

    /** 数量 */
    @ExcelProperty(value = "数量", index = 8)
    private BigDecimal quantity;

    /** 费用 */
    @ExcelProperty(value = "费用", index = 9)
    private BigDecimal fee;

    /** 汇率 */
    @ExcelProperty(value = "汇率", index = 10)
    private BigDecimal exchangeRate;

    /** 本币费用 */
    @ExcelProperty(value = "本币费用", index = 11)
    private BigDecimal localCurrencyFee;

    /** 审核状态 */
    @ExcelProperty(value = "审核状态", index = 12)
    private String auditStatusText;

    /** 核销状态 */
    @ExcelProperty(value = "核销状态", index = 13)
    private String writeOffStatusText;

    /** 自定义标识 */
    @ExcelProperty(value = "自定义标识", index = 14)
    private String customFlag;

    /** 核销时间 */
    @ExcelProperty(value = "核销时间", index = 15)
    private LocalDateTime writeOffTime;

    /** 业务时间 */
    @ExcelProperty(value = "业务时间", index = 16)
    private LocalDateTime businessTime;
}

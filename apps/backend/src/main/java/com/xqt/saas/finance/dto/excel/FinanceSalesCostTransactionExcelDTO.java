package com.xqt.saas.finance.dto.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 销售成本流水Excel导入导出DTO
 */
@Data
public class FinanceSalesCostTransactionExcelDTO {

    /** 流水号 */
    @ExcelProperty(value = "流水号", index = 0)
    private String transactionNo;

    /** 员工 */
    @ExcelProperty(value = "员工", index = 1)
    private String employee;

    /** 运单号 */
    @ExcelProperty(value = "运单号", index = 2)
    private String waybillNo;

    /** 转单号 */
    @ExcelProperty(value = "转单号", index = 3)
    private String transferNo;

    /** 费用类型 */
    @ExcelProperty(value = "费用类型", index = 4)
    private String feeType;

    /** 费用 */
    @ExcelProperty(value = "费用", index = 5)
    private BigDecimal fee;

    /** 汇率 */
    @ExcelProperty(value = "汇率", index = 6)
    private BigDecimal exchangeRate;

    /** 本币费用 */
    @ExcelProperty(value = "本币费用", index = 7)
    private BigDecimal localCurrencyFee;

    /** 审核状态 */
    @ExcelProperty(value = "审核状态", index = 8)
    private String auditStatusText;

    /** 备注 */
    @ExcelProperty(value = "备注", index = 9)
    private String remark;

    /** 业务时间 */
    @ExcelProperty(value = "业务时间", index = 10)
    private LocalDateTime businessTime;
}

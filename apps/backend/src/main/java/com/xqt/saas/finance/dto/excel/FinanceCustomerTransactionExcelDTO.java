package com.xqt.saas.finance.dto.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 客户流水Excel导入导出DTO
 */
@Data
public class FinanceCustomerTransactionExcelDTO {

    /** 流水号 */
    @ExcelProperty(value = "流水号", index = 0)
    private String transactionNo;

    /** 用户 */
    @ExcelProperty(value = "用户", index = 1)
    private String userName;

    /** 用户销售代表 */
    @ExcelProperty(value = "用户销售代表", index = 2)
    private String userSalesRep;

    /** 账单 */
    @ExcelProperty(value = "账单", index = 3)
    private String billNo;

    /** 运单号 */
    @ExcelProperty(value = "运单号", index = 4)
    private String waybillNo;

    /** 提单号 */
    @ExcelProperty(value = "提单号", index = 5)
    private String billOfLadingNo;

    /** 转单号 */
    @ExcelProperty(value = "转单号", index = 6)
    private String transferNo;

    /** 单号 */
    @ExcelProperty(value = "单号", index = 7)
    private String orderNo;

    /** 服务 */
    @ExcelProperty(value = "服务", index = 8)
    private String service;

    /** 费用类型 */
    @ExcelProperty(value = "费用类型", index = 9)
    private String feeType;

    /** 数量 */
    @ExcelProperty(value = "数量", index = 10)
    private BigDecimal quantity;

    /** 费用 */
    @ExcelProperty(value = "费用", index = 11)
    private BigDecimal fee;

    /** 汇率 */
    @ExcelProperty(value = "汇率", index = 12)
    private BigDecimal exchangeRate;

    /** 本币费用 */
    @ExcelProperty(value = "本币费用", index = 13)
    private BigDecimal localCurrencyFee;

    /** 审核状态 */
    @ExcelProperty(value = "审核状态", index = 14)
    private String auditStatusText;

    /** 核销状态 */
    @ExcelProperty(value = "核销状态", index = 15)
    private String writeOffStatusText;

    /** 审批状态 */
    @ExcelProperty(value = "审批状态", index = 16)
    private String approvalStatusText;

    /** 审批人 */
    @ExcelProperty(value = "审批人", index = 17)
    private String approver;

    /** 自定义标识 */
    @ExcelProperty(value = "自定义标识", index = 18)
    private String customFlag;

    /** 备注 */
    @ExcelProperty(value = "备注", index = 19)
    private String remark;

    /** 核销时间 */
    @ExcelProperty(value = "核销时间", index = 20)
    private LocalDateTime writeOffTime;

    /** 业务时间 */
    @ExcelProperty(value = "业务时间", index = 21)
    private LocalDateTime businessTime;
}

package com.xqt.saas.finance.dto.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 运单审计Excel导入导出DTO
 */
@Data
public class FinanceWaybillAuditExcelDTO {

    /** 运单号 */
    @ExcelProperty(value = "运单号", index = 0)
    private String waybillNo;

    /** 用户 */
    @ExcelProperty(value = "用户", index = 1)
    private String userName;

    /** 服务 */
    @ExcelProperty(value = "服务", index = 2)
    private String service;

    /** 国家 */
    @ExcelProperty(value = "国家", index = 3)
    private String country;

    /** 件数 */
    @ExcelProperty(value = "件数", index = 4)
    private Integer pieceCount;

    /** 实重 */
    @ExcelProperty(value = "实重", index = 5)
    private BigDecimal actualWeight;

    /** 材重 */
    @ExcelProperty(value = "材重", index = 6)
    private BigDecimal volumeWeight;

    /** 收费重 */
    @ExcelProperty(value = "收费重", index = 7)
    private BigDecimal chargeWeight;

    /** 供应商重量 */
    @ExcelProperty(value = "供应商重量", index = 8)
    private BigDecimal supplierWeight;

    /** 状态 */
    @ExcelProperty(value = "状态", index = 9)
    private String statusText;

    /** 应收 */
    @ExcelProperty(value = "应收", index = 10)
    private BigDecimal receivableAmount;

    /** 应付 */
    @ExcelProperty(value = "应付", index = 11)
    private BigDecimal payableAmount;

    /** 销售成本 */
    @ExcelProperty(value = "销售成本", index = 12)
    private BigDecimal salesCost;

    /** 销售提成 */
    @ExcelProperty(value = "销售提成", index = 13)
    private BigDecimal salesCommission;

    /** 毛利 */
    @ExcelProperty(value = "毛利", index = 14)
    private BigDecimal grossProfit;

    /** 客服代表 */
    @ExcelProperty(value = "客服代表", index = 15)
    private String customerServiceRep;

    /** 销售代表 */
    @ExcelProperty(value = "销售代表", index = 16)
    private String salesRep;

    /** 拣货时间 */
    @ExcelProperty(value = "拣货时间", index = 17)
    private LocalDateTime pickingTime;
}

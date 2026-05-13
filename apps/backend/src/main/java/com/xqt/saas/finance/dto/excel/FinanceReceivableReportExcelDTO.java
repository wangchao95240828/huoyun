package com.xqt.saas.finance.dto.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 应收报表Excel导出DTO
 */
@Data
public class FinanceReceivableReportExcelDTO {

    /** 用户 */
    @ExcelProperty(value = "用户", index = 0)
    private String userName;

    /** 币种 */
    @ExcelProperty(value = "币种", index = 1)
    private String currency;

    /** 营业额 */
    @ExcelProperty(value = "营业额", index = 2)
    private BigDecimal turnover;

    /** 已支付 */
    @ExcelProperty(value = "已支付", index = 3)
    private BigDecimal paidAmount;

    /** 待支付 */
    @ExcelProperty(value = "待支付", index = 4)
    private BigDecimal pendingAmount;

    /** 已出账单 */
    @ExcelProperty(value = "已出账单", index = 5)
    private BigDecimal issuedBill;

    /** 待出账单 */
    @ExcelProperty(value = "待出账单", index = 6)
    private BigDecimal pendingBill;

    /** 已付账单 */
    @ExcelProperty(value = "已付账单", index = 7)
    private BigDecimal paidBill;

    /** 待付账单 */
    @ExcelProperty(value = "待付账单", index = 8)
    private BigDecimal pendingPaymentBill;

    /** 账户实际金额 */
    @ExcelProperty(value = "账户实际金额", index = 9)
    private BigDecimal actualAmount;

    /** 客服代表 */
    @ExcelProperty(value = "客服代表", index = 10)
    private String customerServiceRep;

    /** 销售代表 */
    @ExcelProperty(value = "销售代表", index = 11)
    private String salesRep;

    /** 财务代表 */
    @ExcelProperty(value = "财务代表", index = 12)
    private String financeRep;

    /** 结算方式 */
    @ExcelProperty(value = "结算方式", index = 13)
    private String settlementMethod;

    /** 用户等级 */
    @ExcelProperty(value = "用户等级", index = 14)
    private String userLevel;

    /** 用户备注 */
    @ExcelProperty(value = "用户备注", index = 15)
    private String userRemark;
}

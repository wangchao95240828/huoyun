package com.xqt.saas.finance.dto.request;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 账户流水查询请求参数
 */
@Data
public class FinanceAccountTransactionQueryRequest {

    /** 流水号 */
    private String transactionNo;

    /** 账户ID */
    private Long accountId;

    /** 客户ID */
    private Long customerId;

    /** 类型：1.公司 2.客户 3.供应商 4.员工 */
    private Integer transactionType;

    /** 币种 */
    private String currency;

    /** 备注 */
    private String remark;

    /** 支付时间开始 */
    private LocalDateTime paymentTimeStart;

    /** 支付时间结束 */
    private LocalDateTime paymentTimeEnd;
}

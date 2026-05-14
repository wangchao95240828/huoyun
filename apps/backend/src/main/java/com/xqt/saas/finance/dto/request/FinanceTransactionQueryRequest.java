package com.xqt.saas.finance.dto.request;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 财务流水查询请求参数
 */
@Data
public class FinanceTransactionQueryRequest {

    /** 页码 */
    private Long pageNum = 1L;

    /** 每页条数 */
    private Long pageSize = 10L;

    /** 流水号 */
    private String transactionNo;

    /** 用户 */
    private String userName;

    /** 公司账户 */
    private String companyAccount;

    /** 用户账户 */
    private String userAccount;

    /** 币种 */
    private String currency;

    /** 类型：1客户充值 2客户提现 3支付供应商 4供应商退款 5经营收入 6经营支出 7工资发放 8提成发放 9内部转账 */
    private Integer type;

    /** 支付状态 */
    private Integer paymentStatus;

    /** 是否已开票 */
    private Boolean invoiced;

    /** 账单 */
    private String billNo;

    /** 审核时间开始 */
    private LocalDateTime auditTimeStart;

    /** 审核时间结束 */
    private LocalDateTime auditTimeEnd;
}

package com.xqt.saas.finance.dto.request;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户流水查询请求参数
 */
@Data
public class FinanceCustomerTransactionQueryRequest {

    /** 页码 */
    private Long pageNum = 1L;

    /** 每页条数 */
    private Long pageSize = 10L;

    /** 流水号 */
    private String transactionNo;

    /** 用户 */
    private String userName;

    /** 用户销售代表 */
    private String userSalesRep;

    /** 账单 */
    private String billNo;

    /** 运单号 */
    private String waybillNo;

    /** 提单号 */
    private String billOfLadingNo;

    /** 转单号 */
    private String transferNo;

    /** 单号 */
    private String orderNo;

    /** 服务 */
    private String service;

    /** 费用类型 */
    private String feeType;

    /** 审核：1.待审计 2.已审计 */
    private Integer auditStatus;

    /** 核销：1.待核销 2.已核销 */
    private Integer writeOffStatus;

    /** 审批状态 */
    private Integer approvalStatus;

    /** 审批人 */
    private String approver;

    /** 自定义标识 */
    private String customFlag;

    /** 业务时间开始 */
    private LocalDateTime businessTimeStart;

    /** 业务时间结束 */
    private LocalDateTime businessTimeEnd;
}

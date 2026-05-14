package com.xqt.saas.finance.dto.request;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 供应商流水查询请求参数
 */
@Data
public class FinanceSupplierTransactionQueryRequest {

    /** 页码 */
    private Long pageNum = 1L;

    /** 每页条数 */
    private Long pageSize = 10L;

    /** 流水号 */
    private String transactionNo;

    /** 供应商 */
    private String supplier;

    /** 账单 */
    private String billNo;

    /** 运单号 */
    private String waybillNo;

    /** 运单销售代表 */
    private String waybillSales;

    /** 提单号 */
    private String billOfLadingNo;

    /** 转单号 */
    private String transferNo;

    /** 费用类型 */
    private String feeType;

    /** 审核：1.待审计 2.已审计 */
    private Integer auditStatus;

    /** 核销：1.待核销 2.已核销 */
    private Integer writeOffStatus;

    /** 自定义标识 */
    private String customFlag;

    /** 业务时间开始 */
    private LocalDateTime businessTimeStart;

    /** 业务时间结束 */
    private LocalDateTime businessTimeEnd;
}

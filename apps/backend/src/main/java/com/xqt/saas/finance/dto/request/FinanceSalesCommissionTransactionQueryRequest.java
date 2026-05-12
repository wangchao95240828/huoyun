package com.xqt.saas.finance.dto.request;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 销售提成流水查询请求参数
 */
@Data
public class FinanceSalesCommissionTransactionQueryRequest {

    /** 页码 */
    private Long pageNum = 1L;

    /** 每页条数 */
    private Long pageSize = 10L;

    /** 流水号 */
    private String transactionNo;

    /** 销售 */
    private String sales;

    /** 提成单号 */
    private String commissionNo;

    /** 运单号 */
    private String waybillNo;

    /** 费用类型 */
    private String feeType;

    /** 审核：1.待审计 2.已审计 */
    private Integer auditStatus;

    /** 备注 */
    private String remark;

    /** 业务时间开始 */
    private LocalDateTime businessTimeStart;

    /** 业务时间结束 */
    private LocalDateTime businessTimeEnd;
}

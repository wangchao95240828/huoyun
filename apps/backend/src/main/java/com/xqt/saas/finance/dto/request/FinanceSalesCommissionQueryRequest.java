package com.xqt.saas.finance.dto.request;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 销售提成单查询请求参数
 */
@Data
public class FinanceSalesCommissionQueryRequest {

    /** 页码 */
    private Long pageNum = 1L;

    /** 每页条数 */
    private Long pageSize = 10L;

    /** 提成单号 */
    private String commissionNo;

    /** 销售 */
    private String sales;

    /** 币种 */
    private String currency;

    /** 状态：1.待审核 2.已审核 3.已发放 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 账单日期开始 */
    private LocalDateTime billDateStart;

    /** 账单日期结束 */
    private LocalDateTime billDateEnd;

    /** 到期时间开始 */
    private LocalDateTime dueDateStart;

    /** 到期时间结束 */
    private LocalDateTime dueDateEnd;
}

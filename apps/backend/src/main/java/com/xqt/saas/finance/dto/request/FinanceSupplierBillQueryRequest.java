package com.xqt.saas.finance.dto.request;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 供应商账单查询请求参数
 */
@Data
public class FinanceSupplierBillQueryRequest {

    /** 页码 */
    private Long pageNum = 1L;

    /** 每页条数 */
    private Long pageSize = 10L;

    /** 单号 */
    private String billNo;

    /** 供应商 */
    private String supplier;

    /** 币种 */
    private String currency;

    /** 状态：1.待审核 2.待核销 3.已核销 */
    private Integer status;

    /** 自定义标识 */
    private String customFlag;

    /** 账单日期开始 */
    private LocalDateTime billDateStart;

    /** 账单日期结束 */
    private LocalDateTime billDateEnd;

    /** 到期时间开始 */
    private LocalDateTime dueDateStart;

    /** 到期时间结束 */
    private LocalDateTime dueDateEnd;
}

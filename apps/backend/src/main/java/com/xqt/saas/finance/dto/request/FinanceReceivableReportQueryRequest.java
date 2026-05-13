package com.xqt.saas.finance.dto.request;

import lombok.Data;

/**
 * 应收报表查询请求参数
 */
@Data
public class FinanceReceivableReportQueryRequest {

    /** 页码 */
    private Long pageNum = 1L;

    /** 每页条数 */
    private Long pageSize = 10L;

    /** 用户 */
    private String userName;

    /** 币种 */
    private String currency;

    /** 用户等级 */
    private String userLevel;
}

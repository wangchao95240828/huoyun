package com.xqt.saas.finance.dto.request;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 运单审计查询请求参数
 */
@Data
public class FinanceWaybillAuditQueryRequest {

    /** 页码 */
    private Long pageNum = 1L;

    /** 每页条数 */
    private Long pageSize = 10L;

    /** 运单号 */
    private String waybillNo;

    /** 用户 */
    private String userName;

    /** 服务 */
    private String service;

    /** 国家 */
    private String country;

    /** 状态：1已收货 2转运中 3已签收 4退件 */
    private Integer status;

    /** 客服代表 */
    private String customerServiceRep;

    /** 销售代表 */
    private String salesRep;

    /** 拣货时间开始 */
    private LocalDateTime pickingTimeStart;

    /** 拣货时间结束 */
    private LocalDateTime pickingTimeEnd;
}

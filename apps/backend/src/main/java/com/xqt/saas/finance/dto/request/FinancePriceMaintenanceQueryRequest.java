package com.xqt.saas.finance.dto.request;

import lombok.Data;

/**
 * 运价维护查询请求参数
 */
@Data
public class FinancePriceMaintenanceQueryRequest {

    /** 页码 */
    private Long pageNum = 1L;

    /** 每页条数 */
    private Long pageSize = 10L;

    /** 名称 */
    private String name;

    /** 服务 */
    private String service;

    /** 收货区域 */
    private String receiveArea;

    /** 用户等级 */
    private String userLevel;

    /** 用户 */
    private String userName;

    /** 状态 */
    private Boolean status;

    /** 价格类型：1.公布价 2.销售员底价 3.成本价 */
    private Integer priceType;
}

package com.xqt.saas.order.dto.request;

import lombok.Data;

/**
 * 服务查询请求参数
 */
@Data
public class OrderServiceQueryRequest {

    /** 页码 */
    private Long pageNum = 1L;

    /** 每页条数 */
    private Long pageSize = 10L;

    /** 服务名称 */
    private String serviceName;

    /** 状态：1正常 2停用 */
    private Integer status;

    /** 服务代码 */
    private String serviceCode;

    /** 类型 */
    private String type;

    /** 承运类型 */
    private String carrierType;

    /** 服务分类 */
    private String serviceCategory;
}

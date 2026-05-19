package com.xqt.saas.order.dto.request;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 服务保存/更新请求参数
 */
@Data
public class OrderServiceSaveRequest {

    /** 主键ID（更新时必填） */
    private Long id;

    /** 租户ID */
    private String tenantId;

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

    /** 计费方式 */
    private String billingMethod;

    /** 计重方式 */
    private String weightMethod;

    /** 计泡系数 */
    private BigDecimal bubbleFactor;

    /** 分泡比例 */
    private BigDecimal bubbleShare;

    /** 件数范围 */
    private String pieceRange;

    /** 打单账号 */
    private String printAccount;

    /** 打印标签类型 */
    private String labelType;
}

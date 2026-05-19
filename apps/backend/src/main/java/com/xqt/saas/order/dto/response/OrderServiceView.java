package com.xqt.saas.order.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 服务响应视图
 */
@Data
public class OrderServiceView {

    /** 主键ID */
    private Long id;

    /** 租户ID */
    private UUID tenantId;

    /** 服务名称 */
    private String serviceName;

    /** 状态：1正常 2停用 */
    private Integer status;

    /** 状态文本 */
    private String statusText;

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

    /** 创建者 */
    private String createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新者 */
    private String updateBy;

    /** 更新时间 */
    private LocalDateTime updateTime;
}

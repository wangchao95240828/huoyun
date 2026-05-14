package com.xqt.saas.finance.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 运价维护响应视图
 */
@Data
public class FinancePriceMaintenanceView {

    /** 主键ID */
    private Long id;

    /** 租户ID */
    private UUID tenantId;

    /** 名称 */
    private String name;

    /** 服务 */
    private String service;

    /** 收货区域 */
    private String receiveArea;

    /** 优先级 */
    private Integer priority;

    /** 用户等级 */
    private String userLevel;

    /** 用户 */
    private String userName;

    /** 最小重量 */
    private BigDecimal minWeight;

    /** 最大重量 */
    private BigDecimal maxWeight;

    /** 邮编开头 */
    private String zipPrefix;

    /** 父级 */
    private Long parentId;

    /** 状态 */
    private Boolean status;

    /** 价格类型：1.公布价 2.销售员底价 3.成本价 */
    private Integer priceType;

    /** 创建者 */
    private String createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新者 */
    private String updateBy;

    /** 更新时间 */
    private LocalDateTime updateTime;
}

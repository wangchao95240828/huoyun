package com.xqt.saas.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 运价维护实体类
 * 对应数据库表：finance_price_maintenance
 */
@Data
@TableName("finance_price_maintenance")
public class FinancePriceMaintenance {

    /** 主键ID，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 租户ID */
    @TableField("tenant_id")
    private UUID tenantId;

    /** 名称 */
    @TableField("name")
    private String name;

    /** 服务 */
    @TableField("service")
    private String service;

    /** 收货区域 */
    @TableField("receive_area")
    private String receiveArea;

    /** 优先级 */
    @TableField("priority")
    private Integer priority;

    /** 用户等级 */
    @TableField("user_level")
    private String userLevel;

    /** 用户 */
    @TableField("user_name")
    private String userName;

    /** 最小重量 */
    @TableField("min_weight")
    private BigDecimal minWeight;

    /** 最大重量 */
    @TableField("max_weight")
    private BigDecimal maxWeight;

    /** 邮编开头 */
    @TableField("zip_prefix")
    private String zipPrefix;

    /** 父级 */
    @TableField("parent_id")
    private Long parentId;

    /** 状态 */
    @TableField("status")
    private Boolean status;

    /** 价格类型：1.公布价 2.销售员底价 3.成本价 */
    @TableField("price_type")
    private Integer priceType;

    /** 创建者 */
    @TableField("create_by")
    private String createBy;

    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 更新者 */
    @TableField("update_by")
    private String updateBy;

    /** 更新时间 */
    @TableField("update_time")
    private LocalDateTime updateTime;
}

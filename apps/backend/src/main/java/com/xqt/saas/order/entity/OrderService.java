package com.xqt.saas.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 服务实体类
 * 对应数据库表：order_service
 */
@Data
@TableName("order_service")
public class OrderService {

    /** 主键ID，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 租户ID */
    @TableField("tenant_id")
    private UUID tenantId;

    /** 服务名称 */
    @TableField("service_name")
    private String serviceName;

    /** 状态：1正常 2停用 */
    @TableField("status")
    private Integer status;

    /** 服务代码 */
    @TableField("service_code")
    private String serviceCode;

    /** 类型 */
    @TableField("type")
    private String type;

    /** 承运类型 */
    @TableField("carrier_type")
    private String carrierType;

    /** 服务分类 */
    @TableField("service_category")
    private String serviceCategory;

    /** 计费方式 */
    @TableField("billing_method")
    private String billingMethod;

    /** 计重方式 */
    @TableField("weight_method")
    private String weightMethod;

    /** 计泡系数 */
    @TableField("bubble_factor")
    private BigDecimal bubbleFactor;

    /** 分泡比例 */
    @TableField("bubble_share")
    private BigDecimal bubbleShare;

    /** 件数范围 */
    @TableField("piece_range")
    private String pieceRange;

    /** 打单账号 */
    @TableField("print_account")
    private String printAccount;

    /** 打印标签类型 */
    @TableField("label_type")
    private String labelType;

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

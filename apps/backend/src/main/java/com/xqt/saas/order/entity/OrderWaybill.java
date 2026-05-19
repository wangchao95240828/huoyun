package com.xqt.saas.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@TableName("order_waybill")
public class OrderWaybill {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("tenant_id")
    private UUID tenantId;

    @TableField("waybill_no")
    private String waybillNo;

    @TableField("type")
    private Integer type;

    @TableField("user_name")
    private String userName;

    @TableField("service")
    private String service;

    @TableField("receiver")
    private String receiver;

    @TableField("country")
    private String country;

    @TableField("piece_count")
    private Integer pieceCount;

    @TableField("bubble_ratio")
    private BigDecimal bubbleRatio;

    @TableField("customer_weight")
    private BigDecimal customerWeight;

    @TableField("customer_volume")
    private BigDecimal customerVolume;

    @TableField("declare_value")
    private BigDecimal declareValue;

    @TableField("actual_weight")
    private BigDecimal actualWeight;

    @TableField("volume_weight")
    private BigDecimal volumeWeight;

    @TableField("volume")
    private BigDecimal volume;

    @TableField("charge_weight")
    private BigDecimal chargeWeight;

    @TableField("customs_method")
    private String customsMethod;

    @TableField("tax_method")
    private String taxMethod;

    @TableField("carrier")
    private String carrier;

    @TableField("main_product")
    private String mainProduct;

    @TableField("product_attr")
    private String productAttr;

    @TableField("remark")
    private String remark;

    @TableField("freight_station")
    private String freightStation;

    @TableField("order_time")
    private LocalDateTime orderTime;

    @TableField("create_by")
    private String createBy;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_by")
    private String updateBy;

    @TableField("update_time")
    private LocalDateTime updateTime;
}

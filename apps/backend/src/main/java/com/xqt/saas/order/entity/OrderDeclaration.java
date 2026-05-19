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
@TableName("order_declaration")
public class OrderDeclaration {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("tenant_id")
    private UUID tenantId;

    @TableField("waybill_id")
    private Long waybillId;

    @TableField("sku")
    private String sku;

    @TableField("chinese_name")
    private String chineseName;

    @TableField("english_name")
    private String englishName;

    @TableField("declare_price")
    private BigDecimal declarePrice;

    @TableField("quantity")
    private Integer quantity;

    @TableField("material")
    private String material;

    @TableField("purpose")
    private String purpose;

    @TableField("model")
    private String model;

    @TableField("product_weight")
    private BigDecimal productWeight;

    @TableField("image_url")
    private String imageUrl;

    @TableField("customs_code")
    private String customsCode;

    @TableField("image")
    private String image;

    @TableField("is_electric")
    private Boolean isElectric;

    @TableField("is_magnetic")
    private Boolean isMagnetic;

    @TableField("product_image")
    private String productImage;

    @TableField("create_by")
    private String createBy;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_by")
    private String updateBy;

    @TableField("update_time")
    private LocalDateTime updateTime;
}

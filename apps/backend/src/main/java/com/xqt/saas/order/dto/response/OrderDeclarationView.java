package com.xqt.saas.order.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class OrderDeclarationView {

    private Long id;
    private UUID tenantId;
    private Long waybillId;
    private String sku;
    private String chineseName;
    private String englishName;
    private BigDecimal declarePrice;
    private Integer quantity;
    private String material;
    private String purpose;
    private String model;
    private BigDecimal productWeight;
    private String imageUrl;
    private String customsCode;
    private String image;
    private Boolean isElectric;
    private Boolean isMagnetic;
    private String productImage;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
}

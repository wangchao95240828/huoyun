package com.xqt.saas.order.dto.request;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderDeclarationSaveRequest {

    private Long id;
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
}

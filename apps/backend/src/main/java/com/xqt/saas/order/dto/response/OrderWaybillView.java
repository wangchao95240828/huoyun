package com.xqt.saas.order.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class OrderWaybillView {

    private Long id;
    private UUID tenantId;
    private String waybillNo;
    private Integer type;
    private String typeText;
    private String userName;
    private String service;
    private String receiver;
    private String country;
    private Integer pieceCount;
    private BigDecimal bubbleRatio;
    private BigDecimal customerWeight;
    private BigDecimal customerVolume;
    private BigDecimal declareValue;
    private BigDecimal actualWeight;
    private BigDecimal volumeWeight;
    private BigDecimal volume;
    private BigDecimal chargeWeight;
    private String customsMethod;
    private String taxMethod;
    private String carrier;
    private String mainProduct;
    private String productAttr;
    private String remark;
    private String freightStation;
    private LocalDateTime orderTime;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;

    private List<OrderRouteView> routes;
    private List<OrderContainerView> containers;
    private List<OrderDeclarationView> declarations;
    private List<OrderAttachmentView> attachments;
    private List<OrderDeliveryView> deliveries;
    private List<OrderWorkOrderView> workOrders;
    private List<OrderPodView> pods;
}

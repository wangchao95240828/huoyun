package com.xqt.saas.order.dto.request;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderWaybillSaveRequest {

    private Long id;
    private String tenantId;
    private String waybillNo;
    private Integer type;
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

    private List<OrderRouteSaveRequest> routes;
    private List<OrderContainerSaveRequest> containers;
    private List<OrderDeclarationSaveRequest> declarations;
    private List<OrderAttachmentSaveRequest> attachments;
    private List<OrderDeliverySaveRequest> deliveries;
    private List<OrderWorkOrderSaveRequest> workOrders;
    private List<OrderPodSaveRequest> pods;
}

package com.xqt.saas.order.dto.request;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderContainerSaveRequest {

    private Long id;
    private Long waybillId;
    private String containerNo;
    private String referenceNo;
    private String customerData;
    private String pickingData;
    private BigDecimal perimeter;
    private String billOfLadingNo;
    private String carrier;
    private Boolean expressMark;
    private Boolean fbaMark;
    private Boolean abnormalFlag;
    private Boolean labelChange;
    private Boolean checkStatus;
    private Boolean checkGoods;
    private Boolean intercept;
    private String freightStation;
    private Integer status;
}

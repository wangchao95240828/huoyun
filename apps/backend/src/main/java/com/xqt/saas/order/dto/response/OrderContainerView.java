package com.xqt.saas.order.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class OrderContainerView {

    private Long id;
    private UUID tenantId;
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
    private String statusText;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
}

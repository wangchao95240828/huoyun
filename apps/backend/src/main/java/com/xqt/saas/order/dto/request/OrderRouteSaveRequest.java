package com.xqt.saas.order.dto.request;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OrderRouteSaveRequest {

    private Long id;
    private Long waybillId;
    private LocalDateTime operationTime;
    private String operationInfo;
    private String remark;
    private String commonAddress;
    private String city;
    private String province;
    private String zipCode;
    private String country;
}

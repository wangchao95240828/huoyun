package com.xqt.saas.order.dto.request;

import lombok.Data;

@Data
public class OrderWaybillQueryRequest {

    private Long pageNum = 1L;
    private Long pageSize = 10L;
    private String waybillNo;
    private Integer type;
    private String userName;
    private String service;
    private String country;
    private String receiver;
}

package com.xqt.saas.order.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class OrderRouteView {

    private Long id;
    private UUID tenantId;
    private Long waybillId;
    private LocalDateTime operationTime;
    private String operationInfo;
    private String remark;
    private String commonAddress;
    private String city;
    private String province;
    private String zipCode;
    private String country;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
}

package com.xqt.saas.order.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class OrderDeliveryView {

    private Long id;
    private UUID tenantId;
    private Long waybillId;
    private String carrierAccount;
    private String uniqueRefNo;
    private String warehouseCode;
    private String receiver;
    private String address1;
    private String address2;
    private String address3;
    private String city;
    private String state;
    private String zipCode;
    private String country;
    private String phone;
    private String email;
    private String boxes;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
}

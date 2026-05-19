package com.xqt.saas.order.dto.request;

import lombok.Data;

@Data
public class OrderDeliverySaveRequest {

    private Long id;
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
}

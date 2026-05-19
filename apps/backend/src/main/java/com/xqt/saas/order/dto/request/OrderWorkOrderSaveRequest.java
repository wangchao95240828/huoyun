package com.xqt.saas.order.dto.request;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OrderWorkOrderSaveRequest {

    private Long id;
    private Long waybillId;
    private String title;
    private String relatedNo;
    private String description;
    private String followers;
    private String responsible;
    private LocalDateTime remindTime;
    private Boolean userVisible;
    private Boolean intercept;
}

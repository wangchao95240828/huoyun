package com.xqt.saas.order.dto.request;

import lombok.Data;

@Data
public class OrderPodSaveRequest {

    private Long id;
    private Long waybillId;
    private String name;
    private String fileName;
    private String remark;
    private Boolean customerVisible;
}

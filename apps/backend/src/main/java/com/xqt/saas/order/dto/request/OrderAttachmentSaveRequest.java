package com.xqt.saas.order.dto.request;

import lombok.Data;

@Data
public class OrderAttachmentSaveRequest {

    private Long id;
    private Long waybillId;
    private String attachmentType;
    private String attachmentUrl;
    private Boolean userVisible;
}

package com.xqt.saas.order.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class OrderAttachmentView {

    private Long id;
    private UUID tenantId;
    private Long waybillId;
    private String attachmentType;
    private String attachmentUrl;
    private Boolean userVisible;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
}

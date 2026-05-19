package com.xqt.saas.order.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class OrderWorkOrderView {

    private Long id;
    private UUID tenantId;
    private Long waybillId;
    private String title;
    private String relatedNo;
    private String description;
    private String followers;
    private String responsible;
    private LocalDateTime remindTime;
    private Boolean userVisible;
    private Boolean intercept;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
}

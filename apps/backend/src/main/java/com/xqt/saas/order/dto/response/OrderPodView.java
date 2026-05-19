package com.xqt.saas.order.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class OrderPodView {

    private Long id;
    private UUID tenantId;
    private Long waybillId;
    private String name;
    private String fileName;
    private String remark;
    private Boolean customerVisible;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
}

package com.xqt.saas.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@TableName("finance_approval")
public class FinanceApprovalType {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private UUID tenantId;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String createdBy;
    private String updatedBy;

    private String type;
    private String associateOrderId;
    private String associateOrderUser;
    private String reason;
    private String feeType;
    private Double feeAmount;
    private Double modifier;
    private Double bubbleModifier;
    private String approver;
    private String applicant;
    private String approvalStatus;
    private String approvalReason;
    private String payStatus;
    private OffsetDateTime happenedAt;
}

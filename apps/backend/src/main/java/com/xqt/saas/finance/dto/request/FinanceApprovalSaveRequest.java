package com.xqt.saas.finance.dto.request;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.With;

import java.time.OffsetDateTime;

@With
public record FinanceApprovalSaveRequest(
        @NotBlank String createdBy,
        @NotBlank String type,
        @NotBlank String associateOrderId,
        @NotBlank String associateOrderUser,
        @NotBlank String reason,
        @NotBlank String feeType,
        @NotNull Double feeAmount,
        @NotNull Double modifier,
        @NotNull Double bubbleModifier,
        @NotBlank String approver,
        @NotBlank String applicant,
        @NotBlank String approvalStatus,
        @NotBlank String approvalReason,
        @NotBlank String payStatus,
        @NotBlank OffsetDateTime happenedAt
) {
}

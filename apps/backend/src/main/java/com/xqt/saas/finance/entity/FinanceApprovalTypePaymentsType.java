package com.xqt.saas.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.apache.poi.hpsf.Decimal;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@TableName("finance_approval_type_payments")
public class FinanceApprovalTypePaymentsType {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private UUID tenantId;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String createdBy;
    private String updatedBy;

    private Long approvalId;
    private String feeTypeCode;
    private Decimal amount;
    private String currencyCode;
    private OffsetDateTime happenedAt;
}

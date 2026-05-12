package com.xqt.saas.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xqt.saas.finance.typehandler.StringListTypeHandler;
import com.xqt.saas.finance.typehandler.UuidListTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@TableName("finance_currency")
public class FinanceApprovalType {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private UUID tenantId;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String createdBy;
    private String updatedBy;

    private UUID creatorId;
    private String approvalType;
    private String reason;
    private String remark;
    @TableField(typeHandler = UuidListTypeHandler.class)
    private List<UUID> ccUserIds;
    @TableField(typeHandler = StringListTypeHandler.class)
    private List<String> fileUrls;
}

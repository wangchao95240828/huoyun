package com.xqt.saas.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@TableName("finance_account")
public class FinanceAccount {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("tenant_id")
    private UUID tenantId;

    @TableField("account_name")
    private String accountName;

    @TableField("currency")
    private String currency;

    @TableField("balance")
    private BigDecimal balance;

    @TableField("bank_name")
    private String bankName;

    @TableField("type")
    private Integer type;

    @TableField("visible")
    private Boolean visible;

    @TableField("remark")
    private String remark;

    @TableField("create_by")
    private String createBy;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_by")
    private String updateBy;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
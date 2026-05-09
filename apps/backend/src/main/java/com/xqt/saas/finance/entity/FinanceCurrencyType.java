package com.xqt.saas.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.ZonedDateTime;

@Data
@TableName("finance_currency")
public class FinanceCurrencyType {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private ZonedDateTime createdAt;
    private String createdBy;
    private ZonedDateTime updatedAt;
    private String updatedBy;

    private String code;
    private String name;
}

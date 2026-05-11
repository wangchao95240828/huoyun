package com.xqt.saas.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@TableName("finance_currency_exchange_rate_history")
public class FinanceCurrencyExchangeRateHistoryType {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private OffsetDateTime createdAt;
    private String createdBy;
    private OffsetDateTime updatedAt;
    private String updatedBy;

    private String code;
    private String applicationScenario;
    private BigDecimal rate;
    private OffsetDateTime effectiveFrom;
}


package com.xqt.saas.finance.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class FinanceFeeTypeView {

    private Long id;

    private String code;

    private String name;

    private String type;

    private BigDecimal price;

    private Boolean isShow;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;
}
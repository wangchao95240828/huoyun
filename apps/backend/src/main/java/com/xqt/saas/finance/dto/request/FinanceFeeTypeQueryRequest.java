package com.xqt.saas.finance.dto.request;

import lombok.Data;

@Data
public class FinanceFeeTypeQueryRequest {

    private Long pageNum = 1L;

    private Long pageSize = 10L;

    private String code;

    private String name;

    private String type;

    private Boolean isShow;
}
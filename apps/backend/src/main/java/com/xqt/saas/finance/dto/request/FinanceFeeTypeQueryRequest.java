package com.xqt.saas.finance.dto.request;

import lombok.Data;

@Data
public class FinanceFeeTypeQueryRequest {

    private String code;

    private String name;

    private String type;

    private Boolean isShow;
}
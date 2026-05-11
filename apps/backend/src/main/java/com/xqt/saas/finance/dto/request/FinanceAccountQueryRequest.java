package com.xqt.saas.finance.dto.request;

import lombok.Data;

@Data
public class FinanceAccountQueryRequest {

    private Long pageNum = 1L;

    private Long pageSize = 10L;

    private String accountName;

    private String currency;

    private String bankName;

    private Integer type;

    private Boolean visible;

    private String remark;
}
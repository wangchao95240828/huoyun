package com.xqt.saas.finance.dto.request;

import lombok.Data;

/**
 * 费用类型查询请求参数
 */
@Data
public class FinanceFeeTypeQueryRequest {

    /** 页码 */
    private Long pageNum = 1L;

    /** 每页条数 */
    private Long pageSize = 10L;

    /** 费用代码 */
    private String code;

    /** 费用名称 */
    private String name;

    /** 类型 */
    private String type;

    /** 是否显示 */
    private Boolean isShow;
}

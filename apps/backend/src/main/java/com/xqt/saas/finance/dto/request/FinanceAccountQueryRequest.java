package com.xqt.saas.finance.dto.request;

import lombok.Data;

/**
 * 账户查询请求参数
 */
@Data
public class FinanceAccountQueryRequest {

    /** 页码 */
    private Long pageNum = 1L;

    /** 每页条数 */
    private Long pageSize = 10L;

    /** 账户名称 */
    private String accountName;

    /** 币种 */
    private String currency;

    /** 开户行 */
    private String bankName;

    /** 类型：1.公司 2.客户 3.供应商 4.员工 */
    private Integer type;

    /** 用户可见 */
    private Boolean visible;

    /** 备注 */
    private String remark;
}

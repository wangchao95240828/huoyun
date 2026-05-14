package com.xqt.saas.finance.dto.request;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户账单查询请求参数
 */
@Data
public class FinanceCustomerBillQueryRequest {

    /** 页码 */
    private Long pageNum = 1L;

    /** 每页条数 */
    private Long pageSize = 10L;

    /** 账单号 */
    private String billNo;

    /** 用户（结算方式） */
    private String userSettle;

    /** 分公司 */
    private String branchCompany;

    /** 币种 */
    private String currency;

    /** 销售代表 */
    private String salesRep;

    /** 客服代表 */
    private String customerServiceRep;

    /** 财务代表 */
    private String financeRep;

    /** 到期动作 */
    private String dueAction;

    /** 账单确认 */
    private Boolean billConfirmed;

    /** 状态：1.待审核 2.待核销 3.已核销 */
    private Integer status;

    /** 账单日期开始 */
    private LocalDateTime billDateStart;

    /** 账单日期结束 */
    private LocalDateTime billDateEnd;

    /** 到期时间开始 */
    private LocalDateTime dueDateStart;

    /** 到期时间结束 */
    private LocalDateTime dueDateEnd;
}

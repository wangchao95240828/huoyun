package com.xqt.saas.finance.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 账户响应视图
 */
@Data
public class FinanceAccountView {

    /** 主键ID */
    private Long id;

    /** 租户ID */
    private UUID tenantId;

    /** 账户名称 */
    private String accountName;

    /** 币种 */
    private String currency;

    /** 余额 */
    private BigDecimal balance;

    /** 开户行 */
    private String bankName;

    /** 类型：1.公司 2.客户 3.供应商 4.员工 */
    private Integer type;

    /** 用户可见 */
    private Boolean visible;

    /** 备注 */
    private String remark;

    /** 创建者 */
    private String createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新者 */
    private String updateBy;

    /** 更新时间 */
    private LocalDateTime updateTime;
}

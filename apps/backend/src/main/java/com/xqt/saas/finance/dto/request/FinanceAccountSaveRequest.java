package com.xqt.saas.finance.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 账户保存/更新请求参数
 */
@Data
public class FinanceAccountSaveRequest {

    /** 主键ID（更新时必填） */
    private Long id;

    /** 租户ID */
    private String tenantId;

    /** 账户名称 */
    @NotBlank(message = "账户名称不能为空")
    private String accountName;

    /** 币种 */
    private String currency;

    /** 余额 */
    private BigDecimal balance;

    /** 开户行 */
    private String bankName;

    /** 类型：1.公司 2.客户 3.供应商 4.员工 */
    @NotNull(message = "类型不能为空")
    private Integer type;

    /** 用户可见 */
    private Boolean visible;

    /** 备注 */
    private String remark;
}

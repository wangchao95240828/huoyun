package com.xqt.saas.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 账户实体类
 * 对应数据库表：finance_account
 */
@Data
@TableName("finance_account")
public class FinanceAccount {

    /** 主键ID，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 租户ID */
    @TableField("tenant_id")
    private UUID tenantId;

    /** 账户名称 */
    @TableField("account_name")
    private String accountName;

    /** 币种 */
    @TableField("currency")
    private String currency;

    /** 余额 */
    @TableField("balance")
    private BigDecimal balance;

    /** 开户行 */
    @TableField("bank_name")
    private String bankName;

    /** 类型：1.公司 2.客户 3.供应商 4.员工 */
    @TableField("type")
    private Integer type;

    /** 用户可见 */
    @TableField("visible")
    private Boolean visible;

    /** 备注 */
    @TableField("remark")
    private String remark;

    /** 创建者 */
    @TableField("create_by")
    private String createBy;

    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 更新者 */
    @TableField("update_by")
    private String updateBy;

    /** 更新时间 */
    @TableField("update_time")
    private LocalDateTime updateTime;
}

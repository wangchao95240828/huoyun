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
 * 费用类型实体类
 * 对应数据库表：finance_fee_type
 */
@Data
@TableName("finance_fee_type")
public class FinanceFeeType {

    /** 主键ID，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 租户ID */
    @TableField("tenant_id")
    private UUID tenantId;

    /** 费用代码 */
    @TableField("code")
    private String code;

    /** 费用名称 */
    @TableField("name")
    private String name;

    /** 类型 */
    @TableField("type")
    private String type;

    /** 价格 */
    @TableField("price")
    private BigDecimal price;

    /** 是否显示 */
    @TableField("is_show")
    private Boolean isShow;

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

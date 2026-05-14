package com.xqt.saas.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 月结单实体类
 * 对应数据库表：finance_monthly_statement
 */
@Data
@TableName("finance_monthly_statement")
public class FinanceMonthlyStatement {

    /** 主键ID，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 租户ID */
    @TableField("tenant_id")
    private UUID tenantId;

    /** 开始时间 */
    @TableField("start_time")
    private LocalDateTime startTime;

    /** 结束时间 */
    @TableField("end_time")
    private LocalDateTime endTime;

    /** 应收（锁定/开启） */
    @TableField("receivable")
    private Boolean receivable;

    /** 应付（锁定/开启） */
    @TableField("payable")
    private Boolean payable;

    /** 销售成本（锁定/开启） */
    @TableField("sales_cost")
    private Boolean salesCost;

    /** 运单（锁定/开启） */
    @TableField("waybill")
    private Boolean waybill;

    /** 提单（锁定/开启） */
    @TableField("bill_of_lading")
    private Boolean billOfLading;

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

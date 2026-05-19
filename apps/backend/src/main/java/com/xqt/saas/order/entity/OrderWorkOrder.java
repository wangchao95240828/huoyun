package com.xqt.saas.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@TableName("order_work_order")
public class OrderWorkOrder {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("tenant_id")
    private UUID tenantId;

    @TableField("waybill_id")
    private Long waybillId;

    @TableField("title")
    private String title;

    @TableField("related_no")
    private String relatedNo;

    @TableField("description")
    private String description;

    @TableField("followers")
    private String followers;

    @TableField("responsible")
    private String responsible;

    @TableField("remind_time")
    private LocalDateTime remindTime;

    @TableField("user_visible")
    private Boolean userVisible;

    @TableField("intercept")
    private Boolean intercept;

    @TableField("create_by")
    private String createBy;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_by")
    private String updateBy;

    @TableField("update_time")
    private LocalDateTime updateTime;
}

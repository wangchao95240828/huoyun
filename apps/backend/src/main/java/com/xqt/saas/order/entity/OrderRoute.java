package com.xqt.saas.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@TableName("order_route")
public class OrderRoute {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("tenant_id")
    private UUID tenantId;

    @TableField("waybill_id")
    private Long waybillId;

    @TableField("operation_time")
    private LocalDateTime operationTime;

    @TableField("operation_info")
    private String operationInfo;

    @TableField("remark")
    private String remark;

    @TableField("common_address")
    private String commonAddress;

    @TableField("city")
    private String city;

    @TableField("province")
    private String province;

    @TableField("zip_code")
    private String zipCode;

    @TableField("country")
    private String country;

    @TableField("create_by")
    private String createBy;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_by")
    private String updateBy;

    @TableField("update_time")
    private LocalDateTime updateTime;
}

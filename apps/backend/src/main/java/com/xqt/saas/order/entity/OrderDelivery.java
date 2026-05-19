package com.xqt.saas.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@TableName("order_delivery")
public class OrderDelivery {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("tenant_id")
    private UUID tenantId;

    @TableField("waybill_id")
    private Long waybillId;

    @TableField("carrier_account")
    private String carrierAccount;

    @TableField("unique_ref_no")
    private String uniqueRefNo;

    @TableField("warehouse_code")
    private String warehouseCode;

    @TableField("receiver")
    private String receiver;

    @TableField("address1")
    private String address1;

    @TableField("address2")
    private String address2;

    @TableField("address3")
    private String address3;

    @TableField("city")
    private String city;

    @TableField("state")
    private String state;

    @TableField("zip_code")
    private String zipCode;

    @TableField("country")
    private String country;

    @TableField("phone")
    private String phone;

    @TableField("email")
    private String email;

    @TableField("boxes")
    private String boxes;

    @TableField("create_by")
    private String createBy;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_by")
    private String updateBy;

    @TableField("update_time")
    private LocalDateTime updateTime;
}

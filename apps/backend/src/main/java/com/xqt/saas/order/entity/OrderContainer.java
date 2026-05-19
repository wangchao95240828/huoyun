package com.xqt.saas.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@TableName("order_container")
public class OrderContainer {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("tenant_id")
    private UUID tenantId;

    @TableField("waybill_id")
    private Long waybillId;

    @TableField("container_no")
    private String containerNo;

    @TableField("reference_no")
    private String referenceNo;

    @TableField("customer_data")
    private String customerData;

    @TableField("picking_data")
    private String pickingData;

    @TableField("perimeter")
    private BigDecimal perimeter;

    @TableField("bill_of_lading_no")
    private String billOfLadingNo;

    @TableField("carrier")
    private String carrier;

    @TableField("express_mark")
    private Boolean expressMark;

    @TableField("fba_mark")
    private Boolean fbaMark;

    @TableField("abnormal_flag")
    private Boolean abnormalFlag;

    @TableField("label_change")
    private Boolean labelChange;

    @TableField("check_status")
    private Boolean checkStatus;

    @TableField("check_goods")
    private Boolean checkGoods;

    @TableField("intercept")
    private Boolean intercept;

    @TableField("freight_station")
    private String freightStation;

    @TableField("status")
    private Integer status;

    @TableField("create_by")
    private String createBy;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_by")
    private String updateBy;

    @TableField("update_time")
    private LocalDateTime updateTime;
}

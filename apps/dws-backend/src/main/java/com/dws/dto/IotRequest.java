package com.dws.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class IotRequest {

    private String action;

    @JsonProperty("item_number")
    private String itemNumber;

    @JsonProperty("shipment_number")
    private String shipmentNumber;

    private String time;
    private String token;

    private BigDecimal weight;
    private BigDecimal length;
    private BigDecimal width;
    private BigDecimal height;

    @JsonProperty("pic_base64")
    private String picBase64;

    @JsonProperty("pic_url")
    private String picUrl;

    private Map<String, Object> ext;
}

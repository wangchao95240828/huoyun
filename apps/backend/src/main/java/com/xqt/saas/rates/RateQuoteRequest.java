package com.xqt.saas.rates;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 对应 acc/api/APIClass.php?act=Price + acc/config/Freight.php::getFee 入参。
 *
 * ACC 老入参 PHP 命名 → 本类字段映射：
 *   $Product       → channelCode / serviceCode（新模型把 Product 拆开）
 *   $Type          → shipmentType（0 普货 / 1 特货）
 *   $BatteryType   → batteryType（0 无 / 1 内置 / 2 干电池）
 *   $SpecialType   → specialType（5 仿牌 / 0 一般）
 *   $Country       → countryCode
 *   $Postcode      → postalCode
 *   $WeightList    → weightKg + pieces + volumeCbm
 *   $DeclareValue  → declaredValue
 *   $ChannelAccount→ channelAccountCode
 *
 * customerId / customerGroupId 是 ACC PHP 实际从 session 取的 $this->Customer，
 * 本系统改为显式入参，方便客户外部 API 走签名调用时携带。
 */
public record RateQuoteRequest(
    String customerId,
    String customerGroupId,
    String channelCode,
    String serviceCode,
    String channelAccountCode,
    String countryCode,
    String postalCode,
    BigDecimal weightKg,
    Integer pieces,
    BigDecimal volumeCbm,
    BigDecimal declaredValue,
    String currency,
    LocalDate chargeDate,
    Integer batteryType,
    Integer specialType,
    Integer shipmentType
) {
    public RateQuoteRequest {
        if (pieces == null) {
            pieces = 1;
        }
        if (currency == null || currency.isBlank()) {
            currency = "CNY";
        }
        if (chargeDate == null) {
            chargeDate = LocalDate.now();
        }
        if (batteryType == null) batteryType = 0;
        if (specialType == null) specialType = 0;
        if (shipmentType == null) shipmentType = 0;
    }

    /** 旧 5 参构造：客户外部 API 走 PHP 兼容路径时用。 */
    public static RateQuoteRequest legacy(String channelCode, String countryCode, String postalCode,
                                          BigDecimal weightKg, BigDecimal volumeCbm,
                                          BigDecimal declaredValue, String currency,
                                          LocalDate chargeDate) {
        return new RateQuoteRequest(null, null, channelCode, null, null,
            countryCode, postalCode, weightKg, 1, volumeCbm, declaredValue, currency, chargeDate,
            0, 0, 0);
    }
}

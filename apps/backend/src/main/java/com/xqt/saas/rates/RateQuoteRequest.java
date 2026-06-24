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
    Integer shipmentType,
    // ════ W1 (调研结论): UPS Rating.yaml 一等 indicator 字段, 触发对应 surcharge ════
    /** ShipTo 是住宅地址 (触发 Residential Surcharge, ACC isHome 对齐) */
    Boolean residentialAddress,
    /** 包裹超大 (UPS Worldwide Economy DDU 触发 Oversize 附加费) */
    Boolean oversize,
    /** 大包裹 (触发 Large Package Surcharge — 单边 > 96in 或 girth+length > 130in) */
    Boolean largePackage,
    /** 超长/超重特殊处理 (Additional Handling Surcharge — 一边 > 48in 或重 > 50lb) */
    Boolean additionalHandling,
    /** 签收方式: NONE / STANDARD / ADULT */
    String signatureType,
    /** 周六派送 (Saturday Delivery 附加费) */
    Boolean saturdayDelivery,
    /** 退货标签 (额外 PRP/UPS Return Service 费) */
    Boolean returnService
) {
    public RateQuoteRequest {
        if (pieces == null) pieces = 1;
        if (currency == null || currency.isBlank()) currency = "CNY";
        if (chargeDate == null) chargeDate = LocalDate.now();
        if (batteryType == null) batteryType = 0;
        if (specialType == null) specialType = 0;
        if (shipmentType == null) shipmentType = 0;
        if (residentialAddress == null) residentialAddress = false;
        if (oversize == null) oversize = false;
        if (largePackage == null) largePackage = false;
        if (additionalHandling == null) additionalHandling = false;
        if (signatureType == null || signatureType.isBlank()) signatureType = "NONE";
        if (saturdayDelivery == null) saturdayDelivery = false;
        if (returnService == null) returnService = false;
    }

    /** 16 参兼容老 callsite (不传 7 个 W1 indicator) */
    public RateQuoteRequest(String customerId, String customerGroupId, String channelCode,
                            String serviceCode, String channelAccountCode, String countryCode,
                            String postalCode, BigDecimal weightKg, Integer pieces, BigDecimal volumeCbm,
                            BigDecimal declaredValue, String currency, LocalDate chargeDate,
                            Integer batteryType, Integer specialType, Integer shipmentType) {
        this(customerId, customerGroupId, channelCode, serviceCode, channelAccountCode, countryCode,
             postalCode, weightKg, pieces, volumeCbm, declaredValue, currency, chargeDate,
             batteryType, specialType, shipmentType,
             false, false, false, false, "NONE", false, false);
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

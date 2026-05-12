package com.xqt.saas.rates;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 对应 acc/api/APIClass.php?act=Price 的请求体。
 * 旧字段 Type/Battery/Special 暂作为对照保留，本期不参与计费，仅放入 metadata。
 */
public record RateQuoteRequest(
    String channelCode,
    String countryCode,
    String postalCode,
    BigDecimal weightKg,
    Integer pieces,
    BigDecimal volumeCbm,
    BigDecimal declaredValue,
    String currency,
    LocalDate chargeDate
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
    }
}

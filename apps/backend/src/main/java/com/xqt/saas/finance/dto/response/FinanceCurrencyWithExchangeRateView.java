package com.xqt.saas.finance.dto.response;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

public record FinanceCurrencyWithExchangeRateView(
        Long id,
        String code,
        String name,
        BigDecimal fromRate,
        ZonedDateTime fromRateEffectiveFrom,
        BigDecimal toRate,
        ZonedDateTime toRateEffectiveFrom
) {
}

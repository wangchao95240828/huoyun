package com.xqt.saas.finance.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record FinanceCurrencyWithExchangeRateView(
        Long id,
        String code,
        String name,
        BigDecimal fromRate,
        OffsetDateTime fromRateEffectiveFrom,
        BigDecimal toRate,
        OffsetDateTime toRateEffectiveFrom
) {
}

package com.xqt.saas.finance.dto.response;


import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record FinanceCurrencyExchangeRateHistoryView(
        Long id,
        String code,
        String applicationScenario,
        BigDecimal rate,
        OffsetDateTime effectiveFrom
) {
}

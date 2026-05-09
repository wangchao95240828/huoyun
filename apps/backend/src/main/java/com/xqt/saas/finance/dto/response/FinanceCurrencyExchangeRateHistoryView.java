package com.xqt.saas.finance.dto.response;


import java.math.BigDecimal;
import java.time.ZonedDateTime;

public record FinanceCurrencyExchangeRateHistoryView(
        Long id,
        String code,
        String applicationScenario,
        BigDecimal rate,
        ZonedDateTime effectiveFrom
) {
}

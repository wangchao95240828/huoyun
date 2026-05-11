package com.xqt.saas.finance.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record FinanceCurrencyExchangeRateHistorySaveRequest(
        @NotBlank String createdBy,
        @Pattern(regexp = "^(应收|应付)$") String applicationScenario,
        @NotNull @Positive BigDecimal rate,
        @NotNull OffsetDateTime effectiveFrom
) {
}

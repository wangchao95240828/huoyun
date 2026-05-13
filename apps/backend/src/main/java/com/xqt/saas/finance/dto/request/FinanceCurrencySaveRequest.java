package com.xqt.saas.finance.dto.request;

import jakarta.validation.constraints.NotBlank;

public record FinanceCurrencySaveRequest(
        @NotBlank String createdBy,
        @NotBlank String code,
        @NotBlank String name
) {
}

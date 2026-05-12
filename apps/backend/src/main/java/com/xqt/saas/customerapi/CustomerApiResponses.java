package com.xqt.saas.customerapi;

import java.math.BigDecimal;
import java.util.List;

public final class CustomerApiResponses {
    private CustomerApiResponses() {
    }

    public record BalanceLine(
        String currency,
        String accountName,
        BigDecimal balance
    ) {
    }

    public record BalanceList(
        String customerCode,
        List<BalanceLine> data
    ) {
    }

    public record PreOrderResult(
        String orderId,
        String orderNo,
        String status,
        String customerCode
    ) {
    }
}

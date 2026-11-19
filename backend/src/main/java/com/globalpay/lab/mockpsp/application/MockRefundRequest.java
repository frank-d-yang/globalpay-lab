package com.globalpay.lab.mockpsp.application;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;

public record MockRefundRequest(
        UUID refundId,
        UUID paymentId,
        BigDecimal amount,
        Currency currency
) {
}


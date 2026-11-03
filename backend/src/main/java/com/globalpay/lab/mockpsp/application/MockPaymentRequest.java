package com.globalpay.lab.mockpsp.application;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;

public record MockPaymentRequest(
        UUID paymentId,
        String operationId,
        BigDecimal amount,
        Currency currency
) {
}

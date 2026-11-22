
package com.globalpay.lab.payment.api.dto;

import com.globalpay.lab.payment.domain.PaymentIntent;
import com.globalpay.lab.payment.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID merchantId,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        String providerReference,
        Instant createdAt,
        Instant updatedAt
) {

    public static PaymentResponse from(PaymentIntent payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getMerchantId(),
                payment.getAmount(),
                payment.getCurrency().getCurrencyCode(),
                payment.getStatus(),
                payment.getProviderReference(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}

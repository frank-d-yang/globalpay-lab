package com.globalpay.lab.refund.api.dto;

import com.globalpay.lab.refund.domain.Refund;
import com.globalpay.lab.refund.domain.RefundStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RefundResponse(
        UUID id,
        UUID paymentId,
        BigDecimal amount,
        String currency,
        RefundStatus status,
        Instant createdAt
) {

    public static RefundResponse from(Refund refund) {
        return new RefundResponse(
                refund.getId(),
                refund.getPaymentId(),
                refund.getAmount(),
                refund.getCurrency().getCurrencyCode(),
                refund.getStatus(),
                refund.getCreatedAt()
        );
    }
}

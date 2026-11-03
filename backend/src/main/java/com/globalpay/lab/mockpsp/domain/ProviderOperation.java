
package com.globalpay.lab.mockpsp.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

public class ProviderOperation {

    private final UUID id;
    private final UUID paymentId;
    private final String operationId;
    private final BigDecimal amount;
    private final Currency currency;
    private final Instant createdAt;

    private ProviderOperationStatus status;
    private String providerReference;
    private Instant updatedAt;

    public ProviderOperation(
            UUID id,
            UUID paymentId,
            String operationId,
            BigDecimal amount,
            Currency currency,
            ProviderOperationStatus status,
            String providerReference,
            Instant createdAt,
            Instant updatedAt) {

        this.id = Objects.requireNonNull(id);
        this.paymentId = Objects.requireNonNull(paymentId);
        this.operationId = Objects.requireNonNull(operationId);
        this.amount = Objects.requireNonNull(amount);
        this.currency = Objects.requireNonNull(currency);
        this.status = Objects.requireNonNull(status);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);

        if (operationId.isBlank() || operationId.length() > 128) {
            throw new IllegalArgumentException("Invalid operation ID");
        }

        if (amount.signum() <= 0
                || amount.scale() > 8
                || amount.precision() - amount.scale() > 16) {
            throw new IllegalArgumentException("Invalid payment amount");
        }

        if (providerReference != null
                && (providerReference.isBlank()
                || providerReference.length() > 128)) {
            throw new IllegalArgumentException("Invalid provider reference");
        }

        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException(
                    "Updated time cannot precede creation time"
            );
        }

        this.providerReference = providerReference;
    }

    public void succeed(String providerReference) {
        requireProcessing();
        validateReference(providerReference);

        this.status = ProviderOperationStatus.SUCCEEDED;
        this.providerReference = providerReference;
        this.updatedAt = Instant.now();
    }

    public void decline(String providerReference) {
        requireProcessing();
        validateReference(providerReference);

        this.status = ProviderOperationStatus.DECLINED;
        this.providerReference = providerReference;
        this.updatedAt = Instant.now();
    }

    private void requireProcessing() {
        if (status != ProviderOperationStatus.PROCESSING) {
            throw new IllegalStateException(
                    "Provider operation is not processing: " + status
            );
        }
    }

    private void validateReference(String reference) {
        if (reference == null
                || reference.isBlank()
                || reference.length() > 128) {
            throw new IllegalArgumentException(
                    "Invalid provider reference"
            );
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public String getOperationId() {
        return operationId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Currency getCurrency() {
        return currency;
    }

    public ProviderOperationStatus getStatus() {
        return status;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

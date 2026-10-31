package com.globalpay.lab.payment.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

public class PaymentIntent {

    private final UUID id;
    private final UUID merchantId;
    private final BigDecimal amount;
    private final Currency currency;
    private final String idempotencyKey;
    private final Instant createdAt;

    private PaymentStatus status;
    private String providerReference;
    private Instant updatedAt;

    public PaymentIntent(
            UUID id,
            UUID merchantId,
            BigDecimal amount,
            Currency currency,
            String idempotencyKey,
            PaymentStatus status,
            String providerReference,
            Instant createdAt,
            Instant updatedAt) {

        this.id = Objects.requireNonNull(id);
        this.merchantId = Objects.requireNonNull(merchantId);
        this.currency = Objects.requireNonNull(currency);
        this.status = Objects.requireNonNull(status);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);

        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Payment amount must be positive"
            );
        }

        if (idempotencyKey == null ||
                idempotencyKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Idempotency key must not be blank"
            );
        }

        if (providerReference != null &&
                providerReference.isBlank()) {
            throw new IllegalArgumentException(
                    "Provider reference must not be blank"
            );
        }

        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException(
                    "Updated time cannot precede creation time"
            );
        }

        this.amount = amount;
        this.idempotencyKey = idempotencyKey.trim();
        this.providerReference = providerReference;
    }

    public static PaymentIntent create(
            UUID merchantId,
            BigDecimal amount,
            Currency currency,
            String idempotencyKey) {

        Instant now = Instant.now();

        return new PaymentIntent(
                UUID.randomUUID(),
                merchantId,
                amount,
                currency,
                idempotencyKey,
                PaymentStatus.CREATED,
                null,
                now,
                now
        );
    }

    public void startProcessing() {
        requireStatus(PaymentStatus.CREATED);

        status = PaymentStatus.PROCESSING;
        updatedAt = Instant.now();
    }

    public void succeed(
            boolean providerSuccessConfirmed,
            boolean ledgerPosted) {

        requireStatus(PaymentStatus.PROCESSING);

        if (!providerSuccessConfirmed || !ledgerPosted) {
            throw new IllegalStateException(
                    "Payment provider success and ledger posting " +
                            "must both be confirmed"
            );
        }

        status = PaymentStatus.SUCCEEDED;
        updatedAt = Instant.now();
    }

    public void fail(boolean providerDeclineConfirmed) {
        requireStatus(PaymentStatus.PROCESSING);

        if (!providerDeclineConfirmed) {
            throw new IllegalStateException(
                    "Provider decline must be confirmed"
            );
        }

        status = PaymentStatus.FAILED;
        updatedAt = Instant.now();
    }

    private void requireStatus(PaymentStatus expected) {
        if (status != expected) {
            throw new IllegalStateException(
                    "Invalid payment status transition: " + status
            );
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Currency getCurrency() {
        return currency;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public PaymentStatus getStatus() {
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

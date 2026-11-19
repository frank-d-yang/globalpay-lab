package com.globalpay.lab.refund.domain;

import com.globalpay.lab.payment.domain.PaymentIntent;
import com.globalpay.lab.payment.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

public class Refund {

    private final UUID id;
    private final UUID paymentId;
    private final UUID merchantId;
    private final BigDecimal amount;
    private final Currency currency;
    private final Instant createdAt;

    private RefundStatus status;
    private String providerReference;
    private Instant updatedAt;

    public Refund(
            UUID id,
            UUID paymentId,
            UUID merchantId,
            BigDecimal amount,
            Currency currency,
            RefundStatus status,
            String providerReference,
            Instant createdAt,
            Instant updatedAt) {

        this.id = Objects.requireNonNull(id);
        this.paymentId = Objects.requireNonNull(paymentId);
        this.merchantId = Objects.requireNonNull(merchantId);
        this.currency = Objects.requireNonNull(currency);
        this.status = Objects.requireNonNull(status);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);

        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Refund amount must be positive"
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
        this.providerReference = providerReference;
    }

    public static Refund create(PaymentIntent payment) {

        Objects.requireNonNull(payment);

        if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
            throw new IllegalStateException(
                    "Only successful payments can be refunded"
            );
        }

        Instant now = Instant.now();

        return new Refund(
                UUID.randomUUID(),
                payment.getId(),
                payment.getMerchantId(),
                payment.getAmount(),
                payment.getCurrency(),
                RefundStatus.CREATED,
                null,
                now,
                now
        );
    }

    public void startProcessing() {
        requireStatus(RefundStatus.CREATED);

        status = RefundStatus.PROCESSING;
        updatedAt = Instant.now();
    }

    public void confirmProviderSuccess(String reference) {
        requireStatus(RefundStatus.PROCESSING);

        if (reference == null ||
                reference.isBlank() ||
                reference.length() > 128) {
            throw new IllegalArgumentException(
                    "Invalid provider reference"
            );
        }

        if (providerReference != null &&
                !providerReference.equals(reference)) {
            throw new IllegalStateException(
                    "Conflicting provider reference"
            );
        }

        providerReference = reference;
        updatedAt = Instant.now();
    }

    public void succeed(
            boolean providerSuccessConfirmed,
            boolean reversalPosted) {

        requireStatus(RefundStatus.PROCESSING);

        if (!providerSuccessConfirmed || !reversalPosted) {
            throw new IllegalStateException(
                    "Provider success and reversal posting " +
                            "must both be confirmed"
            );
        }

        status = RefundStatus.SUCCEEDED;
        updatedAt = Instant.now();
    }

    public void fail(boolean providerDeclineConfirmed) {
        requireStatus(RefundStatus.PROCESSING);

        if (!providerDeclineConfirmed) {
            throw new IllegalStateException(
                    "Provider decline must be confirmed"
            );
        }

        status = RefundStatus.FAILED;
        updatedAt = Instant.now();
    }

    private void requireStatus(RefundStatus expected) {
        if (status != expected) {
            throw new IllegalStateException(
                    "Invalid refund status transition: " + status
            );
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getPaymentId() {
        return paymentId;
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

    public RefundStatus getStatus() {
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


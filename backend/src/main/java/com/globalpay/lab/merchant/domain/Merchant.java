package com.globalpay.lab.merchant.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class Merchant {
    private final UUID id;
    private final String displayName;
    private final MerchantStatus status;
    private final Instant createdAt;

    public Merchant(
            UUID id,
            String displayName,
            MerchantStatus status,
            Instant createdAt) {

        this.id = Objects.requireNonNull(id);

        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException(
                    "Merchant display name must not be blank"
            );
        }

        this.displayName = displayName.trim();
        this.status = Objects.requireNonNull(status);
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public UUID getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public MerchantStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

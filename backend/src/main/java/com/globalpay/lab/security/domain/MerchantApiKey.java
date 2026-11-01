package com.globalpay.lab.security.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class MerchantApiKey {
    private final UUID id;
    private final UUID merchantId;
    private final String keyHash;
    private final ApiKeyStatus status;
    private final Instant createdAt;
    private final Instant revokedAt;

    public MerchantApiKey(
            UUID id,
            UUID merchantId,
            String keyHash,
            ApiKeyStatus status,
            Instant createdAt,
            Instant revokedAt) {

        this.id = Objects.requireNonNull(id);
        this.merchantId = Objects.requireNonNull(merchantId);
        this.status = Objects.requireNonNull(status);
        this.createdAt = createdAt;

        if (keyHash == null ||
                !keyHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "Invalid API key hash"
            );
        }

        if ((status == ApiKeyStatus.ACTIVE && revokedAt != null)
                || (status == ApiKeyStatus.REVOKED && revokedAt == null)
                || (revokedAt != null
                && revokedAt.isBefore(createdAt))) {
            throw new IllegalArgumentException(
                    "Invalid API key revocation state"
            );
        }

        this.keyHash = keyHash;
        this.revokedAt = revokedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public String getKeyHash() {
        return keyHash;
    }

    public ApiKeyStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}


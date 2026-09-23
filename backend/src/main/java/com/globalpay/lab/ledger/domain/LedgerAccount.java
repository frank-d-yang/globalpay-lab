package com.globalpay.lab.ledger.domain;

import java.time.Instant;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

public class LedgerAccount {
    private final UUID id;
    private final OwnerType ownerType;
    private final String ownerId;
    private final Currency currency;
    private final AccountType accountType;
    private AccountStatus status;
    private final Instant createdAt;

    public LedgerAccount(
            UUID id,
            OwnerType ownerType,
            String ownerId,
            Currency currency,
            AccountType accountType,
            AccountStatus status,
            Instant createdAt) {

        this.id = Objects.requireNonNull(id);
        this.ownerType = Objects.requireNonNull(ownerType);
        this.ownerId = Objects.requireNonNull(ownerId);
        this.currency = Objects.requireNonNull(currency);
        this.accountType = Objects.requireNonNull(accountType);
        this.status = Objects.requireNonNull(status);
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public UUID getId() {
        return id;
    }

    public OwnerType getOwnerType() {
        return ownerType;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public Currency getCurrency() {
        return currency;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

}

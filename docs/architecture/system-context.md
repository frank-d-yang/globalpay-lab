# System Context

```mermaid
flowchart LR
    MD[Merchant Developer]
    FO[Finance Operations]
    RO[Risk Operations]
    GP[GlobalPay Lab]
    PP[Mock Payment Provider]
    BF[External Settlement File]
    WH[Merchant Webhook Endpoint]

    MD -->|Payment API and API key| GP
    FO -->|Operations dashboard| GP
    RO -->|Risk investigation| GP
    GP -->|Authorise and process| PP
    PP -->|Provider result| GP
    BF -->|CSV settlement data| GP
    GP -->|Signed payment events| WH
```

## Platform responsibility

GlobalPay Lab owns:

- payment intent and lifecycle;
- internal wallet and ledger records;
- idempotency;
- provider orchestration;
- webhook delivery history;
- settlement batches;
- reconciliation results;
- risk and audit records.

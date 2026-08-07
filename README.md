# GlobalPay Lab

**Cross-border Payments, Ledger & Automated Reconciliation Platform**

GlobalPay Lab is a sandbox FinTech platform for learning and demonstrating how
cross-border payments are created, processed, recorded, settled, reconciled,
audited, and explained to enterprise customers.

> Current status: Week 0 — project definition and architecture

## Product scenario

Frank Global Commerce Pty Ltd operates across Australia, Singapore, and the
United States. The company needs to:

- accept payments in AUD, SGD, and USD;
- manage multi-currency wallet balances;
- prevent duplicate payment processing;
- maintain an auditable double-entry ledger;
- receive payment-status webhooks;
- reconcile internal records with external settlement files;
- investigate payment and reconciliation exceptions.

## MVP capabilities

- Enterprise customer and API key management
- Multi-currency wallets
- Double-entry ledger
- Payment lifecycle and state machine
- Idempotent payment APIs
- Refunds and ledger reversals
- Mock payment provider
- Signed webhooks with retries
- Settlement batches
- CSV-based automated reconciliation
- Risk alerts and audit trail
- React operations dashboard
- OpenAPI documentation
- Dockerised deployment

## Initial architecture

```text
React + TypeScript
        ↓
Spring Boot modular monolith
        ↓
PostgreSQL
```

Planned modules:

```text
identity
customer
wallet
ledger
payment
provider
webhook
settlement
reconciliation
risk
audit
```

## Milestones

| Milestone | Target date | Outcome |
|---|---:|---|
| M1 — Ledger Core | 2026-09-06 | Balanced journal posting and wallet balances |
| M2 — Payment MVP v0.1 | 2026-10-04 | End-to-end sandbox payment flow |
| M3 — Job-ready MVP v0.2 | 2026-11-01 | Reconciliation, risk and operations dashboard |
| M4 — Deployed Release v1.0 | 2026-11-29 | Public sandbox, documentation and English demo |

## Documentation

- [Project Charter](docs/product/project-charter.md)
- [MVP Scope](docs/product/mvp-scope.md)
- [Out of Scope](docs/product/out-of-scope.md)
- [Roadmap](docs/product/roadmap.md)
- [System Context](docs/architecture/system-context.md)
- [Payment Flow](docs/architecture/payment-flow.md)
- [Payment Ecosystem Notes](docs/domain/payment-ecosystem.md)
- [Week 0 Checklist](docs/product/week0-checklist.md)

## Disclaimer

This project is an educational sandbox. It does not process real money, store
real card data, provide regulated financial services, or claim production
compliance.

# Project Charter

## Project

**GlobalPay Lab — Cross-border Payments, Ledger & Automated Reconciliation Platform**

## Purpose

Build a sandbox FinTech platform that demonstrates how enterprise payment
systems manage multi-currency balances, payment processing, double-entry
ledgering, settlement, reconciliation, risk operations, and customer-facing
integrations.

## Problem statement

Cross-border merchants may accept payments through multiple providers and
currencies. A successful checkout does not solve the operational problems that
follow:

- duplicate requests and uncertain payment outcomes;
- inconsistent internal and external transaction records;
- settlement delays and provider fees;
- refunds and financial corrections;
- webhook failures;
- auditability and operational investigation.

GlobalPay Lab models these problems in a controlled sandbox.

## Target users

1. **Merchant developer** — integrates through APIs and webhooks.
2. **Finance operations analyst** — monitors balances, settlements, and
   reconciliation exceptions.
3. **Risk operations analyst** — reviews rule-based alerts.
4. **Platform administrator** — manages customers, API access, and audit events.

## Objectives

- Demonstrate Java and Spring Boot backend engineering.
- Learn payment, ledger, settlement, and reconciliation concepts.
- Apply idempotency, state machines, retries, audit trails, and concurrency control.
- Build a customer-facing API and developer experience.
- Produce a portfolio project relevant to FinTech, payments, integration, and
  future Solutions Architect roles.

## Success criteria

The project is complete when:

- the sandbox is publicly deployed;
- an enterprise customer can execute a complete payment scenario;
- every financial journal remains balanced;
- duplicate API requests do not create duplicate payments;
- refunds create reversal entries instead of mutating history;
- settlement files can be imported and reconciled;
- automated tests cover core financial invariants;
- OpenAPI documentation and an English demo are available.

## Constraints

- Delivery target: 12–16 weeks.
- Architecture starts as a modular monolith.
- No real money, card data, bank connection, or regulated activity.
- New ideas enter the Future Backlog unless they replace an existing MVP item.

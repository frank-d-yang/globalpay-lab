# Payment Ecosystem — Week 0 Notes

| Term | Meaning | Responsibility |
|---|---|---|
| Customer | The person or business paying | Initiates payment |
| Merchant | The business receiving payment | Sells goods or services |
| Digital wallet | Apple Pay, Google Pay, etc. | Presents tokenised credentials |
| Payment gateway | Secure integration layer | Transfers payment data |
| PSP | Payment service provider | Helps merchants accept and manage payments |
| Acquirer | Merchant-side financial institution | Processes merchant transactions |
| Card network | Visa, Mastercard, etc. | Routes card messages |
| Issuer | Customer-side financial institution | Approves or declines payment |
| Ledger | Authoritative financial record | Records obligations and balances |
| Settlement | Transfer of net funds | Moves money after processing |
| Reconciliation | Comparison of records | Finds mismatches and missing items |

## Simplified payment path

```text
Customer
  → Apple Pay / Google Pay / card
  → Merchant checkout
  → Payment gateway or PSP
  → Acquirer
  → Visa / Mastercard network
  → Issuer
  → Authorisation result
  → Clearing and settlement
  → Merchant ledger and reconciliation
```

## Questions for Week 1

1. What is the difference between authorisation and settlement?
2. Why is a customer wallet balance a liability for a payment platform?
3. Why must a payment API be idempotent?
4. Why can platform records disagree with settlement records?
5. What evidence is needed to investigate a reconciliation mismatch?

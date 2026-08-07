# Initial API Draft

## Customers

- `POST /api/v1/customers`
- `GET /api/v1/customers/{customerId}`

## Wallets

- `POST /api/v1/customers/{customerId}/wallets`
- `GET /api/v1/wallets/{walletId}`
- `GET /api/v1/wallets/{walletId}/balances`

## Payments

- `POST /api/v1/payments`
- `GET /api/v1/payments/{paymentId}`
- `POST /api/v1/payments/{paymentId}/refunds`

## Webhooks

- `POST /api/v1/webhook-endpoints`
- `GET /api/v1/webhook-deliveries`
- `POST /api/v1/webhook-deliveries/{deliveryId}/retry`

## Reconciliation

- `POST /api/v1/reconciliation/files`
- `GET /api/v1/reconciliation/runs/{runId}`
- `GET /api/v1/reconciliation/exceptions`

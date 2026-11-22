# GlobalPay Lab — Sandbox Payment Operations

A read-only React interface for inspecting payment transactions in the GlobalPay Lab sandbox environment.

## Features

- View merchant-scoped payment transactions
- Inspect payment details and provider references
- Display payment statuses (CREATED, PROCESSING, SUCCEEDED, FAILED)
- Navigate payments using pagination
- Handle loading, empty, and error states
- Retry failed data requests

## Tech Stack

- React
- TypeScript
- Vite
- Spring Boot REST API
- PostgreSQL

## Local Development

### 1. Start PostgreSQL

From the project root:

```bash
docker compose up -d
```

### 2. Start Spring Boot

From the `backend` directory:

```bash
./mvnw spring-boot:run
```

### 3. Configure the frontend

Create `frontend/.env.local`:

```dotenv
SANDBOX_MERCHANT_API_KEY=<your-local-sandbox-key>
```

Use an API key issued for a merchant in the local development database.

Never commit API keys or credentials to the repository.

### 4. Start React

From the `frontend` directory:

```bash
npm ci
npm run dev
```

Vite forwards local payment API requests to Spring Boot and injects the sandbox API key on the development server.

## Verification

```bash
npm run lint
npm run build
```

## Security and Limitations

- This is a local sandbox demonstration, not a production merchant portal.
- The current authentication approach is for local development only.
- Do not expose the development proxy publicly.
- Use only disposable sandbox credentials.
- The interface is read-only and does not initiate payments or refunds.
- Production deployment requires a proper authentication and authorization architecture.

## Future Improvements

- Merchant Portal authentication and authorization
- Wallet balance and refund information
- Production-ready API access architecture

# Ledger Core API Reference

> GlobalPay Lab · M1 — Ledger Core  
> Scope: implemented account APIs and internal journal operations only.  
> Source of truth: `LedgerAccountController`, its DTOs, `JournalService`, and the current response/exception handlers on `dev`.

## 1. Overview

The Ledger Core currently exposes **five REST endpoints**, all under:

```text
/api/v1/ledger/accounts
```

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `POST` | `/api/v1/ledger/accounts` | Create a ledger account |
| `GET` | `/api/v1/ledger/accounts/{id}` | Retrieve an account |
| `GET` | `/api/v1/ledger/accounts` | List all accounts |
| `GET` | `/api/v1/ledger/accounts/{id}/balance` | Calculate an account's posted balance |
| `GET` | `/api/v1/ledger/accounts/{id}/journals` | Retrieve an account's posted journal-line history |

**Important:** Journal draft creation and posting are implemented in `JournalService`, but there are **no public Journal REST endpoints** in this milestone. The Java examples in Section 7 describe internal service usage, not HTTP requests.

Examples assume the backend is available at `http://localhost:8080` (Spring Boot's default port unless overridden). UUIDs and timestamps in sample responses are illustrative.

## 2. Response Format

The application returns a common `Response<T>` object:

```json
{
  "code": 200,
  "message": "Success",
  "data": {}
}
```

- `code`: application-level result code.
- `message`: application-level result description.
- `data`: response payload; omitted when null (`@JsonInclude(NON_NULL)`).

**Current implementation detail:** the controller and exception handler return `Response<T>` directly. For the handled validation and business-error cases shown below, the **HTTP status is 200** while the JSON `code` can be `400` or `404`. Always examine the response body; do not assume that a JSON `code` of `404` means an HTTP 404 was returned.

## 3. Create Ledger Account

`POST /api/v1/ledger/accounts`

### Request

```bash
curl -X POST http://localhost:8080/api/v1/ledger/accounts \
  -H 'Content-Type: application/json' \
  -d '{
    "ownerType": "MERCHANT",
    "ownerId": "merchant-001",
    "currency": "AUD",
    "accountType": "LIABILITY"
  }'
```

Request fields:

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| `ownerType` | enum | Yes | `USER`, `MERCHANT`, `PLATFORM`, `PARTNER` |
| `ownerId` | string | Yes | Must not be blank |
| `currency` | string | Yes | Currency code interpreted by `java.util.Currency`, e.g. `AUD` |
| `accountType` | enum | Yes | `ASSET`, `LIABILITY`, `REVENUE`, `EXPENSE`, `EQUITY` |

The service assigns a UUID, sets `accountStatus` to `ACTIVE`, and records `createdAt`.

### Example successful response

```json
{
  "code": 200,
  "message": "Success",
  "data": {
    "id": "b3c6e0b3-90ce-4b87-a19f-5cb496a6b821",
    "ownerType": "MERCHANT",
    "ownerId": "merchant-001",
    "currency": "AUD",
    "accountType": "LIABILITY",
    "accountStatus": "ACTIVE",
    "createdAt": "2026-10-30T03:00:00Z"
  }
}
```

**Note:** the JSON property is `accountStatus` (not `status`) because that is the name in `LedgerAccountResponse`.

## 4. Get and List Accounts

### Get an account by ID

`GET /api/v1/ledger/accounts/{id}`

```bash
curl http://localhost:8080/api/v1/ledger/accounts/b3c6e0b3-90ce-4b87-a19f-5cb496a6b821
```

The success payload is the same account object returned by the create endpoint.

### List accounts

`GET /api/v1/ledger/accounts`

```bash
curl http://localhost:8080/api/v1/ledger/accounts
```

The `data` property is an array of account objects:

```json
{
  "code": 200,
  "message": "Success",
  "data": [
    {
      "id": "b3c6e0b3-90ce-4b87-a19f-5cb496a6b821",
      "ownerType": "MERCHANT",
      "ownerId": "merchant-001",
      "currency": "AUD",
      "accountType": "LIABILITY",
      "accountStatus": "ACTIVE",
      "createdAt": "2026-10-30T03:00:00Z"
    }
  ]
}
```

The current implementation returns all accounts ordered by `created_at DESC, id` in the mapper; **pagination and filtering are not implemented**.

## 5. Get Account Balance

`GET /api/v1/ledger/accounts/{id}/balance`

```bash
curl http://localhost:8080/api/v1/ledger/accounts/b3c6e0b3-90ce-4b87-a19f-5cb496a6b821/balance
```

Illustrative response **assuming posted entries result in a 125.00 AUD credit balance for this liability account**:

```json
{
  "code": 200,
  "message": "Success",
  "data": {
    "accountId": "b3c6e0b3-90ce-4b87-a19f-5cb496a6b821",
    "currency": "AUD",
    "balance": 125.00
  }
}
```

Balance is **calculated from journal lines belonging to `POSTED` journals**, rather than maintained as an independently updated balance column. The mapper first calculates:

```text
netAmount = SUM(DEBIT amounts) - SUM(CREDIT amounts)
```

`LedgerAccountService` then interprets the account type:

| Account type | Balance calculation |
| --- | --- |
| `ASSET`, `EXPENSE` | `netAmount` |
| `LIABILITY`, `REVENUE`, `EQUITY` | `-netAmount` |

If there are no posted journal lines, the SQL `COALESCE` produces zero. `DRAFT` journal lines do not change the returned balance. The JSON numeric representation may not retain the example's trailing zeros.

## 6. Get Account Journal History

`GET /api/v1/ledger/accounts/{id}/journals`

```bash
curl http://localhost:8080/api/v1/ledger/accounts/b3c6e0b3-90ce-4b87-a19f-5cb496a6b821/journals
```

Illustrative response for one posted credit line:

```json
{
  "code": 200,
  "message": "Success",
  "data": {
    "accountId": "b3c6e0b3-90ce-4b87-a19f-5cb496a6b821",
    "currency": "AUD",
    "accountType": "LIABILITY",
    "entries": [
      {
        "journalId": "0ed89fa6-b97d-4e07-81de-755a3ac17283",
        "lineId": "0b8fd23e-c427-4cf4-b251-86e52e56c070",
        "side": "CREDIT",
        "amount": 125.00,
        "postedAt": "2026-10-30T03:10:00Z"
      }
    ]
  }
}
```

Each `entries` element describes a **journal line associated with the requested account**, not a complete multi-line journal. Only lines from `POSTED` journals are included. The query sorts by `posted_at DESC`, then journal ID and line ID descending. The current endpoint has no pagination.

## 7. Internal Journal Operations (Not REST APIs)

`JournalService` exposes two application-service methods:

```java
void createDraft(JournalEntry journal);
void postJournal(UUID journalId);
```

A simplified illustration of invoking these methods from Java application code:

```java
UUID debitAccountId = /* existing ACTIVE AUD account */;
UUID creditAccountId = /* another existing ACTIVE AUD account */;

JournalEntry journal = new JournalEntry(
        UUID.randomUUID(), Currency.getInstance("AUD"), Instant.now());

journal.addLine(new JournalLine(
        UUID.randomUUID(), debitAccountId, EntrySide.DEBIT,
        new BigDecimal("125.00")));

journal.addLine(new JournalLine(
        UUID.randomUUID(), creditAccountId, EntrySide.CREDIT,
        new BigDecimal("125.00")));

journalService.createDraft(journal);
journalService.postJournal(journal.getId());
```

The snippet assumes an application component with access to `journalService` and appropriately imported Java/domain classes. It is **not executable as-is** without supplying the account IDs and component context.

### Posting behaviour

1. `createDraft` persists the draft journal and its lines in a transaction.
2. `postJournal` locks the parent journal row using `SELECT ... FOR UPDATE`.
3. It loads the journal lines and checks that every referenced account exists, is `ACTIVE`, and matches the journal currency.
4. `JournalEntry.post()` requires at least two lines and equal total debit and credit amounts.
5. The service updates the journal to `POSTED` in the same transaction; failures roll the transaction back.
6. Database triggers in Flyway V2 prevent changes to posted journal entries and their lines; line modifications lock the parent journal to coordinate concurrent posting.

Each journal has one currency. This is a **single-currency balanced journal**; no currency-conversion or FX posting endpoint exists yet.

## 8. Error Examples

### Unknown account ID

For `GET /api/v1/ledger/accounts/{id}` (and the balance/history routes when the account is missing), the service raises `ACCOUNT_NOT_FOUND`:

```json
{
  "code": 404,
  "message": "Ledger account not found"
}
```

### Invalid request body

For example, an empty `ownerId` is rejected by request validation:

```json
{
  "code": 400,
  "message": "Invalid request parameters"
}
```

Both examples above are the response **body** for currently handled errors; the existing exception handler does not set an HTTP error status. These examples describe the implementation as it is, not a recommendation for future API design.

## 9. Implementation References

- `backend/src/main/java/com/globalpay/lab/ledger/api/LedgerAccountController.java`
- `backend/src/main/java/com/globalpay/lab/ledger/api/dto/`
- `backend/src/main/java/com/globalpay/lab/ledger/application/LedgerAccountService.java`
- `backend/src/main/java/com/globalpay/lab/ledger/application/JournalService.java`
- `backend/src/main/java/com/globalpay/lab/common/api/Response.java`
- `backend/src/main/java/com/globalpay/lab/common/exception/GlobalExceptionHandler.java`
- `backend/src/main/resources/mapper/`
- `backend/src/main/resources/db/migration/V1__create_ledger_schema.sql`
- `backend/src/main/resources/db/migration/V2__protect_posted_journals.sql`

For domain model, ERD, debit/credit invariants, and concurrency details, see [Ledger Core Architecture](../architecture/ledger-core.md).

**Not yet implemented:** Payment lifecycle, settlement, reconciliation, risk checks, FX, and public Journal APIs. These are outside Issue #10.

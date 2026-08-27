# LedgerCore

LedgerCore is a Spring Boot REST API for double-entry bookkeeping. It stores balances as a function of completed ledger entries rather than as a manually maintained running total, and it supports account management, transaction validation, idempotency, reversal, and transaction history.

## Features

- Account creation and lookup
- Double-entry transaction processing
- Debit/credit validation
- Idempotent transaction handling
- Transaction reversal
- Balance calculation from completed entries
- Transaction history by account
- Pessimistic locking on accounts during updates
- Centralized exception handling
- Swagger/OpenAPI documentation
- Integration testing against the local PostgreSQL test database

## Architecture

```text
Controller
    |
Service
    |
Repository
    |
PostgreSQL
```

- Controller: exposes the REST API and maps requests to service methods.
- Service: contains validation, locking, balance logic, reversal logic, and idempotency behavior.
- Repository: persists and queries accounts, transactions, and entries via Spring Data JPA.
- PostgreSQL: stores the application data in the local ledger database.

## Tech Stack

- Java 21
- Spring Boot 4.1.1
- Spring Data JPA
- Hibernate ORM
- PostgreSQL
- Maven
- JUnit 5
- Springdoc OpenAPI

## PostgreSQL Setup

PostgreSQL is expected to be running locally. The application uses the following databases:

- ledgercore
- ledgercore_test

No Docker or Testcontainers are used.

Example local configuration:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/ledgercore
spring.datasource.username=postgres
spring.datasource.password=YOUR_PASSWORD
spring.jpa.hibernate.ddl-auto=update
```

The test profile points to the test database automatically through src/test/resources/application-test.properties.

## Running the Application

```powershell
.\mvnw.cmd spring-boot:run
```

The app runs on port 8080.

## Running Tests

```powershell
.\mvnw.cmd clean test
```

The integration tests use the local PostgreSQL test database and do not rely on Docker or Testcontainers.

## Swagger / OpenAPI

Swagger UI:

http://localhost:8080/swagger-ui.html

OpenAPI JSON:

http://localhost:8080/v3/api-docs

## API Overview

### Accounts

- POST /accounts
  - Create a new account
- GET /accounts
  - List all accounts
- GET /accounts/{id}
  - Get one account
- GET /accounts/{id}/balance
  - Get the balance for an account
- GET /accounts/{id}/transactions
  - Get transaction history for an account

### Transactions

- POST /transactions
  - Create a balanced transaction with a unique idempotency key
- GET /transactions/{id}
  - Fetch a transaction by ID
- POST /transactions/{id}/reverse
  - Reverse a completed transaction and create an opposite transaction

### Example JSON

Create account:

```json
{
  "name": "Wallet"
}
```

Create balanced transaction:

```json
{
  "idempotencyKey": "txn-001",
  "entries": [
    {
      "accountId": "11111111-1111-1111-1111-111111111111",
      "amount": 100,
      "type": "DEBIT"
    },
    {
      "accountId": "22222222-2222-2222-2222-222222222222",
      "amount": 100,
      "type": "CREDIT"
    }
  ]
}
```

## Double-Entry Explanation

A valid transaction balances both sides of the ledger.

Example:

- Wallet DEBIT 100
- Savings CREDIT 100

This means:

- Total debit = 100
- Total credit = 100

The API rejects transactions where totals differ.

## Idempotency Explanation

Each transaction includes an idempotency key. If the same request is retried, the service returns the original transaction instead of creating another one. This prevents duplicate ledger entries when clients retry after network failures or timeouts.

## Reversal Explanation

A reversal does not delete the original transaction. Instead:

- the original transaction is marked as REVERSED
- a new reversal transaction is created with opposite entry directions
- the reversal transaction is linked back through reversedTransactionId

## Balance Explanation

The balance is calculated from completed ledger entries. It is not maintained as a separate stored balance field. The repository sums credit entries and subtracts debit entries for the given account, while excluding pending transactions.

## Testing

The project includes integration tests that exercise the real REST API against the local PostgreSQL test database. The tests validate account creation, transaction creation, invalid inputs, reversal behavior, idempotency, history ordering, and balance calculation.

Get one account:

```powershell
Invoke-RestMethod `
    -Uri "http://localhost:8080/accounts/ACCOUNT_UUID" `
    -Method GET
```

Create a transaction:

```powershell
$body = @{
    idempotencyKey = "txn-001"
    entries = @(
        @{
            accountId = "ACCOUNT_UUID_1"
            amount = 1000
            type = "DEBIT"
        },
        @{
            accountId = "ACCOUNT_UUID_2"
            amount = 1000
            type = "CREDIT"
        }
    )
} | ConvertTo-Json -Depth 5

Invoke-RestMethod `
    -Uri "http://localhost:8080/transactions" `
    -Method POST `
    -ContentType "application/json" `
    -Body $body
```

Get balance:

```powershell
Invoke-RestMethod `
    -Uri "http://localhost:8080/accounts/ACCOUNT_UUID/balance" `
    -Method GET
```

Get a transaction:

```powershell
Invoke-RestMethod `
    -Uri "http://localhost:8080/transactions/TRANSACTION_UUID" `
    -Method GET
```

Get transaction history:

```powershell
Invoke-RestMethod `
    -Uri "http://localhost:8080/accounts/ACCOUNT_UUID/transactions" `
    -Method GET
```

Reverse a transaction:

```powershell
Invoke-RestMethod `
    -Uri "http://localhost:8080/transactions/TRANSACTION_UUID/reverse" `
    -Method POST
```

## Double-Entry Explanation

Every transaction must have at least one `DEBIT` and one `CREDIT`.

Total `DEBIT` amount must equal total `CREDIT` amount.

Valid:

```text
Wallet  DEBIT   1000
Savings CREDIT  1000
```

Valid multi-entry transaction:

```text
Wallet   DEBIT   1000
Savings  CREDIT   600
Checking CREDIT   400
```

Invalid:

```text
Wallet  DEBIT   1000
Savings CREDIT   900
```

## Transaction Lifecycle

New ledger transactions begin as:

```text
PENDING -> COMPLETED
```

Completed transactions can be reversed:

```text
COMPLETED -> REVERSED
```

Reversal creates a new transaction with opposite entries. If the original entry is a debit, the reversal entry is a credit for the same account and amount. If the original entry is a credit, the reversal entry is a debit.

## Idempotency

Idempotency keys protect clients from accidentally creating duplicate transactions when a request is retried.

For example, sending `txn-001` twice should return the same transaction instead of creating two separate transactions. The database also enforces uniqueness for transaction idempotency keys.

## Balance

Balance is calculated as:

```text
Balance = total CREDIT - total DEBIT
```

Only `COMPLETED` transactions participate in balance calculations. `PENDING` and `REVERSED` transactions are excluded.

## Concurrency

LedgerCore locks accounts pessimistically before creating or reversing transactions. Accounts are locked in deterministic UUID order so concurrent requests acquire locks consistently and reduce the risk of deadlocks.

## Error Handling

Errors are returned through a centralized `ErrorResponse` shape:

```json
{
  "timestamp": "...",
  "status": 400,
  "error": "Bad Request",
  "message": "...",
  "path": "/transactions"
}
```

Stack traces are not returned to clients.

# LedgerCore

**A production-oriented double-entry ledger backend built with Spring Boot and PostgreSQL.**

LedgerCore is a REST API for managing financial accounts and processing double-entry transactions. Instead of maintaining a mutable balance, the system derives account balances from completed ledger entries, providing an auditable source of truth.

The project focuses on backend engineering concerns commonly found in financial systems: **transaction integrity, idempotency, concurrency control, reversals, database consistency, and integration testing.**

---

## ✨ Key Features

* **Double-entry bookkeeping** — every transaction must balance debits and credits.
* **Idempotent transactions** — prevents duplicate transactions when requests are retried.
* **Transaction reversals** — reverses completed transactions without deleting ledger history.
* **Derived balances** — balances are calculated from ledger entries rather than stored as mutable state.
* **Pessimistic locking** — locks accounts during transaction processing to protect concurrent updates.
* **Deterministic lock ordering** — accounts are locked in UUID order to reduce deadlock risk.
* **Transaction history** — retrieve transactions associated with an account.
* **Centralized error handling** — consistent JSON error responses through a global exception handler.
* **PostgreSQL persistence** — relational storage using Spring Data JPA and Hibernate.
* **Integration testing** — REST API tested against a dedicated PostgreSQL test database.
* **OpenAPI documentation** — interactive API documentation through Swagger UI.

---

## 🏗️ Architecture

```text
                    HTTP Request
                         │
                         ▼
                ┌─────────────────┐
                │   Controllers   │
                │ REST API Layer  │
                └────────┬────────┘
                         │
                         ▼
                ┌─────────────────┐
                │    Services     │
                │ Business Logic  │
                └────────┬────────┘
                         │
                         ▼
                ┌─────────────────┐
                │   Repositories  │
                │   Spring Data   │
                └────────┬────────┘
                         │
                         ▼
                ┌─────────────────┐
                │   PostgreSQL    │
                │    Database     │
                └─────────────────┘
```

### Project Structure

```text
src/main/java/com/ledgercore
│
├── controller/
│   ├── AccountController.java
│   └── TransactionController.java
│
├── service/
│   ├── AccountService.java
│   └── LedgerService.java
│
├── repository/
│   ├── AccountRepository.java
│   ├── EntryRepository.java
│   └── LedgerTransactionRepository.java
│
├── entity/
│   ├── Account.java
│   ├── Entry.java
│   ├── LedgerTransaction.java
│   ├── EntryType.java
│   └── TransactionStatus.java
│
├── dto/
│   ├── CreateAccountRequest.java
│   ├── CreateTransactionRequest.java
│   ├── AccountResponse.java
│   ├── TransactionResponse.java
│   └── BalanceResponse.java
│
└── exception/
    ├── GlobalExceptionHandler.java
    └── custom exceptions
```

---

## 🛠️ Tech Stack

| Technology        | Purpose                       |
| ----------------- | ----------------------------- |
| Java 21           | Backend language              |
| Spring Boot 4.1.1 | REST API framework            |
| Spring Data JPA   | Database access               |
| Hibernate ORM     | Persistence / ORM             |
| PostgreSQL        | Relational database           |
| Maven             | Build & dependency management |
| JUnit 5           | Testing                       |
| Springdoc OpenAPI | API documentation             |

---

## 🔄 Transaction Flow

A typical transfer between two accounts follows this flow:

```text
Client
  │
  │ POST /transactions
  ▼
TransactionController
  │
  ▼
LedgerService
  │
  ├── Check idempotency key
  │
  ├── Validate debit/credit totals
  │
  ├── Load and lock accounts
  │
  ├── Lock accounts in deterministic UUID order
  │
  ├── Create LedgerTransaction
  │
  ├── Create Entry records
  │
  └── Commit transaction
          │
          ▼
      PostgreSQL
```

For example:

```text
Wallet       DEBIT     1000
Savings      CREDIT    1000
```

The transaction is valid because:

```text
Total Debit  = 1000
Total Credit = 1000
```

Multi-entry transactions are also supported:

```text
Wallet       DEBIT     1000
Savings      CREDIT     600
Checking     CREDIT     400
```

---

## 💰 Balance Calculation

LedgerCore does not maintain a manually updated `balance` field.

Instead:

```text
Balance = Total Credits - Total Debits
```

Only entries belonging to `COMPLETED` transactions participate in the calculation.

This makes the ledger entries the source of truth and avoids maintaining another mutable value that could become inconsistent with the transaction history.

---

## 🔁 Transaction Lifecycle

Normal transaction:

```text
PENDING
   │
   ▼
COMPLETED
```

Reversal:

```text
COMPLETED
   │
   ▼
REVERSED
   │
   └──► New reversal transaction
```

A reversal does **not** delete the original transaction.

Instead, LedgerCore:

1. Marks the original transaction as `REVERSED`.
2. Creates a new transaction.
3. Generates opposite entries.
4. Links the reversal using `reversedTransactionId`.

Example:

```text
Original:

Wallet       DEBIT     1000
Bank         CREDIT    1000


Reversal:

Wallet       CREDIT    1000
Bank         DEBIT     1000
```

This preserves the complete audit trail.

---

## 🔐 Idempotency

Every transaction requires an `idempotencyKey`.

If a client retries the same request:

```text
Request 1 → txn-001 → Transaction A created
Request 2 → txn-001 → Transaction A returned
```

A second transaction is not created.

The database also enforces uniqueness on the idempotency key, providing an additional layer of protection against duplicate ledger operations.

---

## 🔒 Concurrency Control

Financial transactions can be processed concurrently, so LedgerCore uses **pessimistic database locking** when modifying accounts.

To reduce deadlock risk, multiple accounts are always locked in a deterministic UUID order.

Conceptually:

```text
Request A                 Request B
   │                         │
   ▼                         ▼
Lock Account A          Lock Account A
   │                         │
   ▼                         │
Lock Account B              waits
   │
   ▼
Commit
   │
   ▼
Release locks
                             │
                             ▼
                        Lock Account B
```

This avoids two concurrent transactions acquiring the same account locks in different orders.

---

## 🌐 REST API

### Accounts

| Method | Endpoint                      | Description                     |
| ------ | ----------------------------- | ------------------------------- |
| `POST` | `/accounts`                   | Create an account               |
| `GET`  | `/accounts`                   | List all accounts               |
| `GET`  | `/accounts/{id}`              | Get an account                  |
| `GET`  | `/accounts/{id}/balance`      | Calculate account balance       |
| `GET`  | `/accounts/{id}/transactions` | Get account transaction history |

### Transactions

| Method | Endpoint                     | Description                     |
| ------ | ---------------------------- | ------------------------------- |
| `POST` | `/transactions`              | Create a ledger transaction     |
| `GET`  | `/transactions/{id}`         | Get transaction details         |
| `POST` | `/transactions/{id}/reverse` | Reverse a completed transaction |

---

## 📖 API Documentation

Once the application is running, the complete API can be explored interactively through Swagger UI:

**Swagger UI**

`http://localhost:8080/swagger-ui.html`

**OpenAPI specification**

`http://localhost:8080/v3/api-docs`

Swagger provides request/response schemas and allows the endpoints to be tested directly from the browser.

---

## 🧪 Testing

The project contains integration tests that exercise the actual REST API against a dedicated local PostgreSQL test database.

The test suite covers:

* Account creation
* Account retrieval
* Transaction creation
* Double-entry validation
* Invalid transaction handling
* Idempotency
* Transaction reversal
* Balance calculation
* Transaction history
* Error handling

Latest verification:

```text
30 tests
0 failures
0 errors
0 skipped

BUILD SUCCESS
```

Run the test suite with:

```powershell
.\mvnw.cmd clean test
```

---

## 🚀 Running Locally

### Prerequisites

* Java 21
* PostgreSQL
* Maven (or the included Maven Wrapper)

Create the required PostgreSQL databases:

```text
ledgercore
ledgercore_test
```

Configure the application with environment variables:

```text
DATABASE_URL
DATABASE_USERNAME
DATABASE_PASSWORD
```

The application defaults to port `8080`.

Start the backend:

```powershell
.\mvnw.cmd spring-boot:run
```

The API will be available at:

```text
http://localhost:8080
```

---

## 📌 Example Requests

### Create an Account

```json
{
  "name": "Wallet"
}
```

### Create a Transaction

```json
{
  "idempotencyKey": "txn-001",
  "entries": [
    {
      "accountId": "ACCOUNT_UUID_1",
      "amount": 1000,
      "type": "DEBIT"
    },
    {
      "accountId": "ACCOUNT_UUID_2",
      "amount": 1000,
      "type": "CREDIT"
    }
  ]
}
```

### Get Balance

```text
GET /accounts/{accountId}/balance
```

### Reverse a Transaction

```text
POST /transactions/{transactionId}/reverse
```

---

## ⚠️ Error Handling

Errors are returned through a consistent response structure:

```json
{
  "timestamp": "...",
  "status": 400,
  "error": "Bad Request",
  "message": "...",
  "path": "/transactions"
}
```

Internal stack traces are not exposed through the API.

---

## 🎯 Engineering Focus

LedgerCore was designed around backend concepts that are important in systems handling financial data:

* Data consistency
* Atomic database transactions
* Double-entry accounting
* Idempotent APIs
* Concurrency control
* Pessimistic locking
* Deadlock prevention
* Auditability
* Database-backed source of truth
* Integration testing
* REST API design





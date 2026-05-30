# Payment Orchestration System

A production-grade **Payment Orchestration Service** built with Java 21 + Spring Boot 3. It provides intelligent payment routing, provider failover, idempotency guarantees, distributed locking, and retry mechanisms — modelled after real-world payment orchestration platforms like Yuno.

---

## Table of Contents

1. [Tech Stack](#tech-stack)
2. [Architecture](#architecture)
3. [Prerequisites](#prerequisites)
4. [Installation](#installation)
5. [Running the Application](#running-the-application)
6. [API Reference](#api-reference)
7. [Key Design Decisions](#key-design-decisions)
8. [Functional Requirements](#functional-requirements)
9. [Non-Functional Requirements](#non-functional-requirements)
10. [Running Tests](#running-tests)
11. [Test Case Catalogue](#test-case-catalogue)
12. [AI Prompts Used (Vibe Coding)](#ai-prompts-used-vibe-coding)

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.x |
| Database | PostgreSQL 15 |
| Cache / Lock | Redis 7 |
| ORM | Spring Data JPA / Hibernate |
| Connection Pool | HikariCP |
| Concurrency | Java 21 Virtual Threads |
| Testing | JUnit 5, Mockito, MockMvc |
| Build | Maven |
| Containerisation | Docker / Docker Compose |

---

## Architecture

```
Client
  └─► REST Controller  (/api/v1/payments)
        └─► PaymentService
              └─► PaymentOrchestrationService
                    ├─► IdempotencyService  ←─── Redis (L1) + PostgreSQL (L2)
                    ├─► DistributedLockService ← Redis (SETNX)
                    ├─► RoutingEngine
                    │     ├─► CardRoutingStrategy  → Provider A
                    │     └─► UPIRoutingStrategy   → Provider B
                    ├─► RetryService (exponential backoff)
                    ├─► ProviderExecutorService (virtual threads)
                    │     ├─► ProviderAConnector
                    │     └─► ProviderBConnector
                    └─► PaymentAttemptService → PostgreSQL
```

### Routing Rules

| Payment Method | Primary Provider |
|---|---|
| CARD | Provider A |
| UPI  | Provider B |

### Request Flow

```
POST /api/v1/payments
  1. IdempotencyService.checkDuplicate()   → Redis L1 → PostgreSQL L2 (throws 409 if found)
  2. DistributedLockService.acquireLock()  → Redis SETNX (throws 409 if not acquired)
  3. IdempotencyService.checkDuplicate()   → double-check after lock (TOCTOU guard)
  4. PaymentTransaction saved to DB
  5. IdempotencyService.cacheTransaction() → write to Redis
  6. RoutingEngine resolves provider
  7. RetryService executes provider call   → 3 attempts with exponential backoff
  8. On retry exhaustion → failover to secondary provider
  9. PaymentTransaction status updated → PaymentResponse returned
```

---

## Prerequisites

- **Java 21+** — [Download JDK 21](https://adoptium.net/)
- **Maven 3.9+** — bundled via `./mvnw` wrapper (no separate install needed)
- **Docker** — for running PostgreSQL and Redis locally

---

## Installation

### 1. Clone the Repository

```bash
git clone https://github.com/Harshal-Taori-11/payment-orchestration-system.git
cd payment-orchestration-system
```

### 2. Start Infrastructure

Starts PostgreSQL (port 5433) and Redis (port 6379):

```bash
docker-compose up -d
```

### 3. Build the Project

```bash
./mvnw clean package -DskipTests
```

---

## Running the Application

```bash
./mvnw spring-boot:run
```

The application starts on **http://localhost:8080**

| Resource | URL |
|---|---|
| Base API | http://localhost:8080/api/v1/payments |
| Swagger UI | http://localhost:8080/swagger-ui/index.html |

> **Windows — Java 21 path setup (if needed):**
> ```powershell
> $env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.11"
> $env:PATH = "$env:JAVA_HOME\bin;" + $env:PATH
> ```

---

## API Reference

### Create Payment

**POST** `/api/v1/payments`

**Request Body:**

| Field | Type | Required | Constraints |
|---|---|---|---|
| merchantId | Long | ✅ | Must not be null |
| idempotencyKey | String | ✅ | Max 64 chars. Unique per merchant — safe to retry on network failure |
| amount | BigDecimal | ✅ | Must be positive |
| currencyCode | Enum | ✅ | `INR`, `USD`, `EUR` |
| paymentMethodType | Enum | ✅ | `CARD`, `UPI` |

```json
{
  "merchantId": 1001,
  "idempotencyKey": "ORD-12345",
  "amount": 1500.00,
  "currencyCode": "INR",
  "paymentMethodType": "CARD"
}
```

**Response (201 Created):**

| Field | Type | Description |
|---|---|---|
| paymentId | UUID | Unique payment identifier |
| status | Enum | `PROCESSING`, `SUCCESS`, `FAILED` |
| provider | Enum | `PROVIDER_A`, `PROVIDER_B` |
| amount | BigDecimal | Payment amount (major units) |
| currency | Enum | Currency code |
| paymentMethod | Enum | Payment method used |
| idempotencyKey | String | Echo of the submitted key |
| createdAt | DateTime | Timestamp of creation |
| message | String | Human-readable result message |

```json
{
  "paymentId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "status": "SUCCESS",
  "provider": "PROVIDER_A",
  "amount": 1500.00,
  "currency": "INR",
  "paymentMethod": "CARD",
  "idempotencyKey": "ORD-12345",
  "createdAt": "2024-01-15T10:30:00",
  "message": "Payment processed successfully"
}
```

**Error Responses:**

| HTTP Status | Scenario |
|---|---|
| 400 | Validation failure (missing / invalid fields) |
| 409 | Duplicate idempotency key or lock acquisition conflict |
| 500 | Internal server error |

---

### Get Payment Status

**GET** `/api/v1/payments/{paymentId}`

**Path Parameter:** `paymentId` (UUID)

**Response (200 OK):**

| Field | Type | Description |
|---|---|---|
| paymentId | UUID | Payment identifier |
| status | Enum | Current status |
| provider | Enum | Provider that processed it |
| amount | BigDecimal | Payment amount |
| currency | Enum | Currency code |
| paymentMethod | Enum | Payment method |
| idempotencyKey | String | Original idempotency key |
| createdAt | DateTime | Creation timestamp |
| updatedAt | DateTime | Last update timestamp |
| failureReason | String | Populated only if status is `FAILED` |

```json
{
  "paymentId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "status": "SUCCESS",
  "provider": "PROVIDER_A",
  "amount": 1500.00,
  "currency": "INR",
  "paymentMethod": "CARD",
  "idempotencyKey": "ORD-12345",
  "createdAt": "2024-01-15T10:30:00",
  "updatedAt": "2024-01-15T10:30:02",
  "failureReason": null
}
```

**Error Responses:**

| HTTP Status | Scenario |
|---|---|
| 404 | Payment not found |
| 500 | Internal server error |

---

## Key Design Decisions

### 1. Idempotency (Two-Layer)
- **L1 — Redis:** Sub-millisecond duplicate detection via key `idempotency:{merchantId}:{idempotencyKey}`
- **L2 — PostgreSQL:** Source of truth with unique constraint on `(merchant_id, idempotency_key)`
- Checked **before** acquiring the lock (fast path) and **after** (TOCTOU race condition guard)
- No separate idempotency table — `PaymentTransaction` IS the idempotency record

### 2. Distributed Lock
- Redis `SETNX` (`setIfAbsent`) with a 30-second TTL prevents concurrent duplicate processing
- Always released in a `finally` block to prevent deadlocks

### 3. Retry with Exponential Backoff
- Up to **3 attempts** per provider: 500ms → 1000ms → 2000ms
- Retryable exceptions (e.g. timeouts) are retried; non-retryable (e.g. card declined) fail immediately

### 4. Failover
- On primary provider retry exhaustion, flow automatically routes to the secondary provider
- Every attempt (provider, status, latency, error code) is recorded in `PaymentAttempt`

### 5. Virtual Threads (Java 21)
- Provider calls execute on virtual threads via `Executors.newVirtualThreadPerTaskExecutor()`
- Configured globally via `spring.threads.virtual.enabled=true`
- Enables high-throughput I/O-bound workloads with minimal resource overhead

### 6. Optimistic Locking
- `@Version` on `PaymentTransaction` prevents lost updates in concurrent scenarios

---

## Running Tests

Tests run fully in-memory — **no Docker required**.

```bash
# Run all tests
./mvnw test

# Run a specific test class
./mvnw test -Dtest=PaymentIntegrationTest
./mvnw test -Dtest=PaymentOrchestrationServiceTest
./mvnw test -Dtest=ConcurrentPaymentTest

# Build without running tests
./mvnw package -DskipTests
```

> Tests use `@ActiveProfiles("test")` — H2 in-memory database + Redis auto-config excluded.
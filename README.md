# Ledger Final

High-throughput financial ledger microservice example and learning project.

This repository is both a learning resource and a small microservice reference implementation demonstrating Java, Spring Boot, PostgreSQL, Redis, Kafka, Docker, and related patterns for building a ledger system with a focus on correctness.

---

## What we're building

- A set of services (at least `ledger-service` and `api-gateway`) that implement a financial ledger and transfer APIs.
- Demonstrates principles important for payment systems: monetary correctness (BigDecimal), idempotency, optimistic concurrency, event sourcing patterns, and durable storage in PostgreSQL.

Why it matters: correctness and reliability are more important than raw performance for ledger systems. This project is designed to teach and demonstrate safe patterns for money and transaction processing.

---

## Project layout

- `ledger-service/` - core ledger microservice (Spring Boot)
- `api-gateway/` - lightweight gateway that forwards transfer requests for demos
- `db-init/` - initial SQL used to create DB schema for local runs
- `docker-compose.yml` - quick local composition of required services
- `k8s/` - Kubernetes manifests (if you want to run on k8s)
- `Documentations/` - additional design docs, runbooks, and guides

---

## Tech stack

- Java 17+ (project uses Maven wrapper)
- Spring Boot
- PostgreSQL (durable source of truth)
- Redis (cache/coordination only)
- Kafka (asynchronous messaging/eventing)
- Docker & Docker Compose

---

## Financial safety rules (important)

- Do not use `double` or `float` for monetary values. Use `BigDecimal` consistently.
- Treat PostgreSQL as the durable source of truth; Redis is a cache/coordination layer only.
- Preserve idempotency guarantees and prefer DB constraints for correctness-critical rules.
- Prefer append-only event records and avoid destructive history updates.

See `Documentations/redis_integration.md` and other docs for detailed rationale and patterns used in this project.

---

## Prerequisites

- Git
- Java JDK 17+ installed and on PATH
- Maven (or use the included Maven Wrapper `mvnw` / `mvnw.cmd`)
- Docker & Docker Compose (for local full-stack runs)

On Windows PowerShell you can verify:

```powershell
java -version
.\mvnw -v
docker --version
docker-compose --version
```

---

## Quick start (local development)

1. Build all modules (use the Maven wrapper):

```powershell
Set-Location -Path "C:\Users\Chamod\Documents\Personal Projects\Ledger Final";
.\mvnw clean package -DskipTests
```

2. Start supporting infrastructure with Docker Compose (Postgres, Redis, Kafka):

```powershell
docker-compose up --build
```

3. Start services (you can run the jars in `ledger-service/target` and `api-gateway/target` or use your IDE):

```powershell
java -jar .\ledger-service\target\ledger-service-1.0.0.jar
java -jar .\api-gateway\target\api-gateway-1.0.0.jar
```

4. Use the API gateway's /transfer endpoint (see `api-gateway/src/main/resources/application.properties` for port) to exercise transfer flows.

---

## Running tests

Run unit and integration tests for a module with:

```powershell
.\mvnw -pl ledger-service test
```

Integration tests may require Docker services (Postgres, Kafka) running.

---

## Important development notes

- Controllers should be thin; put business logic into services.
- Use DTOs for request/response shapes and mapping at service boundaries.
- Prefer constructor injection in Spring components.
- Preserve idempotency: design idempotency keys for transfer endpoints and store them durably.
- Use DB transactions and optimistic concurrency where appropriate.

---

## Helpful commands

- Build (project root): `./mvnw clean package -DskipTests`
- Run local infra: `docker-compose up --build`
- Run a single module tests: `./mvnw -pl ledger-service test`

---

## Contributing

This project is primarily a learning tool. If you want to contribute:

1. Open an issue describing what you want to change.
2. Create a topic branch, implement tests for new behaviors, and open a PR.

Please write tests for correctness-critical behavior (transfers, idempotency, concurrency scenarios, DB constraints).

---

## Next steps and learning path

- Read `Documentations/implementation_guide.md` for design decisions.
- Experiment by adding more extensive integration tests that simulate duplicate messages, retries, and concurrency.
- Explore transactional outbox patterns for safe Kafka publishing from DB transactions.



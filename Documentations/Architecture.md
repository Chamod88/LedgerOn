Github Repo - (https://github.com/Chamod88/LedgerOn.git)

# High-Throughput Financial Ledger Microservice

A highly concurrent, fault-tolerant financial ledger system inspired by the architectures of Stripe and PayPal. 

This project demonstrates how to handle financial transactions at scale without dropping a single cent, utilizing **Event Sourcing**, **Asynchronous Messaging**, strict **Idempotency** guarantees, and enterprise-grade infrastructure automation, security, and observability.

## 🚀 Key Features

* **Event Sourcing:** Account balances are not updated in place. Every transaction is appended as an immutable event (`FundsDeposited`, `FundsWithdrawn`). The current state is folded from this event stream, providing a 100% accurate audit trail.
* **Idempotency & Double-Spend Prevention:** Leverages Redis to cache idempotency keys, ensuring that client network retries never result in duplicate charges.
* **Optimistic Concurrency Control (OCC):** Database-level versioning prevents race conditions when thousands of concurrent requests attempt to modify the same account balance simultaneously.
* **Asynchronous Processing:** An API Gateway accepts the request, verifies idempotency, and publishes it to a Kafka topic for background processing, ensuring ultra-low latency for the client.
* **Zero-Downtime Resilience:** Designed to handle message broker failures and network partitions gracefully.
* **Automated Deployment:** GitHub Actions CI/CD pipeline automatically builds, tests, and deploys on every code push.
* **Infrastructure as Code:** Azure Bicep templates enable one-command cloud environment provisioning.
* **Zero-Trust Security:** Mutual TLS enforces cryptographic verification between all microservices, preventing identity spoofing and data interception.

## 🏗️ Architecture

```
[ Client ] 
    │ (POST /transfer)
    ▼
[ API Gateway ] ──(Check Idempotency)──> [ Redis Cache ]
    │ (Publish Event)
    ▼
[ Apache Kafka / RabbitMQ ] 
    │ (Consume Event)
    ▼
[ Core Ledger Service ] ──(Append Event + OCC)──> [ PostgreSQL ]
    │ (Publish Success/Failure)
    ▼
[ Notification Service ] ──> (Email/SMS Mock Alert)
    │
    └──(Service Mesh with mTLS)──> [Linkerd]
```

## 🛠️ Technology Stack Overview

### Core Application
* **Backend Language:** Java (Spring Boot) / C# (.NET Core)
* **API Gateway:** Spring Cloud Gateway / YARP
* **Message Broker:** Apache Kafka (high-throughput asynchronous processing)
* **Alternative Broker:** RabbitMQ (moderate throughput, lower overhead)
* **Database:** PostgreSQL (ACID-compliant, immutable event store)
* **Cache & Distributed Locks:** Redis (idempotency caching, lock management)

### Infrastructure & Deployment
* **Containerization:** Docker & Docker Compose (local development and testing)
* **Infrastructure as Code:** Azure Bicep (Kubernetes, managed databases, caching layers)
* **CI/CD Pipeline:** GitHub Actions (automated builds, testing, validation, deployment)

### Testing & Observability
* **Load Testing:** Locust (Python-based, simulates thousands of concurrent users)
* **Alternative Load Testing:** k6 (Grafana's high-performance tool)
* **Chaos Engineering:** Toxiproxy (optional; simulate network failures) or manual Docker container stopping

### Security
* **Zero-Trust Network:** Mutual TLS via Service Mesh (Linkerd)
  - All internal microservice communication requires cryptographic certificate verification
  - Automatic mTLS injection without code changes
  - Traffic encryption across the internal network
  - Identity verification preventing container spoofing attacks

## 📦 Complete Technology Stack

For detailed documentation on each technology and its role, see [TECH_STACK.md](TECH_STACK.md).

## ⚙️ Local Development Setup

### Prerequisites
* Docker Desktop installed
* Docker Compose (included with Docker Desktop)
* k6 or Locust installed for load testing (optional)
* Git for version control

### 1. Start the Infrastructure

Bring up the entire ecosystem with a single command:

```bash
docker-compose up -d
```

This starts:
- PostgreSQL (transactional event store)
- Redis (idempotency cache & distributed locks)
- Apache Kafka (asynchronous message broker)
- Spring Cloud Gateway (API Gateway)
- Ledger Microservice

### 2. Verify Services

Check container status:
```bash
docker ps
```

Expected services running on:
- **API Gateway:** `http://localhost:8080`
- **PostgreSQL:** `localhost:5432` (credentials in `docker-compose.yml`)
- **Redis:** `localhost:6379`
- **Kafka:** `localhost:9092`

### 3. View Service Logs

```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f ledger-service
```

## 🔌 API Documentation

### Process a Transfer

Initiates a transfer or withdrawal. The request is queued asynchronously.

**HTTP Request:**
```http
POST /api/v1/ledger/transfer
Content-Type: application/json
X-Idempotency-Key: a-unique-uuid-per-request
```

**Request Body:**
```json
{
  "accountId": "123e4567-e89b-12d3-a456-426614174000",
  "amount": 50.00,
  "currency": "USD",
  "transactionType": "WITHDRAWAL"
}
```

**Response Examples:**

Success (Queued):
```json
HTTP/1.1 202 Accepted
{
  "transactionId": "txn_abc123",
  "status": "QUEUED",
  "message": "Transaction accepted for processing"
}
```

Duplicate (Idempotency):
```json
HTTP/1.1 409 Conflict
{
  "error": "DUPLICATE_REQUEST",
  "message": "Transaction with this idempotency key already exists"
}
```

Error:
```json
HTTP/1.1 400 Bad Request
{
  "error": "INVALID_PAYLOAD",
  "message": "Missing required field: amount"
}
```

## 📈 Load Testing & Resilience Validation

### Using Locust

Simulate thousands of concurrent virtual users hitting your API with realistic transaction payloads:

```bash
locust -f locustfile.py --host=http://localhost:8080
```

Then open `http://localhost:8089` to configure and start the test.

### Using k6

Run the high-performance k6 load test:

```bash
k6 run loadtest.js
```

**Expected Results:**
* `0%` Request Failure Rate
* Consistent sub-100ms response times at 500+ concurrent users
* Zero duplicate transactions (idempotency verified)
* PostgreSQL transaction ledger matches successful k6 requests exactly

### Chaos Testing (Manual Approach)

To test resilience without additional tooling, manually stop a critical service during load testing:

```bash
# While load test is running, stop Redis to simulate cache failure
docker-compose stop redis

# Observe application behavior and recovery
docker-compose logs -f ledger-service

# Restart Redis
docker-compose start redis
```

## 🔒 Security & Zero-Trust Architecture

### Mutual TLS (mTLS) with Service Mesh

For production environments, deploy a service mesh (Linkerd) that enforces:
- **Cryptographic certificate verification** between all microservice-to-microservice calls
- **Automatic mTLS injection** without code changes
- **Traffic encryption** across the internal network
- **Identity verification** preventing container spoofing attacks

Enable in `docker-compose.yml` or Kubernetes manifests for production clusters.

## 🚀 CI/CD Pipeline (GitHub Actions)

The project includes automated GitHub Actions workflows that:

1. **On every push:**
   - Compile and build the application
   - Run unit and integration tests
   - Package code into Docker images
   - Push images to container registry
   - Validate Azure Bicep infrastructure templates
   - Deploy to Azure Kubernetes Service (AKS) on main branch

2. **Workflow file:** `.github/workflows/deploy.yml`

### Manual Deployment Trigger

```bash
git push origin main
```

GitHub Actions automatically handles the rest.

## 📝 Infrastructure as Code (Azure Bicep)

The project includes Bicep templates for provisioning production infrastructure:

- **Location:** `infra/main.bicep`
- **Deploys:**
  - Azure Kubernetes Service (AKS) cluster
  - Managed PostgreSQL Database
  - Azure Cache for Redis
  - Application Insights for observability
  - Key Vault for secrets management

### Deploy Infrastructure

```bash
# Authenticate with Azure
az login

# Deploy or update infrastructure
az deployment group create \
  --resource-group my-resource-group \
  --template-file infra/main.bicep
```

## 📊 Monitoring & Observability

* **Docker Compose Logs:** `docker-compose logs -f [service-name]`
* **PostgreSQL Query Insights:** Connect via psql to inspect the `ledger_events` table
* **Redis Monitoring:** Use `redis-cli` to inspect cache hit rates
* **Application Metrics:** Application Insights integration (Azure deployment)

## 🧑‍💻 Development Workflow

### Prerequisites for Development
* JDK 17+ (for Spring Boot) or .NET 8+ (for C#/.NET)
* Maven or Gradle (Java) / dotnet CLI (.NET)
* Git
* Docker & Docker Compose

### Build & Test Locally

**Java/Spring Boot:**
```bash
mvn clean package
mvn test
```

**C#/.NET Core:**
```bash
dotnet build
dotnet test
```

### Code Changes & Deployment

1. Create a feature branch: `git checkout -b feature/my-feature`
2. Make changes and commit: `git commit -am "Add my feature"`
3. Push to repository: `git push origin feature/my-my-feature`
4. Open a Pull Request on GitHub
5. GitHub Actions automatically runs tests and validates infrastructure
6. Merge to `main` to trigger production deployment

## 🐛 Troubleshooting

### Services won't start
```bash
# Check Docker daemon
docker info

# Remove old containers and restart
docker-compose down -v
docker-compose up -d
```

### High latency in requests
* Verify Redis is responsive: `docker-compose exec redis redis-cli ping`
* Check Kafka broker status: `docker-compose logs kafka`
* Inspect database connection pools in application logs

### Load test failures
* Increase ulimit for file descriptors: `ulimit -n 65536`
* Check Docker CPU/memory limits: `docker stats`
* Verify idempotency key uniqueness in your load test script

## 📚 Further Reading

* [Technology Stack Details](TECH_STACK.md) - In-depth breakdown of each tool and its role
* [Event Sourcing Pattern](https://martinfowler.com/eaaDev/EventSourcing.html) - Martin Fowler's guide
* [Idempotent Microservices](https://microservices.io/patterns/idempotent.html) - Idempotency best practices
* [Azure Bicep Documentation](https://learn.microsoft.com/en-us/azure/azure-resource-manager/bicep/)
* [GitHub Actions Documentation](https://docs.github.com/en-us/actions)
* [Linkerd Service Mesh](https://linkerd.io/2.15/overview/)

## 📄 License

This project is provided as-is for educational and commercial use.
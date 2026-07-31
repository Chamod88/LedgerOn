# Ledger Project — Command Cheat Sheet

## 🐳 Docker Commands

```powershell
# Start all containers
docker-compose up -d

# Stop all containers
docker-compose down

# Check container status
docker ps

# Check all containers (including stopped)
docker ps -a

# View logs from a specific container
docker logs -f <container_id_or_name>

# View logs for all services
docker-compose logs -f

# Stop a specific container
docker stop <container_id>

# Start a specific container
docker start <container_id>

# Restart all containers
docker-compose restart

# Remove all containers and volumes (DANGEROUS - clears data)
docker-compose down -v

# Rebuild images
docker-compose build

# Rebuild and start
docker-compose up --build -d
```

---

## 🔨 Maven Commands

```powershell
# Run ledger-service
cd ledger-service
mvn spring-boot:run

# Run API Gateway
cd api-gateway
mvn spring-boot:run

# Build all projects
mvn clean install

# Build specific module
cd ledger-service
mvn clean package

# Run tests
mvn test

# Run tests for a specific class
mvn test -Dtest=AccountControllerTest

# Skip tests
mvn clean install -DskipTests

# Build from root (multi-module)
cd .. (go to root)
mvn clean install

# View dependency tree
mvn dependency:tree

# Update dependencies
mvn versions:display-dependency-updates
```

---

## 📊 Load Testing Commands

### K6 (Performance Testing)

```powershell
# Run basic test
k6 run "Heavy Testings/k6_ledger_test.js"

# Run with JSON output
k6 run --out json=k6_results.json "Heavy Testings/k6_ledger_test.js"

# Run with specific thresholds
k6 run --vus 500 --duration 5m "Heavy Testings/k6_ledger_test.js"

# Check K6 version
k6 version

# Install K6 (Windows)
winget install k6
# or
choco install k6
```

### Locust (Live Dashboard Testing)

```powershell
# Run with web UI (browser at http://localhost:8089)
cd "Heavy Testings"
python -m locust -f locust_ledger_test.py

# Or use the long form
locust -f locust_ledger_test.py

# Headless run (no UI, scripted)
locust -f locust_ledger_test.py \
    --headless \
    --users 500 \
    --spawn-rate 50 \
    --run-time 5m \
    --host http://localhost:8082 \
    --html locust_report.html

# Check Locust version
python -m locust --version

# Install Locust
pip install locust
```

---

## 🗄️ Database Commands

### PostgreSQL (inside Docker)

```powershell
# Connect to PostgreSQL
docker exec -it <postgres_container_id> psql -U ledger_user -d ledger_db

# Inside psql:
\dt                          # List all tables
\d accounts                  # Describe accounts table
SELECT * FROM accounts;      # Query accounts
SELECT * FROM transactions;  # Query transactions
SELECT * FROM idempotency_keys;  # Check idempotency keys
\q                           # Exit psql

# View PostgreSQL logs
docker logs -f <postgres_container_id>
```

### Via Docker without entering container

```powershell
# Run SQL query
docker exec <postgres_container_id> psql -U ledger_user -d ledger_db -c "SELECT COUNT(*) FROM accounts;"
```

---

## 💾 Redis Commands

### Redis (inside Docker)

```powershell
# Connect to Redis
docker exec -it <redis_container_id> redis-cli

# Inside redis-cli:
PING                         # Test connection
KEYS *                       # List all keys
GET <key>                    # Get a value
DEL <key>                    # Delete a key
FLUSHDB                      # Clear all data
DBSIZE                       # Number of keys
INFO                         # Server info
\q or EXIT                   # Exit redis-cli

# View Redis logs
docker logs -f <redis_container_id>
```

### Via Docker without entering container

```powershell
# Check if key exists
docker exec <redis_container_id> redis-cli EXISTS idempotency:<key>

# Get value
docker exec <redis_container_id> redis-cli GET <key>
```

---

## 🚀 API Testing Commands

### cURL (Quick API tests)

```powershell
# Create account
curl -X POST http://localhost:8081/api/v1/accounts `
  -H "Content-Type: application/json" `
  -d '{
    "id": "ACC-001",
    "currency": "USD",
    "initialBalance": 1000
  }'

# Get account balance
curl -X GET http://localhost:8081/api/v1/ledger/accounts/ACC-001/balance

# Deposit (via Gateway)
curl -X POST http://localhost:8082/api/v1/ledger/transfer `
  -H "Content-Type: application/json" `
  -H "X-Idempotency-Key: unique-key-123" `
  -d '{
    "accountId": "ACC-001",
    "amount": 100,
    "currency": "USD",
    "transactionType": "DEPOSIT"
  }'

# Withdrawal
curl -X POST http://localhost:8082/api/v1/ledger/transfer `
  -H "Content-Type: application/json" `
  -H "X-Idempotency-Key: unique-key-456" `
  -d '{
    "accountId": "ACC-001",
    "amount": 50,
    "currency": "USD",
    "transactionType": "WITHDRAWAL"
  }'
```

---

## 🔍 Debugging & Logs

```powershell
# View ledger-service logs in real-time
docker logs -f ledger-service

# View API Gateway logs in real-time
docker logs -f api-gateway

# View all service logs
docker-compose logs -f

# Follow logs with search pattern
docker logs -f ledger-service | Select-String "ERROR"

# Get last 100 lines of logs
docker logs --tail 100 ledger-service

# View logs with timestamps
docker logs -f --timestamps ledger-service
```

---

## 📦 Project Structure Navigation

```powershell
# Go to root
cd C:\Users\Chamod\Documents\Personal Projects\Ledger Final

# Go to ledger service
cd ledger-service

# Go to API gateway
cd api-gateway

# Go to load tests
cd "Heavy Testings"

# Go to database init scripts
cd db-init

# Go to deployment
cd deployment

# Go to Kubernetes
cd k8s
```

---

## 🧪 Testing Your System

```powershell
# Check all containers are running
docker ps

# Test ledger-service health
curl http://localhost:8081/actuator/health

# Test API gateway health
curl http://localhost:8082/actuator/health

# Test database connection
docker exec <postgres_container_id> psql -U ledger_user -d ledger_db -c "SELECT 1;"

# Test Redis connection
docker exec <redis_container_id> redis-cli PING

# Quick load test (K6)
k6 run "Heavy Testings/k6_ledger_test.js"

# Start Locust UI
python -m locust -f "Heavy Testings/locust_ledger_test.py"
```

---

## 🚢 Typical Development Workflow

```powershell
# 1. Start everything
docker-compose up -d

# 2. Run ledger service (Terminal 1)
cd ledger-service
mvn spring-boot:run

# 3. Run API gateway (Terminal 2)
cd api-gateway
mvn spring-boot:run

# 4. Run tests (Terminal 3)
cd "Heavy Testings"
python -m locust -f locust_ledger_test.py
# Open http://localhost:8089

# 5. Quick checks (Terminal 4)
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
```

---

## 🎯 Performance Tuning Commands

```powershell
# Run K6 with custom settings
k6 run --vus 100 --duration 1m "Heavy Testings/k6_ledger_test.js"

# Generate K6 HTML report
k6 run --out json=results.json "Heavy Testings/k6_ledger_test.js"

# Headless Locust with HTML report
python -m locust -f "Heavy Testings/locust_ledger_test.py" \
    --headless \
    --users 500 \
    --spawn-rate 50 \
    --run-time 5m \
    --html locust_report.html

# Monitor Docker resource usage during tests
docker stats

# Check database size
docker exec <postgres_container_id> psql -U ledger_user -d ledger_db -c "\db"
```

---

## 🔧 Quick Troubleshooting

```powershell
# Container won't start?
docker-compose logs <service_name>

# Port already in use?
netstat -ano | findstr :8081
netstat -ano | findstr :8082
netstat -ano | findstr :8089

# Kill process using a port (Windows)
taskkill /PID <process_id> /F

# Rebuild from scratch
docker-compose down -v
docker-compose up --build -d

# Clear Maven cache
mvn clean
rmdir /s .\target

# Reset test data
docker exec <postgres_container_id> psql -U ledger_user -d ledger_db -c "DELETE FROM idempotency_keys; DELETE FROM transactions; DELETE FROM accounts;"
```

---

## 📋 Environment URLs

| Service | URL | Purpose |
|---------|-----|---------|
| **Ledger Service** | http://localhost:8081 | Core ledger API |
| **API Gateway** | http://localhost:8082 | Public API entry point |
| **Locust Dashboard** | http://localhost:8089 | Load test UI |
| **PostgreSQL** | localhost:5432 | Database |
| **Redis** | localhost:6379 | Caching/coordination |
| **Kafka** | localhost:9092 | Event streaming |

---

## 📝 Common Workflows

### Clean Rebuild
```powershell
docker-compose down -v
docker-compose up --build -d
cd ledger-service && mvn clean package
cd ..\api-gateway && mvn clean package
```

### Run Full Test Suite
```powershell
# Terminal 1: Start services
docker-compose up -d

# Terminal 2: Run ledger
cd ledger-service
mvn spring-boot:run

# Terminal 3: Run gateway
cd api-gateway
mvn spring-boot:run

# Terminal 4: Run K6 (performance validation)
k6 run "Heavy Testings/k6_ledger_test.js"

# Terminal 5: Run Locust (interactive demo)
python -m locust -f "Heavy Testings/locust_ledger_test.py"
```

---

**Last Updated:** June 30, 2026  
**Project:** Ledger Final — High-Throughput Financial Microservice


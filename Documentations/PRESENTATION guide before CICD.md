# Delivering a Stellar Technical Demo

Here is how you can deliver a stellar, highly visual demo to your interviewer using the tools already configured in your project.

---

## 1. Simple Explanation

To make a lasting impression, structure your demo around three main stories:

* **The Life of a Transaction (Happy Path & Logs):** Show them a transaction flowing live. Show the API Gateway accepting the transaction, writing it to Kafka, and the Ledger Service picking it up and persisting it to PostgreSQL.
* **Preventing Double-Spending (Concurrency & Idempotency):** Show what happens when network retries occur. You will run a live stress test, sending identical duplicate transactions, and show how the system immediately filters them out using Redis.
* **Meeting the SLA (Performance Verification):** Run a quick automated performance check using k6 to prove the service processes transactions in under 100 milliseconds even under a load of 500+ concurrent users.

---

## 2. Technical Explanation

Here is the underlying architecture you are demonstrating:

* **Asynchronous Gateway Pattern:** When a client sends a transaction request, the Gateway performs a fast lookup in Redis for the idempotency key. If unique, the Gateway immediately logs it to Kafka and returns an HTTP `202 Accepted` to the client. This keeps response times extremely low (~20-40ms).
* **Distributed Deduplication:** If the client retries (simulated during the load test), the Gateway calls `SET key value NX` on Redis. Since the key already exists, Redis returns `nil` and the Gateway rejects the request with an HTTP `409 Conflict`, stopping the duplicate from reaching Kafka or PostgreSQL.
* **Database OCC Versioning:** The `version` column in PostgreSQL prevents race conditions. Even if multiple instances of the ledger processor pull messages concurrently, the database locks updates if the version changes, enforcing transactional consistency.

---

## 3. Step-by-Step Demo Walkthrough

You can perform this demonstration live on a screen share in about 5 minutes:

### Step 1: Spin Up the Stack

Prepare your workspace and ensure clean databases:

```powershell
# 1. Package the Java code (skipping tests for speed)
./mvnw clean package -DskipTests

# 2. Reset and boot the containers clean
docker-compose down -v
docker-compose up -d
```

### Step 2: Show the Happy Path & Idempotency (Live Logs)
Split your terminal or use side-by-side windows:

In Terminal A, tail the ledger service logs:

```powershell
docker-compose logs -f ledger-service
```

In Terminal B, make a transfer request using curl:

```powershell
curl -X POST http://localhost:8082/api/v1/ledger/transfer `
  -H "Content-Type: application/json" `
  -H "X-Idempotency-Key: demo-key-101" `
  -d '{"accountId": "123e4567-e89b-12d3-a456-426614174000", "amount": 150.00, "currency": "USD", "transactionType": "DEPOSIT"}'
```

**What to show:** Note the immediate 202 Accepted response, and point to the log in Terminal A showing the event processor saving the event.

Execute the same curl command again:

**What to show:** Point out the immediate 409 Conflict response. Explain that Redis filtered this out at the gate, keeping your event stream clean and preventing a double deposit.

### Step 3: Run the Live Dashboard (Locust)
This is the most visually engaging part of the demo:

Start the Locust test script:

```powershell
locust -f "Heavy Testings/locust_ledger_test.py"
```

Open http://localhost:8089 in your browser and enter:

* Number of users: 500
* Spawn rate: 50
* Host: http://localhost:8082

**What to show:** Show the charts climbing. Point out the failures tab—you will see a steady stream of 409 Conflict status codes. Explain to the interviewer that these are intentional failures showing the idempotency layer successfully blocking double-spends under high concurrent loads.

### Step 4: Run the CLI Quality Gate (k6)
Show that you treat performance as a build requirement:

Run the k6 load test:

```powershell
k6 run "Heavy Testings/k6_ledger_test.js"
```

**What to show:** Scroll to the bottom of the output and highlight the green checkmarks next to the thresholds. Show that the p(95) response time stayed below 100ms (e.g., ✓ http_req_duration: p(95)=68ms). Explain that this script acts as a quality gate in your CI/CD pipeline.

---

## 4. Common Mistakes During a Live Demo

**Forgetting to reset state:** If you run the Locust test first, the database will be populated with the test accounts, which might make your manual curl commands return unexpected balances. Always run your manual CLI demos first, or reset the databases using `docker-compose down -v && docker-compose up -d` before demonstrating the happy path.

**Running out of resources:** Running 500 concurrent virtual users inside Docker on a single laptop can spike CPU usage and artificially inflate latency. Close memory-hogging apps (like heavy browser tabs or IDEs) before starting the test to ensure p95 latency stays under 100ms.

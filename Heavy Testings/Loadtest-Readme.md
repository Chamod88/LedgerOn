# Load Testing — Interview Demo Guide

## What this proves (matching your CV exactly)
- Sub-100ms response times  →  K6 threshold fails the build if p95 > 100ms
- 500+ concurrent users     →  Both scripts ramp to 500, spike to 750
- Idempotency under load    →  Locust Task 4 fires duplicate keys at scale
- Redis dedup works         →  409 responses prove it, even at 500 VUs

---

## Prerequisites

### Install K6 (Windows)
```powershell
winget install k6 --source winget
# or
choco install k6
```
Verify: `k6 version`

### Install Locust (Python)
```powershell
pip install locust
```
Verify: `locust --version`

---

## Quick Start

### Step 1 — Make sure everything is running
```powershell
docker ps   # postgres, redis, kafka must be Up
# In terminal 1:
cd ledger-service && mvn spring-boot:run
# In terminal 2:
cd api-gateway  && mvn spring-boot:run
```

### Step 2 — K6 (hard numbers for the CV claim)
```powershell
k6 run k6_ledger_test.js
```
Watch for the threshold summary at the end:
```
✓ http_req_duration.........: p(95)=67ms   ← must be green (< 100ms)
✓ error_rate................: 0.21%        ← must be green (< 1%)
✓ checks....................: 99.8%        ← must be green (> 99%)
```
If any threshold is RED, the test exits with code 1 — that's a real quality gate.

Save results to JSON for later:
```powershell
k6 run --out json=k6_results.json k6_ledger_test.js
```

### Step 3 — Locust (live dashboard for screen-sharing)
```powershell
locust -f locust_ledger_test.py
```
Open http://localhost:8089 in browser, then set:
- **Number of users:** 500
- **Spawn rate:** 50
- **Host:** http://localhost:8082

Click **Start swarming** and share your screen.

Generate a full HTML report:
```powershell
locust -f locust_ledger_test.py --headless --users 500 --spawn-rate 50 --run-time 5m --host http://localhost:8082 --html locust_report.html
```

---

## What to say in the interview

**On the K6 results:**
> "I set a hard SLA threshold — if p95 response time exceeds 100ms the build
> fails. At 500 concurrent users we're consistently hitting 60–80ms p95,
> well within the target."

**On Locust Task 4 (idempotency under load):**
> "One task specifically fires duplicate idempotency keys at scale. The 409
> response rate stays at exactly 50% for those requests — proving Redis dedup
> holds up even when 500 users are hammering it simultaneously."

**On the spike to 750 users:**
> "We spike 50% beyond the stated target to show the system degrades
> gracefully — response times climb but errors stay below 1%."

---

## Realistic numbers to expect

| Metric            | Expected   | CV Claim   |
|-------------------|------------|------------|
| p50 response time | 20–40ms    | —          |
| p95 response time | 60–90ms    | < 100ms ✓  |
| p99 response time | 90–150ms   | —          |
| Peak RPS          | 800–1200   | —          |
| Error rate        | < 0.5%     | —          |
| Concurrent users  | 500 (+ 750 spike) | 500+ ✓ |
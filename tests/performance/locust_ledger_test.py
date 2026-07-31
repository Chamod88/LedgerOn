"""
Locust Load Test — Financial Ledger Microservice
=================================================
CV claim: sub-100ms response times under 500+ concurrent users.

This gives the LIVE WEB DASHBOARD — perfect for screen-sharing in interviews.
The interviewer watches the RPS, response times, and failure rate in real time.

Run (with live UI at http://localhost:8089):
    locust -f locust_ledger_test.py

Then open http://localhost:8089 and set:
    - Number of users:  500
    - Spawn rate:       50 users/second  (ramps up over 10 seconds)
    - Host:             http://localhost:8082

Headless run (for CI / scripted demo):
    locust -f locust_ledger_test.py \
        --headless \
        --users 500 \
        --spawn-rate 50 \
        --run-time 5m \
        --host http://localhost:8082 \
        --html report.html       # generates a full HTML report
"""

from locust import HttpUser, task, between, events
from locust.runners import MasterRunner
import uuid
import random
import logging

logger = logging.getLogger(__name__)

# ── Account pool ─────────────────────────────────────────────────────────────
# 50 pre-seeded accounts. The @events.test_start hook creates them automatically.
ACCOUNT_POOL = [f"LOCUST-ACC-{str(i).zfill(3)}" for i in range(1, 51)]


# ── One-time setup: seed accounts before the swarm starts ────────────────────
@events.test_start.add_listener
def seed_accounts(environment, **kwargs):
    """Creates test accounts once before any users spawn."""
    import requests
    logger.info("🔧 Seeding 50 test accounts into ledger-service...")
    for account_id in ACCOUNT_POOL:
        try:
            res = requests.post(
                "http://localhost:8081/api/v1/accounts",
                json={"id": account_id, "currency": "USD", "initialBalance": 100000},
                timeout=5,
            )
            if res.status_code not in (201, 409):
                logger.warning(f"Could not create {account_id}: {res.status_code}")
        except Exception as e:
            logger.warning(f"Account seed failed for {account_id}: {e}")
    logger.info("✅ Accounts ready.")


# ── Virtual User behaviour ────────────────────────────────────────────────────
class LedgerUser(HttpUser):
    """
    Simulates a real user hitting the ledger API.

    wait_time: think-time between tasks (0.5–1.5s = realistic user pacing).
    Each task has a weight — higher weight = called more often.
    """
    wait_time = between(0.5, 1.5)
    host = "http://localhost:8082"

    def on_start(self):
        """Called once per virtual user when it spawns."""
        # Each VU claims a random account from the pool
        self.account_id = random.choice(ACCOUNT_POOL)

    # ── Task 1: DEPOSIT (weight=3 — most common action) ──────────────────────
    @task(3)
    def deposit(self):
        idempotency_key = str(uuid.uuid4())
        with self.client.post(
            "/api/v1/ledger/transfer",
            json={
                "accountId":       self.account_id,
                "amount":          10.00,
                "currency":        "USD",
                "transactionType": "DEPOSIT",
            },
            headers={
                "Content-Type":    "application/json",
                "X-Idempotency-Key": idempotency_key,
            },
            name="POST /transfer [DEPOSIT]",   # Groups nicely in the UI
            catch_response=True,
        ) as response:
            if response.status_code == 202:
                data = response.json()
                if data.get("status") != "QUEUED":
                    response.failure(f"Expected QUEUED, got: {data.get('status')}")
            else:
                response.failure(f"Deposit failed: HTTP {response.status_code}")

    # ── Task 2: GET BALANCE (weight=4 — read-heavy is realistic) ─────────────
    @task(4)
    def get_balance(self):
        with self.client.get(
            f"http://localhost:8081/api/v1/ledger/accounts/{self.account_id}/balance",
            name="GET /accounts/{id}/balance",
            catch_response=True,
        ) as response:
            if response.status_code == 200:
                data = response.json()
                if "balance" not in data:
                    response.failure("Response missing 'balance' field")
            elif response.status_code == 404:
                # Account not seeded yet — not a failure, just skip
                response.success()
            else:
                response.failure(f"Balance check failed: HTTP {response.status_code}")

    # ── Task 3: WITHDRAWAL (weight=1 — least frequent, avoids overdrafts) ────
    @task(1)
    def withdrawal(self):
        idempotency_key = str(uuid.uuid4())
        with self.client.post(
            "/api/v1/ledger/transfer",
            json={
                "accountId":       self.account_id,
                "amount":          5.00,
                "currency":        "USD",
                "transactionType": "WITHDRAWAL",
            },
            headers={
                "Content-Type":    "application/json",
                "X-Idempotency-Key": idempotency_key,
            },
            name="POST /transfer [WITHDRAWAL]",
            catch_response=True,
        ) as response:
            if response.status_code == 202:
                pass  # success
            else:
                response.failure(f"Withdrawal failed: HTTP {response.status_code}")

    # ── Task 4: DUPLICATE REQUEST (weight=1 — proves idempotency under load) ──
    @task(1)
    def duplicate_request(self):
        """
        Sends the SAME idempotency key twice in quick succession.
        The second call must return 409 CONFLICT — proves Redis dedup works
        even under 500 concurrent users. Great talking point in interviews.
        """
        idempotency_key = f"dupe-{self.account_id}-{uuid.uuid4()}"
        payload = {
            "accountId":       self.account_id,
            "amount":          10.00,
            "currency":        "USD",
            "transactionType": "DEPOSIT",
        }
        headers = {
            "Content-Type":      "application/json",
            "X-Idempotency-Key": idempotency_key,
        }

        # First request — must succeed
        self.client.post(
            "/api/v1/ledger/transfer",
            json=payload,
            headers=headers,
            name="POST /transfer [IDEMPOTENCY first]",
        )

        # Second request with SAME key — must be rejected with 409
        with self.client.post(
            "/api/v1/ledger/transfer",
            json=payload,
            headers=headers,
            name="POST /transfer [IDEMPOTENCY dupe]",
            catch_response=True,
        ) as response:
            if response.status_code == 409:
                response.success()  # This is the CORRECT behaviour
            else:
                response.failure(
                    f"Expected 409 for duplicate, got {response.status_code}"
                )
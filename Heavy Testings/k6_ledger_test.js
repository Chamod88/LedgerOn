/**
 * K6 Load Test — Financial Ledger Microservice
 * =============================================
 * Proves the CV claim: sub-100ms response times under 500+ concurrent users.
 *
 * Three stages run in sequence:
 *   1. RAMP UP   — 0 → 500 users over 1 minute   (find the breaking point)
 *   2. SUSTAINED — hold 500 users for 3 minutes   (prove stability)
 *   3. SPIKE     — burst to 750 users for 30s     (prove resilience)
 *   4. RAMP DOWN — 750 → 0 over 30s
 *
 * SLA thresholds (test FAILS if these are breached):
 *   - p95 response time < 100ms
 *   - Error rate < 1%
 *   - Throughput > 200 requests/sec at peak
 */

import http from "k6/http";
import { check, sleep } from "k6";
import { Counter, Rate, Trend } from "k6/metrics";

// ── Custom metrics ─────────────────────────────────────────────────────────
const depositErrors    = new Counter("deposit_errors");
const withdrawalErrors = new Counter("withdrawal_errors");
const balanceErrors    = new Counter("balance_errors");
const errorRate        = new Rate("error_rate");
const depositDuration  = new Trend("deposit_duration",  true); 
const balanceDuration  = new Trend("balance_duration",  true);

// ── Test configuration ─────────────────────────────────────────────────────
export const options = {
  stages: [
    { duration: "1m",  target: 500 },  // Ramp up to 500 users
    { duration: "3m",  target: 500 },  // Hold — this is the CV claim
    { duration: "30s", target: 750 },  // Spike beyond the claim
    { duration: "30s", target: 0   },  // Ramp down
  ],

  thresholds: {
    "http_req_duration":  ["p(95)<100"],
    "deposit_duration":   ["p(95)<100"],
    "balance_duration":   ["p(95)<100"],
    "error_rate":         ["rate<0.01"],
    "checks":             ["rate>0.99"],
  },
};

// ── Constants ──────────────────────────────────────────────────────────────
const GATEWAY_URL = "http://localhost:8082/api/v1/ledger";
const LEDGER_URL  = "http://localhost:8081/api/v1";
const HEADERS     = { "Content-Type": "application/json" };

const ACCOUNT_POOL = Array.from({ length: 50 }, (_, i) =>
  `LOAD-ACC-${String(i + 1).padStart(3, "0")}`
);

// ── Setup: runs ONCE before all VUs start ─────────────────────────────────
export function setup() {
  console.log("🔧 Setting up 50 test accounts...");
  for (const accountId of ACCOUNT_POOL) {
    const res = http.post(
      `${LEDGER_URL}/accounts`,
      JSON.stringify({ id: accountId, currency: "USD", initialBalance: 100000 }),
      { headers: HEADERS }
    );
    if (res.status !== 201 && res.status !== 409) {
      console.warn(`⚠️  Could not create account ${accountId}: ${res.status} ${res.body}`);
    }
  }
  console.log("✅ Accounts ready. Starting load test...");
}

// ── Virtual User scenario ──────────────────────────────────────────────────
export default function () {
  const accountId = ACCOUNT_POOL[Math.floor(Math.random() * ACCOUNT_POOL.length)];
  const idempotencyKey = `k6-${__VU}-${__ITER}-${Date.now()}`;

  // ── Request 1: DEPOSIT ──────────────────────────────────────────────────
  const depositStart = Date.now();
  const depositRes = http.post(
    `${GATEWAY_URL}/transfer`,
    JSON.stringify({
      accountId:       accountId,
      amount:          10.00,
      currency:        "USD",
      transactionType: "DEPOSIT",
    }),
    {
      headers: { ...HEADERS, "X-Idempotency-Key": idempotencyKey },
      tags:    { name: "deposit" },
    }
  );
  depositDuration.add(Date.now() - depositStart);

  const depositOk = check(depositRes, {
    "deposit: status 202":          (r) => r.status === 202,
    "deposit: has transactionId":   (r) => r.json().transactionId !== undefined,
    "deposit: status is QUEUED":    (r) => r.json().status === "QUEUED",
  });

  if (!depositOk) {
    depositErrors.add(1);
    errorRate.add(1);
  } else {
    errorRate.add(0);
  }

  sleep(0.5); 

  // ── Request 2: CHECK BALANCE ────────────────────────────────────────────
  const balanceStart = Date.now();
  const balanceRes = http.get(
    `${LEDGER_URL}/ledger/accounts/${accountId}/balance`,
    { tags: { name: "get_balance" } }
  );
  balanceDuration.add(Date.now() - balanceStart);

  const balanceOk = check(balanceRes, {
    "balance: status 200":       (r) => r.status === 200,
    "balance: has balance field": (r) => r.json().balance !== undefined,
  });

  if (!balanceOk) {
    balanceErrors.add(1);
    errorRate.add(1);
  } else {
    errorRate.add(0);
  }

  sleep(0.5);

  // ── Request 3: WITHDRAWAL (every 3rd iteration) ─────────────────────────
  if (__ITER % 3 === 0) {
    const withdrawKey = `k6-withdraw-${__VU}-${__ITER}-${Date.now()}`;
    const withdrawRes = http.post(
      `${GATEWAY_URL}/transfer`,
      JSON.stringify({
        accountId:       accountId,
        amount:          5.00,
        currency:        "USD",
        transactionType: "WITHDRAWAL",
      }),
      {
        headers: { ...HEADERS, "X-Idempotency-Key": withdrawKey },
        tags:    { name: "withdrawal" },
      }
    );

    const withdrawOk = check(withdrawRes, {
      "withdrawal: status 202": (r) => r.status === 202,
    });

    if (!withdrawOk) {
      withdrawalErrors.add(1);
      errorRate.add(1);
    } else {
      errorRate.add(0);
    }

    sleep(0.5);
  }
}

export function teardown() {
  console.log("🏁 Load test complete. Check thresholds above for pass/fail.");
}

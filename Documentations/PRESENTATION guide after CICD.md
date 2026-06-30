# Delivering a Stellar Technical Demo — After CI/CD

This guide updates the original "before CICD" presentation notes to include GitHub Actions CI and CD deployment to Azure (ACR + AKS). It is designed to help you run a short, repeatable live demo that shows both the system behavior and the delivery pipeline that makes it production-ready.

Checklist (what you'll do during the demo)
- Show repository and CI status (GitHub Actions)
- Make a small code change, push a branch → show CI run (build & tests)
- Merge to `main` → show CD run: image build, push to ACR, deploy to AKS
- Exercise the running system (happy-path transaction) on AKS and show logs, DB effect, and Kafka flow
- Demonstrate deduplication / idempotency with duplicate requests (show Redis behavior)
- Run the performance test (k6) and show basic SLA metrics
- Show a failure case and how CI prevents broken code from reaching production
- Call out security and operational considerations (secrets, image tags, immutability)

High-level stories (short version — what to narrate)

1) The Life of a Transaction — now with CI/CD

- What to show:
  - Start by showing GitHub Actions for the repository (`Actions` tab). Point out a recent successful CI run and a successful CD run after a merge to `main`.
  - Explain what CI does: runs `./mvnw -B clean verify` (unit & integration tests), caches Maven dependencies, uploads test reports.
  - Explain what CD does: builds container images (multi-stage Dockerfiles), pushes images to Azure Container Registry (ACR) with an immutable tag (commit SHA), and deploys updated Kubernetes manifests to AKS.
  - Now demonstrate a live transaction against the AKS-deployed API Gateway and show it traverse Kafka and arrive in PostgreSQL.

- Why this matters for the interviewer:
  - You show both functional correctness (the transaction completes end-to-end) and delivery maturity (automated tests, immutable artifacts, reproducible deployments).

2) Preventing Double-Spending (Concurrency & Idempotency)

- What to show:
  - Run two identical requests quickly (simulate a retry). Show the Gateway performing a Redis SET NX check and responding with `202` for the first and `409`/reject for the duplicate.
  - Pull logs from the Gateway pod in AKS (`kubectl logs`) to show the idempotency decision.
  - Show unit/integration tests in CI that exercise idempotency — point to the test artifact uploaded by CI.

- Why this matters:
  - Demonstrates correctness under retries and shows automated tests catch regressions before they reach production.

3) Meeting the SLA (Performance Verification)

- What to show:
  - Run the included k6 script (`Heavy Testings/k6_ledger_test.js`) locally against the AKS endpoint or a staging endpoint.
  - Show response time percentiles and throughput and compare to a target SLA (e.g., 100ms per request).
  - If you have a CI stage that runs load tests (optional), explain that this can be gated into the CD to promote builds only when performance is acceptable.

- Why this matters:
  - Performance metrics show you considered non-functional requirements and can prove capacity/SLA during the interview.

Step-by-step demo walkthrough (aim for ~5 minutes)

Step 0 — Prep (before the call)
- Ensure the GitHub repository `Chamod88/LedgerOn` has the workflow secrets configured (`AZURE_CREDENTIALS`, `ACR_NAME`, `AKS_NAME`, `AKS_RESOURCE_GROUP`).
- Ensure you have `kubectl`, `az`, `docker` and `gh` (optional) installed locally.
- Have the AKS cluster up and the demo namespace available.

Step 1 — Show the repo and CI status (30s)
- Open the repo in the browser and click `Actions`. Show a recent green run for `CI` and `CD` to establish credibility.

Step 2 — Small code change to trigger CI (60–90s)
- Create a tiny, safe change (modify a log message or add a unit test). Use a feature branch, push it, and show the CI run starting.

PowerShell commands (make safe edits locally):
```powershell
# create a branch, make a small change, commit and push
git checkout -b demo/change-logging
# Edit a small file in your editor (e.g., change a log line in api-gateway)
git add -A
git commit -m "demo: tweak log message for presentation"
git push -u origin demo/change-logging
```

- While CI runs, explain the steps it performs: compile, unit tests, caching, artifact upload. Click into the running job and show the logs (tests, surefire reports).

Step 3 — Merge to `main` → CD runs (60–90s)
- Merge the PR to `main` (or merge locally and push main). CD should trigger via `workflow_run` after CI completes. Show in `Actions` that `CD` starts.

PowerShell commands to merge locally and push (option):
```powershell
git checkout main
git merge demo/change-logging
git push origin main
```

- While CD runs, open the CD job logs and show:
  - Azure login step
  - ACR login and `az acr show` (to resolve the login server)
  - Docker `build-push` action building the `ledger-service` and `api-gateway` images
  - kubectl context set for AKS and `kubectl apply` steps

Step 4 — Verify deployment on AKS (60s)
- Get AKS credentials (this is performed in the workflow; locally you can also:
```powershell
az aks get-credentials --resource-group <RG> --name <AKS_CLUSTER> --overwrite-existing
kubectl get pods -n default -l app=api-gateway
kubectl logs -f deployment/api-gateway
```

- Confirm the updated image tag is in the deployment:
```powershell
kubectl describe deployment api-gateway | Select-String "Image"
```

Step 5 — Show a happy-path transaction (45–60s)
- POST a transaction to the API Gateway (replace <HOST> with your LoadBalancer or ingress host):
```powershell
curl -X POST "http://<HOST>/transactions" -H "Content-Type: application/json" -d '{"accountId":"acct-123","amount":100.00,"idempotencyKey":"demo-1"}'
```

- Show:
  - Gateway logs indicating message accepted and pushed to Kafka
  - Ledger service logs indicating it consumed the Kafka event and persisted to PostgreSQL
  - Query PostgreSQL to show the record exists (via `psql` or connecting to the DB pod/managed DB)

Step 6 — Demonstrate deduplication (45s)
- Re-run the same `curl` POST with the same `idempotencyKey` and show the Gateway rejects it (HTTP 409 or similar). Show the Redis key behaviour if desired:
```powershell
# check redis keys (if using port-forward locally)
kubectl port-forward svc/redis 6379:6379 &
redis-cli -h 127.0.0.1 GET demo-1
```

Step 7 — Run a quick performance check (90s)
- Locally run the k6 script found in `Heavy Testings/k6_ledger_test.js` against the deployed endpoint to show latency percentiles and requests/sec.
```powershell
# example k6 run (adjust URL in the script or pass an env var)
k6 run Heavy Testings/k6_ledger_test.js
```

- Show the output from k6: p50, p95, p99 and failures. If p95 < 100ms you can claim the SLA.

Step 8 — Show rollback / safety (optional, 60s)
- Explain how CI prevents bad code: demonstrate a commit that breaks a unit test, push it and show CI failing and CD not running. This shows the safety net.

Operational and security talking points (quick bullets)
- Image immutability: we tag images with commit SHA and avoid `:latest` for production to ensure reproducible deployments.
- Secrets management: do not store passwords in `k8s/config.yaml` in the repo. Use GitHub Secrets and Azure Key Vault; CI should create k8s secrets at deploy time.
- ACR & AKS auth: prefer AKS managed identity with `AcrPull` role to avoid ACR admin user.
- Observability: actuator endpoints ( `/actuator/health` ) are used for readiness/liveness probes; extend with Prometheus/Grafana if needed.

Common mistakes to call out
- Committing secrets in repo (we have `ledger-secret` base64 in `k8s/config.yaml`) — explain the risk and how to fix it.
- Relying on `:latest` — unpredictable deployment behaviour and caching/pull issues.
- Building containers on developer machines and not in CI — leads to different images and "works on my machine" issues. Show that CD builds the images in pipeline so what is deployed is what built in CI/CD.

Short Q&A checkpoints (ask the interviewer or self-check)
- Why do we tag images with commit SHAs instead of `latest`?
- What protects us from duplicate transaction requests? (Answer: Redis idempotency key + tests)
- If a performance regression appears in CD, how would you prevent deployment? (Answer: add a performance gate in the pipeline)

Appendix — Useful commands and places to click (quick reference)

- GitHub: open the repository and click `Actions` to see `CI` and `CD` workflows.
- To run CI locally (quick sanity):
```powershell
./mvnw -B -DskipTests=false clean verify
```

- Trigger a manual CD-style build locally (build Docker image):
```powershell
docker build -f api-gateway/Dockerfile -t api-gateway:local .
docker build -f ledger-service/Dockerfile -t ledger-service:local .
```

- Push to ACR (example):
```powershell
az acr login --name <ACR_NAME>
docker tag ledger-service:local <ACR_LOGIN_SERVER>/ledger-service:local
docker push <ACR_LOGIN_SERVER>/ledger-service:local
```

- Inspect Kubernetes after CD deploy:
```powershell
az aks get-credentials --resource-group <RG> --name <AKS_CLUSTER> --overwrite-existing
kubectl get pods -o wide
kubectl logs deployment/api-gateway -f
kubectl describe deployment api-gateway
```

Finish line: wrap up in 30s
- Reiterate the three stories: transaction life, deduplication, SLA.
- Emphasize the delivery pipeline: automated tests in CI + reproducible deployments via CD.
- Offer to deep-dive on any single area: reliability, security, or performance.

-- End of guide --


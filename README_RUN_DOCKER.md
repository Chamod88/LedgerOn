Running the Ledger project with Docker Compose (step-by-step)

Summary
-------
This repository includes a `docker-compose.yml` that starts Postgres, Redis, Kafka,
the `ledger-service` and the `api-gateway`. The easiest way to run the system on
another Windows machine is with Docker Desktop and the included PowerShell helper script.

Prerequisites
-------------
- Docker Desktop (with WSL2 backend recommended) installed and running.
- Git (optional) to clone the repository, or copy the repository folder manually.
- PowerShell (Windows default). The included script targets PowerShell v5.1 and later.

Quick checklist
---------------
1. Copy/clone the repository to the other computer.
2. Create a `.env` in the repo root (copy from `.env.example`).
3. (Optional) Edit `.env` to change passwords or ports.
4. Run `scripts\run-docker.ps1` from PowerShell as described below.

Step-by-step commands (PowerShell)
----------------------------------
Open PowerShell, then run these commands in the directory where you cloned or copied the repo.

1) Clone the repo (if you have a remote URL):

```powershell
git clone <repo-url> "Ledger Final"
Set-Location "Ledger Final"
```

2) Copy the example `.env` to `.env` (only if you don't already have an `.env`):

```powershell
if (-not (Test-Path -Path .env)) { Copy-Item .env.example .env }
notepad .env # optional: edit and save
```

3) Run the helper script (recommended):

```powershell
# If running for the first time, allow script execution in this PowerShell session only:
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass

# Run the script that boots Docker compose and waits for health checks
.\scripts\run-docker.ps1
```

What the script does
- Verifies Docker is available.
- Ensures `.env` exists (copies from `.env.example` if it does not).
- Runs `docker compose up --build -d`.
- Polls the gateway `http://localhost:<API_GATEWAY_PORT>/actuator/health` until healthy or times out.

Verify services
---------------
- Gateway health: http://localhost:8082/actuator/health (or port set in `.env`).
- Ledger service health: http://localhost:8081/actuator/health

Useful Docker commands
----------------------
- Show containers and their status:

```powershell
docker compose ps
```

- View combined logs and follow:

```powershell
docker compose logs -f
```

- Stop and remove containers:

```powershell
docker compose down
```

Troubleshooting
---------------
- If Kafka fails to become healthy on Windows, check container logs:

```powershell
docker compose logs kafka
```

- If Postgres schema didn't initialize, ensure the `db-init/init.sql` exists and the Postgres container had permission to run it on first startup. You can re-create the Postgres container (be aware this will remove data):

```powershell
docker compose rm -s -f postgres
docker compose up -d postgres
```

Security note
-------------
`.env` contains credentials. Do not commit `.env` with real secrets. Use a secret manager for production.

If you want, I can also:
- Add a small script to tear down and remove volumes safely.
- Create a one-line GitHub Actions job to build the images.


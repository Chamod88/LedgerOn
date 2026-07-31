<#
run-docker.ps1

Helper script to start the project with Docker Compose on Windows PowerShell.
It will:
 - check for docker availability
 - ensure .env exists (copy from .env.example if missing)
 - run docker compose up --build -d
 - poll gateway health endpoint until it's healthy or timeout
#>

Set-StrictMode -Version Latest

function Write-ErrAndExit($msg, $code=1) {
    Write-Host "ERROR: $msg" -ForegroundColor Red
    exit $code
}

# Check Docker
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-ErrAndExit "Docker CLI not found. Please install Docker Desktop and ensure 'docker' is on PATH."
}

$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $repoRoot

# Ensure .env exists
if (-not (Test-Path -Path .env)) {
    if (Test-Path -Path .env.example) {
        Copy-Item -Path .env.example -Destination .env
        Write-Host "Created .env from .env.example. Edit .env if you need to change credentials or ports."
    } else {
        Write-ErrAndExit "No .env or .env.example found in $repoRoot"
    }
}

# Load API_GATEWAY_PORT from .env (simple parser)
$envContent = Get-Content .env | Where-Object { $_ -and ($_ -notmatch '^\s*#') }
$envVars = @{}
foreach ($line in $envContent) {
    if ($line -match '=') {
        $parts = $line -split '=',2
        $key = $parts[0].Trim()
        $val = $parts[1].Trim()
        $envVars[$key] = $val
    }
}

$gatewayPort = $envVars['API_GATEWAY_PORT']
if (-not $gatewayPort) { $gatewayPort = 8082 }

Write-Host "Starting Docker Compose (this will build images if needed)..."
docker compose up --build -d

Write-Host "Waiting for gateway to become healthy at http://localhost:$gatewayPort/actuator/health"
$maxSeconds = 180
$interval = 5
$elapsed = 0
while ($elapsed -lt $maxSeconds) {
    try {
        $resp = Invoke-WebRequest -Uri "http://localhost:$gatewayPort/actuator/health" -UseBasicParsing -TimeoutSec 3 -ErrorAction Stop
        if ($resp.StatusCode -eq 200) {
            Write-Host "Gateway is healthy (HTTP 200)."
            break
        }
    } catch {
        # ignore and retry
    }
    Start-Sleep -Seconds $interval
    $elapsed += $interval
    Write-Host "Still waiting... ($elapsed s)"
}

if ($elapsed -ge $maxSeconds) {
    Write-Host "Timed out waiting for gateway health. Check logs with: docker compose logs api-gateway" -ForegroundColor Yellow
    docker compose ps
    exit 2
}

Write-Host "All done. To follow logs run: docker compose logs -f" -ForegroundColor Green
Write-Host "Gateway: http://localhost:$gatewayPort/actuator/health"
Write-Host "Ledger service: http://localhost:8081/actuator/health (or check LEDGER_SERVICE_PORT in .env)"


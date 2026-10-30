# apply-fixes.ps1 - run from C:\Projects\agentprobe
$root = (Get-Location).Path
if (-not (Test-Path "$root\pom.xml")) {
    Write-Host "Run this from C:\Projects\agentprobe (the folder that contains pom.xml)." -ForegroundColor Red
    exit 1
}

function Write-File($path, $content) {
    $full = Join-Path $root $path
    $dir = Split-Path $full
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    $utf8 = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($full, ($content -replace "`r`n", "`n"), $utf8)
    Write-Host "  wrote $path"
}

Write-Host "Writing deployment files..."

Write-File '.dockerignore' @'
.git
.github
.idea
.vscode
.env
target
cli/target
ui
docs
observability
*.md
*.log
'@

Write-File 'ui/.dockerignore' @'
node_modules
dist
.env
*.log
'@

Write-File '.env.example' @'
# Copy this file to ".env" and fill in every value. Never commit ".env".
POSTGRES_PASSWORD=change-me
GRAFANA_PASSWORD=change-me
# At least 32 random characters
JWT_SECRET=change-me-to-a-random-string-of-at-least-32-characters
# Base64 of 32 random bytes
ENCRYPTION_KEY=
# Model server reachable from inside Docker (Ollama running on the host machine)
OLLAMA_BASE_URL=http://host.docker.internal:11434
'@

Write-File 'docker-compose.prod.yml' @'
services:
  postgres:
    image: pgvector/pgvector:pg16
    environment:
      POSTGRES_DB: agentprobe
      POSTGRES_USER: agentprobe
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?set POSTGRES_PASSWORD in .env}
    volumes: [pgdata:/var/lib/postgresql/data]
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U agentprobe"]
      interval: 5s
      retries: 10
    restart: unless-stopped

  redis:
    image: redis:7
    restart: unless-stopped

  # Optional local model server. Start with: docker compose -f docker-compose.prod.yml --profile ollama up -d
  ollama:
    image: ollama/ollama
    profiles: ["ollama"]
    volumes: [ollama:/root/.ollama]
    restart: unless-stopped

  agentprobe:
    build: .
    environment:
      # No "demo" profile in production: it creates a user with a public password.
      SPRING_PROFILES_ACTIVE: prod
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/agentprobe
      SPRING_DATASOURCE_USERNAME: agentprobe
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD:?set POSTGRES_PASSWORD in .env}
      SPRING_DATA_REDIS_HOST: redis
      SPRING_AI_OLLAMA_BASE_URL: ${OLLAMA_BASE_URL:-http://host.docker.internal:11434}
      JWT_SECRET: ${JWT_SECRET:?set JWT_SECRET in .env}
      ENCRYPTION_KEY: ${ENCRYPTION_KEY:?set ENCRYPTION_KEY in .env}
      ALLOW_PRIVATE_TARGETS: "false"
      # Host names of agents that are allowed to resolve to private addresses (comma separated)
      AGENTPROBE_SECURITY_ALLOWED_HOSTS: shopbot
      AGENTPROBE_RATELIMIT_TRUST_FORWARDED_FOR: "true"
      AGENTPROBE_PRIVACY_REDACT_TRANSCRIPTS: "true"
    extra_hosts: ["host.docker.internal:host-gateway"]
    depends_on:
      postgres: { condition: service_healthy }
      redis: { condition: service_started }
    restart: unless-stopped

  # Optional: only needed if the ShopBot project sits next to this one (../shopbot).
  # Start with: docker compose -f docker-compose.prod.yml --profile shopbot up -d --build
  shopbot:
    build: ../shopbot
    profiles: ["shopbot"]
    environment:
      SPRING_AI_OLLAMA_BASE_URL: ${OLLAMA_BASE_URL:-http://host.docker.internal:11434}
    extra_hosts: ["host.docker.internal:host-gateway"]
    restart: unless-stopped

  ui:
    build: ./ui
    ports: ["80:80"]
    depends_on: [agentprobe]
    restart: unless-stopped

  prometheus:
    image: prom/prometheus
    volumes: ["./observability/prometheus.yml:/etc/prometheus/prometheus.yml:ro"]
    restart: unless-stopped

  grafana:
    image: grafana/grafana
    environment:
      GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_PASSWORD:?set GRAFANA_PASSWORD in .env}
    volumes:
      - ./observability/grafana/provisioning:/etc/grafana/provisioning:ro
      - ./observability/grafana/dashboards:/var/lib/grafana/dashboards:ro
    # Only reachable from this machine. Put it behind your proxy if you need remote access.
    ports: ["127.0.0.1:3000:3000"]
    depends_on: [prometheus]
    restart: unless-stopped

volumes:
  pgdata:
  ollama:
'@

Write-File '.github/workflows/ci.yml' @'
name: CI
on:
  push:
  pull_request:
jobs:
  backend:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: pgvector/pgvector:pg16
        env:
          POSTGRES_DB: agentprobe
          POSTGRES_USER: agentprobe
          POSTGRES_PASSWORD: agentprobe
        ports: ["5433:5432"]
        options: >-
          --health-cmd "pg_isready -U agentprobe" --health-interval 5s --health-timeout 5s --health-retries 10
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven
      - run: mvn -B verify

  cli:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven
      - run: mvn -B -f cli/pom.xml package -DskipTests
      - run: java -jar cli/target/agentprobe-cli.jar --help

  ui:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: npm
          cache-dependency-path: ui/package-lock.json
      - run: npm ci
        working-directory: ui
      - run: npm run build
        working-directory: ui
'@


Write-Host "Fixing the Grafana folder layout..."
$g = "$root\observability\grafana"
$old = "$g\provisioning\datasources\dashboards"
if (Test-Path "$old\dashboards.yml") {
    New-Item -ItemType Directory -Force "$g\provisioning\dashboards" | Out-Null
    New-Item -ItemType Directory -Force "$g\dashboards" | Out-Null
    Move-Item "$old\dashboards.yml" "$g\provisioning\dashboards\dashboards.yml" -Force
    if (Test-Path "$old\dashboards\agentprobe.json") {
        Move-Item "$old\dashboards\agentprobe.json" "$g\dashboards\agentprobe.json" -Force
    }
    Remove-Item $old -Recurse -Force
    Write-Host "  moved dashboard files"
} else {
    Write-Host "  already fixed, nothing to move"
}

function New-Hex($bytes) {
    $b = New-Object byte[] $bytes
    $rng = New-Object System.Security.Cryptography.RNGCryptoServiceProvider
    $rng.GetBytes($b)
    return ($b | ForEach-Object { $_.ToString("x2") }) -join ""
}
function New-Base64($bytes) {
    $b = New-Object byte[] $bytes
    $rng = New-Object System.Security.Cryptography.RNGCryptoServiceProvider
    $rng.GetBytes($b)
    return [Convert]::ToBase64String($b)
}

if (-not (Test-Path "$root\.env")) {
    Write-Host "Creating .env with fresh random secrets..."
    $envText = @"
POSTGRES_PASSWORD=$(New-Hex 16)
GRAFANA_PASSWORD=$(New-Hex 12)
JWT_SECRET=$(New-Hex 32)
ENCRYPTION_KEY=$(New-Base64 32)
OLLAMA_BASE_URL=http://host.docker.internal:11434
"@
    $utf8 = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText("$root\.env", ($envText -replace "`r`n", "`n"), $utf8)
    Write-Host "  wrote .env (keep it private, it is already in .gitignore)"
} else {
    Write-Host ".env already exists, left unchanged."
}

Write-Host ""
Write-Host "Done." -ForegroundColor Green
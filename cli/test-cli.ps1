$base = "http://localhost:8081"

function Stop-Here($msg) {
    Write-Host ""
    Write-Host "STOPPED: $msg" -ForegroundColor Red
    exit 1
}

$email = Read-Host "Email you use to log in to AgentProbe"
$secure = Read-Host "Password" -AsSecureString
$password = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))

Write-Host "Step 1/5: logging in..."
try {
    $loginBody = @{ email = $email; password = $password } | ConvertTo-Json
    $login = Invoke-RestMethod -Method Post -Uri "$base/api/auth/login" -ContentType "application/json" -Body $loginBody
} catch {
    Stop-Here "Login failed. Is the backend running at $base, and are the email and password right? ($($_.Exception.Message))"
}
$headers = @{ Authorization = "Bearer $($login.accessToken)" }
Write-Host "  OK" -ForegroundColor Green

Write-Host "Step 2/5: finding your project..."
try {
    $projects = @(Invoke-RestMethod -Uri "$base/api/projects" -Headers $headers)
} catch {
    Stop-Here "Could not list projects. ($($_.Exception.Message))"
}
if ($projects.Count -eq 0) { Stop-Here "You have no projects. Create one in the app first." }
Write-Host "  Projects found: $(($projects | ForEach-Object { $_.name }) -join ', ')"
$project = $projects | Where-Object { $_.name -like "*shopbot*" } | Select-Object -First 1
if (-not $project) { $project = $projects[0] }
Write-Host "  Using project: $($project.name) ($($project.id))" -ForegroundColor Green

Write-Host "Step 3/5: finding your agent..."
try {
    $agents = @(Invoke-RestMethod -Uri "$base/api/projects/$($project.id)/agents" -Headers $headers)
} catch {
    Stop-Here "Could not list agents. ($($_.Exception.Message))"
}
if ($agents.Count -eq 0) { Stop-Here "This project has no agents. Create one in the Agents tab first." }
$agent = $agents | Where-Object { $_.name -like "*shopbot*" } | Select-Object -First 1
if (-not $agent) { $agent = $agents[0] }
Write-Host "  Using agent: $($agent.name) ($($agent.id))" -ForegroundColor Green

try {
    $versions = @(Invoke-RestMethod -Uri "$base/api/projects/$($project.id)/agents/$($agent.id)/versions" -Headers $headers)
} catch {
    Stop-Here "Could not list agent versions. ($($_.Exception.Message))"
}
if ($versions.Count -eq 0) { Stop-Here "This agent has no versions. Register one in the Agents tab first." }
$version = $versions[0]
Write-Host "  Using version: $($version.versionLabel)" -ForegroundColor Green

Write-Host "Step 4/5: creating an API token..."
try {
    $tokenBody = @{ name = "cli-test-" + (Get-Date -Format "HHmmss") } | ConvertTo-Json
    $created = Invoke-RestMethod -Method Post -Uri "$base/api/tokens" -Headers $headers -ContentType "application/json" -Body $tokenBody
} catch {
    Stop-Here "Could not create an API token. ($($_.Exception.Message))"
}
Write-Host "  OK" -ForegroundColor Green

Write-Host "Step 5/5: running the CLI (this takes a few minutes with a slow agent)..."
Write-Host ""
$jar = Join-Path $PSScriptRoot "target\agentprobe-cli.jar"
if (-not (Test-Path $jar)) { Stop-Here "Cannot find $jar. Run '..\mvnw package -DskipTests' in the cli folder first." }

& java -jar $jar run --project $project.id --suite "ShopBot Basic Tests" --agent $agent.id --agent-version $version.versionLabel --token $created.token --trials 2 --set-baseline-if-missing

$code = $LASTEXITCODE
Write-Host ""
Write-Host "CLI exit code: $code   (0 = passed, 1 = regression, 2 = error or no baseline, 3 = run did not finish)"
if (Test-Path "agentprobe-report.md") {
    Write-Host ""
    Write-Host "----- agentprobe-report.md -----"
    Get-Content "agentprobe-report.md"
}
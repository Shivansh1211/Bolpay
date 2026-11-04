$ErrorActionPreference = 'Stop'

function Invoke-Checked {
    param([string]$Command, [string[]]$Arguments)
    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Command $($Arguments -join ' ') failed ($LASTEXITCODE)" }
}

Write-Host '== Docker Compose configuration =='
Invoke-Checked 'docker' @('compose', 'config', '--quiet')
Write-Host 'PASS: docker compose config'

Write-Host '== Production Compose template configuration =='
$validationEnvFile = Join-Path ([System.IO.Path]::GetTempPath()) "bolpay-validation-$([guid]::NewGuid().ToString('N')).env"
$validationEnvContent = @'
POSTGRES_DB=bolpay_validation
POSTGRES_USER=bolpay_validation
POSTGRES_PASSWORD=validation-only-placeholder-not-a-credential
JWT_SECRET=validation-only-placeholder-secret-0123456789012345678901234567890123456789
PAYMENT_GATEWAY=cashfree
CASHFREE_BASE_URL=https://example.invalid/gateway
CASHFREE_CLIENT_ID=validation-only-client-id
CASHFREE_CLIENT_SECRET=validation-only-client-secret
CASHFREE_WEBHOOK_SECRET=validation-only-webhook-secret
PUBLIC_BASE_URL=https://example.invalid
CORS_ALLOWED_ORIGINS=https://example.invalid
'@
Set-Content -LiteralPath $validationEnvFile -Value $validationEnvContent -Encoding Ascii
try {
    Invoke-Checked 'docker' @('compose', '--env-file', $validationEnvFile, '-f', 'docker-compose.production.yml', 'config', '--quiet')
    Write-Host 'PASS: provider-neutral production Compose template config (dummy validation values only)'
    Invoke-Checked 'docker' @('compose', '--env-file', $validationEnvFile, '-f', 'docker-compose.production.yml', 'build', 'frontend')
    Write-Host 'PASS: production frontend/Nginx image build'
} finally {
    Remove-Item -LiteralPath $validationEnvFile -Force -ErrorAction SilentlyContinue
}

Write-Host '== Build and start services =='
$previousOtpFlag = $env:OTP_RETURN_TO_CLIENT
$previousExpiryAfter = $env:EXPIRE_AFTER
$previousExpiryCheck = $env:EXPIRY_CHECK_MS
$previousCorsOrigins = $env:CORS_ALLOWED_ORIGINS
$env:OTP_RETURN_TO_CLIENT = 'true'
$env:EXPIRE_AFTER = '5'
$env:EXPIRY_CHECK_MS = '250'
$env:CORS_ALLOWED_ORIGINS = 'http://localhost:5173,http://frontend'
try {
    Invoke-Checked 'docker' @('compose', 'up', '--build', '-d')
} finally {
    $env:OTP_RETURN_TO_CLIENT = $previousOtpFlag
    $env:EXPIRE_AFTER = $previousExpiryAfter
    $env:EXPIRY_CHECK_MS = $previousExpiryCheck
    $env:CORS_ALLOWED_ORIGINS = $previousCorsOrigins
}

Write-Host '== Backend tests using disposable PostgreSQL =='
$dbName = 'bolpay-run-all-db'
try {
    Invoke-Checked 'docker' @('run', '-d', '--rm', '--name', $dbName, '--network', 'bolpay_internal', '--network-alias', 'bolpay-run-all-db', '--health-cmd', 'pg_isready -U bolpay_test -d bolpay_test', '--health-interval', '2s', '--health-timeout', '2s', '--health-retries', '30', '-e', 'POSTGRES_DB=bolpay_test', '-e', 'POSTGRES_USER=bolpay_test', '-e', 'POSTGRES_PASSWORD=bolpay_test_only', 'postgres:15-alpine') | Out-Null
    $dbReady = $false
    for ($attempt = 0; $attempt -lt 40; $attempt++) {
        if ((docker inspect --format '{{.State.Health.Status}}' $dbName 2>$null) -eq 'healthy') { $dbReady = $true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $dbReady) { throw 'Disposable PostgreSQL did not become healthy.' }
    $mountPath = (Resolve-Path 'backend').Path.Replace('\', '/')
    Invoke-Checked 'docker' @('run', '--rm', '--network', 'bolpay_internal', '--mount', 'type=volume,source=bolpay-maven-cache,target=/root/.m2', '-v', "${mountPath}:/workspace", '-w', '/workspace', '-e', 'SPRING_DATASOURCE_URL=jdbc:postgresql://bolpay-run-all-db:5432/bolpay_test', '-e', 'POSTGRES_USER=bolpay_test', '-e', 'SPRING_DATASOURCE_PASSWORD=bolpay_test_only', '-e', 'JWT_SECRET=local-test-secret-only-012345678901234567890123456789012345678901234567890123456789', 'maven:3.9-eclipse-temurin-17', 'mvn', '-B', '-ntp', 'test')
} finally {
    docker rm -f $dbName 2>$null | Out-Null
}

Write-Host '== Docker/API smoke tests =='
& "$PSScriptRoot/step1-smoke.ps1"
if ($LASTEXITCODE -ne 0) { throw 'Step 1 smoke tests failed.' }

Write-Host '== Playwright browser and webhook-to-board tests =='
$playwrightImage = 'bolpay-e2e:local'
$artifactDirectory = Join-Path $PSScriptRoot 'artifacts'
New-Item -ItemType Directory -Path $artifactDirectory -Force | Out-Null
$artifactMount = (Resolve-Path $artifactDirectory).Path.Replace('\', '/')
Invoke-Checked 'docker' @('build', '-f', 'tests/e2e/Dockerfile', '-t', $playwrightImage, 'tests/e2e')
Invoke-Checked 'docker' @('run', '--rm', '--network', 'bolpay_internal', '-e', 'API_URL=http://backend:8080', '-e', 'MOCK_URL=http://mock-cashfree:9000', '-e', 'FRONTEND_URL=http://frontend', '-e', 'PLAYWRIGHT_SCREENSHOT_DIR=/results', '-e', 'PLAYWRIGHT_METRICS_PATH=/results/latency.json', '-v', "${artifactMount}:/results", $playwrightImage)
Write-Host 'PASS: Playwright customer flow, vendor live board, voice announcement, screenshots, and webhook latency samples'

Write-Host 'ALL TESTS PASSED.'

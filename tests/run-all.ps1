$ErrorActionPreference = 'Stop'

function Invoke-Checked {
    param([string]$Command, [string[]]$Arguments)
    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Command $($Arguments -join ' ') failed ($LASTEXITCODE)" }
}

Write-Host '== Docker Compose configuration =='
Invoke-Checked 'docker' @('compose', 'config', '--quiet')
Write-Host 'PASS: docker compose config'

Write-Host '== Build and start services =='
$previousOtpFlag = $env:OTP_RETURN_TO_CLIENT
$env:OTP_RETURN_TO_CLIENT = 'true'
Invoke-Checked 'docker' @('compose', 'up', '--build', '-d')
$env:OTP_RETURN_TO_CLIENT = $previousOtpFlag

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

Write-Host 'ALL TESTS PASSED.'

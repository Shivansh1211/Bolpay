$ErrorActionPreference = 'Stop'

function Invoke-CheckedDocker {
    param([string[]]$DockerArgs)
    & docker @DockerArgs
    if ($LASTEXITCODE -ne 0) {
        throw "docker $($DockerArgs -join ' ') failed with exit code $LASTEXITCODE"
    }
}

function Wait-ForBackend {
    param([string]$ContainerName)
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        $state = docker inspect --format '{{.State.Status}}' $ContainerName 2>$null
        if ($LASTEXITCODE -ne 0 -or $state -eq 'exited' -or $state -eq 'dead') {
            docker logs $ContainerName 2>&1 | Write-Host
            throw 'Smoke backend stopped during startup.'
        }
        $logs = docker logs $ContainerName 2>&1 | Out-String
        if ($logs -match 'Started BolPayApplication') {
            return
        }
        if ($logs -match 'Flyway.*(checksum|migration).*(mismatch|failed)|Migration .* failed') {
            $logs | Write-Host
            throw 'Flyway reported a failed migration; no database volume was changed.'
        }
        Start-Sleep -Seconds 2
    }
    docker logs $ContainerName 2>&1 | Write-Host
    throw 'Timed out waiting for the smoke backend to start.'
}

function New-SmokeVendorAndOrder {
    param([string]$BaseUrl)
    $suffix = [Guid]::NewGuid().ToString('N').Substring(0, 8)
    $phonePrefix = (Get-Random -Minimum 6 -Maximum 10).ToString()
    $phone = $phonePrefix + (Get-Random -Minimum 100000000 -Maximum 999999999).ToString()
    $vendorBody = @{
        phoneNumber = $phone
        upiId = "smoke-$suffix@upi"
        businessName = "Smoke Vendor $suffix"
        slug = "smoke-$suffix"
        description = 'Step 1 API smoke test'
    } | ConvertTo-Json

    $vendor = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/vendor/register" -ContentType 'application/json' -Body $vendorBody
    if ($vendor.slug -ne "smoke-$suffix") { throw 'Vendor registration did not return the requested slug.' }

    $orderBody = @{ vendorSlug = $vendor.slug; amount = 60.00 } | ConvertTo-Json
    $order = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/orders/create" -ContentType 'application/json' -Body $orderBody
    if (-not $order.token -or $order.token -notmatch '^[A-Z][0-9]{2,3}$') {
        throw "Order returned an invalid token: '$($order.token)'"
    }
    if (-not $order.paymentSessionId) { throw 'Order returned no payment_session_id.' }

    [pscustomobject]@{ Vendor = $vendor; Order = $order }
}

$network = docker inspect --format '{{range $name,$cfg := .NetworkSettings.Networks}}{{$name}}{{end}}' bolpay-backend
if ($LASTEXITCODE -ne 0 -or -not $network) { throw 'Could not find the running Compose internal network.' }
$dbName = 'bolpay-step1-smoke-db'
$backendName = 'bolpay-step1-smoke-backend'

try {
    Invoke-CheckedDocker @(
        'run', '-d', '--rm', '--name', $dbName, '--network', $network, '--network-alias', 'bolpay-step1-smoke-db',
        '--health-cmd', 'pg_isready -U bolpay_smoke -d bolpay_smoke', '--health-interval', '2s', '--health-timeout', '2s', '--health-retries', '30',
        '-e', 'POSTGRES_DB=bolpay_smoke', '-e', 'POSTGRES_USER=bolpay_smoke', '-e', 'POSTGRES_PASSWORD=bolpay_smoke_only', 'postgres:15-alpine'
    ) | Out-Null
    $dbReady = $false
    for ($attempt = 0; $attempt -lt 40; $attempt++) {
        $health = docker inspect --format '{{.State.Health.Status}}' $dbName 2>$null
        if ($health -eq 'healthy') { $dbReady = $true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $dbReady) { throw 'Temporary empty PostgreSQL database did not become healthy.' }
    Write-Host 'PASS: temporary PostgreSQL started with no persisted volume.'

    Invoke-CheckedDocker @(
        'run', '-d', '--rm', '--name', $backendName, '--network', $network, '-p', '18080:8080',
        '-e', 'SPRING_DATASOURCE_URL=jdbc:postgresql://bolpay-step1-smoke-db:5432/bolpay_smoke',
        '-e', 'POSTGRES_USER=bolpay_smoke', '-e', 'SPRING_DATASOURCE_PASSWORD=bolpay_smoke_only',
        '-e', 'JWT_SECRET=local-step1-smoke-jwt-secret-only-not-for-deployment-12345678901234567890',
        '-e', 'PAYMENT_GATEWAY=mock', '-e', 'CASHFREE_BASE_URL=http://mock-cashfree:9000',
        '-e', 'CASHFREE_CLIENT_ID=bolpay-mock-client', '-e', 'CASHFREE_CLIENT_SECRET=bolpay-mock-client-secret',
        '-e', 'CASHFREE_WEBHOOK_SECRET=bolpay-mock-webhook-secret',
        'bolpay-backend'
    ) | Out-Null
    Write-Host 'Starting backend against the empty smoke database...'
    Wait-ForBackend $backendName
    Write-Host 'PASS: backend started with PAYMENT_GATEWAY=mock.'

    $migrationState = docker exec $dbName psql -U bolpay_smoke -d bolpay_smoke -Atc "select count(*) || ':' || bool_and(success) || ':' || max(version) from flyway_schema_history where success"
    if ($LASTEXITCODE -ne 0 -or $migrationState.Trim() -ne '5:true:5') {
        throw "Expected five successful migrations on the empty database; got '$($migrationState.Trim())'."
    }
    Write-Host 'PASS: Flyway applied V1-V5 successfully to an empty database.'

    $flow = New-SmokeVendorAndOrder 'http://localhost:18080'
    Write-Host "PASS: API created vendor '$($flow.Vendor.slug)' and order token '$($flow.Order.token)' with payment_session_id."
    $checkout = Invoke-WebRequest -UseBasicParsing -Uri "http://localhost:5173/checkout/$($flow.Order.paymentSessionId)"
    if ($checkout.StatusCode -ne 200 -or $checkout.Content -notmatch 'Simulated money') {
        throw 'Nginx did not forward the payment session to the simulated checkout.'
    }
    Write-Host 'PASS: mock service accepted payment_session_id through nginx /checkout/.'

    $publicFlow = New-SmokeVendorAndOrder 'http://localhost:8080'
    Write-Host "PASS: running Compose API created vendor '$($publicFlow.Vendor.slug)' and order token '$($publicFlow.Order.token)' with payment_session_id."
    $checkout2 = Invoke-WebRequest -UseBasicParsing -Uri "http://localhost:5173/checkout/$($publicFlow.Order.paymentSessionId)"
    if ($checkout2.StatusCode -ne 200 -or $checkout2.Content -notmatch 'Simulated money') {
        throw 'The running Compose order payment_session_id was not accepted by mock checkout.'
    }
    Write-Host 'PASS: running Compose order is accepted by mock checkout.'

    foreach ($path in @('/mock-admin', '/admin/attack/anything')) {
        try {
            Invoke-WebRequest -UseBasicParsing -Method Get -Uri "http://localhost:5173$path" | Out-Null
            throw "Nginx unexpectedly exposed $path"
        } catch {
            if ($_.Exception.Response -and [int]$_.Exception.Response.StatusCode -eq 404) { continue }
            if ($_.Exception.Message -like "Nginx unexpectedly exposed*") { throw }
            throw "Unexpected response for protected mock path $path : $($_.Exception.Message)"
        }
    }
    Write-Host 'PASS: nginx returns 404 for /mock-admin and /admin/attack/... .'
    Write-Host 'STEP 1 SMOKE TESTS PASSED.'
}
finally {
    docker rm -f $backendName $dbName 2>$null | Out-Null
}

param(
    [Parameter(Mandatory)][string]$FrontendUrl,
    [Parameter(Mandatory)][string]$BackendUrl,
    [Parameter(Mandatory)][string]$MockUrl,
    [Parameter(Mandatory)][string]$MlUrl
)
$ErrorActionPreference = 'Stop'
$FrontendUrl = $FrontendUrl.TrimEnd('/')
$BackendUrl = $BackendUrl.TrimEnd('/')
$MockUrl = $MockUrl.TrimEnd('/')
$MlUrl = $MlUrl.TrimEnd('/')
$script:Failures = 0

function Check([string]$Name, [scriptblock]$Action) {
    try {
        if (& $Action) { Write-Host "PASS: $Name" }
        else { Write-Host "FAIL: $Name"; $script:Failures++ }
    } catch {
        # Never dump request/response objects: these can contain demo codes or JWTs.
        Write-Host "FAIL: $Name"
        $script:Failures++
    }
}

function Get-PrivateInviteCode {
    if (-not [string]::IsNullOrWhiteSpace($env:BOLPAY_INVITE_CODE)) { return $env:BOLPAY_INVITE_CODE }
    $secure = Read-Host 'Render INVITE_CODE (input hidden)' -AsSecureString
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
        $secure.Dispose()
    }
}

function Get-StompFrame([System.Net.WebSockets.ClientWebSocket]$Socket, [string]$Text) {
    $buffer = [byte[]]::new(8192)
    $all = [System.Text.StringBuilder]::new()
    $cts = [System.Threading.CancellationTokenSource]::new([TimeSpan]::FromSeconds(18))
    try {
        do {
            $result = $Socket.ReceiveAsync([ArraySegment[byte]]::new($buffer), $cts.Token).GetAwaiter().GetResult()
            if ($result.MessageType -eq [System.Net.WebSockets.WebSocketMessageType]::Close) { return 'SOCKET_CLOSED' }
            [void]$all.Append([System.Text.Encoding]::UTF8.GetString($buffer, 0, $result.Count))
        } while (-not $result.EndOfMessage)
        $raw = $all.ToString()
        if ($raw.StartsWith('a')) {
            try { return [string]((ConvertFrom-Json -InputObject $raw.Substring(1) -AsHashtable)[0]) }
            catch { return $raw }
        }
        return $raw
    } finally { $cts.Dispose() }
}

function Send-SockJsFrame([System.Net.WebSockets.ClientWebSocket]$Socket, [string]$Frame) {
    $json = ConvertTo-Json -InputObject @($Frame) -Compress
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
    $Socket.SendAsync([ArraySegment[byte]]::new($bytes), [System.Net.WebSockets.WebSocketMessageType]::Text,
        $true, [System.Threading.CancellationToken]::None).GetAwaiter().GetResult()
}

function Start-Stomp([string]$BaseUrl, [string]$Jwt) {
    $wsBase = $BaseUrl -replace '^https:', 'wss:' -replace '^http:', 'ws:'
    $session = [guid]::NewGuid().ToString('N')
    $socket = [System.Net.WebSockets.ClientWebSocket]::new()
    $socket.Options.KeepAliveInterval = [TimeSpan]::FromSeconds(20)
    $socket.ConnectAsync([Uri]::new("$wsBase/ws/123/$session/websocket"), [System.Threading.CancellationToken]::None).GetAwaiter().GetResult()
    $opened = Get-StompFrame $socket ''
    if ($opened -ne 'o') { $socket.Dispose(); throw 'SockJS did not open' }
    $headers = "CONNECT`naccept-version:1.1,1.0`nheart-beat:0,0`n"
    if ($Jwt) { $headers += "Authorization:Bearer $Jwt`n" }
    Send-SockJsFrame $socket ($headers + "`n`0")
    return @{ Socket = $socket; Frame = (Get-StompFrame $socket '') }
}

function ConvertTo-ErrorStatus($ErrorRecord) {
    try { [int]$ErrorRecord.Exception.Response.StatusCode }
    catch {
        try { [int]$ErrorRecord.Exception.Response.StatusCode.value__ }
        catch { 0 }
    }
}

Check 'Frontend root loads' {
    $r = Invoke-WebRequest -Uri "$FrontendUrl/" -TimeoutSec 45 -UseBasicParsing
    $r.StatusCode -eq 200 -and $r.Content.Contains('<div id="root">')
}

$inviteCode = Get-PrivateInviteCode
$phone = '900000' + (Get-Random -Minimum 0 -Maximum 10000).ToString('D4')
$slug = 'smoke-' + ([guid]::NewGuid().ToString('N').Substring(0, 10))
$amount = 9.17
$otpProof = $null
$jwt = $null
$vendor = $null
$order = $null
$demoReady = $false

try {
    $otpResponse = Invoke-RestMethod -Method Post -Uri "$BackendUrl/api/auth/send-otp" -ContentType 'application/json' `
        -Body (@{ phoneNumber = $phone } | ConvertTo-Json -Compress) -TimeoutSec 45
    if ($otpResponse.mode -eq 'demo login' -and $otpResponse.otp) {
        $otpProof = [string]$otpResponse.otp
        $demoReady = $true
    }
} catch { }
Check 'Demo login is enabled for this smoke run (no OTP output)' { $demoReady }

if ($demoReady) {
    try {
        $verified = Invoke-RestMethod -Method Post -Uri "$BackendUrl/api/auth/verify-otp" -ContentType 'application/json' `
            -Body (@{ phoneNumber = $phone; otpCode = $otpProof } | ConvertTo-Json -Compress) -TimeoutSec 45
        $proofToken = [string]$verified.token
        $profile = @{
            phoneNumber = $phone; upiId = "demo.$slug@examplebank"; businessName = "ZZ-TEST-$slug"; slug = $slug
            description = 'Synthetic deployment smoke-test vendor'; bannerUrl = ''; menuItems = ''
        } | ConvertTo-Json -Compress
        $vendor = Invoke-RestMethod -Method Post -Uri "$BackendUrl/api/vendor/register" -ContentType 'application/json' `
            -Headers @{ Authorization = "Bearer $proofToken"; 'X-Invite-Code' = $inviteCode } -Body $profile -TimeoutSec 45

        # Re-authenticate after registration to obtain a vendor-scoped JWT for WebSocket checks.
        $loginOtpResponse = Invoke-RestMethod -Method Post -Uri "$BackendUrl/api/auth/send-otp" -ContentType 'application/json' `
            -Body (@{ phoneNumber = $phone } | ConvertTo-Json -Compress) -TimeoutSec 45
        $loginOtpCode = [string]$loginOtpResponse.otp
        $login = Invoke-RestMethod -Method Post -Uri "$BackendUrl/api/auth/verify-otp" -ContentType 'application/json' `
            -Body (@{ phoneNumber = $phone; otpCode = $loginOtpCode } | ConvertTo-Json -Compress) -TimeoutSec 45
        $jwt = [string]$login.token
    } catch { $vendor = $null }
}
Check 'Vendor registration accepts invite code and demo OTP' { $null -ne $vendor -and $null -ne $jwt }

if ($vendor) {
    Check 'Vendor QR route loads after refresh' {
        $r = Invoke-WebRequest -Uri "$FrontendUrl/v/$slug" -TimeoutSec 45 -UseBasicParsing
        $r.StatusCode -eq 200 -and $r.Content.Contains('<div id="root">')
    }
    try {
        $order = Invoke-RestMethod -Method Post -Uri "$BackendUrl/api/orders/create" -ContentType 'application/json' `
            -Body (@{ vendorSlug = $slug; amount = $amount } | ConvertTo-Json -Compress) -TimeoutSec 45
    } catch { $order = $null }
    Check 'Order creation returns a payment session' { $null -ne $order -and $order.orderCode -and $order.paymentSessionId }
} else {
    Check 'Vendor QR route loads after refresh' { $false }
    Check 'Order creation returns a payment session' { $false }
}

if ($order) {
    Check 'Public mock checkout opens' {
        $r = Invoke-WebRequest -Uri "$MockUrl/checkout/$($order.paymentSessionId)" -TimeoutSec 45 -UseBasicParsing
        $r.StatusCode -eq 200 -and $r.Content.Contains('BolPay Checkout')
    }

    $rawBody = (@{ event_id = 'remote-smoke-unsigned'; order_code = $order.orderCode; amount = $amount; status = 'PAID' } | ConvertTo-Json -Compress)
    Check 'Unsigned webhook is rejected' {
        try {
            Invoke-RestMethod -Method Post -Uri "$BackendUrl/api/webhook/payment" -ContentType 'application/json' -Body $rawBody -TimeoutSec 30 | Out-Null
            $false
        } catch { (ConvertTo-ErrorStatus $_) -eq 401 }
    }
    Check 'Bad-signature webhook is rejected' {
        try {
            Invoke-RestMethod -Method Post -Uri "$BackendUrl/api/webhook/payment" -ContentType 'application/json' `
                -Headers @{ 'X-Webhook-Signature' = 'invalid'; 'X-Webhook-Timestamp' = [string][DateTimeOffset]::UtcNow.ToUnixTimeSeconds() } `
                -Body $rawBody -TimeoutSec 30 | Out-Null
            $false
        } catch { (ConvertTo-ErrorStatus $_) -eq 401 }
    }

    Check 'Correctly signed mock webhook marks order PAID' {
        $sent = Invoke-RestMethod -Method Post -Uri "$MockUrl/webhook/send" -ContentType 'application/json' `
            -Body (@{ order_id = $order.orderCode; order_status = 'PAID'; order_amount = $amount; scenario = 'success' } | ConvertTo-Json -Compress) -TimeoutSec 60
        if ($sent.response_code -ne 200) { return $false }
        for ($i = 0; $i -lt 12; $i++) {
            $status = Invoke-RestMethod -Uri "$BackendUrl/api/orders/$($order.orderCode)/status" -TimeoutSec 30
            if ($status.status -eq 'PAID') { return $true }
            Start-Sleep -Seconds 2
        }
        return $false
    }
} else {
    Check 'Public mock checkout opens' { $false }
    Check 'Unsigned webhook is rejected' { $false }
    Check 'Bad-signature webhook is rejected' { $false }
    Check 'Correctly signed mock webhook marks order PAID' { $false }
}

Check 'Mock admin panel rejects missing credentials' {
    try {
        $r = Invoke-WebRequest -Uri "$MockUrl/mock-admin" -TimeoutSec 30 -UseBasicParsing
        $r.StatusCode -eq 401
    } catch { (ConvertTo-ErrorStatus $_) -eq 401 }
}
Check 'ML endpoint rejects missing API key' {
    try { Invoke-RestMethod -Uri "$MlUrl/model-info" -TimeoutSec 30 | Out-Null; $false }
    catch { (ConvertTo-ErrorStatus $_) -eq 401 }
}

Check 'Foreign-origin CORS request is blocked' {
    try {
        $r = Invoke-WebRequest -Method Options -Uri "$BackendUrl/api/auth/send-otp" -TimeoutSec 30 -UseBasicParsing `
            -Headers @{ Origin = 'https://foreign-origin.invalid'; 'Access-Control-Request-Method' = 'POST'; 'Access-Control-Request-Headers' = 'content-type' }
        $r.StatusCode -eq 403 -or -not $r.Headers['Access-Control-Allow-Origin']
    } catch { (ConvertTo-ErrorStatus $_) -eq 403 }
}

if ($vendor -and $jwt) {
    Check 'WebSocket accepts a valid vendor JWT and own topic' {
        $connected = Start-Stomp $BackendUrl $jwt
        try {
            if (-not $connected.Frame.StartsWith('CONNECTED')) { return $false }
            Send-SockJsFrame $connected.Socket ("SUBSCRIBE`nid:own`ndestination:/topic/vendor/$($vendor.id)`nreceipt:own-ok`n`n`0")
            $receipt = Get-StompFrame $connected.Socket ''
            $receipt.StartsWith('RECEIPT')
        } finally { $connected.Socket.Dispose() }
    }

    Check 'WebSocket denies another vendor topic' {
        $socket = $null
        try {
            $connected = Start-Stomp $BackendUrl $jwt
            $socket = $connected.Socket
            if (-not $connected.Frame.StartsWith('CONNECTED')) { return $true }
            $otherId = [long]$vendor.id + 999999
            Send-SockJsFrame $socket ("SUBSCRIBE`nid:other`ndestination:/topic/vendor/$otherId`nreceipt:other-check`n`n`0")
            $frame = Get-StompFrame $socket ''
            $frame.StartsWith('ERROR') -or $frame -eq 'SOCKET_CLOSED'
        } finally { if ($socket) { $socket.Dispose() } }
    }
} else {
    Check 'WebSocket accepts a valid vendor JWT and own topic' { $false }
    Check 'WebSocket denies another vendor topic' { $false }
}

Check 'WebSocket denies CONNECT without a JWT' {
    $socket = $null
    try {
        $connected = Start-Stomp $BackendUrl ''
        $socket = $connected.Socket
        -not $connected.Frame.StartsWith('CONNECTED')
    } catch { $true }
    finally { if ($socket) { $socket.Dispose() } }
}

if ($inviteCode) { $inviteCode = $null }
if ($otpProof) { $otpProof = $null }
if ($jwt) { $jwt = $null }
if ($loginOtpCode) { $loginOtpCode = $null }
if ($proofToken) { $proofToken = $null }
$loginOtpResponse = $null
$verified = $null
$login = $null
if ($script:Failures -gt 0) {
    Write-Host "Remote smoke failed: $script:Failures check(s)."
    exit 1
}
Write-Host 'All remote smoke checks passed.'

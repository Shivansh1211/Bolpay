param(
    [ValidateSet('live', 'local')][string]$Target = 'live',
    [switch]$SkipWarmup
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$evidenceDir = Join-Path $repoRoot 'docs\live-test-evidence'
New-Item -ItemType Directory -Force -Path $evidenceDir | Out-Null

function Invoke-DockerChecked {
    param([string[]]$Arguments)
    & docker.exe --context default @Arguments
    if ($LASTEXITCODE -ne 0) { throw 'Docker command failed. See the command result above.' }
}

function Test-LocalHealth {
    param([string]$Url, [int]$TimeoutSeconds = 90)
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        try {
            $response = Invoke-WebRequest -Uri $Url -TimeoutSec 10 -UseBasicParsing
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 400) { return $true }
        } catch { }
        Start-Sleep -Seconds 2
    }
    return $false
}

function Build-PlaywrightImage {
    Write-Host '== Build local Playwright runner =='
    Invoke-DockerChecked @('build', '-f', 'tests/e2e/Dockerfile', '-t', 'bolpay-e2e:local', 'tests/e2e')
}

function Run-LiveOtpBrowserTest {
    param([string]$FrontendUrl, [string]$BackendUrl)
    $spec = (Resolve-Path (Join-Path $PSScriptRoot 'specs\otp-live.spec.js')).Path.Replace('\', '/')
    $evidence = (Resolve-Path $evidenceDir).Path.Replace('\', '/')
    $dockerArguments = @(
        'run', '--rm', '--network', 'bridge',
        '-e', "FRONTEND_URL=$FrontendUrl", '-e', "BACKEND_URL=$BackendUrl", '-e', 'EVIDENCE_DIR=/results',
        '-v', "${spec}:/tests/specs/otp-live.spec.js:ro", '-v', "${evidence}:/results",
        'bolpay-e2e:local', 'npx', 'playwright', 'test', 'specs/otp-live.spec.js', '--reporter=line'
    )
    Write-Host '== Live OTP browser reproduction =='
    Invoke-DockerChecked $dockerArguments
}

function Run-LocalOtpRestartBrowserTest {
    $signals = Join-Path ([System.IO.Path]::GetTempPath()) ('bolpay-live-signals-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Force -Path $signals | Out-Null
    $ready = Join-Path $signals 'otp-restart-ready.signal'
    $done = Join-Path $signals 'otp-restart-done.signal'
    $spec = (Resolve-Path (Join-Path $PSScriptRoot 'specs\otp-restart.spec.js')).Path.Replace('\', '/')
    $evidence = (Resolve-Path $evidenceDir).Path.Replace('\', '/')
    $signalsMount = $signals.Replace('\', '/')
    $dockerArguments = @(
        '--context', 'default', 'run', '--rm', '--network', 'bolpay_internal',
        '-e', 'FRONTEND_URL=http://frontend', '-e', 'EVIDENCE_DIR=/results', '-e', 'SIGNAL_DIR=/signals',
        '-v', "${spec}:/tests/specs/otp-restart.spec.js:ro", '-v', "${evidence}:/results", '-v', "${signalsMount}:/signals",
        'bolpay-e2e:local', 'npx', 'playwright', 'test', 'specs/otp-restart.spec.js', '--reporter=line'
    )

    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = (Get-Command docker.exe -ErrorAction Stop).Source
    $startInfo.Arguments = $dockerArguments -join ' '
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $startInfo
    $started = $process.Start()
    if (-not $started) { throw 'Could not start the local Playwright container.' }
    $stdoutTask = $process.StandardOutput.ReadToEndAsync()
    $stderrTask = $process.StandardError.ReadToEndAsync()

    try {
        $sendDeadline = [DateTime]::UtcNow.AddSeconds(150)
        while (-not (Test-Path -LiteralPath $ready) -and -not $process.HasExited -and [DateTime]::UtcNow -lt $sendDeadline) {
            Start-Sleep -Milliseconds 500
        }
        if (-not (Test-Path -LiteralPath $ready)) {
            if ($process.HasExited) { throw 'The browser test exited before requesting an OTP.' }
            throw 'The browser test did not reach the restart checkpoint.'
        }

        Write-Host 'PASS: local browser requested one OTP before backend restart'
        Invoke-DockerChecked @('compose', 'restart', 'backend') | Out-Null
        if (-not (Test-LocalHealth 'http://localhost:8080/health' 120)) {
            throw 'Local backend did not return healthy after restart.'
        }
        Write-Host 'PASS: backend container restarted; PostgreSQL volume stayed running'
        Set-Content -LiteralPath $done -Value 'restarted' -Encoding Ascii

        $exitDeadline = [DateTime]::UtcNow.AddSeconds(150)
        while (-not $process.HasExited -and [DateTime]::UtcNow -lt $exitDeadline) { Start-Sleep -Milliseconds 500 }
        if (-not $process.HasExited) {
            $process.Kill()
            throw 'The local browser restart test did not finish in time.'
        }
        $process.WaitForExit()
        $stdoutTask.Wait()
        $stderrTask.Wait()
        if ($process.ExitCode -ne 0) { throw 'The local browser OTP restart test failed.' }
        Write-Host 'PASS: OTP verified in the browser after backend restart'
    } finally {
        if (-not $process.HasExited) { $process.Kill() }
        $process.Dispose()
        Remove-Item -LiteralPath $signals -Recurse -Force -ErrorAction SilentlyContinue
    }
}

Push-Location $repoRoot
try {
    Build-PlaywrightImage
    if ($Target -eq 'live') {
        $frontendUrl = if ($env:FRONTEND_URL) { $env:FRONTEND_URL } else { 'https://bolpay-phi.vercel.app' }
        $backendUrl = if ($env:BACKEND_URL) { $env:BACKEND_URL } else { 'https://bolpay-backend.onrender.com' }
        $mockUrl = if ($env:MOCK_URL) { $env:MOCK_URL } else { 'https://bolpay-mock-cashfree.onrender.com' }
        $mlUrl = if ($env:ML_URL) { $env:ML_URL } else { 'https://bolpay-ml-service.onrender.com' }
        if (-not $SkipWarmup) {
            Write-Host '== Warm live services =='
            & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot '..\warmup.ps1') `
                -FrontendUrl $frontendUrl -BackendUrl $backendUrl -MockUrl $mockUrl -MlUrl $mlUrl -TimeoutSeconds 240
            if ($LASTEXITCODE -ne 0) { throw 'Live warm-up failed. No OTP test was started.' }
        }
        Run-LiveOtpBrowserTest $frontendUrl $backendUrl
    } else {
        $oldDemoOtp = $env:DEMO_OTP
        $oldInvite = $env:INVITE_CODE
        $oldTestInvite = $env:TEST_INVITE_CODE
        $localInvite = 'local-' + [guid]::NewGuid().ToString('N')
        try {
            $env:DEMO_OTP = 'true'
            $env:INVITE_CODE = $localInvite
            $env:TEST_INVITE_CODE = $localInvite
            Write-Host '== Build and start the local Compose stack =='
            Invoke-DockerChecked @('compose', 'up', '--build', '-d', 'backend', 'frontend', 'mock-cashfree', 'ml-service', 'postgres')
        } finally {
            $env:DEMO_OTP = $oldDemoOtp
            $env:INVITE_CODE = $oldInvite
            $env:TEST_INVITE_CODE = $oldTestInvite
        }
        if (-not (Test-LocalHealth 'http://localhost:5173/' 120)) { throw 'Local frontend did not become healthy.' }
        if (-not (Test-LocalHealth 'http://localhost:8080/health' 120)) { throw 'Local backend did not become healthy.' }
        Write-Host 'PASS: local frontend and backend are healthy'
        Run-LocalOtpRestartBrowserTest
    }
    Write-Host 'Live test target completed.'
} catch {
    Write-Host ('FAIL: ' + $_.Exception.Message)
    exit 1
} finally {
    Pop-Location
}

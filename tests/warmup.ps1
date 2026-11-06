param(
    [Parameter(Mandatory)][string]$FrontendUrl,
    [Parameter(Mandatory)][string]$BackendUrl,
    [Parameter(Mandatory)][string]$MockUrl,
    [Parameter(Mandatory)][string]$MlUrl,
    [int]$TimeoutSeconds = 240
)
$ErrorActionPreference = 'Stop'

function Wait-Healthy([string]$Name, [string]$Url) {
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        try {
            $response = Invoke-WebRequest -Uri $Url -TimeoutSec 20 -UseBasicParsing
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 400) {
                Write-Host "PASS: $Name healthy"
                return $true
            }
        } catch { }
        Start-Sleep -Seconds 8
    }
    Write-Host "FAIL: $Name did not become healthy within $TimeoutSeconds seconds"
    return $false
}

$checks = @(
    @{ Name = 'Frontend'; Url = $FrontendUrl.TrimEnd('/') + '/' },
    @{ Name = 'Backend'; Url = $BackendUrl.TrimEnd('/') + '/health' },
    @{ Name = 'Mock Cashfree'; Url = $MockUrl.TrimEnd('/') + '/health' },
    @{ Name = 'ML service'; Url = $MlUrl.TrimEnd('/') + '/health' }
)
$failed = $false
foreach ($check in $checks) {
    if (-not (Wait-Healthy $check.Name $check.Url)) { $failed = $true }
}
if ($failed) { exit 1 }
Write-Host 'All services are warm and responding.'

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

foreach ($name in @('SPRING_DATASOURCE_URL', 'SPRING_DATASOURCE_USERNAME', 'SPRING_DATASOURCE_PASSWORD')) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) {
        throw "Missing required environment variable: $name"
    }
}

$jdbc = $env:SPRING_DATASOURCE_URL
if (-not $jdbc.StartsWith('jdbc:postgresql://', [StringComparison]::Ordinal)) {
    throw 'SPRING_DATASOURCE_URL must start with jdbc:postgresql://.'
}
$parsed = [Uri]($jdbc.Substring(5))
if ($parsed.Host -match '-pooler(?:\.|$)') {
    throw 'Use the Neon direct endpoint (hostname without -pooler) for seeding and migrations.'
}
$query = @{}
foreach ($pair in $parsed.Query.TrimStart('?').Split('&', [StringSplitOptions]::RemoveEmptyEntries)) {
    $parts = $pair.Split('=', 2)
    $query[[Uri]::UnescapeDataString($parts[0])] = if ($parts.Length -gt 1) { [Uri]::UnescapeDataString($parts[1]) } else { '' }
}
if ($query['sslmode'] -ne 'require' -or $query['channelBinding'] -ne 'require') {
    throw 'The JDBC URL must require sslmode=require and channelBinding=require.'
}
$database = [Uri]::UnescapeDataString($parsed.AbsolutePath.TrimStart('/'))
if ([string]::IsNullOrWhiteSpace($database)) { throw 'SPRING_DATASOURCE_URL must include a database name.' }

$pgNames = @('PGHOST', 'PGPORT', 'PGDATABASE', 'PGUSER', 'PGPASSWORD', 'PGSSLMODE', 'PGCHANNELBINDING', 'PGCONNECT_TIMEOUT')
$previousPg = @{}
foreach ($name in $pgNames) { $previousPg[$name] = [Environment]::GetEnvironmentVariable($name) }

$env:PGHOST = $parsed.Host
$env:PGPORT = if ($parsed.IsDefaultPort) { '5432' } else { [string]$parsed.Port }
$env:PGDATABASE = $database
$env:PGUSER = $env:SPRING_DATASOURCE_USERNAME
$env:PGPASSWORD = $env:SPRING_DATASOURCE_PASSWORD
$env:PGSSLMODE = 'require'
$env:PGCHANNELBINDING = 'require'
$env:PGCONNECT_TIMEOUT = '20'

try {
    & psql --no-password --host=$env:PGHOST --port=$env:PGPORT --username=$env:PGUSER --dbname=$env:PGDATABASE --set=ON_ERROR_STOP=1 --file=(Join-Path $PSScriptRoot 'seed-demo.sql')
    if ($LASTEXITCODE -ne 0) { throw "psql exited with code $LASTEXITCODE." }
    Write-Output 'PASS: fictional demo vendor and sample orders seeded.'
}
finally {
    foreach ($name in $pgNames) {
        if ($null -eq $previousPg[$name]) {
            Remove-Item "Env:$name" -ErrorAction SilentlyContinue
        } else {
            [Environment]::SetEnvironmentVariable($name, $previousPg[$name])
        }
    }
}

[CmdletBinding()]
param(
    [string]$LocalUrl = 'http://127.0.0.1:8080',
    [string]$PublicUrl = 'https://aita-api.bogdan-donduk.workers.dev',
    [ValidateRange(5, 3600)]
    [int]$IntervalSeconds = 15,
    [ValidateRange(2, 120)]
    [int]$TimeoutSeconds = 20,
    [switch]$Once
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Continue'

function Invoke-AitaHealthProbe {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$Uri,
        [Parameter(Mandatory = $true)][int]$TimeoutSeconds
    )

    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri $Uri -TimeoutSec $TimeoutSeconds -ErrorAction Stop
        $stopwatch.Stop()
        $healthy = $response.StatusCode -ge 200 -and $response.StatusCode -lt 300
        $statusText = if ($healthy) { 'OK' } else { 'FAIL' }
        Write-Host ("{0}  {1,-4}  HTTP {2}  {3,6} ms  {4}  {5}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $statusText, $response.StatusCode, $stopwatch.ElapsedMilliseconds, $Name, $Uri)
        return $healthy
    } catch {
        $stopwatch.Stop()
        Write-Warning ("{0}  FAIL  {1,6} ms  {2}  {3}  {4}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $stopwatch.ElapsedMilliseconds, $Name, $Uri, $_.Exception.Message)
        return $false
    }
}

$localBase = $LocalUrl.TrimEnd('/')
$publicBase = $PublicUrl.TrimEnd('/')
$targets = @(
    @{ Name = 'local health'; Uri = "$localBase/healthz" },
    @{ Name = 'local readiness'; Uri = "$localBase/readyz" },
    @{ Name = 'public health'; Uri = "$publicBase/healthz" },
    @{ Name = 'public readiness'; Uri = "$publicBase/readyz" },
    @{ Name = 'public bootstrap'; Uri = "$publicBase/.well-known/aita-server.json" }
)

while ($true) {
    $allHealthy = $true
    foreach ($target in $targets) {
        if (-not (Invoke-AitaHealthProbe -Name $target.Name -Uri $target.Uri -TimeoutSeconds $TimeoutSeconds)) {
            $allHealthy = $false
        }
    }

    if ($Once) {
        if ($allHealthy) { exit 0 } else { exit 1 }
    }

    Start-Sleep -Seconds $IntervalSeconds
}

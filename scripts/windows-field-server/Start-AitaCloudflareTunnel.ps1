[CmdletBinding()]
param(
    [string]$FieldRoot = 'C:\AITA_FIELD',
    [string]$TunnelToken = '',
    [string]$TokenFile = '',
    [string]$LocalServerUrl = 'http://127.0.0.1:8080',
    [ValidateRange(2, 120)]
    [int]$HealthTimeoutSeconds = 15,
    [ValidateRange(1048576, 1073741824)]
    [long]$MaxLogBytes = 52428800,
    [switch]$AllowAlongsideInstalledService
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Rotate-AitaTunnelLog {
    param([string]$Path, [long]$MaximumBytes)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return }
    if ((Get-Item -LiteralPath $Path).Length -lt $MaximumBytes) { return }

    $archive = "$Path.$(Get-Date -Format 'yyyyMMdd-HHmmss')"
    Move-Item -LiteralPath $Path -Destination $archive -Force
    $name = [System.IO.Path]::GetFileName($Path)
    $directory = Split-Path -Parent $Path
    Get-ChildItem -LiteralPath $directory -File -Filter "$name.*" |
        Sort-Object LastWriteTimeUtc -Descending |
        Select-Object -Skip 6 |
        Remove-Item -Force -ErrorAction SilentlyContinue
}

$FieldRoot = [System.IO.Path]::GetFullPath($FieldRoot)
if ([string]::IsNullOrWhiteSpace($TokenFile)) {
    $TokenFile = Join-Path $FieldRoot 'cloudflare-tunnel-token.txt'
}

$installedService = Get-Service -Name 'cloudflared' -ErrorAction SilentlyContinue
if (-not $AllowAlongsideInstalledService -and $null -ne $installedService -and $installedService.Status -eq 'Running') {
    throw 'The installed cloudflared Windows service is already running. Do not start a second tunnel process.'
}

$cloudflared = Get-Command cloudflared.exe -ErrorAction SilentlyContinue
if ($null -eq $cloudflared) {
    $cloudflared = Get-Command cloudflared -ErrorAction SilentlyContinue
}
if ($null -eq $cloudflared) {
    throw 'cloudflared was not found on PATH.'
}

$healthUrl = $LocalServerUrl.TrimEnd('/') + '/healthz'
try {
    $healthResponse = Invoke-WebRequest -UseBasicParsing -Uri $healthUrl -TimeoutSec $HealthTimeoutSeconds -ErrorAction Stop
    if ($healthResponse.StatusCode -lt 200 -or $healthResponse.StatusCode -ge 300) {
        throw "HTTP $($healthResponse.StatusCode)"
    }
} catch {
    throw "The local AITA origin is not healthy at $healthUrl. Start and verify the server before the tunnel. $($_.Exception.Message)"
}

if ([string]::IsNullOrWhiteSpace($TunnelToken)) {
    if (-not (Test-Path -LiteralPath $TokenFile -PathType Leaf)) {
        throw "Provide -TunnelToken or create $TokenFile containing only the managed tunnel token."
    }
    $TunnelToken = (Get-Content -LiteralPath $TokenFile -Raw).Trim()
}
if ([string]::IsNullOrWhiteSpace($TunnelToken) -or $TunnelToken.Length -lt 40) {
    throw 'The Cloudflare managed-tunnel token is empty or unexpectedly short.'
}
if ($TunnelToken -match '\s') {
    throw 'The Cloudflare tunnel token must not contain whitespace.'
}

$logDirectory = Join-Path $FieldRoot 'logs\cloudflare'
New-Item -ItemType Directory -Force -Path $logDirectory | Out-Null
$logFile = Join-Path $logDirectory 'cloudflared.log'
Rotate-AitaTunnelLog -Path $logFile -MaximumBytes $MaxLogBytes

& $cloudflared.Source 'tunnel' '--no-autoupdate' '--loglevel' 'info' '--logfile' $logFile 'run' '--token' $TunnelToken
$exitCode = $LASTEXITCODE
if ($exitCode -ne 0) {
    throw "cloudflared exited with code $exitCode. Review $logFile"
}

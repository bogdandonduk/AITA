[CmdletBinding()]
param(
    [string]$FieldRoot = 'C:\AITA_FIELD',
    [string]$TunnelToken,
    [string]$TokenFile = 'C:\AITA_FIELD\cloudflare-tunnel-token.txt'
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$cloudflared = (Get-Command cloudflared.exe -ErrorAction Stop).Source
if (-not $TunnelToken) {
    if (-not (Test-Path -LiteralPath $TokenFile -PathType Leaf)) { throw "Provide -TunnelToken or create $TokenFile containing only the managed tunnel token." }
    $TunnelToken = (Get-Content -LiteralPath $TokenFile -Raw).Trim()
}
if ([string]::IsNullOrWhiteSpace($TunnelToken)) { throw 'Cloudflare Tunnel token is empty.' }
$logDir = Join-Path $FieldRoot 'logs\cloudflare'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$logFile = Join-Path $logDir 'cloudflared.log'
& $cloudflared tunnel --no-autoupdate --loglevel info --logfile $logFile run --token $TunnelToken
exit $LASTEXITCODE

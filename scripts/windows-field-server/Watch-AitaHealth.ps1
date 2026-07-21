[CmdletBinding()]
param([string]$LocalUrl='http://127.0.0.1:8080',[string]$PublicUrl='https://api.aita.kz',[int]$IntervalSeconds=15)
Set-StrictMode -Version Latest
$ErrorActionPreference='Continue'
while ($true) {
    $stamp=Get-Date -Format 'yyyy-MM-dd HH:mm:ss'
    foreach ($target in @("$LocalUrl/healthz","$LocalUrl/readyz","$PublicUrl/healthz")) {
        try { $r=Invoke-WebRequest -UseBasicParsing -Uri $target -TimeoutSec 20; Write-Host "$stamp  $($r.StatusCode)  $target" }
        catch { Write-Warning "$stamp  FAIL  $target  $($_.Exception.Message)" }
    }
    Start-Sleep -Seconds $IntervalSeconds
}

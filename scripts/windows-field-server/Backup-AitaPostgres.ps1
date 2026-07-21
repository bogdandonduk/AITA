[CmdletBinding()]
param([string]$FieldRoot='C:\AITA_FIELD',[int]$IntervalSeconds=300,[string]$RcloneRemote='')
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Import-AitaEnv.ps1')
Import-AitaEnv -Path (Join-Path $FieldRoot 'aita-prod.env')
$pgDump=(Get-Command pg_dump.exe -ErrorAction Stop).Source
$backupDir=Join-Path $FieldRoot 'backups'
New-Item -ItemType Directory -Force -Path $backupDir | Out-Null
$uri=[Uri]$env:AITA_DB_URL.Replace('jdbc:','')
$dbName=$uri.AbsolutePath.Trim('/')
$env:PGPASSWORD=$env:DB_PASS
try {
    while ($true) {
        $temp=Join-Path $backupDir 'aita_latest.dump.tmp'
        $final=Join-Path $backupDir 'aita_latest.dump'
        & $pgDump --format=custom --no-owner --no-privileges --host $uri.Host --port $uri.Port --username $env:DB_USER --file $temp $dbName
        if ($LASTEXITCODE) { throw "pg_dump failed with code $LASTEXITCODE" }
        Move-Item -Force -LiteralPath $temp -Destination $final
        if ($RcloneRemote) {
            & rclone.exe copyto $final "$RcloneRemote/aita_latest.dump" --checksum
            if ($LASTEXITCODE) { Write-Warning "rclone failed with code $LASTEXITCODE" }
        }
        Write-Host "[$(Get-Date -Format o)] Backup completed: $final"
        Start-Sleep -Seconds $IntervalSeconds
    }
} finally { Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue }

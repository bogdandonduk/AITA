[CmdletBinding()]
param(
    [string]$FieldRoot = 'C:\AITA_FIELD',
    [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    [switch]$Build
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Import-AitaEnv.ps1')

$envFile = Join-Path $FieldRoot 'aita-prod.env'
Import-AitaEnv -Path $envFile
foreach ($required in 'AITA_DB_URL','DB_USER','DB_PASS','AITA_JWT_SECRET','AITA_REFRESH_PEPPER','AITA_PUBLIC_SERVER_URL') {
    $value = [Environment]::GetEnvironmentVariable($required, 'Process')
    if ([string]::IsNullOrWhiteSpace($value) -or $value -like 'CHANGE_ME*') { throw "$required is missing or still uses CHANGE_ME in $envFile" }
}
if ($env:AITA_JWT_SECRET.Length -lt 64) { throw 'AITA_JWT_SECRET must contain at least 64 characters.' }
if ($env:AITA_REFRESH_PEPPER.Length -lt 64) { throw 'AITA_REFRESH_PEPPER must contain at least 64 characters.' }

$java = Get-Command java.exe -ErrorAction Stop
$javaVersion = & $java.Source -version 2>&1 | Select-Object -First 1
if ($javaVersion -notmatch 'version "21(?:\.|\")') { throw "Java 21 is required. Current runtime: $javaVersion" }

$logDir = if ($env:AITA_LOG_DIR) { $env:AITA_LOG_DIR } else { Join-Path $FieldRoot 'logs\server' }
$filesRoot = if ($env:AITA_SERVER_FILES_ROOT) { $env:AITA_SERVER_FILES_ROOT } else { Join-Path $FieldRoot 'server-files' }
New-Item -ItemType Directory -Force -Path $logDir,$filesRoot,(Join-Path $FieldRoot 'backups') | Out-Null

Push-Location $ProjectRoot
try {
    if ($Build) { & .\gradlew.bat :server:buildFatJar --no-configuration-cache; if ($LASTEXITCODE) { throw "Gradle server build failed with code $LASTEXITCODE" } }
    $jar = Join-Path $ProjectRoot 'server\build\libs\aita-server-all.jar'
    if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) { throw "Server JAR not found: $jar. Run with -Build first." }
    $log = Join-Path $logDir 'aita-server-console.log'
    "[$(Get-Date -Format o)] Starting AITA production server from $jar" | Tee-Object -FilePath $log -Append
    & $java.Source '-Dfile.encoding=UTF-8' '-XX:+UseG1GC' '-XX:MaxRAMPercentage=70' '-jar' $jar 2>&1 | Tee-Object -FilePath $log -Append
    exit $LASTEXITCODE
} finally { Pop-Location }

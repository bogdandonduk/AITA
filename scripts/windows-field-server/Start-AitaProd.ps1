[CmdletBinding()]
param(
    [string]$FieldRoot = 'C:\AITA_FIELD',
    [string]$ProjectRoot = '',
    [switch]$Build,
    [ValidateRange(5, 600)]
    [int]$DatabaseWaitSeconds = 90,
    [ValidateRange(1048576, 1073741824)]
    [long]$MaxConsoleLogBytes = 52428800,
    [ValidateRange(1, 50)]
    [int]$ConsoleLogArchivesToKeep = 6
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'Import-AitaEnv.ps1')

function Get-AitaProcessEnvironmentValue {
    param([Parameter(Mandatory = $true)][string]$Name)
    return [Environment]::GetEnvironmentVariable($Name, 'Process')
}

function Get-AitaRequiredEnvironmentValue {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$EnvironmentFile
    )

    $value = Get-AitaProcessEnvironmentValue -Name $Name
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "$Name is missing in $EnvironmentFile"
    }
    if ($value -match '(?i)(CHANGE_ME|CHANGEME|REPLACE_WITH|PLACEHOLDER|YOUR_SECRET|SECRET_HERE)') {
        throw "$Name still contains a placeholder value in $EnvironmentFile"
    }
    return $value.Trim()
}

function Set-AitaProcessEnvironmentDefault {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$Value
    )

    if ([string]::IsNullOrWhiteSpace((Get-AitaProcessEnvironmentValue -Name $Name))) {
        [Environment]::SetEnvironmentVariable($Name, $Value, 'Process')
    }
}

function Rotate-AitaLog {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][long]$MaximumBytes,
        [Parameter(Mandatory = $true)][int]$ArchivesToKeep
    )

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        return
    }

    $item = Get-Item -LiteralPath $Path
    if ($item.Length -lt $MaximumBytes) {
        return
    }

    $timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $archivePath = "$Path.$timestamp"
    Move-Item -LiteralPath $Path -Destination $archivePath -Force

    $fileName = [System.IO.Path]::GetFileName($Path)
    $directory = Split-Path -Parent $Path
    Get-ChildItem -LiteralPath $directory -File -Filter "$fileName.*" |
        Sort-Object LastWriteTimeUtc -Descending |
        Select-Object -Skip $ArchivesToKeep |
        Remove-Item -Force -ErrorAction SilentlyContinue
}

function Test-AitaPortAvailable {
    param([Parameter(Mandatory = $true)][int]$Port)

    $listener = New-Object -TypeName System.Net.Sockets.TcpListener -ArgumentList @([System.Net.IPAddress]::Loopback, $Port)
    try {
        $listener.Start()
    } catch {
        throw "TCP port $Port is already in use or unavailable. Stop the old AITA process before starting another one. $($_.Exception.Message)"
    } finally {
        try { $listener.Stop() } catch { }
    }
}

function Wait-AitaPostgres {
    param(
        [Parameter(Mandatory = $true)][System.Uri]$DatabaseUri,
        [Parameter(Mandatory = $true)][string]$DatabaseUser,
        [Parameter(Mandatory = $true)][int]$TimeoutSeconds
    )

    $pgIsReady = Get-Command pg_isready.exe -ErrorAction SilentlyContinue
    if ($null -eq $pgIsReady) {
        $pgIsReady = Get-Command pg_isready -ErrorAction SilentlyContinue
    }
    if ($null -eq $pgIsReady) {
        throw 'pg_isready was not found. Add the PostgreSQL bin directory to PATH.'
    }

    $databaseName = $DatabaseUri.AbsolutePath.Trim('/')
    $databasePort = if ($DatabaseUri.Port -gt 0) { $DatabaseUri.Port } else { 5432 }
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        & $pgIsReady.Source '--host' $DatabaseUri.Host '--port' $databasePort '--username' $DatabaseUser '--dbname' $databaseName '--quiet'
        if ($LASTEXITCODE -eq 0) {
            return
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)

    throw "PostgreSQL did not become ready within $TimeoutSeconds seconds at $($DatabaseUri.Host):$($DatabaseUri.Port)/$databaseName."
}

function Install-AitaBuiltJar {
    param(
        [Parameter(Mandatory = $true)][string]$SourceJar,
        [Parameter(Mandatory = $true)][string]$DestinationJar,
        [Parameter(Mandatory = $true)][string]$PreviousJar
    )

    if (-not (Test-Path -LiteralPath $SourceJar -PathType Leaf)) {
        throw "Built server JAR was not found: $SourceJar"
    }
    if ((Get-Item -LiteralPath $SourceJar).Length -lt 1048576) {
        throw "Built server JAR is unexpectedly small: $SourceJar"
    }

    $destinationDirectory = Split-Path -Parent $DestinationJar
    New-Item -ItemType Directory -Force -Path $destinationDirectory | Out-Null
    $stagingJar = Join-Path $destinationDirectory ("aita-server-all.staging.{0}.jar" -f $PID)

    Remove-Item -LiteralPath $stagingJar -Force -ErrorAction SilentlyContinue
    Copy-Item -LiteralPath $SourceJar -Destination $stagingJar -Force

    try {
        if (Test-Path -LiteralPath $DestinationJar -PathType Leaf) {
            Remove-Item -LiteralPath $PreviousJar -Force -ErrorAction SilentlyContinue
            [System.IO.File]::Replace($stagingJar, $DestinationJar, $PreviousJar, $true)
        } else {
            [System.IO.File]::Move($stagingJar, $DestinationJar)
        }
    } finally {
        Remove-Item -LiteralPath $stagingJar -Force -ErrorAction SilentlyContinue
    }
}

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
    $ProjectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
} else {
    $ProjectRoot = [System.IO.Path]::GetFullPath($ProjectRoot)
}
$FieldRoot = [System.IO.Path]::GetFullPath($FieldRoot)

$environmentFile = Join-Path $FieldRoot 'aita-prod.env'
Import-AitaEnv -Path $environmentFile

$environmentName = Get-AitaRequiredEnvironmentValue -Name 'AITA_ENV' -EnvironmentFile $environmentFile
if ($environmentName.ToLowerInvariant() -notin @('prod', 'production', 'stage', 'staging', 'cloud')) {
    throw "AITA_ENV must identify a production-like environment. Current value: $environmentName"
}

$databaseUrl = Get-AitaRequiredEnvironmentValue -Name 'AITA_DB_URL' -EnvironmentFile $environmentFile
$databaseUser = Get-AitaRequiredEnvironmentValue -Name 'DB_USER' -EnvironmentFile $environmentFile
[void](Get-AitaRequiredEnvironmentValue -Name 'DB_PASS' -EnvironmentFile $environmentFile)
$jwtIssuer = Get-AitaRequiredEnvironmentValue -Name 'AITA_JWT_ISSUER' -EnvironmentFile $environmentFile
$jwtAudience = Get-AitaRequiredEnvironmentValue -Name 'AITA_JWT_AUDIENCE' -EnvironmentFile $environmentFile
$jwtSecret = Get-AitaRequiredEnvironmentValue -Name 'AITA_JWT_SECRET' -EnvironmentFile $environmentFile
$refreshPepper = Get-AitaRequiredEnvironmentValue -Name 'AITA_REFRESH_PEPPER' -EnvironmentFile $environmentFile
$publicServerUrl = Get-AitaRequiredEnvironmentValue -Name 'AITA_PUBLIC_SERVER_URL' -EnvironmentFile $environmentFile
$corsOrigins = Get-AitaRequiredEnvironmentValue -Name 'AITA_CORS_ALLOWED_ORIGINS' -EnvironmentFile $environmentFile

if ($jwtIssuer.Length -lt 3 -or $jwtAudience.Length -lt 3) {
    throw 'AITA_JWT_ISSUER and AITA_JWT_AUDIENCE must be meaningful non-placeholder values.'
}
if ($jwtSecret.Length -lt 64) {
    throw 'AITA_JWT_SECRET must contain at least 64 characters.'
}
if ($refreshPepper.Length -lt 64) {
    throw 'AITA_REFRESH_PEPPER must contain at least 64 characters.'
}
if ($jwtSecret -ceq $refreshPepper) {
    throw 'AITA_JWT_SECRET and AITA_REFRESH_PEPPER must be independently generated values.'
}
if ($corsOrigins.Split(',') | Where-Object { $_.Trim() -eq '*' }) {
    throw 'Wildcard CORS is not allowed for the production server.'
}

$publicUri = [System.Uri]$publicServerUrl
if ($publicUri.Scheme -ne 'https' -or [string]::IsNullOrWhiteSpace($publicUri.Host)) {
    throw 'AITA_PUBLIC_SERVER_URL must be a valid public HTTPS URL.'
}

if (-not $databaseUrl.StartsWith('jdbc:postgresql://', [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'AITA_DB_URL must be a jdbc:postgresql:// URL.'
}
$databaseUri = [System.Uri]($databaseUrl.Substring(5))
if ($databaseUri.Host -notin @('127.0.0.1', 'localhost', '::1')) {
    throw "The field-server database must stay on localhost. Current AITA_DB_URL host: $($databaseUri.Host)"
}
if ([string]::IsNullOrWhiteSpace($databaseUri.AbsolutePath.Trim('/'))) {
    throw 'AITA_DB_URL must include the database name.'
}

$portText = Get-AitaProcessEnvironmentValue -Name 'AITA_PORT'
if ([string]::IsNullOrWhiteSpace($portText)) {
    $portText = '8080'
    [Environment]::SetEnvironmentVariable('AITA_PORT', $portText, 'Process')
}
$port = 0
if (-not [int]::TryParse($portText, [ref]$port) -or $port -lt 1 -or $port -gt 65535) {
    throw "AITA_PORT must be an integer from 1 through 65535. Current value: $portText"
}

foreach ($safeBoolean in @(
    @{ Name = 'AITA_FLYWAY_CLEAN_DISABLED'; Unsafe = 'false' },
    @{ Name = 'AITA_SCHEMA_AUTO_REPAIR'; Unsafe = 'true' }
)) {
    $configuredValue = Get-AitaProcessEnvironmentValue -Name $safeBoolean.Name
    if (-not [string]::IsNullOrWhiteSpace($configuredValue) -and $configuredValue.Trim().ToLowerInvariant() -eq $safeBoolean.Unsafe) {
        throw "$($safeBoolean.Name) has an unsafe production value: $configuredValue"
    }
}

$java = Get-Command java.exe -ErrorAction SilentlyContinue
if ($null -eq $java) {
    $java = Get-Command java -ErrorAction SilentlyContinue
}
if ($null -eq $java) {
    throw 'Java was not found on PATH.'
}
$javaVersionText = (& $java.Source -version 2>&1 | Out-String).Trim()
if ($javaVersionText -notmatch '(?m)version\s+"(?<major>[0-9]+)') {
    throw "Could not determine the Java version from: $javaVersionText"
}
if ([int]$Matches.major -ne 21) {
    throw "Java 21 is required. Current runtime: $javaVersionText"
}

$appDirectory = Join-Path $FieldRoot 'app'
$backupDirectory = Join-Path $FieldRoot 'backups'
$crashDirectory = Join-Path $FieldRoot 'crash'
$defaultLogDirectory = Join-Path $FieldRoot 'logs\server'
$defaultFilesRoot = Join-Path $FieldRoot 'server-files'
$tempDirectory = Join-Path $FieldRoot 'temp'
New-Item -ItemType Directory -Force -Path @(
    $FieldRoot,
    $appDirectory,
    $backupDirectory,
    $crashDirectory,
    $defaultLogDirectory,
    $defaultFilesRoot,
    $tempDirectory
) | Out-Null

Set-AitaProcessEnvironmentDefault -Name 'AITA_LOG_DIR' -Value $defaultLogDirectory
Set-AitaProcessEnvironmentDefault -Name 'AITA_SERVER_FILES_ROOT' -Value $defaultFilesRoot

$logDirectory = Get-AitaProcessEnvironmentValue -Name 'AITA_LOG_DIR'
$filesRoot = Get-AitaProcessEnvironmentValue -Name 'AITA_SERVER_FILES_ROOT'
New-Item -ItemType Directory -Force -Path $logDirectory, $filesRoot | Out-Null

$builtJar = Join-Path $ProjectRoot 'server\build\libs\aita-server-all.jar'
$deployedJar = Join-Path $appDirectory 'aita-server-all.jar'
$previousJar = Join-Path $appDirectory 'aita-server-all.previous.jar'

if ($Build) {
    $gradleWrapper = Join-Path $ProjectRoot 'gradlew.bat'
    if (-not (Test-Path -LiteralPath $gradleWrapper -PathType Leaf)) {
        throw "Gradle wrapper was not found: $gradleWrapper"
    }

    Push-Location $ProjectRoot
    try {
        & $gradleWrapper '--stop'
        & $gradleWrapper ':server:buildFatJar' '--no-configuration-cache'
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle server build failed with exit code $LASTEXITCODE"
        }
    } finally {
        Pop-Location
    }

    Install-AitaBuiltJar -SourceJar $builtJar -DestinationJar $deployedJar -PreviousJar $previousJar
} elseif (-not (Test-Path -LiteralPath $deployedJar -PathType Leaf)) {
    if (Test-Path -LiteralPath $builtJar -PathType Leaf) {
        Install-AitaBuiltJar -SourceJar $builtJar -DestinationJar $deployedJar -PreviousJar $previousJar
    } else {
        throw "Deployed server JAR was not found at $deployedJar. Run this script once with -Build."
    }
}

Test-AitaPortAvailable -Port $port
Wait-AitaPostgres -DatabaseUri $databaseUri -DatabaseUser $databaseUser -TimeoutSeconds $DatabaseWaitSeconds

$consoleLog = Join-Path $logDirectory 'aita-server-console.log'
Rotate-AitaLog -Path $consoleLog -MaximumBytes $MaxConsoleLogBytes -ArchivesToKeep $ConsoleLogArchivesToKeep

$startupLine = "[{0}] Starting AITA production server from {1} on port {2}" -f (Get-Date -Format 'o'), $deployedJar, $port
$startupLine | Tee-Object -FilePath $consoleLog -Append

$jvmArguments = @(
    '-Dfile.encoding=UTF-8',
    "-Djava.io.tmpdir=$tempDirectory",
    '-XX:+UseG1GC',
    '-XX:MaxRAMPercentage=70',
    '-XX:+ExitOnOutOfMemoryError',
    '-XX:+HeapDumpOnOutOfMemoryError',
    "-XX:HeapDumpPath=$crashDirectory",
    "-XX:ErrorFile=$crashDirectory\hs_err_pid%p.log",
    '-jar',
    $deployedJar
)

Push-Location $FieldRoot
try {
    & $java.Source @jvmArguments 2>&1 | Tee-Object -FilePath $consoleLog -Append
    $exitCode = $LASTEXITCODE
} finally {
    Pop-Location
}

if ($exitCode -ne 0) {
    throw "AITA server exited with code $exitCode. Review $consoleLog"
}

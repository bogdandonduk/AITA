[CmdletBinding()]
param(
    [string]$FieldRoot = 'C:\AITA_FIELD',
    [string]$ProjectRoot = '',
    [string]$LocalUrl = 'http://127.0.0.1:8080',
    [string]$PublicUrl = 'https://aita-api.bogdan-dond.uk.workers.dev',
    [ValidateRange(2, 120)]
    [int]$TimeoutSeconds = 15,
    [switch]$CheckEndpoints
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Continue'

. (Join-Path $PSScriptRoot 'Import-AitaEnv.ps1')

$script:PassCount = 0
$script:WarningCount = 0
$script:FailureCount = 0

function Write-AitaReadinessResult {
    param(
        [Parameter(Mandatory = $true)][ValidateSet('PASS', 'WARN', 'FAIL')][string]$Status,
        [Parameter(Mandatory = $true)][string]$Message
    )

    switch ($Status) {
        'PASS' { $script:PassCount += 1; Write-Host "PASS  $Message" }
        'WARN' { $script:WarningCount += 1; Write-Warning $Message }
        'FAIL' { $script:FailureCount += 1; Write-Host "FAIL  $Message" -ForegroundColor Red }
    }
}

function Get-AitaReadinessEnvironmentValue {
    param([string]$Name)
    return [Environment]::GetEnvironmentVariable($Name, 'Process')
}

function Find-AitaReadinessCommand {
    param([string[]]$Names)
    foreach ($name in $Names) {
        $command = Get-Command $name -ErrorAction SilentlyContinue
        if ($null -ne $command) { return $command }
    }
    return $null
}

function Test-AitaReadinessEndpoint {
    param([string]$Name, [string]$Uri)
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri $Uri -TimeoutSec $TimeoutSeconds -ErrorAction Stop
        if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 300) {
            Write-AitaReadinessResult -Status PASS -Message "$Name is reachable: $Uri"
        } else {
            Write-AitaReadinessResult -Status FAIL -Message "$Name returned HTTP $($response.StatusCode): $Uri"
        }
    } catch {
        Write-AitaReadinessResult -Status FAIL -Message "$Name is unreachable at $Uri. $($_.Exception.Message)"
    }
}

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
    $ProjectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
} else {
    $ProjectRoot = [System.IO.Path]::GetFullPath($ProjectRoot)
}
$FieldRoot = [System.IO.Path]::GetFullPath($FieldRoot)

$environmentFile = Join-Path $FieldRoot 'aita-prod.env'
if (-not (Test-Path -LiteralPath $environmentFile -PathType Leaf)) {
    Write-AitaReadinessResult -Status FAIL -Message "Production environment file is missing: $environmentFile"
} else {
    try {
        Import-AitaEnv -Path $environmentFile
        Write-AitaReadinessResult -Status PASS -Message "Production environment parsed without duplicate or malformed entries."
    } catch {
        Write-AitaReadinessResult -Status FAIL -Message "Production environment is invalid. $($_.Exception.Message)"
    }
}

$requiredNames = @(
    'AITA_ENV',
    'AITA_PORT',
    'AITA_DB_URL',
    'DB_USER',
    'DB_PASS',
    'AITA_JWT_ISSUER',
    'AITA_JWT_AUDIENCE',
    'AITA_JWT_SECRET',
    'AITA_REFRESH_PEPPER',
    'AITA_PUBLIC_SERVER_URL',
    'AITA_CORS_ALLOWED_ORIGINS',
    'AITA_SERVER_FILES_ROOT'
)
foreach ($name in $requiredNames) {
    $value = Get-AitaReadinessEnvironmentValue -Name $name
    if ([string]::IsNullOrWhiteSpace($value)) {
        Write-AitaReadinessResult -Status FAIL -Message "$name is missing."
    } elseif ($value -match '(?i)(CHANGE_ME|CHANGEME|REPLACE_WITH|PLACEHOLDER|YOUR_SECRET|SECRET_HERE)') {
        Write-AitaReadinessResult -Status FAIL -Message "$name still contains a placeholder value."
    } else {
        Write-AitaReadinessResult -Status PASS -Message "$name is configured."
    }
}

$environmentName = Get-AitaReadinessEnvironmentValue -Name 'AITA_ENV'
if (-not [string]::IsNullOrWhiteSpace($environmentName) -and $environmentName.ToLowerInvariant() -notin @('prod', 'production', 'stage', 'staging', 'cloud')) {
    Write-AitaReadinessResult -Status FAIL -Message "AITA_ENV is not production-like: $environmentName"
}

$jwtSecret = Get-AitaReadinessEnvironmentValue -Name 'AITA_JWT_SECRET'
$refreshPepper = Get-AitaReadinessEnvironmentValue -Name 'AITA_REFRESH_PEPPER'
if (-not [string]::IsNullOrWhiteSpace($jwtSecret) -and $jwtSecret.Length -lt 64) {
    Write-AitaReadinessResult -Status FAIL -Message 'AITA_JWT_SECRET is shorter than 64 characters.'
}
if (-not [string]::IsNullOrWhiteSpace($refreshPepper) -and $refreshPepper.Length -lt 64) {
    Write-AitaReadinessResult -Status FAIL -Message 'AITA_REFRESH_PEPPER is shorter than 64 characters.'
}
if (-not [string]::IsNullOrWhiteSpace($jwtSecret) -and $jwtSecret -ceq $refreshPepper) {
    Write-AitaReadinessResult -Status FAIL -Message 'AITA_JWT_SECRET and AITA_REFRESH_PEPPER are identical; generate them independently.'
}

$databaseUri = $null
$databaseUrl = Get-AitaReadinessEnvironmentValue -Name 'AITA_DB_URL'
if (-not [string]::IsNullOrWhiteSpace($databaseUrl)) {
    try {
        if (-not $databaseUrl.StartsWith('jdbc:postgresql://', [System.StringComparison]::OrdinalIgnoreCase)) {
            throw 'Expected jdbc:postgresql:// URL.'
        }
        $databaseUri = [System.Uri]($databaseUrl.Substring(5))
        if ($databaseUri.Host -notin @('127.0.0.1', 'localhost', '::1')) {
            Write-AitaReadinessResult -Status FAIL -Message "PostgreSQL is not configured on localhost: $($databaseUri.Host)"
        } else {
            Write-AitaReadinessResult -Status PASS -Message 'PostgreSQL URL is restricted to localhost.'
        }
    } catch {
        Write-AitaReadinessResult -Status FAIL -Message "AITA_DB_URL is invalid. $($_.Exception.Message)"
        $databaseUri = $null
    }
}

$publicServerUrl = Get-AitaReadinessEnvironmentValue -Name 'AITA_PUBLIC_SERVER_URL'
if (-not [string]::IsNullOrWhiteSpace($publicServerUrl)) {
    try {
        $publicUri = [System.Uri]$publicServerUrl
        if ($publicUri.Scheme -ne 'https') {
            Write-AitaReadinessResult -Status FAIL -Message 'AITA_PUBLIC_SERVER_URL must use HTTPS.'
        } else {
            Write-AitaReadinessResult -Status PASS -Message 'Public server URL uses HTTPS.'
        }
    } catch {
        Write-AitaReadinessResult -Status FAIL -Message "AITA_PUBLIC_SERVER_URL is invalid. $($_.Exception.Message)"
    }
}

$corsOrigins = Get-AitaReadinessEnvironmentValue -Name 'AITA_CORS_ALLOWED_ORIGINS'
if (-not [string]::IsNullOrWhiteSpace($corsOrigins) -and ($corsOrigins.Split(',') | Where-Object { $_.Trim() -eq '*' })) {
    Write-AitaReadinessResult -Status FAIL -Message 'Production CORS contains a wildcard origin.'
}

foreach ($safeSetting in @(
    @{ Name = 'AITA_FLYWAY_CLEAN_DISABLED'; Required = 'true' },
    @{ Name = 'AITA_SCHEMA_AUTO_REPAIR'; Required = 'false' },
    @{ Name = 'AITA_FLYWAY_REPAIR_ON_VALIDATE_FAILURE'; Required = 'false' }
)) {
    $value = Get-AitaReadinessEnvironmentValue -Name $safeSetting.Name
    if ([string]::IsNullOrWhiteSpace($value)) {
        Write-AitaReadinessResult -Status WARN -Message "$($safeSetting.Name) is not explicit; set it to $($safeSetting.Required) for production clarity."
    } elseif ($value.Trim().ToLowerInvariant() -ne $safeSetting.Required) {
        Write-AitaReadinessResult -Status FAIL -Message "$($safeSetting.Name) should be $($safeSetting.Required), not $value."
    } else {
        Write-AitaReadinessResult -Status PASS -Message "$($safeSetting.Name) has the safe production value."
    }
}

$java = Find-AitaReadinessCommand -Names @('java.exe', 'java')
if ($null -eq $java) {
    Write-AitaReadinessResult -Status FAIL -Message 'Java was not found on PATH.'
} else {
    $javaText = (& $java.Source -version 2>&1 | Out-String).Trim()
    if ($javaText -match '(?m)version\s+"(?<major>[0-9]+)' -and [int]$Matches.major -eq 21) {
        Write-AitaReadinessResult -Status PASS -Message 'Java 21 is active.'
    } else {
        Write-AitaReadinessResult -Status FAIL -Message "Java 21 is required. Reported runtime: $javaText"
    }
}

$pgDump = Find-AitaReadinessCommand -Names @('pg_dump.exe', 'pg_dump')
$pgRestore = Find-AitaReadinessCommand -Names @('pg_restore.exe', 'pg_restore')
$pgIsReady = Find-AitaReadinessCommand -Names @('pg_isready.exe', 'pg_isready')
$psql = Find-AitaReadinessCommand -Names @('psql.exe', 'psql')
foreach ($tool in @(
    @{ Name = 'pg_dump'; Command = $pgDump },
    @{ Name = 'pg_restore'; Command = $pgRestore },
    @{ Name = 'pg_isready'; Command = $pgIsReady },
    @{ Name = 'psql'; Command = $psql }
)) {
    if ($null -eq $tool.Command) {
        Write-AitaReadinessResult -Status FAIL -Message "$($tool.Name) was not found on PATH."
    } else {
        Write-AitaReadinessResult -Status PASS -Message "$($tool.Name) is available."
    }
}

if ($null -ne $databaseUri -and $null -ne $psql) {
    $databasePort = if ($databaseUri.Port -gt 0) { $databaseUri.Port } else { 5432 }
    $databaseName = $databaseUri.AbsolutePath.Trim('/')
    $oldPgPassword = [Environment]::GetEnvironmentVariable('PGPASSWORD', 'Process')
    [Environment]::SetEnvironmentVariable('PGPASSWORD', (Get-AitaReadinessEnvironmentValue -Name 'DB_PASS'), 'Process')
    try {
        & $psql.Source '--host' $databaseUri.Host '--port' $databasePort '--username' (Get-AitaReadinessEnvironmentValue -Name 'DB_USER') '--dbname' $databaseName '--no-password' '--tuples-only' '--command' 'SELECT 1;' | Out-Null
        if ($LASTEXITCODE -eq 0) {
            Write-AitaReadinessResult -Status PASS -Message 'PostgreSQL accepted the configured AITA credentials.'
        } else {
            Write-AitaReadinessResult -Status FAIL -Message "PostgreSQL credential check failed with exit code $LASTEXITCODE."
        }
    } finally {
        if ($null -eq $oldPgPassword) {
            Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
        } else {
            [Environment]::SetEnvironmentVariable('PGPASSWORD', $oldPgPassword, 'Process')
        }
    }
}

$deployedJar = Join-Path $FieldRoot 'app\aita-server-all.jar'
$builtJar = Join-Path $ProjectRoot 'server\build\libs\aita-server-all.jar'
if (Test-Path -LiteralPath $deployedJar -PathType Leaf) {
    Write-AitaReadinessResult -Status PASS -Message "Deployed server JAR exists: $deployedJar"
} elseif (Test-Path -LiteralPath $builtJar -PathType Leaf) {
    Write-AitaReadinessResult -Status WARN -Message "Only the Gradle-built JAR exists; run Start-AitaProd.ps1 -Build to stage it under $FieldRoot\app."
} else {
    Write-AitaReadinessResult -Status FAIL -Message 'No deployed or built server JAR was found.'
}

$cloudflared = Find-AitaReadinessCommand -Names @('cloudflared.exe', 'cloudflared')
if ($null -eq $cloudflared) {
    Write-AitaReadinessResult -Status FAIL -Message 'cloudflared was not found on PATH.'
} else {
    Write-AitaReadinessResult -Status PASS -Message 'cloudflared is available.'
}
$cloudflaredService = Get-Service -Name 'cloudflared' -ErrorAction SilentlyContinue
$tunnelTokenFile = Join-Path $FieldRoot 'cloudflare-tunnel-token.txt'
if ($null -ne $cloudflaredService) {
    if ($cloudflaredService.Status -eq 'Running') {
        Write-AitaReadinessResult -Status PASS -Message 'The cloudflared Windows service is running.'
    } else {
        Write-AitaReadinessResult -Status WARN -Message "The cloudflared service is installed but not running: $($cloudflaredService.Status)"
    }
} elseif (Test-Path -LiteralPath $tunnelTokenFile -PathType Leaf) {
    Write-AitaReadinessResult -Status WARN -Message 'A manual tunnel token exists, but cloudflared is not installed as a service yet.'
} else {
    Write-AitaReadinessResult -Status FAIL -Message 'No cloudflared service or managed-tunnel token file was found.'
}

$age = Find-AitaReadinessCommand -Names @('age.exe', 'age')
$ageRecipient = Get-AitaReadinessEnvironmentValue -Name 'AITA_BACKUP_AGE_RECIPIENT'
if ($null -eq $age) {
    Write-AitaReadinessResult -Status FAIL -Message 'age was not found on PATH; production backups cannot be encrypted.'
} else {
    Write-AitaReadinessResult -Status PASS -Message 'age is available.'
}
if ([string]::IsNullOrWhiteSpace($ageRecipient) -or $ageRecipient -notmatch '^age1[0-9a-z]+$') {
    Write-AitaReadinessResult -Status FAIL -Message 'AITA_BACKUP_AGE_RECIPIENT is missing or invalid.'
} else {
    Write-AitaReadinessResult -Status PASS -Message 'Backup age recipient is configured.'
}

$rcloneRemote = Get-AitaReadinessEnvironmentValue -Name 'AITA_BACKUP_RCLONE_REMOTE'
if (-not [string]::IsNullOrWhiteSpace($rcloneRemote)) {
    $rclone = Find-AitaReadinessCommand -Names @('rclone.exe', 'rclone')
    if ($null -eq $rclone) {
        Write-AitaReadinessResult -Status FAIL -Message 'AITA_BACKUP_RCLONE_REMOTE is configured, but rclone is unavailable.'
    } else {
        Write-AitaReadinessResult -Status PASS -Message 'rclone is available for encrypted off-site backups.'
    }
} else {
    Write-AitaReadinessResult -Status WARN -Message 'AITA_BACKUP_RCLONE_REMOTE is empty; only local encrypted backups will be created.'
}

if ($CheckEndpoints) {
    $localBase = $LocalUrl.TrimEnd('/')
    $publicBase = $PublicUrl.TrimEnd('/')
    Test-AitaReadinessEndpoint -Name 'Local health' -Uri "$localBase/healthz"
    Test-AitaReadinessEndpoint -Name 'Local readiness' -Uri "$localBase/readyz"
    Test-AitaReadinessEndpoint -Name 'Public health' -Uri "$publicBase/healthz"
    Test-AitaReadinessEndpoint -Name 'Public readiness' -Uri "$publicBase/readyz"
    Test-AitaReadinessEndpoint -Name 'Public bootstrap descriptor' -Uri "$publicBase/.well-known/aita-server.json"
}

Write-Host ''
Write-Host ("Readiness summary: {0} passed, {1} warnings, {2} failures." -f $script:PassCount, $script:WarningCount, $script:FailureCount)
if ($script:FailureCount -gt 0) {
    exit 1
}
exit 0

[CmdletBinding()]
param(
    [string]$FieldRoot = 'C:\AITA_FIELD',
    [ValidateRange(30, 86400)]
    [int]$IntervalSeconds = 300,
    [string]$RcloneRemote = '',
    [switch]$Once,
    [switch]$EndOfDay,
    [switch]$AllowPlaintext
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'Import-AitaEnv.ps1')

function Get-AitaBackupEnvironmentValue {
    param([Parameter(Mandatory = $true)][string]$Name)
    return [Environment]::GetEnvironmentVariable($Name, 'Process')
}

function Get-AitaBackupRequiredValue {
    param([Parameter(Mandatory = $true)][string]$Name)
    $value = Get-AitaBackupEnvironmentValue -Name $Name
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "$Name is required for database backup."
    }
    if ($value -match '(?i)(CHANGE_ME|CHANGEME|REPLACE_WITH|PLACEHOLDER)') {
        throw "$Name still contains a placeholder value."
    }
    return $value.Trim()
}

function Get-AitaCommand {
    param([Parameter(Mandatory = $true)][string[]]$Names)
    foreach ($name in $Names) {
        $command = Get-Command $name -ErrorAction SilentlyContinue
        if ($null -ne $command) { return $command }
    }
    return $null
}

function Move-AitaFileAtomically {
    param(
        [Parameter(Mandatory = $true)][string]$Source,
        [Parameter(Mandatory = $true)][string]$Destination
    )

    if (Test-Path -LiteralPath $Destination -PathType Leaf) {
        $replacementBackup = "$Destination.replace-backup"
        Remove-Item -LiteralPath $replacementBackup -Force -ErrorAction SilentlyContinue
        try {
            [System.IO.File]::Replace($Source, $Destination, $replacementBackup, $true)
        } finally {
            Remove-Item -LiteralPath $replacementBackup -Force -ErrorAction SilentlyContinue
        }
    } else {
        [System.IO.File]::Move($Source, $Destination)
    }
}

if ($EndOfDay -and -not $Once) {
    throw '-EndOfDay is an archival action and must be used together with -Once.'
}

$FieldRoot = [System.IO.Path]::GetFullPath($FieldRoot)
$environmentFile = Join-Path $FieldRoot 'aita-prod.env'
Import-AitaEnv -Path $environmentFile

$databaseUrl = Get-AitaBackupRequiredValue -Name 'AITA_DB_URL'
$databaseUser = Get-AitaBackupRequiredValue -Name 'DB_USER'
$databasePassword = Get-AitaBackupRequiredValue -Name 'DB_PASS'
if (-not $databaseUrl.StartsWith('jdbc:postgresql://', [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'AITA_DB_URL must be a jdbc:postgresql:// URL.'
}
$databaseUri = [System.Uri]($databaseUrl.Substring(5))
$databaseName = $databaseUri.AbsolutePath.Trim('/')
$databasePort = if ($databaseUri.Port -gt 0) { $databaseUri.Port } else { 5432 }
if ([string]::IsNullOrWhiteSpace($databaseName)) {
    throw 'AITA_DB_URL must include the database name.'
}

$pgDump = Get-AitaCommand -Names @('pg_dump.exe', 'pg_dump')
$pgRestore = Get-AitaCommand -Names @('pg_restore.exe', 'pg_restore')
if ($null -eq $pgDump -or $null -eq $pgRestore) {
    throw 'pg_dump and pg_restore must both be available on PATH.'
}

$age = $null
$ageRecipient = Get-AitaBackupEnvironmentValue -Name 'AITA_BACKUP_AGE_RECIPIENT'
if (-not $AllowPlaintext) {
    if ([string]::IsNullOrWhiteSpace($ageRecipient) -or $ageRecipient -notmatch '^age1[0-9a-z]+$') {
        throw 'AITA_BACKUP_AGE_RECIPIENT must contain a valid public age recipient. Plaintext backup is disabled by default.'
    }
    $age = Get-AitaCommand -Names @('age.exe', 'age')
    if ($null -eq $age) {
        throw 'age was not found on PATH. Install age or explicitly use -AllowPlaintext for a non-production test.'
    }
}

if ([string]::IsNullOrWhiteSpace($RcloneRemote)) {
    $RcloneRemote = Get-AitaBackupEnvironmentValue -Name 'AITA_BACKUP_RCLONE_REMOTE'
}
$rclone = $null
if (-not [string]::IsNullOrWhiteSpace($RcloneRemote)) {
    $rclone = Get-AitaCommand -Names @('rclone.exe', 'rclone')
    if ($null -eq $rclone) {
        throw 'AITA_BACKUP_RCLONE_REMOTE is configured, but rclone was not found on PATH.'
    }
    $RcloneRemote = $RcloneRemote.TrimEnd('/')
}

$backupDirectory = Join-Path $FieldRoot 'backups'
$tempDirectory = Join-Path $FieldRoot 'temp'
New-Item -ItemType Directory -Force -Path $backupDirectory, $tempDirectory | Out-Null

$lockPath = Join-Path $backupDirectory 'aita-backup.lock'
try {
    $lockStream = [System.IO.File]::Open(
        $lockPath,
        [System.IO.FileMode]::OpenOrCreate,
        [System.IO.FileAccess]::ReadWrite,
        [System.IO.FileShare]::None
    )
} catch {
    throw "Another AITA backup process is already running or the backup lock cannot be acquired: $lockPath"
}

$oldPgPassword = [Environment]::GetEnvironmentVariable('PGPASSWORD', 'Process')
[Environment]::SetEnvironmentVariable('PGPASSWORD', $databasePassword, 'Process')

function Invoke-AitaPostgresBackupOnce {
    $token = [Guid]::NewGuid().ToString('N')
    $plainTemporary = Join-Path $tempDirectory "aita-$token.dump"
    $candidateExtension = if ($AllowPlaintext) { '.dump' } else { '.dump.age' }
    $candidate = Join-Path $backupDirectory ".aita-$token$candidateExtension.tmp"
    $stableBaseName = if ($EndOfDay) { 'aita_end_of_day' } else { 'aita_latest' }
    $stableFile = Join-Path $backupDirectory "$stableBaseName$candidateExtension"
    $datedFile = $null
    $datedCandidate = $null

    try {
        $dumpArguments = @(
            '--format=custom',
            '--compress=9',
            '--no-owner',
            '--no-privileges',
            '--host', $databaseUri.Host,
            '--port', $databasePort,
            '--username', $databaseUser,
            '--file', $plainTemporary,
            $databaseName
        )
        & $pgDump.Source @dumpArguments
        if ($LASTEXITCODE -ne 0) {
            throw "pg_dump failed with exit code $LASTEXITCODE"
        }
        if (-not (Test-Path -LiteralPath $plainTemporary -PathType Leaf) -or (Get-Item -LiteralPath $plainTemporary).Length -eq 0) {
            throw 'pg_dump did not produce a non-empty backup file.'
        }

        & $pgRestore.Source '--list' $plainTemporary | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw "pg_restore could not validate the dump; exit code $LASTEXITCODE"
        }

        if ($AllowPlaintext) {
            Copy-Item -LiteralPath $plainTemporary -Destination $candidate -Force
        } else {
            & $age.Source '--encrypt' '--recipient' $ageRecipient '--output' $candidate $plainTemporary
            if ($LASTEXITCODE -ne 0) {
                throw "age encryption failed with exit code $LASTEXITCODE"
            }
        }

        if (-not (Test-Path -LiteralPath $candidate -PathType Leaf) -or (Get-Item -LiteralPath $candidate).Length -eq 0) {
            throw 'The final backup candidate is missing or empty.'
        }

        Move-AitaFileAtomically -Source $candidate -Destination $stableFile

        if ($EndOfDay) {
            $dateStamp = Get-Date -Format 'yyyy-MM-dd'
            $datedFile = Join-Path $backupDirectory "aita_end_of_day_$dateStamp$candidateExtension"
            $datedCandidate = "$datedFile.$token.tmp"
            Copy-Item -LiteralPath $stableFile -Destination $datedCandidate -Force
            Move-AitaFileAtomically -Source $datedCandidate -Destination $datedFile
        }

        $filesForRemoteCopy = @($stableFile)
        if ($null -ne $datedFile) {
            $filesForRemoteCopy += $datedFile
        }
        if ($null -ne $rclone) {
            foreach ($file in $filesForRemoteCopy) {
                $remotePath = "$RcloneRemote/$([System.IO.Path]::GetFileName($file))"
                & $rclone.Source 'copyto' $file $remotePath '--checksum' '--retries' '3' '--low-level-retries' '10'
                if ($LASTEXITCODE -ne 0) {
                    throw "rclone upload failed with exit code $LASTEXITCODE for $remotePath"
                }
            }
        }

        $backupKind = if ($AllowPlaintext) { 'Plaintext test' } else { 'Encrypted' }
        Write-Host "[$(Get-Date -Format o)] $backupKind PostgreSQL backup completed: $stableFile"
        if ($null -ne $datedFile) {
            Write-Host "[$(Get-Date -Format o)] End-of-day archive completed: $datedFile"
        }
    } finally {
        Remove-Item -LiteralPath $plainTemporary -Force -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $candidate -Force -ErrorAction SilentlyContinue
        if ($null -ne $datedCandidate) {
            Remove-Item -LiteralPath $datedCandidate -Force -ErrorAction SilentlyContinue
        }
    }
}

try {
    while ($true) {
        try {
            Invoke-AitaPostgresBackupOnce
        } catch {
            if ($Once) {
                throw
            }
            Write-Warning "[$(Get-Date -Format o)] Backup failed: $($_.Exception.Message)"
        }

        if ($Once) {
            break
        }
        Start-Sleep -Seconds $IntervalSeconds
    }
} finally {
    if ($null -eq $oldPgPassword) {
        Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    } else {
        [Environment]::SetEnvironmentVariable('PGPASSWORD', $oldPgPassword, 'Process')
    }
    $lockStream.Dispose()
}

# Pure PowerShell helper tests; no network, installation, elevation or process termination.
# Run with -FixtureDirectory containing envelope.json, public.txt and release.json made by test_windows_helpers.py.
param([Parameter(Mandatory=$true)][string]$FixtureDirectory)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\aita-install.ps1"
$script:Assertions = 0
function Test-Equal($Actual, $Expected, [string]$Label) {
    if ($Actual -cne $Expected) { throw "FAILED: $Label (actual=$Actual expected=$Expected)" }
    $script:Assertions++
}
function Test-Rejected([scriptblock]$Action, [string]$Label) {
    $rejected = $false
    try { & $Action } catch { $rejected = $true }
    if (-not $rejected) { throw "FAILED: $Label was accepted" }
    $script:Assertions++
}
$key = [IO.File]::ReadAllText((Join-Path $FixtureDirectory 'public.txt')).Trim()
$raw = [IO.File]::ReadAllBytes((Join-Path $FixtureDirectory 'envelope.json'))
$verified = Read-AitaEnvelope $raw $key
$release = $verified.Release
Test-Equal $release.version '1.3.0' 'verified version'
Test-Equal $verified.PayloadHash.Length 64 'payload hash'
Test-Rejected { Read-AitaEnvelope $raw } 'test key cannot impersonate production key'
$bad = ([Text.Encoding]::UTF8.GetString($raw) | ConvertFrom-Json)
$bad.signature = [Convert]::ToBase64String((New-Object byte[] 256))
Test-Rejected { Read-AitaEnvelope ([Text.Encoding]::UTF8.GetBytes(($bad | ConvertTo-Json -Compress))) $key } 'tampered signature'
$now = 1800000000000L
Assert-AitaRelease $release $verified.PayloadHash $null $now
$previous = [pscustomobject]@{ sequence=35L; build=35L; version='1.3.0'; payloadHash=$verified.PayloadHash }
Assert-AitaRelease $release $verified.PayloadHash $previous $now
Test-Rejected { Assert-AitaRelease $release 'different' $previous $now } 'same sequence changed payload'
$previous.sequence = 36L
Test-Rejected { Assert-AitaRelease $release $verified.PayloadHash $previous $now } 'old catalog replay'
Test-Rejected { Assert-AitaRelease $release $verified.PayloadHash $null ($now + 86400000L) } 'expired catalog'
Test-Rejected { Get-AitaInteger $true 1 100 } 'boolean as integer'
Test-Rejected { Get-AitaInteger 3.5 1 100 } 'fraction as integer'
$artifact = Select-AitaArtifact $release 'X64' 'EXE' 10
Test-Equal $artifact.kind 'EXE' 'explicit EXE selection'
Test-Rejected { Select-AitaArtifact $release 'ARM64' 'EXE' 10 } 'wrong architecture'
Test-Rejected { Select-AitaArtifact $release 'X64' 'EXE' 6 } 'old operating system'
foreach ($url in @('http://aita.kz/f.exe','https://evil.example/f.exe','https://aita.kz:8443/f.exe','https://aita.kz/f.exe?q=1','https://aita.kz@evil.example/f.exe','https://aita.kz/f.exe#x','https://aita.kz/f%2f.exe')) {
    Test-Rejected { Assert-AitaUrl $url 'EXE' } 'unsafe download address'
}
Assert-AitaUrl 'https://aita-api.bogdan-donduk.workers.dev/client-updates/artifacts/test.exe' 'EXE'
$file = Join-Path $FixtureDirectory 'installer.exe'
Assert-AitaFile $file $artifact
[IO.File]::AppendAllText($file,'modified')
Test-Rejected { Assert-AitaFile $file $artifact } 'modified installer'
Write-Host ("PASS: {0} PowerShell trust and package checks; no installer launched." -f $script:Assertions)

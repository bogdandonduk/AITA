#requires -Version 5.1
<#
AITA installer / updater. Download this file first; do not pipe a remote script into PowerShell.
Run from normal PowerShell: powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$HOME\Downloads\aita-install.ps1"
The execution-policy override applies only to this invocation. Windows protection and UAC remain enabled.
Use -Check or -DownloadOnly to inspect without installing. EXE is preferred; -Package MSI is explicit.
#>
[CmdletBinding()]
param([switch]$Check, [switch]$DownloadOnly, [ValidateSet('EXE','MSI')][string]$Package = 'EXE')

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$script:AitaFeed = 'https://aita-api.bogdan-donduk.workers.dev/client-updates/release.json'
$script:AitaPublicKey = 'MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEA8jCMS4mrcZwVK3qHQmnfBd+Nr2clmJTgH6RcIcHhRcc9fGU3A9QfDrXougrPuQ3WDSfZQaoa6RKw/Yb6k2NqOG2ZRiqgKcxYpEsWYkgjpFfWP+JBEsJrFvjhEfy43QWYWaxDVV30se8LOrDHyfw/ajBqyninHcfPeVMqsoh5Jznc8dCQd3vBih85pTMbR2h9aUKw+rYkZhZMtbWoWhoXRyC+NIA8KHpEgKnCtPP355+cEi/GF+A9pPK8W3/b5uNiejFFR6nP/nqpg91d7zfxzYLFqbEUv9qxL9/wh0MSKODGYRx/PzK0zXxwumEsz1S0u9hEKLm9SCm8oAVgB7d77HJ4AbL5geetgW/xNaz8gk4f8B6V/RDxUvIRFDX5F4cw2DXIJQW1wQkk0gwSaN10XsEJ5RrpZ6bfOV9/SF2P6RZNmIhLGujVl2zZxJzjZJ7xim2PnrLkSJtpk8HeNrVY+WgO85H1yy+PU/vlV0ozE9KAlRerqfndIV7BZ+6bNGTxAgMBAAE='
$script:AitaLog = $null

function Write-Aita([string]$Message) {
    Write-Host $Message
    if ($script:AitaLog) { Add-Content -LiteralPath $script:AitaLog -Encoding UTF8 -Value ((Get-Date -Format 'HH:mm:ss ') + $Message) }
}

function Assert-Aita($Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

function Get-AitaField($Value, [string]$Name, $Default = $null) {
    if ($null -ne $Value -and $Value.PSObject.Properties.Name -contains $Name) { return $Value.$Name }
    return $Default
}

function Get-AitaInteger($Value, [long]$Minimum, [long]$Maximum) {
    Assert-Aita (($Value -is [int] -or $Value -is [long]) -and $Value -ge $Minimum -and $Value -le $Maximum) 'Invalid signed numeric metadata.'
    return [long]$Value
}

function Get-AitaVersion($Value) {
    Assert-Aita ($Value -is [string] -and $Value -cmatch '^[0-9]{1,5}\.[0-9]{1,5}\.[0-9]{1,5}$') 'Invalid release version.'
    return [version]$Value
}

function Assert-AitaUrl([string]$Url, [string]$Kind = '') {
    Assert-Aita ($Url.Length -ge 10 -and $Url.Length -le 2048 -and $Url -notmatch '[\s\\\x00-\x1f\x7f]') 'Unsafe download address.'
    $uri = [uri]$Url
    Assert-Aita ($uri.IsAbsoluteUri -and $uri.Scheme -ceq 'https' -and $uri.Port -eq 443 -and
        @('aita-api.bogdan-donduk.workers.dev','api.aita.kz','aita.kz') -contains $uri.Host -and
        -not $uri.UserInfo -and -not $uri.Query -and -not $uri.Fragment -and $Url -notmatch '(?i)%(2f|5c|2e)|/\.\./') 'Download is outside trusted AITA HTTPS hosts.'
    if ($Kind) { Assert-Aita ($uri.AbsolutePath.EndsWith('.' + $Kind.ToLowerInvariant(), [StringComparison]::OrdinalIgnoreCase)) 'Installer type and address disagree.' }
}

function Initialize-AitaCrypto {
    if ('AitaInstallerCryptoV1' -as [type]) { return }
    # .NET Framework / Windows PowerShell 5.1 has no ImportSubjectPublicKeyInfo.
    # Parse only a strict DER RSA SPKI, then use Windows' RSA/SHA-256 implementation.
    Add-Type -TypeDefinition @'
using System;
using System.IO;
using System.Security.Cryptography;
public static class AitaInstallerCryptoV1 {
    static byte[] Read(byte[] data, ref int p, byte tag) {
        if(p >= data.Length || data[p++] != tag || p >= data.Length) throw new InvalidDataException("Invalid public key");
        int length = data[p++];
        if((length & 128) != 0) {
            int count = length & 127; length = 0;
            if(count < 1 || count > 4 || p + count > data.Length) throw new InvalidDataException();
            for(int n=0; n<count; n++) length = checked((length << 8) | data[p++]);
        }
        if(length < 0 || length > data.Length - p) throw new InvalidDataException();
        byte[] result = new byte[length]; Buffer.BlockCopy(data,p,result,0,length); p += length; return result;
    }
    static byte[] Positive(byte[] value) {
        if(value.Length > 1 && value[0] == 0) { byte[] cut = new byte[value.Length-1]; Buffer.BlockCopy(value,1,cut,0,cut.Length); return cut; }
        return value;
    }
    public static bool Verify(byte[] key, byte[] payload, byte[] signature) {
        int p=0; byte[] outer=Read(key,ref p,48); if(p!=key.Length) return false;
        p=0; byte[] algorithm=Read(outer,ref p,48); byte[] bits=Read(outer,ref p,3);
        if(p!=outer.Length || bits.Length < 2 || bits[0]!=0 || BitConverter.ToString(algorithm)!="06-09-2A-86-48-86-F7-0D-01-01-01-05-00") return false;
        byte[] body=new byte[bits.Length-1]; Buffer.BlockCopy(bits,1,body,0,body.Length);
        p=0; byte[] rsa=Read(body,ref p,48); if(p!=body.Length) return false;
        p=0; byte[] modulus=Positive(Read(rsa,ref p,2)); byte[] exponent=Positive(Read(rsa,ref p,2));
        if(p!=rsa.Length || modulus.Length < 256 || modulus.Length > 1024) return false;
        using(RSA verifier=RSA.Create()) {
            verifier.ImportParameters(new RSAParameters { Modulus=modulus, Exponent=exponent });
            return verifier.VerifyData(payload,signature,HashAlgorithmName.SHA256,RSASignaturePadding.Pkcs1);
        }
    }
}
'@
}

function Get-AitaHash([byte[]]$Bytes) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($sha.ComputeHash($Bytes))).Replace('-','').ToLowerInvariant() }
    finally { $sha.Dispose() }
}

function Read-AitaEnvelope([byte[]]$Bytes, [string]$PublicKey = $script:AitaPublicKey) {
    Assert-Aita ($Bytes.Length -le 262144) 'Release metadata is too large.'
    $utf8 = New-Object Text.UTF8Encoding($false, $true)
    $envelope = $utf8.GetString($Bytes) | ConvertFrom-Json
    Assert-Aita ((Get-AitaField $envelope 'algorithm') -ceq 'RS256') 'Unsupported release signature.'
    $payload = [Convert]::FromBase64String((Get-AitaField $envelope 'payload'))
    $signature = [Convert]::FromBase64String((Get-AitaField $envelope 'signature'))
    Assert-Aita ($payload.Length -gt 0 -and $payload.Length -le 180000 -and $signature.Length -ge 256 -and $signature.Length -le 1024) 'Invalid release signature size.'
    Initialize-AitaCrypto
    Assert-Aita ([AitaInstallerCryptoV1]::Verify([Convert]::FromBase64String($PublicKey), $payload, $signature)) 'Release signature could not be verified. Nothing was installed.'
    return @{ Release = ($utf8.GetString($payload) | ConvertFrom-Json); PayloadHash = (Get-AitaHash $payload) }
}

function Read-AitaHttp([string]$Url, [long]$Maximum, [string]$Destination = '') {
    Assert-AitaUrl $Url
    $request = [Net.HttpWebRequest]::Create($Url)
    $request.AllowAutoRedirect = $false
    $request.Timeout = 30000; $request.ReadWriteTimeout = 30000
    $request.UserAgent = 'AITA-Install/1'
    $response = $null; $stream = $null; $output = $null
    try {
        $response = $request.GetResponse()
        Assert-Aita ([int]$response.StatusCode -eq 200) 'Download did not return a file. Redirects are not accepted; get a current helper from aita.kz.'
        Assert-Aita ($response.ContentLength -le $Maximum) 'Download exceeds its signed size.'
        $stream = $response.GetResponseStream()
        if ($Destination) { $output = [IO.File]::Open($Destination, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None) }
        else { $output = New-Object IO.MemoryStream }
        $buffer = New-Object byte[] 262144
        [long]$total = 0; $last = -1
        while (($count = $stream.Read($buffer, 0, $buffer.Length)) -gt 0) {
            $total += $count
            Assert-Aita ($total -le $Maximum) 'Download exceeds its signed size.'
            $output.Write($buffer, 0, $count)
            if ($Destination) {
                $bucket = [int][Math]::Floor(($total * 100.0 / $Maximum) / 10)
                if ($bucket -gt $last) { Write-Aita ('Downloading: ' + ($bucket * 10) + '%'); $last = $bucket }
            }
        }
        if ($Destination -and $total -lt $Maximum) { throw [IO.IOException]::new('Download ended before the signed byte count.') }
        $output.Flush()
        if (-not $Destination) { return ,$output.ToArray() }
    } finally {
        if ($output) { $output.Dispose() }; if ($stream) { $stream.Dispose() }; if ($response) { $response.Dispose() }
    }
}

function Assert-AitaRelease($Release, [string]$PayloadHash, $Previous = $null, [long]$Now = ([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())) {
    Assert-Aita ((Get-AitaInteger (Get-AitaField $Release 'schema') 1 1) -eq 1 -and (Get-AitaField $Release 'channel') -ceq 'RELEASE') 'Not an AITA production release.'
    $sequence = Get-AitaInteger (Get-AitaField $Release 'sequence') 1 9007199254740991
    $build = Get-AitaInteger (Get-AitaField $Release 'build') 1 2100000000
    $version = Get-AitaVersion (Get-AitaField $Release 'version')
    Assert-Aita ((Get-AitaField $Release 'id') -cmatch '^[A-Za-z0-9][A-Za-z0-9._-]{0,79}$') 'Invalid release identity.'
    $published = Get-AitaInteger (Get-AitaField $Release 'publishedAtMillis') 1 9007199254740991
    $expires = Get-AitaInteger (Get-AitaField $Release 'expiresAtMillis') 1 9007199254740991
    Assert-Aita ($published -le ($Now + 600000) -and $expires -gt $Now -and $expires -gt $published -and ($expires - $published) -le (366L * 86400000L)) 'Catalog expired or computer clock incorrect. Check the date and retry.'
    $artifacts = @(Get-AitaField $Release 'artifacts' @())
    Assert-Aita ($artifacts.Count -ge 1 -and $artifacts.Count -le 32) 'No valid installer list.'
    $seen = @{}
    foreach ($artifact in $artifacts) {
        $key = ((Get-AitaField $artifact 'os'), (Get-AitaField $artifact 'arch' 'UNIVERSAL'), (Get-AitaField $artifact 'kind')) -join ':'
        Assert-Aita (-not $seen.ContainsKey($key)) 'Duplicate installer entry.'
        $seen[$key] = $true
    }
    if ($Previous) {
        Assert-Aita ($sequence -ge (Get-AitaInteger (Get-AitaField $Previous 'sequence') 1 9007199254740991) -and
            $build -ge (Get-AitaInteger (Get-AitaField $Previous 'build') 1 2100000000) -and $version -ge (Get-AitaVersion (Get-AitaField $Previous 'version'))) 'Older release catalog received. Downgrade refused.'
        if ($sequence -eq $Previous.sequence) { Assert-Aita ($PayloadHash -ceq (Get-AitaField $Previous 'payloadHash')) 'Catalog changed without a new sequence; retry later.' }
    }
}

function Select-AitaArtifact($Release, [string]$Architecture, [string]$Kind, [int]$Major) {
    $entries = @($Release.artifacts | Where-Object { $_.os -ceq 'WINDOWS' -and $_.kind -ceq $Kind -and (Get-AitaField $_ 'arch' 'UNIVERSAL') -cin @($Architecture, 'UNIVERSAL') } | Sort-Object { (Get-AitaField $_ 'arch' 'UNIVERSAL') -cne $Architecture })
    Assert-Aita ($entries.Count -gt 0) 'No compatible Windows installer is published. Use https://aita.kz or choose the other package type explicitly.'
    $artifact = $entries[0]
    Assert-Aita ((Get-AitaInteger (Get-AitaField $artifact 'minimumOsMajor' 0) 0 10000) -le $Major) 'This release requires a newer Windows version.'
    $null = Get-AitaInteger (Get-AitaField $artifact 'bytes') 1 2147483648
    Assert-Aita ((Get-AitaField $artifact 'sha256') -cmatch '^[0-9a-f]{64}$') 'Invalid installer checksum.'
    Assert-AitaUrl $artifact.url $Kind
    return $artifact
}

function Assert-AitaPath([string]$Path) {
    $item = Get-Item -LiteralPath $Path -Force -ErrorAction SilentlyContinue
    while ($item) {
        Assert-Aita (-not ($item.Attributes -band [IO.FileAttributes]::ReparsePoint)) 'Installer folder contains a redirect/junction. Use a regular local user profile.'
        if ($item -is [IO.FileInfo]) { $item = $item.Directory }
        else { $item = $item.Parent }
    }
}

function Assert-AitaFile([string]$Path, $Artifact) {
    Assert-AitaPath $Path
    $file = Get-Item -LiteralPath $Path
    Assert-Aita (-not $file.PSIsContainer -and $file.Length -eq $Artifact.bytes -and (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() -ceq $Artifact.sha256) 'Installer checksum/size mismatch. Nothing was installed; download again.'
}

function Save-AitaInstaller($Artifact, [string]$Folder) {
    $path = Join-Path $Folder ($Artifact.sha256 + '.' + $Artifact.kind.ToLowerInvariant())
    if (Test-Path -LiteralPath $path) { Assert-AitaFile $path $Artifact; Write-Aita 'Already downloaded; checksum verified.'; return $path }
    $drive = New-Object IO.DriveInfo([IO.Path]::GetPathRoot($Folder))
    Assert-Aita ($drive.AvailableFreeSpace -ge ($Artifact.bytes + 67108864)) 'Not enough free disk space for the installer.'
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        $partial = Join-Path $Folder ('download-' + [guid]::NewGuid().ToString('N') + '.partial')
        try {
            Read-AitaHttp $Artifact.url $Artifact.bytes $partial
            Assert-AitaFile $partial $Artifact
            Move-Item -LiteralPath $partial -Destination $path
            return $path
        } catch [Net.WebException] {
            if ($attempt -eq 3) { throw 'Download interrupted three times. Check your connection and rerun the script.' }
            Write-Aita "Connection interrupted; retrying ($attempt/3)."
            Start-Sleep -Seconds $attempt
        } catch [IO.IOException] {
            if ($attempt -eq 3) { throw 'Download or file write interrupted three times. Check your connection and free disk space, then rerun.' }
            Write-Aita "Download interrupted; retrying ($attempt/3)."
            Start-Sleep -Seconds $attempt
        } finally { if (Test-Path -LiteralPath $partial) { Remove-Item -LiteralPath $partial -Force } }
    }
}

function Start-AitaInstallation {
    Assert-Aita ($env:OS -eq 'Windows_NT') 'This script is for Windows. Linux uses aita-install.py.'
    # This is independent of whether the terminal itself is 32-bit.
    $nativeArch = if ($env:PROCESSOR_ARCHITEW6432) { $env:PROCESSOR_ARCHITEW6432 } else { $env:PROCESSOR_ARCHITECTURE }
    $arch = switch ($nativeArch) { 'AMD64' { 'X64' }; 'ARM64' { 'ARM64' }; default { '' } }
    Assert-Aita $arch '32-bit Windows has no supported native AITA build. Use the web app at https://aita.kz.'
    $osVersion = [Environment]::OSVersion.Version
    Assert-Aita ($osVersion.Major -ge 10) 'Windows 10 or later is required.'
    Assert-Aita $env:LOCALAPPDATA 'Windows LocalAppData is unavailable.'
    $folder = Join-Path $env:LOCALAPPDATA 'AITA\InstallUpdates'
    # Check existing parents before creation; creating a child through a junction is not permitted.
    $parent = $folder
    while (-not (Test-Path -LiteralPath $parent)) { $parent = Split-Path -Parent $parent }
    Assert-AitaPath $parent
    $null = New-Item -ItemType Directory -Path $folder -Force
    Assert-AitaPath $folder
    $script:AitaLog = Join-Path $folder ('install-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N') + '.log')
    Write-Aita 'AITA | verified installation and updates'
    Write-Aita ('Log: ' + $script:AitaLog)
    $lock = $null
    try {
        Assert-AitaPath (Join-Path $folder 'install.lock')
        try { $lock = [IO.File]::Open((Join-Path $folder 'install.lock'), [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None) }
        catch { throw 'Another AITA installer is already running. Finish or close that window first.' }
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        Write-Aita '[1/5] Checking the signed release catalog...'
        $catalog = Read-AitaEnvelope (Read-AitaHttp $script:AitaFeed 262144)
        $statePath = Join-Path $folder 'trusted-release.json'
        $previous = $null
        if (Test-Path -LiteralPath $statePath) {
            Assert-AitaPath $statePath
            Assert-Aita ((Get-Item -LiteralPath $statePath).Length -le 262144) 'Saved release state is damaged.'
            $previous = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
        }
        $release = $catalog.Release
        Assert-AitaRelease $release $catalog.PayloadHash $previous
        $artifact = Select-AitaArtifact $release $arch $Package $osVersion.Major
        foreach ($registry in @('HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\*', 'HKLM:\Software\Microsoft\Windows\CurrentVersion\Uninstall\*', 'HKLM:\Software\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\*')) {
            foreach ($installed in @(Get-ItemProperty -Path $registry -ErrorAction SilentlyContinue)) {
                if ((Get-AitaField $installed 'DisplayName') -ceq 'AITA' -and (Get-AitaField $installed 'DisplayVersion') -match '^\d+\.\d+\.\d+$') {
                    Assert-Aita ((Get-AitaVersion $installed.DisplayVersion) -le (Get-AitaVersion $release.version)) 'A newer AITA version is already installed. Downgrade refused.'
                }
            }
        }
        $temporaryState = Join-Path $folder ('state-' + [guid]::NewGuid().ToString('N') + '.json')
        @{ sequence=$release.sequence; build=$release.build; version=$release.version; payloadHash=$catalog.PayloadHash } | ConvertTo-Json -Compress | Set-Content -LiteralPath $temporaryState -Encoding UTF8
        if (Test-Path -LiteralPath $statePath) { [IO.File]::Replace($temporaryState, $statePath, $null) }
        else { [IO.File]::Move($temporaryState, $statePath) }
        Write-Aita ("Verified: AITA {0} | build {1} | Windows {2} {3}" -f $release.version,$release.build,$arch,$Package)
        if ($Check) { Write-Aita 'CHECKED: no installer was downloaded or started.'; return }
        Write-Aita '[2/5] Downloading and checking every byte...'
        $path = Save-AitaInstaller $artifact $folder
        Write-Aita ('Verified installer: ' + $path)
        $signature = Get-AuthenticodeSignature -LiteralPath $path
        Assert-Aita ($signature.Status -in @('Valid','NotSigned')) 'Windows reports an invalid publisher signature. Installation blocked.'
        # Pilot releases may intentionally be unsigned. Do not describe feed signatures as Windows publisher signing.
        $signedExpected = $false
        foreach ($entry in @(Get-AitaField $release 'downloads' @())) {
            if ((Get-AitaField $entry 'build') -eq $release.build) {
                foreach ($file in @(Get-AitaField $entry 'files' @())) {
                    if ((Get-AitaField $file 'sha256') -ceq $artifact.sha256 -and (Get-AitaField $file 'publisherSigned' $false) -eq $true) { $signedExpected = $true }
                }
            }
        }
        Assert-Aita (-not $signedExpected -or $signature.Status -eq 'Valid') 'A signed publisher identity was expected but Windows could not verify it.'
        if ($signature.Status -eq 'NotSigned') { Write-Aita 'This is a pilot installer without a Windows publisher certificate. Its AITA release signature and file checksum were verified. Windows may show its normal warning.' }
        if ($DownloadOnly) { Write-Aita 'DOWNLOADED: installation was not started.'; return }
        Write-Aita '[3/5] Save your work and close all AITA windows before continuing.'
        Write-Aita 'Existing account data and offline caches are preserved. Windows permission prompts remain enabled.'
        $answer = Read-Host 'Install this verified release now? [y/N]'
        Assert-Aita ($answer -in @('y','yes')) 'Cancelled. The verified installer is retained for another attempt.'
        # Ask only exact AITA windows in this interactive Windows session to close normally.
        # Never TerminateProcess/Stop-Process, generic Java killing, or cross-user shutdown.
        $session = (Get-Process -Id $PID).SessionId
        $running = @(Get-Process -Name 'AITA' -ErrorAction SilentlyContinue | Where-Object { $_.SessionId -eq $session })
        foreach ($app in $running) {
            Write-Aita 'Asking the open AITA window to close normally. Respond to any unsaved-work dialog.'
            $null = $app.CloseMainWindow()
        }
        $deadline = [DateTime]::UtcNow.AddSeconds(30)
        foreach ($app in $running) {
            $remaining = [Math]::Max(0, [int]($deadline - [DateTime]::UtcNow).TotalMilliseconds)
            Assert-Aita ($app.WaitForExit($remaining)) 'AITA is still running. Save your work, close it normally, then rerun this script.'
        }
        $otherInstaller = @(Get-Process -ErrorAction SilentlyContinue | Where-Object { $_.ProcessName -eq $artifact.sha256 })
        Assert-Aita ($otherInstaller.Count -eq 0) 'This AITA installer is already running. Finish that installation first.'
        Assert-AitaFile $path $artifact
        Write-Aita '[4/5] Starting the visible Windows installer. Approve the normal permission prompt and follow its steps.'
        if ($Package -eq 'MSI') {
            $msiLog = Join-Path $folder ('windows-msi-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.log')
            Write-Aita ('Windows Installer log: ' + $msiLog)
            $arguments = '/i "' + $path + '" /norestart /L*v "' + $msiLog + '"'
            $process = Start-Process -FilePath (Join-Path $env:WINDIR 'System32\msiexec.exe') -ArgumentList $arguments -PassThru -Wait
        } else {
            # No hidden or silent switches; jpackage's bootstrapper handles its own normal UAC prompt.
            $process = Start-Process -FilePath $path -PassThru -Wait
        }
        $exitCode = $process.ExitCode
        Assert-Aita ($exitCode -in @(0,3010,1641)) ("Windows installer returned code {0}. Resolve its displayed error and rerun. Installer retained; no app data deleted." -f $exitCode)
        Write-Aita '[5/5] Windows reported success. Removing only this helper''s verified installer.'
        Remove-Item -LiteralPath $path
        if ($exitCode -in @(3010,1641)) { Write-Aita 'RESTART REQUIRED: restart Windows when convenient before using the new version.' }
        Write-Aita 'INSTALLED: open AITA from the Start menu. Logs remain in the folder shown above.'
    } finally { if ($lock) { $lock.Dispose() } }
}

# Dot-sourcing exposes pure helpers to the isolated tests; normal invocation always executes verification.
if ($MyInvocation.InvocationName -ne '.') {
    try { Start-AitaInstallation }
    catch {
        # Do not dump invocation arguments, environment variables, or complete HTTP error responses.
        Write-Aita ('STOPPED: ' + $_.Exception.Message)
        if ($script:AitaLog) { Write-Host ('Log: ' + $script:AitaLog) }
        exit 1
    }
}

# Run on the Windows build machine before publishing a Windows package.
# This compiles the bundled helper and optionally validates the packaged runtime.
# It never records any user window or connects to the network.
param([string]$AppImage = '')
$packagedOcrVerified = $false
$ErrorActionPreference = 'Stop'
$repo = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$helper = Join-Path $repo 'composeApp\src\jvmMain\resources\stock-capture\stock-capture-helper.ps1'
Write-Host '[1/4] Checking local Windows helper compilation; no screen capture'
$reply = & powershell.exe -NoLogo -NoProfile -NonInteractive -STA -ExecutionPolicy Bypass -File $helper -Mode Verify
if ($LASTEXITCODE -ne 0) { throw 'Local capture helper failed to compile' }
$parsed = $reply | ConvertFrom-Json
if ($parsed.type -ne 'verified' -or $parsed.protocol -ne 1) { throw 'Unexpected helper protocol' }
Write-Host '[2/4] Verifying pinned, bundled offline recognition models'
$data = Join-Path $repo 'composeApp\src\jvmMain\resources\stock-capture\tessdata'
$manifest = Get-Content -Raw (Join-Path $data 'manifest.json') | ConvertFrom-Json
foreach ($model in $manifest.models) {
    $path = Join-Path $data $model.file
    if ((Get-Item $path).Length -ne $model.bytes -or (Get-FileHash $path -Algorithm SHA256).Hash.ToLowerInvariant() -ne $model.sha256) {
        throw ('Recognition model failed integrity verification: ' + $model.file)
    }
}
Write-Host '[3/4] Checking package runtime dependencies'
if ($AppImage) {
    $AppImage = (Resolve-Path -LiteralPath $AppImage).Path
    $runtime = Join-Path $AppImage 'runtime\bin'
    # Tess4J and Lept4J carry libtesseract552/libleptonica1870 themselves. These
    # remaining Microsoft runtime DLLs must be included with the application's JRE.
    foreach ($dll in @('vcruntime140.dll','vcruntime140_1.dll','msvcp140.dll')) {
        if (-not (Test-Path (Join-Path $runtime $dll))) { throw "Missing bundled OCR runtime dependency: $dll" }
    }
    $jars = @(Get-ChildItem -Path (Join-Path $AppImage 'app') -Filter '*.jar' -Recurse)
    if (-not ($jars.Name -like '*tess4j*5.18.0*')) { throw 'Pinned Tess4J runtime is missing from package' }
    if (-not ($jars.Name -like '*lept4j*1.23.0*')) { throw 'Pinned Lept4J runtime is missing from package' }
    if (-not (Test-Path (Join-Path $AppImage 'AITA.exe'))) { throw 'Installed worker launcher is missing' }
    Write-Host 'Checking packaged OCR with a generated fixture image (no screen capture)'
    Add-Type -AssemblyName System.Drawing
    $bitmap = New-Object System.Drawing.Bitmap(1100,160)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $font = New-Object System.Drawing.Font('Arial',26)
    $memory = New-Object System.IO.MemoryStream
    try {
        $graphics.Clear([System.Drawing.Color]::White)
        $graphics.DrawString('Milk  4601234567893  12.5 kg', $font, [System.Drawing.Brushes]::Black, 20, 25)
        $cyrillic = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('0JzQvtC70L7QutC+ICA2MDAsMDA='))
        $graphics.DrawString($cyrillic, $font, [System.Drawing.Brushes]::Black, 20, 85)
        $bitmap.Save($memory, [System.Drawing.Imaging.ImageFormat]::Png)
        $png = [Convert]::ToBase64String($memory.ToArray())
    } finally { $graphics.Dispose(); $font.Dispose(); $bitmap.Dispose(); $memory.Dispose() }
    $encodedData = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($data))
    $encodedLanguage = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes('rus+eng'))
    $info = New-Object System.Diagnostics.ProcessStartInfo
    $info.FileName = Join-Path $AppImage 'AITA.exe'
    $info.Arguments = '--aita-local-ocr ' + $encodedData + ' ' + $encodedLanguage
    $info.UseShellExecute = $false; $info.CreateNoWindow = $true
    $info.RedirectStandardInput = $true; $info.RedirectStandardOutput = $true; $info.RedirectStandardError = $true
    $info.StandardOutputEncoding = [Text.Encoding]::UTF8
    $info.EnvironmentVariables['PATH'] = $runtime + ';' + $env:PATH
    $info.EnvironmentVariables['JAVA_TOOL_OPTIONS'] = '-Xmx320m -Djava.awt.headless=true -XX:ActiveProcessorCount=2'
    $info.EnvironmentVariables['OMP_THREAD_LIMIT'] = '1'
    $child = New-Object System.Diagnostics.Process
    $child.StartInfo = $info
    try {
        # .NET Framework creates and AutoFlush-es its stdin StreamWriter inside
        # Process.Start. Prevent a BOM there, before touching BaseStream below.
        $previousInputEncoding = [Console]::InputEncoding
        try {
            [Console]::InputEncoding = New-Object System.Text.UTF8Encoding($false)
            if (-not $child.Start()) { throw 'Packaged OCR worker did not start' }
        } finally { [Console]::InputEncoding = $previousInputEncoding }
        $outputTask = $child.StandardOutput.ReadToEndAsync()
        $errorTask = $child.StandardError.ReadToEndAsync()
        # Windows PowerShell's default StreamWriter can add an encoding preamble.
        # The production JVM sends raw ASCII Base64, so exercise that exact framing.
        $frameBytes = [Text.Encoding]::ASCII.GetBytes($png + "`n")
        $workerInput = $child.StandardInput.BaseStream
        $workerInput.Write($frameBytes, 0, $frameBytes.Length)
        $workerInput.Flush(); $workerInput.Close()
        if (-not $child.WaitForExit(30000)) { $child.Kill(); throw 'Packaged OCR worker timed out' }
        $result = $outputTask.Result.Trim() | ConvertFrom-Json
        if ($result.type -eq 'error') {
            $phase = if ($result.phase -match '^[a-z_]{1,32}$') { $result.phase } else { 'unknown' }
            $classes = @($result.classes | Where-Object { $_ -cmatch '^[A-Za-z0-9.$]{1,160}$' } | Select-Object -First 4) -join ', '
            throw ('Packaged local OCR worker failed at ' + $phase + '; exception classes: ' + $classes)
        }
        if ($result.type -ne 'ocr' -or -not ($result.words.text -contains '4601234567893') -or -not ($result.words.text -contains $cyrillic.Split(' ')[0])) {
            $wordCount = @($result.words).Count
            $hasBarcode = $result.words.text -contains '4601234567893'
            $hasCyrillic = $result.words.text -contains $cyrillic.Split(' ')[0]
            # This process received only the image generated above; never use this
            # fixture diagnostic path for actual captured windows or imported files.
            $syntheticWords = @($result.words | Select-Object -First 16 | ForEach-Object { ([string]$_.text).Substring(0, [Math]::Min(80, ([string]$_.text).Length)) })
            Write-Host ('Synthetic fixture words only: ' + ($syntheticWords | ConvertTo-Json -Compress))
            throw ('Packaged local OCR failed the synthetic fixture; words=' + $wordCount + '; barcode=' + $hasBarcode + '; Cyrillic=' + $hasCyrillic + '; exit=' + $child.ExitCode)
        }
        # stderr may include native errors. Never print it or save screenshots into diagnostics.
    } finally { try { if (-not $child.HasExited) { $child.Kill() } } catch { }; $child.Dispose() }
    $packagedOcrVerified = $true
    Write-Host 'PASS: packaged dependencies and synthetic local OCR verified'
} else {
    Write-Host 'PASS: helper and seven languages verified; provide -AppImage to verify a built package'
}

Write-Host '[4/4] Checking actual recorder against owned synthetic windows'
$probe = New-Object System.Diagnostics.ProcessStartInfo
$probe.FileName = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
$fixturePath = Join-Path $PSScriptRoot 'run-windows-stock-capture-fixture.ps1'
$probe.Arguments = '-NoLogo -NoProfile -NonInteractive -STA -ExecutionPolicy Bypass -File "' + $fixturePath + '" -Verify'
$probe.UseShellExecute = $false; $probe.CreateNoWindow = $true
$probe.RedirectStandardOutput = $true; $probe.RedirectStandardError = $true
$probe.StandardOutputEncoding = [Text.Encoding]::UTF8
$native = New-Object System.Diagnostics.Process
$native.StartInfo = $probe
$nativeStatus = 'failed'; $nativeReason = 'native_probe_failed'
try {
    if (-not $native.Start()) { throw 'Native recorder probe could not start' }
    $nativeOutput = $native.StandardOutput.ReadToEndAsync()
    $nativeErrors = $native.StandardError.ReadToEndAsync()
    if (-not $native.WaitForExit(55000)) { $native.Kill(); throw 'Native recorder probe exceeded its time limit' }
    $observation = $nativeOutput.Result.Trim() | ConvertFrom-Json
    if ($native.ExitCode -eq 2 -and $observation.type -eq 'fixture_unsupported') {
        $nativeStatus = 'unsupported'; $nativeReason = $observation.code
        Write-Warning ('UNSUPPORTED: interactive recorder acceptance was NOT executed: ' + $nativeReason + '. Run the fixture on an unlocked Windows desktop before claiming physical capture acceptance.')
    } elseif ($native.ExitCode -eq 0 -and $observation.type -eq 'fixture_verified') {
        $nativeStatus = 'passed'; $nativeReason = ''
        Write-Host 'PASS: real selected-window UIA, clipping, pause/resume, foreground ownership, nonactivating overlay, OCR bitmap and stop verified'
    } else {
        if ($observation.code -match '^[a-z_]{1,64}$') { $nativeReason = $observation.code }
        if ($observation.diagnostics) {
            # The fixture process owns the only source HWND; these are generated cells.
            Write-Host ('Owned synthetic-window diagnostics: ' + ($observation.diagnostics | ConvertTo-Json -Compress -Depth 4))
        }
        throw ('Native recorder synthetic-window verification failed: ' + $nativeReason)
    }
} finally {
    try { if (-not $native.HasExited) { $native.Kill() } } catch { }
    $native.Dispose()
    $report = Join-Path $repo 'build\windows-stock-capture-verification.json'
    New-Item -ItemType Directory -Force (Split-Path $report -Parent) | Out-Null
    [ordered]@{schema=1;helperCompilation='passed';bundledModels='passed';packagedOcr=$packagedOcrVerified;
        nativeRecorder=$nativeStatus;nativeReason=$nativeReason;customerScreenCaptured=$false;
        generatedAt=[DateTime]::UtcNow.ToString('o')} | ConvertTo-Json | Set-Content -Encoding UTF8 $report
}

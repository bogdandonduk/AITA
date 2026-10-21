package kz.aita

import java.io.File
import java.util.UUID
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString

internal fun windowsUpdateLauncher(path: String?): File? = path?.takeIf { it.none { c -> c.code < 32 || c == '"' } }
    ?.let(::File)?.takeIf { it.isAbsolute && it.isFile && it.name.equals("AITA.exe", true) }

/** The signed feed authenticates the installer even during the explicitly unsigned Windows pilot.
 * Windows Installer owns major upgrades and removal of superseded program files. This helper
 * never deletes an installation directory, app data, database, credential or pending transaction.
 */
internal val windowsUpdateScript = """
${'$'}ErrorActionPreference = 'Stop'
${'$'}ready = ${'$'}env:AITA_UPDATE_READY
${'$'}launcher = ${'$'}env:AITA_UPDATE_LAUNCHER
${'$'}installer = ${'$'}env:AITA_UPDATE_INSTALLER
${'$'}parentId = [int]${'$'}env:AITA_UPDATE_PARENT
${'$'}log = ${'$'}env:AITA_UPDATE_LOG
${'$'}parentExited = ${'$'}false
${'$'}installed = ${'$'}false
${'$'}lease = ${'$'}null
${'$'}failure = ${'$'}null
${'$'}script:window = ${'$'}null
${'$'}script:finished = ${'$'}false
${'$'}words = @{}
if (${'$'}env:AITA_UPDATE_MESSAGES) { ${'$'}words = Get-Content -Raw -LiteralPath ${'$'}env:AITA_UPDATE_MESSAGES -Encoding UTF8 | ConvertFrom-Json }
function Text([string]${'$'}key) { if (${'$'}words.${'$'}key) { return [string]${'$'}words.${'$'}key }; return ${'$'}key }
function Initialize-UpdateWindow {
    Add-Type -AssemblyName System.Windows.Forms
    Add-Type -AssemblyName System.Drawing
    [Windows.Forms.Application]::EnableVisualStyles()
    ${'$'}script:window = New-Object Windows.Forms.Form
    ${'$'}window.Text = Text 'title'
    ${'$'}window.ClientSize = New-Object Drawing.Size(550, 210)
    ${'$'}window.StartPosition = 'CenterScreen'
    ${'$'}window.FormBorderStyle = 'FixedDialog'
    ${'$'}window.MaximizeBox = ${'$'}false
    ${'$'}window.Font = New-Object Drawing.Font('Segoe UI', 11)
    try { ${'$'}window.Icon = [Drawing.Icon]::ExtractAssociatedIcon(${'$'}launcher) } catch { }
    ${'$'}script:statusLabel = New-Object Windows.Forms.Label
    ${'$'}statusLabel.SetBounds(24, 20, 502, 92)
    ${'$'}window.Controls.Add(${'$'}statusLabel)
    ${'$'}script:progress = New-Object Windows.Forms.ProgressBar
    ${'$'}progress.SetBounds(24, 120, 502, 12)
    ${'$'}progress.Style = 'Marquee'
    ${'$'}window.Controls.Add(${'$'}progress)
    ${'$'}details = New-Object Windows.Forms.Button
    ${'$'}details.SetBounds(24, 154, 242, 34)
    ${'$'}details.Text = Text 'logs'
    ${'$'}details.Add_Click({ Start-Process -FilePath 'explorer.exe' -ArgumentList ('"' + [IO.Path]::GetDirectoryName(${'$'}log) + '"') })
    ${'$'}window.Controls.Add(${'$'}details)
    ${'$'}script:closeButton = New-Object Windows.Forms.Button
    ${'$'}closeButton.SetBounds(284, 154, 242, 34)
    ${'$'}closeButton.Text = Text 'close'
    ${'$'}closeButton.Enabled = ${'$'}false
    ${'$'}closeButton.Add_Click({ ${'$'}window.Close() })
    ${'$'}window.Controls.Add(${'$'}closeButton)
    ${'$'}window.Add_FormClosing({ if (-not ${'$'}script:finished) { ${'$'}_.Cancel = ${'$'}true } })
    ${'$'}window.Show()
    ${'$'}window.Activate()
    [Windows.Forms.Application]::DoEvents()
}
function Pump-UpdateWindow { if (${'$'}script:window) { [Windows.Forms.Application]::DoEvents() } }
function Set-UpdateStatus([string]${'$'}key) {
    Write-Output (([DateTime]::UtcNow.ToString('o')) + ' ' + ${'$'}key)
    if (${'$'}script:window) { ${'$'}statusLabel.Text = Text ${'$'}key; Pump-UpdateWindow }
}
function Finish-UpdateWindow([bool]${'$'}success) {
    if (-not ${'$'}script:window) { return }
    ${'$'}script:finished = ${'$'}true
    ${'$'}progress.Style = 'Blocks'
    ${'$'}progress.Value = if (${'$'}success) { 100 } else { 0 }
    ${'$'}closeButton.Enabled = ${'$'}true
    if (${'$'}success) { ${'$'}window.Close() }
    else { while (${'$'}window.Visible) { Pump-UpdateWindow; Start-Sleep -Milliseconds 100 } }
    ${'$'}window.Dispose()
}
function Get-AitaInstallerHash([string]${'$'}path) {
    ${'$'}stream = [IO.File]::OpenRead(${'$'}path)
    ${'$'}sha = [Security.Cryptography.SHA256]::Create()
    try { return [BitConverter]::ToString(${'$'}sha.ComputeHash(${'$'}stream)).Replace('-', '').ToLowerInvariant() }
    finally { ${'$'}sha.Dispose(); ${'$'}stream.Dispose() }
}
function Wait-AitaProcess(${'$'}process, [int]${'$'}timeoutSeconds) {
    ${'$'}deadline = [DateTime]::UtcNow.AddSeconds(${'$'}timeoutSeconds)
    while (${'$'}process -and -not ${'$'}process.WaitForExit(200)) {
        Pump-UpdateWindow
        if ([DateTime]::UtcNow -ge ${'$'}deadline) { return ${'$'}false }
    }
    return ${'$'}true
}
# BEGIN_HANDOFF
try {
    # This OS-held lease also covers separate app processes and disappears automatically on a crash.
    try { ${'$'}lease = [IO.File]::Open(${'$'}env:AITA_UPDATE_LOCK, [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None) }
    catch { throw 'another_update_is_running' }
    if (-not (Test-Path -LiteralPath ${'$'}launcher -PathType Leaf)) { throw 'Installed AITA launcher is missing' }
    ${'$'}kind = [IO.Path]::GetExtension(${'$'}installer).ToLowerInvariant()
    if (${'$'}kind -notin @('.msi', '.exe')) { throw 'Unsupported Windows installer' }
    if ((Get-AitaInstallerHash ${'$'}installer) -ne ${'$'}env:AITA_UPDATE_SHA256) { throw 'Installer checksum mismatch' }
    # A visible independent window must exist before the running app agrees to exit.
    Initialize-UpdateWindow
    Set-UpdateStatus 'saving'
    [IO.File]::WriteAllText(${'$'}env:AITA_UPDATE_ACTIVE, [string]${'$'}PID)
    [IO.File]::WriteAllText(${'$'}ready, 'ready')
    ${'$'}parent = Get-Process -Id ${'$'}parentId -ErrorAction SilentlyContinue
    if (-not (Wait-AitaProcess ${'$'}parent 120)) { throw 'AITA did not finish saving; installation was cancelled' }
    ${'$'}parentExited = ${'$'}true
    if ((Get-AitaInstallerHash ${'$'}installer) -ne ${'$'}env:AITA_UPDATE_SHA256) { throw 'Installer changed after handoff' }
    ${'$'}deadline = [DateTime]::UtcNow.AddSeconds(120)
    do {
        ${'$'}holders = @(Get-Process -Name AITA -ErrorAction SilentlyContinue | Where-Object {
            try { ${'$'}_.Path -eq ${'$'}launcher } catch { ${'$'}false }
        })
        if (${'$'}holders.Count -eq 0) { break }
        Set-UpdateStatus 'windows'
        if ([DateTime]::UtcNow -ge ${'$'}deadline) { throw 'Close all AITA windows and retry the update' }
        Pump-UpdateWindow
        Start-Sleep -Milliseconds 200
    } while (${'$'}true)
    ${'$'}installDirectory = [IO.Path]::GetDirectoryName(${'$'}launcher)
    ${'$'}installArgument = if (${'$'}installDirectory -match '\s') { 'INSTALLDIR="' + ${'$'}installDirectory.TrimEnd('\') + '"' } else { 'INSTALLDIR=' + ${'$'}installDirectory }
    ${'$'}ui = if (${'$'}env:AITA_UPDATE_UNATTENDED -eq 'true') { ' /passive' } else { '' }
    Set-UpdateStatus 'permission'
    if (${'$'}kind -eq '.msi') {
        ${'$'}arguments = '/i "' + ${'$'}installer + '" ' + ${'$'}installArgument + ${'$'}ui + ' /norestart /L*V "' + ${'$'}log + '"'
        ${'$'}result = Start-Process -FilePath (Join-Path ${'$'}env:SystemRoot 'System32\msiexec.exe') -ArgumentList ${'$'}arguments -Verb RunAs -WindowStyle Normal -PassThru
    } else {
        ${'$'}arguments = ${'$'}installArgument + ${'$'}ui + ' /norestart /L*V "' + ${'$'}log + '"'
        ${'$'}result = Start-Process -FilePath ${'$'}installer -ArgumentList ${'$'}arguments -Verb RunAs -WindowStyle Normal -PassThru
    }
    if (-not ${'$'}result) { throw 'Installer did not return a process handle' }
    Set-UpdateStatus 'installing'
    ${'$'}slowAt = [DateTime]::UtcNow.AddSeconds(60)
    ${'$'}slowShown = ${'$'}false
    # Start-Process -Wait includes descendants, including AITA launched by the installer.
    # jpackage's EXE itself waits for its MSI. Wait only for that installer process.
    while (-not ${'$'}result.WaitForExit(200)) {
        Pump-UpdateWindow
        if (-not ${'$'}slowShown -and [DateTime]::UtcNow -ge ${'$'}slowAt) { Set-UpdateStatus 'slow'; ${'$'}slowShown = ${'$'}true }
    }
    ${'$'}result.Refresh()
    Write-Output ('Installer exit code: ' + ${'$'}result.ExitCode)
    if (${'$'}result.ExitCode -notin @(0, 3010)) { throw ('Windows Installer returned ' + ${'$'}result.ExitCode) }
    ${'$'}installed = ${'$'}true
    [IO.File]::WriteAllText(${'$'}env:AITA_UPDATE_RESULT, 'success')
    [IO.File]::WriteAllText(${'$'}env:AITA_UPDATE_LAST_RESULT, 'success')
    Set-UpdateStatus 'complete'
} catch {
    ${'$'}failure = ${'$'}_.Exception.Message
    ${'$'}failureText = 'failed: ' + ${'$'}failure + [Environment]::NewLine + ${'$'}log
    [IO.File]::WriteAllText(${'$'}env:AITA_UPDATE_RESULT, ${'$'}failureText)
    if (${'$'}lease) { [IO.File]::WriteAllText(${'$'}env:AITA_UPDATE_LAST_RESULT, ${'$'}failureText) }
    Write-Output ('AITA update stopped: ' + ${'$'}failure)
    Set-UpdateStatus 'failed'
} finally {
    Remove-Item -LiteralPath ${'$'}env:AITA_UPDATE_ACTIVE -ErrorAction SilentlyContinue
    # Only delete our staged installer after confirmed completion; never touch data or program folders.
    if (${'$'}installed -and ${'$'}env:AITA_UPDATE_STAGED -eq 'true') { Remove-Item -LiteralPath ${'$'}installer -ErrorAction SilentlyContinue }
    Remove-Item -LiteralPath ${'$'}ready -ErrorAction SilentlyContinue
    if (${'$'}lease) { ${'$'}lease.Dispose() }
    if (${'$'}parentExited -and ${'$'}env:AITA_UPDATE_RELAUNCH -ne 'false' -and (Test-Path -LiteralPath ${'$'}launcher -PathType Leaf)) {
        # The installer may already have started AITA. Do not create a second launch.
        ${'$'}running = @(Get-Process -Name AITA -ErrorAction SilentlyContinue | Where-Object { try { ${'$'}_.Path -eq ${'$'}launcher } catch { ${'$'}false } })
        if (${'$'}running.Count -eq 0) { try { Start-Process -FilePath ${'$'}launcher -WindowStyle Normal } catch { Write-Output ${'$'}_.Exception.Message } }
    }
    Finish-UpdateWindow ${'$'}installed
    Remove-Item -LiteralPath ${'$'}PSCommandPath -ErrorAction SilentlyContinue
}
""".trimIndent()

internal suspend fun startWindowsUpdateHandoff(installer: File, hash: String, launcher: File, relaunch: Boolean = true, unattended: Boolean = true): Boolean {
    require(Regex("[a-f0-9]{64}").matches(hash))
    require(installer.extension.lowercase() in setOf("msi", "exe") && installer.absolutePath.none { it.code < 32 || it == '"' })
    clientInstallerDetailsState.value = null
    val root = windowsInstallerLogRoot()
    val identity = UUID.randomUUID().toString()
    val folder = File(root, identity).apply { check(mkdir()) }
    // A short, local source avoids redirected Downloads/network paths and survives app cleanup.
    val staged = File(folder, "AITA-update.${installer.extension.lowercase()}")
    if (folder.usableSpace < installer.length() + 16L * 1024 * 1024) throw ClientUpdateFailure("space")
    Files.copy(installer.toPath(), staged.toPath())
    val digest = MessageDigest.getInstance("SHA-256")
    staged.inputStream().use { stream ->
        val buffer = ByteArray(64 * 1024)
        while (true) { val count = stream.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
    }
    if (digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) } != hash) {
        staged.delete(); throw ClientUpdateFailure("integrity")
    }
    val script = File(folder, "handoff.ps1")
    val ready = File(folder, "handoff.ready")
    val active = File(folder, "handoff.active")
    val log = File(folder, "installer.log")
    val diagnostic = File(folder, "handoff.log")
    val result = File(folder, "result.txt")
    val lastResult = File(root, "last-result.txt")
    val messages = File(folder, "messages.json").apply {
        writeText(jsonBase.encodeToString(listOf("title", "logs", "close", "saving", "windows", "permission", "installing", "slow", "complete", "failed").associateWith(::windowsInstallerMessage)))
    }
    val powershell = File(System.getenv("SystemRoot") ?: "C:\\Windows", "System32/WindowsPowerShell/v1.0/powershell.exe")
    script.writeText(windowsUpdateScript)
    val process = try {
        ProcessBuilder(powershell.absolutePath, "-NoLogo", "-NoProfile", "-NonInteractive", "-STA", "-WindowStyle", "Hidden", "-ExecutionPolicy", "Bypass", "-File", script.absolutePath)
            .apply {
                // Rebuild Windows PowerShell's own default module path, rather than inheriting
                // incompatible PowerShell 7 module paths from the desktop launcher.
                environment().remove("PSModulePath")
                environment().putAll(mapOf("AITA_UPDATE_READY" to ready.absolutePath, "AITA_UPDATE_LAUNCHER" to launcher.absolutePath,
                    "AITA_UPDATE_INSTALLER" to staged.absolutePath, "AITA_UPDATE_PARENT" to ProcessHandle.current().pid().toString(),
                    "AITA_UPDATE_LOG" to log.absolutePath, "AITA_UPDATE_SHA256" to hash,
                    "AITA_UPDATE_ACTIVE" to active.absolutePath, "AITA_UPDATE_RESULT" to result.absolutePath,
                    "AITA_UPDATE_STAGED" to "true", "AITA_UPDATE_LAST_RESULT" to lastResult.absolutePath,
                    "AITA_UPDATE_LOCK" to File(root, "install.lock").absolutePath, "AITA_UPDATE_MESSAGES" to messages.absolutePath,
                    "AITA_UPDATE_RELAUNCH" to relaunch.toString(), "AITA_UPDATE_UNATTENDED" to unattended.toString()))
                redirectErrorStream(true); redirectOutput(diagnostic)
            }.start()
    } catch (error: Exception) {
        clientInstallerDetailsState.value = windowsInstallerMessage("failed") + "\n" + diagnostic.absolutePath
        script.delete(); throw error
    }
    repeat(300) {
        if (ready.isFile && process.isAlive) return true
        if (!process.isAlive) {
            val duplicate = runCatching { result.readText().contains("another_update_is_running") }.getOrDefault(false)
            clientInstallerDetailsState.value = windowsInstallerMessage(if (duplicate) "running" else "failed") + "\n" + diagnostic.absolutePath
            script.delete(); return false
        }
        delay(100)
    }
    process.destroy(); script.delete(); ready.delete()
    clientInstallerDetailsState.value = windowsInstallerMessage("failed") + "\n" + diagnostic.absolutePath
    return false
}

internal fun currentWindowsUpdateLauncher(): File? =
    windowsUpdateLauncher(System.getProperty("jpackage.app-path"))
        ?: windowsUpdateLauncher(ProcessHandle.current().info().command().orElse(null))

private fun windowsInstallerLogRoot(): File {
    val base = System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }?.let(::File)
        ?: File(System.getProperty("user.home"), ".aita")
    val root = File(base, "AITA/InstallerLogs")
    require(root.absoluteFile == root.canonicalFile)
    check(root.isDirectory || root.mkdirs())
    return root
}

internal fun windowsInstallerIsRunning(): Boolean {
    if (!System.getProperty("os.name").contains("win", true)) return false
    return try {
        val helper = windowsInstallerLogRoot().listFiles().orEmpty().filter { it.isDirectory }.any { directory ->
            val marker = File(directory, "handoff.active")
            marker.takeIf { it.isFile && it.length() < 32 }?.readText()?.trim()?.toLongOrNull()?.let {
                ProcessHandle.of(it).map { process -> process.isAlive }.orElse(false)
            } == true
        }
        helper || ProcessHandle.allProcesses().use { processes -> processes.anyMatch {
            it.info().command().orElse("").substringAfterLast('\\').equals("msiexec.exe", true)
        } }
    } catch (_: Exception) { true } // Inability to inspect is never permission to delete a live source.
}

internal fun restoreWindowsInstallerResult() {
    if (!System.getProperty("os.name").contains("win", true)) return
    runCatching {
        val result = File(windowsInstallerLogRoot(), "last-result.txt")
        if (result.isFile && result.length() < 16_384) {
            val text = result.readText()
            if (text.startsWith("failed:")) clientInstallerDetailsState.value = windowsInstallerMessage("failed") + "\n" + text.substringAfter("failed:").trim()
        }
    }
}

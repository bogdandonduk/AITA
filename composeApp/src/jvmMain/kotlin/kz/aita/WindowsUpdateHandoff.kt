package kz.aita

import java.io.File
import java.util.UUID
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.delay

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
function Get-AitaInstallerHash([string]${'$'}path) {
    # Windows PowerShell can inherit a PSModulePath from PowerShell 7 that hides Get-FileHash.
    # Use the built-in cryptographic runtime instead; verification remains mandatory twice.
    ${'$'}stream = [IO.File]::OpenRead(${'$'}path)
    ${'$'}sha = [Security.Cryptography.SHA256]::Create()
    try { return [BitConverter]::ToString(${'$'}sha.ComputeHash(${'$'}stream)).Replace('-', '').ToLowerInvariant() }
    finally { ${'$'}sha.Dispose(); ${'$'}stream.Dispose() }
}
try {
    if (-not (Test-Path -LiteralPath ${'$'}launcher -PathType Leaf)) { throw 'Installed AITA launcher is missing' }
    ${'$'}kind = [IO.Path]::GetExtension(${'$'}installer).ToLowerInvariant()
    if (${'$'}kind -notin @('.msi', '.exe')) { throw 'Unsupported Windows installer' }
    if ((Get-AitaInstallerHash ${'$'}installer) -ne ${'$'}env:AITA_UPDATE_SHA256) { throw 'Installer checksum mismatch' }
    [IO.File]::WriteAllText(${'$'}env:AITA_UPDATE_ACTIVE, [string]${'$'}PID)
    [IO.File]::WriteAllText(${'$'}ready, 'ready')
    ${'$'}parent = Get-Process -Id ${'$'}parentId -ErrorAction SilentlyContinue
    if (${'$'}parent -and -not ${'$'}parent.WaitForExit(120000)) { throw 'AITA did not finish saving; installation was cancelled' }
    ${'$'}parentExited = ${'$'}true
    if ((Get-AitaInstallerHash ${'$'}installer) -ne ${'$'}env:AITA_UPDATE_SHA256) { throw 'Installer changed after handoff' }
    # Another AITA window or native printer child may still hold runtime DLLs.
    # Wait for only the exact installed launcher; never kill a process or discard drafts.
    ${'$'}deadline = [DateTime]::UtcNow.AddSeconds(120)
    do {
        ${'$'}holders = @(Get-Process -Name AITA -ErrorAction SilentlyContinue | Where-Object {
            try { ${'$'}_.Path -eq ${'$'}launcher } catch { ${'$'}false }
        })
        if (${'$'}holders.Count -eq 0) { break }
        if ([DateTime]::UtcNow -ge ${'$'}deadline) { throw 'Close all AITA windows and retry the update' }
        Start-Sleep -Milliseconds 500
    } while (${'$'}true)
    # jpackage's MSI exposes INSTALLDIR. Preserve a user's chosen installation folder
    # so the verified existing launcher path remains the relaunch path after the upgrade.
    ${'$'}installDirectory = [IO.Path]::GetDirectoryName(${'$'}launcher)
    ${'$'}installArgument = if (${'$'}installDirectory -match '\s') { 'INSTALLDIR="' + ${'$'}installDirectory.TrimEnd('\') + '"' } else { 'INSTALLDIR=' + ${'$'}installDirectory }
    ${'$'}ui = if (${'$'}env:AITA_UPDATE_UNATTENDED -eq 'true') { ' /passive' } else { '' }
    if (${'$'}kind -eq '.msi') {
        ${'$'}arguments = '/i "' + ${'$'}installer + '" ' + ${'$'}installArgument + ${'$'}ui + ' /norestart /L*V "' + ${'$'}log + '"'
        ${'$'}result = Start-Process -FilePath (Join-Path ${'$'}env:SystemRoot 'System32\msiexec.exe') -ArgumentList ${'$'}arguments -Verb RunAs -Wait -PassThru
    } else {
        # EXE wrapper owns its wizard/UAC. It must also start only after AITA releases its DLLs.
        # jpackage's EXE forwards these arguments to its embedded MSI.
        ${'$'}arguments = ${'$'}installArgument + ${'$'}ui + ' /norestart /L*V "' + ${'$'}log + '"'
        ${'$'}result = Start-Process -FilePath ${'$'}installer -ArgumentList ${'$'}arguments -Verb RunAs -Wait -PassThru
    }
    if (${'$'}result.ExitCode -notin @(0, 3010)) { throw ('Windows Installer returned ' + ${'$'}result.ExitCode) }
    ${'$'}installed = ${'$'}true
    [IO.File]::WriteAllText(${'$'}env:AITA_UPDATE_RESULT, 'success')
    Write-Output 'AITA update installed. Starting the updated app.'
} catch {
    [IO.File]::WriteAllText(${'$'}env:AITA_UPDATE_RESULT, 'failed: ' + ${'$'}_.Exception.Message + [Environment]::NewLine + ${'$'}log)
    Write-Output ('AITA update stopped: ' + ${'$'}_.Exception.Message)
    Write-Output 'Installer and local work are retained. Review the installer log or retry from AITA.'
} finally {
    Remove-Item -LiteralPath ${'$'}env:AITA_UPDATE_ACTIVE -ErrorAction SilentlyContinue
    # Delete only our staged copy, after the EXE/MSI and its children have finished.
    if (${'$'}installed -and ${'$'}env:AITA_UPDATE_STAGED -eq 'true') { Remove-Item -LiteralPath ${'$'}installer -ErrorAction SilentlyContinue }
    # On cancellation/failure, reopen the installed version if Windows Installer retained it.
    if (${'$'}parentExited -and ${'$'}env:AITA_UPDATE_RELAUNCH -ne 'false' -and (Test-Path -LiteralPath ${'$'}launcher -PathType Leaf)) { Start-Process -FilePath ${'$'}launcher }
    Remove-Item -LiteralPath ${'$'}ready -ErrorAction SilentlyContinue
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
    val result = File(root, "last-result.txt")
    val powershell = File(System.getenv("SystemRoot") ?: "C:\\Windows", "System32/WindowsPowerShell/v1.0/powershell.exe")
    script.writeText(windowsUpdateScript)
    val process = try {
        ProcessBuilder(powershell.absolutePath, "-NoLogo", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-ExecutionPolicy", "Bypass", "-File", script.absolutePath)
            .apply {
                // Rebuild Windows PowerShell's own default module path, rather than inheriting
                // incompatible PowerShell 7 module paths from the desktop launcher.
                environment().remove("PSModulePath")
                environment().putAll(mapOf("AITA_UPDATE_READY" to ready.absolutePath, "AITA_UPDATE_LAUNCHER" to launcher.absolutePath,
                    "AITA_UPDATE_INSTALLER" to staged.absolutePath, "AITA_UPDATE_PARENT" to ProcessHandle.current().pid().toString(),
                    "AITA_UPDATE_LOG" to log.absolutePath, "AITA_UPDATE_SHA256" to hash,
                    "AITA_UPDATE_ACTIVE" to active.absolutePath, "AITA_UPDATE_RESULT" to result.absolutePath,
                    "AITA_UPDATE_STAGED" to "true",
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
            clientInstallerDetailsState.value = windowsInstallerMessage("failed") + "\n" + diagnostic.absolutePath
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

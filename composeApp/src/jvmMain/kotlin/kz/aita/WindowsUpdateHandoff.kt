package kz.aita

import java.io.File
import java.util.UUID
import kotlinx.coroutines.delay

internal fun windowsUpdateLauncher(path: String?): File? = path?.takeIf { it.none { c -> c.code < 32 || c == '"' } }
    ?.let(::File)?.takeIf { it.isAbsolute && it.isFile && it.name.equals("AITA.exe", true) }

/** The signed feed authenticates the MSI even during the explicitly unsigned Windows pilot.
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
    if ([IO.Path]::GetExtension(${'$'}installer) -ne '.msi') { throw 'Automatic update requires MSI' }
    if ((Get-AitaInstallerHash ${'$'}installer) -ne ${'$'}env:AITA_UPDATE_SHA256) { throw 'Installer checksum mismatch' }
    [IO.File]::WriteAllText(${'$'}ready, 'ready')
    ${'$'}parent = Get-Process -Id ${'$'}parentId -ErrorAction SilentlyContinue
    if (${'$'}parent -and -not ${'$'}parent.WaitForExit(120000)) { throw 'AITA did not finish saving; installation was cancelled' }
    ${'$'}parentExited = ${'$'}true
    if ((Get-AitaInstallerHash ${'$'}installer) -ne ${'$'}env:AITA_UPDATE_SHA256) { throw 'Installer changed after handoff' }
    ${'$'}arguments = '/i "' + ${'$'}installer + '" /passive /norestart /L*V "' + ${'$'}log + '"'
    ${'$'}result = Start-Process -FilePath (Join-Path ${'$'}env:SystemRoot 'System32\msiexec.exe') -ArgumentList ${'$'}arguments -Verb RunAs -Wait -PassThru
    if (${'$'}result.ExitCode -notin @(0, 3010)) { throw ('Windows Installer returned ' + ${'$'}result.ExitCode) }
    Write-Output 'AITA update installed. Starting the updated app.'
} catch {
    Write-Output ('AITA update stopped: ' + ${'$'}_.Exception.Message)
    Write-Output 'Installer and local work are retained. Review the installer log or retry from AITA.'
} finally {
    # On cancellation/failure, reopen the installed version if Windows Installer retained it.
    if (${'$'}parentExited -and (Test-Path -LiteralPath ${'$'}launcher -PathType Leaf)) { Start-Process -FilePath ${'$'}launcher }
    Remove-Item -LiteralPath ${'$'}ready -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath ${'$'}PSCommandPath -ErrorAction SilentlyContinue
}
""".trimIndent()

internal suspend fun startWindowsUpdateHandoff(installer: File, hash: String, launcher: File): Boolean {
    require(Regex("[a-f0-9]{64}").matches(hash))
    require(installer.extension.equals("msi", true) && installer.absolutePath.none { it.code < 32 || it == '"' })
    val folder = installer.parentFile
    val identity = UUID.randomUUID().toString()
    val script = File(folder, "handoff-$identity.ps1")
    val ready = File(folder, "handoff-$identity.ready")
    val log = File(folder, "install-$identity.log")
    val powershell = File(System.getenv("SystemRoot") ?: "C:\\Windows", "System32/WindowsPowerShell/v1.0/powershell.exe")
    script.writeText(windowsUpdateScript)
    val process = try {
        ProcessBuilder(powershell.absolutePath, "-NoLogo", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", script.absolutePath)
            .apply {
                environment().putAll(mapOf("AITA_UPDATE_READY" to ready.absolutePath, "AITA_UPDATE_LAUNCHER" to launcher.absolutePath,
                    "AITA_UPDATE_INSTALLER" to installer.absolutePath, "AITA_UPDATE_PARENT" to ProcessHandle.current().pid().toString(),
                    "AITA_UPDATE_LOG" to log.absolutePath, "AITA_UPDATE_SHA256" to hash))
                redirectErrorStream(true); redirectOutput(File(folder, "handoff-$identity.log"))
            }.start()
    } catch (error: Exception) { script.delete(); throw error }
    repeat(150) {
        if (ready.isFile && process.isAlive) return true
        if (!process.isAlive) { script.delete(); return false }
        delay(100)
    }
    process.destroy(); script.delete(); ready.delete()
    return false
}

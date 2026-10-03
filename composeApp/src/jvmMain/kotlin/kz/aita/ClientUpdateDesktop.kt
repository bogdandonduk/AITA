package kz.aita

import kz.aita.updates.*
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

private fun desktopUpdates(path: String = ".aita/client-updates") = ManagedClientInstaller(File(System.getProperty("user.home"), path)) { from, to ->
    try { Files.move(from.toPath(),to.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) }
    catch (_: java.nio.file.AtomicMoveNotSupportedException) { Files.move(from.toPath(),to.toPath(), StandardCopyOption.REPLACE_EXISTING) }
    Unit
}
internal actual fun installedClientBuild() = GeneratedClientBuild.identity
internal actual fun clientUpdatePlatform(): ClientPlatform {
    val name = System.getProperty("os.name").orEmpty(); val version = System.getProperty("os.version").orEmpty()
    val os = when { name.contains("win",true) -> ClientOs.WINDOWS; name.contains("mac",true) -> ClientOs.MACOS; else -> ClientOs.LINUX }
    val linuxPackage = if (os == ClientOs.LINUX) runCatching {
        // Only OS metadata is read. Do not guess a Debian package on a different distribution.
        val file = File("/etc/os-release")
        val values = if(file.isFile && file.length()<16384) file.readLines().filter { it.startsWith("ID=") || it.startsWith("ID_LIKE=") }
            .joinToString(" ") { it.substringAfter('=').trim('"').lowercase() }.split(Regex("\\s+")) else emptyList()
        when {
            values.any { it in setOf("debian","ubuntu","linuxmint","pop","elementary") } -> InstallerKind.DEB
            values.any { it in setOf("rhel","fedora","centos","rocky","almalinux","suse","opensuse") } -> InstallerKind.RPM
            else -> null
        }
    }.getOrNull() else null
    return ClientPlatform(when { name.contains("win",true) -> ClientOs.WINDOWS; name.contains("mac",true) -> ClientOs.MACOS; else -> ClientOs.LINUX },
        when (System.getProperty("os.arch").orEmpty().lowercase()) { "aarch64", "arm64" -> ClientArch.ARM64; "x86_64", "amd64", "x64" -> ClientArch.X64; else -> ClientArch.UNIVERSAL },
        version.substringBefore('.').toIntOrNull() ?: 0, "$name $version", GeneratedClientBuild.identity.distribution == "store", linuxPackage,
        if (os == ClientOs.WINDOWS && desktopUpdates().readPreference("windows-installer") == "MSI") InstallerKind.MSI else InstallerKind.EXE)
}
internal actual suspend fun verifyClientReleaseSignature(payload: ByteArray, signature: ByteArray, publicKey: ByteArray) =
    withContext(Dispatchers.IO) { verifyRsaClientRelease(payload,signature,publicKey) }
internal actual suspend fun readClientUpdatePreference(key: String) = withContext(Dispatchers.IO) { desktopUpdates().readPreference(key) }
internal actual suspend fun writeClientUpdatePreference(key: String, value: String?) = withContext(Dispatchers.IO) { desktopUpdates().writePreference(key,value) }
internal actual suspend fun prepareClientInstaller(release: ClientRelease, artifact: ClientArtifact, progress: (Long,Long)->Unit) = withContext(Dispatchers.IO) { desktopUpdates().prepare(release,artifact,progress) }
internal actual suspend fun restoreClientInstaller(release: ClientRelease, artifact: ClientArtifact) = withContext(Dispatchers.IO) { desktopUpdates().restore(release,artifact) }
internal actual suspend fun cleanCompletedClientInstallers(installed: ClientBuildIdentity) {
    restoreWindowsInstallerResult()
    suspend fun clean() {
        desktopUpdates().clean(installed); desktopDownloadInstallers().clean(installed)
        InstalledDownloadCleanup(desktopDownloadInstallers()).clean(installed, ::removeOwnedInstallerFile)
    }
    withContext(Dispatchers.IO) {
        if (!windowsInstallerIsRunning()) clean()
        else kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO).launch {
            // A manually started installer can open AITA before its final MSI action ends.
            // Keep both its source and public download until Windows releases the operation.
            repeat(60) {
                kotlinx.coroutines.delay(30_000)
                if (!windowsInstallerIsRunning()) { clean(); return@launch }
            }
        }
    }
}
internal actual suspend fun handoffClientUpdate(release: ClientRelease, artifact: ClientArtifact, prepared: PreparedClientInstaller?): UpdateHandoff = withContext(Dispatchers.IO) {
    if (artifact != selectClientArtifact(release,clientUpdatePlatform()) || !clientReleaseIsNewer(release,installedClientBuild())) throw ClientUpdateFailure("integrity")
    if (!artifact.isFile) {
        if (artifact.kind != InstallerKind.APP_STORE) throw ClientUpdateFailure("unsupported")
        Desktop.getDesktop().browse(URI(artifact.url)); return@withContext UpdateHandoff.STORE_OPENED
    }
    openDesktopInstaller(release, artifact, prepared, desktopUpdates())
}
internal fun desktopDownloadInstallers() = desktopUpdates(".aita/download-installs")
internal actual fun clientInstallerPermissionGranted() = true
internal actual suspend fun handoffClientDownload(request: ClientDownloadInstallRequest, prepared: PreparedClientInstaller): UpdateHandoff = withContext(Dispatchers.IO) {
    val release = request.release(System.currentTimeMillis(), clientUpdatePlatform(), installedClientBuild())
    openDesktopInstaller(release, release.artifacts.single(), prepared, desktopDownloadInstallers())
}
private suspend fun openDesktopInstaller(release: ClientRelease, artifact: ClientArtifact,
    prepared: PreparedClientInstaller?, storage: ManagedClientInstaller): UpdateHandoff {
    if (prepared == null || prepared.build != release.build || prepared.releaseId != release.id || prepared.channel != release.channel) throw ClientUpdateFailure("integrity")
    val file = storage.verifiedFile(prepared, artifact)
    when (clientUpdatePlatform().os) {
        ClientOs.WINDOWS -> {
            val launcher = currentWindowsUpdateLauncher()
            if (artifact.kind in setOf(InstallerKind.MSI, InstallerKind.EXE) && launcher != null) {
                withContext(Dispatchers.Main) { AppStateWorkspace.flush() }
                flushCartsBeforeClientUpdate()
                if (!startWindowsUpdateHandoff(file, artifact.sha256, launcher, unattended = clientReleaseIsNewer(release, installedClientBuild()))) throw ClientUpdateFailure("install")
                // Helper has verified the file and is waiting for this exact process to exit.
                // The new process acknowledges the build before ManagedClientInstaller cleans up.
                kotlin.system.exitProcess(0)
            } else {
                clientInstallerDetailsState.value = windowsInstallerMessage("launcher")
                throw ClientUpdateFailure("install")
            }
        }
        ClientOs.MACOS -> ProcessBuilder("/usr/bin/open", file.absolutePath).start()
        ClientOs.LINUX -> {
            withContext(Dispatchers.Main) { AppStateWorkspace.flush() }
            flushCartsBeforeClientUpdate()
            if (!startLinuxUpdateHandoff(file, artifact.sha256, artifact.kind)) throw ClientUpdateFailure("install")
            kotlin.system.exitProcess(0)
        }
        else -> throw ClientUpdateFailure("unsupported")
    }
    // Installer exit or wizard launch is not installation acknowledgement. Do not delete the file yet.
    return UpdateHandoff.INSTALLER_OPENED
}

/** A normal window close is a safe opportunity to apply an already verified download.
 * Keep the app closed afterward. Nothing is downloaded here, and failed handoff retains files. */
internal suspend fun installPreparedWindowsUpdateOnExit(): Boolean = withContext(Dispatchers.IO) {
    val state = AppUpdateWorkspace.state.value
    val platform = state.platform ?: return@withContext false
    val release = state.available ?: return@withContext false
    val artifact = state.artifact ?: return@withContext false
    val prepared = state.prepared ?: return@withContext false
    if (!state.backgroundDownloads || !state.configured || platform.os != ClientOs.WINDOWS ||
        state.busy || artifact.kind !in setOf(InstallerKind.MSI, InstallerKind.EXE) || artifact != selectClientArtifact(release, platform) ||
        clientReleaseProblem(release, System.currentTimeMillis(), installedClientBuild().channel) != null ||
        !clientReleaseIsNewer(release, installedClientBuild())) return@withContext false
    val launcher = currentWindowsUpdateLauncher() ?: return@withContext false
    if (prepared.build != release.build || prepared.releaseId != release.id || prepared.channel != release.channel)
        throw ClientUpdateFailure("integrity")
    val file = desktopUpdates().verifiedFile(prepared, artifact)
    startWindowsUpdateHandoff(file, artifact.sha256, launcher, relaunch = false)
}

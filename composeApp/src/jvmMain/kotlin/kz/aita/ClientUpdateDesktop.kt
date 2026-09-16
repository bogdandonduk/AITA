package kz.aita

import kz.aita.updates.*
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun desktopUpdates() = ManagedClientInstaller(File(System.getProperty("user.home"), ".aita/client-updates")) { from, to ->
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
        version.substringBefore('.').toIntOrNull() ?: 0, "$name $version", GeneratedClientBuild.identity.distribution == "store", linuxPackage)
}
internal actual suspend fun verifyClientReleaseSignature(payload: ByteArray, signature: ByteArray, publicKey: ByteArray) =
    withContext(Dispatchers.IO) { verifyRsaClientRelease(payload,signature,publicKey) }
internal actual suspend fun readClientUpdatePreference(key: String) = withContext(Dispatchers.IO) { desktopUpdates().readPreference(key) }
internal actual suspend fun writeClientUpdatePreference(key: String, value: String?) = withContext(Dispatchers.IO) { desktopUpdates().writePreference(key,value) }
internal actual suspend fun prepareClientInstaller(release: ClientRelease, artifact: ClientArtifact, progress: (Long,Long)->Unit) = withContext(Dispatchers.IO) { desktopUpdates().prepare(release,artifact,progress) }
internal actual suspend fun restoreClientInstaller(release: ClientRelease, artifact: ClientArtifact) = withContext(Dispatchers.IO) { desktopUpdates().restore(release,artifact) }
internal actual suspend fun cleanCompletedClientInstallers(installed: ClientBuildIdentity) = withContext(Dispatchers.IO) { desktopUpdates().clean(installed) }
internal actual suspend fun handoffClientUpdate(release: ClientRelease, artifact: ClientArtifact, prepared: PreparedClientInstaller?): UpdateHandoff = withContext(Dispatchers.IO) {
    if (artifact != selectClientArtifact(release,clientUpdatePlatform()) || !clientReleaseIsNewer(release,installedClientBuild())) throw ClientUpdateFailure("integrity")
    if (!artifact.isFile) {
        if (artifact.kind != InstallerKind.APP_STORE) throw ClientUpdateFailure("unsupported")
        Desktop.getDesktop().browse(URI(artifact.url)); return@withContext UpdateHandoff.STORE_OPENED
    }
    if (prepared == null || prepared.build != release.build || prepared.releaseId != release.id || prepared.channel != release.channel) throw ClientUpdateFailure("integrity")
    val file = desktopUpdates().verifiedFile(prepared, artifact)
    when (clientUpdatePlatform().os) {
        ClientOs.WINDOWS -> if (artifact.kind == InstallerKind.MSI) ProcessBuilder("msiexec.exe", "/i", file.absolutePath, "/norestart").start() else ProcessBuilder(file.absolutePath).start()
        ClientOs.MACOS -> ProcessBuilder("/usr/bin/open", file.absolutePath).start()
        ClientOs.LINUX -> ProcessBuilder("xdg-open", file.absolutePath).start()
        else -> throw ClientUpdateFailure("unsupported")
    }
    // Installer exit or wizard launch is not installation acknowledgement. Do not delete the file yet.
    UpdateHandoff.INSTALLER_OPENED
}

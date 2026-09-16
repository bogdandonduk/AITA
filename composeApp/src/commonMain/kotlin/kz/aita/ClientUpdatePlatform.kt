package kz.aita

import kz.aita.updates.*
import kotlinx.serialization.Serializable

internal class ClientUpdateFailure(val reason: String) : Exception(reason)
@Serializable
internal data class PreparedClientInstaller(val releaseId: String, val build: Long, val channel: ReleaseChannel,
    val fileName: String, val sha256: String, val bytes: Long, val createdAtMillis: Long)
internal enum class UpdateHandoff { INSTALLER_OPENED, STORE_OPENED, RELOADING, PERMISSION_REQUIRED }

internal expect fun clientUpdatePlatform(): ClientPlatform
internal expect fun installedClientBuild(): ClientBuildIdentity
internal expect suspend fun verifyClientReleaseSignature(payload: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean
internal expect suspend fun readClientUpdatePreference(key: String): String?
internal expect suspend fun writeClientUpdatePreference(key: String, value: String?)
internal expect suspend fun prepareClientInstaller(release: ClientRelease, artifact: ClientArtifact,
    progress: (Long, Long) -> Unit): PreparedClientInstaller
internal expect suspend fun restoreClientInstaller(release: ClientRelease, artifact: ClientArtifact): PreparedClientInstaller?
internal expect suspend fun handoffClientUpdate(release: ClientRelease, artifact: ClientArtifact,
    prepared: PreparedClientInstaller?): UpdateHandoff
internal expect suspend fun cleanCompletedClientInstallers(installed: ClientBuildIdentity)

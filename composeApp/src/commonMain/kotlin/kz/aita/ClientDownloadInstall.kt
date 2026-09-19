package kz.aita

import kz.aita.updates.*

/** The exact file and version must still belong to the authenticated, unexpired catalogue. */
internal data class ClientDownloadInstallRequest(val catalog: ClientRelease,
    val version: ClientDownloadVersion, val file: ClientDownloadFile) {
    fun release(now: Long, platform: ClientPlatform, installed: ClientBuildIdentity): ClientRelease {
        if (clientReleaseProblem(catalog, now, installed.channel) != null ||
            verifiedDownloadVersions(catalog)?.any { it == version && file in it.files } != true ||
            clientDownloadAction(version, file, platform, installed) == ClientDownloadAction.DOWNLOAD)
            throw ClientUpdateFailure("integrity")
        val artifact = ClientArtifact(ClientOs.valueOf(file.os), ClientArch.valueOf(file.arch),
            InstallerKind.valueOf(file.kind), file.url, file.bytes, file.sha256,
            minimumOsMajor = if (file.os == "ANDROID") 24 else 10)
        return catalog.copy(id = version.id, version = version.version, build = version.build,
            notes = version.notes, artifacts = listOf(artifact), downloads = emptyList())
    }
}

internal expect suspend fun handoffClientDownload(request: ClientDownloadInstallRequest,
    prepared: PreparedClientInstaller): UpdateHandoff
internal expect fun clientInstallerPermissionGranted(): Boolean

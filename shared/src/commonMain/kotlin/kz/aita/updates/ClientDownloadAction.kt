package kz.aita.updates

enum class ClientDownloadAction { DOWNLOAD, UPDATE, INSTALL }

/** Explicit historical installation is separate from the automatic updater's high-water mark. */
fun clientDownloadAction(version: ClientDownloadVersion, file: ClientDownloadFile,
    platform: ClientPlatform, installed: ClientBuildIdentity): ClientDownloadAction {
    val compatible = !platform.storeManaged && file.os == platform.os.name &&
        (file.arch == platform.arch.name || file.arch == ClientArch.UNIVERSAL.name) && when (platform.os) {
            ClientOs.ANDROID -> file.kind == "APK" && platform.osMajor >= 24
            ClientOs.WINDOWS -> file.kind in setOf("EXE", "MSI") && platform.osMajor >= 10
            else -> false
        }
    if (!compatible) return ClientDownloadAction.DOWNLOAD
    return if (version.build > installed.build && compareClientVersions(version.version, installed.version) >= 0)
        ClientDownloadAction.UPDATE else ClientDownloadAction.INSTALL
}

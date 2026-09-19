package kz.aita.updates

import kotlin.test.*

class ClientDownloadActionTest {
    private val installed = ClientBuildIdentity("1.0.9", 10, ReleaseChannel.RELEASE, "", "", "direct", "", "")
    private val apk = ClientDownloadFile("ANDROID", kind = "APK", url = "https://example.org/app.apk", bytes = 1, sha256 = "a".repeat(64))
    private val version = ClientDownloadVersion("r11", "1.1.0", 11, files = listOf(apk))
    private val android = ClientPlatform(ClientOs.ANDROID, ClientArch.ARM64, 35)
    @Test fun compatibleNewReleaseUpdatesInOneAction() {
        assertEquals(ClientDownloadAction.UPDATE, clientDownloadAction(version, apk, android, installed))
        val msi = apk.copy(os = "WINDOWS", arch = "X64", kind = "MSI")
        assertEquals(ClientDownloadAction.UPDATE, clientDownloadAction(version, msi, ClientPlatform(ClientOs.WINDOWS, ClientArch.X64, 10), installed))
    }
    @Test fun HistoricalAndSameVersionInstallExplicitly() {
        assertEquals(ClientDownloadAction.INSTALL, clientDownloadAction(version.copy(build = 9, version = "1.0.8"), apk, android, installed))
        assertEquals(ClientDownloadAction.INSTALL, clientDownloadAction(version.copy(build = 10, version = "1.0.9"), apk, android, installed))
    }
    @Test fun foreignPlatformArchitectureStoreOrBundleOnlyDownloads() {
        assertEquals(ClientDownloadAction.DOWNLOAD, clientDownloadAction(version, apk, ClientPlatform(ClientOs.WEB, ClientArch.UNIVERSAL), installed))
        assertEquals(ClientDownloadAction.DOWNLOAD, clientDownloadAction(version, apk.copy(kind = "AAB"), android, installed))
        assertEquals(ClientDownloadAction.DOWNLOAD, clientDownloadAction(version, apk, android.copy(storeManaged = true), installed))
        assertEquals(ClientDownloadAction.DOWNLOAD, clientDownloadAction(version, apk.copy(arch = "X64"), android, installed))
        assertEquals(ClientDownloadAction.DOWNLOAD, clientDownloadAction(version, apk, android.copy(osMajor = 23), installed))
    }
}

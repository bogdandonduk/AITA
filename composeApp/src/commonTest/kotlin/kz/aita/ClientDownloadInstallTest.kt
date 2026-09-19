package kz.aita

import kz.aita.updates.*
import kotlin.test.*

class ClientDownloadInstallTest {
    private val file = ClientDownloadFile("ANDROID", kind = "APK", url = "https://example.org/app.apk", bytes = 2, sha256 = "a".repeat(64))
    private val version = ClientDownloadVersion("r9", "1.0.8", 9, files = listOf(file))
    private val catalog = ClientRelease(channel = ReleaseChannel.RELEASE, sequence = 11, id = "r11", version = "1.1.0", build = 11,
        publishedAtMillis = 1000, expiresAtMillis = 100000, artifacts = listOf(ClientArtifact(ClientOs.ANDROID, kind = InstallerKind.APK,
            url = file.url, bytes = file.bytes, sha256 = file.sha256)), downloads = listOf(version))
    private val installed = ClientBuildIdentity("1.0.9", 10, ReleaseChannel.RELEASE, "", "", "direct", "", "")
    private val platform = ClientPlatform(ClientOs.ANDROID, ClientArch.ARM64, 35)
    @Test fun historicalInstallerRetainsAuthenticatedIdentityWithoutReplacingCatalog() {
        val target = ClientDownloadInstallRequest(catalog, version, file).release(2000, platform, installed)
        assertEquals(9L, target.build); assertEquals("r9", target.id); assertEquals(file.sha256, target.artifacts.single().sha256)
        assertFalse(clientReleaseIsNewer(target, installed)); assertEquals(11L, catalog.build)
    }
    @Test fun alteredFileVersionExpiredCatalogOrWrongDeviceCannotLaunch() {
        val request = ClientDownloadInstallRequest(catalog, version, file)
        assertFailsWith<ClientUpdateFailure> { request.copy(file = file.copy(sha256 = "b".repeat(64))).release(2000, platform, installed) }
        assertFailsWith<ClientUpdateFailure> { request.copy(version = version.copy(build = 8)).release(2000, platform, installed) }
        assertFailsWith<ClientUpdateFailure> { request.release(100001, platform, installed) }
        assertFailsWith<ClientUpdateFailure> { request.release(2000, platform.copy(os = ClientOs.WEB), installed) }
    }
}

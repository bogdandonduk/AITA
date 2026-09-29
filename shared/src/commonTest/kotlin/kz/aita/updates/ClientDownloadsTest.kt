package kz.aita.updates

import kotlin.test.*

class ClientDownloadsTest {
    private val apk = ClientArtifact(ClientOs.ANDROID, kind = InstallerKind.APK, url = "https://updates.example.org/app.apk", bytes = 3, sha256 = "a".repeat(64))
    private val release = ClientRelease(channel = ReleaseChannel.RELEASE, sequence = 4, id = "r4", version = "1.0.4", build = 4,
        publishedAtMillis = 1000, expiresAtMillis = 100000, artifacts = listOf(apk))
    private val aab = ClientDownloadFile("ANDROID", kind = "AAB", url = "https://updates.example.org/app.aab", bytes = 3, sha256 = "b".repeat(64), publisherSigned = true)
    @Test fun aabIsDownloadOnlyAndOlderUpdatersStillReceiveTheirApk() {
        val catalogue = release.copy(downloads = listOf(ClientDownloadVersion("r4", "1.0.4", 4, files = listOf(aab))))
        assertEquals("AAB", verifiedDownloadVersions(catalogue)!!.single().files.single().kind)
        assertEquals(apk, selectClientArtifact(catalogue, ClientPlatform(ClientOs.ANDROID, ClientArch.UNIVERSAL, 36)))
        assertNull(clientReleaseProblem(catalogue, 2000, ReleaseChannel.RELEASE))
    }
    @Test fun existingSignedFeedsStillProvideLatestDownloadsWithoutAnIndex() {
        assertEquals("APK", verifiedDownloadVersions(release)!!.single().files.single().kind)
        assertEquals(4L, verifiedDownloadVersions(release)!!.single().build)
    }
    @Test fun forgedTargetsUnsafeUrlsAndFutureVersionsAreRejected() {
        fun versions(file: ClientDownloadFile = aab, build: Long = 4) = release.copy(downloads = listOf(ClientDownloadVersion("r4", "1.0.4", build, files = listOf(file))))
        assertNull(verifiedDownloadVersions(versions(aab.copy(url = "http://updates.example.org/app.aab"))))
        assertNull(verifiedDownloadVersions(versions(aab.copy(os = "WINDOWS"))))
        assertNull(verifiedDownloadVersions(versions(aab.copy(sha256 = "bad"))))
        assertNull(verifiedDownloadVersions(versions(build = 5)))
    }
    @Test fun previousSignedVersionsRemainOrderedAndDoNotChangeInstalledUpdateSelection() {
        val current = ClientDownloadVersion("r4", "1.0.4", 4, files = listOf(aab))
        val previous = current.copy(id = "r3", version = "1.0.3", build = 3)
        val catalogue = release.copy(downloads = listOf(previous, current))
        assertEquals(listOf(4L, 3L), verifiedDownloadVersions(catalogue)!!.map { it.build })
        assertEquals("AITA-1.0.4-4-android-universal.aab", clientDownloadFileName(current, aab))
        assertEquals(apk, selectClientArtifact(catalogue, ClientPlatform(ClientOs.ANDROID, ClientArch.UNIVERSAL, 36)))
    }
    @Test fun linuxIsAdditiveAndCannotReplaceLegacyDownloads() {
        val deb = ClientDownloadFile("LINUX", "X64", "DEB", "https://updates.example.org/app.deb", 4, "d".repeat(64), false)
        val linux = ClientDownloadVersion("r4", "1.0.4", 4, files = listOf(deb))
        val catalogue = release.copy(linuxDownloads = listOf(linux))
        assertEquals(listOf("APK", "DEB"), verifiedDownloadVersions(catalogue)!!.single().files.map { it.kind })
        assertNull(verifiedDownloadVersions(catalogue.copy(linuxDownloads = listOf(linux.copy(version = "1.0.3")))))
        assertNull(verifiedDownloadVersions(catalogue.copy(linuxDownloads = listOf(linux.copy(files = listOf(aab))))))
        val onlyLinux = catalogue.copy(artifacts = listOf(ClientArtifact(ClientOs.LINUX, ClientArch.X64, InstallerKind.DEB,
            url = deb.url, bytes = deb.bytes, sha256 = deb.sha256)))
        assertEquals(listOf(deb), verifiedDownloadVersions(onlyLinux)!!.single().files)
    }
}

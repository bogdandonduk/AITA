package kz.aita.updates

import kotlin.test.*

class ClientReleasePolicyTest {
    private val now = 1_789_530_000_000L
    private fun installed(channel: ReleaseChannel = ReleaseChannel.RELEASE, build: Long = 3) =
        ClientBuildIdentity("1.2.0",build,channel,"revision","date","direct","2.2.21","1.9.2")
    private fun artifact(os: ClientOs = ClientOs.ANDROID, kind: InstallerKind = InstallerKind.APK, arch: ClientArch = ClientArch.UNIVERSAL) =
        ClientArtifact(os,arch,kind,"https://updates.example.org/artifacts/"+"a".repeat(64)+"."+kind.name.lowercase(),100,"a".repeat(64))
    private fun release(channel: ReleaseChannel = ReleaseChannel.RELEASE, artifacts: List<ClientArtifact> = listOf(artifact())) =
        ClientRelease(channel=channel,sequence=4,id="release-4",version="1.3.0",build=4,publishedAtMillis=now-1000,expiresAtMillis=now+86400000,notes=mapOf("en" to "Updated"),artifacts=artifacts)

    @Test fun validNewBuildIsOffered() { val r=release();assertNull(clientReleaseProblem(r,now,ReleaseChannel.RELEASE));assertTrue(clientReleaseIsNewer(r,installed())) }
    @Test fun equalOrOlderBuildIsNotOffered() { assertFalse(clientReleaseIsNewer(release().copy(build=3),installed()));assertFalse(clientReleaseIsNewer(release().copy(build=2),installed())) }
    @Test fun testChannelNeverLeaksIntoRelease() { assertFalse(clientReleaseIsNewer(release(ReleaseChannel.TEST),installed()));assertEquals("channel",clientReleaseProblem(release(ReleaseChannel.TEST),now,ReleaseChannel.RELEASE)) }
    @Test fun lowerMarketingVersionIsNotAnUpgrade() { assertFalse(clientReleaseIsNewer(release().copy(version="1.1.99"),installed())) }
    @Test fun versionNumbersCompareNumerically() { assertTrue(compareClientVersions("1.10.0","1.9.0")>0);assertEquals(0,compareClientVersions("1.2.3","1.2.3")) }
    @Test fun expiryAndFuturePublicationAreRejected() {
        assertEquals("expired",clientReleaseProblem(release().copy(expiresAtMillis=now),now,ReleaseChannel.RELEASE))
        assertEquals("expired",clientReleaseProblem(release().copy(publishedAtMillis=now+600001),now,ReleaseChannel.RELEASE))
    }
    @Test fun unknownSchemaAndOversizedNotesAreRejected() {
        assertEquals("channel",clientReleaseProblem(release().copy(schema=2),now,ReleaseChannel.RELEASE))
        assertEquals("notes",clientReleaseProblem(release().copy(notes=mapOf("en" to "a".repeat(24001))),now,ReleaseChannel.RELEASE))
    }
    @Test fun duplicateTargetsCannotAmbiguouslySelect() { val a=artifact();assertEquals("artifacts",clientReleaseProblem(release(artifacts=listOf(a,a)),now,ReleaseChannel.RELEASE)) }
    @Test fun installersRequireExactHashAndSize() {
        for(a in listOf(artifact().copy(bytes=0),artifact().copy(bytes=CLIENT_INSTALLER_MAX_BYTES+1),artifact().copy(sha256="x")))
            assertEquals("artifact",clientReleaseProblem(release(artifacts=listOf(a)),now,ReleaseChannel.RELEASE))
    }
    @Test fun unsafeUrlsAreRejected() {
        for(url in listOf("http://example.org/x.apk","https://127.0.0.1/x.apk","https://[::1]/x.apk","https://localhost/x.apk","https://a.local/x.apk","https://user:pass@example.org/x.apk","https://example.org:444/x.apk","https://example.org/x.apk#x","https://example.org/\\x.apk"))
            assertFalse(isPublicClientReleaseUrl(url),url)
    }
    @Test fun exactArchitectureWinsOverUniversal() {
        val universal=artifact(ClientOs.MACOS,InstallerKind.PKG);val arm=universal.copy(arch=ClientArch.ARM64)
        assertEquals(arm,selectClientArtifact(release(artifacts=listOf(universal,arm)),ClientPlatform(ClientOs.MACOS,ClientArch.ARM64)))
        assertEquals(universal,selectClientArtifact(release(artifacts=listOf(universal,arm)),ClientPlatform(ClientOs.MACOS,ClientArch.X64)))
    }
    @Test fun noArtifactForWrongOsOrMinimumVersion() {
        assertNull(selectClientArtifact(release(),ClientPlatform(ClientOs.WINDOWS,ClientArch.X64)))
        assertNull(selectClientArtifact(release(artifacts=listOf(artifact().copy(minimumOsMajor=34))),ClientPlatform(ClientOs.ANDROID,ClientArch.ARM64,33)))
    }
    @Test fun playStoreInstallationNeverGetsAnApk() {
        val store=ClientArtifact(ClientOs.ANDROID,kind=InstallerKind.PLAY_STORE,url="https://play.google.com/store/apps/details?id=kz.aita")
        val r=release(artifacts=listOf(artifact(),store))
        assertEquals(store,selectClientArtifact(r,ClientPlatform(ClientOs.ANDROID,ClientArch.ARM64,storeManaged=true)))
        assertEquals(InstallerKind.APK,selectClientArtifact(r,ClientPlatform(ClientOs.ANDROID,ClientArch.ARM64))?.kind)
    }
    @Test fun iosChannelSelectsItsStore() {
        val app=ClientArtifact(ClientOs.IOS,kind=InstallerKind.APP_STORE,url="https://apps.apple.com/app/id123456789")
        val test=ClientArtifact(ClientOs.IOS,kind=InstallerKind.TESTFLIGHT,url="https://testflight.apple.com/join/Ab12")
        val platform=ClientPlatform(ClientOs.IOS,ClientArch.ARM64)
        assertEquals(app,selectClientArtifact(release(artifacts=listOf(app,test)),platform))
        assertEquals(test,selectClientArtifact(release(ReleaseChannel.TEST,listOf(app,test)),platform))
    }
    @Test fun wrongStoreHostOrPlayPackageIsRejected() {
        val a=ClientArtifact(ClientOs.ANDROID,kind=InstallerKind.PLAY_STORE,url="https://play.google.com/store/apps/details?id=other.app")
        assertEquals("store",clientReleaseProblem(release(artifacts=listOf(a)),now,ReleaseChannel.RELEASE))
    }
    @Test fun linuxDoesNotGuessAnotherPackageManager() {
        val deb=artifact(ClientOs.LINUX,InstallerKind.DEB);val rpm=artifact(ClientOs.LINUX,InstallerKind.RPM);val r=release(artifacts=listOf(deb,rpm))
        assertEquals(deb,selectClientArtifact(r,ClientPlatform(ClientOs.LINUX,ClientArch.X64,linuxPackage=InstallerKind.DEB)))
        assertEquals(rpm,selectClientArtifact(r,ClientPlatform(ClientOs.LINUX,ClientArch.X64,linuxPackage=InstallerKind.RPM)))
        assertNull(selectClientArtifact(r,ClientPlatform(ClientOs.LINUX,ClientArch.X64)))
    }
    @Test fun installerLaunchIsNotAnInstalledBuild() {
        assertFalse(clientUpdateInstalled(4,ReleaseChannel.RELEASE,installed()))
        assertTrue(clientUpdateInstalled(4,ReleaseChannel.RELEASE,installed(build=4)))
        assertFalse(clientUpdateInstalled(4,ReleaseChannel.TEST,installed(build=4)))
    }
    @Test fun rollbackAndSequenceCollisionAreRejected() {
        val accepted=VerifiedClientRelease(release(),"e","p")
        assertFalse(isClientReleaseRollback(accepted,accepted))
        assertTrue(isClientReleaseRollback(accepted.copy(payload="other"),accepted))
        assertTrue(isClientReleaseRollback(accepted.copy(release=release().copy(sequence=3)),accepted))
        assertTrue(isClientReleaseRollback(accepted.copy(release=release().copy(sequence=5,build=2)),accepted))
        assertTrue(isClientReleaseRollback(accepted.copy(release=release().copy(sequence=5,build=5,version="1.2.0")),accepted))
    }
    @Test fun noteLanguageFallbackIsPredictable() { assertEquals("Updated",release().notesFor("en-US"));assertEquals("Updated",release().notesFor("ky")) }
    @Test fun sameBuildMetadataMayRefreshButCannotChangeExistingArtifacts() {
        val r=release(); val accepted=VerifiedClientRelease(r,"e","p")
        val refreshed=accepted.copy(release=r.copy(sequence=5,expiresAtMillis=r.expiresAtMillis+1000),payload="fresh")
        assertFalse(isClientReleaseRollback(refreshed,accepted))
        assertTrue(isClientReleaseRollback(refreshed.copy(release=refreshed.release.copy(id="relabelled")),accepted))
        assertTrue(isClientReleaseRollback(refreshed.copy(release=refreshed.release.copy(artifacts=listOf(artifact().copy(sha256="b".repeat(64))))),accepted))
        assertFalse(isClientReleaseRollback(refreshed.copy(release=refreshed.release.copy(artifacts=r.artifacts+artifact(ClientOs.WINDOWS,InstallerKind.MSI))),accepted))
    }
}

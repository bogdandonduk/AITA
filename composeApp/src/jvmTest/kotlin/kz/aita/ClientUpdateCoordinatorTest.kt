package kz.aita

import kz.aita.updates.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import java.security.KeyPairGenerator
import java.security.Signature
import kotlin.io.encoding.Base64
import kotlin.test.*

/** Real state machine and serializers/signatures; all network, installer and storage effects are in memory. */
class ClientUpdateCoordinatorTest {
    companion object { private val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair() }
    private class Backend : ClientUpdateBackend {
        override var publicKey = Base64.encode(pair.public.encoded)
        override val feedBase = "https://updates.example.org/client-updates"
        var time = 1_800_000_000_000L
        var build = ClientBuildIdentity("1.0.0", 1, ReleaseChannel.RELEASE, "test", "test", "direct", "", "")
        val values = mutableMapOf<String, String>()
        var fetches = 0; var installs = 0; var preparations = 0; var cleans = 0
        var response = ClientUpdateFeedReply(204)
        var storageFailure = false; var fetchFailure = false
        var clientPlatform = ClientPlatform(ClientOs.MACOS, ClientArch.ARM64, 15)
        var preparationGate: CompletableDeferred<Unit>? = null
        var permissionGranted = true
        override fun installerPermissionGranted() = permissionGranted
        override fun nowMillis() = time
        override fun installedBuild() = build
        override fun platform() = clientPlatform
        override suspend fun readPreference(name: String) = values[name]
        override suspend fun writePreference(name: String, value: String?) {
            if (storageFailure) throw ClientUpdateFailure("storage")
            if (value == null) values.remove(name) else values[name] = value
        }
        override suspend fun verify(payload: ByteArray, signature: ByteArray, key: ByteArray) = verifyRsaClientRelease(payload, signature, key)
        override suspend fun fetch(channel: ReleaseChannel): ClientUpdateFeedReply { fetches++; if(fetchFailure) throw ClientUpdateFailure("network"); return response }
        override suspend fun restore(release: ClientRelease, artifact: ClientArtifact): PreparedClientInstaller? = null
        override suspend fun prepare(release: ClientRelease, artifact: ClientArtifact, progress: (Long, Long) -> Unit): PreparedClientInstaller {
            preparations++; preparationGate?.await(); progress(artifact.bytes, artifact.bytes)
            return PreparedClientInstaller(release.id, release.build, release.channel, "fixture.pkg", artifact.sha256, artifact.bytes, time)
        }
        override suspend fun handoff(release: ClientRelease, artifact: ClientArtifact, prepared: PreparedClientInstaller?): UpdateHandoff { installs++; return if (permissionGranted) UpdateHandoff.INSTALLER_OPENED else UpdateHandoff.PERMISSION_REQUIRED }
        override suspend fun clean(installed: ClientBuildIdentity) { cleans++ }
        fun release(buildNumber: Long = 2) = ClientRelease(channel = ReleaseChannel.RELEASE, sequence = buildNumber, id = "r$buildNumber",
            version = "1.0.0", build = buildNumber, publishedAtMillis = time - 100, expiresAtMillis = time + 100_000,
            notes = mapOf("en" to "A real verified test release"), artifacts = listOf(ClientArtifact(ClientOs.MACOS, ClientArch.ARM64,
                InstallerKind.PKG, "https://updates.example.org/test.pkg", 20, "a".repeat(64))))
        fun envelope(release: ClientRelease): String {
            val payload = clientReleaseJson.encodeToString(ClientRelease.serializer(), release).encodeToByteArray()
            val signature = Signature.getInstance("SHA256withRSA").run { initSign(pair.private); update(payload); sign() }
            return clientReleaseJson.encodeToString(SignedClientRelease.serializer(), SignedClientRelease(Base64.encode(payload), Base64.encode(signature)))
        }
        fun announce(release: ClientRelease = release()) { response = ClientUpdateFeedReply(200, envelope(release)) }
    }
    private fun scenario(test: suspend (Backend, ClientUpdateCoordinator, MutableList<String>) -> Unit) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val backend = Backend(); val coordinator = ClientUpdateCoordinator(backend, scope); val notes = mutableListOf<String>()
        scope.launch(start = CoroutineStart.UNDISPATCHED) { coordinator.notifications.collect { notes.add(it) } }
        try { test(backend, coordinator, notes) } finally { scope.cancel() }
    }
    private fun Backend.windowsRelease(): ClientRelease {
        clientPlatform = ClientPlatform(ClientOs.WINDOWS, ClientArch.X64, 10)
        return release().copy(artifacts = listOf(ClientArtifact(ClientOs.WINDOWS, ClientArch.X64,
            InstallerKind.MSI, "https://updates.example.org/update.msi", 20, "a".repeat(64))))
    }
    @Test fun windowsPreparesInBackgroundButNeverClosesWorkingApplication() = scenario { b, c, _ ->
        b.announce(b.windowsRelease()); c.start()
        assertTrue(c.state.value.backgroundDownloads)
        assertEquals(1, b.preparations); assertNotNull(c.state.value.prepared); assertEquals(0, b.installs)
        repeat(3) { c.checkNow() }
        assertEquals(1, b.preparations)
        c.update(); assertEquals(1, b.installs)
    }
    @Test fun disabledAutomaticWindowsUpdatesKeepTheManualAction() = scenario { b, c, _ ->
        b.values["release-background-downloads"] = "false"
        b.announce(b.windowsRelease()); c.start()
        assertFalse(c.state.value.backgroundDownloads); assertEquals(0, b.preparations)
        c.update(); assertEquals(1, b.preparations); assertEquals(1, b.installs)
    }
    @Test fun cancellingBackgroundDownloadIsRespectedByLaterPolls() = scenario { b, c, _ ->
        b.preparationGate = CompletableDeferred()
        b.announce(b.windowsRelease()); c.start()
        assertEquals(ClientUpdatePhase.DOWNLOADING, c.state.value.phase)
        c.cancelDownload(); yield(); repeat(3) { c.checkNow() }
        assertEquals(1, b.preparations); assertEquals(0, b.installs)
        b.preparationGate = null; c.update(); assertEquals(2, b.preparations); assertEquals(1, b.installs)
    }
    @Test fun macAndAndroidNeverEnableWindowsAutomaticDownloads() = scenario { b, c, _ ->
        b.announce(); c.start(); assertFalse(c.state.value.backgroundDownloads); assertEquals(0, b.preparations)
    }
    @Test fun newSignedReleaseShowsMarkerAndNotifiesOnce() = scenario { b, c, notes ->
        b.announce(); c.start(); assertTrue(c.state.value.hasUpdate); assertEquals(listOf("1.0.0"), notes)
        repeat(3) { c.checkNow() }; assertEquals(1, notes.size); assertEquals(2L, c.state.value.available?.build)
    }
    @Test fun invalidSignatureNeverShowsAnInstallAction() = scenario { b, c, notes ->
        b.announce(); b.response = b.response.copy(text = b.response.text.replace("RS256", "none")); c.start()
        assertFalse(c.state.value.hasUpdate); assertEquals("integrity", c.state.value.problem); assertTrue(notes.isEmpty())
    }
    @Test fun lowerSequenceCannotReplaceAcceptedRelease() = scenario { b, c, _ ->
        b.announce(b.release(4)); c.start(); b.announce(b.release(2)); c.checkNow()
        assertEquals(4L, c.state.value.available?.build); assertEquals("integrity", c.state.value.problem)
    }
    @Test fun offlineCheckKeepsUnexpiredSignedObservation() = scenario { b, c, _ ->
        b.announce(); c.start(); b.fetchFailure = true; c.checkNow()
        assertTrue(c.state.value.hasUpdate); assertTrue(c.state.value.offline)
    }
    @Test fun cachedReleaseRestoresWithoutRepeatedNotification() = scenario { b, c, notes ->
        b.values["release-accepted"] = b.envelope(b.release()); b.fetchFailure = true; c.start()
        assertTrue(c.state.value.hasUpdate); assertTrue(c.state.value.offline); assertTrue(notes.isEmpty())
    }
    @Test fun openingAnInstallerDoesNotHideTheUpdate() = scenario { b, c, _ ->
        b.announce(); c.start(); c.downloadUpdate(); c.installUpdate()
        assertEquals(1, b.installs); assertEquals(UpdateHandoff.INSTALLER_OPENED, c.state.value.handoff); assertTrue(c.state.value.hasUpdate)
    }
    @Test fun actualNewExecutableClearsTheOfferWithoutLaunchingInstaller() = scenario { b, c, _ ->
        b.announce(); c.start(); c.downloadUpdate(); b.build = b.build.copy(build = 2); c.installUpdate()
        assertEquals(0, b.installs); assertFalse(c.state.value.hasUpdate); assertEquals(2L, c.state.value.installed.build)
    }
    @Test fun reloadedWebBuildClearsCachedOfferButAnOldRuntimeStillOffersTheVerifiedUpdate() = scenario { b, c, notes ->
        b.clientPlatform = ClientPlatform(ClientOs.WEB, ClientArch.UNIVERSAL)
        val release = b.release().copy(artifacts = listOf(ClientArtifact(ClientOs.WEB, ClientArch.UNIVERSAL,
            InstallerKind.WEB_RELOAD, "https://app.example.org/")))
        b.values["release-accepted"] = b.envelope(release)
        b.build = b.build.copy(build = release.build)
        b.fetchFailure = true
        c.start()
        assertFalse(c.state.value.hasUpdate)
        assertTrue(notes.isEmpty())

        val oldRuntimeScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            b.build = b.build.copy(build = release.build - 1)
            val oldRuntime = ClientUpdateCoordinator(b, oldRuntimeScope)
            oldRuntime.start()
            assertTrue(oldRuntime.state.value.hasUpdate)
            assertEquals(release.build, oldRuntime.state.value.available?.build)
        } finally { oldRuntimeScope.cancel() }
    }
    @Test fun missingTrustKeyDoesNotFetchOrOfferAnything() = scenario { b, c, _ ->
        b.publicKey = ""; b.announce(); c.start(); assertFalse(c.state.value.configured); assertEquals(0, b.fetches)
    }
    @Test fun expiredCachedReleaseNeverBecomesInstallable() = scenario { b, c, _ ->
        b.values["release-accepted"] = b.envelope(b.release().copy(expiresAtMillis = b.time - 1)); b.fetchFailure = true; c.start()
        assertFalse(c.state.value.hasUpdate)
    }
    @Test fun highWaterMustBeDurableBeforeShowingNewRelease() = scenario { b, c, notes ->
        b.announce(); b.storageFailure = true; c.start(); assertFalse(c.state.value.hasUpdate); assertTrue(notes.isEmpty())
    }
    @Test fun downloadCancellationUnlocksCheckAndAllowsRetry() = scenario { b, c, _ ->
        b.announce(); c.start(); b.preparationGate = CompletableDeferred(); c.downloadUpdate()
        assertEquals(ClientUpdatePhase.DOWNLOADING, c.state.value.phase)
        c.downloadUpdate(); assertEquals(1, b.preparations)
        c.cancelDownload(); yield(); assertEquals(ClientUpdatePhase.IDLE, c.state.value.phase)
        b.preparationGate = null; c.downloadUpdate(); assertEquals(2, b.preparations); assertNotNull(c.state.value.prepared)
    }
    @Test fun channelMismatchCannotLeakTestReleasesIntoProduction() = scenario { b, c, _ ->
        b.announce(b.release().copy(channel = ReleaseChannel.TEST)); c.start(); assertFalse(c.state.value.hasUpdate)
    }
    @Test fun releaseWithoutThisPlatformsArtifactDoesNotShowMarker() = scenario { b, c, notes ->
        b.announce(b.release().copy(artifacts = listOf(ClientArtifact(ClientOs.ANDROID, kind = InstallerKind.PLAY_STORE, url = "https://play.google.com/store/apps/details?id=kz.aita"))))
        c.start(); assertFalse(c.state.value.hasUpdate); assertTrue(notes.isEmpty())
    }
    @Test fun fileInstallerCannotBeOpenedBeforeDownloadCompletes() = scenario { b,c,_ ->
        b.announce(); c.start(); c.installUpdate()
        assertEquals(0,b.installs); assertEquals("install",c.state.value.problem)
    }
    @Test fun oneUpdateActionDownloadsVerifiesAndOpensInstallerOnce() = scenario { b, c, _ ->
        b.announce(); c.start(); b.preparationGate = CompletableDeferred()
        c.update(); c.update(); c.checkNow()
        assertEquals(1, b.preparations); assertEquals(0, b.installs)
        b.preparationGate!!.complete(Unit); yield()
        assertEquals(1, b.installs); assertEquals(UpdateHandoff.INSTALLER_OPENED, c.state.value.handoff)
    }
    @Test fun permissionReturnResumesOnlyAnExplicitPendingInstall() = scenario { b, c, _ ->
        b.announce(); c.start(); c.onForeground(); assertEquals(0, b.installs)
        b.permissionGranted = false; c.update()
        assertEquals(UpdateHandoff.PERMISSION_REQUIRED, c.state.value.handoff)
        c.onForeground(); assertEquals(1, b.installs)
        b.permissionGranted = true; c.onForeground(); c.onForeground()
        assertEquals(2, b.installs); assertEquals(1, b.preparations)
    }
    @Test fun cancelledUnifiedDownloadNeverLaunchesInstaller() = scenario { b, c, _ ->
        b.announce(); c.start(); b.preparationGate = CompletableDeferred(); c.update(); c.cancelDownload(); yield()
        assertEquals(0, b.installs); assertFalse(c.state.value.busy)
    }

}

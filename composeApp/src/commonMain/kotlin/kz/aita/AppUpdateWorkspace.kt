@file:OptIn(kotlin.time.ExperimentalTime::class)

package kz.aita

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kz.aita.updates.*
import kotlin.io.encoding.Base64
import kotlin.time.Clock

internal enum class ClientUpdatePhase { IDLE, CHECKING, DOWNLOADING, INSTALLING, READY, HANDOFF }
internal data class ClientUpdateState(
    val initialized: Boolean = false,
    val installed: ClientBuildIdentity = GeneratedClientBuild.identity,
    val platform: ClientPlatform? = null,
    val available: ClientRelease? = null,
    val artifact: ClientArtifact? = null,
    val prepared: PreparedClientInstaller? = null,
    val phase: ClientUpdatePhase = ClientUpdatePhase.IDLE,
    val completedBytes: Long = 0,
    val totalBytes: Long = 0,
    val lastCheckedAtMillis: Long? = null,
    val offline: Boolean = false,
    val problem: String? = null,
    val handoff: UpdateHandoff? = null,
    val configured: Boolean = false,
    val backgroundDownloads: Boolean = false
) {
    val hasUpdate: Boolean get() = available != null && artifact != null
    val busy: Boolean get() = phase == ClientUpdatePhase.DOWNLOADING || phase == ClientUpdatePhase.CHECKING || phase == ClientUpdatePhase.INSTALLING
}

/** One process-lifetime workspace, independent of the current account/store or an open update screen. */
internal class ClientUpdateCoordinator(
    private val backend: ClientUpdateBackend,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val operation = Mutex()
    private val startup = Mutex()
    private var started = false
    private var accepted: VerifiedClientRelease? = null
    private var key = byteArrayOf()
    private var download: Job? = null
    private var automaticAttempt: String? = null
    private val mutable = MutableStateFlow(ClientUpdateState())
    val state = mutable.asStateFlow()
    private val messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val notifications = messages.asSharedFlow()
    private fun now() = backend.nowMillis()
    private fun pref(name: String) = "${mutable.value.installed.channel.name.lowercase()}-$name"

    fun start() { scope.launch {
        startup.withLock {
            if (started) return@launch
            started = true
            try {
            val installed = backend.installedBuild()
            val platform = backend.platform()
            key = runCatching { Base64.decode(backend.publicKey) }.getOrDefault(byteArrayOf())
            mutable.update { it.copy(installed = installed, platform = platform,
                configured = key.size in 256..2048 && isPublicClientReleaseUrl(backend.feedBase)) }
            val background = platform.os == ClientOs.WINDOWS && backend.readPreference(pref("background-downloads")) != "false"
            mutable.update { it.copy(backgroundDownloads = background) }
            runCatching { backend.clean(installed) }
            operation.withLock {
                if (mutable.value.configured) {
                    val stored = runCatching { backend.readPreference(pref("accepted")) }.getOrNull()
                    accepted = stored?.let { verifyClientReleaseEnvelope(it, key, backend::verify) }
                    accepted?.takeIf { clientReleaseProblem(it.release, now(), installed.channel) == null }?.let { expose(it.release, notify = false) }
                }
                mutable.update { it.copy(initialized = true, problem = if (!it.configured) "configuration" else null) }
            }
            // Poll only while the process lives; startup, foreground and reconnect also request a check.
            scope.launch { while (isActive) { checkNow(); delay(60_000L) } }
            } catch(cancel: CancellationException) { started = false; throw cancel }
            catch(_: Exception) {
                started = false
                mutable.update { it.copy(initialized = true, problem = "storage", phase = ClientUpdatePhase.IDLE) }
            }
        }
    } }

    fun checkNow() { scope.launch { if(!started) start() else check() } }
    private suspend fun check() {
        if (!mutable.value.initialized || !mutable.value.configured || !operation.tryLock()) return
        try {
            mutable.update { it.copy(phase = ClientUpdatePhase.CHECKING, problem = null) }
            val channel = mutable.value.installed.channel
            val response = backend.fetch(channel)
            if (response.status == 204 || response.status == 404) {
                // Absence is not a signed revocation. Keep an unexpired authenticated observation.
                expireOffer()
                mutable.update { it.copy(lastCheckedAtMillis = now(), offline = false, problem = null) }
                return
            }
            if (response.status != 200) throw ClientUpdateFailure("network")
            if (response.text.encodeToByteArray().size > CLIENT_RELEASE_MAX_BYTES) throw ClientUpdateFailure("integrity")
            val verified = verifyClientReleaseEnvelope(response.text, key, backend::verify)
                ?: throw ClientUpdateFailure("integrity")
            val problem = clientReleaseProblem(verified.release, now(), channel)
            if (problem != null) throw ClientUpdateFailure(if (problem == "expired") "expired" else "integrity")
            if (isClientReleaseRollback(verified, accepted)) throw ClientUpdateFailure("integrity")
            // Persist the high-water mark before exposing a newly accepted release.
            backend.writePreference(pref("accepted"), verified.envelope)
            accepted = verified
            expose(verified.release, notify = true)
            mutable.update { it.copy(lastCheckedAtMillis = now(), offline = false, problem = null) }
        } catch (cancel: CancellationException) { throw cancel }
        catch (failure: Exception) {
            expireOffer()
            mutable.update { it.copy(offline = true, problem = (failure as? ClientUpdateFailure)?.reason ?: "network") }
        } finally {
            mutable.update { it.copy(phase = if (it.prepared != null) ClientUpdatePhase.READY else ClientUpdatePhase.IDLE) }
            operation.unlock()
            prepareInBackgroundIfNeeded()
        }
    }
    private fun prepareInBackgroundIfNeeded() {
        val s = mutable.value
        val release = s.available ?: return
        val artifact = s.artifact ?: return
        if (!s.backgroundDownloads || s.platform?.os != ClientOs.WINDOWS || artifact.kind != InstallerKind.MSI ||
            s.prepared != null || s.offline || s.problem != null || s.busy || s.handoff != null) return
        val identity = release.identity + ":" + artifact.sha256
        // Cancellation/errors pause automatic retries for this artifact in this process.
        // Manual Update remains available; a 60-second poll must not restart a cancelled download.
        if (automaticAttempt == identity) return
        automaticAttempt = identity
        downloadUpdate(installWhenReady = false)
    }
    fun setBackgroundDownloads(enabled: Boolean) { scope.launch {
        try {
            backend.writePreference(pref("background-downloads"), enabled.toString())
            mutable.update { it.copy(backgroundDownloads = enabled) }
            if (enabled) { automaticAttempt = null; prepareInBackgroundIfNeeded() }
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { mutable.update { it.copy(problem = "storage") } }
    } }
    private fun expireOffer() {
        if (mutable.value.available?.expiresAtMillis?.let { it <= now() } == true)
            mutable.update { it.copy(available = null, artifact = null, prepared = null) }
    }
    private suspend fun expose(release: ClientRelease, notify: Boolean) {
        val current = mutable.value
        val artifact = current.platform?.let { selectClientArtifact(release, it) }
        if (!clientReleaseIsNewer(release, current.installed) || artifact == null) {
            mutable.update { it.copy(available = null, artifact = null, prepared = null, handoff = null) }; return
        }
        val same = current.available?.identity == release.identity && current.artifact == artifact
        val restored = if (same) current.prepared else if (artifact.isFile) try { backend.restore(release, artifact) }
            catch(cancel: CancellationException) { throw cancel } catch(_: Exception) { null } else null
        mutable.update { it.copy(available = release, artifact = artifact, prepared = restored,
            handoff = if (same) it.handoff else null) }
        if (notify && backend.readPreference(pref("notified")) != release.identity) {
            backend.writePreference(pref("notified"), release.identity)
            messages.emit(release.version)
        }
    }
    fun downloadUpdate(installWhenReady: Boolean = false) {
        if (download?.isActive == true) return
        download = scope.launch {
            if (!operation.tryLock()) return@launch
            try {
                val s = mutable.value; val release = s.available ?: return@launch; val artifact = s.artifact ?: return@launch
                if (!artifact.isFile || clientReleaseProblem(release,now(),s.installed.channel) != null) throw ClientUpdateFailure("expired")
                mutable.update { it.copy(phase = ClientUpdatePhase.DOWNLOADING, problem = null, handoff = null, completedBytes = 0, totalBytes = artifact.bytes) }
                val prepared = s.prepared ?: backend.prepare(release, artifact) { done, total ->
                    mutable.update { it.copy(completedBytes = done, totalBytes = total) }
                }
                mutable.update { it.copy(prepared = prepared, phase = ClientUpdatePhase.READY) }
                if (installWhenReady) handoffLocked()
            } catch (cancel: CancellationException) { throw cancel }
            catch (failure: Exception) { mutable.update { it.copy(problem = (failure as? ClientUpdateFailure)?.reason ?: "network") } }
            finally {
                mutable.update { if (it.phase == ClientUpdatePhase.HANDOFF) it else it.copy(phase = if (it.prepared == null) ClientUpdatePhase.IDLE else ClientUpdatePhase.READY) }
                operation.unlock()
            }
        }
    }
    fun cancelDownload() { download?.cancel() }
    /** Called only by a deliberate UI action, after the user reviews the platform-specific explanation. */
    fun installUpdate() { scope.launch {
        if (!operation.tryLock()) return@launch
        try {
            handoffLocked()
        } catch (cancel: CancellationException) { throw cancel }
        catch (failure: Exception) { mutable.update { it.copy(problem = (failure as? ClientUpdateFailure)?.reason ?: "install") } }
        finally {
            mutable.update { if (it.phase == ClientUpdatePhase.INSTALLING) it.copy(phase = if (it.prepared != null) ClientUpdatePhase.READY else ClientUpdatePhase.IDLE) else it }
            operation.unlock()
        }
    } }
    private suspend fun handoffLocked() {
            val s = mutable.value; val release = s.available ?: return; val artifact = s.artifact ?: return
            if (!s.configured || clientReleaseProblem(release,now(),s.installed.channel) != null) throw ClientUpdateFailure("expired")
            val actual = backend.installedBuild()
            if (!clientReleaseIsNewer(release, actual)) {
                backend.clean(actual)
                mutable.update { it.copy(installed = actual) }
                expose(release,false); return
            }
            if (artifact.isFile && s.prepared == null) throw ClientUpdateFailure("install")
            mutable.update { it.copy(phase = ClientUpdatePhase.INSTALLING, problem = null, handoff = null) }
            val result = backend.handoff(release,artifact,s.prepared)
            mutable.update { it.copy(handoff = result, problem = null, phase = ClientUpdatePhase.HANDOFF) }
    }
    fun update() {
        if (mutable.value.artifact?.isFile == true) downloadUpdate(installWhenReady = true) else installUpdate()
    }
    /** Resume only an action requested in this process, and only after the OS permission is granted. */
    fun onForeground() {
        if (mutable.value.handoff == UpdateHandoff.PERMISSION_REQUIRED && backend.installerPermissionGranted()) installUpdate()
    }

}

/** Test seam covers the same coordinator used by the application without touching real app storage. */
internal data class ClientUpdateFeedReply(val status: Int, val text: String = "")
internal interface ClientUpdateBackend {
    val publicKey: String
    val feedBase: String
    fun nowMillis(): Long
    fun installedBuild(): ClientBuildIdentity
    fun platform(): ClientPlatform
    suspend fun readPreference(name: String): String?
    suspend fun writePreference(name: String, value: String?)
    suspend fun verify(payload: ByteArray, signature: ByteArray, key: ByteArray): Boolean
    suspend fun fetch(channel: ReleaseChannel): ClientUpdateFeedReply
    suspend fun restore(release: ClientRelease, artifact: ClientArtifact): PreparedClientInstaller?
    suspend fun prepare(release: ClientRelease, artifact: ClientArtifact, progress: (Long, Long) -> Unit): PreparedClientInstaller
    suspend fun handoff(release: ClientRelease, artifact: ClientArtifact, prepared: PreparedClientInstaller?): UpdateHandoff
    suspend fun clean(installed: ClientBuildIdentity)
    fun installerPermissionGranted(): Boolean = true
}

internal class PlatformClientUpdateBackend : ClientUpdateBackend {
    override val publicKey get() = GeneratedClientBuild.publicKey
    override val feedBase get() = GeneratedClientBuild.feedBase
    override fun nowMillis() = Clock.System.now().toEpochMilliseconds()
    override fun installedBuild() = installedClientBuild()
    override fun platform() = clientUpdatePlatform()
    override suspend fun readPreference(name: String) = readClientUpdatePreference(name)
    override suspend fun writePreference(name: String, value: String?) = writeClientUpdatePreference(name, value)
    override suspend fun verify(payload: ByteArray, signature: ByteArray, key: ByteArray) = verifyClientReleaseSignature(payload, signature, key)
    override suspend fun restore(release: ClientRelease, artifact: ClientArtifact) = restoreClientInstaller(release, artifact)
    override suspend fun prepare(release: ClientRelease, artifact: ClientArtifact, progress: (Long, Long) -> Unit) = prepareClientInstaller(release, artifact, progress)
    override suspend fun handoff(release: ClientRelease, artifact: ClientArtifact, prepared: PreparedClientInstaller?) = handoffClientUpdate(release, artifact, prepared)
    override suspend fun clean(installed: ClientBuildIdentity) = cleanCompletedClientInstallers(installed)
    override fun installerPermissionGranted() = clientInstallerPermissionGranted()
    private val http by lazy { HttpClient(getHttpClientEngine()) {
        followRedirects = false
        install(HttpTimeout) { requestTimeoutMillis = 20_000; connectTimeoutMillis = 10_000; socketTimeoutMillis = 15_000 }
    } }
    override suspend fun fetch(channel: ReleaseChannel): ClientUpdateFeedReply {
        val url = feedBase.trimEnd('/') + "/${channel.name.lowercase()}.json"
        val response = http.get(url) { header(HttpHeaders.CacheControl, "no-cache"); accept(ContentType.Application.Json) }
        val source = response.bodyAsChannel()
        if (response.status != HttpStatusCode.OK) {
            source.cancel(null)
            return ClientUpdateFeedReply(response.status.value)
        }
        val chunks = ArrayList<ByteArray>(); var bytes = 0
        val buffer = ByteArray(8192)
        try {
            while (true) {
                val n = source.readAvailable(buffer, 0, buffer.size)
                if (n < 0) break
                if (n == 0) { yield(); continue }
                bytes += n
                if (bytes > CLIENT_RELEASE_MAX_BYTES) throw ClientUpdateFailure("integrity")
                chunks.add(buffer.copyOf(n))
            }
            val body = ByteArray(bytes); var offset = 0
            chunks.forEach { it.copyInto(body, offset); offset += it.size }
            return ClientUpdateFeedReply(200, body.decodeToString(throwOnInvalidSequence = true))
        } finally { source.cancel(null) }
    }
}

internal object AppUpdateWorkspace {
    private val coordinator = ClientUpdateCoordinator(PlatformClientUpdateBackend())
    val state = coordinator.state
    val notifications = coordinator.notifications
    fun start() = coordinator.start()
    fun update() = coordinator.update()
    fun onForeground() = coordinator.onForeground()
    fun checkNow() = coordinator.checkNow()
    fun downloadUpdate() = coordinator.downloadUpdate()
    fun cancelDownload() = coordinator.cancelDownload()
    fun installUpdate() = coordinator.installUpdate()
    fun setBackgroundDownloads(enabled: Boolean) = coordinator.setBackgroundDownloads(enabled)
}

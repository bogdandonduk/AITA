package kz.aita

import androidx.compose.runtime.Composable
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kz.aita.updates.*
import kotlin.io.encoding.Base64

internal data class DownloadEntry(val id: String, val version: String, val build: Long,
    val platform: String, val kind: String, val fileName: String, val sizeBytes: Long, val notes: Map<String, String>,
    val publisherSigned: Boolean? = null, val action: ClientDownloadAction = ClientDownloadAction.DOWNLOAD)
internal data class DownloadsState(val entries: List<DownloadEntry> = emptyList(), val loaded: Boolean = false,
    val loading: Boolean = false, val error: String? = null, val progress: Float? = null, val savingId: String? = null,
    val destinationLabel: String? = null, val canChooseFolder: Boolean = false,
    val folderReady: Boolean = false, val folderChanging: Boolean = false, val folderError: String? = null,
    val savedId: String? = null, val savedDestination: String? = null,
    val installing: Boolean = false, val handoff: UpdateHandoff? = null,
    val webVersion: String? = null, val webBuild: Long? = null, val webNotes: Map<String, String> = emptyMap())
internal data class ClientDownloadResult(val destination: String? = null, val cancelled: Boolean = false,
    val prepared: PreparedClientInstaller? = null)

internal expect fun clientDownloadsCanChooseFolder(): Boolean
internal expect suspend fun clientDownloadsFolderLabel(folder: String?): String?
@Composable internal expect fun rememberDownloadsFolderPicker(onChosen: (String?) -> Unit): () -> Unit
internal expect suspend fun saveClientDownload(file: ClientDownloadFile, fileName: String, folder: String?, installRequest: ClientDownloadInstallRequest? = null,
    progress: (Long, Long) -> Unit): ClientDownloadResult

internal data class DownloadFolderInspection(val folder: String?, val loaded: Boolean,
    val label: String? = null, val error: String? = null)

/** A broken folder must not hide recovery controls or prevent a catalogue refresh. */
internal suspend fun inspectDownloadFolder(current: String?, loaded: Boolean,
    readPreference: suspend () -> String?, resolveLabel: suspend (String?) -> String?): DownloadFolderInspection {
    var folder = current
    var preferenceLoaded = loaded
    return try {
        if (!preferenceLoaded) {
            folder = readPreference()
            preferenceLoaded = true
        }
        DownloadFolderInspection(folder, preferenceLoaded, resolveLabel(folder))
    } catch (cancel: CancellationException) { throw cancel }
    catch (_: Exception) { DownloadFolderInspection(folder, preferenceLoaded, error = "storage") }
}

/** Public signed binaries; compatible installers launch only after a deliberate action. */
internal object DownloadsWorkspace {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val backend = PlatformClientUpdateBackend()
    private val mutable = MutableStateFlow(DownloadsState())
    val state = mutable.asStateFlow()
    private var accepted: VerifiedClientRelease? = null
    private var release: ClientRelease? = null
    private var files = emptyMap<String, ClientDownloadFile>()
    private var pendingInstall: Pair<ClientDownloadInstallRequest, PreparedClientInstaller>? = null
    private var versions = emptyMap<String, ClientDownloadVersion>()
    private var folder: String? = null
    private var folderLoaded = false
    private val channel get() = GeneratedClientBuild.identity.channel
    private val preference get() = "downloads-${channel.name.lowercase()}-accepted"
    fun reportProblem(reason: String) { mutable.update { it.copy(error = reason) } }

    fun refresh() {
        if (mutable.value.loading || mutable.value.folderChanging || mutable.value.savingId != null) return
        mutable.update { it.copy(loading = true, error = null, canChooseFolder = clientDownloadsCanChooseFolder()) }
        scope.launch {
            try {
                val inspection = inspectDownloadFolder(folder, folderLoaded,
                    { readClientUpdatePreference("downloads-folder") }, ::clientDownloadsFolderLabel)
                folder = inspection.folder
                folderLoaded = inspection.loaded
                mutable.update { it.copy(destinationLabel = inspection.label, folderReady = inspection.error == null,
                    folderError = inspection.error, error = inspection.error) }
                val key = Base64.decode(backend.publicKey)
                if (key.size !in 256..2048 || !isPublicClientReleaseUrl(backend.feedBase)) throw ClientUpdateFailure("configuration")
                if (accepted == null) {
                    accepted = readClientUpdatePreference(preference)?.let { verifyClientReleaseEnvelope(it, key, backend::verify) }
                    accepted?.takeIf { clientReleaseProblem(it.release, backend.nowMillis(), channel) == null }?.let { expose(it.release) }
                }
                val response = backend.fetch(channel)
                if (response.status in setOf(204, 404)) return@launch
                if (response.status != 200) throw ClientUpdateFailure("network")
                val verified = verifyClientReleaseEnvelope(response.text, key, backend::verify) ?: throw ClientUpdateFailure("integrity")
                if (clientReleaseProblem(verified.release, backend.nowMillis(), channel) != null ||
                    isClientReleaseRollback(verified, accepted) || verifiedDownloadVersions(verified.release) == null) throw ClientUpdateFailure("integrity")
                writeClientUpdatePreference(preference, verified.envelope)
                accepted = verified
                expose(verified.release)
            } catch (cancel: CancellationException) { throw cancel }
            catch (failure: Exception) {
                mutable.update { it.copy(error = (failure as? ClientUpdateFailure)?.reason ?: "network") }
            } finally {
                if (release?.let { clientReleaseProblem(it, backend.nowMillis(), channel) != null } == true) {
                    release = null; files = emptyMap(); mutable.update { it.copy(entries = emptyList(), webVersion = null, webBuild = null, webNotes = emptyMap()) }
                }
                mutable.update { it.copy(loading = false, loaded = true) }
            }
        }
    }

    private fun expose(value: ClientRelease) {
        val versions = verifiedDownloadVersions(value) ?: throw ClientUpdateFailure("integrity")
        val nextFiles = mutableMapOf<String, ClientDownloadFile>()
        val nextVersions = mutableMapOf<String, ClientDownloadVersion>()
        val platform = backend.platform(); val installed = backend.installedBuild()
        val entries = versions.flatMap { version -> version.files.map { file ->
            val id = "${version.build}:${file.os}:${file.arch}:${file.kind}"
            nextFiles[id] = file
            nextVersions[id] = version
            DownloadEntry(id, version.version, version.build, file.os, file.kind,
                clientDownloadFileName(version, file), file.bytes, version.notes, file.publisherSigned, clientDownloadAction(version, file, platform, installed))
        } }
        release = value; files = nextFiles; this.versions = nextVersions
        val web = value.artifacts.any { it.os == ClientOs.WEB && it.kind == InstallerKind.WEB_RELOAD }
        mutable.update { it.copy(entries = entries, webVersion = value.version.takeIf { web }, webBuild = value.build.takeIf { web },
            webNotes = if (web) value.notes else emptyMap()) }
    }

    fun setFolder(value: String?) {
        if (mutable.value.savingId != null || mutable.value.loading || mutable.value.folderChanging) return
        mutable.update { it.copy(folderChanging = true) }
        scope.launch {
            try {
                val label = clientDownloadsFolderLabel(value)
                writeClientUpdatePreference("downloads-folder", value)
                folder = value; folderLoaded = true
                mutable.update { it.copy(destinationLabel = label, folderReady = true, folderError = null, error = null) }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { mutable.update { it.copy(error = "storage") } }
            finally { mutable.update { it.copy(folderChanging = false) } }
        }
    }

    fun save(entryId: String) {
        if (mutable.value.savingId != null || mutable.value.loading || mutable.value.folderChanging) return
        if (!mutable.value.folderReady) {
            mutable.update { it.copy(error = "storage") }; return
        }
        val entry = mutable.value.entries.firstOrNull { it.id == entryId } ?: return
        val file = files[entryId] ?: return
        if (release?.let { clientReleaseProblem(it, backend.nowMillis(), channel) == null } != true) {
            mutable.update { it.copy(error = "expired") }; return
        }
        val request = if (entry.action == ClientDownloadAction.DOWNLOAD) null else
            ClientDownloadInstallRequest(release ?: return, versions[entryId] ?: return, file)
        pendingInstall = null
        mutable.update { it.copy(savingId = entryId, progress = 0f, error = null, savedId = null, savedDestination = null, handoff = null) }
        // On the web, showSaveFilePicker must run in the original button gesture before suspension.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val result = saveClientDownload(file, entry.fileName, folder, request) { count, total ->
                    mutable.update { it.copy(progress = if (total > 0) (count.toDouble() / total).toFloat().coerceIn(0f, 1f) else null) }
                }
                if (!result.cancelled) {
                    mutable.update { it.copy(savedId = entryId, savedDestination = result.destination) }
                    if (request != null) {
                        val prepared = result.prepared ?: throw ClientUpdateFailure("integrity")
                        pendingInstall = request to prepared
                        openPendingInstaller()
                    }
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (failure: Exception) { mutable.update { it.copy(error = (failure as? ClientUpdateFailure)?.reason ?: "storage") } }
            finally { mutable.update { it.copy(savingId = null, progress = null, installing = false) } }
        }
    }
    private suspend fun openPendingInstaller() {
        val pending = pendingInstall ?: return
        mutable.update { it.copy(installing = true, handoff = null) }
        val result = handoffClientDownload(pending.first, pending.second)
        if (result != UpdateHandoff.PERMISSION_REQUIRED) pendingInstall = null
        mutable.update { it.copy(handoff = result) }
    }
    fun onForeground() {
        if (mutable.value.handoff != UpdateHandoff.PERMISSION_REQUIRED || mutable.value.savingId != null ||
            !clientInstallerPermissionGranted()) return
        val pending = pendingInstall ?: return
        mutable.update { it.copy(savingId = "${pending.first.version.build}:${pending.first.file.os}:${pending.first.file.arch}:${pending.first.file.kind}") }
        scope.launch {
            try { openPendingInstaller() }
            catch (cancel: CancellationException) { throw cancel }
            catch (failure: Exception) { mutable.update { it.copy(error = (failure as? ClientUpdateFailure)?.reason ?: "install") } }
            finally { mutable.update { it.copy(savingId = null, progress = null, installing = false) } }
        }
    }

}

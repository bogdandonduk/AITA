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
    val publisherSigned: Boolean? = null)
internal data class DownloadsState(val entries: List<DownloadEntry> = emptyList(), val loaded: Boolean = false,
    val loading: Boolean = false, val error: String? = null, val progress: Float? = null, val savingId: String? = null,
    val destinationLabel: String? = null, val canChooseFolder: Boolean = false,
    val savedId: String? = null, val savedDestination: String? = null,
    val webVersion: String? = null, val webBuild: Long? = null, val webNotes: Map<String, String> = emptyMap())
internal data class ClientDownloadResult(val destination: String? = null, val cancelled: Boolean = false)

internal expect fun clientDownloadsCanChooseFolder(): Boolean
internal expect suspend fun clientDownloadsFolderLabel(folder: String?): String?
@Composable internal expect fun rememberDownloadsFolderPicker(onChosen: (String?) -> Unit): () -> Unit
internal expect suspend fun saveClientDownload(file: ClientDownloadFile, fileName: String, folder: String?,
    progress: (Long, Long) -> Unit): ClientDownloadResult

/** Public signed binaries only. No account token, installer launch, or GitHub credential is used. */
internal object DownloadsWorkspace {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val backend = PlatformClientUpdateBackend()
    private val mutable = MutableStateFlow(DownloadsState())
    val state = mutable.asStateFlow()
    private var accepted: VerifiedClientRelease? = null
    private var release: ClientRelease? = null
    private var files = emptyMap<String, ClientDownloadFile>()
    private var folder: String? = null
    private var folderLoaded = false
    private val channel get() = GeneratedClientBuild.identity.channel
    private val preference get() = "downloads-${channel.name.lowercase()}-accepted"
    fun reportProblem(reason: String) { mutable.update { it.copy(error = reason) } }

    fun refresh() {
        if (mutable.value.loading) return
        mutable.update { it.copy(loading = true, error = null) }
        scope.launch {
            try {
                if (!folderLoaded) {
                    folder = readClientUpdatePreference("downloads-folder")
                    folderLoaded = true
                }
                val destination = clientDownloadsFolderLabel(folder)
                mutable.update { it.copy(canChooseFolder = clientDownloadsCanChooseFolder(), destinationLabel = destination) }
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
        val entries = versions.flatMap { version -> version.files.map { file ->
            val id = "${version.build}:${file.os}:${file.arch}:${file.kind}"
            nextFiles[id] = file
            DownloadEntry(id, version.version, version.build, file.os, file.kind,
                clientDownloadFileName(version, file), file.bytes, version.notes, file.publisherSigned)
        } }
        release = value; files = nextFiles
        val web = value.artifacts.any { it.os == ClientOs.WEB && it.kind == InstallerKind.WEB_RELOAD }
        mutable.update { it.copy(entries = entries, webVersion = value.version.takeIf { web }, webBuild = value.build.takeIf { web },
            webNotes = if (web) value.notes else emptyMap()) }
    }

    fun setFolder(value: String?) {
        if (mutable.value.savingId != null) return
        scope.launch {
            try {
                val label = clientDownloadsFolderLabel(value)
                writeClientUpdatePreference("downloads-folder", value)
                folder = value; folderLoaded = true
                mutable.update { it.copy(destinationLabel = label, error = null) }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { mutable.update { it.copy(error = "storage") } }
        }
    }

    fun save(entryId: String) {
        if (mutable.value.savingId != null) return
        val entry = mutable.value.entries.firstOrNull { it.id == entryId } ?: return
        val file = files[entryId] ?: return
        if (release?.let { clientReleaseProblem(it, backend.nowMillis(), channel) == null } != true) {
            mutable.update { it.copy(error = "expired") }; return
        }
        mutable.update { it.copy(savingId = entryId, progress = 0f, error = null, savedId = null, savedDestination = null) }
        // On the web, showSaveFilePicker must run in the original button gesture before suspension.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val result = saveClientDownload(file, entry.fileName, folder) { count, total ->
                    mutable.update { it.copy(progress = if (total > 0) (count.toDouble() / total).toFloat().coerceIn(0f, 1f) else null) }
                }
                if (!result.cancelled) mutable.update { it.copy(savedId = entryId, savedDestination = result.destination) }
            } catch (cancel: CancellationException) { throw cancel }
            catch (failure: Exception) { mutable.update { it.copy(error = (failure as? ClientUpdateFailure)?.reason ?: "storage") } }
            finally { mutable.update { it.copy(savingId = null, progress = null) } }
        }
    }
}

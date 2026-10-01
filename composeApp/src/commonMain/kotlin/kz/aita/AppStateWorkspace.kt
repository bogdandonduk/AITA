package kz.aita

import io.ktor.http.HttpMethod
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable internal data class LocalAppState(
    val document: AppStateDocument = AppStateDocument(), val revision: Long = 0,
    val cloud: Boolean = true, val dirty: Boolean = false
)
internal data class AppStateUi(val device: Boolean = true, val cloud: Boolean = true,
    val status: String = "loading", val conflict: Boolean = false, val signedIn: Boolean = false)

/** One workspace per account/mode/store. The session generation fences every asynchronous result. */
internal object AppStateWorkspace {
    private data class Owner(val id: String, val scope: AppStateScope, val generation: Long) {
        val key get() = "app-state-v1:$id:${scope.key}"
        val navigationKey get() = "navigation-place-v2:$id:${scope.mode}"
    }
    private val job = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mutex = Mutex()
    private val storageMutex = Mutex()
    private val memory = mutableMapOf<String, LocalAppState>()
    val readyScope = MutableStateFlow<String?>(null)
    fun readyForCurrentScope(): Boolean = readyScope.value == "${current().key}:${current().generation}"
    private var localNavigation: LocalNavigationPlace? = null
    private var restoredNavigation = false
    private var savedNavigation: LocalNavigationPlace? = null
    val restoreRevision = MutableStateFlow(0L)
    private val record = MutableStateFlow(LocalAppState())
    val state = MutableStateFlow(AppStateUi())
    private var owner: Owner? = null
    private var syncJob: Job? = null
    private var loaded = false
    private var started = false
    private var device = true
    private var remote: AccountAppState? = null
    private var legacyAllowed = true
    private var restoredLocal = false
    private var firstRead = true
    private var nextSync = 0L
    private var persisted: LocalAppState? = null
    private var change = 0L
    fun edited() { change++ }
    private fun current() = Owner(userAccountState.payloadValue?.id?.takeIf { getStoredUserAuthTokens?.invoke() != null } ?: "anonymous",
        AppStateScope(appModeState.value, activeStoreIdState.value?.takeIf { it.isNotBlank() }), currentAuthenticatedSessionGeneration())
    private fun belongs(expected: Owner?) = expected != null && owner == expected && current() == expected
    private fun publish(status: String) {
        state.value = AppStateUi(device, record.value.cloud, status, remote != null, owner?.id != "anonymous")
    }
    fun start() {
        if (started) return
        started = true
        job.launch {
            kotlinx.coroutines.flow.combine(Navigation.Main, Navigation.Stock.Left, Navigation.Stock.Right,
                Navigation.Menu.Left, Navigation.Menu.Right) { _, _, _, _, _ -> Unit }.collect {
                mutex.withLock { owner?.takeIf { loaded && belongs(it) }?.let { expected ->
                    try { persistNavigation(expected) }
                    catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) { publish("unavailable") }
                } }
            }
        }
        job.launch {
            while (isActive) {
                try { mutex.withLock { tick() } }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    publish("unavailable")
                    if (belongs(owner)) {
                        readyScope.value = "${owner!!.key}:${owner!!.generation}"
                        appNavigationRestoredState.value = true
                    }
                }
                finally { if (loaded && belongs(owner)) appNavigationRestoredState.value = true }
                delay(500)
            }
        }
    }
    private suspend fun tick() {
        if (!localApplicationHydratedState.value) return
        val expected = current()
        if (expected.id != "anonymous" && !localWorkspaceScopeReady()) return
        if (owner != expected || !loaded) {
            owner?.let { if (loaded) memory[it.key] = record.value }
            syncJob?.cancel(); syncJob = null
            readyScope.value = null
            owner = expected; loaded = false; remote = null; firstRead = true; nextSync = 0; persisted = null; change++
            record.value = LocalAppState()
            val latestPlace = getLocalKv(expected.navigationKey)?.let {
                runCatching { jsonBase.decodeFromString<DeviceNavigationPlace>(it) }.getOrNull()?.forStore(expected.scope.store)
            }
            localNavigation = latestPlace ?: getLocalKv("${expected.key}:place")?.let {
                runCatching { jsonBase.decodeFromString<LocalNavigationPlace>(it) }.getOrNull()?.takeIf { it.valid() }
            }
            savedNavigation = localNavigation
            restoredNavigation = localNavigation != null
            if (!belongs(expected)) return
            // Do not let another account's singleton StateHosts become this account's drafts.
            Navigation.clearAccountUiState()
            device = getLocalKv("${expected.key}:device") != "false"
            if (!belongs(expected)) return
            legacyAllowed = getLocalKv("${expected.key}:legacyDisabled") != "true"
            val saved = memory[expected.key] ?: if (device) getPersistentUiDraftValue?.invoke(expected.key)?.let {
                runCatching { jsonBase.decodeFromString<LocalAppState>(it) }.getOrNull()?.takeIf { it.document.valid(APP_STATE_DEVICE_MAX_BYTES) }
                    ?: error("Saved app state could not be read; original copy retained")
            } else null
            if (!belongs(expected)) return
            record.value = saved ?: LocalAppState()
            persisted = saved; restoredLocal = saved != null; loaded = true
            if (saved != null) {
                Navigation.restoreAccountUiState(saved.document)
                if (localNavigation == null) localNavigation = Navigation.localNavigationSnapshot().takeIf { it.valid() }
                restoredNavigation = localNavigation != null
            }
            localNavigation?.let { Navigation.restoreLocalNavigation(it) }
            readyScope.value = "${expected.key}:${expected.generation}"
            restoreRevision.value++
            appNavigationRestoredState.value = true
            publish(if (expected.id == "anonymous") "local" else "loading")
            // Preserve the old unscoped cache for recovery, but never adopt it into a different account.
        }
        if (!belongs(expected)) return
        capture()
        persistNavigation(expected)
        persist(expected)
        if (expected.id != "anonymous" && getCurrentTimeMillis() >= nextSync && remote == null) {
            nextSync = getCurrentTimeMillis() + 15_000
            if (syncJob?.isActive != true) syncJob = job.launch {
                try { sync(expected) }
                catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { if (belongs(expected)) publish("unavailable") }
            }
        }
    }
    private suspend fun persistNavigation(expected: Owner) {
        if (!belongs(expected)) return
        val place = Navigation.localNavigationSnapshot()
        if (!place.valid() || place == savedNavigation) return
        putLocalKv(expected.navigationKey, jsonBase.encodeToString(DeviceNavigationPlace(expected.scope.store, place)))
        putLocalKv("${expected.key}:place", jsonBase.encodeToString(place))
        if (belongs(expected)) { localNavigation = place; savedNavigation = place }
    }
    private fun capture() {
        if (!loaded) return
        val snapshot = Navigation.accountUiStateSnapshot(record.value.document.drafts)
        if (snapshot != record.value.document) {
            record.update { it.copy(document = snapshot, dirty = true) }; change++
        }
    }
    private suspend fun persist(expected: Owner) = storageMutex.withLock {
        if (!belongs(expected) || record.value == persisted) return@withLock
        val value = record.value
        if (!value.document.valid(APP_STATE_DEVICE_MAX_BYTES)) { publish("too_large"); return@withLock }
        if (device) setPersistentUiDraftValue?.invoke(expected.key, jsonBase.encodeToString(value))
        if (belongs(expected)) persisted = value
    }
    private suspend fun sync(expected: Owner) {
        val before = record.value
        val atRequest = change
        val visibleBefore = Navigation.accountUiStateSnapshot(before.document.drafts)
        val initial = firstRead && !restoredLocal && before.document.drafts.isEmpty()
        firstRead = false
        val reply = networkRequest<AppStateResult, Unit>(HttpMethod.Get, endpointUrl = "user/app-state",
            query = mapOf("mode" to expected.scope.mode, "store" to expected.scope.store), expectedSessionGeneration = expected.generation)
        if (!belongs(expected)) return
        val server = reply.payload?.state
        if (reply.negative || server == null) { publish("unavailable"); return }
        val untouched = change == atRequest && Navigation.accountUiStateSnapshot(record.value.document.drafts) == visibleBefore
        // A new device starts with no local journal: its default screen is not a competing edit.
        when (appStateMerge(AccountAppState(before.revision, before.cloud, before.document), server, before.dirty, untouched, initial)) {
            AppStateMerge.ACKNOWLEDGE -> {
                record.update { it.copy(revision = server.revision, dirty = it.document != before.document || it.cloud != before.cloud) }
            }
            AppStateMerge.RESTORE -> {
                record.value = LocalAppState(server.document ?: before.document, server.revision, server.enabled, false)
                val place = localNavigation.takeIf { restoredNavigation }
                server.document?.let { Navigation.restoreAccountUiState(it) }
                place?.let { Navigation.restoreLocalNavigation(it) }
                restoreRevision.value++; change++; persisted = null
            }
            AppStateMerge.CONFLICT -> { remote = server; publish("conflict"); return }
            AppStateMerge.SAME -> if (!before.dirty && untouched && before.cloud != server.enabled) {
                record.update { it.copy(cloud = server.enabled) }
            }
        }
        capture()
        val pending = record.value
        if (!pending.document.valid()) { publish("too_large"); return }
        if (pending.dirty && (pending.cloud || server.enabled)) {
            val sent = networkRequest<AppStateResult, AppStateWrite>(HttpMethod.Put, endpointUrl = "user/app-state",
                body = AppStateWrite(expected.scope, pending.revision, pending.cloud, pending.document.takeIf { pending.cloud }),
                expectedSessionGeneration = expected.generation)
            if (!belongs(expected)) return
            val result = sent.payload
            if (sent.negative || result == null) { publish("unavailable"); return }
            if (result.conflict) { remote = result.state; publish("conflict"); return }
            record.update { it.copy(revision = result.state.revision, dirty = it.document != pending.document || it.cloud != pending.cloud) }
        }
        if (!pending.cloud && !server.enabled) record.update { it.copy(dirty = false) }
        persist(expected)
        publish(if (record.value.cloud) "saved" else "local")
    }

    suspend fun readDraft(key: String): String? {
        val expected = owner
        if (!loaded || !belongs(expected) || !ownsDraftKey(key, expected!!.id, expected.scope.store)) return null
        record.value.document.drafts[key]?.let { return it }
        // Import old device drafts on demand, preserving work from releases before account sync.
        if (!device || !legacyAllowed || record.value.revision > 0) return null
        val atRead = change
        val value = getPersistentUiDraftValue?.invoke(key) ?: return null
        if (!belongs(expected)) return null
        // Storage can finish after a scan cleared this draft or the user typed again.
        record.value.document.drafts[key]?.let { return it }
        if (change != atRead || !device || !legacyAllowed || record.value.revision > 0) return null
        record.update { it.copy(document = it.document.copy(drafts = it.document.drafts + (key to value)), dirty = true) }
        change++
        return value
    }
    suspend fun writeDraft(key: String, value: String?) {
        val expected = owner
        if (!loaded || !belongs(expected) || !ownsDraftKey(key, expected!!.id, expected.scope.store)) return
        record.update { it.copy(document = it.document.copy(drafts = if (value == null) it.document.drafts - key else it.document.drafts + (key to value)), dirty = true) }
        change++
        persist(expected)
        // Remove an imported legacy value only after the aggregate journal is durable.
        if (device && belongs(expected)) setPersistentUiDraftValue?.invoke(key, if (record.value.document.valid(APP_STATE_DEVICE_MAX_BYTES)) null else value)
    }
    /** A consumed scan must stay cleared even if its field leaves composition before debounce. */
    fun clearDraft(key: String) {
        val expected = owner
        if (!loaded || !belongs(expected) || !ownsDraftKey(key, expected!!.id, expected.scope.store)) return
        record.update { it.copy(document = it.document.copy(drafts = it.document.drafts + (key to "")), dirty = true) }
        change++
        job.launch {
            try {
                persist(expected)
                if (device && belongs(expected)) {
                    setPersistentUiDraftValue?.invoke(key,
                        if (record.value.document.valid(APP_STATE_DEVICE_MAX_BYTES)) null else record.value.document.drafts[key])
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (belongs(expected)) publish("unavailable") }
        }
    }
    fun setDevice(enabled: Boolean) = job.launch { mutex.withLock {
        val expected = owner ?: return@withLock
        if (!belongs(expected)) return@withLock
        putLocalKv("${expected.key}:device", enabled.toString())
        if (!belongs(expected)) return@withLock
        device = enabled; persisted = null
        if (!enabled) storageMutex.withLock {
            legacyAllowed = false
            putLocalKv("${expected.key}:legacyDisabled", "true")
            setPersistentUiDraftValue?.invoke(expected.key, null)
            record.value.document.drafts.keys.forEach { setPersistentUiDraftValue?.invoke(it, null) }
        } else persist(expected)
        publish(if (record.value.cloud) "pending" else "local")
    } }
    fun setCloud(enabled: Boolean) = job.launch { mutex.withLock {
        if (!belongs(owner) || owner?.id == "anonymous") return@withLock
        record.update { it.copy(cloud = enabled, dirty = true) }; change++; nextSync = 0
        persist(owner!!); publish("pending")
    } }
    fun resolve(useAccount: Boolean) = job.launch { mutex.withLock {
        val expected = owner ?: return@withLock
        val server = remote ?: return@withLock
        if (!belongs(expected)) return@withLock
        if (useAccount) {
            record.value = LocalAppState(server.document ?: AppStateDocument(), server.revision, server.enabled, false)
            Navigation.clearAccountUiState()
            Navigation.restoreAccountUiState(record.value.document)
        } else record.update { it.copy(revision = server.revision, dirty = true) }
        restoreRevision.value++; remote = null; change++; nextSync = 0; persisted = null
        persist(expected); publish("pending")
    } }
    suspend fun flush() = mutex.withLock { owner?.takeIf(::belongs)?.let { capture(); persistNavigation(it); persist(it) } }
}

internal fun ownsDraftKey(key: String, owner: String, store: String?): Boolean = appStateSafeKey(key) &&
    key.split(':').filter { it.endsWith("NavigationScreenModelRoute") }.all(::appStateSafeRoute) &&
    (key.startsWith("aita-ui-draft-v1:$owner:${store ?: "no-store"}:") ||
        key.startsWith("stock-add-edit-goods-item:$owner:${store ?: "no-store"}:") ||
        key.startsWith("stock-add-edit-last-category:$owner:${store ?: "no-store"}:") ||
        key.startsWith("operation-choice:$owner:${store ?: "no-store"}:"))

internal val readAppStateDraft: (suspend (String) -> String?) = AppStateWorkspace::readDraft
internal val writeAppStateDraft: (suspend (String, String?) -> Unit) = AppStateWorkspace::writeDraft

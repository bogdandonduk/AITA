package kz.aita

import io.ktor.http.HttpMethod
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable

@Serializable
private data class ActiveStoreSelectionJournal(
    val storeId: String? = null,
    val explicitNone: Boolean = false,
    val pendingSync: Boolean = false
)

internal object ActiveStores {
    private val failedRestoreOwner = kotlinx.coroutines.flow.MutableStateFlow<ActiveStoreOwner?>(null)
    // A failed local read must leave Settings/account access usable, without overwriting
    // the unread store selection or pretending it was successfully restored.
    val readyForNavigation: Boolean get() = hydrated || owner()?.let { failedRestoreOwner.value == it } == true
    private const val LEGACY_OWNER_KEY = "active-store.owner.v2"
    private fun key(owner: ActiveStoreOwner) = "active-store.selection.v2:${owner.accountId}"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.ourIo + CoroutineExceptionHandler { _, failure ->
        if (failure !is Exception) throw failure
        logCloudConnectionDiagnostic("Active-store persistence/sync interrupted; latest selection retained")
    })

    private fun owner(): ActiveStoreOwner? = userAccountState.payloadValue?.id
        ?.takeIf { it.isNotBlank() && getStoredUserAuthTokens?.invoke() != null }
        ?.let { ActiveStoreOwner(it, currentAuthenticatedSessionGeneration()) }

    private val coordinator: ActiveStoreSelectionCoordinator by lazy { ActiveStoreSelectionCoordinator(
        scope = scope,
        ownerIsCurrent = { it == owner() },
        load = { expected ->
            val raw = getLocalKv(key(expected))
            val saved = raw?.let {
                runCatching { jsonBase.decodeFromString(ActiveStoreSelectionJournal.serializer(), it) }.getOrNull()
            }
            if (saved != null) {
                SavedActiveStoreChoice(ActiveStoreChoice(saved.storeId, saved.explicitNone), saved.pendingSync)
            } else {
                // One-time legacy import only when the cached account proves whose old global key it is.
                val legacyOwner = getLocalKv(LEGACY_OWNER_KEY)
                val cachedOwner = getStoredUserAccountDataModel?.invoke()?.id
                if (legacyOwner == expected.accountId || (legacyOwner == null && cachedOwner == expected.accountId)) {
                    val id = getLocalKv(KEY_ACTIVE_STORE_ID)?.trim()?.takeIf { it.isNotEmpty() }
                    val cleared = getLocalKv(KEY_ACTIVE_STORE_EXPLICIT_NONE) == "1"
                    if (id != null || cleared) SavedActiveStoreChoice(ActiveStoreChoice(id, cleared), false) else null
                } else null
            }
        },
        persist = { selected ->
            selected.owner?.let { expected ->
                putLocalKv(key(expected), jsonBase.encodeToString(ActiveStoreSelectionJournal.serializer(),
                    ActiveStoreSelectionJournal(selected.choice.storeId, selected.choice.explicitNone, selected.pendingSync)))
            }
            DynamicCarts.prepareLegacyImport()
            // Retain compatibility keys, but never observe them as commands.
            if (coordinator.isCurrent(selected)) {
                putLocalKv(KEY_ACTIVE_STORE_ID, selected.choice.storeId)
                putLocalKv(KEY_ACTIVE_STORE_EXPLICIT_NONE, if (selected.choice.explicitNone) "1" else null)
                putLocalKv(LEGACY_OWNER_KEY, selected.owner?.accountId)
            }
        },
        publish = { selected ->
            publishActiveInventoryStoreId(selected.choice.storeId) { coordinator.isCurrent(selected) }
        },
        sync = { selected ->
            val response = networkRequest<Unit, String>(
                method = HttpMethod.Put,
                endpointUrl = "stores/active",
                body = selected.choice.storeId.orEmpty(),
                expectedSessionGeneration = requireNotNull(selected.owner).sessionGeneration
            )
            val outcome = activeStoreSyncOutcome(response.negative, response.transportFailure, response.httpStatusCode)
            if (outcome == ActiveStoreSyncOutcome.REJECTED && coordinator.isCurrent(selected)) {
                postInAppNotification(response.message, NotificationType.Negative, transient = false)
            }
            outcome
        },
        acknowledged = { selected ->
            if (coordinator.isCurrent(selected)) {
                userAccountState.payloadValue?.takeIf { it.id == selected.owner?.accountId }?.let { account ->
                    val merged = account.copy(activeStoreId = selected.choice.storeId)
                    userAccountState.emit(DataState.Success(merged))
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.ourIo) { setStoredUserAccountDataModel?.invoke(merged) }
                }
            }
        }
    ) }

    val revision: Long get() = coordinator.snapshot.revision
    val explicitNone: Boolean get() = coordinator.snapshot.let { it.hydrated && it.owner == owner() && it.choice.explicitNone }
    val hydrated: Boolean get() = coordinator.snapshot.let { it.hydrated && it.owner == owner() }

    fun select(id: String?, syncServer: Boolean) { coordinator.select(owner(), id, syncServer) }
    fun retryPending() { coordinator.retryPending() }

    suspend fun acceptAccount(account: UserAccountDataModel, applyServerSelection: Boolean = true) {
        val expected = owner()?.takeIf { it.accountId == account.id } ?: return
        try {
            coordinator.adopt(expected, account.activeStoreId, applyServerSelection)
            failedRestoreOwner.compareAndSet(expected, null)
        }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            if (expected == owner()) failedRestoreOwner.value = expected
            logCloudConnectionDiagnostic("Active-store restoration interrupted; current selection retained")
        }
    }

    fun mergeAccount(account: UserAccountDataModel): UserAccountDataModel {
        val selected = coordinator.snapshot
        return if (selected.hydrated && selected.owner == owner() && selected.owner?.accountId == account.id) {
            account.copy(activeStoreId = selected.choice.storeId)
        } else account
    }
}

package kz.aita

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Loading/failure is separate from the last usable inventory, never an invented empty list. */
data class InventoryLoadStatus(
    val storeId: String? = null,
    val loading: Boolean = false,
    val source: InventoryLoadSource = InventoryLoadSource.None,
    val failure: List<LocalizedStringDataModel>? = null,
    val accessDenied: Boolean = false,
    val cacheWriteFailed: Boolean = false,
    val cacheChecked: Boolean = false
)

enum class InventoryLoadSource { None, Cache, Cloud, Local }

val stockLoadStatusState = MutableStateFlow(InventoryLoadStatus())
val stockBatchesLoadStatusState = MutableStateFlow(InventoryLoadStatus())

/** An epoch also rejects A -> B -> A responses, not just responses with a different store ID. */
internal data class InventoryOwner(
    val storeId: String?,
    val accountId: String?,
    val sessionGeneration: Long,
    val epoch: Long
)

internal class InventoryOwnerTracker {
    private val owner = MutableStateFlow(InventoryOwner(null, null, -1L, 0L))
    val state: StateFlow<InventoryOwner> = owner.asStateFlow()
    val current: InventoryOwner get() = owner.value

    fun select(storeId: String?, accountId: String?, sessionGeneration: Long): InventoryOwner {
        while (true) {
            val previous = owner.value
            if (previous.storeId == storeId && previous.accountId == accountId &&
                previous.sessionGeneration == sessionGeneration) return previous
            val next = InventoryOwner(storeId, accountId, sessionGeneration, previous.epoch + 1L)
            if (owner.compareAndSet(previous, next)) return next
        }
    }

    fun owns(ticket: InventoryOwner): Boolean = owner.value == ticket
}

internal val inventoryStateMutex = Mutex()
internal val inventoryOwners = InventoryOwnerTracker()
internal val stockRead = OwnedScopedRead<InventoryOwner>()
internal val stockBatchesRead = OwnedScopedRead<InventoryOwner>()
// Held under inventoryStateMutex. A denial rejects already-running companion reads as well.
internal var inventoryAccessRevision: Long = 0L

internal fun sameInventoryAccountAndStore(first: InventoryOwner, second: InventoryOwner): Boolean =
    !first.accountId.isNullOrBlank() && !first.storeId.isNullOrBlank() &&
        first.accountId == second.accountId && first.storeId == second.storeId

/** 401 is an expired cloud credential, not a decision revoking the cached store's permission. */
internal fun inventoryReadRevokesAccess(httpStatus: Int?, transportFailure: Boolean): Boolean =
    httpStatus == 403 && !transportFailure

internal fun InventoryLoadStatus.afterSessionChange(storeId: String?): InventoryLoadStatus =
    copy(storeId = storeId, loading = false, failure = if (accessDenied) failure else null)

/** All active-store publishers must go through this before exposing a new inventory scope. */
internal suspend fun publishActiveInventoryStoreId(storeId: String?, selectionIsCurrent: () -> Boolean = { true }) {
    DynamicCarts.prepareLegacyImport()
    DynamicCarts.flush()
    inventoryStateMutex.withLock {
        if (!selectionIsCurrent()) return@withLock
        val cleanId = storeId?.trim()?.takeIf { it.isNotEmpty() }
        val previous = inventoryOwners.current
        val owner = inventoryOwners.select(
            cleanId,
            userAccountState.payloadValue?.id?.takeIf { it.isNotBlank() },
            currentAuthenticatedSessionGeneration()
        )
        if (owner != previous) {
            stockRead.cancel()
            stockBatchesRead.cancel()
            inventoryAccessRevision++
            val retain = sameInventoryAccountAndStore(previous, owner)
            if (!retain) {
                selectStoreSubscriptionScope(cleanId)
                AnalyticsWorkspace.invalidate()
                storeAnalyticsDashboardState.emit(DataState.Empty())
                transactionsState.emit(DataState.Empty())
                cashRegisterState.emit(DataState.Empty())
                cashRegisterEventsState.emit(DataState.Empty())
                cashRegisterExtractionsState.emit(DataState.Empty())
                cashRegisterAmountState.emit(0.0)
                stockState.emit(DataState.Empty())
                stockBatchesState.emit(DataState.Empty())
                parentStoreStockState.emit(DataState.Empty())
                stockItemBranchAvailabilityState.emit(DataState.Empty())
                stockLoadStatusState.value = InventoryLoadStatus(storeId = cleanId)
                stockBatchesLoadStatusState.value = InventoryLoadStatus(storeId = cleanId)
            } else {
                // Fresh sign-in invalidates requests, not this same account/store's offline data.
                stockLoadStatusState.value = stockLoadStatusState.value.afterSessionChange(cleanId)
                stockBatchesLoadStatusState.value = stockBatchesLoadStatusState.value.afterSessionChange(cleanId)
            }
            stockBatchMoveResultState.emit(DataState.Empty())
            resetOperationLogViews(owner, retain)
        }
        if (owner != previous) latestTransactionReceiptSnapshotState.value = null
        if (owner != previous || !DynamicCarts.state.value.ready) {
            cartPersistenceHydratedState.value = false
            publishCartUiState(CartUiState())
            activeStoreIdState.value = cleanId
            DynamicCarts.adoptCurrent()
        } else activeStoreIdState.value = cleanId
    }
}

internal fun inventoryOwnerIsCurrent(owner: InventoryOwner): Boolean =
    inventoryOwners.owns(owner) &&
        !owner.storeId.isNullOrBlank() && !owner.accountId.isNullOrBlank() &&
        activeStoreIdState.value == owner.storeId &&
        userAccountState.payloadValue?.id == owner.accountId &&
        authenticatedSessionGenerationIsCurrent(owner.sessionGeneration)

internal fun inventoryCacheKey(name: String, owner: InventoryOwner): String =
    "inventory-v2:$name:${owner.accountId}:${owner.storeId}"

internal fun inventoryLoadFailureMessage(): List<LocalizedStringDataModel> = eventMessage("message.could_not_load_stock_try_again")

/** Cache hydration must never overwrite a cloud/local result (even an authoritative empty list). */
internal fun canHydrateInventory(hasPayload: Boolean, ownerIsCurrent: Boolean): Boolean =
    ownerIsCurrent && !hasPayload

fun inventoryCachedWhileOfflineMessage(): List<LocalizedStringDataModel> = eventMessage("message.offline_showing_saved_stock")

fun inventoryCacheWriteFailureMessage(): List<LocalizedStringDataModel> = eventMessage("message.stock_is_visible_but_could_not_be_saved_on_this_device")

/** Opaque runtime-only owner key for retained presentation indexes; never used as authorization. */
fun inventoryViewScopeKey(): String? = inventoryOwners.current.takeIf(::inventoryOwnerIsCurrent)?.let {
    "${it.accountId}:${it.storeId}:${it.sessionGeneration}:${it.epoch}"
}

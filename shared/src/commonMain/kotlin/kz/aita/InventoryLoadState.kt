package kz.aita

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Loading/failure is separate from the last usable inventory, never an invented empty list. */
data class InventoryLoadStatus(
    val storeId: String? = null,
    val loading: Boolean = false,
    val source: InventoryLoadSource = InventoryLoadSource.None,
    val failure: List<LocalizedStringDataModel>? = null,
    val accessDenied: Boolean = false
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

/** All active-store publishers must go through this before exposing a new inventory scope. */
internal suspend fun publishActiveInventoryStoreId(storeId: String?, selectionIsCurrent: () -> Boolean = { true }) {
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
            stockState.emit(DataState.Empty())
            stockBatchesState.emit(DataState.Empty())
            parentStoreStockState.emit(DataState.Empty())
            stockLoadStatusState.value = InventoryLoadStatus(storeId = cleanId)
            stockBatchesLoadStatusState.value = InventoryLoadStatus(storeId = cleanId)
        }
        activeStoreIdState.value = cleanId
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

internal fun inventoryLoadFailureMessage(): List<LocalizedStringDataModel> = listOf(
    LocalizedStringDataModel("main", "Could not load stock. Try again."),
    LocalizedStringDataModel("en", "Could not load stock. Try again."),
    LocalizedStringDataModel("ru", "Не удалось загрузить склад. Повторите попытку."),
    LocalizedStringDataModel("kk", "Қойманы жүктеу мүмкін болмады. Қайталап көріңіз.")
)

/** Cache hydration must never overwrite a cloud/local result (even an authoritative empty list). */
internal fun canHydrateInventory(hasPayload: Boolean, ownerIsCurrent: Boolean): Boolean =
    ownerIsCurrent && !hasPayload

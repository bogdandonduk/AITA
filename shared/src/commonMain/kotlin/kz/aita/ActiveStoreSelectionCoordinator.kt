package kz.aita

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class ActiveStoreOwner(val accountId: String, val sessionGeneration: Long)
internal data class ActiveStoreChoice(val storeId: String? = null, val explicitNone: Boolean = false,
    val parentStoreId: String? = null)
internal data class SavedActiveStoreChoice(val choice: ActiveStoreChoice, val pendingSync: Boolean)
internal data class ActiveStoreSelectionSnapshot(
    val owner: ActiveStoreOwner? = null,
    val choice: ActiveStoreChoice = ActiveStoreChoice(),
    val revision: Long = 0L,
    val hydrated: Boolean = false,
    val pendingSync: Boolean = false
)
internal enum class ActiveStoreSyncOutcome { SAVED, RETRY_LATER, REJECTED }

internal fun activeStoreSyncOutcome(negative: Boolean, transportFailure: Boolean, httpStatusCode: Int?): ActiveStoreSyncOutcome = when {
    !negative -> ActiveStoreSyncOutcome.SAVED
    !transportFailure && httpStatusCode in listOf(400, 403) -> ActiveStoreSyncOutcome.REJECTED
    // 401 requires sign-in/refresh; 404 can be a missing route on an older server, not lost store access.
    else -> ActiveStoreSyncOutcome.RETRY_LATER
}

/** One selection owner, one persistence writer, one ordered server writer. Database/profile
 * emissions are snapshots, not new user commands. An old A -> B -> A response cannot own new A.
 */
internal class ActiveStoreSelectionCoordinator(
    private val scope: CoroutineScope,
    private val ownerIsCurrent: (ActiveStoreOwner) -> Boolean,
    private val load: suspend (ActiveStoreOwner) -> SavedActiveStoreChoice?,
    private val persist: suspend (ActiveStoreSelectionSnapshot) -> Unit,
    private val publish: suspend (ActiveStoreSelectionSnapshot) -> Unit,
    private val sync: suspend (ActiveStoreSelectionSnapshot) -> ActiveStoreSyncOutcome,
    private val acknowledged: suspend (ActiveStoreSelectionSnapshot) -> Unit = {},
    private val syncTimeoutMillis: Long = 60_000L
) {
    private val state = MutableStateFlow(ActiveStoreSelectionSnapshot())
    private val storageMutex = Mutex()
    private val syncMutex = Mutex()
    val snapshot: ActiveStoreSelectionSnapshot get() = state.value

    fun isCurrent(candidate: ActiveStoreSelectionSnapshot): Boolean =
        state.value == candidate && (candidate.owner?.let(ownerIsCurrent) != false)

    private fun update(transform: (ActiveStoreSelectionSnapshot) -> ActiveStoreSelectionSnapshot): ActiveStoreSelectionSnapshot {
        while (true) {
            val old = state.value
            val next = transform(old)
            if (state.compareAndSet(old, next)) return next
        }
    }

    fun select(owner: ActiveStoreOwner?, storeId: String?, syncServer: Boolean, parentStoreId: String? = null): Job {
        val id = storeId?.trim()?.takeIf { it.isNotEmpty() }
        // Reserve synchronously before launching. Comparing only the displayed ID loses a fast B -> A.
        update { old ->
            val parent = parentStoreId?.takeIf { id != null && it.isNotBlank() && it != id }
                ?: old.choice.parentStoreId.takeIf { old.owner == owner && old.choice.storeId == id && id != null }
            val choice = ActiveStoreChoice(id, explicitNone = id == null && syncServer, parentStoreId = parent)
            if (old.owner == owner && old.hydrated && old.choice == choice) old
            else ActiveStoreSelectionSnapshot(owner, choice, old.revision + 1L, hydrated = true,
                pendingSync = syncServer && owner != null)
        }
        return scope.launch(start = CoroutineStart.UNDISPATCHED) {
            flush()
            if (syncServer) synchronize()
        }
    }

    suspend fun adopt(owner: ActiveStoreOwner, serverStoreId: String?, applyServerSelection: Boolean = true, preferServerSelection: Boolean = false) {
        storageMutex.withLock {
            if (!ownerIsCurrent(owner)) return@withLock
            val before = state.value
            if (before.owner == owner && before.hydrated) return@withLock
            val reserved = ActiveStoreSelectionSnapshot(owner = owner, revision = before.revision + 1L)
            if (!state.compareAndSet(before, reserved)) return@withLock
            publish(reserved) // Remove another account's visible store before any disk/network wait.
            val saved = load(owner)
            if (!isCurrent(reserved)) return@withLock
            val serverId = serverStoreId?.trim()?.takeIf { it.isNotEmpty() && applyServerSelection }
            val adopted = reserved.copy(
                choice = if (preferServerSelection && applyServerSelection && saved?.pendingSync != true) ActiveStoreChoice(serverId)
                    else saved?.choice ?: ActiveStoreChoice(serverId),
                hydrated = true,
                pendingSync = saved?.pendingSync == true
            )
            if (state.compareAndSet(reserved, adopted)) flushLocked()
        }
        retryPending()
    }

    /** An authoritative response may change another device's selection, but cannot undo a
     * local click made while that response was in flight or an unsynchronized offline choice. */
    suspend fun acceptRemote(owner: ActiveStoreOwner, serverStoreId: String?, revisionAtRequest: Long) {
        storageMutex.withLock {
            val current = state.value
            if (!isCurrent(current) || current.owner != owner || !current.hydrated || current.pendingSync ||
                current.revision != revisionAtRequest) return@withLock
            val id = serverStoreId?.trim()?.takeIf(String::isNotEmpty)
            if (current.choice.storeId == id && !current.choice.explicitNone) return@withLock
            val next = current.copy(choice = ActiveStoreChoice(id), revision = current.revision + 1L)
            if (state.compareAndSet(current, next)) flushLocked()
        }
    }

    fun retryPending(): Job = scope.launch {
        if (state.value.pendingSync) { flush(); synchronize() }
    }

    private suspend fun flush() = storageMutex.withLock { flushLocked() }
    private suspend fun flushLocked() {
        val selected = state.value
        if (!selected.hydrated || !isCurrent(selected)) return
        publish(selected)
        // Account-scoped persistence cannot echo an older value into the live selection.
        persist(selected)
    }

    private suspend fun synchronize() = syncMutex.withLock {
        while (true) {
            val selected = state.value
            if (!selected.pendingSync || selected.owner == null || !isCurrent(selected)) return@withLock
            val result = withTimeoutOrNull(syncTimeoutMillis) { sync(selected) } ?: ActiveStoreSyncOutcome.RETRY_LATER
            if (!isCurrent(selected)) continue // A newer choice is the next (and only next) request.
            when (result) {
                ActiveStoreSyncOutcome.RETRY_LATER -> return@withLock // Preserve visible choice + journal.
                ActiveStoreSyncOutcome.SAVED -> storageMutex.withLock {
                    val saved = selected.copy(pendingSync = false)
                    if (state.compareAndSet(selected, saved) && isCurrent(saved)) {
                        persist(saved)
                        if (isCurrent(saved)) acknowledged(saved)
                    }
                }
                ActiveStoreSyncOutcome.REJECTED -> storageMutex.withLock {
                    // A real permission rejection is not a transport failure. Clear only this exact
                    // selection; never resurrect a previous store or discard a newer user's choice.
                    val cleared = selected.copy(choice = ActiveStoreChoice(explicitNone = true),
                        revision = selected.revision + 1L, pendingSync = selected.choice.storeId != null)
                    if (state.compareAndSet(selected, cleared)) flushLocked()
                }
            }
        }
    }
}

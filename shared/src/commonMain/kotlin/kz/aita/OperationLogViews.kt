package kz.aita

import io.ktor.http.HttpMethod
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

/** Null records mean not loaded, never zero operations. Counts describe the returned (up to 500) rows. */
data class OperationLogScopeState(
    val records: List<OperationLogDataModel>? = null,
    val loading: Boolean = false,
    val failure: List<LocalizedStringDataModel>? = null,
    val cached: Boolean = false,
    val accessDenied: Boolean = false
)

data class OperationLogViews(
    val accountId: String? = null,
    val storeId: String? = null,
    val ownerEpoch: Long = 0L,
    val current: OperationLogScopeState = OperationLogScopeState(),
    val family: OperationLogScopeState = OperationLogScopeState()
)

private val mutableOperationLogViews = MutableStateFlow(OperationLogViews())
val operationLogViewsState: StateFlow<OperationLogViews> = mutableOperationLogViews.asStateFlow()
private val currentLogRead = OwnedScopedRead<InventoryOwner>()
private val familyLogRead = OwnedScopedRead<InventoryOwner>()

@Serializable
private data class CachedOperationLogScope(
    val records: List<OperationLogDataModel>? = null,
    val accessDenied: Boolean = false
)

internal fun operationLogScopeIsFamily(scope: String): Boolean = scope.equals(OPERATION_LOG_SCOPE_ROOT, true)
internal fun operationLogCacheKey(owner: InventoryOwner, family: Boolean): String =
    "cache_json:operation-logs-v2:${owner.accountId}:${owner.storeId}:${if (family) "family" else "current"}"

/** Called under inventoryStateMutex when a selected owner is published. */
internal fun resetOperationLogViews(owner: InventoryOwner, retain: Boolean) {
    currentLogRead.cancel()
    familyLogRead.cancel()
    val previous = mutableOperationLogViews.value
    mutableOperationLogViews.value = OperationLogViews(
        accountId = owner.accountId, storeId = owner.storeId, ownerEpoch = owner.epoch,
        current = if (retain) previous.current.copy(loading = false) else OperationLogScopeState(),
        family = if (retain) previous.family.copy(loading = false) else OperationLogScopeState()
    )
    if (!retain) operationLogsState.emit(DataState.Empty())
}

private fun logScope(family: Boolean): OperationLogScopeState =
    if (family) mutableOperationLogViews.value.family else mutableOperationLogViews.value.current

/** Caller holds inventoryStateMutex and has checked the owner. Legacy state remains CURRENT only. */
private fun publishLogScope(family: Boolean, next: OperationLogScopeState) {
    val previous = mutableOperationLogViews.value
    mutableOperationLogViews.value = if (family) previous.copy(family = next) else previous.copy(current = next)
    if (!family) {
        operationLogsState.emit(next.records?.let { DataState.Success(it, next.failure) } ?: DataState.Empty(next.failure))
    }
}

private fun operationLogReadFailureMessage(): List<LocalizedStringDataModel> = listOf(
    LocalizedStringDataModel("main", "Could not refresh operation logs. Try again when connected."),
    LocalizedStringDataModel("en", "Could not refresh operation logs. Try again when connected."),
    LocalizedStringDataModel("ru", "Не удалось обновить журнал. Повторите при подключении."),
    LocalizedStringDataModel("kk", "Журнал жаңартылмады. Байланыс орнағанда қайталаңыз.")
)

/** The two readers/caches never overwrite each other, including a delayed refresh from another tab. */
internal fun loadOperationLogScope(
    storeId: String,
    scope: String,
    onCompleted: ((DataState<List<OperationLogDataModel>>) -> Unit)? = null
) {
    val owner = inventoryOwners.current
    if (owner.storeId != storeId.trim() || !inventoryOwnerIsCurrent(owner)) return
    val family = operationLogScopeIsFamily(scope)
    val reader = if (family) familyLogRead else currentLogRead
    val started = reader.start(owner, GlobalScope, Dispatchers.ourIo) {
        val requestJob = currentCoroutineContext()[Job]
        var completion: DataState<List<OperationLogDataModel>>? = null
        try {
            val shouldHydrate = inventoryStateMutex.withLock {
                if (!inventoryOwnerIsCurrent(owner) || !reader.owns(owner, requestJob)) return@start
                val current = logScope(family)
                publishLogScope(family, current.copy(loading = true, failure = null))
                current.records == null && !current.accessDenied
            }
            if (shouldHydrate) {
                // Never hold the ownership lock while reading: a store switch/logout cancels this work.
                val cached = try {
                    withTimeoutOrNull(5_000L) {
                        getLocalKv(operationLogCacheKey(owner, family))?.let {
                            jsonBase.decodeFromString<CachedOperationLogScope>(it)
                        }
                    }
                } catch (failure: Exception) {
                    ensureConnectionOwnerActive(failure)
                    null
                }
                inventoryStateMutex.withLock {
                    if (!inventoryOwnerIsCurrent(owner) || !reader.owns(owner, requestJob)) return@start
                    val current = logScope(family)
                    if (cached != null && current.records == null && !current.accessDenied) {
                        publishLogScope(family, current.copy(
                            records = cached.records.takeUnless { cached.accessDenied },
                            cached = true, accessDenied = cached.accessDenied
                        ))
                    }
                }
            }
            val response = withTimeoutOrNull(45_000L) {
                networkRequest<List<OperationLogDataModel>, Unit>(
                    method = HttpMethod.Get,
                    endpointUrl = globalAppConfigurationState.payloadValue.getOperationLogsPath.first,
                    headers = mapOf("store_id" to requireNotNull(owner.storeId)),
                    query = mapOf("scope" to if (family) OPERATION_LOG_SCOPE_ROOT else OPERATION_LOG_SCOPE_CURRENT),
                    expectedSessionGeneration = owner.sessionGeneration
                )
            }
            inventoryStateMutex.withLock {
                if (!inventoryOwnerIsCurrent(owner) || !reader.owns(owner, requestJob)) return@withLock
                val payload = response?.payload
                val failed = response?.message?.takeIf { it.isNotEmpty() } ?: operationLogReadFailureMessage()
                val next = when {
                    response != null && !response.negative && payload != null -> OperationLogScopeState(records = payload)
                    response != null && inventoryReadRevokesAccess(response.httpStatusCode, response.transportFailure) ->
                        OperationLogScopeState(failure = failed, accessDenied = true)
                    else -> logScope(family).copy(loading = false, failure = failed)
                }
                // One envelope records both data and a denial; an empty/failed load is not a fake cache.
                if ((next.records != null && next.failure == null) || next.accessDenied) {
                    try {
                        val saved = withTimeoutOrNull(5_000L) {
                            putLocalKv(operationLogCacheKey(owner, family), jsonBase.encodeToString(
                                CachedOperationLogScope(next.records, next.accessDenied)
                            ))
                            true
                        } ?: false
                        if (!saved) logCloudConnectionDiagnostic("Operation log cache write timed out")
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { logCloudConnectionDiagnostic("Operation log cache write failed") }
                }
                if (!inventoryOwnerIsCurrent(owner) || !reader.owns(owner, requestJob)) return@withLock
                publishLogScope(family, next)
                completion = if (response != null && !response.negative && payload != null)
                    DataState.Success(payload, response.message) else DataState.Empty(failed)
            }
        } catch (failure: Exception) {
            ensureConnectionOwnerActive(failure)
            inventoryStateMutex.withLock {
                if (inventoryOwnerIsCurrent(owner) && reader.owns(owner, requestJob)) {
                    val message = operationLogReadFailureMessage()
                    publishLogScope(family, logScope(family).copy(loading = false, failure = message))
                    completion = DataState.Empty(message)
                }
            }
        } finally {
            withContext(NonCancellable) {
                inventoryStateMutex.withLock {
                    if (inventoryOwners.owns(owner) && reader.owns(owner, requestJob)) publishLogScope(family, logScope(family).copy(loading = false))
                }
            }
        }
        if (inventoryOwnerIsCurrent(owner)) completion?.let { onCompleted?.invoke(it) }
    }
    if (!started && onCompleted != null) {
        val existing = reader.jobFor(owner)
        GlobalScope.launch(Dispatchers.ourIo) {
            existing?.join()
            val result = inventoryStateMutex.withLock {
                if (!inventoryOwnerIsCurrent(owner)) return@launch
                val loaded = logScope(family)
                val rows = loaded.records
                if (rows != null && loaded.failure == null && !loaded.accessDenied) DataState.Success(rows)
                else DataState.Empty(loaded.failure ?: operationLogReadFailureMessage())
            }
            if (inventoryOwnerIsCurrent(owner)) onCompleted(result)
        }
    }
}

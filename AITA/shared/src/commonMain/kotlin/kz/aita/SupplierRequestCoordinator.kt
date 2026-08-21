package kz.aita

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal const val SUPPLIER_WORKSPACE_AUTO_REFRESH_MIN_INTERVAL_MILLIS: Long = 15_000L

internal data class SupplierWorkspaceRefreshDecision(
    val refreshBaseWorkspace: Boolean,
    val refreshContracts: Boolean
)

internal fun supplierWorkspaceRefreshDecision(
    nowMillis: Long,
    lastBaseRefreshAtMillis: Long,
    lastContractsRefreshAtMillis: Long,
    includeContracts: Boolean,
    force: Boolean,
    minIntervalMillis: Long = SUPPLIER_WORKSPACE_AUTO_REFRESH_MIN_INTERVAL_MILLIS
): SupplierWorkspaceRefreshDecision {
    fun refreshDue(lastRefreshAtMillis: Long): Boolean =
        force ||
                lastRefreshAtMillis <= 0L ||
                nowMillis < lastRefreshAtMillis ||
                nowMillis - lastRefreshAtMillis >= minIntervalMillis.coerceAtLeast(0L)

    return SupplierWorkspaceRefreshDecision(
        refreshBaseWorkspace = refreshDue(lastBaseRefreshAtMillis),
        refreshContracts = includeContracts && refreshDue(lastContractsRefreshAtMillis)
    )
}

/**
 * Coalesces identical concurrent reads into one request while allowing every caller to receive the result.
 * Different keys remain distinct and can be serialized separately by the owning repository when necessary.
 */
internal class SingleFlightRequestCoordinator<Key, Result> {
    private val mutex = Mutex()
    private val inFlight = mutableMapOf<Key, CompletableDeferred<Result>>()

    suspend fun run(key: Key, request: suspend () -> Result): Result {
        var ownsRequest = false
        val deferred = mutex.withLock {
            inFlight[key] ?: CompletableDeferred<Result>().also { created ->
                inFlight[key] = created
                ownsRequest = true
            }
        }

        if (ownsRequest) {
            try {
                deferred.complete(request())
            } catch (throwable: Throwable) {
                deferred.completeExceptionally(throwable)
            } finally {
                withContext(NonCancellable) {
                    mutex.withLock {
                        if (inFlight[key] === deferred) {
                            inFlight.remove(key)
                        }
                    }
                }
            }
        }

        return deferred.await()
    }
}

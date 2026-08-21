package kz.aita.server.payments

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Coalesces concurrent identical provider operations. It is intentionally process-local; durable
 * idempotency remains enforced by PostgreSQL unique constraints and row locks.
 */
class PaymentOperationCoordinator {
    private val guard = Mutex()
    private val operations = mutableMapOf<String, CompletableDeferred<Result<Any?>>>()

    @Suppress("UNCHECKED_CAST")
    suspend fun <T> singleFlight(key: String, operation: suspend () -> T): T {
        require(key.isNotBlank())
        var owner = false
        val deferred = guard.withLock {
            operations[key]?.also { return@withLock it } ?: CompletableDeferred<Result<Any?>>().also {
                operations[key] = it
                owner = true
            }
        }
        if (!owner) return deferred.await().getOrThrow() as T
        try {
            val value = operation()
            deferred.complete(Result.success(value as Any?))
            return value
        } catch (throwable: Throwable) {
            deferred.complete(Result.failure(throwable))
            throw throwable
        } finally {
            guard.withLock { if (operations[key] === deferred) operations.remove(key) }
        }
    }
}

/** A bounded replay cache for webhook event IDs; PostgreSQL remains the source of truth. */
class RecentWebhookEventCache(
    private val maximumEntries: Int = 1_024,
) {
    private val values = ConcurrentHashMap<String, Long>()

    fun rememberIfNew(key: String, nowEpochMs: Long, ttlMs: Long): Boolean {
        require(ttlMs > 0)
        values.entries.removeIf { nowEpochMs - it.value >= ttlMs }
        if (values.size >= maximumEntries) {
            values.entries.minByOrNull { it.value }?.let { values.remove(it.key, it.value) }
        }
        return values.putIfAbsent(key, nowEpochMs) == null
    }
}

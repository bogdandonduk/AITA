package kz.aita.auth

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One bounded bootstrap per session. A Compose disposal does not interrupt account publication.
 * Failed/completed attempts may be retried without repeating the already consumed login code.
 */
internal class AuthenticationCompletionCoordinator<T>(
    private val scope: CoroutineScope,
    private val complete: suspend (Long) -> T
) {
    private val mutex = Mutex()
    private var generation: Long? = null
    private var pending: Deferred<T>? = null

    suspend fun await(expectedGeneration: Long): T {
        val work = mutex.withLock {
            val current = pending
            if (generation == expectedGeneration && current?.isActive == true) current
            else {
                current?.cancel()
                generation = expectedGeneration
                scope.async { complete(expectedGeneration) }.also { pending = it }
            }
        }
        return work.await()
    }

    suspend fun cancel(expectedGeneration: Long) = mutex.withLock {
        if (generation == expectedGeneration) {
            pending?.cancel()
            pending = null
            generation = null
        }
    }
}

package kz.aita

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Coalesces bursts, but an invalidation received during a read always gets a trailing read.
 * A new owner replaces the old worker; its completion cannot clear the replacement.
 */
internal class TrailingInvalidationRunner<Key> {
    private class Run<Key>(val key: Key, var dirty: Boolean = true, var job: Job? = null)
    private val mutex = Mutex()
    private var current: Run<Key>? = null

    suspend fun invalidate(key: Key, scope: CoroutineScope, context: CoroutineContext,
        read: suspend () -> Unit) {
        mutex.withLock {
            val previous = current
            if (previous != null && previous.key == key && previous.job?.isActive == true) {
                previous.dirty = true
                return
            }
            previous?.job?.cancel()
            val run = Run(key)
            current = run
            run.job = scope.launch(context, start = CoroutineStart.LAZY) {
                try {
                    while (isActive) {
                        val owns = mutex.withLock {
                            if (current !== run) false else { run.dirty = false; true }
                        }
                        if (!owns) break
                        read()
                        val again = mutex.withLock {
                            if (current !== run) false
                            else if (run.dirty) true
                            else { current = null; false }
                        }
                        if (!again) break
                    }
                } finally {
                    withContext(NonCancellable) { mutex.withLock { if (current === run) current = null } }
                }
            }
            run.job?.start()
        }
    }

    suspend fun cancel() = mutex.withLock { current?.job?.cancel(); current = null }
}

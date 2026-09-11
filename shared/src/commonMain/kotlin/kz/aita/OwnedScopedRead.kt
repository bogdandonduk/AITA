package kz.aita

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Coalesces one scope's reads; a replaced owner never waits behind its abandoned request. */
internal class OwnedScopedRead<Key> {
    private data class Running<Key>(val key: Key, val job: Job)
    private val current = MutableStateFlow<Running<Key>?>(null)

    fun start(
        key: Key,
        scope: CoroutineScope,
        context: CoroutineContext,
        block: suspend CoroutineScope.() -> Unit
    ): Boolean {
        val candidate = scope.launch(context, start = CoroutineStart.LAZY, block = block)
        val next = Running(key, candidate)
        while (true) {
            val previous = current.value
            if (previous != null && previous.key == key && !previous.job.isCompleted && !previous.job.isCancelled) {
                candidate.cancel()
                return false
            }
            if (current.compareAndSet(previous, next)) {
                // Registered before either job can finish, including on an immediate dispatcher.
                previous?.job?.cancel()
                break
            }
        }
        candidate.invokeOnCompletion { current.compareAndSet(next, null) }
        candidate.start()
        return true
    }

    fun owns(key: Key, job: Job?): Boolean = current.value?.let { it.key == key && it.job === job } == true

    /** Used by an optional completion listener joining an already-running read. */
    fun jobFor(key: Key): Job? = current.value?.takeIf { it.key == key }?.job

    fun cancel() {
        while (true) {
            val previous = current.value
            if (current.compareAndSet(previous, null)) {
                previous?.job?.cancel()
                return
            }
        }
    }
}

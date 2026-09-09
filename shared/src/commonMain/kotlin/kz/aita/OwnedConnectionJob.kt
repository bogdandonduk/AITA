package kz.aita

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A monitor/socket is registered before it can run, including on an immediate dispatcher. */
internal class OwnedConnectionJob {
    internal data class State(val job: Job? = null, val connected: Boolean = false)

    private val mutableState = MutableStateFlow(State())
    val state: StateFlow<State> = mutableState.asStateFlow()
    val isRunning: Boolean get() = mutableState.value.job?.let { !it.isCompleted && !it.isCancelled } == true
    val isConnected: Boolean get() = mutableState.value.connected

    fun owns(job: Job?): Boolean = job != null && mutableState.value.job === job

    fun startIfIdle(
        scope: CoroutineScope,
        context: CoroutineContext,
        block: suspend CoroutineScope.() -> Unit
    ): Boolean {
        val candidate = scope.launch(context, start = CoroutineStart.LAZY, block = block)
        while (true) {
            val previous = mutableState.value
            if (previous.job?.let { !it.isCompleted && !it.isCancelled } == true) {
                candidate.cancel()
                return false
            }
            if (mutableState.compareAndSet(previous, State(candidate))) break
        }
        candidate.invokeOnCompletion { clear(candidate) }
        // A concurrent cancel may already have detached this job. start() cannot revive it.
        candidate.start()
        return true
    }

    /** Connection publication and ownership share one atomic value, not a check then write. */
    fun setConnected(job: Job?, connected: Boolean): Boolean {
        if (job == null) return false
        while (true) {
            val current = mutableState.value
            if (current.job !== job) return false
            if (mutableState.compareAndSet(current, current.copy(connected = connected))) return true
        }
    }

    fun clear(job: Job?) {
        if (job == null) return
        while (true) {
            val current = mutableState.value
            if (current.job !== job) return
            if (mutableState.compareAndSet(current, State())) return
        }
    }

    /** Detach first. A late finally/close from this job cannot clear a replacement socket. */
    fun cancel(): Job? {
        while (true) {
            val current = mutableState.value
            if (mutableState.compareAndSet(current, State())) {
                current.job?.cancel()
                return current.job
            }
        }
    }
}

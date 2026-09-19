package kz.aita

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet

/** Request identities let visual feedback stop without waiting for unrelated background work. */
object NetworkActionActivity {
    private val running = MutableStateFlow<Set<Long>>(emptySet())
    val active = running.asStateFlow()
    private val sequence = MutableStateFlow(0L)
    fun begin(): Long = sequence.updateAndGet { it + 1 }.also { id -> running.update { it + id } }
    fun finish(id: Long) { running.update { it - id } }
}

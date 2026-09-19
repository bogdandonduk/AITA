package kz.aita

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/** A hidden browser may freeze timers for hours. Keep one suspended refresh, not a backlog. */
internal class ClientBackgroundWorkGate(initiallyActive: Boolean = true) {
    private val active = MutableStateFlow(initiallyActive)
    val isActive: Boolean get() = active.value
    fun setActive(value: Boolean): Boolean = active.compareAndSet(!value, value)
    suspend fun awaitActive() { active.first { it } }
}

private val clientBackgroundWork = ClientBackgroundWorkGate()
internal val clientBackgroundWorkIsActive: Boolean get() = clientBackgroundWork.isActive

/** Used only by refresh timers/read scheduling; explicit writes and durable journals keep running. */
suspend fun awaitClientBackgroundWork() = clientBackgroundWork.awaitActive()

/** Native platforms keep their existing lifecycle. The browser bridge owns these hints. */
fun setBrowserPageActive(active: Boolean) {
    if (!clientBackgroundWork.setActive(active)) return
    if (!active) {
        stopRealtimeUpdates()
    } else {
        reconnectVisibleBrowserPage()
    }
}

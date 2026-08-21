package kz.aita

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Assigns explicit ownership to asynchronous UI work. Only the newest ticket
 * may publish, which protects rapid A → B → A selections even when a lower
 * layer converts coroutine cancellation into an ordinary result.
 */
internal class AitaLatestUiRequestOwner {
    private var generation: Long = 0L

    fun begin(): Long {
        generation = if (generation == Long.MAX_VALUE) 1L else generation + 1L
        return generation
    }

    fun owns(ticket: Long): Boolean =
        ticket != 0L && ticket == generation

    fun invalidate() {
        generation = if (generation == Long.MAX_VALUE) 1L else generation + 1L
    }
}

@Composable
internal fun rememberAitaLatestUiRequestOwner(): AitaLatestUiRequestOwner =
    remember { AitaLatestUiRequestOwner() }

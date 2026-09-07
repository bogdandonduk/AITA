package kz.aita

import androidx.compose.runtime.*
import kotlinx.coroutines.delay

/**
 * A tiny lifecycle-owned clock for time-sensitive Supplier views.
 *
 * A server snapshot timestamp is evidence of when the snapshot was produced; it is not a
 * clock. Keeping this pulse in composition means due/overdue views continue to advance while
 * the app remains open, without forcing network traffic or retaining a screen-owned job.
 */
@Composable
internal fun <T> rememberSupplierLiveNow(
    pulseMillis: Long = 30_000L,
    readNow: () -> T,
): T {
    val latestReadNow by rememberUpdatedState(readNow)
    var current by remember { mutableStateOf(readNow()) }

    LaunchedEffect(pulseMillis) {
        val safePulseMillis = pulseMillis.coerceAtLeast(1_000L)
        while (true) {
            delay(safePulseMillis)
            current = latestReadNow()
        }
    }

    return current
}

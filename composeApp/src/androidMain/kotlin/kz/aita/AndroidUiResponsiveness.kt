package kz.aita

import android.os.Debug
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

/** Records a bounded main-thread stack only while this activity is visible and unresponsive.
 * Uses the existing diagnostic consent, redaction and upload queue; no user text is captured.
 */
internal class AndroidUiResponsiveness {
    private val handler = Handler(Looper.getMainLooper())
    private var job: Job? = null
    fun start() {
        if (job?.isActive == true) return
        job = CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            while (isActive) {
                val answered = AtomicBoolean(false)
                val pulse = Runnable { answered.set(true) }
                handler.post(pulse)
                try {
                    delay(5_000)
                    if (!answered.get() && !Debug.isDebuggerConnected() && RuntimeDiagnostics.state.value.enabled) {
                        val blocked = IllegalStateException().apply { stackTrace = Looper.getMainLooper().thread.stackTrace.take(48).toTypedArray() }
                        RuntimeDiagnostics.capture(blocked, "android.ui_stall")
                        // One sample per continuous stall, not an ever-growing error queue.
                        while (!answered.get() && isActive) delay(1_000)
                    }
                } finally { handler.removeCallbacks(pulse) }
            }
        }
    }
    fun stop() { job?.cancel(); job = null }
}

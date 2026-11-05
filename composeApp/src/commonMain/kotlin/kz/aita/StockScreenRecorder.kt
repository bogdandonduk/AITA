package kz.aita

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** An explicitly selected top-level window; the id is opaque and valid only for this session. */
data class StockCaptureWindow(val id: String, val title: String)

/** Pixel bounds relative to the captured window. Confidence is normalized to 0..1. */
data class StockScreenWord(
    val text: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val confidence: Float = 1f,
)

enum class StockScreenSource { UI_AUTOMATION, OCR }

/** Never send these fields to telemetry: they are private, locally recognized stock data. */
data class StockScreenFrame(
    val id: String,
    val capturedAtMillis: Long,
    val sourceTitle: String,
    val words: List<StockScreenWord>,
    val source: StockScreenSource,
    val width: Int = 0,
    val height: Int = 0,
)

enum class StockCaptureStatus { IDLE, RECORDING, PAUSED, STOPPED, ERROR }

/** Message codes contain no titles, recognized text, screenshots or exception messages. */
data class StockCaptureState(
    val status: StockCaptureStatus = StockCaptureStatus.IDLE,
    val messageCode: String? = null,
    val frameCount: Int = 0,
)

interface StockScreenRecorder {
    val supported: Boolean
    val state: StateFlow<StockCaptureState>
    val frames: SharedFlow<StockScreenFrame>
    suspend fun windows(): List<StockCaptureWindow>
    /** Requires informed user consent and a window explicitly selected in the current UI. */
    suspend fun start(windowId: String, languageTag: String)
    fun pause()
    fun resume()
    fun stop()
    fun close()
}

expect fun createStockScreenRecorder(): StockScreenRecorder

internal class UnavailableStockScreenRecorder : StockScreenRecorder {
    override val supported = false
    override val state = MutableStateFlow(StockCaptureState(messageCode = "windows_only"))
    override val frames = MutableSharedFlow<StockScreenFrame>()
    override suspend fun windows(): List<StockCaptureWindow> = emptyList()
    override suspend fun start(windowId: String, languageTag: String) {
        state.value = StockCaptureState(StockCaptureStatus.ERROR, "windows_only")
    }
    override fun pause() = Unit
    override fun resume() = Unit
    override fun stop() = Unit
    override fun close() = Unit
}

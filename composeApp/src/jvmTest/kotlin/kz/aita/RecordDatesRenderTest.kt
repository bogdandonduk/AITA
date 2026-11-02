package kz.aita

import aita.composeapp.generated.resources.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.lang.reflect.Proxy
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import javax.swing.SwingUtilities
import kotlin.test.*

class RecordDatesRenderTest {
    private fun render(language: String, width: Int, scale: Float) {
        val failure = AtomicReference<Throwable?>()
        SwingUtilities.invokeAndWait {
            val field = AppConfiguration::class.java.getDeclaredField("stateValues")
            val previous = field.get(AppConfiguration)
            try {
                val base = MarketBrowseControlsRenderTest().presentation(language, width)
                val icons = mapOf("getDrawableResIconExpandLess" to Res.drawable._8_0,
                    "getDrawableResIconExpandMore" to Res.drawable._7_0,
                    "getDrawableResIconTransactionHistory" to Res.drawable._20_0,
                    "getDrawableResIconCancel" to Res.drawable._2_0,
                    "getDrawableResIconSort" to Res.drawable._61_0).mapValues { MutableStateFlow(it.value) }
                AppConfiguration.stateValues = Proxy.newProxyInstance(AppConfiguration.StateValues::class.java.classLoader,
                    arrayOf(AppConfiguration.StateValues::class.java)) { _, method, args ->
                    icons[method.name] ?: method.invoke(base, *(args ?: emptyArray()))
                } as AppConfiguration.StateValues
                var bounds: Rect? = null
                val scene = ImageComposeScene(width, 1200, Density(1f, scale)) {
                    Column(Modifier.fillMaxWidth().background(Color(0xFF17181B)).padding(12.dp)
                        .onGloballyPositioned { bounds = it.boundsInRoot() }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        AppConfiguration.RecordDates(1793592000000L, 1793606400000L)
                        AppConfiguration.RecordListTools(RecordListOptions(dateField="modified", fromDate="2026-11-01", throughDate="2026-11-02", sort="modified"), {}, collapsible=false)
                    }
                }
                try {
                    repeat(16) { scene.render((it + 1) * 16_000_000L).close() }
                    val layout = assertNotNull(bounds)
                    assertTrue(layout.width <= width && layout.bottom < 1200, "Date controls must fit at the selected text scale")
                    System.getenv("AITA_DATE_RENDER_DIR")?.let { path ->
                        File(path).mkdirs()
                        scene.render(272_000_000L).use { it.encodeToData()?.use { png -> File(path, "dates-$language-$width.png").writeBytes(png.bytes) } }
                    }
                } finally { scene.close() }
            } catch (error: Throwable) { failure.set(error) }
            finally { field.set(AppConfiguration, previous) }
        }
        failure.get()?.let { throw it }
    }
    @Test fun narrowRussianWithLargeText() = render("ru", 390, 1.4f)
    @Test fun wideEnglish() = render("en", 1024, 1f)
}

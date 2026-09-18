package kz.aita

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import javax.swing.SwingUtilities
import kotlin.test.*

class AppStateSettingsRenderTest {
    private fun render(language: String, width: Int, scale: Float) {
        val failure = AtomicReference<Throwable?>()
        SwingUtilities.invokeAndWait {
            val field = AppConfiguration::class.java.getDeclaredField("stateValues")
            val previous = field.get(AppConfiguration)
            try {
                AppConfiguration.stateValues = MarketBrowseControlsRenderTest().presentation(language, width)
                var bounds: Rect? = null
                val scene = ImageComposeScene(width, 1000, Density(1f, scale)) {
                    Box(Modifier.fillMaxSize().background(Color(0xFF17181B)).onGloballyPositioned { bounds = it.boundsInRoot() }) {
                        AppConfiguration.AppStateSettingsPane(AppStateUi(status = "saved", signedIn = true))
                    }
                }
                try {
                    repeat(12) { scene.render((it + 1) * 16_000_000L).close() }
                    assertEquals(width.toFloat(), assertNotNull(bounds).width)
                    System.getenv("AITA_MARKET_RENDER_DIR")?.let { path ->
                        File(path).mkdirs()
                        scene.render(224_000_000L).use { it.encodeToData()?.use { png -> File(path, "app-state-$language-$width.png").writeBytes(png.bytes) } }
                    }
                } finally { scene.close() }
            } catch (error: Throwable) { failure.set(error) }
            finally { field.set(AppConfiguration, previous) }
        }
        failure.get()?.let { throw it }
    }
    @Test fun narrowRussianSettingsWithLargeText() = render("ru", 390, 1.4f)
    @Test fun wideEnglishSettings() = render("en", 1024, 1f)
}

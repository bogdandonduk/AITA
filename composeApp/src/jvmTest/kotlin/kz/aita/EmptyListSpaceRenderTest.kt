package kz.aita

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.util.concurrent.atomic.AtomicReference
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Real Compose measurement on a raster scene: no application session, network or display window. */
class EmptyListSpaceRenderTest {
    private fun onUiThread(block: () -> Unit) {
        val failure = AtomicReference<Throwable?>()
        SwingUtilities.invokeAndWait { try { block() } catch (error: Throwable) { failure.set(error) } }
        failure.get()?.let { throw it }
    }

    private fun verifyCenter(width: Int, height: Int, header: Int, density: Float = 1f, resizedHeight: Int? = null) = onUiThread {
        var bounds: Rect? = null
        val headerHeight = mutableIntStateOf(header)
        val scene = ImageComposeScene(width, height, Density(density)) {
            val list = rememberLazyListState()
            LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(12.dp)) {
                if (headerHeight.intValue > 0) item("header") {
                    Box(Modifier.fillMaxWidth().height(headerHeight.intValue.dp))
                }
                item("empty") {
                    Box(Modifier.fillMaxWidth().remainingListSpace(list, "empty"), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(20.dp).onGloballyPositioned { bounds = it.boundsInRoot() })
                    }
                }
            }
        }
        try {
            fun settle() { repeat(16) { scene.render(it * 16_000_000L).close() } }
            fun check(heightPixels: Int) {
                val measured = assertNotNull(bounds)
                assertEquals(width / 2f, measured.center.x, 1f)
                assertEquals((heightPixels + headerHeight.intValue * density) / 2f, measured.center.y, 1.5f)
            }
            settle()
            check(height)
            if (resizedHeight != null) {
                scene.constraints = Constraints(maxWidth = width, maxHeight = resizedHeight)
                settle()
                check(resizedHeight)
            }
        } finally { scene.close() }
    }

    @Test fun centersWithoutHeaders() = verifyCenter(400, 800, 0)
    @Test fun centersUnderScrollableHeader() = verifyCenter(400, 800, 120)
    @Test fun centersAtHighDensity() = verifyCenter(800, 1600, 120, density = 2f)
    @Test fun recentersAfterViewportResize() = verifyCenter(500, 900, 150, resizedHeight = 620)
}

package kz.aita

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.util.concurrent.atomic.AtomicReference
import javax.swing.SwingUtilities
import kotlin.test.*

/** Real pointer input and child scrolling, using the production sheet gesture modifiers. */
class AitaSheetDragRenderTest {
    private class Fixture(width: Int) : AutoCloseable {
        val drag = AitaSheetDragState().apply { height = 600f }
        var dismissals = 0
        private var frame = 0L
        val scene = ImageComposeScene(width, 640, Density(1f)) {
            val dismiss = { dismissals++; Unit }
            val nested = rememberSheetNestedScroll(drag, 1f, dismiss)
            Column(Modifier.fillMaxSize().offset { IntOffset(0, drag.offset.roundToInt()) }
                .nestedScroll(nested).observeSheetPointer(drag, 1f, dismiss)) {
                Box(Modifier.fillMaxWidth().height(72.dp).background(Color.Gray).aitaSheetHandle(drag, 1f, dismiss))
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                    Box(Modifier.fillMaxWidth().height(1200.dp).background(Color.White))
                }
            }
        }
        fun frames(count: Int = 20) { repeat(count) { scene.render(++frame * 16_000_000L).close() } }
        fun event(type: PointerEventType, y: Float, pointer: PointerType, scroll: Float = 0f) {
            scene.sendPointerEvent(type, Offset(60f, y), scrollDelta = Offset(0f, scroll),
                timeMillis = ++frame * 16, type = pointer)
            frames(1)
        }
        fun pull(start: Float, distance: Float, type: PointerType) {
            event(PointerEventType.Press, start, type)
            repeat(8) { event(PointerEventType.Move, start + distance * (it + 1) / 8, type) }
        }
        override fun close() = scene.close()
    }
    private fun onUi(block: () -> Unit) {
        val failure = AtomicReference<Throwable?>()
        SwingUtilities.invokeAndWait { try { block() } catch (error: Throwable) { failure.set(error) } }
        failure.get()?.let { throw it }
    }
    @Test fun touchAndMousePullHeaderAndDismissOnce() = onUi {
        for (width in listOf(280, 720)) for (type in listOf(PointerType.Mouse, PointerType.Touch)) {
            Fixture(width).use { f ->
                f.frames()
                f.pull(30f, 210f, type)
                assertTrue(f.drag.offset > 150f, "Header did not follow $type at width $width: ${f.drag.offset}")
                f.event(PointerEventType.Release, 240f, type)
                f.frames()
                assertEquals(1, f.dismissals)
            }
        }
    }
    @Test fun shortHeaderPullReturnsAndMouseWheelDoesNotDragSheet() = onUi {
        Fixture(320).use { f ->
            f.frames()
            f.pull(30f, 18f, PointerType.Mouse)
            f.event(PointerEventType.Release, 48f, PointerType.Mouse)
            f.frames()
            assertEquals(0f, f.drag.offset, 1f)
            assertEquals(0, f.dismissals)
            f.event(PointerEventType.Scroll, 150f, PointerType.Mouse, -20f)
            f.frames()
            assertEquals(0f, f.drag.offset, 1f)
            assertEquals(0, f.dismissals)
        }
    }
    @Test fun downwardTouchAtTopOfContentPullsSheet() = onUi {
        Fixture(320).use { f ->
            f.frames()
            f.pull(160f, 210f, PointerType.Touch)
            assertTrue(f.drag.offset > 150f, "Unconsumed content drag did not reach sheet: ${f.drag.offset}; down=${f.drag.pointerDown}")
            f.event(PointerEventType.Release, 370f, PointerType.Touch)
            f.frames()
            assertEquals(1, f.dismissals)
        }
    }
}

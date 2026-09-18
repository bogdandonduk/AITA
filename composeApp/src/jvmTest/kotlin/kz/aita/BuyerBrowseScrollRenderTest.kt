package kz.aita

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.*
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.util.concurrent.atomic.AtomicReference
import javax.swing.SwingUtilities
import kotlin.test.*

/** Uses the production scroll lifecycle with real lazy layouts, including temporary short
 * loading windows. No authenticated session or network client is started by the fixture.
 */
class BuyerBrowseScrollRenderTest {
    private fun onUi(block: () -> Unit) {
        val failure = AtomicReference<Throwable?>()
        SwingUtilities.invokeAndWait { try { block() } catch (error: Throwable) { failure.set(error) } }
        failure.get()?.let { throw it }
    }
    private class Fixture(width: Int) : AutoCloseable {
        val browse = BuyerBrowseNavigation(false)
        var visible by mutableStateOf(true)
        var ready by mutableStateOf(true)
        var target by mutableStateOf<Pair<Int, Int>?>(null)
        private var frame = 0L
        val scene = ImageComposeScene(width, 320, Density(1f)) {
            if (visible) {
                val grid = rememberBuyerBrowseGrid(browse, ready)
                LaunchedEffect(target) { target?.let { grid.scrollToItem(it.first, it.second) } }
                LazyVerticalGrid(GridCells.Adaptive(200.dp), Modifier.fillMaxSize(), state = grid) {
                    items(if (ready) 100 else 2) { Box(Modifier.fillMaxWidth().height(100.dp)) }
                }
            }
        }
        fun frames() { repeat(24) { scene.render(++frame * 16_000_000L).close() } }
        fun scroll(index: Int, offset: Int) { target = index to offset; frames(); target = null; frames() }
        override fun close() = scene.close()
    }

    @Test fun returnRestoresPositionAfterLoadingInNarrowAndWideLayouts() = onUi {
        for (width in listOf(320, 960)) Fixture(width).use { f ->
            f.frames(); f.scroll(28, 17)
            val before = f.browse.point()
            assertTrue(before.index >= 28)
            f.visible = false; f.frames()
            f.ready = false; f.visible = true; f.frames()
            assertEquals(before, f.browse.scrollRestore)
            f.ready = true; f.frames()
            assertEquals(before.index, f.browse.grid.firstVisibleItemIndex)
            assertEquals(before.offset, f.browse.grid.firstVisibleItemScrollOffset)
            assertNull(f.browse.scrollRestore)
        }
    }

    @Test fun leavingAgainBeforeReadCompletesDoesNotReplaceSavedPositionWithPlaceholder() = onUi {
        Fixture(320).use { f ->
            f.frames(); f.scroll(38, 21)
            val before = f.browse.point()
            f.visible = false; f.frames(); f.ready = false; f.visible = true; f.frames()
            f.visible = false; f.frames()
            assertEquals(before, f.browse.scrollRestore)
            f.visible = true; f.frames(); f.ready = true; f.frames()
            assertEquals(before.index, f.browse.grid.firstVisibleItemIndex)
            assertEquals(before.offset, f.browse.grid.firstVisibleItemScrollOffset)
        }
    }

    @Test fun changingSearchRetiresOldScrollPositionEvenBeforeResponseArrives() = onUi {
        Fixture(320).use { f ->
            f.frames(); f.scroll(28, 17)
            f.visible = false; f.frames(); f.ready = false; f.visible = true; f.frames()
            f.browse.search.value = "Coffee"; f.browse.appliedSearch.value = "Coffee"; f.frames()
            assertNull(f.browse.scrollRestore)
            f.ready = true; f.frames()
            assertEquals(0, f.browse.grid.firstVisibleItemIndex)
        }
    }

    @Test fun visitingShopFromAnotherScreenStartsAtTopAndBackRestoresSearchPosition() = onUi {
        Fixture(320).use { f ->
            f.frames(); f.scroll(28, 17)
            val before = f.browse.point()
            f.visible = false; f.frames()
            f.browse.visitShop("00000000-0000-0000-0000-000000000001")
            f.visible = true; f.frames()
            assertEquals(0, f.browse.grid.firstVisibleItemIndex)
            f.browse.leaveShop(); f.frames()
            assertEquals(before.index, f.browse.grid.firstVisibleItemIndex)
            assertEquals(before.offset, f.browse.grid.firstVisibleItemScrollOffset)
        }
    }
}

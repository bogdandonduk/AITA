package kz.aita

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.ui.ImageComposeScene
import java.util.concurrent.atomic.AtomicReference
import javax.swing.SwingUtilities
import kotlin.test.*

class WarehouseListSavedStateTest {
    @Test fun largeCatalogueDoesNotEnterActivityStateAndResetsForNewOwner() {
        val failure = AtomicReference<Throwable?>()
        SwingUtilities.invokeAndWait {
            try {
                val registry = SaveableStateRegistry(null) { true }
                var owner by mutableStateOf("account-a:store-a:session-1")
                var state: WarehouseListWorkingState? = null
                val scene = ImageComposeScene(20, 20) {
                    CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
                        state = rememberWarehouseListWorkingState(owner, null, null)
                    }
                }
                try {
                    scene.render(0).close()
                    val first = assertNotNull(state)
                    val ids = List(20_000) { "00000000-0000-4000-8000-${it.toString().padStart(12, '0')}" }
                    first.selectedIds.value = ids; first.sortOrderIds.value = ids
                    scene.render(16_000_000L).close()
                    assertTrue(registry.performSave().isEmpty(), "Catalogue IDs leaked into saved state")
                    assertSame(first, state)
                    assertEquals(20_000, first.selectedIds.value.size)
                    owner = "account-a:store-b:session-1"
                    repeat(3) { scene.render((it + 2L) * 16_000_000).close() }
                    assertNotSame(first, state)
                    assertTrue(assertNotNull(state).selectedIds.value.isEmpty())
                    assertTrue(assertNotNull(state).sortOrderIds.value.isEmpty())
                } finally { scene.close() }
            } catch (error: Throwable) { failure.set(error) }
        }
        failure.get()?.let { throw it }
    }
}

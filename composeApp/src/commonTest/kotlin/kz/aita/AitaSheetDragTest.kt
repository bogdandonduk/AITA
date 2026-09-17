package kz.aita

import kotlin.test.*

class AitaSheetDragTest {
    @Test fun movementIsBoundedAndReversible() {
        val drag = AitaSheetDragState().apply { height = 600f }
        assertEquals(0f, drag.drag(-50f))
        assertEquals(80f, drag.drag(80f))
        assertFalse(drag.shouldDismiss(0f, 1f))
        assertEquals(-80f, drag.drag(-100f))
        assertEquals(0f, drag.offset)
        assertEquals(600f, drag.drag(900f))
        assertTrue(drag.shouldDismiss(0f, 1f))
    }
    @Test fun deliberatePullOrDownwardFlingDismissesAcrossDensities() {
        for (density in listOf(1f, 2f, 3f)) {
            val drag = AitaSheetDragState().apply { height = 600f * density }
            drag.drag(20f * density)
            assertFalse(drag.shouldDismiss(1500f * density, density))
            drag.drag(20f * density)
            assertTrue(drag.shouldDismiss(1500f * density, density))
            assertFalse(drag.shouldDismiss(-1500f * density, density))
            assertFalse(drag.shouldDismiss(500f * density, density))
            drag.drag(120f * density)
            assertTrue(drag.shouldDismiss(0f, density))
        }
    }
}

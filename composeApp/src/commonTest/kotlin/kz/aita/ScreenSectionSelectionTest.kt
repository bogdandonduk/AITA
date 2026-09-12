package kz.aita

import kotlin.test.Test
import kotlin.test.assertEquals

class ScreenSectionSelectionTest {
    @Test fun initialSelectionUsesFirstAvailableSection() {
        assertEquals("balance", resolveScreenSectionId(null, listOf("balance", "history")))
    }

    @Test fun changingLanguageDoesNotChangeSelectedId() {
        val english = listOf("balance" to "Balance", "history" to "History")
        val russian = listOf("balance" to "Баланс", "history" to "История")
        assertEquals("history", resolveScreenSectionId("history", english.map { it.first }))
        assertEquals("history", resolveScreenSectionId("history", russian.map { it.first }))
    }

    @Test fun reorderedTabsKeepTheirIdentity() {
        assertEquals("invoices", resolveScreenSectionId("invoices", listOf("history", "invoices", "balance")))
    }

    @Test fun revokedPermissionCannotKeepUnavailableSectionVisible() {
        assertEquals("workers", resolveScreenSectionId("invite", listOf("workers")))
    }

    @Test fun explicitDefaultIsUsedWhenSelectedTabIsUnavailable() {
        assertEquals("history", resolveScreenSectionId("unknown", listOf("balance", "history"), "history"))
    }

    @Test fun invalidDefaultFallsBackToAvailableTab() {
        assertEquals("balance", resolveScreenSectionId("unknown", listOf("balance", "history"), "also-unknown"))
    }

    @Test fun selectedAvailableTabTakesPrecedenceOverDefault() {
        assertEquals("history", resolveScreenSectionId("history", listOf("balance", "history"), "balance"))
    }

    @Test fun noAvailableSectionsProducesNoPhantomSection() {
        assertEquals("", resolveScreenSectionId("invite", emptyList(), "workers"))
    }

    @Test fun removedFinalPageClampsToLastExistingPage() {
        assertEquals(1, boundedSectionPage(2, 40, 20))
        assertEquals(0, boundedSectionPage(1, 20, 20))
    }

    @Test fun partialFinalPageRemainsReachable() {
        assertEquals(2, boundedSectionPage(2, 41, 20))
    }

    @Test fun emptyOrNegativeItemCountHasOnlyInitialPage() {
        assertEquals(0, boundedSectionPage(4, 0, 20))
        assertEquals(0, boundedSectionPage(4, Int.MIN_VALUE, 20))
    }

    @Test fun negativeRequestedPageDoesNotReachNegativeSlice() {
        assertEquals(0, boundedSectionPage(Int.MIN_VALUE, 100, 20))
    }

    @Test fun invalidPageSizeDoesNotDivideByZero() {
        assertEquals(3, boundedSectionPage(5, 4, 0))
        assertEquals(3, boundedSectionPage(5, 4, Int.MIN_VALUE))
    }

    @Test fun largeCountsDoNotOverflowWhenCalculatingLastPage() {
        assertEquals(Int.MAX_VALUE - 1, boundedSectionPage(Int.MAX_VALUE, Int.MAX_VALUE, 1))
        assertEquals(0, boundedSectionPage(Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE))
    }
}

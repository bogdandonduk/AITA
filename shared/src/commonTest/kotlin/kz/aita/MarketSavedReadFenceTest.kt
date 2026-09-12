package kz.aita

import kotlin.test.*

class MarketSavedReadFenceTest {
    private fun page(vararg ids: String) = MarketPage(ids.map { MarketOffer(it, MarketStorefront("shop"), it,
        checkedAtMillis = 1, sourceUpdatedAtMillis = 1, saved = true) })
    @Test fun bulkClearAckFencesTheOldPageAndUnavailableCount() {
        val fence = MarketSavedReadFence(); val old = fence.capture()
        fence.acknowledgeSnapshot(setOf("kept"))
        val result = fence.reconcile(page("removed", "kept").copy(unavailableSavedCount = 4), old, true)
        assertEquals(listOf("kept"), result.offers.map { it.id }); assertEquals(0, result.unavailableSavedCount)
    }
    @Test fun newerGetDoesNotForgetEvidenceNeededByAnOlderGet() {
        val fence = MarketSavedReadFence(); val oldest = fence.capture()
        fence.acknowledge("removed", false)
        fence.reconcile(page("kept"), fence.capture(), true)
        assertTrue(fence.reconcile(page("removed"), oldest, true).offers.isEmpty())
    }
    @Test fun futureReadCanStillReflectAnotherDevicesLegitimateChanges() {
        val fence = MarketSavedReadFence(); fence.acknowledgeSnapshot(setOf("kept"))
        assertEquals(listOf("remote"), fence.reconcile(page("remote"), fence.capture(), true).offers.map { it.id })
    }
    @Test fun latestWholeSnapshotSupersedesOlderIndividualOverrides() {
        val fence = MarketSavedReadFence(); val old = fence.capture()
        fence.acknowledge("kept", false); fence.acknowledgeSnapshot(setOf("kept"))
        assertTrue(fence.reconcile(page("kept"), old).offers.single().saved)
    }
}

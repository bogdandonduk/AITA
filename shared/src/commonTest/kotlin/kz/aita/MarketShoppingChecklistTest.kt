package kz.aita

import kotlinx.serialization.encodeToString
import kotlin.test.*

class MarketShoppingChecklistTest {
    private fun id(n: Int) = "00000000-0000-0000-0000-" + n.toString().padStart(12, '0')
    private fun line(n: Int, collected: Boolean = false) = MarketShoppingLine(id(n), id(90), "Product $n", "Shop", 2,
        MarketShoppingBasis(currencyCode = "KZT", unitId = "piece", pricedAmount = 1.0), updatedAtMillis = 10, collected = collected)
    private fun snapshot(vararg rows: MarketShoppingLine) = MarketShoppingSnapshot(id(99), 3, rows.map { MarketShoppingQuotedLine(it) }, 20)

    @Test fun newFieldsDoNotChangeLegacyCommandBytes() {
        val command = MarketShoppingCommand(id(4), 3, id(1), 0)
        assertEquals("{\"commandId\":\"${id(4)}\",\"expectedRevision\":3,\"offerId\":\"${id(1)}\",\"units\":0,\"basis\":null}",
            jsonBase.encodeToString(command))
    }
    @Test fun checklistHasDedicatedEndpointAndExclusiveCommandShape() {
        val change = MarketChecklistChange(listOf(id(1)), MARKET_CHECKLIST_COLLECT)
        val command = MarketShoppingCommand(id(4), 3, "", 0, checklistChange = change)
        assertTrue(command.isValidMarketShoppingCommand())
        assertEquals("market/shopping-list/checklist", command.shoppingMutationEndpoint())
        assertFalse(command.copy(offerId = id(1)).isValidMarketShoppingCommand())
        assertFalse(command.copy(units = 1).isValidMarketShoppingCommand())
        assertFalse(command.copy(basis = line(1).basis).isValidMarketShoppingCommand())
        assertFalse(command.copy(replaceOfferId = id(2)).isValidMarketShoppingCommand())
        assertFalse(command.copy(expectedRevision = Long.MAX_VALUE).isValidMarketShoppingCommand())
    }
    @Test fun bulkTargetsAreBoundedCanonicalAndDistinct() {
        assertFalse(MarketChecklistChange(emptyList(), MARKET_CHECKLIST_COLLECT).isValidChecklistChange())
        assertFalse(MarketChecklistChange(listOf(id(1),id(1)), MARKET_CHECKLIST_COLLECT).isValidChecklistChange())
        assertFalse(MarketChecklistChange(listOf("all"), MARKET_CHECKLIST_REMOVE).isValidChecklistChange())
        assertFalse(MarketChecklistChange((1..51).map(::id), MARKET_CHECKLIST_COLLECT).isValidChecklistChange())
        assertFalse(MarketChecklistChange(listOf(id(1)), "delete_all").isValidChecklistChange())
    }
    @Test fun removalRequiresEveryReviewedItemToBeCollected() {
        val value = snapshot(line(1,true), line(2))
        assertNotNull(value.reviewChecklist(listOf(id(1)), MARKET_CHECKLIST_REMOVE))
        assertNull(value.reviewChecklist(listOf(id(1),id(2)), MARKET_CHECKLIST_REMOVE))
        assertNull(value.reviewChecklist(listOf(id(3)), MARKET_CHECKLIST_REMOVE))
    }
    @Test fun frozenReviewCannotRebaseAcrossAccountRevisionOrLineChange() {
        val value = snapshot(line(1,true))
        val review = requireNotNull(value.reviewChecklist(listOf(id(1)), MARKET_CHECKLIST_REMOVE))
        assertNotNull(review.command(value,id(4)))
        assertNull(review.command(value.copy(userId=id(88)),id(4)))
        assertNull(review.command(value.copy(revision=4),id(4)))
        assertNull(review.command(snapshot(line(1,false)),id(4)))
        assertNull(review.command(snapshot(line(1,true).copy(units=3)),id(4)))
    }
    @Test fun acknowledgementMustProveTheClaimedCheckmarkAtItsAppliedRevision() {
        val value = snapshot(line(1),line(2))
        val command = requireNotNull(value.reviewChecklist(listOf(id(1)),MARKET_CHECKLIST_COLLECT)?.command(value,id(4)))
        val unchanged = MarketShoppingOutcome(id(4),true,4,snapshot=value.copy(revision=4))
        assertFalse(command.isValidShoppingOutcome(unchanged,value.userId))
        val changed = unchanged.copy(snapshot=snapshot(line(1,true),line(2)).copy(revision=4))
        assertTrue(command.isValidShoppingOutcome(changed,value.userId))
        // A later revision is allowed to reflect an intervening uncheck on another device.
        assertTrue(command.isValidShoppingOutcome(unchanged.copy(snapshot=value.copy(revision=5)),value.userId))
    }
    @Test fun acknowledgementMustProveExactRemovalWithoutRequiringTheWholeListEmpty() {
        val value=snapshot(line(1,true),line(2))
        val command=requireNotNull(value.reviewChecklist(listOf(id(1)),MARKET_CHECKLIST_REMOVE)?.command(value,id(4)))
        assertFalse(command.isValidShoppingOutcome(MarketShoppingOutcome(id(4),true,4,snapshot=value.copy(revision=4)),value.userId))
        assertTrue(command.isValidShoppingOutcome(MarketShoppingOutcome(id(4),true,4,snapshot=snapshot(line(2)).copy(revision=4)),value.userId))
    }
    @Test fun checklistSurvivesDurableJournalAndCancellationRecovery() {
        val value=snapshot(line(1,true));val command=requireNotNull(value.reviewChecklist(listOf(id(1)),MARKET_CHECKLIST_REMOVE)?.command(value,id(4)))
        val pending=PendingMarketShoppingCommand(value.userId,command)
        val journal=MarketShoppingJournal(value.userId,value).prepare(pending).requestCancellation(pending)
        val restored=jsonBase.decodeFromString<MarketShoppingJournal>(jsonBase.encodeToString(journal))
        assertEquals(journal,restored);assertTrue(restored.hasValidCancellationIntent())
        val cancelled=MarketShoppingOutcome(id(4),false,errorKey="market.shopping_cancelled",snapshot=value)
        assertNull(restored.acknowledge(pending,cancelled).pending)
        assertTrue(restored.acknowledge(pending,cancelled).snapshot!!.lines.single().line.collected)
    }
    @Test fun historyRecordsCheckmarksWithoutInventingAPurchase() {
        val before=line(1);val after=before.copy(collected=true,updatedAtMillis=30)
        val value=snapshot(before)
        val command=requireNotNull(value.reviewChecklist(listOf(id(1)),MARKET_CHECKLIST_COLLECT)?.command(value,id(4)))
        val entry=MarketShoppingActivityEntry(id(4),30,3,true,4,kind=MARKET_ACTIVITY_CHECKLIST,requestedUnits=0,
            changedLines=1,details=command.shoppingActivityDetails(listOf(before),listOf(after)))
        assertTrue(entry.isValidShoppingActivityEntry())
        assertFalse(entry.copy(details=MarketShoppingActivityDetails(listOf(MarketShoppingActivityLine(before,after.copy(units=9))))).isValidShoppingActivityEntry())
        assertFalse(entry.copy(reviewedCurrency="KZT",reviewedSubtotalMinor=500).isValidShoppingActivityEntry())
    }
    @Test fun oldHistoryRequestsDoNotAcceptUnknownChecklistRecords() {
        val legacy=MarketShoppingActivitySearchRequest()
        val wanted=legacy.copy(includeChecklist=true)
        val entry=MarketShoppingActivityEntry(id(4),30,3,true,4,kind=MARKET_ACTIVITY_CHECKLIST,requestedUnits=0,changedLines=1)
        val page=MarketShoppingActivitySearchPage(id(99),wanted,listOf(entry),false,false,40)
        assertTrue(page.isValidActivitySearchPage(id(99),wanted))
        assertFalse(page.copy(request=legacy).isValidActivitySearchPage(id(99),legacy))
        assertNull(legacy.copy(filter=MarketShoppingActivityFilter(kind=MARKET_ACTIVITY_CHECKLIST)).normalizedActivitySearch())
    }
    @Test fun quantityHistoryAllowsResetOnlyWhenTheQuantityChanges() {
        val before=line(1,true);val after=before.copy(units=3,collected=false,updatedAtMillis=30)
        val entry=MarketShoppingActivityEntry(id(4),30,3,true,4,kind=MARKET_ACTIVITY_QUANTITY,requestedUnits=3,
            changedLines=1,details=MarketShoppingActivityDetails(listOf(MarketShoppingActivityLine(before,after))))
        assertTrue(entry.isValidShoppingActivityEntry())
        assertFalse(entry.copy(details=MarketShoppingActivityDetails(listOf(MarketShoppingActivityLine(before,after.copy(collected=true))))).isValidShoppingActivityEntry())
    }
    @Test fun progressIsPerShopAndDoesNotSumMixedCurrencies() {
        val value=snapshot(line(1,true),line(2),line(3,true).copy(storeId=id(91)))
        assertEquals(MarketTripProgress(3,2),value.tripProgress())
        assertEquals(MarketTripProgress(2,1),value.tripProgress(id(90)))
        assertEquals(0f,value.tripProgress(id(92)).fraction)
    }
}

package kz.aita

import kotlin.test.*

class MarketShoppingActivitySearchTest {
    private val account = "00000000-0000-0000-0000-000000000001"
    private fun id(n: Int) = "00000000-0000-0000-0000-${n.toString(16).padStart(12, '0')}"
    private fun entry(n: Int, time: Long = 100) = MarketShoppingActivityEntry(id(n), time, 1, true, 2,
        kind = MARKET_ACTIVITY_QUANTITY, requestedUnits = 1, changedLines = 1)
    private fun page(request: MarketShoppingActivitySearchRequest = MarketShoppingActivitySearchRequest(),
        entries: List<MarketShoppingActivityEntry> = listOf(entry(10)), older: Boolean = false, newer: Boolean = false) =
        MarketShoppingActivitySearchPage(account, request, entries, older, newer, 200)

    @Test fun referenceNormalizesCaseAndOuterWhitespaceOnly() {
        val value = "abcdefab-cdef-abcd-efab-cdefabcdefab"
        assertEquals(value, normalizedMarketChangeReference(" \n${value.uppercase()} \t"))
        for (invalid in listOf("", "abcdef", value.replace("-", ""), "{$value}", "ref:$value", "$value.extra",
            value.replaceFirst("-", "- "), "0".repeat(65))) assertNull(normalizedMarketChangeReference(invalid), invalid)
    }
    @Test fun directReferenceHasNoHiddenFilterOrPaging() {
        val reference = MarketShoppingActivitySearchRequest(MarketShoppingActivityFilter(commandId = " ${id(10).uppercase()} "))
        assertEquals(id(10), reference.normalizedActivitySearch()?.filter?.commandId)
        assertNull(reference.copy(filter = reference.filter.copy(kind = MARKET_ACTIVITY_REMOVE)).normalizedActivitySearch())
        assertNull(reference.copy(filter = reference.filter.copy(result = MARKET_ACTIVITY_RESULT_REJECTED)).normalizedActivitySearch())
        assertNull(reference.copy(boundary = entry(11).activityCursor()).normalizedActivitySearch())
    }
    @Test fun unknownFiltersAndMalformedBoundariesAreNotBroadened() {
        assertNull(MarketShoppingActivitySearchRequest(MarketShoppingActivityFilter(kind = "delete-all")).normalizedActivitySearch())
        assertNull(MarketShoppingActivitySearchRequest(MarketShoppingActivityFilter(result = "success OR TRUE")).normalizedActivitySearch())
        assertNull(MarketShoppingActivitySearchRequest(newer = true).normalizedActivitySearch())
        for (cursor in listOf(MarketShoppingActivityCursor(0,id(10)), MarketShoppingActivityCursor(-1,id(10)),
            MarketShoppingActivityCursor(100,"bad"))) assertNull(MarketShoppingActivitySearchRequest(boundary = cursor).normalizedActivitySearch())
    }
    @Test fun appliedRejectedAndNoOpRemainDistinct() {
        val applied = entry(10)
        val rejected = applied.copy(accepted = false, appliedRevision = null, changedLines = 0, errorKey = "market.shopping_changed")
        val noOp = applied.copy(appliedRevision = 1, changedLines = 0)
        for ((result, target) in listOf(MARKET_ACTIVITY_RESULT_APPLIED to applied, MARKET_ACTIVITY_RESULT_REJECTED to rejected,
            MARKET_ACTIVITY_RESULT_UNCHANGED to noOp)) {
            val filter = MarketShoppingActivityFilter(result = result)
            assertEquals(listOf(target), listOf(applied,rejected,noOp).filter(filter::matchesActivity))
        }
    }
    @Test fun bothFiltersMustMatchTheReturnedEntry() {
        val request = MarketShoppingActivitySearchRequest(MarketShoppingActivityFilter(MARKET_ACTIVITY_QUANTITY,MARKET_ACTIVITY_RESULT_APPLIED))
        assertTrue(page(request).isValidActivitySearchPage(account,request))
        assertFalse(page(request,listOf(entry(10).copy(kind = MARKET_ACTIVITY_REMOVE,requestedUnits = 0))).isValidActivitySearchPage(account,request))
        assertFalse(page(request,listOf(entry(10).copy(appliedRevision = 1,changedLines = 0))).isValidActivitySearchPage(account,request))
    }
    @Test fun canonicalUuidTiesUseFullIdentityNotOnlyTimestamp() {
        val high = entry(10).copy(commandId = "80000000-0000-0000-0000-000000000000")
        val low = entry(10).copy(commandId = "7fffffff-ffff-ffff-ffff-ffffffffffff")
        assertTrue(high.activityCursor().compareActivityCursor(low.activityCursor()) > 0)
        assertTrue(page(entries = listOf(high,low)).isValidActivitySearchPage(account,MarketShoppingActivitySearchRequest()))
        assertFalse(page(entries = listOf(low,high)).isValidActivitySearchPage(account,MarketShoppingActivitySearchRequest()))
    }
    @Test fun olderAndNewerUseStrictBoundariesAndDescendingDisplay() {
        val older = MarketShoppingActivitySearchRequest(boundary = entry(20).activityCursor())
        assertTrue(page(older,listOf(entry(19),entry(18)),newer = true).isValidActivitySearchPage(account,older))
        assertFalse(page(older,listOf(entry(20))).isValidActivitySearchPage(account,older))
        assertFalse(page(older,listOf(entry(21))).isValidActivitySearchPage(account,older))
        val newer = older.copy(newer = true)
        assertTrue(page(newer,listOf(entry(22),entry(21)),older = true).isValidActivitySearchPage(account,newer))
        assertFalse(page(newer,listOf(entry(21),entry(22))).isValidActivitySearchPage(account,newer))
        assertFalse(page(newer,listOf(entry(19))).isValidActivitySearchPage(account,newer))
    }
    @Test fun wrongOwnerProtocolScopeAndOversizedResultsAreRejected() {
        val request = MarketShoppingActivitySearchRequest()
        val valid = page()
        assertTrue(valid.isValidActivitySearchPage(account,request))
        for (bad in listOf(valid.copy(accountId = id(9)), valid.copy(protocolVersion = 2), valid.copy(checkedAtMillis = 0),
            valid.copy(request = request.copy(filter = MarketShoppingActivityFilter(result = MARKET_ACTIVITY_RESULT_APPLIED))),
            valid.copy(entries = (30 downTo 10).map(::entry)), valid.copy(entries = listOf(entry(10),entry(10)))))
            assertFalse(bad.isValidActivitySearchPage(account,request))
    }
    @Test fun summaryWindowCannotSmuggleFullHistoricalDetails() {
        val invalid = entry(10).copy(details = MarketShoppingActivityDetails(emptyList()),detailsRecorded = true)
        assertFalse(page(entries = listOf(invalid)).isValidActivitySearchPage(account,MarketShoppingActivitySearchRequest()))
    }
    @Test fun exactReferenceAllowsOneOwnedResultOrAnHonestEmptyPage() {
        val request = MarketShoppingActivitySearchRequest(MarketShoppingActivityFilter(commandId = id(10)))
        assertTrue(page(request).isValidActivitySearchPage(account,request))
        assertTrue(page(request,emptyList()).isValidActivitySearchPage(account,request))
        assertFalse(page(request,listOf(entry(11))).isValidActivitySearchPage(account,request))
        assertFalse(page(request,older = true).isValidActivitySearchPage(account,request))
    }
    @Test fun continuationFlagsCannotTurnTruncationIntoAValidPage() {
        val request = MarketShoppingActivitySearchRequest()
        assertFalse(page(older = true).isValidActivitySearchPage(account,request))
        assertFalse(page(newer = true).isValidActivitySearchPage(account,request))
        assertFalse(page(entries = emptyList(),newer = true).isValidActivitySearchPage(account,request))
        val complete = page(entries = (30 downTo 11).map(::entry),older = true)
        assertTrue(complete.isValidActivitySearchPage(account,request))
        assertEquals(entry(11).activityCursor(),complete.olderActivityRequest()?.boundary)
        assertNull(complete.newerActivityRequest())
        assertNull(complete.copy(hasOlder = false).olderActivityRequest())
    }
    @Test fun moreThanTwoHundredCanBeTraversedWithoutGrowingTheWindow() {
        val history = (237 downTo 1).map { entry(it,100L + it / 7) }
        fun read(request: MarketShoppingActivitySearchRequest): MarketShoppingActivitySearchPage {
            val candidates = history.filter { row -> request.boundary?.let { b ->
                val compare = row.activityCursor().compareActivityCursor(b)
                if (request.newer) compare > 0 else compare < 0
            } ?: true }
            val rows = if (request.newer) candidates.takeLast(20) else candidates.take(20)
            val older = rows.lastOrNull()?.let { last -> history.any { it.activityCursor().compareActivityCursor(last.activityCursor()) < 0 } } == true
            val newer = rows.firstOrNull()?.let { first -> history.any { it.activityCursor().compareActivityCursor(first.activityCursor()) > 0 } } == true
            return page(request,rows,older,newer)
        }
        var request = MarketShoppingActivitySearchRequest()
        val visited = mutableListOf<String>()
        var last = read(request)
        while (true) {
            val result = read(request); last = result
            assertTrue(result.isValidActivitySearchPage(account,request)); assertTrue(result.entries.size <= 20)
            visited += result.entries.map { it.commandId }
            request = result.olderActivityRequest() ?: break
        }
        assertEquals(history.map { it.commandId },visited)
        val before = read(requireNotNull(last.newerActivityRequest()))
        assertEquals(history.drop(200).take(20),before.entries)
    }
}

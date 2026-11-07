package kz.aita

import kotlinx.coroutines.*
import kotlin.test.*

class MarketPriceWatchControllerTest {
    private val account = "11111111-1111-4111-8111-111111111111"
    private val store = "22222222-2222-4222-8222-222222222222"
    private val row = MarketPriceInsightRow("33333333-3333-4333-8333-333333333333", name = emptyList(),
        barcodes = listOf("04006381333931"), basis = MarketShoppingBasis("04006381333931", "KZT", "0", 1.0))
    private class Server(var state: MarketPriceWatchState) {
        val requests = mutableListOf<MarketPriceWatchUpdate>()
        var loseReply = false
        var unavailable = false
        var conflict = false
        var lastId: String? = null
        suspend fun request(body: MarketPriceWatchUpdate?): MarketPriceWatchResult {
            if (unavailable) return MarketPriceWatchResult(message = marketPriceMessage("watch_unavailable"))
            if (body == null) return MarketPriceWatchResult(state)
            requests += body
            if (conflict) return MarketPriceWatchResult(message = marketPriceMessage("watch_conflict"), conflict = true)
            if (lastId != body.requestId) {
                assertEquals(state.revision, body.expectedRevision)
                state = state.copy(revision = state.revision + 1, enabled = body.enabled, settings = body.settings,
                    nextCheckAtMillis = if (body.enabled) 1000 else null)
                lastId = body.requestId
            }
            if (loseReply) { loseReply = false; return MarketPriceWatchResult(message = marketPriceMessage("watch_unavailable")) }
            return MarketPriceWatchResult(state)
        }
    }
    private fun scenario(block: (MarketPriceWatchController, Server) -> Unit) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val server = Server(MarketPriceWatchState(account, store))
        try { block(MarketPriceWatchController(scope, { true }, server::request), server) }
        finally { scope.cancel() }
    }
    @Test fun uncertainSaveRetriesExactRequestAndDoesNotDuplicateTheWatch() = scenario { ui, server ->
        ui.refresh(); server.loseReply = true; ui.toggle(row, MarketPriceRequest())
        assertNotNull(ui.pending); assertEquals(0, ui.count); assertNotNull(ui.message); assertFalse(ui.busy)
        val original = ui.pending
        ui.retry()
        assertNull(ui.pending); assertEquals(1, ui.count); assertTrue(ui.follows(row))
        assertEquals(listOf(original, original), server.requests); assertEquals(1L, server.state.revision)
    }
    @Test fun repeatingVisibleFollowAfterLostReplyCannotAccidentallyUnfollow() = scenario { ui, server ->
        ui.refresh(); server.loseReply = true; ui.toggle(row, MarketPriceRequest())
        assertFalse(ui.follows(row)); assertTrue(server.state.enabled)
        ui.toggle(row, MarketPriceRequest())
        assertTrue(ui.follows(row)); assertEquals(1, server.state.settings.items.size)
        assertTrue(server.requests.all { it.enabled })
    }
    @Test fun unavailableReconciliationDoesNotSendSettingsOrPretendToEnableAlerts() = scenario { ui, server ->
        server.unavailable = true; ui.toggle(row, MarketPriceRequest())
        assertEquals(0, ui.count); assertTrue(server.requests.isEmpty()); assertNotNull(ui.message); assertFalse(ui.busy)
    }
    @Test fun conflictingEditIsExplainedAndNeverRetriedBlindly() = scenario { ui, server ->
        ui.refresh(); server.conflict = true; ui.toggle(row, MarketPriceRequest())
        assertNull(ui.pending); assertEquals(marketPriceMessage("watch_conflict"), ui.message)
        assertFalse(ui.follows(row)); assertFalse(ui.busy)
    }
    @Test fun switchingAccountWhileSettingsLoadCannotExposePreviousAccountState() = runBlocking {
        var current = true
        val response = CompletableDeferred<MarketPriceWatchResult>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val ui = MarketPriceWatchController(scope, { current }) { response.await() }
            ui.refresh(); assertTrue(ui.busy); current = false
            response.complete(MarketPriceWatchResult(MarketPriceWatchState(account, store)))
            assertNull(ui.state); assertFalse(ui.busy)
        } finally { scope.cancel() }
    }
    @Test fun permissionRevocationClearsVisibleStateAndPreventsASave() = scenario { ui, server ->
        ui.refresh(); ui.toggle(row, MarketPriceRequest()); assertEquals(1, ui.count)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var deny = false
            val revoked = MarketPriceWatchController(scope, { true }) { body ->
                if (deny) MarketPriceWatchResult(message = marketPriceMessage("access"), denied = true) else server.request(body)
            }
            revoked.refresh(); assertEquals(1, revoked.count); deny = true
            revoked.remove(row.goodsItemId)
            assertNull(revoked.state); assertTrue(revoked.denied); assertEquals(1, server.requests.size)
        } finally { scope.cancel() }
    }
}

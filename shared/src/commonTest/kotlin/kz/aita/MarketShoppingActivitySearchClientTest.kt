package kz.aita

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class MarketShoppingActivitySearchClientTest {
    private class Owner : MarketAccountScope {
        override val accountId = "00000000-0000-0000-0000-000000000001"
        override val generation = 1L
        var current = true
        override fun isCurrent() = current
    }
    private val request = MarketShoppingActivitySearchRequest()
    private fun page(owner: Owner, input: MarketShoppingActivitySearchRequest = request) =
        MarketShoppingActivitySearchPage(owner.accountId,input,emptyList(),false,false,100)
    private fun <T> ok(value: T) = ResponseDataModel(null,value,false,200)
    @Test fun normalizesBeforeReadingAndAcceptsTheExactScope() = runTest {
        val owner = Owner(); var calls = 0
        val id = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
        val raw = request.copy(filter = MarketShoppingActivityFilter(commandId = " ${id.uppercase()} "))
        val result = readOwnedShoppingActivitySearch(owner,raw) { input ->
            calls++; assertEquals(id,input.filter.commandId); ok(page(owner,input))
        }
        assertFalse(result.negative); assertEquals(1,calls)
    }
    @Test fun invalidRequestAndExpiredAccountDoNotRead() = runTest {
        val owner = Owner(); var calls = 0
        assertTrue(readOwnedShoppingActivitySearch(owner,request.copy(newer = true)) { calls++; ok(page(owner)) }.negative)
        owner.current = false
        assertTrue(readOwnedShoppingActivitySearch(owner,request) { calls++; ok(page(owner)) }.negative)
        assertEquals(0,calls)
    }
    @Test fun lateAccountChangeCannotPublishAnotherAccountsHistory() = runTest {
        val owner = Owner()
        val result = readOwnedShoppingActivitySearch(owner,request) { owner.current = false; ok(page(owner)) }
        assertTrue(result.negative); assertNull(result.payload)
    }
    @Test fun ignoredFilterOrWrongProtocolIsNotASuccess() = runTest {
        val owner = Owner(); val filtered = request.copy(filter = MarketShoppingActivityFilter(result = MARKET_ACTIVITY_RESULT_REJECTED))
        for (value in listOf(page(owner),page(owner,filtered).copy(protocolVersion = 99),page(owner,filtered).copy(accountId = "other"))) {
            val result = readOwnedShoppingActivitySearch(owner,filtered) { ok(value) }
            assertTrue(result.negative); assertEquals(502,result.httpStatusCode); assertNull(result.payload)
        }
    }
    @Test fun olderServerCannotSilentlyReturnUnfilteredHistory() = runTest {
        val owner = Owner(); var calls = 0
        val result = readOwnedShoppingActivitySearch(owner,request) { calls++; ResponseDataModel(null,page(owner),false,404) }
        assertTrue(result.negative); assertNotNull(result.message); assertNull(result.payload); assertEquals(1,calls)
    }
    @Test fun negativeResponsesCannotCarryAVisiblePayload() = runTest {
        val owner = Owner()
        val result = readOwnedShoppingActivitySearch(owner,request) { ResponseDataModel(null,page(owner),true,403) }
        assertTrue(result.negative); assertNull(result.payload)
    }
    @Test fun cancellationIsNotTurnedIntoAnEmptyHistory() = runTest {
        assertFailsWith<CancellationException> { readOwnedShoppingActivitySearch(Owner(),request) { throw CancellationException() } }
    }
}

package kz.aita

import kotlin.test.*

class TransactionSearchInputBurstTest {
    @Test fun manualTypingDoesNotDispatchShortPrefixes() {
        val burst = TransactionSearchInputBurst()
        val code = "2112345010006"
        code.indices.forEach { i -> assertFalse(burst.edited(code.take(i), code.take(i+1), i * 180L)) }
    }
    @Test fun suffixlessScannerIsRecognizedOnlyAsABurst() {
        val burst = TransactionSearchInputBurst()
        val code = "2112345010006"
        code.indices.forEach { i -> assertEquals(i >= 3, burst.edited(code.take(i), code.take(i+1), i * 12L)) }
    }
    @Test fun deletionReplacementAndPauseCancelPendingBurst() {
        val burst = TransactionSearchInputBurst()
        repeat(4) { burst.edited("1234".take(it),"1234".take(it+1),it * 10L) }
        assertFalse(burst.edited("1234","123",50))
        assertFalse(burst.edited("123","1235",60))
        assertFalse(burst.edited("1235","12356",600))
        assertFalse(burst.edited("12356","9",610))
    }
    @Test fun wholeScannerOrPasteIsAcceptedButOrdinaryPastedWordsStaySearchable() {
        val burst = TransactionSearchInputBurst()
        assertTrue(burst.edited("","2112345010006",100))
        assertFalse(burst.edited("","coffee",200))
        assertFalse(burst.edited("","1234",300))
    }
    @Test fun quicklyTypedProductNamesAreStillManualSearch() {
        val burst = TransactionSearchInputBurst()
        val name = "coffee"
        name.indices.forEach { i -> assertFalse(burst.edited(name.take(i), name.take(i+1), i * 20L)) }
    }
    @Test fun coalescedScannerKeyEventsStillCompleteWithoutATerminator() {
        val burst = TransactionSearchInputBurst()
        val code = "2112345010006"
        var length = 0
        var recognized = false
        listOf(2, 2, 2, 2, 2, 2, 1).forEachIndexed { i, added ->
            recognized = burst.edited(code.take(length), code.take(length + added), i * 16L)
            length += added
        }
        assertTrue(recognized)
    }
    @Test fun twoBatchesCanFormAShortScannerCodeButSlowManualChunksDoNot() {
        val burst = TransactionSearchInputBurst()
        assertFalse(burst.edited("", "1234", 0))
        assertTrue(burst.edited("1234", "123456", 16))
        burst.reset()
        assertFalse(burst.edited("", "1234", 0))
        assertFalse(burst.edited("1234", "123456", 300))
    }
}

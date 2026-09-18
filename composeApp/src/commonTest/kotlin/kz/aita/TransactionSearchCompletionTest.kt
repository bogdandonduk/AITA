package kz.aita

import kotlin.test.*

class TransactionSearchCompletionTest {
    @Test fun disposingAnOldSearchCannotDetachTheNewOne() {
        val completion = TransactionSearchCompletion()
        var first = 0; var second = 0
        val detachFirst = completion.attach { first++ }
        val detachSecond = completion.attach { second++ }
        detachFirst(); completion.handled()
        assertEquals(0, first); assertEquals(1, second)
        detachSecond(); completion.handled()
        assertEquals(1, second)
    }
    @Test fun lateDraftReadsCannotRestoreAConsumedBarcodeOrOverwriteTyping() {
        val guard = TextDraftRestoreGuard()
        val oldRead = guard.revision
        assertTrue(guard.accepts(oldRead))
        guard.edited() // Scanner completion clears both the editor and saved draft.
        assertFalse(guard.accepts(oldRead))
        val nextRead = guard.revision
        guard.edited() // A new manual search must also win over its earlier disk read.
        assertFalse(guard.accepts(nextRead))
    }
}

package kz.aita

import kotlin.test.*

class ExactBarcodeChoiceTest {
    @Test fun partialAndAmbiguousCodesNeverChooseAnItem() {
        val values = listOf(listOf("123456", "ALT-A"), listOf("12345678", "ALT-B"))
        assertNull(uniqueExactBarcode(values, "123", { it }))
        assertEquals(values[0], uniqueExactBarcode(values, "123456", { it }))
        assertEquals(values[1], uniqueExactBarcode(values, "alt-b", { it }))
        assertNull(uniqueExactBarcode(values + listOf(listOf("123456")), "123456", { it }))
        assertNull(uniqueExactBarcode(values, "123456789", { it }))
    }
    @Test fun delayedCompletionCannotClearAReplacementSearchOwner() {
        val completion = TransactionSearchCompletion()
        var first = 0; var second = 0
        completion.attachSnapshot { { first++ } }
        val saved = completion.capture()
        completion.attachSnapshot { { second++ } }
        saved()
        assertEquals(0, first); assertEquals(0, second)
        completion.capture()()
        assertEquals(1, second)
    }
}

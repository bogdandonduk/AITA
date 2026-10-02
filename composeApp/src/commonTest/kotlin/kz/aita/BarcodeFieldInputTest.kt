package kz.aita

import kotlin.test.Test
import kotlin.test.assertEquals

class BarcodeFieldInputTest {
    @Test
    fun rapidTypingNeverTreatsAnEarlierPrefixAsAnotherScan() {
        val code = "2618000001248"
        for (length in 1..code.length) {
            val liveEditor = code.take(length)
            assertEquals(liveEditor, normalizeVisibleBarcodeFieldInput(liveEditor))
        }
    }

    @Test
    fun scannerAndPastePreserveFullIdentifiersIncludingLeadingZeroes() {
        for (code in listOf("0012345678905", "2618000001248", "2900060749919", "721688562788", "A".repeat(40))) {
            assertEquals(code, normalizeVisibleBarcodeFieldInput(" $code\r\n"))
        }
    }
}

package kz.aita

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PaymentAmountSlotsTest {
    @Test
    fun cashOnlyAlwaysPlacesKeypadDirectlyBelowCash() {
        for (selection in listOf("cash", "card", "debt", "", "unknown")) {
            assertEquals(
                listOf(PaymentAmountSlot("cash"), PaymentAmountSlot("cash", keypad = true)),
                paymentAmountSlots("0", selection)
            )
        }
    }

    @Test
    fun mixedModeHasExactlyOneKeypadAfterSelectedField() {
        for (selection in listOf("cash", "card", "debt")) {
            val rows = paymentAmountSlots("2", selection)
            assertEquals(listOf("cash", "card", "debt"), rows.filterNot { it.keypad }.map { it.field })
            assertEquals(1, rows.count { it.keypad })
            val editor = rows.indexOfFirst { !it.keypad && it.field == selection }
            assertEquals(PaymentAmountSlot(selection, keypad = true), rows[editor + 1])
        }
    }

    @Test
    fun switchingFocusKeepsEveryRowIdentityStable() {
        val initialKeys = paymentAmountSlots("2", "cash").map { it.key }.toSet()
        for (selection in listOf("debt", "card", "cash", "debt")) {
            val rows = paymentAmountSlots("2", selection)
            assertEquals(initialKeys, rows.map { it.key }.toSet())
            assertEquals(rows.size, rows.map { it.key }.distinct().size)
        }
    }

    @Test
    fun invalidMixedSelectionFallsBackToCash() {
        val rows = paymentAmountSlots("2", "missing")
        assertEquals(PaymentAmountSlot("cash", keypad = true), rows[1])
    }

    @Test
    fun cashlessOnlyDoesNotAddAnEditableTotalOrKeypad() {
        assertTrue(paymentAmountSlots("1", "card").isEmpty())
        assertTrue(paymentAmountSlots("invalid", "cash").isEmpty())
    }

    @Test
    fun cashFieldAndKeypadSurviveChangingBetweenCashAndMixed() {
        val cashKeys = paymentAmountSlots("0", "cash").map { it.key }
        val mixedKeys = paymentAmountSlots("2", "cash").take(2).map { it.key }
        assertEquals(cashKeys, mixedKeys)
        assertFalse(cashKeys[0] == cashKeys[1])
    }
}

package kz.aita

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class ControlledTextFieldValueTest {
    @Test
    fun keypadReplacementAppearsWithoutWaitingForFocusLoss() {
        val result = reconcileControlledTextFieldValue(TextFieldValue("12"), "123")
        assertEquals("123", result.text)
        assertEquals(TextRange(3), result.selection)
    }

    @Test
    fun parentClampWinsEvenWhenAcceptedValueHasNotChanged() {
        // The previous parent value was 100; it rejects typing another zero and stays 100.
        val result = reconcileControlledTextFieldValue(TextFieldValue("1000", TextRange(4)), "100")
        assertEquals("100", result.text)
        assertEquals(TextRange(3), result.selection)
    }

    @Test
    fun acceptedTypingPreservesCursorAndComposition() {
        val editor = TextFieldValue("12.30", TextRange(2), TextRange(0, 2))
        assertSame(editor, reconcileControlledTextFieldValue(editor, "12.30"))
    }

    @Test
    fun replacingTextDropsAnObsoleteImeComposition() {
        val editor = TextFieldValue("12", TextRange(1), TextRange(0, 2))
        val result = reconcileControlledTextFieldValue(editor, "125")
        assertNull(result.composition)
        assertEquals(TextRange(3), result.selection)
    }

    @Test
    fun clearingUsesEmptyParentValueAndResetsCursor() {
        val result = reconcileControlledTextFieldValue(TextFieldValue("123", TextRange(3)), "")
        assertEquals("", result.text)
        assertEquals(TextRange.Zero, result.selection)
    }

    @Test
    fun decimalEditingIsNotReformattedByTheField() {
        for (text in listOf(".", "0.", "12.", "12.0", "12.00")) {
            assertEquals(text, reconcileControlledTextFieldValue(TextFieldValue(""), text).text)
        }
    }

    @Test
    fun unchangedTextPreservesReversedSelection() {
        val editor = TextFieldValue("1234", TextRange(3, 1))
        assertSame(editor, reconcileControlledTextFieldValue(editor, "1234"))
    }
}

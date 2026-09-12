@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package kz.aita

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AitaTextFieldBridgeTest {
    private fun AitaTextFieldBridge.edit(text: String, selection: TextRange = TextRange(text.length)) {
        state.edit { replace(0, length, text); this.selection = selection }
    }
    @Test fun unchangedParentDoesNotResetNativeHandleMotion() {
        val initial = TextFieldValue("Long text", TextRange(2))
        val bridge = AitaTextFieldBridge(initial)
        bridge.state.edit { selection = TextRange(8) }
        bridge.updateFromParent(initial)
        assertEquals(TextRange(8), bridge.editorValue().selection)
    }
    @Test fun reportsSelectionOnceAndKeepsReversedSelection() {
        val bridge = AitaTextFieldBridge(TextFieldValue("12345"))
        bridge.state.edit { selection = TextRange(4, 1) }
        var calls = 0
        assertTrue(bridge.reportEditorChange { calls++; it })
        assertFalse(bridge.reportEditorChange { calls++; it })
        assertEquals(1, calls); assertEquals(TextRange(4, 1), bridge.editorValue().selection)
    }
    @Test fun staleAcknowledgementDoesNotOverwriteNewerTyping() {
        val bridge = AitaTextFieldBridge(TextFieldValue("a"))
        bridge.edit("ab"); bridge.reportEditorChange { it }
        bridge.edit("abc")
        bridge.updateFromParent(TextFieldValue("ab", TextRange(2)))
        assertEquals("abc", bridge.editorValue().text)
        var sent = ""
        bridge.reportEditorChange { sent = it.text; it }
        assertEquals("abc", sent)
    }
    @Test fun unchangedAcceptedParentCanRejectExtraPaymentDigit() {
        val initial = TextFieldValue("100", TextRange(3))
        val bridge = AitaTextFieldBridge(initial)
        bridge.edit("1000"); bridge.reportEditorChange { it }
        bridge.updateFromParent(initial)
        assertEquals(initial, bridge.editorValue())
    }
    @Test fun callbackRejectionImmediatelyRestoresAcceptedValue() {
        val initial = TextFieldValue("100", TextRange(3))
        val bridge = AitaTextFieldBridge(initial)
        bridge.edit("1000"); bridge.reportEditorChange { initial }
        assertEquals(initial, bridge.editorValue())
        assertFalse(bridge.reportEditorChange { error("Rejected edit must not be emitted again") })
    }
    @Test fun parentKeypadClearAndReplacementAreVisible() {
        val bridge = AitaTextFieldBridge(TextFieldValue("12"))
        bridge.updateFromParent(TextFieldValue("125", TextRange(3)))
        assertEquals("125", bridge.editorValue().text)
        bridge.updateFromParent(TextFieldValue(""))
        assertEquals("", bridge.editorValue().text)
    }
    @Test fun perCharacterPasswordFormattingNeverChangesLength() {
        val bridge = AitaTextFieldBridge(TextFieldValue("a🛒б", TextRange(2)))
        bridge.state.edit {
            applyAitaVisualTransformation { value -> TransformedText(AnnotatedString("•".repeat(value.text.length)), OffsetMapping.Identity) }
        }
        assertEquals("•".repeat("a🛒б".length), bridge.editorValue().text)
    }
    @Test fun malformedPasswordMappingFailsClosedToFormattedOutput() {
        val bridge = AitaTextFieldBridge(TextFieldValue("abc"))
        bridge.state.edit {
            applyAitaVisualTransformation { TransformedText(AnnotatedString("•••"), object : OffsetMapping {
                override fun originalToTransformed(offset: Int) = 3 - offset
                override fun transformedToOriginal(offset: Int) = 3 - offset
            }) }
        }
        assertEquals("•••", bridge.editorValue().text)
    }
}

@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package kz.aita

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.focus.FocusRequester
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
    @Test fun scannerCompletionClearsBeforeTheFirstParentAcknowledgement() {
        val bridge = AitaTextFieldBridge(TextFieldValue(""))
        bridge.edit("2112345010006")
        bridge.reportEditorChange { it }
        bridge.resetFromParent(TextFieldValue(""))
        bridge.updateFromParent(TextFieldValue(""))
        assertEquals("", bridge.editorValue().text)
        assertFalse(bridge.reportEditorChange { error("A handled barcode must not be submitted again") })
        bridge.edit("12345678")
        var nextScan = ""
        assertTrue(bridge.reportEditorChange { nextScan = it.text; it })
        assertEquals("12345678", nextScan)
    }
    @Test fun authoritativeResetClearsLiveInputEvenWhenParentIsAlreadyEmpty() {
        val bridge = AitaTextFieldBridge(TextFieldValue(""))
        bridge.edit("12345678")
        bridge.resetFromParent(TextFieldValue(""))
        assertEquals(TextFieldValue(""), bridge.editorValue())
    }
    @Test fun asyncScannerSubmissionUsesLiveBurstAndKeepsTextUntilDurableAcknowledgement() {
        val bridge = AitaTextFieldBridge(TextFieldValue(""))
        bridge.edit("4006381333931")
        var submitted = ""
        assertTrue(bridge.submitEditorValue(clearOnHandled = false) { submitted = it; true })
        assertEquals("4006381333931", submitted)
        assertEquals(submitted, bridge.editorValue().text)
        // A failed or still-pending save must leave the barcode available to retry.
        assertTrue(bridge.submitEditorValue(clearOnHandled = false) { it == submitted })
        assertEquals(submitted, bridge.editorValue().text)
        bridge.resetFromParent(TextFieldValue(""))
        assertEquals("", bridge.editorValue().text)
    }
    @Test fun normalSubmissionStillClearsOnlyWhenHandled() {
        val bridge = AitaTextFieldBridge(TextFieldValue("123"))
        assertFalse(bridge.submitEditorValue(clearOnHandled = true) { false })
        assertEquals("123", bridge.editorValue().text)
        assertTrue(bridge.submitEditorValue(clearOnHandled = true) { true })
        assertEquals("", bridge.editorValue().text)
    }
    @Test fun actionCanReadEditedTextFromAFieldHandleCreatedBeforeRecomposition() {
        var current = "rejected-barcode"
        val handle = GenericTextFieldContent(TextFieldValue(current), false, FocusRequester(), true,
            onReadCurrentText = { current })
        current = "4006381333931"
        assertEquals("rejected-barcode", handle.value.text)
        assertEquals("4006381333931", handle.liveText())
        current = ""
        assertEquals("", handle.liveText())
    }
    @Test fun adjacentButtonFlushesUnpublishedEditorBurstAndPreservesLaterTyping() {
        var parent = TextFieldValue("old")
        val bridge = AitaTextFieldBridge(parent)
        val reader = AitaTextFieldReadHandle()
        reader.attach {
            bridge.reportEditorChange { parent = it; it }
            bridge.editorValue().text
        }
        bridge.edit("TEA-123")
        assertEquals("old", parent.text)
        val submitted = reader.read()
        assertEquals("TEA-123", submitted)
        assertEquals("TEA-123", parent.text)
        // Durable acknowledgement for the first barcode must not clear the next pending burst.
        bridge.edit("RICE-WEIGHT")
        if (reader.read() == submitted) bridge.resetFromParent(TextFieldValue(""))
        assertEquals("RICE-WEIGHT", bridge.editorValue().text)
    }
    @Test fun liveReaderDetachCannotClearAReplacementEditor() {
        val reader = AitaTextFieldReadHandle()
        val detachOld = reader.attach { "old" }
        val detachNew = reader.attach { "new" }
        detachOld()
        assertEquals("new", reader.read())
        detachNew()
        assertEquals(null, reader.read())
    }
    @Test fun adjacentButtonReadsValidatedValueAndFlushesOnlyOnce() {
        val bridge = AitaTextFieldBridge(TextFieldValue("10"))
        val reader = AitaTextFieldReadHandle()
        var calls = 0
        reader.attach {
            bridge.reportEditorChange { calls++; TextFieldValue("10") }
            bridge.editorValue().text
        }
        bridge.edit("100")
        assertEquals("10", reader.read())
        assertEquals("10", reader.read())
        assertEquals(1, calls)
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

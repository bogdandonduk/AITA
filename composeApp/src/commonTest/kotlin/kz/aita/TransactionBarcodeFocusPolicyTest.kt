package kz.aita

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransactionBarcodeFocusPolicyTest {
    private fun target(capture: Boolean = true, window: Boolean = true, modal: Boolean = false,
        editing: Boolean = false, visible: Boolean = true, attached: Boolean = true) =
        transactionBarcodeFocusTarget(capture, window, modal, editing, visible, attached)

    @Test fun wideCheckoutUsesVisibleSearch() = assertEquals(TransactionBarcodeFocusTarget.Search, target())
    @Test fun attachedSearchNeverCompetesWithHiddenInput() = assertEquals(TransactionBarcodeFocusTarget.Search, target(attached = true))
    @Test fun paymentWithoutSearchUsesHiddenReceiver() = assertEquals(TransactionBarcodeFocusTarget.Hid, target(attached = false))
    @Test fun narrowCheckoutUsesHiddenReceiver() = assertEquals(TransactionBarcodeFocusTarget.Hid, target(visible = false, attached = false))
    @Test fun modalWinsOverSearch() = assertEquals(TransactionBarcodeFocusTarget.None, target(modal = true))
    @Test fun modalAlsoWinsOverHiddenReceiver() = assertEquals(TransactionBarcodeFocusTarget.None, target(modal = true, visible = false))
    @Test fun manualEditorIsNeverInterrupted() = assertEquals(TransactionBarcodeFocusTarget.None, target(editing = true))
    @Test fun inactiveWindowDoesNotTakeFocus() = assertEquals(TransactionBarcodeFocusTarget.None, target(window = false))
    @Test fun committedCheckoutDisablesCapture() = assertEquals(TransactionBarcodeFocusTarget.None, target(capture = false))
    @Test fun returningToCheckoutRestoresItsTarget() {
        assertEquals(TransactionBarcodeFocusTarget.None, target(capture = false))
        assertEquals(TransactionBarcodeFocusTarget.Search, target(capture = true))
    }
    @Test fun everyGateCombinationHonorsAllBlockers() {
        for (bits in 0 until 64) {
            val flags = (0..5).map { bits and (1 shl it) != 0 }
            val actual = transactionBarcodeFocusTarget(flags[0], flags[1], flags[2], flags[3], flags[4], flags[5])
            val blocked = !flags[0] || !flags[1] || flags[2] || flags[3]
            assertEquals(if (blocked) TransactionBarcodeFocusTarget.None
                else if (flags[4] && flags[5]) TransactionBarcodeFocusTarget.Search else TransactionBarcodeFocusTarget.Hid, actual)
        }
    }
    @Test fun actualDesktopAndBrowserPlatformNamesUseSearch() {
        for (platform in listOf("jvm-windows", "jvm-macos", "jvm-linux", "jvm", "wasmJs", "Desktop", "web", "browser")) {
            assertTrue(transactionPrefersVisibleSearch(platform, false), platform)
            assertFalse(transactionPrefersVisibleSearch(platform, true), platform)
        }
    }
    @Test fun mobilePlatformsKeepHiddenScannerWithoutAutoOpeningSearchKeyboard() {
        for (platform in listOf("android", "ios")) assertFalse(transactionPrefersVisibleSearch(platform, false))
    }
    @Test fun scannerCharactersAccumulateWithoutDiscardingPrefix() {
        var buffer = ""
        for (digit in "4870000001234") buffer = transactionHidBuffer(buffer + digit)
        assertEquals("4870000001234", buffer)
    }
    @Test fun replacingOrDeletingTextUsesWholeEditedValue() {
        assertEquals("487000", transactionHidBuffer("487000"))
        assertEquals("", transactionHidBuffer(""))
        assertEquals("SKU-123_A", transactionHidBuffer("SKU-123_A"))
    }
    @Test fun scannerSuffixesDoNotPolluteNextBuffer() = assertEquals("4870000001234", transactionHidBuffer("4870000001234\r\n\t"))
    @Test fun abandonedOrPastedBuffersAreBounded() = assertEquals("2".repeat(32), transactionHidBuffer("1".repeat(40) + "2".repeat(32)))
    @Test fun receiptHidInputPreservesFullIdentityAndAimPrefixCharacterByCharacter() {
        val payload = transactionReceiptBarcodePayload("12345678-1234-4234-9234-123456789abc")!!
        var buffer = ""
        for (char in "]C0$payload") buffer = transactionHidBuffer(buffer + char, 64)
        assertEquals("12345678-1234-4234-9234-123456789abc", parseTransactionReceiptBarcode(buffer + "\r\n"))
    }

}

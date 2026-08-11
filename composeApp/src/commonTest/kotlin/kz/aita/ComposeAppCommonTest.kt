package kz.aita

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComposeAppCommonTest {

    @Test
    fun example() {
        assertEquals(3, 1 + 2)
    }

    @Test
    fun newStockDraftStartsWithExactlyOneBarcodeRow() {
        assertEquals(listOf(""), emptyList<String>().normalizedInitialStockBarcodeRows())
        assertEquals(listOf(""), listOf("", "   ").normalizedInitialStockBarcodeRows())
        assertEquals(listOf("4601234567890", ""), listOf("4601234567890", "").normalizedInitialStockBarcodeRows())
    }

    @Test
    fun restoredRootStockEditorAlwaysHasAnEscapeFromEditMode() {
        assertFalse(shouldShowStockAddEditBack(isVeryFirstScreen = true, editedGoodsItemId = null))
        assertFalse(shouldShowStockAddEditBack(isVeryFirstScreen = true, editedGoodsItemId = ""))
        assertTrue(shouldShowStockAddEditBack(isVeryFirstScreen = true, editedGoodsItemId = "goods-42"))
        assertTrue(shouldShowStockAddEditBack(isVeryFirstScreen = false, editedGoodsItemId = null))
    }
}

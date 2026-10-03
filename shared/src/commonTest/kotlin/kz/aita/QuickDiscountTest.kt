package kz.aita

import kotlin.test.*

class QuickDiscountTest {
    @Test fun discountsRoundTheUnitPriceAndKeepFractionalQuantities() {
        assertEquals(90.0, discountedUnitPrice(100.0, 10.0))
        assertEquals(6.66, discountedUnitPrice(9.99, 33.333))
        assertEquals(0.0, discountedUnitPrice(12.5, 100.0))
        val sold = GoodsItemInTransactionDataModel("code", .125, 90.0, quickDiscountPercent = 10.0, priceBeforeDiscount = 100.0)
        assertEquals(1.25, sold.quickDiscountAmount())
        assertEquals(11.25, sold.quantity * sold.pricePerUnit)
    }
    @Test fun malformedDiscountsAreRejected() {
        listOf(-1.0, 100.01, Double.NaN, Double.POSITIVE_INFINITY).forEach { percent ->
            assertFalse(validQuickDiscount(percent))
            assertFailsWith<IllegalArgumentException> { discountedUnitPrice(100.0, percent) }
        }
    }
    @Test fun oldTransactionsRemainUndiscountedAndNewMetadataRoundTrips() {
        val old = jsonBase.decodeFromString<GoodsItemInTransactionDataModel>("""{"barcode":"1","quantity":2,"pricePerUnit":12}""")
        assertEquals(0.0, old.quickDiscountAmount())
        val discounted = old.copy(pricePerUnit = 10.8, quickDiscountPercent = 10.0, priceBeforeDiscount = 12.0)
        assertEquals(discounted, jsonBase.decodeFromString(jsonBase.encodeToString(GoodsItemInTransactionDataModel.serializer(), discounted)))
        assertEquals(2.4, discounted.quickDiscountAmount())
    }
    @Test fun clearingOrRemovingOneCartDoesNotLeakItsDiscountToAnother() {
        val book = CartBook(counts = listOf(4, 2, 2), ui = CartUiState(discounts = mapOf("0:2" to 10.0, "0:3" to 25.0))).validated()
        assertEquals(mapOf("0:3" to 25.0), book.withoutCart(0, 2).ui.discounts)
        val removed = book.removeSlot(0, 2).validated()
        assertEquals(mapOf("0:3" to 25.0), removed.ui.discounts)
        assertEquals(removed, jsonBase.decodeFromString(CartBook.serializer(), jsonBase.encodeToString(CartBook.serializer(), removed)).validated())
    }
}

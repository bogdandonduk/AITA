package kz.aita

import kotlin.test.*

class MarketShoppingQuantityEditTest {
    private val basis = MarketShoppingBasis(null, "KZT", "kg", 0.5)
    private val line = MarketShoppingLine("offer", "shop", "Product", "Shop", 2, basis, updatedAtMillis = 10)
    private val snapshot = MarketShoppingSnapshot("buyer", 4, listOf(MarketShoppingQuotedLine(line)), 20)
    private val review = requireNotNull(snapshot.reviewShoppingLine(line))

    @Test fun everySupportedQuantityParsesWithoutRounding() {
        for (units in 1..MARKET_SHOPPING_MAX_UNITS) assertEquals(units, marketShoppingQuantityFromDraft(units.toString()))
    }
    @Test fun outerWhitespaceAndLeadingZerosAreUnambiguous() {
        assertEquals(12, marketShoppingQuantityFromDraft(" \t0012\n"))
        assertEquals(1, marketShoppingQuantityFromDraft("0000000000000001"))
    }
    @Test fun zeroAndOverflowNeverBecomeRemoveOrMaximumQuantity() {
        for (text in listOf("0", "000", "1000", "2147483647", "2147483648", "9999999999999999"))
            assertNull(marketShoppingQuantityFromDraft(text), text)
    }
    @Test fun decimalsSignsExponentAndSeparatorsAreRejectedNotFiltered() {
        for (text in listOf("1.5", "1,5", "+5", "-5", "1e2", "1_0", "1 0", "5\n6", "12kg", "NaN"))
            assertNull(marketShoppingQuantityFromDraft(text), text)
    }
    @Test fun emptyAndNonAsciiDigitInputDoNotInventAQuantity() {
        for (text in listOf("", " \t\n", "１２", "١٢", "−2", "12\u0000"))
            assertNull(marketShoppingQuantityFromDraft(text), text)
    }
    @Test fun longInputIsRejectedBeforeScanningEvenWhenItsPrefixIsValid() {
        assertNull(marketShoppingQuantityFromDraft("00000000000000001"))
        assertNull(marketShoppingQuantityFromDraft("9".repeat(100_000)))
        assertNull(marketShoppingQuantityFromDraft("1" + " ".repeat(100_000)))
    }
    @Test fun unchangedQuantityIncludingPaddedValueIsNoOp() {
        for (text in listOf("2", "002", " 2 ")) assertNull(review.quantityEditUnits(snapshot, text))
    }
    @Test fun editRetainsSellingAmountAndDoesNotConvertToStockQuantity() {
        val units = requireNotNull(review.quantityEditUnits(snapshot, "12"))
        val command = requireNotNull(review.command(snapshot, units, "edit"))
        assertEquals(12, command.units)
        assertEquals(0.5, command.basis?.pricedAmount)
        assertEquals("kg", command.basis?.unitId)
        assertEquals(4L, command.expectedRevision)
        assertNull(command.replaceOfferId); assertNull(command.basketChange)
    }
    @Test fun noSnapshotOtherOwnerAndNewRevisionCannotUseTheDraft() {
        assertNull(review.quantityEditUnits(null, "12"))
        assertNull(review.quantityEditUnits(snapshot.copy(userId = "other"), "12"))
        assertNull(review.quantityEditUnits(snapshot.copy(revision = 5), "12"))
    }
    @Test fun removedReplacedOrEditedLineRequiresAnotherReview() {
        assertNull(review.quantityEditUnits(snapshot.copy(lines = emptyList()), "12"))
        for (changed in listOf(line.copy(units = 8), line.copy(offerId = "other"), line.copy(storeId = "other"),
            line.copy(basis = basis.copy(pricedAmount = 1.0)), line.copy(updatedAtMillis = 11))) {
            assertNull(review.quantityEditUnits(snapshot.copy(lines = listOf(MarketShoppingQuotedLine(changed))), "12"))
        }
    }
    @Test fun priceOnlyRefreshAndWithdrawnLineKeepEditableIntent() {
        assertEquals(12, review.quantityEditUnits(snapshot, "12")) // No live offer required.
        assertEquals(12, review.quantityEditUnits(snapshot.copy(checkedAtMillis = 50,
            lines = listOf(MarketShoppingQuotedLine(line, status = MARKET_QUOTE_PRICE))), "12"))
    }
    @Test fun invalidOrNoOpDraftDoesNotProduceEditUnits() {
        for (text in listOf("", "0", "-1", "1000", "5.5", "2"))
            assertNull(review.quantityEditUnits(snapshot, text), text)
    }
    @Test fun editorMessagesHaveExactTranslationsInAllSixLanguages() {
        val examples = mapOf(
            "edit" to emptyMap(), "label" to mapOf("max" to "999"),
            "current" to mapOf("units" to "2", "amount" to "0.5", "unit" to "kg"),
            "proposed" to mapOf("units" to "12", "amount" to "0.5", "unit" to "kg"),
            "invalid" to mapOf("max" to "999"), "help" to emptyMap(), "save" to emptyMap())
        for ((key, arguments) in examples) for (language in listOf("en", "ru", "kk", "ky", "tg", "uz")) {
            val text = EventMessages.renderExact(EventMessageReference("market.shopping_quantity_$key", arguments), language)
            assertNotNull(text, "$key:$language"); assertTrue(text.isNotBlank()); assertFalse(text.contains("{max}"))
        }
    }
    @Test fun proposedQuantityKeepsCountAndSellingAmountSeparate() {
        for (language in listOf("en", "ru", "kk", "ky", "tg", "uz")) {
            val text = EventMessages.renderExact(EventMessageReference("market.shopping_quantity_proposed",
                mapOf("units" to "12", "amount" to "0.5", "unit" to "kg")), language)
            assertTrue(requireNotNull(text).contains("12 × 0.5 kg"))
        }
    }
    @Test fun unitLabelIsLiteralRatherThanAnotherTemplate() {
        val text = EventMessages.renderExact(EventMessageReference("market.shopping_quantity_proposed",
            mapOf("units" to "12", "amount" to "0.5", "unit" to "{units}")), "en")
        assertEquals("New quantity: 12 × 0.5 {units}", text)
    }
    @Test fun incompleteMessageArgumentsAreNotRenderedAsAValidReview() {
        assertNull(EventMessages.renderExact(EventMessageReference("market.shopping_quantity_proposed",
            mapOf("units" to "12")), "en"))
    }

}

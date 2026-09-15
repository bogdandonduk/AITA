package kz.aita

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LegacyEventMessagesTest {
    private fun localized(en: String, ru: String, kk: String) = listOf(
        LocalizedStringDataModel("main", en), LocalizedStringDataModel("en", en),
        LocalizedStringDataModel("ru", ru), LocalizedStringDataModel("kk", kk))

    @Test fun recognizesCompleteOldStaticTextInAllThreeLanguages() {
        listOf("Workshift password updated", "Пароль смены обновлён", "Ауысым құпия сөзі жаңартылды").forEach {
            assertEquals(EventMessageReference("message.workshift_password_updated"), legacyEventMessageReference(it))
        }
    }
    @Test fun ambiguousOldSingleLanguageMessageIsNotAssignedAnArbitraryType() {
        // The old stock-item and goods-item messages deliberately had the same Russian wording.
        assertNull(legacyEventMessageReference("Товар добавлен"))
        assertNull(EventResourceCatalogue(emptyList()).referenceFor("Товар добавлен"))
    }
    @Test fun doesNotGuessFromSubstring() {
        assertNull(legacyEventMessageReference("Customer said: Stock item added; please investigate"))
    }
    @Test fun conflictingLanguageFactsAreNotSilentlyMerged() {
        val texts = localized("Batch updated: Apples", "Партия обновлена: Груши", "Партия жаңартылды: Алма")
        assertNull(texts.eventMessageReferenceOrNull())
    }
    @Test fun oldParameterizedBatchTitleRetainsItsExactName() {
        val reference = assertNotNull(legacyEventMessageReference("Batch updated: Sale {value} Shop"))
        assertEquals("Партия обновлена: Sale {value} Shop", EventMessages.render(reference, "ru"))
    }
    @Test fun multilineFactsKeepEveryLineBreakAcrossLegacyRecognition() {
        listOf("\n", "\r", "\r\n", "\u0085", "\u2028", "\u2029").forEach { separator ->
            val fact = "Tea${separator}Shelf {value} [.*] 🌿"
            val reference = assertNotNull(legacyEventMessageReference("Batch updated: $fact"))
            assertEquals("Партия обновлена: $fact", EventMessages.render(reference, "ru"))
        }
    }
    @Test fun legacyCaptureDoesNotEvaluateBracesOrRegexInUserFacts() {
        val fact = "{children} \\E.* [en] \\Q 🌿"
        val reference = assertNotNull(legacyEventMessageReference("Batch updated: $fact"))
        assertEquals("Партия обновлена: $fact", EventMessages.render(reference, "ru"))
    }
    @Test fun legacyRecognitionStillRequiresTheWholeTemplate() {
        assertNull(legacyEventMessageReference("Unrelated note\nBatch updated: Tea"))
    }
    @Test fun explicitIdentityWinsOverObsoleteFallbackWording() {
        val ref = EventMessageReference("message.stock_item_added")
        assertEquals(ref, listOf(LocalizedStringDataModel("en", "obsolete", ref)).eventMessageReferenceOrNull())
    }
    @Test fun genericOperationUsesItsOriginalEntityAndAction() {
        val event = OperationLogDataModel(action = "updated", entityType = "store",
            title = localized("Store updated", "Магазин обновлён", "Дүкен жаңартылды"), createdAtMillis = 451)
        assertEquals("Магазин: обновлено", event.localizedEventTitle("ru"))
        assertEquals(451L, event.createdAtMillis)
    }
    @Test fun conflictingGenericOperationMetadataDoesNotRewriteText() {
        val event = OperationLogDataModel(action = "deleted", entityType = "store",
            title = localized("Store updated", "Магазин обновлён", "Дүкен жаңартылды"))
        assertNull(event.resolvedTitleMessageReference())
        assertEquals("Магазин обновлён", event.localizedEventTitle("ru"))
    }
    @Test fun oldTransactionMetadataAndOriginalTotalMustMatch() {
        val event = OperationLogDataModel(action = "completed", entityType = "transaction",
            title = localized("Sale completed", "Продажа завершена", "Сату аяқталды"),
            details = localized("Sale • 1250.00", "Продажа • 1250.00", "Сату • 1250.00"),
            metadata = mapOf("type" to "purchase", "total" to "1250.00"))
        assertEquals("Сату • 1250.00", event.localizedEventDetails("kk"))
        assertNotNull(event.resolvedDetailsMessageReference())
        assertNull(event.copy(metadata = event.metadata + ("total" to "9999")).resolvedDetailsMessageReference())
    }
    @Test fun oldBatchStatusUsesSavedMetadataNotCurrentInventory() {
        val event = OperationLogDataModel(entityType = "stock_batch", action = "updated",
            metadata = mapOf("goods_name" to "Sale Shop", "quantity" to "2.0", "quantity_unit" to "kg", "status" to "OnShelf"),
            details = localized("Sale Shop • Qty: 2 kg • Status: OnShelf", "Sale Shop • Кол-во: 2 kg • Статус: OnShelf", "Sale Shop • Саны: 2 kg • Күйі: OnShelf"))
        assertNotNull(event.resolvedDetailsMessageReference())
        val text = event.localizedEventDetails("ru")
        assertTrue(text.contains("Sale Shop")); assertTrue(text.contains("2 kg")); assertTrue(text.contains("На полке"))
        assertNull(event.copy(metadata = event.metadata + ("quantity" to "3")).resolvedDetailsMessageReference())
    }
    @Test fun arbitraryOperationNotesRemainUnchanged() {
        val text = "Customer Supply Sale Return, original wording"
        val event = OperationLogDataModel(entityType = "stock_item", details = localized(text, text, text))
        assertEquals(text, event.localizedEventDetails("ru"))
    }
    @Test fun changedFieldLabelsTranslateWithoutChangingUnknownCustomFields() {
        val reference = operationChangedFieldsReference(listOf("name", "sale price override", "Custom X"))
        val text = assertNotNull(EventMessages.render(reference, "ru"))
        assertTrue(text.contains("Custom X")); assertTrue(text.contains("Цена продажи партии"))
    }
    @Test fun hugeLegacyInputIsIgnored() {
        assertNull(legacyEventMessageReference("Batch updated: " + "x".repeat(8200)))
    }
    @Test fun authenticationRepliesResolveToSharedPresentationWithoutTokens() {
        val reference = assertNotNull(legacyEventMessageReference("Invalid login or password"))
        assertEquals("Неверный логин или пароль", EventMessages.render(reference, "ru"))
        assertTrue(reference.arguments.isEmpty())
    }
}

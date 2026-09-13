package kz.aita

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EventMessagesTest {
    @Test fun everyTemplateHasMatchingParametersAndRendersAllLanguages() {
        assertTrue(EventMessages.templates.size > 350)
        val placeholders = Regex("\\{([a-zA-Z][a-zA-Z0-9_]{0,63})\\}")
        EventMessages.templates.values.forEach { template ->
            val names = placeholders.findAll(template.en).map { it.groupValues[1] }.toSet()
            val reference = EventMessageReference(template.key, names.associateWith { "original \$amount {literal} 🛒" })
            listOf("en", "ru", "kk", "ky").forEach { language ->
                val rendered = assertNotNull(EventMessages.render(reference, language), "${template.key}: $language")
                assertTrue(rendered.isNotBlank(), template.key)
                names.forEach { assertTrue(rendered.contains("original \$amount {literal} 🛒"), template.key) }
            }
        }
    }

    @Test fun historicalNotificationUsesSelectedLanguageNotStoredText() {
        val event = NotificationDataModel("old saved wording", NotificationType.Positive,
            createdAtMillis = 123, messageTemplate = EventMessageReference("message.stock_item_added"))
        assertEquals("Товар добавлен", event.localizedEventMessage("ru"))
        assertEquals("Тауар қосылды", event.localizedEventMessage("kk"))
        assertEquals("Stock item added", event.localizedEventMessage("en"))
        assertEquals(123L, event.createdAtMillis)
        assertEquals("old saved wording", event.message)
    }

    @Test fun regionalLanguageCodesSelectTheirBaseLanguage() {
        val reference = EventMessageReference("message.stock_item_added")
        assertEquals(EventMessages.render(reference, "ru"), EventMessages.render(reference, "ru-RU"))
        assertEquals(EventMessages.render(reference, "kk"), EventMessages.render(reference, "kk_KZ"))
        assertEquals(EventMessages.render(reference, "en"), EventMessages.render(reference, "de"))
    }

    @Test fun originalFactsAreNotInterpretedAsTemplatesOrCode() {
        val name = "Sale {person} <script>alert(1)</script> \$199 🛒"
        val reference = EventMessageReference("worker.request.body", children = mapOf(
            "person" to listOf(eventFact(name)), "store" to listOf(eventFact("Көк дүкен"))))
        listOf("en", "ru", "kk", "ky").forEach { language ->
            val text = assertNotNull(EventMessages.render(reference, language))
            assertTrue(text.contains(name)); assertTrue(text.contains("Көк дүкен"))
        }
    }

    @Test fun blankWorkerNamesUseTranslatableFallbackInsteadOfEnglishFact() {
        val reference = EventMessageReference("worker.request.body", children = mapOf(
            "person" to listOf(eventNamedFact("", "event.fallback.user")),
            "store" to listOf(eventNamedFact("", "event.fallback.store"))))
        assertNotNull(EventMessages.render(reference, "ru"))
        assertNotEquals(EventMessages.render(reference, "en"), EventMessages.render(reference, "ru"))
    }

    @Test fun unknownNewKeyPreservesLocalizedFallback() {
        val event = NotificationDataModel("Original", NotificationType.Neutral,
            messageTemplate = EventMessageReference("future.unknown"),
            messageTranslations = listOf(LocalizedStringDataModel("ru", "Исходный текст")))
        assertEquals("Исходный текст", event.localizedEventMessage("ru"))
    }

    @Test fun missingOrUnexpectedArgumentsDoNotEraseHistoricalText() {
        assertNull(EventMessages.render(EventMessageReference("worker.request.body"), "en"))
        assertNull(EventMessages.render(eventMessageReference("message.stock_item_added", "extra" to "x"), "ru"))
        val invalid = EventMessageReference("event.literal", mapOf("value" to "x"), mapOf("value" to listOf(eventFact("y"))))
        assertNull(EventMessages.render(invalid, "en"))
    }

    @Test fun dedupeIdentityDoesNotDependOnMapInsertionOrderOrLanguage() {
        val a = EventMessageReference("supplier.orders.partial.updated", linkedMapOf("updatedCount" to "2", "requestedCount" to "5"))
        val b = EventMessageReference(a.key, linkedMapOf("requestedCount" to "5", "updatedCount" to "2"))
        assertEquals(EventMessages.identity(a), EventMessages.identity(b))
        assertNotEquals(EventMessages.render(a, "en"), EventMessages.render(b, "ru"))
        assertNotEquals(EventMessages.identity(a), EventMessages.identity(a.copy(arguments = a.arguments + ("updatedCount" to "3"))))
    }

    @Test fun nestedGraphAndHugeFactsAreBounded() {
        var reference = eventFact("x")
        repeat(9) { reference = EventMessageReference("event.join", children = mapOf("parts" to listOf(reference))) }
        assertNull(EventMessages.render(reference, "en"))
        assertNull(EventMessages.render(eventFact("x".repeat(16_385)), "en"))
        assertNull(EventMessages.render(EventMessageReference("event.join", children = mapOf("parts" to List(41) { eventFact("x") })), "en"))
        assertTrue(EventMessages.identity(reference).isNotEmpty())
    }

    @Test fun localizedCompatibilityListCarriesOnlyOneReference() {
        val reference = EventMessageReference("message.stock_item_added")
        val values = EventMessages.localized(reference)
        assertEquals(listOf("main", "en", "ru", "kk", "ky"), values.map { it.language })
        assertEquals(1, values.count { it.messageTemplate != null })
        assertEquals(reference, values.explicitEventMessageReference())
    }

    @Test fun knownStorageKeepsIdentityNotDuplicatedWording() {
        val text = eventTextForStorage("Stock item added")
        assertEquals(EventMessageReference("message.stock_item_added"), text.reference)
        assertEquals("", text.fallback); assertTrue(text.translations.isEmpty())
    }

    @Test fun explicitlyTypedLanguageVariantsShareStorageIdentity() {
        val reference = EventMessageReference("message.stock_item_added")
        assertEquals(eventTextIdentity(eventTextForStorage("Stock item added", reference = reference)),
            eventTextIdentity(eventTextForStorage("Товар добавлен", reference = reference)))
    }

    @Test fun unknownKeysCannotMergeDifferentFallbackMessages() {
        val key = EventMessageReference("future.event")
        val a = eventTextForStorage("one", reference = key)
        val b = eventTextForStorage("two", reference = key)
        assertEquals("one", a.fallback)
        assertNotEquals(eventTextIdentity(a), eventTextIdentity(b))
    }

    @Test fun deviceIdentityAlsoPreservesUnknownFutureFallbackDifferences() {
        val event = NotificationDataModel("one", NotificationType.Neutral,
            messageTemplate = EventMessageReference("future.event"))
        val resources = EventResourceCatalogue(emptyList())
        assertNotEquals(event.eventMessageIdentity(resources), event.copy(message = "two").eventMessageIdentity(resources))
    }

    @Test fun currentResourceWordingReplacesOldHistoryWording() {
        fun resources(en: String, ru: String) = EventResourceCatalogue(listOf(LocalizedStringGroupDataModel(701,
            listOf(LocalizedStringDataModel("en", en), LocalizedStringDataModel("ru", ru), LocalizedStringDataModel("kk", "Жаңа")))))
        val old = resources("Old", "Старое")
        val saved = eventTextForStorage("Old", resources = old)
        assertEquals(EventMessageReference("resource.701"), saved.reference)
        val current = resources("New", "Новое")
        assertEquals("Новое", EventMessages.render(saved.reference, "ru", current::values))
        assertEquals("", saved.fallback)
    }

    @Test fun ambiguousResourceTextIsNotAssignedAnArbitraryId() {
        val catalogue = EventResourceCatalogue(listOf(701L, 702L).map { id ->
            LocalizedStringGroupDataModel(id, listOf(LocalizedStringDataModel("en", "Ambiguous wording"))) })
        assertNull(catalogue.referenceFor("Ambiguous wording"))
    }

    @Test fun unknownCustomizedWordingSurvivesStorageAndCompatibilityRendering() {
        val source = listOf(LocalizedStringDataModel("en", "Custom exact text"), LocalizedStringDataModel("ru", "Особый текст"))
        val saved = eventTextForStorage("Custom exact text", source)
        assertNull(saved.reference); assertEquals(source, saved.translations)
        assertEquals(source, eventTextCompatibilityValues(saved.reference, source))
    }

    @Test fun outputForOlderClientsHasReadableTranslations() {
        val reference = EventMessageReference("message.stock_item_added")
        val rendered = eventTextCompatibilityValues(reference, emptyList())
        assertEquals("Товар добавлен", rendered.first { it.language == "ru" }.value)
        assertEquals(listOf("main", "en", "ru", "kk", "ky"), rendered.map { it.language })
    }

    @Test fun fileNotificationLabelsTranslateButPathsStayLiteral() {
        val reference = EventMessageReference("device.file.location", mapOf("folder" to "/home/{value}/Receipts", "file" to "receipt 🛒.pdf"),
            children = mapOf("result" to listOf(eventFact("PDF"))))
        val text = assertNotNull(EventMessages.render(reference, "ru"))
        assertTrue(text.contains("Папка: /home/{value}/Receipts"))
        assertTrue(text.contains("Файл: receipt 🛒.pdf"))
    }

    @Test fun searchCanUseCurrentLocalizedHistoryText() {
        val event = NotificationDataModel("Old", NotificationType.Neutral,
            titleTemplate = EventMessageReference("worker.request.title"),
            messageTemplate = EventMessageReference("message.stock_item_added"))
        val haystack = listOf(event.localizedEventTitle("ru"), event.localizedEventMessage("ru")).joinToString(" ").lowercase()
        assertTrue(haystack.contains("товар")); assertFalse(haystack.contains("old"))
    }
}

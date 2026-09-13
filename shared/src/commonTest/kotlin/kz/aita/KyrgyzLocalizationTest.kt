package kz.aita

import kotlin.test.*

class KyrgyzLocalizationTest {
    @Test fun kyrgyzPreferencesAndRegionalTagsSurviveNormalization() {
        for (language in listOf("ky", "ky-KG", "KY_kg", " ky ")) {
            assertEquals("ky", normalizeAppLanguagePreference(language))
        }
        assertEquals("tg", normalizeAppLanguagePreference("tg-TJ"))
        assertEquals("system", normalizeAppLanguagePreference("system"))
        assertEquals(DEFAULT_APP_LANGUAGE, normalizeAppLanguagePreference("kg"))
        assertEquals(DEFAULT_APP_LANGUAGE, normalizeAppLanguagePreference("unknown"))
    }

    @Test fun kyrgyzOfflineAndLoginChoicesAreNotReplacedByOlderAccountPreferences() {
        val choice = UserPreferencesDataModel("ky", 1, 1)
        val previous = UserPreferencesDataModel("ru", 0, 0)
        val result = resolveAppPreferenceChoice(AppPreferenceIntent(), previous, 0, choice,
            AuthScreenPreferenceOverrideDataModel())
        assertEquals(choice, result.value)
        assertTrue(result.shouldSync)
        val loginChoice = resolveAppPreferenceChoice(AppPreferenceIntent(), previous, 0, null,
            AuthScreenPreferenceOverrideDataModel(appLanguage = "ky", languageTouched = true))
        assertEquals(previous.copy(appLanguage = "ky"), loginChoice.value)
    }

    @Test fun coldStartAndOldCataloguesOfferKyrgyzAndKeepTajik() {
        val languages = emptyList<AppLanguageDataModel>().withBundledAppLanguages()
        assertEquals(SUPPORTED_APP_LANGUAGES.toSet(), languages.map { it.language }.toSet())
        val kyrgyz = languages.single { it.language == "ky" }
        assertEquals("Кыргызча", kyrgyz.name.single { it.language == "ky" }.value)
        assertEquals("png/flag_kg.png", kyrgyz.flagDrawablePath)
        assertEquals("png/flag_tj.png", languages.single { it.language == "tg" }.flagDrawablePath)
        assertEquals(languages, languages.withBundledAppLanguages())
    }

    @Test fun languageEnrichmentPreservesExistingServerNames() {
        val original = AppLanguageDataModel("ru", listOf(LocalizedStringDataModel("en", "Custom Russian")), "custom.png")
        val enriched = listOf(original).withBundledAppLanguages().single { it.language == "ru" }
        assertEquals("Custom Russian", enriched.name.single { it.language == "en" }.value)
        assertEquals("Орусча", enriched.name.single { it.language == "ky" }.value)
        assertEquals(original.flagDrawablePath, enriched.flagDrawablePath)
    }

    @Test fun staleEnglishResourceDoesNotMaskKyrgyzOrTajik() {
        val remote = listOf(LocalizedStringGroupDataModel(2658, listOf(LocalizedStringDataModel("en", "Work"))))
        val bundled = listOf(LocalizedStringGroupDataModel(2658, listOf(
            LocalizedStringDataModel("ky", "Иш"), LocalizedStringDataModel("tg", "Кор"))))
        val catalog = AppearanceCatalog.build(strings = remote, bundledStrings = bundled)
        assertEquals("Иш", catalog.string(2658, "ky-KG"))
        assertEquals("Кор", catalog.string(2658, "tg"))
        assertEquals("Work", catalog.string(2658, "en"))
    }

    @Test fun explicitServerKyrgyzWinsAndFirstDuplicateStillWins() {
        val first = LocalizedStringGroupDataModel(2658, listOf(LocalizedStringDataModel("ky", "Иш орду")))
        val second = first.copy(values = listOf(LocalizedStringDataModel("ky", "Экинчи")))
        val catalog = AppearanceCatalog.build(strings = listOf(first, second))
        assertEquals("Иш орду", catalog.string(2658, "ky"))
    }

    @Test fun sharedKyrgyzStringsAreAvailableWithoutDownloadedResources() {
        assertEquals("Иш", AppearanceCatalog.build().string(2658, "ky"))
        assertEquals("Иш", EventMessages.render(EventMessageReference("resource.2658"), "ky"))
        assertNull(bundledKyrgyzStringResource(Long.MAX_VALUE))
        assertNull(AppearanceCatalog.build().string(Long.MAX_VALUE, "ky"))
    }

    @Test fun unknownResourceStillKeepsItsAuthoredFallback() {
        val value = LocalizedStringGroupDataModel(Long.MAX_VALUE,
            listOf(LocalizedStringDataModel("main", "Unchanged name {amount}")))
        assertEquals("Unchanged name {amount}", AppearanceCatalog.build(strings = listOf(value)).string(Long.MAX_VALUE, "ky"))
    }

    @Test fun literalNamesAreNotTranslatedOrRecursivelyInterpolated() {
        val text = "Supplier {amount} \$123 <b>name</b>"
        assertEquals(text, listOf(LocalizedStringDataModel("main", text)).extractLocalizedString("ky"))
        assertEquals(text, EventMessages.render(eventFact(text), "ky"))
    }

    @Test fun kyrgyzEventsKeepOneIdentityAndExplicitCompatibilityText() {
        val reference = EventMessageReference("message.stock_item_added")
        val values = EventMessages.localized(reference)
        assertTrue(values.single { it.language == "ky" }.value.isNotBlank())
        assertEquals(reference, values.explicitEventMessageReference())
        assertEquals(1, values.count { it.messageTemplate != null })
        assertEquals(EventMessages.render(reference, "ky"), EventMessages.render(reference, "KY_kg"))
        assertNotEquals(EventMessages.render(reference, "en"), EventMessages.render(reference, "ky"))
    }
}

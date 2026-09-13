package kz.aita

import kotlin.test.*

class AppLanguageRuntimeTest {
    @Test fun sixLanguagesAndLegacyAliasesNormalizeConsistently() {
        for ((tag, expected) in listOf("TG_tj" to "tg", "TJ" to "tg", "ky-KG" to "ky",
            "UZ_Latn_UZ" to "uz", "KK-kz" to "kk", "kz" to "kk", "ru-RU" to "ru", "en-US" to "en")) {
            assertEquals(expected, normalizeAppLanguagePreference(tag))
            assertEquals(expected, normalizeAuthEmailLocale(tag))
        }
        assertEquals("ru", normalizeAppLanguagePreference("unknown"))
        assertEquals("en", normalizeAuthEmailLocale("unknown"))
        assertEquals("en", normalizeAuthEmailLocale("rubbish"))
        assertEquals("en", normalizeAuthEmailLocale("system"))
        assertEquals("en", authRequestLocale("rubbish"))
        assertEquals("tg", authRequestLocale("TJ"))
        assertEquals(effectiveAppLanguage("system"), authRequestLocale("system"))
    }

    @Test fun systemIsPersistedButResolvedForEachDeviceAndResume() {
        assertEquals("system", normalizeAppLanguagePreference(" SYSTEM "))
        for (language in SUPPORTED_APP_LANGUAGES) assertEquals(language, effectiveAppLanguage("system", language))
        assertEquals("tg", effectiveAppLanguage("system", "tg-TJ"))
        assertEquals("uz", effectiveAppLanguage("system", "uz-Latn-UZ"))
        assertEquals("ru", effectiveAppLanguage("system", "de-DE"))
        assertEquals("ky", effectiveAppLanguage("ky", "ru-RU"))
        assertEquals("system", UserPreferencesDataModel("system").appLanguage)
    }

    @Test fun sixChoicesSurviveEachOfflineAndLoginSelection() {
        for (language in SUPPORTED_APP_LANGUAGES + "system") {
            val selected = UserPreferencesDataModel(language, 1, 1)
            val stale = UserPreferencesDataModel("ru", 0, 0)
            val pending = resolveAppPreferenceChoice(AppPreferenceIntent(), stale, 0, selected,
                AuthScreenPreferenceOverrideDataModel())
            assertEquals(selected, pending.value)
            assertTrue(pending.shouldSync)
            val clickDuringFetch = resolveAppPreferenceChoice(AppPreferenceIntent(selected, 4, true),
                stale, 3, null, AuthScreenPreferenceOverrideDataModel())
            assertEquals(selected, clickDuringFetch.value)
        }
    }

    @Test fun coercedWrongAndPartialAcknowledgementsCannotSettleAChoice() {
        for (language in listOf("tg", "ky", "uz", "system")) {
            val choice = UserPreferencesDataModel(language, 1, 1)
            assertTrue(appPreferenceAcknowledges(choice, choice))
            assertFalse(appPreferenceAcknowledges(choice, choice.copy(appLanguage = "ru")))
            assertFalse(appPreferenceAcknowledges(choice, choice.copy(appLanguage = "unsupported")))
            assertFalse(appPreferenceAcknowledges(choice, choice.copy(appThemeId = 0)))
            assertFalse(appPreferenceAcknowledges(choice, choice.copy(appSizeModeId = 0)))
        }
        assertTrue(appPreferenceAcknowledges(UserPreferencesDataModel("uz"), UserPreferencesDataModel("UZ_latn_UZ")))
        assertFalse(appPreferenceAcknowledges(UserPreferencesDataModel("ru"), UserPreferencesDataModel("garbage")))
    }

    @Test fun staleEnglishCannotMaskAnyNewBundledLocale() {
        val primary = listOf(LocalizedStringDataModel("main", "Old Work"), LocalizedStringDataModel("en", "Old Work"))
        val expected = mapOf("tg" to "Кор", "ky" to "Иш", "uz" to "Ish")
        for ((language, text) in expected) {
            assertEquals(text, resolveLocalizedResource(2658, language, primary, null))
            assertEquals(text, listOf(LocalizedStringGroupDataModel(2658, primary)).extractString(2658, language))
            assertEquals(text, AppearanceCatalog.build().string(2658, language))
            assertEquals(text, EventMessages.render(EventMessageReference("resource.2658"), language))
        }
    }

    @Test fun everyExactSourcePrecedesEveryMainOrEnglishFallback() {
        val primary = listOf(LocalizedStringDataModel("main", "Primary"), LocalizedStringDataModel("ky", ""))
        val bundled = listOf(LocalizedStringDataModel("ky", "Explicit bundle"), LocalizedStringDataModel("ru", "Bundle RU"))
        assertEquals("Explicit bundle", resolveLocalizedResource(2658, "ky", primary, bundled))
        assertEquals("Bundle RU", resolveLocalizedResource(2658, "ru", primary, bundled))
        assertEquals("Explicit remote", resolveLocalizedResource(2658, "KY_kg",
            primary + LocalizedStringDataModel("ky", "Explicit remote"), bundled))
        assertEquals("Primary", resolveLocalizedResource(2658, "main", primary, bundled))
    }

    @Test fun legacyProjectionMergesOnceAndDoesNotReverseDuplicatePrecedence() {
        fun group(id: Long, code: String, text: String) = LocalizedStringGroupDataModel(id, listOf(LocalizedStringDataModel(code, text)))
        val primary = listOf(group(2658, "en", "First"), group(2658, "en", "Last"))
        val bundled = listOf(group(2658, "ky", "Bundled"), group(99, "ru", "Other"))
        val merged = mergeLocalizedStringGroups(primary, bundled)
        assertEquals(2, merged.size)
        assertEquals("First", merged.extractString(2658, "en"))
        assertEquals("Bundled", merged.extractString(2658, "ky"))
        assertEquals("Other", merged.extractString(99, "ru"))
        assertEquals(merged, mergeLocalizedStringGroups(merged, bundled))
    }

    @Test fun unknownIdsAndLiteralUserNamesAreNotTranslatedBySimilarity() {
        val literal = "Work {amount} <b> AITA"
        assertEquals(literal, listOf(LocalizedStringDataModel("main", literal)).extractLocalizedString("uz"))
        assertNull(bundledTranslatedStringResource(Long.MAX_VALUE, "uz"))
        assertEquals(literal, resolveLocalizedResource(Long.MAX_VALUE, "uz",
            listOf(LocalizedStringDataModel("main", literal)), null))
    }

    @Test fun regionalLanguageDescriptorsDeduplicateAndRetainCustomNames() {
        val custom = AppLanguageDataModel("KY_kg", listOf(LocalizedStringDataModel("en", "My Kyrgyz")), "custom.png")
        val result = listOf(custom, custom.copy(language = "ky"), custom.copy(language = "unknown")).withBundledAppLanguages()
        assertEquals(SUPPORTED_APP_LANGUAGES.toSet(), result.map { it.language }.toSet())
        assertEquals(6, result.size)
        assertEquals("My Kyrgyz", result.single { it.language == "ky" }.name.exactLocalizedValue("en"))
        assertEquals("custom.png", result.single { it.language == "ky" }.flagDrawablePath)
        assertEquals("png/flag_uz.png", result.single { it.language == "uz" }.flagDrawablePath)
        assertEquals(result, result.withBundledAppLanguages())
    }

    @Test fun blankAndRegionSpecificResourceValuesAreHandledExplicitly() {
        val values = listOf(LocalizedStringDataModel("ky", ""), LocalizedStringDataModel("KY_kg", "Value"),
            LocalizedStringDataModel("main", "Fallback"))
        assertEquals("Value", values.exactLocalizedValue("ky-KG"))
        assertEquals("Value", values.withMissingLocalizedValues(listOf(LocalizedStringDataModel("ky", "Bundle"))).exactLocalizedValue("ky"))
    }

    @Test fun knownEnglishTemplateDoesNotEraseAnAuthoredUzbekTranslation() {
        val ref = EventMessageReference("message.stock_item_added")
        val authored = listOf(LocalizedStringDataModel("uz", "Tarjima", ref))
        assertEquals("Tarjima", authored.extractLocalizedString("uz"))
        val stored = eventTextForStorage("Fallback", authored, ref)
        assertEquals("Tarjima", stored.translations.single().value)
        val compatibility = eventTextCompatibilityValues(ref, stored.translations)
        assertEquals("Tarjima", compatibility.single { it.language == "uz" }.value)
        assertEquals(1, compatibility.count { it.messageTemplate != null })
        assertNotNull(EventMessages.render(ref, "uz"))
        assertNull(EventMessages.renderExact(ref, "uz"))
    }

    @Test fun unknownFutureLanguageFallbackIsRetainedToo() {
        val ref = EventMessageReference("message.stock_item_added")
        val authored = listOf(LocalizedStringDataModel("de", "Deutsch", ref))
        assertNull(EventMessages.renderExact(ref, "de"))
        assertEquals(authored, eventTextForStorage("Fallback", authored, ref).translations)
        assertEquals("Deutsch", authored.extractLocalizedString("de"))
    }

    @Test fun exactNestedRenderingNeverReinterpretsUserFacts() {
        val fact = "{amount} <b> 123"
        for (language in listOf("tg", "ky", "uz")) assertEquals(fact, EventMessages.renderExact(eventFact(fact), language))
        val joined = EventMessageReference("event.join", children = mapOf("parts" to listOf(
            EventMessageReference("resource.2658"), eventFact(fact))))
        assertTrue(requireNotNull(EventMessages.renderExact(joined, "uz")).contains("Ish"))
        assertTrue(requireNotNull(EventMessages.renderExact(joined, "uz")).contains(fact))
    }
    @Test fun notificationsAndOperationHistoryKeepExactTranslationsOverEnglishFallbacks() {
        val ref = EventMessageReference("message.stock_item_added")
        val values = listOf(LocalizedStringDataModel("uz", "Tarjima", ref))
        val notification = NotificationDataModel("Original", NotificationType.Neutral,
            messageTemplate = ref, titleTemplate = ref, messageTranslations = values, titleTranslations = values)
        assertEquals("Tarjima", notification.localizedEventMessage("uz"))
        assertEquals("Tarjima", notification.localizedEventTitle("uz"))
        val log = OperationLogDataModel(title = values, details = values, titleTemplate = ref, detailsTemplate = ref)
        assertEquals("Tarjima", log.localizedEventTitle("uz"))
        assertEquals("Tarjima", log.localizedEventDetails("uz"))
    }

}

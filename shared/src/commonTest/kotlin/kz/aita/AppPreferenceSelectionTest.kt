package kz.aita

import kotlin.test.*

class AppPreferenceSelectionTest {
    private val server = UserPreferencesDataModel("en", 0L, 0L)
    private val choice = UserPreferencesDataModel("kk", 1L, 1L)
    private val untouched = AuthScreenPreferenceOverrideDataModel()

    @Test fun initialAccountPreferencesAreAdopted() {
        val result = resolveAppPreferenceChoice(AppPreferenceIntent(), server, 0L, null, untouched)
        assertEquals(server, result.value); assertFalse(result.shouldSync)
    }
    @Test fun firstClickWhileAccountLoadsIsNotMistakenForHydration() {
        val result = resolveAppPreferenceChoice(AppPreferenceIntent(choice, 1L, true), server, 0L, null, untouched)
        assertEquals(choice, result.value); assertTrue(result.shouldSync)
    }
    @Test fun hydrationDuringAccountLoadDoesNotOverrideAccountDefaults() {
        assertEquals(server, resolveAppPreferenceChoice(AppPreferenceIntent(choice, 1L), server, 0L, null, untouched).value)
    }
    @Test fun anotherAccountDoesNotInheritEarlierSelections() {
        assertEquals(server, resolveAppPreferenceChoice(AppPreferenceIntent(choice, 7L, true), server, 7L, null, untouched).value)
    }
    @Test fun pendingOfflineChoicesOutrankServer() {
        assertEquals(choice, resolveAppPreferenceChoice(AppPreferenceIntent(), server, 0L, choice, untouched).value)
    }
    @Test fun matchingPendingChoiceStillNeedsAcknowledgement() {
        assertTrue(resolveAppPreferenceChoice(AppPreferenceIntent(), server, 0L, server, untouched).shouldSync)
    }
    @Test fun loginLanguageOverridePreservesOtherAccountPreferences() {
        val result = resolveAppPreferenceChoice(AppPreferenceIntent(), choice, 0L, null,
            AuthScreenPreferenceOverrideDataModel(appLanguage = "ru", languageTouched = true))
        assertEquals(choice.copy(appLanguage = "ru"), result.value); assertTrue(result.shouldSync)
    }
    @Test fun loginThemeOverridePreservesOtherAccountPreferences() {
        assertEquals(server.copy(appThemeId = 1L), resolveAppPreferenceChoice(AppPreferenceIntent(), server, 0L, null,
            AuthScreenPreferenceOverrideDataModel(appThemeId = 1L, themeTouched = true)).value)
    }
    @Test fun loginScaleOverridePreservesOtherAccountPreferences() {
        assertEquals(server.copy(appSizeModeId = 1L), resolveAppPreferenceChoice(AppPreferenceIntent(), server, 0L, null,
            AuthScreenPreferenceOverrideDataModel(appSizeModeId = 1L, sizeModeTouched = true)).value)
    }
    @Test fun recentLoginOverridesAlsoOutrankAnOlderOfflineJournal() {
        val result = resolveAppPreferenceChoice(AppPreferenceIntent(), server, 0L, choice,
            AuthScreenPreferenceOverrideDataModel(appLanguage = "ru", languageTouched = true))
        assertEquals(choice.copy(appLanguage = "ru"), result.value)
    }
    @Test fun untouchedFieldsAreNotAccidentallyClearedByNullableOverrides() {
        assertEquals(choice, resolveAppPreferenceChoice(AppPreferenceIntent(), server, 0L, choice,
            AuthScreenPreferenceOverrideDataModel(appLanguage = null, appThemeId = 0L)).value)
    }
    @Test fun pendingJournalValuesAreNormalized() {
        val result = resolveAppPreferenceChoice(AppPreferenceIntent(), server, 0L,
            UserPreferencesDataModel("not-a-language", -99, 999), untouched)
        assertEquals(normalizeAppLanguagePreference("not-a-language"), result.value.appLanguage)
        assertEquals(normalizeAppThemePreference(-99), result.value.appThemeId)
        assertEquals(normalizeAppSizeModePreference(999), result.value.appSizeModeId)
    }
}

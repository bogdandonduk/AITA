package kz.aita

import kotlin.test.*
import kotlinx.serialization.decodeFromString

class AppFontPreferenceTest {
    @Test fun olderClientsOmitFontWithoutClearingTheServersChoice() {
        assertNull(jsonBase.decodeFromString<UserPreferencesDataModel>("{\"appLanguage\":\"ru\",\"appThemeId\":0,\"appSizeModeId\":0}").appFontId)
        assertEquals(DEFAULT_APP_FONT_ID,normalizeAppFontPreference("missing-font"))
    }
    @Test fun fontsSurviveAccountAdoptionAndLaterLanguageChanges() {
        val account=UserPreferencesDataModel("ru",1,1,"plex_serif")
        val result=resolveAppPreferenceChoice(AppPreferenceIntent(),account,0,null,
            AuthScreenPreferenceOverrideDataModel(appLanguage="kk",languageTouched=true))
        assertEquals(account.copy(appLanguage="kk"),result.value)
    }
    @Test fun loginFontChoiceBeatsTheAccountWithoutOverwritingOtherPreferences() {
        val account=UserPreferencesDataModel("ru",1,1,"noto_sans")
        val result=resolveAppPreferenceChoice(AppPreferenceIntent(),account,0,null,
            AuthScreenPreferenceOverrideDataModel(appFontId="ubuntu",fontTouched=true))
        assertEquals(account.copy(appFontId="ubuntu"),result.value)
        assertTrue(result.shouldSync)
    }
    @Test fun oldServerCannotAcknowledgeAFontItHasNotSaved() {
        val selected=UserPreferencesDataModel("en",0,0,"lato")
        assertFalse(appPreferenceAcknowledges(selected,selected.copy(appFontId=null)))
        assertTrue(appPreferenceAcknowledges(selected,selected))
    }
}

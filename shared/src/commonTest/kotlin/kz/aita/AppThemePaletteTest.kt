package kz.aita

import kotlin.test.*

class AppThemePaletteTest {
    @Test fun newThemesRemainDistinctSavedPreferences() {
        assertEquals(2L,normalizeAppThemePreference(APP_THEME_PURPLE))
        assertEquals(3L,normalizeAppThemePreference(APP_THEME_BLUE))
        assertEquals(0L,normalizeAppThemePreference(-99))
        assertEquals(0L,normalizeAppThemePreference(null))
    }
    @Test fun allColoredThemesUseDarkIconVariants() {
        for(id in listOf(1L,APP_THEME_PURPLE,APP_THEME_BLUE)) {
            assertTrue(isDarkAppTheme(id));assertEquals(1L,appDrawableThemeId(id))
            assertEquals("svg/212_1.svg",AppearanceCatalog.build().drawable(212,id))
            assertEquals("svg/55_1.svg",emptyList<StylizedDrawablePathsGroupDataModel>().extractPath(55,id))
        }
        assertEquals("svg/55_0.svg",AppearanceCatalog.build().drawable(55,0))
    }
    @Test fun oldServerPaletteStillGetsTintedSurfacesAndUnchangedAccent() {
        val old=listOf(
            StylizedColorGroupDataModel(0,listOf(StylizedColorDataModel(-1,"ffffba24"))),
            StylizedColorGroupDataModel(1,listOf(StylizedColorDataModel(0,"ffffffff"),StylizedColorDataModel(1,"ff000000"))),
            StylizedColorGroupDataModel(2,listOf(StylizedColorDataModel(0,"ff000000"),StylizedColorDataModel(1,"ffffffff"))))
        val catalogue=AppearanceCatalog.build(colors=old)
        assertEquals("ff241c36",catalogue.color(1,APP_THEME_PURPLE))
        assertEquals("ff17212b",catalogue.color(1,APP_THEME_BLUE))
        for(theme in SUPPORTED_APP_THEME_IDS)assertEquals("ffffba24",catalogue.color(0,theme))
        for(theme in listOf(APP_THEME_PURPLE,APP_THEME_BLUE))assertEquals("ffffffff",catalogue.color(2,theme))
        assertEquals("ff000000",catalogue.color(1,1))
        assertEquals("ffffffff",catalogue.color(1,0))
    }
    @Test fun explicitRemoteThemeVariantWins() {
        val palette=listOf(StylizedColorGroupDataModel(1,listOf(StylizedColorDataModel(APP_THEME_BLUE,"ff182533"))))
        assertEquals("ff182533",AppearanceCatalog.build(colors=palette).color(1,APP_THEME_BLUE))
    }
    @Test fun oldConfigurationAndLocalesExposeAllFourThemes() {
        val old=AppThemeDataModel(0,listOf(LocalizedStringDataModel("en","Existing name")))
        val themes=availableAppThemes(listOf(old))
        assertEquals(SUPPORTED_APP_THEME_IDS,themes.map {it.id});assertEquals(old,themes.first())
        themes.filter {it.id>=2}.forEach {theme->
            assertEquals(setOf("en","ru","kk","ky","tg","uz"),theme.name.map {it.language}.toSet())
        }
    }
}

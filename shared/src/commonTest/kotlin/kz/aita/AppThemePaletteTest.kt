package kz.aita

import kotlin.test.*
import kotlin.math.pow

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
        for(theme in SUPPORTED_APP_THEME_IDS.filter { it < APP_THEME_BUTTER_YELLOW })
            assertEquals("ffffba24",catalogue.color(0,theme))
        for(theme in listOf(APP_THEME_PURPLE,APP_THEME_BLUE))assertEquals("ffffffff",catalogue.color(2,theme))
        assertEquals("ff000000",catalogue.color(1,1))
        assertEquals("ffffffff",catalogue.color(1,0))
    }
    @Test fun explicitRemoteThemeVariantWins() {
        val palette=listOf(StylizedColorGroupDataModel(1,listOf(StylizedColorDataModel(APP_THEME_BLUE,"ff182533"))))
        assertEquals("ff182533",AppearanceCatalog.build(colors=palette).color(1,APP_THEME_BLUE))
    }
    @Test fun oldConfigurationCannotRestoreGenericNames() {
        val old=AppThemeDataModel(0,listOf(LocalizedStringDataModel("en","Existing name")))
        val themes=availableAppThemes(listOf(old))
        assertEquals(SUPPORTED_APP_THEME_IDS,themes.map {it.id})
        assertEquals("Porcelain White", themes.first().name.first { it.language == "en" }.value)
        themes.forEach {theme->
            assertEquals(setOf("en","ru","kk","ky","tg","uz"),theme.name.map {it.language}.toSet())
        }
    }

    @Test fun everyShadeRoundTripsAndChoosesReadableTextAndIcons() {
        fun luminance(hex: String): Double {
            val rgb = hex.takeLast(6).chunked(2).map { it.toInt(16) / 255.0 }.map {
                if (it <= .04045) it / 12.92 else ((it + .055) / 1.055).pow(2.4)
            }
            return rgb[0] * .2126 + rgb[1] * .7152 + rgb[2] * .0722
        }
        assertEquals(30, SUPPORTED_APP_THEME_IDS.size)
        assertEquals(SUPPORTED_APP_THEME_IDS.size, SUPPORTED_APP_THEME_IDS.distinct().size)
        for (theme in SUPPORTED_APP_THEME_IDS) {
            assertEquals(theme, normalizeAppThemePreference(theme))
            val dark = isDarkAppTheme(theme)
            val background = luminance(appThemeSwatch(theme))
            val foreground = luminance(tintedAppThemeColor(2, theme) ?: if (dark) "ffffffff" else "ff000000")
            val contrast = (maxOf(background, foreground) + .05) / (minOf(background, foreground) + .05)
            assertTrue(contrast >= 7.0, "Theme $theme text contrast: $contrast")
            assertEquals(if (dark) "svg/215_1.svg" else "svg/215_0.svg", AppearanceCatalog.build().drawable(215, theme))
        }
        assertFalse(isDarkAppTheme(APP_THEME_BUTTER_YELLOW))
        assertFalse(isDarkAppTheme(APP_THEME_WARM_IVORY))
    }
    @Test fun lightShadesOverrideOldDarkServerFallbacks() {
        val old = listOf(
            StylizedColorGroupDataModel(1, listOf(StylizedColorDataModel(0,"ffffffff"), StylizedColorDataModel(1,"ff111111"))),
            StylizedColorGroupDataModel(2, listOf(StylizedColorDataModel(0,"ff000000"), StylizedColorDataModel(1,"ffffffff"))))
        val catalog = AppearanceCatalog.build(colors = old)
        for (theme in listOf(APP_THEME_BUTTER_YELLOW, APP_THEME_WARM_IVORY)) {
            assertEquals(appThemeSwatch(theme), catalog.color(1, theme))
            assertEquals("ff211e18", catalog.color(2, theme))
            assertEquals("svg/29_0.svg", catalog.drawable(29, theme))
        }
    }
}

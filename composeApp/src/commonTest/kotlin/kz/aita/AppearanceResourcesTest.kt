package kz.aita

import androidx.compose.ui.graphics.toArgb
import kotlin.test.*

class AppearanceResourcesTest {
    @Test fun bothDefaultPalettesInitializeWithoutResourceIo() {
        val palettes = UiAppearanceResources(AppearanceCatalog.build())
        assertEquals(0xffffffff.toInt(), palettes.color(1,0).toArgb())
        assertEquals(0xff111111.toInt(), palettes.color(1,1).toArgb())
        assertEquals(0xff000000.toInt(), palettes.color(2,0).toArgb())
        assertEquals(0xffffffff.toInt(), palettes.color(2,1).toArgb())
    }
    @Test fun rgbAndArgbFormsUseOpaqueRgbAndPreserveArgb() {
        for (text in listOf("#aabbcc", "ffaabbcc", "#ffaabbcc", "0xffaabbcc"))
            assertEquals(0xffaabbcc.toInt(), parseAppearanceColor(text).toArgb())
        assertEquals(0x80aabbcc.toInt(), parseAppearanceColor("80aabbcc").toArgb())
    }
    @Test fun invalidRemoteColorCannotCrashAppearanceSwitching() {
        val resources = UiAppearanceResources(AppearanceCatalog.build(colors=listOf(
            StylizedColorGroupDataModel(1, listOf(StylizedColorDataModel(0,"not-hex"))))))
        assertEquals(0xffffffff.toInt(), resources.color(1,0).toArgb())
    }
    @Test fun missingDrawableKeepsTheSelectedTheme() {
        val resources=UiAppearanceResources(AppearanceCatalog.build())
        assertEquals("svg/65_1.svg",resources.catalog.drawable(65,1))
        assertEquals("svg/65_0.svg",resources.catalog.drawable(65,0))
    }
    @Test fun coloredDefaultPalettesKeepGoldAccentAndWhiteText() {
        val palettes=UiAppearanceResources(AppearanceCatalog.build())
        assertEquals(0xff241c36.toInt(),palettes.color(1,APP_THEME_PURPLE).toArgb())
        assertEquals(0xff17212b.toInt(),palettes.color(1,APP_THEME_BLUE).toArgb())
        for(theme in listOf(APP_THEME_PURPLE,APP_THEME_BLUE)) {
            assertEquals(0xffffffff.toInt(),palettes.color(2,theme).toArgb())
            assertEquals(0xffffba24.toInt(),palettes.color(0,theme).toArgb())
        }
    }
}

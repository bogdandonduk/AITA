package kz.aita

import kotlin.test.*

class AppearanceCatalogTest {
    private val strings = listOf(LocalizedStringGroupDataModel(1, listOf(
        LocalizedStringDataModel("en", "Receipt"), LocalizedStringDataModel("ru", "Чек"), LocalizedStringDataModel("kk", "Түбіртек"))))
    private fun catalog() = AppearanceCatalog.build(strings = strings,
        dimensions = listOf(StylizedDimensionGroupDataModel(99, listOf(StylizedDimensionDataModel(0,16f),StylizedDimensionDataModel(1,20f)))),
        colors = listOf(StylizedColorGroupDataModel(1,listOf(StylizedColorDataModel(0,"#ffffffff"),StylizedColorDataModel(1,"#ff111111")))),
        drawables = listOf(StylizedDrawablePathsGroupDataModel(7,listOf(StylizedDrawablePathsDataModel(0,"svg/7_0.svg"),StylizedDrawablePathsDataModel(1,"svg/7_1.svg")))))
    @Test fun allLanguagesAreReadyWithoutAnotherProjection() {
        val value=catalog()
        assertEquals("Чек",value.string(1,"ru")); assertEquals("Receipt",value.string(1,"en")); assertEquals("Түбіртек",value.string(1,"kk"))
        assertEquals("Чек",value.string(1,"ru"))
    }
    @Test fun themeAndScaleCanBeReadAtomicallyFromOneChoice() {
        val value=catalog(); val choice=UserPreferencesDataModel("kk",1,1)
        assertEquals("Түбіртек",value.string(1,choice.appLanguage))
        assertEquals("#ff111111",value.color(1,choice.appThemeId))
        assertEquals(20f,value.dimension(99,choice.appSizeModeId,0f))
        assertEquals("svg/7_1.svg",value.drawable(7,choice.appThemeId))
    }
    @Test fun resourceReloadDoesNotCaptureAnOldThemeOrLanguage() {
        val old=catalog(); val new=catalog()
        repeat(20) { index -> val theme=(index%2).toLong(); assertEquals(old.color(1,theme),new.color(1,theme)) }
        assertEquals("Түбіртек",new.string(1,"kk")); assertEquals("svg/7_1.svg",new.drawable(7,1))
    }
    @Test fun missingPrimaryStringUsesBundledEntry() {
        assertEquals("Чек",AppearanceCatalog.build(bundledStrings=strings).string(1,"ru"))
        assertNull(catalog().string(999,"ru"))
    }
    @Test fun firstDuplicateWinsLikeTheOriginalExtractor() {
        assertEquals("Чек",AppearanceCatalog.build(strings=strings+LocalizedStringGroupDataModel(1,listOf(LocalizedStringDataModel("ru","wrong")))).string(1,"ru"))
    }
    @Test fun unknownDrawableRetainsNumberedThemeFallback() {
        assertEquals("svg/136_1.svg",catalog().drawable(136,1))
    }
    @Test fun unknownDimensionsUseCallerFallbackAndModesNormalize() {
        assertEquals(37f,catalog().dimension(999,0,37f))
        assertEquals(catalog().color(1,normalizeAppThemePreference(-9)),catalog().color(1,-9))
    }
}

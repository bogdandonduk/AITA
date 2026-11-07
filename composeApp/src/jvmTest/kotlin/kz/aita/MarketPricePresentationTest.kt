package kz.aita

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.io.File
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicReference
import javax.swing.SwingUtilities
import kotlin.test.*

class MarketPricePresentationTest {
    private val now = 1_793_960_000_000L
    private val item = MarketPriceInsightRow("33333333-3333-4333-8333-333333333333",
        listOf(LocalizedStringDataModel("en", "Alpine tea"), LocalizedStringDataModel("ru", "Чай с молоком и ароматом горных трав")),
        listOf("04006381333931"), MarketShoppingBasis("04006381333931", "KZT", "1", 1.0),
        ownPriceMinor = 15_000, referenceKind = "shelf", compatibleGroups = 5,
        statistics = MarketPriceStatistics(5, 9000, 9500, 10000, 11000, 12000))
    private val page = MarketPriceInsightsPage("11111111-1111-4111-8111-111111111111", "22222222-2222-4222-8222-222222222222",
        MarketPriceRequest(), listOf(item), totalItems = 1, checkedAtMillis = now)

    @Test fun printableReportPreservesAmountsSampleAndExplicitPlace() {
        val report = marketPriceReport(page, null, false, "ru", "Наш филиал")
        val plain = report.buildAnalyticsReportPlainText()
        assertEquals("Наш филиал", report.storeName)
        assertEquals("Казахстан · Все города этой страны", report.scopeText)
        assertTrue(plain.contains("150.00 KZT")); assertTrue(plain.contains("100.00 KZT"))
        assertTrue(plain.contains("50% · Выше медианы"))
        assertTrue(plain.contains("04006381333931 · 1 кг · KZT"))
        assertTrue(plain.contains("1 / 1")); assertFalse(plain.contains("1.0 1"))
        assertTrue(plain.contains("Сравниваются только"))
        assertTrue(report.buildAnalyticsReportPdfDocument().keepSectionsTogether)
    }

    @Test fun offlineAndLimitedSampleWarningsTravelWithExportedReport() {
        val limited = page.copy(rows = listOf(item.copy(sampleLimited = true)))
        val before = page.copy(checkedAtMillis = now - 3_600_000)
        val text = marketPriceReport(limited, before, true, "en", "Branch").buildAnalyticsReportPlainText()
        assertTrue(text.contains("Saved report")); assertTrue(text.contains("Limited sample"))
        assertTrue(text.contains(receiptUiDateTime(before.checkedAtMillis)))
        assertTrue(text.contains("participating shops may differ"))
    }

    @Test fun unavailablePricesRemainUnknownInEveryLocale() {
        val unknown = item.copy(ownPriceMinor = null, ownCurrencyCode = null, basis = null, statistics = null, compatibleGroups = 0, referenceKind = "unpriced")
        for (language in listOf("en", "ru", "kk", "ky", "tg", "uz")) {
            val report = marketPriceReport(page.copy(rows = listOf(unknown)), null, false, language, "Store")
            assertTrue(report.buildAnalyticsReportPlainText().contains("—"), language)
            assertFalse(report.buildAnalyticsReportPlainText().contains("100.00 KZT"), language)
            assertEquals(marketPriceText("title", language), report.title)
        }
    }

    @Test fun quantityNamesUseRealUnitsInsteadOfInternalIdentifiers() {
        assertEquals("1 кг", marketPriceQuantityLabel(item, "ru"))
        assertEquals("1000 г", marketPriceQuantityLabel(item.copy(basis = item.basis!!.copy(unitId = "g", pricedAmount = 1000.0)), "ru"))
        assertEquals("1 box", marketPriceQuantityLabel(item.copy(unitName = listOf(LocalizedStringDataModel("en", "box"))), "en"))
        assertEquals("1 dona", marketPriceQuantityLabel(item.copy(basis = item.basis!!.copy(unitId = "0")), "uz"))
    }

    @Test fun historicalAlertAmountsShowDirectionWithoutLosingMinorCurrencyUnits() {
        val change = MarketPriceChange(item.goodsItemId, item.name, item.basis!!, 10001, 11002)
        assertEquals("+10.01%", marketPriceChangePercent(change))
        assertEquals("-5.01%", marketPriceChangePercent(change.copy(currentMinor = 9500)))
    }

    private fun render(width: Int, language: String, scale: Float, light: Boolean = false, alert: Boolean = false) {
        val failure = AtomicReference<Throwable?>()
        SwingUtilities.invokeAndWait {
            val field = AppConfiguration::class.java.getDeclaredField("stateValues")
            val previous = field.get(AppConfiguration)
            try {
                val base = MarketBrowseControlsRenderTest().presentation(language, width)
                AppConfiguration.stateValues = Proxy.newProxyInstance(AppConfiguration.StateValues::class.java.classLoader,
                    arrayOf(AppConfiguration.StateValues::class.java)) { _, method, args ->
                    when (method.name.substringBefore('-')) {
                        "getDrawablePathIconExpandLess" -> "svg/8_0.svg"
                        "getDrawablePathIconExpandMore" -> "svg/7_0.svg"
                        "getBackgroundColor" -> (if (light) Color.White else Color(0xFF17181B)).value.toLong()
                        "getTextColor" -> (if (light) Color(0xFF181818) else Color.White).value.toLong()
                        "getPlaceholderTextColor" -> (if (light) Color(0xFF666666) else Color(0xFFBABCC1)).value.toLong()
                        else -> method.invoke(base, *(args ?: emptyArray()))
                    }
                } as AppConfiguration.StateValues
                var bounds: Rect? = null
                val scene = ImageComposeScene(width, 1800, Density(1f, scale)) {
                    Column(Modifier.fillMaxWidth().background(if (light) Color(0xFFF4F5F7) else Color(0xFF17181B)).padding(12.dp)
                        .onGloballyPositioned { bounds = it.boundsInRoot() }) {
                        if (alert) AppConfiguration.MarketPriceAlertChanges(MarketPriceAlertDetails(page.accountId, page.storeId,
                            "kz", "Astana", now, List(3) { MarketPriceChange(item.goodsItemId, item.name, item.basis!!, 10001, 11002) }))
                        else AppConfiguration.MarketPriceInsightCard(item, page, page.copy(checkedAtMillis = now - 86_400_000), true, {}, {})
                    }
                }
                try {
                    repeat(24) { scene.render((it + 1) * 16_000_000L).close() }
                    val layout = assertNotNull(bounds)
                    assertTrue(layout.width <= width && layout.left >= 0 && layout.height in 200f..1500f, "Report card must fit: $layout")
                    System.getenv("AITA_PRICE_RENDER_DIR")?.let { directory ->
                        File(directory).mkdirs()
                        scene.render(416_000_000L).use { image -> image.encodeToData()?.use { png ->
                            File(directory, "price-${if (alert) "alert" else "card"}-$width-$language-$scale-$light.png").writeBytes(png.bytes)
                        } }
                    }
                } finally { scene.close() }
            } catch (error: Throwable) { failure.set(error) }
            finally { field.set(AppConfiguration, previous) }
        }
        failure.get()?.let { throw it }
    }
    @Test fun narrowRussianWithLargeText() = render(320, "ru", 1.4f)
    @Test fun narrowKazakhLightTheme() = render(390, "kk", 1.2f, true)
    @Test fun wideEnglish() = render(900, "en", 1f)
    @Test fun narrowRussianAlertDetailsWithLargeText() = render(320, "ru", 1.4f, alert = true)
    @Test fun lightKazakhAlertDetails() = render(390, "kk", 1.2f, light = true, alert = true)
}

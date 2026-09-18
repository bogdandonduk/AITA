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
import androidx.compose.ui.unit.sp
import java.io.File
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicReference
import javax.swing.SwingUtilities
import kotlin.test.*

/** Render actual controls with presentation-only values: no app startup, preferences, session
 * restoration or network services. Screenshots are optional local verification artifacts.
 */
class MarketBrowseControlsRenderTest {
    private fun presentation(language: String, width: Int): AppConfiguration.StateValues = Proxy.newProxyInstance(
        AppConfiguration.StateValues::class.java.classLoader, arrayOf(AppConfiguration.StateValues::class.java)
    ) { _, method, _ ->
        val name = method.name.substringBefore('-')
        when {
            name == "getAppLanguage" || name == "getAppLanguagePreference" -> language
            name == "getIsNarrowScreen" -> width < 700
            name == "getUserAccount" || name == "localizedString" -> null
            name == "getGlobalAppConfiguration" -> globalAppConfigurationState.payloadValue
            name.contains("Color") -> when {
                name.contains("Background") -> Color(0xFF17181B).value.toLong()
                name.contains("AccentText") -> Color.Black.value.toLong()
                name.contains("Accent") -> Color(0xFFE9C86E).value.toLong()
                name.contains("Placeholder") -> Color(0xFFBABCC1).value.toLong()
                else -> Color.White.value.toLong()
            }
            name.contains("TextSize", ignoreCase = true) -> {
                val size = if (name.contains("Small")) 14.sp else 16.sp
                size.javaClass.getMethod("unbox-impl").invoke(size)
            }
            method.returnType == String::class.java -> if (name.contains("drawablePath", true)) "" else name
            method.returnType == java.lang.Float.TYPE -> when {
                name.contains("BorderWidth") -> 1f
                name.contains("TextFieldHeight") -> 52f
                else -> 12f
            }
            method.returnType == java.lang.Long.TYPE -> 0L
            method.returnType == java.lang.Integer.TYPE -> 0
            method.returnType == java.lang.Boolean.TYPE -> false
            List::class.java.isAssignableFrom(method.returnType) -> emptyList<Any>()
            else -> error("Unexpected presentation dependency: ${method.name}")
        }
    } as AppConfiguration.StateValues

    private fun verify(width: Int, language: String, fontScale: Float, shopping: Boolean = false) {
        val failure = AtomicReference<Throwable?>()
        SwingUtilities.invokeAndWait {
            val stateField = AppConfiguration::class.java.getDeclaredField("stateValues")
            val previous = stateField.get(AppConfiguration)
            try {
                AppConfiguration.stateValues = presentation(language, width)
                val browse = BuyerBrowseNavigation(false)
                var bounds: Rect? = null
                val scene = ImageComposeScene(width, 1100, Density(1f, fontScale)) {
                    Column(Modifier.fillMaxSize().background(Color(0xFF17181B)).padding(12.dp)) {
                        Box(Modifier.fillMaxWidth().onGloballyPositioned { bounds = it.boundsInRoot() }) {
                            if (shopping) AppConfiguration.MarketShoppingListControls(MarketShoppingListView(),
                                MarketShoppingSnapshot("render-buyer", lines = listOf(MarketShoppingQuotedLine(
                                    MarketShoppingLine("milk", "shop", "Milk", "Shop", 1, MarketShoppingBasis(null, "KZT", "piece", 1.0))))))
                            else AppConfiguration.MarketBrowseControls(browse, null, {})
                        }
                    }
                }
                var frame = 0L
                fun render(label: String) {
                    repeat(24) { scene.render(++frame * 16_000_000L).close() }
                    System.getenv("AITA_MARKET_RENDER_DIR")?.let { directory ->
                        scene.render(++frame * 16_000_000L).use { image ->
                            image.encodeToData()?.use { png ->
                                File(directory).mkdirs()
                                File(directory, "market-controls-$width-$language-$shopping-$label.png").writeBytes(png.bytes)
                            }
                        }
                    }
                }
                try {
                    render("collapsed")
                    val collapsed = assertNotNull(bounds)
                    assertTrue(collapsed.height < (if (shopping) 410f else 260f), "Search should leave room for products: $collapsed")
                    browse.filtersExpanded = true
                    render("expanded")
                    val expanded = assertNotNull(bounds)
                    if (!shopping) assertTrue(expanded.height > collapsed.height + 70f)
                    assertTrue(expanded.left >= 0f && expanded.right <= width)
                    assertTrue(expanded.bottom < 1100f)
                } finally { scene.close() }
            } catch (error: Throwable) { failure.set(error) }
            finally { stateField.set(AppConfiguration, previous) }
        }
        failure.get()?.let { throw it }
    }

    @Test fun narrowSearchLeavesSpaceForOffers() = verify(390, "en", 1f)
    @Test fun narrowTranslatedFiltersFitWithLargeText() = verify(320, "ru", 1.4f)
    @Test fun wideSearchAndOptionalFiltersStayWithinTheirContainer() = verify(1120, "kk", 1f)
    @Test fun shoppingReviewControlsFitNarrowEnglish() = verify(390, "en", 1f, shopping = true)
    @Test fun shoppingReviewControlsFitLargeRussianText() = verify(390, "ru", 1.4f, shopping = true)
    @Test fun shoppingReviewControlsFitWideKazakh() = verify(1024, "kk", 1f, shopping = true)
}

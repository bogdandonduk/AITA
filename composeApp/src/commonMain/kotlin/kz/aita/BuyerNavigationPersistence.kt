package kz.aita

import androidx.compose.runtime.*
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

private const val BUYER_NAVIGATION_KEY = "buyer_navigation_place"

@Serializable internal data class SavedBuyerBrowse(
    val point: BuyerReturnPoint, val back: BuyerReturnPoint? = null,
    val section: String = "products", val filters: Boolean = false,
    val directoryText: String = "", val directoryCity: String = "",
    val directoryRequest: MarketShopDirectoryRequest = MarketShopDirectoryRequest(),
    val directoryIndex: Int = 0, val directoryOffset: Int = 0
)
@Serializable internal data class SavedBuyerNavigation(
    val market: SavedBuyerBrowse, val saved: SavedBuyerBrowse,
    val shoppingSection: String = "list", val shoppingSearch: String = "", val attention: Boolean = false,
    val shoppingStore: String? = null, val shoppingCurrency: String? = null, val lastSaved: Boolean = false
)

private fun BuyerBrowseNavigation.savedPlace() = SavedBuyerBrowse(point(), returnPoint.value, section.value,
    filtersExpanded, directory.text, directory.city, directory.request,
    directory.restorePosition?.first ?: directory.scroll.firstVisibleItemIndex,
    directory.restorePosition?.second ?: directory.scroll.firstVisibleItemScrollOffset)

private fun BuyerBrowseNavigation.restorePlace(saved: SavedBuyerBrowse) {
    val p = saved.point
    val q = p.query.normalizedDiscoveryQuery() ?: return
    search.value = p.search.take(120); city.value = p.city.take(100)
    appliedSearch.value = q.text; appliedCity.value = q.city
    shopId.value = q.storefrontId; categoryId.value = q.categoryId; sort.value = q.sort
    limit.value = (p.limit.coerceIn(MARKET_DISCOVERY_PAGE_SIZE, MARKET_DISCOVERY_MAX_OFFERS) / MARKET_DISCOVERY_PAGE_SIZE) * MARKET_DISCOVERY_PAGE_SIZE
    section.value = saved.section.takeIf { it in setOf("products", "shops") } ?: "products"
    filtersExpanded = saved.filters
    returnPoint.value = saved.back?.takeIf { it.query.normalizedDiscoveryQuery() != null }
    scrollRestore = p.copy(query = query, index = p.index.coerceIn(0, MARKET_DISCOVERY_MAX_OFFERS), offset = p.offset.coerceIn(0, 10000))
    directory.text = saved.directoryText.take(120); directory.city = saved.directoryCity.take(100)
    directory.request = saved.directoryRequest.copy(savedOnly = savedOnly).normalizedShopDirectoryRequest() ?: MarketShopDirectoryRequest(savedOnly = savedOnly)
    directory.restorePosition = saved.directoryIndex.coerceIn(0, MARKET_SHOPS_MAX_WINDOW) to saved.directoryOffset.coerceIn(0, 10000)
}

internal fun BuyerMarketNavigation.savePlace(): String = jsonBase.encodeToString(SavedBuyerNavigation(
    market.savedPlace(), saved.savedPlace(), shoppingList.section, shoppingList.search, shoppingList.attentionOnly,
    shoppingList.shop?.first, shoppingList.shop?.second, lastSavedOnly))

internal fun BuyerMarketNavigation.restorePlace(raw: String?) {
    val value = raw?.takeIf { it.length <= 32768 }?.let { runCatching { jsonBase.decodeFromString<SavedBuyerNavigation>(it) }.getOrNull() } ?: return
    market.restorePlace(value.market); saved.restorePlace(value.saved)
    shoppingList.section = value.shoppingSection.take(40)
    shoppingList.search = value.shoppingSearch.take(120); shoppingList.attentionOnly = value.attention
    shoppingList.shop = value.shoppingStore?.let(::marketDiscoveryId)?.let { store ->
        value.shoppingCurrency?.takeIf { it.length == 3 && it.all(Char::isLetter) }?.let { store to it }
    }
    restoreLastSaved(value.lastSaved)
}

/** Only presentation is restored. Offers, quotes, permission/read fences and commands are fetched anew. */
@Composable internal fun AppConfiguration.BuyerNavigationPersistence(navigation: BuyerMarketNavigation) {
    val revision by AppStateWorkspace.restoreRevision.collectAsState()
    val account = stateValues.userAccount?.id
    val mode = stateValues.appModeId
    LaunchedEffect(navigation, revision, account, mode) {
        if (account == null || mode != APP_MODE_BUYER) return@LaunchedEffect
        val owner = captureMarketRequestScope() ?: return@LaunchedEffect
        val host = NavigationScreenModel.Buyer.Main.Home
        navigation.restorePlace(host.state.value[BUYER_NAVIGATION_KEY])
        snapshotFlow { navigation.savePlace() }.collect { raw ->
            if (owner.isCurrent() && AppStateWorkspace.readyForCurrentScope()) host.setState(BUYER_NAVIGATION_KEY to raw)
        }
    }
}

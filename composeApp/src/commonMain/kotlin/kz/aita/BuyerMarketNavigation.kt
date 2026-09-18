package kz.aita

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.*

internal data class BuyerReturnPoint(
    val query: MarketDiscoveryQuery, val search: String, val city: String,
    val limit: Int, val index: Int, val offset: Int
)

/** Session-local presentation only. No offer, price, read fence or pending command is retained.
 * Screens recreate their read owners and revalidate prices whenever the buyer returns.
 */
@Stable
internal class BuyerBrowseNavigation(val savedOnly: Boolean) {
    val search = mutableStateOf("")
    val city = mutableStateOf("")
    val appliedSearch = mutableStateOf("")
    val appliedCity = mutableStateOf("")
    val categoryId = mutableStateOf<String?>(null)
    val sort = mutableStateOf(MARKET_DISCOVERY_RECENT)
    val limit = mutableStateOf(MARKET_DISCOVERY_PAGE_SIZE)
    val shopId = mutableStateOf<String?>(null)
    val returnPoint = mutableStateOf<BuyerReturnPoint?>(null)
    var scrollRestore by mutableStateOf<BuyerReturnPoint?>(null)
    val section = mutableStateOf("products")
    var filtersExpanded by mutableStateOf(false)
    val grid = LazyGridState()
    var gridQuery: MarketDiscoveryQuery? = null
    val directory = MarketShopDirectoryNavigation(savedOnly)

    val query: MarketDiscoveryQuery get() {
        val raw = MarketDiscoveryQuery(appliedSearch.value, if (shopId.value == null) appliedCity.value else "",
            shopId.value, categoryId.value, savedOnly && shopId.value == null, sort.value)
        return raw.normalizedDiscoveryQuery() ?: raw
    }
    val filterCount: Int get() = (if (shopId.value == null && city.value.isNotBlank()) 1 else 0) +
        (if (categoryId.value != null) 1 else 0) + (if (sort.value != MARKET_DISCOVERY_RECENT) 1 else 0)

    fun point(): BuyerReturnPoint {
        val restoring = scrollRestore?.takeIf { it.query == query }
        return BuyerReturnPoint(query, search.value, city.value, limit.value,
            restoring?.index ?: grid.firstVisibleItemIndex, restoring?.offset ?: grid.firstVisibleItemScrollOffset)
    }

    fun visitShop(id: String): Boolean {
        val normalized = marketDiscoveryId(id) ?: return false
        if (shopId.value == normalized) return true
        if (shopId.value == null) returnPoint.value = point()
        gridQuery = null; scrollRestore = null
        shopId.value = normalized
        search.value = ""; appliedSearch.value = ""; categoryId.value = null
        sort.value = MARKET_DISCOVERY_RECENT; limit.value = MARKET_DISCOVERY_PAGE_SIZE
        return true
    }

    fun leaveShop(): BuyerReturnPoint? {
        val point = returnPoint.value
        shopId.value = null; returnPoint.value = null
        search.value = point?.search.orEmpty(); appliedSearch.value = point?.query?.text.orEmpty()
        city.value = point?.city ?: city.value; appliedCity.value = point?.query?.city ?: city.value
        categoryId.value = point?.query?.categoryId; sort.value = point?.query?.sort ?: MARKET_DISCOVERY_RECENT
        limit.value = point?.limit ?: MARKET_DISCOVERY_PAGE_SIZE
        scrollRestore = point
        return point
    }

    fun clearFilters() {
        scrollRestore = null
        search.value = ""; appliedSearch.value = ""; city.value = ""; appliedCity.value = ""
        categoryId.value = null; sort.value = MARKET_DISCOVERY_RECENT; limit.value = MARKET_DISCOVERY_PAGE_SIZE
    }
}

/** Owned above the route switch, but below the authenticated account/session identity. A new
 * login (even for the same account) starts a new workspace; nothing enters persisted routes.
 */
@Stable
internal class BuyerMarketNavigation(private val owner: MarketAccountScope?) {
    val market = BuyerBrowseNavigation(false)
    val saved = BuyerBrowseNavigation(true)
    val shoppingList = MarketShoppingListView()
    var lastSavedOnly = false
        private set
    fun browse(savedOnly: Boolean) = if (savedOnly) saved else market
    fun entered(savedOnly: Boolean) { if (owner?.isCurrent() == true) lastSavedOnly = savedOnly }
    fun continueDestination(): NavigationScreenModel.Buyer.Main? = if (owner?.isCurrent() != true) null
        else if (lastSavedOnly) NavigationScreenModel.Buyer.Main.Saved else NavigationScreenModel.Buyer.Main.Home
    fun visitShop(id: String): Boolean {
        if (owner?.isCurrent() != true || !market.visitShop(id)) return false
        lastSavedOnly = false
        return true
    }
}

/** Preserve the intended position while the returning screen shows a shorter loading/failed
 * window. That temporary layout must not overwrite the buyer's place in the actual results.
 */
@Composable
internal fun rememberBuyerBrowseGrid(browse: BuyerBrowseNavigation, resultsReady: Boolean): LazyGridState {
    val query = browse.query
    val grid = browse.grid
    DisposableEffect(browse) {
        onDispose {
            if (browse.gridQuery == browse.query) browse.scrollRestore = browse.point()
        }
    }
    LaunchedEffect(query) {
        if (browse.scrollRestore?.query != query) {
            browse.scrollRestore = null
            grid.scrollToItem(0)
        }
        browse.gridQuery = query
    }
    LaunchedEffect(query, resultsReady, browse.scrollRestore) {
        val point = browse.scrollRestore
        if (resultsReady && point != null && point.query == query) {
            grid.scrollToItem(point.index, point.offset)
            browse.scrollRestore = null
        }
    }
    return grid
}

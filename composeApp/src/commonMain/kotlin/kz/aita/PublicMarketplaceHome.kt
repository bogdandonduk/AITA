package kz.aita

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Guest navigation never changes the remembered account mode, workplace or authenticated stacks. */
@Composable
internal fun AppConfiguration.PublicMarketplaceHome(navigation: BuyerBrowseNavigation, onLogin: () -> Unit, onSignup: () -> Unit, onDownloads: () -> Unit) {
    var search by navigation.search
    var category by navigation.categoryId
    var shop by navigation.shopId
    var limit by navigation.limit
    var revision by remember { mutableIntStateOf(0) }
    var catalogue by remember { mutableStateOf<MarketCategoryCatalogue?>(null) }
    var choosingCategory by remember { mutableStateOf(false) }
    var data by remember { mutableStateOf<MarketDiscoverySnapshot?>(null) }
    var loading by remember { mutableStateOf(true) }
    var saved by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var opened by remember { mutableStateOf<MarketOffer?>(null) }
    val query = MarketDiscoveryQuery(text = search.trim(), categoryId = category, storefrontId = shop)
    val request = MarketDiscoveryRequest(query, limit)
    val grid = rememberBuyerBrowseGrid(navigation, resultsReady = data != null)
    SideEffect { navigation.appliedSearch.value = search }
    val scope = rememberCoroutineScope()
    val home = search.isBlank() && category == null && shop == null
    LaunchedEffect(request, revision) {
        loading = true; failure = null; opened = null
        if (data?.result?.query != query.normalizedDiscoveryQuery()) data = null
        try {
            if (home && data == null && limit == MARKET_DISCOVERY_PAGE_SIZE) cachedPublicMarketplace()?.let {
                data = it; catalogue = it.catalogue; saved = true
            }
            delay(if (search.isBlank()) 0 else 350)
            val response = loadPublicMarketplace(request, catalogue)
            val payload = response.payload
            if (!response.negative && payload != null) {
                data = payload; catalogue = payload.catalogue; saved = false
            } else {
                saved = data != null
                failure = response.message?.visibleLocalizedString(stateValues.appLanguage, "")
                    ?.takeIf { it.isNotBlank() } ?: eventMessage("market.refresh_failed").visibleLocalizedString(stateValues.appLanguage, "")
            }
        } catch (c: CancellationException) { throw c }
        catch (_: Exception) { saved = data != null; failure = eventMessage("market.refresh_failed").visibleLocalizedString(stateValues.appLanguage, "") }
        finally { loading = false }
    }
    fun reset() { shop = null; navigation.returnPoint.value = null; navigation.clearFilters(); scope.launch { grid.scrollToItem(0) } }
    fun visitShop(value: MarketStorefront) { navigation.visitShop(value.storeId) }
    val rows = data?.result?.page?.offers.orEmpty()
    AitaScreenColumn(Modifier.fillMaxSize(), maximumContentWidth = 1440.dp,
        horizontalAlignment = Alignment.CenterHorizontally, appBar = {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!home) ScreenBackButton(onBack = { navigation.goBack() })
                Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = ::reset).padding(4.dp)) {
                    Text("AITA", color = stateValues.TextColor, fontSize = stateValues.titleTextSize, fontWeight = FontWeight.Black)
                    Text("MARKET", color = stateValues.AccentColor, fontSize = stateValues.smallTextSize, fontWeight = FontWeight.Bold)
                }
                if (!stateValues.isNarrowScreen) Box(Modifier.width(240.dp)) { AuthPreferencesChooser() }
                Box(Modifier.widthIn(max = 156.dp)) { actionButton(text = pass29Text("front.signin"), autoLoading = false,
                    confirmationRequired = false, onClick = onLogin) }
                if (!stateValues.isNarrowScreen) Box(Modifier.widthIn(max = 210.dp)) { actionButton(text = pass29Text("front.signup"),
                    enabledColor = stateValues.BackgroundColor, textColor = stateValues.TextColor,
                    autoLoading = false, confirmationRequired = false, onClick = onSignup) }
            }
        }) {
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = stateValues.AccentColor)
        LazyVerticalGrid(columns = GridCells.Adaptive(if (stateValues.isNarrowScreen) 160.dp else 224.dp), state = grid,
            modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            item("search", span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (stateValues.isNarrowScreen) AuthPreferencesChooser()
                    aitaFormTextField(Modifier.fillMaxWidth(), search, { search = it.take(120); limit = MARKET_DISCOVERY_PAGE_SIZE },
                        pass29Text("front.search"), identityKey = "public-market-search", autoFocus = false, parentOwnsValue = true)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.weight(1f)) { actionButton(text = catalogue?.categories?.firstOrNull { it.id == category }?.name
                            ?.visibleLocalizedString(stateValues.appLanguage, "") ?: pass29Text("front.catalogue"),
                            iconPath = marketIconPath(18), iconRes = marketIconFallback(18), autoLoading = false,
                            confirmationRequired = false, onClick = { choosingCategory = true }) }

                    }
                    if (failure != null || saved) {
                        Text(if (saved) pass29Text("front.offline") else failure.orEmpty(), color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
                        actionButton(text = pass29Text("pass29.retry"), loading = loading, autoLoading = false, confirmationRequired = false,
                            onClick = { if (!loading) revision++ })
                    }
                }
            }
            if (home) {
                item("hero", span = { GridItemSpan(maxLineSpan) }) {
                    PublicMarketHero(onCatalogue = { choosingCategory = true }, onSignup = onSignup)
                }
                item("categories", span = { GridItemSpan(maxLineSpan) }) {
                    MarketHomeCatalogue(catalogue) { category = it; limit = MARKET_DISCOVERY_PAGE_SIZE }
                }
                if (rows.any { marketPromotionPercent(it) != null }) item("promotions", span = { GridItemSpan(maxLineSpan) }) {
                    MarketHomePromotions(rows) { id -> opened = rows.firstOrNull { it.id == id } }
                }
                if (rows.isNotEmpty()) item("collections", span = { GridItemSpan(maxLineSpan) }) {
                    MarketHomeCollections(rows, onOffer = { id -> opened = rows.firstOrNull { it.id == id } }, onShop = ::visitShop)
                }
            } else data?.result?.storefront?.let { storefront ->
                item("shop", span = { GridItemSpan(maxLineSpan) }) { MarketShopIdentity(storefront) }
            }
            item("products-title", span = { GridItemSpan(maxLineSpan) }) {
                Text(if (home) pass29Text("front.picks") else catalogue?.categories?.firstOrNull { it.id == category }?.name
                    ?.visibleLocalizedString(stateValues.appLanguage, "") ?: pass24Text("catalogue_short"),
                    color = stateValues.TextColor, fontSize = stateValues.titleTextSize, fontWeight = FontWeight.Bold)
            }
            items(rows, key = { it.id }) { offer -> PublicMarketProductCard(offer) { opened = offer } }
            if (!loading && failure == null && rows.isEmpty()) item("empty", span = { GridItemSpan(maxLineSpan) }) {
                Text(pass29Text("front.empty"), color = stateValues.PlaceholderTextColor, fontSize = stateValues.textSize, modifier = Modifier.padding(vertical = 24.dp))
            }
            if (data?.result?.page?.nextId != null && limit < 120) item("more", span = { GridItemSpan(maxLineSpan) }) {
                actionButton(text = pass29Text("front.more"), autoLoading = false, confirmationRequired = false,
                    loading = loading, onClick = { if (!loading) limit = (limit + MARKET_DISCOVERY_PAGE_SIZE).coerceAtMost(120) })
            }
            item("footer", span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(pass29Text("front.login_note"), color = stateValues.TextColor, fontSize = stateValues.accentTextSize, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.weight(1f)) { actionButton(text = pass29Text("front.signin"), autoLoading = false, confirmationRequired = false, onClick = onLogin) }
                        Box(Modifier.weight(1f)) { actionButton(text = pass29Text("front.signup"), autoLoading = false, confirmationRequired = false, onClick = onSignup) }
                    }
                    actionButton(text = downloadsText("title"), iconPath = downloadsIconPath(), iconRes = downloadsIconResource(),
                        enabledColor = stateValues.BackgroundColor, textColor = stateValues.TextColor,
                        autoLoading = false, confirmationRequired = false, onClick = onDownloads)
                    Text("AITA · " + pass29Text("front.shop_note"), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                }
            }
        }
    }
    if (choosingCategory) catalogue?.let { MarketCategoryPickerDialog(it, category,
        onDismiss = { choosingCategory = false }, onSelected = { category = it; limit = MARKET_DISCOVERY_PAGE_SIZE; choosingCategory = false }) }
        ?: AitaBottomSheet(title = pass29Text("front.catalogue"), onDismiss = { choosingCategory = false }) {
            Text(failure ?: pass29Text("front.empty"), color = stateValues.TextColor, fontSize = stateValues.textSize)
            actionButton(text = pass29Text("pass29.retry"), autoLoading = false, confirmationRequired = false,
                onClick = { choosingCategory = false; revision++ })
        }
    opened?.let { offer ->
        AitaBottomSheet(title = offer.title, onDismiss = { opened = null }) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                MarketProductPhoto(offer.product.imageUrls.firstOrNull(), offer.title, photoHeight = 260.dp)
                Text(marketPriceLabel(offer), color = stateValues.TextColor, fontSize = stateValues.titleTextSize, fontWeight = FontWeight.Bold)
                Text(offer.description, color = stateValues.TextColor, fontSize = stateValues.textSize)
                MarketShopIdentity(offer.storefront)
                Text(offer.storefront.publicAddress, color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                Text(if (saved) pass29Text("front.offline") else marketBrowseText("market.availability_note"),
                    color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
            }
            Text(pass29Text("front.login_note"), color = stateValues.TextColor, fontSize = stateValues.smallTextSize, modifier = Modifier.padding(vertical = 12.dp))
            actionButton(text = pass29Text("front.signin"), autoLoading = false, confirmationRequired = false, onClick = { opened = null; onLogin() })
        }
    }
}

@Composable
private fun AppConfiguration.PublicMarketHero(onCatalogue: () -> Unit, onSignup: () -> Unit) {
    val shape = RoundedCornerShape(stateValues.cornerRadius * 1.5f)
    Column(Modifier.fillMaxWidth().clip(shape)
        .background(Brush.linearGradient(listOf(stateValues.AccentColor.copy(alpha = .22f), stateValues.AccentColor.copy(alpha = .04f))))
        .border(1.dp, stateValues.AccentColor.copy(alpha = .3f), shape)
        .padding(if (stateValues.isNarrowScreen) 24.dp else 40.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("AITA MARKET", color = stateValues.AccentColor, fontSize = stateValues.smallTextSize, fontWeight = FontWeight.Bold)
                Text(pass29Text("front.title"), color = stateValues.TextColor,
                    fontSize = stateValues.titleTextSize * if (stateValues.isNarrowScreen) 1.35f else 1.9f,
                    fontWeight = FontWeight.Black)
                Text(pass29Text("front.note"), color = stateValues.TextColor.copy(alpha = .8f), fontSize = stateValues.textSize)
            }
            if (!stateValues.isNarrowScreen) MarketBagIllustration(Modifier.size(190.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) { actionButton(text = pass29Text("front.catalogue"), iconPath = marketIconPath(18),
                iconRes = marketIconFallback(18), autoLoading = false, confirmationRequired = false, onClick = onCatalogue) }
            Box(Modifier.weight(1f)) { actionButton(text = pass29Text("front.signup"), enabledColor = stateValues.BackgroundColor,
                textColor = stateValues.TextColor, autoLoading = false, confirmationRequired = false, onClick = onSignup) }
        }
    }
}

@Composable
private fun AppConfiguration.PublicMarketProductCard(offer: MarketOffer, onOpen: () -> Unit) {
    val shape = RoundedCornerShape(stateValues.cornerRadius)
    Column(Modifier.fillMaxWidth().clip(shape).background(stateValues.BackgroundColor)
        .border(1.dp, stateValues.TextColor.copy(alpha = .12f), shape).clickable(role = Role.Button, onClick = onOpen).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MarketProductPhoto(offer.product.imageUrls.firstOrNull(), offer.title, photoHeight = if (stateValues.isNarrowScreen) 132.dp else 180.dp)
        Text(offer.title, color = stateValues.TextColor, fontSize = stateValues.textSize, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(marketPriceLabel(offer), color = stateValues.TextColor, fontSize = stateValues.accentTextSize, fontWeight = FontWeight.Bold)
        Text(offer.storefront.displayName + " · " + offer.storefront.city, color = stateValues.PlaceholderTextColor,
            fontSize = stateValues.smallTextSize, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

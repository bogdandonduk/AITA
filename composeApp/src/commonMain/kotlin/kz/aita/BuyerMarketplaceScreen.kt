package kz.aita

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal fun AppConfiguration.marketPriceLabel(offer: MarketOffer): String {
    val minor = offer.priceMinor ?: return authUiText("Confirm price", "Уточните цену", "Бағасын нақтылаңыз")
    val amount = offer.pricedAmount?.toString()?.removeSuffix(".0").orEmpty()
    val unit = offer.unitName.visibleLocalizedString(stateValues.appLanguage, authUiText("unit", "ед.", "бірл."))
    return "${marketMoneyLabel(minor, offer.currencyCode.orEmpty())} / $amount $unit"
}

private data class BuyerBrowseQuery(val search: String, val city: String, val shopId: String?,
    val savedOnly: Boolean)

@Stable
private class BuyerBrowseData {
    var page by mutableStateOf<MarketPage?>(null)
    var shop by mutableStateOf<MarketStorefront?>(null)
    var loading by mutableStateOf(true)
    var gone by mutableStateOf(false)
    var failure by mutableStateOf<List<LocalizedStringDataModel>?>(null)
}

@Composable
internal fun AppConfiguration.BuyerMarketplaceScreen() {
    val account = stateValues.userAccount?.id
    val generation = currentAuthenticatedSessionGeneration()
    val savedOnly = stateValues.navigationScreensMain.last() == NavigationScreenModel.Buyer.Main.Saved
    val owner = remember(account, generation) { captureMarketRequestScope() }
    val revision by MarketplaceSignals.revision.collectAsState()
    val shopping = rememberMarketShoppingUiState()
    val scope = rememberCoroutineScope()
    val home = NavigationScreenModel.Buyer.Main.Home
    var search by rememberSaveable(account) { mutableStateOf("") }
    var city by rememberSaveable(account) { mutableStateOf("") }
    var shopId by remember(account, savedOnly) { mutableStateOf(if (savedOnly) null else home.state.value["market-shop:$account"]?.takeIf { it.isNotBlank() }) }
    var compareTo by remember(account, generation, savedOnly) { mutableStateOf<MarketComparisonSelection?>(null) }
    var openedId by remember(account, generation, savedOnly) { mutableStateOf<String?>(null) }
    var savingId by remember(account, generation) { mutableStateOf<String?>(null) }
    var saveFailure by remember(account, generation) { mutableStateOf<List<LocalizedStringDataModel>?>(null) }
    val fence = remember(account, generation) { MarketSavedReadFence() }
    val dialogOpen = openedId != null || compareTo != null
    val latestDialogOpen by rememberUpdatedState(dialogOpen)
    val query = BuyerBrowseQuery(search.trim(), if (shopId == null) city.trim() else "", shopId, savedOnly && shopId == null)
    val data = remember(account, generation, query) { BuyerBrowseData() }
    var wantedPages by remember(account, generation, query) { mutableStateOf(1) }
    val latestWantedPages by rememberUpdatedState(wantedPages)
    val requests = remember(account, generation, query) { Channel<Unit>(Channel.CONFLATED) }
    val gridState = rememberLazyGridState()
    fun visitShop(shop: MarketStorefront) {
        openedId = null; compareTo = null; shopId = shop.storeId; search = ""
        if (!savedOnly) home.setStateNow("market-shop:$account" to shop.storeId)
    }
    DisposableEffect(requests) { onDispose { requests.close() } }
    LaunchedEffect(requests, revision, wantedPages, dialogOpen) { if (!dialogOpen) requests.trySend(Unit) }
    LaunchedEffect(requests) {
        val owned = owner ?: run { data.loading = false; return@LaunchedEffect }
        for (ignored in requests) {
            delay(220)
            while (requests.tryReceive().isSuccess) { /* coalesce before starting this read */ }
            if (latestDialogOpen) continue // The dialogue owns its own fresh read; reload this window when it closes.
            data.loading = true
            val readRevision = fence.capture()
            try {
                if (query.shopId != null) {
                    val result = loadMarketShop(owned, query.shopId)
                    val shop = result.payload
                    if (result.negative || shop == null) {
                        if (owned.isCurrent()) {
                            data.failure = result.message ?: eventMessage("market.shop_unavailable")
                            if (result.httpStatusCode == 404 || result.httpStatusCode == 403) { data.gone = true; data.page = null; data.shop = null }
                        }
                        continue
                    }
                    if (owned.isCurrent()) { data.shop = shop; data.gone = false }
                }
                val result = if (query.savedOnly) loadMarketSaved(owned) else readMarketPageWindow(latestWantedPages) { after ->
                    loadMarketOffers(owned, query.search, query.city, after, storefrontId = query.shopId)
                }
                if (owned.isCurrent()) {
                    val value = result.payload
                    if (!result.negative && value != null) { data.page = fence.reconcile(value, readRevision, query.savedOnly); data.failure = null }
                    else data.failure = result.message ?: eventMessage("market.refresh_failed")
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (owned.isCurrent()) data.failure = eventMessage("market.refresh_failed") }
            finally { data.loading = false }
        }
    }
    LaunchedEffect(requests) { while (isActive) { delay(30_000); requests.trySend(Unit) } }
    LaunchedEffect(query) { gridState.scrollToItem(0) }

    fun setSaved(offer: MarketOffer) {
        val owned = owner ?: return
        if (savingId != null || !owned.isCurrent()) return
        val desired = !offer.saved
        savingId = offer.id; saveFailure = null
        scope.launch {
            try {
                val result = updateMarketSaved(owned, offer.id, desired)
                if (owned.isCurrent()) {
                    val value = result.payload
                    if (!result.negative && value != null) {
                        fence.acknowledgeSnapshot(value.offers.map { it.id }.toSet(), value.unavailableSavedCount)
                        data.page = if (query.savedOnly) value else data.page?.let { current ->
                            current.copy(offers = current.offers.map { if (it.id == offer.id) it.copy(saved = desired) else it })
                        }
                        MarketplaceSignals.changed()
                    } else saveFailure = result.message ?: eventMessage("market.refresh_failed")
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (owned.isCurrent()) saveFailure = eventMessage("market.refresh_failed") }
            finally { savingId = null }
        }
    }

    val rows = data.page?.offers.orEmpty()
    Column(Modifier.fillMaxSize().aitaWidthCap(1440.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ScreenAppBarWidget(title = when {
            shopId != null -> data.shop?.displayName ?: authUiText("Shop window", "Витрина", "Витрина")
            savedOnly -> authUiText("Saved offers", "Сохранённое", "Сақталғандар")
            else -> "AITA Market"
        }, iconPath = marketIconPath(when { savedOnly && shopId == null -> 140; else -> 139 }))
        LazyVerticalGrid(columns = GridCells.Adaptive(250.dp), state = gridState, modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "browse-header", span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(authUiText("Discover local shops · plan your list · confirm at the shop",
                        "Находите магазины · планируйте список · уточняйте в магазине",
                        "Дүкендерді табыңыз · тізімді жоспарлаңыз · дүкеннен нақтылаңыз"),
                        color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                    if (shopId != null) {
                        data.shop?.let { shop ->
                            Text("${shop.city} · ${shop.publicAddress}", color = stateValues.TextColor, fontSize = stateValues.textSize)
                            if (shop.pickupNote.isNotBlank()) Text(shop.pickupNote, color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                        }
                        actionButton(text = if (savedOnly) authUiText("Back to saved", "К сохранённому", "Сақталғандарға оралу")
                            else authUiText("All shops", "Все магазины", "Барлық дүкендер"), fillMaxWidthIfTextPresent = false,
                            autoLoading = false, confirmationRequired = false, onClick = {
                                shopId = null; search = ""; if (!savedOnly) home.setStateNow("market-shop:$account" to "")
                            })
                    }
                    if (!query.savedOnly) BoxWithConstraints(Modifier.fillMaxWidth()) {
                        if (maxWidth > 720.dp && shopId == null) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            aitaFormTextField(Modifier.weight(2f), search, { search = it.take(120) }, authUiText("Product or barcode", "Товар или штрихкод", "Тауар не штрихкод"),
                                identityKey = "market-search:$account", autoFocus = false)
                            aitaFormTextField(Modifier.weight(1f), city, { city = it.take(100) }, authUiText("City · optional", "Город · необязательно", "Қала · міндетті емес"),
                                identityKey = "market-city:$account", autoFocus = false)
                        } else Column {
                            aitaFormTextField(Modifier.fillMaxWidth(), search, { search = it.take(120) }, authUiText("Product or barcode", "Товар или штрихкод", "Тауар не штрихкод"),
                                identityKey = "market-search:$account", autoFocus = false)
                            if (shopId == null) aitaFormTextField(Modifier.fillMaxWidth(), city, { city = it.take(100) },
                                authUiText("City · optional", "Город · необязательно", "Қала · міндетті емес"), identityKey = "market-city:$account", autoFocus = false)
                        }
                    }
                    if (data.failure != null || saveFailure != null) Text((saveFailure ?: data.failure).orEmpty().visibleLocalizedString(stateValues.appLanguage, ""),
                        color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
                    MarketShoppingFeedback(shopping)
                }
            }
            if (data.page == null && data.loading) items(6) {
                LoadingSkeleton(Modifier.fillMaxWidth().heightIn(min = 240.dp), rows = 4)
            } else if (rows.isEmpty()) item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.fillMaxWidth().padding(vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CpImage(Modifier.size(64.dp), url = marketIconPath(if (query.savedOnly) 140 else 139), fallbackRes = marketIconFallback(if (query.savedOnly) 140 else 139), contentDescription = null, tintColor = stateValues.AccentColor)
                    Text(when {
                        data.gone -> authUiText("This shop is no longer available", "Этот магазин больше недоступен", "Бұл дүкен енді қолжетімсіз")
                        data.page == null -> authUiText("Connect to load shop windows", "Подключитесь, чтобы загрузить витрины", "Витриналарды жүктеу үшін қосылыңыз")
                        else -> authUiText("No matching published offers", "Подходящих опубликованных предложений нет", "Сәйкес жарияланған ұсыныстар жоқ")
                    }, Modifier.padding(16.dp), color = stateValues.TextColor, fontSize = stateValues.accentTextSize)
                    if (query.savedOnly) Text(authUiText("Save offers with the bookmark on a product card.", "Сохраняйте предложения закладкой на карточке товара.", "Тауар карточкасындағы бетбелгі арқылы ұсыныстарды сақтаңыз."),
                        color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                }
            } else items(rows, key = { it.id }) { offer ->
                MarketOfferCard(offer, savingId != null, shopping, onOpen = { openedId = offer.id }, onSaved = { setSaved(offer) },
                    onCompare = { compareTo = offer.comparisonSelection() }, onShop = { visitShop(offer.storefront) })
            }
            if (data.page?.nextId != null) item(key = "more", span = { GridItemSpan(maxLineSpan) }) {
                if (wantedPages < 10) actionButton(text = authUiText("More offers", "Ещё предложения", "Тағы ұсыныстар"), enabled = !data.loading,
                    autoLoading = false, confirmationRequired = false, onClick = { wantedPages++ })
                else Text(authUiText("Showing the first 400 offers. Refine your search to explore more.", "Показаны первые 400 предложений. Уточните поиск, чтобы найти другие.", "Алғашқы 400 ұсыныс көрсетілді. Басқаларын табу үшін іздеуді нақтылаңыз."),
                    color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
            }
            if (query.savedOnly && (data.page?.unavailableSavedCount ?: 0) > 0) item(key = "unavailable-saved", span = { GridItemSpan(maxLineSpan) }) {
                actionButton(text = authUiText("Remove unavailable saved offers (${data.page?.unavailableSavedCount})",
                    "Убрать недоступные предложения (${data.page?.unavailableSavedCount})", "Қолжетімсіз ұсыныстарды жою (${data.page?.unavailableSavedCount})"),
                    enabled = savingId == null, autoLoading = false, confirmationRequired = true, onClick = {
                        val owned = owner
                        if (owned != null && owned.isCurrent() && savingId == null) {
                            savingId = "clear"
                            scope.launch {
                                try {
                                    val result = clearUnavailableMarketSaved(owned)
                                    if (owned.isCurrent()) {
                                        val value = result.payload
                                        if (!result.negative && value != null) {
                                            fence.acknowledgeSnapshot(value.offers.map { it.id }.toSet(), value.unavailableSavedCount)
                                            data.page = value; MarketplaceSignals.changed()
                                        }
                                        else saveFailure = result.message
                                    }
                                } catch (cancelled: CancellationException) { throw cancelled }
                                catch (_: Exception) { if (owned.isCurrent()) saveFailure = eventMessage("market.refresh_failed") }
                                finally { savingId = null }
                            }
                        }
                    })
            }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(if (data.page == null) authUiText("Waiting for offers", "Ожидаем предложения", "Ұсыныстар күтілуде")
                else authUiText("${rows.size} loaded offers", "Предложений загружено: ${rows.size}", "${rows.size} ұсыныс жүктелді"),
                Modifier.weight(1f), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
            actionButton(text = authUiText("Refresh", "Обновить", "Жаңарту"), fillMaxWidthIfTextPresent = false,
                autoLoading = false, enabled = !data.loading, loading = data.loading, confirmationRequired = false, onClick = { requests.trySend(Unit) })
        }
    }
    openedId?.let { id -> MarketOfferDetailDialog(id, shopping, onDismiss = { openedId = null }, onVisitShop = ::visitShop,
        onCompare = { offer -> openedId = null; compareTo = offer.comparisonSelection() }) }
    compareTo?.let { target -> MarketComparisonDialog(target, shopping, onDismiss = { compareTo = null }, onVisitShop = ::visitShop, initialCity = city) }
}

@Composable
private fun AppConfiguration.MarketOfferCard(offer: MarketOffer, saving: Boolean, shopping: MarketShoppingUiState,
    onOpen: () -> Unit, onSaved: () -> Unit, onCompare: () -> Unit, onShop: () -> Unit) {
    val scope = rememberCoroutineScope()
    val inList = shopping.contains(offer.id)
    Column(Modifier.fillMaxWidth().heightIn(min = 310.dp).foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
        .clip(RoundedCornerShape(stateValues.cornerRadius)).background(stateValues.BackgroundColor)
        .border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor.copy(alpha = 0.30f), RoundedCornerShape(stateValues.cornerRadius))
        .clickable(onClick = onOpen).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CpImage(Modifier.size(42.dp), url = marketIconPath(139), fallbackRes = marketIconFallback(139), contentDescription = null, tintColor = stateValues.AccentColor)
            Spacer(Modifier.weight(1f))
            actionButton(text = "", iconPath = marketIconPath(140), iconRes = marketIconFallback(140),
                iconContentDescription = if (offer.saved) authUiText("Unsave", "Не сохранять", "Сақтаудан алып тастау") else authUiText("Save offer", "Сохранить", "Сақтау"),
                enabledColor = if (offer.saved) stateValues.AccentColor else stateValues.BackgroundColor,
                textColor = if (offer.saved) stateValues.AccentTextColor else stateValues.TextColor,
                enabled = !saving, autoLoading = false, confirmationRequired = false, onClick = onSaved)
        }
        Text(offer.title, color = stateValues.TextColor, fontSize = stateValues.accentTextSize, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Text(marketPriceLabel(offer), color = changedValueColor(offer.priceMinor, "market:${offer.id}:${offer.currencyCode}:${stateValues.appLanguage}", stateValues.AccentColor),
            fontSize = stateValues.textSize, fontWeight = FontWeight.Bold)
        Text("${offer.storefront.displayName} · ${offer.storefront.city}", Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onShop).padding(vertical = 6.dp),
            color = stateValues.AccentColor, fontSize = stateValues.smallTextSize, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(if (offer.availability == MARKET_AVAILABILITY_RECORDED) authUiText("Recorded in stock · not reserved", "Есть в учёте · не зарезервировано", "Есепте бар · резервтелмеген")
            else authUiText("Confirm availability", "Уточните наличие", "Бар-жоғын нақтылаңыз"), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
        actionButton(text = if (inList) authUiText("In your list", "В вашем списке", "Сіздің тізіміңізде") else authUiText("Add to list", "В список покупок", "Тізімге қосу"),
            iconPath = marketIconPath(143), iconRes = marketIconFallback(143), autoLoading = false, confirmationRequired = false,
            enabled = inList || (shopping.canChange && offer.shoppingBasis() != null), onClick = {
                if (inList) scope.launch { Navigation.goMain(NavigationScreenModel.Buyer.Main.Shopping) }
                else shopping.change(offer.id, 1, offer.shoppingBasis())
            })
        if (offer.comparisonSelection() != null) actionButton(text = authUiText("Compare", "Сравнить", "Салыстыру"),
            iconPath = marketIconPath(141), iconRes = marketIconFallback(141), autoLoading = false, confirmationRequired = false,
            enabledColor = stateValues.BackgroundColor, textColor = stateValues.TextColor, onClick = onCompare)
    }
}

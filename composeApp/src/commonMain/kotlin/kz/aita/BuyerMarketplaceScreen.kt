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
    val minor = offer.priceMinor ?: return authUiText("Confirm price", "Уточните цену", "Бағасын нақтылаңыз", "Бааны ырастатуу")
    val amount = offer.pricedAmount?.toString()?.removeSuffix(".0").orEmpty()
    val unit = offer.unitName.visibleLocalizedString(stateValues.appLanguage, authUiText("unit", "ед.", "бірл.", "бирдик"))
    return "${marketMoneyLabel(minor, offer.currencyCode.orEmpty())} / $amount $unit"
}

private data class BuyerReturnPoint(val query: MarketDiscoveryQuery, val search: String, val city: String,
    val limit: Int, val index: Int, val offset: Int)

@Stable
private class BuyerBrowseData {
    var active = true
    var page by mutableStateOf<MarketPage?>(null)
    var result by mutableStateOf<MarketDiscoveryResult?>(null)
    var shop by mutableStateOf<MarketStorefront?>(null)
    var loading by mutableStateOf(true)
    var fresh by mutableStateOf(false)
    var countsFresh by mutableStateOf(false)
    var readRevision by mutableStateOf(-1L)
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
    var browseSection by rememberSaveable(account) { mutableStateOf("products") }
    val directory = remember(account, generation) { MarketShopDirectoryNavigation() }
    var search by rememberSaveable(account, savedOnly) { mutableStateOf("") }
    var city by rememberSaveable(account, savedOnly) { mutableStateOf("") }
    var appliedSearch by rememberSaveable(account, savedOnly) { mutableStateOf("") }
    var appliedCity by rememberSaveable(account, savedOnly) { mutableStateOf("") }
    var categoryId by rememberSaveable(account, savedOnly) { mutableStateOf<String?>(null) }
    var sort by rememberSaveable(account, savedOnly) { mutableStateOf(MARKET_DISCOVERY_RECENT) }
    var limit by rememberSaveable(account, savedOnly) { mutableStateOf(MARKET_DISCOVERY_PAGE_SIZE) }
    var shopId by remember(account, savedOnly) { mutableStateOf(if (savedOnly) null else home.state.value["market-shop:$account"]?.takeIf { it.isNotBlank() }) }
    var returnPoint by remember(account, generation, savedOnly) { mutableStateOf<BuyerReturnPoint?>(null) }
    var scrollRestore by remember(account, generation, savedOnly) { mutableStateOf<BuyerReturnPoint?>(null) }
    var catalogue by remember(account, generation) { mutableStateOf<MarketCategoryCatalogue?>(null) }
    val latestCatalogue by rememberUpdatedState(catalogue)
    var choosingCategory by remember(account, generation, savedOnly) { mutableStateOf(false) }
    var compareTo by remember(account, generation, savedOnly) { mutableStateOf<MarketComparisonSelection?>(null) }
    var openedId by remember(account, generation, savedOnly) { mutableStateOf<String?>(null) }
    var savingId by remember(account, generation) { mutableStateOf<String?>(null) }
    var saveFailure by remember(account, generation) { mutableStateOf<List<LocalizedStringDataModel>?>(null) }
    val fence = remember(account, generation) { MarketSavedReadFence() }
    // Category selection uses a local catalogue; unlike product dialogues it does not own I/O.
    val directoryOpen = !savedOnly && shopId == null && browseSection == "shops"
    val dialogOwnsReads = openedId != null || compareTo != null || directoryOpen
    val latestDialogOwnsReads by rememberUpdatedState(dialogOwnsReads)
    val rawQuery = MarketDiscoveryQuery(appliedSearch, if (shopId == null) appliedCity else "", shopId, categoryId, savedOnly && shopId == null, sort)
    val query = rawQuery.normalizedDiscoveryQuery() ?: rawQuery
    val data = remember(account, generation, query) { BuyerBrowseData() }
    val latestLimit by rememberUpdatedState(limit)
    val requests = remember(account, generation, query) { Channel<Unit>(Channel.CONFLATED) }
    val gridState = rememberLazyGridState()
    val inputPending = search != appliedSearch || (shopId == null && city != appliedCity)
    LaunchedEffect(account, savedOnly, search, city, shopId) {
        delay(350)
        if (appliedSearch != search || appliedCity != city) {
            appliedSearch = search; appliedCity = city; limit = MARKET_DISCOVERY_PAGE_SIZE; scrollRestore = null
        }
    }
    fun visitShop(shop: MarketStorefront) {
        if (shopId == null) returnPoint = BuyerReturnPoint(query, appliedSearch, appliedCity, limit,
            gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset)
        openedId = null; compareTo = null; shopId = shop.storeId
        search = ""; appliedSearch = ""; categoryId = null; sort = MARKET_DISCOVERY_RECENT
        limit = MARKET_DISCOVERY_PAGE_SIZE; scrollRestore = null
        if (!savedOnly) home.setStateNow("market-shop:$account" to shop.storeId)
    }
    fun leaveShop() {
        val point = returnPoint
        shopId = null; returnPoint = null
        search = point?.search.orEmpty(); appliedSearch = point?.search.orEmpty()
        city = point?.city ?: city; appliedCity = point?.city ?: city
        categoryId = point?.query?.categoryId; sort = point?.query?.sort ?: MARKET_DISCOVERY_RECENT
        limit = point?.limit ?: MARKET_DISCOVERY_PAGE_SIZE; scrollRestore = point
        if (!savedOnly) home.setStateNow("market-shop:$account" to "")
    }
    fun clearFilters() {
        search = ""; appliedSearch = ""; city = ""; appliedCity = ""; categoryId = null
        limit = MARKET_DISCOVERY_PAGE_SIZE; scrollRestore = null
    }
    DisposableEffect(data, requests) { onDispose { data.active = false; requests.close() } }
    LaunchedEffect(requests, revision, limit, dialogOwnsReads) {
        if (!dialogOwnsReads) { data.fresh = false; data.countsFresh = false; requests.trySend(Unit) }
    }
    LaunchedEffect(requests) {
        val owned = owner ?: run { data.loading = false; return@LaunchedEffect }
        for (ignored in requests) {
            delay(180)
            while (requests.tryReceive().isSuccess) { /* coalesce before starting this read */ }
            if (latestDialogOwnsReads) continue
            data.loading = true; data.fresh = false; data.countsFresh = false
            val readRevision = fence.capture()
            val startedRevision = MarketplaceSignals.revision.value
            try {
                val cached = latestCatalogue
                val request = MarketDiscoveryRequest(query, latestLimit, cached?.version)
                val response = loadMarketDiscovery(owned, request, cached)
                if (data.active && owned.isCurrent()) {
                    val checked = response.payload
                    val latestWanted = MarketDiscoveryRequest(query, latestLimit)
                    val refreshQueued = requests.tryReceive().isSuccess
                    // Reject superseded failures too: an old 404 must not erase a newer scope.
                    val superseded = latestDialogOwnsReads || refreshQueued || request.limit != latestLimit ||
                        startedRevision != MarketplaceSignals.revision.value
                    if (superseded) {
                        requests.trySend(Unit)
                    } else if (!response.negative && checked != null &&
                        checked.result.matchesDiscoveryRead(latestWanted, startedRevision, MarketplaceSignals.revision.value)) {
                        catalogue = checked.catalogue
                        data.result = checked.result; data.shop = checked.result.storefront; data.gone = false
                        data.page = fence.reconcile(checked.result.page, readRevision, query.savedOnly)
                        val savedChangedDuringRead = fence.capture() != readRevision
                        data.failure = null; data.fresh = true; data.readRevision = startedRevision
                        data.countsFresh = !savedChangedDuringRead && savingId == null
                        if (savedChangedDuringRead) requests.trySend(Unit) // a bookmark changed DURING the read
                    } else {
                        data.failure = response.message ?: eventMessage("market.page_changed")
                        if (!response.transportFailure && response.httpStatusCode in setOf(401, 403, 404)) {
                            data.page = null; data.result = null; data.shop = null
                            data.gone = query.storefrontId != null &&
                                response.message?.eventMessageReferenceOrNull()?.key == "market.shop_unavailable"
                        }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (data.active && owned.isCurrent()) data.failure = eventMessage("market.refresh_failed") }
            finally { data.loading = false }
        }
    }
    LaunchedEffect(requests) {
        while (isActive) {
            delay(30_000)
            if (!latestDialogOwnsReads) { data.fresh = false; data.countsFresh = false; requests.trySend(Unit) }
        }
    }
    LaunchedEffect(query) {
        if (scrollRestore?.query != query) { scrollRestore = null; gridState.scrollToItem(0) }
    }
    LaunchedEffect(query, data.page, scrollRestore) {
        val point = scrollRestore
        if (point != null && point.query == query && data.page != null) {
            gridState.scrollToItem(point.index, point.offset); scrollRestore = null
        }
    }

    fun catalogueIsCurrent(currentRevision: Long = MarketplaceSignals.revision.value): Boolean =
        data.active && data.fresh && !data.loading && !inputPending && !latestDialogOwnsReads && owner?.isCurrent() == true &&
            data.result?.matchesDiscoveryRead(MarketDiscoveryRequest(query, limit), data.readRevision, currentRevision) == true

    fun acknowledgeSaved(page: MarketPage) {
        data.page = fence.acknowledgeDiscoverySaved(data.page, page, query.savedOnly)
        data.countsFresh = false
        requests.trySend(Unit) // the owned mutation boundary also invalidates on an uncertain reply
    }
    fun setSaved(offer: MarketOffer) {
        val owned = owner ?: return
        if (savingId != null || !owned.isCurrent() || !catalogueIsCurrent()) return
        val desired = !offer.saved
        savingId = offer.id; saveFailure = null; data.countsFresh = false
        scope.launch {
            try {
                val response = updateMarketSaved(owned, offer.id, desired)
                if (owned.isCurrent()) {
                    val value = response.payload
                    if (!response.negative && value != null) acknowledgeSaved(value)
                    else saveFailure = response.message ?: eventMessage("market.saved_unconfirmed")
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (owned.isCurrent()) saveFailure = eventMessage("market.refresh_failed") }
            finally { savingId = null }
        }
    }

    val catalogueReady = catalogueIsCurrent(revision)
    val rows = data.page?.offers.orEmpty()
    val selectedCategory = catalogue?.categories?.firstOrNull { it.id == categoryId }
    AitaScreenColumn(
        Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
        maximumContentWidth = 1440.dp,
        appBar = {
            ScreenAppBarWidget(title = when {
                shopId != null -> data.shop?.displayName ?: authUiText("Shop window", "Витрина", "Витрина", "Дүкөн витринасы")
                savedOnly -> authUiText("Saved offers", "Сохранённое", "Сақталғандар", "Сакталган сунуштар")
                else -> "AITA Market"
            }, iconPath = marketIconPath(if (savedOnly && shopId == null) 140 else 139))
        }
    ) {
        if (!savedOnly && shopId == null) sectionTabsWidget("buyer-market-sections:$account", listOf(
            TabContent("products", authUiText("Products", "Товары", "Тауарлар", "Товарлар")),
            TabContent("shops", authUiText("Shops", "Магазины", "Дүкендер", "Дүкөндөр"))),
            selectedId = browseSection, onSelected = { browseSection = it })
        if (directoryOpen) MarketShopDirectoryPanel(directory, Modifier.weight(1f).fillMaxWidth(), onVisit = ::visitShop)
        else {
        LazyVerticalGrid(columns = GridCells.Adaptive(250.dp), state = gridState, modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "browse-header", span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (shopId != null) {
                        data.shop?.let { shop ->
                            Text("${shop.city} · ${shop.publicAddress}", color = stateValues.TextColor, fontSize = stateValues.textSize)
                            if (shop.pickupNote.isNotBlank()) Text(shop.pickupNote, color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                        }
                        actionButton(text = if (savedOnly) authUiText("Back to saved", "К сохранённому", "Сақталғандарға оралу", "Сакталгандарга кайтуу")
                            else if (browseSection == "shops") authUiText("Back to shops", "К магазинам", "Дүкендерге оралу", "Дүкөндөргө кайтуу")
                            else authUiText("Back to market", "Вернуться в маркет", "Маркетке оралу", "Маркетке кайтуу"),
                            autoLoading = false, confirmationRequired = false, onClick = ::leaveShop)
                    }
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        if (maxWidth > 720.dp && shopId == null) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            aitaFormTextField(Modifier.weight(2f), search, { search = it.take(120) }, authUiText("Product or barcode", "Товар или штрихкод", "Тауар не штрихкод", "Товар же штрихкод"),
                                identityKey = "market-search:$account:$savedOnly", autoFocus = false, parentOwnsValue = true)
                            aitaFormTextField(Modifier.weight(1f), city, { city = it.take(100) }, authUiText("City · optional", "Город · необязательно", "Қала · міндетті емес", "Шаар · милдеттүү эмес"),
                                identityKey = "market-city:$account:$savedOnly", autoFocus = false, parentOwnsValue = true)
                        } else Column {
                            aitaFormTextField(Modifier.fillMaxWidth(), search, { search = it.take(120) }, authUiText("Product or barcode", "Товар или штрихкод", "Тауар не штрихкод", "Товар же штрихкод"),
                                identityKey = "market-search:$account:$savedOnly", autoFocus = false, parentOwnsValue = true)
                            if (shopId == null) aitaFormTextField(Modifier.fillMaxWidth(), city, { city = it.take(100) },
                                authUiText("City · optional", "Город · необязательно", "Қала · міндетті емес", "Шаар · милдеттүү эмес"), identityKey = "market-city:$account:$savedOnly", autoFocus = false, parentOwnsValue = true)
                        }
                    }
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        actionButton(text = selectedCategory?.name?.visibleLocalizedString(stateValues.appLanguage, "")?.substringAfterLast(" / ")
                            ?: if (categoryId == null) authUiText("All categories", "Все категории", "Барлық санаттар", "Бардык категориялар")
                                else authUiText("Selected category", "Выбранная категория", "Таңдалған санат", "Тандалган категория"),
                            iconPath = marketIconPath(18), iconRes = marketIconFallback(18), autoLoading = false, confirmationRequired = false,
                             enabled = catalogue != null, onClick = { choosingCategory = true })
                        if (categoryId != null) actionButton(text = authUiText("Clear category", "Сбросить категорию", "Санатты алып тастау", "Категорияны тазалоо"),
                            autoLoading = false, confirmationRequired = false,
                            enabledColor = stateValues.BackgroundColor, textColor = stateValues.TextColor,
                            onClick = { categoryId = null; limit = MARKET_DISCOVERY_PAGE_SIZE; scrollRestore = null })
                        if (search.isNotEmpty() || city.isNotEmpty() || categoryId != null) actionButton(text = authUiText("Clear filters", "Сбросить фильтры", "Сүзгілерді тазалау", "Чыпкаларды тазалоо"),
                            autoLoading = false, confirmationRequired = false,
                            enabledColor = stateValues.BackgroundColor, textColor = stateValues.TextColor, onClick = ::clearFilters)
                    }
                    sectionTabsWidget("market-sort:$account:$savedOnly", listOf(
                        TabContent(MARKET_DISCOVERY_RECENT, if (query.savedOnly) authUiText("Recently saved", "Недавно сохранённые", "Жақында сақталған", "Жакында сакталган")
                            else authUiText("Newest listings", "Новые предложения", "Жаңа ұсыныстар", "Эң жаңы жарыялар")),
                        TabContent(MARKET_DISCOVERY_TITLE, authUiText("By name", "По названию", "Атауы бойынша", "Аталышы боюнча"))),
                        selectedId = sort, onSelected = { sort = it; limit = MARKET_DISCOVERY_PAGE_SIZE; scrollRestore = null })
                    Text(authUiText("Categories include their subcategories. Counts are published offers, not available stock or a reservation.",
                        "Категории включают подкатегории. Счётчики показывают опубликованные предложения, не остатки и не резерв.",
                        "Санаттар ішкі санаттарды қамтиды. Санақ жарияланған ұсыныстарды көрсетеді, қор не резерв емес.", "Категориялар ички категорияларын камтыйт. Сандар кампадагы товарды же резервди эмес, жарыяланган сунуштарды көрсөтөт."),
                        color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                    if (data.failure != null || saveFailure != null) Text((saveFailure ?: data.failure).orEmpty().visibleLocalizedString(stateValues.appLanguage, ""),
                        color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
                    MarketShoppingFeedback(shopping)
                }
            }
            if (data.page == null && data.loading) items(6) { LoadingSkeleton(Modifier.fillMaxWidth().heightIn(min = 240.dp), layout = LoadingLayout.MarketplaceCard, rows = 1) }
            else if (rows.isEmpty()) item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.fillMaxWidth().padding(vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CpImage(Modifier.size(64.dp), url = marketIconPath(if (query.savedOnly) 140 else 139), fallbackRes = marketIconFallback(if (query.savedOnly) 140 else 139), contentDescription = null, tintColor = stateValues.AccentColor)
                    Text(when {
                        data.gone -> authUiText("This shop is no longer available", "Этот магазин больше недоступен", "Бұл дүкен енді қолжетімсіз", "Бул дүкөн эми жеткиликсиз")
                        data.page == null && data.failure != null -> authUiText("Could not load these results", "Не удалось загрузить результаты", "Бұл нәтижелерді жүктеу мүмкін болмады", "Бул натыйжаларды жүктөө мүмкүн болгон жок")
                        data.page == null -> authUiText("Connect to load shop windows", "Подключитесь, чтобы загрузить витрины", "Витриналарды жүктеу үшін қосылыңыз", "Дүкөн витриналарын жүктөө үчүн туташыңыз")
                        else -> authUiText("No published offers match these filters", "Опубликованных предложений по этим фильтрам нет", "Бұл сүзгілерге сәйкес жарияланған ұсыныстар жоқ", "Бул чыпкаларга дал келген жарыяланган сунуштар жок")
                    }, Modifier.padding(16.dp), color = stateValues.TextColor, fontSize = stateValues.accentTextSize)
                    if (query.savedOnly) Text(authUiText("Save offers with the bookmark on a product card. Filtering does not remove bookmarks.",
                        "Сохраняйте предложения закладкой на карточке товара. Фильтры не удаляют закладки.", "Тауар карточкасындағы бетбелгі арқылы сақтаңыз. Сүзгілер бетбелгілерді жоймайды.", "Сунуштарды товар карточкасындагы кыстарма менен сактаңыз. Чыпкалоо кыстармаларды алып салбайт."),
                        color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                }
            }
            items(rows, key = { it.id }) { offer ->
                MarketOfferCard(offer, savingId != null || !catalogueReady, shopping, canUseEstimate = catalogueReady,
                    estimateIsCurrent = { catalogueIsCurrent() }, onOpen = { openedId = offer.id }, onSaved = { setSaved(offer) },
                    onCompare = { if (catalogueIsCurrent()) compareTo = offer.comparisonSelection() }, onShop = { visitShop(offer.storefront) })
            }
            if (data.page?.nextId != null) item(key = "more", span = { GridItemSpan(maxLineSpan) }) {
                if (limit < MARKET_DISCOVERY_MAX_OFFERS) actionButton(text = authUiText("More offers", "Ещё предложения", "Тағы ұсыныстар", "Дагы сунуштар"), enabled = catalogueReady,
                    autoLoading = false, confirmationRequired = false, onClick = { limit = (limit + MARKET_DISCOVERY_PAGE_SIZE).coerceAtMost(MARKET_DISCOVERY_MAX_OFFERS) })
                else Text(authUiText("Showing the first 400 matches. Refine the category, city or search to explore more.",
                    "Показаны первые 400 совпадений. Уточните категорию, город или поиск, чтобы найти другие.", "Алғашқы 400 сәйкестік көрсетілді. Басқаларын табу үшін санатты, қаланы не іздеуді нақтылаңыз.", "Алгачкы 400 дал келүү көрсөтүлдү. Көбүрөөк көрүү үчүн категорияны, шаарды же издөөнү тактаңыз."),
                    color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
            }
            if (query.savedOnly && (data.page?.unavailableSavedCount ?: 0) > 0) item(key = "unavailable-saved", span = { GridItemSpan(maxLineSpan) }) {
                Text(authUiText("Unavailable across all saved offers; items merely hidden by these filters are not included.",
                    "Недоступные среди всех сохранённых: предложения, скрытые только фильтрами, сюда не входят.",
                    "Барлық сақталғандар арасындағы қолжетімсіздер: тек сүзгі жасырған ұсыныстар бұл санға кірмейді.", "Бардык сакталган сунуштардын ичинен жеткиликсиз болгондору; ушул чыпкалар гана жашырган товарлар кошулган жок."),
                    color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                actionButton(text = authUiText("Remove unavailable saved offers (${data.page?.unavailableSavedCount})",
                    "Убрать недоступные предложения (${data.page?.unavailableSavedCount})", "Қолжетімсіз ұсыныстарды жою (${data.page?.unavailableSavedCount})", "Жеткиликсиз сакталган сунуштарды алып салуу (${data.page?.unavailableSavedCount})"),
                    enabled = savingId == null && catalogueReady && data.countsFresh, autoLoading = false, confirmationRequired = true, onClick = {
                        val owned = owner
                        if (owned != null && owned.isCurrent() && savingId == null && data.countsFresh && catalogueIsCurrent()) {
                            savingId = "clear"; saveFailure = null; data.countsFresh = false
                            scope.launch {
                                try {
                                    val response = clearUnavailableMarketSaved(owned)
                                    if (owned.isCurrent()) {
                                        val value = response.payload
                                        if (!response.negative && value != null) acknowledgeSaved(value) else saveFailure = response.message ?: eventMessage("market.refresh_failed")
                                    }
                                } catch (cancelled: CancellationException) { throw cancelled }
                                catch (_: Exception) { if (owned.isCurrent()) saveFailure = eventMessage("market.refresh_failed") }
                                finally { savingId = null }
                            }
                        }
                    })
            }
        }
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.Start) {
            val counts = data.result
            Column(Modifier.fillMaxWidth()) {
                Text(when {
                    inputPending -> authUiText("Searching…", "Поиск…", "Іздеу…", "Изделүүдө…")
                    data.loading -> authUiText("Updating matches…", "Обновляем результаты…", "Нәтижелер жаңартылуда…", "Дал келүүлөр жаңыртылууда…")
                    catalogueReady && data.countsFresh && counts != null -> authUiText("${rows.size} of ${counts.totalOffers} offers · ${counts.totalShops} shops",
                        "${rows.size} из ${counts.totalOffers} предложений · магазинов: ${counts.totalShops}", "${counts.totalOffers} ұсыныстың ${rows.size} ұсынысы · ${counts.totalShops} дүкен", "${counts.totalOffers} сунуштун ${rows.size} сунушу · ${counts.totalShops} дүкөн")
                    data.page != null -> authUiText("${rows.size} shown · refresh needed", "Показано: ${rows.size} · обновите", "${rows.size} көрсетілді · жаңартыңыз", "${rows.size} көрсөтүлдү · жаңыртуу керек")
                    else -> authUiText("Waiting for offers", "Ожидаем предложения", "Ұсыныстар күтілуде", "Сунуштарды күтүүдө")
                }, color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                data.page?.checkedAtMillis?.let { Text(receiptUiDateTime(it), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize) }
            }
            actionButton(text = authUiText("Refresh", "Обновить", "Жаңарту", "Жаңыртуу"),
                autoLoading = false, enabled = !data.loading && !inputPending, loading = data.loading, confirmationRequired = false, onClick = {
                    data.fresh = false; data.countsFresh = false; requests.trySend(Unit)
                })
        }
        }
    }
    if (choosingCategory) catalogue?.let { loaded -> MarketCategoryPickerDialog(loaded, categoryId, onDismiss = { choosingCategory = false },
        onSelected = { id -> categoryId = id; choosingCategory = false; limit = MARKET_DISCOVERY_PAGE_SIZE; scrollRestore = null }) }
    openedId?.let { id -> MarketOfferDetailDialog(id, shopping, onDismiss = { openedId = null }, onVisitShop = ::visitShop,
        onCompare = { offer -> openedId = null; compareTo = offer.comparisonSelection() }) }
    compareTo?.let { target -> MarketComparisonDialog(target, shopping, onDismiss = { compareTo = null }, onVisitShop = ::visitShop, initialCity = appliedCity) }
}

@Composable
private fun AppConfiguration.MarketOfferCard(offer: MarketOffer, saving: Boolean, shopping: MarketShoppingUiState, canUseEstimate: Boolean,
    estimateIsCurrent: () -> Boolean, onOpen: () -> Unit, onSaved: () -> Unit, onCompare: () -> Unit, onShop: () -> Unit) {
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
                iconContentDescription = if (offer.saved) authUiText("Unsave", "Не сохранять", "Сақтаудан алып тастау", "Сакталгандардан алып салуу") else authUiText("Save offer", "Сохранить", "Сақтау", "Сунушту сактоо"),
                enabledColor = if (offer.saved) stateValues.AccentColor else stateValues.BackgroundColor,
                textColor = if (offer.saved) stateValues.AccentTextColor else stateValues.TextColor,
                enabled = !saving, autoLoading = false, confirmationRequired = false, onClick = onSaved)
        }
        MarketProductPhoto(offer.product.imageUrls.firstOrNull(), offer.title)
        Text(offer.title, color = stateValues.TextColor, fontSize = stateValues.accentTextSize, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Text(marketPriceLabel(offer), color = changedValueColor(offer.priceMinor, "market:${offer.id}:${offer.currencyCode}:${stateValues.appLanguage}", stateValues.AccentColor),
            fontSize = stateValues.textSize, fontWeight = FontWeight.Bold)
        Text("${offer.storefront.displayName} · ${offer.storefront.city}", Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onShop).padding(vertical = 6.dp),
            color = stateValues.AccentColor, fontSize = stateValues.smallTextSize, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(if (offer.availability == MARKET_AVAILABILITY_RECORDED) authUiText("Recorded in stock · not reserved", "Есть в учёте · не зарезервировано", "Есепте бар · резервтелмеген", "Кампада бар деп катталган · резервге коюлган эмес")
            else authUiText("Confirm availability", "Уточните наличие", "Бар-жоғын нақтылаңыз", "Бар экенин ырастатуу"), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
        actionButton(text = if (inList) authUiText("In your list", "В вашем списке", "Сіздің тізіміңізде", "Тизмеңизде") else authUiText("Add to list", "В список покупок", "Тізімге қосу", "Тизмеге кошуу"),
            iconPath = marketIconPath(143), iconRes = marketIconFallback(143), autoLoading = false, confirmationRequired = false,
            enabled = inList || (canUseEstimate && shopping.canChange && offer.shoppingBasis() != null), onClick = {
                if (inList) scope.launch { Navigation.goMain(NavigationScreenModel.Buyer.Main.Shopping) }
                else if (estimateIsCurrent()) shopping.add(offer.id, 1, offer.shoppingBasis())
            })
        if (offer.comparisonSelection() != null) actionButton(text = authUiText("Compare", "Сравнить", "Салыстыру", "Салыштыруу"),
            iconPath = marketIconPath(141), iconRes = marketIconFallback(141), enabled = canUseEstimate, autoLoading = false, confirmationRequired = false,
            enabledColor = stateValues.BackgroundColor, textColor = stateValues.TextColor, onClick = onCompare)
    }
}

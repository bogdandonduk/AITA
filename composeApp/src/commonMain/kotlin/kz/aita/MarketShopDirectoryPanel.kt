package kz.aita

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Kept above Products/Shops and shop-detail branches. Public rows stay in memory only, scoped
 * to this account/session, not in Android saved state or an unbounded navigation payload.
 */
@Stable
internal class MarketShopDirectoryNavigation {
    var text by mutableStateOf("")
    var city by mutableStateOf("")
    var request by mutableStateOf(MarketShopDirectoryRequest())
    var result by mutableStateOf<MarketShopDirectoryResult?>(null)
    val scroll = LazyGridState()
}

private class ShopDirectoryRead {
    var active = true
    var loading by mutableStateOf(true)
    var error by mutableStateOf<List<LocalizedStringDataModel>?>(null)
    val fence = MarketShopDirectoryReadFence()
    var loadedStamp by mutableStateOf<MarketShopDirectoryReadFence.Stamp?>(null)
    var receivedAt: TimeMark? = null

    fun invalidate() { fence.invalidate(); loadedStamp = null }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppConfiguration.MarketShopDirectoryPanel(
    navigation: MarketShopDirectoryNavigation,
    modifier: Modifier = Modifier,
    onVisit: (MarketStorefront) -> Unit
) {
    val account = stateValues.userAccount?.id
    val generation = currentAuthenticatedSessionGeneration()
    val owner = remember(account, generation) { captureMarketRequestScope() }
    val wanted = navigation.request
    val data = remember(account, generation, wanted) { ShopDirectoryRead() }
    val requests = remember(data) { Channel<Unit>(Channel.CONFLATED) }
    val signal by MarketplaceSignals.revision.collectAsState()
    val latestVisit by rememberUpdatedState(onVisit)
    val lastRead = remember(account, generation) { mutableStateOf<TimeMark?>(null) }
    fun inputMatchesRequest(): Boolean = navigation.text == navigation.request.text && navigation.city == navigation.request.city
    val inputPending = !inputMatchesRequest()
    fun queueRefresh() {
        if (!data.active || owner?.isCurrent() != true) return
        data.invalidate() // Immediately retires displayed actions, including during the cooldown.
        requests.trySend(Unit)
    }
    fun setSearch(text: String = navigation.text, city: String = navigation.city) {
        if (!data.active || owner?.isCurrent() != true) return
        if (navigation.text != text || navigation.city != city) data.invalidate()
        navigation.text = text; navigation.city = city
    }
    fun viewBlocked(): Boolean = !data.active || owner?.isCurrent() != true || data.loading ||
        data.error != null || !inputMatchesRequest() || navigation.request != wanted ||
        navigation.result?.accountId != owner.accountId
    fun resultIsCurrent(revision: Long = MarketplaceSignals.revision.value): Boolean = data.fence.canUse(
        data.loadedStamp, navigation.result, navigation.request, revision,
        data.receivedAt?.elapsedNow()?.inWholeMilliseconds ?: -1L, viewBlocked())
    fun visitDisplayed(entry: MarketShopDirectoryEntry) {
        if (data.fence.canVisit(data.loadedStamp, navigation.result, entry, navigation.request,
                MarketplaceSignals.revision.value, data.receivedAt?.elapsedNow()?.inWholeMilliseconds ?: -1L, viewBlocked()))
            latestVisit(entry.storefront)
        else queueRefresh()
    }
    fun expandWindow() {
        val current = navigation.result ?: return
        if (!resultIsCurrent() || current.totalShops <= current.shops.size || navigation.request.limit >= MARKET_SHOPS_MAX_WINDOW) return
        data.invalidate()
        navigation.request = navigation.request.copy(limit = navigation.request.limit + MARKET_SHOPS_PAGE_SIZE)
    }
    DisposableEffect(data) { onDispose { data.active = false; data.invalidate(); requests.close() } }
    LaunchedEffect(navigation.text, navigation.city) {
        delay(350)
        val query = MarketShopDirectoryRequest(navigation.text, navigation.city)
        if (query.text != navigation.request.text || query.city != navigation.request.city) {
            navigation.request = query
            navigation.scroll.scrollToItem(0)
        } else if (data.loadedStamp == null) {
            // An A -> B -> A edit can finish before debounce commits B. It still retired A's
            // old response and needs a read now, not a wait for the periodic refresh.
            queueRefresh()
        }
    }
    LaunchedEffect(data, signal) { queueRefresh() }
    LaunchedEffect(data) {
        val owned = owner ?: run { data.loading = false; data.error = eventMessage("market.shopping_denied"); return@LaunchedEffect }
        if (wanted.normalizedShopDirectoryRequest() == null) {
            data.loading = false; data.error = eventMessage("market.shops_invalid"); return@LaunchedEffect
        }
        for (ignored in requests) {
            delay(180)
            val wait = lastRead.value?.let { (2_000 - it.elapsedNow().inWholeMilliseconds).coerceAtLeast(0) } ?: 0
            if (wait > 0) delay(wait)
            while (requests.tryReceive().isSuccess) { /* I/O arrivals retain one trailing read. */ }
            if (!data.active || !owned.isCurrent()) break
            if (navigation.request != wanted || !inputMatchesRequest()) continue
            val stamp = data.fence.capture(wanted, MarketplaceSignals.revision.value)
            lastRead.value = TimeSource.Monotonic.markNow()
            data.loading = true
            try {
                val response = loadMarketShopDirectory(owned, wanted)
                if (!data.active || !owned.isCurrent()) break
                if (!inputMatchesRequest() || !data.fence.isCurrent(stamp, navigation.request, MarketplaceSignals.revision.value)) {
                    // Never publish a superseded success OR error, including a late 401/403.
                    queueRefresh()
                } else {
                    val result = response.payload
                    if (!response.negative && result != null) {
                        navigation.result = result; data.loadedStamp = stamp
                        data.receivedAt = TimeSource.Monotonic.markNow(); data.error = null
                    } else {
                        data.loadedStamp = null; data.error = response.message ?: eventMessage("market.shops_failed")
                        if (!response.transportFailure && response.httpStatusCode in setOf(401, 403)) navigation.result = null
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (data.active && owned.isCurrent()) {
                    if (!inputMatchesRequest() || !data.fence.isCurrent(stamp, navigation.request, MarketplaceSignals.revision.value)) queueRefresh()
                    else { data.loadedStamp = null; data.error = eventMessage("market.shops_failed") }
                }
            } finally { if (data.active) data.loading = false }
        }
    }
    LaunchedEffect(data) { while (isActive) { delay(MARKET_SHOP_DIRECTORY_FRESH_MILLIS); queueRefresh() } }
    val result = navigation.result?.takeIf { it.accountId == account && it.request == wanted.normalizedShopDirectoryRequest() }
    val fresh = resultIsCurrent(signal)
    LazyVerticalGrid(columns = GridCells.Adaptive(300.dp), modifier = modifier, state = navigation.scroll,
        contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "shop-search", span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(authUiText("Find a shop, then explore its window", "Найдите магазин и откройте его витрину", "Дүкенді тауып, витринасын ашыңыз", "Дүкөндү таап, андан кийин анын витринасын караңыз"),
                    color = stateValues.TextColor, fontSize = stateValues.accentTextSize, fontWeight = FontWeight.Bold)
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth > 720.dp) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        aitaFormTextField(Modifier.weight(2f), navigation.text, { setSearch(text = it.take(120)) },
                            authUiText("Shop name or pickup address", "Магазин или адрес выдачи", "Дүкен атауы немесе алу мекенжайы", "Дүкөндүн аталышы же алып кетүү дареги"),
                            identityKey = "shop-directory-name:$account", autoFocus = false, parentOwnsValue = true)
                        aitaFormTextField(Modifier.weight(1f), navigation.city, { setSearch(city = it.take(100)) },
                            authUiText("City · optional", "Город · необязательно", "Қала · міндетті емес", "Шаар · милдеттүү эмес"),
                            identityKey = "shop-directory-city:$account", autoFocus = false, parentOwnsValue = true)
                    } else Column {
                        aitaFormTextField(Modifier.fillMaxWidth(), navigation.text, { setSearch(text = it.take(120)) },
                            authUiText("Shop name or pickup address", "Магазин или адрес выдачи", "Дүкен атауы немесе алу мекенжайы", "Дүкөндүн аталышы же алып кетүү дареги"),
                            identityKey = "shop-directory-name:$account", autoFocus = false, parentOwnsValue = true)
                        aitaFormTextField(Modifier.fillMaxWidth(), navigation.city, { setSearch(city = it.take(100)) },
                            authUiText("City · optional", "Город · необязательно", "Қала · міндетті емес", "Шаар · милдеттүү эмес"),
                            identityKey = "shop-directory-city:$account", autoFocus = false, parentOwnsValue = true)
                    }
                }
                Text(authUiText("Each card is a published physical shop. Counts are published offers, not stock, opening hours or reservations. For product/category search, use Products.",
                    "Каждая карточка — опубликованный физический магазин. Числа показывают предложения, не остатки, часы работы или резерв. Товары и категории ищите во вкладке «Товары».",
                    "Әр карточка — жарияланған нақты дүкен. Сандар ұсыныстарды көрсетеді, қор, жұмыс уақыты не резерв емес. Тауар мен санатты «Тауарлар» қойындысынан іздеңіз.", "Ар бир карточка — жарыяланган чыныгы дүкөн. Сандар товар калдыгын, иш убактысын же резервди эмес, жарыяланган сунуштарды көрсөтөт. Товар же категория издөө үчүн «Товарлар» бөлүмүн колдонуңуз."),
                    color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                if (navigation.text.isNotEmpty() || navigation.city.isNotEmpty()) actionButton(
                    text = authUiText("Clear filters", "Сбросить фильтры", "Сүзгілерді тазалау", "Чыпкаларды тазалоо"),
                    autoLoading = false, confirmationRequired = false, onClick = { setSearch(text = "", city = "") })
                data.error?.let { Text(it.visibleLocalizedString(stateValues.appLanguage, ""), color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize) }
            }
        }
        if (result == null && data.loading) items(4) { LoadingSkeleton(layout = LoadingLayout.ShopCard, modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp), rows = 1) }
        else if (result?.shops.isNullOrEmpty()) item(key = "shops-empty", span = { GridItemSpan(maxLineSpan) }) {
            Text(if (data.error != null || result == null) authUiText("Connect to load published shops", "Подключитесь для загрузки магазинов", "Жарияланған дүкендерді жүктеу үшін қосылыңыз", "Жарыяланган дүкөндөрдү жүктөө үчүн туташыңыз")
                else authUiText("No published shops match yet. Try another city or clear the filters.", "Опубликованных магазинов пока нет. Попробуйте другой город или сбросьте фильтры.", "Сәйкес жарияланған дүкендер әзірше жоқ. Басқа қаланы көріңіз немесе сүзгілерді тазалаңыз.", "Дал келген жарыяланган дүкөндөр азырынча жок. Башка шаарды тандаңыз же чыпкаларды тазалаңыз."),
                Modifier.padding(vertical = 24.dp), color = stateValues.TextColor, fontSize = stateValues.textSize)
        }
        items(result?.shops.orEmpty(), key = { it.storefront.storeId }) { entry ->
            val shop = entry.storefront
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(stateValues.cornerRadius)).background(stateValues.BackgroundColor)
                .border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor.copy(alpha = 0.25f), RoundedCornerShape(stateValues.cornerRadius))
                .padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CpImage(Modifier.size(36.dp), url = marketIconPath(139), fallbackRes = marketIconFallback(139),
                        contentDescription = null, tintColor = stateValues.AccentColor)
                    Text(shop.displayName, Modifier.weight(1f), color = stateValues.TextColor, fontSize = stateValues.accentTextSize,
                        fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text(shop.city, color = stateValues.AccentColor, fontSize = stateValues.textSize)
                SelectionContainer { Text(shop.publicAddress, color = stateValues.TextColor, fontSize = stateValues.smallTextSize) }
                if (shop.pickupNote.isNotBlank()) Text(shop.pickupNote, color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(if (!fresh) eventMessage("market.shops_previous_count", "count" to entry.publishedOffers.toString())
                    .visibleLocalizedString(stateValues.appLanguage, "")
                    else if (entry.publishedOffers == 0L) authUiText("No published offers yet", "Пока нет опубликованных предложений", "Жарияланған ұсыныстар әзірше жоқ", "Жарыяланган сунуштар азырынча жок")
                    else authUiText("${entry.publishedOffers} published offers", "Опубликованных предложений: ${entry.publishedOffers}", "${entry.publishedOffers} жарияланған ұсыныс", "${entry.publishedOffers} жарыяланган сунуш"),
                    color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                actionButton(text = authUiText("View shop", "Открыть магазин", "Дүкенді ашу", "Дүкөндү көрүү"), enabled = fresh,
                    autoLoading = false, confirmationRequired = false, onClick = { visitDisplayed(entry) })
            }
        }
        item(key = "shops-footer", span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(when {
                    inputPending -> authUiText("Applying search…", "Применяем поиск…", "Іздеу қолданылуда…", "Издөө колдонулууда…")
                    data.loading -> authUiText("Updating shops…", "Обновляем магазины…", "Дүкендер жаңартылуда…", "Дүкөндөр жаңыртылууда…")
                    fresh && result != null -> authUiText("${result.shops.size} of ${result.totalShops} shops · by name", "${result.shops.size} из ${result.totalShops} магазинов · по названию", "${result.totalShops} дүкеннің ${result.shops.size} дүкені · атауы бойынша", "${result.totalShops} дүкөндүн ${result.shops.size} дүкөнү · аталышы боюнча")
                    else -> if (result != null) eventMessage("market.shops_stale").visibleLocalizedString(stateValues.appLanguage, "")
                        else authUiText("Shops · refresh needed", "Магазины · требуется обновление", "Дүкендер · жаңарту қажет", "Дүкөндөр · жаңыртуу керек")
                }, color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                result?.let { Text(receiptUiDateTime(it.checkedAtMillis), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize) }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    actionButton(text = authUiText("Refresh", "Обновить", "Жаңарту", "Жаңыртуу"), enabled = !data.loading && !inputPending,
                        loading = data.loading, autoLoading = false, confirmationRequired = false,
                        onClick = { queueRefresh() })
                    if (result != null && result.totalShops > result.shops.size && wanted.limit < MARKET_SHOPS_MAX_WINDOW)
                        actionButton(text = authUiText("More shops", "Ещё магазины", "Тағы дүкендер", "Дагы дүкөндөр"), enabled = fresh,
                            autoLoading = false, confirmationRequired = false,
                            onClick = { expandWindow() })
                }
                if (result != null && result.totalShops > MARKET_SHOPS_MAX_WINDOW && wanted.limit == MARKET_SHOPS_MAX_WINDOW)
                    Text(authUiText("First 200 matches. Refine the city or shop name to explore more.", "Первые 200 совпадений. Уточните город или название магазина для поиска других.", "Алғашқы 200 сәйкестік. Басқаларын көру үшін қаланы не дүкен атауын нақтылаңыз.", "Алгачкы 200 дал келүү. Көбүрөөк көрүү үчүн шаарды же дүкөндүн аталышын тактаңыз."),
                        color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
            }
        }
    }
}

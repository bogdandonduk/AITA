package kz.aita

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Details own a fresh, authorized read, not a pointer into whichever grid page is currently loaded. */
@Composable
internal fun AppConfiguration.MarketOfferDetailDialog(
    offerId: String,
    shopping: MarketShoppingUiState,
    onDismiss: () -> Unit,
    onVisitShop: (MarketStorefront) -> Unit,
    onCompare: ((MarketOffer) -> Unit)? = null
) {
    val account = stateValues.userAccount?.id
    val generation = currentAuthenticatedSessionGeneration()
    val owner = remember(account, generation, offerId) { captureMarketRequestScope() }
    var offer by remember(account, generation, offerId) { mutableStateOf<MarketOffer?>(null) }
    var branches by remember(account, generation, offerId) { mutableStateOf<List<MarketBranchAvailability>>(emptyList()) }
    var branchesTruncated by remember(account, generation, offerId) { mutableStateOf(false) }
    var loading by remember(account, generation, offerId) { mutableStateOf(true) }
    var fresh by remember(account, generation, offerId) { mutableStateOf(false) }
    var unavailable by remember(account, generation, offerId) { mutableStateOf(false) }
    var error by remember(account, generation, offerId) { mutableStateOf<List<LocalizedStringDataModel>?>(null) }
    val remote by MarketplaceSignals.revision.collectAsState()
    val requests = remember(account, generation, offerId) { Channel<Unit>(Channel.CONFLATED) }
    val scope = rememberCoroutineScope()
    val latestShopping by rememberUpdatedState(shopping)
    val fence = remember(account, generation, offerId) { MarketOfferDetailReadFence() }
    var loadedStamp by remember(account, generation, offerId) { mutableStateOf<MarketOfferDetailReadFence.Stamp?>(null) }
    var receivedAt by remember(account, generation, offerId) { mutableStateOf<TimeMark?>(null) }
    fun requestRefresh() {
        // Disable old actions immediately, even before the actor consumes a conflated request.
        fence.invalidate(); fresh = false; requests.trySend(Unit)
    }
    fun detailIsCurrent(revision: Long): Boolean = owner?.isCurrent() == true && fresh && !unavailable && offer != null &&
        fence.canUse(loadedStamp, offerId, revision, receivedAt?.elapsedNow()?.inWholeMilliseconds ?: -1L, loading)
    fun displayedOfferIsCurrent(displayed: MarketOffer): Boolean =
        displayed == offer && detailIsCurrent(MarketplaceSignals.revision.value)
    DisposableEffect(requests) { onDispose { requests.close() } }
    LaunchedEffect(requests, remote) { requestRefresh() }
    LaunchedEffect(requests) {
        val owned = owner ?: run { loading = false; return@LaunchedEffect }
        for (ignored in requests) {
            delay(100)
            while (requests.tryReceive().isSuccess) { /* keep a trailing request only for changes during I/O */ }
            val stamp = fence.capture(offerId, MarketplaceSignals.revision.value)
            loading = true; fresh = false
            try {
                val response = loadMarketOfferDetail(owned, offerId)
                if (owned.isCurrent()) {
                    if (!fence.isCurrent(stamp, offerId, MarketplaceSignals.revision.value)) {
                        // An invalidation can precede the Compose effect that enqueues its read.
                        // Never publish this superseded success OR error; retain one trailing read.
                        requests.trySend(Unit)
                    } else {
                        val data = response.payload
                        if (!response.negative && data != null) {
                            offer = data.offer; branches = data.branchAvailability; branchesTruncated = data.branchAvailabilityTruncated; unavailable = false; error = null
                            loadedStamp = stamp; receivedAt = TimeSource.Monotonic.markNow(); fresh = true
                        } else {
                            error = response.message ?: eventMessage("market.detail_failed")
                            // A missing new route is an upgrade problem, not a withdrawn offer.
                            if (!response.transportFailure && response.httpStatusCode == 404 &&
                                response.message?.eventMessageReferenceOrNull()?.key == "market.unavailable") {
                                offer = null; branches = emptyList(); branchesTruncated = false; loadedStamp = null; receivedAt = null; unavailable = true
                            }
                        }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (owned.isCurrent()) {
                    if (!fence.isCurrent(stamp, offerId, MarketplaceSignals.revision.value)) requests.trySend(Unit)
                    else error = eventMessage("market.detail_failed")
                }
            } finally { loading = false }
        }
    }
    LaunchedEffect(requests) { while (isActive) { delay(MARKET_OFFER_DETAIL_FRESH_MILLIS); requestRefresh() } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.padding(16.dp).fillMaxWidth().aitaWidthCap(700.dp).heightIn(max = stateValues.screenHeight * 0.88f)
            .clip(RoundedCornerShape(stateValues.cornerRadius)).background(stateValues.BackgroundColor).padding(20.dp)
            .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val current = offer
            val detailReady = detailIsCurrent(remote)
            if (current == null && loading) LoadingSkeleton(Modifier.fillMaxWidth(), layout = LoadingLayout.OfferDetail, rows = 1)
            else if (current == null) Text(if (unavailable) authUiText("This offer is no longer available", "Предложение больше недоступно", "Ұсыныс енді қолжетімсіз", "Бул сунуш эми жеткиликсиз")
                else authUiText("Connect to view this offer", "Подключитесь, чтобы открыть предложение", "Ұсынысты көру үшін қосылыңыз", "Бул сунушту көрүү үчүн туташыңыз"),
                color = stateValues.TextColor, fontSize = stateValues.titleTextSize, fontWeight = FontWeight.Bold)
            else {
                MarketProductGallery(current.product, current.title)
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(current.title, color = stateValues.TextColor, fontSize = stateValues.titleTextSize, fontWeight = FontWeight.Bold)
                        Text(marketPriceLabel(current), color = changedValueColor(current.priceMinor, "detail:$offerId:${current.currencyCode}", stateValues.AccentColor),
                            fontSize = stateValues.accentTextSize, fontWeight = FontWeight.Bold)
                        if (current.description.isNotBlank()) Text(current.description, color = stateValues.TextColor, fontSize = stateValues.textSize)
                        MarketProductFacts(current.product)
                        MarketBranchAvailabilityContent(branches, branchesTruncated)
                        Text("${current.storefront.displayName}\n${current.storefront.city}\n${current.storefront.publicAddress}", color = stateValues.TextColor, fontSize = stateValues.textSize)
                        if (current.storefront.pickupNote.isNotBlank()) Text(current.storefront.pickupNote, color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                        Text(authUiText("Server inventory snapshot", "Снимок учётных остатков", "Есептегі қордың көрінісі", "Сервердеги товар калдыгынын учурундагы көрүнүшү") + ": " + receiptUiDateTime(current.checkedAtMillis),
                            color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                        Text(authUiText("A recorded-stock estimate, not a reservation. Your list is private to your account. Confirm final price and fulfilment with the shop; no order or payment is created here.",
                            "Это расчёт по учётным остаткам, не резерв. Список покупок доступен только вашему аккаунту. Уточните итоговую цену и получение в магазине: заказ и оплата здесь не создаются.",
                            "Бұл — есептегі қор бойынша есеп, резерв емес. Тізім тек сіздің аккаунтыңызға қолжетімді. Баға мен алуды дүкеннен нақтылаңыз: мұнда тапсырыс пен төлем жасалмайды.", "Бул резерв эмес, катталган товар калдыгы боюнча баа. Тизмеңиз аккаунтуңузга гана көрүнөт. Акыркы бааны жана аткаруу шарттарын дүкөндөн ырастатыңыз; бул жерде тапшырык же төлөм түзүлбөйт."),
                            color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                    }
                }
                if (!detailReady) Text(eventMessage("market.detail_stale").visibleLocalizedString(stateValues.appLanguage, ""),
                    color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                val inList = shopping.contains(offerId)
                actionButton(text = if (inList) authUiText("Open your list", "Открыть список покупок", "Тізімді ашу", "Тизмеңизди ачуу") else authUiText("Add to shopping list", "В список покупок", "Сатып алу тізіміне қосу", "Сатып алуу тизмесине кошуу"),
                    iconPath = marketIconPath(143), iconRes = marketIconFallback(143), autoLoading = false, confirmationRequired = false,
                    enabled = inList || (detailReady && shopping.canChange && current.shoppingBasis() != null), onClick = {
                        // A retained callback must not reuse its old membership or selling basis.
                        if (latestShopping.contains(offerId)) scope.launch {
                            if (owner?.isCurrent() == true) { Navigation.goMain(NavigationScreenModel.Buyer.Main.Shopping); onDismiss() }
                        }
                        else if (displayedOfferIsCurrent(current) && latestShopping.canChange && current.shoppingBasis() != null)
                            latestShopping.add(offerId, 1, current.shoppingBasis())
                        else requestRefresh()
                    })
                actionButton(text = authUiText("Visit shop", "Открыть магазин", "Дүкенге өту", "Дүкөнгө өтүү"), iconPath = marketIconPath(139), iconRes = marketIconFallback(139),
                    enabled = detailReady, autoLoading = false, confirmationRequired = false, onClick = {
                        if (displayedOfferIsCurrent(current)) onVisitShop(current.storefront) else requestRefresh()
                    })
                if (onCompare != null && current.comparisonSelection() != null) actionButton(text = authUiText("Compare offers", "Сравнить предложения", "Ұсыныстарды салыстыру", "Сунуштарды салыштыруу"),
                    iconPath = marketIconPath(141), iconRes = marketIconFallback(141), enabled = detailReady, autoLoading = false, confirmationRequired = false,
                    onClick = { if (displayedOfferIsCurrent(current)) onCompare(current) else requestRefresh() })
            }
            error?.let { Text(it.visibleLocalizedString(stateValues.appLanguage, ""), color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize) }
            MarketShoppingFeedback(shopping)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                actionButton(modifier = Modifier.fillMaxWidth(), text = authUiText("Refresh", "Обновить", "Жаңарту", "Жаңыртуу"), autoLoading = false, enabled = !loading,
                    loading = loading, confirmationRequired = false, onClick = { requestRefresh() })
                actionButton(modifier = Modifier.fillMaxWidth(), text = authUiText("Close", "Закрыть", "Жабу", "Жабуу"), autoLoading = false, confirmationRequired = false,
                    enabledColor = stateValues.BackgroundColor, textColor = stateValues.TextColor, onClick = onDismiss)
            }
        }
    }
}

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
    var loading by remember(account, generation, offerId) { mutableStateOf(true) }
    var fresh by remember(account, generation, offerId) { mutableStateOf(false) }
    var unavailable by remember(account, generation, offerId) { mutableStateOf(false) }
    var error by remember(account, generation, offerId) { mutableStateOf<List<LocalizedStringDataModel>?>(null) }
    val remote by MarketplaceSignals.revision.collectAsState()
    val requests = remember(account, generation, offerId) { Channel<Unit>(Channel.CONFLATED) }
    val scope = rememberCoroutineScope()
    DisposableEffect(requests) { onDispose { requests.close() } }
    LaunchedEffect(requests, remote) { requests.trySend(Unit) }
    LaunchedEffect(requests) {
        val owned = owner ?: run { loading = false; return@LaunchedEffect }
        for (ignored in requests) {
            delay(100)
            while (requests.tryReceive().isSuccess) { /* keep a trailing request only for changes during I/O */ }
            loading = true; fresh = false
            try {
                val response = loadMarketOffer(owned, offerId)
                if (owned.isCurrent()) {
                    val data = response.payload
                    if (!response.negative && data?.id == offerId) { offer = data; unavailable = false; fresh = true; error = null }
                    else {
                        error = response.message ?: eventMessage("market.refresh_failed")
                        if (response.httpStatusCode == 404 || response.httpStatusCode == 403) { offer = null; unavailable = true }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (owned.isCurrent()) error = eventMessage("market.refresh_failed") }
            finally { loading = false }
        }
    }
    LaunchedEffect(requests) { while (isActive) { delay(30_000); requests.trySend(Unit) } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.padding(16.dp).fillMaxWidth().aitaWidthCap(700.dp).heightIn(max = stateValues.screenHeight * 0.88f)
            .clip(RoundedCornerShape(stateValues.cornerRadius)).background(stateValues.BackgroundColor).padding(20.dp)
            .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val current = offer
            if (current == null && loading) LoadingSkeleton(Modifier.fillMaxWidth(), rows = 5)
            else if (current == null) Text(if (unavailable) authUiText("This offer is no longer available", "Предложение больше недоступно", "Ұсыныс енді қолжетімсіз")
                else authUiText("Connect to view this offer", "Подключитесь, чтобы открыть предложение", "Ұсынысты көру үшін қосылыңыз"),
                color = stateValues.TextColor, fontSize = stateValues.titleTextSize, fontWeight = FontWeight.Bold)
            else {
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(current.title, color = stateValues.TextColor, fontSize = stateValues.titleTextSize, fontWeight = FontWeight.Bold)
                        Text(marketPriceLabel(current), color = changedValueColor(current.priceMinor, "detail:$offerId:${current.currencyCode}", stateValues.AccentColor),
                            fontSize = stateValues.accentTextSize, fontWeight = FontWeight.Bold)
                        if (current.description.isNotBlank()) Text(current.description, color = stateValues.TextColor, fontSize = stateValues.textSize)
                        Text("${current.storefront.displayName}\n${current.storefront.city}\n${current.storefront.publicAddress}", color = stateValues.TextColor, fontSize = stateValues.textSize)
                        if (current.storefront.pickupNote.isNotBlank()) Text(current.storefront.pickupNote, color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                        Text(authUiText("Server inventory snapshot", "Снимок учётных остатков", "Есептегі қордың көрінісі") + ": " + receiptUiDateTime(current.checkedAtMillis),
                            color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                        Text(authUiText("A recorded-stock estimate, not a reservation. Your list is private to your account. Confirm final price and fulfilment with the shop; no order or payment is created here.",
                            "Это расчёт по учётным остаткам, не резерв. Список покупок доступен только вашему аккаунту. Уточните итоговую цену и получение в магазине: заказ и оплата здесь не создаются.",
                            "Бұл — есептегі қор бойынша есеп, резерв емес. Тізім тек сіздің аккаунтыңызға қолжетімді. Баға мен алуды дүкеннен нақтылаңыз: мұнда тапсырыс пен төлем жасалмайды."),
                            color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                    }
                }
                val inList = shopping.contains(offerId)
                actionButton(text = if (inList) authUiText("Open your list", "Открыть список покупок", "Тізімді ашу") else authUiText("Add to shopping list", "В список покупок", "Сатып алу тізіміне қосу"),
                    iconPath = marketIconPath(143), iconRes = marketIconFallback(143), autoLoading = false, confirmationRequired = false,
                    enabled = inList || (fresh && shopping.canChange && current.shoppingBasis() != null), onClick = {
                        if (inList) scope.launch { Navigation.goMain(NavigationScreenModel.Buyer.Main.Shopping); onDismiss() }
                        else shopping.change(offerId, 1, current.shoppingBasis())
                    })
                actionButton(text = authUiText("Visit shop", "Открыть магазин", "Дүкенге өту"), iconPath = marketIconPath(139), iconRes = marketIconFallback(139),
                    enabled = fresh, autoLoading = false, confirmationRequired = false, onClick = { onVisitShop(current.storefront) })
                if (onCompare != null && current.comparisonSelection() != null) actionButton(text = authUiText("Compare offers", "Сравнить предложения", "Ұсыныстарды салыстыру"),
                    iconPath = marketIconPath(141), iconRes = marketIconFallback(141), enabled = fresh, autoLoading = false, confirmationRequired = false,
                    onClick = { onCompare(current) })
            }
            error?.let { Text(it.visibleLocalizedString(stateValues.appLanguage, ""), color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize) }
            MarketShoppingFeedback(shopping)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                actionButton(modifier = Modifier.weight(1f), text = authUiText("Refresh", "Обновить", "Жаңарту"), autoLoading = false, enabled = !loading,
                    loading = loading, confirmationRequired = false, onClick = { requests.trySend(Unit) })
                actionButton(modifier = Modifier.weight(1f), text = authUiText("Close", "Закрыть", "Жабу"), autoLoading = false, confirmationRequired = false,
                    enabledColor = stateValues.BackgroundColor, textColor = stateValues.TextColor, onClick = onDismiss)
            }
        }
    }
}

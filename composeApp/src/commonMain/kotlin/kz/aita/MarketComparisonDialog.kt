package kz.aita

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Stable
private class MarketComparisonData {
    var page by mutableStateOf<MarketComparisonPage?>(null)
    var loading by mutableStateOf(true)
    var fresh by mutableStateOf(false)
    var error by mutableStateOf<List<LocalizedStringDataModel>?>(null)
}

/** A single live dialogue: browse -> explicit review -> durable replacement, not nested edit forms. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppConfiguration.MarketComparisonDialog(
    selection: MarketComparisonSelection,
    shopping: MarketShoppingUiState,
    onDismiss: () -> Unit,
    onVisitShop: (MarketStorefront) -> Unit,
    initialCity: String = ""
) {
    val account = stateValues.userAccount?.id
    val generation = currentAuthenticatedSessionGeneration()
    val owner = remember(account, generation, selection) { captureMarketRequestScope() }
    var units by remember(account, generation, selection) { mutableStateOf(selection.units) }
    var city by remember(account, generation, selection) { mutableStateOf(initialCity.take(100)) }
    var quantityDraft by remember(account, generation, selection, units) { mutableStateOf(units.toString()) }
    val target = selection.copy(units = units)
    val place = city.trim()
    val data = remember(account, generation, target, place) { MarketComparisonData() }
    var pages by remember(account, generation, target, place) { mutableStateOf(1) }
    val requestedPages by rememberUpdatedState(pages)
    var review by remember(account, generation, target, place) { mutableStateOf<MarketShoppingQuotedLine?>(null) }
    var submittedId by remember(account, generation, selection) { mutableStateOf<String?>(null) }
    val requests = remember(account, generation, target, place) { Channel<Unit>(Channel.CONFLATED) }
    val remote by MarketplaceSignals.revision.collectAsState()
    val listChanged = target.shoppingRevision != null && shopping.snapshot?.revision?.let { it != target.shoppingRevision } == true
    DisposableEffect(requests) { onDispose { requests.close() } }
    LaunchedEffect(requests, remote, pages) { requests.trySend(Unit) }
    LaunchedEffect(requests) {
        val owned = owner ?: run { data.loading = false; return@LaunchedEffect }
        for (ignored in requests) {
            delay(220)
            while (requests.tryReceive().isSuccess) { /* pre-read burst; retain a trailing request during I/O */ }
            if (!owned.isCurrent()) break
            data.loading = true; data.fresh = false
            try {
                val response = readMarketComparisonWindow(target, place, requestedPages) { loadMarketComparison(owned, it) }
                if (owned.isCurrent()) {
                    val value = response.payload
                    if (!response.negative && value != null) { data.page = value; data.fresh = true; data.error = null }
                    else {
                        data.error = response.message ?: eventMessage("market.comparison_refresh")
                        // Revoked publication / changed reference must not leave clickable ghost offers.
                        if (response.httpStatusCode in setOf(401, 403, 404, 409)) data.page = null
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (owned.isCurrent()) data.error = eventMessage("market.comparison_refresh") }
            finally { data.loading = false }
        }
    }
    LaunchedEffect(requests) { while (isActive) { delay(30_000); requests.trySend(Unit) } }
    LaunchedEffect(shopping.acknowledgedCommandId, submittedId, shopping.changing, shopping.pending) {
        if (submittedId != null && shopping.acknowledgedCommandId == submittedId) {
            if (shopping.lastChangeAccepted) onDismiss()
            else { submittedId = null; review = null; requests.trySend(Unit) }
        } else if (submittedId != null && !shopping.changing && shopping.pending == null) {
            // A failed local prepare never reached the server. Keep the review usable.
            submittedId = null
        }
    }
    val page = data.page
    val selected = review
    val comparisonReady = data.fresh && !data.loading && owner?.isCurrent() == true && !listChanged
    val reviewedCandidate = page?.matches?.firstOrNull { it.line.offerId == selected?.line?.offerId }
    val reviewUnchanged = selected != null && reviewedCandidate != null &&
        selected.line == reviewedCandidate.line && selected.status == reviewedCandidate.status &&
        selected.subtotalMinor == reviewedCandidate.subtotalMinor && selected.offer?.shoppingBasis() == reviewedCandidate.offer?.shoppingBasis() &&
        selected.offer?.storefront == reviewedCandidate.offer?.storefront

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth().aitaWidthCap(920.dp).fillMaxHeight(0.92f).padding(12.dp)
            .clip(RoundedCornerShape(stateValues.cornerRadius)).background(stateValues.BackgroundColor),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CpImage(Modifier.size(32.dp), url = marketIconPath(141), fallbackRes = marketIconFallback(141),
                    contentDescription = null, tintColor = stateValues.AccentColor)
                Text(if (selected == null) authUiText("Compare shops", "Сравнить магазины", "Дүкендерді салыстыру")
                    else authUiText("Review replacement", "Проверить замену", "Ауыстыруды тексеру"),
                    Modifier.weight(1f), color = stateValues.TextColor, fontSize = stateValues.accentTextSize, fontWeight = FontWeight.Bold)
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item(key = "context") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(authUiText("Same barcode, currency and selling unit. Compare model and packaging too: a barcode is not manufacturer verification.",
                            "Один штрихкод, валюта и единица продажи. Сверьте модель и упаковку: штрихкод не подтверждает подлинность.",
                            "Бір штрихкод, валюта және сату бірлігі. Модель мен қаптаманы да тексеріңіз: штрихкод түпнұсқалықты растамайды."),
                            color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                        val unitLabel = page?.reference?.line?.unitName.orEmpty().visibleLocalizedString(stateValues.appLanguage,
                            authUiText("unit", "ед.", "бірл."))
                        Text("${target.units} × ${target.basis.pricedAmount.toString().removeSuffix(".0")} $unitLabel · ${target.basis.currencyCode}",
                            color = stateValues.TextColor, fontSize = stateValues.textSize, fontWeight = FontWeight.Bold)
                        if (selected == null) {
                            if (target.shoppingRevision == null) {
                                aitaFormTextField(Modifier.fillMaxWidth(), quantityDraft, { value ->
                                    val clean = value.trim()
                                    if (clean.length <= 3 && clean.all { it in '0'..'9' }) quantityDraft = clean
                                }, authUiText("Number of selling units · 1–999", "Количество единиц продажи · 1–999", "Сату бірліктерінің саны · 1–999"),
                                    identityKey = "market-compare-quantity:$account:${selection.offerId}", autoFocus = false)
                                val enteredUnits = quantityDraft.toIntOrNull()?.takeIf { it in 1..MARKET_SHOPPING_MAX_UNITS }
                                if (quantityDraft != units.toString()) actionButton(
                                    text = authUiText("Apply quantity", "Применить количество", "Санды қолдану"),
                                    enabled = enteredUnits != null && !shopping.changing, autoLoading = false, confirmationRequired = false,
                                    onClick = { enteredUnits?.let { units = it } })
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    actionButton(modifier = Modifier.semantics { contentDescription = authUiText("Compare fewer units", "Сравнить меньшее количество", "Аз санды салыстыру") },
                                        text = "−", fillMaxWidthIfTextPresent = false, autoLoading = false, confirmationRequired = false,
                                        enabled = units > 1 && !shopping.changing, onClick = { units-- })
                                    actionButton(modifier = Modifier.semantics { contentDescription = authUiText("Compare more units", "Сравнить большее количество", "Көп санды салыстыру") },
                                        text = "+", fillMaxWidthIfTextPresent = false, autoLoading = false, confirmationRequired = false,
                                        enabled = units < MARKET_SHOPPING_MAX_UNITS && !shopping.changing, onClick = { units++ })
                                }
                            }
                            aitaFormTextField(Modifier.fillMaxWidth(), city, { city = it.take(100) },
                                authUiText("City · blank means all cities", "Город · пусто — все города", "Қала · бос болса — барлық қала"),
                                identityKey = "market-compare-city:$account:${selection.offerId}", autoFocus = false)
                        }
                        page?.reference?.let { reference ->
                            Text(authUiText("Your selection", "Ваш выбор", "Сіздің таңдауыңыз"), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                            Text("${reference.line.title}\n${reference.line.shopName}", color = stateValues.TextColor, fontSize = stateValues.textSize)
                            Text(reference.subtotalMinor?.let { marketMoneyLabel(it, target.basis.currencyCode) }
                                ?: authUiText("Original estimate unavailable · selection kept", "Исходный расчёт недоступен · выбор сохранён", "Бастапқы есеп қолжетімсіз · таңдау сақталды"),
                                color = stateValues.AccentColor, fontSize = stateValues.textSize, fontWeight = FontWeight.Bold)
                        }
                        if (listChanged) Text(authUiText("Your list changed. Close this comparison and open it from the current line.",
                            "Список изменился. Закройте сравнение и откройте его из актуальной строки.",
                            "Тізім өзгерді. Салыстыруды жауып, ағымдағы жолдан ашыңыз."), color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
                        data.error?.let { Text(it.visibleLocalizedString(stateValues.appLanguage, ""), color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize) }
                        MarketShoppingFeedback(shopping)
                    }
                }
                if (selected != null) item(key = "review") {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(stateValues.cornerRadius))
                        .background(stateValues.AccentColor.copy(alpha = 0.07f)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(authUiText("Replace with", "Заменить на", "Мынаған ауыстыру"), color = stateValues.TextColor, fontWeight = FontWeight.Bold, fontSize = stateValues.accentTextSize)
                        Text("${selected.line.title}\n${selected.line.shopName}", color = stateValues.TextColor, fontSize = stateValues.textSize)
                        selected.offer?.storefront?.let { shop -> Text("${shop.city}\n${shop.publicAddress}", color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize) }
                        Text(selected.subtotalMinor?.let { marketMoneyLabel(it, target.basis.currencyCode) }.orEmpty(), color = stateValues.AccentColor, fontWeight = FontWeight.Bold, fontSize = stateValues.titleTextSize)
                        Text(authUiText("Only this line changes shops. Its quantity and selling unit stay the same. Other lines are not merged. This is not an order, reservation or payment; transport and fees are not included.",
                            "Только эта строка меняет магазин. Количество и единица продажи сохраняются. Другие строки не объединяются. Это не заказ, резерв или оплата; доставка и сборы не включены.",
                            "Тек осы жолдың дүкені өзгереді. Саны мен сату бірлігі сақталады. Басқа жолдар біріктірілмейді. Бұл тапсырыс, резерв не төлем емес; жеткізу мен алымдар кірмейді."),
                            color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                        if (!reviewUnchanged && !data.loading) Text(authUiText("The reviewed offer changed. Return to offers and review it again.",
                            "Проверенное предложение изменилось. Вернитесь к предложениям и проверьте снова.", "Тексерілген ұсыныс өзгерді. Ұсыныстарға оралып, қайта тексеріңіз."),
                            color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
                        actionButton(text = authUiText("Replace this list line", "Заменить строку списка", "Тізім жолын ауыстыру"), autoLoading = false, confirmationRequired = false,
                            loading = shopping.changing, enabled = comparisonReady && reviewUnchanged && submittedId == null && shopping.canChange &&
                                shopping.snapshot?.let { target.reviewedReplacement(it, selected, "review") } != null,
                            onClick = { submittedId = shopping.replace(target, selected) })
                        actionButton(text = stateValues.stringBack, enabledColor = stateValues.BackgroundColor, textColor = stateValues.TextColor,
                            autoLoading = false, confirmationRequired = false, enabled = !shopping.changing, onClick = { review = null })
                    }
                } else {
                    if (page == null && data.loading) item(key = "skeleton") { LoadingSkeleton(Modifier.fillMaxWidth(), rows = 5) }
                    else if (page != null && page.matches.isEmpty()) item(key = "no-matches") {
                        Text(if (page.nextId == null) authUiText("No other matching published offers in this search.", "Других подходящих опубликованных предложений в этом поиске нет.", "Осы іздеуде басқа сәйкес жарияланған ұсыныстар жоқ.")
                            else authUiText("No compatible offers on these pages yet. More candidates are available.", "На этих страницах пока нет совместимых предложений. Есть ещё кандидаты.", "Бұл беттерде сәйкес ұсыныстар әлі жоқ. Тағы үміткерлер бар."),
                            color = stateValues.PlaceholderTextColor, fontSize = stateValues.textSize)
                    }
                    items(page?.matches.orEmpty(), key = { it.line.offerId }) { candidate ->
                        MarketComparisonCard(candidate, page?.reference?.subtotalMinor, target, shopping, comparisonReady,
                            onReview = { review = candidate }, onVisitShop = onVisitShop)
                    }
                    if (page?.nextId != null) item(key = "more") {
                        if (pages < MARKET_COMPARISON_MAX_PAGES) actionButton(text = authUiText("Search more offers", "Найти ещё предложения", "Тағы ұсыныстарды іздеу"),
                            autoLoading = false, confirmationRequired = false, enabled = !data.loading, onClick = { pages++ })
                        else Text(authUiText("Search window reached: 400 candidates. Set a city to narrow the search. Unseen offers are not ranked.",
                            "Достигнут предел: 400 кандидатов. Укажите город для уточнения поиска. Непросмотренные предложения не ранжируются.",
                            "Іздеу шегі: 400 үміткер. Іздеуді тарылту үшін қаланы көрсетіңіз. Көрсетілмеген ұсыныстар реттелмейді."),
                            color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                    }
                }
                item(key = "limits") {
                    Text(authUiText("Requested-quantity estimates, lowest loaded estimate first. Prices, publication and stock can change; nothing is reserved. No delivery costs or travel distance are compared.",
                        "Расчёт на выбранное количество, сначала меньшая из загруженных сумм. Цены, публикация и остатки могут измениться; резерва нет. Стоимость доставки и расстояние не сравниваются.",
                        "Таңдалған санға есеп: жүктелген ең төмен сома алдымен. Баға, жарияланым мен қор өзгеруі мүмкін; резерв жоқ. Жеткізу құны мен қашықтық салыстырылмайды."),
                        color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                }
            }
            FlowRow(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.fillMaxWidth()) {
                    Text(if (!data.fresh) authUiText("Refresh needed", "Нужно обновить", "Жаңарту қажет")
                        else authUiText("${page?.matches?.size ?: 0} matching offers loaded", "Загружено совпадений: ${page?.matches?.size ?: 0}", "${page?.matches?.size ?: 0} сәйкес ұсыныс жүктелді"),
                        color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                    page?.checkedAtMillis?.let { Text(receiptUiDateTime(it), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize) }
                }
                actionButton(text = authUiText("Refresh", "Обновить", "Жаңарту"), fillMaxWidthIfTextPresent = false, autoLoading = false,
                    enabled = !data.loading, loading = data.loading, confirmationRequired = false, onClick = { requests.trySend(Unit) })
                actionButton(text = authUiText("Close", "Закрыть", "Жабу"), fillMaxWidthIfTextPresent = false,
                    enabledColor = stateValues.BackgroundColor, textColor = stateValues.TextColor, autoLoading = false, confirmationRequired = false, onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun AppConfiguration.MarketComparisonCard(row: MarketShoppingQuotedLine, originalMinor: Long?, selection: MarketComparisonSelection,
    shopping: MarketShoppingUiState, fresh: Boolean, onReview: () -> Unit, onVisitShop: (MarketStorefront) -> Unit) {
    val offer = row.offer ?: return
    val inList = shopping.contains(offer.id)
    Column(Modifier.fillMaxWidth().border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor.copy(alpha = 0.25f),
        RoundedCornerShape(stateValues.cornerRadius)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(offer.title, color = stateValues.TextColor, fontSize = stateValues.accentTextSize, fontWeight = FontWeight.Bold)
        Text("${offer.storefront.displayName} · ${offer.storefront.city}", color = stateValues.TextColor, fontSize = stateValues.textSize)
        Text(row.subtotalMinor?.let { marketMoneyLabel(it, selection.basis.currencyCode) }
            ?: authUiText("Confirm this quantity and price", "Уточните количество и цену", "Сан мен бағаны нақтылаңыз"),
            color = changedValueColor(row.subtotalMinor, "compare:${row.line.offerId}:${selection.units}", stateValues.AccentColor),
            fontSize = stateValues.accentTextSize, fontWeight = FontWeight.Bold)
        val targetMinor = row.subtotalMinor
        if (originalMinor != null && targetMinor != null && row.status == MARKET_QUOTE_ESTIMATED) {
            val difference = originalMinor - targetMinor
            Text(when {
                difference > 0L -> authUiText("Items estimate is ${marketMoneyLabel(difference, selection.basis.currencyCode)} lower", "Расчёт товаров ниже на ${marketMoneyLabel(difference, selection.basis.currencyCode)}", "Тауар есебі ${marketMoneyLabel(difference, selection.basis.currencyCode)} төмен")
                difference < 0L -> authUiText("Items estimate is ${marketMoneyLabel(-difference, selection.basis.currencyCode)} higher", "Расчёт товаров выше на ${marketMoneyLabel(-difference, selection.basis.currencyCode)}", "Тауар есебі ${marketMoneyLabel(-difference, selection.basis.currencyCode)} жоғары")
                else -> authUiText("Same items estimate", "Та же сумма товаров", "Тауар сомасы бірдей")
            }, color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
        }
        if (selection.shoppingRevision != null) actionButton(text = if (inList) authUiText("Already in your list", "Уже в списке", "Тізімде бар")
            else authUiText("Review replacement", "Проверить замену", "Ауыстыруды тексеру"),
            enabled = fresh && !inList && shopping.canChange && row.status == MARKET_QUOTE_ESTIMATED && row.subtotalMinor != null,
            autoLoading = false, confirmationRequired = false, onClick = onReview)
        else actionButton(text = if (inList) authUiText("In your list", "В вашем списке", "Сіздің тізіміңізде") else authUiText("Add this quantity to list", "Добавить это количество в список", "Осы санды тізімге қосу"),
            enabled = fresh && !inList && shopping.canChange, autoLoading = false, confirmationRequired = false,
            onClick = { shopping.change(offer.id, selection.units, selection.basis) })
        actionButton(text = authUiText("Visit shop", "Открыть магазин", "Дүкенге өту"), enabled = fresh,
            enabledColor = stateValues.BackgroundColor, textColor = stateValues.TextColor, autoLoading = false, confirmationRequired = false,
            onClick = { onVisitShop(offer.storefront) })
    }
}

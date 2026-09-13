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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.time.TimeSource

@Stable
private class MarketBasketView {
    var result by mutableStateOf<MarketBasketResult?>(null)
    var loading by mutableStateOf(false)
    var loadedSignal by mutableStateOf(-1L)
    var error by mutableStateOf<List<LocalizedStringDataModel>?>(null)
}

private data class FrozenBasketReview(val result: MarketBasketResult, val command: MarketShoppingCommand,
    val signal: Long, val started: kotlin.time.TimeMark)

/** Planning stays read-only. A separate frozen review applies all selected-currency changes as
 * one command. Only one live dialog body is composed; reviews never follow changing estimates.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppConfiguration.MarketBasketPlanDialog(
    shopping: MarketShoppingUiState,
    onDismiss: () -> Unit,
    onCompare: (MarketComparisonSelection, String) -> Unit,
    onVisitShop: (MarketStorefront) -> Unit
) {
    val account = stateValues.userAccount?.id
    val generation = currentAuthenticatedSessionGeneration()
    val owner = remember(account, generation) { captureMarketRequestScope() }
    var cityDraft by remember(account, generation) { mutableStateOf("") }
    var city by remember(account, generation) { mutableStateOf("") }
    var kind by remember(account, generation) { mutableStateOf(MARKET_BASKET_TWO_SHOPS) }
    var currency by remember(account, generation) { mutableStateOf("") }
    var review by remember(account, generation) { mutableStateOf<FrozenBasketReview?>(null) }
    var submittedId by remember(account, generation) { mutableStateOf<String?>(null) }
    val listRevision = shopping.snapshot?.revision
    val request = listRevision?.let { MarketBasketRequest(it, city) }
    val data = remember(account, generation, request) { MarketBasketView() }
    val requests = remember(account, generation, request) { Channel<Unit>(Channel.CONFLATED) }
    val signal by MarketplaceSignals.revision.collectAsState()
    val latestSignal by rememberUpdatedState(signal)
    val blocked = shopping.pending != null || shopping.changing || shopping.checking || shopping.cancelling
    val latestBlocked by rememberUpdatedState(blocked || review != null)
    // Across request/filter changes too: typing and toggling cannot reset the automatic cooldown.
    val lastRead = remember(account, generation) { mutableStateOf<kotlin.time.TimeMark?>(null) }
    DisposableEffect(requests) { onDispose { requests.close() } }
    LaunchedEffect(requests, signal, blocked, review) { if (!blocked && review == null) requests.trySend(Unit) }
    LaunchedEffect(requests) {
        val owned = owner ?: run { data.error = eventMessage("market.shopping_denied"); return@LaunchedEffect }
        val wanted = request ?: run { data.error = eventMessage("market.basket_invalid"); return@LaunchedEffect }
        for (ignored in requests) {
            delay(200)
            val waitMillis = lastRead.value?.let { (10_000L - it.elapsedNow().inWholeMilliseconds).coerceAtLeast(0L) } ?: 0L
            if (waitMillis > 0) delay(waitMillis)
            while (requests.tryReceive().isSuccess) { /* keep the newest pre-read signal; I/O arrivals stay queued */ }
            if (!owned.isCurrent()) break
            if (latestBlocked) continue
            val startingSignal = latestSignal
            data.loading = true; data.loadedSignal = -1L
            lastRead.value = TimeSource.Monotonic.markNow()
            try {
                val response = loadMarketBasketPlan(owned, wanted)
                if (owned.isCurrent()) {
                    val result = response.payload
                    if (!response.negative && result != null) {
                        data.result = result; data.error = null
                        // An invalidation DURING the read leaves this result visibly stale until
                        // the queued trailing read succeeds. It cannot re-enable review actions.
                        data.loadedSignal = startingSignal
                    } else {
                        data.error = response.message ?: eventMessage("market.basket_refresh")
                        if (response.httpStatusCode in setOf(401, 403, 404, 409)) data.result = null
                        if (response.httpStatusCode == 409) shopping.refresh()
                        if (response.httpStatusCode == 429) delay(30_000)
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (owned.isCurrent()) data.error = eventMessage("market.basket_refresh") }
            finally { data.loading = false }
        }
    }
    LaunchedEffect(requests) { while (isActive) { delay(60_000); if (!latestBlocked) requests.trySend(Unit) } }
    LaunchedEffect(shopping.acknowledgedCommandId, submittedId, shopping.changing, shopping.pending) {
        val submitted = submittedId
        if (submitted != null && shopping.acknowledgedCommandId == submitted) {
            if (shopping.lastChangeAccepted) onDismiss()
            else { submittedId = null; review = null; requests.trySend(Unit) }
        } else if (submitted != null && !shopping.changing && shopping.pending == null) {
            submittedId = null // A local preparation failure has no network success to assume.
        }
    }
    val frozen = review
    if (frozen != null) {
        MarketBasketReviewDialog(frozen.result, frozen.command, frozen.started, shopping,
            valid = owner?.isCurrent() == true && frozen.signal == signal &&
                shopping.snapshot?.revision == frozen.command.expectedRevision,
            submitted = submittedId != null, onConfirm = {
                if (shopping.applyBasket(frozen.command)) submittedId = frozen.command.commandId
            }, onBack = { review = null; submittedId = null; requests.trySend(Unit) }, onDismiss = onDismiss)
        return
    }
    val result = data.result
    val group = result?.currencies?.firstOrNull { it.currencyCode == currency } ?: result?.currencies?.firstOrNull()
    val plan = group?.plan(kind)
    val fresh = !blocked && !data.loading && owner?.isCurrent() == true && result != null &&
        result.snapshot.revision == listRevision && data.loadedSignal == signal && data.error == null

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth().aitaWidthCap(960.dp).fillMaxHeight(0.92f).padding(12.dp)
            .clip(RoundedCornerShape(stateValues.cornerRadius)).background(stateValues.BackgroundColor)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CpImage(Modifier.size(32.dp), url = marketIconPath(141), fallbackRes = marketIconFallback(141), contentDescription = null, tintColor = stateValues.AccentColor)
                Text(authUiText("Plan your basket", "План корзины", "Себет жоспары"), Modifier.weight(1f),
                    color = stateValues.TextColor, fontSize = stateValues.accentTextSize, fontWeight = FontWeight.Bold)
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item(key = "scope") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(authUiText("Compare first, then explicitly review shop changes. Complete coverage comes before a lower price. Each currency is planned separately.",
                            "Сначала сравните, затем явно проверьте замены магазинов. Полный состав важнее низкой цены. Каждая валюта рассчитывается отдельно.",
                            "Алдымен салыстырып, содан кейін дүкен ауыстыруларын тексеріңіз. Толық қамту төмен бағадан маңызды. Әр валюта бөлек есептеледі."),
                            color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                        aitaFormTextField(Modifier.fillMaxWidth(), cityDraft, { cityDraft = it.take(100) },
                            authUiText("City · blank means all cities", "Город · пусто — все города", "Қала · бос болса — барлық қала"),
                            identityKey = "basket-city:$account", autoFocus = false, parentOwnsValue = true)
                        val normalizedCity = MarketBasketRequest(0, cityDraft).normalizedBasketRequest()?.city
                        if (cityDraft != city) actionButton(text = authUiText("Apply city", "Применить город", "Қаланы қолдану"),
                            enabled = normalizedCity != null && !blocked, fillMaxWidthIfTextPresent = false, autoLoading = false,
                            confirmationRequired = false, onClick = { normalizedCity?.let { city = it; cityDraft = it } })
                        if (city.isNotEmpty()) Text(authUiText("Alternatives: $city. Current list keeps its original shops, including other cities.",
                            "Альтернативы: $city. Текущий список сохраняет исходные магазины, в том числе в других городах.",
                            "Баламалар: $city. Ағымдағы тізім бастапқы дүкендерді, соның ішінде басқа қалаларды сақтайды."),
                            color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                        shopping.notice?.let { Text(it.visibleLocalizedString(stateValues.appLanguage, ""), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize) }
                        data.error?.let { Text(it.visibleLocalizedString(stateValues.appLanguage, ""), color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize) }
                        if (blocked) Text(authUiText("Resolve the unconfirmed list change before comparing again.", "Подтвердите незавершённое изменение списка перед сравнением.", "Қайта салыстырмас бұрын расталмаған тізім өзгерісін аяқтаңыз."),
                            color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
                    }
                }
                if (result == null && (data.loading || data.error == null) && !blocked) item { LoadingSkeleton(Modifier.fillMaxWidth(), rows = 5) }
                if (result != null && group == null) item {
                    Text(authUiText("Add products to your list first", "Сначала добавьте товары в список", "Алдымен тізімге тауар қосыңыз"), color = stateValues.TextColor, fontSize = stateValues.textSize)
                }
                if (result != null && group != null && plan != null) {
                    item(key = "plans") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (result.currencies.size > 1) sectionTabsWidget("basket-currency:$account",
                                result.currencies.map { TabContent(it.currencyCode, it.currencyCode) }, selectedId = group.currencyCode, onSelected = { currency = it })
                            sectionTabsWidget("basket-plan:$account", listOf(
                                TabContent(MARKET_BASKET_CURRENT, authUiText("Current", "Сейчас", "Қазір")),
                                TabContent(MARKET_BASKET_ONE_SHOP, authUiText("1 shop", "1 магазин", "1 дүкен")),
                                TabContent(MARKET_BASKET_TWO_SHOPS, authUiText("Up to 2", "До 2 магазинов", "2 дүкенге дейін")),
                                TabContent(MARKET_BASKET_LOWEST_ITEMS, authUiText("Lower total", "Меньше сумма", "Төмен сома"))),
                                selectedId = kind, onSelected = { kind = it })
                            BasketPlanSummary(group, plan)
                            if (plan.kind != MARKET_BASKET_CURRENT && plan.changedLines > 0) {
                                actionButton(text = authUiText("Review this plan", "Проверить этот план", "Осы жоспарды тексеру"),
                                    iconPath = marketIconPath(141), iconRes = marketIconFallback(141),
                                    enabled = fresh && shopping.canChange && plan.complete,
                                    autoLoading = false, confirmationRequired = false, onClick = {
                                        result.reviewedBasketCommand(group.currencyCode, plan.kind, newClientSideUuidString())?.let { command ->
                                            review = FrozenBasketReview(result, command, signal, lastRead.value ?: TimeSource.Monotonic.markNow())
                                        }
                                    })
                                if (!plan.complete) Text(authUiText("A whole-plan change needs every line in this currency. Missing lines are never removed to make it fit.",
                                    "Для применения плана нужны все строки в этой валюте. Недостающие товары не удаляются ради результата.",
                                    "Жоспарды қолдану үшін осы валютадағы барлық жол қажет. Жетіспейтін тауарлар нәтиже үшін жойылмайды."),
                                    color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                            }
                        }
                    }
                    items(plan.choices, key = { "choice:${it.sourceOfferId}" }) { choice ->
                        val original = result.snapshot.lines.first { it.line.offerId == choice.sourceOfferId }.line
                        BasketPlanChoiceCard(original, choice, fresh, onCompare = {
                            original.comparisonSelection(result.snapshot.revision)?.let { onCompare(it, city) }
                        }, onVisitShop = onVisitShop)
                    }
                    if (plan.missingOfferIds.isNotEmpty()) item(key = "missing") {
                        Column(Modifier.fillMaxWidth().background(stateValues.ErrorColor.copy(alpha = 0.05f)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(authUiText("Not covered · not free", "Не включено · не бесплатно", "Қамтылмаған · тегін емес"),
                                color = stateValues.ErrorColor, fontSize = stateValues.textSize, fontWeight = FontWeight.Bold)
                            plan.missingOfferIds.forEach { id ->
                                val original = result.snapshot.lines.first { it.line.offerId == id }.line
                                Text("${original.title} · ${original.units} × ${original.basis.pricedAmount.toString().removeSuffix(".0")} ${original.unitName.visibleLocalizedString(stateValues.appLanguage, authUiText("unit", "ед.", "бірл."))}",
                                    color = stateValues.TextColor, fontSize = stateValues.smallTextSize)
                            }
                        }
                    }
                    item(key = "limits") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(authUiText("Checked ${result.candidatesChecked} alternative listings: up to $MARKET_BASKET_CANDIDATES_PER_LINE per comparable line. UUID order, not the whole market's cheapest offers. ${result.limitedSourceOfferIds.size} lines reached that limit.",
                                "Проверено альтернатив: ${result.candidatesChecked}, до $MARKET_BASKET_CANDIDATES_PER_LINE на сравнимую строку. Порядок по ID, не самые дешёвые предложения всего рынка. Лимит достигнут у ${result.limitedSourceOfferIds.size} строк.",
                                "${result.candidatesChecked} балама тексерілді: салыстырылатын әр жолға $MARKET_BASKET_CANDIDATES_PER_LINE дейін. ID реті, бүкіл нарықтағы ең арзан ұсыныстар емес. ${result.limitedSourceOfferIds.size} жол шекке жетті."),
                                color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                            Text(authUiText("${group.eligibleShopCount} eligible shops in ${group.currencyCode}; two-shop combinations use up to ${group.pairSearchShopCount}, prioritised by coverage. Lower total may need more shops. This is not a route planner.",
                                "Подходящих магазинов в ${group.currencyCode}: ${group.eligibleShopCount}; пары проверяются среди ${group.pairSearchShopCount}, с приоритетом состава корзины. Низкая сумма может требовать больше магазинов. Это не расчёт маршрута.",
                                "${group.currencyCode}: ${group.eligibleShopCount} жарамды дүкен; жұптар қамту бойынша таңдалған ${group.pairSearchShopCount} дүкеннен тексеріледі. Төмен сомаға көбірек дүкен қажет болуы мүмкін. Бұл бағыт жоспарлаушы емес."),
                                color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                            if (result.fixedLines.isNotEmpty()) Text(authUiText("${result.fixedLines.size} lines have no valid barcode or repeat the same product/unit: they stay at their original shop, when priceable. Quantities are not merged.",
                                "У ${result.fixedLines.size} строк нет корректного штрихкода или повторяется товар с той же единицей: они остаются в исходном магазине, если доступны для расчёта. Количества не объединяются.",
                                "${result.fixedLines.size} жолда дұрыс штрихкод жоқ немесе тауар мен бірлік қайталанады: есептеу мүмкін болса, бастапқы дүкенде қалады. Сандар біріктірілмейді."),
                                color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                            Text(authUiText("Same barcode, currency and selling amount; check the actual product too. Delivery, travel costs and basket discounts are excluded. No order, payment or reservation is created. Use Review this plan for one complete currency group, or Compare this item for an individual replacement.",
                                "Одинаковые штрихкод, валюта и единица продажи; сверьте сам товар. Доставка, дорога и скидки на корзину не учтены. Заказ, оплата и резерв не создаются. Проверьте план целиком для одной валюты или отдельную замену через «Сравнить товар».",
                                "Бірдей штрихкод, валюта және сату мөлшері; тауардың өзін де тексеріңіз. Жеткізу, жол құны мен себет жеңілдіктері кірмейді. Тапсырыс, төлем не резерв жасалмайды. Бір валютадағы толық жоспарды немесе «Тауарды салыстыру» арқылы жеке ауыстыруды тексеріңіз."),
                                color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                        }
                    }
                }
            }
            FlowRow(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (fresh) authUiText("Checked", "Проверено", "Тексерілді") + " · " + receiptUiDateTime(result?.snapshot?.checkedAtMillis ?: 0L)
                    else authUiText("Refresh needed · estimates only", "Нужно обновить · только расчёт", "Жаңарту қажет · тек есеп"),
                    Modifier.padding(vertical = 10.dp), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                actionButton(text = authUiText("Refresh", "Обновить", "Жаңарту"), fillMaxWidthIfTextPresent = false, autoLoading = false,
                    enabled = !data.loading && !blocked, loading = data.loading, confirmationRequired = false, onClick = { requests.trySend(Unit) })
                actionButton(text = authUiText("Close", "Закрыть", "Жабу"), fillMaxWidthIfTextPresent = false, autoLoading = false,
                    enabledColor = stateValues.BackgroundColor, textColor = stateValues.TextColor, confirmationRequired = false, onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun AppConfiguration.BasketPlanSummary(group: MarketBasketCurrencyPlans, plan: MarketBasketPlan) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(stateValues.cornerRadius))
        .background(stateValues.AccentColor.copy(alpha = 0.07f)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (plan.complete) authUiText("Estimated items total", "Расчёт товаров", "Тауарлар есебі")
            else authUiText("Partial items subtotal", "Неполная сумма товаров", "Тауарлардың толық емес сомасы"), color = stateValues.TextColor, fontSize = stateValues.textSize)
        Text(plan.itemsSubtotalMinor?.let { marketMoneyLabel(it, group.currencyCode) } ?: authUiText("No priced lines", "Нет рассчитанных строк", "Есептелген жолдар жоқ"),
            color = changedValueColor(plan.itemsSubtotalMinor, "basket:${group.currencyCode}:${plan.kind}", stateValues.AccentColor),
            fontSize = stateValues.titleTextSize, fontWeight = FontWeight.Bold)
        val total = plan.choices.size + plan.missingOfferIds.size
        Text(authUiText("${plan.choices.size}/$total lines · ${plan.storeIds.size} shops · ${plan.changedLines} alternatives",
            "${plan.choices.size}/$total строк · магазинов: ${plan.storeIds.size} · замен: ${plan.changedLines}",
            "${plan.choices.size}/$total жол · ${plan.storeIds.size} дүкен · ${plan.changedLines} балама"), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
        if (plan.kind != MARKET_BASKET_CURRENT) plan.savingsAgainst(group.current)?.let { difference ->
            Text(when {
                difference > 0 -> authUiText("${marketMoneyLabel(difference, group.currencyCode)} lower than your complete current basket", "Ниже полной текущей корзины на ${marketMoneyLabel(difference, group.currencyCode)}", "Толық ағымдағы себеттен ${marketMoneyLabel(difference, group.currencyCode)} төмен")
                difference < 0 -> authUiText("${marketMoneyLabel(-difference, group.currencyCode)} higher than your complete current basket", "Выше полной текущей корзины на ${marketMoneyLabel(-difference, group.currencyCode)}", "Толық ағымдағы себеттен ${marketMoneyLabel(-difference, group.currencyCode)} жоғары")
                else -> authUiText("Same complete items estimate", "Та же полная сумма товаров", "Тауарлардың толық сомасы бірдей")
            }, color = stateValues.TextColor, fontSize = stateValues.smallTextSize)
        }
    }
}

@Composable
private fun AppConfiguration.BasketPlanChoiceCard(source: MarketShoppingLine, choice: MarketBasketChoice, fresh: Boolean,
    onCompare: () -> Unit, onVisitShop: (MarketStorefront) -> Unit) {
    val row = choice.quote
    val offer = row.offer ?: return
    val unit = source.unitName.visibleLocalizedString(stateValues.appLanguage, authUiText("unit", "ед.", "бірл."))
    Column(Modifier.fillMaxWidth().border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor.copy(alpha = 0.22f),
        RoundedCornerShape(stateValues.cornerRadius)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(source.title, color = stateValues.TextColor, fontSize = stateValues.textSize, fontWeight = FontWeight.Bold)
        if (offer.title != source.title) Text(authUiText("Shop label: ${offer.title}", "Название у продавца: ${offer.title}", "Сатушыдағы атауы: ${offer.title}"),
            color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
        Text("${offer.storefront.displayName} · ${offer.storefront.city}", color = stateValues.TextColor, fontSize = stateValues.textSize)
        if (offer.id != source.offerId) Text(authUiText("Instead of ${source.shopName}", "Вместо ${source.shopName}", "${source.shopName} орнына"),
            color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
        Text("${source.units} × ${source.basis.pricedAmount.toString().removeSuffix(".0")} $unit · ${marketMoneyLabel(requireNotNull(row.subtotalMinor), source.basis.currencyCode)}",
            color = stateValues.AccentColor, fontSize = stateValues.textSize, fontWeight = FontWeight.Bold)
        if (source.basis.gtin != null) actionButton(text = authUiText("Compare this item", "Сравнить товар", "Тауарды салыстыру"),
            iconPath = marketIconPath(141), iconRes = marketIconFallback(141), enabled = fresh, autoLoading = false, confirmationRequired = false, onClick = onCompare)
        actionButton(text = authUiText("Visit shop", "Открыть магазин", "Дүкенге өту"), iconPath = marketIconPath(139), iconRes = marketIconFallback(139),
            enabledColor = stateValues.BackgroundColor, textColor = stateValues.TextColor, enabled = fresh,
            autoLoading = false, confirmationRequired = false, onClick = { onVisitShop(offer.storefront) })
    }
}

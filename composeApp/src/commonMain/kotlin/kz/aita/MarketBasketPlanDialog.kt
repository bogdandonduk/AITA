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
    var active = true
    var result by mutableStateOf<MarketBasketResult?>(null)
    var loading by mutableStateOf(false)
    var loadedStamp by mutableStateOf<MarketBasketPlanReadFence.Stamp?>(null)
    var error by mutableStateOf<List<LocalizedStringDataModel>?>(null)
    val fence = MarketBasketPlanReadFence()
    var startedAt: kotlin.time.TimeMark? = null
    // Switching plan/currency retires old row/button callbacks without spending a network read.
    var presentation by mutableStateOf(Any())

    fun invalidate() { fence.invalidate(); loadedStamp = null; presentation = Any() }
    fun select() { presentation = Any() }
}

private data class FrozenBasketReview(val result: MarketBasketResult, val command: MarketShoppingCommand,
    val stamp: MarketBasketPlanReadFence.Stamp, val started: kotlin.time.TimeMark)

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
    val request = shopping.snapshot?.revision?.let { MarketBasketRequest(it, city) }
    val data = remember(account, generation, request) { MarketBasketView() }
    val requests = remember(data) { Channel<Unit>(Channel.CONFLATED) }
    val signal by MarketplaceSignals.revision.collectAsState()
    val latestDismiss by rememberUpdatedState(onDismiss)
    val latestCompare by rememberUpdatedState(onCompare)
    val latestVisitShop by rememberUpdatedState(onVisitShop)
    // Across request/filter changes too: typing and toggling cannot reset the automatic cooldown.
    val lastRead = remember(account, generation) { mutableStateOf<kotlin.time.TimeMark?>(null) }
    fun shoppingBlocked(): Boolean = shopping.pending != null || shopping.changing || shopping.checking || shopping.cancelling
    fun wantedNow(): MarketBasketRequest? = shopping.snapshot?.revision?.let { MarketBasketRequest(it, city) }
    fun queueRefresh() {
        if (!data.active || owner?.isCurrent() != true) return
        data.invalidate() // Retire displayed actions before the cooldown, not when I/O starts.
        requests.trySend(Unit)
    }
    fun planIsCurrent(displayed: MarketBasketResult? = data.result, presentation: Any = data.presentation,
        currentSignal: Long = MarketplaceSignals.revision.value): Boolean = data.fence.canUse(
        data.loadedStamp, data.result, wantedNow(), currentSignal, shopping.snapshot,
        data.startedAt?.elapsedNow()?.inWholeMilliseconds ?: -1L,
        !data.active || owner?.isCurrent() != true || !shopping.active || shoppingBlocked() || review != null ||
            data.loading || data.error != null || data.result !== displayed || data.presentation !== presentation)
    fun reviewIsCurrent(frozen: FrozenBasketReview): Boolean = data.fence.canConfirm(
        frozen.stamp, frozen.result, frozen.command, wantedNow(), MarketplaceSignals.revision.value,
        shopping.snapshot, frozen.started.elapsedNow().inWholeMilliseconds,
        !data.active || owner?.isCurrent() != true || !shopping.canChange || data.loading || data.error != null)
    fun requireFreshPlan() {
        queueRefresh()
        if (data.active && owner?.isCurrent() == true) data.error = eventMessage("market.basket_plan_stale")
    }
    val blocked = shoppingBlocked()
    DisposableEffect(data) { onDispose { data.active = false; data.invalidate(); requests.close() } }
    LaunchedEffect(data, signal, blocked, review) {
        if (review == null) {
            data.invalidate()
            if (!blocked) queueRefresh()
        }
    }
    LaunchedEffect(data) {
        val owned = owner ?: run { data.error = eventMessage("market.shopping_denied"); return@LaunchedEffect }
        val wanted = request?.normalizedBasketRequest() ?: run { data.error = eventMessage("market.basket_invalid"); return@LaunchedEffect }
        for (ignored in requests) {
            delay(200)
            val waitMillis = lastRead.value?.let { (10_000L - it.elapsedNow().inWholeMilliseconds).coerceAtLeast(0L) } ?: 0L
            if (waitMillis > 0) delay(waitMillis)
            while (requests.tryReceive().isSuccess) { /* I/O arrivals keep one trailing refresh. */ }
            if (!data.active || !owned.isCurrent()) break
            if (shoppingBlocked() || review != null || wantedNow() != wanted) continue
            val stamp = data.fence.capture(wanted, MarketplaceSignals.revision.value)
            val started = TimeSource.Monotonic.markNow()
            data.loading = true; data.loadedStamp = null
            lastRead.value = started
            fun stillOwnsRead(): Boolean = data.fence.isCurrent(stamp, wantedNow(), MarketplaceSignals.revision.value) &&
                !shoppingBlocked() && review == null
            try {
                val response = loadMarketBasketPlan(owned, wanted)
                if (!data.active || !owned.isCurrent()) break
                if (!stillOwnsRead()) {
                    // A late error must not erase the newer view or trigger its list refresh.
                    if (!shoppingBlocked() && review == null) queueRefresh()
                } else {
                    val result = response.payload
                    if (!response.negative && result != null) {
                        data.result = result; data.error = null; data.loadedStamp = stamp
                        data.startedAt = started; data.select()
                    } else {
                        data.error = response.message ?: eventMessage("market.basket_refresh")
                        if (!response.transportFailure && response.httpStatusCode in setOf(401, 403, 404, 409)) data.result = null
                        if (!response.transportFailure && response.httpStatusCode == 409) shopping.refresh()
                        if (!response.transportFailure && response.httpStatusCode == 429) delay(30_000)
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (data.active && owned.isCurrent()) {
                    if (!stillOwnsRead()) { if (!shoppingBlocked() && review == null) queueRefresh() }
                    else data.error = eventMessage("market.basket_refresh")
                }
            } finally { if (data.active) data.loading = false }
        }
    }
    LaunchedEffect(data) {
        while (isActive) { delay(MARKET_BASKET_PLAN_FRESH_MILLIS); if (!shoppingBlocked() && review == null) queueRefresh() }
    }
    LaunchedEffect(shopping.acknowledgedCommandId, submittedId, shopping.changing, shopping.pending) {
        val submitted = submittedId
        if (submitted != null && shopping.acknowledgedCommandId == submitted) {
            if (shopping.lastChangeAccepted) latestDismiss()
            else { submittedId = null; review = null; queueRefresh() }
        } else if (submitted != null && !shopping.changing && shopping.pending == null) {
            submittedId = null // A local preparation failure has no network success to assume.
        }
    }
    val frozen = review
    if (frozen != null) {
        MarketBasketReviewDialog(frozen.result, frozen.command, frozen.started, shopping,
            valid = reviewIsCurrent(frozen), submitted = submittedId != null, onConfirm = {
                // Re-evaluate live state and the original read age even if the button's last
                // rendered enabled value was true. Never create a new ID for this review.
                if (review !== frozen || submittedId != null || !reviewIsCurrent(frozen)) false
                else if (shopping.applyBasket(frozen.command)) {
                    submittedId = frozen.command.commandId
                    true
                } else false
            }, onBack = {
                if (data.active && owner?.isCurrent() == true && review === frozen && !shoppingBlocked()) {
                    review = null; submittedId = null; queueRefresh()
                }
            }, onDismiss = latestDismiss)
        return
    }
    val result = data.result
    val presentation = data.presentation
    val group = result?.currencies?.firstOrNull { it.currencyCode == currency } ?: result?.currencies?.firstOrNull()
    val plan = group?.plan(kind)
    val fresh = planIsCurrent(currentSignal = signal)

    Dialog(onDismissRequest = latestDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        TransactionBarcodeModalGuard()
        Column(Modifier.fillMaxWidth().aitaWidthCap(960.dp).fillMaxHeight(0.92f).padding(12.dp)
            .clip(RoundedCornerShape(stateValues.cornerRadius)).background(stateValues.BackgroundColor)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CpImage(Modifier.size(32.dp), url = marketIconPath(141), fallbackRes = marketIconFallback(141), contentDescription = null, tintColor = stateValues.AccentColor)
                Text(authUiText("Plan your basket", "План корзины", "Себет жоспары", "Себетиңизди пландаңыз"), Modifier.weight(1f),
                    color = stateValues.TextColor, fontSize = stateValues.accentTextSize, fontWeight = FontWeight.Bold)
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item(key = "scope") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(authUiText("Compare first, then explicitly review shop changes. Complete coverage comes before a lower price. Each currency is planned separately.",
                            "Сначала сравните, затем явно проверьте замены магазинов. Полный состав важнее низкой цены. Каждая валюта рассчитывается отдельно.",
                            "Алдымен салыстырып, содан кейін дүкен ауыстыруларын тексеріңіз. Толық қамту төмен бағадан маңызды. Әр валюта бөлек есептеледі.", "Адегенде салыштырып, андан кийин дүкөн өзгөртүүлөрүн атайын карап чыгыңыз. Төмөн баага караганда толук камтууга артыкчылык берилет. Ар бир валюта өзүнчө пландалат."),
                            color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                        aitaFormTextField(Modifier.fillMaxWidth(), cityDraft, { cityDraft = it.take(100) },
                            authUiText("City · blank means all cities", "Город · пусто — все города", "Қала · бос болса — барлық қала", "Шаар · бош болсо бардык шаарлар"),
                            identityKey = "basket-city:$account", autoFocus = false, parentOwnsValue = true)
                        val normalizedCity = MarketBasketRequest(0, cityDraft).normalizedBasketRequest()?.city
                        if (cityDraft != city) actionButton(text = authUiText("Apply city", "Применить город", "Қаланы қолдану", "Шаарды колдонуу"),
                            enabled = normalizedCity != null && !blocked,autoLoading = false,
                            confirmationRequired = false, onClick = {
                                if (data.active && owner?.isCurrent() == true && !shoppingBlocked()) {
                                    // The draft may have changed since the Apply button was rendered.
                                    MarketBasketRequest(0, cityDraft).normalizedBasketRequest()?.city?.let {
                                        data.invalidate(); city = it; cityDraft = it; queueRefresh()
                                    }
                                }
                            })
                        if (city.isNotEmpty()) Text(authUiText("Alternatives: $city. Current list keeps its original shops, including other cities.",
                            "Альтернативы: $city. Текущий список сохраняет исходные магазины, в том числе в других городах.",
                            "Баламалар: $city. Ағымдағы тізім бастапқы дүкендерді, соның ішінде басқа қалаларды сақтайды.", "Башка варианттар: $city. Учурдагы тизме баштапкы дүкөндөрдү, анын ичинде башка шаарлардагы дүкөндөрдү да сактайт."),
                            color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                        shopping.notice?.let { Text(it.visibleLocalizedString(stateValues.appLanguage, ""), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize) }
                        data.error?.let { Text(it.visibleLocalizedString(stateValues.appLanguage, ""), color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize) }
                        if (blocked) Text(authUiText("Resolve the unconfirmed list change before comparing again.", "Подтвердите незавершённое изменение списка перед сравнением.", "Қайта салыстырмас бұрын расталмаған тізім өзгерісін аяқтаңыз.", "Кайра салыштыруудан мурун тизменин ырастала элек өзгөртүүсүн чечиңиз."),
                            color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
                    }
                }
                if (result == null && (data.loading || data.error == null) && !blocked) item { LoadingSkeleton(layout = LoadingLayout.Comparison, modifier = Modifier.fillMaxWidth(), rows = 5) }
                if (result != null && group == null) item {
                    Text(authUiText("Add products to your list first", "Сначала добавьте товары в список", "Алдымен тізімге тауар қосыңыз", "Адегенде тизмеңизге товарларды кошуңуз"), color = stateValues.TextColor, fontSize = stateValues.textSize)
                }
                if (result != null && group != null && plan != null) {
                    item(key = "plans") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (result.currencies.size > 1) sectionTabsWidget("basket-currency:$account",
                                result.currencies.map { TabContent(it.currencyCode, it.currencyCode, icon = AitaTabIcon.Money) }, selectedId = group.currencyCode, onSelected = {
                                    if (data.active && owner?.isCurrent() == true && currency != it) { data.select(); currency = it }
                                })
                            sectionTabsWidget("basket-plan:$account", listOf(
                                TabContent(MARKET_BASKET_CURRENT, authUiText("Current", "Сейчас", "Қазір", "Учурдагы"), icon = AitaTabIcon.Basket),
                                TabContent(MARKET_BASKET_ONE_SHOP, authUiText("1 shop", "1 магазин", "1 дүкен", "1 дүкөн")),
                                TabContent(MARKET_BASKET_TWO_SHOPS, authUiText("Up to 2", "До 2 магазинов", "2 дүкенге дейін", "2ге чейин")),
                                TabContent(MARKET_BASKET_LOWEST_ITEMS, authUiText("Lower total", "Меньше сумма", "Төмен сома", "Төмөнүрөөк жалпы сумма"))),
                                selectedId = kind, onSelected = {
                                    if (data.active && owner?.isCurrent() == true && kind != it) { data.select(); kind = it }
                                })
                            BasketPlanSummary(group, plan)
                            if (plan.kind != MARKET_BASKET_CURRENT && plan.changedLines > 0) {
                                actionButton(text = authUiText("Review this plan", "Проверить этот план", "Осы жоспарды тексеру", "Бул планды кароо"),
                                    iconPath = marketIconPath(141), iconRes = marketIconFallback(141),
                                    enabled = fresh && shopping.canChange && plan.complete,
                                    autoLoading = false, confirmationRequired = false, onClick = {
                                        if (planIsCurrent(result, presentation) && shopping.canChange) {
                                            val stamp = data.loadedStamp
                                            val started = data.startedAt
                                            val command = result.reviewedBasketCommand(group.currencyCode, plan.kind, newClientSideUuidString())
                                            if (stamp != null && started != null && command != null) {
                                                review = FrozenBasketReview(result, command, stamp, started)
                                            }
                                        } else requireFreshPlan()
                                    })
                                if (!plan.complete) Text(authUiText("A whole-plan change needs every line in this currency. Missing lines are never removed to make it fit.",
                                    "Для применения плана нужны все строки в этой валюте. Недостающие товары не удаляются ради результата.",
                                    "Жоспарды қолдану үшін осы валютадағы барлық жол қажет. Жетіспейтін тауарлар нәтиже үшін жойылмайды.", "Бүт планды өзгөртүү үчүн ушул валютадагы бардык саптар керек. Планды ылайыкташтыруу үчүн жетишпеген саптар эч качан өчүрүлбөйт."),
                                    color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                            }
                        }
                    }
                    items(plan.choices, key = { "choice:${it.sourceOfferId}" }) { choice ->
                        val original = result.snapshot.lines.first { it.line.offerId == choice.sourceOfferId }.line
                        BasketPlanChoiceCard(original, choice, fresh, onCompare = {
                            if (planIsCurrent(result, presentation) && shopping.canChange) {
                                result.snapshot.reviewShoppingLine(original)?.let { lineReview ->
                                    shopping.compareLine(lineReview)?.let { latestCompare(it, result.request.city) }
                                }
                            } else requireFreshPlan()
                        }, onVisitShop = { shop ->
                            if (planIsCurrent(result, presentation) && choice.quote.offer?.storefront == shop) latestVisitShop(shop)
                            else requireFreshPlan()
                        })
                    }
                    if (plan.missingOfferIds.isNotEmpty()) item(key = "missing") {
                        Column(Modifier.fillMaxWidth().background(stateValues.ErrorColor.copy(alpha = 0.05f)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(authUiText("Not covered · not free", "Не включено · не бесплатно", "Қамтылмаған · тегін емес", "Камтылган эмес · бекер эмес"),
                                color = stateValues.ErrorColor, fontSize = stateValues.textSize, fontWeight = FontWeight.Bold)
                            plan.missingOfferIds.forEach { id ->
                                val original = result.snapshot.lines.first { it.line.offerId == id }.line
                                Text("${original.title} · ${original.units} × ${original.basis.pricedAmount.toString().removeSuffix(".0")} ${original.unitName.visibleLocalizedString(stateValues.appLanguage, authUiText("unit", "ед.", "бірл.", "бирдик"))}",
                                    color = stateValues.TextColor, fontSize = stateValues.smallTextSize)
                            }
                        }
                    }
                    item(key = "limits") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(authUiText("Checked ${result.candidatesChecked} alternative listings: up to $MARKET_BASKET_CANDIDATES_PER_LINE per comparable line. UUID order, not the whole market's cheapest offers. ${result.limitedSourceOfferIds.size} lines reached that limit.",
                                "Проверено альтернатив: ${result.candidatesChecked}, до $MARKET_BASKET_CANDIDATES_PER_LINE на сравнимую строку. Порядок по ID, не самые дешёвые предложения всего рынка. Лимит достигнут у ${result.limitedSourceOfferIds.size} строк.",
                                "${result.candidatesChecked} балама тексерілді: салыстырылатын әр жолға $MARKET_BASKET_CANDIDATES_PER_LINE дейін. ID реті, бүкіл нарықтағы ең арзан ұсыныстар емес. ${result.limitedSourceOfferIds.size} жол шекке жетті.", "${result.candidatesChecked} башка жарыя текшерилди: ар бир салыштырылуучу сапка эң көп $MARKET_BASKET_CANDIDATES_PER_LINE. Булар бүт маркеттеги эң арзан сунуштар эмес, UUID боюнча иреттелген. ${result.limitedSourceOfferIds.size} сап ушул чекке жетти."),
                                color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                            Text(authUiText("${group.eligibleShopCount} eligible shops in ${group.currencyCode}; two-shop combinations use up to ${group.pairSearchShopCount}, prioritised by coverage. Lower total may need more shops. This is not a route planner.",
                                "Подходящих магазинов в ${group.currencyCode}: ${group.eligibleShopCount}; пары проверяются среди ${group.pairSearchShopCount}, с приоритетом состава корзины. Низкая сумма может требовать больше магазинов. Это не расчёт маршрута.",
                                "${group.currencyCode}: ${group.eligibleShopCount} жарамды дүкен; жұптар қамту бойынша таңдалған ${group.pairSearchShopCount} дүкеннен тексеріледі. Төмен сомаға көбірек дүкен қажет болуы мүмкін. Бұл бағыт жоспарлаушы емес.", "${group.currencyCode} валютасында ${group.eligibleShopCount} ылайыктуу дүкөн; эки дүкөндүн айкалыштары камтуу деңгээлине жараша артыкчылык берилген эң көп ${group.pairSearchShopCount} дүкөндү колдонот. Төмөнүрөөк жалпы сумма үчүн көбүрөөк дүкөн талап кылынышы мүмкүн. Бул каттам пландоочу эмес."),
                                color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                            if (result.fixedLines.isNotEmpty()) Text(authUiText("${result.fixedLines.size} lines have no valid barcode or repeat the same product/unit: they stay at their original shop, when priceable. Quantities are not merged.",
                                "У ${result.fixedLines.size} строк нет корректного штрихкода или повторяется товар с той же единицей: они остаются в исходном магазине, если доступны для расчёта. Количества не объединяются.",
                                "${result.fixedLines.size} жолда дұрыс штрихкод жоқ немесе тауар мен бірлік қайталанады: есептеу мүмкін болса, бастапқы дүкенде қалады. Сандар біріктірілмейді.", "${result.fixedLines.size} сапта жарактуу штрихкод жок же бир эле товар/бирдик кайталанат: баасын эсептөөгө мүмкүн болсо, алар баштапкы дүкөнүндө калат. Сандар бириктирилбейт."),
                                color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                            Text(authUiText("Same barcode, currency and selling amount; check the actual product too. Delivery, travel costs and basket discounts are excluded. No order, payment or reservation is created. Use Review this plan for one complete currency group, or Compare this item for an individual replacement.",
                                "Одинаковые штрихкод, валюта и единица продажи; сверьте сам товар. Доставка, дорога и скидки на корзину не учтены. Заказ, оплата и резерв не создаются. Проверьте план целиком для одной валюты или отдельную замену через «Сравнить товар».",
                                "Бірдей штрихкод, валюта және сату мөлшері; тауардың өзін де тексеріңіз. Жеткізу, жол құны мен себет жеңілдіктері кірмейді. Тапсырыс, төлем не резерв жасалмайды. Бір валютадағы толық жоспарды немесе «Тауарды салыстыру» арқылы жеке ауыстыруды тексеріңіз.", "Штрихкод, валюта жана сатуу көлөмү бирдей; товардын өзүн да текшериңиз. Жеткирүү, жол чыгымдары жана себет арзандатуулары кошулган жок. Тапшырык, төлөм же резерв түзүлбөйт. Бир толук валюта тобу үчүн «Бул планды кароону», ал эми жеке алмаштыруу үчүн «Бул товарды салыштырууну» колдонуңуз."),
                                color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                        }
                    }
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(if (fresh) authUiText("Checked", "Проверено", "Тексерілді", "Текшерилди") + " · " + receiptUiDateTime(result?.snapshot?.checkedAtMillis ?: 0L)
                    else authUiText("Refresh needed · estimates only", "Нужно обновить · только расчёт", "Жаңарту қажет · тек есеп", "Жаңыртуу керек · болжолдуу суммалар гана"),
                    Modifier.padding(vertical = 10.dp), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                actionButton(text = authUiText("Refresh", "Обновить", "Жаңарту", "Жаңыртуу"),autoLoading = false,
                    enabled = !data.loading && !blocked, loading = data.loading, confirmationRequired = false, onClick = { queueRefresh() })
                actionButton(text = authUiText("Close", "Закрыть", "Жабу", "Жабуу"),autoLoading = false,
                    enabledColor = stateValues.BackgroundColor, textColor = stateValues.TextColor, confirmationRequired = false, onClick = latestDismiss)
            }
        }
    }
}

@Composable
private fun AppConfiguration.BasketPlanSummary(group: MarketBasketCurrencyPlans, plan: MarketBasketPlan) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(stateValues.cornerRadius))
        .background(stateValues.AccentColor.copy(alpha = 0.07f)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (plan.complete) authUiText("Estimated items total", "Расчёт товаров", "Тауарлар есебі", "Товарлардын болжолдуу жалпы суммасы")
            else authUiText("Partial items subtotal", "Неполная сумма товаров", "Тауарлардың толық емес сомасы", "Товарлардын жарым-жартылай аралык суммасы"), color = stateValues.TextColor, fontSize = stateValues.textSize)
        Text(plan.itemsSubtotalMinor?.let { marketMoneyLabel(it, group.currencyCode) } ?: authUiText("No priced lines", "Нет рассчитанных строк", "Есептелген жолдар жоқ", "Баасы бар саптар жок"),
            color = changedValueColor(plan.itemsSubtotalMinor, "basket:${group.currencyCode}:${plan.kind}", stateValues.AccentColor),
            fontSize = stateValues.titleTextSize, fontWeight = FontWeight.Bold)
        val total = plan.choices.size + plan.missingOfferIds.size
        Text(authUiText("${plan.choices.size}/$total lines · ${plan.storeIds.size} shops · ${plan.changedLines} alternatives",
            "${plan.choices.size}/$total строк · магазинов: ${plan.storeIds.size} · замен: ${plan.changedLines}",
            "${plan.choices.size}/$total жол · ${plan.storeIds.size} дүкен · ${plan.changedLines} балама", "${plan.choices.size}/$total сап · ${plan.storeIds.size} дүкөн · ${plan.changedLines} башка вариант"), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
        if (plan.kind != MARKET_BASKET_CURRENT) plan.savingsAgainst(group.current)?.let { difference ->
            Text(when {
                difference > 0 -> authUiText("${marketMoneyLabel(difference, group.currencyCode)} lower than your complete current basket", "Ниже полной текущей корзины на ${marketMoneyLabel(difference, group.currencyCode)}", "Толық ағымдағы себеттен ${marketMoneyLabel(difference, group.currencyCode)} төмен", "Учурдагы толук себетиңизден ${marketMoneyLabel(difference, group.currencyCode)} арзан")
                difference < 0 -> authUiText("${marketMoneyLabel(-difference, group.currencyCode)} higher than your complete current basket", "Выше полной текущей корзины на ${marketMoneyLabel(-difference, group.currencyCode)}", "Толық ағымдағы себеттен ${marketMoneyLabel(-difference, group.currencyCode)} жоғары", "Учурдагы толук себетиңизден ${marketMoneyLabel(-difference, group.currencyCode)} кымбат")
                else -> authUiText("Same complete items estimate", "Та же полная сумма товаров", "Тауарлардың толық сомасы бірдей", "Толук товар курамынын болжолдуу суммасы бирдей")
            }, color = stateValues.TextColor, fontSize = stateValues.smallTextSize)
        }
    }
}

@Composable
private fun AppConfiguration.BasketPlanChoiceCard(source: MarketShoppingLine, choice: MarketBasketChoice, fresh: Boolean,
    onCompare: () -> Unit, onVisitShop: (MarketStorefront) -> Unit) {
    val row = choice.quote
    val offer = row.offer ?: return
    val unit = source.unitName.visibleLocalizedString(stateValues.appLanguage, authUiText("unit", "ед.", "бірл.", "бирдик"))
    Column(Modifier.fillMaxWidth().border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor.copy(alpha = 0.22f),
        RoundedCornerShape(stateValues.cornerRadius)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(source.title, color = stateValues.TextColor, fontSize = stateValues.textSize, fontWeight = FontWeight.Bold)
        if (offer.title != source.title) Text(authUiText("Shop label: ${offer.title}", "Название у продавца: ${offer.title}", "Сатушыдағы атауы: ${offer.title}", "Дүкөндөгү аталышы: ${offer.title}"),
            color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
        Text("${offer.storefront.displayName} · ${offer.storefront.city}", color = stateValues.TextColor, fontSize = stateValues.textSize)
        if (offer.id != source.offerId) Text(authUiText("Instead of ${source.shopName}", "Вместо ${source.shopName}", "${source.shopName} орнына", "${source.shopName} дүкөнүнүн ордуна"),
            color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
        Text("${source.units} × ${source.basis.pricedAmount.toString().removeSuffix(".0")} $unit · ${marketMoneyLabel(requireNotNull(row.subtotalMinor), source.basis.currencyCode)}",
            color = stateValues.AccentColor, fontSize = stateValues.textSize, fontWeight = FontWeight.Bold)
        if (source.basis.gtin != null) actionButton(text = authUiText("Compare this item", "Сравнить товар", "Тауарды салыстыру", "Бул товарды салыштыруу"),
            iconPath = marketIconPath(141), iconRes = marketIconFallback(141), enabled = fresh, autoLoading = false, confirmationRequired = false, onClick = onCompare)
        actionButton(text = authUiText("Visit shop", "Открыть магазин", "Дүкенге өту", "Дүкөнгө өтүү"), iconPath = marketIconPath(139), iconRes = marketIconFallback(139),
            enabledColor = stateValues.BackgroundColor, textColor = stateValues.TextColor, enabled = fresh,
            autoLoading = false, confirmationRequired = false, onClick = { onVisitShop(offer.storefront) })
    }
}

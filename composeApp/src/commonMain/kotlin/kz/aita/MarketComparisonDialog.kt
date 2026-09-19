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
import kotlin.time.TimeSource

@Stable
private class MarketComparisonData {
    var active = true
    var page by mutableStateOf<MarketComparisonWindowResult?>(null)
    var loadedStamp by mutableStateOf<MarketComparisonReadFence.Stamp?>(null)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<List<LocalizedStringDataModel>?>(null)
    val fence = MarketComparisonReadFence()
    var startedAt: kotlin.time.TimeMark? = null
    var presentation by mutableStateOf(Any())

    fun invalidate() { fence.invalidate(); loadedStamp = null; select() }
    fun select() { presentation = Any() }
}

private data class FrozenComparisonReview(val page: MarketComparisonWindowResult,
    val candidate: MarketShoppingQuotedLine, val stamp: MarketComparisonReadFence.Stamp,
    val started: kotlin.time.TimeMark)

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
    var quantityDraft by remember(account, generation, selection) { mutableStateOf(units.toString()) }
    var pages by remember(account, generation, selection) { mutableStateOf(1) }
    val data = remember(account, generation, selection) { MarketComparisonData() }
    var review by remember(data) { mutableStateOf<FrozenComparisonReview?>(null) }
    var submittedId by remember(data) { mutableStateOf<String?>(null) }
    val requests = remember(data) { Channel<Unit>(Channel.CONFLATED) }
    val remote by MarketplaceSignals.revision.collectAsState()
    val latestDismiss by rememberUpdatedState(onDismiss)
    val latestVisitShop by rememberUpdatedState(onVisitShop)
    // Keep the read cooldown across quantity/city edits, not just across identical requests.
    val lastRead = remember(account, generation, selection) { mutableStateOf<kotlin.time.TimeMark?>(null) }
    fun wantedNow() = MarketComparisonWindowRequest(selection.copy(units = units), city,
        pages * MARKET_COMPARISON_PAGE_CANDIDATES)
    fun shoppingBlocked() = !shopping.active || shopping.pending != null || shopping.changing || shopping.checking || shopping.cancelling
    fun draftPending() = selection.shoppingRevision == null && quantityDraft != units.toString()
    fun queueRefresh() {
        if (!data.active || owner?.isCurrent() != true) return
        data.invalidate() // Retire old callbacks NOW, before debounce/cooldown or network I/O.
        if (review == null && !shoppingBlocked()) requests.trySend(Unit)
    }
    fun comparisonIsCurrent(displayed: MarketComparisonWindowResult? = data.page,
        presentation: Any = data.presentation, signal: Long = MarketplaceSignals.revision.value): Boolean =
        data.fence.canUse(data.loadedStamp, data.page, wantedNow(), account.orEmpty(), signal, shopping.snapshot,
            data.startedAt?.elapsedNow()?.inWholeMilliseconds ?: -1L,
            !data.active || owner?.isCurrent() != true || shoppingBlocked() || data.loading || data.error != null ||
                review != null || draftPending() || data.page !== displayed || data.presentation !== presentation)
    fun reviewIsCurrent(frozen: FrozenComparisonReview): Boolean =
        data.fence.canConfirm(frozen.stamp, frozen.page, frozen.candidate, wantedNow(), account.orEmpty(),
            MarketplaceSignals.revision.value, shopping.snapshot, frozen.started.elapsedNow().inWholeMilliseconds,
            !data.active || owner?.isCurrent() != true || !shopping.canChange || shoppingBlocked() ||
                data.loading || data.error != null || data.page !== frozen.page || review !== frozen || draftPending())
    fun requireFreshComparison() {
        if (!data.active || owner?.isCurrent() != true) return
        queueRefresh()
        data.error = eventMessage("market.comparison_action_stale")
    }
    fun closeComparison() {
        if (!data.active) return
        data.active = false; data.invalidate(); latestDismiss()
    }
    fun applyQuantity(value: Int) {
        if (!data.active || owner?.isCurrent() != true || shoppingBlocked() || review != null ||
            selection.shoppingRevision != null || value !in 1..MARKET_SHOPPING_MAX_UNITS) return
        units = value; quantityDraft = value.toString(); pages = 1
        data.page = null; data.error = null // Never display an old subtotal beside the new quantity.
        queueRefresh()
    }
    val blocked = shoppingBlocked()
    DisposableEffect(data) { onDispose { data.active = false; data.invalidate(); requests.close() } }
    LaunchedEffect(data, remote, blocked) { queueRefresh() }
    LaunchedEffect(data) {
        val owned = owner ?: run { data.error = eventMessage("market.shopping_denied"); return@LaunchedEffect }
        for (ignored in requests) {
            delay(220)
            val waitMillis = lastRead.value?.let { (5_000L - it.elapsedNow().inWholeMilliseconds).coerceAtLeast(0L) } ?: 0L
            if (waitMillis > 0L) delay(waitMillis)
            while (requests.tryReceive().isSuccess) { /* coalesce the pre-read burst, not I/O arrivals */ }
            if (!data.active || !owned.isCurrent()) break
            if (review != null || shoppingBlocked()) continue
            val wanted = wantedNow().normalizedComparisonWindowRequest()
            if (wanted == null) { data.error = eventMessage("market.comparison_window_invalid"); continue }
            val stamp = data.fence.capture(wanted, MarketplaceSignals.revision.value)
            val started = TimeSource.Monotonic.markNow()
            data.loading = true; data.loadedStamp = null; lastRead.value = started
            fun stillOwnsRead() = data.fence.isCurrent(stamp, wantedNow(), MarketplaceSignals.revision.value) &&
                review == null && !shoppingBlocked()
            try {
                val response = loadMarketComparisonWindow(owned, wanted)
                if (!data.active || !owned.isCurrent()) break
                if (!stillOwnsRead()) queueRefresh() // A superseded error is not an error for the current request.
                else {
                    val value = response.payload
                    if (!response.negative && value != null) {
                        data.page = value; data.loadedStamp = stamp; data.startedAt = started
                        data.error = null; data.select()
                    } else {
                        data.error = response.message ?: eventMessage("market.comparison_window_refresh")
                        if (!response.transportFailure && response.httpStatusCode in setOf(401, 403, 404, 409)) data.page = null
                        if (!response.transportFailure && response.httpStatusCode == 429) delay(30_000)
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (data.active && owned.isCurrent()) {
                    if (!stillOwnsRead()) queueRefresh()
                    else data.error = eventMessage("market.comparison_window_refresh")
                }
            } finally { if (data.active) data.loading = false }
        }
    }
    LaunchedEffect(data) {
        while (isActive) { delay(30_000); awaitClientBackgroundWork(); if (review == null && !shoppingBlocked()) queueRefresh() }
    }
    LaunchedEffect(review) {
        val frozen = review ?: return@LaunchedEffect
        delay((MARKET_COMPARISON_FRESH_MILLIS - frozen.started.elapsedNow().inWholeMilliseconds).coerceAtLeast(0L))
        if (data.active && owner?.isCurrent() == true && review === frozen && submittedId == null && !shoppingBlocked()) {
            data.invalidate()
            data.error = eventMessage("market.comparison_review_stale")
        }
    }
    LaunchedEffect(shopping.acknowledgedCommandId, submittedId, shopping.changing, shopping.pending) {
        if (submittedId != null && shopping.acknowledgedCommandId == submittedId) {
            if (shopping.lastChangeAccepted) closeComparison()
            else { submittedId = null; review = null; queueRefresh() }
        } else if (submittedId != null && !shopping.changing && shopping.pending == null) {
            // Local preparation failure: no network success is assumed and no retry is invented.
            submittedId = null
        }
    }
    val target = selection.copy(units = units)
    val page = data.page
    val presentation = data.presentation
    val frozen = review
    val selected = frozen?.candidate
    val listChanged = target.shoppingRevision != null && shopping.snapshot?.revision != target.shoppingRevision
    val comparisonReady = comparisonIsCurrent(signal = remote)
    val reviewUnchanged = frozen != null && reviewIsCurrent(frozen)
    val displayedReady = if (frozen == null) comparisonReady else reviewUnchanged

    Dialog(onDismissRequest = ::closeComparison, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        TransactionBarcodeModalGuard()
        Column(Modifier.fillMaxWidth().aitaWidthCap(920.dp).fillMaxHeight(0.92f).padding(12.dp)
            .clip(RoundedCornerShape(stateValues.cornerRadius)).background(stateValues.BackgroundColor),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CpImage(Modifier.size(32.dp), url = marketIconPath(141), fallbackRes = marketIconFallback(141),
                    contentDescription = null, tintColor = stateValues.AccentColor)
                Text(if (selected == null) authUiText("Compare shops", "Сравнить магазины", "Дүкендерді салыстыру", "Дүкөндөрдү салыштыруу")
                    else authUiText("Review replacement", "Проверить замену", "Ауыстыруды тексеру", "Алмаштырууну кароо"),
                    Modifier.weight(1f), color = stateValues.TextColor, fontSize = stateValues.accentTextSize, fontWeight = FontWeight.Bold)
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item(key = "context") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(authUiText("Same barcode, currency and selling unit. Compare model and packaging too: a barcode is not manufacturer verification.",
                            "Один штрихкод, валюта и единица продажи. Сверьте модель и упаковку: штрихкод не подтверждает подлинность.",
                            "Бір штрихкод, валюта және сату бірлігі. Модель мен қаптаманы да тексеріңіз: штрихкод түпнұсқалықты растамайды.", "Штрихкод, валюта жана сатуу бирдиги бирдей. Моделин жана таңгагын да салыштырыңыз: штрихкод өндүрүүчү текшерилгенин билдирбейт."),
                            color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                        val unitLabel = page?.reference?.line?.unitName.orEmpty().visibleLocalizedString(stateValues.appLanguage,
                            authUiText("unit", "ед.", "бірл.", "бирдик"))
                        Text("${target.units} × ${target.basis.pricedAmount.toString().removeSuffix(".0")} $unitLabel · ${target.basis.currencyCode}",
                            color = stateValues.TextColor, fontSize = stateValues.textSize, fontWeight = FontWeight.Bold)
                        if (selected == null) {
                            if (target.shoppingRevision == null) {
                                aitaFormTextField(Modifier.fillMaxWidth(), quantityDraft, { value ->
                                    val clean = value.trim()
                                    if (data.active && owner?.isCurrent() == true && review == null &&
                                        clean.length <= 3 && clean.all { it in '0'..'9' } && quantityDraft != clean) {
                                        quantityDraft = clean; data.select() // Old Add must not use an unapplied draft.
                                    }
                                }, authUiText("Number of selling units · 1–999", "Количество единиц продажи · 1–999", "Сату бірліктерінің саны · 1–999", "Сатуу бирдиктеринин саны · 1–999"),
                                    identityKey = "market-compare-quantity:$account:${selection.offerId}", autoFocus = false, parentOwnsValue = true)
                                val enteredUnits = quantityDraft.toIntOrNull()?.takeIf { it in 1..MARKET_SHOPPING_MAX_UNITS }
                                if (quantityDraft != units.toString()) actionButton(
                                    text = authUiText("Apply quantity", "Применить количество", "Санды қолдану", "Санды колдонуу"),
                                    enabled = enteredUnits != null && !blocked, autoLoading = false, confirmationRequired = false,
                                    onClick = { quantityDraft.toIntOrNull()?.let { applyQuantity(it) } })
                                if (draftPending()) Text(eventMessage("market.comparison_quantity_pending").visibleLocalizedString(stateValues.appLanguage, ""),
                                    color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    actionButton(modifier = Modifier.semantics { contentDescription = authUiText("Compare fewer units", "Сравнить меньшее количество", "Аз санды салыстыру", "Азыраак бирдикти салыштыруу") },
                                        text = "", icon = { Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) { Text("−", color = stateValues.AccentTextColor, fontSize = stateValues.textSize, fontWeight = FontWeight.Bold) } }, iconContentDescription = authUiText("Decrease quantity", "Уменьшить количество", "Санды азайту", "Санды азайтуу"),autoLoading = false, confirmationRequired = false,
                                        enabled = units > 1 && !blocked && !draftPending(),
                                        onClick = { if (units == target.units && !draftPending()) applyQuantity(units - 1) })
                                    actionButton(modifier = Modifier.semantics { contentDescription = authUiText("Compare more units", "Сравнить большее количество", "Көп санды салыстыру", "Көбүрөөк бирдикти салыштыруу") },
                                        text = "", icon = { Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) { Text("+", color = stateValues.AccentTextColor, fontSize = stateValues.textSize, fontWeight = FontWeight.Bold) } }, iconContentDescription = authUiText("Increase quantity", "Увеличить количество", "Санды көбейту", "Санды көбөйтүү"),autoLoading = false, confirmationRequired = false,
                                        enabled = units < MARKET_SHOPPING_MAX_UNITS && !blocked && !draftPending(),
                                        onClick = { if (units == target.units && !draftPending()) applyQuantity(units + 1) })
                                }
                            }
                            aitaFormTextField(Modifier.fillMaxWidth(), city, {
                                val value = it.take(100)
                                if (data.active && owner?.isCurrent() == true && review == null && city != value) {
                                    city = value; pages = 1; data.page = null; data.error = null; queueRefresh()
                                }
                            },
                                authUiText("City · blank means all cities", "Город · пусто — все города", "Қала · бос болса — барлық қала", "Шаар · бош болсо бардык шаарлар"),
                                identityKey = "market-compare-city:$account:${selection.offerId}", autoFocus = false, parentOwnsValue = true)
                        }
                        page?.reference?.let { reference ->
                            Text(authUiText("Your selection", "Ваш выбор", "Сіздің таңдауыңыз", "Тандооңуз"), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                            Text("${reference.line.title}\n${reference.line.shopName}", color = stateValues.TextColor, fontSize = stateValues.textSize)
                            Text(reference.subtotalMinor?.let { marketMoneyLabel(it, target.basis.currencyCode) }
                                ?: authUiText("Original estimate unavailable · selection kept", "Исходный расчёт недоступен · выбор сохранён", "Бастапқы есеп қолжетімсіз · таңдау сақталды", "Баштапкы болжолдуу сумма жеткиликсиз · тандоо сакталды"),
                                color = stateValues.AccentColor, fontSize = stateValues.textSize, fontWeight = FontWeight.Bold)
                        }
                        if (listChanged) Text(authUiText("Your list changed. Close this comparison and open it from the current line.",
                            "Список изменился. Закройте сравнение и откройте его из актуальной строки.",
                            "Тізім өзгерді. Салыстыруды жауып, ағымдағы жолдан ашыңыз.", "Тизмеңиз өзгөрдү. Бул салыштырууну жаап, аны учурдагы саптан кайра ачыңыз."), color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
                        data.error?.let { Text(it.visibleLocalizedString(stateValues.appLanguage, ""), color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize) }
                        MarketShoppingFeedback(shopping)
                    }
                }
                if (selected != null) item(key = "review") {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(stateValues.cornerRadius))
                        .background(stateValues.AccentColor.copy(alpha = 0.07f)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(authUiText("Replace with", "Заменить на", "Мынаған ауыстыру", "Муну менен алмаштыруу"), color = stateValues.TextColor, fontWeight = FontWeight.Bold, fontSize = stateValues.accentTextSize)
                        Text("${selected.line.title}\n${selected.line.shopName}", color = stateValues.TextColor, fontSize = stateValues.textSize)
                        selected.offer?.storefront?.let { shop -> Text("${shop.city}\n${shop.publicAddress}", color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize) }
                        Text(selected.subtotalMinor?.let { marketMoneyLabel(it, target.basis.currencyCode) }.orEmpty(), color = stateValues.AccentColor, fontWeight = FontWeight.Bold, fontSize = stateValues.titleTextSize)
                        Text(authUiText("Only this line changes shops. Its quantity and selling unit stay the same. Other lines are not merged. This is not an order, reservation or payment; transport and fees are not included.",
                            "Только эта строка меняет магазин. Количество и единица продажи сохраняются. Другие строки не объединяются. Это не заказ, резерв или оплата; доставка и сборы не включены.",
                            "Тек осы жолдың дүкені өзгереді. Саны мен сату бірлігі сақталады. Басқа жолдар біріктірілмейді. Бұл тапсырыс, резерв не төлем емес; жеткізу мен алымдар кірмейді.", "Ушул сап гана дүкөндү алмаштырат. Анын саны жана сатуу бирдиги ошол бойдон калат. Башка саптар бириктирилбейт. Бул тапшырык, резерв же төлөм эмес; ташуу жана кызмат акылары кошулган жок."),
                            color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                        if (!reviewUnchanged && submittedId == null && !shoppingBlocked() && data.error == null) Text(eventMessage("market.comparison_review_stale")
                            .visibleLocalizedString(stateValues.appLanguage, ""), color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
                        actionButton(text = authUiText("Replace this list line", "Заменить строку списка", "Тізім жолын ауыстыру", "Тизменин бул сабын алмаштыруу"), autoLoading = false, confirmationRequired = false,
                            loading = shopping.changing, enabled = reviewUnchanged && submittedId == null && shopping.canChange &&
                                shopping.snapshot?.let { target.reviewedReplacement(it, selected, "review") } != null,
                            onClick = {
                                // Read the exact frozen window/quantity again at click time, not just enabled.
                                if (frozen != null && review === frozen && submittedId == null && reviewIsCurrent(frozen))
                                    submittedId = shopping.replace(frozen.page.request.selection, frozen.candidate)
                                else if (data.active && owner?.isCurrent() == true && submittedId == null && !shoppingBlocked())
                                    data.error = eventMessage("market.comparison_review_stale")
                            })
                        actionButton(text = stateValues.stringBack, enabledColor = stateValues.BackgroundColor, textColor = stateValues.TextColor,
                            autoLoading = false, confirmationRequired = false, enabled = !blocked, onClick = {
                                if (data.active && owner?.isCurrent() == true && review === frozen && !shoppingBlocked()) {
                                    review = null; submittedId = null; queueRefresh()
                                }
                            })
                    }
                } else {
                    if (page == null && (data.loading || data.error == null)) item(key = "skeleton") { LoadingSkeleton(layout = LoadingLayout.Comparison, modifier = Modifier.fillMaxWidth(), rows = 5) }
                    else if (comparisonReady && page != null && page.matches.isEmpty()) item(key = "no-matches") {
                        Text(if (!page.moreCandidates) authUiText("No other matching published offers in this search.", "Других подходящих опубликованных предложений в этом поиске нет.", "Осы іздеуде басқа сәйкес жарияланған ұсыныстар жоқ.", "Бул издөөдө башка дал келген жарыяланган сунуштар жок.")
                            else eventMessage("market.comparison_window_empty_more").visibleLocalizedString(stateValues.appLanguage, ""),
                            color = stateValues.PlaceholderTextColor, fontSize = stateValues.textSize)
                    }
                    items(page?.matches.orEmpty(), key = { it.line.offerId }) { candidate ->
                        MarketComparisonCard(candidate, page?.reference?.subtotalMinor, target, shopping, comparisonReady,
                            onReview = {
                                if (comparisonIsCurrent(page, presentation) && data.page?.matches?.contains(candidate) == true && shopping.canChange &&
                                    shopping.snapshot?.let { target.reviewedReplacement(it, candidate, "review") } != null) {
                                    val stamp = data.loadedStamp; val started = data.startedAt
                                    if (page != null && stamp != null && started != null) {
                                        review = FrozenComparisonReview(page, candidate, stamp, started); data.select()
                                    }
                                } else requireFreshComparison()
                            }, onAdd = {
                                if (comparisonIsCurrent(page, presentation) && data.page?.matches?.contains(candidate) == true && target.shoppingRevision == null) {
                                    val offer = candidate.offer
                                    if (offer != null) shopping.add(offer.id, target.units, target.basis)
                                } else requireFreshComparison()
                            }, onVisitShop = {
                                if (comparisonIsCurrent(page, presentation) && data.page?.matches?.contains(candidate) == true) {
                                    candidate.offer?.storefront?.let { shop ->
                                        data.active = false; data.invalidate(); latestVisitShop(shop)
                                    }
                                } else requireFreshComparison()
                            })
                    }
                    if (page?.moreCandidates == true) item(key = "more") {
                        if (pages < MARKET_COMPARISON_MAX_PAGES) actionButton(text = authUiText("Search more offers", "Найти ещё предложения", "Тағы ұсыныстарды іздеу", "Дагы сунуштарды издөө"),
                            autoLoading = false, confirmationRequired = false, enabled = comparisonReady && !blocked, onClick = {
                                if (comparisonIsCurrent(page, presentation) && data.page?.moreCandidates == true && pages < MARKET_COMPARISON_MAX_PAGES) {
                                    pages++; queueRefresh()
                                } else requireFreshComparison()
                            })
                        else Text(authUiText("Search window reached: 400 candidates. Set a city to narrow the search. Unseen offers are not ranked.",
                            "Достигнут предел: 400 кандидатов. Укажите город для уточнения поиска. Непросмотренные предложения не ранжируются.",
                            "Іздеу шегі: 400 үміткер. Іздеуді тарылту үшін қаланы көрсетіңіз. Көрсетілмеген ұсыныстар реттелмейді.", "Издөө чегине жетти: 400 вариант. Издөөнү тарытуу үчүн шаарды коюңуз. Көрүнө элек сунуштар иреттелбейт."),
                            color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                    }
                }
                item(key = "limits") {
                    Text(authUiText("Requested-quantity estimates, lowest loaded estimate first. Prices, publication and stock can change; nothing is reserved. No delivery costs or travel distance are compared.",
                        "Расчёт на выбранное количество, сначала меньшая из загруженных сумм. Цены, публикация и остатки могут измениться; резерва нет. Стоимость доставки и расстояние не сравниваются.",
                        "Таңдалған санға есеп: жүктелген ең төмен сома алдымен. Баға, жарияланым мен қор өзгеруі мүмкін; резерв жоқ. Жеткізу құны мен қашықтық салыстырылмайды.", "Суралган сан үчүн болжолдуу суммалар; жүктөлгөндөрдүн эң төмөнкүсү биринчи. Баалар, жарыянын абалы жана товар калдыгы өзгөрүшү мүмкүн; эч нерсе резервге коюлбайт. Жеткирүү чыгымдары же жол аралыгы салыштырылбайт."),
                        color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Text(if (!displayedReady) authUiText("Refresh needed", "Нужно обновить", "Жаңарту қажет", "Жаңыртуу керек")
                        else eventMessage("market.comparison_window_scope", "checked" to (page?.candidatesChecked ?: 0).toString(),
                            "matches" to (page?.matches?.size ?: 0).toString()).visibleLocalizedString(stateValues.appLanguage, ""),
                        color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                    page?.checkedAtMillis?.let { Text(receiptUiDateTime(it), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize) }
                }
                actionButton(text = authUiText("Refresh", "Обновить", "Жаңарту", "Жаңыртуу"),autoLoading = false,
                    enabled = !data.loading && !blocked, loading = data.loading, confirmationRequired = false, onClick = {
                        if (data.active && owner?.isCurrent() == true && !shoppingBlocked()) {
                            review = null; submittedId = null; queueRefresh()
                        }
                    })
                actionButton(text = authUiText("Close", "Закрыть", "Жабу", "Жабуу"),                    enabledColor = stateValues.BackgroundColor, textColor = stateValues.TextColor, autoLoading = false, confirmationRequired = false, onClick = ::closeComparison)
            }
        }
    }
}

@Composable
private fun AppConfiguration.MarketComparisonCard(row: MarketShoppingQuotedLine, originalMinor: Long?, selection: MarketComparisonSelection,
    shopping: MarketShoppingUiState, fresh: Boolean, onReview: () -> Unit, onAdd: () -> Unit, onVisitShop: () -> Unit) {
    val offer = row.offer ?: return
    val inList = shopping.contains(offer.id)
    Column(Modifier.fillMaxWidth().border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor.copy(alpha = 0.25f),
        RoundedCornerShape(stateValues.cornerRadius)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(offer.title, color = stateValues.TextColor, fontSize = stateValues.accentTextSize, fontWeight = FontWeight.Bold)
        Text("${offer.storefront.displayName} · ${offer.storefront.city}", color = stateValues.TextColor, fontSize = stateValues.textSize)
        Text(row.subtotalMinor?.let { marketMoneyLabel(it, selection.basis.currencyCode) }
            ?: authUiText("Confirm this quantity and price", "Уточните количество и цену", "Сан мен бағаны нақтылаңыз", "Бул санды жана бааны ырастатуу"),
            color = changedValueColor(row.subtotalMinor, "compare:${row.line.offerId}:${selection.units}", stateValues.AccentColor),
            fontSize = stateValues.accentTextSize, fontWeight = FontWeight.Bold)
        val targetMinor = row.subtotalMinor
        if (originalMinor != null && targetMinor != null && row.status == MARKET_QUOTE_ESTIMATED) {
            val difference = originalMinor - targetMinor
            Text(when {
                difference > 0L -> authUiText("Items estimate is ${marketMoneyLabel(difference, selection.basis.currencyCode)} lower", "Расчёт товаров ниже на ${marketMoneyLabel(difference, selection.basis.currencyCode)}", "Тауар есебі ${marketMoneyLabel(difference, selection.basis.currencyCode)} төмен", "Товарлардын болжолдуу суммасы ${marketMoneyLabel(difference, selection.basis.currencyCode)} арзан")
                difference < 0L -> authUiText("Items estimate is ${marketMoneyLabel(-difference, selection.basis.currencyCode)} higher", "Расчёт товаров выше на ${marketMoneyLabel(-difference, selection.basis.currencyCode)}", "Тауар есебі ${marketMoneyLabel(-difference, selection.basis.currencyCode)} жоғары", "Товарлардын болжолдуу суммасы ${marketMoneyLabel(-difference, selection.basis.currencyCode)} кымбат")
                else -> authUiText("Same items estimate", "Та же сумма товаров", "Тауар сомасы бірдей", "Товарлардын болжолдуу суммасы бирдей")
            }, color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
        }
        if (selection.shoppingRevision != null) actionButton(text = if (inList) authUiText("Already in your list", "Уже в списке", "Тізімде бар", "Тизмеңизде бар")
            else authUiText("Review replacement", "Проверить замену", "Ауыстыруды тексеру", "Алмаштырууну кароо"),
            enabled = fresh && !inList && shopping.canChange && row.status == MARKET_QUOTE_ESTIMATED && row.subtotalMinor != null,
            autoLoading = false, confirmationRequired = false, onClick = onReview)
        else actionButton(text = if (inList) authUiText("In your list", "В вашем списке", "Сіздің тізіміңізде", "Тизмеңизде") else authUiText("Add this quantity to list", "Добавить это количество в список", "Осы санды тізімге қосу", "Бул санды тизмеге кошуу"),
            enabled = fresh && !inList && shopping.canChange, autoLoading = false, confirmationRequired = false,
            onClick = onAdd)
        actionButton(text = authUiText("Visit shop", "Открыть магазин", "Дүкенге өту", "Дүкөнгө өтүү"), enabled = fresh,
            enabledColor = stateValues.BackgroundColor, textColor = stateValues.TextColor, autoLoading = false, confirmationRequired = false,
            onClick = onVisitShop)
    }
}

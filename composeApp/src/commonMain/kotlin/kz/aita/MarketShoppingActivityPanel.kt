package kz.aita

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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

private class ShoppingActivityReadLifetime { var active = true }

private fun AppConfiguration.activityKind(entry: MarketShoppingActivityEntry): String = when (entry.kind) {
    MARKET_ACTIVITY_BASKET -> authUiText("Basket shop changes", "Замены магазинов в корзине", "Себеттегі дүкен ауыстырулары", "Себеттеги дүкөн өзгөртүүлөрү")
    MARKET_ACTIVITY_REPLACE -> authUiText("Offer replacement", "Замена предложения", "Ұсынысты ауыстыру", "Сунушту алмаштыруу")
    MARKET_ACTIVITY_REMOVE -> authUiText("Remove from list", "Удаление из списка", "Тізімнен жою", "Тизмеден алып салуу")
    else -> authUiText("Set list quantity", "Количество в списке", "Тізімдегі сан", "Тизмедеги санды коюу")
}

private fun AppConfiguration.activityStatus(entry: MarketShoppingActivityEntry): String = when {
    entry.errorKey == "market.shopping_cancelled" -> authUiText("Cancelled", "Отменено", "Бас тартылды", "Жокко чыгарылды")
    !entry.accepted -> authUiText("Not applied", "Не применено", "Қолданылған жоқ", "Колдонулган жок")
    !entry.changed -> authUiText("Already matched · no list edit", "Уже совпадало · список не изменён", "Бұрыннан сәйкес · тізім өзгермеді", "Буга чейин дал келген · тизме өзгөртүлгөн жок")
    else -> authUiText("Applied to list", "Список изменён", "Тізім өзгертілді", "Тизмеге колдонулду")
}

/** Kept above the Items/By shop/Activity branch. No custom objects enter Android saved state. */
@Stable
internal class MarketShoppingActivityNavigation {
    val reads = MarketShoppingActivityReadFence()
    var selection by mutableStateOf(Any())
        private set
    private var selectedRequest by mutableStateOf(MarketShoppingActivitySearchRequest())
    var request: MarketShoppingActivitySearchRequest
        get() = selectedRequest
        set(value) {
            if (selectedRequest != value) { reads.invalidate(); selection = Any(); selectedRequest = value }
        }
    var reference by mutableStateOf("")
    var referenceError by mutableStateOf(false)
    val scroll = LazyListState()
    var positionedRequest = request
}

@Stable
private class ShoppingActivityPageRead {
    var active = true
    var page by mutableStateOf<MarketShoppingActivitySearchPage?>(null)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<List<LocalizedStringDataModel>?>(null)
    var notice by mutableStateOf<List<LocalizedStringDataModel>?>(null)
    var loadedSignal by mutableStateOf(-1L)
    var stamp by mutableStateOf<MarketShoppingActivityReadFence.Stamp?>(null)

    fun retire() { stamp = null }
}

/** History is read-only, including an exact reference search. Finding an entry never retires the
 * local pending command: Check result still verifies the exact original saved payload.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppConfiguration.MarketShoppingActivityPanel(
    navigation: MarketShoppingActivityNavigation,
    modifier: Modifier = Modifier
) {
    val account = stateValues.userAccount?.id
    val generation = currentAuthenticatedSessionGeneration()
    val owner = remember(account, generation) { captureMarketRequestScope() }
    val wanted = navigation.request
    val selection = navigation.selection
    // A filter/page change gets an empty, independent result cell. Old rows are never relabelled
    // with new filters while their replacement loads. A disposed read cannot publish into it.
    val data = remember(account, generation, wanted, selection) { ShoppingActivityPageRead() }
    val requests = remember(data) { Channel<Unit>(Channel.CONFLATED) }
    var opened by remember(account, generation) { mutableStateOf<MarketShoppingActivityEntry?>(null) }
    val signal by MarketplaceSignals.revision.collectAsState()
    val detailOpen by rememberUpdatedState(opened != null)
    val lastRead = remember(account, generation) { mutableStateOf<kotlin.time.TimeMark?>(null) }
    val scroll = navigation.scroll
    fun controlsCurrent(): Boolean = data.active && owner?.isCurrent() == true &&
        navigation.selection === selection && navigation.request == wanted
    fun refreshPage() {
        if (!controlsCurrent()) return
        navigation.reads.invalidate()
        data.retire()
        requests.trySend(Unit)
    }
    fun canUsePage(displayed: MarketShoppingActivitySearchPage?): Boolean =
        controlsCurrent() && displayed != null && data.page === displayed &&
            navigation.reads.canUse(data.stamp, displayed, navigation.request,
                MarketplaceSignals.revision.value, data.loading || data.error != null || opened != null)
    fun stalePage() {
        if (controlsCurrent()) {
            data.notice = eventMessage("market.activity_page_changed")
            if (opened == null) refreshPage()
        }
    }
    DisposableEffect(data) { onDispose { data.active = false; requests.close() } }
    LaunchedEffect(wanted) {
        if (navigation.positionedRequest != wanted) { scroll.scrollToItem(0); navigation.positionedRequest = wanted }
    }
    LaunchedEffect(data, signal, opened?.commandId) {
        // Do not jump someone reading older immutable records to a new head page on every event.
        if (opened == null && (data.page == null || wanted.boundary == null)) refreshPage()
    }
    LaunchedEffect(data) {
        val owned = owner
        if (owned == null) { data.error = eventMessage("market.shopping_denied"); return@LaunchedEffect }
        if (wanted.normalizedActivitySearch() == null) {
            data.error = eventMessage("market.activity_search_invalid"); return@LaunchedEffect
        }
        for (ignored in requests) {
            delay(200)
            val wait = lastRead.value?.let { (2_000 - it.elapsedNow().inWholeMilliseconds).coerceAtLeast(0) } ?: 0
            if (wait > 0) delay(wait)
            while (requests.tryReceive().isSuccess) { /* arrivals during I/O retain a trailing read */ }
            if (!data.active || !owned.isCurrent()) break
            if (detailOpen) continue
            if (navigation.request != wanted) continue
            val atSignal = MarketplaceSignals.revision.value
            val stamp = navigation.reads.capture(wanted, atSignal)
            fun stillOwnsRead() = data.active && owned.isCurrent() &&
                navigation.selection === selection &&
                navigation.reads.isCurrent(stamp, navigation.request, MarketplaceSignals.revision.value)
            lastRead.value = kotlin.time.TimeSource.Monotonic.markNow()
            data.loading = true
            try {
                val response = loadMarketShoppingActivitySearch(owned, wanted)
                if (!data.active || !owned.isCurrent()) break
                if (!stillOwnsRead()) continue
                val result = response.payload
                if (!response.negative && result != null) {
                    data.page = result; data.error = null; data.notice = null; data.loadedSignal = atSignal; data.stamp = stamp
                } else {
                    data.error = response.message ?: eventMessage("market.shopping_activity_failed")
                    if (!response.transportFailure && response.httpStatusCode in setOf(401, 403)) data.page = null
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (stillOwnsRead()) data.error = eventMessage("market.shopping_activity_failed") }
            finally { if (data.active) data.loading = false }
        }
    }
    LaunchedEffect(data) {
        while (isActive) { delay(30_000); if (wanted.boundary == null && !detailOpen) refreshPage() }
    }
    val page = data.page
    val canNavigate = canUsePage(page)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = scroll, contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "history-controls") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(authUiText("List changes, not purchases. Finding a record does not settle an unconfirmed change; use Check result for that.",
                        "Изменения списка, не покупки. Поиск записи не подтверждает незавершённую команду — для этого используйте «Проверить результат».",
                        "Бұл сатып алулар емес, тізім өзгерістері. Жазбаны табу расталмаған өзгерісті аяқтамайды; ол үшін «Нәтижені тексеру» қолданыңыз.", "Бул сатып алуулар эмес, тизменин өзгөртүүлөрү. Жазууну табуу ырастала элек өзгөртүүнүн абалын чечпейт; бул үчүн «Натыйжаны текшерүүнү» колдонуңуз."),
                        color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                    aitaFormTextField(Modifier.fillMaxWidth(), navigation.reference,
                        { navigation.reference = it.take(64); navigation.referenceError = false },
                        authUiText("Change reference · optional", "Номер изменения · необязательно", "Өзгеріс нөмірі · міндетті емес", "Өзгөртүү шилтемеси · милдеттүү эмес"),
                        identityKey = "activity-reference:$account:$generation", autoFocus = false, parentOwnsValue = true)
                    if (navigation.referenceError) Text(eventMessage("market.activity_reference_invalid")
                        .visibleLocalizedString(stateValues.appLanguage, ""), color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        actionButton(text = authUiText("Find reference", "Найти по номеру", "Нөмір бойынша табу", "Шилтемени табуу"),
                            fillMaxWidthIfTextPresent = false, autoLoading = false, confirmationRequired = false,
                            enabled = navigation.reference.isNotBlank(), onClick = {
                                val reference = normalizedMarketChangeReference(navigation.reference)
                                if (controlsCurrent()) navigation.referenceError = reference == null
                                if (reference != null && controlsCurrent()) {
                                    navigation.reference = reference
                                    val next = MarketShoppingActivitySearchRequest(MarketShoppingActivityFilter(commandId = reference))
                                    navigation.request = next
                                    if (wanted == next) refreshPage()
                                }
                            })
                        if (wanted.filter != MarketShoppingActivityFilter() || navigation.reference.isNotEmpty())
                            actionButton(text = authUiText("Clear filters", "Сбросить фильтры", "Сүзгілерді тазалау", "Чыпкаларды тазалоо"),
                                fillMaxWidthIfTextPresent = false, autoLoading = false, confirmationRequired = false,
                                onClick = {
                                    if (controlsCurrent()) {
                                        navigation.reference = ""; navigation.referenceError = false
                                        navigation.request = MarketShoppingActivitySearchRequest()
                                    }
                                })
                    }
                    if (wanted.filter.commandId == null) {
                        sectionTabsWidget("activity-result:$account", listOf(
                            TabContent("all", authUiText("All results", "Все результаты", "Барлық нәтижелер", "Бардык натыйжалар")),
                            TabContent(MARKET_ACTIVITY_RESULT_APPLIED, authUiText("Applied", "Применено", "Қолданылды", "Колдонулду")),
                            TabContent(MARKET_ACTIVITY_RESULT_REJECTED, authUiText("Not applied", "Не применено", "Қолданылмады", "Колдонулган жок")),
                            TabContent(MARKET_ACTIVITY_RESULT_CANCELLED, authUiText("Cancelled", "Отменённые", "Бас тартылған", "Жокко чыгарылды")),
                            TabContent(MARKET_ACTIVITY_RESULT_UNCHANGED, authUiText("No change", "Без изменений", "Өзгеріс жоқ", "Өзгөртүү жок"))),
                            selectedId = wanted.filter.result ?: "all", onSelected = {
                                val current = navigation.request.filter
                                if (controlsCurrent() && current.commandId == null) navigation.request = MarketShoppingActivitySearchRequest(
                                    current.copy(result = it.takeUnless { key -> key == "all" }))
                            })
                        sectionTabsWidget("activity-kind:$account", listOf(
                            TabContent("all", authUiText("All types", "Все типы", "Барлық түрлер", "Бардык түрлөр")),
                            TabContent(MARKET_ACTIVITY_QUANTITY, authUiText("Quantity", "Количество", "Саны", "Саны")),
                            TabContent(MARKET_ACTIVITY_REMOVE, authUiText("Removal", "Удаление", "Жою", "Алып салуу")),
                            TabContent(MARKET_ACTIVITY_REPLACE, authUiText("Replacement", "Замена", "Ауыстыру", "Алмаштыруу")),
                            TabContent(MARKET_ACTIVITY_BASKET, authUiText("Basket", "Корзина", "Себет", "Себет"))),
                            selectedId = wanted.filter.kind ?: "all", onSelected = {
                                val current = navigation.request.filter
                                if (controlsCurrent() && current.commandId == null) navigation.request = MarketShoppingActivitySearchRequest(
                                    current.copy(kind = it.takeUnless { key -> key == "all" }))
                            })
                    } else Text(authUiText("Exact reference search · type and result filters are cleared.",
                        "Поиск по точному номеру · фильтры типа и результата сброшены.",
                        "Нақты нөмір бойынша іздеу · түр мен нәтиже сүзгілері тазартылды.", "Так шилтеме боюнча издөө · түр жана натыйжа чыпкалары тазаланды."),
                        color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                    if (wanted.boundary != null && data.loadedSignal != signal) Text(authUiText(
                        "Activity may have changed. Your older page stays open; use Latest to see the head again.",
                        "История могла измениться. Старая страница остаётся открытой; нажмите «Последние», чтобы увидеть начало.",
                        "Тарих өзгеруі мүмкін. Ескі бет ашық қалады; басын көру үшін «Соңғылары» түймесін басыңыз.", "Аракеттер өзгөргөн болушу мүмкүн. Эски бетиңиз ачык бойдон калат; башына кайра өтүү үчүн «Акыркыларды» колдонуңуз."),
                        color = stateValues.AccentColor, fontSize = stateValues.smallTextSize)
                }
            }
            if (page == null && (data.loading || data.error == null)) item { LoadingSkeleton(Modifier.fillMaxWidth(), layout = LoadingLayout.Activity, rows = 4) }
            data.error?.let { message -> item(key = "history-error") {
                Text(message.visibleLocalizedString(stateValues.appLanguage, ""), color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
            } }
            data.notice?.let { message -> item(key = "history-notice") {
                Text(message.visibleLocalizedString(stateValues.appLanguage, ""), color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize)
            } }
            if (canNavigate && page?.entries?.isEmpty() == true) item(key = "history-empty") {
                Text(if (wanted.filter.commandId != null) authUiText("No recorded result for this reference in your account. This does not prove a pending request failed.",
                    "В вашем аккаунте нет записанного результата с этим номером. Это не доказывает отказ по ожидающей команде.",
                    "Аккаунтыңызда осы нөмірдің жазылған нәтижесі жоқ. Бұл күтілген пәрменнің орындалмағанын дәлелдемейді.", "Аккаунтуңузда бул шилтеме боюнча катталган натыйжа жок. Бул күтүүдөгү суроо-талап аткарылбай калганын далилдебейт.")
                else authUiText("No changes match this page and these filters", "На этой странице нет изменений по выбранным фильтрам", "Бұл бетте таңдалған сүзгілерге сәйкес өзгерістер жоқ", "Бул бетке жана чыпкаларга дал келген өзгөртүү жок"),
                    color = stateValues.TextColor, fontSize = stateValues.textSize)
            }
            items(page?.entries.orEmpty(), key = { it.commandId }) { entry ->
                Column(Modifier.fillMaxWidth().border(stateValues.unfocusedBorderWidth,
                    stateValues.PlaceholderTextColor.copy(alpha = 0.25f), RoundedCornerShape(stateValues.cornerRadius))
                    .padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(activityKind(entry), color = stateValues.TextColor, fontSize = stateValues.textSize, fontWeight = FontWeight.Bold)
                    Text(activityStatus(entry), color = when { entry.accepted -> stateValues.AccentColor; entry.errorKey == "market.shopping_cancelled" -> stateValues.PlaceholderTextColor; else -> stateValues.ErrorColor },
                        fontSize = stateValues.smallTextSize)
                    Text(receiptUiDateTime(entry.recordedAtMillis), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                    entry.previewTitle?.let { title -> Text(title, color = stateValues.TextColor, fontSize = stateValues.textSize) }
                    if (entry.kind == MARKET_ACTIVITY_BASKET) Text(if (entry.accepted) authUiText("${entry.changedLines} shop changes · ${entry.reviewedLines} reviewed lines",
                        "Замен магазинов: ${entry.changedLines} · проверено строк: ${entry.reviewedLines}",
                        "${entry.changedLines} дүкен ауыстыруы · ${entry.reviewedLines} тексерілген жол", "Дүкөн боюнча ${entry.changedLines} өзгөртүү · ${entry.reviewedLines} сап каралды")
                    else authUiText("${entry.changedLines} proposed shop changes · none applied",
                        "Предлагалось замен: ${entry.changedLines} · ни одна не применена",
                        "${entry.changedLines} ауыстыру ұсынылды · ешқайсысы қолданылмады", "Дүкөн боюнча ${entry.changedLines} өзгөртүү сунушталды · эч бири колдонулган жок"),
                        color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                    actionButton(text = authUiText("View result", "Посмотреть результат", "Нәтижені көру", "Натыйжаны көрүү"), fillMaxWidthIfTextPresent = false,
                        enabled = canNavigate, confirmationRequired = false, autoLoading = false, onClick = {
                            if (canUsePage(page) && page?.entries?.contains(entry) == true) opened = entry
                            else stalePage()
                        })
                }
            }
            item(key = "history-navigation") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (wanted.boundary != null) actionButton(text = authUiText("Latest", "Последние", "Соңғылары", "Акыркылар"),
                        enabled = !data.loading, autoLoading = false, confirmationRequired = false, fillMaxWidthIfTextPresent = false,
                        onClick = {
                            if (controlsCurrent())
                                navigation.request = MarketShoppingActivitySearchRequest(navigation.request.filter)
                        })
                    page?.newerActivityRequest()?.let { next -> actionButton(text = authUiText("Newer", "Более новые", "Жаңарақ", "Жаңыраак"),
                        enabled = canNavigate, autoLoading = false, confirmationRequired = false, fillMaxWidthIfTextPresent = false,
                        onClick = {
                            if (canUsePage(page)) navigation.request = next else stalePage()
                        }) }
                    page?.olderActivityRequest()?.let { next -> actionButton(text = authUiText("Older", "Более ранние", "Бұрынғы", "Эскирээк"),
                        enabled = canNavigate, autoLoading = false, confirmationRequired = false, fillMaxWidthIfTextPresent = false,
                        onClick = {
                            if (canUsePage(page)) navigation.request = next else stalePage()
                        }) }
                }
                if (page != null && wanted.filter.commandId == null) Text(authUiText(
                    "${page.entries.size} changes on this page · older history remains available, 20 at a time.",
                    "На странице: ${page.entries.size} изменений · старая история доступна по 20 записей.",
                    "Бұл бетте ${page.entries.size} өзгеріс · ескі тарих бір бетте 20 жазбадан қолжетімді.", "Бул бетте ${page.entries.size} өзгөртүү · эски тарых 20дан көрсөтүлүп, жеткиликтүү бойдон калат."),
                    color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
            }
        }
        FlowRow(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (page != null && canNavigate && data.loadedSignal == signal)
                authUiText("Updated", "Обновлено", "Жаңартылды", "Жаңыртылды") + " · " + receiptUiDateTime(page.checkedAtMillis)
            else authUiText("History · refresh needed", "История · требуется обновление", "Тарих · жаңарту қажет", "Тарых · жаңыртуу керек"),
                color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
            actionButton(text = authUiText("Refresh page", "Обновить страницу", "Бетті жаңарту", "Бетти жаңыртуу"), enabled = !data.loading,
                loading = data.loading, autoLoading = false, confirmationRequired = false, fillMaxWidthIfTextPresent = false,
                onClick = { refreshPage() })
        }
    }
    opened?.let { entry -> MarketShoppingActivityDialog(entry, onDismiss = { opened = null }) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppConfiguration.MarketShoppingActivityDialog(entry: MarketShoppingActivityEntry, onDismiss: () -> Unit) {
    val account = stateValues.userAccount?.id
    val generation = currentAuthenticatedSessionGeneration()
    val owner = remember(account, generation) { captureMarketRequestScope() }
    var detail by remember(account, generation, entry) { mutableStateOf<MarketShoppingActivityEntry?>(null) }
    var loading by remember(account, generation, entry) { mutableStateOf(false) }
    var error by remember(account, generation, entry) { mutableStateOf<List<LocalizedStringDataModel>?>(null) }
    var attempt by remember(account, generation, entry) { mutableStateOf(0) }
    val detailLifetime = remember(account, generation, entry, attempt) { ShoppingActivityReadLifetime() }
    DisposableEffect(detailLifetime) { onDispose { detailLifetime.active = false } }
    LaunchedEffect(detailLifetime) {
        val owned = owner ?: return@LaunchedEffect
        if (!entry.detailsRecorded || !owned.isCurrent()) return@LaunchedEffect
        loading = true
        try {
            val response = loadMarketShoppingActivityDetail(owned, entry)
            if (detailLifetime.active && owned.isCurrent()) {
                detail = response.payload.takeUnless { response.negative }
                error = if (response.negative) response.message ?: eventMessage("market.shopping_activity_failed") else null
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { if (detailLifetime.active && owned.isCurrent()) error = eventMessage("market.shopping_activity_failed") }
        finally { if (detailLifetime.active) loading = false }
    }
    val scroll = rememberLazyListState()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth().aitaWidthCap(900.dp).fillMaxHeight(0.90f).padding(12.dp)
            .clip(RoundedCornerShape(stateValues.cornerRadius)).background(stateValues.BackgroundColor)) {
            Text(activityKind(entry), Modifier.padding(16.dp), color = stateValues.TextColor,
                fontSize = stateValues.accentTextSize, fontWeight = FontWeight.Bold)
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = scroll, contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(activityStatus(entry), color = when { entry.accepted -> stateValues.AccentColor; entry.errorKey == "market.shopping_cancelled" -> stateValues.PlaceholderTextColor; else -> stateValues.ErrorColor },
                            fontSize = stateValues.textSize, fontWeight = FontWeight.Bold)
                        Text(receiptUiDateTime(entry.recordedAtMillis), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                        entry.errorKey?.let { key ->
                            Text(authUiText("Recorded response", "Записанный ответ", "Жазылған жауап", "Катталган жооп"),
                                color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                            Text(eventMessage(key).visibleLocalizedString(stateValues.appLanguage, key),
                                color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
                        }
                        val subtotal = entry.reviewedSubtotalMinor; val currency = entry.reviewedCurrency
                        if (subtotal != null && currency != null) Text(authUiText("Historical reviewed items estimate", "Исторический расчёт при проверке", "Тексеру кезіндегі бұрынғы тауар есебі", "Мурун каралган товарлардын болжолдуу суммасы") +
                            " · " + marketMoneyLabel(subtotal, currency), color = stateValues.TextColor, fontSize = stateValues.textSize)
                        Text(authUiText("This record is not a receipt or a current price. Viewing it does not apply or undo anything.",
                            "Это не чек и не текущая цена. Просмотр ничего не применяет и не отменяет.",
                            "Бұл чек те, ағымдағы баға да емес. Көру еш өзгерісті қолданбайды не болдырмайды.", "Бул жазуу чек же учурдагы баа эмес. Аны көрүү эч нерсени колдонбойт жана артка кайтарбайт."),
                            color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                    }
                }
                if (loading) item { LoadingSkeleton(Modifier.fillMaxWidth(), layout = LoadingLayout.Activity, rows = 3) }
                error?.let { message -> item {
                    Text(message.visibleLocalizedString(stateValues.appLanguage, ""), color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
                    actionButton(text = authUiText("Load details again", "Загрузить данные снова", "Деректерді қайта жүктеу", "Чоо-жайын кайра жүктөө"),
                        enabled = !loading, autoLoading = false, confirmationRequired = false, onClick = {
                            if (detailLifetime.active && owner?.isCurrent() == true && !loading) {
                                detailLifetime.active = false; loading = true; attempt++
                            }
                        })
                } }
                if (!entry.detailsRecorded) item {
                    Text(if (entry.accepted) authUiText("Older record: product and shop labels were not captured. Today's catalogue has not been substituted for past details.",
                        "Старая запись: названия товаров и магазинов не сохранялись. Сегодняшний каталог не подставлен вместо прошлых данных.",
                        "Ескі жазба: тауар мен дүкен атаулары сақталмаған. Бұрынғы деректер орнына бүгінгі каталог қойылған жоқ.", "Эски жазуу: товар жана дүкөн аталыштары катталган эмес. Өткөн чоо-жайдын ордуна учурдагы каталог колдонулган жок.")
                    else authUiText("This command did not apply a list change. Rejected proposals are not shown as completed replacements.",
                        "Эта команда не изменила список. Отклонённые предложения не показаны как выполненные замены.",
                        "Бұл команда тізімді өзгертпеді. Қабылданбаған ұсыныстар орындалған ауыстыру ретінде көрсетілмейді.", "Бул буйрук тизмеге өзгөртүү киргизген жок. Четке кагылган сунуштар аяктаган алмаштыруулар катары көрсөтүлбөйт."),
                        color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                }
                items(detail?.details?.lines.orEmpty()) { change ->
                    Column(Modifier.fillMaxWidth().border(stateValues.unfocusedBorderWidth,
                        stateValues.PlaceholderTextColor.copy(alpha = 0.25f), RoundedCornerShape(stateValues.cornerRadius))
                        .padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        change.before?.let { ActivitySelection(authUiText("Before", "Было", "Бұрын", "Мурун"), it) }
                        change.after?.let { ActivitySelection(authUiText("After", "Стало", "Кейін", "Кийин"), it) }
                        if (change.after == null) Text(authUiText("Removed from list", "Удалено из списка", "Тізімнен жойылды", "Тизмеден алынды"),
                            color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                    }
                }
                item {
                    Text(authUiText("Change reference", "Номер изменения", "Өзгеріс нөмірі", "Өзгөртүү шилтемеси"), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                    SelectionContainer { Text(entry.commandId, color = stateValues.TextColor, fontSize = stateValues.smallTextSize) }
                    ClipboardCopyButton(textToCopy = entry.commandId)
                    entry.appliedRevision?.let { Text(authUiText("List revision", "Версия списка", "Тізім нұсқасы", "Тизменин редакциясы") + " · $it",
                        color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize) }
                }
            }
            actionButton(modifier = Modifier.padding(12.dp), text = authUiText("Close", "Закрыть", "Жабу", "Жабуу"),
                autoLoading = false, confirmationRequired = false, onClick = onDismiss)
        }
    }
}

@Composable
private fun AppConfiguration.ActivitySelection(label: String, line: MarketShoppingLine) {
    val unit = line.unitName.visibleLocalizedString(stateValues.appLanguage, authUiText("unit", "ед.", "бірл.", "бирдик"))
    Text(label, color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
    SelectionContainer { Text("${line.title} · ${line.shopName}", color = stateValues.TextColor, fontSize = stateValues.textSize) }
    Text("${line.units} × ${line.basis.pricedAmount.toString().removeSuffix(".0")} $unit", color = stateValues.TextColor, fontSize = stateValues.smallTextSize)
}

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
    MARKET_ACTIVITY_BASKET -> authUiText("Basket shop changes", "Замены магазинов в корзине", "Себеттегі дүкен ауыстырулары")
    MARKET_ACTIVITY_REPLACE -> authUiText("Offer replacement", "Замена предложения", "Ұсынысты ауыстыру")
    MARKET_ACTIVITY_REMOVE -> authUiText("Remove from list", "Удаление из списка", "Тізімнен жою")
    else -> authUiText("Set list quantity", "Количество в списке", "Тізімдегі сан")
}

private fun AppConfiguration.activityStatus(entry: MarketShoppingActivityEntry): String = when {
    entry.errorKey == "market.shopping_cancelled" -> authUiText("Cancelled", "Отменено", "Бас тартылды")
    !entry.accepted -> authUiText("Not applied", "Не применено", "Қолданылған жоқ")
    !entry.changed -> authUiText("Already matched · no list edit", "Уже совпадало · список не изменён", "Бұрыннан сәйкес · тізім өзгермеді")
    else -> authUiText("Applied to list", "Список изменён", "Тізім өзгертілді")
}

/** Kept above the Items/By shop/Activity branch. No custom objects enter Android saved state. */
@Stable
internal class MarketShoppingActivityNavigation {
    var request by mutableStateOf(MarketShoppingActivitySearchRequest())
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
    var loadedSignal by mutableStateOf(-1L)
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
    // A filter/page change gets an empty, independent result cell. Old rows are never relabelled
    // with new filters while their replacement loads. A disposed read cannot publish into it.
    val data = remember(account, generation, wanted) { ShoppingActivityPageRead() }
    val requests = remember(data) { Channel<Unit>(Channel.CONFLATED) }
    var opened by remember(account, generation) { mutableStateOf<MarketShoppingActivityEntry?>(null) }
    val signal by MarketplaceSignals.revision.collectAsState()
    val latestSignal by rememberUpdatedState(signal)
    val detailOpen by rememberUpdatedState(opened != null)
    val lastRead = remember(account, generation) { mutableStateOf<kotlin.time.TimeMark?>(null) }
    val scroll = navigation.scroll
    DisposableEffect(data) { onDispose { data.active = false; requests.close() } }
    LaunchedEffect(wanted) {
        if (navigation.positionedRequest != wanted) { scroll.scrollToItem(0); navigation.positionedRequest = wanted }
    }
    LaunchedEffect(data, signal, opened?.commandId) {
        // Do not jump someone reading older immutable records to a new head page on every event.
        if (opened == null && (data.page == null || wanted.boundary == null)) requests.trySend(Unit)
    }
    LaunchedEffect(data) {
        val owned = owner
        if (owned == null) { data.error = eventMessage("market.shopping_denied"); return@LaunchedEffect }
        for (ignored in requests) {
            delay(200)
            val wait = lastRead.value?.let { (2_000 - it.elapsedNow().inWholeMilliseconds).coerceAtLeast(0) } ?: 0
            if (wait > 0) delay(wait)
            while (requests.tryReceive().isSuccess) { /* arrivals during I/O retain a trailing read */ }
            if (!data.active || !owned.isCurrent()) break
            if (detailOpen) continue
            val atSignal = latestSignal
            lastRead.value = kotlin.time.TimeSource.Monotonic.markNow()
            data.loading = true
            try {
                val response = loadMarketShoppingActivitySearch(owned, wanted)
                if (!data.active || !owned.isCurrent()) break
                val result = response.payload
                if (!response.negative && result != null) {
                    data.page = result; data.error = null; data.loadedSignal = atSignal
                } else {
                    data.error = response.message ?: eventMessage("market.shopping_activity_failed")
                    if (response.httpStatusCode in setOf(401, 403)) data.page = null
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (data.active && owned.isCurrent()) data.error = eventMessage("market.shopping_activity_failed") }
            finally { if (data.active) data.loading = false }
        }
    }
    LaunchedEffect(data) {
        while (isActive) { delay(30_000); if (wanted.boundary == null && !detailOpen) requests.trySend(Unit) }
    }
    val page = data.page
    val canNavigate = page != null && !data.loading && data.error == null && owner?.isCurrent() == true
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = scroll, contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "history-controls") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(authUiText("List changes, not purchases. Finding a record does not settle an unconfirmed change; use Check result for that.",
                        "Изменения списка, не покупки. Поиск записи не подтверждает незавершённую команду — для этого используйте «Проверить результат».",
                        "Бұл сатып алулар емес, тізім өзгерістері. Жазбаны табу расталмаған өзгерісті аяқтамайды; ол үшін «Нәтижені тексеру» қолданыңыз."),
                        color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                    aitaFormTextField(Modifier.fillMaxWidth(), navigation.reference,
                        { navigation.reference = it.take(64); navigation.referenceError = false },
                        authUiText("Change reference · optional", "Номер изменения · необязательно", "Өзгеріс нөмірі · міндетті емес"),
                        identityKey = "activity-reference:$account:$generation", autoFocus = false, parentOwnsValue = true)
                    if (navigation.referenceError) Text(eventMessage("market.activity_reference_invalid")
                        .visibleLocalizedString(stateValues.appLanguage, ""), color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        actionButton(text = authUiText("Find reference", "Найти по номеру", "Нөмір бойынша табу"),
                            fillMaxWidthIfTextPresent = false, autoLoading = false, confirmationRequired = false,
                            enabled = navigation.reference.isNotBlank(), onClick = {
                                val reference = normalizedMarketChangeReference(navigation.reference)
                                navigation.referenceError = reference == null
                                if (reference != null) {
                                    navigation.reference = reference
                                    val next = MarketShoppingActivitySearchRequest(MarketShoppingActivityFilter(commandId = reference))
                                    navigation.request = next
                                    if (wanted == next) requests.trySend(Unit)
                                }
                            })
                        if (wanted.filter != MarketShoppingActivityFilter() || navigation.reference.isNotEmpty())
                            actionButton(text = authUiText("Clear filters", "Сбросить фильтры", "Сүзгілерді тазалау"),
                                fillMaxWidthIfTextPresent = false, autoLoading = false, confirmationRequired = false,
                                onClick = { navigation.reference = ""; navigation.referenceError = false
                                    navigation.request = MarketShoppingActivitySearchRequest() })
                    }
                    if (wanted.filter.commandId == null) {
                        sectionTabsWidget("activity-result:$account", listOf(
                            TabContent("all", authUiText("All results", "Все результаты", "Барлық нәтижелер")),
                            TabContent(MARKET_ACTIVITY_RESULT_APPLIED, authUiText("Applied", "Применено", "Қолданылды")),
                            TabContent(MARKET_ACTIVITY_RESULT_REJECTED, authUiText("Not applied", "Не применено", "Қолданылмады")),
                            TabContent(MARKET_ACTIVITY_RESULT_CANCELLED, authUiText("Cancelled", "Отменённые", "Бас тартылған")),
                            TabContent(MARKET_ACTIVITY_RESULT_UNCHANGED, authUiText("No change", "Без изменений", "Өзгеріс жоқ"))),
                            selectedId = wanted.filter.result ?: "all", onSelected = {
                                val current = navigation.request.filter
                                if (current.commandId == null) navigation.request = MarketShoppingActivitySearchRequest(
                                    current.copy(result = it.takeUnless { key -> key == "all" }))
                            })
                        sectionTabsWidget("activity-kind:$account", listOf(
                            TabContent("all", authUiText("All types", "Все типы", "Барлық түрлер")),
                            TabContent(MARKET_ACTIVITY_QUANTITY, authUiText("Quantity", "Количество", "Саны")),
                            TabContent(MARKET_ACTIVITY_REMOVE, authUiText("Removal", "Удаление", "Жою")),
                            TabContent(MARKET_ACTIVITY_REPLACE, authUiText("Replacement", "Замена", "Ауыстыру")),
                            TabContent(MARKET_ACTIVITY_BASKET, authUiText("Basket", "Корзина", "Себет"))),
                            selectedId = wanted.filter.kind ?: "all", onSelected = {
                                val current = navigation.request.filter
                                if (current.commandId == null) navigation.request = MarketShoppingActivitySearchRequest(
                                    current.copy(kind = it.takeUnless { key -> key == "all" }))
                            })
                    } else Text(authUiText("Exact reference search · type and result filters are cleared.",
                        "Поиск по точному номеру · фильтры типа и результата сброшены.",
                        "Нақты нөмір бойынша іздеу · түр мен нәтиже сүзгілері тазартылды."),
                        color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                    if (wanted.boundary != null && data.loadedSignal != signal) Text(authUiText(
                        "Activity may have changed. Your older page stays open; use Latest to see the head again.",
                        "История могла измениться. Старая страница остаётся открытой; нажмите «Последние», чтобы увидеть начало.",
                        "Тарих өзгеруі мүмкін. Ескі бет ашық қалады; басын көру үшін «Соңғылары» түймесін басыңыз."),
                        color = stateValues.AccentColor, fontSize = stateValues.smallTextSize)
                }
            }
            if (page == null && (data.loading || data.error == null)) item { LoadingSkeleton(Modifier.fillMaxWidth(), rows = 4) }
            data.error?.let { message -> item(key = "history-error") {
                Text(message.visibleLocalizedString(stateValues.appLanguage, ""), color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
            } }
            if (page != null && page.entries.isEmpty()) item(key = "history-empty") {
                Text(if (wanted.filter.commandId != null) authUiText("No recorded result for this reference in your account. This does not prove a pending request failed.",
                    "В вашем аккаунте нет записанного результата с этим номером. Это не доказывает отказ по ожидающей команде.",
                    "Аккаунтыңызда осы нөмірдің жазылған нәтижесі жоқ. Бұл күтілген пәрменнің орындалмағанын дәлелдемейді.")
                else authUiText("No changes match this page and these filters", "На этой странице нет изменений по выбранным фильтрам", "Бұл бетте таңдалған сүзгілерге сәйкес өзгерістер жоқ"),
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
                        "${entry.changedLines} дүкен ауыстыруы · ${entry.reviewedLines} тексерілген жол")
                    else authUiText("${entry.changedLines} proposed shop changes · none applied",
                        "Предлагалось замен: ${entry.changedLines} · ни одна не применена",
                        "${entry.changedLines} ауыстыру ұсынылды · ешқайсысы қолданылмады"),
                        color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                    actionButton(text = authUiText("View result", "Посмотреть результат", "Нәтижені көру"), fillMaxWidthIfTextPresent = false,
                        confirmationRequired = false, autoLoading = false, onClick = { opened = entry })
                }
            }
            item(key = "history-navigation") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (wanted.boundary != null) actionButton(text = authUiText("Latest", "Последние", "Соңғылары"),
                        enabled = !data.loading, autoLoading = false, confirmationRequired = false, fillMaxWidthIfTextPresent = false,
                        onClick = { navigation.request = MarketShoppingActivitySearchRequest(navigation.request.filter) })
                    page?.newerActivityRequest()?.let { next -> actionButton(text = authUiText("Newer", "Более новые", "Жаңарақ"),
                        enabled = canNavigate, autoLoading = false, confirmationRequired = false, fillMaxWidthIfTextPresent = false,
                        onClick = { if (navigation.request == wanted) navigation.request = next }) }
                    page?.olderActivityRequest()?.let { next -> actionButton(text = authUiText("Older", "Более ранние", "Бұрынғы"),
                        enabled = canNavigate, autoLoading = false, confirmationRequired = false, fillMaxWidthIfTextPresent = false,
                        onClick = { if (navigation.request == wanted) navigation.request = next }) }
                }
                if (page != null && wanted.filter.commandId == null) Text(authUiText(
                    "${page.entries.size} changes on this page · older history remains available, 20 at a time.",
                    "На странице: ${page.entries.size} изменений · старая история доступна по 20 записей.",
                    "Бұл бетте ${page.entries.size} өзгеріс · ескі тарих бір бетте 20 жазбадан қолжетімді."),
                    color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
            }
        }
        FlowRow(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (page != null && !data.loading && data.error == null && data.loadedSignal == signal)
                authUiText("Updated", "Обновлено", "Жаңартылды") + " · " + receiptUiDateTime(page.checkedAtMillis)
            else authUiText("History · refresh needed", "История · требуется обновление", "Тарих · жаңарту қажет"),
                color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
            actionButton(text = authUiText("Refresh page", "Обновить страницу", "Бетті жаңарту"), enabled = !data.loading,
                loading = data.loading, autoLoading = false, confirmationRequired = false, fillMaxWidthIfTextPresent = false,
                onClick = { requests.trySend(Unit) })
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
    var detail by remember(account, generation, entry.commandId) { mutableStateOf<MarketShoppingActivityEntry?>(null) }
    var loading by remember(account, generation, entry.commandId) { mutableStateOf(false) }
    var error by remember(account, generation, entry.commandId) { mutableStateOf<List<LocalizedStringDataModel>?>(null) }
    var attempt by remember(account, generation, entry.commandId) { mutableStateOf(0) }
    val detailLifetime = remember(account, generation, entry.commandId, attempt) { ShoppingActivityReadLifetime() }
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
                            Text(authUiText("Recorded response", "Записанный ответ", "Жазылған жауап"),
                                color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                            Text(eventMessage(key).visibleLocalizedString(stateValues.appLanguage, key),
                                color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
                        }
                        val subtotal = entry.reviewedSubtotalMinor; val currency = entry.reviewedCurrency
                        if (subtotal != null && currency != null) Text(authUiText("Historical reviewed items estimate", "Исторический расчёт при проверке", "Тексеру кезіндегі бұрынғы тауар есебі") +
                            " · " + marketMoneyLabel(subtotal, currency), color = stateValues.TextColor, fontSize = stateValues.textSize)
                        Text(authUiText("This record is not a receipt or a current price. Viewing it does not apply or undo anything.",
                            "Это не чек и не текущая цена. Просмотр ничего не применяет и не отменяет.",
                            "Бұл чек те, ағымдағы баға да емес. Көру еш өзгерісті қолданбайды не болдырмайды."),
                            color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                    }
                }
                if (loading) item { LoadingSkeleton(Modifier.fillMaxWidth(), rows = 3) }
                error?.let { message -> item {
                    Text(message.visibleLocalizedString(stateValues.appLanguage, ""), color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
                    actionButton(text = authUiText("Load details again", "Загрузить данные снова", "Деректерді қайта жүктеу"),
                        enabled = !loading, autoLoading = false, confirmationRequired = false, onClick = { attempt++ })
                } }
                if (!entry.detailsRecorded) item {
                    Text(if (entry.accepted) authUiText("Older record: product and shop labels were not captured. Today's catalogue has not been substituted for past details.",
                        "Старая запись: названия товаров и магазинов не сохранялись. Сегодняшний каталог не подставлен вместо прошлых данных.",
                        "Ескі жазба: тауар мен дүкен атаулары сақталмаған. Бұрынғы деректер орнына бүгінгі каталог қойылған жоқ.")
                    else authUiText("This command did not apply a list change. Rejected proposals are not shown as completed replacements.",
                        "Эта команда не изменила список. Отклонённые предложения не показаны как выполненные замены.",
                        "Бұл команда тізімді өзгертпеді. Қабылданбаған ұсыныстар орындалған ауыстыру ретінде көрсетілмейді."),
                        color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                }
                items(detail?.details?.lines.orEmpty()) { change ->
                    Column(Modifier.fillMaxWidth().border(stateValues.unfocusedBorderWidth,
                        stateValues.PlaceholderTextColor.copy(alpha = 0.25f), RoundedCornerShape(stateValues.cornerRadius))
                        .padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        change.before?.let { ActivitySelection(authUiText("Before", "Было", "Бұрын"), it) }
                        change.after?.let { ActivitySelection(authUiText("After", "Стало", "Кейін"), it) }
                        if (change.after == null) Text(authUiText("Removed from list", "Удалено из списка", "Тізімнен жойылды"),
                            color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                    }
                }
                item {
                    Text(authUiText("Change reference", "Номер изменения", "Өзгеріс нөмірі"), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                    SelectionContainer { Text(entry.commandId, color = stateValues.TextColor, fontSize = stateValues.smallTextSize) }
                    ClipboardCopyButton(textToCopy = entry.commandId)
                    entry.appliedRevision?.let { Text(authUiText("List revision", "Версия списка", "Тізім нұсқасы") + " · $it",
                        color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize) }
                }
            }
            actionButton(modifier = Modifier.padding(12.dp), text = authUiText("Close", "Закрыть", "Жабу"),
                autoLoading = false, confirmationRequired = false, onClick = onDismiss)
        }
    }
}

@Composable
private fun AppConfiguration.ActivitySelection(label: String, line: MarketShoppingLine) {
    val unit = line.unitName.visibleLocalizedString(stateValues.appLanguage, authUiText("unit", "ед.", "бірл."))
    Text(label, color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
    SelectionContainer { Text("${line.title} · ${line.shopName}", color = stateValues.TextColor, fontSize = stateValues.textSize) }
    Text("${line.units} × ${line.basis.pricedAmount.toString().removeSuffix(".0")} $unit", color = stateValues.TextColor, fontSize = stateValues.smallTextSize)
}

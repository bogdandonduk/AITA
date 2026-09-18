package kz.aita

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

internal fun marketMoneyLabel(minor: Long, currency: String) = "${minor / 100}.${(minor % 100).toString().padStart(2, '0')} $currency"

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppConfiguration.MarketShoppingFeedback(state: MarketShoppingUiState) {
    state.error?.let { Text(it.visibleLocalizedString(stateValues.appLanguage, ""), Modifier.fillMaxWidth().padding(12.dp),
        color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize) }
    state.notice?.let { Text(it.visibleLocalizedString(stateValues.appLanguage, ""), Modifier.fillMaxWidth().padding(12.dp),
        color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize) }
    MarketShoppingRecoveryControls(state)
}

/** The confirmation keeps its original payload, even if another view resolves it while open.
 * Shared by the list and frozen basket review; no second live business dialogue is composed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppConfiguration.MarketShoppingRecoveryControls(state: MarketShoppingUiState) {
    val pending = state.pending ?: return
    var reviewed by remember(state) { mutableStateOf<PendingMarketShoppingCommand?>(null) }
    val cancelling = state.cancellingCommandId == pending.command.commandId
    val busy = state.changing || state.checking || state.cancelling
    val frozen = reviewed
    Column(Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (cancelling) authUiText("Cancellation awaiting confirmation", "Ожидается подтверждение отмены", "Бас тартудың расталуы күтілуде", "Жокко чыгаруу ырастоону күтүүдө")
            else authUiText("Unconfirmed list change", "Неподтверждённое изменение списка", "Тізім өзгерісі расталмады", "Тизменин ырастала элек өзгөртүүсү"),
            color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SelectionContainer(Modifier.weight(1f)) { Text(pending.command.commandId,
                color = stateValues.TextColor, fontSize = stateValues.smallTextSize) }
            ClipboardCopyButton(textToCopy = pending.command.commandId)
        }
        if (frozen != null && !cancelling) {
            Text(authUiText("Cancel the change below? This can stop an unrecorded request, but cannot undo a change that already committed.",
                "Отменить команду ниже? Это остановит ещё не записанный запрос, но не отменит уже применённое изменение.",
                "Төмендегі өзгерістен бас тартасыз ба? Бұл әлі тіркелмеген сұрауды тоқтатады, бірақ қолданылған өзгерісті кері қайтармайды.", "Төмөнкү өзгөртүүнү жокко чыгарасызбы? Бул каттала элек суроо-талапты токтото алат, бирок сакталган өзгөртүүнү артка кайтара албайт."),
                color = stateValues.TextColor, fontSize = stateValues.smallTextSize)
            SelectionContainer { Text(frozen.command.commandId, color = stateValues.TextColor, fontSize = stateValues.smallTextSize) }
            if (frozen != pending) Text(eventMessage("market.shopping_recovery_changed").visibleLocalizedString(stateValues.appLanguage, ""),
                color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                actionButton(text = authUiText("Confirm cancellation", "Подтвердить отмену", "Бас тартуды растау", "Жокко чыгарууну ырастоо"),
                    enabled = !busy && state.active && frozen == pending, loading = state.cancelling,
                    autoLoading = false, confirmationRequired = false,
                    onClick = { state.cancelPending(frozen.command); reviewed = null })
                actionButton(text = authUiText("Keep pending", "Оставить ожидающим", "Күтілуде қалдыру", "Күтүүдө калтыруу"),
                    autoLoading = false, confirmationRequired = false,
                    enabled = !busy, onClick = { reviewed = null })
            }
        } else {
            if (cancelling) Text(authUiText("Check reads the result. Retry continues cancellation, not the original edit. Closing keeps recovery saved.",
                "Проверка читает результат. Повтор продолжает отмену, не исходное изменение. Закрытие сохраняет восстановление.",
                "Тексеру нәтижені оқиды. Қайталау бастапқы өзгерісті емес, бас тартуды жалғастырады. Жапқанда қалпына келтіру сақталады.", "«Текшерүү» натыйжаны окуйт. «Кайталоо» баштапкы түзөтүүнү эмес, жокко чыгарууну улантат. Жабылганда калыбына келтирүү маалыматы сакталат."),
                color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                actionButton(text = authUiText("Check result", "Проверить результат", "Нәтижені тексеру", "Натыйжаны текшерүү"),
                    autoLoading = false, confirmationRequired = false,
                    enabled = !busy && state.active, loading = state.checking, onClick = { state.checkResult() })
                actionButton(text = if (cancelling) authUiText("Retry cancellation", "Повторить отмену", "Бас тартуды қайталау", "Жокко чыгарууну кайталоо")
                    else authUiText("Retry same change", "Повторить ту же команду", "Сол өзгерісті қайталау", "Ошол эле өзгөртүүнү кайталоо"),
                    autoLoading = false, confirmationRequired = false,
                    enabled = !busy && state.active, loading = state.changing, onClick = { state.retry() })
                if (!cancelling) actionButton(text = authUiText("Cancel pending change", "Отменить ожидающее изменение", "Күтілудегі өзгерістен бас тарту", "Күтүүдөгү өзгөртүүнү жокко чыгаруу"),
                    autoLoading = false, confirmationRequired = false,
                    enabled = !busy && state.active, onClick = { reviewed = pending })
            }
        }
    }
}

@Composable
internal fun AppConfiguration.BuyerShoppingListScreen(navigation: BuyerMarketNavigation) {
    val state = rememberMarketShoppingUiState()
    val account = stateValues.userAccount?.id
    val generation = currentAuthenticatedSessionGeneration()
    val scope = rememberCoroutineScope()
    fun continueBrowsing() {
        scope.launch { navigation.continueDestination()?.let { Navigation.goMain(it) } }
    }
    fun visitShop(id: String) {
        scope.launch {
            if (navigation.visitShop(id)) Navigation.goMain(NavigationScreenModel.Buyer.Main.Home)
        }
    }
    var openedId by remember(account, generation) { mutableStateOf<String?>(null) }
    var comparison by remember(account, generation) { mutableStateOf<MarketComparisonSelection?>(null) }
    var planning by remember(account, generation) { mutableStateOf(false) }
    var comparisonCity by remember(account, generation) { mutableStateOf("") }
    val activityNavigation = remember(account, generation) { MarketShoppingActivityNavigation() }
    var removal by remember(account, generation) { mutableStateOf<MarketShoppingLineReview?>(null) }
    var quantityEdit by remember(account, generation) { mutableStateOf<MarketShoppingLineReview?>(null) }
    fun listDialogIsOpen(): Boolean = quantityEdit != null || removal != null || openedId != null || comparison != null || planning
    val displayed = state.snapshot
    val groups = displayed?.shoppingGroups().orEmpty()
    AitaScreenColumn(
        Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
        maximumContentWidth = 1120.dp,
        appBar = {
            ScreenAppBarWidget(title = authUiText("Shopping list", "Список покупок", "Сатып алу тізімі", "Сатып алуу тизмеси"), iconPath = marketIconPath(143))
        }
    ) {
        Text(authUiText("Plan by shop. These are item estimates, not orders or reservations.",
            "Планируйте покупки по магазинам. Это расчёт товаров, не заказ и не резерв.",
            "Сатып алуды дүкен бойынша жоспарлаңыз. Бұл — тауар есебі, тапсырыс не резерв емес.", "Дүкөндөр боюнча пландаңыз. Бул тапшырык же резерв эмес, товарлардын болжолдуу суммалары."),
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
        actionButton(modifier = Modifier.padding(horizontal = 16.dp), text = marketBrowseText("market.browse_continue"),
            autoLoading = false, confirmationRequired = false, enabledColor = stateValues.BackgroundColor,
            textColor = stateValues.TextColor, onClick = ::continueBrowsing)
        val section = sectionTabsWidget("buyer-shopping:$account", listOf(
            TabContent("list", authUiText("Items", "Товары", "Тауарлар", "Товарлар")),
            TabContent("estimate", authUiText("By shop", "По магазинам", "Дүкен бойынша", "Дүкөн боюнча")),
            TabContent("activity", authUiText("Activity", "История", "Тарих", "Аракеттер"))))
        MarketShoppingFeedback(state)
        if (section == "activity") {
            MarketShoppingActivityPanel(activityNavigation, Modifier.weight(1f).fillMaxWidth())
        } else {
            if (state.snapshot == null && state.loading) {
                LoadingSkeleton(layout = LoadingLayout.ShoppingLine, modifier = Modifier.fillMaxWidth().padding(16.dp), rows = 5)
                Spacer(Modifier.weight(1f))
            } else LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.snapshot?.lines.isNullOrEmpty()) item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        CpImage(Modifier.size(64.dp), url = marketIconPath(143), fallbackRes = marketIconFallback(143), contentDescription = null, tintColor = stateValues.AccentColor)
                        Text(if (state.snapshot == null) authUiText("Connect to load your list", "Подключитесь, чтобы загрузить список", "Тізімді жүктеу үшін қосылыңыз", "Тизмеңизди жүктөө үчүн туташыңыз")
                            else authUiText("Start with something you need", "Начните с нужного товара", "Қажетті тауардан бастаңыз", "Керектүү нерсеңизден баштаңыз"),
                            Modifier.padding(16.dp), color = stateValues.TextColor, fontSize = stateValues.accentTextSize, fontWeight = FontWeight.Bold)
                    }
                }
                if (!state.snapshot?.lines.isNullOrEmpty()) item(key = "plan-basket") {
                    actionButton(text = authUiText("Compare whole basket", "Сравнить всю корзину", "Бүкіл себетті салыстыру", "Бүт себетти салыштыруу"),
                        iconPath = marketIconPath(141), iconRes = marketIconFallback(141),
                        enabled = state.canChange, autoLoading = false, confirmationRequired = false,
                        onClick = { if (!listDialogIsOpen() && state.canChange) planning = true })
                }
                if (section == "list") {
                    if (displayed != null) items(displayed.lines, key = { it.line.offerId }) { row ->
                        val review = displayed.reviewShoppingLine(row.line)
                        ShoppingLineCard(row, state, review, onOpen = {
                            if (!listDialogIsOpen()) openedId = row.line.offerId
                        }, onCompare = {
                            if (!listDialogIsOpen()) {
                                comparisonCity = ""
                                review?.let { comparison = state.compareLine(it) }
                            }
                        }, onEditQuantity = {
                            // Capture only a still-current row; never rebuild its review from a later revision.
                            if (!listDialogIsOpen() && state.canChange && review?.matches(state.snapshot) == true) quantityEdit = review
                        }, onRemove = {
                            // Keep this exact row/revision while the confirmation is open.
                            if (!listDialogIsOpen() && state.canChange && review?.matches(state.snapshot) == true) removal = review
                        })
                    }
                } else {
                    items(groups, key = { "${it.storeId}:${it.currencyCode}" }) { group ->
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(stateValues.cornerRadius))
                            .background(stateValues.AccentColor.copy(alpha = 0.06f)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(group.shopName, color = stateValues.TextColor, fontSize = stateValues.accentTextSize, fontWeight = FontWeight.Bold)
                            Text(authUiText("Estimated items subtotal", "Предварительная сумма товаров", "Тауарлардың алдын ала сомасы", "Товарлардын болжолдуу аралык суммасы"),
                                color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                            Text(group.pricedSubtotalMinor?.let { marketMoneyLabel(it, group.currencyCode) }
                                ?: authUiText("Needs review", "Требует проверки", "Тексеру қажет", "Карап чыгуу керек"),
                                color = changedValueColor(group.pricedSubtotalMinor, "shopping:${group.storeId}:${group.currencyCode}", stateValues.AccentColor),
                                fontWeight = FontWeight.Bold, fontSize = stateValues.titleTextSize)
                            Text(authUiText("${group.lines.size - group.unpricedLines} of ${group.lines.size} lines priced",
                                "Рассчитано строк: ${group.lines.size - group.unpricedLines} из ${group.lines.size}",
                                "${group.lines.size} жолдың ${group.lines.size - group.unpricedLines} жолы есептелді", "${group.lines.size} саптын ${group.lines.size - group.unpricedLines} сабынын баасы бар"),
                                color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                            if (group.unpricedLines > 0) Text(authUiText("Incomplete: ${group.unpricedLines} lines are excluded. They are not free.",
                                "Неполная сумма: ${group.unpricedLines} строк не включены. Это не бесплатные товары.",
                                "Сома толық емес: ${group.unpricedLines} жол кірмейді. Олар тегін емес.", "Толук эмес: ${group.unpricedLines} сап эсепке кошулган жок. Алар бекер эмес."), color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
                            actionButton(text = authUiText("Visit shop", "Открыть магазин", "Дүкенге өту", "Дүкөнгө өтүү"), iconPath = marketIconPath(139),
                                iconRes = marketIconFallback(139), autoLoading = false, confirmationRequired = false, onClick = {
                                    visitShop(group.storeId)
                                })
                        }
                    }
                    if (groups.isNotEmpty()) item {
                        Text(authUiText("Each currency stays separate. Delivery, service fees and cross-item basket discounts are not included. No stock is held and no payment is made.",
                            "Валюты не смешиваются. Доставка, сервисные сборы и скидки на корзину не включены. Остатки не резервируются, оплата не производится.",
                            "Валюталар араластырылмайды. Жеткізу, қызмет алымы және себет жеңілдіктері кірмейді. Қор резервтелмейді, төлем жасалмайды.", "Ар бир валюта өзүнчө калат. Жеткирүү, кызмат акылары жана бир нече товарга жалпы себет арзандатуулары кошулган эмес. Товар резервге коюлбайт жана төлөм жасалбайт."),
                            color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                    }
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Text(if (state.fresh) authUiText("Server estimate", "Расчёт сервера", "Сервер есебі", "Сервердин болжолдуу суммасы")
                        else if (state.snapshot == null) authUiText("Waiting for your list", "Ожидаем список", "Тізім күтілуде", "Тизмеңизди күтүүдө")
                        else authUiText("Saved estimate · refresh needed", "Сохранённый расчёт · обновите", "Сақталған есеп · жаңартыңыз", "Сакталган болжолдуу сумма · жаңыртуу керек"),
                        color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                    state.snapshot?.checkedAtMillis?.takeIf { it > 0 }?.let {
                        Text(receiptUiDateTime(it), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                    }
                }
                actionButton(text = authUiText("Refresh", "Обновить", "Жаңарту", "Жаңыртуу"),autoLoading = false,
                    enabled = !state.loading, loading = state.loading, confirmationRequired = false, onClick = { state.refresh(userInitiated = true) })
            }
        }
    }
    quantityEdit?.let { review ->
        MarketShoppingQuantityDialog(review, state, onDismiss = { quantityEdit = null })
    }
    removal?.let { review ->
        MarketShoppingRemoveDialog(review, state, onDismiss = { removal = null })
    }
    openedId?.let { id -> MarketOfferDetailDialog(id, state, onDismiss = { openedId = null }, onVisitShop = { shop ->
        openedId = null
        visitShop(shop.storeId)
    }, onCompare = { offer ->
        openedId = null
        comparisonCity = ""
        val current = state.snapshot
        comparison = current?.let { snapshot -> snapshot.lines.firstOrNull { it.line.offerId == offer.id }?.line?.comparisonSelection(snapshot.revision) }
            ?: offer.comparisonSelection()
    }) }
    if (planning) MarketBasketPlanDialog(state, onDismiss = { planning = false }, onCompare = { selected, city ->
        planning = false; comparisonCity = city; comparison = selected
    }, onVisitShop = { shop ->
        planning = false
        visitShop(shop.storeId)
    })
    comparison?.let { selection -> MarketComparisonDialog(selection, state, initialCity = comparisonCity, onDismiss = { comparison = null }, onVisitShop = { shop ->
        comparison = null
        visitShop(shop.storeId)
    }) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppConfiguration.ShoppingLineCard(row: MarketShoppingQuotedLine, state: MarketShoppingUiState,
    review: MarketShoppingLineReview?, onOpen: () -> Unit, onCompare: () -> Unit,
    onEditQuantity: () -> Unit, onRemove: () -> Unit) {
    val line = row.line
    val editable = state.canChange && review?.matches(state.snapshot) == true
    val unit = line.unitName.visibleLocalizedString(stateValues.appLanguage, authUiText("unit", "ед.", "бірл.", "бирдик"))
    val amount = line.basis.pricedAmount.toString().removeSuffix(".0")
    val status = when (row.status) {
        MARKET_QUOTE_ESTIMATED -> authUiText("Estimated · not reserved", "Рассчитано · не зарезервировано", "Есептелді · резервтелмеген", "Болжолдуу · резервге коюлган эмес")
        MARKET_QUOTE_QUANTITY -> authUiText("Confirm this quantity with the shop", "Уточните это количество в магазине", "Бұл санды дүкеннен нақтылаңыз", "Бул санды дүкөндөн ырастатыңыз")
        MARKET_QUOTE_CHANGED -> authUiText("Product or selling unit changed · review before re-adding", "Товар или единица продажи изменились · проверьте перед добавлением", "Тауар не сату бірлігі өзгерді · қайта қоспас бұрын тексеріңіз", "Товар же сатуу бирдиги өзгөрдү · кайра кошуудан мурун карап чыгыңыз")
        MARKET_QUOTE_PRICE -> authUiText("Price needs confirmation", "Цену нужно уточнить", "Бағаны нақтылау қажет", "Бааны ырастатуу керек")
        else -> authUiText("No longer published · kept in your list", "Больше не опубликовано · сохранено в списке", "Енді жарияланбаған · тізімде сақталған", "Эми жарыяланган эмес · тизмеңизде сакталды")
    }
    Column(Modifier.fillMaxWidth().border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor.copy(alpha = 0.25f),
        RoundedCornerShape(stateValues.cornerRadius)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(line.title, color = stateValues.TextColor, fontSize = stateValues.accentTextSize, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Text("${line.shopName} · ${line.units} × $amount $unit", color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
        Text(row.subtotalMinor?.let { marketMoneyLabel(it, line.basis.currencyCode) }
            ?: authUiText("Not included in subtotal", "Не включено в сумму", "Сомаға кірмейді", "Аралык суммага кошулган жок"),
            color = changedValueColor(row.subtotalMinor, "shopping-line:${line.offerId}", stateValues.AccentColor),
            fontWeight = FontWeight.Bold, fontSize = stateValues.accentTextSize)
        Text(status, color = if (row.status == MARKET_QUOTE_ESTIMATED) stateValues.PlaceholderTextColor else stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            actionButton(modifier = Modifier.semantics { contentDescription = authUiText("Decrease quantity", "Уменьшить количество", "Санды азайту", "Санды азайтуу") }, text = "", icon = { Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) { Text("−", color = stateValues.AccentTextColor, fontSize = stateValues.textSize, fontWeight = FontWeight.Bold) } }, iconContentDescription = authUiText("Decrease units", "Уменьшить количество", "Санды азайту", "Бирдиктерди азайтуу"),
                enabled = editable && line.units > 1, autoLoading = false, confirmationRequired = false,
                onClick = { review?.let { state.changeLine(it, line.units - 1) } })
            Text(line.units.toString(), color = stateValues.TextColor, fontSize = stateValues.accentTextSize, fontWeight = FontWeight.Bold)
            actionButton(modifier = Modifier.semantics { contentDescription = authUiText("Increase quantity", "Увеличить количество", "Санды көбейту", "Санды көбөйтүү") }, text = "", icon = { Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) { Text("+", color = stateValues.AccentTextColor, fontSize = stateValues.textSize, fontWeight = FontWeight.Bold) } }, iconContentDescription = authUiText("Increase units", "Увеличить количество", "Санды көбейту", "Бирдиктерди көбөйтүү"),
                enabled = editable && line.units < MARKET_SHOPPING_MAX_UNITS, autoLoading = false, confirmationRequired = false,
                onClick = { review?.let { state.changeLine(it, line.units + 1) } })
            }
            actionButton(text = eventMessage("market.shopping_quantity_edit").visibleLocalizedString(stateValues.appLanguage, ""),
                enabled = editable, autoLoading = false, confirmationRequired = false,
                onClick = onEditQuantity)
            actionButton(text = "", iconPath = stateValues.drawablePathIconDelete,
                iconContentDescription = authUiText("Remove from list", "Убрать из списка", "Тізімнен жою", "Тизмеден алып салуу"),
                enabled = editable, autoLoading = false, confirmationRequired = false,
                onClick = onRemove)
        }
        if (review != null && line.comparisonSelection(review.expectedRevision) != null) actionButton(
            text = authUiText("Compare other shops", "Сравнить другие магазины", "Басқа дүкендерді салыстыру", "Башка дүкөндөрдү салыштыруу"),
            iconPath = marketIconPath(141), iconRes = marketIconFallback(141), enabled = editable,
            autoLoading = false, confirmationRequired = false, onClick = onCompare)
        if (row.offer != null) actionButton(text = authUiText("View current offer", "Посмотреть предложение", "Ағымдағы ұсынысты көру", "Учурдагы сунушту көрүү"),
            autoLoading = false, confirmationRequired = false, onClick = onOpen)
    }
}

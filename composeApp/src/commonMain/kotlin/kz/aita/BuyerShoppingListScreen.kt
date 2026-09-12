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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

internal fun marketMoneyLabel(minor: Long, currency: String) = "${minor / 100}.${(minor % 100).toString().padStart(2, '0')} $currency"

@Composable
internal fun AppConfiguration.MarketShoppingFeedback(state: MarketShoppingUiState) {
    state.error?.let { Text(it.visibleLocalizedString(stateValues.appLanguage, ""), Modifier.fillMaxWidth().padding(12.dp),
        color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize) }
    if (state.pending != null) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(authUiText("Unconfirmed list change", "Неподтверждённое изменение списка", "Тізім өзгерісі расталмады"),
            Modifier.weight(1f), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
        actionButton(text = authUiText("Retry", "Повторить", "Қайталау"), autoLoading = false, confirmationRequired = false,
            fillMaxWidthIfTextPresent = false, enabled = !state.changing, loading = state.changing, onClick = { state.retry() })
    }
}

@Composable
internal fun AppConfiguration.BuyerShoppingListScreen() {
    val state = rememberMarketShoppingUiState()
    val account = stateValues.userAccount?.id
    val generation = currentAuthenticatedSessionGeneration()
    val scope = rememberCoroutineScope()
    var openedId by remember(account, generation) { mutableStateOf<String?>(null) }
    var comparison by remember(account, generation) { mutableStateOf<MarketComparisonSelection?>(null) }
    val groups = state.snapshot?.shoppingGroups().orEmpty()
    Column(Modifier.fillMaxSize().aitaWidthCap(1120.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ScreenAppBarWidget(title = authUiText("Shopping list", "Список покупок", "Сатып алу тізімі"), iconPath = marketIconPath(143))
        Text(authUiText("Plan by shop. These are item estimates, not orders or reservations.",
            "Планируйте покупки по магазинам. Это расчёт товаров, не заказ и не резерв.",
            "Сатып алуды дүкен бойынша жоспарлаңыз. Бұл — тауар есебі, тапсырыс не резерв емес."),
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
        val section = sectionTabsWidget("buyer-shopping:$account", listOf(
            TabContent("list", authUiText("Items", "Товары", "Тауарлар")),
            TabContent("estimate", authUiText("By shop", "По магазинам", "Дүкен бойынша"))))
        MarketShoppingFeedback(state)
        if (state.snapshot == null && state.loading) {
            LoadingSkeleton(Modifier.fillMaxWidth().padding(16.dp), rows = 5)
            Spacer(Modifier.weight(1f))
        } else LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.snapshot?.lines.isNullOrEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CpImage(Modifier.size(64.dp), url = marketIconPath(143), fallbackRes = marketIconFallback(143), contentDescription = null, tintColor = stateValues.AccentColor)
                    Text(if (state.snapshot == null) authUiText("Connect to load your list", "Подключитесь, чтобы загрузить список", "Тізімді жүктеу үшін қосылыңыз")
                        else authUiText("Start with something you need", "Начните с нужного товара", "Қажетті тауардан бастаңыз"),
                        Modifier.padding(16.dp), color = stateValues.TextColor, fontSize = stateValues.accentTextSize, fontWeight = FontWeight.Bold)
                    actionButton(text = authUiText("Explore the market", "Открыть маркет", "Маркетке өту"), confirmationRequired = false, autoLoading = false,
                        onClick = { scope.launch { Navigation.goMain(NavigationScreenModel.Buyer.Main.Home) } })
                }
            }
            if (section == "list") items(state.snapshot?.lines.orEmpty(), key = { it.line.offerId }) { row ->
                ShoppingLineCard(row, state, onOpen = { openedId = row.line.offerId }, onCompare = {
                    state.snapshot?.revision?.let { comparison = row.line.comparisonSelection(it) }
                })
            } else {
                items(groups, key = { "${it.storeId}:${it.currencyCode}" }) { group ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(stateValues.cornerRadius))
                        .background(stateValues.AccentColor.copy(alpha = 0.06f)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(group.shopName, color = stateValues.TextColor, fontSize = stateValues.accentTextSize, fontWeight = FontWeight.Bold)
                        Text(authUiText("Estimated items subtotal", "Предварительная сумма товаров", "Тауарлардың алдын ала сомасы"),
                            color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                        Text(group.pricedSubtotalMinor?.let { marketMoneyLabel(it, group.currencyCode) }
                            ?: authUiText("Needs review", "Требует проверки", "Тексеру қажет"),
                            color = changedValueColor(group.pricedSubtotalMinor, "shopping:${group.storeId}:${group.currencyCode}", stateValues.AccentColor),
                            fontWeight = FontWeight.Bold, fontSize = stateValues.titleTextSize)
                        Text(authUiText("${group.lines.size - group.unpricedLines} of ${group.lines.size} lines priced",
                            "Рассчитано строк: ${group.lines.size - group.unpricedLines} из ${group.lines.size}",
                            "${group.lines.size} жолдың ${group.lines.size - group.unpricedLines} жолы есептелді"),
                            color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                        if (group.unpricedLines > 0) Text(authUiText("Incomplete: ${group.unpricedLines} lines are excluded. They are not free.",
                            "Неполная сумма: ${group.unpricedLines} строк не включены. Это не бесплатные товары.",
                            "Сома толық емес: ${group.unpricedLines} жол кірмейді. Олар тегін емес."), color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
                        actionButton(text = authUiText("Visit shop", "Открыть магазин", "Дүкенге өту"), iconPath = marketIconPath(139),
                            iconRes = marketIconFallback(139), autoLoading = false, confirmationRequired = false, onClick = {
                                NavigationScreenModel.Buyer.Main.Home.setStateNow("market-shop:$account" to group.storeId)
                                scope.launch { Navigation.goMain(NavigationScreenModel.Buyer.Main.Home) }
                            })
                    }
                }
                if (groups.isNotEmpty()) item {
                    Text(authUiText("Each currency stays separate. Delivery, service fees and cross-item basket discounts are not included. No stock is held and no payment is made.",
                        "Валюты не смешиваются. Доставка, сервисные сборы и скидки на корзину не включены. Остатки не резервируются, оплата не производится.",
                        "Валюталар араластырылмайды. Жеткізу, қызмет алымы және себет жеңілдіктері кірмейді. Қор резервтелмейді, төлем жасалмайды."),
                        color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(if (state.fresh) authUiText("Server estimate", "Расчёт сервера", "Сервер есебі")
                    else if (state.snapshot == null) authUiText("Waiting for your list", "Ожидаем список", "Тізім күтілуде")
                    else authUiText("Saved estimate · refresh needed", "Сохранённый расчёт · обновите", "Сақталған есеп · жаңартыңыз"),
                    color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                state.snapshot?.checkedAtMillis?.takeIf { it > 0 }?.let {
                    Text(receiptUiDateTime(it), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                }
            }
            actionButton(text = authUiText("Refresh", "Обновить", "Жаңарту"), fillMaxWidthIfTextPresent = false, autoLoading = false,
                enabled = !state.loading, loading = state.loading, confirmationRequired = false, onClick = { state.refresh(userInitiated = true) })
        }
    }
    openedId?.let { id -> MarketOfferDetailDialog(id, state, onDismiss = { openedId = null }, onVisitShop = { shop ->
        NavigationScreenModel.Buyer.Main.Home.setStateNow("market-shop:$account" to shop.storeId)
        openedId = null
        scope.launch { Navigation.goMain(NavigationScreenModel.Buyer.Main.Home) }
    }, onCompare = { offer ->
        openedId = null
        val current = state.snapshot
        comparison = current?.let { snapshot -> snapshot.lines.firstOrNull { it.line.offerId == offer.id }?.line?.comparisonSelection(snapshot.revision) }
            ?: offer.comparisonSelection()
    }) }
    comparison?.let { selection -> MarketComparisonDialog(selection, state, onDismiss = { comparison = null }, onVisitShop = { shop ->
        comparison = null
        NavigationScreenModel.Buyer.Main.Home.setStateNow("market-shop:$account" to shop.storeId)
        scope.launch { Navigation.goMain(NavigationScreenModel.Buyer.Main.Home) }
    }) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppConfiguration.ShoppingLineCard(row: MarketShoppingQuotedLine, state: MarketShoppingUiState, onOpen: () -> Unit, onCompare: () -> Unit) {
    val line = row.line
    val unit = line.unitName.visibleLocalizedString(stateValues.appLanguage, authUiText("unit", "ед.", "бірл."))
    val amount = line.basis.pricedAmount.toString().removeSuffix(".0")
    val status = when (row.status) {
        MARKET_QUOTE_ESTIMATED -> authUiText("Estimated · not reserved", "Рассчитано · не зарезервировано", "Есептелді · резервтелмеген")
        MARKET_QUOTE_QUANTITY -> authUiText("Confirm this quantity with the shop", "Уточните это количество в магазине", "Бұл санды дүкеннен нақтылаңыз")
        MARKET_QUOTE_CHANGED -> authUiText("Product or selling unit changed · review before re-adding", "Товар или единица продажи изменились · проверьте перед добавлением", "Тауар не сату бірлігі өзгерді · қайта қоспас бұрын тексеріңіз")
        MARKET_QUOTE_PRICE -> authUiText("Price needs confirmation", "Цену нужно уточнить", "Бағаны нақтылау қажет")
        else -> authUiText("No longer published · kept in your list", "Больше не опубликовано · сохранено в списке", "Енді жарияланбаған · тізімде сақталған")
    }
    Column(Modifier.fillMaxWidth().border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor.copy(alpha = 0.25f),
        RoundedCornerShape(stateValues.cornerRadius)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(line.title, color = stateValues.TextColor, fontSize = stateValues.accentTextSize, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Text("${line.shopName} · ${line.units} × $amount $unit", color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
        Text(row.subtotalMinor?.let { marketMoneyLabel(it, line.basis.currencyCode) }
            ?: authUiText("Not included in subtotal", "Не включено в сумму", "Сомаға кірмейді"),
            color = changedValueColor(row.subtotalMinor, "shopping-line:${line.offerId}", stateValues.AccentColor),
            fontWeight = FontWeight.Bold, fontSize = stateValues.accentTextSize)
        Text(status, color = if (row.status == MARKET_QUOTE_ESTIMATED) stateValues.PlaceholderTextColor else stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            actionButton(modifier = Modifier.semantics { contentDescription = authUiText("Decrease quantity", "Уменьшить количество", "Санды азайту") }, text = "−", iconContentDescription = authUiText("Decrease units", "Уменьшить количество", "Санды азайту"),
                enabled = state.canChange && line.units > 1, autoLoading = false, confirmationRequired = false, fillMaxWidthIfTextPresent = false,
                onClick = { state.change(line.offerId, line.units - 1, line.basis) })
            Text(line.units.toString(), color = stateValues.TextColor, fontSize = stateValues.accentTextSize, fontWeight = FontWeight.Bold)
            actionButton(modifier = Modifier.semantics { contentDescription = authUiText("Increase quantity", "Увеличить количество", "Санды көбейту") }, text = "+", iconContentDescription = authUiText("Increase units", "Увеличить количество", "Санды көбейту"),
                enabled = state.canChange && line.units < MARKET_SHOPPING_MAX_UNITS, autoLoading = false, confirmationRequired = false, fillMaxWidthIfTextPresent = false,
                onClick = { state.change(line.offerId, line.units + 1, line.basis) })
            }
            actionButton(text = "", iconPath = stateValues.drawablePathIconDelete,
                iconContentDescription = authUiText("Remove from list", "Убрать из списка", "Тізімнен жою"),
                enabled = state.canChange, autoLoading = false, confirmationRequired = true,
                onClick = { state.change(line.offerId, 0, null) })
        }
        if (line.comparisonSelection(state.snapshot?.revision ?: 0L) != null) actionButton(
            text = authUiText("Compare other shops", "Сравнить другие магазины", "Басқа дүкендерді салыстыру"),
            iconPath = marketIconPath(141), iconRes = marketIconFallback(141), enabled = !state.changing && state.pending == null,
            autoLoading = false, confirmationRequired = false, onClick = onCompare)
        if (row.offer != null) actionButton(text = authUiText("View current offer", "Посмотреть предложение", "Ағымдағы ұсынысты көру"),
            autoLoading = false, confirmationRequired = false, onClick = onOpen)
    }
}

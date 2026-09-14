package kz.aita

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import kotlinx.coroutines.delay
import kotlin.time.TimeMark

/** The reviewed object and command ID never change under the user's finger. A refresh/invalidation
 * requires going back to review a new plan, not updating the contents behind a confirmation button.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppConfiguration.MarketBasketReviewDialog(
    result: MarketBasketResult,
    command: MarketShoppingCommand,
    started: TimeMark,
    shopping: MarketShoppingUiState,
    valid: Boolean,
    submitted: Boolean,
    onConfirm: () -> Boolean,
    onBack: () -> Unit,
    onDismiss: () -> Unit
) {
    val change = command.basketChange ?: return
    var tab by remember(command.commandId) { mutableStateOf("changes") }
    val changesScroll = rememberLazyListState()
    val keptScroll = rememberLazyListState()
    var expired by remember(command.commandId) { mutableStateOf(started.elapsedNow().inWholeMilliseconds >= MARKET_BASKET_REVIEW_MAX_AGE_MILLIS) }
    LaunchedEffect(command.commandId) {
        while (!expired) { delay(1_000); expired = started.elapsedNow().inWholeMilliseconds >= MARKET_BASKET_REVIEW_MAX_AGE_MILLIS }
    }
    val ownPending = shopping.pending?.command?.commandId == command.commandId
    var attemptRejected by remember(command.commandId) { mutableStateOf(false) }
    val needsReview = !valid || expired || attemptRejected
    val canConfirm = !needsReview && !submitted && shopping.canChange
    val sources = result.snapshot.lines.associateBy { it.line.offerId }
    val group = result.currencies.first { it.currencyCode == change.currencyCode }
    val plan = group.plan(change.kind)
    val bySource = plan.choices.associateBy { it.sourceOfferId }
    val shown = change.lines.filter { (it.sourceOfferId != it.targetOfferId) == (tab == "changes") }
    val untouchedCurrencyLines = result.snapshot.lines.count { it.line.basis.currencyCode != change.currencyCode }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth().aitaWidthCap(960.dp).fillMaxHeight(0.92f).padding(12.dp)
            .clip(RoundedCornerShape(stateValues.cornerRadius)).background(stateValues.BackgroundColor)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CpImage(Modifier.size(32.dp), url = marketIconPath(141), fallbackRes = marketIconFallback(141),
                    contentDescription = null, tintColor = stateValues.AccentColor)
                Text(authUiText("Review shop changes", "Проверьте замены магазинов", "Дүкен ауыстыруларын тексеріңіз", "Дүкөн өзгөртүүлөрүн кароо"),
                    Modifier.weight(1f), color = stateValues.TextColor, fontSize = stateValues.accentTextSize, fontWeight = FontWeight.Bold)
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = if (tab == "changes") changesScroll else keptScroll,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item(key = "summary") {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(stateValues.cornerRadius))
                        .background(stateValues.AccentColor.copy(alpha = 0.07f)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(marketMoneyLabel(change.reviewedItemsSubtotalMinor, change.currencyCode),
                            color = stateValues.AccentColor, fontSize = stateValues.titleTextSize, fontWeight = FontWeight.Bold)
                        Text(authUiText("Reviewed items estimate · ${change.lines.size} lines · ${plan.storeIds.size} shops",
                            "Проверяемый расчёт · ${change.lines.size} строк · магазинов: ${plan.storeIds.size}",
                            "Тексерілетін тауар есебі · ${change.lines.size} жол · ${plan.storeIds.size} дүкен", "Каралган товарлардын болжолдуу суммасы · ${change.lines.size} сап · ${plan.storeIds.size} дүкөн"),
                            color = stateValues.TextColor, fontSize = stateValues.textSize)
                        Text(authUiText("Only ${change.changedLines} shop selections change, together or not at all. Quantities stay the same. $untouchedCurrencyLines lines in other currencies stay untouched.",
                            "Изменятся только выбранные магазины у ${change.changedLines} строк: все замены вместе или ни одной. Количества прежние. Строк в других валютах без изменений: $untouchedCurrencyLines.",
                            "Тек ${change.changedLines} жолдағы дүкендер өзгереді: барлығы бірге немесе ешқайсысы. Сандар өзгермейді. Басқа валютадағы $untouchedCurrencyLines жол өзгеріссіз қалады.", "Дүкөн боюнча ${change.changedLines} тандоо гана өзгөрөт: баары бирге же эч бири өзгөрбөйт. Сандар ошол бойдон калат. Башка валюталардагы $untouchedCurrencyLines сап өзгөрбөйт."),
                            color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                        Text(authUiText("This saves your shopping list, not an order. No payment, stock reservation or guaranteed checkout price. Delivery and travel costs are excluded.",
                            "Сохраняется список покупок, не заказ. Нет оплаты, резерва товара или гарантированной цены на кассе. Доставка и дорога не учтены.",
                            "Бұл тапсырысты емес, сатып алу тізімін сақтайды. Төлем, тауар резерві немесе кассадағы кепілді баға жоқ. Жеткізу мен жол құны кірмейді.", "Бул тапшырыкты эмес, сатып алуу тизмеңизди сактайт. Төлөм, товар резерви же кассадагы кепилденген баа жок. Жеткирүү жана жол чыгымдары кошулган эмес."),
                            color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                        Text(receiptUiDateTime(change.checkedAtMillis), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                    }
                }
                item(key = "status") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        shopping.error?.let { Text(it.visibleLocalizedString(stateValues.appLanguage, ""), color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize) }
                        shopping.notice?.let { Text(it.visibleLocalizedString(stateValues.appLanguage, ""), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize) }
                        if (attemptRejected) Text(eventMessage("market.basket_review_stale").visibleLocalizedString(stateValues.appLanguage, ""),
                            color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
                        if (ownPending) MarketShoppingRecoveryControls(shopping)
                        else if (needsReview) Text(authUiText("The list, offers or review time changed. Go back and refresh before confirming a new review.",
                            "Изменились список, предложения или срок проверки. Вернитесь и обновите план перед новым подтверждением.",
                            "Тізім, ұсыныстар немесе тексеру мерзімі өзгерді. Жаңа растау алдында кері қайтып, жоспарды жаңартыңыз.", "Тизме, сунуштар же кароо мөөнөтү өзгөрдү. Жаңы кароону ырастоодон мурун артка кайтып, жаңыртыңыз."),
                            color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
                    }
                }
                item(key = "tabs") {
                    sectionTabsWidget("basket-review:${command.commandId}", listOf(
                        TabContent("changes", authUiText("Changes (${change.changedLines})", "Замены (${change.changedLines})", "Ауыстырулар (${change.changedLines})", "Өзгөртүүлөр (${change.changedLines})")),
                        TabContent("kept", authUiText("Unchanged (${change.lines.size - change.changedLines})", "Без замены (${change.lines.size - change.changedLines})", "Өзгеріссіз (${change.lines.size - change.changedLines})", "Өзгөргөн жок (${change.lines.size - change.changedLines})"))),
                        selectedId = tab, onSelected = { tab = it })
                }
                items(shown, key = { it.sourceOfferId }) { line ->
                    val source = sources.getValue(line.sourceOfferId).line
                    val offer = requireNotNull(bySource.getValue(line.sourceOfferId).quote.offer)
                    val unit = source.unitName.visibleLocalizedString(stateValues.appLanguage, authUiText("unit", "ед.", "бірл.", "бирдик"))
                    Column(Modifier.fillMaxWidth().border(stateValues.unfocusedBorderWidth,
                        stateValues.PlaceholderTextColor.copy(alpha = 0.25f), RoundedCornerShape(stateValues.cornerRadius))
                        .padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(source.title, color = stateValues.TextColor, fontSize = stateValues.textSize, fontWeight = FontWeight.Bold)
                        if (source.title != line.reviewedTitle) Text(authUiText("Shop label: ${line.reviewedTitle}", "Название у продавца: ${line.reviewedTitle}", "Сатушыдағы атауы: ${line.reviewedTitle}", "Дүкөндөгү аталышы: ${line.reviewedTitle}"),
                            color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                        Text(if (line.sourceOfferId == line.targetOfferId) offer.storefront.displayName
                            else "${source.shopName} → ${offer.storefront.displayName}", color = stateValues.TextColor, fontSize = stateValues.textSize)
                        Text(listOf(offer.storefront.city, offer.storefront.publicAddress).filter { it.isNotBlank() }.joinToString(" · "),
                            color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                        if (offer.storefront.pickupNote.isNotBlank()) Text(offer.storefront.pickupNote,
                            color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                        Text("${line.units} × ${line.basis.pricedAmount.toString().removeSuffix(".0")} $unit · ${marketMoneyLabel(line.reviewedSubtotalMinor, change.currencyCode)}",
                            color = stateValues.AccentColor, fontSize = stateValues.textSize, fontWeight = FontWeight.Bold)
                    }
                }
            }
            FlowRow(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!ownPending) actionButton(text = authUiText("Apply ${change.changedLines} shop changes", "Применить ${change.changedLines} замен", "${change.changedLines} ауыстыруды қолдану", "Дүкөн боюнча ${change.changedLines} өзгөртүүнү колдонуу"),
                    enabled = canConfirm, loading = shopping.changing, autoLoading = false,
                    confirmationRequired = false, onClick = {
                        // The parent's Boolean callback performs the final live-state check.
                        // Rendering alone cannot guard a click on an expired/invalidated review.
                        if (!shopping.changing && !shopping.checking && !shopping.cancelling && shopping.pending == null) {
                            if (started.elapsedNow().inWholeMilliseconds >= MARKET_BASKET_REVIEW_MAX_AGE_MILLIS || !onConfirm()) {
                                attemptRejected = true
                            }
                        }
                    })
                actionButton(text = authUiText("Back to plans", "Назад к планам", "Жоспарларға қайту", "Тарифтерге кайтуу"),
                    enabled = !shopping.changing && !shopping.checking && !shopping.cancelling && shopping.pending == null, fillMaxWidthIfTextPresent = false,
                    enabledColor = stateValues.BackgroundColor, textColor = stateValues.TextColor,
                    autoLoading = false, confirmationRequired = false, onClick = onBack)
                actionButton(text = authUiText("Close", "Закрыть", "Жабу", "Жабуу"), fillMaxWidthIfTextPresent = false,
                    enabledColor = stateValues.BackgroundColor, textColor = stateValues.TextColor,
                    autoLoading = false, confirmationRequired = false, onClick = onDismiss)
            }
        }
    }
}

// THIS IS CommonMainCompose.kt split slice: CartStockA
@file:OptIn(ExperimentalTime::class, ExperimentalFoundationApi::class)
package kz.aita

import aita.composeapp.generated.resources.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import org.jetbrains.compose.resources.DrawableResource
import kotlin.math.abs
import kotlin.math.round
import kotlin.random.Random
import kotlin.time.ExperimentalTime

data class CartConditionUiModel(
    val key: String,
    val text: String,
    val requiresManualConfirmation: Boolean = true,
    val automaticallySatisfied: Boolean = true,
    val statusText: String? = null
)

internal data class TransactionRestrictionBadgeUiModel(
    val key: String,
    val label: String,
    val iconPath: String,
    val fallbackRes: DrawableResource,
    val contentDescription: String
)

internal fun formatStockConditionMinuteBadge(minuteOfDay: Int): String {
    val clean = minuteOfDay.coerceIn(0, 23 * 60 + 59)
    val hours = clean / 60
    val minutes = clean % 60
    return "$hours.${minutes.toString().padStart(2, '0')}"
}

internal fun AppConfiguration.transactionRestrictionBadgesFor(
    goodsItem: GoodsItemDataModel,
    transactionTypeIndex: Int
): List<TransactionRestrictionBadgeUiModel> {
    val conditions = goodsItem.conditions
        .map { it.toStockConditionDataModel().normalizedStockCondition() }
        .filter { it.transactionTypeIndex == transactionTypeIndex }

    val ageBadge = conditions
        .filter { it.kind == STOCK_CONDITION_KIND_BUYER_MINIMUM_AGE }
        .maxOfOrNull { it.minimumAge.coerceAtLeast(0) }
        ?.let { age ->
            TransactionRestrictionBadgeUiModel(
                key = "age_$age",
                label = "$age+",
                iconPath = stateValues.drawablePathIconBuyerAgeRestriction,
                fallbackRes = stateValues.drawableResIconBuyerAgeRestriction.value,
                contentDescription = localizedStringResource(1012, "Buyer age")
            )
        }

    val timeBadges = conditions
        .filter { it.kind == STOCK_CONDITION_KIND_TRANSACTION_TIME_WINDOW }
        .distinctBy { it.startsAtMinutes to it.endsAtMinutes }
        .map { condition ->
            TransactionRestrictionBadgeUiModel(
                key = "time_${condition.startsAtMinutes}_${condition.endsAtMinutes}",
                label = "${formatStockConditionMinuteBadge(condition.startsAtMinutes)}–${formatStockConditionMinuteBadge(condition.endsAtMinutes)}",
                iconPath = stateValues.drawablePathIconTransactionTimeRestriction,
                fallbackRes = stateValues.drawableResIconTransactionTimeRestriction.value,
                contentDescription = localizedStringResource(1013, "Transaction time")
            )
        }

    return listOfNotNull(ageBadge) + timeBadges
}

@Composable
internal fun AppConfiguration.TransactionRestrictionBadges(
    badges: List<TransactionRestrictionBadgeUiModel>,
    textColor: Color = stateValues.TextColor
) {
    if (badges.isEmpty()) return

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        badges.take(3).forEach { badge ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(stateValues.AccentColor.copy(alpha = 0.12f))
                    .border(
                        width = stateValues.unfocusedBorderWidth,
                        color = stateValues.AccentColor.copy(alpha = 0.50f),
                        shape = RoundedCornerShape(999.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CpImage(
                    modifier = Modifier.size(13.dp),
                    url = badge.iconPath,
                    fallbackRes = badge.fallbackRes,
                    contentDescription = badge.contentDescription,
                    tintColor = stateValues.AccentColor
                )
                Text(
                    text = badge.label,
                    color = textColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun AppConfiguration.GoodsItemInCartWidget(
    modifier: Modifier = Modifier,
    index: Int? = null,
    goodsItemInCart: GoodsItemInCartDataModel,
    goodsItem: GoodsItemDataModel,
    transactionTypeIndex: Int,
    textColor: Color = stateValues.TextColor,
    onClick: ((GoodsItemDataModel) -> Unit)? = null,
    onDelete: ((GoodsItemDataModel) -> Unit)? = null,
    saleMethodId: String = SALE_METHOD_RETAIL,
    conditions: List<CartConditionUiModel> = emptyList(),
    conditionChecks: Map<String, Boolean> = emptyMap(),
    highlightedConditionKey: String? = null,
    returnReason: String = "",
    itemBatches: List<GoodsBatchDataModel> = emptyList(),
    availableSaleQuantity: Double? = null,
    returnBatchSelection: CartReturnBatchSelectionDataModel? = null,
    onConditionCheckedChange: (String, Boolean) -> Unit = { _, _ -> },
    setReturnReasonAction: (String) -> Unit = {},
    setReturnBatchSelectionAction: (CartReturnBatchSelectionDataModel) -> Unit = {},
    setSaleMethodAction: (String) -> Unit = {},
    setQuantityAction: (Double) -> Unit = {},
    increaseQuantityAction: () -> Unit,
    decreaseQuantityAction: () -> Unit
) {
    val isWeightQuantity = goodsItem.isWeightMeasurementUnit(stateValues.globalAppConfiguration) || !goodsItemInCart.quantity.roundTotal
    var showQuantityBottomSheet by rememberSaveable(goodsItemInCart.id, goodsItemInCart.quantity.total) {
        mutableStateOf(false)
    }
    var showReturnPriceBottomSheet by rememberSaveable(goodsItemInCart.id) {
        mutableStateOf(false)
    }
    val quantityStep = goodsItemInCart.quantity.pricedAmount.takeIf { it > 0.0 } ?: 1.0
    val canDecreaseQuantity = goodsItemInCart.quantity.total - quantityStep >= quantityStep - 0.000001

    val localScope = rememberCoroutineScope()
    var quantityLimitError by rememberSaveable(goodsItemInCart.id) { mutableStateOf<String?>(null) }

    fun currentAvailableSaleQuantity(): Double = availableSaleQuantity ?: availableSaleQuantityFor(goodsItem)

    fun showQuantityLimitError() {
        val message = cartQuantityLimitMessage(goodsItem, currentAvailableSaleQuantity())
        quantityLimitError = message
        localScope.launch {
            delay(2_600L)
            if (quantityLimitError == message) {
                quantityLimitError = null
            }
        }
    }

    fun runIfSaleQuantityAvailable(targetTotal: Double, action: () -> Unit) {
        if (transactionTypeIndex == 0 && targetTotal > currentAvailableSaleQuantity() + 0.000001) {
            showQuantityLimitError()
        } else {
            quantityLimitError = null
            action()
        }
    }

    if (showQuantityBottomSheet) {
        CartQuantityBottomSheet(
            goodsItem = goodsItem,
            quantity = goodsItemInCart.quantity,
            onDismiss = { showQuantityBottomSheet = false },
            onConfirm = { total ->
                runIfSaleQuantityAvailable(total) {
                    setQuantityAction(total)
                    showQuantityBottomSheet = false
                }
            }
        )
    }

    if (showReturnPriceBottomSheet) {
        CartReturnPriceBatchBottomSheet(
            goodsItem = goodsItem,
            cartItem = goodsItemInCart,
            allBatches = itemBatches,
            selection = returnBatchSelection,
            onDismiss = { showReturnPriceBottomSheet = false },
            onConfirm = { selection ->
                setReturnBatchSelectionAction(selection)
                showReturnPriceBottomSheet = false
            }
        )
    }

    Row(
        modifier
            .padding(bottom = 4.dp)
            .fillMaxWidth()
            .heightIn(min = stateValues.textFieldHeight * 1.25f)
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(
                stateValues.unfocusedBorderWidth,
                stateValues.PlaceholderTextColor,
                RoundedCornerShape(
                    stateValues.cornerRadius
                )
            )
            .run {
                onClick?.run {
                    aitaClickable(
                        interactionSource = remember {
                            MutableInteractionSource()
                        },
                        indication = ripple(color = textColor, radius = stateValues.cornerRadius),
                        onClick = {
                            this(goodsItem)
                        }
                    )
                } ?: this
            }
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(16.dp),
        ) {
            val itemName = goodsItem.name.visibleLocalizedString(stateValues.appLanguage, "Unnamed item")
            val restrictionBadges = transactionRestrictionBadgesFor(goodsItem, transactionTypeIndex)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (index != null) {
                    Text(
                        text = "${index + 1}.",
                        fontSize = stateValues.titleTextSize,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        maxLines = 1
                    )
                }

                TransactionRestrictionBadges(restrictionBadges, textColor)

                Text(
                    modifier = Modifier.weight(1f),
                    text = itemName,
                    fontSize = stateValues.titleTextSize,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (transactionTypeIndex == 0 && goodsItem.hasWholesalePrice()) {
                Spacer(modifier = Modifier.height(6.dp))

                StockCardInfoLine(
                    title = localizedStringResource(243, "Sale method"),
                    value = if (saleMethodId == SALE_METHOD_WHOLESALE) localizedStringResource(245, "Wholesale") else localizedStringResource(244, "Retail"),
                    textColor = textColor
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        SALE_METHOD_RETAIL to localizedStringResource(244, "Retail"),
                        SALE_METHOD_WHOLESALE to localizedStringResource(245, "Wholesale")
                    ).forEach { (methodId, title) ->
                        val selected = saleMethodId == methodId
                        val wholesaleAllowed = methodId != SALE_METHOD_WHOLESALE || goodsItem.isWholesaleEligible(goodsItemInCart.quantity.total)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height((stateValues.textFieldHeight.value / 1.35).dp)
                                .foregroundSubtleShadow(stateValues.cornerRadius)
                                .clip(RoundedCornerShape(stateValues.cornerRadius))
                                .background(if (selected) stateValues.AccentColor else stateValues.BackgroundColor)
                                .border(
                                    stateValues.unfocusedBorderWidth,
                                    if (selected) stateValues.AccentColor else if (wholesaleAllowed) stateValues.PlaceholderTextColor else stateValues.ErrorColor,
                                    RoundedCornerShape(stateValues.cornerRadius)
                                )
                                .aitaClickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(color = stateValues.AccentColor)
                                ) {
                                    setSaleMethodAction(methodId)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                text = title,
                                color = if (selected) stateValues.AccentTextColor else stateValues.TextColor,
                                fontSize = stateValues.smallTextSize,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (saleMethodId == SALE_METHOD_WHOLESALE && !goodsItem.isWholesaleEligible(goodsItemInCart.quantity.total)) {
                    val minimum = goodsItem.wholesaleMinQuantity?.quantityText(stateValues.appLanguage).orEmpty()
                    Text(
                        modifier = Modifier.padding(top = 4.dp),
                        text = "${localizedStringResource(250, "Wholesale from")} $minimum",
                        color = stateValues.ErrorColor,
                        fontSize = stateValues.smallTextSize,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(
                modifier = Modifier
                    .height(6.dp)
            )

            goodsItem.allBarcodeValues().run {
                if (size == 1) {
                    this[0]
                } else {
                    foldIndexed("") { index, acc, item ->
                        if (index == 0)
                            item
                        else
                            "$acc, $item"
                    }
                }
            }.run {
                StockCardInfoLine(
                    title = stateValues.stringBarcode,
                    value = this,
                    textColor = textColor
                )
            }

            goodsItem.categoryIds
                .mapNotNull { goodsCategoryName(it) }
                .joinToString(", ")
                .takeIf { it.isNotBlank() }
                ?.run {
                    StockCardInfoLine(
                        title = stateValues.stringCategory,
                        value = this,
                        textColor = textColor
                    )
                }

            val cartUnitText = goodsItemInCart.quantity.immutableUnitName.extractLocalizedString(stateValues.appLanguage).orEmpty()
            val quantityText = goodsItemInCart.quantity.quantityText(stateValues.appLanguage)
            val sortedItemBatches = remember(itemBatches, goodsItem.id, goodsItem.activeShelfBatchId) {
                itemBatches.filter { it.goodsItemId == goodsItem.id && it.isActive }.sortedForShelf(goodsItem)
            }
            val resolvedReturnSelection = if (transactionTypeIndex == 1) {
                resolveReturnBatchSelection(goodsItem, goodsItemInCart, itemBatches, returnBatchSelection)
            } else {
                null
            }
            val activeBatch = resolvedReturnSelection?.batch
                ?: if (transactionTypeIndex == 0) {
                    sortedItemBatches.bestBatchForSale(goodsItem)
                } else {
                    sortedItemBatches.firstOrNull { it.id == goodsItem.activeShelfBatchId }
                        ?: sortedItemBatches.firstOrNull()
                }
            val promotedItemPrice = if (transactionTypeIndex == 1 && resolvedReturnSelection != null) {
                PromotedPriceDataModel(resolvedReturnSelection.price, resolvedReturnSelection.price, null)
            } else {
                goodsItem.promotedPriceForTransaction(
                    transactionTypeIndex = transactionTypeIndex,
                    saleMethodId = saleMethodId,
                    quantityTotal = goodsItemInCart.quantity.total,
                    batch = activeBatch
                )
            }
            val itemPrice = promotedItemPrice.finalPrice
            val itemPriceTitle = when (transactionTypeIndex) {
                0 -> if (saleMethodId == SALE_METHOD_WHOLESALE && goodsItem.isWholesaleEligible(goodsItemInCart.quantity.total)) {
                    localizedStringResource(246, "Wholesale price")
                } else {
                    stateValues.stringSalePrice
                }
                1 -> stateValues.stringReturnPrice
                else -> stateValues.stringSupplyPrice
            }
            val lineTotal = itemPrice.price.replace(',', '.').toDoubleOrNull()?.let { it * goodsItemInCart.quantity.total }
            val availableQuantity = if (transactionTypeIndex == 0) {
                currentAvailableSaleQuantity()
            } else {
                itemBatches
                    .filter { it.status != StockBatchStatusDataModel.Deleted && it.status != StockBatchStatusDataModel.WrittenOff }
                    .sumOf { it.quantity.total }
            }

            StockCardInfoLine(
                title = localizedStringResource(263, "In cart"),
                value = quantityText,
                textColor = textColor
            )

            if (transactionTypeIndex == 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .foregroundSubtleShadow(stateValues.cornerRadius)
                        .clip(RoundedCornerShape(stateValues.cornerRadius))
                        .background(stateValues.BackgroundColor)
                        .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor, RoundedCornerShape(stateValues.cornerRadius))
                        .aitaClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(color = stateValues.AccentColor)
                        ) { showReturnPriceBottomSheet = true }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = itemPriceTitle,
                            color = stateValues.AccentColor,
                            fontSize = stateValues.smallTextSize,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = listOfNotNull(
                                "${itemPrice.price.toMoneyDouble().moneyText()} ${itemPrice.currency}".trim(),
                                resolvedReturnSelection?.batch?.let { batch ->
                                    returnBatchSummaryText(goodsItem, batch, returnCandidateBatchesFor(goodsItem, itemBatches), itemPrice.currency)
                                } ?: localizedStringResource(1315, "Returned no-stock batch")
                            ).joinToString(" • "),
                            color = textColor,
                            fontSize = stateValues.textSize,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "›",
                        color = stateValues.AccentColor,
                        fontSize = stateValues.titleTextSize,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                StockPromotionPriceInfoLine(
                    title = itemPriceTitle,
                    promotedPrice = promotedItemPrice,
                    textColor = textColor
                )
            }

            goodsItem.firstViolatedPromotionRestriction(
                transactionTypeIndex = transactionTypeIndex,
                quantityTotal = goodsItemInCart.quantity.total,
                batch = activeBatch
            )?.let { restriction ->
                StockCardInfoLine(
                    title = localizedStringResource(937, "Restriction not satisfied"),
                    value = restriction.visiblePromotionTitle(stateValues.appLanguage, localizedStringResource(936, "Promo period")),
                    textColor = stateValues.ErrorColor
                )
            }

            lineTotal?.let { totalValue ->
                StockCardInfoLine(
                    title = stateValues.stringTotal,
                    value = "${totalValue.moneyText()} ${itemPrice.currency}".trim(),
                    textColor = textColor
                )
            }

            StockCardInfoLine(
                title = localizedStringResource(1181, "Available total"),
                value = "${availableQuantity.quantityAmountText(goodsItemInCart.quantity.roundTotal)} $cartUnitText".trim(),
                textColor = textColor
            )

            activeBatch?.let { batch ->
                val batchSupplierText = stateValues.suppliers
                    .orEmpty()
                    .find { it.id == batch.supplierId }
                    ?.name
                    ?.extractLocalizedString(stateValues.appLanguage)
                    ?: batch.supplierId?.takeIf { it.isNotBlank() }
                    ?: localizedStringResource(638, "No supplier selected")

                StockCardInfoLine(
                    title = localizedStringResource(265, "Active batch"),
                    value = listOfNotNull(
                        "#${sortedItemBatches.indexOfFirst { it.id == batch.id } + 1}",
                        batchSupplierText,
                        batch.quantity.quantityText(stateValues.appLanguage),
                        batch.deliveredAtMillis?.toStockDateInputText()?.takeIf { it.isNotBlank() }?.let { "${localizedStringResource(342, "Delivered")} $it" },
                        batch.expirationDateMillis?.toStockDateInputText()?.takeIf { it.isNotBlank() }?.let { "${localizedStringResource(234, "Expires")} $it" },
                        stockBatchStatusText(batch.status)
                    ).joinToString(" • "),
                    textColor = textColor
                )
            }

            expirationReminderTextForItem(goodsItem)?.let { reminder ->
                StockCardInfoLine(
                    title = localizedStringResource(1309, "Expires very soon"),
                    value = reminder.substringAfter(": ", reminder),
                    textColor = stateValues.ErrorColor
                )
            }

            if (transactionTypeIndex == 1) {
                Spacer(modifier = Modifier.height(6.dp))
                SimpleTextInput(
                    modifier = Modifier.fillMaxWidth(),
                    value = returnReason,
                    placeholder = localizedStringResource(1308, "Return reason"),
                    singleLine = false,
                    isFocusedInitial = false,
                    autoFocus = false,
                    stateHost = null,
                    stateKey = null,
                    onTransformValue = { it.take(500) },
                    onValueChange = setReturnReasonAction
                )
            }

            val visibleNote = goodsItem.noteLocalized.extractLocalizedString(stateValues.appLanguage)
                ?: goodsItem.note
            visibleNote?.takeIf { it.isNotBlank() }?.let { note ->
                StockCardInfoLine(
                    title = localizedStringResource(266, "Note"),
                    value = note,
                    textColor = textColor
                )
            }

            if (conditions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = localizedStringResource(609, "Conditions"),
                    color = textColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(2.dp))

                conditions.forEach { condition ->
                    val checked = if (condition.requiresManualConfirmation) {
                        conditionChecks[condition.key] == true
                    } else {
                        condition.automaticallySatisfied
                    }
                    val blocked = !condition.automaticallySatisfied
                    val highlighted = highlightedConditionKey == condition.key && (!checked || blocked)
                    val highlightColor by animateColorAsState(
                        targetValue = if (highlighted || blocked) stateValues.ErrorColor.copy(alpha = 0.22f) else Color.Transparent
                    )
                    val borderColor = when {
                        highlighted || blocked -> stateValues.ErrorColor
                        checked -> stateValues.AccentColor
                        else -> stateValues.PlaceholderTextColor
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp)
                            .clip(RoundedCornerShape(stateValues.cornerRadius))
                            .background(highlightColor)
                            .border(
                                stateValues.unfocusedBorderWidth,
                                borderColor,
                                RoundedCornerShape(stateValues.cornerRadius)
                            )
                            .aitaClickable(
                                enabled = condition.requiresManualConfirmation && condition.automaticallySatisfied,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(color = if (checked) stateValues.AccentColor else stateValues.ErrorColor)
                            ) {
                                onConditionCheckedChange(condition.key, !checked)
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AitaRoundCheckbox(
                            checked = checked,
                            onCheckedChange = null,
                            blocked = blocked,
                            borderColor = borderColor,
                            containerSize = 20.dp,
                            circleSize = 20.dp
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = condition.text,
                                color = if (highlighted || blocked) stateValues.ErrorColor else textColor,
                                fontSize = stateValues.smallTextSize,
                                fontWeight = if (highlighted || checked || blocked) FontWeight.Bold else FontWeight.Normal
                            )

                            condition.statusText?.takeIf { it.isNotBlank() }?.let { status ->
                                Text(
                                    text = status,
                                    color = if (blocked) stateValues.ErrorColor else stateValues.PlaceholderTextColor,
                                    fontSize = stateValues.smallTextSize,
                                    fontWeight = if (blocked) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Spacer(
                modifier = Modifier
                    .height(6.dp)
            )

            if (isWeightQuantity) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((stateValues.textFieldHeight.value / 1.2).dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(stateValues.textFieldHeight)
                            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
                            .clip(RoundedCornerShape(stateValues.cornerRadius))
                            .background(stateValues.BackgroundColor)
                            .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor, RoundedCornerShape(stateValues.cornerRadius))
                            .aitaClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(color = stateValues.AccentColor)
                            ) {
                                showQuantityBottomSheet = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            modifier = Modifier
                                .padding(horizontal = stateValues.textFieldIconPadding),
                            text = quantityText,
                            fontWeight = FontWeight.Bold,
                            color = stateValues.TextColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .height((stateValues.textFieldHeight.value / 1.2).dp)
                ) {
                    actionButton(
                        fillMaxHeight = true,
                        text = "",
                        enabled = canDecreaseQuantity,
                        enabledColor = stateValues.ErrorColor,
                        iconPath = stateValues.drawablePathIconSubtract,
                        iconContentDescription = stateValues.stringSubtract,
                        onClick = {
                            if (canDecreaseQuantity) decreaseQuantityAction()
                        }
                    )

                    Spacer(modifier = Modifier.width(2.dp))

                    Box(
                        modifier = Modifier
                            .height(stateValues.textFieldHeight)
                            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
                            .clip(RoundedCornerShape(stateValues.cornerRadius))
                            .background(stateValues.BackgroundColor)
                            .border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor, RoundedCornerShape(stateValues.cornerRadius))
                            .aitaClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(color = stateValues.AccentColor)
                            ) {
                                showQuantityBottomSheet = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            modifier = Modifier
                                .padding(horizontal = stateValues.textFieldIconPadding),
                            text = quantityText,
                            fontWeight = FontWeight.Bold,
                            color = stateValues.TextColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(2.dp))

                    actionButton(
                        fillMaxHeight = true,
                        text = "",
                        enabledColor = stateValues.OkayColor,
                        iconPath = stateValues.drawablePathIconAdd,
                        iconContentDescription = stateValues.stringAdd,
                        onClick = {
                            runIfSaleQuantityAvailable(goodsItemInCart.quantity.total + quantityStep, increaseQuantityAction)
                        }
                    )
                }
            }

            quantityLimitError?.takeIf { it.isNotBlank() }?.let { errorText ->
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    text = errorText,
                    color = stateValues.ErrorColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Column(
            modifier = Modifier
                .padding(end = 16.dp, top = 16.dp, start = 8.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            onDelete?.let {
                actionButton(
                    text = "",
                    enabledColor = stateValues.ErrorColor,
                    iconPath = stateValues.drawablePathIconCancel,
                    iconContentDescription = stateValues.stringDelete,
                ) {
                    onDelete(goodsItem)
                }
            }

//      Spacer(
//        modifier = Modifier
//          .height(stateValues.marginTextField)
//      )
//
//      onEdit?.let {
//        actionButton(
//          text = "",
//          iconPath = stateValues.drawablePathIconEdit,
//          iconContentDescription = stateValues.drawablePathIconEdit,
//        ) {
//          onEdit(goodsItem)
//        }
//      }
        }
    }
}

@Composable
fun AppConfiguration.TransactionCartScreen() {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        val context = rememberTransactionContext()

        ScreenAppBarWidget(
            title = transactionTitle(
                context.transactionTypeIndex,
                stateValues.stringSale,
                stateValues.stringReturn,
                stateValues.stringSupply
            ),
            iconPath = when (context.transactionTypeIndex) {
                0 -> stateValues.drawablePathIconTransactionSale
                1 -> stateValues.drawablePathIconTransactionReturn
                else -> stateValues.drawablePathIconTransactionSupply
            },
            trailingIcons = buildList<Triple<String, DrawableResource, () -> Unit>> {
                add(
                    Triple(stateValues.drawablePathIconStock, stateValues.drawableResIconStock.value) {
                        openQuickStockAddSheet(
                            transactionTypeIndex = context.transactionTypeIndex,
                            clientId = context.clientId
                        )
                    }
                )

                if (stateValues.isNarrowScreen) {
                    add(
                        Triple(
                            stateValues.drawablePathIconTransactionSelection,
                            stateValues.drawableResIconTransactionSelection.value
                        ) {
                            coroutineScope.launch {
                                when (context.transactionTypeIndex) {
                                    0 -> Navigation.TransactionSale.go(NavigationScreenModel.Transaction.Selection)
                                    1 -> Navigation.TransactionReturn.go(NavigationScreenModel.Transaction.Selection)
                                    else -> Navigation.TransactionSupply.go(NavigationScreenModel.Transaction.Selection)
                                }
                            }
                        }
                    )
                }
            }
        )

        val goodsInCart by getCartState(
            context.transactionTypeIndex,
            context.clientId
        ).collectAsState()
        val saleMethodIds by cartSaleMethodIdsState.collectAsState()
        val cartReturnReasons by getCartReturnReasonsState().collectAsState()
        val returnBatchSelections by getCartReturnBatchSelectionsState().collectAsState()
        val cartConditionChecks by getCartConditionChecksState().collectAsState()
        val cartScrollStates by getTransactionCartScrollStatesState().collectAsState()
        val cartStateKey = transactionSupplySupplierKey(context.transactionTypeIndex, context.clientId)
        val persistedCartScrollState = cartScrollStates[cartStateKey]
        val cartListState = rememberLazyListState(
            initialFirstVisibleItemIndex = persistedCartScrollState?.firstVisibleItemIndex ?: 0,
            initialFirstVisibleItemScrollOffset = persistedCartScrollState?.firstVisibleItemScrollOffset ?: 0
        )
        var restoredCartScrollKey by rememberSaveable { mutableStateOf("") }
        var highlightedConditionKey by rememberSaveable { mutableStateOf<String?>(null) }
        val acceptableConditionText = localizedStringResource(613, "Item in acceptable condition")
        val rememberedCartAvailability = remember { mutableStateMapOf<String, Double>() }
        var cartStockUpdateDialogText by rememberSaveable { mutableStateOf<String?>(null) }

        LaunchedEffect(cartStateKey, persistedCartScrollState?.updatedAtMillis, goodsInCart.size) {
            val target = persistedCartScrollState ?: return@LaunchedEffect
            if (restoredCartScrollKey == cartStateKey || goodsInCart.isEmpty()) return@LaunchedEffect

            cartListState.scrollToItem(
                target.firstVisibleItemIndex.coerceIn(0, goodsInCart.lastIndex),
                target.firstVisibleItemScrollOffset.coerceAtLeast(0)
            )
            restoredCartScrollKey = cartStateKey
        }

        LaunchedEffect(cartStateKey, cartListState) {
            var pendingSaveJob: kotlinx.coroutines.Job? = null
            snapshotFlow { cartListState.firstVisibleItemIndex to cartListState.firstVisibleItemScrollOffset }
                .collect { (firstIndex, firstOffset) ->
                    pendingSaveJob?.cancel()
                    pendingSaveJob = launch {
                        delay(220L)
                        setTransactionCartScrollState(
                            TransactionCartScrollStateDataModel(
                                transactionTypeIndex = context.transactionTypeIndex,
                                clientId = context.clientId,
                                firstVisibleItemIndex = firstIndex,
                                firstVisibleItemScrollOffset = firstOffset
                            )
                        )
                    }
                }
        }

        val stockById = remember(stateValues.stock) {
            stateValues.stock.orEmpty().associateBy { it.id }
        }
        val batchesByGoodsItemId = remember(stateValues.stockBatches, stateValues.activeStoreId) {
            stateValues.stockBatches.orEmpty()
                .filter { batch ->
                    batch.isActive && batchBelongsToInventoryStoreForUi(batch.storeId, stateValues.activeStoreId)
                }
                .groupBy { it.goodsItemId }
        }
        val cartItemsWithGoods = remember(goodsInCart, stockById) {
            goodsInCart.mapIndexedNotNull { index, cartItem ->
                stockById[cartItem.id]?.let { Triple(index, cartItem, it) }
            }
        }
        val cartAvailabilityByItem = remember(goodsInCart, batchesByGoodsItemId) {
            goodsInCart.associate { cartItem ->
                cartItem.id to batchesByGoodsItemId[cartItem.id].orEmpty()
                    .filter {
                        it.status != StockBatchStatusDataModel.Ordered &&
                                it.status != StockBatchStatusDataModel.Reserved &&
                                it.status != StockBatchStatusDataModel.Deleted &&
                                it.status != StockBatchStatusDataModel.InTransit &&
                                it.status != StockBatchStatusDataModel.WrittenOff &&
                                it.status != StockBatchStatusDataModel.SoldOut
                    }
                    .sumOf { it.quantity.total }
            }
        }

        LaunchedEffect(cartAvailabilityByItem) {
            val changedLines = cartItemsWithGoods.mapNotNull { (_, cartItem, goodsItem) ->
                val previous = rememberedCartAvailability[cartItem.id]
                val current = cartAvailabilityByItem[cartItem.id] ?: return@mapNotNull null
                if (previous == null || abs(previous - current) <= 0.000001) {
                    null
                } else {
                    val unit = cartItem.quantity.immutableUnitName.extractLocalizedString(stateValues.appLanguage).orEmpty()
                    val beforeText = "${previous.quantityAmountText(cartItem.quantity.roundTotal)} $unit".trim()
                    val nowText = "${current.quantityAmountText(cartItem.quantity.roundTotal)} $unit".trim()
                    val cartText = cartItem.quantity.quantityText(stateValues.appLanguage)
                    "${goodsItem.name.visibleLocalizedString(stateValues.appLanguage, localizedStringResource(365, "Goods item"))} — ${localizedStringResource(1061, "Before")}: $beforeText, ${localizedStringResource(1062, "Now")}: $nowText, ${localizedStringResource(1064, "In cart")}: $cartText"
                }
            }

            rememberedCartAvailability.clear()
            rememberedCartAvailability.putAll(cartAvailabilityByItem)

            if (changedLines.isNotEmpty()) {
                val message = buildString {
                    append(localizedStringResource(1060, "Available quantity changed while this cart was open."))
                    append("\n\n")
                    append(changedLines.take(8).joinToString("\n"))
                    if (changedLines.size > 8) append("\n…")
                }
                cartStockUpdateDialogText = message
                postInAppNotification(message, NotificationType.Neutral, transient = true)
            }
        }

        cartStockUpdateDialogText?.let { message ->
            ModalDialogWidget(
                title = localizedStringResource(1059, "Cart stock updated"),
                subTitle = message,
                negativeButtonText = localizedStringResource(319, "Close"),
                positiveButtonText = localizedStringResource(237, "Refresh"),
                onDismiss = { cartStockUpdateDialogText = null },
                negativeAction = { cartStockUpdateDialogText = null },
                positiveAction = {
                    cartStockUpdateDialogText = null
                    stateValues.activeStoreId?.let { storeId ->
                        getStock(storeId)
                        getStockBatches(storeId)
                    }
                }
            )
        }

        fun cartConditionsFor(cartItem: GoodsItemInCartDataModel, goodsItem: GoodsItemDataModel): List<CartConditionUiModel> {
            val stockConditions = goodsItem.conditions
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .map { raw -> raw to raw.toStockConditionDataModel().normalizedStockCondition() }
                .filter { (_, condition) -> condition.transactionTypeIndex == context.transactionTypeIndex }
                .mapIndexed { conditionIndex, (raw, condition) ->
                    val isTimeWindow = condition.kind == STOCK_CONDITION_KIND_TRANSACTION_TIME_WINDOW
                    val timeWindowSatisfied = if (isTimeWindow) {
                        isMinuteInsideStockConditionWindow(
                            currentStockConditionLocalMinuteOfDay(),
                            condition.startsAtMinutes,
                            condition.endsAtMinutes
                        )
                    } else {
                        true
                    }

                    CartConditionUiModel(
                        key = "${context.transactionTypeIndex}:${context.clientId}:${cartItem.id}:stock:$conditionIndex:${raw.hashCode()}",
                        text = visibleStockConditionText(condition),
                        requiresManualConfirmation = !isTimeWindow,
                        automaticallySatisfied = timeWindowSatisfied,
                        statusText = stockConditionStatusText(condition)
                    )
                }

            val transactionCondition = if (context.transactionTypeIndex == 1 || context.transactionTypeIndex == 2) {
                listOf(
                    CartConditionUiModel(
                        key = "${context.transactionTypeIndex}:${context.clientId}:${cartItem.id}:acceptable_condition",
                        text = acceptableConditionText
                    )
                )
            } else {
                emptyList()
            }

            return stockConditions + transactionCondition
        }

        val conditionsByCartId = remember(
            cartItemsWithGoods,
            stateValues.appLanguage,
            context.transactionTypeIndex,
            context.clientId,
            acceptableConditionText
        ) {
            cartItemsWithGoods.associate { (_, cartItem, goodsItem) ->
                cartItem.id to cartConditionsFor(cartItem, goodsItem)
            }
        }
        val validCartConditionKeys = remember(conditionsByCartId) {
            conditionsByCartId.values.flatten().map { it.key }.toSet()
        }

        LaunchedEffect(context.transactionTypeIndex, context.clientId, validCartConditionKeys) {
            pruneCartConditionChecks(context.transactionTypeIndex, context.clientId, validCartConditionKeys)
        }

        if (goodsInCart.isEmpty()) {
            MessageText(
                modifier = Modifier.fillMaxSize(),
                text = stateValues.stringCartEmpty
            )
        } else {
            LazyColumn(
                state = cartListState,
                modifier = Modifier
                    .weight(1f)
                    .padding(stateValues.marginTextField)
            ) {
                itemsIndexed(
                    items = cartItemsWithGoods,
                    key = { _, triple -> triple.second.id }
                ) { _, (index, cartItem, goodsItem) ->
                    val itemBatches = batchesByGoodsItemId[cartItem.id].orEmpty()
                    GoodsItemInCartWidget(
                        modifier = Modifier.fillParentMaxWidth(),
                        index = index,
                        goodsItemInCart = cartItem,
                        goodsItem = goodsItem,
                        transactionTypeIndex = context.transactionTypeIndex,
                        saleMethodId = saleMethodIds["${context.transactionTypeIndex}:${context.clientId}:${cartItem.id}"] ?: SALE_METHOD_RETAIL,
                        conditions = conditionsByCartId[cartItem.id].orEmpty(),
                        conditionChecks = cartConditionChecks,
                        highlightedConditionKey = highlightedConditionKey,
                        returnReason = cartReturnReasons[cartReturnReasonKey(context.transactionTypeIndex, context.clientId, cartItem.id)].orEmpty(),
                        itemBatches = itemBatches,
                        availableSaleQuantity = cartAvailabilityByItem[cartItem.id],
                        returnBatchSelection = returnBatchSelections[cartReturnBatchSelectionKey(context.transactionTypeIndex, context.clientId, cartItem.id)],
                        onConditionCheckedChange = { conditionKey, checked ->
                            setCartConditionChecked(conditionKey, checked)
                        },
                        setReturnReasonAction = { reason ->
                            setCartReturnReason(
                                transactionTypeIndex = context.transactionTypeIndex,
                                clientId = context.clientId,
                                goodsItemId = cartItem.id,
                                reason = reason
                            )
                        },
                        setReturnBatchSelectionAction = { selection ->
                            setCartReturnBatchSelection(
                                transactionTypeIndex = context.transactionTypeIndex,
                                clientId = context.clientId,
                                goodsItemId = cartItem.id,
                                selection = selection
                            )
                        },
                        setSaleMethodAction = { methodId ->
                            setCartSaleMethodId(
                                transactionTypeIndex = context.transactionTypeIndex,
                                clientId = context.clientId,
                                goodsItemId = cartItem.id,
                                saleMethodId = methodId
                            )
                        },
                        onDelete = {
                            deleteCartById(
                                id = cartItem.id,
                                transactionTypeIndex = context.transactionTypeIndex,
                                clientId = context.clientId
                            )
                        },
                        setQuantityAction = { total ->
                            setCartQuantity(
                                id = cartItem.id,
                                transactionTypeIndex = context.transactionTypeIndex,
                                clientId = context.clientId,
                                current = cartItem.quantity,
                                total = total
                            )
                        },
                        increaseQuantityAction = {
                            changeCartQuantity(
                                id = cartItem.id,
                                transactionTypeIndex = context.transactionTypeIndex,
                                clientId = context.clientId,
                                current = cartItem.quantity,
                                deltaSteps = 1
                            )
                        },
                        decreaseQuantityAction = {
                            changeCartQuantity(
                                id = cartItem.id,
                                transactionTypeIndex = context.transactionTypeIndex,
                                clientId = context.clientId,
                                current = cartItem.quantity,
                                deltaSteps = -1
                            )
                        }
                    )
                }
            }
        }

        val currentTransactionScreens by Navigation
            .getCurrentTransactionScreens(
                context.transactionTypeIndex,
                context.clientId,
                stateValues.isNarrowScreen
            )
            .collectAsState()

        val lastScreen = currentTransactionScreens.lastOrNull()

        val invalidWholesaleCartItems = remember(cartItemsWithGoods, saleMethodIds, context.transactionTypeIndex, context.clientId, stateValues.appLanguage) {
            if (context.transactionTypeIndex != 0) {
                emptyList()
            } else {
                cartItemsWithGoods.mapNotNull { (_, cartItem, goodsItem) ->
                    val selectedMethodId = saleMethodIds["${context.transactionTypeIndex}:${context.clientId}:${cartItem.id}"]
                    if (selectedMethodId == SALE_METHOD_WHOLESALE && !goodsItem.isWholesaleEligible(cartItem.quantity.total)) {
                        goodsItem.name.extractLocalizedString(stateValues.appLanguage) ?: goodsItem.firstBarcode().ifBlank { cartItem.id }
                    } else {
                        null
                    }
                }
            }
        }

        val firstUncheckedCondition = cartItemsWithGoods.firstNotNullOfOrNull { (index, cartItem, _) ->
            conditionsByCartId[cartItem.id].orEmpty().firstOrNull { condition ->
                !condition.automaticallySatisfied || (condition.requiresManualConfirmation && cartConditionChecks[condition.key] != true)
            }?.let { condition -> index to condition }
        }

        fun activeCartBatch(goodsItem: GoodsItemDataModel, cartItem: GoodsItemInCartDataModel): GoodsBatchDataModel? {
            val itemBatches = batchesByGoodsItemId[goodsItem.id].orEmpty()
            return if (context.transactionTypeIndex == 1) {
                resolveReturnBatchSelection(
                    goodsItem = goodsItem,
                    cartItem = cartItem,
                    allBatches = itemBatches,
                    selection = returnBatchSelections[cartReturnBatchSelectionKey(context.transactionTypeIndex, context.clientId, goodsItem.id)]
                ).batch
            } else {
                itemBatches.sortedForShelf(goodsItem).firstOrNull { it.id == goodsItem.activeShelfBatchId }
                    ?: itemBatches.sortedForShelf(goodsItem).firstOrNull()
            }
        }

        val invalidPromotionRestrictions = cartItemsWithGoods.mapNotNull { (index, cartItem, goodsItem) ->
            val activeBatch = activeCartBatch(goodsItem, cartItem)
            goodsItem.firstViolatedPromotionRestriction(
                transactionTypeIndex = context.transactionTypeIndex,
                quantityTotal = cartItem.quantity.total,
                batch = activeBatch
            )?.let { restriction ->
                index to (goodsItem.name.extractLocalizedString(stateValues.appLanguage)
                    ?: restriction.visiblePromotionTitle(stateValues.appLanguage, localizedStringResource(936, "Promo period")))
            }
        }

        val cartTotalPrice = cartItemsWithGoods.sumOf { (_, cartItem, goodsItem) ->
            val saleMethodId = saleMethodIds["${context.transactionTypeIndex}:${context.clientId}:${cartItem.id}"] ?: SALE_METHOD_RETAIL
            val itemBatches = batchesByGoodsItemId[goodsItem.id].orEmpty()
            val price = if (context.transactionTypeIndex == 1) {
                resolveReturnBatchSelection(
                    goodsItem = goodsItem,
                    cartItem = cartItem,
                    allBatches = itemBatches,
                    selection = returnBatchSelections[cartReturnBatchSelectionKey(context.transactionTypeIndex, context.clientId, goodsItem.id)]
                ).price
            } else {
                goodsItem.priceForTransaction(
                    transactionTypeIndex = context.transactionTypeIndex,
                    saleMethodId = saleMethodId,
                    quantityTotal = cartItem.quantity.total,
                    batch = activeCartBatch(goodsItem, cartItem)
                )
            }
            price.price.toMoneyDouble() * cartItem.quantity.total
        }

        val cartCurrency = cartItemsWithGoods.firstNotNullOfOrNull { (_, cartItem, goodsItem) ->
            val saleMethodId = saleMethodIds["${context.transactionTypeIndex}:${context.clientId}:${cartItem.id}"] ?: SALE_METHOD_RETAIL
            val itemBatches = batchesByGoodsItemId[goodsItem.id].orEmpty()
            val price = if (context.transactionTypeIndex == 1) {
                resolveReturnBatchSelection(
                    goodsItem = goodsItem,
                    cartItem = cartItem,
                    allBatches = itemBatches,
                    selection = returnBatchSelections[cartReturnBatchSelectionKey(context.transactionTypeIndex, context.clientId, goodsItem.id)]
                ).price
            } else {
                goodsItem.priceForTransaction(context.transactionTypeIndex, saleMethodId, cartItem.quantity.total, activeCartBatch(goodsItem, cartItem))
            }
            price.currency.takeIf { it.isNotBlank() }
        } ?: defaultTransactionCurrencyCode()

        val cartPiecesCount = cartItemsWithGoods.sumOf { (_, cartItem, _) ->
            if (cartItem.quantity.roundTotal) round(cartItem.quantity.total).toInt().coerceAtLeast(0) else 1
        }

        if (
            goodsInCart.isNotEmpty() &&
            (
                    lastScreen is NavigationScreenModel.Transaction.Cart ||
                            lastScreen is NavigationScreenModel.Transaction.Selection
                    )
        ) {
            val cartSummaryText = "${localizedStringResource(614, "Cart total")}: ${cartTotalPrice.moneyText()} $cartCurrency • $cartPiecesCount ${localizedStringResource(615, "items")}"
            val cartWarningText = when {
                invalidPromotionRestrictions.isNotEmpty() -> "${localizedStringResource(937, "Restriction not satisfied")}: ${invalidPromotionRestrictions.joinToString(", ") { it.second }}"
                invalidWholesaleCartItems.isNotEmpty() -> "${localizedStringResource(251, "Not enough for wholesale")}: ${invalidWholesaleCartItems.joinToString(", ")}"
                firstUncheckedCondition != null -> localizedStringResource(612, "Check all conditions")
                else -> null
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = cartSummaryText,
                        color = stateValues.TextColor,
                        fontSize = stateValues.accentTextSize,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    cartWarningText?.let { warning ->
                        Text(
                            text = warning,
                            color = stateValues.ErrorColor,
                            fontSize = stateValues.smallTextSize,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                actionButton(
                    modifier = Modifier.widthIn(min = 120.dp, max = 180.dp),
                    fillMaxWidthIfTextPresent = false,
                    text = stateValues.stringPayment,
                    enabled = stateValues.latestNotification == null && invalidPromotionRestrictions.isEmpty() && invalidWholesaleCartItems.isEmpty() && firstUncheckedCondition == null,
                    onDisabledClick = {
                        val firstRestriction = invalidPromotionRestrictions.firstOrNull()

                        if (firstRestriction != null) {
                            postInAppNotification(localizedStringResource(937, "Restriction not satisfied"), NotificationType.Negative, transient = true)
                            coroutineScope.launch { cartListState.animateScrollToItem(firstRestriction.first) }
                        } else {
                            val first = firstUncheckedCondition
                            if (first != null) {
                                postInAppNotification(localizedStringResource(612, "Check all conditions"), NotificationType.Negative, transient = true)
                                coroutineScope.launch {
                                    cartListState.animateScrollToItem(first.first)
                                    highlightedConditionKey = first.second.key
                                    delay(2000)
                                    if (highlightedConditionKey == first.second.key) {
                                        highlightedConditionKey = null
                                    }
                                }
                            }
                        }
                    },
                    onClick = {
                        coroutineScope.launch {
                            when (context.transactionTypeIndex) {
                                0 -> Navigation.TransactionSale.go(NavigationScreenModel.Transaction.Payment)
                                1 -> Navigation.TransactionReturn.go(NavigationScreenModel.Transaction.Payment)
                                else -> Navigation.TransactionSupply.go(NavigationScreenModel.Transaction.Payment)
                            }
                        }
                    }
                )
            }
        }
    }
}

//@Composable
//fun AppConfiguration.TransactionCartScreen() {
//  Column(
//    modifier = Modifier
//      .fillMaxSize()
//  ) {
//    val transactionTypeIndex = when (stateValues.navigationScreensMain.last()) {
//      is NavigationScreenModel.Transaction.MainSale -> {
//        0
//      }
//      is NavigationScreenModel.Transaction.MainReturn -> {
//        1
//      }
//      else -> {
//        2
//      }
//    }
//
//    val clientId = when(transactionTypeIndex) {
//      0 -> {
//        stateValues.navigationTransactionSaleClientId
//      }
//      1 -> {
//        stateValues.navigationTransactionReturnClientId
//      }
//      else -> {
//        stateValues.navigationTransactionSupplyClientId
//      }
//    }
//
//    ScreenAppBarWidget(
//      title = when (transactionTypeIndex) {
//        1 -> stateValues.stringReturn
//        2 -> stateValues.stringSupply
//        else -> stateValues.stringSale
//      },
//      iconPath = when (transactionTypeIndex) {
//        1 -> stateValues.drawablePathIconTransactionReturn
//        2 -> stateValues.drawablePathIconTransactionSupply
//        else -> stateValues.drawablePathIconTransactionSale
//      }
//    )
//
//    val goodsInCart by getCartState(transactionTypeIndex, clientId).collectAsState()
//
//    if (goodsInCart.isEmpty()) {
//      MessageText(
//        modifier = Modifier
//          .fillMaxSize(),
//        stateValues.stringCartEmpty
//      )
//    } else {
//      LazyColumn(
//        modifier = Modifier
//          .weight(1f)
//          .padding(stateValues.marginTextField),
//      ) {
//        itemsIndexed(goodsInCart) { index, item ->
//          stateValues.stock?.find {
//            item.id == it.id
//          }?.let {
//            GoodsItemInCartWidget(
//              index = index,
//              goodsItemInCart = item,
//              goodsItem = it,
//              onDelete = {
//                deleteCartById(item.id, transactionTypeIndex, clientId)
//              },
//              increaseQuantityAction = {
//                  upsertCart(
//                    id = it.id,
//                    transactionTypeIndex,
//                    clientId,
//                    QuantityDataModel("", immutableUnitName = listOf(LocalizedStringDataModel("main", "pc.")), roundTotal = true) // TODO it.quantity.copy(total = it.quantity.total + it.quantity.pricedAmount)
//                  )
//              },
//              decreaseQuantityAction = {
//                upsertCart(
//                  id = it.id,
//                  transactionTypeIndex,
//                  clientId,
//                  QuantityDataModel("", immutableUnitName = listOf(LocalizedStringDataModel("main", "pc.")), roundTotal = true) // TODO it.quantity.copy(total = it.quantity.total + it.quantity.pricedAmount)
//                )
//              }
//            )
//          }
//        }
//      }
//    }
//
//    val currentTransactionScreens by Navigation.getCurrentTransactionScreens(transactionTypeIndex, clientId, stateValues.isNarrowScreen).collectAsState()
//
//    if (goodsInCart.isNotEmpty() && currentTransactionScreens.run { last() is NavigationScreenModel.Transaction.Cart || last() is NavigationScreenModel.Transaction.Selection })
//      Column(
//        modifier = Modifier
//          .fillMaxWidth()
//          .padding(horizontal = 8.dp)
//      ) {
//        Spacer(modifier = Modifier.height(4.dp))
//
//        actionButton(
//          text = stateValues.stringPayment,
//          enabled = stateValues.latestNotification == null,
//          onClick = {
//            coroutineScope.launch {
//              when (transactionTypeIndex) {
//                0 -> {
//                  Navigation.TransactionSale.go(NavigationScreenModel.Transaction.Payment)
//                }
//                1 -> {
//                  Navigation.TransactionReturn.go(NavigationScreenModel.Transaction.Payment)
//                }
//                else -> {
//                  Navigation.TransactionSupply.go(NavigationScreenModel.Transaction.Payment)
//                }
//
//              }
//            }
//          }
//        )
//
//        Spacer(modifier = Modifier.height(4.dp))
//      }
//  }
//}


internal fun decodeLazyListScrollState(encoded: String?): Pair<Int, Int> {
    val parts = encoded
        ?.split(':')
        ?.takeIf { it.size == 2 }
        ?: return 0 to 0
    return (parts.getOrNull(0)?.toIntOrNull()?.coerceAtLeast(0) ?: 0) to
            (parts.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(0) ?: 0)
}

internal fun encodeLazyListScrollState(index: Int, offset: Int): String =
    "${index.coerceAtLeast(0)}:${offset.coerceAtLeast(0)}"

@Composable
internal fun AppConfiguration.rememberPersistentLazyListState(
    stateHost: StateHost,
    stateKey: String
): LazyListState {
    val hostState by stateHost.state.collectAsState()
    val persisted = hostState[stateKey]
    val initial = remember(stateKey) { decodeLazyListScrollState(persisted) }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initial.first,
        initialFirstVisibleItemScrollOffset = initial.second
    )
    var restoredKey by rememberSaveable(stateKey) { mutableStateOf("") }

    LaunchedEffect(stateKey, persisted) {
        val encoded = persisted ?: return@LaunchedEffect
        val restoreKey = "$stateKey|$encoded"
        if (restoreKey == restoredKey) return@LaunchedEffect
        val (index, offset) = decodeLazyListScrollState(encoded)
        runCatching { listState.scrollToItem(index, offset) }
        restoredKey = restoreKey
    }

    LaunchedEffect(stateHost, stateKey, listState) {
        var pendingSaveJob: kotlinx.coroutines.Job? = null
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                pendingSaveJob?.cancel()
                pendingSaveJob = launch {
                    delay(220L)
                    stateHost.setState(stateKey to encodeLazyListScrollState(index, offset))
                }
            }
    }

    return listState
}

internal fun menuPersistentScrollSuffix(raw: String): String =
    raw
        .ifBlank { "main" }
        .map { char -> if (char.isLetterOrDigit() || char == '_' || char == '-') char else '_' }
        .joinToString("")
        .take(80)
        .ifBlank { "main" }

@Composable
internal fun AppConfiguration.rememberMenuScreenLazyListState(
    screen: NavigationScreenModel.Menu,
    suffix: String = "main"
): LazyListState = rememberPersistentLazyListState(
    stateHost = screen,
    stateKey = listOf(
        "menu_scroll_v2",
        if (stateValues.isNarrowScreen) "narrow" else "wide",
        screen.route,
        menuPersistentScrollSuffix(suffix)
    ).joinToString(":")
)

@Composable
fun AppConfiguration.tabRowWidget(
    modifier: Modifier = Modifier,
    tabs: List<TabContent>,

    selectedIndexInitial: String = tabs.firstOrNull()?.id.orEmpty(),
    persistSelection: Boolean = true,
    selectedContainerColor: Color = stateValues.AccentColor,
    unselectedContainerColor: Color = Color.Transparent,

    selectedTextColor: Color = stateValues.AccentTextColor,
    unselectedTextColor: Color = stateValues.TextColor,

    cornerRadius: Dp = stateValues.cornerRadius,

    textSize: TextUnit = stateValues.textSize,

    titleText: String = "",
    titleTextSize: TextUnit = stateValues.accentTextSize,
    titleTextColor: Color = stateValues.TextColor,
): TabRowContent {
    val tabsKey = remember(tabs) { tabs.joinToString(separator = "|") { it.id } }
    val savedSelectedIdState = rememberSaveable(tabsKey, selectedIndexInitial) { mutableStateOf(selectedIndexInitial) }
    val transientSelectedIdState = remember(tabsKey, selectedIndexInitial) { mutableStateOf(selectedIndexInitial) }
    val selectedIdState = if (persistSelection) savedSelectedIdState else transientSelectedIdState
    var selectedId by selectedIdState

    LaunchedEffect(selectedIndexInitial, tabsKey) {
        if (tabs.any { it.id == selectedIndexInitial } && selectedId != selectedIndexInitial) {
            selectedId = selectedIndexInitial
        }
    }

    LaunchedEffect(tabsKey) {
        if (tabs.none { it.id == selectedId }) {
            selectedId = selectedIndexInitial
        }
    }

    if (tabs.isNotEmpty()) {
        Column(modifier = modifier) {
            titleText.takeIf { it.isNotEmpty() && it.isNotBlank() }?.apply {
                Text(
                    text = this,
                    modifier = Modifier,
                    style = TextStyle(
                        color = titleTextColor,
                        fontSize = titleTextSize,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            val scrollableTabs = tabs.size > 4
            val tabScrollState = rememberScrollState()

            Row(
                modifier = Modifier
                    .padding(2.dp)
                    .foregroundTactileShadow(cornerRadius, elevated = false)
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(stateValues.BackgroundColor)
                    .run { if (scrollableTabs) horizontalScroll(tabScrollState) else fillMaxWidth() }
            ) {
                tabs.forEachIndexed { _, tab ->
                    val isSelected = tab.id == selectedId
                    val tabItemModifier = if (scrollableTabs) Modifier.widthIn(min = 106.dp) else Modifier.weight(1f)

                    val containerColor by animateColorAsState(
                        targetValue = if (isSelected) selectedContainerColor else unselectedContainerColor
                    )
                    val textColor by animateColorAsState(
                        targetValue = if (isSelected) selectedTextColor else unselectedTextColor
                    )

                    Box(
                        modifier = tabItemModifier
                            .background(containerColor)
                            .aitaClickable(
                                interactionSource = remember {
                                    MutableInteractionSource()
                                },
                                indication = ripple(color = textColor)
                            ) {
                                selectedId = tab.id

                                tab.onClick?.invoke(tab.id)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab.text,
                            modifier = Modifier
                                .padding(6.dp),
                            fontSize = textSize,
                            color = textColor,
                            fontWeight = accentTextWeight(textColor, stateValues.AccentColor),
                            style = TextStyle(shadow = accentTextShadow(textColor, stateValues.AccentColor)),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    return TabRowContent(selectedId)
}

data class TabRowContent(
    var id: String
)

class TabContent(
    val id: String,
    val text: String,
    val onClick: ((String) -> Unit)? = null
)

internal fun tabLabelWithCount(label: String, count: Int): String =
    "${label.trim()} (${count.coerceAtLeast(0)})"

internal fun List<StoreDataModel>?.findStoreOrBranchForUi(id: String?): StoreDataModel? =
    this.orEmpty().findStoreOrBranch(id)

@Composable
internal fun AppConfiguration.StoreBranchHeader(parentStore: StoreDataModel) {
    Text(
        text = "${localizedStringResource(532, "Branches")} • ${parentStore.name.extractLocalizedString(stateValues.appLanguage).orEmpty()}",
        color = stateValues.AccentColor,
        fontSize = stateValues.textSize,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = stateValues.marginTextField, top = 6.dp, bottom = 4.dp)
    )
}

@Composable
fun AppConfiguration.StoreWidget(
    modifier: Modifier = Modifier,
    store: StoreDataModel,
    textColor: Color = stateValues.TextColor,
    onDelete: ((StoreDataModel) -> Unit)? = null,
    onEdit: ((StoreDataModel) -> Unit)? = null,
    onSetActive: ((StoreDataModel) -> Unit)? = null,
    onSetInactive: ((StoreDataModel) -> Unit)? = null
) {
    val isActiveWidget = onSetInactive != null

    Row(
        modifier
            .padding(bottom = 4.dp)
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .run {
                if (onEdit != null) {
                    aitaClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = stateValues.AccentColor)
                    ) { onEdit(store) }
                } else this
            }
            .border(
                if (isActiveWidget) stateValues.focusedBorderWidth else stateValues.unfocusedBorderWidth,
                if (isActiveWidget) stateValues.OkayColor else stateValues.PlaceholderTextColor,
                RoundedCornerShape(
                    stateValues.cornerRadius
                )
            )
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 8.dp, start = (if (isActiveWidget) 8 else 16).dp, end = 16.dp, bottom = 12.dp),
        ) {
            if (store.isBranchStore()) {
                Text(
                    text = localizedStringResource(530, "Branch"),
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold,
                    color = stateValues.AccentColor,
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                )
            }

            if (isActiveWidget)
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    actionButton(
                        text = "",
                        modifier = Modifier.padding(8.dp),
                        enabled = false,
                        disabledColor = stateValues.OkayColor,
                        iconPath = stateValues.drawablePathIconCheck,
                        iconContentDescription = stateValues.stringSelect,
                    ) {

                    }

                    Text(
                        text = stateValues.stringActiveStore,
                        fontSize = stateValues.titleTextSize,
                        fontWeight = FontWeight.Bold,
                        color = stateValues.OkayColor
                    )
                }

            Column(
                modifier = Modifier
                    .padding(start = 8.dp, end = 8.dp),
            ) {
                store.name.extractLocalizedString(stateValues.appLanguage)?.run {
                    Text(
                        text = this,
                        fontSize = stateValues.titleTextSize,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }

                store.alias.takeIf { it.any { item -> item.value.isNotEmpty() && item.value.isNotBlank() } }
                    ?.extractLocalizedString(stateValues.appLanguage)?.let {
                        Text(
                            text = it,
                            fontSize = stateValues.textSize,
                            color = textColor
                        )
                    }

                store.description.takeIf { it.any { item -> item.value.isNotEmpty() && item.value.isNotBlank() } }
                    ?.extractLocalizedString(stateValues.appLanguage)?.let {
                        Text(
                            text = it,
                            fontSize = stateValues.textSize,
                            color = textColor
                        )
                    }

                val companyFormsText = store.companyForms.takeIf { it.isNotEmpty() }?.let { companyForms ->
                    StringBuilder()
                        .also {
                            companyForms.forEachIndexed { index, companyForm ->
                                val name = companyForm.name.extractLocalizedString(stateValues.appLanguage)

                                name?.run {
                                    if (index == companyForms.lastIndex)
                                        it.append(name)
                                    else
                                        it.append("$name, ")
                                }
                            }
                        }
                        .toString()
                }

                if (!store.isBranchStore()) {
                    Text(
                        text = companyFormsText.takeIf { it?.isNotEmpty() == true } ?: localizedStringResource(517, "No company form specified"),
                        fontSize = stateValues.textSize,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }

                Text(
                    text = store.displayAddress(stateValues.appLanguage),
                    fontSize = stateValues.textSize,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )

                if (!store.isBranchStore() && store.legalId.isNotBlank()) {
                    Text(
                        text = "${localizedStringResource(523, "Legal ID")}: ${store.legalId}",
                        fontSize = stateValues.smallTextSize,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }

                if (!store.isBranchStore() && store.branches.isNotEmpty()) {
                    Text(
                        text = "${localizedStringResource(532, "Branches")}: ${store.branches.size}",
                        fontSize = stateValues.smallTextSize,
                        fontWeight = FontWeight.Bold,
                        color = stateValues.AccentColor
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = localizedStringResource(502, "Public store ID"),
                        fontSize = stateValues.smallTextSize,
                        color = stateValues.PlaceholderTextColor
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            modifier = Modifier.weight(1f, fill = false),
                            text = store.visibleEmploymentId(),
                            fontSize = stateValues.textSize,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        ClipboardCopyButton(textToCopy = store.visibleEmploymentId())
                    }
                    Text(
                        text = localizedStringResource(519, "Use this ID when requesting employment"),
                        fontSize = stateValues.smallTextSize,
                        color = stateValues.PlaceholderTextColor
                    )
                }

                Spacer(
                    modifier = Modifier
                        .height(stateValues.marginTextField)
                )
            }
        }

        Column(
            modifier = Modifier
                .padding(end = 16.dp, top = 16.dp, start = 8.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            onDelete?.let {
                actionButton(
                    text = "",
                    enabledColor = stateValues.ErrorColor,
                    iconPath = stateValues.drawablePathIconDelete,
                    iconContentDescription = stateValues.drawablePathIconDelete,
                ) {
                    onDelete(store)
                }
            }

            Spacer(
                modifier = Modifier
                    .height(stateValues.marginTextField)
            )

            onEdit?.let {
                actionButton(
                    text = "",
                    iconPath = stateValues.drawablePathIconEdit,
                    iconContentDescription = stateValues.drawablePathIconEdit,
                ) {
                    onEdit(store)
                }
            }

            Spacer(
                modifier = Modifier
                    .height(stateValues.marginTextField)
            )

            onSetActive?.let {
                actionButton(
                    text = "",
                    enabledColor = stateValues.OkayColor,
                    iconPath = stateValues.drawablePathIconCheck,
                    iconContentDescription = stateValues.stringSelect,
                ) {
                    onSetActive(store)
                }
            } ?: onSetInactive?.let {
                actionButton(
                    text = "",
                    enabledColor = stateValues.ErrorColor,
                    iconPath = stateValues.drawablePathIconCancel,
                    iconContentDescription = stateValues.stringMakeInactive
                ) {
                    onSetInactive(store)
                }
            }
        }
    }
}

@Composable
fun AppConfiguration.StockWarehouseScreen() {
    var sortMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var sortMode by rememberSaveable { mutableStateOf("name") }
    var sortAscending by rememberSaveable { mutableStateOf(true) }
    var labelPrintItemId by rememberSaveable { mutableStateOf<String?>(null) }
    val labelPrintItem = stateValues.stock.orEmpty().find { it.id == labelPrintItemId }
    val stockWarehouseQuantitySortAvailable = remember(stateValues.stockBatches, stateValues.activeStoreId) {
        val activeStoreId = stateValues.activeStoreId
        stateValues.stockBatches.orEmpty().any { batch ->
            batch.isActive &&
                    batch.status != StockBatchStatusDataModel.Ordered &&
                    batch.status != StockBatchStatusDataModel.Deleted &&
                    batch.status != StockBatchStatusDataModel.InTransit &&
                    batch.status != StockBatchStatusDataModel.SoldOut &&
                    batch.status != StockBatchStatusDataModel.WrittenOff &&
                    batch.quantity.total > 0.0 &&
                    (activeStoreId.isNullOrBlank() || batchBelongsToInventoryStoreForUi(batch.storeId, activeStoreId))
        }
    }

    LaunchedEffect(stockWarehouseQuantitySortAvailable, sortMode) {
        if (!stockWarehouseQuantitySortAvailable && sortMode == "quantity") {
            sortMode = "name"
        }
    }

    labelPrintItem?.let { item ->
        StockItemLabelPrintBottomSheet(
            goodsItem = item,
            batches = stateValues.stockBatches.orEmpty().filter { it.goodsItemId == item.id && it.isActive },
            onDismiss = { labelPrintItemId = null }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        ScreenAppBarWidget(
            title = stateValues.stringStock,
            iconPath = stateValues.drawablePathIconStock,
            trailingIcons = listOf(
                Triple(
                    sortActionIconPath(),
                    sortActionIconFallback()
                ) {
                    sortMenuExpanded = !sortMenuExpanded
                }
            )
        )

        AnimatedVisibility(visible = sortMenuExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(stateValues.BackgroundColor)
                    .padding(horizontal = stateValues.marginTextField, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = localizedStringResource(512, "Sort by"),
                    color = stateValues.TextColor,
                    fontSize = stateValues.textSize,
                    fontWeight = FontWeight.Bold
                )

                val sortTabs = buildList {
                    add(TabContent("name", stateValues.stringName) { sortMode = it })
                    add(TabContent("price", localizedStringResource(340, "Prices")) { sortMode = it })
                    if (stockWarehouseQuantitySortAvailable) {
                        add(TabContent("quantity", localizedStringResource(513, "Quantity")) { sortMode = it })
                    }
                    add(TabContent("created", localizedStringResource(514, "Time added")) { sortMode = it })
                }

                tabRowWidget(
                    modifier = Modifier.fillMaxWidth(),
                    tabs = sortTabs,
                    selectedIndexInitial = sortMode.takeIf { mode -> sortTabs.any { it.id == mode } } ?: "name",
                    textSize = stateValues.smallTextSize
                )

                tabRowWidget(
                    modifier = Modifier.fillMaxWidth(),
                    tabs = listOf(
                        TabContent("asc", localizedStringResource(515, "Ascending")) { sortAscending = true },
                        TabContent("desc", localizedStringResource(516, "Descending")) { sortAscending = false }
                    ),
                    selectedIndexInitial = if (sortAscending) "asc" else "desc",
                    textSize = stateValues.smallTextSize
                )
            }
        }

        val openEdit: (GoodsItemDataModel) -> Unit = { item ->
            coroutineScope.launch {
                NavigationScreenModel.Stock.AddEditGoodsItem.removeState("stock_add_edit_add_session_id")
                NavigationScreenModel.Stock.AddEditGoodsItem.removeState("stock_add_edit_global_template")
                NavigationScreenModel.Stock.AddEditGoodsItem.setState(
                    NavigationScreenModel.Stock.AddEditGoodsItem.KEY_STATE_EDITED_GOODS_ITEM_ID to item.id
                )
                NavigationScreenModel.Stock.AddEditGoodsItem.setState(
                    "stock_add_edit_selected_tab" to "info"
                )
                Navigation.Stock.go(NavigationScreenModel.Stock.AddEditGoodsItem, forceSecond = true)
            }
        }

        val openAddBatch: (GoodsItemDataModel) -> Unit = { item ->
            coroutineScope.launch {
                NavigationScreenModel.Stock.AddEditGoodsItem.removeState("stock_add_edit_add_session_id")
                NavigationScreenModel.Stock.AddEditGoodsItem.removeState("stock_add_edit_global_template")
                NavigationScreenModel.Stock.AddEditGoodsItem.setState(
                    NavigationScreenModel.Stock.AddEditGoodsItem.KEY_STATE_EDITED_GOODS_ITEM_ID to item.id
                )
                NavigationScreenModel.Stock.AddEditGoodsItem.setState(
                    "stock_add_edit_selected_tab" to "batches"
                )
                NavigationScreenModel.Stock.AddEditGoodsItem.setState(
                    "stock_add_edit_start_add_batch" to "true"
                )
                Navigation.Stock.go(NavigationScreenModel.Stock.AddEditGoodsItem, forceSecond = true)
            }
        }

        val openAddGoodsItem: () -> Unit = {
            coroutineScope.launch {
                NavigationScreenModel.Stock.AddEditGoodsItem.removeState(
                    NavigationScreenModel.Stock.AddEditGoodsItem.KEY_STATE_EDITED_GOODS_ITEM_ID
                )
                NavigationScreenModel.Stock.AddEditGoodsItem.removeState("stock_add_edit_start_add_batch")
                NavigationScreenModel.Stock.AddEditGoodsItem.removeState("stock_add_edit_global_template")
                NavigationScreenModel.Stock.AddEditGoodsItem.setState("stock_add_edit_add_session_id" to getCurrentTimeMillis().toString())
                NavigationScreenModel.Stock.AddEditGoodsItem.setState("stock_add_edit_selected_tab" to "info")
                Navigation.Stock.go(NavigationScreenModel.Stock.AddEditGoodsItem, forceSecond = true)
            }
        }

        val activeStoreIdForPermissions = stateValues.activeStoreId
        val canCreateStockItem = currentUserHasStorePermission(activeStoreIdForPermissions, STORE_PERMISSION_STOCK_ITEM_CREATE)
        val canEditStockItem = currentUserHasStorePermission(activeStoreIdForPermissions, STORE_PERMISSION_STOCK_ITEM_EDIT)
        val canDeleteStockItem = currentUserHasStorePermission(activeStoreIdForPermissions, STORE_PERMISSION_STOCK_ITEM_DELETE)
        val canCreateStockBatch = currentUserHasStorePermission(activeStoreIdForPermissions, STORE_PERMISSION_STOCK_BATCH_CREATE)

        StockWarehouseScreenContent(
            modifier = Modifier
                .weight(1f),
            onDelete = if (canDeleteStockItem) {
                { item ->
                    deleteGoodsItem(id = item.id, storeId = item.storeId.ifBlank { stateValues.activeStoreId.orEmpty() }) {

                    }
                }
            } else null,
            onClick = if (canEditStockItem) openEdit else null,
            onEdit = if (canEditStockItem) openEdit else null,
            onAddBatch = if (canCreateStockBatch) openAddBatch else null,
            onPrintLabel = { item -> labelPrintItemId = item.id },
            sortMode = sortMode,
            sortAscending = sortAscending
        )

        if (stateValues.isNarrowScreen && canCreateStockItem) {
            actionButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                text = stateValues.stringAddGoodsItem,
                iconPath = stateValues.drawablePathIconAdd,
                confirmationRequired = false,
                onClick = openAddGoodsItem
            )
        }
    }
}



internal data class StockWarehouseMetricsData(
    val totalItems: Int,
    val inStockItems: Int,
    val outOfStockItems: Int,
    val lowStockItems: Int,
    val expiringSoonItems: Int,
    val noBarcodeItems: Int,
    val activeBatchCount: Int
)

internal data class StockWarehouseSearchResult(
    val items: List<GoodsItemDataModel>,
    val exactHit: GoodsItemDataModel? = null,
    val exactQuery: String = ""
)

internal const val STOCK_WAREHOUSE_FILTER_TOTAL = "total"
internal const val STOCK_WAREHOUSE_FILTER_IN_STOCK = "in_stock"
internal const val STOCK_WAREHOUSE_FILTER_OUT = "out"
internal const val STOCK_WAREHOUSE_FILTER_LOW = "low"
internal const val STOCK_WAREHOUSE_FILTER_EXPIRING = "expiring"
internal const val STOCK_WAREHOUSE_FILTER_NO_BARCODE = "no_barcode"

internal fun stockWarehouseActiveBatchesForUi(batches: List<GoodsBatchDataModel>): List<GoodsBatchDataModel> =
    batches.filter { batch ->
        batch.isActive &&
                batch.status != StockBatchStatusDataModel.Ordered &&
                batch.status != StockBatchStatusDataModel.Deleted &&
                batch.status != StockBatchStatusDataModel.InTransit &&
                batch.status != StockBatchStatusDataModel.SoldOut &&
                batch.status != StockBatchStatusDataModel.WrittenOff &&
                batch.quantity.total > 0.0
    }

internal fun stockWarehouseBatchesByItemForUi(batches: List<GoodsBatchDataModel>): Map<String, List<GoodsBatchDataModel>> =
    stockWarehouseActiveBatchesForUi(batches).groupBy { it.goodsItemId }

internal fun GoodsItemDataModel.stockWarehouseQuantityForUi(
    batchesByItem: Map<String, List<GoodsBatchDataModel>>
): Double = batchesByItem[id].orEmpty().sumOf { it.quantity.total }

internal fun GoodsItemDataModel.stockWarehouseSortPriceForUi(): Double? =
    salePrices
        .firstOrNull { it.price.toMoneyDouble() > 0.0 }
        ?.price
        ?.toMoneyDouble()
        ?: returnPrices
            .firstOrNull { it.price.toMoneyDouble() > 0.0 }
            ?.price
            ?.toMoneyDouble()

internal fun stockWarehouseFallbackOrderMap(items: List<GoodsItemDataModel>): Map<String, Int> =
    items
        .asSequence()
        .mapIndexedNotNull { index, item -> item.id.takeIf { it.isNotBlank() }?.let { it to index } }
        .toMap()

internal fun stockWarehouseQuantitySortedItems(
    items: List<GoodsItemDataModel>,
    quantityByItem: Map<String, Double>,
    fallbackOrder: Map<String, Int>,
    ascending: Boolean
): List<GoodsItemDataModel> {
    val farFallback = Int.MAX_VALUE / 2
    return items.sortedWith { left, right ->
        val leftQuantity = quantityByItem[left.id] ?: 0.0
        val rightQuantity = quantityByItem[right.id] ?: 0.0
        val leftHasRealStock = leftQuantity > 0.0
        val rightHasRealStock = rightQuantity > 0.0

        when {
            leftHasRealStock && rightHasRealStock -> {
                val quantityCompare = if (ascending) {
                    leftQuantity.compareTo(rightQuantity)
                } else {
                    rightQuantity.compareTo(leftQuantity)
                }
                if (quantityCompare != 0) quantityCompare
                else (fallbackOrder[left.id] ?: farFallback).compareTo(fallbackOrder[right.id] ?: farFallback)
            }
            leftHasRealStock != rightHasRealStock -> {
                if (ascending) {
                    if (leftHasRealStock) 1 else -1
                } else {
                    if (leftHasRealStock) -1 else 1
                }
            }
            else -> (fallbackOrder[left.id] ?: farFallback).compareTo(fallbackOrder[right.id] ?: farFallback)
        }
    }
}

internal fun stockWarehouseDefaultSortedItems(
    items: List<GoodsItemDataModel>,
    preferredOrder: Map<String, Int>,
    sortMode: String?,
    sortAscending: Boolean,
    language: String
): List<GoodsItemDataModel> {
    // Decorate once instead of localizing/lowercasing on every comparator invocation.
    // Keep the key with the item rather than indexing by ID (draft IDs can be empty).
    val namedItems = items.map { it to it.name.extractLocalizedString(language).orEmpty().lowercase() }
    if (sortMode == "price" && preferredOrder.isEmpty()) {
        val pricedItems = namedItems
            .mapNotNull { (item, name) -> item.stockWarehouseSortPriceForUi()?.let { price -> Triple(item, price, name) } }
            .sortedWith(compareBy<Triple<GoodsItemDataModel, Double, String>> { it.second }.thenBy { it.third })
            .let { if (sortAscending) it else it.reversed() }
            .map { it.first }
        val pricedIds = pricedItems.map { it.id }.toSet()
        val unpricedItems = namedItems
            .asSequence()
            .filter { it.first.id !in pricedIds }
            .sortedBy { it.second }
            .map { it.first }
            .toList()
        return pricedItems + unpricedItems
    }

    val sorted = when {
        preferredOrder.isNotEmpty() -> namedItems.sortedWith(
            compareBy<Pair<GoodsItemDataModel, String>> { preferredOrder[it.first.id] ?: Int.MAX_VALUE }
                .thenBy { it.second }
        )
        sortMode == "created" -> namedItems.sortedBy { it.first.createdAtMillis }
        else -> namedItems.sortedBy { it.second }
    }.map { it.first }
    return if (preferredOrder.isEmpty() && !sortAscending) sorted.reversed() else sorted
}

internal fun GoodsItemDataModel.stockWarehouseExpiringSoonForUi(
    batchesByItem: Map<String, List<GoodsBatchDataModel>>,
    now: Long = getCurrentTimeMillis(),
    horizonMillis: Long = 7L * 24L * 60L * 60L * 1000L
): Boolean = batchesByItem[id].orEmpty().any { batch ->
    val expiresAt = batch.expirationDateMillis ?: return@any false
    expiresAt in now..(now + horizonMillis)
}

internal fun GoodsItemDataModel.stockWarehouseHasNoBarcodeForUi(): Boolean =
    allBarcodeValues().none { it.isNotBlank() }

internal fun stockWarehouseItemsForFilter(
    filterId: String,
    items: List<GoodsItemDataModel>,
    batchesByItem: Map<String, List<GoodsBatchDataModel>>
): List<GoodsItemDataModel> {
    if (filterId == STOCK_WAREHOUSE_FILTER_TOTAL) return items

    val quantityByItem = batchesByItem.mapValues { (_, itemBatches) -> itemBatches.sumOf { it.quantity.total } }
    return items.filter { item ->
        val quantity = quantityByItem[item.id] ?: 0.0
        when (filterId) {
            STOCK_WAREHOUSE_FILTER_IN_STOCK -> quantity > 0.0
            STOCK_WAREHOUSE_FILTER_OUT -> quantity <= 0.0
            STOCK_WAREHOUSE_FILTER_LOW -> quantity > 0.0 && quantity <= 5.0
            STOCK_WAREHOUSE_FILTER_EXPIRING -> item.stockWarehouseExpiringSoonForUi(batchesByItem)
            STOCK_WAREHOUSE_FILTER_NO_BARCODE -> item.stockWarehouseHasNoBarcodeForUi()
            else -> true
        }
    }
}

internal fun stockWarehouseItemsForFilter(
    filterId: String,
    items: List<GoodsItemDataModel>,
    batches: List<GoodsBatchDataModel>
): List<GoodsItemDataModel> = stockWarehouseItemsForFilter(
    filterId = filterId,
    items = items,
    batchesByItem = stockWarehouseBatchesByItemForUi(batches)
)

internal fun stockWarehouseMetricsForUi(
    items: List<GoodsItemDataModel>,
    batchesByItem: Map<String, List<GoodsBatchDataModel>>
): StockWarehouseMetricsData {
    val activeBatchCount = batchesByItem.values.sumOf { it.size }
    val quantityByItem = batchesByItem.mapValues { (_, itemBatches) -> itemBatches.sumOf { it.quantity.total } }

    return StockWarehouseMetricsData(
        totalItems = items.size,
        inStockItems = items.count { item -> (quantityByItem[item.id] ?: 0.0) > 0.0 },
        outOfStockItems = items.count { item -> (quantityByItem[item.id] ?: 0.0) <= 0.0 },
        lowStockItems = items.count { item ->
            val quantity = quantityByItem[item.id] ?: 0.0
            quantity > 0.0 && quantity <= 5.0
        },
        expiringSoonItems = items.count { it.stockWarehouseExpiringSoonForUi(batchesByItem) },
        noBarcodeItems = items.count { item -> item.stockWarehouseHasNoBarcodeForUi() },
        activeBatchCount = activeBatchCount
    )
}

internal fun stockWarehouseMetricsForUi(
    items: List<GoodsItemDataModel>,
    batches: List<GoodsBatchDataModel>
): StockWarehouseMetricsData = stockWarehouseMetricsForUi(
    items = items,
    batchesByItem = stockWarehouseBatchesByItemForUi(batches)
)

internal fun AppConfiguration.stockItemLabelDataForUi(
    goodsItem: GoodsItemDataModel,
    batches: List<GoodsBatchDataModel>,
    copies: Int = 1,
    barcodeOverride: String? = null,
    priceTextOverride: String? = null,
    storeNameOverride: String? = null
): StockItemLabelDataModel? {
    val itemName = goodsItem.name.visibleLocalizedString(stateValues.appLanguage, localizedStringResource(113, "Unnamed item"))
    val sortedBatches = batches.sortedForShelf(goodsItem)
    val activeBatch = sortedBatches.firstOrNull { it.id == goodsItem.activeShelfBatchId }
        ?: sortedBatches.bestBatchForSale(goodsItem)
    val activeStore = stateValues.stores.findStoreOrBranchForUi(stateValues.activeStoreId ?: goodsItem.storeId)
    val defaultStoreName = activeStore
        ?.name
        ?.visibleLocalizedString(stateValues.appLanguage, activeStore.publicId.ifBlank { activeStore.id })
        .orEmpty()
    val promotedPrice = goodsItem.promotedPriceForTransaction(
        transactionTypeIndex = 0,
        saleMethodId = SALE_METHOD_RETAIL,
        quantityTotal = 1.0,
        batch = activeBatch
    ).finalPrice
    val defaultPriceText = listOf(promotedPrice.price.trim(), promotedPrice.currency.trim())
        .filter { it.isNotBlank() && it != "0" }
        .joinToString(" ")
    val defaultUnitText = activeBatch
        ?.quantity
        ?.immutableUnitName
        ?.visibleLocalizedString(stateValues.appLanguage, goodsItem.measurementUnitId)
        ?: goodsItem.measurementUnitId
    val barcode = barcodeOverride
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: goodsItem.allBarcodeValues()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()

    if (barcode.isBlank()) return null

    return StockItemLabelDataModel(
        itemName = itemName,
        barcode = barcode,
        priceText = priceTextOverride?.takeIf { it.isNotBlank() } ?: defaultPriceText,
        priceLabel = localizedStringResource(1329, "PRICE"),
        storeName = storeNameOverride?.takeIf { it.isNotBlank() } ?: defaultStoreName.ifBlank { "AITA" },
        unitText = defaultUnitText,
        copies = copies.coerceIn(1, 99),
        protocol = LABEL_PRINTER_PROTOCOL_AUTO
    )
}

@Composable
internal fun AppConfiguration.StockWarehouseMetricPill(
    filterId: String? = null,
    title: String,
    value: String,
    selected: Boolean = false,
    warning: Boolean = false,
    enabled: Boolean = true,
    onFilterSelected: (String) -> Unit = {}
) {
    val shape = RoundedCornerShape(8.dp)
    val borderColor = when {
        selected -> stateValues.AccentColor
        warning -> stateValues.ErrorColor
        else -> stateValues.PlaceholderTextColor
    }
    val valueColor = when {
        selected -> stateValues.AccentColor
        warning -> stateValues.ErrorColor
        enabled -> stateValues.TextColor
        else -> stateValues.PlaceholderTextColor
    }

    Column(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) stateValues.AccentColor.copy(alpha = 0.14f) else stateValues.BackgroundColor)
            .border(stateValues.unfocusedBorderWidth, borderColor.copy(alpha = if (selected || warning) 0.9f else 0.45f), shape)
            .then(
                if (enabled && filterId != null) {
                    Modifier.aitaClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = stateValues.AccentColor),
                        onClick = { onFilterSelected(filterId) }
                    )
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 18.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            color = valueColor,
            fontSize = stateValues.accentTextSize,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Text(
            text = title,
            color = if (selected) stateValues.AccentColor else stateValues.PlaceholderTextColor,
            fontSize = stateValues.smallTextSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun AppConfiguration.StockWarehouseInfoTile(
    modifier: Modifier = Modifier,
    metrics: StockWarehouseMetricsData,
    selectedFilterId: String,
    selectionMode: Boolean,
    selectedCount: Int,
    totalSelectableCount: Int,
    onFilterSelected: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onPrintSelected: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(stateValues.cornerRadius)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selectionMode) stateValues.AccentColor.copy(alpha = 0.12f) else stateValues.BackgroundColor)
            .border(
                stateValues.unfocusedBorderWidth,
                if (selectionMode) stateValues.AccentColor else stateValues.PlaceholderTextColor.copy(alpha = 0.55f),
                shape
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (selectionMode) {
                Text(
                    text = "${localizedStringResource(1323, "Selected")}: $selectedCount / $totalSelectableCount",
                    color = stateValues.AccentColor,
                    fontSize = stateValues.accentTextSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = localizedStringResource(1328, "Clear selection"),
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StockWarehouseMetricPill(
                        filterId = STOCK_WAREHOUSE_FILTER_TOTAL,
                        title = localizedStringResource(1318, "Total items"),
                        value = metrics.totalItems.toString(),
                        selected = selectedFilterId == STOCK_WAREHOUSE_FILTER_TOTAL,
                        onFilterSelected = onFilterSelected
                    )
                    StockWarehouseMetricPill(
                        filterId = STOCK_WAREHOUSE_FILTER_IN_STOCK,
                        title = localizedStringResource(1319, "In stock"),
                        value = metrics.inStockItems.toString(),
                        selected = selectedFilterId == STOCK_WAREHOUSE_FILTER_IN_STOCK,
                        onFilterSelected = onFilterSelected
                    )
                    StockWarehouseMetricPill(
                        filterId = STOCK_WAREHOUSE_FILTER_OUT,
                        title = localizedStringResource(1320, "Out"),
                        value = metrics.outOfStockItems.toString(),
                        selected = selectedFilterId == STOCK_WAREHOUSE_FILTER_OUT,
                        warning = metrics.outOfStockItems > 0,
                        onFilterSelected = onFilterSelected
                    )
                    StockWarehouseMetricPill(
                        filterId = STOCK_WAREHOUSE_FILTER_LOW,
                        title = localizedStringResource(1321, "Low"),
                        value = metrics.lowStockItems.toString(),
                        selected = selectedFilterId == STOCK_WAREHOUSE_FILTER_LOW,
                        warning = metrics.lowStockItems > 0,
                        onFilterSelected = onFilterSelected
                    )
                    StockWarehouseMetricPill(
                        filterId = STOCK_WAREHOUSE_FILTER_EXPIRING,
                        title = localizedStringResource(1309, "Expires very soon"),
                        value = metrics.expiringSoonItems.toString(),
                        selected = selectedFilterId == STOCK_WAREHOUSE_FILTER_EXPIRING,
                        warning = metrics.expiringSoonItems > 0,
                        onFilterSelected = onFilterSelected
                    )
                    StockWarehouseMetricPill(
                        filterId = STOCK_WAREHOUSE_FILTER_NO_BARCODE,
                        title = localizedStringResource(1322, "No barcode"),
                        value = metrics.noBarcodeItems.toString(),
                        selected = selectedFilterId == STOCK_WAREHOUSE_FILTER_NO_BARCODE,
                        warning = metrics.noBarcodeItems > 0,
                        onFilterSelected = onFilterSelected
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .width(stateValues.unfocusedBorderWidth)
                            .height(34.dp)
                            .background(stateValues.PlaceholderTextColor.copy(alpha = 0.30f))
                    )
                    StockWarehouseMetricPill(
                        title = localizedStringResource(1327, "Batches"),
                        value = metrics.activeBatchCount.toString(),
                        enabled = false
                    )
                }
            }
        }

        if (selectionMode) {
            actionButton(
                text = "",
                iconPath = stateValues.drawablePathIconCheck,
                iconRes = stateValues.drawableResIconCheck.value,
                iconContentDescription = localizedStringResource(1324, "Select all"),
                enabled = selectedCount < totalSelectableCount,
                fillMaxWidthIfTextPresent = false,
                confirmationRequired = false,
                onClick = onSelectAll
            )
            onPrintSelected?.let { printSelected ->
                actionButton(
                    text = "",
                    iconPath = stateValues.drawablePathIconPrintTag,
                    iconRes = stateValues.drawableResIconPrintTag.value,
                    iconContentDescription = localizedStringResource(1288, "Print item label"),
                    enabled = selectedCount > 0,
                    fillMaxWidthIfTextPresent = false,
                    confirmationRequired = false,
                    onClick = printSelected
                )
            }
            actionButton(
                text = "",
                iconPath = stateValues.drawablePathIconCancel,
                iconRes = stateValues.drawableResIconCancel.value,
                iconContentDescription = localizedStringResource(1328, "Clear selection"),
                fillMaxWidthIfTextPresent = false,
                confirmationRequired = false,
                onClick = onClearSelection
            )
        }
    }
}

@Composable
fun AppConfiguration.StockWarehouseScreenContent(
    modifier: Modifier = Modifier,
    searchQuery: String? = null,
    stockItemsOverride: List<GoodsItemDataModel>? = null,
    disableIfOutOfStock: Boolean = false,
    showStockType: Boolean = true,
    showBatches: Boolean = true,
    transactionTypeIndex: Int? = null,
    onFilter: ((GoodsItemDataModel) -> Boolean)? = null,
    onClick: ((GoodsItemDataModel) -> Unit)? = null,
    onDelete: ((GoodsItemDataModel) -> Unit)? = null,
    onEdit: ((GoodsItemDataModel) -> Unit)? = null,
    onAddBatch: ((GoodsItemDataModel) -> Unit)? = null,
    onPrintLabel: ((GoodsItemDataModel) -> Unit)? = null,
    sortMode: String? = null,
    sortAscending: Boolean = true,
    preferredOrderIds: List<String> = emptyList(),
    scrollStateHost: StateHost = NavigationScreenModel.Stock.Warehouse,
    scrollStateKey: String = "stock_warehouse_scroll",
    onExactSearchHit: ((GoodsItemDataModel) -> Unit)? = null,
    onExactSearchHitWithQuery: ((GoodsItemDataModel, String) -> Unit)? = null
){
    val stockLoadStatus by stockLoadStatusState.collectAsState()
    val batchesLoadStatus by stockBatchesLoadStatusState.collectAsState()
    LaunchedEffect(stateValues.activeStoreId, stateValues.userAccount?.id) {
        stateValues.activeStoreId?.let { storeId ->
            if (!stockLoadStatus.loading && (stateValues.stock == null || stockLoadStatus.source == InventoryLoadSource.Cache)) getStock(storeId)
            if (!batchesLoadStatus.loading && (stateValues.stockBatches == null || batchesLoadStatus.source == InventoryLoadSource.Cache)) getStockBatches(storeId)
        }
    }
    when (val state = stateValues.stockState) {
        is DataState.Success -> {
            val stockPayload = stockItemsOverride ?: state.payload
            if (stockPayload.isEmpty()) {
                val status = if (stockLoadStatus.failure != null || stockLoadStatus.loading) stockLoadStatus
                    else if (transactionTypeIndex != null && stateValues.stockBatches == null) batchesLoadStatus
                    else stockLoadStatus
                InventoryLoadFeedback(
                    modifier = modifier.fillMaxSize(),
                    status = status,
                    emptyText = if (stockItemsOverride != null && state.payload.isNotEmpty())
                        stateValues.stringNoMatches else stateValues.stringListEmpty
                )
            } else {
                val activeStoreId = stateValues.activeStoreId
                val stockBatches = stateValues.stockBatches.orEmpty()
                val stockPayloadAlreadyScopedForTransaction = stockItemsOverride != null && transactionTypeIndex != null
                val showWarehouseInfoTile = searchQuery == null && transactionTypeIndex == null
                val selectedSortMode = sortMode ?: "name"
                val needsWarehouseQuantityMap = showWarehouseInfoTile || selectedSortMode == "quantity"
                val warehouseBatchesByItem = remember(stockBatches, needsWarehouseQuantityMap) {
                    if (needsWarehouseQuantityMap) stockWarehouseBatchesByItemForUi(stockBatches) else emptyMap()
                }
                val warehouseQuantityByItem = remember(warehouseBatchesByItem, needsWarehouseQuantityMap) {
                    if (needsWarehouseQuantityMap) warehouseBatchesByItem.mapValues { (_, batches) -> batches.sumOf { it.quantity.total } } else emptyMap()
                }
                val needsDisplayBatchesByItem = showBatches || disableIfOutOfStock || onPrintLabel != null
                val stockPayloadItemIdsForBatchDisplay = remember(stockPayload, stockItemsOverride) {
                    if (stockItemsOverride == null) emptySet()
                    else stockPayload.map { it.id }.filter { it.isNotBlank() }.toSet()
                }
                val displayBatchesByItem = remember(stockBatches, transactionTypeIndex, activeStoreId, needsDisplayBatchesByItem, stockPayloadItemIdsForBatchDisplay) {
                    if (!needsDisplayBatchesByItem) {
                        emptyMap()
                    } else {
                        stockBatches
                            .asSequence()
                            .filter { batch ->
                                (stockPayloadItemIdsForBatchDisplay.isEmpty() || batch.goodsItemId in stockPayloadItemIdsForBatchDisplay) &&
                                        batch.isActive && (
                                        transactionTypeIndex == null ||
                                                (batchBelongsToInventoryStoreForUi(batch.storeId, activeStoreId) && batch.isSelectableActiveStockBatch())
                                        )
                            }
                            .groupBy { it.goodsItemId }
                    }
                }
                val sellableItemIdsForActiveStore = remember(stockBatches, activeStoreId, stockPayloadAlreadyScopedForTransaction) {
                    if (stockPayloadAlreadyScopedForTransaction || activeStoreId.isNullOrBlank()) {
                        emptySet()
                    } else {
                        stockBatches
                            .asSequence()
                            .filter { batch ->
                                batch.isActive &&
                                        batch.isSelectableActiveStockBatch() &&
                                        batchBelongsToInventoryStoreForUi(batch.storeId, activeStoreId)
                            }
                            .map { it.goodsItemId }
                            .filter { it.isNotBlank() }
                            .toSet()
                    }
                }

                var lSearchQuery: String by rememberSaveable {
                    mutableStateOf("")
                }
                var appliedSearchQuery: String by rememberSaveable(searchQuery, transactionTypeIndex) {
                    mutableStateOf(searchQuery.orEmpty())
                }
                var selectedStockItemIds by rememberSaveable(activeStoreId, stateValues.userAccount?.id) {
                    mutableStateOf(emptyList<String>())
                }
                var selectedWarehouseFilterId by remember(searchQuery, transactionTypeIndex) {
                    mutableStateOf(STOCK_WAREHOUSE_FILTER_TOTAL)
                }

                if (searchQuery == null) {
                    val autoFocusSearch = platformAllowsAutomaticTextFieldFocus()
                    val searchTextFieldContent =
                        searchTextField(
                            modifier = Modifier
                                .padding(start = 8.dp, top = 8.dp, end = 8.dp),
                            stateHost = NavigationScreenModel.Stock.Warehouse,
                            stateKey = NavigationScreenModel.KEY_STATE_SEARCH_QUERY,
                            isFocusedInitial = autoFocusSearch,
                            autoFocus = autoFocusSearch,
                            forceRefocus = false,
                            barcodeCamScanner = true
                        )

                    LaunchedEffect(autoFocusSearch) {
                        if (autoFocusSearch) {
                            delay(320)
                            searchTextFieldContent.focusRequester.requestFocus()
                        }
                    }

                    LaunchedEffect(searchTextFieldContent.value) {
                        lSearchQuery = searchTextFieldContent.value.text
                    }
                } else {
                    LaunchedEffect(searchQuery) {
                        lSearchQuery = searchQuery
                    }
                }

                LaunchedEffect(lSearchQuery, transactionTypeIndex) {
                    if (lSearchQuery.isNotBlank()) {
                        if (transactionTypeIndex == null) {
                            delay(160)
                        } else if (!lSearchQuery.looksLikeCompleteRetailBarcodeInput()) {
                            delay(90)
                        }
                    }
                    appliedSearchQuery = lSearchQuery
                }

                var lastExactSearchHandledKey by rememberSaveable {
                    mutableStateOf("")
                }

                LaunchedEffect(appliedSearchQuery) {
                    if (appliedSearchQuery.isBlank() && lastExactSearchHandledKey.isNotEmpty()) {
                        lastExactSearchHandledKey = ""
                    }
                }

                fun handleExactSearchHit(item: GoodsItemDataModel, query: String) {
                    val key = "${item.id}|$query"
                    if (key == lastExactSearchHandledKey)
                        return

                    lastExactSearchHandledKey = key
                    onExactSearchHitWithQuery?.invoke(item, query)
                        ?: onExactSearchHit?.invoke(item)
                }

                val baseItems = remember(stockPayload, onFilter, transactionTypeIndex, activeStoreId, sellableItemIdsForActiveStore, stockPayloadAlreadyScopedForTransaction) {
                    stockPayload
                        .let { payload -> onFilter?.let { filterAction -> payload.filter { filterAction(it) } } ?: payload }
                        .let { filtered ->
                            when {
                                transactionTypeIndex == null -> filtered
                                stockPayloadAlreadyScopedForTransaction -> filtered
                                else -> filtered.filter { item ->
                                    activeStoreId.isNullOrBlank() ||
                                            sameInventoryStoreGroupForUi(activeStoreId, item.storeId) ||
                                            item.id in sellableItemIdsForActiveStore
                                }
                            }
                        }
                }

                var lastNonQuantitySortOrderIds by rememberSaveable(searchQuery, transactionTypeIndex) {
                    mutableStateOf(emptyList<String>())
                }
                val language = stateValues.appLanguage
                val fallbackOrderIds = if (selectedSortMode == "quantity") lastNonQuantitySortOrderIds else emptyList()
                val projectionRequest = remember(
                    baseItems, appliedSearchQuery, preferredOrderIds, selectedSortMode, sortAscending,
                    language, warehouseQuantityByItem, warehouseBatchesByItem, selectedWarehouseFilterId,
                    showWarehouseInfoTile, fallbackOrderIds, activeStoreId
                ) { Any() }
                // Keep the previous layout while sorting this same store, rather than flashing a
                // full-screen spinner for every keystroke or stock update. Never retain across owners.
                val projectionState = remember(activeStoreId, stateValues.userAccount?.id) {
                    mutableStateOf<Pair<Any, StockWarehouseProjection>?>(null)
                }
                LaunchedEffect(projectionRequest) {
                    val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                        buildStockWarehouseProjection(
                            baseItems, appliedSearchQuery, preferredOrderIds, selectedSortMode,
                            sortAscending, language, warehouseQuantityByItem, warehouseBatchesByItem,
                            if (showWarehouseInfoTile) selectedWarehouseFilterId else STOCK_WAREHOUSE_FILTER_TOTAL,
                            fallbackOrderIds
                        )
                    }
                    projectionState.value = projectionRequest to result
                }
                val projection = projectionState.value?.second
                val projectionCurrent = projectionState.value?.first === projectionRequest
                if (projection == null) {
                    InventoryLoadFeedback(
                        modifier = modifier.fillMaxSize(),
                        status = InventoryLoadStatus(storeId = activeStoreId, loading = true)
                    )
                    return
                }
                val searchResult = projection.search
                val defaultSortedItems = projection.defaultSortedItems
                val unfilteredSortedItems = projection.unfilteredSortedItems
                val sortedItems = projection.sortedItems
                LaunchedEffect(projectionCurrent, selectedSortMode, defaultSortedItems) {
                    if (projectionCurrent && (selectedSortMode != "quantity" || lastNonQuantitySortOrderIds.isEmpty())) {
                        lastNonQuantitySortOrderIds = defaultSortedItems.map { it.id }.filter { it.isNotBlank() }
                    }
                }
                LaunchedEffect(projectionCurrent, lSearchQuery, searchResult.exactHit?.id, searchResult.exactQuery) {
                    val exactHit = searchResult.exactHit
                    if (projectionCurrent && lSearchQuery == appliedSearchQuery && exactHit != null && searchResult.exactQuery.isNotBlank()) {
                        handleExactSearchHit(exactHit, searchResult.exactQuery)
                    }
                }

                val selectionHostAvailable = searchQuery == null && transactionTypeIndex == null
                val selectableItemIds = remember(sortedItems, selectionHostAvailable) {
                    if (selectionHostAvailable) sortedItems.map { it.id }.filter { it.isNotBlank() }.distinct() else emptyList()
                }
                val selectableItemIdSet = remember(selectableItemIds) { selectableItemIds.toSet() }
                val selectionCleanupFirstId = selectableItemIds.firstOrNull().orEmpty()
                val selectionCleanupLastId = selectableItemIds.lastOrNull().orEmpty()
                LaunchedEffect(selectionHostAvailable, selectableItemIds.size, selectionCleanupFirstId, selectionCleanupLastId, appliedSearchQuery, selectedWarehouseFilterId) {
                    if (!selectionHostAvailable) {
                        if (selectedStockItemIds.isNotEmpty()) selectedStockItemIds = emptyList()
                    } else {
                        val cleaned = selectedStockItemIds.filter { it in selectableItemIdSet }.distinct()
                        if (cleaned != selectedStockItemIds) selectedStockItemIds = cleaned
                    }
                }

                val selectionAvailable = projectionCurrent && selectionHostAvailable && selectableItemIds.isNotEmpty()
                val selectionMode = selectionAvailable && selectedStockItemIds.isNotEmpty()

                fun toggleSelection(item: GoodsItemDataModel) {
                    if (!selectionAvailable || item.id.isBlank()) return
                    selectedStockItemIds = if (item.id in selectedStockItemIds) {
                        selectedStockItemIds.filterNot { it == item.id }
                    } else {
                        (selectedStockItemIds + item.id).distinct()
                    }
                }

                fun enterSelection(item: GoodsItemDataModel) {
                    if (!selectionAvailable || item.id.isBlank()) return
                    if (item.id !in selectedStockItemIds) selectedStockItemIds = (selectedStockItemIds + item.id).distinct()
                }

                fun clearSelection() {
                    selectedStockItemIds = emptyList()
                }

                fun selectAllVisibleStock() {
                    selectedStockItemIds = selectableItemIds
                }

                fun setSelectionForItemId(itemId: String, shouldSelect: Boolean) {
                    if (!selectionAvailable || itemId.isBlank() || itemId !in selectableItemIdSet) return
                    selectedStockItemIds = if (shouldSelect) {
                        (selectedStockItemIds + itemId).distinct()
                    } else {
                        selectedStockItemIds.filterNot { it == itemId }
                    }
                }

                fun selectedStockItemsInScreenOrder(): List<GoodsItemDataModel> {
                    val selectedIds = selectedStockItemIds.toSet()
                    return sortedItems.filter { it.id in selectedIds }
                }

                fun printSelectedStockLabels() {
                    val selectedLabels = selectedStockItemsInScreenOrder().mapNotNull { item ->
                        stockItemLabelDataForUi(
                            goodsItem = item,
                            batches = displayBatchesByItem[item.id].orEmpty(),
                            copies = 1
                        )
                    }

                    if (selectedLabels.isEmpty()) {
                        postInAppNotification(
                            localizedStringResource(1299, "This item has no barcode yet; add a barcode before printing a shelf label."),
                            NotificationType.Negative,
                            transient = true
                        )
                        return
                    }

                    coroutineScope.launch {
                        receiptActionNotification(
                            printStockItemLabelsDocument(
                                labels = selectedLabels,
                                notConfiguredMessage = localizedStringResource(1269, "Paper document printing is not configured for this platform")
                            ),
                            localizedStringResource(1325, "Label opened for printing")
                        )
                    }
                }

                var page by rememberSaveable(appliedSearchQuery, selectedSortMode, sortAscending, selectedWarehouseFilterId, sortedItems.size) {
                    mutableStateOf(0)
                }
                val pageSize = stateValues.globalAppConfiguration.pagingDefaultPageSize.coerceIn(20, 100)
                val visibleItems = remember(sortedItems, page, pageSize) { sortedItems.clientPaged(page, pageSize) }

                Column(
                    modifier = modifier
                        .fillMaxWidth()
                ) {
                    val failedLoad = stockLoadStatus.takeIf { it.failure != null }
                        ?: batchesLoadStatus.takeIf { it.failure != null }
                    if (failedLoad != null) {
                        InventoryLoadFeedback(
                            modifier = Modifier.fillMaxWidth(),
                            status = failedLoad,
                            compact = true
                        )
                    }
                    if (showWarehouseInfoTile) {
                        val overviewMetrics = remember(unfilteredSortedItems, warehouseBatchesByItem) {
                            stockWarehouseMetricsForUi(unfilteredSortedItems, warehouseBatchesByItem)
                        }
                        StockWarehouseInfoTile(
                            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp),
                            metrics = overviewMetrics,
                            selectedFilterId = selectedWarehouseFilterId,
                            selectionMode = selectionMode,
                            selectedCount = selectedStockItemIds.size,
                            totalSelectableCount = selectableItemIds.size,
                            onFilterSelected = { selectedWarehouseFilterId = it },
                            onSelectAll = { if (projectionCurrent) selectAllVisibleStock() },
                            onClearSelection = { clearSelection() },
                            onPrintSelected = { if (projectionCurrent) printSelectedStockLabels() }
                        )
                    }

                    if (sortedItems.isEmpty()) {
                        MessageText(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            stateValues.stringNoMatches
                        )
                    } else {
                        val warehouseListState = rememberPersistentLazyListState(
                            stateHost = scrollStateHost,
                            stateKey = scrollStateKey
                        )

                        LaunchedEffect(page, appliedSearchQuery, selectedWarehouseFilterId, selectedSortMode, sortAscending) {
                            warehouseListState.scrollToItem(0)
                        }

                        var selectionDragShouldSelect by remember(selectionMode) { mutableStateOf<Boolean?>(null) }
                        var selectionDragTouchedIds by remember(selectionMode) { mutableStateOf(emptySet<String>()) }
                        val latestSelectedStockItemIds by rememberUpdatedState(selectedStockItemIds)
                        val latestSelectableItemIdSet by rememberUpdatedState(selectableItemIdSet)
                        val visibleItemsFirstId = visibleItems.firstOrNull()?.id.orEmpty()
                        val visibleItemsLastId = visibleItems.lastOrNull()?.id.orEmpty()

                        fun stockItemIdAtListY(y: Float): String? {
                            val yInt = y.toInt()
                            return warehouseListState.layoutInfo.visibleItemsInfo
                                .firstOrNull { info -> yInt >= info.offset && yInt <= info.offset + info.size }
                                ?.key
                                ?.let { it as? String }
                                ?.takeIf { it in latestSelectableItemIdSet }
                        }

                        fun applyDragSelectionAt(y: Float, shouldSelectOverride: Boolean? = null) {
                            val itemId = stockItemIdAtListY(y) ?: return
                            val shouldSelect = shouldSelectOverride ?: selectionDragShouldSelect ?: return
                            if (itemId in selectionDragTouchedIds) return
                            selectionDragTouchedIds = selectionDragTouchedIds + itemId
                            setSelectionForItemId(itemId, shouldSelect)
                        }

                        LazyColumn(
                            state = warehouseListState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(8.dp)
                                .pointerInput(selectionMode, visibleItems.size, visibleItemsFirstId, visibleItemsLastId) {
                                    if (!selectionMode) return@pointerInput
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            val itemId = stockItemIdAtListY(offset.y)
                                            val shouldSelect = itemId?.let { it !in latestSelectedStockItemIds } ?: true
                                            selectionDragShouldSelect = shouldSelect
                                            selectionDragTouchedIds = emptySet()
                                            applyDragSelectionAt(offset.y, shouldSelect)
                                        },
                                        onDragEnd = {
                                            selectionDragShouldSelect = null
                                            selectionDragTouchedIds = emptySet()
                                        },
                                        onDragCancel = {
                                            selectionDragShouldSelect = null
                                            selectionDragTouchedIds = emptySet()
                                        }
                                    ) { change, _ ->
                                        applyDragSelectionAt(change.position.y)
                                    }
                                }
                        ) {
                            items(visibleItems, key = { it.id }) { item ->
                                val itemBatches = displayBatchesByItem[item.id].orEmpty()
                                val availableQuantity = itemBatches
                                    .asSequence()
                                    .filter { batch ->
                                        if (transactionTypeIndex == null) {
                                            batch.status != StockBatchStatusDataModel.Deleted && batch.status != StockBatchStatusDataModel.WrittenOff
                                        } else {
                                            batch.isSelectableActiveStockBatch()
                                        }
                                    }
                                    .sumOf { it.quantity.total }
                                val trulyOutOfStock = disableIfOutOfStock && availableQuantity <= 0.0

                                val canOperateThisStoreInventory = projectionCurrent && (
                                    activeStoreId.isNullOrBlank() || sameInventoryStoreGroupForUi(activeStoreId, item.storeId) || item.id in sellableItemIdsForActiveStore
                                )

                                GoodsItemInStockWidget(
                                    modifier = Modifier
                                        .alpha(if (trulyOutOfStock) 0.5f else 1f),
                                    goodsItem = item,
                                    batches = itemBatches,
                                    showBatches = showBatches && canOperateThisStoreInventory,
                                    transactionTypeIndex = transactionTypeIndex,
                                    selectionMode = selectionMode,
                                    selected = item.id in selectedStockItemIds,
                                    onSelectionToggle = if (selectionAvailable) ({ selectedItem: GoodsItemDataModel -> toggleSelection(selectedItem) }) else null,
                                    onLongPress = if (selectionAvailable) ({ selectedItem: GoodsItemDataModel -> enterSelection(selectedItem) }) else null,
                                    onDelete = onDelete?.takeIf { !selectionMode && canOperateThisStoreInventory },
                                    onClick = when {
                                        selectionMode -> { selectedItem: GoodsItemDataModel -> toggleSelection(selectedItem) }
                                        trulyOutOfStock || !canOperateThisStoreInventory -> null
                                        else -> onClick
                                    },
                                    onEdit = onEdit?.takeIf { !selectionMode && canOperateThisStoreInventory },
                                    onAddBatch = onAddBatch?.takeIf { !selectionMode && canOperateThisStoreInventory },
                                    onPrintLabel = onPrintLabel?.takeIf { !selectionMode && canOperateThisStoreInventory }
                                )
                            }

                            if (sortedItems.size > pageSize) {
                                item {
                                    PagingControls(
                                        page = page,
                                        totalItems = sortedItems.size,
                                        pageSize = pageSize,
                                        onPageChange = { page = it }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        is DataState.Empty -> {
            InventoryLoadFeedback(
                modifier = modifier.fillMaxSize(),
                status = stockLoadStatus
            )
        }
    }
}


@Composable
fun AppConfiguration.StockScreen() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (stateValues.activeStoreId == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                MessageText(
                    text = stateValues.stringNoActiveStore,
                    textSize = stateValues.titleTextSize
                )

                actionButton(
                    text = stateValues.stringSelectInMenu,
                    fillMaxWidthIfTextPresent = false
                ) {
                    coroutineScope.launch {
                        Navigation.Menu.go(NavigationScreenModel.Menu.Stores)
                        Navigation.goMain(NavigationScreenModel.Menu.Main)
                    }
                }
            }
        } else {
            if (stateValues.isNarrowScreen) {
                AnimatedContent(
                    modifier = Modifier
                        .weight(1f),
                    targetState = stateValues.navigationScreensStockLeft,
                    transitionSpec = { aitaStackContentTransform() },
                    label = "stockNavigationNarrow"
                ) { navigationStack ->
                    val model = navigationStack.last()
                    when (model) {
                        is NavigationScreenModel.Stock.Warehouse -> {
                            StockWarehouseScreen()
                        }
                        is NavigationScreenModel.Stock.AddEditGoodsItem -> {
                            StockAddEditGoodsItemScreen()
                        }
                        else -> { }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .weight(1f)
                ) {
                    AnimatedContent(
                        modifier = Modifier
                            .weight(1f),
                        targetState = stateValues.navigationScreensStockLeft,
                        transitionSpec = { aitaStackContentTransform() },
                        label = "stockNavigationLeft"
                    ) { navigationStack ->
                        val model = navigationStack.last()
                        when (model) {
                            is NavigationScreenModel.Stock.Warehouse -> {
                                StockWarehouseScreen()
                            }
                            is NavigationScreenModel.Stock.AddEditGoodsItem -> {
                                StockAddEditGoodsItemScreen()
                            }
                            else -> { }
                        }
                    }

                    AnimatedContent(
                        modifier = Modifier
                            .weight(1f),
                        targetState = stateValues.navigationScreensStockRight,
                        transitionSpec = { aitaStackContentTransform() },
                        label = "stockNavigationRight"
                    ) { navigationStack ->
                        val model = navigationStack.last()
                        when (model) {
                            is NavigationScreenModel.Stock.Warehouse -> {
                                StockWarehouseScreen()
                            }
                            is NavigationScreenModel.Stock.AddEditGoodsItem -> {
                                StockAddEditGoodsItemScreen()
                            }
                            else -> { }
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun AppConfiguration.StockBatchWidget(
    modifier: Modifier = Modifier,
    stateHost: StateHost? = null,
    stateKey: String? = null,
    containedSupplierIds: List<String> = emptyList()
) {
    Column(
        modifier = modifier
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(width = stateValues.unfocusedBorderWidth, color = stateValues.TextColor, shape = RoundedCornerShape(stateValues.cornerRadius))
    ) {
        val supplierContent = stateValues.suppliers?.filter { !containedSupplierIds.contains(it.id) }?.run {
            dropdownListWidget(
                modifier = Modifier
                    .padding(16.dp),
                titleText = stateValues.stringSupplier,
                domains = map {
                    SelectableDomain(
                        id = it.id,
                        displayId = it.name,
                        name = it.name,
                        iconPath = null,
                        iconRes = null,
                    )
                },
                showName = false,
                search = Triple("search", stateHost, stateKey)
            )


            Spacer(modifier = Modifier.height(4.dp))

            val priceOnFilterValue = { text: String, _: String, _: String? ->
                text.isNumericalDoubleString()
            }
            val priceOnContentValidityCheck = { text: String, id: String, _: String? ->
                text.isNotEmpty() && id.isNumericalDoubleString()
            }

            Spacer(
                modifier = Modifier
                    .height(stateValues.marginTextFieldGroup)
            )
        }
    }
}

@Composable
fun AppConfiguration.SplashScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val imageRes by stateValues.drawableResAITALogo.collectAsState()

        LargeIconWithTitleWidget(
            modifier = Modifier
                .width(stateValues.boundWidgetWidth),
            imageUrl = stateValues.drawablePathAITALogo,
            imageRes = imageRes,
            title = stateValues.stringLogIn
        )
    }
}

data class BatchPriceInfo(
    val supplierId: String,
    val supplyPrice: String,
    val salePrice: String,
    val returnPrice: String,
    val currency: String
)


internal const val STOCK_CONDITION_STORAGE_PREFIX = "aita-stock-condition-v1:"
internal const val STOCK_CONDITION_KIND_CUSTOM_TEXT = "custom_text"
internal const val STOCK_CONDITION_KIND_BUYER_MINIMUM_AGE = "buyer_minimum_age"
internal const val STOCK_CONDITION_KIND_TRANSACTION_TIME_WINDOW = "transaction_time_window"
internal const val STOCK_CONDITION_KIND_MARGIN_LIMIT = "margin_limit"

@kotlinx.serialization.Serializable
data class StockConditionDataModel(
    val kind: String = STOCK_CONDITION_KIND_CUSTOM_TEXT,
    val transactionTypeIndex: Int = 0,
    val text: List<LocalizedStringDataModel> = emptyList(),
    val minimumAge: Int = 18,
    val startsAtMinutes: Int = 6 * 60,
    val endsAtMinutes: Int = 22 * 60,
    val marginLimitPercent: String = "30"
)

internal fun AppConfiguration.defaultCustomStockCondition(): StockConditionDataModel = StockConditionDataModel(
    kind = STOCK_CONDITION_KIND_CUSTOM_TEXT,
    transactionTypeIndex = 0,
    text = listOf(LocalizedStringDataModel("main", ""))
)

internal fun defaultBuyerMinimumAgeStockCondition(minimumAge: Int = 18): StockConditionDataModel = StockConditionDataModel(
    kind = STOCK_CONDITION_KIND_BUYER_MINIMUM_AGE,
    transactionTypeIndex = 0,
    minimumAge = minimumAge.coerceIn(1, 130)
)

internal fun defaultTransactionTimeWindowStockCondition(
    startsAtMinutes: Int = 6 * 60,
    endsAtMinutes: Int = 22 * 60
): StockConditionDataModel = StockConditionDataModel(
    kind = STOCK_CONDITION_KIND_TRANSACTION_TIME_WINDOW,
    transactionTypeIndex = 0,
    startsAtMinutes = startsAtMinutes.coerceIn(0, 23 * 60 + 59),
    endsAtMinutes = endsAtMinutes.coerceIn(0, 23 * 60 + 59)
)

internal fun defaultMarginLimitStockCondition(limitPercent: String = "30"): StockConditionDataModel = StockConditionDataModel(
    kind = STOCK_CONDITION_KIND_MARGIN_LIMIT,
    transactionTypeIndex = 0,
    marginLimitPercent = limitPercent.cleanStockConditionPercent()
)

internal fun String.cleanStockConditionPercent(): String {
    val normalized = trim()
        .replace(',', '.')
        .filter { it.isDigit() || it == '.' }
    val singleDot = buildString {
        var seenDot = false
        normalized.forEach { char ->
            if (char == '.') {
                if (!seenDot) {
                    append(char)
                    seenDot = true
                }
            } else {
                append(char)
            }
        }
    }.trim('.')
    return singleDot.take(6).ifBlank { "30" }
}

internal fun StockConditionDataModel.normalizedStockCondition(): StockConditionDataModel = copy(
    kind = when (kind) {
        STOCK_CONDITION_KIND_BUYER_MINIMUM_AGE -> STOCK_CONDITION_KIND_BUYER_MINIMUM_AGE
        STOCK_CONDITION_KIND_TRANSACTION_TIME_WINDOW -> STOCK_CONDITION_KIND_TRANSACTION_TIME_WINDOW
        STOCK_CONDITION_KIND_MARGIN_LIMIT -> STOCK_CONDITION_KIND_MARGIN_LIMIT
        else -> STOCK_CONDITION_KIND_CUSTOM_TEXT
    },
    transactionTypeIndex = transactionTypeIndex.coerceIn(0, 2),
    text = text
        .map { it.copy(language = it.language.ifBlank { "main" }, value = it.value.trim()) }
        .filter { it.value.isNotBlank() || kind == STOCK_CONDITION_KIND_CUSTOM_TEXT }
        .distinctBy { it.language },
    minimumAge = minimumAge.coerceIn(1, 130),
    startsAtMinutes = startsAtMinutes.coerceIn(0, 23 * 60 + 59),
    endsAtMinutes = endsAtMinutes.coerceIn(0, 23 * 60 + 59),
    marginLimitPercent = marginLimitPercent.cleanStockConditionPercent()
)

internal fun escapeStockConditionJsonString(raw: String): String = buildString {
    raw.forEach { char ->
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(char)
        }
    }
}

internal fun unescapeStockConditionJsonString(raw: String): String = buildString {
    var index = 0
    while (index < raw.length) {
        val char = raw[index]
        if (char == '\\' && index + 1 < raw.length) {
            when (val next = raw[index + 1]) {
                '"' -> append('"')
                '\\' -> append('\\')
                'n' -> append('\n')
                'r' -> append('\r')
                't' -> append('\t')
                else -> append(next)
            }
            index += 2
        } else {
            append(char)
            index += 1
        }
    }
}

internal fun extractStockConditionJsonString(payload: String, key: String): String? {
    val match = Regex("\"" + key + "\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"").find(payload)
    return match?.groupValues?.getOrNull(1)?.let(::unescapeStockConditionJsonString)
}

internal fun extractStockConditionJsonInt(payload: String, key: String): Int? {
    val match = Regex("\"" + key + "\"\\s*:\\s*(-?\\d+)").find(payload)
    return match?.groupValues?.getOrNull(1)?.toIntOrNull()
}

internal fun extractStockConditionJsonArray(payload: String, key: String): String? {
    val keyIndex = payload.indexOf("\"$key\"")
    if (keyIndex < 0) return null
    val colonIndex = payload.indexOf(':', keyIndex)
    if (colonIndex < 0) return null
    val startIndex = payload.indexOf('[', colonIndex)
    if (startIndex < 0) return null

    var depth = 0
    var inString = false
    var escaped = false

    for (index in startIndex until payload.length) {
        val char = payload[index]
        if (escaped) {
            escaped = false
            continue
        }
        if (char == '\\') {
            escaped = true
            continue
        }
        if (char == '"') {
            inString = !inString
            continue
        }
        if (inString) continue

        when (char) {
            '[' -> depth += 1
            ']' -> {
                depth -= 1
                if (depth == 0) return payload.substring(startIndex, index + 1)
            }
        }
    }

    return null
}

internal fun StockConditionDataModel.toStoredStockCondition(): String {
    val normalized = normalizedStockCondition()
    val localizedTextJson = jsonBase.encodeToString(
        ListSerializer(LocalizedStringDataModel.serializer()),
        normalized.text
    )

    return STOCK_CONDITION_STORAGE_PREFIX + buildString {
        append('{')
        append("\"kind\":\"").append(escapeStockConditionJsonString(normalized.kind)).append("\",")
        append("\"transactionTypeIndex\":").append(normalized.transactionTypeIndex).append(',')
        append("\"text\":").append(localizedTextJson).append(',')
        append("\"minimumAge\":").append(normalized.minimumAge).append(',')
        append("\"startsAtMinutes\":").append(normalized.startsAtMinutes).append(',')
        append("\"endsAtMinutes\":").append(normalized.endsAtMinutes).append(',')
        append("\"marginLimitPercent\":\"").append(escapeStockConditionJsonString(normalized.marginLimitPercent)).append("\"")
        append('}')
    }
}

internal fun legacyStockCondition(text: String): StockConditionDataModel = StockConditionDataModel(
    kind = STOCK_CONDITION_KIND_CUSTOM_TEXT,
    transactionTypeIndex = 0,
    text = listOf(LocalizedStringDataModel("main", text.trim()))
)

internal fun String.toStockConditionDataModel(): StockConditionDataModel {
    val clean = trim()
    if (clean.isBlank()) return legacyStockCondition("")

    if (clean.startsWith(STOCK_CONDITION_STORAGE_PREFIX)) {
        val payload = clean.removePrefix(STOCK_CONDITION_STORAGE_PREFIX)
        val parsedText = extractStockConditionJsonArray(payload, "text")
            ?.let { raw ->
                runCatching {
                    jsonBase.decodeFromString(ListSerializer(LocalizedStringDataModel.serializer()), raw)
                }.getOrNull()
            }
            .orEmpty()

        return StockConditionDataModel(
            kind = extractStockConditionJsonString(payload, "kind") ?: STOCK_CONDITION_KIND_CUSTOM_TEXT,
            transactionTypeIndex = extractStockConditionJsonInt(payload, "transactionTypeIndex") ?: 0,
            text = parsedText,
            minimumAge = extractStockConditionJsonInt(payload, "minimumAge") ?: 18,
            startsAtMinutes = extractStockConditionJsonInt(payload, "startsAtMinutes") ?: 6 * 60,
            endsAtMinutes = extractStockConditionJsonInt(payload, "endsAtMinutes") ?: 22 * 60,
            marginLimitPercent = extractStockConditionJsonString(payload, "marginLimitPercent") ?: "30"
        ).normalizedStockCondition()
    }

    return legacyStockCondition(clean)
}

internal fun formatStockConditionMinute(minuteOfDay: Int): String {
    val clean = minuteOfDay.coerceIn(0, 23 * 60 + 59)
    val hours = clean / 60
    val minutes = clean % 60
    return "${hours.toString().padStart(2, '0')}.${minutes.toString().padStart(2, '0')}"
}

internal fun parseStockConditionMinute(raw: String): Int? {
    val clean = raw.trim()
    val compactDigits = clean.filter { it.isDigit() }
    val normalized = when {
        clean.contains(':') || clean.contains('.') -> clean.replace('.', ':')
        compactDigits.length in 3..4 -> compactDigits.dropLast(2) + ":" + compactDigits.takeLast(2)
        compactDigits.length in 1..2 -> compactDigits + ":00"
        else -> clean
    }
    val parts = normalized.split(':')
    if (parts.size != 2) return null
    val hours = parts[0].toIntOrNull() ?: return null
    val minutes = parts[1].toIntOrNull() ?: return null
    if (hours !in 0..23 || minutes !in 0..59) return null
    return hours * 60 + minutes
}

internal fun currentStockConditionLocalMinuteOfDay(): Int {
    val localDateTime = Instant
        .fromEpochMilliseconds(getCurrentTimeMillis())
        .toLocalDateTime(TimeZone.currentSystemDefault())
    return localDateTime.hour * 60 + localDateTime.minute
}

internal fun isMinuteInsideStockConditionWindow(now: Int, start: Int, end: Int): Boolean {
    val cleanNow = now.coerceIn(0, 23 * 60 + 59)
    val cleanStart = start.coerceIn(0, 23 * 60 + 59)
    val cleanEnd = end.coerceIn(0, 23 * 60 + 59)

    return if (cleanStart <= cleanEnd) {
        cleanNow in cleanStart..cleanEnd
    } else {
        cleanNow >= cleanStart || cleanNow <= cleanEnd
    }
}

internal fun AppConfiguration.stockConditionTransactionTitle(transactionTypeIndex: Int): String = when (transactionTypeIndex) {
    1 -> stateValues.stringReturn
    2 -> stateValues.stringSupply
    else -> stateValues.stringSale
}

internal fun AppConfiguration.stockConditionKindTitle(kind: String): String = when (kind) {
    STOCK_CONDITION_KIND_BUYER_MINIMUM_AGE -> localizedStringResource(1012, "Buyer age")
    STOCK_CONDITION_KIND_TRANSACTION_TIME_WINDOW -> localizedStringResource(1013, "Transaction time")
    STOCK_CONDITION_KIND_MARGIN_LIMIT -> localizedStringResource(1499, "Margin limit")
    else -> localizedStringResource(1011, "Manual condition")
}

internal fun AppConfiguration.visibleStockConditionText(condition: StockConditionDataModel): String {
    val normalized = condition.normalizedStockCondition()
    return when (normalized.kind) {
        STOCK_CONDITION_KIND_BUYER_MINIMUM_AGE -> "${localizedStringResource(1019, "Buyer must be at least")} ${normalized.minimumAge} ${localizedStringResource(1020, "years old")}."
        STOCK_CONDITION_KIND_TRANSACTION_TIME_WINDOW -> {
            val start = formatStockConditionMinute(normalized.startsAtMinutes)
            val end = formatStockConditionMinute(normalized.endsAtMinutes)
            "${stockConditionTransactionTitle(normalized.transactionTypeIndex)}: ${localizedStringResource(1021, "allowed only from")} $start ${localizedStringResource(1022, "to")} $end."
        }
        STOCK_CONDITION_KIND_MARGIN_LIMIT -> "${localizedStringResource(1502, "Store may add no more than")} ${normalized.marginLimitPercent}% ${localizedStringResource(1499, "margin limit").lowercase()}."
        else -> normalized.text.visibleLocalizedString(stateValues.appLanguage, localizedStringResource(611, "Enter condition"))
    }
}

internal fun AppConfiguration.stockConditionStatusText(condition: StockConditionDataModel): String? {
    val normalized = condition.normalizedStockCondition()
    if (normalized.kind != STOCK_CONDITION_KIND_TRANSACTION_TIME_WINDOW) return null

    val satisfied = isMinuteInsideStockConditionWindow(
        currentStockConditionLocalMinuteOfDay(),
        normalized.startsAtMinutes,
        normalized.endsAtMinutes
    )

    return if (satisfied) {
        localizedStringResource(1023, "Available now")
    } else {
        localizedStringResource(1024, "Not available at this time")
    }
}

internal fun AppConfiguration.defaultStockConditionsForGoodsCategory(category: GenericGoodsCategoryDataModel?): List<StockConditionDataModel> {
    if (category == null) return emptyList()

    val searchText = buildString {
        append(category.id.lowercase())
        append(' ')
        append(category.typeIds.orEmpty().joinToString(" ").lowercase())
        append(' ')
        append(category.name.joinToString(" ") { it.value.lowercase() })
        append(' ')
        append(category.alias.orEmpty().joinToString(" ") { it.value.lowercase() })
        append(' ')
        append(category.description.orEmpty().joinToString(" ") { it.value.lowercase() })
    }

    val looksAlcoholRelated = listOf(
        "alcohol", "beer", "wine", "vodka", "spirits",
        "алког", "пиво", "вино", "водка", "спирт",
        "алко", "шарап", "сыра"
    ).any { token -> searchText.contains(token) }

    return if (looksAlcoholRelated) {
        listOf(
            defaultBuyerMinimumAgeStockCondition(minimumAge = 21),
            defaultTransactionTimeWindowStockCondition(startsAtMinutes = 6 * 60, endsAtMinutes = 22 * 60)
        )
    } else {
        emptyList()
    }
}

internal fun List<String>.missingDefaultStockConditions(defaults: List<StockConditionDataModel>): List<StockConditionDataModel> {
    val existingKinds = map { it.toStockConditionDataModel().normalizedStockCondition().kind }.toSet()
    return defaults.filter { it.kind !in existingKinds }
}

data class StockAddEditDraft(
    val id: String = "",
    val barcodes: List<String> = listOf(""),
    val barcodeTypes: List<String> = listOf(GOODS_ITEM_BARCODE_TYPE_STANDARD),
    val name: List<LocalizedStringDataModel> = emptyList(),
    val description: List<LocalizedStringDataModel> = emptyList(),
    val measurementUnitId: String = "0",
    val categoryIds: List<String> = emptyList(),
    val salePrices: List<PriceDataModel> = emptyList(),
    val returnPrices: List<PriceDataModel> = emptyList(),
    val supplyPrices: List<PriceDataModel> = emptyList(),
    val wholesalePrices: List<PriceDataModel> = emptyList(),
    val wholesaleMinQuantityText: String = "",
    val genericExpirationPeriod: ExpirationPeriodDataModel? = null,
    val promotions: List<StockPromotionDataModel> = emptyList(),
    val isQuickItem: Boolean = false,
    val note: String = "",
    val noteLocalized: List<LocalizedStringDataModel> = emptyList(),
    val conditions: List<String> = emptyList()
)

/**
 * Normalizes a draft only at creation/restoration boundaries.
 *
 * A user-created extra blank row must remain visible while editing, but an old persisted draft that
 * contains only multiple blank rows should reopen as one clean initial barcode field.
 */
internal fun List<String>.normalizedInitialStockBarcodeRows(): List<String> = when {
    isEmpty() -> listOf("")
    all { it.isBlank() } -> listOf("")
    else -> this
}

internal fun List<String>.alignedStockBarcodeTypes(barcodes: List<String>): List<String> {
    val targetBarcodes = barcodes.ifEmpty { listOf("") }
    return List(targetBarcodes.size.coerceAtLeast(1)) { index ->
        val barcode = targetBarcodes.getOrNull(index).orEmpty()
        getOrNull(index)?.normalizedGoodsItemBarcodeType(barcode) ?: GOODS_ITEM_BARCODE_TYPE_STANDARD
    }
}

internal fun List<String>.alignedStockBarcodeTypes(size: Int): List<String> {
    return alignedStockBarcodeTypes(List(size.coerceAtLeast(1)) { "" })
}

internal fun StockAddEditDraft.visibleBarcodes(): List<String> = barcodes.ifEmpty { listOf("") }
internal fun StockAddEditDraft.visibleBarcodeTypes(): List<String> = barcodeTypes.alignedStockBarcodeTypes(visibleBarcodes())

internal const val STOCK_ADD_EDIT_DRAFT_STORAGE_PREFIX = "aita-stock-add-edit-draft-v1:"
internal const val STOCK_ADD_EDIT_DRAFT_SEPARATOR = "\u001E"

internal fun String.cleanForStockAddEditDraftState(): String = replace(STOCK_ADD_EDIT_DRAFT_SEPARATOR, " ")

internal fun StockAddEditDraft.toPersistentDraftStateString(): String {
    val persistentBarcodes = barcodes.normalizedInitialStockBarcodeRows()
    val persistentBarcodeTypes = barcodeTypes.alignedStockBarcodeTypes(persistentBarcodes)

    return STOCK_ADD_EDIT_DRAFT_STORAGE_PREFIX + listOf(
        id,
        jsonBase.encodeToString(ListSerializer(String.serializer()), persistentBarcodes),
        jsonBase.encodeToString(ListSerializer(LocalizedStringDataModel.serializer()), name),
        jsonBase.encodeToString(ListSerializer(LocalizedStringDataModel.serializer()), description),
        measurementUnitId,
        jsonBase.encodeToString(ListSerializer(String.serializer()), categoryIds),
        jsonBase.encodeToString(ListSerializer(PriceDataModel.serializer()), salePrices),
        jsonBase.encodeToString(ListSerializer(PriceDataModel.serializer()), returnPrices),
        jsonBase.encodeToString(ListSerializer(PriceDataModel.serializer()), supplyPrices),
        jsonBase.encodeToString(ListSerializer(PriceDataModel.serializer()), wholesalePrices),
        wholesaleMinQuantityText,
        genericExpirationPeriod?.let { jsonBase.encodeToString(ExpirationPeriodDataModel.serializer(), it) }.orEmpty(),
        jsonBase.encodeToString(ListSerializer(StockPromotionDataModel.serializer()), promotions),
        isQuickItem.toString(),
        note,
        jsonBase.encodeToString(ListSerializer(LocalizedStringDataModel.serializer()), noteLocalized),
        jsonBase.encodeToString(ListSerializer(String.serializer()), conditions),
        jsonBase.encodeToString(ListSerializer(String.serializer()), persistentBarcodeTypes)
    ).joinToString(STOCK_ADD_EDIT_DRAFT_SEPARATOR) { it.cleanForStockAddEditDraftState() }
}

internal fun String.toPersistentStockAddEditDraftOrNull(): StockAddEditDraft? {
    val payload = trim().removePrefix(STOCK_ADD_EDIT_DRAFT_STORAGE_PREFIX)
    if (payload.isBlank()) return null
    val values = payload.split(STOCK_ADD_EDIT_DRAFT_SEPARATOR)
    if (values.size < 17) return null

    return runCatching {
        val decodedBarcodes = jsonBase
            .decodeFromString(ListSerializer(String.serializer()), values[1])
            .normalizedInitialStockBarcodeRows()
        val decodedBarcodeTypes = values.getOrNull(17)
            ?.takeIf { it.isNotBlank() }
            ?.let { jsonBase.decodeFromString(ListSerializer(String.serializer()), it) }
            .orEmpty()
            .alignedStockBarcodeTypes(decodedBarcodes)

        StockAddEditDraft(
            id = values[0],
            barcodes = decodedBarcodes,
            barcodeTypes = decodedBarcodeTypes,
            name = jsonBase.decodeFromString(ListSerializer(LocalizedStringDataModel.serializer()), values[2]),
            description = jsonBase.decodeFromString(ListSerializer(LocalizedStringDataModel.serializer()), values[3]),
            measurementUnitId = values[4].ifBlank { "0" },
            categoryIds = jsonBase.decodeFromString(ListSerializer(String.serializer()), values[5]),
            salePrices = jsonBase.decodeFromString(ListSerializer(PriceDataModel.serializer()), values[6]),
            returnPrices = jsonBase.decodeFromString(ListSerializer(PriceDataModel.serializer()), values[7]),
            supplyPrices = jsonBase.decodeFromString(ListSerializer(PriceDataModel.serializer()), values[8]),
            wholesalePrices = jsonBase.decodeFromString(ListSerializer(PriceDataModel.serializer()), values[9]),
            wholesaleMinQuantityText = values[10],
            genericExpirationPeriod = values[11].takeIf { it.isNotBlank() }?.let {
                jsonBase.decodeFromString(ExpirationPeriodDataModel.serializer(), it)
            },
            promotions = jsonBase.decodeFromString(ListSerializer(StockPromotionDataModel.serializer()), values[12]),
            isQuickItem = values[13].toBooleanStrictOrNull() ?: false,
            note = values[14],
            noteLocalized = jsonBase.decodeFromString(ListSerializer(LocalizedStringDataModel.serializer()), values[15]),
            conditions = jsonBase.decodeFromString(ListSerializer(String.serializer()), values[16])
        )
    }.getOrNull()
}

fun GoodsItemDataModel.toStockAddEditDraft(): StockAddEditDraft {
    val effectiveBarcodeModels = effectiveBarcodeModels()
    val visibleBarcodeValues = effectiveBarcodeModels
        .toLegacyBarcodeStrings()
        .ifEmpty { barcodes }
        .normalizedInitialStockBarcodeRows()
    val visibleBarcodeTypes = effectiveBarcodeModels
        .map { it.type.normalizedGoodsItemBarcodeType(it.value) }
        .ifEmpty { visibleBarcodeValues.map { GOODS_ITEM_BARCODE_TYPE_STANDARD } }
        .alignedStockBarcodeTypes(visibleBarcodeValues)

    return StockAddEditDraft(
        id = id,
        barcodes = visibleBarcodeValues,
        barcodeTypes = visibleBarcodeTypes,
        name = name,
        description = description,
        measurementUnitId = measurementUnitId,
        categoryIds = categoryIds,
        salePrices = salePrices,
        returnPrices = returnPrices,
        supplyPrices = supplyPrices,
        wholesalePrices = wholesalePrices,
        wholesaleMinQuantityText = wholesaleMinQuantity
            ?.let { minQuantity -> stockQuantityInputTextFromAmount(minQuantity.total, minQuantity) }
            .orEmpty(),
        genericExpirationPeriod = genericExpirationPeriod,
        promotions = promotions,
        isQuickItem = isQuickItem,
        note = note.orEmpty(),
        noteLocalized = noteLocalized.ifEmpty {
            note?.takeIf { it.isNotBlank() }?.let { listOf(LocalizedStringDataModel("main", it)) } ?: emptyList()
        },
        conditions = conditions
    )
}

fun StockAddEditDraft.toGoodsItem(
    storeId: String,
    configuration: GlobalAppConfigurationDataModel,
    current: GoodsItemDataModel? = null
): GoodsItemDataModel {
    val now = getCurrentTimeMillis()
    val selectedUnit = configuration.goodsItemsQuantityUnits.find { it.id == measurementUnitId }
        ?: configuration.goodsItemsQuantityUnits.firstOrNull()
    val wholesaleMinimumUnit = selectedUnit ?: QuantityDataModel(
        id = measurementUnitId,
        immutableUnitName = emptyList(),
        total = 1.0,
        pricedAmount = 1.0,
        roundTotal = measurementUnitId == "0"
    )
    val wholesaleMinimumQuantity = parseStockQuantityInputText(wholesaleMinQuantityText, wholesaleMinimumUnit)
        ?.takeIf { it > 0.0 }
        ?.let { total -> wholesaleMinimumUnit.withStockQuantityInputTotalValue(total) }
    val cleanNoteLocalized = noteLocalized.filter { it.value.isNotBlank() }
    val legacyNote = cleanNoteLocalized.extractLocalizedString("main")
        ?: cleanNoteLocalized.firstOrNull()?.value
        ?: note.takeIf { it.isNotBlank() }
    val cleanBarcodeModels = visibleBarcodes().flatMapIndexed { index, rawBarcode ->
        val cleanType = visibleBarcodeTypes()
            .getOrNull(index)
            ?.normalizedGoodsItemBarcodeType(rawBarcode)
            ?: GOODS_ITEM_BARCODE_TYPE_STANDARD
        rawBarcode.trim().toStoredGoodsItemBarcodeCandidates().map { candidate ->
            GoodsItemBarcodeDataModel(
                value = candidate,
                type = cleanType,
                storeId = if (cleanType == GOODS_ITEM_BARCODE_TYPE_INTERNAL) storeId else null
            )
        }
    }.normalizedGoodsItemBarcodesForStore(storeId)
    val cleanBarcodes = cleanBarcodeModels.toLegacyBarcodeStrings()

    return GoodsItemDataModel(
        id = id,
        userId = current?.userId.orEmpty(),
        storeId = storeId,
        barcodes = cleanBarcodes,
        barcodeModels = cleanBarcodeModels,
        name = name.filter { it.value.isNotBlank() },
        description = description.filter { it.value.isNotBlank() },
        measurementUnitId = measurementUnitId,
        categoryIds = categoryIds,
        salePrices = salePrices,
        returnPrices = returnPrices,
        supplyPrices = supplyPrices,
        wholesalePrices = wholesalePrices.filter { it.price.isNotBlank() && it.price.toMoneyDouble() > 0.0 },
        wholesaleMinQuantity = wholesaleMinimumQuantity,
        genericExpirationPeriod = genericExpirationPeriod,
        promotions = promotions.sanitizedStockPromotions(),
        isQuickItem = isQuickItem,
        imagePaths = current?.imagePaths.orEmpty(),
        activeShelfBatchId = current?.activeShelfBatchId,
        note = legacyNote,
        noteLocalized = cleanNoteLocalized,
        conditions = conditions.map { it.trim() }.filter { it.isNotBlank() }.distinct(),
        createdAtMillis = current?.createdAtMillis ?: now,
        updatedAtMillis = now,
        isActive = true
    )
}

fun StockAddEditDraft.isValidStockDraft(configuration: GlobalAppConfigurationDataModel): Boolean {
    val cleanBarcodes = barcodes.map { it.trim().toStoredGoodsItemBarcode() }.filter { it.isNotEmpty() }

    val hasName = name.any { it.value.isNotBlank() }
    val hasBarcode = cleanBarcodes.isNotEmpty()
    val hasUnit = measurementUnitId.isNotBlank()
    val hasSalePrice = salePrices.any { it.price.toDoubleOrNull()?.let { price -> price >= 0.0 } == true }
    val hasSupplyPrice = supplyPrices.any { it.price.toDoubleOrNull()?.let { price -> price >= 0.0 } == true }
    val hasWholesalePrice = wholesalePrices.any { it.price.toDoubleOrNull()?.let { price -> price > 0.0 } == true }
    val wholesaleMinimumUnit = configuration.goodsItemsQuantityUnits.find { it.id == measurementUnitId }
        ?: configuration.goodsItemsQuantityUnits.firstOrNull()
        ?: QuantityDataModel(
            id = measurementUnitId,
            immutableUnitName = emptyList(),
            total = 1.0,
            pricedAmount = 1.0,
            roundTotal = measurementUnitId == "0"
        )
    val hasWholesaleMinimum = parseStockQuantityInputText(wholesaleMinQuantityText, wholesaleMinimumUnit)?.let { it > 0.0 } == true

    return hasName && hasBarcode && hasUnit && hasSalePrice && hasSupplyPrice && (!hasWholesalePrice || hasWholesaleMinimum)
}

internal fun StockPromotionDataModel.visiblePromotionTitle(language: String, fallback: String): String =
    title.visibleLocalizedString(language, fallback).ifBlank { fallback }

internal fun List<LocalizedStringDataModel>.withSingleLanguageValue(language: String, value: String): List<LocalizedStringDataModel> {
    val normalizedLanguage = language.takeIf { it.isNotBlank() } ?: DEFAULT_APP_LANGUAGE
    val mutable = toMutableList()
    val index = mutable.indexOfFirst { it.language == normalizedLanguage }
    val entry = LocalizedStringDataModel(normalizedLanguage, value)

    if (index >= 0) mutable[index] = entry else mutable += entry

    if (mutable.none { it.language == "main" }) {
        mutable += LocalizedStringDataModel("main", value)
    } else if (normalizedLanguage == DEFAULT_APP_LANGUAGE || normalizedLanguage == "main") {
        val mainIndex = mutable.indexOfFirst { it.language == "main" }
        mutable[mainIndex] = LocalizedStringDataModel("main", value)
    }

    return mutable.filter { it.value.isNotBlank() }
}

internal fun defaultStockPromotion(language: String): StockPromotionDataModel = StockPromotionDataModel(
    id = "promo_${getCurrentTimeMillis()}",
    title = listOf(
        LocalizedStringDataModel("main", "Promo"),
        LocalizedStringDataModel(language.takeIf { it.isNotBlank() } ?: DEFAULT_APP_LANGUAGE, "Promo")
    ).distinctBy { it.language },
    type = STOCK_PROMOTION_TYPE_DISCOUNT,
    mode = STOCK_PROMOTION_MODE_PERCENT,
    value = "10",
    transactionTypeIndices = listOf(0),
    isActive = true
)

@Composable
internal fun AppConfiguration.StockPromotionListEditor(
    promotions: List<StockPromotionDataModel>,
    onPromotionsChanged: (List<StockPromotionDataModel>) -> Unit,
    quantityUnit: QuantityDataModel? = null,
    allowApplyToSameSupplier: Boolean = false,
    applyToSameSupplier: Boolean = false,
    onApplyToSameSupplierChanged: ((Boolean) -> Unit)? = null
) {
    val fallbackQuantityUnit = quantityUnit
        ?: stateValues.globalAppConfiguration.goodsItemsQuantityUnits.firstOrNull()
        ?: QuantityDataModel(
            id = "0",
            immutableUnitName = listOf(LocalizedStringDataModel("main", "pcs")),
            total = 1.0,
            pricedAmount = 1.0,
            roundTotal = true
        )
    val promoQuantityAllowsFraction = fallbackQuantityUnit.allowsFractionalStockQuantityInput()

    fun replacePromotion(index: Int, promotion: StockPromotionDataModel) {
        onPromotionsChanged(
            promotions.toMutableList().also { list ->
                if (index in list.indices) list[index] = promotion.normalized()
            }
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = localizedStringResource(936, "Promo period"),
            color = stateValues.TextColor,
            fontSize = stateValues.titleTextSize,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(stateValues.marginTextField))

        if (promotions.isEmpty()) {
            MessageText(
                modifier = Modifier.fillMaxWidth(),
                text = localizedStringResource(922, "No promos yet")
            )
        }

        promotions.forEachIndexed { index, rawPromotion ->
            val promotion = rawPromotion.normalized()
            val typeOptions = listOf(
                DropdownOption(STOCK_PROMOTION_TYPE_DISCOUNT, localizedStringResource(924, "Discount")),
                DropdownOption(STOCK_PROMOTION_TYPE_SPECIAL_PRICE, localizedStringResource(925, "Special price")),
                DropdownOption(STOCK_PROMOTION_TYPE_RESTRICTION, localizedStringResource(926, "Restriction"))
            )
            val modeOptions = listOf(
                DropdownOption(STOCK_PROMOTION_MODE_PERCENT, localizedStringResource(927, "Percent")),
                DropdownOption(STOCK_PROMOTION_MODE_FIXED, localizedStringResource(928, "Fixed amount")),
                DropdownOption(STOCK_PROMOTION_MODE_PRICE, localizedStringResource(929, "New price"))
            )
            val transactionOptions = listOf(
                DropdownOption("all", localizedStringResource(938, "All transaction types")),
                DropdownOption("0", stateValues.stringSale),
                DropdownOption("1", stateValues.stringReturn),
                DropdownOption("2", stateValues.stringSupply)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
                    .clip(RoundedCornerShape(stateValues.cornerRadius))
                    .background(stateValues.BackgroundColor)
                    .border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor, RoundedCornerShape(stateValues.cornerRadius))
                    .padding(stateValues.marginTextField)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = promotion.visiblePromotionTitle(stateValues.appLanguage, "${localizedStringResource(920, "Promos")} ${index + 1}"),
                        color = stateValues.AccentColor,
                        fontSize = stateValues.accentTextSize,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    actionButton(
                        text = "",
                        iconPath = stateValues.drawablePathIconDelete,
                        iconContentDescription = stateValues.stringDelete,
                        enabledColor = stateValues.ErrorColor,
                        onClick = {
                            onPromotionsChanged(promotions.toMutableList().also { it.removeAt(index) })
                        }
                    )
                }

                Spacer(modifier = Modifier.height(stateValues.marginTextField))

                SimpleTextInput(
                    modifier = Modifier.fillMaxWidth(),
                    value = promotion.title.extractLocalizedString(stateValues.appLanguage)
                        ?: promotion.title.extractLocalizedString("main")
                        ?: "",
                    placeholder = localizedStringResource(923, "Promo title"),
                    leadingIconPath = stateValues.drawablePathIconPromos,
                    onValueChange = { value ->
                        replacePromotion(index, promotion.copy(title = promotion.title.withSingleLanguageValue(stateValues.appLanguage, value)))
                    }
                )

                Spacer(modifier = Modifier.height(stateValues.marginTextField))

                SimpleDropdownField(
                    title = localizedStringResource(936, "Promo period"),
                    selectedId = promotion.type,
                    options = typeOptions,
                    placeholder = localizedStringResource(924, "Discount"),
                    onSelected = { selectedType ->
                        val nextMode = when (selectedType) {
                            STOCK_PROMOTION_TYPE_SPECIAL_PRICE -> STOCK_PROMOTION_MODE_PRICE
                            STOCK_PROMOTION_TYPE_RESTRICTION -> STOCK_PROMOTION_MODE_FIXED
                            else -> promotion.mode.takeIf { it != STOCK_PROMOTION_MODE_PRICE } ?: STOCK_PROMOTION_MODE_PERCENT
                        }
                        replacePromotion(index, promotion.copy(type = selectedType, mode = nextMode))
                    }
                )

                Spacer(modifier = Modifier.height(stateValues.marginTextField))

                if (promotion.type != STOCK_PROMOTION_TYPE_RESTRICTION) {
                    SimpleDropdownField(
                        title = localizedStringResource(930, "Value"),
                        selectedId = promotion.mode,
                        options = if (promotion.type == STOCK_PROMOTION_TYPE_SPECIAL_PRICE) {
                            listOf(DropdownOption(STOCK_PROMOTION_MODE_PRICE, localizedStringResource(929, "New price")))
                        } else {
                            modeOptions
                        },
                        placeholder = localizedStringResource(927, "Percent"),
                        onSelected = { replacePromotion(index, promotion.copy(mode = it)) }
                    )

                    Spacer(modifier = Modifier.height(stateValues.marginTextField))

                    SimpleTextInput(
                        modifier = Modifier.fillMaxWidth(),
                        value = promotion.value,
                        placeholder = when (promotion.mode) {
                            STOCK_PROMOTION_MODE_PERCENT -> "%"
                            STOCK_PROMOTION_MODE_PRICE -> localizedStringResource(929, "New price")
                            else -> localizedStringResource(928, "Fixed amount")
                        },
                        keyboardType = KeyboardType.Decimal,
                        leadingIconPath = stateValues.drawablePathIconFinances,
                        onValueChange = { value ->
                            if (value.isEmpty() || value.replace(',', '.').isNumericalDoubleString()) {
                                replacePromotion(index, promotion.copy(value = value.replace(',', '.')))
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(stateValues.marginTextField))
                }

                var promoMinQuantityText by rememberSaveable(promotion.id, fallbackQuantityUnit.id) {
                    mutableStateOf(
                        promotion.minQuantity
                            ?.let { amount -> stockQuantityInputTextFromAmount(amount, fallbackQuantityUnit) }
                            .orEmpty()
                    )
                }

                LaunchedEffect(promotion.id, promotion.minQuantity, fallbackQuantityUnit.id) {
                    val externalMinQuantity = promotion.minQuantity
                    val nextText = externalMinQuantity
                        ?.let { amount -> stockQuantityInputTextFromAmount(amount, fallbackQuantityUnit) }
                        .orEmpty()
                    val localAmount = parseStockQuantityInputText(promoMinQuantityText, fallbackQuantityUnit)

                    if (externalMinQuantity == null && promoMinQuantityText.isBlank()) return@LaunchedEffect
                    if (externalMinQuantity != null && localAmount != null && abs(localAmount - externalMinQuantity) < 0.000001) return@LaunchedEffect
                    if (!promoMinQuantityText.endsWith(".")) {
                        promoMinQuantityText = nextText
                    }
                }

                SimpleTextInput(
                    modifier = Modifier.fillMaxWidth(),
                    value = promoMinQuantityText,
                    placeholder = localizedStringResource(931, "Minimum quantity"),
                    keyboardType = if (promoQuantityAllowsFraction) KeyboardType.Decimal else KeyboardType.Number,
                    leadingIconPath = stateValues.drawablePathIconStock,
                    onTransformValue = { raw -> sanitizeStockQuantityInput(raw, promoQuantityAllowsFraction) },
                    onValueChange = { value ->
                        if (value.isStockQuantityInputText(promoQuantityAllowsFraction)) {
                            promoMinQuantityText = value
                            replacePromotion(
                                index,
                                promotion.copy(minQuantity = parseStockQuantityInputText(value, fallbackQuantityUnit))
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(6.dp))

                StockQuantityQuickFillButtons(
                    quantityUnit = fallbackQuantityUnit,
                    currentText = promoMinQuantityText,
                    onAmountSelected = { selectedAmount ->
                        promoMinQuantityText = selectedAmount
                        replacePromotion(
                            index,
                            promotion.copy(minQuantity = parseStockQuantityInputText(selectedAmount, fallbackQuantityUnit))
                        )
                    }
                )

                Spacer(modifier = Modifier.height(stateValues.marginTextField))

                SimpleDropdownField(
                    title = localizedStringResource(111, "Payment"),
                    selectedId = promotion.transactionTypeIndices.singleOrNull()?.toString() ?: "all",
                    options = transactionOptions,
                    placeholder = localizedStringResource(938, "All transaction types"),
                    onSelected = { selected ->
                        replacePromotion(
                            index,
                            promotion.copy(transactionTypeIndices = selected.toIntOrNull()?.let { listOf(it) } ?: emptyList())
                        )
                    }
                )

                Spacer(modifier = Modifier.height(stateValues.marginTextField))

                StockDateRangeEditor(
                    title = localizedStringResource(936, "Promo period"),
                    startTitle = localizedStringResource(932, "Starts"),
                    endTitle = localizedStringResource(933, "Ends"),
                    startDateText = promotion.startsAtMillis.toStockDateInputText(),
                    endDateText = promotion.endsAtMillis.toStockDateInputText(),
                    onStartDateChanged = { replacePromotion(index, promotion.copy(startsAtMillis = stockDateInputTextToMillis(it))) },
                    onEndDateChanged = { replacePromotion(index, promotion.copy(endsAtMillis = stockDateInputTextToMillis(it))) }
                )

                Spacer(modifier = Modifier.height(stateValues.marginTextField))

                StockLocalizedStringGroupEditor(
                    title = localizedStringResource(201, "Notes"),
                    placeholder = stateValues.stringOptional,
                    values = promotion.noteLocalized.ifEmpty {
                        promotion.note?.takeIf { it.isNotBlank() }?.let { listOf(LocalizedStringDataModel("main", it)) }
                            ?: emptyLocalizedItemForCurrentLanguage()
                    },
                    addText = localizedStringResource(951, "Add promo note translation"),
                    required = false,
                    singleLine = false,
                    adaptiveMultiline = true,
                    onChanged = { notes ->
                        replacePromotion(
                            index,
                            promotion.copy(
                                noteLocalized = notes,
                                note = notes.extractLocalizedString("main")
                                    ?: notes.firstOrNull { item -> item.value.isNotBlank() }?.value
                            )
                        )
                    }
                )

                Spacer(modifier = Modifier.height(stateValues.marginTextField))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    AitaRoundCheckbox(
                        checked = promotion.isActive,
                        onCheckedChange = { checked -> replacePromotion(index, promotion.copy(isActive = checked)) }
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = localizedStringResource(935, "Active promo"),
                        color = stateValues.TextColor,
                        fontSize = stateValues.textSize
                    )
                }
            }
        }

        if (allowApplyToSameSupplier && onApplyToSameSupplierChanged != null) {
            Spacer(modifier = Modifier.height(stateValues.marginTextField))

            Row(verticalAlignment = Alignment.CenterVertically) {
                AitaRoundCheckbox(
                    checked = applyToSameSupplier,
                    onCheckedChange = onApplyToSameSupplierChanged
                )

                Spacer(modifier = Modifier.width(4.dp))

                Text(
                    text = localizedStringResource(934, "Apply to all batches of this supplier"),
                    color = stateValues.TextColor,
                    fontSize = stateValues.textSize
                )
            }
        }

        Spacer(modifier = Modifier.height(stateValues.marginTextField))

        actionButton(
            text = localizedStringResource(921, "Add promo"),
            iconPath = stateValues.drawablePathIconPromos,
            iconRes = stateValues.drawableResIconPromos.value,
            confirmationRequired = false,
            fillMaxWidthIfTextPresent = false,
            onClick = { onPromotionsChanged(promotions + defaultStockPromotion(stateValues.appLanguage)) }
        )
    }
}

internal data class StockPromotedPriceDisplayLine(
    val title: String,
    val promotedPrice: PromotedPriceDataModel
)

internal fun stockCompactPriceKey(price: PriceDataModel): String? {
    val currency = price.currency.trim()
    val rawAmount = price.price.trim().replace(',', '.')
    if (currency.isBlank() || rawAmount.isBlank()) return null
    val normalizedAmount = rawAmount.toDoubleOrNull()?.let { moneyInputFromDouble(it) } ?: rawAmount
    return "$normalizedAmount|$currency"
}

internal fun PromotedPriceDataModel.compactStockPriceKeyOrNull(): String? {
    if (hasPriceChange || promotion != null) return null
    return stockCompactPriceKey(finalPrice)
}

internal fun stockPriceAmountCurrencyMatches(first: PriceDataModel?, second: PriceDataModel?): Boolean {
    return stockCompactPriceKey(first ?: return false) == stockCompactPriceKey(second ?: return false)
}

@Composable
internal fun AppConfiguration.StockCompactPromotionPriceInfoLines(
    lines: List<StockPromotedPriceDisplayLine>,
    textColor: Color
) {
    val visibleLines = lines.filter { line ->
        line.promotedPrice.finalPrice.price.isNotBlank() || line.promotedPrice.originalPrice.price.isNotBlank()
    }
    val emittedCompactKeys = mutableSetOf<String>()

    visibleLines.forEach { line ->
        val compactKey = line.promotedPrice.compactStockPriceKeyOrNull()
        if (compactKey == null) {
            StockPromotionPriceInfoLine(
                title = line.title,
                promotedPrice = line.promotedPrice,
                textColor = textColor
            )
            return@forEach
        }

        if (!emittedCompactKeys.add(compactKey)) return@forEach

        val compactGroup = visibleLines.filter { other ->
            other.promotedPrice.compactStockPriceKeyOrNull() == compactKey
        }

        StockPromotionPriceInfoLine(
            title = compactGroup.joinToString(", ") { it.title },
            promotedPrice = line.promotedPrice,
            textColor = textColor
        )
    }
}

@Composable
internal fun AppConfiguration.StockPromotionPriceInfoLine(
    title: String,
    promotedPrice: PromotedPriceDataModel,
    textColor: Color
) {
    if (!promotedPrice.hasPriceChange) {
        StockCardInfoLine(
            title = title,
            value = "${promotedPrice.finalPrice.price} ${promotedPrice.finalPrice.currency}".trim(),
            textColor = textColor
        )
        return
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "$title: ",
            fontSize = stateValues.textSize,
            color = textColor,
            fontWeight = accentTextWeight(textColor, stateValues.AccentColor),
            style = TextStyle(shadow = accentTextShadow(textColor, stateValues.AccentColor))
        )

        Text(
            text = "${promotedPrice.originalPrice.price} ${promotedPrice.originalPrice.currency}".trim(),
            fontSize = stateValues.smallTextSize,
            color = stateValues.PlaceholderTextColor,
            textDecoration = TextDecoration.LineThrough,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = "${promotedPrice.finalPrice.price} ${promotedPrice.finalPrice.currency}".trim(),
            fontSize = stateValues.textSize,
            color = stateValues.OkayColor,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }

    promotedPrice.promotion?.let { promotion ->
        Text(
            text = promotion.visiblePromotionTitle(stateValues.appLanguage, localizedStringResource(920, "Promos")),
            color = stateValues.AccentColor,
            fontSize = stateValues.smallTextSize,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun AppConfiguration.AitaBottomSheet(
    title: String,
    iconPath: String? = null,
    iconRes: DrawableResource = Res.drawable._0_0,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val bottomNavReserve = if (stateValues.navigationScreensMain.lastOrNull()?.run {
            this !is NavigationScreenModel.Splash && this !is NavigationScreenModel.UserAuth
        } == true) 60.dp else 0.dp

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = bottomNavReserve)
                .background(Color.Black.copy(alpha = 0.32f))
                .padding(top = stateValues.screenHeight * 0.06f),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .aitaBottomSheetEntrance()
                    .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.78f)
                    .heightIn(min = stateValues.screenHeight * 0.38f, max = (stateValues.screenHeight * 0.92f - bottomNavReserve).coerceAtLeast(stateValues.screenHeight * 0.50f))
                    .foregroundTactileShadow(stateValues.cornerRadius, elevated = true)
                    .clip(
                        RoundedCornerShape(
                            topStart = stateValues.cornerRadius,
                            topEnd = stateValues.cornerRadius
                        )
                    )
                    .background(stateValues.BackgroundColor)
                    .border(
                        stateValues.focusedBorderWidth,
                        stateValues.AccentColor,
                        RoundedCornerShape(
                            topStart = stateValues.cornerRadius,
                            topEnd = stateValues.cornerRadius
                        )
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(horizontal = stateValues.marginTextFieldGroup),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    iconPath?.let {
                        CpImage(
                            modifier = Modifier.size(24.dp),
                            url = it,
                            fallbackRes = iconRes,
                            contentDescription = title,
                            tintColor = stateValues.AccentColor
                        )
                    }

                    Text(
                        modifier = Modifier.weight(1f),
                        text = title,
                        color = stateValues.TextColor,
                        fontSize = stateValues.titleTextSize,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    actionButton(
                        text = "",
                        iconPath = stateValues.drawablePathIconCancel,
                        iconRes = stateValues.drawableResIconCancel.value,
                        enabledColor = stateValues.BackgroundColor,
                        textColor = stateValues.TextColor,
                        iconTintColor = stateValues.TextColor,
                        confirmationRequired = false,
                        onClick = onDismiss
                    )
                }

                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(stateValues.unfocusedBorderWidth)
                        .background(stateValues.PlaceholderTextColor)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(
                            start = stateValues.marginTextFieldGroup,
                            end = stateValues.marginTextFieldGroup,
                            top = stateValues.marginTextFieldGroup,
                            bottom = stateValues.marginTextFieldGroup
                        )
                ) {
                    content()
                }
            }
        }
    }
}


@Composable
internal fun StockItemBarcodeBars(
    modifier: Modifier = Modifier,
    barcode: String
) {
    val previewData = remember(barcode) { stockItemLabelBarcodePreviewData(barcode) }
    val modules = previewData.modules.ifEmpty { listOf(false) }

    Row(
        modifier = modifier
            .clipToBounds()
            .background(Color.White),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        modules.forEach { black ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (black) Color.Black else Color.White)
            )
        }
    }
}

@Composable
internal fun AppConfiguration.StockItemLabelPreviewCard(
    label: StockItemLabelDataModel,
    modifier: Modifier = Modifier
) {
    val barcodePreview = remember(label.barcode) { stockItemLabelBarcodePreviewData(label.barcode) }
    val priceText = remember(label.priceText) { stockItemLabelPriceDisplayText(label.priceText) }
    val shape = RoundedCornerShape(2.dp)

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .aspectRatio(58f / 40f)
                .clip(shape)
                .background(Color.White)
                .border(1.dp, Color.Black, shape)
        ) {
            val barcodeStart = maxWidth * 0.045f
            val barcodeTop = maxHeight * 0.49f
            val barcodeWidth = maxWidth * 0.47f
            val barcodeHeight = maxHeight * 0.31f
            val priceEnd = maxWidth * 0.015f
            val priceWidth = maxWidth * 0.455f
            val priceTitleTop = maxHeight * 0.485f
            val priceBoxTop = maxHeight * 0.60f
            val priceBoxHeight = maxHeight * 0.185f

            Text(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(start = maxWidth * 0.02f, end = maxWidth * 0.02f, top = maxHeight * 0.02f),
                text = label.storeName.ifBlank { "AITA" },
                color = Color.Black,
                fontSize = 25.sp,
                lineHeight = 27.sp,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
                textDecoration = TextDecoration.Underline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart)
                    .padding(start = maxWidth * 0.018f, end = maxWidth * 0.018f, top = maxHeight * 0.17f),
                text = label.itemName.ifBlank { localizedStringResource(113, "Unnamed item") },
                color = Color.Black,
                fontSize = 25.sp,
                lineHeight = 27.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = barcodeStart, top = barcodeTop)
                    .width(barcodeWidth)
                    .height(barcodeHeight),
                verticalArrangement = Arrangement.Bottom
            ) {
                StockItemBarcodeBars(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    barcode = label.barcode
                )
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = barcodePreview.humanText,
                    color = Color.Black,
                    fontSize = 14.sp,
                    lineHeight = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
            }

            Text(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = priceEnd, top = priceTitleTop)
                    .width(priceWidth),
                text = label.priceLabel.ifBlank { localizedStringResource(1329, "PRICE") },
                color = Color.Black,
                fontSize = 15.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = priceEnd, top = priceBoxTop)
                    .width(priceWidth)
                    .height(priceBoxHeight)
                    .border(1.dp, Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = 3.dp),
                    text = priceText,
                    color = Color.Black,
                    fontSize = 32.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
internal fun AppConfiguration.StockItemLabelPrintBottomSheet(
    goodsItem: GoodsItemDataModel,
    batches: List<GoodsBatchDataModel>,
    onDismiss: () -> Unit
) {
    val itemName = goodsItem.name.visibleLocalizedString(stateValues.appLanguage, localizedStringResource(113, "Unnamed item"))
    val sortedBatches = batches.sortedForShelf(goodsItem)
    val defaultBatch = sortedBatches.firstOrNull { it.id == goodsItem.activeShelfBatchId }
        ?: sortedBatches.bestBatchForSale(goodsItem)
    var selectedBatchId by rememberSaveable(goodsItem.id, sortedBatches.joinToString("|") { it.id }) {
        mutableStateOf(defaultBatch?.id.orEmpty())
    }
    LaunchedEffect(defaultBatch?.id, sortedBatches.joinToString("|") { it.id }) {
        if (selectedBatchId.isBlank() || sortedBatches.none { it.id == selectedBatchId }) {
            selectedBatchId = defaultBatch?.id.orEmpty()
        }
    }
    val selectedBatch = sortedBatches.firstOrNull { it.id == selectedBatchId } ?: defaultBatch
    val activeStore = stateValues.stores.findStoreOrBranchForUi(stateValues.activeStoreId ?: goodsItem.storeId)
    val officialStoreName = activeStore
        ?.name
        ?.visibleLocalizedString(stateValues.appLanguage, activeStore.publicId.ifBlank { activeStore.id })
        .orEmpty()
        .ifBlank { "AITA" }
    val price = goodsItem.promotedPriceForTransaction(
        transactionTypeIndex = 0,
        saleMethodId = SALE_METHOD_RETAIL,
        quantityTotal = 1.0,
        batch = selectedBatch
    ).finalPrice
    val officialPriceText = listOf(price.price.trim(), price.currency.trim())
        .filter { it.isNotBlank() && it != "0" }
        .joinToString(" ")
    val defaultUnitText = selectedBatch
        ?.quantity
        ?.immutableUnitName
        ?.visibleLocalizedString(stateValues.appLanguage, goodsItem.measurementUnitId)
        ?: goodsItem.measurementUnitId
    val priceLabel = localizedStringResource(1329, "PRICE")
    val barcodeOptions = goodsItem.allBarcodeValues()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()

    var selectedBarcode by rememberSaveable(goodsItem.id, barcodeOptions.joinToString("|")) {
        mutableStateOf(barcodeOptions.firstOrNull().orEmpty())
    }
    var copies by rememberSaveable(goodsItem.id) { mutableStateOf(1) }
    val coroutineScope = rememberCoroutineScope()
    val previewLabel = StockItemLabelDataModel(
        itemName = itemName,
        barcode = selectedBarcode,
        priceText = officialPriceText,
        priceLabel = priceLabel,
        storeName = officialStoreName,
        unitText = defaultUnitText,
        copies = copies,
        protocol = LABEL_PRINTER_PROTOCOL_AUTO
    )

    AitaBottomSheet(
        title = localizedStringResource(1288, "Print item label"),
        iconPath = stateValues.drawablePathIconPrintTag,
        iconRes = stateValues.drawableResIconPrintTag.value,
        onDismiss = onDismiss
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(stateValues.marginTextFieldGroup),
            contentPadding = PaddingValues(bottom = stateValues.screenHeight / 7)
        ) {
            item {
                MessageText(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(1289, "Sticky shelf tag"),
                    subText = localizedStringResource(1290, "Print barcode, name and price on an adhesive item label."),
                    textSize = stateValues.accentTextSize,
                    subTextSize = stateValues.smallTextSize
                )
            }


            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = localizedStringResource(1292, "Label preview"),
                        color = stateValues.AccentColor,
                        fontSize = stateValues.smallTextSize,
                        fontWeight = FontWeight.Bold
                    )
                    StockItemLabelPreviewCard(
                        label = previewLabel,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                if (barcodeOptions.isEmpty()) {
                    MessageText(
                        modifier = Modifier.fillMaxWidth(),
                        text = localizedStringResource(1299, "This item has no barcode yet; add a barcode before printing a shelf label."),
                        textSize = stateValues.textSize
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = localizedStringResource(1293, "Barcode for label"),
                            color = stateValues.TextColor,
                            fontSize = stateValues.accentTextSize,
                            fontWeight = FontWeight.Bold
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp)
                        ) {
                            items(barcodeOptions, key = { it }) { barcode ->
                                actionButton(
                                    text = barcode.take(24),
                                    enabledColor = if (barcode == selectedBarcode) stateValues.AccentColor else stateValues.BackgroundColor,
                                    textColor = if (barcode == selectedBarcode) stateValues.AccentTextColor else stateValues.TextColor,
                                    iconPath = if (barcode == selectedBarcode) stateValues.drawablePathIconCheck else stateValues.drawablePathIconBarcodeType,
                                    iconRes = if (barcode == selectedBarcode) stateValues.drawableResIconCheck.value else stateValues.drawableResIconBarcodeType.value,
                                    iconTintColor = if (barcode == selectedBarcode) stateValues.AccentTextColor else stateValues.TextColor,
                                    fillMaxWidthIfTextPresent = false,
                                    confirmationRequired = false,
                                    onClick = { selectedBarcode = barcode }
                                )
                            }
                        }
                    }
                }
            }

            if (sortedBatches.size > 1) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = localizedStringResource(341, "Batch data"),
                            color = stateValues.TextColor,
                            fontSize = stateValues.accentTextSize,
                            fontWeight = FontWeight.Bold
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp)
                        ) {
                            items(sortedBatches, key = { it.id }) { batch ->
                                val isSelected = batch.id == selectedBatchId
                                val batchInfo = buildList {
                                    add(batch.quantity.quantityText(stateValues.appLanguage))
                                    batch.expirationDateMillis?.toStockDateInputText()?.takeIf { it.isNotBlank() }?.let {
                                        add("${localizedStringResource(234, "Expires")}: $it")
                                    }
                                    batch.shelfPosition?.takeIf { it.isNotBlank() }?.let { add(it) }
                                }.joinToString(" • ")
                                actionButton(
                                    text = batchInfo.take(40).ifBlank { batch.id.take(10) },
                                    enabledColor = if (isSelected) stateValues.AccentColor else stateValues.BackgroundColor,
                                    textColor = if (isSelected) stateValues.AccentTextColor else stateValues.TextColor,
                                    iconPath = if (isSelected) stateValues.drawablePathIconCheck else stateValues.drawablePathIconStock,
                                    iconRes = if (isSelected) stateValues.drawableResIconCheck.value else stateValues.drawableResIconStock.value,
                                    iconTintColor = if (isSelected) stateValues.AccentTextColor else stateValues.TextColor,
                                    fillMaxWidthIfTextPresent = false,
                                    confirmationRequired = false,
                                    onClick = { selectedBatchId = batch.id }
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                ) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = localizedStringResource(1291, "Label copies"),
                        color = stateValues.TextColor,
                        fontSize = stateValues.accentTextSize,
                        fontWeight = FontWeight.Bold
                    )
                    actionButton(
                        text = "−",
                        enabled = copies > 1,
                        fillMaxWidthIfTextPresent = false,
                        confirmationRequired = false,
                        onClick = { copies = (copies - 1).coerceAtLeast(1) }
                    )
                    Text(
                        text = copies.toString(),
                        color = stateValues.TextColor,
                        fontSize = stateValues.titleTextSize,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(min = 36.dp)
                    )
                    actionButton(
                        text = "+",
                        enabled = copies < 99,
                        fillMaxWidthIfTextPresent = false,
                        confirmationRequired = false,
                        onClick = { copies = (copies + 1).coerceAtMost(99) }
                    )
                }
            }

            item {
                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(1288, "Print item label"),
                    iconPath = stateValues.drawablePathIconPrintTag,
                    iconRes = stateValues.drawableResIconPrintTag.value,
                    enabled = selectedBarcode.isNotBlank(),
                    onDisabledClick = {
                        postInAppNotification(
                            localizedStringResource(1299, "This item has no barcode yet; add a barcode before printing a shelf label."),
                            NotificationType.Negative,
                            transient = true
                        )
                    },
                    confirmationRequired = false,
                    onClick = {
                        coroutineScope.launch {
                            receiptActionNotification(
                                printStockItemLabelDocument(
                                    label = previewLabel.copy(copies = copies.coerceIn(1, 99)),
                                    notConfiguredMessage = localizedStringResource(1269, "Paper document printing is not configured for this platform")
                                ),
                                localizedStringResource(1325, "Label opened for printing")
                            )
                        }
                    }
                )
            }
        }
    }
}


@Composable
internal fun AppConfiguration.QuickSupplierAddBottomSheet(
    onDismiss: () -> Unit,
    onSaved: (SupplierDataModel) -> Unit
) {
    var supplierName by rememberSaveable { mutableStateOf("") }
    var supplierEmail by rememberSaveable { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    val quickSupplierPhoneStateHost = remember { object : StateHost() {} }
    val coroutineScope = rememberCoroutineScope()

    AitaBottomSheet(
        title = stateValues.stringAddSupplier,
        iconPath = stateValues.drawablePathIconSuppliers,
        onDismiss = onDismiss
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(stateValues.marginTextField),
            contentPadding = PaddingValues(bottom = stateValues.screenHeight / 8)
        ) {
            item {
                MessageText(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(630, "Supplier data")
                )

                Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

                genericTextField(
                    titleText = stateValues.stringName,
                    placeholderText = localizedStringResource(622, "Enter supplier name"),
                    valueInitial = supplierName,
                    onValueChange = { value, apply ->
                        supplierName = value
                        apply()
                    }
                )

                Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

                val supplierPhoneTextFieldContent = countrySelectionPhoneNumberTextField(
                    titleText = localizedStringResource(620, "Supplier phone number"),
                    placeholderText = stateValues.stringEnterPhoneNumber,
                    stateHost = quickSupplierPhoneStateHost,
                    stateKey = "quick_supplier_phone_number"
                )

                Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

                genericTextField(
                    titleText = localizedStringResource(621, "Supplier email"),
                    placeholderText = stateValues.stringEnterEmailAddress,
                    valueInitial = supplierEmail,
                    keyboardType = KeyboardType.Email,
                    onValueChange = { value, apply ->
                        supplierEmail = value
                        apply()
                    }
                )

                Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(631, "Save supplier"),
                    iconPath = stateValues.drawablePathIconCheck,
                    enabled = supplierName.isNotBlank() && !isSaving,
                    confirmationRequired = false,
                    onClick = {
                        val phoneLocal = supplierPhoneTextFieldContent.value.text.trim()
                        val phoneNumber = if (phoneLocal.isBlank()) {
                            ""
                        } else {
                            supplierPhoneTextFieldContent.checkContentValidity()
                            if (!supplierPhoneTextFieldContent.isContentValid) {
                                postInAppNotification(stateValues.stringPhoneNumberMustBe, NotificationType.Negative, transient = true)
                                return@actionButton
                            }
                            supplierPhoneTextFieldContent.selectedSecondaryId.orEmpty().removePrefix("+") + phoneLocal
                        }

                        val cleanEmail = normalizeSupplierProfileEmail(supplierEmail)
                        if (cleanEmail != null && !supplierProfileEmailLooksValid(cleanEmail)) {
                            postInAppNotification(
                                localizedStringResource(2510, "Supplier email is invalid"),
                                NotificationType.Negative,
                                transient = true
                            )
                            return@actionButton
                        }
                        val supplier = SupplierDataModel(
                            id = "",
                            userIds = emptyList(),
                            typeIds = null,
                            categoryIds = emptyList(),
                            name = listOf(LocalizedStringDataModel("main", supplierName.trim())),
                            phoneNumbers = listOf(phoneNumber).filter { it.isNotBlank() },
                            emails = listOfNotNull(cleanEmail),
                            addedAt = getCurrentTimeMillis(),
                            isActive = true
                        ).normalizedSupplierProfileFields()

                        if (supplier.supplierProfileValidationIssues().isNotEmpty()) {
                            postInAppNotification(
                                localizedStringResource(2511, "Check the supplier profile fields"),
                                NotificationType.Negative,
                                transient = true
                            )
                            return@actionButton
                        }

                        isSaving = true
                        addSupplier(supplier) { result ->
                            coroutineScope.launch {
                                isSaving = false
                                if (result is DataState.Success) {
                                    onSaved(result.payload)
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}


@Composable
internal fun AppConfiguration.SupplierPickerBottomSheet(
    title: String,
    selectedSupplierId: String?,
    onDismiss: () -> Unit,
    onSupplierSelected: (SupplierDataModel) -> Unit
) {
    LaunchedEffect(Unit) { getSuppliers() }

    val suppliers = stateValues.suppliers.orEmpty().filter { it.isActive }
    val autoFocusSearch = platformAllowsAutomaticTextFieldFocus()
    var search by rememberSaveable { mutableStateOf("") }
    var addMode by rememberSaveable { mutableStateOf(false) }

    if (addMode) {
        QuickSupplierAddBottomSheet(
            onDismiss = { addMode = false },
            onSaved = { supplier ->
                addMode = false
                onSupplierSelected(supplier)
            }
        )
        return
    }

    AitaBottomSheet(
        title = title,
        iconPath = stateValues.drawablePathIconSuppliers,
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(stateValues.marginTextField)
        ) {
            val searchContent = searchTextField(
                valueInitial = search,
                stateHost = SupplierPickerAutoFocusStateHost,
                stateKey = null,
                modifier = Modifier.fillMaxWidth(),
                isFocusedInitial = autoFocusSearch,
                autoFocus = autoFocusSearch,
                forceRefocus = autoFocusSearch,
                updateIsFocusedAction = null
            )

            LaunchedEffect(searchContent.value.text) {
                search = searchContent.value.text
            }

            Spacer(modifier = Modifier.height(stateValues.marginTextField))

            actionButton(
                text = localizedStringResource(635, "Add supplier here"),
                iconPath = stateValues.drawablePathIconAdd,
                confirmationRequired = false,
                onClick = { addMode = true }
            )

            Spacer(modifier = Modifier.height(stateValues.marginTextField))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField),
                contentPadding = PaddingValues(bottom = stateValues.screenHeight / 6)
            ) {
                val q = search.trim()
                val visibleSuppliers = suppliers
                    .filter { supplier ->
                        q.isBlank() || listOf(
                            supplier.id,
                            supplier.name.visibleLocalizedString(stateValues.appLanguage, ""),
                            supplier.phoneNumbers.orEmpty().asDisplayPhoneNumbers().joinToString(" "),
                            supplier.emails.orEmpty().joinToString(" ")
                        ).any { it.contains(q, ignoreCase = true) }
                    }
                    .sortedBy { it.name.visibleLocalizedString(stateValues.appLanguage, it.id) }

                if (visibleSuppliers.isEmpty()) {
                    item {
                        MessageText(
                            modifier = Modifier.fillParentMaxSize().fillMaxWidth(),
                            text = if (q.isBlank()) localizedStringResource(626, "No suppliers yet") else stateValues.stringNoMatches
                        )
                    }
                } else {
                    items(visibleSuppliers, key = { it.id }) { supplier ->
                        SupplierCard(
                            supplier = supplier,
                            editable = false,
                            selected = supplier.id == selectedSupplierId,
                            onSelect = { onSupplierSelected(supplier) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun AppConfiguration.TransactionSupplySupplierBanner(
    transactionTypeIndex: Int,
    clientId: Int,
    selectedSupplierId: String?,
    onSelectSupplier: () -> Unit
) {
    if (transactionTypeIndex != 2) return

    val supplier = stateValues.suppliers.orEmpty().find { it.id == selectedSupplierId }
    val supplierText = supplier?.visibleSupplierName(stateValues.appLanguage)
        ?: localizedStringResource(638, "No supplier selected")

    Row(
        modifier = Modifier
            .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.72f)
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = selectedSupplierId != null)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(
                stateValues.focusedBorderWidth,
                if (selectedSupplierId != null) stateValues.AccentColor else stateValues.PlaceholderTextColor,
                RoundedCornerShape(stateValues.cornerRadius)
            )
            .aitaClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = stateValues.AccentColor),
                onClick = onSelectSupplier
            )
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        CpImage(
            modifier = Modifier.size(22.dp),
            url = stateValues.drawablePathIconSuppliers,
            fallbackRes = stateValues.drawableResIconSuppliers.value,
            contentDescription = localizedStringResource(639, "Supplier for supply"),
            tintColor = if (selectedSupplierId != null) stateValues.AccentColor else stateValues.TextColor
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = localizedStringResource(639, "Supplier for supply"),
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = supplierText,
                color = stateValues.TextColor,
                fontSize = stateValues.accentTextSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        actionButton(
            text = stateValues.stringSelect,
            iconPath = stateValues.drawablePathIconCheck,
            confirmationRequired = false,
            fillMaxWidthIfTextPresent = false,
            onClick = onSelectSupplier
        )
    }
}

@Composable
internal fun AppConfiguration.QuickStockAddBottomSheet(
    request: QuickStockAddSheetRequest,
    onDismiss: () -> Unit
) {
    val goodsInCart by getCartState(request.transactionTypeIndex, request.clientId).collectAsState()
    val defaultCurrency = stateValues.globalAppConfiguration
        .countries
        .withTajikistanFallback()
        .find { it.locale.equals(stateValues.userAccount?.countryLocale, ignoreCase = true) }
        ?.currencies
        ?.firstOrNull()
        ?.code
        ?: stateValues.globalAppConfiguration.countries.firstOrNull()?.currencies?.firstOrNull()?.code
        ?: "KZT"

    val defaultMeasurementUnitId = stateValues.globalAppConfiguration.goodsItemsQuantityUnits.firstOrNull()?.id ?: "0"

    fun newQuickDraft(): StockAddEditDraft = StockAddEditDraft(
        barcodes = listOf(request.barcode.takeIf { it.isNotBlank() }.orEmpty()),
        barcodeTypes = listOf(GOODS_ITEM_BARCODE_TYPE_STANDARD),
        name = emptyLocalizedItemForCurrentLanguage(),
        description = emptyLocalizedItemForCurrentLanguage(),
        measurementUnitId = defaultMeasurementUnitId,
        categoryIds = emptyList(),
        salePrices = listOf(PriceDataModel(price = "", currency = defaultCurrency, supplierId = "")),
        returnPrices = listOf(PriceDataModel(price = "", currency = defaultCurrency, supplierId = "")),
        supplyPrices = listOf(PriceDataModel(price = "", currency = defaultCurrency, supplierId = "")),
        wholesalePrices = listOf(PriceDataModel(price = "", currency = defaultCurrency, supplierId = "")),
        noteLocalized = emptyLocalizedItemForCurrentLanguage()
    )

    var draft by remember(request.barcode, request.transactionTypeIndex, request.clientId, defaultCurrency, defaultMeasurementUnitId) {
        mutableStateOf(newQuickDraft())
    }
    var selectedTabId by rememberSaveable(request.barcode, request.transactionTypeIndex, request.clientId) { mutableStateOf("info") }
    var returnPriceManuallyEdited by rememberSaveable(request.barcode, request.transactionTypeIndex, request.clientId) { mutableStateOf(false) }

    val tabs = listOf(
        StockAddEditTabContent(
            id = "info",
            title = localizedStringResource(252, "Info"),
            iconPath = stateValues.drawablePathIconEdit
        ),
        StockAddEditTabContent(
            id = "conditions",
            title = localizedStringResource(609, "Conditions"),
            iconPath = stateValues.drawablePathIconCheck,
            count = draft.conditions.size
        ),
        StockAddEditTabContent(
            id = "prices",
            title = localizedStringResource(253, "Generic prices"),
            iconPath = stateValues.drawablePathIconFinances
        )
    )

    AitaBottomSheet(
        title = localizedStringResource(636, "Quick add item"),
        iconPath = stateValues.drawablePathIconAdd,
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StockAddEditTabs(
                modifier = Modifier.fillMaxWidth(),
                selectedId = selectedTabId,
                tabs = tabs,
                onSelected = { selectedTabId = it }
            )

            val centeredFormModifier = Modifier
                .fillMaxWidth()
                .weight(1f)

            when (selectedTabId) {
                "conditions" -> StockAddEditConditionsTab(
                    modifier = centeredFormModifier,
                    draft = draft,
                    contentFillFraction = 1f,
                    onDraftChanged = { draft = it }
                )

                "prices" -> StockAddEditPricesTab(
                    modifier = centeredFormModifier,
                    draft = draft,
                    defaultCurrency = defaultCurrency,
                    returnPriceManuallyEdited = returnPriceManuallyEdited,
                    onReturnPriceManuallyEditedChanged = { returnPriceManuallyEdited = it },
                    contentFillFraction = 1f,
                    onDraftChanged = { draft = it }
                )

                else -> StockAddEditInfoTab(
                    modifier = centeredFormModifier,
                    draft = draft,
                    contentFillFraction = 1f,
                    onDraftChanged = { draft = it }
                )
            }

            actionButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                text = localizedStringResource(637, "Save item and add to cart"),
                iconPath = stateValues.drawablePathIconCheck,
                enabled = draft.isValidStockDraft(stateValues.globalAppConfiguration) && stateValues.activeStoreId != null && stateValues.latestNotification == null,
                confirmationRequired = false,
                onClick = {
                    val storeId = stateValues.activeStoreId ?: return@actionButton
                    val goodsItem = draft.toGoodsItem(storeId, stateValues.globalAppConfiguration, null)
                    addGoodsItem(goodsItem) { result ->
                        if (result is DataState.Success) {
                            addGoodsItemToTransactionCart(
                                goodsItem = result.payload,
                                transactionTypeIndex = request.transactionTypeIndex,
                                clientId = request.clientId,
                                configuration = stateValues.globalAppConfiguration,
                                currentCart = goodsInCart
                            )
                            closeQuickStockAddSheet()
                        }
                    }
                }
            )
        }
    }
}


@Composable
internal fun AppConfiguration.StockConditionMinuteField(
    modifier: Modifier = Modifier,
    title: String,
    value: Int,
    onChanged: (Int) -> Unit
) {
    var text by remember(value) {
        mutableStateOf(formatStockConditionMinute(value))
    }

    genericTextField(
        modifier = modifier,
        titleText = title,
        valueInitial = text,
        placeholderText = "HH:MM",
        leadingIconPath = stateValues.drawablePathIconWorkers,
        keyboardType = KeyboardType.Number,
        showClearButton = false,
        onTransformValue = { raw -> raw.filter { it.isDigit() || it == ':' }.take(5) },
        onValueChange = { nextText, applyChange ->
            text = nextText
            parseStockConditionMinute(nextText)?.let(onChanged)
            applyChange()
        }
    )
}

@Composable
internal fun AppConfiguration.StockConditionCard(
    index: Int,
    condition: StockConditionDataModel,
    onChanged: (StockConditionDataModel) -> Unit,
    onDelete: () -> Unit
) {
    val normalized = condition.normalizedStockCondition()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor, RoundedCornerShape(stateValues.cornerRadius))
            .padding(stateValues.marginTextFieldGroup)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${localizedStringResource(609, "Conditions")} ${index + 1}",
                    color = stateValues.TextColor,
                    fontSize = stateValues.accentTextSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = visibleStockConditionText(normalized),
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            CpImage(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(stateValues.iconSize)
                    .clip(RoundedCornerShape(stateValues.cornerRadius))
                    .aitaClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = stateValues.ErrorColor),
                        onClick = onDelete
                    )
                    .padding(2.dp),
                url = stateValues.drawablePathIconDelete,
                fallbackRes = Res.drawable._9_0,
                contentDescription = stateValues.stringDelete,
                tintColor = stateValues.ErrorColor
            )
        }

        Spacer(modifier = Modifier.height(stateValues.marginTextField))

        SimpleDropdownField(
            title = localizedStringResource(1025, "Condition type"),
            selectedId = normalized.kind,
            options = listOf(
                DropdownOption(STOCK_CONDITION_KIND_CUSTOM_TEXT, localizedStringResource(1011, "Manual condition"), localizedStringResource(1026, "Cashier confirms this condition")),
                DropdownOption(STOCK_CONDITION_KIND_BUYER_MINIMUM_AGE, localizedStringResource(1012, "Buyer age"), localizedStringResource(1027, "Cashier confirms buyer age")),
                DropdownOption(STOCK_CONDITION_KIND_TRANSACTION_TIME_WINDOW, localizedStringResource(1013, "Transaction time"), localizedStringResource(1028, "Automatically checks current time")),
                DropdownOption(STOCK_CONDITION_KIND_MARGIN_LIMIT, localizedStringResource(1499, "Margin limit"), localizedStringResource(1514, "Contracts protect age/time/margin rules before goods start moving."))
            ),
            placeholder = localizedStringResource(1025, "Condition type"),
            onSelected = { selectedKind ->
                val next = when (selectedKind) {
                    STOCK_CONDITION_KIND_BUYER_MINIMUM_AGE -> normalized.copy(kind = STOCK_CONDITION_KIND_BUYER_MINIMUM_AGE, minimumAge = normalized.minimumAge.coerceAtLeast(18))
                    STOCK_CONDITION_KIND_TRANSACTION_TIME_WINDOW -> normalized.copy(kind = STOCK_CONDITION_KIND_TRANSACTION_TIME_WINDOW)
                    STOCK_CONDITION_KIND_MARGIN_LIMIT -> normalized.copy(kind = STOCK_CONDITION_KIND_MARGIN_LIMIT, marginLimitPercent = normalized.marginLimitPercent.cleanStockConditionPercent())
                    else -> normalized.copy(
                        kind = STOCK_CONDITION_KIND_CUSTOM_TEXT,
                        text = normalized.text.ifEmpty { listOf(LocalizedStringDataModel("main", "")) }
                    )
                }
                onChanged(next.normalizedStockCondition())
            }
        )

        Spacer(modifier = Modifier.height(stateValues.marginTextField))

        SimpleDropdownField(
            title = localizedStringResource(1014, "Applies to"),
            selectedId = normalized.transactionTypeIndex.toString(),
            options = listOf(
                DropdownOption("0", stateValues.stringSale),
                DropdownOption("1", stateValues.stringReturn),
                DropdownOption("2", stateValues.stringSupply)
            ),
            placeholder = localizedStringResource(1014, "Applies to"),
            onSelected = { selected ->
                onChanged(normalized.copy(transactionTypeIndex = selected.toIntOrNull() ?: 0).normalizedStockCondition())
            }
        )

        Spacer(modifier = Modifier.height(stateValues.marginTextField))

        when (normalized.kind) {
            STOCK_CONDITION_KIND_BUYER_MINIMUM_AGE -> {
                genericTextField(
                    modifier = Modifier.fillMaxWidth(),
                    titleText = localizedStringResource(1015, "Minimum buyer age"),
                    valueInitial = normalized.minimumAge.toString(),
                    placeholderText = "18",
                    leadingIconPath = stateValues.drawablePathIconUserAccount,
                    keyboardType = KeyboardType.Number,
                    showClearButton = false,
                    onTransformValue = { raw -> raw.filter { it.isDigit() }.take(3) },
                    onValueChange = { value, applyChange ->
                        value.toIntOrNull()?.let { age ->
                            onChanged(normalized.copy(minimumAge = age.coerceIn(1, 130)).normalizedStockCondition())
                        }
                        applyChange()
                    }
                )

                Text(
                    modifier = Modifier.padding(top = 4.dp),
                    text = localizedStringResource(1029, "The cashier must confirm this before payment."),
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize
                )
            }

            STOCK_CONDITION_KIND_TRANSACTION_TIME_WINDOW -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                ) {
                    StockConditionMinuteField(
                        modifier = Modifier.weight(1f),
                        title = localizedStringResource(1016, "From"),
                        value = normalized.startsAtMinutes,
                        onChanged = { minute -> onChanged(normalized.copy(startsAtMinutes = minute).normalizedStockCondition()) }
                    )

                    StockConditionMinuteField(
                        modifier = Modifier.weight(1f),
                        title = localizedStringResource(1017, "To"),
                        value = normalized.endsAtMinutes,
                        onChanged = { minute -> onChanged(normalized.copy(endsAtMinutes = minute).normalizedStockCondition()) }
                    )
                }

                stockConditionStatusText(normalized)?.let { status ->
                    Text(
                        modifier = Modifier.padding(top = 4.dp),
                        text = status,
                        color = if (isMinuteInsideStockConditionWindow(currentStockConditionLocalMinuteOfDay(), normalized.startsAtMinutes, normalized.endsAtMinutes)) stateValues.PlaceholderTextColor else stateValues.ErrorColor,
                        fontSize = stateValues.smallTextSize,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            STOCK_CONDITION_KIND_MARGIN_LIMIT -> {
                genericTextField(
                    modifier = Modifier.fillMaxWidth(),
                    titleText = localizedStringResource(1501, "Maximum added margin, %"),
                    valueInitial = normalized.marginLimitPercent,
                    placeholderText = "30",
                    leadingIconPath = stateValues.drawablePathIconFinances,
                    keyboardType = KeyboardType.Decimal,
                    showClearButton = false,
                    onTransformValue = { raw -> raw.cleanStockConditionPercent() },
                    onValueChange = { value, applyChange ->
                        onChanged(normalized.copy(marginLimitPercent = value.cleanStockConditionPercent()).normalizedStockCondition())
                        applyChange()
                    }
                )

                Text(
                    modifier = Modifier.padding(top = 4.dp),
                    text = localizedStringResource(1514, "Contracts protect age/time/margin rules before goods start moving."),
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize
                )
            }

            else -> {
                StockLocalizedStringGroupEditor(
                    title = localizedStringResource(1030, "Condition text"),
                    placeholder = localizedStringResource(611, "Enter condition"),
                    values = normalized.text.ifEmpty { listOf(LocalizedStringDataModel("main", "")) },
                    addText = stateValues.stringAddTranslation,
                    required = true,
                    singleLine = false,
                    adaptiveMultiline = true,
                    persistentKey = "stock-condition-$index-text",
                    onChanged = { text ->
                        onChanged(normalized.copy(text = text).normalizedStockCondition())
                    }
                )
            }
        }
    }
}

@Composable
internal fun AppConfiguration.StockConditionListEditor(
    title: String,
    values: List<String>,
    onChanged: (List<String>) -> Unit
) {
    var conditions by remember {
        mutableStateOf(values.map { it.toStockConditionDataModel().normalizedStockCondition() }.filter { visibleStockConditionText(it).isNotBlank() || it.kind != STOCK_CONDITION_KIND_CUSTOM_TEXT })
    }
    val darkConditionIcons = stateValues.appThemeId == 1L
    val manualConditionIconPath = if (darkConditionIcons) "svg/131_1.svg" else "svg/131_0.svg"
    val manualConditionIconRes = if (darkConditionIcons) Res.drawable._131_1 else Res.drawable._131_0
    val buyerAgeConditionIconPath = if (darkConditionIcons) "svg/132_1.svg" else "svg/132_0.svg"
    val buyerAgeConditionIconRes = if (darkConditionIcons) Res.drawable._132_1 else Res.drawable._132_0
    val timeConditionIconPath = if (darkConditionIcons) "svg/133_1.svg" else "svg/133_0.svg"
    val timeConditionIconRes = if (darkConditionIcons) Res.drawable._133_1 else Res.drawable._133_0
    val marginConditionIconPath = if (darkConditionIcons) "svg/134_1.svg" else "svg/134_0.svg"
    val marginConditionIconRes = if (darkConditionIcons) Res.drawable._134_1 else Res.drawable._134_0

    fun storedCustomConditionProjection(items: List<StockConditionDataModel>): List<String> {
        return items
            .map { it.normalizedStockCondition() }
            .filter { condition ->
                condition.kind != STOCK_CONDITION_KIND_CUSTOM_TEXT ||
                        condition.text.any { localized -> localized.value.isNotBlank() }
            }
            .map { it.toStoredStockCondition() }
    }

    LaunchedEffect(values) {
        val next = values.map { it.toStockConditionDataModel().normalizedStockCondition() }
        if (storedCustomConditionProjection(next) != storedCustomConditionProjection(conditions)) {
            conditions = next
        }
    }

    fun emit(next: List<StockConditionDataModel>) {
        val normalizedForUi = next
            .map { it.normalizedStockCondition() }
            .distinctBy { condition ->
                if (condition.kind == STOCK_CONDITION_KIND_CUSTOM_TEXT && condition.text.none { it.value.isNotBlank() }) {
                    "blank-custom-condition"
                } else {
                    condition.toStoredStockCondition()
                }
            }

        val normalizedForStorage = normalizedForUi
            .filter { condition ->
                condition.kind != STOCK_CONDITION_KIND_CUSTOM_TEXT ||
                        condition.text.any { it.value.isNotBlank() }
            }
            .distinctBy { it.toStoredStockCondition() }

        conditions = normalizedForUi
        onChanged(normalizedForStorage.map { it.toStoredStockCondition() })
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = stateValues.TextColor,
            fontSize = stateValues.accentTextSize,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Text(
            text = localizedStringResource(1031, "Use ready rules for buyer age and transaction time, or add your own condition text.") + " " + localizedStringResource(1514, "Contracts protect age/time/margin rules before goods start moving."),
            color = stateValues.PlaceholderTextColor,
            fontSize = stateValues.smallTextSize,
            modifier = Modifier.padding(bottom = stateValues.marginTextField)
        )

        conditions.forEachIndexed { index, condition ->
            key("stock_condition_${index}_${condition.kind}_${condition.transactionTypeIndex}") {
                StockConditionCard(
                    index = index,
                    condition = condition,
                    onChanged = { updated ->
                        emit(conditions.toMutableList().also { list ->
                            if (index in list.indices) list[index] = updated
                        })
                    },
                    onDelete = {
                        emit(conditions.toMutableList().also { list ->
                            if (index in list.indices) list.removeAt(index)
                        })
                    }
                )
            }

            Spacer(modifier = Modifier.height(stateValues.marginTextField))
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
            ) {
                actionButton(
                    modifier = Modifier.weight(1f),
                    text = localizedStringResource(1018, "Manual"),
                    iconPath = manualConditionIconPath,
                    iconRes = manualConditionIconRes,
                    iconTintColor = null,
                    textSize = stateValues.smallTextSize,
                    confirmationRequired = false,
                    onClick = { emit(conditions + defaultCustomStockCondition()) }
                )

                actionButton(
                    modifier = Modifier.weight(1f),
                    text = localizedStringResource(1012, "Buyer age"),
                    iconPath = buyerAgeConditionIconPath,
                    iconRes = buyerAgeConditionIconRes,
                    iconTintColor = null,
                    textSize = stateValues.smallTextSize,
                    confirmationRequired = false,
                    onClick = { emit(conditions + defaultBuyerMinimumAgeStockCondition()) }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
            ) {
                actionButton(
                    modifier = Modifier.weight(1f),
                    text = localizedStringResource(1013, "Time"),
                    iconPath = timeConditionIconPath,
                    iconRes = timeConditionIconRes,
                    iconTintColor = null,
                    textSize = stateValues.smallTextSize,
                    confirmationRequired = false,
                    onClick = { emit(conditions + defaultTransactionTimeWindowStockCondition()) }
                )

                actionButton(
                    modifier = Modifier.weight(1f),
                    text = localizedStringResource(1499, "Margin"),
                    iconPath = marginConditionIconPath,
                    iconRes = marginConditionIconRes,
                    iconTintColor = null,
                    textSize = stateValues.smallTextSize,
                    confirmationRequired = false,
                    onClick = { emit(conditions + defaultMarginLimitStockCondition()) }
                )
            }
        }
    }
}

@Composable
internal fun AppConfiguration.SimpleTextListEditor(
    title: String,
    values: List<String>,
    placeholder: String,
    addText: String,
    onChanged: (List<String>) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = stateValues.TextColor,
            fontSize = stateValues.accentTextSize,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        values.forEachIndexed { index, value ->
            key("simple_text_${title}_${index}") {
                genericTextField(
                    modifier = Modifier.fillMaxWidth(),
                    placeholderText = placeholder,
                    valueInitial = value,
                    onValueChange = { text, commit ->
                        val current = values.toMutableList()
                        if (index in current.indices) {
                            if (text.isBlank()) {
                                current.removeAt(index)
                            } else {
                                current[index] = text
                            }
                            onChanged(current)
                        }
                        commit()
                    }
                )
            }

            Spacer(modifier = Modifier.height(stateValues.marginTextField))
        }

        actionButton(
            text = addText,
            iconPath = stateValues.drawablePathIconAdd,
            confirmationRequired = false,
            onClick = { onChanged(values + "") }
        )
    }
}

fun List<GoodsBatchDataModel>.bestBatchForSale(
    goodsItem: GoodsItemDataModel
): GoodsBatchDataModel? {
    val active = firstOrNull {
        it.id == goodsItem.activeShelfBatchId &&
                it.isActive &&
                it.quantity.total > 0.0 &&
                it.status != StockBatchStatusDataModel.Ordered &&
                it.status != StockBatchStatusDataModel.Reserved &&
                it.status != StockBatchStatusDataModel.SoldOut &&
                it.status != StockBatchStatusDataModel.Deleted &&
                it.status != StockBatchStatusDataModel.WrittenOff &&
                it.status != StockBatchStatusDataModel.InTransit
    }

    if (active != null) return active

    return filter {
        it.goodsItemId == goodsItem.id &&
                it.isActive &&
                it.quantity.total > 0.0 &&
                it.status != StockBatchStatusDataModel.Ordered &&
                it.status != StockBatchStatusDataModel.Reserved &&
                it.status != StockBatchStatusDataModel.SoldOut &&
                it.status != StockBatchStatusDataModel.Deleted &&
                it.status != StockBatchStatusDataModel.WrittenOff &&
                it.status != StockBatchStatusDataModel.InTransit
    }
        .sortedWith(
            compareBy<GoodsBatchDataModel> {
                it.expirationDateMillis ?: Long.MAX_VALUE
            }.thenByDescending {
                it.shelfPriority
            }
        )
        .firstOrNull()
}

internal fun newClientSideUuidString(): String {
    val hex = "0123456789abcdef"
    fun hexChar(): Char = hex[Random.nextInt(hex.length)]

    return buildString(36) {
        repeat(8) { append(hexChar()) }
        append('-')
        repeat(4) { append(hexChar()) }
        append('-')
        append('4')
        repeat(3) { append(hexChar()) }
        append('-')
        append(listOf('8', '9', 'a', 'b')[Random.nextInt(4)])
        repeat(3) { append(hexChar()) }
        append('-')
        repeat(12) { append(hexChar()) }
    }
}

@kotlinx.serialization.Serializable
internal data class GoodsBatchDraft(
    val id: String = newClientSideUuidString(),
    val goodsItemId: String,
    val storeId: String,
    val supplierId: String? = null,
    val quantityText: String = "1",
    val quantityUnitId: String,
    val supplyPrice: PriceDataModel,
    val salePriceOverride: PriceDataModel? = null,
    val returnPriceOverride: PriceDataModel? = null,
    val wholesalePriceOverride: PriceDataModel? = null,
    val expirationDateText: String = "",
    val manufacturedDateText: String = "",
    val additionalNotes: String = "",
    val additionalNotesLocalized: List<LocalizedStringDataModel> = emptyList(),
    val status: StockBatchStatusDataModel = StockBatchStatusDataModel.Delivered,
    val promotions: List<StockPromotionDataModel> = emptyList()
)

internal const val GOODS_BATCH_DRAFT_SEPARATOR = "\u001F"

internal fun String.cleanForGoodsBatchDraftState(): String {
    return replace(GOODS_BATCH_DRAFT_SEPARATOR, " ")
}

internal fun GoodsBatchDraft.toNavigationStateString(): String {
    return listOf(
        id,
        goodsItemId,
        storeId,
        supplierId.orEmpty(),
        quantityText,
        quantityUnitId,
        supplyPrice.price,
        supplyPrice.currency,
        supplyPrice.supplierId,
        salePriceOverride?.price.orEmpty(),
        salePriceOverride?.currency.orEmpty(),
        salePriceOverride?.supplierId.orEmpty(),
        returnPriceOverride?.price.orEmpty(),
        returnPriceOverride?.currency.orEmpty(),
        returnPriceOverride?.supplierId.orEmpty(),
        wholesalePriceOverride?.price.orEmpty(),
        wholesalePriceOverride?.currency.orEmpty(),
        wholesalePriceOverride?.supplierId.orEmpty(),
        expirationDateText,
        manufacturedDateText,
        additionalNotes,
        status.name,
        jsonBase.encodeToString(ListSerializer(LocalizedStringDataModel.serializer()), additionalNotesLocalized)
    ).joinToString(GOODS_BATCH_DRAFT_SEPARATOR) { it.cleanForGoodsBatchDraftState() }
}

internal fun goodsBatchDraftFromNavigationStateString(raw: String): GoodsBatchDraft? {
    val values = raw.split(GOODS_BATCH_DRAFT_SEPARATOR)
    if (values.size < 19) return null

    val salePrice = values[9].takeIf { it.isNotBlank() }?.let {
        PriceDataModel(
            price = it,
            currency = values.getOrNull(10).orEmpty(),
            supplierId = values.getOrNull(11).orEmpty()
        )
    }

    val returnPrice = values[12].takeIf { it.isNotBlank() }?.let {
        PriceDataModel(
            price = it,
            currency = values.getOrNull(13).orEmpty(),
            supplierId = values.getOrNull(14).orEmpty()
        )
    }

    val hasWholesaleFields = values.size >= 22
    val wholesalePrice = if (hasWholesaleFields) {
        values[15].takeIf { it.isNotBlank() }?.let {
            PriceDataModel(
                price = it,
                currency = values.getOrNull(16).orEmpty(),
                supplierId = values.getOrNull(17).orEmpty()
            )
        }
    } else null

    val expirationIndex = if (hasWholesaleFields) 18 else 15
    val manufacturedIndex = if (hasWholesaleFields) 19 else 16
    val notesIndex = if (hasWholesaleFields) 20 else 17
    val statusIndex = if (hasWholesaleFields) 21 else 18
    val localizedNotesIndex = if (hasWholesaleFields) 22 else 19
    val localizedNotes = values.getOrNull(localizedNotesIndex)
        ?.let { raw -> runCatching { jsonBase.decodeFromString(ListSerializer(LocalizedStringDataModel.serializer()), raw) }.getOrNull() }
        .orEmpty()

    return runCatching {
        GoodsBatchDraft(
            id = values[0].ifBlank { newClientSideUuidString() },
            goodsItemId = values[1],
            storeId = values[2],
            supplierId = values[3].takeIf { it.isNotBlank() },
            quantityText = values[4].ifBlank { "1" },
            quantityUnitId = values[5],
            supplyPrice = PriceDataModel(
                price = values[6].ifBlank { "0" },
                currency = values[7].ifBlank { "KZT" },
                supplierId = values[8]
            ),
            salePriceOverride = salePrice,
            returnPriceOverride = returnPrice,
            wholesalePriceOverride = wholesalePrice,
            expirationDateText = values.getOrNull(expirationIndex).orEmpty(),
            manufacturedDateText = values.getOrNull(manufacturedIndex).orEmpty(),
            additionalNotes = values.getOrNull(notesIndex).orEmpty(),
            additionalNotesLocalized = localizedNotes.ifEmpty {
                values.getOrNull(notesIndex).orEmpty().takeIf { it.isNotBlank() }?.let { listOf(LocalizedStringDataModel("main", it)) } ?: emptyList()
            },
            status = StockBatchStatusDataModel.valueOf(values.getOrNull(statusIndex).orEmpty().ifBlank { StockBatchStatusDataModel.Delivered.name })
        )
    }.getOrNull()
}

internal fun Long?.toStockDateInputText(): String {
    return this?.let { millis ->
        runCatching {
            val localDate = Instant
                .fromEpochMilliseconds(millis)
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .date

            "${localDate.year.toString().padStart(4, '0')}-${localDate.monthNumber.toString().padStart(2, '0')}-${localDate.dayOfMonth.toString().padStart(2, '0')}"
        }.getOrDefault("")
    }.orEmpty()
}

internal fun stockDateInputTextToMillis(value: String): Long? {
    val clean = value.trim()
    if (clean.isBlank()) return null

    val match = Regex("""^(\d{4})-(\d{2})-(\d{2})$""").matchEntire(clean) ?: return null
    val year = match.groupValues[1].toIntOrNull() ?: return null
    val month = match.groupValues[2].toIntOrNull() ?: return null
    val day = match.groupValues[3].toIntOrNull() ?: return null

    return runCatching {
        LocalDate(year, month, day)
            .atStartOfDayIn(TimeZone.currentSystemDefault())
            .toEpochMilliseconds()
    }.getOrNull()
}

internal fun String.filterStockDateInput(): String {
    return filter { it.isDigit() || it == '-' }
        .take(10)
}

internal fun isStockLeapYear(year: Int): Boolean {
    return year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
}

internal fun stockDaysInMonth(year: Int, month: Int): Int {
    return when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if (isStockLeapYear(year)) 29 else 28
        else -> 31
    }
}

internal fun LocalDate.toStockDateInputText(): String {
    return "${year.toString().padStart(4, '0')}-${monthNumber.toString().padStart(2, '0')}-${dayOfMonth.toString().padStart(2, '0')}"
}

internal fun currentStockLocalDate(): LocalDate {
    return Instant
        .fromEpochMilliseconds(getCurrentTimeMillis())
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
}

internal fun stockDateInputTextToLocalDate(value: String): LocalDate? {
    val clean = value.trim()
    if (clean.isBlank()) return null

    val match = Regex("""^(\d{4})-(\d{2})-(\d{2})$""").matchEntire(clean) ?: return null
    val year = match.groupValues[1].toIntOrNull() ?: return null
    val month = match.groupValues[2].toIntOrNull() ?: return null
    val day = match.groupValues[3].toIntOrNull() ?: return null

    if (year !in 1970..2500 || month !in 1..12 || day !in 1..stockDaysInMonth(year, month))
        return null

    return LocalDate(year, month, day)
}

internal fun stockDateFromParts(yearText: String, monthText: String, dayText: String): String {
    val year = yearText.toIntOrNull() ?: return ""
    val month = monthText.toIntOrNull() ?: return ""
    val day = dayText.toIntOrNull() ?: return ""

    if (year !in 1970..2500 || month !in 1..12 || day !in 1..stockDaysInMonth(year, month))
        return ""

    return LocalDate(year, month, day).toStockDateInputText()
}

internal fun sanitizeStockYear(value: String): String {
    return value.filter { it.isDigit() }.take(4)
}

internal fun sanitizeStockMonth(value: String): String {
    val digits = value.filter { it.isDigit() }.take(2)
    if (digits.isBlank()) return ""
    val number = digits.toIntOrNull() ?: return ""
    return number.coerceIn(1, 12).toString().padStart(if (digits.length >= 2) 2 else digits.length, '0')
}

internal fun sanitizeStockDay(value: String, yearText: String, monthText: String): String {
    val digits = value.filter { it.isDigit() }.take(2)
    if (digits.isBlank()) return ""
    val number = digits.toIntOrNull() ?: return ""
    val year = yearText.toIntOrNull() ?: 2024
    val month = monthText.toIntOrNull()?.coerceIn(1, 12) ?: 1
    return number.coerceIn(1, stockDaysInMonth(year, month)).toString().padStart(if (digits.length >= 2) 2 else digits.length, '0')
}

internal fun addStockExpirationPeriod(date: LocalDate, period: ExpirationPeriodDataModel): LocalDate {
    val amount = period.amount.coerceAtLeast(0)

    return when (period.unit) {
        "days" -> Instant
            .fromEpochMilliseconds(date.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds() + amount * 24L * 60L * 60L * 1000L)
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date

        "weeks" -> Instant
            .fromEpochMilliseconds(date.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds() + amount * 7L * 24L * 60L * 60L * 1000L)
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date

        "months" -> {
            val monthIndex = (date.year * 12 + (date.monthNumber - 1)) + amount
            val year = monthIndex / 12
            val month = monthIndex % 12 + 1
            LocalDate(year, month, date.dayOfMonth.coerceAtMost(stockDaysInMonth(year, month)))
        }

        "years" -> {
            val year = date.year + amount
            LocalDate(year, date.monthNumber, date.dayOfMonth.coerceAtMost(stockDaysInMonth(year, date.monthNumber)))
        }

        else -> date
    }
}

internal fun AppConfiguration.stockExpirationPeriodUnitDomains(): List<SelectableDomain> {
    return listOf(
        SelectableDomain("days", "Days".toLocalizedSingleMain(), "Days".toLocalizedSingleMain(), null, null),
        SelectableDomain("weeks", "Weeks".toLocalizedSingleMain(), "Weeks".toLocalizedSingleMain(), null, null),
        SelectableDomain("months", "Months".toLocalizedSingleMain(), "Months".toLocalizedSingleMain(), null, null),
        SelectableDomain("years", "Years".toLocalizedSingleMain(), "Years".toLocalizedSingleMain(), null, null)
    )
}

@Composable
internal fun AppConfiguration.StockDatePartsEditor(
    title: String,
    dateText: String,
    onDateChanged: (String) -> Unit
) {
    var calendarOpen by rememberSaveable(title, dateText) { mutableStateOf(false) }

    if (calendarOpen) {
        TransactionHistoryCalendarDialog(
            title = title,
            selectedDateText = dateText,
            onDismiss = { calendarOpen = false },
            onDateSelected = onDateChanged
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField),
        verticalAlignment = Alignment.Bottom
    ) {
        TransactionHistoryDateButton(
            modifier = Modifier.weight(1f),
            title = title,
            dateText = dateText,
            onClick = { calendarOpen = true }
        )

        if (dateText.isNotBlank()) {
            actionButton(
                modifier = Modifier.widthIn(min = stateValues.textFieldHeight),
                text = "",
                iconPath = stateValues.drawablePathIconCancel,
                iconContentDescription = stateValues.stringClear,
                enabledColor = stateValues.DisabledColor,
                fillMaxWidthIfTextPresent = false,
                confirmationRequired = false,
                onClick = { onDateChanged("") }
            )
        }
    }
}

@Composable
internal fun AppConfiguration.StockDateRangeEditor(
    title: String,
    startTitle: String,
    endTitle: String,
    startDateText: String,
    endDateText: String,
    onStartDateChanged: (String) -> Unit,
    onEndDateChanged: (String) -> Unit
) {
    var calendarTarget by rememberSaveable(title, startDateText, endDateText) { mutableStateOf<String?>(null) }

    calendarTarget?.let { target ->
        TransactionHistoryCalendarDialog(
            title = if (target == "start") startTitle else endTitle,
            selectedDateText = if (target == "start") startDateText else endDateText,
            onDismiss = { calendarTarget = null },
            onDateSelected = { selectedDate ->
                if (target == "start") {
                    onStartDateChanged(selectedDate)
                    val startMillis = stockDateInputTextToMillis(selectedDate)
                    val endMillis = stockDateInputTextToMillis(endDateText)
                    if (startMillis != null && endMillis != null && startMillis > endMillis) {
                        onEndDateChanged(selectedDate)
                    }
                } else {
                    onEndDateChanged(selectedDate)
                    val startMillis = stockDateInputTextToMillis(startDateText)
                    val endMillis = stockDateInputTextToMillis(selectedDate)
                    if (startMillis != null && endMillis != null && endMillis < startMillis) {
                        onStartDateChanged(selectedDate)
                    }
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = stateValues.TextColor,
            fontSize = stateValues.textSize,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField),
            verticalAlignment = Alignment.Top
        ) {
            TransactionHistoryDateButton(
                modifier = Modifier.weight(1f),
                title = startTitle,
                dateText = startDateText,
                onClick = { calendarTarget = "start" }
            )

            TransactionHistoryDateButton(
                modifier = Modifier.weight(1f),
                title = endTitle,
                dateText = endDateText,
                onClick = { calendarTarget = "end" }
            )
        }

        if (startDateText.isNotBlank() || endDateText.isNotBlank()) {
            Spacer(modifier = Modifier.height(stateValues.marginTextField))
            actionButton(
                text = localizedStringResource(950, "Clear period"),
                iconPath = stateValues.drawablePathIconCancel,
                enabledColor = stateValues.DisabledColor,
                confirmationRequired = false
            ) {
                onStartDateChanged("")
                onEndDateChanged("")
            }
        }
    }
}

@Composable
internal fun AppConfiguration.StockExpirationPeriodEditor(
    period: ExpirationPeriodDataModel?,
    onChanged: (ExpirationPeriodDataModel?) -> Unit
) {
    val amount = period?.amount?.takeIf { it > 0 }?.toString().orEmpty()
    val unit = period?.unit ?: "days"

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = localizedStringResource(332, "Generic expiration period"),
            color = stateValues.TextColor,
            fontSize = stateValues.textSize,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField),
            verticalAlignment = Alignment.Top
        ) {
            SimpleTextInput(
                modifier = Modifier.weight(1f),
                value = amount,
                placeholder = "0",
                keyboardType = KeyboardType.Number,
                leadingIconPath = stateValues.drawablePathIconStock,
                onTransformValue = { it.filter { char -> char.isDigit() }.take(4) },
                onValueChange = { raw ->
                    val number = raw.toIntOrNull() ?: 0
                    onChanged(number.takeIf { it > 0 }?.let { ExpirationPeriodDataModel(it, unit) })
                }
            )

            SimpleDropdownField(
                modifier = Modifier.weight(1.2f),
                title = "",
                selectedId = unit,
                options = stockExpirationPeriodUnitDomains().map {
                    DropdownOption(
                        id = it.id,
                        title = it.displayId.visibleLocalizedString(stateValues.appLanguage, it.id)
                    )
                },
                placeholder = localizedStringResource(270, "Unit"),
                onSelected = { selectedUnit ->
                    val number = amount.toIntOrNull() ?: 0
                    onChanged(number.takeIf { it > 0 }?.let { ExpirationPeriodDataModel(it, selectedUnit) })
                }
            )
        }
    }
}

internal fun GoodsBatchDataModel.toDraft(
    fallbackUnitId: String
): GoodsBatchDraft {
    return GoodsBatchDraft(
        id = id,
        goodsItemId = goodsItemId,
        storeId = storeId,
        supplierId = supplierId,
        quantityText = stockQuantityInputTextFromAmount(quantity.total, quantity),
        quantityUnitId = quantity.id.ifBlank { fallbackUnitId },
        supplyPrice = supplyPrice,
        salePriceOverride = salePriceOverride,
        returnPriceOverride = returnPriceOverride,
        wholesalePriceOverride = wholesalePriceOverride,
        expirationDateText = expirationDateMillis.toStockDateInputText(),
        manufacturedDateText = manufacturedAtMillis.toStockDateInputText(),
        additionalNotes = additionalNotes.orEmpty(),
        additionalNotesLocalized = additionalNotesLocalized.ifEmpty {
            additionalNotes?.takeIf { it.isNotBlank() }?.let { listOf(LocalizedStringDataModel("main", it)) } ?: emptyList()
        },
        status = status,
        promotions = promotions
    )
}

internal fun List<GoodsBatchDataModel>.sortedForShelf(goodsItem: GoodsItemDataModel): List<GoodsBatchDataModel> {
    return filter {
        it.goodsItemId == goodsItem.id &&
                it.isActive &&
                it.status != StockBatchStatusDataModel.Deleted
    }.sortedWith(
        compareBy<GoodsBatchDataModel> {
            if (it.id == goodsItem.activeShelfBatchId) 0 else 1
        }.thenBy {
            it.shelfPriority
        }.thenBy {
            it.expirationDateMillis ?: Long.MAX_VALUE
        }
    )
}

internal fun AppConfiguration.reorderShelfBatches(
    goodsItem: GoodsItemDataModel,
    batches: List<GoodsBatchDataModel>,
    fromIndex: Int,
    toIndex: Int
) {
    val storeId = stateValues.activeStoreId ?: return
    val current = batches.sortedForShelf(goodsItem).toMutableList()

    if (fromIndex !in current.indices || toIndex !in current.indices || fromIndex == toIndex)
        return

    val moved = current.removeAt(fromIndex)
    current.add(toIndex, moved)

    val updated = current.mapIndexed { index, batch ->
        batch.copy(
            shelfPriority = index,
            shelfPosition = (index + 1).toString()
        )
    }

    updateGoodsBatches(updated) {
        if (it is DataState.Success) {
            updated.firstOrNull()?.let { firstBatch ->
                if (firstBatch.id != goodsItem.activeShelfBatchId) {
                    setActiveShelfBatch(
                        batch = firstBatch,
                        storeId = storeId,
                        previousActiveShelfBatchId = goodsItem.activeShelfBatchId
                    )
                }
            }
        }
    }
}

internal fun AppConfiguration.moveShelfBatch(
    goodsItem: GoodsItemDataModel,
    batches: List<GoodsBatchDataModel>,
    batch: GoodsBatchDataModel,
    direction: Int
) {
    val sorted = batches.sortedForShelf(goodsItem)
    val from = sorted.indexOfFirst { it.id == batch.id }
    val to = (from + direction).coerceIn(0, sorted.lastIndex)

    reorderShelfBatches(
        goodsItem = goodsItem,
        batches = sorted,
        fromIndex = from,
        toIndex = to
    )
}

// THIS IS CommonMainCompose.kt split slice: SupplierA
@file:OptIn(ExperimentalTime::class, ExperimentalFoundationApi::class)
package kz.aita

import aita.composeapp.generated.resources.*
import androidx.compose.animation.*
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import io.kamel.core.config.*
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import io.kamel.image.config.LocalKamelConfig
import io.kamel.image.config.imageBitmapDecoder
import io.kamel.image.config.svgDecoder
import io.ktor.client.plugins.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.*
import kz.aita.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.text.equals
import kotlin.time.ExperimentalTime

internal fun AppConfiguration.buildSupplierCatalogItems(
    orders: List<SupplierOrderDataModel>,
    lines: List<SupplierOrderLineDataModel>,
    supplierPrices: List<SupplierGoodsPriceDataModel>
): List<SupplierCatalogItemUiModel> {
    val activeOrders = orders.filter { it.isActive }
    val ordersById = activeOrders.associateBy { it.id }
    val activeLines = lines.filter { line -> line.isActive && ordersById[line.orderId] != null }
    val activePrices = supplierPrices.filter { price -> price.isActive && price.goodsItemId.isNotBlank() }
    val pricesByGoodsItem = activePrices.groupBy { it.goodsItemId }
    val replyStatuses = setOf(
        SupplierOrderStatusDataModel.Sent,
        SupplierOrderStatusDataModel.SeenBySupplier,
        SupplierOrderStatusDataModel.IssueReported
    )

    val orderGoodsKeys = activeLines
        .map { line ->
            line.goodsItemId
                .ifBlank { line.goodsItemBarcodeSnapshots.firstOrNull().orEmpty() }
                .ifBlank { supplierDeskLineTitle(line) }
                .ifBlank { line.id }
        }
    val priceBookGoodsKeys = activePrices.map { it.goodsItemId }

    return (orderGoodsKeys + priceBookGoodsKeys)
        .filter { it.isNotBlank() }
        .distinct()
        .map { goodsKey ->
            val itemLines = activeLines.filter { line ->
                line.goodsItemId == goodsKey ||
                        line.substituteGoodsItemId == goodsKey ||
                        (line.goodsItemId.isBlank() && line.goodsItemBarcodeSnapshots.contains(goodsKey)) ||
                        supplierDeskLineTitle(line) == goodsKey
            }
            val itemPrices = pricesByGoodsItem[goodsKey].orEmpty()
            val relatedOrders = itemLines
                .mapNotNull { ordersById[it.orderId] }
                .distinctBy { it.id }
                .sortedByDescending { it.supplierDeskSortTime() }
            val latestOrder = relatedOrders.firstOrNull()
            val latestLine = itemLines.maxByOrNull { line -> ordersById[line.orderId]?.supplierDeskSortTime() ?: 0L }
            val latestPrice = itemPrices.maxByOrNull { price -> price.lastUsedAtMillis ?: price.updatedAtMillis }
            val editableGoodsItemId = latestPrice?.goodsItemId?.takeIf { it.isNotBlank() }
                ?: goodsKey.takeIf { key -> itemLines.any { line -> line.goodsItemId == key || line.substituteGoodsItemId == key } }
                ?: latestLine?.goodsItemId?.takeIf { it.isNotBlank() }
                ?: goodsKey
            val defaultOrderForPrice = relatedOrders.firstOrNull { order ->
                itemLines.any { line ->
                    line.orderId == order.id &&
                            (line.goodsItemId == editableGoodsItemId || line.substituteGoodsItemId == editableGoodsItemId)
                }
            } ?: latestOrder
            val defaultStoreId = latestPrice?.storeId?.takeIf { it.isNotBlank() }
                ?: defaultOrderForPrice?.storeId.orEmpty()
            val defaultSupplierId = latestPrice?.supplierId?.takeIf { it.isNotBlank() }
                ?: defaultOrderForPrice?.supplierId.orEmpty()
            val defaultQuantityTemplate = latestLine?.requestedQuantity
                ?: latestPrice?.minOrderQuantity
                ?: latestPrice?.packageQuantity
                ?: stateValues.globalAppConfiguration.goodsItemsQuantityUnits.firstOrNull()
                ?: QuantityDataModel(
                    id = "0",
                    immutableUnitName = listOf(LocalizedStringDataModel("main", "unit")),
                    total = 1.0,
                    pricedAmount = 1.0,
                    roundTotal = false
                )
            val title = itemLines
                .asSequence()
                .map { supplierDeskLineTitle(it) }
                .firstOrNull { it.isNotBlank() }
                ?: latestPrice?.supplierGoodsName?.takeIf { it.isNotBlank() }
                ?: goodsKey.take(12)
            val barcodeText = itemLines
                .asSequence()
                .flatMap { it.goodsItemBarcodeSnapshots.asSequence() }
                .firstOrNull { it.isNotBlank() }
                ?: latestPrice?.supplierBarcode?.takeIf { it.isNotBlank() }
                ?: ""
            val storeTitles = relatedOrders
                .map { supplierDeskStoreTitle(it) }
                .filter { it.isNotBlank() }
                .distinct()
            val priceBookStoreIds = itemPrices.map { it.storeId }.filter { it.isNotBlank() }.distinct()
            val totalQuantityText = supplierCatalogQuantityText(itemLines).ifBlank { localizedStringResource(1681, "No open quantity yet") }
            val savedPriceText = latestPrice?.supplyPrice.supplierDeskMoneyText()
            val expectedPriceText = (latestLine?.supplierOfferedSupplyPrice
                ?: latestPrice?.supplyPrice
                ?: latestLine?.expectedSupplyPrice
                ?: latestOrder?.amount).supplierDeskMoneyText()
            val latestStatus = latestOrder?.status ?: SupplierOrderStatusDataModel.Draft
            val priceInputText = (latestPrice?.supplyPrice
                ?: latestLine?.supplierOfferedSupplyPrice
                ?: latestLine?.expectedSupplyPrice
                ?: latestOrder?.amount).supplierDeskPriceInputText()
            val minOrderInputText = latestPrice?.minOrderQuantity?.let { stockQuantityInputTextFromAmount(it.total, it) }.orEmpty()
            val packageInputText = latestPrice?.packageQuantity?.let { stockQuantityInputTextFromAmount(it.total, it) }.orEmpty()
            val currency = latestPrice?.supplyPrice?.currency?.takeIf { it.isNotBlank() }
                ?: latestLine?.supplierOfferedSupplyPrice?.currency?.takeIf { it.isNotBlank() }
                ?: latestLine?.expectedSupplyPrice?.currency?.takeIf { it.isNotBlank() }
                ?: latestOrder?.amount?.currency?.takeIf { it.isNotBlank() }
                ?: "KZT"
            val supplierGoodsName = latestPrice?.supplierGoodsName?.takeIf { it.isNotBlank() } ?: title
            val supplierBarcode = latestPrice?.supplierBarcode?.takeIf { it.isNotBlank() } ?: barcodeText
            val canEditPriceBook = editableGoodsItemId.isNotBlank() && defaultStoreId.isNotBlank() && defaultSupplierId.isNotBlank()
            val openOrderCount = relatedOrders.count { !it.status.isSupplierOrderClosed() }
            val lastOrderActivityMillis = latestOrder?.supplierDeskSortTime() ?: 0L
            val lastPriceActivityMillis = latestPrice?.let { it.lastUsedAtMillis ?: it.updatedAtMillis } ?: 0L
            val lastActivityMillis = maxOf(lastOrderActivityMillis, lastPriceActivityMillis)
            val needsReply = relatedOrders.any { it.status in replyStatuses }
            val deliveredOnly = relatedOrders.any { order ->
                order.status == SupplierOrderStatusDataModel.Delivered || order.status == SupplierOrderStatusDataModel.PartiallyDelivered
            }
            val storeListText = storeTitles.take(4).joinToString(", ").ifBlank { localizedStringResource(1410, "Interested stores") }
            val searchKey = buildString {
                append(goodsKey).append(' ')
                append(title).append(' ')
                append(barcodeText).append(' ')
                append(savedPriceText).append(' ')
                append(priceInputText).append(' ')
                append(minOrderInputText).append(' ')
                append(packageInputText).append(' ')
                append(currency).append(' ')
                append(supplierGoodsName).append(' ')
                append(supplierBarcode).append(' ')
                append(storeTitles.joinToString(" ")).append(' ')
                append(priceBookStoreIds.joinToString(" ")).append(' ')
                append(relatedOrders.joinToString(" ") { it.id }).append(' ')
                append(itemPrices.joinToString(" ") { price ->
                    listOf(
                        price.id,
                        price.storeId,
                        price.supplierId,
                        price.goodsItemId,
                        price.supplierBarcode.orEmpty(),
                        price.supplierGoodsName.orEmpty(),
                        price.supplyPrice.supplierDeskMoneyText()
                    ).joinToString(" ")
                }).append(' ')
                append(itemLines.joinToString(" ") { line ->
                    listOf(
                        line.goodsItemId,
                        line.substituteGoodsItemId.orEmpty(),
                        line.goodsItemBarcodeSnapshots.joinToString(" "),
                        line.substituteGoodsItemBarcodeSnapshots.joinToString(" "),
                        line.additionalNotes.orEmpty(),
                        line.supplierComment.orEmpty()
                    ).joinToString(" ")
                })
            }.lowercase()
            val offerNote = buildString {
                append(title)
                if (barcodeText.isNotBlank()) append('\n').append(stateValues.stringBarcode).append(": ").append(barcodeText)
                if (totalQuantityText.isNotBlank()) append('\n').append(localizedStringResource(1424, "Total requested")).append(": ").append(totalQuantityText)
                if (expectedPriceText.isNotBlank()) append('\n').append(localizedStringResource(1425, "Expected price")).append(": ").append(expectedPriceText)
                if (savedPriceText.isNotBlank()) append('\n').append(localizedStringResource(1677, "Saved supplier price")).append(": ").append(savedPriceText)
                append('\n').append(localizedStringResource(1422, "Stores asking")).append(": ").append(storeListText)
                if (priceBookStoreIds.isNotEmpty()) append('\n').append(localizedStringResource(1678, "Price-book stores")).append(": ").append(priceBookStoreIds.size)
                append('\n').append(localizedStringResource(1430, "Latest status")).append(": ").append(if (relatedOrders.isEmpty() && itemPrices.isNotEmpty()) localizedStringResource(1682, "Price book ready") else supplierOrderStatusTitle(latestStatus))
            }

            SupplierCatalogItemUiModel(
                goodsItemId = editableGoodsItemId.ifBlank { goodsKey },
                title = title,
                barcodeText = barcodeText,
                totalQuantityText = totalQuantityText,
                expectedPriceText = expectedPriceText,
                savedPriceText = savedPriceText,
                storeTitles = storeTitles,
                priceBookStoreCount = priceBookStoreIds.size,
                priceBookBacked = itemPrices.isNotEmpty(),
                openOrderCount = openOrderCount,
                orderCount = relatedOrders.size,
                lineCount = itemLines.size,
                lastActivityMillis = lastActivityMillis,
                latestStatus = latestStatus,
                needsReply = needsReply,
                deliveredOnly = deliveredOnly,
                searchKey = searchKey,
                offerNote = offerNote,
                storeId = defaultStoreId,
                supplierId = defaultSupplierId,
                quantityTemplate = defaultQuantityTemplate,
                currency = currency,
                priceInputText = priceInputText,
                minOrderInputText = minOrderInputText,
                packageInputText = packageInputText,
                supplierGoodsName = supplierGoodsName,
                supplierBarcode = supplierBarcode,
                canEditPriceBook = canEditPriceBook
            )
        }
        .sortedWith(compareByDescending<SupplierCatalogItemUiModel> { if (it.openOrderCount > 0) 1 else 0 }
            .thenByDescending { if (it.needsReply) 1 else 0 }
            .thenByDescending { if (it.priceBookBacked) 1 else 0 }
            .thenByDescending { it.lastActivityMillis })
}

@Composable
internal fun AppConfiguration.SupplierCatalogChip(text: String) {
    Text(
        text = text,
        color = stateValues.AccentColor,
        fontSize = stateValues.smallTextSize,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.AccentColor.copy(alpha = 0.10f))
            .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor.copy(alpha = 0.55f), RoundedCornerShape(stateValues.cornerRadius))
            .padding(horizontal = 8.dp, vertical = 5.dp)
    )
}

@Composable
internal fun AppConfiguration.SupplierCatalogPriceLadderEditor(item: SupplierCatalogItemUiModel) {
    var priceText by remember(item.goodsItemId, item.storeId, item.supplierId, item.priceInputText) {
        mutableStateOf(item.priceInputText.filterSupplierDeskPriceInput())
    }
    var minOrderText by remember(item.goodsItemId, item.minOrderInputText) {
        mutableStateOf(item.minOrderInputText)
    }
    var packageText by remember(item.goodsItemId, item.packageInputText) {
        mutableStateOf(item.packageInputText)
    }
    val quantityAllowsFraction = item.quantityTemplate.allowsFractionalStockQuantityInput()
    val saveEnabled = item.canEditPriceBook && (priceText.filterSupplierDeskPriceInput().toDoubleOrNull() ?: 0.0) > 0.0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.AccentColor.copy(alpha = 0.07f))
            .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor.copy(alpha = 0.38f), RoundedCornerShape(stateValues.cornerRadius))
            .padding(stateValues.marginTextFieldGroup),
        verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            CpImage(
                modifier = Modifier.size(28.dp),
                url = stateValues.drawablePathIconFinances,
                fallbackRes = stateValues.drawableResIconFinances.value,
                contentDescription = localizedStringResource(1700, "Price ladder shelf"),
                tintColor = stateValues.AccentColor
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = localizedStringResource(1700, "Price ladder shelf"),
                    color = stateValues.TextColor,
                    fontSize = stateValues.textSize,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = localizedStringResource(1701, "Tune reusable supplier price, minimum order and package size. Stores keep their item, supplier keeps the offer memory."),
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize
                )
            }
        }

        if (!item.canEditPriceBook) {
            MessageText(
                modifier = Modifier.fillMaxWidth(),
                text = localizedStringResource(1704, "No store link for this price yet"),
                subText = localizedStringResource(1708, "Set a supplier-side price before the next store request arrives."),
                subTextSize = stateValues.smallTextSize
            )
        } else {
            if (item.supplierGoodsName.isNotBlank() || item.supplierBarcode.isNotBlank()) {
                StockCardInfoLine(
                    localizedStringResource(1705, "Supplier article"),
                    listOf(item.supplierGoodsName, item.supplierBarcode).filter { it.isNotBlank() }.joinToString(" • "),
                    stateValues.TextColor
                )
            }

            SimpleTextInput(
                modifier = Modifier.fillMaxWidth(),
                value = priceText,
                placeholder = stateValues.stringSupplyPrice,
                keyboardType = KeyboardType.Decimal,
                leadingIconPath = stateValues.drawablePathIconFinances,
                onTransformValue = { it.filterSupplierDeskPriceInput() },
                onValueChange = { priceText = it.filterSupplierDeskPriceInput() }
            )

            if (stateValues.isNarrowScreen) {
                Column(verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)) {
                    SimpleTextInput(
                        modifier = Modifier.fillMaxWidth(),
                        value = minOrderText,
                        placeholder = localizedStringResource(337, "Min order"),
                        keyboardType = if (quantityAllowsFraction) KeyboardType.Decimal else KeyboardType.Number,
                        leadingIconPath = stateValues.drawablePathIconStock,
                        onTransformValue = { raw -> sanitizeStockQuantityInput(raw, quantityAllowsFraction) },
                        onValueChange = { value -> if (value.isStockQuantityInputText(quantityAllowsFraction)) minOrderText = value }
                    )
                    SimpleTextInput(
                        modifier = Modifier.fillMaxWidth(),
                        value = packageText,
                        placeholder = localizedStringResource(338, "Package qty"),
                        keyboardType = if (quantityAllowsFraction) KeyboardType.Decimal else KeyboardType.Number,
                        leadingIconPath = stateValues.drawablePathIconStock,
                        onTransformValue = { raw -> sanitizeStockQuantityInput(raw, quantityAllowsFraction) },
                        onValueChange = { value -> if (value.isStockQuantityInputText(quantityAllowsFraction)) packageText = value }
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                ) {
                    SimpleTextInput(
                        modifier = Modifier.weight(1f),
                        value = minOrderText,
                        placeholder = localizedStringResource(337, "Min order"),
                        keyboardType = if (quantityAllowsFraction) KeyboardType.Decimal else KeyboardType.Number,
                        leadingIconPath = stateValues.drawablePathIconStock,
                        onTransformValue = { raw -> sanitizeStockQuantityInput(raw, quantityAllowsFraction) },
                        onValueChange = { value -> if (value.isStockQuantityInputText(quantityAllowsFraction)) minOrderText = value }
                    )
                    SimpleTextInput(
                        modifier = Modifier.weight(1f),
                        value = packageText,
                        placeholder = localizedStringResource(338, "Package qty"),
                        keyboardType = if (quantityAllowsFraction) KeyboardType.Decimal else KeyboardType.Number,
                        leadingIconPath = stateValues.drawablePathIconStock,
                        onTransformValue = { raw -> sanitizeStockQuantityInput(raw, quantityAllowsFraction) },
                        onValueChange = { value -> if (value.isStockQuantityInputText(quantityAllowsFraction)) packageText = value }
                    )
                }
            }

            val ladderSummary = listOf(
                item.currency.takeIf { it.isNotBlank() } ?: "KZT",
                minOrderText.takeIf { it.isNotBlank() }?.let { "${localizedStringResource(337, "Min order")}: $it" },
                packageText.takeIf { it.isNotBlank() }?.let { "${localizedStringResource(338, "Package qty")}: $it" }
            ).filterNotNull().joinToString(" • ")
            SupplierCatalogChip(text = ladderSummary.ifBlank { localizedStringResource(1706, "MOQ / package") })

            actionButton(
                modifier = Modifier.fillMaxWidth(),
                text = localizedStringResource(1702, "Save price ladder"),
                enabled = saveEnabled,
                iconPath = stateValues.drawablePathIconCheck,
                iconRes = stateValues.drawableResIconCheck.value,
                confirmationRequired = false,
                onDisabledClick = {
                    postInAppNotification(localizedStringResource(1709, "Enter supplier price first"), NotificationType.Neutral, transient = true)
                },
                onClick = {
                    val cleanPrice = priceText.filterSupplierDeskPriceInput().toDoubleOrNull() ?: 0.0
                    val cleanMinOrder = parseStockQuantityInputText(minOrderText, item.quantityTemplate)
                        ?.takeIf { it > 0.0 }
                        ?.let { item.quantityTemplate.withStockQuantityInputTotalValue(it) }
                    val cleanPackage = parseStockQuantityInputText(packageText, item.quantityTemplate)
                        ?.takeIf { it > 0.0 }
                        ?.let { item.quantityTemplate.withStockQuantityInputTotalValue(it) }

                    upsertSupplierGoodsPrice(
                        SupplierGoodsPriceDataModel(
                            storeId = item.storeId,
                            supplierId = item.supplierId,
                            goodsItemId = item.goodsItemId,
                            supplyPrice = PriceDataModel(
                                price = cleanPrice.roundMoney().toStockMoneyText(),
                                currency = item.currency.ifBlank { "KZT" },
                                supplierId = item.supplierId
                            ),
                            minOrderQuantity = cleanMinOrder,
                            packageQuantity = cleanPackage,
                            supplierBarcode = item.supplierBarcode.takeIf { it.isNotBlank() },
                            supplierGoodsName = item.supplierGoodsName.takeIf { it.isNotBlank() }
                        )
                    ) { result ->
                        if (result is DataState.Success) {
                            postInAppNotification(localizedStringResource(1703, "Price ladder saved"), NotificationType.Positive, transient = true)
                        }
                    }
                }
            )
        }
    }
}

@Composable
internal fun AppConfiguration.SupplierCatalogItemCard(item: SupplierCatalogItemUiModel) {
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(
                if (item.openOrderCount > 0) stateValues.focusedBorderWidth else stateValues.unfocusedBorderWidth,
                if (item.openOrderCount > 0) stateValues.AccentColor else stateValues.PlaceholderTextColor,
                RoundedCornerShape(stateValues.cornerRadius)
            )
            .padding(stateValues.marginTextFieldGroup),
        verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(stateValues.cornerRadius))
                    .background(stateValues.AccentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                CpImage(
                    modifier = Modifier.size(30.dp),
                    url = stateValues.drawablePathIconSupplierCatalog,
                    fallbackRes = stateValues.drawableResIconSupplierCatalog.value,
                    contentDescription = item.title,
                    tintColor = stateValues.AccentColor
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = stateValues.TextColor,
                    fontSize = stateValues.accentTextSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${supplierOrderStatusTitle(item.latestStatus)} • ${receiptUiDateTime(item.lastActivityMillis)}",
                    color = if (item.openOrderCount > 0) stateValues.AccentColor else stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = item.expectedPriceText.ifBlank { item.totalQuantityText },
                color = stateValues.AccentColor,
                fontSize = stateValues.accentTextSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        item.barcodeText.takeIf { it.isNotBlank() }?.let { barcode ->
            StockCardInfoLine(stateValues.stringBarcode, barcode, stateValues.TextColor)
        }
        StockCardInfoLine(localizedStringResource(1424, "Total requested"), item.totalQuantityText.ifBlank { item.lineCount.toString() }, stateValues.TextColor)
        item.expectedPriceText.takeIf { it.isNotBlank() }?.let { price ->
            StockCardInfoLine(localizedStringResource(1425, "Expected price"), price, stateValues.TextColor)
        }
        item.savedPriceText.takeIf { it.isNotBlank() }?.let { price ->
            StockCardInfoLine(localizedStringResource(1677, "Saved supplier price"), price, stateValues.AccentColor)
        }
        StockCardInfoLine(localizedStringResource(1422, "Stores asking"), item.storeTitles.size.toString(), stateValues.TextColor)
        if (item.priceBookBacked) {
            StockCardInfoLine(localizedStringResource(1678, "Price-book stores"), item.priceBookStoreCount.toString(), stateValues.TextColor)
        }
        StockCardInfoLine(localizedStringResource(1423, "Open requests"), "${item.openOrderCount} / ${item.orderCount}", stateValues.TextColor)
        StockCardInfoLine(
            localizedStringResource(1430, "Latest status"),
            if (item.orderCount == 0 && item.priceBookBacked) localizedStringResource(1682, "Price book ready") else supplierOrderStatusTitle(item.latestStatus),
            stateValues.TextColor
        )

        if (item.storeTitles.isNotEmpty()) {
            Text(
                text = localizedStringResource(1426, "Recent store signals"),
                color = stateValues.TextColor,
                fontSize = stateValues.textSize,
                fontWeight = FontWeight.Bold
            )
            item.storeTitles.take(4).chunked(if (stateValues.isNarrowScreen) 1 else 2).forEach { rowStores ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    rowStores.forEach { storeTitle ->
                        Box(modifier = Modifier.weight(1f)) {
                            SupplierCatalogChip(text = storeTitle)
                        }
                    }
                    if (!stateValues.isNarrowScreen && rowStores.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        Text(
            text = localizedStringResource(1680, "Confirmed supplier responses now update a server-backed price book, so the catalog slowly becomes a reusable offer shelf instead of a paper memory."),
            color = stateValues.PlaceholderTextColor,
            fontSize = stateValues.smallTextSize
        )
        if (item.priceBookBacked) {
            Text(
                text = localizedStringResource(1684, "Confirmed response prices automatically refresh this book."),
                color = stateValues.AccentColor,
                fontSize = stateValues.smallTextSize,
                fontWeight = FontWeight.Bold
            )
        }

        SupplierCatalogPriceLadderEditor(item = item)

        if (stateValues.isNarrowScreen) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
            ) {
                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(1427, "Open related orders"),
                    iconPath = stateValues.drawablePathIconAppModeSupplier,
                    iconRes = stateValues.drawableResIconAppModeSupplier.value,
                    confirmationRequired = false,
                    onClick = {
                        coroutineScope.launch {
                            seedSupplierOrdersInboxNavigation(searchQuery = item.goodsItemId.ifBlank { item.title })
                            Navigation.goMain(NavigationScreenModel.Supplier.Orders.Main)
                        }
                    }
                )
                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(1429, "Copy offer note"),
                    iconPath = stateValues.drawablePathIconClipboard,
                    iconRes = stateValues.drawableResIconClipboard.value,
                    confirmationRequired = false,
                    onClick = { copyTextToClipboard(item.offerNote) }
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
            ) {
                actionButton(
                    modifier = Modifier.weight(1f),
                    text = localizedStringResource(1427, "Open related orders"),
                    iconPath = stateValues.drawablePathIconAppModeSupplier,
                    iconRes = stateValues.drawableResIconAppModeSupplier.value,
                    confirmationRequired = false,
                    onClick = {
                        coroutineScope.launch {
                            seedSupplierOrdersInboxNavigation(searchQuery = item.goodsItemId.ifBlank { item.title })
                            Navigation.goMain(NavigationScreenModel.Supplier.Orders.Main)
                        }
                    }
                )
                actionButton(
                    modifier = Modifier.weight(1f),
                    text = localizedStringResource(1429, "Copy offer note"),
                    iconPath = stateValues.drawablePathIconClipboard,
                    iconRes = stateValues.drawableResIconClipboard.value,
                    confirmationRequired = false,
                    onClick = { copyTextToClipboard(item.offerNote) }
                )
            }
        }
    }
}

@Composable
internal fun AppConfiguration.SupplierCatalogScreen() {
    val orders by supplierOrdersState.payload.collectAsState()
    val lines by supplierOrderLinesState.payload.collectAsState()
    val supplierPrices by supplierGoodsPricesState.payload.collectAsState()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var catalogFilter by rememberSaveable { mutableStateOf("all") }

    LaunchedEffect(stateValues.userAccount?.id) {
        if (stateValues.userAccount != null) {
            refreshSupplierModeWorkspace(includeContracts = true)
        }
    }

    val activeOrders = remember(orders) { orders.orEmpty().filter { it.isActive && it.status != SupplierOrderStatusDataModel.Draft } }
    val activeLines = remember(lines, activeOrders) {
        val activeOrderIds = activeOrders.map { it.id }.toSet()
        lines.orEmpty().filter { line -> line.isActive && line.orderId in activeOrderIds }
    }
    val activeSupplierPrices = remember(supplierPrices) { supplierPrices.orEmpty().filter { it.isActive } }
    val catalogItems = remember(activeOrders, activeLines, activeSupplierPrices, stateValues.appLanguage) {
        buildSupplierCatalogItems(activeOrders, activeLines, activeSupplierPrices)
    }
    val normalizedSearch = searchQuery.trim().lowercase()
    val filteredItems = remember(catalogItems, normalizedSearch, catalogFilter) {
        catalogItems.filter { item ->
            val filterMatches = when (catalogFilter) {
                "open" -> item.openOrderCount > 0
                "reply" -> item.needsReply
                "delivered" -> item.deliveredOnly
                "pricebook" -> item.priceBookBacked
                else -> true
            }
            val queryMatches = normalizedSearch.isBlank() || item.searchKey.contains(normalizedSearch)
            filterMatches && queryMatches
        }
    }
    val interestedStoresCount = catalogItems.flatMap { it.storeTitles }.distinct().size
    val openDemandCount = catalogItems.sumOf { it.openOrderCount }
    val priceBookCount = catalogItems.count { it.priceBookBacked }
    val featurePlan = supplierMarketWinningFeatures()

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenAppBarWidget(
            title = localizedStringResource(1338, "Catalog"),
            iconPath = stateValues.drawablePathIconSupplierCatalog,
            iconRes = stateValues.drawableResIconSupplierCatalog.value
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.74f)
                .align(Alignment.CenterHorizontally)
                .padding(stateValues.marginTextField),
            verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField),
            contentPadding = PaddingValues(bottom = stateValues.screenHeight / 5)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(stateValues.cornerRadius))
                        .background(stateValues.AccentColor.copy(alpha = 0.10f))
                        .border(stateValues.focusedBorderWidth, stateValues.AccentColor, RoundedCornerShape(stateValues.cornerRadius))
                        .padding(stateValues.marginTextFieldGroup),
                    verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                    ) {
                        CpImage(
                            modifier = Modifier.size(42.dp),
                            url = stateValues.drawablePathIconSupplierCatalog,
                            fallbackRes = stateValues.drawableResIconSupplierCatalog.value,
                            contentDescription = localizedStringResource(1406, "Demand-built catalog"),
                            tintColor = stateValues.AccentColor
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = localizedStringResource(1406, "Demand-built catalog"),
                                color = stateValues.TextColor,
                                fontSize = stateValues.titleTextSize,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = localizedStringResource(1407, "Every requested stock item becomes a supplier-side product card, so the catalog grows from real demand instead of manual retyping."),
                                color = stateValues.PlaceholderTextColor,
                                fontSize = stateValues.smallTextSize
                            )
                        }
                    }

                    if (stateValues.isNarrowScreen) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                        ) {
                            SupplierDeskSummaryCard(
                                title = localizedStringResource(1408, "Catalog SKUs"),
                                value = catalogItems.size.toString(),
                                subtitle = localizedStringResource(1409, "Unique goods requested by connected stores"),
                                iconPath = stateValues.drawablePathIconSupplierCatalog,
                                iconRes = stateValues.drawableResIconSupplierCatalog.value
                            )
                            SupplierDeskSummaryCard(
                                title = localizedStringResource(1410, "Interested stores"),
                                value = interestedStoresCount.toString(),
                                subtitle = localizedStringResource(1411, "Stores that asked for these goods"),
                                iconPath = stateValues.drawablePathIconStores,
                                iconRes = stateValues.drawableResIconStores.value
                            )
                            SupplierDeskSummaryCard(
                                title = localizedStringResource(1412, "Open demand"),
                                value = openDemandCount.toString(),
                                subtitle = localizedStringResource(1413, "Unfinished requests linked to catalog"),
                                iconPath = stateValues.drawablePathIconAppModeSupplier,
                                iconRes = stateValues.drawableResIconAppModeSupplier.value
                            )
                            SupplierDeskSummaryCard(
                                title = localizedStringResource(1683, "Saved prices"),
                                value = priceBookCount.toString(),
                                subtitle = localizedStringResource(1684, "Confirmed response prices automatically refresh this book."),
                                iconPath = stateValues.drawablePathIconFinances,
                                iconRes = stateValues.drawableResIconFinances.value
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                        ) {
                            SupplierDeskSummaryCard(
                                modifier = Modifier.weight(1f),
                                title = localizedStringResource(1408, "Catalog SKUs"),
                                value = catalogItems.size.toString(),
                                subtitle = localizedStringResource(1409, "Unique goods requested by connected stores"),
                                iconPath = stateValues.drawablePathIconSupplierCatalog,
                                iconRes = stateValues.drawableResIconSupplierCatalog.value
                            )
                            SupplierDeskSummaryCard(
                                modifier = Modifier.weight(1f),
                                title = localizedStringResource(1410, "Interested stores"),
                                value = interestedStoresCount.toString(),
                                subtitle = localizedStringResource(1411, "Stores that asked for these goods"),
                                iconPath = stateValues.drawablePathIconStores,
                                iconRes = stateValues.drawableResIconStores.value
                            )
                            SupplierDeskSummaryCard(
                                modifier = Modifier.weight(1f),
                                title = localizedStringResource(1412, "Open demand"),
                                value = openDemandCount.toString(),
                                subtitle = localizedStringResource(1413, "Unfinished requests linked to catalog"),
                                iconPath = stateValues.drawablePathIconAppModeSupplier,
                                iconRes = stateValues.drawableResIconAppModeSupplier.value
                            )
                            SupplierDeskSummaryCard(
                                modifier = Modifier.weight(1f),
                                title = localizedStringResource(1683, "Saved prices"),
                                value = priceBookCount.toString(),
                                subtitle = localizedStringResource(1684, "Confirmed response prices automatically refresh this book."),
                                iconPath = stateValues.drawablePathIconFinances,
                                iconRes = stateValues.drawableResIconFinances.value
                            )
                        }
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
                        .clip(RoundedCornerShape(stateValues.cornerRadius))
                        .background(stateValues.BackgroundColor)
                        .border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor, RoundedCornerShape(stateValues.cornerRadius))
                        .padding(stateValues.marginTextFieldGroup),
                    verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                ) {
                    Text(
                        text = localizedStringResource(1414, "Catalog filter"),
                        color = stateValues.TextColor,
                        fontSize = stateValues.titleTextSize,
                        fontWeight = FontWeight.Bold
                    )

                    SimpleTextInput(
                        modifier = Modifier.fillMaxWidth(),
                        value = searchQuery,
                        placeholder = localizedStringResource(216, "Search"),
                        leadingIconPath = stateValues.drawablePathIconSearch,
                        onValueChange = { searchQuery = it }
                    )

                    SimpleDropdownField(
                        title = localizedStringResource(1414, "Catalog filter"),
                        selectedId = catalogFilter,
                        options = listOf(
                            DropdownOption("all", localizedStringResource(1378, "All")),
                            DropdownOption("open", localizedStringResource(1415, "With open demand")),
                            DropdownOption("reply", localizedStringResource(1416, "Needs supplier reply")),
                            DropdownOption("delivered", localizedStringResource(1417, "Delivered history")),
                            DropdownOption("pricebook", localizedStringResource(1679, "With saved prices"))
                        ),
                        placeholder = localizedStringResource(1378, "All"),
                        onSelected = { catalogFilter = it }
                    )
                }
            }

            if (catalogItems.isEmpty()) {
                item {
                    MessageText(
                        modifier = Modifier.fillMaxWidth(),
                        text = localizedStringResource(1418, "No catalog items yet"),
                        subText = localizedStringResource(1419, "The catalog will grow automatically from incoming store orders. When a store requests a stock item, it becomes a reusable supplier offer card here."),
                        subTextSize = stateValues.smallTextSize
                    )
                }
            } else if (filteredItems.isEmpty()) {
                item {
                    MessageText(
                        modifier = Modifier.fillMaxWidth(),
                        text = localizedStringResource(1420, "No catalog items match this filter")
                    )
                }
            } else {
                items(filteredItems, key = { it.goodsItemId }) { item ->
                    SupplierCatalogItemCard(item = item)
                }
            }

            item {
                Text(
                    text = localizedStringResource(1381, "Supplier feature roadmap"),
                    color = stateValues.TextColor,
                    fontSize = stateValues.titleTextSize,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            items(featurePlan.filterNot { it.title == localizedStringResource(1343, "Live B2B catalog") }) { feature ->
                SupplierFeaturePlanCard(feature = feature, compact = true)
            }
        }
    }
}


internal data class SupplierPartnerUiModel(
    val storeKey: String,
    val title: String,
    val publicId: String,
    val address: String,
    val orderCount: Int,
    val openOrderCount: Int,
    val deliveredOrderCount: Int,
    val issueOrderCount: Int,
    val cancelledOrderCount: Int,
    val requestedLineCount: Int,
    val lastActivityMillis: Long,
    val latestStatus: SupplierOrderStatusDataModel,
    val reliabilityScore: Int,
    val rhythmTitle: String,
    val actionTitle: String,
    val searchKey: String,
    val brief: String
)

internal fun AppConfiguration.buildSupplierPartnerItems(
    orders: List<SupplierOrderDataModel>,
    lines: List<SupplierOrderLineDataModel>
): List<SupplierPartnerUiModel> {
    val activeOrders = orders.filter { it.isActive }
    val activeLinesByOrder = lines
        .filter { it.isActive }
        .groupBy { it.orderId }

    return activeOrders
        .groupBy { order ->
            order.storeId
                .ifBlank { order.storePublicIdSnapshot }
                .ifBlank { supplierDeskStoreTitle(order) }
                .ifBlank { order.id }
        }
        .map { (storeKey, storeOrdersRaw) ->
            val storeOrders = storeOrdersRaw.sortedByDescending { it.supplierDeskSortTime() }
            val latestOrder = storeOrders.first()
            val storeLines = storeOrders.flatMap { activeLinesByOrder[it.id].orEmpty() }
            val title = storeOrders
                .asSequence()
                .map { supplierDeskStoreTitle(it) }
                .firstOrNull { it.isNotBlank() }
                ?: storeKey.take(12)
            val publicId = storeOrders
                .asSequence()
                .map { it.storePublicIdSnapshot }
                .firstOrNull { it.isNotBlank() }
                .orEmpty()
            val address = storeOrders
                .asSequence()
                .map { it.storeAddressTextSnapshot }
                .firstOrNull { it.isNotBlank() }
                .orEmpty()
            val openOrderCount = storeOrders.count { !it.status.isSupplierOrderClosed() }
            val deliveredOrderCount = storeOrders.count {
                it.status == SupplierOrderStatusDataModel.Delivered || it.status == SupplierOrderStatusDataModel.PartiallyDelivered
            }
            val issueOrderCount = storeOrders.count { it.status == SupplierOrderStatusDataModel.IssueReported }
            val cancelledOrderCount = storeOrders.count { it.status == SupplierOrderStatusDataModel.Cancelled }
            val activeMovingOrderCount = storeOrders.count {
                it.status == SupplierOrderStatusDataModel.Confirmed ||
                        it.status == SupplierOrderStatusDataModel.Packed ||
                        it.status == SupplierOrderStatusDataModel.InDelivery
            }
            val reliabilityScore = (62 + deliveredOrderCount * 9 + activeMovingOrderCount * 4 + storeOrders.size.coerceAtMost(8) * 2 - issueOrderCount * 16 - cancelledOrderCount * 12 - openOrderCount.coerceAtMost(8))
                .coerceIn(5, 99)
            val rhythmTitle = when {
                storeOrders.size <= 1 -> localizedStringResource(1468, "New relationship")
                reliabilityScore >= 78 && issueOrderCount == 0 -> localizedStringResource(1469, "Healthy rhythm")
                deliveredOrderCount > 0 -> localizedStringResource(1477, "Returning partner")
                storeOrders.size >= 4 -> localizedStringResource(1476, "Regular partner")
                else -> localizedStringResource(1468, "New relationship")
            }
            val actionTitle = when {
                issueOrderCount > 0 -> localizedStringResource(1470, "Watch issues")
                openOrderCount > 0 -> localizedStringResource(1467, "Needs confirmation")
                reliabilityScore >= 78 -> localizedStringResource(1469, "Healthy rhythm")
                else -> localizedStringResource(1468, "New relationship")
            }
            val latestStatus = latestOrder.status
            val lastActivityMillis = latestOrder.supplierDeskSortTime()
            val searchKey = buildString {
                append(storeKey).append(' ')
                append(title).append(' ')
                append(publicId).append(' ')
                append(address).append(' ')
                append(storeOrders.joinToString(" ") { order ->
                    listOf(
                        order.id,
                        order.status.name,
                        supplierOrderStatusTitle(order.status),
                        order.additionalNotes.orEmpty(),
                        order.supplierComment.orEmpty()
                    ).joinToString(" ")
                }).append(' ')
                append(storeLines.joinToString(" ") { line ->
                    listOf(
                        line.goodsItemId,
                        supplierDeskLineTitle(line),
                        line.goodsItemBarcodeSnapshots.joinToString(" "),
                        line.additionalNotes.orEmpty(),
                        line.supplierComment.orEmpty()
                    ).joinToString(" ")
                })
            }.lowercase()
            val brief = buildString {
                append(title)
                if (publicId.isNotBlank()) append('\n').append("ID: ").append(publicId)
                if (address.isNotBlank()) append('\n').append(localizedStringResource(147, "Address")).append(": ").append(address)
                append('\n').append(localizedStringResource(1471, "Total orders")).append(": ").append(storeOrders.size)
                append('\n').append(localizedStringResource(1455, "Open work")).append(": ").append(openOrderCount)
                append('\n').append(localizedStringResource(1472, "Delivered")).append(": ").append(deliveredOrderCount)
                append('\n').append(localizedStringResource(1473, "Issues")).append(": ").append(issueOrderCount + cancelledOrderCount)
                append('\n').append(localizedStringResource(1464, "Reliability score")).append(": ").append(reliabilityScore).append("%")
                append('\n').append(localizedStringResource(1430, "Latest status")).append(": ").append(supplierOrderStatusTitle(latestStatus))
            }

            SupplierPartnerUiModel(
                storeKey = storeKey,
                title = title,
                publicId = publicId,
                address = address,
                orderCount = storeOrders.size,
                openOrderCount = openOrderCount,
                deliveredOrderCount = deliveredOrderCount,
                issueOrderCount = issueOrderCount,
                cancelledOrderCount = cancelledOrderCount,
                requestedLineCount = storeLines.size,
                lastActivityMillis = lastActivityMillis,
                latestStatus = latestStatus,
                reliabilityScore = reliabilityScore,
                rhythmTitle = rhythmTitle,
                actionTitle = actionTitle,
                searchKey = searchKey,
                brief = brief
            )
        }
        .sortedWith(
            compareByDescending<SupplierPartnerUiModel> { it.openOrderCount }
                .thenByDescending { it.issueOrderCount }
                .thenByDescending { it.lastActivityMillis }
                .thenBy { it.title.lowercase() }
        )
}

@Composable
internal fun AppConfiguration.SupplierPartnerCard(partner: SupplierPartnerUiModel) {
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(
                if (partner.openOrderCount > 0 || partner.issueOrderCount > 0) stateValues.focusedBorderWidth else stateValues.unfocusedBorderWidth,
                if (partner.openOrderCount > 0 || partner.issueOrderCount > 0) stateValues.AccentColor else stateValues.PlaceholderTextColor,
                RoundedCornerShape(stateValues.cornerRadius)
            )
            .padding(stateValues.marginTextFieldGroup),
        verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(stateValues.cornerRadius))
                    .background(stateValues.AccentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                CpImage(
                    modifier = Modifier.size(31.dp),
                    url = stateValues.drawablePathIconSupplierPartners,
                    fallbackRes = stateValues.drawableResIconSupplierPartners.value,
                    contentDescription = partner.title,
                    tintColor = stateValues.AccentColor
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = partner.title,
                    color = stateValues.TextColor,
                    fontSize = stateValues.accentTextSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOfNotNull(
                        partner.publicId.takeIf { it.isNotBlank() },
                        partner.address.takeIf { it.isNotBlank() }
                    ).joinToString(" • ").ifBlank { localizedStringResource(1453, "Partner stores") },
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = "${partner.reliabilityScore}%",
                color = stateValues.AccentColor,
                fontSize = stateValues.titleTextSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) { SupplierCatalogChip(text = partner.actionTitle) }
            Box(modifier = Modifier.weight(1f)) { SupplierCatalogChip(text = partner.rhythmTitle) }
        }

        StockCardInfoLine(localizedStringResource(1471, "Total orders"), partner.orderCount.toString(), stateValues.TextColor)
        StockCardInfoLine(localizedStringResource(1455, "Open work"), partner.openOrderCount.toString(), stateValues.TextColor)
        StockCardInfoLine(localizedStringResource(1472, "Delivered"), partner.deliveredOrderCount.toString(), stateValues.TextColor)
        if (partner.issueOrderCount > 0 || partner.cancelledOrderCount > 0) {
            StockCardInfoLine(localizedStringResource(1473, "Issues"), "${partner.issueOrderCount + partner.cancelledOrderCount}", stateValues.ErrorColor)
        }
        StockCardInfoLine(localizedStringResource(1424, "Total requested"), partner.requestedLineCount.toString(), stateValues.TextColor)
        StockCardInfoLine(localizedStringResource(1430, "Latest status"), supplierOrderStatusTitle(partner.latestStatus), stateValues.TextColor)
        if (partner.lastActivityMillis > 0L) {
            StockCardInfoLine(localizedStringResource(1463, "Last activity"), receiptUiDateTime(partner.lastActivityMillis), stateValues.TextColor)
        }

        Text(
            text = localizedStringResource(1474, "This connects store-side supplier order history to supplier-side CRM, so real demand creates the partner profile before a heavier B2B account system exists."),
            color = stateValues.PlaceholderTextColor,
            fontSize = stateValues.smallTextSize
        )

        if (stateValues.isNarrowScreen) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
            ) {
                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(1465, "View partner orders"),
                    iconPath = stateValues.drawablePathIconAppModeSupplier,
                    iconRes = stateValues.drawableResIconAppModeSupplier.value,
                    confirmationRequired = false,
                    onClick = {
                        coroutineScope.launch {
                            seedSupplierOrdersInboxNavigation(searchQuery = partner.storeKey.ifBlank { partner.title })
                            Navigation.goMain(NavigationScreenModel.Supplier.Orders.Main)
                        }
                    }
                )
                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(1466, "Copy partner brief"),
                    iconPath = stateValues.drawablePathIconClipboard,
                    iconRes = stateValues.drawableResIconClipboard.value,
                    confirmationRequired = false,
                    onClick = {
                        copyTextToClipboard(partner.brief)
                        postInAppNotification(localizedStringResource(1475, "Partner brief copied"), NotificationType.Positive, transient = true)
                    }
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
            ) {
                actionButton(
                    modifier = Modifier.weight(1f),
                    text = localizedStringResource(1465, "View partner orders"),
                    iconPath = stateValues.drawablePathIconAppModeSupplier,
                    iconRes = stateValues.drawableResIconAppModeSupplier.value,
                    confirmationRequired = false,
                    onClick = {
                        coroutineScope.launch {
                            seedSupplierOrdersInboxNavigation(searchQuery = partner.storeKey.ifBlank { partner.title })
                            Navigation.goMain(NavigationScreenModel.Supplier.Orders.Main)
                        }
                    }
                )
                actionButton(
                    modifier = Modifier.weight(1f),
                    text = localizedStringResource(1466, "Copy partner brief"),
                    iconPath = stateValues.drawablePathIconClipboard,
                    iconRes = stateValues.drawableResIconClipboard.value,
                    confirmationRequired = false,
                    onClick = {
                        copyTextToClipboard(partner.brief)
                        postInAppNotification(localizedStringResource(1475, "Partner brief copied"), NotificationType.Positive, transient = true)
                    }
                )
            }
        }
    }
}

@Composable
internal fun AppConfiguration.SupplierCustomersScreen() {
    val orders by supplierOrdersState.payload.collectAsState()
    val lines by supplierOrderLinesState.payload.collectAsState()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var partnerFilter by rememberSaveable { mutableStateOf("all") }

    LaunchedEffect(stateValues.userAccount?.id) {
        if (stateValues.userAccount != null) {
            refreshSupplierModeWorkspace(includeContracts = true)
        }
    }

    val activeOrders = remember(orders) { orders.orEmpty().filter { it.isActive && it.status != SupplierOrderStatusDataModel.Draft } }
    val activeLines = remember(lines, activeOrders) {
        val activeOrderIds = activeOrders.map { it.id }.toSet()
        lines.orEmpty().filter { line -> line.isActive && line.orderId in activeOrderIds }
    }
    val partnerItems = remember(activeOrders, activeLines, stateValues.appLanguage) {
        buildSupplierPartnerItems(activeOrders, activeLines)
    }
    val normalizedSearch = searchQuery.trim().lowercase()
    val filteredPartners = remember(partnerItems, normalizedSearch, partnerFilter) {
        partnerItems.filter { partner ->
            val filterMatches = when (partnerFilter) {
                "open" -> partner.openOrderCount > 0
                "attention" -> partner.issueOrderCount > 0 || partner.openOrderCount > 0
                "reliable" -> partner.reliabilityScore >= 78 && partner.issueOrderCount == 0
                else -> true
            }
            val queryMatches = normalizedSearch.isBlank() || partner.searchKey.contains(normalizedSearch)
            filterMatches && queryMatches
        }
    }
    val openWorkCount = partnerItems.sumOf { it.openOrderCount }
    val reliableCount = partnerItems.count { it.reliabilityScore >= 78 && it.issueOrderCount == 0 }
    val featurePlan = supplierMarketWinningFeatures()

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenAppBarWidget(
            title = localizedStringResource(1450, "Store partner CRM"),
            iconPath = stateValues.drawablePathIconSupplierPartners,
            iconRes = stateValues.drawableResIconSupplierPartners.value
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.72f)
                .align(Alignment.CenterHorizontally)
                .padding(stateValues.marginTextField),
            verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField),
            contentPadding = PaddingValues(bottom = stateValues.screenHeight / 5)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(stateValues.cornerRadius))
                        .background(stateValues.AccentColor.copy(alpha = 0.11f))
                        .border(stateValues.focusedBorderWidth, stateValues.AccentColor, RoundedCornerShape(stateValues.cornerRadius))
                        .padding(stateValues.marginTextFieldGroup),
                    verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                    ) {
                        CpImage(
                            modifier = Modifier.size(42.dp),
                            url = stateValues.drawablePathIconSupplierPartners,
                            fallbackRes = stateValues.drawableResIconSupplierPartners.value,
                            contentDescription = localizedStringResource(1450, "Store partner CRM"),
                            tintColor = stateValues.AccentColor
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = localizedStringResource(1451, "Relationship control tower"),
                                color = stateValues.TextColor,
                                fontSize = stateValues.titleTextSize,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = localizedStringResource(1452, "Every store that sends supplier orders becomes a partner card with open work, reliability and recent demand signals."),
                                color = stateValues.PlaceholderTextColor,
                                fontSize = stateValues.textSize
                            )
                        }
                    }

                    if (stateValues.isNarrowScreen) {
                        Column(verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)) {
                            SupplierDeskSummaryCard(
                                title = localizedStringResource(1453, "Partner stores"),
                                value = partnerItems.size.toString(),
                                subtitle = localizedStringResource(1454, "Stores with at least one supplier order"),
                                iconPath = stateValues.drawablePathIconStores,
                                iconRes = stateValues.drawableResIconStores.value
                            )
                            SupplierDeskSummaryCard(
                                title = localizedStringResource(1455, "Open work"),
                                value = openWorkCount.toString(),
                                subtitle = localizedStringResource(1456, "Orders still moving"),
                                iconPath = stateValues.drawablePathIconAppModeSupplier,
                                iconRes = stateValues.drawableResIconAppModeSupplier.value
                            )
                            SupplierDeskSummaryCard(
                                title = localizedStringResource(1457, "Reliable rhythm"),
                                value = reliableCount.toString(),
                                subtitle = localizedStringResource(1458, "Partners with clean delivery flow"),
                                iconPath = stateValues.drawablePathIconSupplierPartners,
                                iconRes = stateValues.drawableResIconSupplierPartners.value
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                        ) {
                            SupplierDeskSummaryCard(
                                modifier = Modifier.weight(1f),
                                title = localizedStringResource(1453, "Partner stores"),
                                value = partnerItems.size.toString(),
                                subtitle = localizedStringResource(1454, "Stores with at least one supplier order"),
                                iconPath = stateValues.drawablePathIconStores,
                                iconRes = stateValues.drawableResIconStores.value
                            )
                            SupplierDeskSummaryCard(
                                modifier = Modifier.weight(1f),
                                title = localizedStringResource(1455, "Open work"),
                                value = openWorkCount.toString(),
                                subtitle = localizedStringResource(1456, "Orders still moving"),
                                iconPath = stateValues.drawablePathIconAppModeSupplier,
                                iconRes = stateValues.drawableResIconAppModeSupplier.value
                            )
                            SupplierDeskSummaryCard(
                                modifier = Modifier.weight(1f),
                                title = localizedStringResource(1457, "Reliable rhythm"),
                                value = reliableCount.toString(),
                                subtitle = localizedStringResource(1458, "Partners with clean delivery flow"),
                                iconPath = stateValues.drawablePathIconSupplierPartners,
                                iconRes = stateValues.drawableResIconSupplierPartners.value
                            )
                        }
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
                        .clip(RoundedCornerShape(stateValues.cornerRadius))
                        .background(stateValues.BackgroundColor)
                        .border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor, RoundedCornerShape(stateValues.cornerRadius))
                        .padding(stateValues.marginTextFieldGroup),
                    verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                ) {
                    Text(
                        text = localizedStringResource(1459, "Find a store partner fast"),
                        color = stateValues.TextColor,
                        fontSize = stateValues.titleTextSize,
                        fontWeight = FontWeight.Bold
                    )

                    SimpleTextInput(
                        modifier = Modifier.fillMaxWidth(),
                        value = searchQuery,
                        placeholder = localizedStringResource(216, "Search"),
                        leadingIconPath = stateValues.drawablePathIconSearch,
                        onValueChange = { searchQuery = it }
                    )

                    SimpleDropdownField(
                        title = localizedStringResource(1376, "Status filter"),
                        selectedId = partnerFilter,
                        options = listOf(
                            DropdownOption("all", localizedStringResource(1378, "All")),
                            DropdownOption("open", localizedStringResource(1455, "Open work")),
                            DropdownOption("attention", localizedStringResource(1467, "Needs confirmation")),
                            DropdownOption("reliable", localizedStringResource(1457, "Reliable rhythm"))
                        ),
                        placeholder = localizedStringResource(1378, "All"),
                        onSelected = { partnerFilter = it }
                    )
                }
            }

            if (partnerItems.isEmpty()) {
                item {
                    MessageText(
                        modifier = Modifier.fillMaxWidth(),
                        text = localizedStringResource(1461, "Store partners will appear here after stores send supplier orders.")
                    )
                }
            } else if (filteredPartners.isEmpty()) {
                item {
                    MessageText(
                        modifier = Modifier.fillMaxWidth(),
                        text = localizedStringResource(1462, "No partners match this filter")
                    )
                }
            } else {
                items(filteredPartners, key = { it.storeKey }) { partner ->
                    SupplierPartnerCard(partner = partner)
                }
            }

            item {
                Text(
                    text = localizedStringResource(1381, "Supplier feature roadmap"),
                    color = stateValues.TextColor,
                    fontSize = stateValues.titleTextSize,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            items(featurePlan.filterNot { it.title == localizedStringResource(1351, "Store reliability scorecards") }) { feature ->
                SupplierFeaturePlanCard(feature = feature, compact = true)
            }
        }
    }
}

@Composable
internal fun AppConfiguration.SupplierDeskSummaryCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String,
    iconPath: String,
    iconRes: DrawableResource?
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor, RoundedCornerShape(stateValues.cornerRadius))
            .padding(stateValues.marginTextFieldGroup),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(stateValues.cornerRadius))
                .background(stateValues.AccentColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            CpImage(
                modifier = Modifier.size(26.dp),
                url = iconPath,
                fallbackRes = iconRes,
                contentDescription = title,
                tintColor = stateValues.AccentColor
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = value,
                color = stateValues.AccentColor,
                fontSize = stateValues.titleTextSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = title,
                color = stateValues.TextColor,
                fontSize = stateValues.textSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun AppConfiguration.SupplierFeaturePlanCard(
    feature: SupplierFeaturePlanUiModel,
    compact: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .run { if (feature.implemented) this else foregroundTactileShadow(stateValues.cornerRadius, elevated = false) }
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(if (feature.implemented) stateValues.AccentColor.copy(alpha = 0.10f) else stateValues.BackgroundColor)
            .border(
                if (feature.implemented) stateValues.focusedBorderWidth else stateValues.unfocusedBorderWidth,
                if (feature.implemented) stateValues.AccentColor else stateValues.PlaceholderTextColor,
                RoundedCornerShape(stateValues.cornerRadius)
            )
            .padding(if (compact) stateValues.marginTextField else stateValues.marginTextFieldGroup),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
    ) {
        CpImage(
            modifier = Modifier.size(if (compact) 24.dp else 34.dp),
            url = feature.iconPath,
            fallbackRes = feature.iconRes,
            contentDescription = feature.title,
            tintColor = if (feature.implemented) stateValues.AccentColor else stateValues.IconTintColor
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = feature.title,
                color = if (feature.implemented) stateValues.AccentColor else stateValues.TextColor,
                fontSize = if (compact) stateValues.textSize else stateValues.accentTextSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = feature.subtitle,
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize,
                maxLines = if (compact) 2 else 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (feature.implemented) {
            Text(
                text = localizedStringResource(1357, "LIVE"),
                color = stateValues.AccentTextColor,
                fontSize = stateValues.smallTextSize,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(stateValues.cornerRadius))
                    .background(stateValues.AccentColor)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

internal data class SupplierSubstituteOptionUiModel(
    val goodsItemId: String,
    val title: String,
    val subtitle: String
)

internal data class SupplierOrderLineResponseDraft(
    val acceptedQuantityText: String,
    val offeredPriceText: String,
    val commentText: String,
    val substituteGoodsItemId: String
)

internal fun String.filterSupplierDeskPriceInput(): String {
    var dotUsed = false
    return replace(',', '.')
        .filter { char ->
            when {
                char.isDigit() -> true
                char == '.' && !dotUsed -> {
                    dotUsed = true
                    true
                }
                else -> false
            }
        }
        .take(18)
}

internal fun PriceDataModel?.supplierDeskPriceInputText(): String = this
    ?.price
    ?.trim()
    ?.replace(',', '.')
    ?.takeIf { it.isNotBlank() }
    .orEmpty()

internal fun supplierDeskMatchingPriceBook(
    order: SupplierOrderDataModel,
    line: SupplierOrderLineDataModel,
    draft: SupplierOrderLineResponseDraft?,
    supplierPriceRows: List<SupplierGoodsPriceDataModel>
): SupplierGoodsPriceDataModel? {
    val targetGoodsItemId = draft?.substituteGoodsItemId?.takeIf { it.isNotBlank() && it != line.goodsItemId }
        ?: line.substituteGoodsItemId?.takeIf { it.isNotBlank() }
        ?: line.goodsItemId

    return supplierPriceRows
        .asSequence()
        .filter { price ->
            price.isActive &&
                    price.storeId == order.storeId &&
                    price.supplierId == order.supplierId &&
                    price.goodsItemId == targetGoodsItemId
        }
        .maxByOrNull { price -> price.lastUsedAtMillis ?: price.updatedAtMillis }
}

internal fun AppConfiguration.supplierOrderLineDraftUsingPriceBook(
    line: SupplierOrderLineDataModel,
    draft: SupplierOrderLineResponseDraft,
    priceBookPrice: SupplierGoodsPriceDataModel
): SupplierOrderLineResponseDraft = draft.copy(
    acceptedQuantityText = stockQuantityInputTextFromAmount(line.requestedQuantity.total, line.requestedQuantity),
    offeredPriceText = priceBookPrice.supplyPrice.supplierDeskPriceInputText()
)

internal fun AppConfiguration.supplierOrderLineResponseDraft(line: SupplierOrderLineDataModel): SupplierOrderLineResponseDraft {
    val responseComment = line.supplierCommentLocalized.extractLocalizedString(stateValues.appLanguage)
        ?: line.supplierCommentLocalized.extractLocalizedString("main")
        ?: line.supplierComment.orEmpty()

    return SupplierOrderLineResponseDraft(
        acceptedQuantityText = line.supplierAcceptedQuantity
            ?.let { quantity -> stockQuantityInputTextFromAmount(quantity.total, line.requestedQuantity) }
            .orEmpty(),
        offeredPriceText = line.supplierOfferedSupplyPrice.supplierDeskPriceInputText(),
        commentText = responseComment,
        substituteGoodsItemId = line.substituteGoodsItemId.orEmpty()
    )
}

internal fun AppConfiguration.supplierOrderLineWithResponseDraft(
    line: SupplierOrderLineDataModel,
    supplierId: String,
    draft: SupplierOrderLineResponseDraft
): SupplierOrderLineDataModel {
    val cleanComment = draft.commentText.trim().takeIf { it.isNotBlank() }
    val cleanPriceText = draft.offeredPriceText.filterSupplierDeskPriceInput()
    val priceCurrency = (line.supplierOfferedSupplyPrice ?: line.expectedSupplyPrice)?.currency?.takeIf { it.isNotBlank() } ?: "KZT"
    val acceptedQuantity = draft.acceptedQuantityText.trim()
        .takeIf { it.isNotBlank() }
        ?.let { raw -> parseStockQuantityInputText(raw, line.requestedQuantity) }
        ?.let { parsed -> line.requestedQuantity.withStockQuantityInputTotalValue(parsed) }

    return line.copy(
        supplierAcceptedQuantity = acceptedQuantity,
        supplierOfferedSupplyPrice = cleanPriceText.toDoubleOrNull()?.let { price ->
            PriceDataModel(
                price = price.roundMoney().toStockMoneyText(),
                currency = priceCurrency,
                supplierId = supplierId
            )
        },
        supplierComment = cleanComment,
        supplierCommentLocalized = cleanComment?.let { listOf(LocalizedStringDataModel(stateValues.appLanguage, it)) }.orEmpty(),
        substituteGoodsItemId = draft.substituteGoodsItemId.trim().takeIf { it.isNotBlank() && it != line.goodsItemId }
    )
}

internal fun AppConfiguration.patchSupplierDeskOrder(
    order: SupplierOrderDataModel,
    lines: List<SupplierOrderLineDataModel>,
    status: SupplierOrderStatusDataModel,
    comment: String,
    confirmedDeliveryDateText: String,
    paymentTermsText: String,
    externalReferenceText: String,
    lineDrafts: Map<String, SupplierOrderLineResponseDraft>
) {
    val cleanComment = comment.trim().takeIf { it.isNotBlank() }
    val cleanPaymentTerms = paymentTermsText.trim().takeIf { it.isNotBlank() }
    val cleanExternalReference = externalReferenceText.trim().takeIf { it.isNotBlank() }
    val patchedLines = lines.map { line ->
        lineDrafts[line.id]
            ?.let { draft -> supplierOrderLineWithResponseDraft(line, order.supplierId, draft) }
            ?: line
    }
    val responseCurrency = patchedLines.firstNotNullOfOrNull { line ->
        (line.supplierOfferedSupplyPrice ?: line.expectedSupplyPrice)?.currency?.takeIf { it.isNotBlank() }
    } ?: order.amount?.currency ?: "KZT"
    val responseAmount = patchedLines.sumOf { line ->
        val price = line.supplierOfferedSupplyPrice?.price?.toMoneyDouble() ?: 0.0
        val quantity = line.supplierAcceptedQuantity?.total?.coerceAtLeast(0.0) ?: 0.0
        price * quantity
    }.roundMoney()

    updateSupplierOrder(
        SupplierOrderWithLinesDataModel(
            order = order.copy(
                amount = responseAmount.takeIf { it > 0.0 }?.let { PriceDataModel(it.toStockMoneyText(), responseCurrency, order.supplierId) },
                confirmedDeliveryTimeMillis = stockDateInputTextToMillis(confirmedDeliveryDateText),
                status = status,
                supplierComment = cleanComment,
                supplierCommentLocalized = cleanComment?.let { listOf(LocalizedStringDataModel(stateValues.appLanguage, it)) }.orEmpty(),
                paymentTerms = cleanPaymentTerms,
                externalReference = cleanExternalReference,
                updatedAtMillis = getCurrentTimeMillis()
            ),
            lines = patchedLines
        )
    ) { result ->
        if (result is DataState.Success) {
            refreshSupplierModeWorkspace(includeContracts = true)
        }
    }
}

@Composable
internal fun AppConfiguration.SupplierOrderLineResponseEditor(
    line: SupplierOrderLineDataModel,
    draft: SupplierOrderLineResponseDraft,
    editable: Boolean,
    substituteOptions: List<SupplierSubstituteOptionUiModel> = emptyList(),
    priceBookPrice: SupplierGoodsPriceDataModel? = null,
    onDraftChanged: (SupplierOrderLineResponseDraft) -> Unit
) {
    val goodsTitle = supplierDeskLineTitle(line)
    val linePrice = line.expectedSupplyPrice.supplierDeskMoneyText()
    val barcode = line.goodsItemBarcodeSnapshots.firstOrNull().orEmpty()
    val quantityAllowsFraction = line.requestedQuantity.allowsFractionalStockQuantityInput()
    val lineNotesText = line.additionalNotesLocalized.extractLocalizedString(stateValues.appLanguage)
        ?: line.additionalNotesLocalized.extractLocalizedString("main")
        ?: line.additionalNotes
    val responseQuantityText = line.supplierAcceptedQuantity?.quantityText(stateValues.appLanguage).orEmpty()
    val responsePriceText = line.supplierOfferedSupplyPrice.supplierDeskMoneyText()
    val responseCommentText = line.supplierCommentLocalized.extractLocalizedString(stateValues.appLanguage)
        ?: line.supplierCommentLocalized.extractLocalizedString("main")
        ?: line.supplierComment
    val currentSubstituteText = supplierDeskSubstituteTitle(line)
    val availableSubstituteOptions = remember(substituteOptions, line.goodsItemId) {
        substituteOptions.filter { it.goodsItemId.isNotBlank() && it.goodsItemId != line.goodsItemId }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .padding(stateValues.marginTextField),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = goodsTitle,
            color = stateValues.TextColor,
            fontSize = stateValues.textSize,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = listOf(
                line.requestedQuantity.quantityText(stateValues.appLanguage),
                linePrice,
                barcode.takeIf { it.isNotBlank() }?.let { "#${it}" }.orEmpty()
            ).filter { it.isNotBlank() }.joinToString(" • "),
            color = stateValues.PlaceholderTextColor,
            fontSize = stateValues.smallTextSize,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        lineNotesText?.takeIf { it.isNotBlank() }?.let { notes ->
            Text(
                text = notes,
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        priceBookPrice?.let { savedPrice ->
            StockCardInfoLine(
                localizedStringResource(1677, "Saved supplier price"),
                savedPrice.supplyPrice.supplierDeskMoneyText(),
                stateValues.AccentColor
            )
        }

        if (editable) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = localizedStringResource(1605, "Supplier response"),
                color = stateValues.AccentColor,
                fontSize = stateValues.smallTextSize,
                fontWeight = FontWeight.Bold
            )

            if (stateValues.isNarrowScreen) {
                Column(verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)) {
                    SimpleTextInput(
                        modifier = Modifier.fillMaxWidth(),
                        value = draft.acceptedQuantityText,
                        placeholder = localizedStringResource(1608, "Accepted quantity"),
                        keyboardType = if (quantityAllowsFraction) KeyboardType.Decimal else KeyboardType.Number,
                        leadingIconPath = stateValues.drawablePathIconStock,
                        onTransformValue = { raw -> sanitizeStockQuantityInput(raw, quantityAllowsFraction) },
                        onValueChange = { value ->
                            if (value.isStockQuantityInputText(quantityAllowsFraction)) {
                                onDraftChanged(draft.copy(acceptedQuantityText = value))
                            }
                        }
                    )
                    SimpleTextInput(
                        modifier = Modifier.fillMaxWidth(),
                        value = draft.offeredPriceText,
                        placeholder = localizedStringResource(1609, "Offered supply price"),
                        keyboardType = KeyboardType.Decimal,
                        leadingIconPath = stateValues.drawablePathIconFinances,
                        onTransformValue = { it.filterSupplierDeskPriceInput() },
                        onValueChange = { value -> onDraftChanged(draft.copy(offeredPriceText = value.filterSupplierDeskPriceInput())) }
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                ) {
                    SimpleTextInput(
                        modifier = Modifier.weight(1f),
                        value = draft.acceptedQuantityText,
                        placeholder = localizedStringResource(1608, "Accepted quantity"),
                        keyboardType = if (quantityAllowsFraction) KeyboardType.Decimal else KeyboardType.Number,
                        leadingIconPath = stateValues.drawablePathIconStock,
                        onTransformValue = { raw -> sanitizeStockQuantityInput(raw, quantityAllowsFraction) },
                        onValueChange = { value ->
                            if (value.isStockQuantityInputText(quantityAllowsFraction)) {
                                onDraftChanged(draft.copy(acceptedQuantityText = value))
                            }
                        }
                    )
                    SimpleTextInput(
                        modifier = Modifier.weight(1f),
                        value = draft.offeredPriceText,
                        placeholder = localizedStringResource(1609, "Offered supply price"),
                        keyboardType = KeyboardType.Decimal,
                        leadingIconPath = stateValues.drawablePathIconFinances,
                        onTransformValue = { it.filterSupplierDeskPriceInput() },
                        onValueChange = { value -> onDraftChanged(draft.copy(offeredPriceText = value.filterSupplierDeskPriceInput())) }
                    )
                }
            }

            SimpleTextInput(
                modifier = Modifier.fillMaxWidth(),
                value = draft.commentText,
                placeholder = localizedStringResource(1610, "Line comment"),
                singleLine = false,
                leadingIconPath = stateValues.drawablePathIconResponse,
                onValueChange = { onDraftChanged(draft.copy(commentText = it)) }
            )

            if (availableSubstituteOptions.isNotEmpty()) {
                SimpleDropdownField(
                    title = localizedStringResource(1658, "Substitute suggestion"),
                    selectedId = draft.substituteGoodsItemId,
                    options = listOf(DropdownOption("", localizedStringResource(1657, "No substitution"))) + availableSubstituteOptions.map { option ->
                        DropdownOption(
                            id = option.goodsItemId,
                            title = listOf(option.title, option.subtitle).filter { it.isNotBlank() }.joinToString(" • ")
                        )
                    },
                    placeholder = localizedStringResource(1657, "No substitution"),
                    onSelected = { selectedId -> onDraftChanged(draft.copy(substituteGoodsItemId = selectedId)) }
                )
                Text(
                    text = localizedStringResource(1659, "Use this when the requested SKU is unavailable but another store-known item can save the order."),
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize
                )
            }

            if (priceBookPrice != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                ) {
                    actionButton(
                        modifier = Modifier.weight(1f),
                        text = localizedStringResource(1695, "Use price book"),
                        iconPath = stateValues.drawablePathIconSupplierCatalog,
                        iconRes = stateValues.drawableResIconSupplierCatalog.value,
                        confirmationRequired = false,
                        onClick = {
                            onDraftChanged(supplierOrderLineDraftUsingPriceBook(line, draft, priceBookPrice))
                        }
                    )
                    actionButton(
                        modifier = Modifier.weight(1f),
                        text = localizedStringResource(1613, "Use request"),
                        iconPath = stateValues.drawablePathIconCheck,
                        confirmationRequired = false,
                        onClick = {
                            onDraftChanged(
                                draft.copy(
                                    acceptedQuantityText = stockQuantityInputTextFromAmount(line.requestedQuantity.total, line.requestedQuantity),
                                    offeredPriceText = line.expectedSupplyPrice.supplierDeskPriceInputText(),
                                    substituteGoodsItemId = ""
                                )
                            )
                        }
                    )
                }
            } else {
                actionButton(
                    text = localizedStringResource(1613, "Use request"),
                    iconPath = stateValues.drawablePathIconCheck,
                    fillMaxWidthIfTextPresent = false,
                    confirmationRequired = false,
                    onClick = {
                        onDraftChanged(
                            draft.copy(
                                acceptedQuantityText = stockQuantityInputTextFromAmount(line.requestedQuantity.total, line.requestedQuantity),
                                offeredPriceText = line.expectedSupplyPrice.supplierDeskPriceInputText(),
                                substituteGoodsItemId = ""
                            )
                        )
                    }
                )
            }

            actionButton(
                text = localizedStringResource(1758, "Decline line"),
                iconPath = stateValues.drawablePathIconCancel,
                iconRes = stateValues.drawableResIconCancel.value,
                enabledColor = stateValues.ErrorColor,
                fillMaxWidthIfTextPresent = false,
                confirmationRequired = false,
                onClick = {
                    onDraftChanged(
                        draft.copy(
                            acceptedQuantityText = stockQuantityInputTextFromAmount(0.0, line.requestedQuantity),
                            offeredPriceText = "",
                            commentText = draft.commentText.ifBlank { localizedStringResource(1760, "Unavailable for this delivery") },
                            substituteGoodsItemId = ""
                        )
                    )
                }
            )
        } else {
            StockCardInfoLine(localizedStringResource(1608, "Accepted quantity"), responseQuantityText.ifBlank { localizedStringResource(1759, "Not answered yet") }, stateValues.TextColor)
            StockCardInfoLine(localizedStringResource(1609, "Offered supply price"), responsePriceText.ifBlank { localizedStringResource(1759, "Not answered yet") }, stateValues.TextColor)
            responseCommentText?.takeIf { it.isNotBlank() }?.let {
                StockCardInfoLine(localizedStringResource(1610, "Line comment"), it, stateValues.TextColor)
            }
            currentSubstituteText.takeIf { it.isNotBlank() }?.let {
                StockCardInfoLine(localizedStringResource(1658, "Substitute suggestion"), it, stateValues.AccentColor)
            }
        }
    }
}

@Composable
internal fun AppConfiguration.SupplierOrderDeskCard(
    order: SupplierOrderDataModel,
    lines: List<SupplierOrderLineDataModel>,
    substituteOptions: List<SupplierSubstituteOptionUiModel> = emptyList(),
    supplierPriceRows: List<SupplierGoodsPriceDataModel> = emptyList()
) {
    val allowedStatuses = supplierDeskAllowedStatuses()
    val activeLines = remember(lines) { lines.filter { it.isActive } }
    var selectedStatusId by rememberSaveable(order.id, order.status.name) {
        mutableStateOf(
            when (order.status) {
                SupplierOrderStatusDataModel.Draft,
                SupplierOrderStatusDataModel.Sent -> SupplierOrderStatusDataModel.SeenBySupplier.name
                else -> order.status.name
            }
        )
    }
    var commentText by rememberSaveable(order.id, order.supplierComment.orEmpty()) {
        mutableStateOf(
            order.supplierCommentLocalized.extractLocalizedString(stateValues.appLanguage)
                ?: order.supplierCommentLocalized.extractLocalizedString("main")
                ?: order.supplierComment.orEmpty()
        )
    }
    var confirmedDeliveryDateText by rememberSaveable(order.id, order.confirmedDeliveryTimeMillis) {
        mutableStateOf(order.confirmedDeliveryTimeMillis.toStockDateInputText())
    }
    var paymentTermsText by rememberSaveable(order.id, order.paymentTerms.orEmpty()) { mutableStateOf(order.paymentTerms.orEmpty()) }
    var externalReferenceText by rememberSaveable(order.id, order.externalReference.orEmpty()) { mutableStateOf(order.externalReference.orEmpty()) }
    val lineDraftSeedKey = activeLines.joinToString("|") { line ->
        listOf(
            line.id,
            line.supplierAcceptedQuantity?.total?.toString().orEmpty(),
            line.supplierOfferedSupplyPrice?.price.orEmpty(),
            line.substituteGoodsItemId.orEmpty(),
            line.supplierComment.orEmpty(),
            line.supplierCommentLocalized.joinToString("/") { it.language + ":" + it.value }
        ).joinToString(":")
    }
    var lineDrafts by remember(order.id, lineDraftSeedKey, stateValues.appLanguage) {
        mutableStateOf(activeLines.associate { line -> line.id to supplierOrderLineResponseDraft(line) })
    }
    fun updateLineDraft(lineId: String, draft: SupplierOrderLineResponseDraft) {
        lineDrafts = lineDrafts + (lineId to draft)
    }

    fun priceBookForLine(line: SupplierOrderLineDataModel): SupplierGoodsPriceDataModel? =
        supplierDeskMatchingPriceBook(order, line, lineDrafts[line.id], supplierPriceRows)

    val priceBookMatchedLineCount = activeLines.count { line -> priceBookForLine(line) != null }

    val selectedStatus = runCatching { SupplierOrderStatusDataModel.valueOf(selectedStatusId) }.getOrDefault(order.status)
    val draftResponseLines = activeLines.map { line ->
        supplierOrderLineWithResponseDraft(
            line = line,
            supplierId = order.supplierId,
            draft = lineDrafts[line.id] ?: supplierOrderLineResponseDraft(line)
        )
    }
    val draftAnsweredLineCount = draftResponseLines.count { line -> line.supplierAcceptedQuantity != null }
    val draftDeclinedLineCount = draftResponseLines.count { line -> line.supplierDeskAcceptedQuantityTotal() == 0.0 }
    val draftMissingQuantityLineCount = draftResponseLines.count { line -> line.isMissingSupplierDeskAcceptedQuantity() }
    val draftMissingPriceLineCount = draftResponseLines.count { line -> line.isMissingSupplierDeskOfferedPriceForAcceptedQuantity() }
    val draftHasPositiveAcceptedLine = draftResponseLines.any { line -> line.hasPositiveSupplierDeskAcceptedQuantity() }
    val draftDeliveryTimeReady = stockDateInputTextToMillis(confirmedDeliveryDateText) != null
    val draftReadyToConfirm = activeLines.isNotEmpty() &&
            draftDeliveryTimeReady &&
            draftHasPositiveAcceptedLine &&
            draftResponseLines.all { line -> line.hasCompleteSupplierResponseLineForSupplierDesk() }
    val draftRequestedQuantityTotal = activeLines.sumOf { line -> line.requestedQuantity.total.coerceAtLeast(0.0) }.roundMoney()
    val draftAcceptedQuantityTotal = draftResponseLines.sumOf { line -> line.supplierAcceptedQuantity?.total?.coerceAtLeast(0.0) ?: 0.0 }.roundMoney()
    val draftAcceptedPercent = if (draftRequestedQuantityTotal > 0.0) {
        ((draftAcceptedQuantityTotal / draftRequestedQuantityTotal) * 100.0).roundToInt().coerceIn(0, 999)
    } else {
        0
    }
    val draftResponseCurrency = draftResponseLines.firstNotNullOfOrNull { line ->
        line.supplierOfferedSupplyPrice?.currency?.takeIf { it.isNotBlank() }
    } ?: order.amount?.currency ?: "KZT"
    val draftProjectedAmount = draftResponseLines.sumOf { line ->
        val quantity = line.supplierAcceptedQuantity?.total?.coerceAtLeast(0.0) ?: 0.0
        val price = line.supplierOfferedSupplyPrice?.price?.toMoneyDouble() ?: 0.0
        quantity * price
    }.roundMoney()
    val draftProjectedAmountText = draftProjectedAmount
        .takeIf { it > 0.0 }
        ?.let { amount -> "${amount.toStockMoneyText()} $draftResponseCurrency" }
        .orEmpty()
    val storeTitle = supplierDeskStoreTitle(order)
    val totalQuantityText = activeLines
        .joinToString(" • ") { line -> line.requestedQuantity.quantityText(stateValues.appLanguage) }
        .takeIf { it.isNotBlank() }
        ?: lines.size.toString()
    val amountText = order.amount.supplierDeskMoneyText()
    val isClosed = order.status.isSupplierOrderClosed()
    fun saveSupplierResponse(targetStatus: SupplierOrderStatusDataModel) {
        patchSupplierDeskOrder(
            order = order,
            lines = activeLines,
            status = targetStatus,
            comment = commentText,
            confirmedDeliveryDateText = confirmedDeliveryDateText,
            paymentTermsText = paymentTermsText,
            externalReferenceText = externalReferenceText,
            lineDrafts = lineDrafts
        )
    }

    fun fillQuickSupplierConfirmation() {
        val deliveryDateCandidate = order.confirmedDeliveryTimeMillis.toStockDateInputText()
            .ifBlank { order.desiredDeliveryTimeMillis.toStockDateInputText() }
            .ifBlank { getCurrentTimeMillis().toStockDateInputText() }
        confirmedDeliveryDateText = confirmedDeliveryDateText.ifBlank { deliveryDateCandidate }
        paymentTermsText = paymentTermsText.ifBlank { localizedStringResource(1652, "Payment after delivery, according to partner terms") }
        externalReferenceText = externalReferenceText.ifBlank { "SUP-${order.id.take(8).uppercase()}" }
        commentText = commentText.ifBlank { localizedStringResource(1653, "We can fulfill the requested lines. Quantities and prices are filled for your confirmation.") }
        selectedStatusId = SupplierOrderStatusDataModel.Confirmed.name
        lineDrafts = activeLines.associate { line ->
            val existingDraft = lineDrafts[line.id] ?: supplierOrderLineResponseDraft(line)
            line.id to existingDraft.copy(
                acceptedQuantityText = stockQuantityInputTextFromAmount(line.requestedQuantity.total, line.requestedQuantity),
                offeredPriceText = (line.supplierOfferedSupplyPrice ?: line.expectedSupplyPrice).supplierDeskPriceInputText()
            )
        }
        postInAppNotification(localizedStringResource(1654, "Supplier answer draft filled"), NotificationType.Positive, transient = true)
    }

    fun fillSupplierConfirmationFromPriceBook() {
        val deliveryDateCandidate = order.confirmedDeliveryTimeMillis.toStockDateInputText()
            .ifBlank { order.desiredDeliveryTimeMillis.toStockDateInputText() }
            .ifBlank { getCurrentTimeMillis().toStockDateInputText() }
        confirmedDeliveryDateText = confirmedDeliveryDateText.ifBlank { deliveryDateCandidate }
        paymentTermsText = paymentTermsText.ifBlank { localizedStringResource(1652, "Payment after delivery, according to partner terms") }
        externalReferenceText = externalReferenceText.ifBlank { "SUP-${order.id.take(8).uppercase()}" }
        commentText = commentText.ifBlank { localizedStringResource(1653, "We can fulfill the requested lines. Quantities and prices are filled for your confirmation.") }
        selectedStatusId = SupplierOrderStatusDataModel.Confirmed.name
        var appliedCount = 0
        lineDrafts = activeLines.associate { line ->
            val existingDraft = lineDrafts[line.id] ?: supplierOrderLineResponseDraft(line)
            val savedPrice = supplierDeskMatchingPriceBook(order, line, existingDraft, supplierPriceRows)
            if (savedPrice != null) appliedCount += 1
            line.id to if (savedPrice != null) {
                supplierOrderLineDraftUsingPriceBook(line, existingDraft, savedPrice)
            } else {
                existingDraft
            }
        }
        postInAppNotification(
            if (appliedCount > 0) localizedStringResource(1697, "Price book draft filled") else localizedStringResource(1698, "No matching saved price yet"),
            if (appliedCount > 0) NotificationType.Positive else NotificationType.Neutral,
            transient = true
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(
                if (isClosed) stateValues.unfocusedBorderWidth else stateValues.focusedBorderWidth,
                if (isClosed) stateValues.PlaceholderTextColor else stateValues.AccentColor,
                RoundedCornerShape(stateValues.cornerRadius)
            )
            .padding(stateValues.marginTextFieldGroup),
        verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(stateValues.cornerRadius))
                    .background(stateValues.AccentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                CpImage(
                    modifier = Modifier.size(28.dp),
                    url = stateValues.drawablePathIconStores,
                    fallbackRes = stateValues.drawableResIconStores.value,
                    contentDescription = storeTitle,
                    tintColor = stateValues.AccentColor
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = storeTitle,
                    color = stateValues.TextColor,
                    fontSize = stateValues.accentTextSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${supplierOrderStatusTitle(order.status)} • ${receiptUiDateTime(order.supplierDeskSortTime())}",
                    color = if (isClosed) stateValues.PlaceholderTextColor else stateValues.AccentColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                order.storeAddressTextSnapshot.takeIf { it.isNotBlank() }?.let { address ->
                    Text(
                        text = address,
                        color = stateValues.PlaceholderTextColor,
                        fontSize = stateValues.smallTextSize,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Text(
                text = amountText.ifBlank { totalQuantityText },
                color = stateValues.AccentColor,
                fontSize = stateValues.accentTextSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        StockCardInfoLine(localizedStringResource(960, "Ordered quantity"), totalQuantityText, stateValues.TextColor)
        order.desiredDeliveryTimeMillis?.toStockDateInputText()?.takeIf { it.isNotBlank() }?.let {
            StockCardInfoLine(localizedStringResource(956, "Desired delivery"), it, stateValues.TextColor)
        }
        order.confirmedDeliveryTimeMillis?.toStockDateInputText()?.takeIf { it.isNotBlank() }?.let {
            StockCardInfoLine(localizedStringResource(1606, "Confirmed delivery"), it, stateValues.TextColor)
        }
        order.externalReference?.takeIf { it.isNotBlank() }?.let {
            StockCardInfoLine(localizedStringResource(1607, "External reference"), it, stateValues.TextColor)
        }
        order.paymentTerms?.takeIf { it.isNotBlank() }?.let {
            StockCardInfoLine(localizedStringResource(1601, "Payment terms"), it, stateValues.TextColor)
        }
        val orderNotesText = order.additionalNotesLocalized.extractLocalizedString(stateValues.appLanguage)
            ?: order.additionalNotesLocalized.extractLocalizedString("main")
            ?: order.additionalNotes
        orderNotesText?.let { notes ->
            StockCardInfoLine(localizedStringResource(201, "Notes"), notes, stateValues.TextColor)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(stateValues.cornerRadius))
                .background(stateValues.DisabledColor.copy(alpha = 0.20f))
                .padding(stateValues.marginTextField),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = localizedStringResource(1358, "Requested goods"),
                color = stateValues.TextColor,
                fontSize = stateValues.textSize,
                fontWeight = FontWeight.Bold
            )
            activeLines.forEach { line ->
                SupplierOrderLineResponseEditor(
                    line = line,
                    draft = lineDrafts[line.id] ?: supplierOrderLineResponseDraft(line),
                    editable = !isClosed,
                    substituteOptions = substituteOptions,
                    priceBookPrice = priceBookForLine(line),
                    onDraftChanged = { updateLineDraft(line.id, it) }
                )
            }
        }

        if (!isClosed) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(stateValues.cornerRadius))
                    .background(stateValues.AccentColor.copy(alpha = 0.08f))
                    .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor.copy(alpha = 0.55f), RoundedCornerShape(stateValues.cornerRadius))
                    .padding(stateValues.marginTextFieldGroup),
                verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
            ) {
                Text(
                    text = localizedStringResource(1611, "Supplier editable answer"),
                    color = stateValues.TextColor,
                    fontSize = stateValues.titleTextSize,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = localizedStringResource(1612, "Stores see accepted quantity, price, delivery date and comments before goods move."),
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(stateValues.cornerRadius))
                        .background(stateValues.BackgroundColor.copy(alpha = 0.78f))
                        .border(
                            stateValues.unfocusedBorderWidth,
                            if (draftReadyToConfirm) stateValues.AccentColor.copy(alpha = 0.55f) else stateValues.PlaceholderTextColor.copy(alpha = 0.35f),
                            RoundedCornerShape(stateValues.cornerRadius)
                        )
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (draftReadyToConfirm) localizedStringResource(1768, "Ready to confirm") else localizedStringResource(1763, "Response progress"),
                        color = if (draftReadyToConfirm) stateValues.AccentColor else stateValues.TextColor,
                        fontSize = stateValues.smallTextSize,
                        fontWeight = FontWeight.Bold
                    )
                    if (stateValues.isNarrowScreen) {
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            SupplierCatalogChip(text = "${localizedStringResource(1764, "Answered")}: $draftAnsweredLineCount/${activeLines.size}")
                            SupplierCatalogChip(text = "${localizedStringResource(1765, "Declined")}: $draftDeclinedLineCount")
                            SupplierCatalogChip(text = "${localizedStringResource(1766, "Need quantity")}: $draftMissingQuantityLineCount")
                            SupplierCatalogChip(text = "${localizedStringResource(1767, "Need price")}: $draftMissingPriceLineCount")
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) { SupplierCatalogChip(text = "${localizedStringResource(1764, "Answered")}: $draftAnsweredLineCount/${activeLines.size}") }
                            Box(modifier = Modifier.weight(1f)) { SupplierCatalogChip(text = "${localizedStringResource(1765, "Declined")}: $draftDeclinedLineCount") }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) { SupplierCatalogChip(text = "${localizedStringResource(1766, "Need quantity")}: $draftMissingQuantityLineCount") }
                            Box(modifier = Modifier.weight(1f)) { SupplierCatalogChip(text = "${localizedStringResource(1767, "Need price")}: $draftMissingPriceLineCount") }
                        }
                    }
                    val acceptedProgressText = "${draftAcceptedQuantityTotal.toStockMoneyText()}/${draftRequestedQuantityTotal.toStockMoneyText()} • $draftAcceptedPercent%"
                    if (stateValues.isNarrowScreen) {
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            SupplierCatalogChip(text = "${localizedStringResource(1780, "Accepted now")}: $acceptedProgressText")
                            SupplierCatalogChip(text = "${localizedStringResource(1781, "Projected amount")}: ${draftProjectedAmountText.ifBlank { "—" }}")
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) { SupplierCatalogChip(text = "${localizedStringResource(1780, "Accepted now")}: $acceptedProgressText") }
                            Box(modifier = Modifier.weight(1f)) { SupplierCatalogChip(text = "${localizedStringResource(1781, "Projected amount")}: ${draftProjectedAmountText.ifBlank { "—" }}") }
                        }
                    }
                    if (order.status == SupplierOrderStatusDataModel.Sent) {
                        Text(
                            text = localizedStringResource(1779, "Saving a new supplier answer marks the request as seen."),
                            color = stateValues.PlaceholderTextColor,
                            fontSize = stateValues.smallTextSize
                        )
                    }
                }

                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(1655, "Fill quick confirmation"),
                    iconPath = stateValues.drawablePathIconResponse,
                    iconRes = stateValues.drawableResIconResponse.value,
                    confirmationRequired = false,
                    onClick = { fillQuickSupplierConfirmation() }
                )

                val fillFromPriceBookText = localizedStringResource(1696, "Fill from price book")
                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = "$fillFromPriceBookText • $priceBookMatchedLineCount/${activeLines.size}",
                    iconPath = stateValues.drawablePathIconSupplierCatalog,
                    iconRes = stateValues.drawableResIconSupplierCatalog.value,
                    confirmationRequired = false,
                    onClick = { fillSupplierConfirmationFromPriceBook() }
                )

                StockDatePartsEditor(
                    title = localizedStringResource(1606, "Confirmed delivery"),
                    dateText = confirmedDeliveryDateText,
                    onDateChanged = { confirmedDeliveryDateText = it }
                )

                if (stateValues.isNarrowScreen) {
                    Column(verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)) {
                        SimpleTextInput(
                            modifier = Modifier.fillMaxWidth(),
                            value = externalReferenceText,
                            placeholder = localizedStringResource(1607, "External reference"),
                            leadingIconPath = stateValues.drawablePathIconReceipt,
                            onValueChange = { externalReferenceText = it.take(80) }
                        )
                        SimpleTextInput(
                            modifier = Modifier.fillMaxWidth(),
                            value = paymentTermsText,
                            placeholder = localizedStringResource(1601, "Payment terms"),
                            leadingIconPath = stateValues.drawablePathIconFinances,
                            onValueChange = { paymentTermsText = it.take(220) }
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                    ) {
                        SimpleTextInput(
                            modifier = Modifier.weight(1f),
                            value = externalReferenceText,
                            placeholder = localizedStringResource(1607, "External reference"),
                            leadingIconPath = stateValues.drawablePathIconReceipt,
                            onValueChange = { externalReferenceText = it.take(80) }
                        )
                        SimpleTextInput(
                            modifier = Modifier.weight(1f),
                            value = paymentTermsText,
                            placeholder = localizedStringResource(1601, "Payment terms"),
                            leadingIconPath = stateValues.drawablePathIconFinances,
                            onValueChange = { paymentTermsText = it.take(220) }
                        )
                    }
                }
            }

            SimpleDropdownField(
                title = localizedStringResource(1359, "Supplier action"),
                selectedId = selectedStatusId,
                options = allowedStatuses.map { status ->
                    DropdownOption(
                        id = status.name,
                        title = supplierOrderStatusTitle(status)
                    )
                },
                placeholder = supplierOrderStatusTitle(order.status),
                onSelected = { selectedStatusId = it }
            )

            SimpleTextInput(
                modifier = Modifier.fillMaxWidth(),
                value = commentText,
                placeholder = localizedStringResource(1360, "Comment for the store"),
                singleLine = false,
                leadingIconPath = stateValues.drawablePathIconResponse,
                onValueChange = { commentText = it }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
            ) {
                actionButton(
                    modifier = Modifier.weight(1f),
                    text = localizedStringResource(1361, "Save response"),
                    iconPath = stateValues.drawablePathIconCheck,
                    confirmationRequired = false,
                    onClick = { saveSupplierResponse(selectedStatus) }
                )
                actionButton(
                    modifier = Modifier.weight(1f),
                    text = localizedStringResource(1362, "Confirm"),
                    enabled = draftReadyToConfirm,
                    iconPath = stateValues.drawablePathIconTransactionSupply,
                    confirmationRequired = false,
                    onDisabledClick = { postInAppNotification(localizedStringResource(1774, "Fill quantity, price, and delivery date first"), NotificationType.Neutral, transient = true) },
                    onClick = { saveSupplierResponse(SupplierOrderStatusDataModel.Confirmed) }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
            ) {
                actionButton(
                    modifier = Modifier.weight(1f),
                    text = localizedStringResource(1363, "Packed"),
                    enabled = order.status == SupplierOrderStatusDataModel.Confirmed && draftReadyToConfirm,
                    iconPath = stateValues.drawablePathIconStock,
                    confirmationRequired = true,
                    onDisabledClick = { postInAppNotification(localizedStringResource(1775, "Confirm first, then pack"), NotificationType.Neutral, transient = true) },
                    onClick = { saveSupplierResponse(SupplierOrderStatusDataModel.Packed) }
                )
                actionButton(
                    modifier = Modifier.weight(1f),
                    text = localizedStringResource(1364, "In delivery"),
                    enabled = order.status == SupplierOrderStatusDataModel.Packed,
                    iconPath = stateValues.drawablePathIconStores,
                    confirmationRequired = true,
                    onDisabledClick = { postInAppNotification(localizedStringResource(1776, "Pack first, then start delivery"), NotificationType.Neutral, transient = true) },
                    onClick = { saveSupplierResponse(SupplierOrderStatusDataModel.InDelivery) }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
            ) {
                actionButton(
                    modifier = Modifier.weight(1f),
                    text = localizedStringResource(1614, "Mark issue"),
                    iconPath = stateValues.drawablePathIconResponse,
                    enabledColor = stateValues.BorderlineBadColor,
                    confirmationRequired = false,
                    onClick = { saveSupplierResponse(SupplierOrderStatusDataModel.IssueReported) }
                )
                actionButton(
                    modifier = Modifier.weight(1f),
                    text = localizedStringResource(975, "Cancelled"),
                    iconPath = stateValues.drawablePathIconCancel,
                    enabledColor = stateValues.ErrorColor,
                    confirmationRequired = true,
                    onClick = { saveSupplierResponse(SupplierOrderStatusDataModel.Cancelled) }
                )
            }
        } else {
            Text(
                text = localizedStringResource(1365, "Closed orders stay here as a clean supplier-side fulfillment record."),
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize
            )
        }
    }
}


internal data class SupplierProfileIdentityUiModel(
    val supplierId: String,
    val title: String,
    val subtitle: String,
    val orderCount: Int,
    val openOrderCount: Int,
    val catalogSkuCount: Int,
    val partnerCount: Int
)

internal fun AppConfiguration.buildSupplierProfileIdentityRows(
    dashboard: SupplierModeDashboardDataModel?,
    localProfiles: List<SupplierDataModel>
): List<SupplierProfileIdentityUiModel> {
    val dashboardRows = dashboard?.supplierProfiles.orEmpty().map { profile ->
        val title = profile.name.visibleLocalizedString(stateValues.appLanguage, "")
            .ifBlank { profile.supplierId.take(8) }
        val contact = (profile.phoneNumbers.asDisplayPhoneNumbers() + profile.emails)
            .filter { it.isNotBlank() }
            .distinct()
            .take(2)
            .joinToString(" • ")
            .ifBlank { localizedStringResource(1634, "No contact yet") }
        SupplierProfileIdentityUiModel(
            supplierId = profile.supplierId,
            title = title,
            subtitle = contact,
            orderCount = profile.orderCount,
            openOrderCount = profile.openOrderCount,
            catalogSkuCount = profile.catalogSkuCount,
            partnerCount = profile.partnerCount
        )
    }

    if (dashboardRows.isNotEmpty()) return dashboardRows

    return localProfiles.map { supplier ->
        val title = supplier.visibleSupplierName(stateValues.appLanguage)
        val contact = (supplier.phoneNumbers.orEmpty().asDisplayPhoneNumbers() + supplier.emails.orEmpty())
            .filter { it.isNotBlank() }
            .distinct()
            .take(2)
            .joinToString(" • ")
            .ifBlank { localizedStringResource(1634, "No contact yet") }
        SupplierProfileIdentityUiModel(
            supplierId = supplier.id,
            title = title,
            subtitle = contact,
            orderCount = 0,
            openOrderCount = 0,
            catalogSkuCount = 0,
            partnerCount = 0
        )
    }
}

@Composable
internal fun AppConfiguration.SupplierProfileIdentityCard(
    modifier: Modifier = Modifier,
    dashboard: SupplierModeDashboardDataModel? = null,
    compact: Boolean = false
) {
    var showCreateProfileSheet by rememberSaveable { mutableStateOf(false) }
    val localProfiles = stateValues.suppliers.orEmpty().supplierProfilesOwnedBy(stateValues.userAccount?.id)
    val rows = remember(dashboard, localProfiles, stateValues.appLanguage) {
        buildSupplierProfileIdentityRows(dashboard, localProfiles)
    }
    val hasProfiles = rows.isNotEmpty() || dashboard?.supplierIds.orEmpty().isNotEmpty()

    if (showCreateProfileSheet) {
        QuickSupplierAddBottomSheet(
            onDismiss = { showCreateProfileSheet = false },
            onSaved = {
                showCreateProfileSheet = false
                refreshSupplierModeWorkspace(includeContracts = true)
                postInAppNotification(
                    localizedStringResource(1629, "Supplier profile created. The desk is refreshing."),
                    NotificationType.Positive,
                    transient = true
                )
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(if (hasProfiles) stateValues.BackgroundColor else stateValues.AccentColor.copy(alpha = 0.10f))
            .border(
                if (hasProfiles) stateValues.unfocusedBorderWidth else stateValues.focusedBorderWidth,
                if (hasProfiles) stateValues.PlaceholderTextColor else stateValues.AccentColor,
                RoundedCornerShape(stateValues.cornerRadius)
            )
            .padding(stateValues.marginTextFieldGroup),
        verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            Box(
                modifier = Modifier
                    .size(if (compact) 38.dp else 46.dp)
                    .clip(RoundedCornerShape(stateValues.cornerRadius))
                    .background(stateValues.AccentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                CpImage(
                    modifier = Modifier.size(if (compact) 24.dp else 30.dp),
                    url = stateValues.drawablePathIconSuppliers,
                    fallbackRes = stateValues.drawableResIconSuppliers.value,
                    contentDescription = localizedStringResource(1625, "Supplier identity"),
                    tintColor = stateValues.AccentColor
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (hasProfiles) localizedStringResource(1625, "Supplier identity") else localizedStringResource(1627, "Set up your supplier profile"),
                    color = stateValues.TextColor,
                    fontSize = if (compact) stateValues.accentTextSize else stateValues.titleTextSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (hasProfiles) localizedStringResource(1626, "This account can receive store orders through these supplier profiles.") else localizedStringResource(1628, "Create the business identity stores will order from. It is like hanging your sign above the warehouse door."),
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = if (compact) 3 else 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (hasProfiles) {
            SupplierCatalogChip(text = "${localizedStringResource(1630, "Active supplier profiles")}: ${rows.size.coerceAtLeast(dashboard?.supplierIds?.size ?: 0)}")

            rows.take(if (compact) 2 else 4).forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(stateValues.cornerRadius))
                        .background(stateValues.AccentColor.copy(alpha = 0.06f))
                        .border(
                            stateValues.unfocusedBorderWidth,
                            stateValues.AccentColor.copy(alpha = 0.25f),
                            RoundedCornerShape(stateValues.cornerRadius)
                        )
                        .padding(horizontal = stateValues.marginTextField, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                ) {
                    CpImage(
                        modifier = Modifier.size(22.dp),
                        url = stateValues.drawablePathIconAppModeSupplier,
                        fallbackRes = stateValues.drawableResIconAppModeSupplier.value,
                        contentDescription = row.title,
                        tintColor = stateValues.AccentColor
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = row.title,
                            color = stateValues.TextColor,
                            fontSize = stateValues.textSize,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = row.subtitle,
                            color = stateValues.PlaceholderTextColor,
                            fontSize = stateValues.smallTextSize,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    val pulse = if (row.orderCount > 0) {
                        "${row.openOrderCount}/${row.orderCount}"
                    } else {
                        localizedStringResource(1631, "Orders will arrive here")
                    }
                    Text(
                        text = pulse,
                        color = stateValues.AccentColor,
                        fontSize = stateValues.smallTextSize,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (!compact && rows.any { it.catalogSkuCount > 0 || it.partnerCount > 0 }) {
                val catalogTotal = rows.sumOf { it.catalogSkuCount }
                val partnerTotal = rows.sumOf { it.partnerCount }
                Text(
                    text = "${localizedStringResource(1408, "Catalog SKUs")}: $catalogTotal • ${localizedStringResource(1339, "Customers")}: $partnerTotal",
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Text(
                text = localizedStringResource(1635, "Incoming store orders start here after a store chooses this supplier."),
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize
            )
        }

        if (stateValues.isNarrowScreen) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
            ) {
                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = if (hasProfiles) localizedStringResource(1633, "Create another profile") else localizedStringResource(1624, "Create supplier profile"),
                    iconPath = stateValues.drawablePathIconSuppliers,
                    iconRes = stateValues.drawableResIconSuppliers.value,
                    confirmationRequired = false,
                    onClick = { showCreateProfileSheet = true }
                )
                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(1636, "Refresh supplier desk"),
                    iconPath = stateValues.drawablePathIconResponse,
                    iconRes = stateValues.drawableResIconResponse.value,
                    confirmationRequired = false,
                    onClick = { refreshSupplierModeWorkspace(includeContracts = true) }
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
            ) {
                actionButton(
                    modifier = Modifier.weight(1f),
                    text = if (hasProfiles) localizedStringResource(1633, "Create another profile") else localizedStringResource(1624, "Create supplier profile"),
                    iconPath = stateValues.drawablePathIconSuppliers,
                    iconRes = stateValues.drawableResIconSuppliers.value,
                    confirmationRequired = false,
                    onClick = { showCreateProfileSheet = true }
                )
                actionButton(
                    modifier = Modifier.weight(1f),
                    text = localizedStringResource(1636, "Refresh supplier desk"),
                    iconPath = stateValues.drawablePathIconResponse,
                    iconRes = stateValues.drawableResIconResponse.value,
                    confirmationRequired = false,
                    onClick = { refreshSupplierModeWorkspace(includeContracts = true) }
                )
            }
        }
    }
}

@Composable
internal fun AppConfiguration.SupplierOrdersInboxScreen() {
    val orders by supplierOrdersState.payload.collectAsState()
    val lines by supplierOrderLinesState.payload.collectAsState()
    val supplierPrices by supplierGoodsPricesState.payload.collectAsState()
    val supplierDashboard by supplierModeDashboardState.payload.collectAsState()
    val supplierOrderNavigationState by NavigationScreenModel.Supplier.Orders.Main.state.collectAsState()
    val supplierOrderSearchSeed = supplierOrderNavigationState[NavigationScreenModel.KEY_STATE_SEARCH_QUERY].orEmpty()
    val supplierOrderDueFilterSeed = supplierOrderNavigationState[SUPPLIER_ORDER_DUE_FILTER_STATE_KEY].orEmpty()
    val supplierOrderStatusFilterSeed = supplierOrderNavigationState[SUPPLIER_ORDER_STATUS_FILTER_STATE_KEY].orEmpty()
    var searchQuery by rememberSaveable(supplierOrderSearchSeed) { mutableStateOf(supplierOrderSearchSeed) }
    var statusFilter by rememberSaveable(supplierOrderSearchSeed, supplierOrderStatusFilterSeed) {
        mutableStateOf(supplierOrderStatusFilterSeed.ifBlank { if (supplierOrderSearchSeed.isBlank()) "open" else "all" })
    }
    var dueFilter by rememberSaveable(supplierOrderDueFilterSeed) { mutableStateOf(supplierOrderDueFilterSeed.ifBlank { "all" }) }

    LaunchedEffect(stateValues.userAccount?.id) {
        if (stateValues.userAccount != null) {
            refreshSupplierModeWorkspace(includeContracts = true)
        }
    }

    val activeOrders = remember(orders) {
        orders.orEmpty()
            .filter { it.isActive && it.status != SupplierOrderStatusDataModel.Draft }
            .sortedByDescending { it.supplierDeskSortTime() }
    }
    val activeOrdersById = remember(activeOrders) { activeOrders.associateBy { it.id } }
    val linesByOrder = remember(lines, activeOrdersById) {
        lines.orEmpty()
            .filter { line -> line.isActive && line.orderId in activeOrdersById }
            .groupBy { it.orderId }
    }
    val substituteOptionsByStore = remember(lines, activeOrdersById, stateValues.appLanguage) {
        lines.orEmpty()
            .filter { line -> line.isActive && line.orderId in activeOrdersById }
            .groupBy { line -> activeOrdersById[line.orderId]?.storeId.orEmpty() }
            .mapValues { (_, storeLines) -> buildSupplierSubstituteOptions(storeLines) }
    }
    val supplierDashboardNow = supplierDashboard?.generatedAtMillis?.takeIf { it > 0L } ?: getCurrentTimeMillis()
    val filteredOrders = remember(activeOrders, linesByOrder, searchQuery, statusFilter, dueFilter, supplierDashboardNow, stateValues.appLanguage) {
        val normalizedSearch = searchQuery.trim().lowercase()
        activeOrders.filter { order ->
            val orderLines = linesByOrder[order.id].orEmpty()
            val statusMatches = when (statusFilter) {
                "all" -> true
                "open" -> !order.status.isSupplierOrderClosed()
                "answer_gaps" -> !order.status.isSupplierOrderClosed() && SupplierOrderWithLinesDataModel(order, orderLines).hasSupplierResponseGapsForSupplierDesk()
                "ready_to_pack" -> order.isSupplierReadyToPackForSupplierDesk(orderLines)
                else -> order.status.name == statusFilter
            }
            val dueMatches = order.matchesSupplierDueFilter(dueFilter, supplierDashboardNow)
            val queryMatches = normalizedSearch.isBlank() || supplierDeskOrderSearchText(order, orderLines).contains(normalizedSearch)
            statusMatches && dueMatches && queryMatches
        }
    }
    val openCount = supplierDashboard?.openOrderCount ?: activeOrders.count { !it.status.isSupplierOrderClosed() }
    val todayAttentionCount = supplierDashboard?.actionRequiredOrderCount ?: activeOrders.count { order ->
        val orderLines = linesByOrder[order.id].orEmpty()
        !order.status.isSupplierOrderClosed() &&
                (order.status == SupplierOrderStatusDataModel.Sent ||
                        order.status == SupplierOrderStatusDataModel.SeenBySupplier ||
                        order.status == SupplierOrderStatusDataModel.IssueReported ||
                        SupplierOrderWithLinesDataModel(order, orderLines).hasSupplierResponseGapsForSupplierDesk())
    }
    val linesCount = supplierDashboard?.lineCount ?: activeOrders.sumOf { order -> linesByOrder[order.id].orEmpty().size }
    val supplierProfileCount = supplierDashboard?.supplierIds?.size ?: 0
    val manufacturerBridgeCount = supplierDashboard?.manufacturerBridge?.size ?: 0
    val manufacturerBridgePriority = supplierDashboard?.manufacturerBridge?.maxOfOrNull { it.priorityScore } ?: 0
    val backorderWatchCount = supplierDashboard?.backorderWatch?.size ?: 0
    val backorderShortQuantity = supplierDashboard?.backorderWatch?.sumOf { it.missingQuantityTotal }?.roundMoney() ?: 0.0
    val recoveryDesk = supplierDashboard?.recoveryDesk ?: SupplierDashboardRecoveryDeskDataModel()
    val supplierStatusMixText = supplierDashboard?.statusBuckets
        ?.take(4)
        ?.joinToString(" • ") { bucket -> "${supplierOrderStatusTitle(bucket.status)} ${bucket.orderCount}" }
        .orEmpty()
    val hasBackendSupplierProfile = supplierDashboard?.supplierIds?.isNotEmpty() ?: true
    val supplierDueFilterOptions = remember(supplierDashboard, stateValues.appLanguage) { supplierDueFilterOptionsFromDashboard(supplierDashboard) }
    val featurePlan = supplierMarketWinningFeatures()

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenAppBarWidget(
            title = localizedStringResource(1366, "Supplier desk"),
            iconPath = stateValues.drawablePathIconAppModeSupplier,
            iconRes = stateValues.drawableResIconAppModeSupplier.value
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.74f)
                .align(Alignment.CenterHorizontally)
                .padding(stateValues.marginTextField),
            verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField),
            contentPadding = PaddingValues(bottom = stateValues.screenHeight / 5)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(stateValues.cornerRadius))
                        .background(stateValues.AccentColor.copy(alpha = 0.10f))
                        .border(stateValues.focusedBorderWidth, stateValues.AccentColor, RoundedCornerShape(stateValues.cornerRadius))
                        .padding(stateValues.marginTextFieldGroup),
                    verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                    ) {
                        CpImage(
                            modifier = Modifier.size(42.dp),
                            url = stateValues.drawablePathIconAppModeSupplier,
                            fallbackRes = stateValues.drawableResIconAppModeSupplier.value,
                            contentDescription = localizedStringResource(1366, "Supplier desk"),
                            tintColor = stateValues.AccentColor
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = localizedStringResource(1367, "Fulfillment command center"),
                                color = stateValues.TextColor,
                                fontSize = stateValues.titleTextSize,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = localizedStringResource(1368, "The first supplier foundation: a market-ready inbox for store orders, delivery status, comments and reliable B2B rhythm."),
                                color = stateValues.PlaceholderTextColor,
                                fontSize = stateValues.smallTextSize
                            )
                        }
                    }

                    if (stateValues.isNarrowScreen) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                        ) {
                            SupplierDeskSummaryCard(
                                title = localizedStringResource(1369, "Open orders"),
                                value = openCount.toString(),
                                subtitle = localizedStringResource(1370, "Waiting for supplier action"),
                                iconPath = stateValues.drawablePathIconAppModeSupplier,
                                iconRes = stateValues.drawableResIconAppModeSupplier.value
                            )
                            SupplierDeskSummaryCard(
                                title = localizedStringResource(1371, "Needs attention"),
                                value = todayAttentionCount.toString(),
                                subtitle = localizedStringResource(1372, "New or recently seen requests"),
                                iconPath = stateValues.drawablePathIconResponse,
                                iconRes = stateValues.drawableResIconResponse.value
                            )
                            SupplierDeskSummaryCard(
                                title = localizedStringResource(1373, "Requested lines"),
                                value = linesCount.toString(),
                                subtitle = localizedStringResource(1374, "Goods positions from stores"),
                                iconPath = stateValues.drawablePathIconStock,
                                iconRes = stateValues.drawableResIconStock.value
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                        ) {
                            SupplierDeskSummaryCard(
                                modifier = Modifier.weight(1f),
                                title = localizedStringResource(1369, "Open orders"),
                                value = openCount.toString(),
                                subtitle = localizedStringResource(1370, "Waiting for supplier action"),
                                iconPath = stateValues.drawablePathIconAppModeSupplier,
                                iconRes = stateValues.drawableResIconAppModeSupplier.value
                            )
                            SupplierDeskSummaryCard(
                                modifier = Modifier.weight(1f),
                                title = localizedStringResource(1371, "Needs attention"),
                                value = todayAttentionCount.toString(),
                                subtitle = localizedStringResource(1372, "New or recently seen requests"),
                                iconPath = stateValues.drawablePathIconResponse,
                                iconRes = stateValues.drawableResIconResponse.value
                            )
                            SupplierDeskSummaryCard(
                                modifier = Modifier.weight(1f),
                                title = localizedStringResource(1373, "Requested lines"),
                                value = linesCount.toString(),
                                subtitle = localizedStringResource(1374, "Goods positions from stores"),
                                iconPath = stateValues.drawablePathIconStock,
                                iconRes = stateValues.drawableResIconStock.value
                            )
                        }
                    }

                    if (supplierDashboard != null) {
                        StockCardInfoLine(localizedStringResource(1619, "Supplier profiles"), supplierProfileCount.toString(), stateValues.TextColor)
                        StockCardInfoLine(
                            localizedStringResource(1710, "Manufacturer bridge"),
                            "$manufacturerBridgeCount • ${localizedStringResource(1722, "Priority score")} $manufacturerBridgePriority",
                            stateValues.TextColor
                        )
                        supplierStatusMixText.takeIf { it.isNotBlank() }?.let { statusMix ->
                            StockCardInfoLine(localizedStringResource(1622, "Status mix"), statusMix, stateValues.TextColor)
                        }
                        if (backorderWatchCount > 0) {
                            StockCardInfoLine(localizedStringResource(1794, "Backorder watch"), "$backorderWatchCount • ${backorderShortQuantity.toStockMoneyText()}", stateValues.TextColor)
                        }
                        recoveryDesk.recoveryDeskLane.takeIf { it.isNotBlank() }?.let { lane ->
                            StockCardInfoLine(localizedStringResource(1958, "Recovery desk"), supplierBackorderRecoveryDeskTitle(lane), stateValues.TextColor)
                        }
                        supplierDashboard?.generatedAtMillis?.takeIf { it > 0L }?.let { generatedAt ->
                            StockCardInfoLine(localizedStringResource(1617, "Server pulse"), receiptUiDateTime(generatedAt), stateValues.TextColor)
                        }
                    }
                }
            }

            item {
                SupplierProfileIdentityCard(dashboard = supplierDashboard)
            }

            supplierDashboard?.actionQueue?.takeIf { it.isNotEmpty() }?.let { actions ->
                item {
                    SupplierActionQueueCard(
                        actions = actions,
                        bulkSeenOrderIds = supplierDashboard?.bulkSeenOrderIds.orEmpty(),
                        bulkPackableOrderIds = supplierDashboard?.bulkPackableOrderIds.orEmpty(),
                        bulkDispatchableOrderIds = supplierDashboard?.bulkDispatchableOrderIds.orEmpty()
                    )
                }
            }

            supplierDashboard?.readiness?.let { readiness ->
                item {
                    SupplierReadinessBoardCard(
                        readiness = readiness,
                        onOpenAnswerGaps = {
                            statusFilter = "answer_gaps"
                            dueFilter = "all"
                            searchQuery = ""
                        },
                        onOpenPackQueue = {
                            statusFilter = "ready_to_pack"
                            dueFilter = "all"
                            searchQuery = ""
                        }
                    )
                }
            }

            supplierDashboard?.deliveryBuckets?.takeIf { it.isNotEmpty() }?.let { buckets ->
                item {
                    SupplierDeliveryPromiseRadarCard(
                        buckets = buckets,
                        selectedBucketId = dueFilter,
                        onBucketSelected = { bucketId ->
                            dueFilter = bucketId
                            statusFilter = "open"
                            searchQuery = ""
                        }
                    )
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
                        .clip(RoundedCornerShape(stateValues.cornerRadius))
                        .background(stateValues.BackgroundColor)
                        .border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor, RoundedCornerShape(stateValues.cornerRadius))
                        .padding(stateValues.marginTextFieldGroup),
                    verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                ) {
                    Text(
                        text = localizedStringResource(1375, "Find the right request fast"),
                        color = stateValues.TextColor,
                        fontSize = stateValues.titleTextSize,
                        fontWeight = FontWeight.Bold
                    )

                    SimpleTextInput(
                        modifier = Modifier.fillMaxWidth(),
                        value = searchQuery,
                        placeholder = localizedStringResource(216, "Search"),
                        leadingIconPath = stateValues.drawablePathIconSearch,
                        onValueChange = { searchQuery = it }
                    )

                    SimpleDropdownField(
                        title = localizedStringResource(1376, "Status filter"),
                        selectedId = statusFilter,
                        options = listOf(
                            DropdownOption("open", localizedStringResource(1377, "Open")),
                            DropdownOption("answer_gaps", localizedStringResource(1691, "Response gaps")),
                            DropdownOption("ready_to_pack", localizedStringResource(1689, "Ready to pack")),
                            DropdownOption("all", localizedStringResource(1378, "All"))
                        ) + SupplierOrderStatusDataModel.entries.map { status ->
                            DropdownOption(status.name, supplierOrderStatusTitle(status))
                        },
                        placeholder = localizedStringResource(1377, "Open"),
                        onSelected = { statusFilter = it }
                    )

                    SimpleDropdownField(
                        title = localizedStringResource(1667, "Delivery promise"),
                        selectedId = dueFilter,
                        options = supplierDueFilterOptions,
                        placeholder = localizedStringResource(1378, "All"),
                        onSelected = { dueFilter = it.ifBlank { "all" } }
                    )
                }
            }

            if (activeOrders.isEmpty()) {
                item {
                    MessageText(
                        modifier = Modifier.fillMaxWidth(),
                        text = if (hasBackendSupplierProfile) {
                            localizedStringResource(1379, "No store orders have reached this supplier profile yet. When stores send supply requests, they will appear here.")
                        } else {
                            localizedStringResource(1620, "No supplier profile yet")
                        },
                        subText = if (hasBackendSupplierProfile) null else localizedStringResource(1628, "Create the business identity stores will order from. It is like hanging your sign above the warehouse door."),
                        subTextSize = stateValues.smallTextSize
                    )
                }
            } else if (filteredOrders.isEmpty()) {
                item {
                    MessageText(
                        modifier = Modifier.fillMaxWidth(),
                        text = localizedStringResource(1380, "No orders match this filter")
                    )
                }
            } else {
                items(filteredOrders, key = { it.id }) { order ->
                    SupplierOrderDeskCard(
                        order = order,
                        lines = linesByOrder[order.id].orEmpty(),
                        substituteOptions = substituteOptionsByStore[order.storeId].orEmpty(),
                        supplierPriceRows = supplierPrices.orEmpty()
                    )
                }
            }

            item {
                Text(
                    text = localizedStringResource(1381, "Supplier feature roadmap"),
                    color = stateValues.TextColor,
                    fontSize = stateValues.titleTextSize,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            items(featurePlan) { feature ->
                SupplierFeaturePlanCard(feature = feature, compact = true)
            }
        }
    }
}


internal data class SupplierContractPartnerUiModel(
    val key: String,
    val storeId: String,
    val supplierId: String,
    val title: String,
    val subtitle: String
)

internal data class SupplierContractGoodsUiModel(
    val key: String,
    val storeId: String,
    val supplierId: String,
    val goodsItemId: String,
    val title: String,
    val subtitle: String,
    val priceText: String,
    val quantityText: String,
    val nameSnapshot: List<LocalizedStringDataModel>,
    val latestSupplyPrice: PriceDataModel?,
    val latestQuantity: QuantityDataModel?
)

internal fun AppConfiguration.supplierContractStatusTitle(status: String): String = when (status) {
    SUPPLIER_CONTRACT_STATUS_PENDING_SUPPLIER -> localizedStringResource(1487, "Pending supplier")
    SUPPLIER_CONTRACT_STATUS_ACTIVE -> localizedStringResource(1488, "Active contract")
    SUPPLIER_CONTRACT_STATUS_DECLINED -> localizedStringResource(1489, "Declined")
    SUPPLIER_CONTRACT_STATUS_ARCHIVED -> localizedStringResource(1490, "Archived")
    else -> localizedStringResource(1486, "Pending store")
}

internal fun AppConfiguration.supplierContractScopeTitle(scope: String): String = when (scope) {
    SUPPLIER_CONTRACT_SCOPE_GOODS_ITEM -> localizedStringResource(1492, "Goods item")
    SUPPLIER_CONTRACT_SCOPE_GOODS_GROUP -> localizedStringResource(1493, "Goods group")
    else -> localizedStringResource(1491, "Partnership-wide")
}

internal fun AppConfiguration.supplierVisibleContractTitle(contract: SupplierPartnershipContractDataModel): String =
    contract.title.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { contract.summary.visibleLocalizedString(stateValues.appLanguage, "") }
        .ifBlank { supplierContractScopeTitle(contract.scopeType) }

internal fun AppConfiguration.supplierVisibleContractSummary(contract: SupplierPartnershipContractDataModel): String =
    contract.summary.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { contract.customTerms.visibleLocalizedString(stateValues.appLanguage, "") }
        .ifBlank { localizedStringResource(1512, "Both sides must accept the same revision before supply is unlocked.") }

internal fun AppConfiguration.supplierContractStatusColor(status: String): Color = when (status) {
    SUPPLIER_CONTRACT_STATUS_ACTIVE -> stateValues.OkayColor
    SUPPLIER_CONTRACT_STATUS_DECLINED, SUPPLIER_CONTRACT_STATUS_ARCHIVED -> stateValues.DisabledColor
    SUPPLIER_CONTRACT_STATUS_PENDING_SUPPLIER, SUPPLIER_CONTRACT_STATUS_PENDING_STORE -> stateValues.BorderlineBadColor
    else -> stateValues.PlaceholderTextColor
}

internal fun AppConfiguration.buildSupplierContractPartners(
    actorSide: String,
    fixedStoreId: String?,
    orders: List<SupplierOrderDataModel>,
    suppliers: List<SupplierDataModel>
): List<SupplierContractPartnerUiModel> {
    val orderPartners = orders
        .filter { it.isActive && (fixedStoreId.isNullOrBlank() || it.storeId == fixedStoreId) }
        .groupBy { it.storeId to it.supplierId }
        .mapNotNull { (ids, partnerOrders) ->
            val firstOrder = partnerOrders.maxByOrNull { it.supplierDeskSortTime() } ?: return@mapNotNull null
            val supplier = suppliers.firstOrNull { it.id == ids.second }
            val storeTitle = supplierDeskStoreTitle(firstOrder)
            val supplierTitle = supplier?.visibleSupplierName(stateValues.appLanguage).orEmpty().ifBlank { firstOrder.supplierId.take(8) }
            SupplierContractPartnerUiModel(
                key = ids.first + "|" + ids.second,
                storeId = ids.first,
                supplierId = ids.second,
                title = if (actorSide == SUPPLIER_CONTRACT_SIDE_STORE) supplierTitle else storeTitle,
                subtitle = if (actorSide == SUPPLIER_CONTRACT_SIDE_STORE) storeTitle else supplierTitle
            )
        }

    if (actorSide != SUPPLIER_CONTRACT_SIDE_STORE || fixedStoreId.isNullOrBlank()) {
        return orderPartners.sortedBy { it.title.lowercase() }
    }

    val supplierPartners = suppliers
        .filter { it.isActive }
        .map { supplier ->
            SupplierContractPartnerUiModel(
                key = fixedStoreId + "|" + supplier.id,
                storeId = fixedStoreId,
                supplierId = supplier.id,
                title = supplier.visibleSupplierName(stateValues.appLanguage),
                subtitle = localizedStringResource(1479, "Supplier contracts")
            )
        }

    return (orderPartners + supplierPartners)
        .distinctBy { it.key }
        .sortedBy { it.title.lowercase() }
}

internal fun AppConfiguration.buildSupplierContractGoodsOptions(
    orders: List<SupplierOrderDataModel>,
    lines: List<SupplierOrderLineDataModel>
): List<SupplierContractGoodsUiModel> {
    val ordersById = orders.filter { it.isActive }.associateBy { it.id }
    return lines
        .filter { it.isActive && it.goodsItemId.isNotBlank() }
        .groupBy { line ->
            val order = ordersById[line.orderId]
            listOf(order?.storeId.orEmpty(), order?.supplierId.orEmpty(), line.goodsItemId).joinToString("|")
        }
        .mapNotNull { (key, itemLines) ->
            val sampleLine = itemLines.maxByOrNull { line -> ordersById[line.orderId]?.supplierDeskSortTime() ?: 0L } ?: return@mapNotNull null
            val sampleOrder = ordersById[sampleLine.orderId] ?: return@mapNotNull null
            val latestPrice = sampleLine.supplierOfferedSupplyPrice ?: sampleLine.expectedSupplyPrice
            val latestQuantity = sampleLine.supplierAcceptedQuantity ?: sampleLine.requestedQuantity
            val title = supplierDeskLineTitle(sampleLine)
            SupplierContractGoodsUiModel(
                key = key,
                storeId = sampleOrder.storeId,
                supplierId = sampleOrder.supplierId,
                goodsItemId = sampleLine.goodsItemId,
                title = title,
                subtitle = sampleLine.goodsItemBarcodeSnapshots.joinToString(" • ").ifBlank { sampleLine.goodsItemId.take(8) },
                priceText = latestPrice.supplierDeskMoneyText(),
                quantityText = latestQuantity?.quantityText(stateValues.appLanguage).orEmpty(),
                nameSnapshot = sampleLine.goodsItemNameSnapshot,
                latestSupplyPrice = latestPrice,
                latestQuantity = latestQuantity
            )
        }
        .distinctBy { it.key }
        .sortedBy { it.title.lowercase() }
}

internal fun AppConfiguration.supplierContractActionHint(contract: SupplierPartnershipContractDataModel, actorSide: String): String = when {
    contract.status == SUPPLIER_CONTRACT_STATUS_ACTIVE -> localizedStringResource(1525, "Terms accepted by both sides")
    actorSide == SUPPLIER_CONTRACT_SIDE_STORE && contract.status == SUPPLIER_CONTRACT_STATUS_PENDING_STORE -> localizedStringResource(1526, "Waiting for your acceptance")
    actorSide == SUPPLIER_CONTRACT_SIDE_SUPPLIER && contract.status == SUPPLIER_CONTRACT_STATUS_PENDING_SUPPLIER -> localizedStringResource(1526, "Waiting for your acceptance")
    contract.status == SUPPLIER_CONTRACT_STATUS_PENDING_STORE || contract.status == SUPPLIER_CONTRACT_STATUS_PENDING_SUPPLIER -> localizedStringResource(1527, "Waiting for the other side")
    else -> supplierContractStatusTitle(contract.status)
}

@Composable
internal fun AppConfiguration.SupplierContractEditorCard(
    actorSide: String,
    existingContract: SupplierPartnershipContractDataModel?,
    partners: List<SupplierContractPartnerUiModel>,
    goodsOptions: List<SupplierContractGoodsUiModel>,
    onClose: () -> Unit
) {
    var selectedPartnerKey by remember(existingContract?.id, partners.map { it.key }) {
        mutableStateOf(
            existingContract?.let { it.storeId + "|" + it.supplierId }
                ?: partners.firstOrNull()?.key.orEmpty()
        )
    }
    var selectedScope by remember(existingContract?.id) {
        mutableStateOf(existingContract?.scopeType ?: SUPPLIER_CONTRACT_SCOPE_PARTNERSHIP)
    }
    var selectedGoodsIds by remember(existingContract?.id) {
        mutableStateOf(existingContract?.goodsItemIds.orEmpty().toSet())
    }
    var title by remember(existingContract?.id, stateValues.appLanguage) {
        mutableStateOf(existingContract?.title?.takeIf { it.isNotEmpty() } ?: listOf(LocalizedStringDataModel("main", localizedStringResource(1479, "Supplier contracts"))))
    }
    var summary by remember(existingContract?.id, stateValues.appLanguage) {
        mutableStateOf(existingContract?.summary?.takeIf { it.isNotEmpty() } ?: listOf(LocalizedStringDataModel("main", localizedStringResource(1516, "Store requirements / supplier terms"))))
    }
    var customTerms by remember(existingContract?.id, stateValues.appLanguage) {
        mutableStateOf(existingContract?.customTerms?.takeIf { it.isNotEmpty() } ?: emptyLocalizedItemForCurrentLanguage())
    }
    var deliverySchedule by remember(existingContract?.id, stateValues.appLanguage) {
        mutableStateOf(existingContract?.deliverySchedule?.takeIf { it.isNotEmpty() } ?: emptyLocalizedItemForCurrentLanguage())
    }
    var paymentSchedule by remember(existingContract?.id, stateValues.appLanguage) {
        mutableStateOf(existingContract?.paymentSchedule?.takeIf { it.isNotEmpty() } ?: emptyLocalizedItemForCurrentLanguage())
    }
    var conditions by remember(existingContract?.id) {
        mutableStateOf(existingContract?.conditions?.takeIf { it.isNotEmpty() } ?: listOf(defaultMarginLimitStockCondition("30").toStoredStockCondition()))
    }

    val selectedPartner = partners.firstOrNull { it.key == selectedPartnerKey }
    val selectableGoods = goodsOptions
        .filter { option -> selectedPartner?.let { option.storeId == it.storeId && option.supplierId == it.supplierId } ?: false }
        .distinctBy { it.goodsItemId }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.AccentColor.copy(alpha = 0.08f))
            .border(stateValues.focusedBorderWidth, stateValues.AccentColor.copy(alpha = 0.70f), RoundedCornerShape(stateValues.cornerRadius))
            .padding(stateValues.marginTextFieldGroup),
        verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            CpImage(
                modifier = Modifier.size(34.dp),
                url = stateValues.drawablePathIconSupplierContracts,
                fallbackRes = stateValues.drawableResIconSupplierContracts.value,
                contentDescription = localizedStringResource(1479, "Supplier contracts"),
                tintColor = stateValues.AccentColor
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = existingContract?.let { localizedStringResource(1495, "Counter / edit proposal") }
                        ?: localizedStringResource(1521, "New proposal"),
                    color = stateValues.TextColor,
                    fontSize = stateValues.titleTextSize,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = localizedStringResource(1512, "Both sides must accept the same revision before supply is unlocked."),
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize
                )
            }
        }

        if (partners.isEmpty()) {
            MessageText(
                modifier = Modifier.fillMaxWidth(),
                text = localizedStringResource(1530, "No partner store yet. Supplier contracts appear after at least one store order or saved supplier link.")
            )
        } else {
            SimpleDropdownField(
                title = if (actorSide == SUPPLIER_CONTRACT_SIDE_STORE) localizedStringResource(1509, "Select supplier") else localizedStringResource(1508, "Select partner store"),
                selectedId = selectedPartnerKey,
                options = partners.map { DropdownOption(it.key, it.title, it.subtitle) },
                placeholder = localizedStringResource(216, "Search"),
                onSelected = { key ->
                    selectedPartnerKey = key
                    selectedGoodsIds = emptySet()
                }
            )

            SimpleDropdownField(
                title = localizedStringResource(1507, "Contract scope"),
                selectedId = selectedScope,
                options = listOf(
                    DropdownOption(SUPPLIER_CONTRACT_SCOPE_PARTNERSHIP, localizedStringResource(1491, "Partnership-wide"), localizedStringResource(1512, "Both sides must accept the same revision before supply is unlocked.")),
                    DropdownOption(SUPPLIER_CONTRACT_SCOPE_GOODS_ITEM, localizedStringResource(1492, "Goods item"), localizedStringResource(1514, "Contracts protect age/time/margin rules before goods start moving.")),
                    DropdownOption(SUPPLIER_CONTRACT_SCOPE_GOODS_GROUP, localizedStringResource(1493, "Goods group"), localizedStringResource(1529, "Selected goods receive copied price/schedule terms from recent store demand."))
                ),
                placeholder = localizedStringResource(1507, "Contract scope"),
                onSelected = { scope ->
                    selectedScope = scope
                    if (scope == SUPPLIER_CONTRACT_SCOPE_PARTNERSHIP) selectedGoodsIds = emptySet()
                }
            )

            if (selectedScope != SUPPLIER_CONTRACT_SCOPE_PARTNERSHIP) {
                Text(
                    text = localizedStringResource(1510, "Select goods"),
                    color = stateValues.TextColor,
                    fontSize = stateValues.textSize,
                    fontWeight = FontWeight.Bold
                )
                if (selectableGoods.isEmpty()) {
                    MessageText(
                        modifier = Modifier.fillMaxWidth(),
                        text = localizedStringResource(1529, "Selected goods receive copied price/schedule terms from recent store demand.")
                    )
                } else {
                    selectableGoods.take(10).forEach { goods ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(stateValues.cornerRadius))
                                .background(stateValues.BackgroundColor)
                                .border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor.copy(alpha = 0.45f), RoundedCornerShape(stateValues.cornerRadius))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(color = stateValues.AccentColor)
                                ) {
                                    selectedGoodsIds = if (selectedGoodsIds.contains(goods.goodsItemId)) selectedGoodsIds - goods.goodsItemId else selectedGoodsIds + goods.goodsItemId
                                    if (selectedScope == SUPPLIER_CONTRACT_SCOPE_GOODS_ITEM && selectedGoodsIds.size > 1) selectedGoodsIds = setOf(goods.goodsItemId)
                                }
                                .padding(stateValues.marginTextField),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                        ) {
                            AitaRoundCheckbox(
                                checked = selectedGoodsIds.contains(goods.goodsItemId),
                                onCheckedChange = { checked ->
                                    selectedGoodsIds = if (checked) {
                                        if (selectedScope == SUPPLIER_CONTRACT_SCOPE_GOODS_ITEM) setOf(goods.goodsItemId) else selectedGoodsIds + goods.goodsItemId
                                    } else selectedGoodsIds - goods.goodsItemId
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = goods.title,
                                    color = stateValues.TextColor,
                                    fontSize = stateValues.textSize,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = listOf(goods.subtitle, goods.priceText, goods.quantityText).filter { it.isNotBlank() }.joinToString(" • "),
                                    color = stateValues.PlaceholderTextColor,
                                    fontSize = stateValues.smallTextSize,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            LocalizedStringListEditor(
                title = localizedStringResource(1522, "Proposal title"),
                values = title,
                onChanged = { title = it }
            )
            LocalizedStringListEditor(
                title = localizedStringResource(1523, "Short summary"),
                values = summary,
                onChanged = { summary = it }
            )
            StockConditionListEditor(
                title = localizedStringResource(1503, "Contract terms"),
                values = conditions,
                onChanged = { conditions = it }
            )
            LocalizedStringListEditor(
                title = localizedStringResource(1524, "Custom written terms"),
                values = customTerms,
                onChanged = { customTerms = it }
            )
            LocalizedStringListEditor(
                title = localizedStringResource(1504, "Delivery schedule"),
                values = deliverySchedule,
                onChanged = { deliverySchedule = it }
            )
            LocalizedStringListEditor(
                title = localizedStringResource(1505, "Payment schedule"),
                values = paymentSchedule,
                onChanged = { paymentSchedule = it }
            )

            val selectedPriceTerms = selectableGoods
                .filter { it.goodsItemId in selectedGoodsIds }
                .map { goods ->
                    SupplierContractPriceTermDataModel(
                        goodsItemId = goods.goodsItemId,
                        goodsItemNameSnapshot = goods.nameSnapshot.ifEmpty { listOf(LocalizedStringDataModel("main", goods.title)) },
                        supplyPrice = goods.latestSupplyPrice,
                        minOrderQuantity = goods.latestQuantity,
                        scheduleText = deliverySchedule,
                        note = summary
                    )
                }

            if (selectedPriceTerms.isNotEmpty()) {
                Text(
                    text = localizedStringResource(1506, "Price lines"),
                    color = stateValues.TextColor,
                    fontSize = stateValues.textSize,
                    fontWeight = FontWeight.Bold
                )
                selectedPriceTerms.take(6).forEach { term ->
                    SupplierCatalogChip(
                        text = listOf(
                            term.goodsItemNameSnapshot.visibleLocalizedString(stateValues.appLanguage, term.goodsItemId.take(8)),
                            term.supplyPrice.supplierDeskMoneyText(),
                            term.minOrderQuantity?.quantityText(stateValues.appLanguage).orEmpty()
                        ).filter { it.isNotBlank() }.joinToString(" • ")
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
            ) {
                actionButton(
                    modifier = Modifier.weight(1f),
                    text = stateValues.stringCancel,
                    iconPath = stateValues.drawablePathIconCancel,
                    enabledColor = stateValues.DisabledColor,
                    confirmationRequired = false,
                    onClick = onClose
                )
                actionButton(
                    modifier = Modifier.weight(1f),
                    text = localizedStringResource(1515, "Save and send proposal"),
                    iconPath = stateValues.drawablePathIconSupplierContracts,
                    iconRes = stateValues.drawableResIconSupplierContracts.value,
                    confirmationRequired = true,
                    onClick = {
                        val partner = selectedPartner ?: return@actionButton
                        val scope = if (selectedScope == SUPPLIER_CONTRACT_SCOPE_GOODS_ITEM && selectedGoodsIds.size > 1) SUPPLIER_CONTRACT_SCOPE_GOODS_GROUP else selectedScope
                        upsertSupplierContract(
                            SupplierPartnershipContractDataModel(
                                id = existingContract?.id.orEmpty(),
                                storeId = partner.storeId,
                                supplierId = partner.supplierId,
                                authorSide = actorSide,
                                scopeType = if (scope == SUPPLIER_CONTRACT_SCOPE_PARTNERSHIP) scope else if (selectedGoodsIds.size <= 1) SUPPLIER_CONTRACT_SCOPE_GOODS_ITEM else SUPPLIER_CONTRACT_SCOPE_GOODS_GROUP,
                                goodsItemIds = if (scope == SUPPLIER_CONTRACT_SCOPE_PARTNERSHIP) emptyList() else selectedGoodsIds.toList(),
                                title = title,
                                summary = summary,
                                conditions = conditions,
                                customTerms = customTerms,
                                deliverySchedule = deliverySchedule,
                                paymentSchedule = paymentSchedule,
                                priceTerms = selectedPriceTerms,
                                authorUserId = stateValues.userAccount?.id.orEmpty(),
                                lastEditorUserId = stateValues.userAccount?.id.orEmpty()
                            )
                        ) { state -> if (state !is DataState.Empty) onClose() }
                    }
                )
            }
        }
    }
}

@Composable
internal fun AppConfiguration.SupplierContractCard(
    contract: SupplierPartnershipContractDataModel,
    actorSide: String,
    onEdit: () -> Unit
) {
    val canAccept = (actorSide == SUPPLIER_CONTRACT_SIDE_STORE && contract.status == SUPPLIER_CONTRACT_STATUS_PENDING_STORE) ||
            (actorSide == SUPPLIER_CONTRACT_SIDE_SUPPLIER && contract.status == SUPPLIER_CONTRACT_STATUS_PENDING_SUPPLIER)
    val statusColor = supplierContractStatusColor(contract.status)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(stateValues.unfocusedBorderWidth, statusColor.copy(alpha = 0.70f), RoundedCornerShape(stateValues.cornerRadius))
            .padding(stateValues.marginTextFieldGroup),
        verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            CpImage(
                modifier = Modifier.size(36.dp),
                url = stateValues.drawablePathIconSupplierContracts,
                fallbackRes = stateValues.drawableResIconSupplierContracts.value,
                contentDescription = localizedStringResource(1479, "Supplier contracts"),
                tintColor = statusColor
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = supplierVisibleContractTitle(contract),
                    color = stateValues.TextColor,
                    fontSize = stateValues.accentTextSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${supplierContractScopeTitle(contract.scopeType)} • ${supplierContractStatusTitle(contract.status)} • rev.${contract.revision}",
                    color = statusColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Text(
            text = supplierVisibleContractSummary(contract),
            color = stateValues.PlaceholderTextColor,
            fontSize = stateValues.textSize,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            SupplierCatalogChip(text = supplierContractActionHint(contract, actorSide))
            if (contract.authorSide == SUPPLIER_CONTRACT_SIDE_SUPPLIER && contract.status == SUPPLIER_CONTRACT_STATUS_PENDING_STORE) {
                Box(modifier = Modifier.weight(1f)) { SupplierCatalogChip(text = localizedStringResource(1528, "This contract blocks supply until accepted")) }
            }
        }

        if (contract.conditions.isNotEmpty()) {
            contract.conditions.take(4).forEach { raw ->
                Text(
                    text = "• ${visibleStockConditionText(raw.toStockConditionDataModel())}",
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize
                )
            }
        }

        val delivery = contract.deliverySchedule.visibleLocalizedString(stateValues.appLanguage, "")
        val payment = contract.paymentSchedule.visibleLocalizedString(stateValues.appLanguage, "")
        listOf(
            localizedStringResource(1504, "Delivery schedule") to delivery,
            localizedStringResource(1505, "Payment schedule") to payment
        ).filter { it.second.isNotBlank() }.forEach { (title, value) ->
            Text(
                text = "$title: $value",
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (contract.priceTerms.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
            ) {
                contract.priceTerms.take(2).forEach { term ->
                    Box(modifier = Modifier.weight(1f)) {
                        SupplierCatalogChip(
                            text = listOf(
                                term.goodsItemNameSnapshot.visibleLocalizedString(stateValues.appLanguage, term.goodsItemId.take(8)),
                                term.supplyPrice.supplierDeskMoneyText()
                            ).filter { it.isNotBlank() }.joinToString(" • ")
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            actionButton(
                modifier = Modifier.weight(1f),
                text = localizedStringResource(1495, "Counter / edit proposal"),
                iconPath = stateValues.drawablePathIconEdit,
                iconRes = stateValues.drawableResIconEdit.value,
                textSize = stateValues.smallTextSize,
                enabledColor = stateValues.BorderlineBadColor,
                confirmationRequired = false,
                onClick = onEdit
            )
            actionButton(
                modifier = Modifier.weight(1f),
                text = localizedStringResource(1496, "Accept contract"),
                iconPath = stateValues.drawablePathIconCheck,
                iconRes = stateValues.drawableResIconCheck.value,
                textSize = stateValues.smallTextSize,
                enabled = canAccept,
                enabledColor = stateValues.OkayColor,
                confirmationRequired = true,
                onDisabledClick = {
                    postInAppNotification(localizedStringResource(1527, "Waiting for the other side"), NotificationType.Neutral)
                },
                onClick = { acceptSupplierContract(contract.id) }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            actionButton(
                modifier = Modifier.weight(1f),
                text = localizedStringResource(1497, "Decline contract"),
                iconPath = stateValues.drawablePathIconCancel,
                iconRes = stateValues.drawableResIconCancel.value,
                textSize = stateValues.smallTextSize,
                enabledColor = stateValues.ErrorColor,
                confirmationRequired = true,
                onClick = { declineSupplierContract(contract.id) }
            )
            actionButton(
                modifier = Modifier.weight(1f),
                text = localizedStringResource(1498, "Archive contract"),
                iconPath = stateValues.drawablePathIconDelete,
                iconRes = stateValues.drawableResIconDelete.value,
                textSize = stateValues.smallTextSize,
                enabledColor = stateValues.DisabledColor,
                confirmationRequired = true,
                onClick = { archiveSupplierContract(contract.id) }
            )
        }
    }
}

@Composable
internal fun AppConfiguration.SupplierContractsBoardContent(
    modifier: Modifier = Modifier,
    actorSide: String,
    fixedStoreId: String? = null,
    showAppBar: Boolean = true
) {
    LaunchedEffect(actorSide, fixedStoreId) {
        if (actorSide == SUPPLIER_CONTRACT_SIDE_STORE) {
            fixedStoreId?.let {
                getSupplierOrders(it)
                getSupplierContracts(storeId = it)
                getSuppliers()
            }
        } else {
            refreshSupplierModeWorkspace(includeContracts = true)
        }
    }

    val contracts by supplierPartnershipContractsState.payload.collectAsState()
    val orders by supplierOrdersState.payload.collectAsState()
    val lines by supplierOrderLinesState.payload.collectAsState()
    val suppliers = stateValues.suppliers.orEmpty()
    val activeOrders = orders.orEmpty().filter { fixedStoreId.isNullOrBlank() || it.storeId == fixedStoreId }
    val partners = remember(actorSide, fixedStoreId, activeOrders, suppliers, stateValues.appLanguage) {
        buildSupplierContractPartners(actorSide, fixedStoreId, activeOrders, suppliers)
    }
    val goodsOptions = remember(activeOrders, lines, stateValues.appLanguage) {
        buildSupplierContractGoodsOptions(activeOrders, lines.orEmpty())
    }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var editingContractId by rememberSaveable { mutableStateOf<String?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var statusFilter by rememberSaveable { mutableStateOf("open") }

    val visibleContracts = remember(contracts, partners, searchQuery, statusFilter, actorSide, fixedStoreId, stateValues.appLanguage) {
        val partnerKeys = partners.map { it.key }.toSet()
        contracts.orEmpty()
            .filter { it.isActive }
            .filter { fixedStoreId.isNullOrBlank() || it.storeId == fixedStoreId }
            .filter { partnerKeys.isEmpty() || (it.storeId + "|" + it.supplierId) in partnerKeys }
            .filter { contract ->
                statusFilter == "all" ||
                        (statusFilter == "open" && contract.status != SUPPLIER_CONTRACT_STATUS_ACTIVE && contract.status != SUPPLIER_CONTRACT_STATUS_DECLINED && contract.status != SUPPLIER_CONTRACT_STATUS_ARCHIVED) ||
                        contract.status == statusFilter
            }
            .filter { contract ->
                val q = searchQuery.trim().lowercase()
                q.isBlank() || buildString {
                    append(supplierVisibleContractTitle(contract)).append(' ')
                    append(supplierVisibleContractSummary(contract)).append(' ')
                    append(contract.storeNameSnapshot.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
                    append(contract.supplierNameSnapshot.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
                    contract.conditions.forEach { append(it).append(' ') }
                }.lowercase().contains(q)
            }
            .sortedWith(compareByDescending<SupplierPartnershipContractDataModel> { if (it.status == SUPPLIER_CONTRACT_STATUS_ACTIVE) 0 else 1 }
                .thenByDescending { it.updatedAtMillis.takeIf { value -> value > 0L } ?: it.createdAtMillis })
    }

    val editingContract = editingContractId?.let { id -> contracts.orEmpty().firstOrNull { it.id == id } }
    val activeCount = contracts.orEmpty().count { it.isActive && it.status == SUPPLIER_CONTRACT_STATUS_ACTIVE && (fixedStoreId.isNullOrBlank() || it.storeId == fixedStoreId) }
    val waitingForMeCount = contracts.orEmpty().count {
        it.isActive &&
                ((actorSide == SUPPLIER_CONTRACT_SIDE_STORE && it.status == SUPPLIER_CONTRACT_STATUS_PENDING_STORE) ||
                        (actorSide == SUPPLIER_CONTRACT_SIDE_SUPPLIER && it.status == SUPPLIER_CONTRACT_STATUS_PENDING_SUPPLIER)) &&
                (fixedStoreId.isNullOrBlank() || it.storeId == fixedStoreId)
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (showAppBar) {
            ScreenAppBarWidget(
                title = localizedStringResource(1479, "Supplier contracts"),
                iconPath = stateValues.drawablePathIconSupplierContracts,
                iconRes = stateValues.drawableResIconSupplierContracts.value
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.72f)
                .align(Alignment.CenterHorizontally)
                .padding(stateValues.marginTextField),
            verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField),
            contentPadding = PaddingValues(bottom = stateValues.screenHeight / 5)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(stateValues.cornerRadius))
                        .background(stateValues.AccentColor.copy(alpha = 0.10f))
                        .border(stateValues.focusedBorderWidth, stateValues.AccentColor, RoundedCornerShape(stateValues.cornerRadius))
                        .padding(stateValues.marginTextFieldGroup),
                    verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                    ) {
                        CpImage(
                            modifier = Modifier.size(46.dp),
                            url = stateValues.drawablePathIconSupplierContracts,
                            fallbackRes = stateValues.drawableResIconSupplierContracts.value,
                            contentDescription = localizedStringResource(1479, "Supplier contracts"),
                            tintColor = stateValues.AccentColor
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = localizedStringResource(1484, "Contract board"),
                                color = stateValues.TextColor,
                                fontSize = stateValues.titleTextSize,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = localizedStringResource(1485, "One negotiated document per supplier/store relationship. Supplier proposes terms, store can counter, and supply is unlocked only after both sides accept."),
                                color = stateValues.PlaceholderTextColor,
                                fontSize = stateValues.smallTextSize
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                    ) {
                        Box(modifier = Modifier.weight(1f)) { SupplierCatalogChip(text = "${localizedStringResource(1488, "Active contract")}: $activeCount") }
                        Box(modifier = Modifier.weight(1f)) { SupplierCatalogChip(text = "${localizedStringResource(1526, "Waiting for your acceptance")}: $waitingForMeCount") }
                    }

                    actionButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = localizedStringResource(1494, "Create contract"),
                        iconPath = stateValues.drawablePathIconSupplierContracts,
                        iconRes = stateValues.drawableResIconSupplierContracts.value,
                        confirmationRequired = false,
                        onClick = {
                            editingContractId = null
                            showEditor = true
                        }
                    )
                }
            }

            if (showEditor) {
                item {
                    SupplierContractEditorCard(
                        actorSide = actorSide,
                        existingContract = editingContract,
                        partners = partners,
                        goodsOptions = goodsOptions,
                        onClose = {
                            showEditor = false
                            editingContractId = null
                        }
                    )
                }
            }

            item {
                SimpleTextInput(
                    modifier = Modifier.fillMaxWidth(),
                    value = searchQuery,
                    placeholder = localizedStringResource(216, "Search"),
                    leadingIconPath = stateValues.drawablePathIconSearch,
                    onValueChange = { searchQuery = it }
                )
            }

            item {
                SimpleDropdownField(
                    title = localizedStringResource(1376, "Status filter"),
                    selectedId = statusFilter,
                    options = listOf(
                        DropdownOption("open", localizedStringResource(1377, "Open")),
                        DropdownOption("all", localizedStringResource(1378, "All")),
                        DropdownOption(SUPPLIER_CONTRACT_STATUS_PENDING_STORE, localizedStringResource(1486, "Pending store")),
                        DropdownOption(SUPPLIER_CONTRACT_STATUS_PENDING_SUPPLIER, localizedStringResource(1487, "Pending supplier")),
                        DropdownOption(SUPPLIER_CONTRACT_STATUS_ACTIVE, localizedStringResource(1488, "Active contract")),
                        DropdownOption(SUPPLIER_CONTRACT_STATUS_DECLINED, localizedStringResource(1489, "Declined"))
                    ),
                    placeholder = localizedStringResource(1377, "Open"),
                    onSelected = { statusFilter = it }
                )
            }

            if (visibleContracts.isEmpty()) {
                item {
                    MessageText(
                        modifier = Modifier.fillMaxWidth(),
                        text = localizedStringResource(1511, "No contracts yet") + "\n" + localizedStringResource(1517, "People talk on WhatsApp, but AITA keeps the accepted version clean and machine-readable.")
                    )
                }
            } else {
                items(visibleContracts, key = { it.id }) { contract ->
                    SupplierContractCard(
                        contract = contract,
                        actorSide = actorSide,
                        onEdit = {
                            editingContractId = contract.id
                            showEditor = true
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun AppConfiguration.SupplierContractsScreen() {
    SupplierContractsBoardContent(
        actorSide = SUPPLIER_CONTRACT_SIDE_SUPPLIER,
        showAppBar = true
    )
}


internal data class SupplierDispatchLaneUiModel(
    val laneKey: String,
    val storeKey: String,
    val storeTitle: String,
    val publicId: String,
    val address: String,
    val bundles: List<SupplierOrderWithLinesDataModel>,
    val orderCount: Int,
    val lineCount: Int,
    val needsConfirmationCount: Int,
    val packedCount: Int,
    val inDeliveryCount: Int,
    val issueCount: Int,
    val activeContractCount: Int,
    val pendingContractCount: Int,
    val nextDeliveryMillis: Long?,
    val lastActivityMillis: Long,
    val goodsPreview: String,
    val manifest: String,
    val searchKey: String
)

internal fun AppConfiguration.buildSupplierDispatchLanes(
    orders: List<SupplierOrderDataModel>,
    lines: List<SupplierOrderLineDataModel>,
    contracts: List<SupplierPartnershipContractDataModel>
): List<SupplierDispatchLaneUiModel> {
    val activeLinesByOrder = lines
        .filter { it.isActive }
        .groupBy { it.orderId }
    val movableOrders = orders
        .filter { order ->
            order.isActive &&
                    order.status != SupplierOrderStatusDataModel.Draft &&
                    !order.status.isSupplierOrderClosed()
        }

    return movableOrders
        .groupBy { order ->
            val storeKey = order.storeId
                .ifBlank { order.storePublicIdSnapshot }
                .ifBlank { supplierDeskStoreTitle(order) }
                .ifBlank { order.id }
            val dueDayKey = order.supplierDueAtMillis()
                ?.let { dueMillis -> (dueMillis - (dueMillis % AITA_SUPPLIER_UI_DAY_MILLIS)).toString() }
                ?: "unscheduled"
            "$storeKey|$dueDayKey"
        }
        .map { (laneKey, storeOrdersRaw) ->
            val storeOrders = storeOrdersRaw.sortedWith(
                compareBy<SupplierOrderDataModel> { it.desiredDeliveryTimeMillis ?: Long.MAX_VALUE }
                    .thenByDescending { it.supplierDeskSortTime() }
            )
            val bundles = storeOrders.map { order ->
                SupplierOrderWithLinesDataModel(order = order, lines = activeLinesByOrder[order.id].orEmpty())
            }
            val newestOrder = storeOrders.maxByOrNull { it.supplierDeskSortTime() } ?: storeOrders.first()
            val storeKey = newestOrder.storeId
                .ifBlank { newestOrder.storePublicIdSnapshot }
                .ifBlank { supplierDeskStoreTitle(newestOrder) }
                .ifBlank { newestOrder.id }
            val storeLines = bundles.flatMap { it.lines }
            val storeTitle = storeOrders
                .asSequence()
                .map { supplierDeskStoreTitle(it) }
                .firstOrNull { it.isNotBlank() }
                ?: storeKey.take(12)
            val publicId = storeOrders
                .asSequence()
                .map { it.storePublicIdSnapshot }
                .firstOrNull { it.isNotBlank() }
                .orEmpty()
            val address = storeOrders
                .asSequence()
                .map { it.storeAddressTextSnapshot }
                .firstOrNull { it.isNotBlank() }
                .orEmpty()
            val relatedContracts = contracts.filter { contract ->
                contract.isActive &&
                        contract.storeId == newestOrder.storeId &&
                        contract.supplierId == newestOrder.supplierId
            }
            val goodsPreview = storeLines
                .groupBy { line ->
                    line.substituteGoodsItemId?.takeIf { it.isNotBlank() }
                        ?: line.goodsItemId
                            .ifBlank { line.substituteGoodsItemBarcodeSnapshots.firstOrNull().orEmpty() }
                            .ifBlank { line.goodsItemBarcodeSnapshots.firstOrNull().orEmpty() }
                            .ifBlank { supplierDeskFulfillmentLineTitle(line) }
                }
                .values
                .take(4)
                .joinToString(" • ") { itemLines ->
                    val first = itemLines.first()
                    val total = itemLines.sumOf { it.supplierDeskFulfillmentQuantity().total.coerceAtLeast(0.0) }
                    val quantityText = if (total > 0.0) first.supplierDeskFulfillmentQuantity().copy(total = total).quantityText(stateValues.appLanguage) else itemLines.size.toString()
                    supplierDeskFulfillmentLineTitle(first) + " × " + quantityText
                }
                .ifBlank { localizedStringResource(1566, "No goods lines yet") }
            val manifest = buildString {
                append(localizedStringResource(1572, "Driver manifest")).append(" — ").append(storeTitle).append('\n')
                if (publicId.isNotBlank()) append("ID: ").append(publicId).append('\n')
                if (address.isNotBlank()) append(localizedStringResource(147, "Address")).append(": ").append(address).append('\n')
                append(localizedStringResource(1563, "Orders in lane")).append(": ").append(storeOrders.size).append('\n')
                append(localizedStringResource(1564, "Goods lines")).append(": ").append(storeLines.size).append('\n')
                storeOrders.forEach { order ->
                    append("\n#").append(order.id.take(8)).append(" — ").append(supplierOrderStatusTitle(order.status))
                    order.desiredDeliveryTimeMillis?.toStockDateInputText()?.takeIf { it.isNotBlank() }?.let { dateText ->
                        append(" — ").append(dateText)
                    }
                    activeLinesByOrder[order.id].orEmpty().take(8).forEach { line ->
                        append('\n')
                            .append("  • ")
                            .append(supplierDeskFulfillmentLineTitle(line))
                            .append(" × ")
                            .append(line.supplierDeskFulfillmentQuantity().quantityText(stateValues.appLanguage))
                    }
                }
            }
            val searchKey = buildString {
                append(storeKey).append(' ')
                append(storeTitle).append(' ')
                append(publicId).append(' ')
                append(address).append(' ')
                append(goodsPreview).append(' ')
                storeOrders.forEach { order ->
                    append(order.id).append(' ')
                    append(order.status.name).append(' ')
                    append(supplierOrderStatusTitle(order.status)).append(' ')
                    append(order.additionalNotes.orEmpty()).append(' ')
                    append(order.supplierComment.orEmpty()).append(' ')
                }
                storeLines.forEach { line ->
                    append(line.goodsItemId).append(' ')
                    append(line.substituteGoodsItemId.orEmpty()).append(' ')
                    append(supplierDeskLineTitle(line)).append(' ')
                    append(supplierDeskFulfillmentLineTitle(line)).append(' ')
                    append(line.goodsItemBarcodeSnapshots.joinToString(" ")).append(' ')
                    append(line.substituteGoodsItemBarcodeSnapshots.joinToString(" ")).append(' ')
                    append(line.supplierDeskFulfillmentQuantity().quantityText(stateValues.appLanguage)).append(' ')
                }
            }.lowercase()

            SupplierDispatchLaneUiModel(
                laneKey = laneKey,
                storeKey = storeKey,
                storeTitle = storeTitle,
                publicId = publicId,
                address = address,
                bundles = bundles,
                orderCount = storeOrders.size,
                lineCount = storeLines.size,
                needsConfirmationCount = bundles.count { bundle ->
                    bundle.order.status == SupplierOrderStatusDataModel.Sent ||
                            bundle.order.status == SupplierOrderStatusDataModel.SeenBySupplier ||
                            (bundle.order.status == SupplierOrderStatusDataModel.Confirmed && bundle.hasSupplierResponseGapsForSupplierDesk())
                },
                packedCount = storeOrders.count { it.status == SupplierOrderStatusDataModel.Packed },
                inDeliveryCount = storeOrders.count { it.status == SupplierOrderStatusDataModel.InDelivery },
                issueCount = storeOrders.count { it.status == SupplierOrderStatusDataModel.IssueReported },
                activeContractCount = relatedContracts.count { it.status == SUPPLIER_CONTRACT_STATUS_ACTIVE },
                pendingContractCount = relatedContracts.count { it.status == SUPPLIER_CONTRACT_STATUS_PENDING_STORE || it.status == SUPPLIER_CONTRACT_STATUS_PENDING_SUPPLIER },
                nextDeliveryMillis = storeOrders.mapNotNull { it.supplierDueAtMillis() }.minOrNull(),
                lastActivityMillis = newestOrder.supplierDeskSortTime(),
                goodsPreview = goodsPreview,
                manifest = manifest,
                searchKey = searchKey
            )
        }
        .sortedWith(
            compareByDescending<SupplierDispatchLaneUiModel> { it.issueCount }
                .thenByDescending { it.needsConfirmationCount }
                .thenBy { it.nextDeliveryMillis ?: Long.MAX_VALUE }
                .thenByDescending { it.lastActivityMillis }
        )
}

@Composable
internal fun AppConfiguration.SupplierDispatchLaneCard(lane: SupplierDispatchLaneUiModel) {
    val coroutineScope = rememberCoroutineScope()
    val packableBundles = remember(lane.bundles) {
        lane.bundles.filter { bundle -> bundle.isSupplierReadyToPackForSupplierDesk() }
    }
    val deliverableBundles = remember(lane.bundles) {
        lane.bundles.filter { it.order.status == SupplierOrderStatusDataModel.Packed }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(
                if (lane.issueCount > 0 || lane.needsConfirmationCount > 0) stateValues.focusedBorderWidth else stateValues.unfocusedBorderWidth,
                if (lane.issueCount > 0) stateValues.ErrorColor else if (lane.needsConfirmationCount > 0) stateValues.AccentColor else stateValues.PlaceholderTextColor,
                RoundedCornerShape(stateValues.cornerRadius)
            )
            .padding(stateValues.marginTextFieldGroup),
        verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(stateValues.cornerRadius))
                    .background(stateValues.AccentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                CpImage(
                    modifier = Modifier.size(28.dp),
                    url = stateValues.drawablePathIconSupplierDispatch,
                    fallbackRes = stateValues.drawableResIconSupplierDispatch.value,
                    contentDescription = localizedStringResource(1556, "Dispatch"),
                    tintColor = stateValues.AccentColor
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = lane.storeTitle,
                    color = stateValues.TextColor,
                    fontSize = stateValues.titleTextSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOf(lane.publicId, lane.address).filter { it.isNotBlank() }.joinToString(" • ").ifBlank { localizedStringResource(1557, "Delivery run") },
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = supplierOrderStatusTitle(lane.bundles.maxByOrNull { it.order.supplierDeskSortTime() }?.order?.status ?: SupplierOrderStatusDataModel.Sent),
                color = if (lane.issueCount > 0) stateValues.ErrorColor else stateValues.AccentColor,
                fontSize = stateValues.smallTextSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End
            )
        }

        Text(
            text = lane.goodsPreview,
            color = stateValues.TextColor,
            fontSize = stateValues.textSize,
            fontWeight = FontWeight.Bold,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )

        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            StockCardInfoLine(localizedStringResource(1563, "Orders in lane"), lane.orderCount.toString(), stateValues.TextColor)
            StockCardInfoLine(localizedStringResource(1564, "Goods lines"), lane.lineCount.toString(), stateValues.TextColor)
            lane.nextDeliveryMillis?.toStockDateInputText()?.takeIf { it.isNotBlank() }?.let { dateText ->
                StockCardInfoLine(localizedStringResource(1565, "Next delivery"), dateText, stateValues.TextColor)
            }
            StockCardInfoLine(localizedStringResource(1561, "Needs confirmation"), lane.needsConfirmationCount.toString(), if (lane.needsConfirmationCount > 0) stateValues.AccentColor else stateValues.PlaceholderTextColor)
            StockCardInfoLine(localizedStringResource(1562, "Contract check"), "${lane.activeContractCount} / ${lane.pendingContractCount}", if (lane.pendingContractCount > 0) stateValues.ErrorColor else stateValues.TextColor)
            StockCardInfoLine(localizedStringResource(1463, "Last activity"), receiptUiDateTime(lane.lastActivityMillis), stateValues.PlaceholderTextColor)
        }

        if (stateValues.isNarrowScreen) {
            Column(verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)) {
                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(1568, "Open run orders"),
                    iconPath = stateValues.drawablePathIconAppModeSupplier,
                    iconRes = stateValues.drawableResIconAppModeSupplier.value,
                    confirmationRequired = false,
                    onClick = {
                        coroutineScope.launch {
                            seedSupplierOrdersInboxNavigation(searchQuery = lane.storeKey.ifBlank { lane.storeTitle })
                            Navigation.goMain(NavigationScreenModel.Supplier.Orders.Main)
                        }
                    }
                )
                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(1569, "Copy driver manifest"),
                    iconPath = stateValues.drawablePathIconClipboard,
                    iconRes = stateValues.drawableResIconClipboard.value,
                    confirmationRequired = false,
                    onClick = {
                        copyTextToClipboard(lane.manifest)
                        postInAppNotification(localizedStringResource(1573, "Driver manifest copied"), NotificationType.Positive, transient = true)
                    }
                )
                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(1570, "Mark packed"),
                    enabled = packableBundles.isNotEmpty(),
                    iconPath = stateValues.drawablePathIconSupplierDispatch,
                    iconRes = stateValues.drawableResIconSupplierDispatch.value,
                    confirmationRequired = true,
                    onDisabledClick = { postInAppNotification(localizedStringResource(1576, "No packable orders in this lane"), NotificationType.Neutral, transient = true) },
                    onClick = { updateSupplierOrdersSupplierStatus(packableBundles, SupplierOrderStatusDataModel.Packed) }
                )
                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(1571, "Start delivery"),
                    enabled = deliverableBundles.isNotEmpty(),
                    iconPath = stateValues.drawablePathIconSupplierDispatch,
                    iconRes = stateValues.drawableResIconSupplierDispatch.value,
                    confirmationRequired = true,
                    onDisabledClick = { postInAppNotification(localizedStringResource(1577, "Pack orders before starting delivery"), NotificationType.Neutral, transient = true) },
                    onClick = { updateSupplierOrdersSupplierStatus(deliverableBundles, SupplierOrderStatusDataModel.InDelivery) }
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
            ) {
                actionButton(
                    modifier = Modifier.weight(1f),
                    text = localizedStringResource(1568, "Open run orders"),
                    iconPath = stateValues.drawablePathIconAppModeSupplier,
                    iconRes = stateValues.drawableResIconAppModeSupplier.value,
                    confirmationRequired = false,
                    onClick = {
                        coroutineScope.launch {
                            seedSupplierOrdersInboxNavigation(searchQuery = lane.storeKey.ifBlank { lane.storeTitle })
                            Navigation.goMain(NavigationScreenModel.Supplier.Orders.Main)
                        }
                    }
                )
                actionButton(
                    modifier = Modifier.weight(1f),
                    text = localizedStringResource(1569, "Copy driver manifest"),
                    iconPath = stateValues.drawablePathIconClipboard,
                    iconRes = stateValues.drawableResIconClipboard.value,
                    confirmationRequired = false,
                    onClick = {
                        copyTextToClipboard(lane.manifest)
                        postInAppNotification(localizedStringResource(1573, "Driver manifest copied"), NotificationType.Positive, transient = true)
                    }
                )
                actionButton(
                    modifier = Modifier.weight(1f),
                    text = localizedStringResource(1570, "Mark packed"),
                    enabled = packableBundles.isNotEmpty(),
                    iconPath = stateValues.drawablePathIconSupplierDispatch,
                    iconRes = stateValues.drawableResIconSupplierDispatch.value,
                    confirmationRequired = true,
                    onDisabledClick = { postInAppNotification(localizedStringResource(1576, "No packable orders in this lane"), NotificationType.Neutral, transient = true) },
                    onClick = { updateSupplierOrdersSupplierStatus(packableBundles, SupplierOrderStatusDataModel.Packed) }
                )
                actionButton(
                    modifier = Modifier.weight(1f),
                    text = localizedStringResource(1571, "Start delivery"),
                    enabled = deliverableBundles.isNotEmpty(),
                    iconPath = stateValues.drawablePathIconSupplierDispatch,
                    iconRes = stateValues.drawableResIconSupplierDispatch.value,
                    confirmationRequired = true,
                    onDisabledClick = { postInAppNotification(localizedStringResource(1577, "Pack orders before starting delivery"), NotificationType.Neutral, transient = true) },
                    onClick = { updateSupplierOrdersSupplierStatus(deliverableBundles, SupplierOrderStatusDataModel.InDelivery) }
                )
            }
        }
    }
}


internal fun AppConfiguration.supplierDispatchRunTitle(run: SupplierDashboardDispatchRunDataModel): String =
    run.storeNameSnapshot.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { run.storePublicIdSnapshot }
        .ifBlank { run.storeAddressTextSnapshot }
        .ifBlank { run.storeId.take(8) }
        .ifBlank { localizedStringResource(1733, "Server dispatch run") }

internal fun AppConfiguration.supplierDispatchRunSuggestedActionTitle(action: String): String = when (action) {
    "issue" -> localizedStringResource(1742, "Resolve issue")
    "contract" -> localizedStringResource(1737, "Clear contract")
    "answer" -> localizedStringResource(1736, "Answer store")
    "pack" -> localizedStringResource(1738, "Pack goods")
    "dispatch" -> localizedStringResource(1739, "Send driver")
    "delivery" -> localizedStringResource(1740, "Track delivery")
    else -> localizedStringResource(1741, "Plan route")
}

internal fun AppConfiguration.supplierDispatchRunSearchText(run: SupplierDashboardDispatchRunDataModel): String = buildString {
    append(run.runId).append(' ')
    append(run.supplierId).append(' ')
    append(run.storeId).append(' ')
    append(supplierDispatchRunTitle(run)).append(' ')
    append(run.storePublicIdSnapshot).append(' ')
    append(run.storeAddressTextSnapshot).append(' ')
    append(run.goodsPreview.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(run.packChecklist.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(run.attentionSummary.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(run.driverHandoffChecklist.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(run.orderIds.joinToString(" ")).append(' ')
    append(run.packableOrderIds.joinToString(" ")).append(' ')
    append(run.dispatchableOrderIds.joinToString(" ")).append(' ')
    append(run.attentionOrderIds.joinToString(" ")).append(' ')
    append(run.contractBlockedOrderIds.joinToString(" ")).append(' ')
    append(run.statusMix.joinToString(" ") { bucket -> bucket.status.name + " " + supplierOrderStatusTitle(bucket.status) }).append(' ')
    append(supplierDispatchRunSuggestedActionTitle(run.suggestedAction))
}.lowercase()

internal fun SupplierDashboardDispatchRunDataModel.matchesSupplierDispatchFilter(laneFilter: String): Boolean = when (laneFilter) {
    "confirm" -> actionRequiredOrderCount > 0
    "packed" -> packedOrderCount > 0 || readyToPackOrderCount > 0
    "delivery" -> inDeliveryOrderCount > 0
    "issue" -> issueOrderCount > 0
    "contracts" -> pendingContractCount > 0
    else -> true
}

internal fun AppConfiguration.supplierDispatchRunManifest(run: SupplierDashboardDispatchRunDataModel): String = buildString {
    append(localizedStringResource(1733, "Server dispatch run")).append(" — ").append(supplierDispatchRunTitle(run)).append('\n')
    run.storePublicIdSnapshot.takeIf { it.isNotBlank() }?.let { publicId -> append("ID: ").append(publicId).append('\n') }
    run.storeAddressTextSnapshot.takeIf { it.isNotBlank() }?.let { address -> append(localizedStringResource(147, "Address")).append(": ").append(address).append('\n') }
    append(localizedStringResource(1735, "Suggested action")).append(": ").append(supplierDispatchRunSuggestedActionTitle(run.suggestedAction)).append('\n')
    append(localizedStringResource(1734, "Priority")).append(": ").append(run.priorityScore).append('\n')
    append(localizedStringResource(1563, "Orders in lane")).append(": ").append(run.orderCount).append('\n')
    append(localizedStringResource(1564, "Goods lines")).append(": ").append(run.lineCount).append('\n')
    run.estimatedAmount.supplierDeskMoneyText().takeIf { it.isNotBlank() }?.let { amount ->
        append(localizedStringResource(581, "Amount")).append(": ").append(amount).append('\n')
    }
    run.earliestDueAtMillis?.takeIf { it > 0L }?.let { due -> append(localizedStringResource(1723, "Earliest due")).append(": ").append(receiptUiDateTime(due)).append('\n') }
    run.latestDueAtMillis?.takeIf { it > 0L && it != run.earliestDueAtMillis }?.let { due -> append(localizedStringResource(1565, "Next delivery")).append(": ").append(receiptUiDateTime(due)).append('\n') }
    append(localizedStringResource(1562, "Contract check")).append(": ").append(run.activeContractCount).append(" / ").append(run.pendingContractCount).append('\n')
    if (run.contractBlockedOrderIds.isNotEmpty()) {
        append(localizedStringResource(1791, "Contract-blocked orders")).append(": ").append(run.contractBlockedOrderIds.size).append('\n')
        append(localizedStringResource(1793, "Pack and dispatch buttons use only server-safe, unblocked orders.")).append('\n')
    }
    run.statusMix.takeIf { it.isNotEmpty() }?.let { mix ->
        append(localizedStringResource(200, "Status")).append(": ")
        append(mix.joinToString(" • ") { bucket -> "${supplierOrderStatusTitle(bucket.status)} ${bucket.orderCount}" })
        append('\n')
    }
    run.goodsPreview.visibleLocalizedString(stateValues.appLanguage, "").takeIf { it.isNotBlank() }?.let { goods ->
        append(localizedStringResource(1564, "Goods lines")).append(": ").append(goods).append('\n')
    }
    run.packChecklist.visibleLocalizedString(stateValues.appLanguage, "").takeIf { it.isNotBlank() }?.let { checklist ->
        append(localizedStringResource(1746, "Pack checklist")).append(":\n").append(checklist).append('\n')
    }
    run.attentionSummary.visibleLocalizedString(stateValues.appLanguage, "").takeIf { it.isNotBlank() }?.let { attention ->
        append(localizedStringResource(1756, "Attention notes")).append(":\n").append(attention).append('\n')
    }
    run.driverHandoffChecklist.visibleLocalizedString(stateValues.appLanguage, "").takeIf { it.isNotBlank() }?.let { handoff ->
        append(localizedStringResource(1757, "Driver handoff")).append(":\n").append(handoff).append('\n')
    }
    if (run.orderIds.isNotEmpty()) {
        append(localizedStringResource(1369, "Open orders")).append(": ").append(run.orderIds.joinToString(", ") { it.take(12) })
    }
}

@Composable
internal fun AppConfiguration.SupplierServerDispatchRunCard(
    run: SupplierDashboardDispatchRunDataModel,
    localBundlesByOrderId: Map<String, SupplierOrderWithLinesDataModel> = emptyMap()
) {
    val coroutineScope = rememberCoroutineScope()
    val title = supplierDispatchRunTitle(run)
    val goodsPreview = run.goodsPreview.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { localizedStringResource(1566, "No goods lines yet") }
    val packChecklistText = run.packChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { run.packChecklist.visibleLocalizedString("main", "") }
    val attentionText = run.attentionSummary.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { run.attentionSummary.visibleLocalizedString("main", "") }
    val handoffText = run.driverHandoffChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { run.driverHandoffChecklist.visibleLocalizedString("main", "") }
    val actionTitle = supplierDispatchRunSuggestedActionTitle(run.suggestedAction)
    val amountText = run.estimatedAmount.supplierDeskMoneyText()
    val statusSummary = run.statusMix
        .take(4)
        .joinToString(" • ") { bucket -> "${supplierOrderStatusTitle(bucket.status)} ${bucket.orderCount}" }
    val expectedLocalOrderCount = run.orderIds.filter { it.isNotBlank() }.distinct().size
    val loadedLocalOrderCount = run.orderIds
        .filter { it.isNotBlank() }
        .distinct()
        .count { orderId -> localBundlesByOrderId[orderId] != null }
    val packableOrderIds = remember(run.packableOrderIds) {
        run.packableOrderIds
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }
    val dispatchableOrderIds = remember(run.dispatchableOrderIds) {
        run.dispatchableOrderIds
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }
    val contractBlockedOrderIds = remember(run.contractBlockedOrderIds) {
        run.contractBlockedOrderIds
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }
    val borderColor = when {
        run.issueOrderCount > 0 -> stateValues.ErrorColor
        run.pendingContractCount > 0 || run.actionRequiredOrderCount > 0 -> stateValues.AccentColor
        else -> stateValues.PlaceholderTextColor
    }
    val localLoadTextColor = if (expectedLocalOrderCount > 0 && loadedLocalOrderCount < expectedLocalOrderCount) {
        stateValues.AccentColor
    } else {
        stateValues.PlaceholderTextColor
    }

    fun openRunOrders() {
        coroutineScope.launch {
            seedSupplierOrdersInboxNavigation(
                searchQuery = run.storeId
                    .ifBlank { run.storePublicIdSnapshot }
                    .ifBlank { title }
            )
            Navigation.goMain(NavigationScreenModel.Supplier.Orders.Main)
        }
    }

    fun openRunContracts() {
        coroutineScope.launch {
            Navigation.goMain(NavigationScreenModel.Supplier.Contracts.Main)
        }
    }

    fun copyManifest() {
        copyTextToClipboard(supplierDispatchRunManifest(run))
        postInAppNotification(localizedStringResource(1743, "Server manifest copied"), NotificationType.Positive, transient = true)
    }

    @Composable
    fun MarkPackedButton(modifier: Modifier, textSize: TextUnit) {
        actionButton(
            modifier = modifier,
            text = localizedStringResource(1570, "Mark packed"),
            enabled = packableOrderIds.isNotEmpty(),
            iconPath = stateValues.drawablePathIconSupplierDispatch,
            iconRes = stateValues.drawableResIconSupplierDispatch.value,
            textSize = textSize,
            confirmationRequired = true,
            onDisabledClick = { postInAppNotification(localizedStringResource(1748, "No server-ready orders in this run"), NotificationType.Neutral, transient = true) },
            onClick = { updateSupplierOrdersSupplierStatusByIds(packableOrderIds, SupplierOrderStatusDataModel.Packed) }
        )
    }

    @Composable
    fun StartDeliveryButton(modifier: Modifier, textSize: TextUnit) {
        actionButton(
            modifier = modifier,
            text = localizedStringResource(1571, "Start delivery"),
            enabled = dispatchableOrderIds.isNotEmpty(),
            iconPath = stateValues.drawablePathIconSupplierDispatch,
            iconRes = stateValues.drawableResIconSupplierDispatch.value,
            textSize = textSize,
            confirmationRequired = true,
            onDisabledClick = { postInAppNotification(localizedStringResource(1749, "No packed server orders in this run"), NotificationType.Neutral, transient = true) },
            onClick = { updateSupplierOrdersSupplierStatusByIds(dispatchableOrderIds, SupplierOrderStatusDataModel.InDelivery) }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(
                if (run.issueOrderCount > 0 || run.pendingContractCount > 0 || run.actionRequiredOrderCount > 0) stateValues.focusedBorderWidth else stateValues.unfocusedBorderWidth,
                borderColor,
                RoundedCornerShape(stateValues.cornerRadius)
            )
            .padding(stateValues.marginTextFieldGroup),
        verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(stateValues.cornerRadius))
                    .background(stateValues.AccentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                CpImage(
                    modifier = Modifier.size(28.dp),
                    url = stateValues.drawablePathIconSupplierDispatch,
                    fallbackRes = stateValues.drawableResIconSupplierDispatch.value,
                    contentDescription = localizedStringResource(1733, "Server dispatch run"),
                    tintColor = stateValues.AccentColor
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = stateValues.TextColor,
                    fontSize = stateValues.titleTextSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOf(run.storePublicIdSnapshot, run.storeAddressTextSnapshot).filter { it.isNotBlank() }.joinToString(" • ").ifBlank { run.storeId.take(8) },
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = actionTitle,
                color = if (run.issueOrderCount > 0) stateValues.ErrorColor else stateValues.AccentColor,
                fontSize = stateValues.smallTextSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = goodsPreview,
            color = stateValues.TextColor,
            fontSize = stateValues.textSize,
            fontWeight = FontWeight.Bold,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )

        if (packChecklistText.isNotBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(stateValues.cornerRadius))
                    .background(stateValues.AccentColor.copy(alpha = 0.06f))
                    .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor.copy(alpha = 0.35f), RoundedCornerShape(stateValues.cornerRadius))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = localizedStringResource(1746, "Pack checklist"),
                    color = stateValues.AccentColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = packChecklistText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (attentionText.isNotBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(stateValues.cornerRadius))
                    .background(if (run.issueOrderCount > 0 || run.pendingContractCount > 0) stateValues.ErrorColor.copy(alpha = 0.07f) else stateValues.AccentColor.copy(alpha = 0.05f))
                    .border(stateValues.unfocusedBorderWidth, if (run.issueOrderCount > 0 || run.pendingContractCount > 0) stateValues.ErrorColor.copy(alpha = 0.42f) else stateValues.AccentColor.copy(alpha = 0.28f), RoundedCornerShape(stateValues.cornerRadius))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = localizedStringResource(1756, "Attention notes"),
                    color = if (run.issueOrderCount > 0 || run.pendingContractCount > 0) stateValues.ErrorColor else stateValues.AccentColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = attentionText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 7,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (handoffText.isNotBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(stateValues.cornerRadius))
                    .background(stateValues.BackgroundColor.copy(alpha = 0.86f))
                    .border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor.copy(alpha = 0.45f), RoundedCornerShape(stateValues.cornerRadius))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = localizedStringResource(1757, "Driver handoff"),
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = handoffText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 10,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (contractBlockedOrderIds.isNotEmpty()) {
            Text(
                text = localizedStringResource(1792, "Clear contracts before packing or dispatching this run."),
                color = stateValues.ErrorColor,
                fontSize = stateValues.smallTextSize,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = localizedStringResource(1793, "Pack and dispatch buttons use only server-safe, unblocked orders."),
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize
            )
        }

        if (stateValues.isNarrowScreen) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SupplierCatalogChip(text = "${localizedStringResource(1735, "Suggested action")}: $actionTitle")
                SupplierCatalogChip(text = "${localizedStringResource(1734, "Priority")}: ${run.priorityScore}")
                SupplierCatalogChip(text = "${localizedStringResource(1732, "Driver-ready")}: ${packableOrderIds.size + dispatchableOrderIds.size}")
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) { SupplierCatalogChip(text = "${localizedStringResource(1735, "Suggested action")}: $actionTitle") }
                Box(modifier = Modifier.weight(1f)) { SupplierCatalogChip(text = "${localizedStringResource(1734, "Priority")}: ${run.priorityScore}") }
                Box(modifier = Modifier.weight(1f)) { SupplierCatalogChip(text = "${localizedStringResource(1732, "Driver-ready")}: ${packableOrderIds.size + dispatchableOrderIds.size}") }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            StockCardInfoLine(localizedStringResource(1563, "Orders in lane"), run.orderCount.toString(), stateValues.TextColor)
            if (expectedLocalOrderCount > 0) {
                StockCardInfoLine(localizedStringResource(1369, "Open orders"), "$loadedLocalOrderCount / $expectedLocalOrderCount", localLoadTextColor)
            }
            StockCardInfoLine(localizedStringResource(1564, "Goods lines"), run.lineCount.toString(), stateValues.TextColor)
            amountText.takeIf { it.isNotBlank() }?.let { StockCardInfoLine(localizedStringResource(581, "Amount"), it, stateValues.TextColor) }
            run.earliestDueAtMillis?.takeIf { it > 0L }?.let { due -> StockCardInfoLine(localizedStringResource(1723, "Earliest due"), receiptUiDateTime(due), stateValues.TextColor) }
            StockCardInfoLine(localizedStringResource(1561, "Needs confirmation"), run.actionRequiredOrderCount.toString(), if (run.actionRequiredOrderCount > 0) stateValues.AccentColor else stateValues.PlaceholderTextColor)
            StockCardInfoLine(localizedStringResource(1562, "Contract check"), "${run.activeContractCount} / ${run.pendingContractCount}", if (run.pendingContractCount > 0) stateValues.ErrorColor else stateValues.TextColor)
            if (contractBlockedOrderIds.isNotEmpty()) {
                StockCardInfoLine(localizedStringResource(1791, "Contract-blocked orders"), contractBlockedOrderIds.size.toString(), stateValues.ErrorColor)
            }
            statusSummary.takeIf { it.isNotBlank() }?.let { StockCardInfoLine(localizedStringResource(200, "Status"), it, stateValues.PlaceholderTextColor) }
            run.latestActivityMillis.takeIf { it > 0L }?.let { StockCardInfoLine(localizedStringResource(1463, "Last activity"), receiptUiDateTime(it), stateValues.PlaceholderTextColor) }
        }

        Text(
            text = localizedStringResource(1747, "Server run actions"),
            color = stateValues.TextColor,
            fontSize = stateValues.smallTextSize,
            fontWeight = FontWeight.Bold
        )

        if (contractBlockedOrderIds.isNotEmpty()) {
            actionButton(
                modifier = Modifier.fillMaxWidth(),
                text = localizedStringResource(1737, "Clear contract"),
                iconPath = stateValues.drawablePathIconSupplierContracts,
                iconRes = stateValues.drawableResIconSupplierContracts.value,
                textSize = stateValues.smallTextSize,
                confirmationRequired = false,
                onClick = { openRunContracts() }
            )
        }

        if (stateValues.isNarrowScreen) {
            Column(verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)) {
                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(1568, "Open run orders"),
                    iconPath = stateValues.drawablePathIconAppModeSupplier,
                    iconRes = stateValues.drawableResIconAppModeSupplier.value,
                    confirmationRequired = false,
                    onClick = { openRunOrders() }
                )
                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(1569, "Copy driver manifest"),
                    iconPath = stateValues.drawablePathIconClipboard,
                    iconRes = stateValues.drawableResIconClipboard.value,
                    confirmationRequired = false,
                    onClick = { copyManifest() }
                )
                MarkPackedButton(Modifier.fillMaxWidth(), stateValues.textSize)
                StartDeliveryButton(Modifier.fillMaxWidth(), stateValues.textSize)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                ) {
                    actionButton(
                        modifier = Modifier.weight(1f),
                        text = localizedStringResource(1568, "Open run orders"),
                        iconPath = stateValues.drawablePathIconAppModeSupplier,
                        iconRes = stateValues.drawableResIconAppModeSupplier.value,
                        textSize = stateValues.smallTextSize,
                        confirmationRequired = false,
                        onClick = { openRunOrders() }
                    )
                    actionButton(
                        modifier = Modifier.weight(1f),
                        text = localizedStringResource(1569, "Copy driver manifest"),
                        iconPath = stateValues.drawablePathIconClipboard,
                        iconRes = stateValues.drawableResIconClipboard.value,
                        textSize = stateValues.smallTextSize,
                        confirmationRequired = false,
                        onClick = { copyManifest() }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                ) {
                    MarkPackedButton(Modifier.weight(1f), stateValues.smallTextSize)
                    StartDeliveryButton(Modifier.weight(1f), stateValues.smallTextSize)
                }
            }
        }
    }
}

@Composable
internal fun AppConfiguration.SupplierDispatchScreen() {
    val orders by supplierOrdersState.payload.collectAsState()
    val lines by supplierOrderLinesState.payload.collectAsState()
    val contracts by supplierPartnershipContractsState.payload.collectAsState()
    val supplierDashboard by supplierModeDashboardState.payload.collectAsState()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var laneFilter by rememberSaveable { mutableStateOf("all") }

    LaunchedEffect(stateValues.userAccount?.id) {
        if (stateValues.userAccount != null) {
            refreshSupplierModeWorkspace(includeContracts = true)
        }
    }

    val lanes = remember(orders, lines, contracts, stateValues.appLanguage) {
        buildSupplierDispatchLanes(
            orders = orders.orEmpty(),
            lines = lines.orEmpty(),
            contracts = contracts.orEmpty()
        )
    }
    val serverDispatchRuns = remember(supplierDashboard) { supplierDashboard?.dispatchRuns.orEmpty() }
    val dispatchAttentionActions = remember(supplierDashboard) {
        supplierDashboard?.actionQueue.orEmpty()
            .filter { action -> action.actionType in setOf("issue", "answer", "complete_response", "terms", "pack", "dispatch") }
            .take(6)
    }
    val localSupplierOrderBundlesById = remember(orders, lines) {
        val activeLinesByOrder = lines.orEmpty().filter { it.isActive }.groupBy { it.orderId }
        orders.orEmpty()
            .filter { order -> order.isActive && order.id.isNotBlank() }
            .associate { order ->
                order.id to SupplierOrderWithLinesDataModel(
                    order = order,
                    lines = activeLinesByOrder[order.id].orEmpty()
                )
            }
    }
    val normalizedSearch = searchQuery.trim().lowercase()
    val visibleLanes = remember(lanes, normalizedSearch, laneFilter) {
        lanes.filter { lane ->
            val filterMatches = when (laneFilter) {
                "confirm" -> lane.needsConfirmationCount > 0
                "packed" -> lane.packedCount > 0
                "delivery" -> lane.inDeliveryCount > 0
                "issue" -> lane.issueCount > 0
                "contracts" -> lane.pendingContractCount > 0
                else -> true
            }
            val queryMatches = normalizedSearch.isBlank() || lane.searchKey.contains(normalizedSearch)
            filterMatches && queryMatches
        }
    }
    val visibleServerDispatchRuns = remember(serverDispatchRuns, normalizedSearch, laneFilter, stateValues.appLanguage) {
        serverDispatchRuns.filter { run ->
            run.matchesSupplierDispatchFilter(laneFilter) &&
                    (normalizedSearch.isBlank() || supplierDispatchRunSearchText(run).contains(normalizedSearch))
        }
    }
    val readyCount = lanes.count { it.packedCount > 0 }
    val inDeliveryCount = lanes.count { it.inDeliveryCount > 0 }
    val attentionCount = lanes.count { it.needsConfirmationCount > 0 || it.issueCount > 0 || it.pendingContractCount > 0 }
    val serverRunCount = serverDispatchRuns.size
    val serverDriverReadyCount = serverDispatchRuns.count { it.readyToPackOrderCount > 0 || it.packedOrderCount > 0 }
    val featurePlan = supplierMarketWinningFeatures()

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenAppBarWidget(
            title = localizedStringResource(1557, "Delivery runs"),
            iconPath = stateValues.drawablePathIconSupplierDispatch,
            iconRes = stateValues.drawableResIconSupplierDispatch.value
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.72f)
                .align(Alignment.CenterHorizontally)
                .padding(stateValues.marginTextField),
            verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField),
            contentPadding = PaddingValues(bottom = stateValues.screenHeight / 5)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(stateValues.cornerRadius))
                        .background(stateValues.AccentColor.copy(alpha = 0.11f))
                        .border(stateValues.focusedBorderWidth, stateValues.AccentColor, RoundedCornerShape(stateValues.cornerRadius))
                        .padding(stateValues.marginTextFieldGroup),
                    verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                    ) {
                        CpImage(
                            modifier = Modifier.size(42.dp),
                            url = stateValues.drawablePathIconSupplierDispatch,
                            fallbackRes = stateValues.drawableResIconSupplierDispatch.value,
                            contentDescription = localizedStringResource(1557, "Delivery runs"),
                            tintColor = stateValues.AccentColor
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = localizedStringResource(1578, "Dispatch rhythm planner"),
                                color = stateValues.TextColor,
                                fontSize = stateValues.titleTextSize,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = localizedStringResource(1558, "Group open store orders into practical packing and driver lanes, with contract blockers visible before goods leave the supplier."),
                                color = stateValues.PlaceholderTextColor,
                                fontSize = stateValues.textSize
                            )
                        }
                    }

                    if (stateValues.isNarrowScreen) {
                        Column(verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)) {
                            SupplierDeskSummaryCard(
                                title = localizedStringResource(1559, "Ready lanes"),
                                value = readyCount.toString(),
                                subtitle = localizedStringResource(1579, "Stores with packed goods"),
                                iconPath = stateValues.drawablePathIconSupplierDispatch,
                                iconRes = stateValues.drawableResIconSupplierDispatch.value
                            )
                            SupplierDeskSummaryCard(
                                title = localizedStringResource(1560, "In delivery"),
                                value = inDeliveryCount.toString(),
                                subtitle = localizedStringResource(1580, "Runs already on the road"),
                                iconPath = stateValues.drawablePathIconSupplierDispatch,
                                iconRes = stateValues.drawableResIconSupplierDispatch.value
                            )
                            SupplierDeskSummaryCard(
                                title = localizedStringResource(1535, "Issue watch"),
                                value = attentionCount.toString(),
                                subtitle = localizedStringResource(1581, "Confirmations, issues or contracts to clear"),
                                iconPath = stateValues.drawablePathIconSupplierContracts,
                                iconRes = stateValues.drawableResIconSupplierContracts.value
                            )
                            SupplierDeskSummaryCard(
                                title = localizedStringResource(1730, "Planned runs"),
                                value = serverRunCount.toString(),
                                subtitle = localizedStringResource(1731, "Runs from supplier dashboard"),
                                iconPath = stateValues.drawablePathIconSupplierDispatch,
                                iconRes = stateValues.drawableResIconSupplierDispatch.value
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                        ) {
                            SupplierDeskSummaryCard(
                                modifier = Modifier.weight(1f),
                                title = localizedStringResource(1559, "Ready lanes"),
                                value = readyCount.toString(),
                                subtitle = localizedStringResource(1579, "Stores with packed goods"),
                                iconPath = stateValues.drawablePathIconSupplierDispatch,
                                iconRes = stateValues.drawableResIconSupplierDispatch.value
                            )
                            SupplierDeskSummaryCard(
                                modifier = Modifier.weight(1f),
                                title = localizedStringResource(1560, "In delivery"),
                                value = inDeliveryCount.toString(),
                                subtitle = localizedStringResource(1580, "Runs already on the road"),
                                iconPath = stateValues.drawablePathIconSupplierDispatch,
                                iconRes = stateValues.drawableResIconSupplierDispatch.value
                            )
                            SupplierDeskSummaryCard(
                                modifier = Modifier.weight(1f),
                                title = localizedStringResource(1535, "Issue watch"),
                                value = attentionCount.toString(),
                                subtitle = localizedStringResource(1581, "Confirmations, issues or contracts to clear"),
                                iconPath = stateValues.drawablePathIconSupplierContracts,
                                iconRes = stateValues.drawableResIconSupplierContracts.value
                            )
                            SupplierDeskSummaryCard(
                                modifier = Modifier.weight(1f),
                                title = localizedStringResource(1730, "Planned runs"),
                                value = serverRunCount.toString(),
                                subtitle = "${localizedStringResource(1732, "Driver-ready")}: $serverDriverReadyCount",
                                iconPath = stateValues.drawablePathIconSupplierDispatch,
                                iconRes = stateValues.drawableResIconSupplierDispatch.value
                            )
                        }
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
                        .clip(RoundedCornerShape(stateValues.cornerRadius))
                        .background(stateValues.BackgroundColor)
                        .border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor, RoundedCornerShape(stateValues.cornerRadius))
                        .padding(stateValues.marginTextFieldGroup),
                    verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                ) {
                    Text(
                        text = localizedStringResource(1582, "Dispatch filter"),
                        color = stateValues.TextColor,
                        fontSize = stateValues.titleTextSize,
                        fontWeight = FontWeight.Bold
                    )
                    SimpleTextInput(
                        modifier = Modifier.fillMaxWidth(),
                        value = searchQuery,
                        placeholder = localizedStringResource(216, "Search"),
                        leadingIconPath = stateValues.drawablePathIconSearch,
                        onValueChange = { searchQuery = it }
                    )
                    SimpleDropdownField(
                        title = localizedStringResource(1582, "Dispatch filter"),
                        selectedId = laneFilter,
                        options = listOf(
                            DropdownOption("all", localizedStringResource(1378, "All")),
                            DropdownOption("confirm", localizedStringResource(1561, "Needs confirmation")),
                            DropdownOption("packed", localizedStringResource(1559, "Ready lanes")),
                            DropdownOption("delivery", localizedStringResource(1560, "In delivery")),
                            DropdownOption("issue", localizedStringResource(1535, "Issue watch")),
                            DropdownOption("contracts", localizedStringResource(1537, "Contract blockers"))
                        ),
                        placeholder = localizedStringResource(1378, "All"),
                        onSelected = { laneFilter = it }
                    )
                }
            }

            if (dispatchAttentionActions.isNotEmpty()) {
                item {
                    SupplierActionQueueCard(
                        actions = dispatchAttentionActions,
                        bulkSeenOrderIds = supplierDashboard?.bulkSeenOrderIds.orEmpty(),
                        bulkPackableOrderIds = supplierDashboard?.bulkPackableOrderIds.orEmpty(),
                        bulkDispatchableOrderIds = supplierDashboard?.bulkDispatchableOrderIds.orEmpty()
                    )
                }
            }

            if (serverDispatchRuns.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
                            .clip(RoundedCornerShape(stateValues.cornerRadius))
                            .background(stateValues.AccentColor.copy(alpha = 0.07f))
                            .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor, RoundedCornerShape(stateValues.cornerRadius))
                            .padding(stateValues.marginTextFieldGroup),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = localizedStringResource(1728, "Server dispatch board"),
                            color = stateValues.TextColor,
                            fontSize = stateValues.titleTextSize,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = localizedStringResource(1729, "Backend groups supplier runs by store, promised date and current fulfilment pressure, so desktop and mobile screens see the same delivery priorities."),
                            color = stateValues.PlaceholderTextColor,
                            fontSize = stateValues.smallTextSize
                        )
                    }
                }
                if (visibleServerDispatchRuns.isEmpty()) {
                    item {
                        MessageText(
                            modifier = Modifier.fillMaxWidth(),
                            text = localizedStringResource(1380, "No orders match this filter")
                        )
                    }
                } else {
                    items(
                        visibleServerDispatchRuns,
                        key = { run -> run.runId.ifBlank { run.storeId.ifBlank { run.orderIds.joinToString("-") } } }
                    ) { run ->
                        SupplierServerDispatchRunCard(run, localSupplierOrderBundlesById)
                    }
                }
            }

            item {
                Text(
                    text = localizedStringResource(1557, "Delivery runs"),
                    color = stateValues.TextColor,
                    fontSize = stateValues.titleTextSize,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (lanes.isEmpty()) {
                item {
                    MessageText(
                        modifier = Modifier.fillMaxWidth(),
                        text = localizedStringResource(1566, "No dispatch lanes yet"),
                        subText = localizedStringResource(1567, "Confirm or pack supplier orders and they will appear here as store delivery lanes."),
                        subTextSize = stateValues.smallTextSize
                    )
                }
            } else if (visibleLanes.isEmpty()) {
                item {
                    MessageText(
                        modifier = Modifier.fillMaxWidth(),
                        text = localizedStringResource(1380, "No orders match this filter")
                    )
                }
            } else {
                items(visibleLanes, key = { it.laneKey }) { lane ->
                    SupplierDispatchLaneCard(lane)
                }
            }

            item {
                Text(
                    text = localizedStringResource(1381, "Supplier feature roadmap"),
                    color = stateValues.TextColor,
                    fontSize = stateValues.titleTextSize,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            items(featurePlan.filterNot { it.title == localizedStringResource(1347, "Route batch planner") }) { feature ->
                SupplierFeaturePlanCard(feature = feature, compact = true)
            }
        }
    }
}

@Composable
internal fun AppConfiguration.SupplierPlaceholderScreen(
    title: String,
    subtitle: String,
    iconPath: String,
    iconRes: DrawableResource?
) {
    Column(modifier = Modifier.fillMaxSize()) {
        ScreenAppBarWidget(
            title = title,
            iconPath = iconPath,
            iconRes = iconRes
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.68f)
                .align(Alignment.CenterHorizontally)
                .padding(stateValues.marginTextField),
            verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField),
            contentPadding = PaddingValues(bottom = stateValues.screenHeight / 5)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
                        .clip(RoundedCornerShape(stateValues.cornerRadius))
                        .background(stateValues.BackgroundColor)
                        .border(stateValues.focusedBorderWidth, stateValues.AccentColor, RoundedCornerShape(stateValues.cornerRadius))
                        .padding(stateValues.marginTextFieldGroup),
                    verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CpImage(
                        modifier = Modifier.size(54.dp),
                        url = iconPath,
                        fallbackRes = iconRes,
                        contentDescription = title,
                        tintColor = stateValues.AccentColor
                    )
                    Text(
                        text = title,
                        color = stateValues.TextColor,
                        fontSize = stateValues.titleTextSize,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = subtitle,
                        color = stateValues.PlaceholderTextColor,
                        fontSize = stateValues.textSize,
                        textAlign = TextAlign.Center
                    )
                }
            }

            items(supplierMarketWinningFeatures().filterNot { it.implemented }) { feature ->
                SupplierFeaturePlanCard(feature = feature, compact = false)
            }
        }
    }
}


internal data class SupplierDemandRadarUiModel(
    val goodsItemId: String,
    val title: String,
    val subtitle: String,
    val totalQuantityText: String,
    val storesCount: Int,
    val openOrderCount: Int,
    val issueCount: Int,
    val latestStatus: SupplierOrderStatusDataModel,
    val lastActivityMillis: Long,
    val latestPriceText: String,
    val demandScore: Int,
    val searchKey: String
)

internal fun AppConfiguration.buildSupplierDemandRadarItems(
    orders: List<SupplierOrderDataModel>,
    lines: List<SupplierOrderLineDataModel>
): List<SupplierDemandRadarUiModel> {
    val activeOrders = orders.filter { it.isActive }
    val ordersById = activeOrders.associateBy { it.id }
    return lines
        .filter { it.isActive && it.goodsItemId.isNotBlank() }
        .groupBy { it.goodsItemId }
        .mapNotNull { (goodsItemId, itemLines) ->
            val relatedOrders = itemLines
                .mapNotNull { ordersById[it.orderId] }
                .distinctBy { it.id }
            if (relatedOrders.isEmpty()) return@mapNotNull null

            val latestOrder = relatedOrders.maxByOrNull { it.supplierDeskSortTime() } ?: return@mapNotNull null
            val sampleLine = itemLines.maxByOrNull { line -> ordersById[line.orderId]?.supplierDeskSortTime() ?: 0L } ?: return@mapNotNull null
            val openOrders = relatedOrders.count { !it.status.isSupplierOrderClosed() }
            val issueCount = relatedOrders.count { it.status == SupplierOrderStatusDataModel.IssueReported || it.status == SupplierOrderStatusDataModel.Cancelled }
            val totalQuantity = itemLines.sumOf { it.requestedQuantity.total.coerceAtLeast(0.0) }
            val baseQuantity = sampleLine.requestedQuantity
            val totalQuantityText = if (totalQuantity > 0.0) {
                baseQuantity.copy(total = totalQuantity).quantityText(stateValues.appLanguage)
            } else {
                itemLines.size.toString()
            }
            val stores = relatedOrders.map { supplierDeskStoreTitle(it) }.filter { it.isNotBlank() }.distinct()
            val latestPrice = itemLines.asSequence()
                .mapNotNull { it.supplierOfferedSupplyPrice ?: it.expectedSupplyPrice }
                .firstOrNull()
                .supplierDeskMoneyText()
            val title = supplierDeskLineTitle(sampleLine)
            val demandScore = openOrders * 3 + issueCount * 2 + stores.size + itemLines.size

            SupplierDemandRadarUiModel(
                goodsItemId = goodsItemId,
                title = title,
                subtitle = stores.take(3).joinToString(" • ").ifBlank { goodsItemId.take(8) },
                totalQuantityText = totalQuantityText,
                storesCount = stores.size,
                openOrderCount = openOrders,
                issueCount = issueCount,
                latestStatus = latestOrder.status,
                lastActivityMillis = latestOrder.supplierDeskSortTime(),
                latestPriceText = latestPrice,
                demandScore = demandScore,
                searchKey = buildString {
                    append(goodsItemId).append(' ')
                    append(title).append(' ')
                    append(stores.joinToString(" ")).append(' ')
                    append(sampleLine.goodsItemBarcodeSnapshots.joinToString(" ")).append(' ')
                    append(latestOrder.status.name).append(' ')
                    append(supplierOrderStatusTitle(latestOrder.status))
                }.lowercase()
            )
        }
        .sortedWith(compareByDescending<SupplierDemandRadarUiModel> { it.demandScore }
            .thenByDescending { it.lastActivityMillis })
}

@Composable
internal fun AppConfiguration.SupplierDemandRadarCard(item: SupplierDemandRadarUiModel) {
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(
                if (item.openOrderCount > 0) stateValues.focusedBorderWidth else stateValues.unfocusedBorderWidth,
                if (item.openOrderCount > 0) stateValues.AccentColor else stateValues.PlaceholderTextColor,
                RoundedCornerShape(stateValues.cornerRadius)
            )
            .padding(stateValues.marginTextFieldGroup),
        verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(stateValues.cornerRadius))
                    .background(stateValues.AccentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                CpImage(
                    modifier = Modifier.size(30.dp),
                    url = stateValues.drawablePathIconSupplierDemandRadar,
                    fallbackRes = stateValues.drawableResIconSupplierDemandRadar.value,
                    contentDescription = item.title,
                    tintColor = stateValues.AccentColor
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = stateValues.TextColor,
                    fontSize = stateValues.accentTextSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.subtitle,
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = item.latestPriceText.ifBlank { item.totalQuantityText },
                color = stateValues.AccentColor,
                fontSize = stateValues.accentTextSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        StockCardInfoLine(localizedStringResource(1542, "Prepare stock"), item.totalQuantityText, stateValues.TextColor)
        StockCardInfoLine(localizedStringResource(1422, "Stores asking"), item.storesCount.toString(), stateValues.TextColor)
        StockCardInfoLine(localizedStringResource(1423, "Open requests"), item.openOrderCount.toString(), stateValues.TextColor)
        StockCardInfoLine(localizedStringResource(1545, "Latest activity"), receiptUiDateTime(item.lastActivityMillis), stateValues.TextColor)
        StockCardInfoLine(localizedStringResource(1552, "Demand score"), item.demandScore.toString(), stateValues.TextColor)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                SupplierCatalogChip(text = supplierOrderStatusTitle(item.latestStatus))
            }
            if (item.issueCount > 0) {
                Box(modifier = Modifier.weight(1f)) {
                    SupplierCatalogChip(text = "${localizedStringResource(1473, "Issues")}: ${item.issueCount}")
                }
            } else {
                Box(modifier = Modifier.weight(1f)) {
                    SupplierCatalogChip(text = localizedStringResource(1469, "Healthy rhythm"))
                }
            }
        }

        actionButton(
            modifier = Modifier.fillMaxWidth(),
            text = localizedStringResource(1546, "Open matching orders"),
            iconPath = stateValues.drawablePathIconAppModeSupplier,
            iconRes = stateValues.drawableResIconAppModeSupplier.value,
            confirmationRequired = false,
            onClick = {
                coroutineScope.launch {
                    seedSupplierOrdersInboxNavigation(searchQuery = item.goodsItemId)
                    Navigation.goMain(NavigationScreenModel.Supplier.Orders.Main)
                }
            }
        )
    }
}


internal data class SupplierTermsGuardUiModel(
    val contract: SupplierPartnershipContractDataModel,
    val title: String,
    val subtitle: String,
    val statusText: String,
    val effectText: String,
    val affectedOpenOrders: Int,
    val affectedLineCount: Int,
    val coveredGoodsText: String,
    val terms: List<String>,
    val priceTermsText: String,
    val searchKey: String,
    val orderSearchQuery: String,
    val brief: String,
    val statusColor: Color
)

internal fun AppConfiguration.supplierTermsGuardEffectText(contract: SupplierPartnershipContractDataModel): String = when (contract.status) {
    SUPPLIER_CONTRACT_STATUS_ACTIVE -> localizedStringResource(1589, "Active terms protecting supply")
    SUPPLIER_CONTRACT_STATUS_PENDING_SUPPLIER -> localizedStringResource(1590, "Waiting for supplier acceptance")
    SUPPLIER_CONTRACT_STATUS_PENDING_STORE -> localizedStringResource(1591, "Waiting for store acceptance")
    else -> supplierContractStatusTitle(contract.status)
}

internal fun AppConfiguration.buildSupplierTermsGuardItems(
    orders: List<SupplierOrderDataModel>,
    lines: List<SupplierOrderLineDataModel>,
    contracts: List<SupplierPartnershipContractDataModel>
): List<SupplierTermsGuardUiModel> {
    val activeOrders = orders.filter { it.isActive }
    val linesByOrder = lines.filter { it.isActive }.groupBy { it.orderId }
    val guardStatuses = setOf(
        SUPPLIER_CONTRACT_STATUS_ACTIVE,
        SUPPLIER_CONTRACT_STATUS_PENDING_SUPPLIER,
        SUPPLIER_CONTRACT_STATUS_PENDING_STORE
    )

    return contracts
        .filter { contract -> contract.isActive && contract.status in guardStatuses }
        .map { contract ->
            val contractGoodsIds = contract.goodsItemIds.toSet()
            val relatedOrders = activeOrders.filter { order ->
                order.storeId == contract.storeId && order.supplierId == contract.supplierId
            }
            val relatedLines = relatedOrders
                .flatMap { order -> linesByOrder[order.id].orEmpty() }
                .filter { line -> contract.scopeType == SUPPLIER_CONTRACT_SCOPE_PARTNERSHIP || line.goodsItemId in contractGoodsIds }
            val affectedOrders = relatedOrders.filter { order ->
                contract.scopeType == SUPPLIER_CONTRACT_SCOPE_PARTNERSHIP || linesByOrder[order.id].orEmpty().any { it.goodsItemId in contractGoodsIds }
            }
            val openAffectedOrders = affectedOrders.count { !it.status.isSupplierOrderClosed() }
            val goodsTitles = relatedLines
                .map { supplierDeskLineTitle(it) }
                .filter { it.isNotBlank() }
                .distinct()
                .take(4)
            val fallbackGoodsTitles = contract.priceTerms
                .map { term -> term.goodsItemNameSnapshot.visibleLocalizedString(stateValues.appLanguage, term.goodsItemId.take(8)) }
                .filter { it.isNotBlank() }
                .distinct()
                .take(4)
            val coveredGoodsText = (goodsTitles.ifEmpty { fallbackGoodsTitles })
                .joinToString(" • ")
                .ifBlank { supplierContractScopeTitle(contract.scopeType) }
            val conditionTerms = contract.conditions
                .mapNotNull { raw -> runCatching { visibleStockConditionText(raw.toStockConditionDataModel()) }.getOrNull() }
                .filter { it.isNotBlank() }
            val deliveryText = contract.deliverySchedule.visibleLocalizedString(stateValues.appLanguage, "")
                .takeIf { it.isNotBlank() }
                ?.let { "${localizedStringResource(1600, "Delivery terms")}: $it" }
            val paymentText = contract.paymentSchedule.visibleLocalizedString(stateValues.appLanguage, "")
                .takeIf { it.isNotBlank() }
                ?.let { "${localizedStringResource(1601, "Payment terms")}: $it" }
            val customText = contract.customTerms.visibleLocalizedString(stateValues.appLanguage, "")
                .takeIf { it.isNotBlank() }
                ?.let { "${localizedStringResource(1599, "Custom terms")}: $it" }
            val priceTermsText = contract.priceTerms
                .filter { it.isActive }
                .take(3)
                .joinToString(" • ") { term ->
                    listOfNotNull(
                        term.goodsItemNameSnapshot.visibleLocalizedString(stateValues.appLanguage, term.goodsItemId.take(8)),
                        term.supplyPrice.supplierDeskMoneyText(),
                        term.minOrderQuantity?.quantityText(stateValues.appLanguage)
                    ).filter { it.isNotBlank() }.joinToString(" ")
                }
            val terms = (conditionTerms + listOfNotNull(deliveryText, paymentText, customText))
                .distinct()
                .take(6)
            val title = supplierVisibleContractTitle(contract)
            val subtitle = listOf(
                supplierContractScopeTitle(contract.scopeType),
                supplierContractStatusTitle(contract.status),
                "rev.${contract.revision}"
            ).joinToString(" • ")
            val effect = supplierTermsGuardEffectText(contract)
            val orderSearchQuery = (listOf(contract.storeId, contract.supplierId) + contract.goodsItemIds).filter { it.isNotBlank() }.joinToString(" ")
            val brief = buildString {
                append(localizedStringResource(1597, "Terms brief")).append('\n')
                append(title).append('\n')
                append(subtitle).append('\n')
                append(localizedStringResource(1585, "Operational effect")).append(": ").append(effect).append('\n')
                append(localizedStringResource(1586, "Affected open orders")).append(": ").append(openAffectedOrders).append('\n')
                if (coveredGoodsText.isNotBlank()) append(localizedStringResource(1587, "Covered goods")).append(": ").append(coveredGoodsText).append('\n')
                if (priceTermsText.isNotBlank()) append(localizedStringResource(1598, "Price terms")).append(": ").append(priceTermsText).append('\n')
                terms.forEach { append("• ").append(it).append('\n') }
            }.trim()
            SupplierTermsGuardUiModel(
                contract = contract,
                title = title,
                subtitle = subtitle,
                statusText = supplierContractStatusTitle(contract.status),
                effectText = effect,
                affectedOpenOrders = openAffectedOrders,
                affectedLineCount = relatedLines.size,
                coveredGoodsText = coveredGoodsText,
                terms = terms,
                priceTermsText = priceTermsText,
                searchKey = buildString {
                    append(contract.id).append(' ')
                    append(title).append(' ')
                    append(subtitle).append(' ')
                    append(effect).append(' ')
                    append(coveredGoodsText).append(' ')
                    append(priceTermsText).append(' ')
                    append(terms.joinToString(" ")).append(' ')
                    append(orderSearchQuery)
                }.lowercase(),
                orderSearchQuery = orderSearchQuery,
                brief = brief,
                statusColor = supplierContractStatusColor(contract.status)
            )
        }
        .sortedWith(compareByDescending<SupplierTermsGuardUiModel> { if (it.contract.status != SUPPLIER_CONTRACT_STATUS_ACTIVE) 1 else 0 }
            .thenByDescending { it.affectedOpenOrders }
            .thenByDescending { it.contract.updatedAtMillis })
}

@Composable
internal fun AppConfiguration.SupplierTermsGuardCard(item: SupplierTermsGuardUiModel) {
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(stateValues.unfocusedBorderWidth, item.statusColor.copy(alpha = 0.75f), RoundedCornerShape(stateValues.cornerRadius))
            .padding(stateValues.marginTextFieldGroup),
        verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(stateValues.cornerRadius))
                    .background(item.statusColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                CpImage(
                    modifier = Modifier.size(30.dp),
                    url = stateValues.drawablePathIconSupplierTermsGuard,
                    fallbackRes = stateValues.drawableResIconSupplierTermsGuard.value,
                    contentDescription = localizedStringResource(1583, "Supplier terms guard"),
                    tintColor = item.statusColor
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = stateValues.TextColor,
                    fontSize = stateValues.accentTextSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.subtitle,
                    color = item.statusColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Text(
            text = item.effectText,
            color = stateValues.TextColor,
            fontSize = stateValues.textSize,
            fontWeight = FontWeight.Bold
        )

        StockCardInfoLine(localizedStringResource(1586, "Affected open orders"), item.affectedOpenOrders.toString(), stateValues.TextColor)
        StockCardInfoLine(localizedStringResource(1604, "Related order lines"), item.affectedLineCount.toString(), stateValues.TextColor)
        StockCardInfoLine(localizedStringResource(1587, "Covered goods"), item.coveredGoodsText, stateValues.TextColor)
        item.priceTermsText.takeIf { it.isNotBlank() }?.let { text ->
            StockCardInfoLine(localizedStringResource(1598, "Price terms"), text, stateValues.TextColor)
        }

        item.terms.take(4).forEach { term ->
            Text(
                text = "• $term",
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) { SupplierCatalogChip(text = item.statusText) }
            Box(modifier = Modifier.weight(1f)) {
                SupplierCatalogChip(
                    text = if (item.contract.status == SUPPLIER_CONTRACT_STATUS_ACTIVE) {
                        localizedStringResource(1602, "Guarded orders")
                    } else {
                        localizedStringResource(1588, "Blocking supply until accepted")
                    }
                )
            }
        }

        if (stateValues.isNarrowScreen) {
            Column(verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)) {
                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(1594, "Open contract board"),
                    iconPath = stateValues.drawablePathIconSupplierContracts,
                    iconRes = stateValues.drawableResIconSupplierContracts.value,
                    textSize = stateValues.smallTextSize,
                    confirmationRequired = false,
                    onClick = { coroutineScope.launch { Navigation.goMain(NavigationScreenModel.Supplier.Contracts.Main) } }
                )
                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(1595, "Open affected orders"),
                    iconPath = stateValues.drawablePathIconAppModeSupplier,
                    iconRes = stateValues.drawableResIconAppModeSupplier.value,
                    textSize = stateValues.smallTextSize,
                    confirmationRequired = false,
                    onClick = {
                        coroutineScope.launch {
                            seedSupplierOrdersInboxNavigation(searchQuery = item.orderSearchQuery)
                            Navigation.goMain(NavigationScreenModel.Supplier.Orders.Main)
                        }
                    }
                )
                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(1596, "Copy terms brief"),
                    iconPath = stateValues.drawablePathIconClipboard,
                    iconRes = stateValues.drawableResIconClipboard.value,
                    textSize = stateValues.smallTextSize,
                    confirmationRequired = false,
                    onClick = { copyTextToClipboard(item.brief) }
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
            ) {
                actionButton(
                    modifier = Modifier.weight(1f),
                    text = localizedStringResource(1594, "Open contract board"),
                    iconPath = stateValues.drawablePathIconSupplierContracts,
                    iconRes = stateValues.drawableResIconSupplierContracts.value,
                    textSize = stateValues.smallTextSize,
                    confirmationRequired = false,
                    onClick = { coroutineScope.launch { Navigation.goMain(NavigationScreenModel.Supplier.Contracts.Main) } }
                )
                actionButton(
                    modifier = Modifier.weight(1f),
                    text = localizedStringResource(1595, "Open affected orders"),
                    iconPath = stateValues.drawablePathIconAppModeSupplier,
                    iconRes = stateValues.drawableResIconAppModeSupplier.value,
                    textSize = stateValues.smallTextSize,
                    confirmationRequired = false,
                    onClick = {
                        coroutineScope.launch {
                            seedSupplierOrdersInboxNavigation(searchQuery = item.orderSearchQuery)
                            Navigation.goMain(NavigationScreenModel.Supplier.Orders.Main)
                        }
                    }
                )
                actionButton(
                    modifier = Modifier.weight(1f),
                    text = localizedStringResource(1596, "Copy terms brief"),
                    iconPath = stateValues.drawablePathIconClipboard,
                    iconRes = stateValues.drawableResIconClipboard.value,
                    textSize = stateValues.smallTextSize,
                    confirmationRequired = false,
                    onClick = { copyTextToClipboard(item.brief) }
                )
            }
        }
    }
}


internal fun AppConfiguration.supplierManufacturerBridgeTitle(item: SupplierDashboardManufacturerBridgeDataModel): String =
    item.goodsItemNameSnapshot.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.barcodeSnapshots.firstOrNull().orEmpty() }
        .ifBlank { item.goodsItemId.take(8) }

internal fun AppConfiguration.supplierManufacturerBridgeQuantityText(
    total: Double,
    unitId: String?
): String {
    if (total <= 0.0) return "0"
    val unit = stateValues.globalAppConfiguration.goodsItemsQuantityUnits
        .find { it.id == unitId }
        ?: stateValues.globalAppConfiguration.goodsItemsQuantityUnits.firstOrNull()
    return unit
        ?.copy(total = total)
        ?.quantityText(stateValues.appLanguage)
        ?: total.toStockMoneyText()
}

internal fun AppConfiguration.supplierManufacturerBridgeActionTitle(action: String): String = when (action) {
    "quote" -> localizedStringResource(1716, "Quote upstream")
    "produce" -> localizedStringResource(1717, "Produce / reserve")
    "ship" -> localizedStringResource(1718, "Ship to stores")
    "price_book" -> localizedStringResource(1719, "Add factory price")
    "backorder" -> localizedStringResource(1720, "Watch backorder")
    else -> localizedStringResource(1721, "Demand is stable")
}

internal fun AppConfiguration.supplierManufacturerBridgeSearchKey(item: SupplierDashboardManufacturerBridgeDataModel): String = buildString {
    append(item.bridgeId).append(' ')
    append(item.goodsItemId).append(' ')
    append(item.orderIds.joinToString(" ")).append(' ')
    append(item.quoteNeededOrderIds.joinToString(" ")).append(' ')
    append(item.productionOrderIds.joinToString(" ")).append(' ')
    append(item.shipmentOrderIds.joinToString(" ")).append(' ')
    append(supplierManufacturerBridgeTitle(item)).append(' ')
    append(item.barcodeSnapshots.joinToString(" ")).append(' ')
    append(item.storePreview.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.storePreview.visibleLocalizedString("main", "")).append(' ')
    append(item.attentionSummary.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.attentionSummary.visibleLocalizedString("main", "")).append(' ')
    append(item.suggestedAction).append(' ')
    append(supplierManufacturerBridgeActionTitle(item.suggestedAction)).append(' ')
    append(item.requestedQuantityTotal).append(' ')
    append(item.acceptedQuantityTotal).append(' ')
    append(item.missingQuantityTotal)
}.lowercase()

internal fun AppConfiguration.supplierManufacturerBridgeBrief(item: SupplierDashboardManufacturerBridgeDataModel): String = buildString {
    append(localizedStringResource(1710, "Manufacturer bridge")).append('\n')
    append(supplierManufacturerBridgeTitle(item)).append('\n')
    if (item.barcodeSnapshots.isNotEmpty()) append(localizedStringResource(69, "Barcode")).append(": ").append(item.barcodeSnapshots.joinToString(", ")).append('\n')
    append(localizedStringResource(1424, "Total requested")).append(": ").append(supplierManufacturerBridgeQuantityText(item.requestedQuantityTotal, item.measurementUnitIdSnapshot)).append('\n')
    append(localizedStringResource(1713, "Accepted qty")).append(": ").append(supplierManufacturerBridgeQuantityText(item.acceptedQuantityTotal, item.measurementUnitIdSnapshot)).append('\n')
    append(localizedStringResource(1714, "Missing qty")).append(": ").append(supplierManufacturerBridgeQuantityText(item.missingQuantityTotal, item.measurementUnitIdSnapshot)).append('\n')
    append(localizedStringResource(1422, "Stores asking")).append(": ").append(item.storeCount).append('\n')
    append(localizedStringResource(1423, "Open requests")).append(": ").append(item.openOrderCount).append('\n')
    append(localizedStringResource(1715, "Response coverage")).append(": ").append(item.responseCoveragePercent).append('%').append('\n')
    append(localizedStringResource(1783, "Factory staging")).append(": ")
        .append(localizedStringResource(1784, "Quote")).append(' ').append(item.quoteNeededOrderIds.size).append(" • ")
        .append(localizedStringResource(1785, "Produce")).append(' ').append(item.productionOrderIds.size).append(" • ")
        .append(localizedStringResource(1786, "Ship")).append(' ').append(item.shipmentOrderIds.size).append('\n')
    item.attentionSummary.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.attentionSummary.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { attention -> append(localizedStringResource(1756, "Attention notes")).append(": ").append(attention).append('\n') }
    append(localizedStringResource(1787, "Upstream privacy")).append(": ").append(localizedStringResource(1788, "Store names are omitted from this copied factory brief.")).append('\n')
    item.estimatedAcceptedAmount.supplierDeskMoneyText().takeIf { it.isNotBlank() }?.let { amount ->
        append(localizedStringResource(1727, "Factory value")).append(": ").append(amount).append('\n')
    }
    item.earliestDueAtMillis?.takeIf { it > 0L }?.let { due ->
        append(localizedStringResource(1723, "Earliest due")).append(": ").append(receiptUiDateTime(due)).append('\n')
    }
    append(localizedStringResource(1722, "Priority score")).append(": ").append(item.priorityScore).append('\n')
    append(localizedStringResource(1650, "Actions")).append(": ").append(supplierManufacturerBridgeActionTitle(item.suggestedAction))
}


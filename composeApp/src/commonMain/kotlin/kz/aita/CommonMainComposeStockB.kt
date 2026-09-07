// THIS IS CommonMainCompose.kt split slice: StockB
@file:OptIn(ExperimentalTime::class, ExperimentalFoundationApi::class)
package kz.aita

import aita.composeapp.generated.resources.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.DrawableResource
import kotlin.math.round
import kotlin.time.ExperimentalTime

@Composable
fun AppConfiguration.StockBatchCard(
    batch: GoodsBatchDataModel,
    activeShelfBatchId: String?,
    shelfIndex: Int? = null,
    compact: Boolean = false,
    draggedBatchId: String? = null,
    draggedBatchIndex: Int? = null,
    dragTargetIndex: Int? = null,
    allBatchesCount: Int = 0,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onSetActiveShelf: (() -> Unit)? = null,
    onDragStart: ((String) -> Unit)? = null,
    onDragTargetChanged: ((Int) -> Unit)? = null,
    onDragFinished: ((Int) -> Unit)? = null,
    onDragCancelled: (() -> Unit)? = null
) {
    val supplierName = stateValues.suppliers
        .orEmpty()
        .find { it.id == batch.supplierId }
        ?.name
        ?.extractLocalizedString(stateValues.appLanguage)
        ?: localizedStringResource(638, "No supplier selected")
    val goodsItem = stateValues.stock.orEmpty().find { it.id == batch.goodsItemId }

    val isActiveShelf = batch.id == activeShelfBatchId
    val isDragging = draggedBatchId == batch.id
    val currentIndex = shelfIndex ?: 0
    val density = LocalDensity.current
    val itemStepPx = with(density) { 116.dp.toPx() }

    var dragOffsetPx by remember(batch.id) { mutableStateOf(0f) }

    val pushedOffsetPx = when {
        draggedBatchIndex == null || dragTargetIndex == null || isDragging -> 0f
        draggedBatchIndex < dragTargetIndex && currentIndex in (draggedBatchIndex + 1)..dragTargetIndex -> -itemStepPx
        draggedBatchIndex > dragTargetIndex && currentIndex in dragTargetIndex until draggedBatchIndex -> itemStepPx
        else -> 0f
    }

    val animatedPushedOffsetPx by animateFloatAsState(
        targetValue = pushedOffsetPx,
        animationSpec = tween(160),
        label = "batchVerticalPushedOffset"
    )

    fun currentTargetIndex(): Int {
        if (allBatchesCount <= 0) return currentIndex
        val deltaSlots = round(dragOffsetPx / itemStepPx).toInt()
        return (currentIndex + deltaSlots).coerceIn(0, allBatchesCount - 1)
    }

    Row(
        modifier = Modifier
            .run {
                if (compact) widthIn(min = 176.dp, max = 240.dp) else fillMaxWidth()
            }
            .zIndex(if (isDragging) 2f else 0f)
            .graphicsLayer {
                translationY = if (isDragging) dragOffsetPx else animatedPushedOffsetPx
                scaleX = if (isDragging) 1.025f else 1f
                scaleY = if (isDragging) 1.025f else 1f
                alpha = if (isDragging) 0.97f else 1f
            }
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = isDragging)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(
                if (isActiveShelf || isDragging) stateValues.focusedBorderWidth else stateValues.unfocusedBorderWidth,
                when {
                    isDragging -> stateValues.AccentColor
                    isActiveShelf -> stateValues.AccentColor
                    else -> stateValues.PlaceholderTextColor
                },
                RoundedCornerShape(stateValues.cornerRadius)
            )
            .pointerInput(batch.id, allBatchesCount, currentIndex) {
                if (onDragFinished == null) return@pointerInput

                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        dragOffsetPx = 0f
                        onDragStart?.invoke(batch.id)
                        onDragTargetChanged?.invoke(currentIndex)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragOffsetPx += dragAmount.y
                        onDragTargetChanged?.invoke(currentTargetIndex())
                    },
                    onDragEnd = {
                        val target = currentTargetIndex()
                        dragOffsetPx = 0f
                        onDragFinished?.invoke(target)
                    },
                    onDragCancel = {
                        dragOffsetPx = 0f
                        onDragCancelled?.invoke()
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = listOfNotNull(
                            shelfIndex?.let { "#${it + 1}" },
                            if (isActiveShelf) localizedStringResource(265, "Active batch") else localizedStringResource(128, "Shelf batch")
                        ).joinToString(" • "),
                        color = if (isActiveShelf || isDragging) stateValues.AccentColor else stateValues.TextColor,
                        fontSize = stateValues.textSize,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = supplierName,
                        color = stateValues.TextColor,
                        fontSize = stateValues.smallTextSize,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = batch.quantity.quantityText(stateValues.appLanguage),
                    color = stateValues.TextColor,
                    fontSize = stateValues.accentTextSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val batchPriceLines = buildList {
                goodsItem?.promotedPriceForTransaction(
                    transactionTypeIndex = 0,
                    saleMethodId = SALE_METHOD_RETAIL,
                    quantityTotal = 1.0,
                    batch = batch
                )?.takeIf { batch.salePriceOverride != null || it.hasPriceChange }?.let { promotedSalePrice ->
                    add(StockPromotedPriceDisplayLine(stateValues.stringSalePrice, promotedSalePrice))
                }

                goodsItem?.promotedPriceForTransaction(
                    transactionTypeIndex = 1,
                    saleMethodId = SALE_METHOD_RETAIL,
                    quantityTotal = 1.0,
                    batch = batch
                )?.takeIf { batch.returnPriceOverride != null || it.hasPriceChange }?.let { promotedReturnPrice ->
                    add(StockPromotedPriceDisplayLine(stateValues.stringReturnPrice, promotedReturnPrice))
                }

                add(
                    StockPromotedPriceDisplayLine(
                        title = stateValues.stringSupplyPrice,
                        promotedPrice = goodsItem?.promotedPriceForTransaction(
                            transactionTypeIndex = 2,
                            saleMethodId = SALE_METHOD_RETAIL,
                            quantityTotal = batch.quantity.total.takeIf { it > 0.0 } ?: 1.0,
                            batch = batch
                        ) ?: PromotedPriceDataModel(originalPrice = batch.supplyPrice, finalPrice = batch.supplyPrice)
                    )
                )
            }

            StockCompactPromotionPriceInfoLines(batchPriceLines, stateValues.TextColor)

            batch.expirationDateMillis?.toStockDateInputText()?.takeIf { it.isNotBlank() }?.let {
                StockCardInfoLine(
                    title = localizedStringResource(202, "Expiration"),
                    value = it,
                    textColor = stateValues.TextColor
                )
            }

            batch.deliveredAtMillis?.toStockDateInputText()?.takeIf { it.isNotBlank() }?.let {
                StockCardInfoLine(
                    title = localizedStringResource(342, "Delivered"),
                    value = it,
                    textColor = stateValues.TextColor
                )
            }

            batch.manufacturedAtMillis?.toStockDateInputText()?.takeIf { it.isNotBlank() }?.let {
                StockCardInfoLine(
                    title = localizedStringResource(343, "Made"),
                    value = it,
                    textColor = stateValues.TextColor
                )
            }

            StockCardInfoLine(
                title = localizedStringResource(200, "Status"),
                value = stockBatchStatusText(batch.status),
                textColor = stateValues.TextColor
            )

            batch.shelfPosition?.takeIf { it.isNotBlank() }?.let {
                StockCardInfoLine(
                    title = localizedStringResource(344, "Shelf position"),
                    value = it,
                    textColor = stateValues.TextColor
                )
            }

            StockCardInfoLine(
                title = localizedStringResource(345, "Priority"),
                value = batch.shelfPriority.toString(),
                textColor = stateValues.TextColor
            )

            if (batch.discounts.isNotEmpty()) {
                StockCardInfoLine(
                    title = localizedStringResource(346, "Discounts"),
                    value = batch.discounts.size.toString(),
                    textColor = stateValues.TextColor
                )
            }

            if (batch.promotions.isNotEmpty()) {
                StockCardInfoLine(
                    title = localizedStringResource(920, "Promos"),
                    value = batch.promotions.count { it.isActiveAt() }.takeIf { it > 0 }?.toString() ?: batch.promotions.size.toString(),
                    textColor = stateValues.TextColor
                )
            }

            val visibleBatchNotes = batch.additionalNotesLocalized.extractLocalizedString(stateValues.appLanguage)
                ?: batch.additionalNotesLocalized.extractLocalizedString("main")
                ?: batch.additionalNotes

            visibleBatchNotes?.takeIf { it.isNotBlank() }?.let {
                StockCardInfoLine(
                    title = localizedStringResource(201, "Notes"),
                    value = it,
                    textColor = stateValues.TextColor
                )
            }
        }

        Column(
            modifier = Modifier
                .padding(end = 12.dp, top = 14.dp, start = 4.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            onEdit?.let { edit ->
                actionButton(
                    text = "",
                    iconPath = stateValues.drawablePathIconEdit,
                    iconContentDescription = stateValues.drawablePathIconEdit,
                    onClick = edit
                )
            }

            onSetActiveShelf?.let { setActiveShelf ->
                actionButton(
                    text = "",
                    iconPath = stateValues.drawablePathIconStock,
                    iconContentDescription = "Shelf",
                    enabled = !isActiveShelf,
                    onClick = setActiveShelf
                )
            }

            onDelete?.let { delete ->
                actionButton(
                    text = "",
                    enabledColor = stateValues.ErrorColor,
                    iconPath = stateValues.drawablePathIconDelete,
                    iconContentDescription = stateValues.drawablePathIconDelete,
                    onClick = delete
                )
            }
        }
    }
}




@Composable
fun AppConfiguration.StockBatchEditor(
    modifier: Modifier = Modifier,
    goodsItem: GoodsItemDataModel,
    existingBatch: GoodsBatchDataModel?,
    draftStateKey: String? = null,
    onCancel: () -> Unit,
    onSaved: () -> Unit
) {
    val defaultUnit = stateValues.globalAppConfiguration.goodsItemsQuantityUnits
        .find { it.id == goodsItem.measurementUnitId }
        ?: stateValues.globalAppConfiguration.goodsItemsQuantityUnits.first()

    val defaultCurrency =
        goodsItem.supplyPrices.firstOrNull()?.currency
            ?: goodsItem.salePrices.firstOrNull()?.currency
            ?: "KZT"

    val addEditState by NavigationScreenModel.Stock.AddEditGoodsItem.state.collectAsState()
    val restoredDraft = draftStateKey
        ?.let { addEditState[it] }
        ?.let { raw ->
            goodsBatchDraftFromNavigationStateString(raw)
        }
        ?.takeIf {
            it.goodsItemId == goodsItem.id &&
                    (existingBatch == null || it.id == existingBatch.id)
        }

    var draft by remember(draftStateKey, existingBatch?.id, goodsItem.id) {
        mutableStateOf(
            restoredDraft
                ?: existingBatch?.toDraft(defaultUnit.id)
                ?: GoodsBatchDraft(
                    goodsItemId = goodsItem.id,
                    storeId = goodsItem.storeId,
                    supplierId = null,
                    quantityUnitId = defaultUnit.id,
                    supplyPrice = goodsItem.supplyPrices.firstOrNull()
                        ?: PriceDataModel(
                            price = "0",
                            currency = defaultCurrency,
                            supplierId = ""
                        ),
                    salePriceOverride = goodsItem.salePrices.firstOrNull(),
                    returnPriceOverride = goodsItem.returnPrices.firstOrNull(),
                    wholesalePriceOverride = goodsItem.wholesalePrices.firstOrNull()
                )
        )
    }

    LaunchedEffect(draft, draftStateKey) {
        draftStateKey?.let {
            NavigationScreenModel.Stock.AddEditGoodsItem.setState(
                it to draft.toNavigationStateString()
            )
        }
    }

    val supplierGoodsPricesPayload by supplierGoodsPricesState.payload.collectAsState()
    val supplierGoodsPrices = supplierGoodsPricesPayload.orEmpty()

    var lastAutoFillKey by remember {
        mutableStateOf("")
    }

    LaunchedEffect(
        draft.goodsItemId,
        draft.supplierId,
        supplierGoodsPrices
    ) {
        val supplierId = draft.supplierId ?: return@LaunchedEffect
        val autoFillKey = "${draft.goodsItemId}:$supplierId"

        if (autoFillKey == lastAutoFillKey)
            return@LaunchedEffect

        val rememberedPrice = supplierGoodsPrices.find {
            it.supplierId == supplierId &&
                    it.goodsItemId == draft.goodsItemId &&
                    it.isActive
        }

        rememberedPrice?.let {
            draft = draft.copy(
                supplyPrice = it.supplyPrice
            )
        }

        lastAutoFillKey = autoFillKey
    }

    val suppliers = stateValues.suppliers.orEmpty()
    var showSupplierAddSheet by rememberSaveable(goodsItem.id, existingBatch?.id) { mutableStateOf(false) }
    var applyPromotionsToSameSupplier by rememberSaveable(goodsItem.id, existingBatch?.id) { mutableStateOf(false) }
    var isSavingBatch by remember(goodsItem.id, existingBatch?.id ?: "new", draftStateKey ?: "batch") { mutableStateOf(false) }
    var saveError by remember(goodsItem.id, existingBatch?.id ?: "new", draftStateKey ?: "batch") { mutableStateOf<String?>(null) }
    var returnPriceOverrideManuallyEdited by rememberSaveable(goodsItem.id, existingBatch?.id ?: "new") {
        mutableStateOf(
            existingBatch?.let { batch ->
                val savedSalePrice = batch.salePriceOverride ?: goodsItem.salePrices.firstOrNull()
                val savedReturnPrice = batch.returnPriceOverride ?: goodsItem.returnPrices.firstOrNull()
                !stockPriceAmountCurrencyMatches(savedSalePrice, savedReturnPrice)
            } ?: false
        )
    }

    if (showSupplierAddSheet) {
        QuickSupplierAddBottomSheet(
            onDismiss = { showSupplierAddSheet = false },
            onSaved = { supplier ->
                draft = draft.copy(
                    supplierId = supplier.id,
                    supplyPrice = draft.supplyPrice.copy(supplierId = supplier.id)
                )
                showSupplierAddSheet = false
            }
        )
    }

    val selectedSupplierRememberedSupplyPrice = draft.supplierId?.let { selectedSupplierId ->
        supplierGoodsPrices.find {
            it.supplierId == selectedSupplierId &&
                    it.goodsItemId == draft.goodsItemId &&
                    it.isActive
        }?.supplyPrice
    }

    val predictedExpirationDate = goodsItem.genericExpirationPeriod
        ?.takeIf { it.isUsable }
        ?.let { addStockExpirationPeriod(currentStockLocalDate(), it).toStockDateInputText() }

    val selectedQuantityUnit = stateValues.globalAppConfiguration.goodsItemsQuantityUnits
        .find { it.id == draft.quantityUnitId }
        ?: defaultUnit
    val selectedQuantityAllowsFraction = selectedQuantityUnit.allowsFractionalStockQuantityInput()

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        ScreenAppBarWidget(
            title = if (existingBatch == null) localizedStringResource(194, "Add batch") else localizedStringResource(195, "Edit batch"),
            onBack = onCancel
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(stateValues.marginTextField)
        ) {
            item {
                SimpleDropdownField(
                    title = stateValues.stringSupplier,
                    selectedId = draft.supplierId,
                    options = suppliers.map {
                        DropdownOption(
                            id = it.id,
                            title = it.name.extractLocalizedString(stateValues.appLanguage)
                                ?: it.name.firstOrNull()?.value
                                ?: it.id
                        )
                    },
                    placeholder = stateValues.stringSelectSupplier,
                    onSelected = { selectedSupplierId ->
                        draft = draft.copy(
                            supplierId = selectedSupplierId,
                            supplyPrice = draft.supplyPrice.copy(
                                supplierId = selectedSupplierId
                            )
                        )
                    }
                )

                Spacer(modifier = Modifier.height(stateValues.marginTextField))

                actionButton(
                    text = localizedStringResource(635, "Add supplier here"),
                    iconPath = stateValues.drawablePathIconAdd,
                    confirmationRequired = false,
                    onClick = { showSupplierAddSheet = true }
                )

                Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

                SimpleTextInput(
                    modifier = Modifier.fillMaxWidth(),
                    value = draft.quantityText,
                    placeholder = localizedStringResource(271, "Quantity"),
                    keyboardType = if (selectedQuantityAllowsFraction) KeyboardType.Decimal else KeyboardType.Number,
                    leadingIconPath = stateValues.drawablePathIconStock,
                    onTransformValue = { raw -> sanitizeStockQuantityInput(raw, selectedQuantityAllowsFraction) },
                    onValueChange = { value ->
                        if (value.isStockQuantityInputText(selectedQuantityAllowsFraction)) {
                            draft = draft.copy(quantityText = value)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(6.dp))

                StockQuantityQuickFillButtons(
                    quantityUnit = selectedQuantityUnit,
                    currentText = draft.quantityText,
                    onAmountSelected = { selectedAmount -> draft = draft.copy(quantityText = selectedAmount) }
                )

                Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

                SimpleDropdownField(
                    title = localizedStringResource(270, "Unit"),
                    selectedId = draft.quantityUnitId,
                    options = stateValues.globalAppConfiguration.goodsItemsQuantityUnits.map {
                        DropdownOption(
                            id = it.id,
                            title = it.immutableUnitName.extractLocalizedString(stateValues.appLanguage)
                                ?: it.id
                        )
                    },
                    placeholder = localizedStringResource(188, "Select unit"),
                    onSelected = { selectedUnitId ->
                        val nextUnit = stateValues.globalAppConfiguration.goodsItemsQuantityUnits
                            .find { unit -> unit.id == selectedUnitId }
                            ?: selectedQuantityUnit
                        draft = draft.copy(
                            quantityUnitId = selectedUnitId,
                            quantityText = sanitizeStockQuantityInput(
                                draft.quantityText,
                                nextUnit.allowsFractionalStockQuantityInput()
                            )
                        )
                    }
                )

                Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

                Text(
                    text = localizedStringResource(267, "Batch prices"),
                    color = stateValues.TextColor,
                    fontSize = stateValues.titleTextSize,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(stateValues.marginTextField))

                StockSinglePriceEditor(
                    title = stateValues.stringSalePrice,
                    price = draft.salePriceOverride ?: goodsItem.salePrices.firstOrNull() ?: PriceDataModel("", defaultCurrency, ""),
                    quickFillPrices = goodsItem.salePrices,
                    onChanged = { nextSalePrice ->
                        val previousSalePrice = draft.salePriceOverride ?: goodsItem.salePrices.firstOrNull()
                        val currentReturnPrice = draft.returnPriceOverride ?: goodsItem.returnPrices.firstOrNull()
                        val shouldMirrorReturnPrice = !returnPriceOverrideManuallyEdited &&
                                (
                                        draft.returnPriceOverride == null ||
                                                draft.returnPriceOverride?.price.isNullOrBlank() ||
                                                stockPriceAmountCurrencyMatches(currentReturnPrice, previousSalePrice)
                                        )

                        draft = draft.copy(
                            salePriceOverride = nextSalePrice,
                            returnPriceOverride = if (shouldMirrorReturnPrice) {
                                nextSalePrice.copy(supplierId = "")
                            } else {
                                draft.returnPriceOverride
                            }
                        )
                    }
                )

                Spacer(modifier = Modifier.height(stateValues.marginTextField))

                StockSinglePriceEditor(
                    title = stateValues.stringReturnPrice,
                    price = draft.returnPriceOverride ?: goodsItem.returnPrices.firstOrNull() ?: PriceDataModel("", defaultCurrency, ""),
                    quickFillPrices = goodsItem.returnPrices,
                    onChanged = {
                        returnPriceOverrideManuallyEdited = true
                        draft = draft.copy(returnPriceOverride = it)
                    }
                )

                Spacer(modifier = Modifier.height(stateValues.marginTextField))

                StockSinglePriceEditor(
                    title = localizedStringResource(247, "Wholesale price override"),
                    price = draft.wholesalePriceOverride ?: goodsItem.wholesalePrices.firstOrNull() ?: PriceDataModel("", defaultCurrency, ""),
                    quickFillPrices = goodsItem.wholesalePrices,
                    onChanged = { draft = draft.copy(wholesalePriceOverride = it) }
                )

                Spacer(modifier = Modifier.height(stateValues.marginTextField))

                StockSinglePriceEditor(
                    title = stateValues.stringSupplyPrice,
                    price = draft.supplyPrice,
                    quickFillPrices = listOfNotNull(selectedSupplierRememberedSupplyPrice, goodsItem.supplyPrices.firstOrNull()) + goodsItem.supplyPrices,
                    onChanged = { draft = draft.copy(supplyPrice = it.copy(supplierId = draft.supplierId.orEmpty())) }
                )

                Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

                StockDateRangeEditor(
                    title = localizedStringResource(341, "Batch data"),
                    startTitle = localizedStringResource(306, "Manufactured date"),
                    endTitle = localizedStringResource(202, "Expiration"),
                    startDateText = draft.manufacturedDateText,
                    endDateText = draft.expirationDateText,
                    onStartDateChanged = { draft = draft.copy(manufacturedDateText = it) },
                    onEndDateChanged = { draft = draft.copy(expirationDateText = it) }
                )

                predictedExpirationDate?.let { predicted ->
                    Spacer(modifier = Modifier.height(stateValues.marginTextField))

                    actionButton(
                        text = "${localizedStringResource(305, "Use predicted expiration")}: $predicted",
                        iconPath = stateValues.drawablePathIconStock,
                        fillMaxWidthIfTextPresent = false
                    ) {
                        draft = draft.copy(expirationDateText = predicted)
                    }
                }

                Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

                StockPromotionListEditor(
                    promotions = draft.promotions,
                    onPromotionsChanged = { draft = draft.copy(promotions = it.sanitizedStockPromotions()) },
                    quantityUnit = selectedQuantityUnit,
                    allowApplyToSameSupplier = draft.supplierId?.isNotBlank() == true,
                    applyToSameSupplier = applyPromotionsToSameSupplier,
                    onApplyToSameSupplierChanged = { applyPromotionsToSameSupplier = it }
                )

                Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

                StockLocalizedStringGroupEditor(
                    title = localizedStringResource(201, "Notes"),
                    placeholder = stateValues.stringOptional,
                    values = draft.additionalNotesLocalized.ifEmpty {
                        draft.additionalNotes.takeIf { it.isNotBlank() }?.let { listOf(LocalizedStringDataModel("main", it)) }
                            ?: emptyLocalizedItemForCurrentLanguage()
                    },
                    addText = localizedStringResource(952, "Add batch note translation"),
                    required = false,
                    singleLine = false,
                    adaptiveMultiline = true,
                    onChanged = { notes ->
                        draft = draft.copy(
                            additionalNotesLocalized = notes,
                            additionalNotes = notes.extractLocalizedString("main")
                                ?: notes.firstOrNull { item -> item.value.isNotBlank() }?.value.orEmpty()
                        )
                    }
                )

                Spacer(modifier = Modifier.height(stateValues.screenHeight / 5))
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            actionButton(
                modifier = Modifier.weight(1f),
                text = stateValues.stringCancel,
                enabledColor = stateValues.DisabledColor,
                onClick = onCancel
            )

            saveError?.let { error ->
                Text(
                    text = error,
                    color = stateValues.ErrorColor,
                    fontSize = stateValues.smallTextSize,
                    modifier = Modifier.weight(1f)
                )
            }

            actionButton(
                modifier = Modifier.weight(1f),
                text = stateValues.stringConfirm,
                enabled = !isSavingBatch &&
                        parseStockQuantityInputText(draft.quantityText, selectedQuantityUnit)?.let { it > 0.0 } == true &&
                        draft.supplyPrice.price.toDoubleOrNull()?.let { it >= 0.0 } == true,
                onClick = {
                    if (!isSavingBatch) {
                        saveError = null
                        isSavingBatch = true

                        val now = getCurrentTimeMillis()
                        val quantityUnit = stateValues.globalAppConfiguration.goodsItemsQuantityUnits
                            .find { it.id == draft.quantityUnitId }
                            ?: defaultUnit

                        val batch = GoodsBatchDataModel(
                        id = draft.id,
                        goodsItemId = goodsItem.id,
                        userId = existingBatch?.userId.orEmpty(),
                        storeId = goodsItem.storeId,
                        supplierId = draft.supplierId?.takeIf { it.isNotBlank() },
                        supplierOrderId = existingBatch?.supplierOrderId,
                        quantity = quantityUnit.withStockQuantityInputTotalValue(
                            parseStockQuantityInputText(draft.quantityText, quantityUnit) ?: 0.0
                        ),
                        supplyPrice = draft.supplyPrice.copy(supplierId = draft.supplierId.orEmpty()),
                        salePriceOverride = draft.salePriceOverride?.takeIf { it.price.isNotBlank() },
                        returnPriceOverride = draft.returnPriceOverride?.takeIf { it.price.isNotBlank() },
                        wholesalePriceOverride = draft.wholesalePriceOverride?.takeIf { it.price.isNotBlank() },
                        deliveredAtMillis = existingBatch?.deliveredAtMillis ?: now,
                        manufacturedAtMillis = stockDateInputTextToMillis(draft.manufacturedDateText),
                        expirationDateMillis = stockDateInputTextToMillis(draft.expirationDateText),
                        discounts = existingBatch?.discounts.orEmpty(),
                        promotions = draft.promotions.sanitizedStockPromotions(),
                        shelfPosition = existingBatch?.shelfPosition,
                        shelfPriority = existingBatch?.shelfPriority ?: stateValues.stockBatches.orEmpty().count { it.goodsItemId == goodsItem.id },
                        status = draft.status,
                        additionalNotes = draft.additionalNotes.takeIf { it.isNotBlank() },
                        additionalNotesLocalized = draft.additionalNotesLocalized.filter { it.value.isNotBlank() },
                        createdAtMillis = existingBatch?.createdAtMillis ?: now,
                        updatedAtMillis = now,
                        createdByUserId = existingBatch?.createdByUserId.orEmpty(),
                        isActive = true
                    )

                    val sameSupplierPromoCopies = if (applyPromotionsToSameSupplier && !batch.supplierId.isNullOrBlank()) {
                        stateValues.stockBatches.orEmpty()
                            .filter { other ->
                                other.goodsItemId == goodsItem.id &&
                                        other.id != batch.id &&
                                        other.supplierId == batch.supplierId &&
                                        other.isActive
                            }
                            .map { other -> other.copy(promotions = batch.promotions, updatedAtMillis = now) }
                    } else {
                        emptyList()
                    }

                        if (existingBatch == null) {
                            addGoodsBatches(listOf(batch)) { state ->
                                if (state is DataState.Success) {
                                    if (sameSupplierPromoCopies.isNotEmpty()) {
                                        updateGoodsBatches(sameSupplierPromoCopies) { updateState ->
                                            if (updateState is DataState.Success) {
                                                onSaved()
                                            } else {
                                                postInAppNotification(
                                                    updateState.message ?: localizedStringResourceMessage(
                                                        id = 13,
                                                        main = "Batch saved, but promotions could not be copied to the other supplier batches",
                                                        ru = "Партия сохранена, но акции не удалось скопировать в другие партии этого поставщика",
                                                        kk = "Партия сақталды, бірақ акцияларды осы жеткізушінің басқа партияларына көшіру мүмкін болмады"
                                                    ),
                                                    NotificationType.Neutral,
                                                    transient = true
                                                )
                                                onSaved()
                                            }
                                        }
                                    } else {
                                        onSaved()
                                    }
                                } else {
                                    val fallbackMessage = localizedStringResource(13, "Could not save batch")
                                    saveError = state.message
                                        ?.extractLocalizedString(stateValues.appLanguage)
                                        ?.trim()
                                        ?.takeIf { it.isNotBlank() }
                                        ?: fallbackMessage
                                    isSavingBatch = false
                                }
                            }
                        } else {
                            updateGoodsBatches(listOf(batch) + sameSupplierPromoCopies) { updateState ->
                                if (updateState is DataState.Success) {
                                    onSaved()
                                } else {
                                    val fallbackMessage = localizedStringResource(13, "Could not save batch")
                                    saveError = updateState.message
                                        ?.extractLocalizedString(stateValues.appLanguage)
                                        ?.trim()
                                        ?.takeIf { it.isNotBlank() }
                                        ?: fallbackMessage
                                    isSavingBatch = false
                                }
                            }
                        }
                    }
                }
            )
        }
    }
}



@Composable
fun AppConfiguration.StockAddEditIdentityPage(
    modifier: Modifier = Modifier,
    draft: StockAddEditDraft,
    onDraftChanged: (StockAddEditDraft) -> Unit
) {
    LazyColumn(
        modifier = modifier.padding(stateValues.marginTextField)
    ) {
        item {
            BarcodeListEditor(
                title = stateValues.stringBarcode,
                barcodes = draft.visibleBarcodes(),
                barcodeTypes = draft.visibleBarcodeTypes(),
                onChanged = { changedBarcodes ->
                    val nextBarcodes = changedBarcodes.ifEmpty { listOf("") }
                    onDraftChanged(
                        draft.copy(
                            barcodes = nextBarcodes,
                            barcodeTypes = draft.barcodeTypes.alignedStockBarcodeTypes(nextBarcodes)
                        )
                    )
                },
                onTypesChanged = { changedTypes ->
                    onDraftChanged(draft.copy(barcodeTypes = changedTypes.alignedStockBarcodeTypes(draft.visibleBarcodes())))
                },
                onBarcodesAndTypesChanged = { changedBarcodes, changedTypes ->
                    val nextBarcodes = changedBarcodes.ifEmpty { listOf("") }
                    onDraftChanged(
                        draft.copy(
                            barcodes = nextBarcodes,
                            barcodeTypes = changedTypes.alignedStockBarcodeTypes(nextBarcodes)
                        )
                    )
                }
            )

            Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

            LocalizedStringListEditor(
                title = stateValues.stringName,
                values = draft.name,
                onChanged = { onDraftChanged(draft.copy(name = it)) }
            )

            Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

            LocalizedStringListEditor(
                title = stateValues.stringDescription,
                values = draft.description,
                onChanged = { onDraftChanged(draft.copy(description = it)) }
            )

            Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

            SimpleDropdownField(
                title = stateValues.stringMeasurementUnit,
                selectedId = draft.measurementUnitId,
                options = stateValues.globalAppConfiguration.goodsItemsQuantityUnits.map {
                    DropdownOption(
                        id = it.id,
                        title = it.immutableUnitName.extractLocalizedString(stateValues.appLanguage)
                            ?: it.id
                    )
                },
                placeholder = localizedStringResource(188, "Select unit"),
                onSelected = {
                    onDraftChanged(draft.copy(measurementUnitId = it))
                }
            )

            Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

            val allCategories = stateValues.goodsCategories.orEmpty()
            val rootCategories = allCategories
                .filter { category -> category.typeIds.orEmpty().none { parentId -> allCategories.any { it.id == parentId } } }
                .sortedBy { it.name.visibleGoodsCategoryName(stateValues.appLanguage, it.id) }

            val selectedCategoryId = draft.categoryIds.firstOrNull()
            val selectedCategory = allCategories.find { it.id == selectedCategoryId }
            val selectedRootId = selectedCategory
                ?.typeIds
                ?.firstOrNull { parentId -> rootCategories.any { it.id == parentId } }
                ?: selectedCategoryId
                ?: rootCategories.firstOrNull()?.id

            val subcategories = allCategories
                .filter { it.typeIds.orEmpty().contains(selectedRootId) }
                .sortedBy { it.name.visibleGoodsCategoryName(stateValues.appLanguage, it.id) }

            val rootOptions = rootCategories.map { category ->
                DropdownOption(
                    id = category.id,
                    title = category.name.visibleGoodsCategoryName(stateValues.appLanguage, category.id),
                    subtitle = category.description?.visibleLocalizedString(stateValues.appLanguage, "")?.withoutGoodsCategoryPrefix()
                        ?: category.alias?.visibleLocalizedString(stateValues.appLanguage, "")?.withoutGoodsCategoryPrefix()
                )
            }

            val subcategoryOptions = subcategories.map { category ->
                DropdownOption(
                    id = category.id,
                    title = category.name.visibleGoodsCategoryName(stateValues.appLanguage, category.id),
                    subtitle = category.description?.visibleLocalizedString(stateValues.appLanguage, "")?.withoutGoodsCategoryPrefix()
                        ?: category.alias?.visibleLocalizedString(stateValues.appLanguage, "")?.withoutGoodsCategoryPrefix()
                )
            }

            if (rootOptions.isEmpty()) {
                MessageText(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = stateValues.marginTextField),
                    text = localizedStringResource(186, "No categories loaded yet. Restart server after category migrations or refresh categories."),
                )

                actionButton(
                    text = localizedStringResource(187, "Refresh categories"),
                    iconPath = stateValues.drawablePathIconSwitch,
                    onClick = { getGenericGoodsCategories() }
                )
            } else {
                SimpleDropdownField(
                    title = stateValues.stringCategory,
                    selectedId = selectedRootId,
                    options = rootOptions,
                    placeholder = stateValues.stringSelectCategory,
                    onSelected = { rootId ->
                        onDraftChanged(draft.copy(categoryIds = listOf(rootId)))
                        rememberLatestStockAddEditCategorySelection(rootId, rootId)
                    }
                )

                if (subcategoryOptions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(stateValues.marginTextField))

                    SimpleDropdownField(
                        title = localizedStringResource(184, "Subcategory"),
                        selectedId = selectedCategoryId?.takeIf { id -> subcategories.any { it.id == id } },
                        options = subcategoryOptions,
                        placeholder = localizedStringResource(185, "Select subcategory"),
                        onSelected = { subcategoryId ->
                            onDraftChanged(draft.copy(categoryIds = listOf(subcategoryId)))
                            rememberLatestStockAddEditCategorySelection(selectedRootId, subcategoryId)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

            StockExpirationPeriodEditor(
                period = draft.genericExpirationPeriod,
                onChanged = { onDraftChanged(draft.copy(genericExpirationPeriod = it)) }
            )
        }
    }
}


@Composable
internal fun AppConfiguration.StockLocationAvailabilityCard(
    location: StockBranchQuantityDataModel,
    currentStoreId: String,
    onMoveBatch: ((GoodsBatchDataModel, String?) -> Unit)?
) {
    val name = location.name.visibleLocalizedString(stateValues.appLanguage, location.publicId.ifBlank { location.storeId })
    val quantityText = location.totalQuantity?.quantityText(stateValues.appLanguage)
        ?: "0"
    val isCurrent = location.storeId == currentStoreId
    val typeText = if (location.isParentStore) localizedStringResource(568, "Parent warehouse") else localizedStringResource(569, "Branch warehouse")
    val stockMoveIconPath = stockBatchMovementIconPath()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(
                width = if (isCurrent) stateValues.focusedBorderWidth else stateValues.unfocusedBorderWidth,
                color = if (isCurrent) stateValues.AccentColor else stateValues.PlaceholderTextColor,
                shape = RoundedCornerShape(stateValues.cornerRadius)
            )
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    color = stateValues.TextColor,
                    fontSize = stateValues.titleTextSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = listOfNotNull(
                        typeText,
                        location.publicId.takeIf { it.isNotBlank() },
                        if (isCurrent) localizedStringResource(548, "Current location") else null
                    ).joinToString(" • "),
                    color = if (isCurrent) stateValues.AccentColor else stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = quantityText,
                color = if (location.totalQuantity?.total ?: 0.0 > 0.0) stateValues.OkayColor else stateValues.PlaceholderTextColor,
                fontSize = stateValues.titleTextSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End
            )
        }

        location.address.takeIf { it.isNotBlank() }?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = it,
                color = stateValues.TextColor,
                fontSize = stateValues.smallTextSize,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        StockCardInfoLine(
            title = stateValues.stringBatches,
            value = location.batchCount.toString(),
            textColor = stateValues.TextColor
        )

        val movableBatches = location.batches.filter {
            it.quantity.total > 0.0 &&
                    it.isActive &&
                    it.status != StockBatchStatusDataModel.Ordered &&
                    it.status != StockBatchStatusDataModel.Reserved &&
                    it.status != StockBatchStatusDataModel.InTransit
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (movableBatches.isEmpty()) {
            Text(
                text = if (isCurrent) localizedStringResource(1208, "No sendable batches here") else localizedStringResource(571, "No movable batches here"),
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize
            )
        } else {
            movableBatches.take(5).forEachIndexed { index, batch ->
                if (index > 0) Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "#${index + 1} • ${batch.quantity.quantityText(stateValues.appLanguage)}",
                            color = stateValues.TextColor,
                            fontSize = stateValues.smallTextSize,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = listOfNotNull(
                                stockBatchStatusText(batch.status),
                                batch.expirationDateMillis?.toStockDateInputText()?.takeIf { it.isNotBlank() }
                            ).joinToString(" • "),
                            color = stateValues.PlaceholderTextColor,
                            fontSize = stateValues.smallTextSize,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    onMoveBatch?.let { moveBatch ->
                        actionButton(
                            text = "",
                            fillMaxWidthIfTextPresent = false,
                            iconPath = stockMoveIconPath,
                            iconRes = stockBatchMovementIconFallback(),
                            iconContentDescription = localizedStringResource(1209, "Move this batch"),
                            confirmationRequired = false,
                            onClick = { moveBatch(batch, if (isCurrent) null else currentStoreId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun AppConfiguration.StockBatchMoveDialog(
    sourceBatch: GoodsBatchDataModel,
    availability: StockItemBranchAvailabilityDataModel?,
    preferredDestinationStoreId: String?,
    onDismiss: () -> Unit,
    onMoved: () -> Unit
) {
    val locations = availability?.locations.orEmpty()
    val sourceLocation = locations.find { it.storeId == sourceBatch.storeId }
    val destinationOptions = locations.filter { it.storeId != sourceBatch.storeId }
    var selectedDestinationId by remember(sourceBatch.id, preferredDestinationStoreId, destinationOptions.map { it.storeId }) {
        mutableStateOf(
            preferredDestinationStoreId
                ?.takeIf { preferred -> destinationOptions.any { it.storeId == preferred } }
                ?: destinationOptions.firstOrNull()?.storeId.orEmpty()
        )
    }
    val selectedDestinationLocation = destinationOptions.find { it.storeId == selectedDestinationId }
    val activeLocation = locations.find { it.storeId == stateValues.activeStoreId }
    val willRequireReceivingAcceptance = sourceLocation?.isParentStore == false &&
            selectedDestinationLocation?.isParentStore == false &&
            activeLocation?.isParentStore != true

    var quantityText by rememberSaveable(sourceBatch.id) {
        mutableStateOf(sourceBatch.quantity.total.quantityAmountText(sourceBatch.quantity.roundTotal))
    }
    var movementNoteLocalized by remember(sourceBatch.id, selectedDestinationId) {
        mutableStateOf(emptyLocalizedItemForCurrentLanguage())
    }

    val stockMoveIconPath = stockBatchMovementIconPath()
    val moveQuantityAllowsFraction = sourceBatch.quantity.allowsFractionalStockQuantityInput()
    val quantityValue = parseStockQuantityInputText(quantityText, sourceBatch.quantity) ?: 0.0
    val canMove = selectedDestinationId.isNotBlank() && quantityValue > 0.0 && quantityValue <= sourceBatch.quantity.total

    AitaBottomSheet(
        title = localizedStringResource(550, "Move batch"),
        iconPath = stockMoveIconPath,
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "${localizedStringResource(554, "Source")}: ${sourceLocation?.name?.visibleLocalizedString(stateValues.appLanguage, sourceBatch.storeId) ?: sourceBatch.storeId}",
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize
            )

            Text(
                text = sourceBatch.quantity.quantityText(stateValues.appLanguage),
                color = stateValues.AccentColor,
                fontSize = stateValues.accentTextSize,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(stateValues.marginTextField))

            genericTextField(
                modifier = Modifier.fillMaxWidth(),
                titleText = localizedStringResource(555, "Quantity to move"),
                placeholderText = stateValues.stringEnterQuantity,
                valueInitial = quantityText,
                keyboardType = if (moveQuantityAllowsFraction) KeyboardType.Decimal else KeyboardType.Number,
                onFilterValue = { text -> text.isStockQuantityInputText(moveQuantityAllowsFraction) },
                onTransformValue = { raw -> sanitizeStockQuantityInput(raw, moveQuantityAllowsFraction) },
                onContentValidityCheck = { text ->
                    parseStockQuantityInputText(text, sourceBatch.quantity)?.let { it > 0.0 && it <= sourceBatch.quantity.total } == true
                },
                contentInvalidText = stateValues.stringEnterQuantity,
                onValueChange = { value, applyChange ->
                    quantityText = value
                    applyChange()
                }
            )

            Spacer(modifier = Modifier.height(stateValues.marginTextField))

            Text(
                text = localizedStringResource(553, "Destination"),
                color = stateValues.TextColor,
                fontSize = stateValues.accentTextSize,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            if (destinationOptions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = stateValues.textFieldHeight * 2f),
                    contentAlignment = Alignment.Center
                ) {
                    MessageText(text = localizedStringResource(558, "No other locations"))
                }
            } else {
                destinationOptions.forEach { location ->
                    val selected = selectedDestinationId == location.storeId
                    val destinationName = location.name.visibleLocalizedString(stateValues.appLanguage, location.publicId.ifBlank { location.storeId })
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                            .foregroundSubtleShadow(stateValues.cornerRadius)
                            .clip(RoundedCornerShape(stateValues.cornerRadius))
                            .background(if (selected) stateValues.AccentColor else stateValues.BackgroundColor)
                            .border(
                                if (selected) stateValues.focusedBorderWidth else stateValues.unfocusedBorderWidth,
                                if (selected) stateValues.AccentColor else stateValues.PlaceholderTextColor,
                                RoundedCornerShape(stateValues.cornerRadius)
                            )
                            .aitaClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(color = if (selected) stateValues.AccentTextColor else stateValues.AccentColor)
                            ) { selectedDestinationId = location.storeId }
                            .padding(10.dp)
                    ) {
                        Text(
                            text = destinationName,
                            color = if (selected) stateValues.AccentTextColor else stateValues.TextColor,
                            fontSize = stateValues.textSize,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = listOfNotNull(
                                if (location.isParentStore) localizedStringResource(568, "Parent warehouse") else localizedStringResource(569, "Branch warehouse"),
                                location.totalQuantity?.quantityText(stateValues.appLanguage),
                                location.publicId.takeIf { it.isNotBlank() }
                            ).joinToString(" • "),
                            color = if (selected) stateValues.AccentTextColor else stateValues.PlaceholderTextColor,
                            fontSize = stateValues.smallTextSize,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (location.goodsItemId.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = localizedStringResource(565, "This item is not created in that location yet. It will be copied automatically."),
                                color = if (selected) stateValues.AccentTextColor else stateValues.AccentColor,
                                fontSize = stateValues.smallTextSize,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            if (willRequireReceivingAcceptance) {
                Spacer(modifier = Modifier.height(6.dp))
                MessageText(
                    text = localizedStringResource(
                        1211,
                        "The receiving branch will accept this batch before it becomes shelf stock."
                    )
                )
            }

            Spacer(modifier = Modifier.height(stateValues.marginTextField))

            StockLocalizedStringGroupEditor(
                title = localizedStringResource(201, "Notes"),
                placeholder = stateValues.stringOptional,
                values = movementNoteLocalized,
                addText = localizedStringResource(1071, "Add movement note translation"),
                required = false,
                singleLine = true,
                persistentKey = "stock-batch-movement-note:${sourceBatch.id}:$selectedDestinationId",
                onChanged = { movementNoteLocalized = it }
            )

            Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            actionButton(
                modifier = Modifier.weight(1f),
                text = stateValues.stringCancel,
                iconPath = stateValues.drawablePathIconCancel,
                iconRes = stateValues.drawableResIconCancel.value,
                enabledColor = stateValues.DisabledColor,
                confirmationRequired = false,
                onClick = onDismiss
            )

            actionButton(
                modifier = Modifier.weight(1f),
                text = if (willRequireReceivingAcceptance) localizedStringResource(1210, "Send en route") else localizedStringResource(549, "Move"),
                enabled = canMove,
                iconPath = stockMoveIconPath,
                iconRes = stockBatchMovementIconFallback(),
                confirmationRequired = false,
                onClick = {
                    moveStockBatchBetweenStores(
                        StockBatchMoveRequestDataModel(
                            sourceStoreId = sourceBatch.storeId,
                            destinationStoreId = selectedDestinationId,
                            sourceGoodsItemId = sourceBatch.goodsItemId,
                            sourceBatchId = sourceBatch.id,
                            quantity = sourceBatch.quantity.withStockQuantityInputTotalValue(quantityValue),
                            note = movementNoteLocalized.toStoredLocalizedNoteOrNull(),
                            actorStoreId = stateValues.activeStoreId
                        )
                    ) { state ->
                        if (state is DataState.Success) {
                            onMoved()
                            onDismiss()
                        }
                    }
                }
            )
        }
    }
}

@Composable
internal fun AppConfiguration.StockBranchAvailabilitySection(
    goodsItem: GoodsItemDataModel,
    availability: StockItemBranchAvailabilityDataModel?,
    onMoveBatch: ((GoodsBatchDataModel, String?) -> Unit)?
) {
    val currentStoreId = stateValues.activeStoreId ?: goodsItem.storeId
    val locations = availability?.locations.orEmpty()
    val currentLocation = locations.find { it.storeId == currentStoreId }
    val otherLocations = locations.filter { it.storeId != currentStoreId }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = localizedStringResource(546, "Branch stock"),
                    color = stateValues.TextColor,
                    fontSize = stateValues.titleTextSize,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = localizedStringResource(557, "Available in this store entity"),
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize
                )
            }
            actionButton(
                text = "",
                iconPath = stateValues.drawablePathIconSwitch,
                iconContentDescription = localizedStringResource(561, "Refresh availability"),
                confirmationRequired = false,
                onClick = { getStockItemBranchAvailability(currentStoreId, goodsItem.id) }
            )
        }

        Spacer(modifier = Modifier.height(stateValues.marginTextField))

        if (locations.isEmpty()) {
            MessageText(text = stateValues.stringListEmpty)
        } else {
            currentLocation?.let {
                StockLocationAvailabilityCard(
                    location = it,
                    currentStoreId = currentStoreId,
                    onMoveBatch = onMoveBatch
                )
                Spacer(modifier = Modifier.height(stateValues.marginTextField))
            }

            if (otherLocations.isEmpty()) {
                MessageText(text = localizedStringResource(558, "No other locations"))
            } else {
                otherLocations.forEach { location ->
                    StockLocationAvailabilityCard(
                        location = location,
                        currentStoreId = currentStoreId,
                        onMoveBatch = onMoveBatch
                    )
                    Spacer(modifier = Modifier.height(stateValues.marginTextField))
                }
            }
        }

        availability?.movements?.takeIf { it.isNotEmpty() }?.let { movements ->
            Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))
            Text(
                text = localizedStringResource(559, "Movement history"),
                color = stateValues.TextColor,
                fontSize = stateValues.accentTextSize,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(stateValues.marginTextField))
            movements.take(5).forEach { movement ->
                val source = locations.find { it.storeId == movement.sourceStoreId }
                val destination = locations.find { it.storeId == movement.destinationStoreId }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .foregroundSubtleShadow(stateValues.cornerRadius)
                        .clip(RoundedCornerShape(stateValues.cornerRadius))
                        .background(stateValues.BackgroundColor)
                        .border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor, RoundedCornerShape(stateValues.cornerRadius))
                        .padding(10.dp)
                ) {
                    Text(
                        text = movement.quantity.quantityText(stateValues.appLanguage),
                        color = stateValues.AccentColor,
                        fontSize = stateValues.textSize,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${localizedStringResource(562, "From")}: ${source?.name?.visibleLocalizedString(stateValues.appLanguage, movement.sourceStoreId) ?: movement.sourceStoreId}",
                        color = stateValues.TextColor,
                        fontSize = stateValues.smallTextSize,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${localizedStringResource(563, "To")}: ${destination?.name?.visibleLocalizedString(stateValues.appLanguage, movement.destinationStoreId) ?: movement.destinationStoreId}",
                        color = stateValues.TextColor,
                        fontSize = stateValues.smallTextSize,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = listOfNotNull(
                            movement.movedAtMillis.toStockDateInputText(),
                            movement.movedByName.takeIf { it.isNotBlank() }?.let { "${localizedStringResource(560, "Moved by")}: $it" }
                        ).joinToString(" • "),
                        color = stateValues.PlaceholderTextColor,
                        fontSize = stateValues.smallTextSize
                    )
                    movement.note?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            color = stateValues.PlaceholderTextColor,
                            fontSize = stateValues.smallTextSize
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun AppConfiguration.IncomingStockBatchTransferCard(
    batch: GoodsBatchDataModel,
    movement: StockBatchMovementDataModel?,
    availability: StockItemBranchAvailabilityDataModel?,
    canDecide: Boolean,
    onDecided: () -> Unit
) {
    val source = movement?.sourceStoreId?.let { sourceId -> availability?.locations.orEmpty().find { it.storeId == sourceId } }
    val destination = movement?.destinationStoreId?.let { destinationId -> availability?.locations.orEmpty().find { it.storeId == destinationId } }
    val supplierName = stateValues.suppliers
        .orEmpty()
        .find { it.id == batch.supplierId }
        ?.name
        ?.extractLocalizedString(stateValues.appLanguage)
        ?: localizedStringResource(638, "No supplier selected")
    val sourceName = source?.name?.visibleLocalizedString(stateValues.appLanguage, movement?.sourceStoreId.orEmpty())
        ?: movement?.sourceStoreId.orEmpty()
    val destinationName = destination?.name?.visibleLocalizedString(stateValues.appLanguage, movement?.destinationStoreId.orEmpty())
        ?: movement?.destinationStoreId.orEmpty()
    var deciding by remember(batch.id, movement?.id) { mutableStateOf(false) }
    val stockMoveIconPath = stockBatchMovementIconPath()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(stateValues.focusedBorderWidth, stateValues.AccentColor, RoundedCornerShape(stateValues.cornerRadius))
            .padding(stateValues.marginTextFieldGroup)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(stateValues.AccentColor),
                contentAlignment = Alignment.Center
            ) {
                CpImage(
                    modifier = Modifier.size(24.dp),
                    url = stockMoveIconPath,
                    fallbackRes = stockBatchMovementIconFallback(),
                    contentDescription = localizedStringResource(1190, "Incoming transfer"),
                    tintColor = stateValues.AccentTextColor
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = localizedStringResource(1191, "Incoming batch en route"),
                    color = stateValues.TextColor,
                    fontSize = stateValues.accentTextSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOfNotNull(
                        movement?.movedAtMillis?.toStockDateInputText(),
                        movement?.movedByName?.takeIf { it.isNotBlank() }
                    ).joinToString(" • ").ifBlank { localizedStringResource(1192, "Waiting for acceptance") },
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = batch.quantity.quantityText(stateValues.appLanguage),
                color = stateValues.AccentColor,
                fontSize = stateValues.accentTextSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        StockCardInfoLine(
            title = localizedStringResource(562, "From"),
            value = sourceName,
            textColor = stateValues.TextColor
        )
        StockCardInfoLine(
            title = localizedStringResource(563, "To"),
            value = destinationName,
            textColor = stateValues.TextColor
        )
        StockCardInfoLine(
            title = stateValues.stringSupplier,
            value = supplierName,
            textColor = stateValues.TextColor
        )
        batch.expirationDateMillis?.toStockDateInputText()?.takeIf { it.isNotBlank() }?.let {
            StockCardInfoLine(
                title = localizedStringResource(202, "Expiration"),
                value = it,
                textColor = stateValues.TextColor
            )
        }
        movement?.note?.takeIf { it.isNotBlank() }?.let {
            StockCardInfoLine(
                title = localizedStringResource(201, "Notes"),
                value = it,
                textColor = stateValues.TextColor
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (movement == null) {
            MessageText(text = localizedStringResource(1193, "Transfer metadata is still loading. Refresh branch stock."))
        } else if (!canDecide) {
            MessageText(text = localizedStringResource(665, "You do not have permission for this action"))
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                actionButton(
                    modifier = Modifier.weight(1f),
                    text = localizedStringResource(1194, "Decline"),
                    iconPath = stateValues.drawablePathIconCancel,
                    enabled = !deciding,
                    enabledColor = stateValues.ErrorColor,
                    confirmationRequired = true,
                    onClick = {
                        deciding = true
                        decideStockBatchMove(
                            StockBatchMoveDecisionRequestDataModel(
                                movementId = movement.id,
                                accept = false
                            )
                        ) { state ->
                            deciding = false
                            if (state is DataState.Success) onDecided()
                        }
                    }
                )
                actionButton(
                    modifier = Modifier.weight(1f),
                    text = localizedStringResource(1195, "Accept"),
                    iconPath = stateValues.drawablePathIconCheck,
                    enabled = !deciding,
                    enabledColor = stateValues.OkayColor,
                    confirmationRequired = true,
                    onClick = {
                        deciding = true
                        decideStockBatchMove(
                            StockBatchMoveDecisionRequestDataModel(
                                movementId = movement.id,
                                accept = true
                            )
                        ) { state ->
                            deciding = false
                            if (state is DataState.Success) onDecided()
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun AppConfiguration.StockAddEditBatchesPage(
    modifier: Modifier = Modifier,
    goodsItem: GoodsItemDataModel?,
    startAddingBatch: Boolean = false,
    onStartAddingBatchConsumed: () -> Unit = {}
) {
    if (goodsItem == null || goodsItem.id.isBlank()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            MessageText(
                text = localizedStringResource(196, "Save the goods item first, then you can add batches.")
            )
        }

        return
    }

    val addEditState by NavigationScreenModel.Stock.AddEditGoodsItem.state.collectAsState()
    val modeKey = "stock_batches_mode_${goodsItem.id}"
    val editIdKey = "stock_batches_edit_id_${goodsItem.id}"
    val currentMode = addEditState[modeKey] ?: "list"
    val currentEditId = addEditState[editIdKey]
    val activeStoreIdForBatches = stateValues.activeStoreId ?: goodsItem.storeId
    val canCreateBatch = currentUserHasStorePermission(activeStoreIdForBatches, STORE_PERMISSION_STOCK_BATCH_CREATE)
    val canEditBatch = currentUserHasStorePermission(activeStoreIdForBatches, STORE_PERMISSION_STOCK_BATCH_EDIT)
    val canDeleteBatch = currentUserHasStorePermission(activeStoreIdForBatches, STORE_PERMISSION_STOCK_BATCH_DELETE)
    val canMoveBatch = currentUserHasStorePermission(activeStoreIdForBatches, STORE_PERMISSION_STOCK_BATCH_MOVE)
    val canDecideBatchTransfers = currentUserHasStorePermission(activeStoreIdForBatches, STORE_PERMISSION_STOCK_BATCH_TRANSFER_DECIDE)
    val canSetActiveShelfBatch = currentUserHasStorePermission(activeStoreIdForBatches, STORE_PERMISSION_STOCK_BATCH_SET_ACTIVE_SHELF)

    val batches = stateValues.stockBatches
        .orEmpty()
        .filter {
            it.goodsItemId == goodsItem.id && it.isActive && it.status != StockBatchStatusDataModel.InTransit
        }
        .sortedForShelf(goodsItem)

    val editingBatch = batches.find { it.id == currentEditId }
    val addingBatch = currentMode == "add"
    val editing = currentMode == "edit" && editingBatch != null
    val draftStateKey = "stock_batches_draft_${goodsItem.id}_${editingBatch?.id ?: "add"}"

    fun closeBatchEditor(clearDraft: Boolean) {
        coroutineScope.launch {
            NavigationScreenModel.Stock.AddEditGoodsItem.setState(modeKey to "list")
            NavigationScreenModel.Stock.AddEditGoodsItem.removeState(editIdKey)
            if (clearDraft) {
                NavigationScreenModel.Stock.AddEditGoodsItem.removeState(draftStateKey)
            }
        }
    }

    LaunchedEffect(startAddingBatch, canCreateBatch) {
        if (startAddingBatch) {
            if (canCreateBatch) {
                NavigationScreenModel.Stock.AddEditGoodsItem.setState(modeKey to "add")
                NavigationScreenModel.Stock.AddEditGoodsItem.removeState(editIdKey)
            } else {
                postInAppNotification(currentUserPermissionDeniedMessage(), NotificationType.Negative, transient = true)
            }
            onStartAddingBatchConsumed()
        }
    }

    if (addingBatch && !canCreateBatch) {
        MessageText(
            modifier = modifier.fillMaxSize(),
            text = localizedStringResource(665, "You do not have permission for this action")
        )
        return
    }

    if (editing && !canEditBatch) {
        MessageText(
            modifier = modifier.fillMaxSize(),
            text = localizedStringResource(665, "You do not have permission for this action")
        )
        return
    }

    if (addingBatch || editing) {
        StockBatchEditor(
            modifier = modifier,
            goodsItem = goodsItem,
            existingBatch = editingBatch,
            draftStateKey = draftStateKey,
            onCancel = {
                closeBatchEditor(clearDraft = false)
            },
            onSaved = {
                closeBatchEditor(clearDraft = true)
            }
        )

        return
    }

    var draggedBatchId by remember(goodsItem.id) { mutableStateOf<String?>(null) }
    var dragTargetIndex by remember(goodsItem.id) { mutableStateOf<Int?>(null) }
    val incomingBatches = stateValues.stockBatches
        .orEmpty()
        .filter {
            it.goodsItemId == goodsItem.id &&
                    it.storeId == activeStoreIdForBatches &&
                    it.isActive &&
                    it.status == StockBatchStatusDataModel.InTransit
        }
        .sortedWith(compareBy<GoodsBatchDataModel> { it.deliveredAtMillis ?: it.createdAtMillis }.thenBy { it.id })
    val availabilityPayload by stockItemBranchAvailabilityState.payload.collectAsState()
    val branchAvailability = availabilityPayload?.takeIf { availability ->
        availability.sourceGoodsItemId == goodsItem.id || availability.locations.any { it.goodsItemId == goodsItem.id }
    }
    var movingBatch by remember(goodsItem.id) { mutableStateOf<GoodsBatchDataModel?>(null) }
    var preferredDestinationStoreId by remember(goodsItem.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(goodsItem.id, activeStoreIdForBatches) {
        getStockItemBranchAvailability(activeStoreIdForBatches, goodsItem.id)
    }

    movingBatch?.let { batch ->
        StockBatchMoveDialog(
            sourceBatch = batch,
            availability = branchAvailability,
            preferredDestinationStoreId = preferredDestinationStoreId,
            onDismiss = {
                movingBatch = null
                preferredDestinationStoreId = null
            },
            onMoved = {
                getStockItemBranchAvailability(activeStoreIdForBatches, goodsItem.id)
            }
        )
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(stateValues.marginTextField)
        ) {
            item {
                StockBranchAvailabilitySection(
                    goodsItem = goodsItem,
                    availability = branchAvailability,
                    onMoveBatch = if (canMoveBatch) {
                        { batch, preferredDestination ->
                            movingBatch = batch
                            preferredDestinationStoreId = preferredDestination
                        }
                    } else null
                )

                Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

                if (incomingBatches.isNotEmpty()) {
                    Text(
                        text = localizedStringResource(1196, "Incoming batches"),
                        color = stateValues.TextColor,
                        fontSize = stateValues.titleTextSize,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(stateValues.marginTextField))

                    incomingBatches.forEach { incomingBatch ->
                        val movement = branchAvailability?.movements.orEmpty()
                            .firstOrNull {
                                it.destinationBatchId == incomingBatch.id &&
                                        it.status == StockBatchMovementStatusDataModel.PendingAcceptance
                            }

                        IncomingStockBatchTransferCard(
                            batch = incomingBatch,
                            movement = movement,
                            availability = branchAvailability,
                            canDecide = canDecideBatchTransfers,
                            onDecided = {
                                getStockItemBranchAvailability(activeStoreIdForBatches, goodsItem.id)
                            }
                        )

                        Spacer(modifier = Modifier.height(stateValues.marginTextField))
                    }

                    Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))
                }

                Text(
                    text = localizedStringResource(197, "Shelf order"),
                    color = stateValues.TextColor,
                    fontSize = stateValues.titleTextSize,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(stateValues.marginTextField))

                Text(
                    text = localizedStringResource(198, "The first batch is the active shelf batch. Long-press and drag a batch up or down to change shelf order."),
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize
                )

                Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))
            }

            if (batches.isEmpty()) {
                item {
                    MessageText(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = stateValues.marginTextFieldGroup),
                        text = localizedStringResource(199, "No batches yet")
                    )
                }
            } else {
                itemsIndexed(batches, key = { _, batch -> batch.id }) { index, batch ->
                    StockBatchCard(
                        batch = batch,
                        activeShelfBatchId = goodsItem.activeShelfBatchId,
                        shelfIndex = index,
                        compact = false,
                        draggedBatchId = draggedBatchId,
                        draggedBatchIndex = draggedBatchId?.let { id -> batches.indexOfFirst { it.id == id }.takeIf { it >= 0 } },
                        dragTargetIndex = dragTargetIndex,
                        allBatchesCount = batches.size,
                        onEdit = if (canEditBatch) {
                            {
                                coroutineScope.launch {
                                    NavigationScreenModel.Stock.AddEditGoodsItem.setState(modeKey to "edit")
                                    NavigationScreenModel.Stock.AddEditGoodsItem.setState(editIdKey to batch.id)
                                }
                            }
                        } else null,
                        onDelete = if (canDeleteBatch) {
                            {
                                stateValues.activeStoreId?.let { storeId ->
                                    deleteGoodsBatches(
                                        ids = listOf(batch.id),
                                        storeId = storeId,
                                        onCompleted = null
                                    )
                                }
                            }
                        } else null,
                        onSetActiveShelf = if (canSetActiveShelfBatch) {
                            {
                                stateValues.activeStoreId?.let { storeId ->
                                    setActiveShelfBatch(
                                        batch = batch,
                                        storeId = storeId,
                                        previousActiveShelfBatchId = goodsItem.activeShelfBatchId
                                    )
                                }
                            }
                        } else null,
                        onDragStart = if (canSetActiveShelfBatch) {
                            { id ->
                                draggedBatchId = id
                                dragTargetIndex = index
                            }
                        } else null,
                        onDragTargetChanged = if (canSetActiveShelfBatch) {
                            { target ->
                                dragTargetIndex = target
                            }
                        } else null,
                        onDragFinished = if (canSetActiveShelfBatch) {
                            { target ->
                                val from = index
                                draggedBatchId = null
                                dragTargetIndex = null
                                if (from != target) {
                                    reorderShelfBatches(
                                        goodsItem = goodsItem,
                                        batches = batches,
                                        fromIndex = from,
                                        toIndex = target
                                    )
                                }
                            }
                        } else null,
                        onDragCancelled = {
                            draggedBatchId = null
                            dragTargetIndex = null
                        }
                    )

                    if (canMoveBatch && batch.quantity.total > 0.0 && branchAvailability?.locations.orEmpty().any { it.storeId != batch.storeId }) {
                        Spacer(modifier = Modifier.height(6.dp))
                        actionButton(
                            modifier = Modifier.fillMaxWidth(),
                            text = localizedStringResource(567, "Export to another location"),
                            iconPath = stateValues.drawablePathIconSwitch,
                            confirmationRequired = false,
                            onClick = {
                                movingBatch = batch
                                preferredDestinationStoreId = null
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(stateValues.marginTextField))
                }
            }

            item {
                Spacer(modifier = Modifier.height(stateValues.screenHeight / 5))
            }
        }

        if (canCreateBatch) {
            actionButton(
                modifier = Modifier.padding(8.dp),
                text = localizedStringResource(194, "Add batch"),
                iconPath = stateValues.drawablePathIconAdd,
                onClick = {
                    coroutineScope.launch {
                        NavigationScreenModel.Stock.AddEditGoodsItem.setState(modeKey to "add")
                        NavigationScreenModel.Stock.AddEditGoodsItem.removeState(editIdKey)
                    }
                }
            )
        }
    }
}

@Composable
fun AppConfiguration.StockSupplierPricesPage(
    modifier: Modifier = Modifier,
    goodsItem: GoodsItemDataModel?
) {
    if (goodsItem == null || goodsItem.id.isBlank()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            MessageText(
                text = localizedStringResource(333, "Save the goods item first, then supplier prices will appear.")
            )
        }

        return
    }

    val storeId = stateValues.activeStoreId ?: goodsItem.storeId
    val canManageSupplierPrices = currentUserHasStorePermission(storeId, STORE_PERMISSION_SUPPLIER_PRICES_MANAGE)
    val canManageSuppliersHere = currentUserCanManageSuppliers(storeId)
    val suppliers = stateValues.suppliers.orEmpty()
    val supplierPricesPayload by supplierGoodsPricesState.payload.collectAsState()
    val allSupplierPrices = supplierPricesPayload.orEmpty()
    val supplierPrices = allSupplierPrices
        .filter { it.goodsItemId == goodsItem.id && it.isActive }

    val defaultCurrency = goodsItem.supplyPrices.firstOrNull()?.currency
        ?: stateValues.globalAppConfiguration.countries
            .find { it.locale.equals(stateValues.userAccount?.countryLocale, true) }
            ?.currencies
            ?.firstOrNull()
            ?.code
        ?: stateValues.globalAppConfiguration.countries
            .firstOrNull()
            ?.currencies
            ?.firstOrNull()
            ?.code
        ?: "KZT"
    val defaultUnit = stateValues.globalAppConfiguration.goodsItemsQuantityUnits
        .find { it.id == goodsItem.measurementUnitId }
        ?: stateValues.globalAppConfiguration.goodsItemsQuantityUnits.firstOrNull()
    val supplierQuantityUnit = defaultUnit ?: QuantityDataModel(
        id = goodsItem.measurementUnitId,
        immutableUnitName = emptyList(),
        total = 1.0,
        pricedAmount = 1.0,
        roundTotal = goodsItem.measurementUnitId == "0"
    )
    val supplierQuantityAllowsFraction = supplierQuantityUnit.allowsFractionalStockQuantityInput()

    var searchText by rememberSaveable(goodsItem.id) { mutableStateOf("") }
    var selectedSupplierId by rememberSaveable(goodsItem.id) { mutableStateOf(suppliers.firstOrNull()?.id.orEmpty()) }
    var supplierGoodsName by rememberSaveable(goodsItem.id) { mutableStateOf("") }
    var supplierBarcode by rememberSaveable(goodsItem.id) { mutableStateOf("") }
    var supplyPriceText by rememberSaveable(goodsItem.id, defaultCurrency) { mutableStateOf(goodsItem.supplyPrices.firstOrNull()?.price ?: "0") }
    var minOrderText by rememberSaveable(goodsItem.id) { mutableStateOf("") }
    var packageText by rememberSaveable(goodsItem.id) { mutableStateOf("") }
    var showSupplierAddSheet by rememberSaveable(goodsItem.id) { mutableStateOf(false) }

    if (showSupplierAddSheet) {
        QuickSupplierAddBottomSheet(
            onDismiss = { showSupplierAddSheet = false },
            onSaved = { supplier ->
                selectedSupplierId = supplier.id
                showSupplierAddSheet = false
            }
        )
    }

    LaunchedEffect(suppliers.map { it.id }, selectedSupplierId) {
        if (selectedSupplierId.isBlank() && suppliers.isNotEmpty()) {
            selectedSupplierId = suppliers.first().id
        }
    }

    LaunchedEffect(selectedSupplierId, supplierPrices.map { it.id to it.supplyPrice.price }) {
        val remembered = supplierPrices.find { it.supplierId == selectedSupplierId }
        remembered?.let {
            supplierGoodsName = it.supplierGoodsName.orEmpty()
            supplierBarcode = it.supplierBarcode.orEmpty()
            supplyPriceText = it.supplyPrice.price
            minOrderText = it.minOrderQuantity
                ?.let { quantity -> stockQuantityInputTextFromAmount(quantity.total, quantity) }
                .orEmpty()
            packageText = it.packageQuantity
                ?.let { quantity -> stockQuantityInputTextFromAmount(quantity.total, quantity) }
                .orEmpty()
        }
    }

    fun supplierName(id: String): String {
        return suppliers
            .find { it.id == id }
            ?.name
            ?.visibleLocalizedString(stateValues.appLanguage, id)
            ?: id
    }

    fun priceSearchText(price: SupplierGoodsPriceDataModel): String {
        return listOf(
            supplierName(price.supplierId),
            price.supplierId,
            price.supplierGoodsName.orEmpty(),
            price.supplierBarcode.orEmpty(),
            price.supplyPrice.price,
            price.supplyPrice.currency,
            price.minOrderQuantity?.total?.toString().orEmpty(),
            price.packageQuantity?.total?.toString().orEmpty(),
            price.lastUsedAtMillis?.toStockDateInputText().orEmpty(),
            price.createdAtMillis.takeIf { it > 0L }?.toStockDateInputText().orEmpty(),
            price.updatedAtMillis.takeIf { it > 0L }?.toStockDateInputText().orEmpty()
        ).joinToString(" ")
    }

    val query = searchText.trim()
    val visibleSupplierPrices = supplierPrices
        .filter { query.isBlank() || priceSearchText(it).contains(query, ignoreCase = true) }
        .sortedWith(
            compareByDescending<SupplierGoodsPriceDataModel> { it.lastUsedAtMillis ?: it.updatedAtMillis }
                .thenBy { supplierName(it.supplierId) }
        )

    val rememberedSupplierIds = supplierPrices.map { it.supplierId }.toSet()
    val supplierOptions = suppliers
        .sortedBy { it.name.visibleGoodsCategoryName(stateValues.appLanguage, it.id) }
        .map {
            DropdownOption(
                id = it.id,
                title = it.name.visibleGoodsCategoryName(stateValues.appLanguage, it.id),
                subtitle = if (it.id in rememberedSupplierIds) localizedStringResource(800, "Remembered price exists") else localizedStringResource(801, "New supplier price")
            )
        }

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(stateValues.marginTextField)
        ) {
            item {
                Text(
                    text = localizedStringResource(203, "Supplier prices"),
                    color = stateValues.TextColor,
                    fontSize = stateValues.titleTextSize,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = localizedStringResource(802, "Remember default supply prices for this goods item per supplier. These prices can prefill new batches later."),
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize
                )

                Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

                val supplierSearchTextFieldContent = searchTextField(
                    valueInitial = searchText,
                    stateHost = NavigationScreenModel.Stock.AddEditGoodsItem,
                    stateKey = "supplier_prices_search_${goodsItem.id}",
                    modifier = Modifier.fillMaxWidth(),
                    updateIsFocusedAction = null
                )

                LaunchedEffect(supplierSearchTextFieldContent.value) {
                    searchText = supplierSearchTextFieldContent.value.text
                }

                Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))
            }

            if (canManageSupplierPrices) item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(2.dp)
                        .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
                        .clip(RoundedCornerShape(stateValues.cornerRadius))
                        .background(stateValues.BackgroundColor)
                        .border(
                            stateValues.unfocusedBorderWidth,
                            stateValues.PlaceholderTextColor,
                            RoundedCornerShape(stateValues.cornerRadius)
                        )
                        .padding(stateValues.marginTextFieldGroup)
                ) {
                    Text(
                        text = localizedStringResource(334, "Add / update supplier price"),
                        color = stateValues.TextColor,
                        fontSize = stateValues.accentTextSize,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(stateValues.marginTextField))

                    if (supplierOptions.isEmpty()) {
                        MessageText(
                            text = localizedStringResource(335, "No suppliers yet. Add suppliers first, then connect them to this goods item.")
                        )
                    } else {
                        SimpleDropdownField(
                            title = stateValues.stringSupplier,
                            selectedId = selectedSupplierId,
                            options = supplierOptions,
                            placeholder = stateValues.stringSupplier,
                            onSelected = { selectedSupplierId = it }
                        )

                        Spacer(modifier = Modifier.height(stateValues.marginTextField))

                        if (canManageSuppliersHere) {
                            actionButton(
                                text = localizedStringResource(635, "Add supplier here"),
                                iconPath = stateValues.drawablePathIconAdd,
                                confirmationRequired = false,
                                onClick = { showSupplierAddSheet = true }
                            )

                            Spacer(modifier = Modifier.height(stateValues.marginTextField))
                        }

                        SimpleTextInput(
                            modifier = Modifier.fillMaxWidth(),
                            value = supplierGoodsName,
                            placeholder = localizedStringResource(336, "Supplier goods name / article"),
                            leadingIconPath = stateValues.drawablePathIconStock,
                            onValueChange = { supplierGoodsName = it }
                        )

                        Spacer(modifier = Modifier.height(stateValues.marginTextField))

                        BarcodeTextInput(
                            modifier = Modifier.fillMaxWidth(),
                            value = supplierBarcode,
                            placeholderText = stateValues.stringBarcode,
                            onValueChange = { supplierBarcode = it }
                        )

                        Spacer(modifier = Modifier.height(stateValues.marginTextField))

                        SimpleTextInput(
                            modifier = Modifier.fillMaxWidth(),
                            value = supplyPriceText,
                            placeholder = stateValues.stringSupplyPrice,
                            keyboardType = KeyboardType.Decimal,
                            leadingIconPath = stateValues.drawablePathIconFinances,
                            onTransformValue = { raw -> raw.filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.') },
                            onValueChange = { supplyPriceText = it }
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            goodsItem.supplyPrices
                                .filter { it.price.isNotBlank() }
                                .distinctBy { it.price to it.currency }
                                .take(4)
                                .forEach { price ->
                                    actionButton(
                                        modifier = Modifier.weight(1f),
                                        text = "${price.price} ${price.currency}",
                                        textSize = stateValues.smallTextSize,
                                        iconPath = stateValues.drawablePathIconFinances,
                                        onClick = { supplyPriceText = price.price }
                                    )
                                }
                        }

                        Spacer(modifier = Modifier.height(stateValues.marginTextField))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                        ) {
                            SimpleTextInput(
                                modifier = Modifier.weight(1f),
                                value = minOrderText,
                                placeholder = localizedStringResource(337, "Min order"),
                                keyboardType = if (supplierQuantityAllowsFraction) KeyboardType.Decimal else KeyboardType.Number,
                                leadingIconPath = stateValues.drawablePathIconStock,
                                onTransformValue = { raw -> sanitizeStockQuantityInput(raw, supplierQuantityAllowsFraction) },
                                onValueChange = { value ->
                                    if (value.isStockQuantityInputText(supplierQuantityAllowsFraction)) {
                                        minOrderText = value
                                    }
                                }
                            )

                            SimpleTextInput(
                                modifier = Modifier.weight(1f),
                                value = packageText,
                                placeholder = localizedStringResource(338, "Package qty"),
                                keyboardType = if (supplierQuantityAllowsFraction) KeyboardType.Decimal else KeyboardType.Number,
                                leadingIconPath = stateValues.drawablePathIconStock,
                                onTransformValue = { raw -> sanitizeStockQuantityInput(raw, supplierQuantityAllowsFraction) },
                                onValueChange = { value ->
                                    if (value.isStockQuantityInputText(supplierQuantityAllowsFraction)) {
                                        packageText = value
                                    }
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

                        actionButton(
                            text = localizedStringResource(339, "Save supplier price"),
                            iconPath = stateValues.drawablePathIconCheck,
                            enabled = selectedSupplierId.isNotBlank() && supplyPriceText.toDoubleOrNull() != null,
                            onClick = {
                                val unit = supplierQuantityUnit
                                upsertSupplierGoodsPrice(
                                    SupplierGoodsPriceDataModel(
                                        storeId = storeId,
                                        supplierId = selectedSupplierId,
                                        goodsItemId = goodsItem.id,
                                        supplyPrice = PriceDataModel(
                                            price = supplyPriceText.ifBlank { "0" },
                                            currency = defaultCurrency,
                                            supplierId = selectedSupplierId
                                        ),
                                        minOrderQuantity = parseStockQuantityInputText(minOrderText, unit)
                                            ?.takeIf { it > 0.0 }
                                            ?.let { total -> unit.withStockQuantityInputTotalValue(total) },
                                        packageQuantity = parseStockQuantityInputText(packageText, unit)
                                            ?.takeIf { it > 0.0 }
                                            ?.let { total -> unit.withStockQuantityInputTotalValue(total) },
                                        supplierBarcode = supplierBarcode.takeIf { it.isNotBlank() },
                                        supplierGoodsName = supplierGoodsName.takeIf { it.isNotBlank() }
                                    )
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))
            }

            item {
                Text(
                    text = "${localizedStringResource(803, "Remembered supplier prices")} (${visibleSupplierPrices.size})",
                    color = stateValues.TextColor,
                    fontSize = stateValues.accentTextSize,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(stateValues.marginTextField))
            }

            if (visibleSupplierPrices.isEmpty()) {
                item {
                    MessageText(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = stateValues.marginTextFieldGroup),
                        text = if (supplierPrices.isEmpty()) {
                            localizedStringResource(804, "No supplier prices yet. Add one above or add a batch with supplier price.")
                        } else {
                            localizedStringResource(805, "No supplier prices match this search.")
                        }
                    )
                }
            } else {
                items(visibleSupplierPrices) { supplierPrice ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(2.dp)
                            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
                            .clip(RoundedCornerShape(stateValues.cornerRadius))
                            .background(stateValues.BackgroundColor)
                            .border(
                                stateValues.unfocusedBorderWidth,
                                stateValues.PlaceholderTextColor,
                                RoundedCornerShape(stateValues.cornerRadius)
                            )
                            .run {
                                if (canManageSupplierPrices) {
                                    aitaClickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(color = stateValues.AccentColor)
                                    ) {
                                        selectedSupplierId = supplierPrice.supplierId
                                        supplierGoodsName = supplierPrice.supplierGoodsName.orEmpty()
                                        supplierBarcode = supplierPrice.supplierBarcode.orEmpty()
                                        supplyPriceText = supplierPrice.supplyPrice.price
                                        minOrderText = supplierPrice.minOrderQuantity
                                            ?.let { quantity -> stockQuantityInputTextFromAmount(quantity.total, quantity) }
                                            .orEmpty()
                                        packageText = supplierPrice.packageQuantity
                                            ?.let { quantity -> stockQuantityInputTextFromAmount(quantity.total, quantity) }
                                            .orEmpty()
                                    }
                                } else this
                            }
                            .padding(stateValues.marginTextFieldGroup)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CpImage(
                                modifier = Modifier.size(22.dp),
                                url = stateValues.drawablePathIconSuppliers,
                                fallbackRes = Res.drawable._0_0,
                                contentDescription = supplierName(supplierPrice.supplierId),
                                tintColor = stateValues.AccentColor
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                modifier = Modifier.weight(1f),
                                text = supplierName(supplierPrice.supplierId),
                                color = stateValues.TextColor,
                                fontSize = stateValues.textSize,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Text(
                                text = "${supplierPrice.supplyPrice.price} ${supplierPrice.supplyPrice.currency}",
                                color = stateValues.AccentColor,
                                fontSize = stateValues.accentTextSize,
                                fontWeight = FontWeight.Bold,
                                style = TextStyle(shadow = accentTextShadow(stateValues.AccentColor, stateValues.AccentColor))
                            )
                        }

                        supplierPrice.supplierGoodsName?.takeIf { it.isNotBlank() }?.let {
                            StockCardInfoLine(localizedStringResource(806, "Goods name"), it, stateValues.TextColor)
                        }

                        supplierPrice.supplierBarcode?.takeIf { it.isNotBlank() }?.let {
                            StockCardInfoLine(stateValues.stringBarcode, it, stateValues.TextColor)
                        }

                        supplierPrice.minOrderQuantity?.let { quantity ->
                            StockCardInfoLine(
                                localizedStringResource(337, "Min order"),
                                quantity.quantityText(stateValues.appLanguage),
                                stateValues.TextColor
                            )
                        }

                        supplierPrice.packageQuantity?.let { quantity ->
                            StockCardInfoLine(
                                localizedStringResource(338, "Package qty"),
                                quantity.quantityText(stateValues.appLanguage),
                                stateValues.TextColor
                            )
                        }

                        supplierPrice.lastUsedAtMillis?.takeIf { it > 0L }?.let {
                            StockCardInfoLine(localizedStringResource(807, "Last used"), it.toStockDateInputText(), stateValues.TextColor)
                        }

                        supplierPrice.updatedAtMillis.takeIf { it > 0L }?.let {
                            StockCardInfoLine(localizedStringResource(808, "Updated"), it.toStockDateInputText(), stateValues.TextColor)
                        }
                    }

                    Spacer(modifier = Modifier.height(stateValues.marginTextField))
                }
            }

            item {
                Spacer(modifier = Modifier.height(stateValues.screenHeight / 5))
            }
        }
    }
}

@Composable
fun AppConfiguration.StockAddEditNotesPage(
    modifier: Modifier = Modifier,
    draft: StockAddEditDraft,
    onDraftChanged: (StockAddEditDraft) -> Unit
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(stateValues.marginTextField)
    ) {
        item {
            Text(
                text = localizedStringResource(201, "Notes"),
                color = stateValues.TextColor,
                fontSize = stateValues.titleTextSize,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

            StockLocalizedStringGroupEditor(
                title = localizedStringResource(201, "Notes"),
                placeholder = stateValues.stringOptional,
                values = draft.noteLocalized.ifEmpty {
                    draft.note.takeIf { it.isNotBlank() }?.let { listOf(LocalizedStringDataModel("main", it)) }
                        ?: emptyLocalizedItemForCurrentLanguage()
                },
                addText = localizedStringResource(307, "Add note translation"),
                required = false,
                singleLine = false,
                adaptiveMultiline = true,
                persistentKey = "stock-add-edit-notes-page",
                onChanged = { notes ->
                    onDraftChanged(
                        draft.copy(
                            noteLocalized = notes,
                            note = notes.extractLocalizedString("main")
                                ?: notes.firstOrNull { item -> item.value.isNotBlank() }?.value.orEmpty()
                        )
                    )
                }
            )

            Spacer(modifier = Modifier.height(stateValues.screenHeight / 5))
        }
    }
}

@Composable
fun AppConfiguration.StockAddEditPricesPage(
    modifier: Modifier = Modifier,
    draft: StockAddEditDraft,
    onDraftChanged: (StockAddEditDraft) -> Unit
) {
    val defaultCurrency = stateValues.globalAppConfiguration
        .countries
        .firstOrNull()
        ?.currencies
        ?.firstOrNull()
        ?.code
        ?: "KZT"

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(stateValues.marginTextField)
    ) {
        item {
            Text(
                text = localizedStringResource(340, "Prices"),
                color = stateValues.TextColor,
                fontSize = stateValues.titleTextSize,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

            StockSinglePriceEditor(
                title = stateValues.stringSalePrice,
                price = draft.salePrices.firstOrNull()
                    ?: PriceDataModel(
                        price = "",
                        currency = defaultCurrency,
                        supplierId = ""
                    ),
                onChanged = {
                    onDraftChanged(
                        draft.copy(
                            salePrices = listOf(it)
                        )
                    )
                }
            )

            Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

            StockSinglePriceEditor(
                title = stateValues.stringReturnPrice,
                price = draft.returnPrices.firstOrNull()
                    ?: PriceDataModel(
                        price = "",
                        currency = defaultCurrency,
                        supplierId = ""
                    ),
                onChanged = {
                    onDraftChanged(
                        draft.copy(
                            returnPrices = listOf(it)
                        )
                    )
                }
            )

            Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

            StockSinglePriceEditor(
                title = stateValues.stringSupplyPrice,
                price = draft.supplyPrices.firstOrNull()
                    ?: PriceDataModel(
                        price = "",
                        currency = defaultCurrency,
                        supplierId = ""
                    ),
                onChanged = {
                    onDraftChanged(
                        draft.copy(
                            supplyPrices = listOf(it)
                        )
                    )
                }
            )

            Spacer(modifier = Modifier.height(stateValues.screenHeight / 5))
        }
    }
}

@Composable
fun AppConfiguration.StockSinglePriceEditor(
    title: String,
    price: PriceDataModel,
    quickFillPrices: List<PriceDataModel> = emptyList(),
    onChanged: (PriceDataModel) -> Unit
) {
    val quickFills = quickFillPrices
        .filter { it.price.isNotBlank() && it.currency.isNotBlank() }
        .distinctBy { it.price to it.currency }

    Column {
        Text(
            text = title,
            color = stateValues.TextColor,
            fontSize = stateValues.textSize,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        SimpleTextInput(
            modifier = Modifier.fillMaxWidth(),
            value = price.price,
            placeholder = "0",
            keyboardType = KeyboardType.Decimal,
            onValueChange = {
                if (it.isEmpty() || it.isNumericalDoubleString()) {
                    onChanged(
                        price.copy(price = it)
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        SimpleDropdownField(
            title = localizedStringResource(268, "Currency"),
            selectedId = price.currency,
            options = stateValues.globalAppConfiguration
                .countries
                .flatMap { it.currencies }
                .distinctBy { it.code }
                .map {
                    DropdownOption(
                        id = it.code,
                        title = "${it.code} ${it.symbol}"
                    )
                },
            placeholder = localizedStringResource(269, "Select currency"),
            onSelected = {
                onChanged(
                    price.copy(currency = it)
                )
            }
        )

        if (quickFills.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(quickFills) { quickPrice ->
                    Box(
                        modifier = Modifier
                            .foregroundSubtleShadow(stateValues.cornerRadius)
                            .clip(RoundedCornerShape(stateValues.cornerRadius))
                            .border(
                                stateValues.unfocusedBorderWidth,
                                stateValues.AccentColor,
                                RoundedCornerShape(stateValues.cornerRadius)
                            )
                            .background(stateValues.BackgroundColor)
                            .aitaClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(color = stateValues.AccentColor)
                            ) {
                                onChanged(
                                    quickPrice.copy(
                                        supplierId = price.supplierId
                                    )
                                )
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${quickPrice.price} ${quickPrice.currency}",
                            color = stateValues.AccentColor,
                            fontSize = stateValues.smallTextSize,
                            fontWeight = FontWeight.Bold,
                            style = TextStyle(shadow = accentTextShadow(stateValues.AccentColor, stateValues.AccentColor)),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}


internal fun AppConfiguration.stockLanguageDomains(): List<SelectableDomain> {
    val defaultDomain = SelectableDomain(
        id = "main",
        displayId = localizedStringResource(671, "Default").toLocalizedSingleMain(),
        name = localizedStringResource(671, "Default").toLocalizedSingleMain(),
        iconPath = null,
        iconRes = null
    )

    return (listOf(defaultDomain) + stateValues.globalAppConfiguration.languages.map { language ->
        SelectableDomain(
            id = language.language,
            displayId = language.language.uppercase().toLocalizedSingleMain(),
            name = language.name,
            iconPath = null,
            iconRes = language.mapIconRes()
        )
    }).distinctBy { it.id.lowercase() }
}

internal fun AppConfiguration.stockCurrencyDomains(): List<SelectableDomain> {
    return stateValues.globalAppConfiguration.countries
        .flatMap { it.currencies }
        .distinctBy { it.code }
        .map { currency ->
            SelectableDomain(
                id = currency.code,
                displayId = currency.code.toLocalizedSingleMain(),
                name = currency.symbol.toLocalizedSingleMain(),
                iconPath = null,
                iconRes = null
            )
        }
}

internal fun AppConfiguration.stockQuantityUnitDomains(): List<SelectableDomain> {
    return stateValues.globalAppConfiguration.goodsItemsQuantityUnits.map { unit ->
        SelectableDomain(
            id = unit.id,
            displayId = unit.immutableUnitName,
            name = unit.immutableUnitName,
            iconPath = null,
            iconRes = null
        )
    }
}

internal fun AppConfiguration.stockCategoryDomains(): List<SelectableDomain> {
    return stateValues.goodsCategories.orEmpty().map { category ->
        val localizedName = category.name
            .visibleLocalizedString(stateValues.appLanguage, category.id)
            .toLocalizedSingleMain()

        SelectableDomain(
            id = category.id,
            displayId = localizedName,
            name = localizedName,
            iconPath = null,
            iconRes = null
        )
    }
}

internal fun List<LocalizedStringDataModel>.toDomainSelectionItems(
    fallbackLanguageId: String
): List<DomainSelectionTextFieldGroupItemContent> {
    val cleaned = map { it.copy(value = it.value.trim()) }
        .filter { it.language.isNotBlank() || it.value.isNotBlank() }
        .distinctBy { it.language }

    return cleaned.ifEmpty {
        listOf(LocalizedStringDataModel(fallbackLanguageId, ""))
    }.map {
        DomainSelectionTextFieldGroupItemContent(
            value = TextFieldValue(it.value, selection = TextRange(it.value.length)),
            selectedDomainId = "text",
            selectedSecondaryDomainId = it.language.ifBlank { fallbackLanguageId },
            isContentValid = true
        )
    }
}

internal fun List<DomainSelectionTextFieldGroupItemContent>.toLocalizedStringsFromLanguageSelection(): List<LocalizedStringDataModel> {
    return map {
        LocalizedStringDataModel(
            language = it.selectedSecondaryDomainId,
            value = it.value.text.trim()
        )
    }
        .filter { it.language.isNotBlank() && it.value.isNotBlank() }
        .distinctBy { it.language }
}

internal const val LOCALIZED_GROUP_EDITOR_STORAGE_PREFIX = "aita-localized-group-editor-v1:"
internal const val LOCALIZED_GROUP_EDITOR_ITEM_SEPARATOR = "\u001D"
internal const val LOCALIZED_GROUP_EDITOR_FIELD_SEPARATOR = "\u001F"

internal fun String.cleanForLocalizedGroupEditorState(): String =
    replace(LOCALIZED_GROUP_EDITOR_ITEM_SEPARATOR, " ")
        .replace(LOCALIZED_GROUP_EDITOR_FIELD_SEPARATOR, " ")

internal fun List<DomainSelectionTextFieldGroupItemContent>.toLocalizedGroupEditorStateString(): String {
    return LOCALIZED_GROUP_EDITOR_STORAGE_PREFIX + joinToString(LOCALIZED_GROUP_EDITOR_ITEM_SEPARATOR) { item ->
        listOf(
            item.selectedDomainId,
            item.selectedSecondaryDomainId,
            item.value.text
        ).joinToString(LOCALIZED_GROUP_EDITOR_FIELD_SEPARATOR) { it.cleanForLocalizedGroupEditorState() }
    }
}

internal fun String.toLocalizedGroupEditorItemsOrNull(fallbackLanguageId: String): List<DomainSelectionTextFieldGroupItemContent>? {
    val payload = trim().removePrefix(LOCALIZED_GROUP_EDITOR_STORAGE_PREFIX)
    if (payload.isBlank()) return null

    return payload
        .split(LOCALIZED_GROUP_EDITOR_ITEM_SEPARATOR)
        .mapNotNull { rawItem ->
            val fields = rawItem.split(LOCALIZED_GROUP_EDITOR_FIELD_SEPARATOR)
            val text = fields.getOrNull(2).orEmpty()
            val language = fields.getOrNull(1)?.takeIf { it.isNotBlank() } ?: fallbackLanguageId
            DomainSelectionTextFieldGroupItemContent(
                value = TextFieldValue(text, selection = TextRange(text.length)),
                selectedDomainId = fields.getOrNull(0)?.takeIf { it.isNotBlank() } ?: "text",
                selectedSecondaryDomainId = language,
                isContentValid = true
            )
        }
        .ifEmpty { null }
}

internal fun AppConfiguration.emptyLocalizedItemForCurrentLanguage(): List<LocalizedStringDataModel> {
    return listOf(LocalizedStringDataModel("main", ""))
}

internal fun AppConfiguration.stockTextOnlyDomain(): List<SelectableDomain> {
    return listOf(
        SelectableDomain(
            id = "text",
            displayId = "".toLocalizedSingleMain(),
            name = null,
            iconPath = null,
            iconRes = null
        )
    )
}

@Composable
internal fun AppConfiguration.StockLocalizedStringGroupEditor(
    title: String,
    placeholder: String,
    values: List<LocalizedStringDataModel>,
    addText: String,
    required: Boolean,
    singleLine: Boolean = true,
    adaptiveMultiline: Boolean = false,
    persistentKey: String? = null,
    onChanged: (List<LocalizedStringDataModel>) -> Unit
) {
    val fallbackLanguageId = "main"

    val languageDomains = stockLanguageDomains()
    val textOnlyDomain = stockTextOnlyDomain()

    fun languageKey(id: String): String = id.trim().lowercase()

    fun sanitizeEditorItems(items: List<DomainSelectionTextFieldGroupItemContent>): List<DomainSelectionTextFieldGroupItemContent> {
        val used = mutableSetOf<String>()
        return items.mapNotNull { rawItem ->
            val requestedId = rawItem.selectedSecondaryDomainId.takeIf { it.isNotBlank() } ?: fallbackLanguageId
            val requestedKey = languageKey(requestedId)
            val resolvedId = if (requestedKey !in used) {
                requestedId
            } else {
                languageDomains.firstOrNull { languageKey(it.id) !in used }?.id ?: return@mapNotNull null
            }
            used += languageKey(resolvedId)
            rawItem.copy(selectedSecondaryDomainId = resolvedId)
        }.ifEmpty {
            listOf(
                DomainSelectionTextFieldGroupItemContent(
                    value = TextFieldValue(""),
                    selectedDomainId = "text",
                    selectedSecondaryDomainId = fallbackLanguageId,
                    isContentValid = true
                )
            )
        }
    }

    val persistentEditorKey = localizedGroupEditorPersistentKey(persistentKey)
    val editorIdentityKey = persistentEditorKey ?: listOf(
        title,
        placeholder,
        values.joinToString("|") { "${it.language}:${it.value}" }
    ).joinToString("::")

    var focusTargetIndex by rememberSaveable(editorIdentityKey) {
        mutableStateOf(-1)
    }

    var data by rememberSaveable(editorIdentityKey) {
        mutableStateOf(sanitizeEditorItems(values.toDomainSelectionItems(fallbackLanguageId)))
    }

    LaunchedEffect(persistentEditorKey, fallbackLanguageId, languageDomains.map { it.id }) {
        val key = persistentEditorKey ?: return@LaunchedEffect
        val restored = getPersistentUiDraftValue?.invoke(key)
            ?.toLocalizedGroupEditorItemsOrNull(fallbackLanguageId)
        if (!restored.isNullOrEmpty()) {
            data = sanitizeEditorItems(restored)
        }
    }

    LaunchedEffect(values, fallbackLanguageId, languageDomains.map { it.id }) {
        val next = sanitizeEditorItems(values.toDomainSelectionItems(fallbackLanguageId))
        val current = values
            .map { it.copy(value = it.value.trim()) }
            .filter { it.language.isNotBlank() && it.value.isNotBlank() }
            .distinctBy { it.language }
        val localSaved = data.toLocalizedStringsFromLanguageSelection()

        if (localSaved != current && next.map { it.selectedSecondaryDomainId to it.value.text } != data.map { it.selectedSecondaryDomainId to it.value.text }) {
            data = next
        }
    }

    val sanitizedData = sanitizeEditorItems(data)
    LaunchedEffect(sanitizedData) {
        if (sanitizedData != data) data = sanitizedData
    }

    val usedLanguageIds = sanitizedData.map { languageKey(it.selectedSecondaryDomainId) }.filter { it.isNotBlank() }.toSet()
    val availableLanguageDomains = languageDomains.filter { languageKey(it.id) !in usedLanguageIds }

    Column(modifier = Modifier.fillMaxWidth()) {
        sanitizedData.forEachIndexed { index, item ->
            val usedByOtherRows = sanitizedData.mapIndexedNotNull { otherIndex, otherItem ->
                if (otherIndex == index) null else languageKey(otherItem.selectedSecondaryDomainId)
            }.toSet()
            val currentLanguageId = item.selectedSecondaryDomainId.takeIf { it.isNotBlank() } ?: fallbackLanguageId
            val safeSelectedLanguageId = currentLanguageId
                .takeIf { languageKey(it) !in usedByOtherRows }
                ?: languageDomains.firstOrNull { languageKey(it.id) !in usedByOtherRows }?.id
                ?: fallbackLanguageId

            val rowLanguageDomains = (
                    listOfNotNull(languageDomains.find { languageKey(it.id) == languageKey(safeSelectedLanguageId) }) +
                            languageDomains.filter { languageKey(it.id) !in usedByOtherRows }
                    )
                .distinctBy { languageKey(it.id) }

            val instance = domainSelectionTextField(
                titleText = if (index == 0) title else "$title ${index + 1}",
                placeholderText = placeholder,
                valueInitial = item.value.text,
                identityKey = "$editorIdentityKey:localized-row:$index",
                titleIconButtonPath = if (sanitizedData.size == 1) null else stateValues.drawablePathIconDelete,
                onTitleIconButtonClick = if (sanitizedData.size == 1) null else {
                    {
                        data = sanitizeEditorItems(sanitizedData.toMutableList().also { list ->
                            if (index in list.indices) list.removeAt(index)
                        })
                        focusTargetIndex = (index - 1).coerceAtLeast(0)
                    }
                },
                domains = textOnlyDomain,
                selectedInitial = "text",
                selectionEnabled = false,
                displayFullDomain = false,
                secondaryDomains = rowLanguageDomains,
                selectedSecondaryInitial = safeSelectedLanguageId.takeIf { it.isNotBlank() }
                    ?: rowLanguageDomains.firstOrNull()?.id
                    ?: fallbackLanguageId,
                secondaryDomainsShowId = true,
                secondaryDomainsShowName = false,
                keyboardType = KeyboardType.Text,
                singleLine = singleLine,
                adaptiveMultiline = adaptiveMultiline,
                isFocusedInitial = index == focusTargetIndex && item.value.text.isBlank(),
                contentInvalidText = if (required) placeholder else null,
                onContentValidityCheck = if (required) {
                    { value, _, _ -> value.isNotBlank() }
                } else null
            )

            Spacer(modifier = Modifier.height(stateValues.marginTextField))

            LaunchedEffect(instance.value.text, instance.selectedSecondaryId, usedByOtherRows) {
                val requestedLanguageId = instance.selectedSecondaryId ?: safeSelectedLanguageId
                val selectedLanguageId = if (languageKey(requestedLanguageId) in usedByOtherRows) {
                    rowLanguageDomains.firstOrNull { languageKey(it.id) !in usedByOtherRows }?.id
                        ?: safeSelectedLanguageId
                } else {
                    requestedLanguageId
                }
                val nextItem = item.copy(
                    value = instance.value,
                    selectedDomainId = "text",
                    selectedSecondaryDomainId = selectedLanguageId,
                    isContentValid = instance.isContentValid
                )

                if (data.getOrNull(index) != nextItem) {
                    data = sanitizeEditorItems(sanitizedData.toMutableList().also { list ->
                        if (index in list.indices) list[index] = nextItem
                    })
                }
            }
        }

        LaunchedEffect(sanitizedData, persistentEditorKey) {
            persistentEditorKey?.let { key ->
                setPersistentUiDraftValue?.invoke(key, sanitizedData.toLocalizedGroupEditorStateString())
            }

            val next = sanitizedData.toLocalizedStringsFromLanguageSelection()
            val current = values
                .map { it.copy(value = it.value.trim()) }
                .filter { it.language.isNotBlank() && it.value.isNotBlank() }
                .distinctBy { it.language }

            if (next != current) {
                onChanged(next)
            }
        }

        if (availableLanguageDomains.isNotEmpty()) {
            actionButton(
                modifier = Modifier.fillMaxWidth(),
                text = addText,
                iconPath = stateValues.drawablePathIconAdd
            ) {
                val nextLanguageId = availableLanguageDomains.first().id
                focusTargetIndex = sanitizedData.size
                data = sanitizeEditorItems(sanitizedData + DomainSelectionTextFieldGroupItemContent(
                    value = TextFieldValue(""),
                    selectedDomainId = "text",
                    selectedSecondaryDomainId = nextLanguageId,
                    isContentValid = true
                ))
            }
        }
    }
}


internal fun List<PriceDataModel>.toStockPriceEditorItems(
    fallbackCurrency: String
): List<DomainSelectionTextFieldGroupItemContent> {
    val cleaned = filter { it.currency.isNotBlank() || it.price.isNotBlank() }
        .distinctBy { it.currency }

    return cleaned.ifEmpty {
        listOf(PriceDataModel("", fallbackCurrency, ""))
    }.map {
        DomainSelectionTextFieldGroupItemContent(
            value = TextFieldValue(it.price, selection = TextRange(it.price.length)),
            selectedDomainId = "text",
            selectedSecondaryDomainId = it.currency.ifBlank { fallbackCurrency },
            isContentValid = true
        )
    }
}

internal fun List<DomainSelectionTextFieldGroupItemContent>.toPriceDataModelsFromCurrencySelection(): List<PriceDataModel> {
    return map {
        PriceDataModel(
            price = it.value.text.trim(),
            currency = it.selectedSecondaryDomainId,
            supplierId = ""
        )
    }
        .filter { it.currency.isNotBlank() && it.price.isNotBlank() }
        .distinctBy { it.currency }
}
@Composable
internal fun AppConfiguration.StockPriceGroupEditor(
    title: String,
    placeholder: String,
    prices: List<PriceDataModel>,
    addText: String,
    onChanged: (List<PriceDataModel>) -> Unit
) {
    val currencies = stockCurrencyDomains()
    val fallbackCurrency = currencies.firstOrNull()?.id ?: "KZT"

    var focusTargetIndex by rememberSaveable {
        mutableStateOf(-1)
    }

    var data by remember {
        mutableStateOf(
            prices.toStockPriceEditorItems(fallbackCurrency)
        )
    }

    LaunchedEffect(prices, fallbackCurrency) {
        val next = prices.toStockPriceEditorItems(fallbackCurrency)
        val currentSaved = prices
            .filter { it.price.isNotBlank() && it.currency.isNotBlank() }
            .distinctBy { it.currency }
        val localSaved = data.toPriceDataModelsFromCurrencySelection()

        if (localSaved != currentSaved && next.map { it.selectedSecondaryDomainId to it.value.text } != data.map { it.selectedSecondaryDomainId to it.value.text }) {
            data = next
        }
    }

    val usedCurrencyIds = data.map { it.selectedSecondaryDomainId }.filter { it.isNotBlank() }.toSet()
    val availableCurrencyDomains = currencies.filter { it.id !in usedCurrencyIds }

    Column(modifier = Modifier.fillMaxWidth()) {
        data.forEachIndexed { index, item ->
            val rowCurrencyDomains = (
                    listOfNotNull(currencies.find { it.id == item.selectedSecondaryDomainId }) +
                            currencies.filter { it.id !in usedCurrencyIds || it.id == item.selectedSecondaryDomainId }
                    )
                .distinctBy { it.id }

            val content = domainSelectionTextField(
                titleText = if (index == 0) title else "$title ${index + 1}",
                placeholderText = placeholder,
                valueInitial = item.value.text,
                titleIconButtonPath = if (data.size == 1) null else stateValues.drawablePathIconDelete,
                onTitleIconButtonClick = if (data.size == 1) null else {
                    {
                        data = data.toMutableList().also { list ->
                            if (index in list.indices) list.removeAt(index)
                        }.ifEmpty {
                            listOf(
                                DomainSelectionTextFieldGroupItemContent(
                                    value = TextFieldValue(""),
                                    selectedDomainId = "text",
                                    selectedSecondaryDomainId = fallbackCurrency,
                                    isContentValid = true
                                )
                            )
                        }
                        focusTargetIndex = (index - 1).coerceAtLeast(0)
                    }
                },
                domains = stockTextOnlyDomain(),
                selectedInitial = "text",
                selectionEnabled = false,
                displayFullDomain = false,
                secondaryDomains = rowCurrencyDomains,
                selectedSecondaryInitial = item.selectedSecondaryDomainId.takeIf { it.isNotBlank() }
                    ?: rowCurrencyDomains.firstOrNull()?.id
                    ?: fallbackCurrency,
                secondaryDomainsShowId = true,
                secondaryDomainsShowName = false,
                keyboardType = KeyboardType.Decimal,
                isFocusedInitial = index == focusTargetIndex,
                contentInvalidText = placeholder,
                onContentValidityCheck = { value, _, _ ->
                    value.toDoubleOrNull()?.let { it >= 0.0 } == true
                },
                onFilterValue = { value, _, _ ->
                    value.isEmpty() || value.isNumericalDoubleString()
                }
            )

            Spacer(modifier = Modifier.height(stateValues.marginTextField))

            LaunchedEffect(content.value.text, content.selectedSecondaryId) {
                val selectedCurrencyId = content.selectedSecondaryId ?: item.selectedSecondaryDomainId
                val nextItem = item.copy(
                    value = content.value,
                    selectedDomainId = "text",
                    selectedSecondaryDomainId = selectedCurrencyId,
                    isContentValid = content.isContentValid
                )

                if (data.getOrNull(index) != nextItem) {
                    data = data.toMutableList().also { list ->
                        if (index in list.indices) list[index] = nextItem
                    }
                }
            }
        }

        LaunchedEffect(data) {
            val next = data.toPriceDataModelsFromCurrencySelection()
            val current = prices
                .filter { it.price.isNotBlank() && it.currency.isNotBlank() }
                .distinctBy { it.currency }

            if (next != current) {
                onChanged(next)
            }
        }

        if (availableCurrencyDomains.isNotEmpty()) {
            actionButton(
                modifier = Modifier.fillMaxWidth(),
                text = addText,
                iconPath = stateValues.drawablePathIconAdd
            ) {
                val nextCurrencyId = availableCurrencyDomains.first().id
                focusTargetIndex = data.size
                data = data + DomainSelectionTextFieldGroupItemContent(
                    value = TextFieldValue(""),
                    selectedDomainId = "text",
                    selectedSecondaryDomainId = nextCurrencyId,
                    isContentValid = true
                )
            }
        }
    }
}

internal data class StockAddEditTabContent(
    val id: String,
    val title: String,
    val iconPath: String? = null,
    val iconRes: DrawableResource? = null,
    val enabled: Boolean = true,
    val count: Int? = null
)

@Composable
internal fun AppConfiguration.StockAddEditTabs(
    modifier: Modifier = Modifier,
    selectedId: String,
    tabs: List<StockAddEditTabContent>,
    onSelected: (String) -> Unit
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
    ) {
        items(tabs) { tab ->
            val selected = selectedId == tab.id
            val visibleTitle = tab.count?.let { tabLabelWithCount(tab.title, it) } ?: tab.title

            val shape = RoundedCornerShape(stateValues.cornerRadius)

            Row(
                modifier = Modifier
                    .height(stateValues.textFieldHeight)
                    .padding(2.dp)
                    .foregroundTactileShadow(stateValues.cornerRadius, elevated = selected)
                    .clip(shape)
                    .background(if (selected) stateValues.AccentColor else stateValues.BackgroundColor)
                    .border(
                        width = if (selected) stateValues.focusedBorderWidth else stateValues.unfocusedBorderWidth,
                        color = when {
                            selected -> stateValues.AccentColor
                            tab.enabled -> stateValues.PlaceholderTextColor
                            else -> stateValues.DisabledColor
                        },
                        shape = shape
                    )
                    .alpha(if (tab.enabled) 1f else 0.55f)
                    .aitaClickable(
                        enabled = tab.enabled,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = if (selected) stateValues.AccentTextColor else stateValues.TextColor),
                        onClick = { onSelected(tab.id) }
                    )
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                tab.iconPath?.let {
                    CpImage(
                        modifier = Modifier
                            .size(18.dp),
                        url = it,
                        fallbackRes = tab.iconRes ?: Res.drawable._0_0,
                        contentDescription = visibleTitle,
                        tintColor = if (selected) stateValues.AccentTextColor else stateValues.TextColor
                    )

                    Spacer(modifier = Modifier.width(6.dp))
                }

                Text(
                    text = visibleTitle,
                    color = if (selected) stateValues.AccentTextColor else stateValues.TextColor,
                    fontSize = stateValues.textSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

internal fun AppConfiguration.stockAddEditLastCategoryStorageKey(kind: String): String = listOf(
    "stock-add-edit-last-category",
    persistentUiDraftOwnerKey(),
    stateValues.activeStoreId ?: "no-store",
    kind
).joinToString(":")

internal fun AppConfiguration.rememberLatestStockAddEditCategorySelection(rootCategoryId: String?, selectedCategoryId: String?) {
    val cleanRootId = rootCategoryId?.takeIf { it.isNotBlank() }
    val cleanCategoryId = selectedCategoryId?.takeIf { it.isNotBlank() } ?: cleanRootId

    coroutineScope.launch {
        cleanRootId?.let {
            NavigationScreenModel.Stock.AddEditGoodsItem.setState(
                NavigationScreenModel.Stock.AddEditGoodsItem.KEY_STATE_LAST_SELECTED_ROOT_CATEGORY_ID to it
            )
            setPersistentUiDraftValue?.invoke(stockAddEditLastCategoryStorageKey("root"), it)
        }
        cleanCategoryId?.let {
            NavigationScreenModel.Stock.AddEditGoodsItem.setState(
                NavigationScreenModel.Stock.AddEditGoodsItem.KEY_STATE_LAST_SELECTED_CATEGORY_ID to it
            )
            setPersistentUiDraftValue?.invoke(stockAddEditLastCategoryStorageKey("selected"), it)
        }
    }
}


internal fun AppConfiguration.globalGoodsIconPath(): String =
    if (stateValues.appThemeId == 1L) "svg/56_1.svg" else "svg/56_0.svg"

internal fun AppConfiguration.globalGoodsIconFallback(): DrawableResource =
    if (stateValues.appThemeId == 1L) Res.drawable._56_1 else Res.drawable._56_0

internal fun AppConfiguration.undoTemplateIconPath(): String =
    if (stateValues.appThemeId == 1L) "svg/58_1.svg" else "svg/58_0.svg"

internal fun AppConfiguration.undoTemplateIconFallback(): DrawableResource =
    if (stateValues.appThemeId == 1L) Res.drawable._58_1 else Res.drawable._58_0

internal fun AppConfiguration.parentStoreStockIconPath(): String =
    if (stateValues.appThemeId == 1L) "svg/60_1.svg" else "svg/60_0.svg"

internal fun AppConfiguration.parentStoreStockIconFallback(): DrawableResource =
    if (stateValues.appThemeId == 1L) Res.drawable._60_1 else Res.drawable._60_0


internal fun AppConfiguration.stockBatchStatusText(status: StockBatchStatusDataModel): String = when (status) {
    StockBatchStatusDataModel.Ordered -> localizedStringResource(1198, "Ordered")
    StockBatchStatusDataModel.Delivered -> localizedStringResource(342, "Delivered")
    StockBatchStatusDataModel.OnShelf -> localizedStringResource(1199, "On shelf")
    StockBatchStatusDataModel.Reserved -> localizedStringResource(1200, "Reserved")
    StockBatchStatusDataModel.InTransit -> localizedStringResource(1201, "In transit")
    StockBatchStatusDataModel.SoldOut -> localizedStringResource(1202, "Sold out")
    StockBatchStatusDataModel.WrittenOff -> localizedStringResource(1203, "Written off")
    StockBatchStatusDataModel.Deleted -> localizedStringResource(1204, "Deleted")
}

internal fun GenericGoodsItemDataModel.visibleGlobalGoodsName(language: String): String =
    name.visibleLocalizedString(language, id.ifBlank { barcode.orEmpty().firstOrNull().orEmpty() })

internal fun GenericGoodsItemDataModel.primaryGlobalGoodsBarcode(): String =
    barcode.orEmpty().firstOrNull { it.isNotBlank() }.orEmpty()

internal fun GenericGoodsItemDataModel.globalGoodsBarcodeText(): String =
    barcode.orEmpty().filter { it.isNotBlank() }.take(3).joinToString(" • ")

internal fun StockAddEditDraft.standardBarcodeForGenericLookup(): String? =
    visibleBarcodes()
        .zip(visibleBarcodeTypes())
        .firstOrNull { (barcode, type) ->
            barcode.isNotBlank() && type.normalizedGoodsItemBarcodeType(barcode) == GOODS_ITEM_BARCODE_TYPE_STANDARD
        }
        ?.first
        ?.toStoredGoodsItemBarcode()
        ?.takeIf { it.isNotBlank() }

internal fun StockAddEditDraft.globalGoodsAutoQuery(language: String): String? =
    name.visibleLocalizedString(language, "")
        .takeIf { it.length >= 2 }
        ?: name.firstOrNull { it.value.isNotBlank() }?.value?.takeIf { it.length >= 2 }

internal fun AppConfiguration.globalGoodsCategoryName(categoryId: String): String? =
    stateValues.goodsCategories
        .orEmpty()
        .find { it.id == categoryId }
        ?.name
        ?.visibleGoodsCategoryName(stateValues.appLanguage, categoryId)

internal fun AppConfiguration.applyGlobalGoodsTemplateToDraft(
    draft: StockAddEditDraft,
    item: GenericGoodsItemDataModel
): StockAddEditDraft {
    val templateBarcodes = item.barcode
        .orEmpty()
        .flatMap { it.toStoredGoodsItemBarcodeCandidates() }
        .map { it.toStoredGoodsItemBarcode() }
        .filter { it.isNotBlank() }
        .distinct()
    val nextBarcodes = templateBarcodes.ifEmpty { draft.visibleBarcodes().filter { it.isNotBlank() } }.ifEmpty { listOf("") }
    val templateCategoryIds = item.categoryIds.orEmpty().filter { it.isNotBlank() }
    val templateCategory = templateCategoryIds.firstOrNull()?.let { categoryId ->
        stateValues.goodsCategories.orEmpty().find { it.id == categoryId }
    }

    return draft.copy(
        barcodes = nextBarcodes,
        barcodeTypes = nextBarcodes.map { barcode -> GOODS_ITEM_BARCODE_TYPE_STANDARD.normalizedGoodsItemBarcodeType(barcode) },
        name = item.name.takeIf { it.isNotEmpty() } ?: draft.name,
        categoryIds = templateCategoryIds.ifEmpty { draft.categoryIds },
        measurementUnitId = templateCategory?.quantityUnitId?.takeIf { it.isNotBlank() } ?: draft.measurementUnitId
    )
}

@Composable
internal fun AppConfiguration.GlobalGoodsSuggestionCard(
    item: GenericGoodsItemDataModel,
    barcodeMatched: Boolean,
    onApply: () -> Unit
) {
    val name = item.visibleGlobalGoodsName(stateValues.appLanguage)
    val barcodeText = item.globalGoodsBarcodeText()
    val categoryText = item.categoryIds
        .orEmpty()
        .mapNotNull { globalGoodsCategoryName(it) }
        .distinct()
        .take(2)
        .joinToString(" • ")

    Column(
        modifier = Modifier
            .widthIn(min = 230.dp, max = 280.dp)
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(
                stateValues.unfocusedBorderWidth,
                if (barcodeMatched) stateValues.AccentColor else stateValues.PlaceholderTextColor,
                RoundedCornerShape(stateValues.cornerRadius)
            )
            .aitaClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = stateValues.AccentColor),
                onClick = onApply
            )
            .padding(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            CpImage(
                modifier = Modifier.size(26.dp),
                url = globalGoodsIconPath(),
                fallbackRes = globalGoodsIconFallback(),
                contentDescription = localizedStringResource(1169, "Global goods"),
                tintColor = if (barcodeMatched) stateValues.AccentColor else stateValues.TextColor
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    color = stateValues.TextColor,
                    fontSize = stateValues.textSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (barcodeMatched) {
                    Text(
                        text = localizedStringResource(1173, "Barcode match"),
                        color = stateValues.AccentColor,
                        fontSize = stateValues.smallTextSize,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        barcodeText.takeIf { it.isNotBlank() }?.let {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${stateValues.stringBarcode}: $it",
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        categoryText.takeIf { it.isNotBlank() }?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = it,
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        actionButton(
            text = localizedStringResource(1170, "Use template"),
            iconPath = globalGoodsIconPath(),
            fillMaxWidthIfTextPresent = true,
            confirmationRequired = false,
            onClick = onApply
        )
    }
}

@Composable
internal fun AppConfiguration.GlobalGoodsSuggestionsPanel(
    suggestions: List<GenericGoodsItemDataModel>,
    loading: Boolean,
    searchText: String,
    barcodeLookup: String?,
    onSearchTextChanged: (String) -> Unit,
    onRefresh: () -> Unit,
    onApply: (GenericGoodsItemDataModel) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .foregroundSubtleShadow(stateValues.cornerRadius)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor, RoundedCornerShape(stateValues.cornerRadius))
            .padding(stateValues.marginTextFieldGroup)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CpImage(
                modifier = Modifier.size(28.dp),
                url = globalGoodsIconPath(),
                fallbackRes = globalGoodsIconFallback(),
                contentDescription = localizedStringResource(1169, "Global goods"),
                tintColor = stateValues.AccentColor
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = localizedStringResource(1169, "Global goods"),
                    color = stateValues.TextColor,
                    fontSize = stateValues.accentTextSize,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = localizedStringResource(1172, "Start from a standard item, then set your own prices, supplier and stock batches."),
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .aitaClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = stateValues.AccentColor),
                        onClick = onRefresh
                    ),
                contentAlignment = Alignment.Center
            ) {
                CpImage(
                    modifier = Modifier.size(20.dp),
                    url = stateValues.drawablePathIconRefresh,
                    fallbackRes = Res.drawable._54_0,
                    contentDescription = localizedStringResource(561, "Refresh availability"),
                    tintColor = stateValues.TextColor
                )
            }
        }

        Spacer(modifier = Modifier.height(stateValues.marginTextField))

        SimpleTextInput(
            modifier = Modifier.fillMaxWidth(),
            value = searchText,
            placeholder = localizedStringResource(1171, "Search global goods"),
            leadingIconPath = stateValues.drawablePathIconSearch,
            onValueChange = onSearchTextChanged
        )

        Spacer(modifier = Modifier.height(stateValues.marginTextField))

        if (loading) {
            MessageText(
                modifier = Modifier.fillMaxWidth().height(54.dp),
                text = localizedStringResource(1141, "Please wait…"),
                textSize = stateValues.textSize,
                textColor = stateValues.PlaceholderTextColor
            )
        } else if (suggestions.isEmpty()) {
            MessageText(
                modifier = Modifier.fillMaxWidth().height(54.dp),
                text = localizedStringResource(1174, "No global suggestions yet"),
                textSize = stateValues.textSize
            )
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(suggestions, key = { it.id }) { item ->
                    val barcodeMatched = barcodeLookup != null &&
                            item.barcode.orEmpty().flatMap { it.toStoredGoodsItemBarcodeCandidates() }.any { it == barcodeLookup }
                    GlobalGoodsSuggestionCard(
                        item = item,
                        barcodeMatched = barcodeMatched,
                        onApply = { onApply(item) }
                    )
                }
            }
        }
    }
}


internal const val GLOBAL_GOODS_CATEGORY_ALL_ID = "__aita_all_global_goods_categories__"
internal const val GLOBAL_GOODS_SUBCATEGORY_ALL_ID = "__aita_all_global_goods_subcategories__"

internal fun List<GenericGoodsCategoryDataModel>.globalGoodsCategoryTreeIds(categoryId: String): Set<String> {
    if (categoryId.isBlank()) return emptySet()
    val result = mutableSetOf(categoryId)
    var changed = true
    while (changed) {
        changed = false
        forEach { category ->
            if (category.id !in result && category.typeIds.orEmpty().any { it in result }) {
                result += category.id
                changed = true
            }
        }
    }
    return result
}

internal fun GenericGoodsItemDataModel.matchesGlobalGoodsPickerFilter(
    rawQuery: String,
    categoryFilterIds: Set<String>
): Boolean {
    val queryTokens = rawQuery
        .trim()
        .lowercase()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }

    val categoryMatches = categoryFilterIds.isEmpty() || categoryIds.orEmpty().any { it in categoryFilterIds }
    if (!categoryMatches) return false
    if (queryTokens.isEmpty()) return true

    val searchableText = buildList {
        add(id)
        addAll(barcode.orEmpty())
        addAll(name.map { it.value })
        addAll(typeIds.orEmpty())
        addAll(categoryIds.orEmpty())
        addAll(supplierIds.orEmpty())
        addAll(manufacturerIds.orEmpty())
    }.joinToString(" ").lowercase()

    return queryTokens.all { token -> searchableText.contains(token) }
}

internal fun AppConfiguration.globalGoodsCategoryDomain(category: GenericGoodsCategoryDataModel): SelectableDomain {
    val categoryName = category.name.visibleGoodsCategoryName(stateValues.appLanguage, category.id).toLocalizedSingleMain()
    val categoryDescription = category.description?.visibleLocalizedString(stateValues.appLanguage, "")?.withoutGoodsCategoryPrefix()
        ?: category.alias?.visibleLocalizedString(stateValues.appLanguage, "")?.withoutGoodsCategoryPrefix()
        ?: ""

    return SelectableDomain(
        id = category.id,
        displayId = categoryName,
        name = categoryDescription.takeIf { it.isNotBlank() }?.toLocalizedSingleMain() ?: categoryName,
        iconPath = null,
        iconRes = null
    )
}

@Composable
internal fun AppConfiguration.GlobalGoodsPickerItemCard(
    item: GenericGoodsItemDataModel,
    selectedCategoryIds: Set<String>,
    onApply: () -> Unit
) {
    val name = item.visibleGlobalGoodsName(stateValues.appLanguage)
    val barcodeText = item.globalGoodsBarcodeText()
    val categories = stateValues.goodsCategories.orEmpty()
    val categoryText = item.categoryIds
        .orEmpty()
        .mapNotNull { globalGoodsCategoryName(it) }
        .distinct()
        .take(4)
        .joinToString(" • ")
    val matchingCategoryCount = item.categoryIds.orEmpty().count { it in selectedCategoryIds }.takeIf { selectedCategoryIds.isNotEmpty() }
    val unitText = item.categoryIds
        .orEmpty()
        .firstNotNullOfOrNull { categoryId -> categories.find { it.id == categoryId }?.quantityUnitId?.takeIf { it.isNotBlank() } }
        ?.let { unitId ->
            stateValues.globalAppConfiguration.goodsItemsQuantityUnits
                .find { it.id == unitId }
                ?.immutableUnitName
                ?.extractLocalizedString(stateValues.appLanguage)
                ?: unitId
        }
        .orEmpty()
    val tagsText = item.typeIds.orEmpty().filter { it.isNotBlank() }.take(5).joinToString(" • ")
    val referencesText = listOfNotNull(
        item.supplierIds.orEmpty().size.takeIf { it > 0 }?.let { "${localizedStringResource(74, "Supplier")}: $it" },
        item.manufacturerIds.orEmpty().size.takeIf { it > 0 }?.let { "${localizedStringResource(1197, "Manufacturers")}: $it" }
    ).joinToString(" • ")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(
                stateValues.unfocusedBorderWidth,
                if (matchingCategoryCount != null) stateValues.AccentColor else stateValues.PlaceholderTextColor,
                RoundedCornerShape(stateValues.cornerRadius)
            )
            .aitaClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = stateValues.AccentColor),
                onClick = onApply
            )
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CpImage(
                modifier = Modifier.size(30.dp),
                url = globalGoodsIconPath(),
                fallbackRes = globalGoodsIconFallback(),
                contentDescription = localizedStringResource(1169, "Global goods"),
                tintColor = stateValues.AccentColor
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    color = stateValues.TextColor,
                    fontSize = stateValues.textSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.id,
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            actionButton(
                text = localizedStringResource(1170, "Use template"),
                iconPath = globalGoodsIconPath(),
                fillMaxWidthIfTextPresent = false,
                confirmationRequired = false,
                onClick = onApply
            )
        }

        val detailLines = listOfNotNull(
            barcodeText.takeIf { it.isNotBlank() }?.let { "${stateValues.stringBarcode}: $it" },
            categoryText.takeIf { it.isNotBlank() }?.let { "${stateValues.stringCategory}: $it" },
            unitText.takeIf { it.isNotBlank() }?.let { "${stateValues.stringMeasurementUnit}: $it" },
            tagsText.takeIf { it.isNotBlank() }?.let { "${localizedStringResource(1186, "Tags")}: $it" },
            referencesText.takeIf { it.isNotBlank() }?.let { "${localizedStringResource(1187, "References")}: $it" }
        )

        detailLines.forEachIndexed { index, line ->
            Spacer(modifier = Modifier.height(if (index == 0) 8.dp else 4.dp))
            Text(
                text = line,
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}


internal fun GoodsItemDataModel.visibleParentStoreStockName(language: String): String =
    name.visibleLocalizedString(language, id.ifBlank { allBarcodeValues().firstOrNull().orEmpty() })

internal fun GoodsItemDataModel.parentStoreStockBarcodeText(): String =
    allBarcodeValues().filter { it.isNotBlank() }.distinct().take(3).joinToString(" • ")

internal fun GoodsItemDataModel.matchesParentStoreStockPickerFilter(rawQuery: String): Boolean {
    val queryTokens = rawQuery
        .trim()
        .lowercase()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }

    if (queryTokens.isEmpty()) return true

    val searchableText = buildList {
        add(id)
        add(storeId)
        addAll(allBarcodeValues())
        addAll(allBarcodeValues().map { it.toStoredGoodsItemBarcode() })
        addAll(name.map { it.value })
        addAll(description.map { it.value })
        add(measurementUnitId)
        addAll(categoryIds)
        addAll(salePrices.flatMap { listOf(it.price, it.currency, it.supplierId) })
        addAll(supplyPrices.flatMap { listOf(it.price, it.currency, it.supplierId) })
        addAll(returnPrices.flatMap { listOf(it.price, it.currency, it.supplierId) })
        addAll(wholesalePrices.flatMap { listOf(it.price, it.currency, it.supplierId) })
        note?.let { add(it) }
        addAll(noteLocalized.map { it.value })
        addAll(conditions)
    }.joinToString(" ").lowercase()

    return queryTokens.all { token -> searchableText.contains(token) }
}

internal fun GoodsItemDataModel.toParentStoreStockTemplateDraft(
    currentDraft: StockAddEditDraft,
    existing: GoodsItemDataModel?
): StockAddEditDraft {
    val parentDraft = toStockAddEditDraft()
    val preservedId = existing?.id ?: currentDraft.id.takeIf { it.isNotBlank() && it != id }.orEmpty()
    return parentDraft.copy(id = preservedId)
}

@Composable
internal fun AppConfiguration.ParentStoreStockPickerItemCard(
    item: GoodsItemDataModel,
    barcodeMatched: Boolean,
    onApply: () -> Unit
) {
    val name = item.visibleParentStoreStockName(stateValues.appLanguage)
    val barcodeText = item.parentStoreStockBarcodeText()
    val categoryText = item.categoryIds
        .mapNotNull { globalGoodsCategoryName(it) }
        .distinct()
        .take(4)
        .joinToString(" • ")
    val unitText = stateValues.globalAppConfiguration.goodsItemsQuantityUnits
        .find { it.id == item.measurementUnitId }
        ?.immutableUnitName
        ?.extractLocalizedString(stateValues.appLanguage)
        ?: item.measurementUnitId.takeIf { it.isNotBlank() }
        ?: ""
    val salePriceText = item.salePrices.firstOrNull()?.let { price ->
        listOf(price.price, price.currency).filter { it.isNotBlank() }.joinToString(" ")
    }.orEmpty()
    val supplyPriceText = item.supplyPrices.firstOrNull()?.let { price ->
        listOf(price.price, price.currency).filter { it.isNotBlank() }.joinToString(" ")
    }.orEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(
                stateValues.unfocusedBorderWidth,
                if (barcodeMatched) stateValues.AccentColor else stateValues.PlaceholderTextColor,
                RoundedCornerShape(stateValues.cornerRadius)
            )
            .aitaClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = stateValues.AccentColor),
                onClick = onApply
            )
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CpImage(
                modifier = Modifier.size(30.dp),
                url = parentStoreStockIconPath(),
                fallbackRes = parentStoreStockIconFallback(),
                contentDescription = localizedStringResource(1212, "Parent store"),
                tintColor = if (barcodeMatched) stateValues.AccentColor else stateValues.TextColor
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    color = stateValues.TextColor,
                    fontSize = stateValues.textSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOfNotNull(
                        localizedStringResource(1217, "Parent stock"),
                        item.id.take(8).takeIf { it.isNotBlank() }
                    ).joinToString(" • "),
                    color = if (barcodeMatched) stateValues.AccentColor else stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = if (barcodeMatched) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            actionButton(
                text = localizedStringResource(1218, "Use parent item"),
                iconPath = parentStoreStockIconPath(),
                fillMaxWidthIfTextPresent = false,
                confirmationRequired = false,
                onClick = onApply
            )
        }

        val detailLines = listOfNotNull(
            barcodeText.takeIf { it.isNotBlank() }?.let { "${stateValues.stringBarcode}: $it" },
            categoryText.takeIf { it.isNotBlank() }?.let { "${stateValues.stringCategory}: $it" },
            unitText.takeIf { it.isNotBlank() }?.let { "${stateValues.stringMeasurementUnit}: $it" },
            salePriceText.takeIf { it.isNotBlank() }?.let { "${stateValues.stringSalePrice}: $it" },
            supplyPriceText.takeIf { it.isNotBlank() }?.let { "${stateValues.stringSupplyPrice}: $it" }
        )

        detailLines.forEachIndexed { index, line ->
            Spacer(modifier = Modifier.height(if (index == 0) 8.dp else 4.dp))
            Text(
                text = line,
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun AppConfiguration.ParentStoreStockSelectionBottomSheet(
    activeStoreId: String,
    draft: StockAddEditDraft,
    existing: GoodsItemDataModel?,
    onDismiss: () -> Unit,
    onApply: (GoodsItemDataModel) -> Unit
) {
    val pickerPageSize = if (stateValues.isNarrowScreen) 18 else 32
    val cachedParentStoreStock by parentStoreStockState.payload.collectAsState()
    val autoFocusSearch = platformAllowsAutomaticTextFieldFocus()
    var pickerPage by rememberSaveable(activeStoreId) { mutableStateOf(0) }
    var serverLoadedItems by remember(activeStoreId) { mutableStateOf<List<GoodsItemDataModel>>(emptyList()) }
    var serverEndReached by rememberSaveable(activeStoreId) { mutableStateOf(false) }
    var loading by remember(activeStoreId) { mutableStateOf(false) }
    var loadError by remember(activeStoreId) { mutableStateOf<String?>(null) }
    var retryNonce by rememberSaveable(activeStoreId) { mutableStateOf(0) }

    val activeStore = stateValues.stores.findStoreOrBranchForUi(activeStoreId)
    val parentStoreId = activeStore?.parentStoreId?.takeIf { it.isNotBlank() }
    val parentStore = stateValues.stores.findStoreOrBranchForUi(parentStoreId)
    val parentStoreName = parentStore
        ?.name
        ?.visibleLocalizedString(stateValues.appLanguage, parentStore.publicId.ifBlank { parentStore.id })
        .orEmpty()

    AitaBottomSheet(
        title = localizedStringResource(1212, "Parent store"),
        iconPath = parentStoreStockIconPath(),
        onDismiss = onDismiss
    ) {
        Text(
            text = listOfNotNull(
                parentStoreName.takeIf { it.isNotBlank() },
                localizedStringResource(1214, "Take a clean copy from the parent store stock, then adjust it for this branch.")
            ).joinToString(" • "),
            color = stateValues.PlaceholderTextColor,
            fontSize = stateValues.smallTextSize,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

        Text(
            text = localizedStringResource(1213, "Search parent stock"),
            color = stateValues.TextColor,
            fontSize = stateValues.accentTextSize,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        val searchTextFieldContent = searchTextField(
            modifier = Modifier.fillMaxWidth(),
            stateHost = NavigationScreenModel.Stock.AddEditGoodsItem,
            stateKey = "stock_parent_store_sheet_search",
            isFocusedInitial = autoFocusSearch,
            autoFocus = autoFocusSearch,
            barcodeCamScanner = false
        )
        val cleanQuery = searchTextFieldContent.value.text.trim()

        LaunchedEffect(activeStoreId, cleanQuery, retryNonce) {
            pickerPage = 0
            serverLoadedItems = emptyList()
            serverEndReached = false
            loading = true
            loadError = null
            try {
                delay(220)
                getParentStoreStock(
                    storeId = activeStoreId,
                    query = cleanQuery.takeIf { it.length >= 2 },
                    limit = pickerPageSize,
                    offset = 0,
                    updateSharedState = true,
                    appendToSharedState = false
                ).collect { state ->
                    val payload = (state as? DataState.Success)?.payload.orEmpty()
                    if (state is DataState.Success) {
                        serverLoadedItems = payload
                        serverEndReached = payload.size < pickerPageSize
                    } else {
                        val fallbackMessage = localizedStringResource(13, "Could not load parent stock")
                        loadError = state.message
                            ?.extractLocalizedString(stateValues.appLanguage)
                            ?.trim()
                            ?.takeIf { it.isNotBlank() }
                            ?: fallbackMessage
                    }
                }
            } finally {
                loading = false
            }
        }

        LaunchedEffect(pickerPage, activeStoreId, cleanQuery, serverLoadedItems.size, serverEndReached) {
            val desiredOffset = pickerPage * pickerPageSize
            if (desiredOffset <= 0 || desiredOffset < serverLoadedItems.size || serverEndReached || loading) return@LaunchedEffect
            loading = true
            try {
                getParentStoreStock(
                    storeId = activeStoreId,
                    query = cleanQuery.takeIf { it.length >= 2 },
                    limit = pickerPageSize,
                    offset = desiredOffset,
                    updateSharedState = true,
                    appendToSharedState = true
                ).collect { state ->
                    val payload = (state as? DataState.Success)?.payload.orEmpty()
                    serverLoadedItems = (serverLoadedItems + payload).distinctBy { it.id }
                    serverEndReached = state is DataState.Empty || payload.size < pickerPageSize
                }
            } finally {
                loading = false
            }
        }

        val cachedFilteredItems = cachedParentStoreStock
            .orEmpty()
            .filter { parentStoreId == null || it.storeId == parentStoreId }
            .filter { it.matchesParentStoreStockPickerFilter(cleanQuery) }
        val lookupBarcode = draft.standardBarcodeForGenericLookup()
        val filteredItems = (serverLoadedItems + cachedFilteredItems)
            .distinctBy { it.id }
            .sortedWith(
                compareByDescending<GoodsItemDataModel> { item ->
                    lookupBarcode?.let { lookup ->
                        item.allBarcodeValues().flatMap { it.toStoredGoodsItemBarcodeCandidates() }.any { it == lookup }
                    } == true
                }.thenBy { it.visibleParentStoreStockName(stateValues.appLanguage).lowercase() }
                    .thenBy { it.id }
            )
        val displayItems = filteredItems.clientPaged(pickerPage, pickerPageSize)
        val pageWaitingForServer = !serverEndReached && pickerPage * pickerPageSize >= serverLoadedItems.size
        val totalItemsForPaging = if (serverEndReached) {
            filteredItems.size
        } else {
            ((pickerPage + 2) * pickerPageSize).coerceAtLeast(filteredItems.size)
        }

        Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = stateValues.screenHeight / 8)
        ) {
            when {
                displayItems.isEmpty() && (loading || pageWaitingForServer) -> item {
                    MessageText(
                        modifier = Modifier.fillParentMaxSize().fillMaxWidth(),
                        text = localizedStringResource(1141, "Please wait…"),
                        textSize = stateValues.textSize,
                        textColor = stateValues.PlaceholderTextColor
                    )
                }

                displayItems.isEmpty() && loadError != null -> item {
                    Column(
                        modifier = Modifier.fillParentMaxSize().fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        MessageText(
                            modifier = Modifier.fillMaxWidth(),
                            text = loadError.orEmpty(),
                            textSize = stateValues.textSize,
                            textColor = stateValues.ErrorColor
                        )
                        Spacer(Modifier.height(8.dp))
                        actionButton(
                            text = localizedStringResource(158, "Retry"),
                            iconPath = stateValues.drawablePathIconRefresh,
                            onClick = { retryNonce += 1 }
                        )
                    }
                }

                displayItems.isEmpty() -> item {
                    MessageText(
                        modifier = Modifier.fillParentMaxSize().fillMaxWidth(),
                        text = if (parentStoreId == null) localizedStringResource(1219, "No parent inventory is available for this store") else localizedStringResource(1215, "No parent store items match these filters"),
                        textSize = stateValues.textSize
                    )
                }

                else -> {
                    items(displayItems, key = { it.id }) { item ->
                        val barcodeMatched = lookupBarcode?.let { lookup ->
                            item.allBarcodeValues().flatMap { it.toStoredGoodsItemBarcodeCandidates() }.any { it == lookup }
                        } == true
                        ParentStoreStockPickerItemCard(
                            item = item,
                            barcodeMatched = barcodeMatched,
                            onApply = { onApply(item) }
                        )
                    }

                    if (loading) {
                        item {
                            MessageText(
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                text = localizedStringResource(1188, "Loading more goods…"),
                                textSize = stateValues.smallTextSize,
                                textColor = stateValues.PlaceholderTextColor
                            )
                        }
                    }

                    item {
                        PagingControls(
                            page = pickerPage,
                            totalItems = totalItemsForPaging,
                            pageSize = pickerPageSize,
                            onPageChange = { pickerPage = it.coerceAtLeast(0) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun AppConfiguration.GlobalGoodsSelectionBottomSheet(
    draft: StockAddEditDraft,
    onDismiss: () -> Unit,
    onApply: (GenericGoodsItemDataModel) -> Unit
) {
    val pickerPageSize = if (stateValues.isNarrowScreen) 18 else 32
    val cachedGenericGoodsItems by genericGoodsItemsState.payload.collectAsState()
    val autoFocusSearch = platformAllowsAutomaticTextFieldFocus()
    var pickerPage by rememberSaveable { mutableStateOf(0) }
    var serverLoadedItems by remember { mutableStateOf<List<GenericGoodsItemDataModel>>(emptyList()) }
    var serverEndReached by rememberSaveable { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }

    AitaBottomSheet(
        title = localizedStringResource(1169, "Global goods"),
        iconPath = globalGoodsIconPath(),
        onDismiss = onDismiss
    ) {
        Text(
            text = localizedStringResource(1172, "Start from a standard item, then set your own prices, supplier and stock batches."),
            color = stateValues.PlaceholderTextColor,
            fontSize = stateValues.smallTextSize,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

        Text(
            text = localizedStringResource(1171, "Search global goods"),
            color = stateValues.TextColor,
            fontSize = stateValues.accentTextSize,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        val searchTextFieldContent = searchTextField(
            modifier = Modifier.fillMaxWidth(),
            stateHost = NavigationScreenModel.Stock.AddEditGoodsItem,
            stateKey = "stock_global_goods_sheet_search",
            isFocusedInitial = autoFocusSearch,
            autoFocus = autoFocusSearch,
            barcodeCamScanner = false
        )
        val cleanQuery = searchTextFieldContent.value.text.trim()

        Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

        val allCategories = stateValues.goodsCategories.orEmpty()
        val rootCategories = allCategories
            .filter { category -> category.typeIds.orEmpty().none { parentId -> allCategories.any { it.id == parentId } } }
            .sortedBy { it.name.visibleGoodsCategoryName(stateValues.appLanguage, it.id) }
        val allCategoryDomain = SelectableDomain(
            id = GLOBAL_GOODS_CATEGORY_ALL_ID,
            displayId = localizedStringResource(1182, "All categories").toLocalizedSingleMain(),
            name = localizedStringResource(1182, "All categories").toLocalizedSingleMain(),
            iconPath = null,
            iconRes = null
        )
        val rootCategoryDropdown = dropdownListWidget(
            titleText = stateValues.stringCategory,
            domains = listOf(allCategoryDomain) + rootCategories.map { globalGoodsCategoryDomain(it) },
            selectedInitial = GLOBAL_GOODS_CATEGORY_ALL_ID,
            showId = false,
            showName = true,
            search = Triple(stateValues.stringSearchByAnyData, NavigationScreenModel.Stock.AddEditGoodsItem, "stock_global_goods_category_search")
        )
        val selectedRootId = rootCategoryDropdown.selectedId
        val subcategories = allCategories
            .filter { it.typeIds.orEmpty().contains(selectedRootId) }
            .sortedBy { it.name.visibleGoodsCategoryName(stateValues.appLanguage, it.id) }
        val selectedSubcategoryId = if (selectedRootId != GLOBAL_GOODS_CATEGORY_ALL_ID && subcategories.isNotEmpty()) {
            Spacer(modifier = Modifier.height(stateValues.marginTextField))
            val allSubcategoryDomain = SelectableDomain(
                id = GLOBAL_GOODS_SUBCATEGORY_ALL_ID,
                displayId = localizedStringResource(1183, "All subcategories").toLocalizedSingleMain(),
                name = localizedStringResource(1183, "All subcategories").toLocalizedSingleMain(),
                iconPath = null,
                iconRes = null
            )
            dropdownListWidget(
                titleText = localizedStringResource(184, "Subcategory"),
                domains = listOf(allSubcategoryDomain) + subcategories.map { globalGoodsCategoryDomain(it) },
                selectedInitial = GLOBAL_GOODS_SUBCATEGORY_ALL_ID,
                showId = false,
                showName = true,
                search = Triple(stateValues.stringSearchByAnyData, NavigationScreenModel.Stock.AddEditGoodsItem, "stock_global_goods_subcategory_search")
            ).selectedId
        } else {
            GLOBAL_GOODS_SUBCATEGORY_ALL_ID
        }

        val selectedCategoryIds = remember(selectedRootId, selectedSubcategoryId, allCategories) {
            when {
                selectedRootId == GLOBAL_GOODS_CATEGORY_ALL_ID -> emptySet()
                selectedSubcategoryId != GLOBAL_GOODS_SUBCATEGORY_ALL_ID -> allCategories.globalGoodsCategoryTreeIds(selectedSubcategoryId)
                else -> allCategories.globalGoodsCategoryTreeIds(selectedRootId)
            }
        }
        val selectedCategoryKey = remember(selectedCategoryIds) { selectedCategoryIds.sorted().joinToString("|") }

        val aitaLatestCatalogueOwner0 = rememberAitaLatestUiRequestOwner()
        LaunchedEffect(cleanQuery, selectedCategoryKey) {            val aitaLatestCatalogueTicket0 = aitaLatestCatalogueOwner0.begin()

            pickerPage = 0
            serverLoadedItems = emptyList()
            serverEndReached = false
            loading = true
            try {
                delay(220)
                if (aitaLatestCatalogueOwner0.owns(aitaLatestCatalogueTicket0)) getGenericGoodsItems(
                    query = cleanQuery.takeIf { it.length >= 2 },
                    categoryIds = selectedCategoryIds.toList(),
                    limit = pickerPageSize,
                    offset = 0,
                    updateSharedState = true,
                    appendToSharedState = true
                ).collect { state ->
                    val payload = (state as? DataState.Success)?.payload.orEmpty()
                    if (aitaLatestCatalogueOwner0.owns(aitaLatestCatalogueTicket0)) serverLoadedItems = payload
                    serverEndReached = state is DataState.Empty || payload.size < pickerPageSize
                }
            } finally {
                if (aitaLatestCatalogueOwner0.owns(aitaLatestCatalogueTicket0)) loading = false
            }
        }

        LaunchedEffect(pickerPage, cleanQuery, selectedCategoryKey, serverLoadedItems.size, serverEndReached) {
            val desiredOffset = pickerPage * pickerPageSize
            if (desiredOffset <= 0 || desiredOffset < serverLoadedItems.size || serverEndReached || loading) return@LaunchedEffect
            loading = true
            try {
                getGenericGoodsItems(
                    query = cleanQuery.takeIf { it.length >= 2 },
                    categoryIds = selectedCategoryIds.toList(),
                    limit = pickerPageSize,
                    offset = desiredOffset,
                    updateSharedState = true,
                    appendToSharedState = true
                ).collect { state ->
                    val payload = (state as? DataState.Success)?.payload.orEmpty()
                    serverLoadedItems = (serverLoadedItems + payload).distinctBy { it.id }
                    serverEndReached = state is DataState.Empty || payload.size < pickerPageSize
                }
            } finally {
                loading = false
            }
        }

        val cachedFilteredItems = cachedGenericGoodsItems
            .orEmpty()
            .filter { it.matchesGlobalGoodsPickerFilter(cleanQuery, selectedCategoryIds) }
        val filteredItems = (serverLoadedItems + cachedFilteredItems)
            .distinctBy { it.id }
            .sortedWith(
                compareByDescending<GenericGoodsItemDataModel> { item ->
                    draft.standardBarcodeForGenericLookup()?.let { lookup ->
                        item.barcode.orEmpty().flatMap { it.toStoredGoodsItemBarcodeCandidates() }.any { it == lookup }
                    } == true
                }.thenBy { it.visibleGlobalGoodsName(stateValues.appLanguage).lowercase() }
                    .thenBy { it.id }
            )
        val displayItems = filteredItems.clientPaged(pickerPage, pickerPageSize)
        val pageWaitingForServer = !serverEndReached && pickerPage * pickerPageSize >= serverLoadedItems.size
        val totalItemsForPaging = if (serverEndReached) {
            filteredItems.size
        } else {
            ((pickerPage + 2) * pickerPageSize).coerceAtLeast(filteredItems.size)
        }

        Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = stateValues.screenHeight / 8)
        ) {
            when {
                displayItems.isEmpty() && (loading || pageWaitingForServer) -> item {
                    MessageText(
                        modifier = Modifier.fillParentMaxSize().fillMaxWidth(),
                        text = localizedStringResource(1141, "Please wait…"),
                        textSize = stateValues.textSize,
                        textColor = stateValues.PlaceholderTextColor
                    )
                }

                displayItems.isEmpty() -> item {
                    MessageText(
                        modifier = Modifier.fillParentMaxSize().fillMaxWidth(),
                        text = localizedStringResource(1189, "No global goods match these filters"),
                        textSize = stateValues.textSize
                    )
                }

                else -> {
                    items(displayItems, key = { it.id }) { item ->
                        GlobalGoodsPickerItemCard(
                            item = item,
                            selectedCategoryIds = selectedCategoryIds,
                            onApply = { onApply(item) }
                        )
                    }

                    if (loading) {
                        item {
                            MessageText(
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                text = localizedStringResource(1188, "Loading more goods…"),
                                textSize = stateValues.smallTextSize,
                                textColor = stateValues.PlaceholderTextColor
                            )
                        }
                    }

                    item {
                        PagingControls(
                            page = pickerPage,
                            totalItems = totalItemsForPaging,
                            pageSize = pickerPageSize,
                            onPageChange = { pickerPage = it.coerceAtLeast(0) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun AppConfiguration.StockAddEditInfoTab(
    modifier: Modifier = Modifier,
    draft: StockAddEditDraft,
    contentFillFraction: Float = if (stateValues.isNarrowScreen) 1f else 0.92f,
    draftPersistenceKey: String? = null,
    onDraftChanged: (StockAddEditDraft) -> Unit
) {
    var lastCategoryDefaultConditionsAppliedForId by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxWidth(contentFillFraction)
            .padding(start = 8.dp, top = 8.dp, end = 8.dp)
    ) {
        item {
            BarcodeListEditor(
                title = stateValues.stringBarcode,
                barcodes = draft.visibleBarcodes(),
                barcodeTypes = draft.visibleBarcodeTypes(),
                onChanged = { changedBarcodes ->
                    val nextBarcodes = changedBarcodes.ifEmpty { listOf("") }
                    onDraftChanged(
                        draft.copy(
                            barcodes = nextBarcodes,
                            barcodeTypes = draft.barcodeTypes.alignedStockBarcodeTypes(nextBarcodes)
                        )
                    )
                },
                onTypesChanged = { changedTypes ->
                    onDraftChanged(draft.copy(barcodeTypes = changedTypes.alignedStockBarcodeTypes(draft.visibleBarcodes())))
                },
                onBarcodesAndTypesChanged = { changedBarcodes, changedTypes ->
                    val nextBarcodes = changedBarcodes.ifEmpty { listOf("") }
                    onDraftChanged(
                        draft.copy(
                            barcodes = nextBarcodes,
                            barcodeTypes = changedTypes.alignedStockBarcodeTypes(nextBarcodes)
                        )
                    )
                }
            )

            Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

            StockLocalizedStringGroupEditor(
                title = stateValues.stringName,
                placeholder = stateValues.stringEnterName,
                values = draft.name,
                addText = stateValues.stringAddTranslation,
                required = true,
                persistentKey = draftPersistenceKey?.let { "$it:name" },
                onChanged = {
                    onDraftChanged(draft.copy(name = it))
                }
            )

            Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

            StockLocalizedStringGroupEditor(
                title = stateValues.stringDescription,
                placeholder = stateValues.stringEnterDescription,
                values = draft.description,
                addText = "${stateValues.stringAdd} ${stateValues.stringDescription}",
                required = false,
                persistentKey = draftPersistenceKey?.let { "$it:description" },
                onChanged = {
                    onDraftChanged(draft.copy(description = it))
                }
            )

            Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

            val measurementUnitDropdown = dropdownListWidget(
                titleText = stateValues.stringMeasurementUnit,
                domains = stockQuantityUnitDomains(),
                selectedInitial = draft.measurementUnitId,
                showId = false,
                showName = true
            )

            LaunchedEffect(measurementUnitDropdown.selectedId) {
                if (measurementUnitDropdown.selectedId != draft.measurementUnitId) {
                    onDraftChanged(draft.copy(measurementUnitId = measurementUnitDropdown.selectedId))
                }
            }

            Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

            val allCategories = stateValues.goodsCategories.orEmpty()
            val rootCategories = allCategories
                .filter { category -> category.typeIds.orEmpty().none { parentId -> allCategories.any { it.id == parentId } } }
                .sortedBy { it.name.visibleGoodsCategoryName(stateValues.appLanguage, it.id) }

            val selectedCategoryId = draft.categoryIds.firstOrNull()
            val selectedCategory = allCategories.find { it.id == selectedCategoryId }

            LaunchedEffect(selectedCategoryId, allCategories.size) {
                val defaults = defaultStockConditionsForGoodsCategory(selectedCategory)
                if (
                    selectedCategoryId != null &&
                    selectedCategoryId != lastCategoryDefaultConditionsAppliedForId &&
                    defaults.isNotEmpty()
                ) {
                    lastCategoryDefaultConditionsAppliedForId = selectedCategoryId
                    val missingDefaults = draft.conditions.missingDefaultStockConditions(defaults)
                    if (missingDefaults.isNotEmpty()) {
                        onDraftChanged(draft.copy(conditions = draft.conditions + missingDefaults.map { it.toStoredStockCondition() }))
                    }
                }
            }

            val selectedRootId = selectedCategory
                ?.typeIds
                ?.firstOrNull { parentId -> rootCategories.any { it.id == parentId } }
                ?: selectedCategoryId
                ?: rootCategories.firstOrNull()?.id

            val subcategories = allCategories
                .filter { it.typeIds.orEmpty().contains(selectedRootId) }
                .sortedBy { it.name.visibleGoodsCategoryName(stateValues.appLanguage, it.id) }

            if (rootCategories.isEmpty()) {
                Text(
                    text = stateValues.stringCategory,
                    color = stateValues.TextColor,
                    fontSize = stateValues.accentTextSize,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                MessageText(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(stateValues.textFieldHeight),
                    text = stateValues.stringListEmpty,
                    textSize = stateValues.textSize
                )
            } else {
                val rootCategoryDropdown = dropdownListWidget(
                    titleText = stateValues.stringCategory,
                    domains = rootCategories.map { category ->
                        val categoryName = category.name.visibleGoodsCategoryName(stateValues.appLanguage, category.id).toLocalizedSingleMain()
                        val categoryDescription = category.description?.visibleLocalizedString(stateValues.appLanguage, "")?.withoutGoodsCategoryPrefix()
                            ?: category.alias?.visibleLocalizedString(stateValues.appLanguage, "")?.withoutGoodsCategoryPrefix()
                            ?: ""

                        SelectableDomain(
                            id = category.id,
                            displayId = categoryName,
                            name = categoryDescription.takeIf { it.isNotBlank() }?.toLocalizedSingleMain() ?: categoryName,
                            iconPath = null,
                            iconRes = null
                        )
                    },
                    selectedInitial = selectedRootId ?: rootCategories.first().id,
                    showId = false,
                    showName = true,
                    search = Triple(stateValues.stringSearchByAnyData, NavigationScreenModel.Stock.AddEditGoodsItem, "stock_category_search"),
                    onSelected = { rootId ->
                        if (draft.categoryIds.firstOrNull() != rootId) {
                            onDraftChanged(draft.copy(categoryIds = listOf(rootId)))
                        }
                        rememberLatestStockAddEditCategorySelection(rootId, rootId)
                    }
                )

                val currentRootId = rootCategoryDropdown.selectedId
                val visibleSubcategories = allCategories
                    .filter { it.typeIds.orEmpty().contains(currentRootId) }
                    .sortedBy { it.name.visibleGoodsCategoryName(stateValues.appLanguage, it.id) }

                if (visibleSubcategories.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(stateValues.marginTextField))

                    val selectedSubcategoryId = selectedCategoryId?.takeIf { id ->
                        visibleSubcategories.any { it.id == id }
                    }

                    dropdownListWidget(
                        titleText = localizedStringResource(184, "Subcategory"),
                        domains = visibleSubcategories.map { category ->
                            val categoryName = category.name.visibleGoodsCategoryName(stateValues.appLanguage, category.id).toLocalizedSingleMain()
                            val categoryDescription = category.description?.visibleLocalizedString(stateValues.appLanguage, "")
                                ?: category.alias?.visibleLocalizedString(stateValues.appLanguage, "")
                                ?: ""

                            SelectableDomain(
                                id = category.id,
                                displayId = categoryName,
                                name = categoryDescription.takeIf { it.isNotBlank() }?.toLocalizedSingleMain() ?: categoryName,
                                iconPath = null,
                                iconRes = null
                            )
                        },
                        selectedInitial = selectedSubcategoryId ?: visibleSubcategories.first().id,
                        showId = false,
                        showName = true,
                        search = Triple(stateValues.stringSearchByAnyData, NavigationScreenModel.Stock.AddEditGoodsItem, "stock_subcategory_search"),
                        onSelected = { subcategoryId ->
                            if (draft.categoryIds.firstOrNull() != subcategoryId) {
                                onDraftChanged(draft.copy(categoryIds = listOf(subcategoryId)))
                            }
                            rememberLatestStockAddEditCategorySelection(currentRootId, subcategoryId)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(stateValues.textFieldHeight)
                    .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
                    .clip(RoundedCornerShape(stateValues.cornerRadius))
                    .border(
                        stateValues.unfocusedBorderWidth,
                        if (draft.isQuickItem) stateValues.AccentColor else stateValues.PlaceholderTextColor,
                        RoundedCornerShape(stateValues.cornerRadius)
                    )
                    .background(stateValues.BackgroundColor)
                    .aitaClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = stateValues.AccentColor),
                        onClick = {
                            onDraftChanged(draft.copy(isQuickItem = !draft.isQuickItem))
                        }
                    )
                    .padding(horizontal = stateValues.marginTextFieldGroup),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AitaRoundCheckbox(
                    checked = draft.isQuickItem,
                    onCheckedChange = {
                        onDraftChanged(draft.copy(isQuickItem = it))
                    }
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = stateValues.stringQuick,
                    color = stateValues.TextColor,
                    fontSize = stateValues.textSize,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

            StockLocalizedStringGroupEditor(
                title = localizedStringResource(201, "Notes"),
                placeholder = stateValues.stringOptional,
                values = draft.noteLocalized.ifEmpty {
                    draft.note.takeIf { it.isNotBlank() }?.let { listOf(LocalizedStringDataModel("main", it)) }
                        ?: emptyLocalizedItemForCurrentLanguage()
                },
                addText = localizedStringResource(307, "Add note translation"),
                required = false,
                singleLine = false,
                adaptiveMultiline = true,
                persistentKey = draftPersistenceKey?.let { "$it:note" },
                onChanged = {
                    onDraftChanged(
                        draft.copy(
                            noteLocalized = it,
                            note = it.extractLocalizedString("main") ?: it.firstOrNull { item -> item.value.isNotBlank() }?.value.orEmpty()
                        )
                    )
                }
            )

            Spacer(modifier = Modifier.height(stateValues.screenHeight / 5))
        }
    }
}

@Composable
internal fun AppConfiguration.StockAddEditConditionsTab(
    modifier: Modifier = Modifier,
    draft: StockAddEditDraft,
    contentFillFraction: Float = if (stateValues.isNarrowScreen) 1f else 0.92f,
    onDraftChanged: (StockAddEditDraft) -> Unit
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth(contentFillFraction)
            .padding(start = 8.dp, top = 8.dp, end = 8.dp)
    ) {
        item {
            StockConditionListEditor(
                title = localizedStringResource(609, "Conditions"),
                values = draft.conditions,
                onChanged = { onDraftChanged(draft.copy(conditions = it)) }
            )

            Spacer(modifier = Modifier.height(stateValues.screenHeight / 5))
        }
    }
}

@Composable
internal fun AppConfiguration.StockAddEditPricesTab(
    modifier: Modifier = Modifier,
    draft: StockAddEditDraft,
    defaultCurrency: String,
    returnPriceManuallyEdited: Boolean,
    onReturnPriceManuallyEditedChanged: (Boolean) -> Unit,
    contentFillFraction: Float = if (stateValues.isNarrowScreen) 1f else 0.92f,
    onDraftChanged: (StockAddEditDraft) -> Unit
) {
    val wholesaleQuantityUnit = stateValues.globalAppConfiguration.goodsItemsQuantityUnits
        .find { it.id == draft.measurementUnitId }
        ?: stateValues.globalAppConfiguration.goodsItemsQuantityUnits.firstOrNull()
        ?: QuantityDataModel(
            id = draft.measurementUnitId,
            immutableUnitName = emptyList(),
            total = 1.0,
            pricedAmount = 1.0,
            roundTotal = draft.measurementUnitId == "0"
        )
    val wholesaleAllowsFraction = wholesaleQuantityUnit.allowsFractionalStockQuantityInput()

    LazyColumn(
        modifier = modifier
            .fillMaxWidth(contentFillFraction)
            .padding(start = 8.dp, top = 8.dp, end = 8.dp)
    ) {
        item {
            StockPriceGroupEditor(
                title = stateValues.stringSalePrice,
                placeholder = stateValues.stringEnterSalePrice,
                prices = draft.salePrices.ifEmpty { listOf(PriceDataModel("", defaultCurrency, "")) },
                addText = "${stateValues.stringAdd} ${stateValues.stringSalePrice}",
                onChanged = { salePrices ->
                    val safeSalePrices = salePrices.ifEmpty { listOf(PriceDataModel("", defaultCurrency, "")) }
                    val updated = draft.copy(salePrices = safeSalePrices)

                    onDraftChanged(
                        if (returnPriceManuallyEdited) {
                            updated
                        } else {
                            updated.copy(
                                returnPrices = safeSalePrices.map { it.copy(supplierId = "") }
                            )
                        }
                    )
                }
            )

            Spacer(modifier = Modifier.height(stateValues.marginTextField))

            StockPriceGroupEditor(
                title = stateValues.stringReturnPrice,
                placeholder = stateValues.stringEnterReturnPrice,
                prices = draft.returnPrices.ifEmpty { listOf(PriceDataModel("", draft.salePrices.firstOrNull()?.currency ?: defaultCurrency, "")) },
                addText = "${stateValues.stringAdd} ${stateValues.stringReturnPrice}",
                onChanged = {
                    onReturnPriceManuallyEditedChanged(true)
                    onDraftChanged(
                        draft.copy(returnPrices = it.ifEmpty { listOf(PriceDataModel("", draft.salePrices.firstOrNull()?.currency ?: defaultCurrency, "")) })
                    )
                }
            )

            Spacer(modifier = Modifier.height(stateValues.marginTextField))

            StockPriceGroupEditor(
                title = stateValues.stringSupplyPrice,
                placeholder = stateValues.stringEnterSupplyPrice,
                prices = draft.supplyPrices.ifEmpty { listOf(PriceDataModel("", draft.salePrices.firstOrNull()?.currency ?: defaultCurrency, "")) },
                addText = "${stateValues.stringAdd} ${stateValues.stringSupplyPrice}",
                onChanged = {
                    onDraftChanged(
                        draft.copy(supplyPrices = it.ifEmpty { listOf(PriceDataModel("", draft.salePrices.firstOrNull()?.currency ?: defaultCurrency, "")) })
                    )
                }
            )

            Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

            StockPriceGroupEditor(
                title = localizedStringResource(246, "Wholesale price"),
                placeholder = localizedStringResource(246, "Wholesale price"),
                prices = draft.wholesalePrices.ifEmpty { listOf(PriceDataModel("", draft.salePrices.firstOrNull()?.currency ?: defaultCurrency, "")) },
                addText = "${stateValues.stringAdd} ${localizedStringResource(246, "Wholesale price")}",
                onChanged = {
                    onDraftChanged(
                        draft.copy(wholesalePrices = it.ifEmpty { listOf(PriceDataModel("", draft.salePrices.firstOrNull()?.currency ?: defaultCurrency, "")) })
                    )
                }
            )

            Spacer(modifier = Modifier.height(stateValues.marginTextField))

            genericTextField(
                titleText = localizedStringResource(248, "Wholesale minimum quantity"),
                valueInitial = draft.wholesaleMinQuantityText,
                placeholderText = localizedStringResource(249, "Enter wholesale minimum quantity"),
                keyboardType = if (wholesaleAllowsFraction) KeyboardType.Decimal else KeyboardType.Number,
                leadingIconPath = stateValues.drawablePathIconStock,
                showClearButton = true,
                contentInvalidText = localizedStringResource(251, "Not enough for wholesale"),
                onFilterValue = { value -> value.isStockQuantityInputText(wholesaleAllowsFraction) },
                onTransformValue = { raw -> sanitizeStockQuantityInput(raw, wholesaleAllowsFraction) },
                onValueChange = { value, applyChange ->
                    if (value.isStockQuantityInputText(wholesaleAllowsFraction)) {
                        applyChange()
                        onDraftChanged(draft.copy(wholesaleMinQuantityText = value))
                    }
                }
            )

            Spacer(modifier = Modifier.height(6.dp))

            StockQuantityQuickFillButtons(
                quantityUnit = wholesaleQuantityUnit,
                currentText = draft.wholesaleMinQuantityText,
                onAmountSelected = { selectedAmount ->
                    onDraftChanged(draft.copy(wholesaleMinQuantityText = selectedAmount))
                }
            )

            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                text = localizedStringResource(
                    274,
                    "Set the minimum amount required for the wholesale sale method. Leave wholesale price empty to disable it."
                ),
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize
            )

            Spacer(modifier = Modifier.height(stateValues.screenHeight / 5))
        }
    }
}

internal fun AppConfiguration.supplierOrderStatusTitle(status: SupplierOrderStatusDataModel): String = when (status) {
    SupplierOrderStatusDataModel.Draft -> localizedStringResource(966, "Draft")
    SupplierOrderStatusDataModel.Sent -> localizedStringResource(967, "Sent")
    SupplierOrderStatusDataModel.SeenBySupplier -> localizedStringResource(968, "Seen by supplier")
    SupplierOrderStatusDataModel.Confirmed -> localizedStringResource(969, "Confirmed")
    SupplierOrderStatusDataModel.Packed -> localizedStringResource(970, "Packed")
    SupplierOrderStatusDataModel.InDelivery -> localizedStringResource(971, "In delivery")
    SupplierOrderStatusDataModel.PartiallyDelivered -> localizedStringResource(972, "Partially delivered")
    SupplierOrderStatusDataModel.Delivered -> localizedStringResource(973, "Delivered")
    SupplierOrderStatusDataModel.IssueReported -> localizedStringResource(974, "Issue reported")
    SupplierOrderStatusDataModel.Cancelled -> localizedStringResource(975, "Cancelled")
}

internal fun SupplierOrderStatusDataModel.isSupplierOrderClosed(): Boolean =
    isClosedForSupplierDesk()

@Composable
internal fun AppConfiguration.SupplierOrderCard(
    order: SupplierOrderDataModel,
    lines: List<SupplierOrderLineDataModel>,
    goodsItem: GoodsItemDataModel,
    onReceive: () -> Unit,
    onCancel: () -> Unit
) {
    val supplierName = stateValues.suppliers.orEmpty()
        .find { it.id == order.supplierId }
        ?.name
        ?.visibleLocalizedString(stateValues.appLanguage, order.supplierId)
        ?: order.supplierId
    val primaryLine = lines.firstOrNull { it.goodsItemId == goodsItem.id }
    val quantityText = primaryLine?.requestedQuantity?.quantityText(stateValues.appLanguage).orEmpty()
    val acceptedQuantityText = primaryLine?.supplierAcceptedQuantity?.quantityText(stateValues.appLanguage).orEmpty()
    val priceText = primaryLine?.expectedSupplyPrice?.let { "${it.price} ${it.currency}" }.orEmpty()
    val offeredSupplyPriceText = (primaryLine?.supplierOfferedSupplyPrice).supplierDeskMoneyText()
    val substituteSuggestionText = primaryLine?.let { supplierDeskSubstituteTitle(it) }.orEmpty()
    val notesText = primaryLine?.additionalNotesLocalized?.extractLocalizedString(stateValues.appLanguage)
        ?: primaryLine?.additionalNotesLocalized?.extractLocalizedString("main")
        ?: primaryLine?.additionalNotes
        ?: order.additionalNotesLocalized.extractLocalizedString(stateValues.appLanguage)
        ?: order.additionalNotesLocalized.extractLocalizedString("main")
        ?: order.additionalNotes
    val supplierCommentText = primaryLine?.supplierCommentLocalized?.extractLocalizedString(stateValues.appLanguage)
        ?: primaryLine?.supplierCommentLocalized?.extractLocalizedString("main")
        ?: primaryLine?.supplierComment
        ?: order.supplierCommentLocalized.extractLocalizedString(stateValues.appLanguage)
        ?: order.supplierCommentLocalized.extractLocalizedString("main")
        ?: order.supplierComment

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
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = supplierName,
                    color = stateValues.TextColor,
                    fontSize = stateValues.accentTextSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = supplierOrderStatusTitle(order.status),
                    color = if (order.status.isSupplierOrderClosed()) stateValues.PlaceholderTextColor else stateValues.AccentColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = offeredSupplyPriceText.ifBlank { priceText.ifBlank { order.amount?.let { "${it.price} ${it.currency}" }.orEmpty() } },
                color = stateValues.AccentColor,
                fontSize = stateValues.accentTextSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        StockCardInfoLine(
            title = localizedStringResource(960, "Ordered quantity"),
            value = quantityText.ifBlank { lines.size.toString() },
            textColor = stateValues.TextColor
        )

        acceptedQuantityText.takeIf { it.isNotBlank() }?.let {
            StockCardInfoLine(localizedStringResource(1608, "Accepted quantity"), it, stateValues.TextColor)
        }

        offeredSupplyPriceText.takeIf { it.isNotBlank() }?.let {
            StockCardInfoLine(localizedStringResource(1609, "Offered supply price"), it, stateValues.TextColor)
        }

        substituteSuggestionText.takeIf { it.isNotBlank() }?.let {
            StockCardInfoLine(localizedStringResource(1658, "Substitute suggestion"), it, stateValues.AccentColor)
        }

        order.desiredDeliveryTimeMillis?.toStockDateInputText()?.takeIf { it.isNotBlank() }?.let {
            StockCardInfoLine(localizedStringResource(956, "Desired delivery"), it, stateValues.TextColor)
        }

        order.confirmedDeliveryTimeMillis?.toStockDateInputText()?.takeIf { it.isNotBlank() }?.let {
            StockCardInfoLine(localizedStringResource(1606, "Confirmed delivery"), it, stateValues.TextColor)
        }

        primaryLine?.desiredExpirationDateMillis?.toStockDateInputText()?.takeIf { it.isNotBlank() }?.let {
            StockCardInfoLine(localizedStringResource(957, "Desired expiration"), it, stateValues.TextColor)
        }

        notesText?.takeIf { it.isNotBlank() }?.let {
            StockCardInfoLine(localizedStringResource(201, "Notes"), it, stateValues.TextColor)
        }

        order.externalReference?.takeIf { it.isNotBlank() }?.let {
            StockCardInfoLine(localizedStringResource(1607, "External reference"), it, stateValues.TextColor)
        }

        order.paymentTerms?.takeIf { it.isNotBlank() }?.let {
            StockCardInfoLine(localizedStringResource(1601, "Payment terms"), it, stateValues.TextColor)
        }

        supplierCommentText?.takeIf { it.isNotBlank() }?.let {
            StockCardInfoLine(localizedStringResource(961, "Supplier response ready"), it, stateValues.TextColor)
        }

        Text(
            modifier = Modifier.padding(top = 6.dp),
            text = localizedStringResource(964, "This order keeps store-side data ready for the future supplier app: supplier, quantities, expected price, delivery dates, notes and receiving batches."),
            color = stateValues.PlaceholderTextColor,
            fontSize = stateValues.smallTextSize
        )

        if (!order.status.isSupplierOrderClosed()) {
            Spacer(modifier = Modifier.height(stateValues.marginTextField))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
            ) {
                actionButton(
                    modifier = Modifier.weight(1f),
                    text = localizedStringResource(979, "Receive full quantity"),
                    iconPath = stateValues.drawablePathIconTransactionSupply,
                    confirmationRequired = true,
                    onClick = onReceive
                )

                actionButton(
                    modifier = Modifier.weight(1f),
                    text = localizedStringResource(965, "Cancel order"),
                    iconPath = stateValues.drawablePathIconCancel,
                    enabledColor = stateValues.ErrorColor,
                    confirmationRequired = true,
                    onClick = onCancel
                )
            }
        }
    }
}

internal data class SupplierFeaturePlanUiModel(
    val title: String,
    val subtitle: String,
    val iconPath: String,
    val iconRes: DrawableResource?,
    val implemented: Boolean = false
)

internal fun AppConfiguration.supplierMarketWinningFeatures(): List<SupplierFeaturePlanUiModel> = listOf(
    SupplierFeaturePlanUiModel(
        title = localizedStringResource(1341, "Smart order inbox"),
        subtitle = localizedStringResource(1342, "See every store request, answer quickly, and keep fulfillment moving from sent to delivery."),
        iconPath = stateValues.drawablePathIconAppModeSupplier,
        iconRes = stateValues.drawableResIconAppModeSupplier.value,
        implemented = true
    ),
    SupplierFeaturePlanUiModel(
        title = localizedStringResource(1343, "Live B2B catalog"),
        subtitle = localizedStringResource(1344, "Turn store stock items into supplier-side offers with MOQ, pack sizes, expiry rules and per-store prices."),
        iconPath = stateValues.drawablePathIconSupplierCatalog,
        iconRes = stateValues.drawableResIconSupplierCatalog.value,
        implemented = true
    ),
    SupplierFeaturePlanUiModel(
        title = localizedStringResource(1518, "Contract guardrails"),
        subtitle = localizedStringResource(1519, "Attach age, time, margin, price and schedule rules to a partner, a goods group, or one item before supply starts."),
        iconPath = stateValues.drawablePathIconSupplierContracts,
        iconRes = stateValues.drawableResIconSupplierContracts.value,
        implemented = true
    ),
    SupplierFeaturePlanUiModel(
        title = localizedStringResource(1345, "Substitutions that save sales"),
        subtitle = localizedStringResource(1346, "Suggest replacements when a SKU is out of stock, with clear approval before the store receives it."),
        iconPath = stateValues.drawablePathIconResponse,
        iconRes = stateValues.drawableResIconResponse.value,
        implemented = true
    ),
    SupplierFeaturePlanUiModel(
        title = localizedStringResource(1347, "Route batch planner"),
        subtitle = localizedStringResource(1348, "Group nearby KZ, KG, TJ and UZ store deliveries into efficient runs and clear driver packs."),
        iconPath = stateValues.drawablePathIconSupplierDispatch,
        iconRes = stateValues.drawableResIconSupplierDispatch.value,
        implemented = true
    ),
    SupplierFeaturePlanUiModel(
        title = localizedStringResource(1349, "Price ladder and payment terms"),
        subtitle = localizedStringResource(1350, "Manage wholesale tiers, local currencies, deferred payments and trusted-store limits."),
        iconPath = stateValues.drawablePathIconFinances,
        iconRes = stateValues.drawableResIconFinances.value,
        implemented = true
    ),
    SupplierFeaturePlanUiModel(
        title = localizedStringResource(1351, "Partner store overview"),
        subtitle = localizedStringResource(1352, "See exact orders, deliveries, contracts and saved offers for every partner store."),
        iconPath = stateValues.drawablePathIconSupplierPartners,
        iconRes = stateValues.drawableResIconSupplierPartners.value,
        implemented = true
    ),
    SupplierFeaturePlanUiModel(
        title = localizedStringResource(1353, "Demand radar"),
        subtitle = localizedStringResource(1354, "Read reorder rhythm from store orders and prepare stock before the call comes."),
        iconPath = stateValues.drawablePathIconSupplierDemandRadar,
        iconRes = stateValues.drawableResIconSupplierDemandRadar.value,
        implemented = true
    ),
    SupplierFeaturePlanUiModel(
        title = localizedStringResource(1355, "Manufacturer backorder bridge"),
        subtitle = localizedStringResource(1356, "Push confirmed demand upstream to producers and keep stores updated on replenishment."),
        iconPath = stateValues.drawablePathIconAppModeManufacturer,
        iconRes = stateValues.drawableResIconAppModeManufacturer.value,
        implemented = true
    )
)

internal fun AppConfiguration.supplierDeskAllowedStatuses(): List<SupplierOrderStatusDataModel> = listOf(
    SupplierOrderStatusDataModel.SeenBySupplier,
    SupplierOrderStatusDataModel.Confirmed,
    SupplierOrderStatusDataModel.Packed,
    SupplierOrderStatusDataModel.InDelivery,
    SupplierOrderStatusDataModel.IssueReported,
    SupplierOrderStatusDataModel.Cancelled
)

internal fun AppConfiguration.supplierDeskStoreTitle(order: SupplierOrderDataModel): String {
    return order.storeNameSnapshot.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { order.storePublicIdSnapshot }
        .ifBlank { order.storeAddressTextSnapshot }
        .ifBlank { order.storeId.take(8) }
}

internal fun AppConfiguration.supplierDeskLineTitle(line: SupplierOrderLineDataModel): String {
    return line.goodsItemNameSnapshot.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { line.goodsItemBarcodeSnapshots.firstOrNull().orEmpty() }
        .ifBlank { line.goodsItemId.take(8) }
}

internal fun AppConfiguration.supplierDeskSubstituteTitle(line: SupplierOrderLineDataModel): String {
    val substituteId = line.substituteGoodsItemId?.takeIf { it.isNotBlank() } ?: return ""
    return line.substituteGoodsItemNameSnapshot.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { line.substituteGoodsItemBarcodeSnapshots.firstOrNull().orEmpty() }
        .ifBlank { substituteId.take(8) }
}

internal fun AppConfiguration.supplierDeskFulfillmentLineTitle(line: SupplierOrderLineDataModel): String =
    supplierDeskSubstituteTitle(line).ifBlank { supplierDeskLineTitle(line) }

internal fun SupplierOrderLineDataModel.supplierDeskFulfillmentQuantity(): QuantityDataModel =
    supplierAcceptedQuantity ?: requestedQuantity

internal fun AppConfiguration.buildSupplierSubstituteOptions(lines: List<SupplierOrderLineDataModel>): List<SupplierSubstituteOptionUiModel> {
    return lines
        .asSequence()
        .filter { it.isActive && it.goodsItemId.isNotBlank() }
        .groupBy { it.goodsItemId }
        .map { (goodsItemId, itemLines) ->
            val sample = itemLines.firstOrNull()
            val title = sample?.let { supplierDeskLineTitle(it) }.orEmpty().ifBlank { goodsItemId.take(8) }
            val barcodeText = itemLines
                .asSequence()
                .flatMap { it.goodsItemBarcodeSnapshots.asSequence() }
                .firstOrNull { it.isNotBlank() }
                .orEmpty()
            SupplierSubstituteOptionUiModel(
                goodsItemId = goodsItemId,
                title = title,
                subtitle = barcodeText.ifBlank { localizedStringResource(1656, "Supplier catalog item") }
            )
        }
        .sortedBy { it.title.lowercase() }
}

internal fun PriceDataModel?.supplierDeskMoneyText(): String = this?.let { price ->
    listOf(price.price, price.currency).filter { it.isNotBlank() }.joinToString(" ")
}.orEmpty()

internal fun SupplierOrderDataModel.supplierDeskSortTime(): Long =
    updatedAtMillis.takeIf { it > 0L } ?: orderedAtMillis.takeIf { it > 0L } ?: createdAtMillis

internal fun AppConfiguration.supplierDeskOrderSearchText(
    order: SupplierOrderDataModel,
    lines: List<SupplierOrderLineDataModel>
): String = buildString {
    append(order.id).append(' ')
    append(order.storeId).append(' ')
    append(order.supplierId).append(' ')
    append(supplierDeskStoreTitle(order)).append(' ')
    append(order.storePublicIdSnapshot).append(' ')
    append(order.storeAddressTextSnapshot).append(' ')
    append(order.status.name).append(' ')
    append(supplierOrderStatusTitle(order.status)).append(' ')
    append(order.additionalNotes.orEmpty()).append(' ')
    append(order.supplierComment.orEmpty()).append(' ')
    append(order.paymentTerms.orEmpty()).append(' ')
    append(order.externalReference.orEmpty()).append(' ')
    append(order.desiredDeliveryTimeMillis.toStockDateInputText()).append(' ')
    append(order.confirmedDeliveryTimeMillis.toStockDateInputText()).append(' ')
    append(supplierDeliveryBucketTitle(supplierUiDeliveryBucketId(getCurrentTimeMillis(), order.supplierDueAtMillis()))).append(' ')
    lines.forEach { line ->
        append(line.goodsItemId).append(' ')
        append(supplierDeskLineTitle(line)).append(' ')
        append(line.goodsItemBarcodeSnapshots.joinToString(" ")).append(' ')
        append(line.requestedQuantity.quantityText(stateValues.appLanguage)).append(' ')
        append(line.expectedSupplyPrice.supplierDeskMoneyText()).append(' ')
        append(line.supplierAcceptedQuantity?.quantityText(stateValues.appLanguage).orEmpty()).append(' ')
        append(line.supplierOfferedSupplyPrice.supplierDeskMoneyText()).append(' ')
        append(line.substituteGoodsItemId.orEmpty()).append(' ')
        append(supplierDeskSubstituteTitle(line)).append(' ')
        append(line.substituteGoodsItemBarcodeSnapshots.joinToString(" ")).append(' ')
        append(line.additionalNotes.orEmpty()).append(' ')
        append(line.supplierComment.orEmpty()).append(' ')
    }
}.lowercase()

internal fun AppConfiguration.supplierDashboardActionTitle(action: SupplierDashboardActionDataModel): String = when (action.actionType) {
    "issue" -> localizedStringResource(1637, "Resolve supplier issue")
    "answer" -> localizedStringResource(1638, "Answer store request")
    "complete_response" -> localizedStringResource(1639, "Complete quantities and prices")
    "pack" -> localizedStringResource(1640, "Pack confirmed goods")
    "dispatch" -> localizedStringResource(1641, "Start delivery run")
    "contract" -> localizedStringResource(1737, "Clear contract")
    "terms" -> localizedStringResource(1642, "Add delivery terms")
    "delivery" -> localizedStringResource(1643, "Follow delivery")
    else -> localizedStringResource(1644, "Supplier action")
}

internal fun AppConfiguration.supplierDashboardActionStoreTitle(action: SupplierDashboardActionDataModel): String =
    action.storeNameSnapshot.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { action.storePublicIdSnapshot }
        .ifBlank { action.storeId.take(8) }
        .ifBlank { localizedStringResource(1453, "Partner stores") }

internal fun AppConfiguration.supplierDashboardActionSubtitle(action: SupplierDashboardActionDataModel): String {
    val dueText = action.dueAtMillis?.toStockDateInputText()?.takeIf { it.isNotBlank() }?.let { due ->
        "${localizedStringResource(1645, "Due")}: $due"
    }
    val goodsText = action.goodsPreview.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { action.goodsPreview.visibleLocalizedString("main", "") }
    val missingText = listOfNotNull(
        action.missingAcceptedQuantityCount.takeIf { it > 0 }?.let { "${localizedStringResource(1646, "Qty gaps")}: $it" },
        action.missingOfferedPriceCount.takeIf { it > 0 }?.let { "${localizedStringResource(1647, "Price gaps")}: $it" }
    ).joinToString(" • ")

    return listOfNotNull(
        supplierDashboardActionStoreTitle(action),
        supplierOrderStatusTitle(action.status),
        dueText,
        goodsText.takeIf { it.isNotBlank() },
        missingText.takeIf { it.isNotBlank() }
    ).joinToString(" • ")
}

internal fun AppConfiguration.supplierDashboardActionIcon(action: SupplierDashboardActionDataModel): Pair<String, DrawableResource?> = when (action.actionType) {
    "issue" -> stateValues.drawablePathIconResponse to stateValues.drawableResIconResponse.value
    "answer", "complete_response" -> stateValues.drawablePathIconAppModeSupplier to stateValues.drawableResIconAppModeSupplier.value
    "pack" -> stateValues.drawablePathIconStock to stateValues.drawableResIconStock.value
    "dispatch", "delivery" -> stateValues.drawablePathIconSupplierDispatch to stateValues.drawableResIconSupplierDispatch.value
    "contract", "terms" -> stateValues.drawablePathIconSupplierContracts to stateValues.drawableResIconSupplierContracts.value
    else -> stateValues.drawablePathIconAppModeSupplier to stateValues.drawableResIconAppModeSupplier.value
}

internal fun SupplierDashboardActionDataModel.supplierDashboardQuickStatus(): SupplierOrderStatusDataModel? = when (actionType) {
    "answer" -> SupplierOrderStatusDataModel.SeenBySupplier.takeIf { status == SupplierOrderStatusDataModel.Sent }
    "pack" -> SupplierOrderStatusDataModel.Packed
    "dispatch" -> SupplierOrderStatusDataModel.InDelivery
    else -> null
}

internal fun AppConfiguration.supplierDashboardQuickActionLabel(status: SupplierOrderStatusDataModel): String = when (status) {
    SupplierOrderStatusDataModel.SeenBySupplier -> localizedStringResource(1753, "Mark seen")
    SupplierOrderStatusDataModel.Packed -> localizedStringResource(1570, "Mark packed")
    SupplierOrderStatusDataModel.InDelivery -> localizedStringResource(1571, "Start delivery")
    else -> localizedStringResource(1754, "Quick step")
}

internal fun AppConfiguration.supplierDashboardQuickActionIcon(status: SupplierOrderStatusDataModel): Pair<String, DrawableResource?> = when (status) {
    SupplierOrderStatusDataModel.Packed,
    SupplierOrderStatusDataModel.InDelivery -> stateValues.drawablePathIconSupplierDispatch to stateValues.drawableResIconSupplierDispatch.value
    else -> stateValues.drawablePathIconCheck to stateValues.drawableResIconCheck.value
}

@Composable
internal fun AppConfiguration.SupplierActionQueueCard(
    actions: List<SupplierDashboardActionDataModel>,
    bulkSeenOrderIds: List<String> = emptyList(),
    bulkPackableOrderIds: List<String> = emptyList(),
    bulkDispatchableOrderIds: List<String> = emptyList()
) {
    if (actions.isEmpty() && bulkSeenOrderIds.isEmpty() && bulkPackableOrderIds.isEmpty() && bulkDispatchableOrderIds.isEmpty()) return

    fun cleanSupplierBulkIds(ids: List<String>): List<String> = ids
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()

    val visibleSeenOrderIds = remember(actions) {
        actions
            .filter { action -> action.actionType == "answer" && action.status == SupplierOrderStatusDataModel.Sent }
            .map { action -> action.orderId.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }
    val visiblePackOrderIds = remember(actions) {
        actions
            .filter { action -> action.actionType == "pack" && action.status == SupplierOrderStatusDataModel.Confirmed }
            .map { action -> action.orderId.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }
    val visibleDispatchOrderIds = remember(actions) {
        actions
            .filter { action -> action.actionType == "dispatch" && action.status == SupplierOrderStatusDataModel.Packed }
            .map { action -> action.orderId.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }
    val sentActionOrderIds = remember(bulkSeenOrderIds, visibleSeenOrderIds) {
        cleanSupplierBulkIds(bulkSeenOrderIds).ifEmpty { visibleSeenOrderIds }
    }
    val packActionOrderIds = remember(bulkPackableOrderIds, visiblePackOrderIds) {
        cleanSupplierBulkIds(bulkPackableOrderIds).ifEmpty { visiblePackOrderIds }
    }
    val dispatchActionOrderIds = remember(bulkDispatchableOrderIds, visibleDispatchOrderIds) {
        cleanSupplierBulkIds(bulkDispatchableOrderIds).ifEmpty { visibleDispatchOrderIds }
    }
    val bulkCoversMoreThanVisible = sentActionOrderIds.size > visibleSeenOrderIds.size ||
            packActionOrderIds.size > visiblePackOrderIds.size ||
            dispatchActionOrderIds.size > visibleDispatchOrderIds.size

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
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
                modifier = Modifier.size(38.dp),
                url = stateValues.drawablePathIconResponse,
                fallbackRes = stateValues.drawableResIconResponse.value,
                contentDescription = localizedStringResource(1648, "Supplier action queue"),
                tintColor = stateValues.AccentColor
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = localizedStringResource(1648, "Supplier action queue"),
                    color = stateValues.TextColor,
                    fontSize = stateValues.titleTextSize,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = localizedStringResource(1649, "Server-ranked next moves: answer, fill gaps, pack, dispatch, or fix issues before the queue turns into noise."),
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize
                )
            }
        }

        val serverBulkCount = (sentActionOrderIds + packActionOrderIds + dispatchActionOrderIds).distinct().size
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) { SupplierCatalogChip(text = "${localizedStringResource(1777, "Visible cards")}: ${actions.size}") }
            Box(modifier = Modifier.weight(1f)) { SupplierCatalogChip(text = "${localizedStringResource(1778, "Server-safe bulk")}: $serverBulkCount") }
        }
        if (bulkCoversMoreThanVisible) {
            Text(
                text = localizedStringResource(1782, "Bulk buttons use all server-known safe orders, not only the visible cards below."),
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize
            )
        }

        if (sentActionOrderIds.isNotEmpty()) {
            actionButton(
                modifier = Modifier.fillMaxWidth(),
                text = "${localizedStringResource(1761, "Mark all seen")} • ${sentActionOrderIds.size}",
                subText = localizedStringResource(1762, "New requests"),
                iconPath = stateValues.drawablePathIconCheck,
                iconRes = stateValues.drawableResIconCheck.value,
                textSize = stateValues.smallTextSize,
                confirmationRequired = false,
                onClick = {
                    updateSupplierOrdersSupplierStatusByIds(
                        orderIds = sentActionOrderIds,
                        status = SupplierOrderStatusDataModel.SeenBySupplier
                    )
                }
            )
        }

        if (packActionOrderIds.isNotEmpty()) {
            actionButton(
                modifier = Modifier.fillMaxWidth(),
                text = "${localizedStringResource(1769, "Pack all ready")} • ${packActionOrderIds.size}",
                iconPath = stateValues.drawablePathIconSupplierDispatch,
                iconRes = stateValues.drawableResIconSupplierDispatch.value,
                textSize = stateValues.smallTextSize,
                confirmationRequired = true,
                onClick = {
                    updateSupplierOrdersSupplierStatusByIds(
                        orderIds = packActionOrderIds,
                        status = SupplierOrderStatusDataModel.Packed
                    )
                }
            )
        }

        if (dispatchActionOrderIds.isNotEmpty()) {
            actionButton(
                modifier = Modifier.fillMaxWidth(),
                text = "${localizedStringResource(1770, "Start all packed")} • ${dispatchActionOrderIds.size}",
                iconPath = stateValues.drawablePathIconSupplierDispatch,
                iconRes = stateValues.drawableResIconSupplierDispatch.value,
                textSize = stateValues.smallTextSize,
                confirmationRequired = true,
                onClick = {
                    updateSupplierOrdersSupplierStatusByIds(
                        orderIds = dispatchActionOrderIds,
                        status = SupplierOrderStatusDataModel.InDelivery
                    )
                }
            )
        }

        actions.take(6).forEach { action ->
            SupplierActionQueueItem(action = action)
        }
    }
}

@Composable
internal fun AppConfiguration.SupplierActionQueueItem(action: SupplierDashboardActionDataModel) {
    val coroutineScope = rememberCoroutineScope()
    val icon = supplierDashboardActionIcon(action)
    val searchSeed = action.orderId.ifBlank { action.storeId.ifBlank { action.storePublicIdSnapshot } }
    val quickStatus = remember(action.actionId, action.actionType, action.status) { action.supplierDashboardQuickStatus() }
    val quickActionIcon = quickStatus?.let { supplierDashboardQuickActionIcon(it) }
    val attentionText = action.attentionSummary.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { action.attentionSummary.visibleLocalizedString("main", "") }

    val primaryActionTitle = if (action.actionType == "contract") {
        localizedStringResource(1737, "Clear contract")
    } else {
        localizedStringResource(1752, "Open order")
    }

    fun openOrderFromQueue() {
        coroutineScope.launch {
            if (action.actionType == "contract") {
                Navigation.goMain(NavigationScreenModel.Supplier.Contracts.Main)
            } else {
                seedSupplierOrdersInboxNavigation(searchQuery = searchSeed)
                Navigation.goMain(NavigationScreenModel.Supplier.Orders.Main)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.AccentColor.copy(alpha = 0.07f))
            .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor.copy(alpha = 0.35f), RoundedCornerShape(stateValues.cornerRadius))
            .padding(stateValues.marginTextField),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .aitaClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(color = stateValues.AccentColor),
                    onClick = { openOrderFromQueue() }
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            CpImage(
                modifier = Modifier.size(28.dp),
                url = icon.first,
                fallbackRes = icon.second,
                contentDescription = supplierDashboardActionTitle(action),
                tintColor = stateValues.AccentColor
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = supplierDashboardActionTitle(action),
                    color = stateValues.TextColor,
                    fontSize = stateValues.textSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = supplierDashboardActionSubtitle(action),
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = action.priority.toString(),
                color = stateValues.AccentColor,
                fontSize = stateValues.smallTextSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End
            )
        }

        if (attentionText.isNotBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(stateValues.cornerRadius))
                    .background(stateValues.BackgroundColor.copy(alpha = 0.72f))
                    .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor.copy(alpha = 0.28f), RoundedCornerShape(stateValues.cornerRadius))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = localizedStringResource(1756, "Attention notes"),
                    color = stateValues.AccentColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = attentionText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else if (quickStatus == null) {
            Text(
                text = localizedStringResource(1755, "Open the order to fill quantities, prices, delivery terms, or issue notes."),
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize
            )
        }

        if (stateValues.isNarrowScreen) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = primaryActionTitle,
                    iconPath = if (action.actionType == "contract") stateValues.drawablePathIconSupplierContracts else stateValues.drawablePathIconAppModeSupplier,
                    iconRes = if (action.actionType == "contract") stateValues.drawableResIconSupplierContracts.value else stateValues.drawableResIconAppModeSupplier.value,
                    textSize = stateValues.smallTextSize,
                    confirmationRequired = false,
                    onClick = { openOrderFromQueue() }
                )
                quickStatus?.let { status ->
                    actionButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = supplierDashboardQuickActionLabel(status),
                        enabled = action.orderId.isNotBlank(),
                        iconPath = quickActionIcon?.first,
                        iconRes = quickActionIcon?.second,
                        textSize = stateValues.smallTextSize,
                        confirmationRequired = status == SupplierOrderStatusDataModel.Packed || status == SupplierOrderStatusDataModel.InDelivery,
                        onClick = { updateSupplierOrdersSupplierStatusByIds(listOf(action.orderId), status) }
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
            ) {
                actionButton(
                    modifier = Modifier.weight(1f),
                    text = primaryActionTitle,
                    iconPath = if (action.actionType == "contract") stateValues.drawablePathIconSupplierContracts else stateValues.drawablePathIconAppModeSupplier,
                    iconRes = if (action.actionType == "contract") stateValues.drawableResIconSupplierContracts.value else stateValues.drawableResIconAppModeSupplier.value,
                    textSize = stateValues.smallTextSize,
                    confirmationRequired = false,
                    onClick = { openOrderFromQueue() }
                )
                quickStatus?.let { status ->
                    actionButton(
                        modifier = Modifier.weight(1f),
                        text = supplierDashboardQuickActionLabel(status),
                        enabled = action.orderId.isNotBlank(),
                        iconPath = quickActionIcon?.first,
                        iconRes = quickActionIcon?.second,
                        textSize = stateValues.smallTextSize,
                        confirmationRequired = status == SupplierOrderStatusDataModel.Packed || status == SupplierOrderStatusDataModel.InDelivery,
                        onClick = { updateSupplierOrdersSupplierStatusByIds(listOf(action.orderId), status) }
                    )
                }
            }
        }
    }
}

@Composable
internal fun AppConfiguration.SupplierReadinessBoardCard(
    readiness: SupplierDashboardReadinessDataModel,
    onOpenAnswerGaps: () -> Unit,
    onOpenPackQueue: () -> Unit
) {
    if (!readiness.hasSupplierReadinessSignal()) return

    val gapCount = readiness.missingAcceptedQuantityLineCount + readiness.missingOfferedPriceLineCount
    val responseFilledText = if (readiness.responseLineCount > 0) {
        "${readiness.answeredLineCount}/${readiness.responseLineCount} • ${readiness.responseProgressPercent}%"
    } else {
        "0%"
    }
    val acceptedQuantityText = listOf(
        readiness.acceptedQuantityTotal.toStockMoneyText(),
        readiness.requestedQuantityTotal.takeIf { it > 0.0 }?.toStockMoneyText()?.let { requested -> "/ $requested" }.orEmpty(),
        readiness.acceptedVsRequestedPercent.takeIf { readiness.requestedQuantityTotal > 0.0 }?.let { percent -> "• $percent%" }.orEmpty()
    ).filter { it.isNotBlank() }.joinToString(" ")
    val readyValue = readiness.estimatedReadyAmount.supplierDeskMoneyText()
        .ifBlank { readiness.readyToPackOrderCount.takeIf { it > 0 }?.toString().orEmpty() }
        .ifBlank { "0" }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
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
                modifier = Modifier.size(38.dp),
                url = stateValues.drawablePathIconSupplierCatalog,
                fallbackRes = stateValues.drawableResIconSupplierCatalog.value,
                contentDescription = localizedStringResource(1686, "Readiness board"),
                tintColor = stateValues.AccentColor
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = localizedStringResource(1686, "Readiness board"),
                    color = stateValues.TextColor,
                    fontSize = stateValues.titleTextSize,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = localizedStringResource(1687, "Server checks open orders for answer gaps, saved price coverage and pack-ready lines before the supplier starts clicking."),
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize
                )
            }
        }

        if (stateValues.isNarrowScreen) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SupplierCatalogChip(text = "${localizedStringResource(1688, "Answer needed")}: ${readiness.answerNeededOrderCount}")
                SupplierCatalogChip(text = "${localizedStringResource(1689, "Ready to pack")}: ${readiness.readyToPackOrderCount}")
                SupplierCatalogChip(text = "${localizedStringResource(1771, "Response filled")}: ${readiness.responseProgressPercent}%")
                SupplierCatalogChip(text = "${localizedStringResource(1690, "Price coverage")}: ${readiness.priceBookCoveragePercent}%")
                SupplierCatalogChip(text = "${localizedStringResource(1691, "Response gaps")}: $gapCount")
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) { SupplierCatalogChip(text = "${localizedStringResource(1688, "Answer needed")}: ${readiness.answerNeededOrderCount}") }
                Box(modifier = Modifier.weight(1f)) { SupplierCatalogChip(text = "${localizedStringResource(1689, "Ready to pack")}: ${readiness.readyToPackOrderCount}") }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) { SupplierCatalogChip(text = "${localizedStringResource(1771, "Response filled")}: ${readiness.responseProgressPercent}%") }
                Box(modifier = Modifier.weight(1f)) { SupplierCatalogChip(text = "${localizedStringResource(1690, "Price coverage")}: ${readiness.priceBookCoveragePercent}%") }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) { SupplierCatalogChip(text = "${localizedStringResource(1691, "Response gaps")}: $gapCount") }
                Box(modifier = Modifier.weight(1f)) { SupplierCatalogChip(text = "${localizedStringResource(1773, "Declined lines")}: ${readiness.declinedLineCount}") }
            }
        }

        StockCardInfoLine(localizedStringResource(1692, "Ready value"), readyValue, stateValues.AccentColor)
        StockCardInfoLine(localizedStringResource(1771, "Response filled"), responseFilledText, if (readiness.responseProgressPercent >= 100 && readiness.responseLineCount > 0) stateValues.AccentColor else stateValues.TextColor)
        StockCardInfoLine(localizedStringResource(1772, "Accepted quantity"), acceptedQuantityText, stateValues.TextColor)
        StockCardInfoLine(localizedStringResource(1699, "Pack-ready lines"), readiness.packReadyLineCount.toString(), stateValues.TextColor)
        readiness.earliestDueAtMillis?.toStockDateInputText()?.takeIf { it.isNotBlank() }?.let { due ->
            StockCardInfoLine(localizedStringResource(1645, "Due"), due, stateValues.TextColor)
        }

        if (stateValues.isNarrowScreen) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
            ) {
                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(1693, "Open answer gaps"),
                    iconPath = stateValues.drawablePathIconResponse,
                    iconRes = stateValues.drawableResIconResponse.value,
                    confirmationRequired = false,
                    onClick = onOpenAnswerGaps
                )
                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(1694, "Open pack queue"),
                    iconPath = stateValues.drawablePathIconStock,
                    iconRes = stateValues.drawableResIconStock.value,
                    confirmationRequired = false,
                    onClick = onOpenPackQueue
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
            ) {
                actionButton(
                    modifier = Modifier.weight(1f),
                    text = localizedStringResource(1693, "Open answer gaps"),
                    iconPath = stateValues.drawablePathIconResponse,
                    iconRes = stateValues.drawableResIconResponse.value,
                    confirmationRequired = false,
                    onClick = onOpenAnswerGaps
                )
                actionButton(
                    modifier = Modifier.weight(1f),
                    text = localizedStringResource(1694, "Open pack queue"),
                    iconPath = stateValues.drawablePathIconStock,
                    iconRes = stateValues.drawableResIconStock.value,
                    confirmationRequired = false,
                    onClick = onOpenPackQueue
                )
            }
        }
    }
}


internal const val SUPPLIER_ORDER_DUE_FILTER_STATE_KEY: String = "supplier_order_due_filter"
internal const val SUPPLIER_ORDER_STATUS_FILTER_STATE_KEY: String = "supplier_order_status_filter"
internal const val AITA_SUPPLIER_UI_DAY_MILLIS: Long = 24L * 60L * 60L * 1000L

internal fun supplierUiDayStartMillis(now: Long): Long = runCatching {
    val timezone = TimeZone.currentSystemDefault()
    Instant
        .fromEpochMilliseconds(now)
        .toLocalDateTime(timezone)
        .date
        .atStartOfDayIn(timezone)
        .toEpochMilliseconds()
}.getOrDefault(now - (now % AITA_SUPPLIER_UI_DAY_MILLIS))

internal fun SupplierOrderDataModel.supplierDueAtMillis(): Long? = confirmedDeliveryTimeMillis ?: desiredDeliveryTimeMillis

internal fun supplierUiDeliveryBucketId(now: Long, dueAtMillis: Long?): String {
    val todayStart = supplierUiDayStartMillis(now)
    val safeDue = dueAtMillis ?: return "unscheduled"
    return when {
        safeDue < todayStart -> "overdue"
        safeDue < todayStart + AITA_SUPPLIER_UI_DAY_MILLIS -> "today"
        safeDue < todayStart + 2L * AITA_SUPPLIER_UI_DAY_MILLIS -> "tomorrow"
        safeDue < todayStart + 7L * AITA_SUPPLIER_UI_DAY_MILLIS -> "week"
        else -> "later"
    }
}

internal fun supplierDueFilterMatchesBucket(dueFilter: String, bucketId: String): Boolean = when (dueFilter) {
    "all" -> true
    "soon" -> bucketId == "tomorrow" || bucketId == "week"
    else -> bucketId == dueFilter
}

internal fun SupplierOrderDataModel.matchesSupplierDueFilter(dueFilter: String, now: Long): Boolean =
    supplierDueFilterMatchesBucket(dueFilter.ifBlank { "all" }, supplierUiDeliveryBucketId(now, supplierDueAtMillis()))

internal suspend fun seedSupplierOrdersInboxNavigation(
    searchQuery: String = "",
    dueFilter: String = "all",
    statusFilter: String = if (searchQuery.isBlank()) "open" else "all"
) {
    val safeSearchQuery = searchQuery.trim()
    NavigationScreenModel.Supplier.Orders.Main.setStates(
        NavigationScreenModel.KEY_STATE_SEARCH_QUERY to safeSearchQuery,
        SUPPLIER_ORDER_DUE_FILTER_STATE_KEY to dueFilter.ifBlank { "all" },
        SUPPLIER_ORDER_STATUS_FILTER_STATE_KEY to statusFilter.ifBlank {
            if (safeSearchQuery.isBlank()) "open" else "all"
        }
    )
}

internal fun AppConfiguration.supplierDeliveryBucketTitle(bucketId: String): String = when (bucketId) {
    "overdue" -> localizedStringResource(1664, "Overdue promises")
    "today" -> localizedStringResource(1665, "Due today")
    "tomorrow" -> localizedStringResource(1666, "Due tomorrow")
    "soon" -> localizedStringResource(1812, "Due soon")
    "week" -> localizedStringResource(1668, "This week")
    "later" -> localizedStringResource(1669, "Later")
    "all" -> localizedStringResource(1378, "All")
    else -> localizedStringResource(1663, "No promised date")
}

internal fun AppConfiguration.supplierDeliveryBucketTitle(bucket: SupplierDashboardDeliveryBucketDataModel): String =
    bucket.title.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { bucket.title.visibleLocalizedString("main", "") }
        .ifBlank { supplierDeliveryBucketTitle(bucket.bucketId) }

internal fun AppConfiguration.supplierDeliveryBucketSubtitle(bucket: SupplierDashboardDeliveryBucketDataModel): String {
    val dueRange = listOfNotNull(
        bucket.earliestDueAtMillis?.toStockDateInputText()?.takeIf { it.isNotBlank() },
        bucket.latestDueAtMillis?.toStockDateInputText()?.takeIf { it.isNotBlank() && it != bucket.earliestDueAtMillis?.toStockDateInputText() }
    ).joinToString(" → ").takeIf { it.isNotBlank() }
    val goodsPreview = bucket.goodsPreview.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { bucket.goodsPreview.visibleLocalizedString("main", "") }
    val pressure = listOfNotNull(
        bucket.actionRequiredOrderCount.takeIf { it > 0 }?.let { "${localizedStringResource(1371, "Needs attention")}: $it" },
        bucket.packedOrderCount.takeIf { it > 0 }?.let { "${localizedStringResource(1674, "Packed")}: $it" },
        bucket.inDeliveryOrderCount.takeIf { it > 0 }?.let { "${localizedStringResource(1675, "Driving")}: $it" },
        bucket.issueOrderCount.takeIf { it > 0 }?.let { "${localizedStringResource(1473, "Issues")}: $it" }
    ).joinToString(" • ")

    return listOfNotNull(
        "${localizedStringResource(1670, "Stores")}: ${bucket.storeCount} • ${localizedStringResource(1671, "Lines")}: ${bucket.lineCount}",
        dueRange?.let { "${localizedStringResource(1673, "Due range")}: $it" },
        goodsPreview.takeIf { it.isNotBlank() },
        pressure.takeIf { it.isNotBlank() }
    ).joinToString("\n")
}

internal fun AppConfiguration.supplierDueFilterOptionsFromDashboard(
    dashboard: SupplierModeDashboardDataModel?
): List<DropdownOption> {
    val knownBuckets = listOf("overdue", "today", "soon", "tomorrow", "week", "later", "unscheduled")
    val dashboardBuckets = dashboard?.deliveryBuckets.orEmpty().map { it.bucketId }.filter { it.isNotBlank() }
    return listOf(DropdownOption("all", localizedStringResource(1378, "All"))) +
            (dashboardBuckets + knownBuckets)
                .distinct()
                .map { bucketId -> DropdownOption(bucketId, supplierDeliveryBucketTitle(bucketId)) }
}

@Composable
internal fun AppConfiguration.SupplierDeliveryPromiseRadarCard(
    buckets: List<SupplierDashboardDeliveryBucketDataModel>,
    selectedBucketId: String,
    onBucketSelected: (String) -> Unit
) {
    if (buckets.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
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
                modifier = Modifier.size(38.dp),
                url = stateValues.drawablePathIconSupplierDispatch,
                fallbackRes = stateValues.drawableResIconSupplierDispatch.value,
                contentDescription = localizedStringResource(1660, "Delivery promise radar"),
                tintColor = stateValues.AccentColor
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = localizedStringResource(1660, "Delivery promise radar"),
                    color = stateValues.TextColor,
                    fontSize = stateValues.titleTextSize,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = localizedStringResource(1661, "Server groups open orders by promised dates, so the supplier sees what is late, what leaves today, and what can wait."),
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) { SupplierCatalogChip(text = "${localizedStringResource(1672, "Promises")}: ${buckets.sumOf { it.orderCount }}") }
            Box(modifier = Modifier.weight(1f)) { SupplierCatalogChip(text = "${localizedStringResource(1371, "Needs attention")}: ${buckets.sumOf { it.actionRequiredOrderCount }}") }
        }

        buckets.take(6).forEach { bucket ->
            val selected = selectedBucketId == bucket.bucketId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(stateValues.cornerRadius))
                    .background(if (selected) stateValues.AccentColor.copy(alpha = 0.14f) else stateValues.AccentColor.copy(alpha = 0.06f))
                    .border(
                        if (selected) stateValues.focusedBorderWidth else stateValues.unfocusedBorderWidth,
                        if (selected) stateValues.AccentColor else stateValues.AccentColor.copy(alpha = 0.28f),
                        RoundedCornerShape(stateValues.cornerRadius)
                    )
                    .aitaClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = stateValues.AccentColor)
                    ) { onBucketSelected(bucket.bucketId) }
                    .padding(stateValues.marginTextField),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
            ) {
                CpImage(
                    modifier = Modifier.size(26.dp),
                    url = stateValues.drawablePathIconSupplierDispatch,
                    fallbackRes = stateValues.drawableResIconSupplierDispatch.value,
                    contentDescription = supplierDeliveryBucketTitle(bucket),
                    tintColor = stateValues.AccentColor
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = supplierDeliveryBucketTitle(bucket),
                        color = stateValues.TextColor,
                        fontSize = stateValues.textSize,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = supplierDeliveryBucketSubtitle(bucket),
                        color = stateValues.PlaceholderTextColor,
                        fontSize = stateValues.smallTextSize,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = bucket.orderCount.toString(),
                    color = stateValues.AccentColor,
                    fontSize = stateValues.titleTextSize,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End
                )
            }
        }

        if (selectedBucketId != "all") {
            actionButton(
                modifier = Modifier.fillMaxWidth(),
                text = localizedStringResource(1676, "Clear promise filter"),
                iconPath = stateValues.drawablePathIconCancel,
                iconRes = stateValues.drawableResIconCancel.value,
                confirmationRequired = false,
                onClick = { onBucketSelected("all") }
            )
        }
    }
}

package kz.aita

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch


@Composable
internal fun AppConfiguration.SupplierCatalogChip(
    text: String,
    color: Color = stateValues.AccentColor
) {
    Text(
        text = text,
        color = color,
        fontSize = stateValues.smallTextSize,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(color.copy(alpha = 0.10f))
            .border(
                stateValues.unfocusedBorderWidth,
                color.copy(alpha = 0.55f),
                RoundedCornerShape(stateValues.cornerRadius)
            )
            .padding(horizontal = 8.dp, vertical = 5.dp)
    )
}

@Composable
internal fun AppConfiguration.SupplierCatalogWorkflowLinks() {
    val coroutineScope = rememberCoroutineScope()

    @Composable
    fun OrdersButton(modifier: Modifier) {
        actionButton(
            modifier = modifier,
            text = localizedStringResource(254, "Orders"),
            iconPath = stateValues.drawablePathIconAppModeSupplier,
            iconRes = stateValues.drawableResIconAppModeSupplier.value,
            confirmationRequired = false,
            autoLoading = false,
            onClick = {
                coroutineScope.launch {
                    Navigation.goMain(NavigationScreenModel.Supplier.Orders.Main)
                }
            }
        )
    }

    @Composable
    fun CustomersButton(modifier: Modifier) {
        actionButton(
            modifier = modifier,
            text = localizedStringResource(1453, "Partner stores"),
            iconPath = stateValues.drawablePathIconSupplierPartners,
            iconRes = stateValues.drawableResIconSupplierPartners.value,
            confirmationRequired = false,
            autoLoading = false,
            onClick = {
                coroutineScope.launch {
                    Navigation.goMain(NavigationScreenModel.Supplier.Customers.Main)
                }
            }
        )
    }

    @Composable
    fun ContractsButton(modifier: Modifier) {
        actionButton(
            modifier = modifier,
            text = localizedStringResource(1479, "Supplier contracts"),
            iconPath = stateValues.drawablePathIconSupplierContracts,
            iconRes = stateValues.drawableResIconSupplierContracts.value,
            confirmationRequired = false,
            autoLoading = false,
            onClick = {
                coroutineScope.launch {
                    Navigation.goMain(NavigationScreenModel.Supplier.Contracts.Main)
                }
            }
        )
    }

    if (stateValues.isNarrowScreen) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            OrdersButton(Modifier.fillMaxWidth())
            CustomersButton(Modifier.fillMaxWidth())
            ContractsButton(Modifier.fillMaxWidth())
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            OrdersButton(Modifier.fillMaxWidth())
            CustomersButton(Modifier.fillMaxWidth())
            ContractsButton(Modifier.fillMaxWidth())
        }
    }
}

@Composable
internal fun AppConfiguration.SupplierCatalogFilterPanel(
    searchQuery: String,
    filterId: String,
    sortId: String,
    resultCount: Int,
    totalCount: Int,
    expanded: Boolean,
    onSearchChanged: (String) -> Unit,
    onFilterChanged: (String) -> Unit,
    onSortChanged: (String) -> Unit,
    onExpandedChanged: (Boolean) -> Unit,
    onClear: () -> Unit
) {
    val quickFilters = listOf(
        SupplierOrdersQuickFilterUiModel(
            SUPPLIER_CATALOG_FILTER_ALL,
            localizedStringResource(2316, "All products")
        ),
        SupplierOrdersQuickFilterUiModel(
            SUPPLIER_CATALOG_FILTER_OPEN,
            localizedStringResource(2313, "Open demand")
        ),
        SupplierOrdersQuickFilterUiModel(
            SUPPLIER_CATALOG_FILTER_MISSING_PRICE,
            localizedStringResource(2317, "Needs a price")
        ),
        SupplierOrdersQuickFilterUiModel(
            SUPPLIER_CATALOG_FILTER_REPLY,
            localizedStringResource(2318, "Needs a reply")
        ),
        SupplierOrdersQuickFilterUiModel(
            SUPPLIER_CATALOG_FILTER_PRICE_BOOK,
            localizedStringResource(2319, "Price book")
        )
    )
    val hasNonDefaultFilter = searchQuery.isNotBlank() ||
            filterId != SUPPLIER_CATALOG_FILTER_ALL ||
            sortId != SUPPLIER_CATALOG_SORT_ACTION

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(
                stateValues.unfocusedBorderWidth,
                stateValues.PlaceholderTextColor,
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
            SimpleTextInput(
                modifier = Modifier.weight(1f),
                value = searchQuery,
                placeholder = stateValues.stringSearchByAnyData,
                leadingIconPath = stateValues.drawablePathIconSearch,
                onValueChange = onSearchChanged
            )
            actionButton(
                modifier = Modifier.size(40.dp),
                text = "",
                iconPath = stateValues.drawablePathIconSettings,
                iconRes = stateValues.drawableResIconSettings.value,
                iconContentDescription = localizedStringResource(1274, "Filters"),
                confirmationRequired = false,
                autoLoading = false,
                enabledColor = if (expanded || sortId != SUPPLIER_CATALOG_SORT_ACTION) {
                    stateValues.AccentColor
                } else {
                    stateValues.DisabledColor
                },
                onClick = { onExpandedChanged(!expanded) }
            )
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 1.dp)
        ) {
            items(quickFilters, key = { it.id }) { filter ->
                SupplierOrdersQuickFilterChip(
                    filter = filter,
                    selected = filterId == filter.id,
                    onClick = { onFilterChanged(filter.id) }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            Text(
                text = "${localizedStringResource(2343, "Results")}: $resultCount / $totalCount",
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize,
                modifier = Modifier.weight(1f)
            )
            if (hasNonDefaultFilter) {
                actionButton(
                    modifier = Modifier.size(40.dp),
                    text = "",
                    iconPath = stateValues.drawablePathIconCancel,
                    iconRes = stateValues.drawableResIconCancel.value,
                    iconContentDescription = localizedStringResource(2344, "Clear filters"),
                    confirmationRequired = false,
                    autoLoading = false,
                    onClick = onClear
                )
            }
        }

        AnimatedVisibility(visible = expanded) {
            SimpleDropdownField(
                title = localizedStringResource(2320, "Sort products"),
                selectedId = sortId,
                options = listOf(
                    DropdownOption(
                        SUPPLIER_CATALOG_SORT_ACTION,
                        localizedStringResource(2321, "Action first")
                    ),
                    DropdownOption(
                        SUPPLIER_CATALOG_SORT_RECENT,
                        localizedStringResource(2322, "Most recent")
                    ),
                    DropdownOption(
                        SUPPLIER_CATALOG_SORT_NAME,
                        localizedStringResource(2323, "Name A–Z")
                    ),
                    DropdownOption(
                        SUPPLIER_CATALOG_SORT_STORES,
                        localizedStringResource(2335, "Stores")
                    )
                ),
                placeholder = localizedStringResource(2321, "Action first"),
                onSelected = { onSortChanged(it.ifBlank { SUPPLIER_CATALOG_SORT_ACTION }) }
            )
        }
    }
}

@Composable
internal fun AppConfiguration.SupplierCatalogCompactCard(
    item: SupplierCatalogItemUiModel,
    onOpen: () -> Unit
) {
    val accent = when {
        item.needsReply -> stateValues.BorderlineBadColor
        item.hasMissingOffer -> stateValues.AccentColor
        item.hasSavedOffer -> stateValues.OkayColor
        else -> stateValues.IconTintColor
    }
    val statusText = when {
        item.needsReply -> localizedStringResource(2318, "Needs a reply")
        item.hasMissingOffer -> localizedStringResource(2337, "Price missing")
        item.orderCount == 0 && item.hasSavedOffer -> localizedStringResource(1682, "Price book ready")
        item.hasSavedOffer -> localizedStringResource(2338, "Offer ready")
        else -> supplierOrderStatusTitle(item.latestStatus)
    }
    val subtitleText = item.barcodeText.ifBlank {
        item.storeTitles.take(2).joinToString(", ").ifBlank {
            if (item.lastActivityMillis > 0L) {
                "${localizedStringResource(2347, "Last activity")}: ${receiptUiDateTime(item.lastActivityMillis)}"
            } else {
                localizedStringResource(2319, "Price book")
            }
        }
    }
    val priceText = item.savedPriceText
        .ifBlank { item.expectedPriceText }
        .ifBlank { localizedStringResource(2317, "Needs a price") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(
                if (item.needsReply || item.hasMissingOffer) {
                    stateValues.focusedBorderWidth
                } else {
                    stateValues.unfocusedBorderWidth
                },
                if (item.needsReply || item.hasMissingOffer) {
                    accent
                } else {
                    stateValues.PlaceholderTextColor
                },
                RoundedCornerShape(stateValues.cornerRadius)
            )
            .aitaClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = accent),
                onClick = onOpen
            )
            .padding(stateValues.marginTextFieldGroup),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(stateValues.cornerRadius))
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                CpImage(
                    modifier = Modifier.size(25.dp),
                    url = stateValues.drawablePathIconSupplierCatalog,
                    fallbackRes = stateValues.drawableResIconSupplierCatalog.value,
                    contentDescription = item.title,
                    tintColor = accent
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = stateValues.TextColor,
                    fontSize = stateValues.accentTextSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitleText,
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (stateValues.isNarrowScreen) {
                Text(
                    text = priceText,
                    color = accent,
                    fontSize = stateValues.textSize,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = priceText,
                        color = accent,
                        fontSize = stateValues.textSize,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = statusText,
                        color = accent,
                        fontSize = stateValues.smallTextSize,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        val chips = buildList<Pair<String, Color>> {
            if (stateValues.isNarrowScreen) add(statusText to accent)
            if (item.openOrderCount > 0) {
                add(
                    "${localizedStringResource(2336, "Open requests")}: ${item.openOrderCount}" to
                            stateValues.AccentColor
                )
            }
            if (item.storeCount > 0) {
                add(
                    "${localizedStringResource(2335, "Stores")}: ${item.storeCount}" to
                            stateValues.AccentColor
                )
            }
            if (item.savedOfferCount > 0) {
                add(
                    "${localizedStringResource(2315, "Saved offers")}: ${item.savedOfferCount}" to
                            stateValues.AccentColor
                )
            }
            item.totalQuantityText
                .takeIf { item.openOrderCount > 0 && it.isNotBlank() }
                ?.let { quantity ->
                    add(quantity to stateValues.AccentColor)
                }
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 1.dp)
        ) {
            items(chips) { (text, color) ->
                SupplierCatalogChip(text = text, color = color)
            }
        }
    }
}

@Composable
internal fun AppConfiguration.SupplierCatalogProductHeaderCard(
    item: SupplierCatalogItemUiModel
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(
                stateValues.unfocusedBorderWidth,
                stateValues.PlaceholderTextColor,
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
                    modifier = Modifier.size(29.dp),
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
                    fontSize = stateValues.titleTextSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.barcodeText.isNotBlank()) {
                    Text(
                        text = "${stateValues.stringBarcode}: ${item.barcodeText}",
                        color = stateValues.PlaceholderTextColor,
                        fontSize = stateValues.smallTextSize,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        StockCardInfoLine(
            localizedStringResource(1424, "Total requested"),
            item.totalQuantityText,
            stateValues.TextColor
        )
        StockCardInfoLine(
            localizedStringResource(2335, "Stores"),
            item.storeCount.toString(),
            stateValues.TextColor
        )
        StockCardInfoLine(
            localizedStringResource(2336, "Open requests"),
            item.openOrderCount.toString(),
            stateValues.TextColor
        )
        if (item.expectedPriceText.isNotBlank()) {
            StockCardInfoLine(
                localizedStringResource(2346, "Expected by store"),
                item.expectedPriceText,
                stateValues.TextColor
            )
        }
        if (item.savedOfferCount > 0) {
            StockCardInfoLine(
                localizedStringResource(2315, "Saved offers"),
                item.savedOfferCount.toString(),
                stateValues.AccentColor
            )
        }
    }
}

private fun String.supplierCatalogPriceSignature(): String =
    filterSupplierDeskPriceInput()
        .toDoubleOrNull()
        ?.takeIf { it.isFinite() }
        ?.roundMoney()
        ?.toStockMoneyText()
        ?: trim()

private fun String.supplierCatalogQuantitySignature(
    quantityTemplate: QuantityDataModel
): String {
    if (isBlank()) return ""
    return parseStockQuantityInputText(this, quantityTemplate)
        ?.takeIf { it.isFinite() }
        ?.let { stockQuantityInputTextFromAmount(it, quantityTemplate) }
        ?: trim()
}

private fun SupplierGoodsPriceDataModel?.supplierCatalogEditorSignature(): String? {
    this ?: return null
    return listOf(
        supplyPrice.price.supplierCatalogPriceSignature(),
        supplyPrice.currency.trim().uppercase(),
        minOrderQuantity?.let {
            stockQuantityInputTextFromAmount(it.total, it)
        }.orEmpty(),
        packageQuantity?.let {
            stockQuantityInputTextFromAmount(it.total, it)
        }.orEmpty(),
        supplierGoodsName.orEmpty().trim(),
        supplierBarcode.orEmpty().trim()
    ).joinToString("\u001F")
}

@Composable
private fun AppConfiguration.SupplierCatalogQuantityInput(
    modifier: Modifier,
    value: String,
    placeholder: String,
    quantityAllowsFraction: Boolean,
    onValueChange: (String) -> Unit
) {
    SimpleTextInput(
        modifier = modifier,
        value = value,
        placeholder = placeholder,
        keyboardType = if (quantityAllowsFraction) KeyboardType.Decimal else KeyboardType.Number,
        leadingIconPath = stateValues.drawablePathIconStock,
        onTransformValue = { raw ->
            sanitizeStockQuantityInput(raw, quantityAllowsFraction)
        },
        onValueChange = { next ->
            if (next.isStockQuantityInputText(quantityAllowsFraction)) {
                onValueChange(next)
            }
        }
    )
}

@Composable
internal fun AppConfiguration.SupplierCatalogOfferEditor(
    offer: SupplierCatalogOfferUiModel,
    expanded: Boolean,
    onExpandedChanged: (Boolean) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val existingPrice = offer.existingPrice
    val latestMinOrderTemplate = existingPrice?.minOrderQuantity ?: offer.quantityTemplate
    val latestPackageTemplate = existingPrice?.packageQuantity ?: offer.quantityTemplate

    val draftHost = NavigationScreenModel.Supplier.Catalog.Main
    val accountId = stateValues.userAccount?.id.orEmpty()
    val sessionGeneration = currentAuthenticatedSessionGeneration()
    val draftKey = supplierCatalogOfferDraftKey(accountId, offer.supplierId, offer.storeId, offer.goodsItemId)
    val latestFields = SupplierCatalogOfferFields(
        price = (existingPrice?.supplyPrice?.takeIf { offer.hasUsablePrice } ?: offer.expectedPrice)
            .supplierDeskPriceInputText().filterSupplierDeskPriceInput(),
        minimum = existingPrice?.minOrderQuantity?.let { stockQuantityInputTextFromAmount(it.total, it) }.orEmpty(),
        packageSize = existingPrice?.packageQuantity?.let { stockQuantityInputTextFromAmount(it.total, it) }.orEmpty(),
        name = offer.supplierGoodsName,
        barcode = offer.supplierBarcode,
        currency = existingPrice?.supplyPrice?.currency?.takeIf { it.isNotBlank() }
            ?: offer.expectedPrice?.currency?.takeIf { it.isNotBlank() } ?: "KZT",
        minimumTemplate = latestMinOrderTemplate,
        packageTemplate = latestPackageTemplate
    )
    var draft by remember(draftKey, sessionGeneration) {
        mutableStateOf(decodeSupplierCatalogOfferDraft(draftHost.state.value[draftKey], draftKey)
            ?: SupplierCatalogOfferDraft(draftKey, latestFields))
    }
    fun keepDraft(next: SupplierCatalogOfferDraft) {
        if (userAccountState.payloadValue?.id != accountId ||
            currentAuthenticatedSessionGeneration() != sessionGeneration) return
        draft = next
        if (next.edited) draftHost.setStateNow(draftKey to next.encoded())
        else coroutineScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { draftHost.removeState(draftKey) }
    }
    LaunchedEffect(latestFields, draftKey) {
        val next = draft.refreshed(latestFields)
        if (next != draft) keepDraft(next)
    }
    val restoredStateRevision by AppStateWorkspace.restoreRevision.collectAsState()
    LaunchedEffect(restoredStateRevision, draftKey) {
        if (!draft.edited) {
            decodeSupplierCatalogOfferDraft(draftHost.state.value[draftKey], draftKey)?.let { draft = it }
        }
    }
    val priceText = draft.fields.price
    val minOrderText = draft.fields.minimum
    val packageText = draft.fields.packageSize
    val supplierGoodsName = draft.fields.name
    val supplierBarcode = draft.fields.barcode
    val remoteOfferChanged = draft.edited && draft.baseline != latestFields
    val minOrderTemplate = draft.fields.minimumTemplate ?: latestMinOrderTemplate
    val packageTemplate = draft.fields.packageTemplate ?: latestPackageTemplate
    val minOrderAllowsFraction = minOrderTemplate.allowsFractionalStockQuantityInput()
    val packageAllowsFraction = packageTemplate.allowsFractionalStockQuantityInput()
    var saving by remember(draftKey, sessionGeneration) { mutableStateOf(false) }
    var resultMessage by remember(draftKey, sessionGeneration) { mutableStateOf("") }
    var resultPositive by remember(draftKey, sessionGeneration) { mutableStateOf(false) }

    val parsedPrice = priceText.filterSupplierDeskPriceInput().toDoubleOrNull()
    val parsedMinOrder = if (minOrderText.isBlank()) {
        null
    } else {
        parseStockQuantityInputText(minOrderText, minOrderTemplate)
    }
    val parsedPackage = if (packageText.isBlank()) {
        null
    } else {
        parseStockQuantityInputText(packageText, packageTemplate)
    }
    val optionalQuantitiesValid =
        (minOrderText.isBlank() || parsedMinOrder?.let { it.isFinite() && it > 0.0 } == true) &&
                (packageText.isBlank() || parsedPackage?.let { it.isFinite() && it > 0.0 } == true)
    val currency = draft.fields.currency
    val currentSignature = listOf(
        priceText.supplierCatalogPriceSignature(),
        currency.trim().uppercase(),
        minOrderText.supplierCatalogQuantitySignature(minOrderTemplate),
        packageText.supplierCatalogQuantitySignature(packageTemplate),
        supplierGoodsName.trim(),
        supplierBarcode.trim()
    ).joinToString("\u001F")
    val savedSignature = existingPrice.supplierCatalogEditorSignature()
    val hasChanges = savedSignature == null || savedSignature != currentSignature
    val saveEnabled = offer.canEdit &&
            !saving &&
            parsedPrice?.let { it.isFinite() && it > 0.0 } == true &&
            optionalQuantitiesValid &&
            hasChanges

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.AccentColor.copy(alpha = 0.055f))
            .border(
                if (offer.openOrderCount > 0 && !offer.hasUsablePrice) {
                    stateValues.focusedBorderWidth
                } else {
                    stateValues.unfocusedBorderWidth
                },
                if (offer.openOrderCount > 0 && !offer.hasUsablePrice) {
                    stateValues.AccentColor
                } else {
                    stateValues.AccentColor.copy(alpha = 0.34f)
                },
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
                    .size(38.dp)
                    .clip(RoundedCornerShape(stateValues.cornerRadius))
                    .background(stateValues.AccentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                CpImage(
                    modifier = Modifier.size(23.dp),
                    url = stateValues.drawablePathIconStores,
                    fallbackRes = stateValues.drawableResIconStores.value,
                    contentDescription = offer.storeTitle,
                    tintColor = stateValues.AccentColor
                )
            }
            val offerStateText = if (draft.edited) {
                supplierOfferDraftText("draft")
            } else if (offer.hasUsablePrice) {
                localizedStringResource(2338, "Offer ready")
            } else {
                localizedStringResource(2337, "Price missing")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = offer.storeTitle,
                    color = stateValues.TextColor,
                    fontSize = stateValues.accentTextSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOfNotNull(
                        offer.supplierTitle.takeIf { it.isNotBlank() },
                        offer.storePublicId.takeIf { it.isNotBlank() },
                        offer.openOrderCount.takeIf { it > 0 }?.let {
                            "${localizedStringResource(2336, "Open requests")}: $it"
                        },
                        offerStateText.takeIf { stateValues.isNarrowScreen }
                    ).joinToString(" • ").ifBlank {
                        localizedStringResource(2325, "Store offers")
                    },
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!stateValues.isNarrowScreen) {
                SupplierCatalogChip(
                    text = offerStateText,
                    color = if (offer.hasUsablePrice) stateValues.OkayColor else stateValues.AccentColor
                )
            }
            actionButton(
                modifier = Modifier.size(38.dp),
                text = "",
                iconPath = stateValues.drawablePathIconSupplierPartners,
                iconRes = stateValues.drawableResIconSupplierPartners.value,
                iconContentDescription = localizedStringResource(2375, "Partner overview"),
                confirmationRequired = false,
                autoLoading = false,
                enabled = !saving,
                onClick = {
                    coroutineScope.launch {
                        seedSupplierCustomersNavigation(
                            searchQuery = offer.storeId
                                .trim()
                                .ifBlank { offer.storePublicId.trim() }
                                .ifBlank { offer.storeTitle }
                        )
                        Navigation.goMain(NavigationScreenModel.Supplier.Customers.Main)
                    }
                }
            )
            actionButton(
                modifier = Modifier.size(38.dp),
                text = "",
                iconPath = if (expanded) {
                    stateValues.drawablePathIconExpandLess
                } else {
                    stateValues.drawablePathIconExpandMore
                },
                iconRes = if (expanded) {
                    stateValues.drawableResIconExpandLess.value
                } else {
                    stateValues.drawableResIconExpandMore.value
                },
                iconContentDescription = if (expanded) {
                    localizedStringResource(2351, "Collapse")
                } else {
                    localizedStringResource(82, "Edit")
                },
                confirmationRequired = false,
                autoLoading = false,
                enabled = !saving,
                onClick = { onExpandedChanged(!expanded) }
            )
        }

        offer.expectedPrice.supplierDeskMoneyText()
            .takeIf { it.isNotBlank() }
            ?.let { expected ->
                StockCardInfoLine(
                    localizedStringResource(2346, "Expected by store"),
                    expected,
                    stateValues.TextColor
                )
            }

        if (!expanded) {
            existingPrice?.let { saved ->
                if (offer.hasUsablePrice) {
                    StockCardInfoLine(
                        stateValues.stringSupplyPrice,
                        saved.supplyPrice.supplierDeskMoneyText(),
                        stateValues.AccentColor
                    )
                }
                saved.minOrderQuantity?.let { minimum ->
                    StockCardInfoLine(
                        localizedStringResource(337, "Min order"),
                        minimum.quantityText(stateValues.appLanguage),
                        stateValues.TextColor
                    )
                }
                saved.packageQuantity?.let { packageQuantity ->
                    StockCardInfoLine(
                        localizedStringResource(338, "Package qty"),
                        packageQuantity.quantityText(stateValues.appLanguage),
                        stateValues.TextColor
                    )
                }
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
            ) {
                if (!offer.canEdit) {
                    MessageText(
                        modifier = Modifier.fillMaxWidth(),
                        text = localizedStringResource(
                            2332,
                            "This product has no valid store link yet"
                        ),
                        subText = localizedStringResource(
                            2326,
                            "Set a separate price and order terms for each store."
                        ),
                        subTextSize = stateValues.smallTextSize
                    )
                } else {
                    if (remoteOfferChanged) {
                        Text(
                            text = supplierOfferDraftText("changed"),
                            color = stateValues.AccentColor,
                            fontSize = stateValues.smallTextSize,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (draft.edited) {
                        actionButton(
                            modifier = Modifier.fillMaxWidth(),
                            text = supplierOfferDraftText("reset"),
                            iconPath = stateValues.drawablePathIconRefresh,
                            iconRes = stateValues.drawableResIconRefresh.value,
                            confirmationRequired = true,
                            autoLoading = false,
                            enabled = !saving,
                            onClick = {
                                keepDraft(SupplierCatalogOfferDraft(draftKey, latestFields))
                                resultMessage = ""
                            }
                        )
                    }
                    SimpleTextInput(
                        modifier = Modifier.fillMaxWidth(),
                        value = priceText,
                        placeholder = "${stateValues.stringSupplyPrice} • $currency",
                        keyboardType = KeyboardType.Decimal,
                        leadingIconPath = stateValues.drawablePathIconFinances,
                        onTransformValue = { it.filterSupplierDeskPriceInput() },
                        onValueChange = {
                            keepDraft(draft.copy(fields = draft.fields.copy(price = it.filterSupplierDeskPriceInput().take(32))))
                            resultMessage = ""
                        }
                    )

                    if (stateValues.isNarrowScreen) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                        ) {
                            SupplierCatalogQuantityInput(
                                modifier = Modifier.fillMaxWidth(),
                                value = minOrderText,
                                placeholder = localizedStringResource(337, "Min order"),
                                quantityAllowsFraction = minOrderAllowsFraction,
                                onValueChange = {
                                    keepDraft(draft.copy(fields = draft.fields.copy(minimum = it.take(32))))
                                    resultMessage = ""
                                }
                            )
                            SupplierCatalogQuantityInput(
                                modifier = Modifier.fillMaxWidth(),
                                value = packageText,
                                placeholder = localizedStringResource(338, "Package qty"),
                                quantityAllowsFraction = packageAllowsFraction,
                                onValueChange = {
                                    keepDraft(draft.copy(fields = draft.fields.copy(packageSize = it.take(32))))
                                    resultMessage = ""
                                }
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                        ) {
                            SupplierCatalogQuantityInput(
                                modifier = Modifier.weight(1f),
                                value = minOrderText,
                                placeholder = localizedStringResource(337, "Min order"),
                                quantityAllowsFraction = minOrderAllowsFraction,
                                onValueChange = {
                                    keepDraft(draft.copy(fields = draft.fields.copy(minimum = it.take(32))))
                                    resultMessage = ""
                                }
                            )
                            SupplierCatalogQuantityInput(
                                modifier = Modifier.weight(1f),
                                value = packageText,
                                placeholder = localizedStringResource(338, "Package qty"),
                                quantityAllowsFraction = packageAllowsFraction,
                                onValueChange = {
                                    keepDraft(draft.copy(fields = draft.fields.copy(packageSize = it.take(32))))
                                    resultMessage = ""
                                }
                            )
                        }
                    }

                    SimpleTextInput(
                        modifier = Modifier.fillMaxWidth(),
                        value = supplierGoodsName,
                        placeholder = localizedStringResource(2327, "Your product name"),
                        leadingIconPath = stateValues.drawablePathIconSupplierCatalog,
                        onValueChange = {
                            keepDraft(draft.copy(fields = draft.fields.copy(name = it.take(500))))
                            resultMessage = ""
                        }
                    )
                    SimpleTextInput(
                        modifier = Modifier.fillMaxWidth(),
                        value = supplierBarcode,
                        placeholder = localizedStringResource(2328, "Your barcode"),
                        keyboardType = KeyboardType.Text,
                        leadingIconPath = stateValues.drawablePathIconStock,
                        onValueChange = {
                            keepDraft(draft.copy(fields = draft.fields.copy(barcode = it.trimStart().take(128))))
                            resultMessage = ""
                        }
                    )

                    if (resultMessage.isNotBlank()) {
                        Text(
                            text = resultMessage,
                            color = if (resultPositive) {
                                stateValues.OkayColor
                            } else {
                                stateValues.ErrorColor
                            },
                            fontSize = stateValues.smallTextSize,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    actionButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = localizedStringResource(2352, "Save offer"),
                        enabled = saveEnabled,
                        loading = saving,
                        autoLoading = false,
                        iconPath = stateValues.drawablePathIconCheck,
                        iconRes = stateValues.drawableResIconCheck.value,
                        confirmationRequired = false,
                        onDisabledClick = disabledClick@{
                            if (saving) return@disabledClick
                            val message = when {
                                !offer.canEdit -> localizedStringResource(
                                    2332,
                                    "This product has no valid store link yet"
                                )

                                parsedPrice?.let { it.isFinite() && it > 0.0 } != true ->
                                    localizedStringResource(
                                        1709,
                                        "Enter supplier price first"
                                    )

                                !optionalQuantitiesValid -> localizedStringResource(
                                    2350,
                                    "Minimum order and package size must be positive"
                                )

                                !hasChanges -> localizedStringResource(2329, "No changes to save")
                                else -> localizedStringResource(2331, "Could not save this offer")
                            }
                            postInAppNotification(
                                message,
                                NotificationType.Neutral,
                                transient = true
                            )
                        },
                        onClick = saveOffer@{
                            if (saving) return@saveOffer
                            val submittedFields = draft.fields
                            val cleanPrice = submittedFields.price.filterSupplierDeskPriceInput().toDoubleOrNull()
                                ?.takeIf { it.isFinite() && it > 0.0 } ?: return@saveOffer
                            val submittedMinTemplate = submittedFields.minimumTemplate ?: offer.quantityTemplate
                            val submittedPackageTemplate = submittedFields.packageTemplate ?: offer.quantityTemplate
                            val submittedMinimum = submittedFields.minimum.takeIf { it.isNotBlank() }
                                ?.let { parseStockQuantityInputText(it, submittedMinTemplate) }
                            val submittedPackage = submittedFields.packageSize.takeIf { it.isNotBlank() }
                                ?.let { parseStockQuantityInputText(it, submittedPackageTemplate) }
                            if ((submittedFields.minimum.isNotBlank() && submittedMinimum?.let { it.isFinite() && it > 0.0 } != true) ||
                                (submittedFields.packageSize.isNotBlank() && submittedPackage?.let { it.isFinite() && it > 0.0 } != true)) return@saveOffer
                            saving = true
                            resultMessage = ""
                            resultPositive = false

                            upsertSupplierGoodsPrice(
                                SupplierGoodsPriceDataModel(
                                    id = existingPrice?.id.orEmpty(),
                                    storeId = offer.storeId,
                                    supplierId = offer.supplierId,
                                    goodsItemId = offer.goodsItemId,
                                    supplyPrice = PriceDataModel(
                                        price = cleanPrice.roundMoney().toStockMoneyText(),
                                        currency = submittedFields.currency,
                                        supplierId = offer.supplierId
                                    ),
                                    minOrderQuantity = submittedMinimum
                                        ?.takeIf { it > 0.0 }
                                        ?.let {
                                            submittedMinTemplate
                                                .withStockQuantityInputTotalValue(it)
                                        },
                                    packageQuantity = submittedPackage
                                        ?.takeIf { it > 0.0 }
                                        ?.let {
                                            submittedPackageTemplate
                                                .withStockQuantityInputTotalValue(it)
                                        },
                                    supplierBarcode = submittedFields.barcode
                                        .trim()
                                        .takeIf { it.isNotBlank() },
                                    supplierGoodsName = submittedFields.name
                                        .trim()
                                        .takeIf { it.isNotBlank() }
                                )
                            ) { result ->
                                coroutineScope.launch {
                                    if (userAccountState.payloadValue?.id != accountId ||
                                        currentAuthenticatedSessionGeneration() != sessionGeneration) return@launch
                                    saving = false
                                    when (result) {
                                        is DataState.Success -> {
                                            val saved = result.payload
                                            val savedFields = SupplierCatalogOfferFields(
                                                price = saved.supplyPrice.supplierDeskPriceInputText(),
                                                minimum = saved.minOrderQuantity?.let { stockQuantityInputTextFromAmount(it.total, it) }.orEmpty(),
                                                packageSize = saved.packageQuantity?.let { stockQuantityInputTextFromAmount(it.total, it) }.orEmpty(),
                                                name = saved.supplierGoodsName.orEmpty(),
                                                barcode = saved.supplierBarcode.orEmpty(),
                                                currency = saved.supplyPrice.currency,
                                                minimumTemplate = saved.minOrderQuantity ?: offer.quantityTemplate,
                                                packageTemplate = saved.packageQuantity ?: offer.quantityTemplate
                                            )
                                            val acknowledged = draft.acknowledged(submittedFields, savedFields)
                                            keepDraft(acknowledged)
                                            resultPositive = true
                                            resultMessage = if (acknowledged.edited) supplierOfferDraftText("kept")
                                                else localizedStringResource(2330, "Offer saved for this store")
                                        }

                                        is DataState.Empty -> {
                                            resultPositive = false
                                            resultMessage = result.message
                                                .orEmpty()
                                                .visibleLocalizedString(
                                                    stateValues.appLanguage,
                                                    localizedStringResource(
                                                        2331,
                                                        "Could not save this offer"
                                                    )
                                                )
                                        }
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun AppConfiguration.SupplierCatalogProductDetail(
    item: SupplierCatalogItemUiModel
) {
    val coroutineScope = rememberCoroutineScope()
    val offerKeys = item.offers.map { it.offerKey }
    var expandedOfferKey by remember(item.catalogKey, offerKeys) {
        mutableStateOf(
            item.offers
                .firstOrNull { it.openOrderCount > 0 && !it.hasUsablePrice }
                ?.offerKey
                ?: item.offers.singleOrNull()?.offerKey
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
    ) {
        Text(
            text = item.title,
            color = stateValues.TextColor,
            fontSize = stateValues.titleTextSize,
            fontWeight = FontWeight.Bold
        )
        val section = sectionTabsWidget(
            stateKey = "supplier-catalog-product:${item.catalogKey}",
            tabs = listOf(
                TabContent("offers", localizedStringResource(2325, "Store offers")),
                TabContent("overview", supplierOfferDraftText("overview"))
            )
        )
        if (section == "overview") {
            SupplierCatalogProductHeaderCard(item = item)
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = localizedStringResource(2325, "Store offers"),
                        color = stateValues.TextColor,
                        fontSize = stateValues.titleTextSize,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = localizedStringResource(
                            2326,
                            "Set a separate price and order terms for each store."
                        ),
                        color = stateValues.PlaceholderTextColor,
                        fontSize = stateValues.smallTextSize
                    )
                }
                Text(
                    text = item.offers.size.toString(),
                    color = stateValues.AccentColor,
                    fontSize = stateValues.titleTextSize,
                    fontWeight = FontWeight.Bold
                )
            }

            if (item.offers.isEmpty()) {
                MessageText(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(
                        2332,
                        "This product has no valid store link yet"
                    )
                )
            } else {
                item.offers.forEach { offer ->
                    key(offer.offerKey) {
                        SupplierCatalogOfferEditor(
                            offer = offer,
                            expanded = expandedOfferKey == offer.offerKey,
                            onExpandedChanged = { shouldExpand ->
                                expandedOfferKey = offer.offerKey.takeIf { shouldExpand }
                            }
                        )
                    }
                }
            }

        }

        if (stateValues.isNarrowScreen) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
            ) {
                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(2333, "Open store orders"),
                    iconPath = stateValues.drawablePathIconAppModeSupplier,
                    iconRes = stateValues.drawableResIconAppModeSupplier.value,
                    confirmationRequired = false,
                    autoLoading = false,
                    onClick = {
                        coroutineScope.launch {
                            seedSupplierOrdersInboxNavigation(
                                searchQuery = item.orderSearchQuery,
                                statusFilter = "all"
                            )
                            Navigation.goMain(NavigationScreenModel.Supplier.Orders.Main)
                        }
                    }
                )
                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(2334, "Copy product brief"),
                    iconPath = stateValues.drawablePathIconClipboard,
                    iconRes = stateValues.drawableResIconClipboard.value,
                    confirmationRequired = false,
                    autoLoading = false,
                    onClick = { copyTextToClipboard(item.offerNote) }
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
            ) {
                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(2333, "Open store orders"),
                    iconPath = stateValues.drawablePathIconAppModeSupplier,
                    iconRes = stateValues.drawableResIconAppModeSupplier.value,
                    confirmationRequired = false,
                    autoLoading = false,
                    onClick = {
                        coroutineScope.launch {
                            seedSupplierOrdersInboxNavigation(
                                searchQuery = item.orderSearchQuery,
                                statusFilter = "all"
                            )
                            Navigation.goMain(NavigationScreenModel.Supplier.Orders.Main)
                        }
                    }
                )
                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(2334, "Copy product brief"),
                    iconPath = stateValues.drawablePathIconClipboard,
                    iconRes = stateValues.drawableResIconClipboard.value,
                    confirmationRequired = false,
                    autoLoading = false,
                    onClick = { copyTextToClipboard(item.offerNote) }
                )
            }
        }
    }
}

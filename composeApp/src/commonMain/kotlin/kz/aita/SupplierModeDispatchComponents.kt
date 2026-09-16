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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource

@Composable
internal fun AppConfiguration.SupplierDispatchFilterPanel(
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
            SUPPLIER_DISPATCH_FILTER_ALL,
            localizedStringResource(2473, "Store runs")
        ),
        SupplierOrdersQuickFilterUiModel(
            SUPPLIER_DISPATCH_FILTER_OVERDUE_PROMISE,
            localizedStringResource(1664, "Overdue promises")
        ),
        SupplierOrdersQuickFilterUiModel(
            SUPPLIER_DISPATCH_FILTER_DUE_SOON_PROMISE,
            localizedStringResource(1812, "Due soon")
        ),
        SupplierOrdersQuickFilterUiModel(
            SUPPLIER_DISPATCH_FILTER_READY_TO_PACK,
            localizedStringResource(1689, "Ready to pack")
        ),
        SupplierOrdersQuickFilterUiModel(
            SUPPLIER_DISPATCH_FILTER_READY_TO_DISPATCH,
            localizedStringResource(2442, "Ready to dispatch")
        ),
        SupplierOrdersQuickFilterUiModel(
            SUPPLIER_DISPATCH_FILTER_IN_DELIVERY,
            localizedStringResource(1560, "In delivery")
        ),
        SupplierOrdersQuickFilterUiModel(
            SUPPLIER_DISPATCH_FILTER_ATTENTION,
            localizedStringResource(1371, "Needs attention")
        ),
        SupplierOrdersQuickFilterUiModel(
            SUPPLIER_DISPATCH_FILTER_CONTRACTS,
            localizedStringResource(2467, "Contract blocked")
        )
    )
    val normalizedFilter = normalizedSupplierDispatchFilter(filterId)
    val normalizedSort = normalizedSupplierDispatchSort(sortId)
    val hasNonDefaultFilter = searchQuery.isNotBlank() ||
            normalizedFilter != SUPPLIER_DISPATCH_FILTER_ALL ||
            normalizedSort != SUPPLIER_DISPATCH_SORT_ACTION

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
                enabledColor = if (expanded || normalizedSort != SUPPLIER_DISPATCH_SORT_ACTION) {
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
                    selected = normalizedFilter == filter.id,
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
                title = localizedStringResource(2450, "Sort delivery runs"),
                selectedId = normalizedSort,
                options = listOf(
                    DropdownOption(
                        SUPPLIER_DISPATCH_SORT_ACTION,
                        localizedStringResource(2451, "Action first")
                    ),
                    DropdownOption(
                        SUPPLIER_DISPATCH_SORT_DUE,
                        localizedStringResource(2452, "Earliest delivery")
                    ),
                    DropdownOption(
                        SUPPLIER_DISPATCH_SORT_STORE,
                        localizedStringResource(2453, "Store A–Z")
                    ),
                    DropdownOption(
                        SUPPLIER_DISPATCH_SORT_RECENT,
                        localizedStringResource(2454, "Most recent")
                    )
                ),
                placeholder = localizedStringResource(2451, "Action first"),
                onSelected = { onSortChanged(normalizedSupplierDispatchSort(it)) }
            )
        }
    }
}

@Composable
internal fun AppConfiguration.SupplierDispatchWorkflowLinks() {
    val coroutineScope = rememberCoroutineScope()

    @Composable
    fun WorkflowButton(
        modifier: Modifier,
        text: String,
        iconPath: String,
        iconRes: DrawableResource?,
        onClick: suspend () -> Unit
    ) {
        actionButton(
            modifier = modifier,
            text = text,
            iconPath = iconPath,
            iconRes = iconRes,
            confirmationRequired = false,
            autoLoading = false,
            onClick = { coroutineScope.launch { onClick() } }
        )
    }

    if (stateValues.isNarrowScreen) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            WorkflowButton(
                modifier = Modifier.fillMaxWidth(),
                text = localizedStringResource(254, "Orders"),
                iconPath = stateValues.drawablePathIconAppModeSupplier,
                iconRes = stateValues.drawableResIconAppModeSupplier.value
            ) { Navigation.goMain(NavigationScreenModel.Supplier.Orders.Main) }
            WorkflowButton(
                modifier = Modifier.fillMaxWidth(),
                text = localizedStringResource(1453, "Partner stores"),
                iconPath = stateValues.drawablePathIconSupplierPartners,
                iconRes = stateValues.drawableResIconSupplierPartners.value
            ) { Navigation.goMain(NavigationScreenModel.Supplier.Customers.Main) }
            WorkflowButton(
                modifier = Modifier.fillMaxWidth(),
                text = localizedStringResource(2398, "All agreements"),
                iconPath = stateValues.drawablePathIconSupplierContracts,
                iconRes = stateValues.drawableResIconSupplierContracts.value
            ) { Navigation.goMain(NavigationScreenModel.Supplier.Contracts.Main) }
            WorkflowButton(
                modifier = Modifier.fillMaxWidth(),
                text = localizedStringResource(1338, "Catalog"),
                iconPath = stateValues.drawablePathIconSupplierCatalog,
                iconRes = stateValues.drawableResIconSupplierCatalog.value
            ) { Navigation.goMain(NavigationScreenModel.Supplier.Catalog.Main) }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
            ) {
                WorkflowButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(254, "Orders"),
                    iconPath = stateValues.drawablePathIconAppModeSupplier,
                    iconRes = stateValues.drawableResIconAppModeSupplier.value
                ) { Navigation.goMain(NavigationScreenModel.Supplier.Orders.Main) }
                WorkflowButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(1453, "Partner stores"),
                    iconPath = stateValues.drawablePathIconSupplierPartners,
                    iconRes = stateValues.drawableResIconSupplierPartners.value
                ) { Navigation.goMain(NavigationScreenModel.Supplier.Customers.Main) }
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
            ) {
                WorkflowButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(2398, "All agreements"),
                    iconPath = stateValues.drawablePathIconSupplierContracts,
                    iconRes = stateValues.drawableResIconSupplierContracts.value
                ) { Navigation.goMain(NavigationScreenModel.Supplier.Contracts.Main) }
                WorkflowButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(1338, "Catalog"),
                    iconPath = stateValues.drawablePathIconSupplierCatalog,
                    iconRes = stateValues.drawableResIconSupplierCatalog.value
                ) { Navigation.goMain(NavigationScreenModel.Supplier.Catalog.Main) }
            }
        }
    }
}

private fun AppConfiguration.supplierDispatchRunAccent(run: SupplierDispatchWorkspaceRunUiModel) = when {
    run.issueCount > 0 -> stateValues.ErrorColor
    run.contractBlockedOrderIds.isNotEmpty() -> stateValues.BorderlineBadColor
    !run.contractSafetyReady -> stateValues.BorderlineBadColor
    run.attentionCount > 0 -> stateValues.BorderlineBadColor
    run.readyToPackCount > 0 || run.readyToDispatchCount > 0 -> stateValues.AccentColor
    run.inDeliveryCount > 0 -> stateValues.OkayColor
    else -> stateValues.IconTintColor
}

@Composable
internal fun AppConfiguration.SupplierDispatchRunCompactCard(
    run: SupplierDispatchWorkspaceRunUiModel,
    onOpen: () -> Unit
) {
    val accent = supplierDispatchRunAccent(run)
    val dueText = run.earliestDueAtMillis
        ?.takeIf { it > 0L }
        ?.toStockDateInputText()
        ?.takeIf { it.isNotBlank() }
        ?: localizedStringResource(2468, "Unscheduled")
    val amountText = run.estimatedAmount.supplierDeskMoneyText()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(
                if (run.attentionCount > 0 || !run.contractSafetyReady) stateValues.focusedBorderWidth else stateValues.unfocusedBorderWidth,
                if (run.attentionCount > 0 || !run.contractSafetyReady) accent else stateValues.PlaceholderTextColor,
                RoundedCornerShape(stateValues.cornerRadius)
            )
            .aitaClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = stateValues.AccentColor),
                onClick = onOpen
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
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                CpImage(
                    modifier = Modifier.size(27.dp),
                    url = stateValues.drawablePathIconSupplierDispatch,
                    fallbackRes = stateValues.drawableResIconSupplierDispatch.value,
                    contentDescription = localizedStringResource(1556, "Dispatch"),
                    tintColor = accent
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = run.storeTitle.ifBlank { localizedStringResource(2477, "Store delivery run") },
                    color = stateValues.TextColor,
                    fontSize = stateValues.accentTextSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOfNotNull(
                        run.storePublicId.takeIf { it.isNotBlank() },
                        run.storeAddress.takeIf { it.isNotBlank() }
                    ).joinToString(" • ").ifBlank { dueText },
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = supplierDispatchSuggestedActionTitle(run.suggestedAction),
                    color = accent,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = dueText,
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    textAlign = TextAlign.End,
                    maxLines = 1
                )
            }
        }

        Text(
            text = run.goodsPreview.ifBlank { localizedStringResource(1566, "No goods lines yet") },
            color = stateValues.TextColor,
            fontSize = stateValues.textSize,
            fontWeight = FontWeight.Bold,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(horizontal = 1.dp)
        ) {
            item {
                SupplierCatalogChip(
                    text = "${localizedStringResource(2478, "Orders in this run")}: ${run.orderCount}"
                )
            }
            if (!run.contractSafetyReady) {
                item {
                    SupplierCatalogChip(
                        text = localizedStringResource(1562, "Contract check"),
                        color = stateValues.BorderlineBadColor
                    )
                }
            }
            if (run.readyToPackCount > 0) {
                item {
                    SupplierCatalogChip(
                        text = "${localizedStringResource(1689, "Ready to pack")}: ${run.readyToPackCount}",
                        color = stateValues.AccentColor
                    )
                }
            }
            if (run.readyToDispatchCount > 0) {
                item {
                    SupplierCatalogChip(
                        text = "${localizedStringResource(2442, "Ready to dispatch")}: ${run.readyToDispatchCount}",
                        color = stateValues.AccentColor
                    )
                }
            }
            if (run.inDeliveryCount > 0) {
                item {
                    SupplierCatalogChip(
                        text = "${localizedStringResource(1560, "In delivery")}: ${run.inDeliveryCount}",
                        color = stateValues.OkayColor
                    )
                }
            }
            if (run.contractBlockedOrderIds.isNotEmpty()) {
                item {
                    SupplierCatalogChip(
                        text = "${localizedStringResource(2467, "Contract blocked")}: ${run.contractBlockedOrderIds.size}",
                        color = stateValues.BorderlineBadColor
                    )
                }
            }
            if (run.issueCount > 0) {
                item {
                    SupplierCatalogChip(
                        text = "${localizedStringResource(1473, "Issues")}: ${run.issueCount}",
                        color = stateValues.ErrorColor
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            Text(
                text = listOfNotNull(
                    amountText.takeIf { it.isNotBlank() },
                    "${localizedStringResource(1564, "Goods lines")}: ${run.lineCount}",
                    if (run.serverPlanned) localizedStringResource(2466, "Run ready") else null
                ).joinToString(" • "),
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            CpImage(
                modifier = Modifier.size(20.dp),
                url = stateValues.drawablePathIconExpandMore,
                fallbackRes = stateValues.drawableResIconExpandMore.value,
                contentDescription = localizedStringResource(2455, "Open delivery run"),
                tintColor = stateValues.IconTintColor
            )
        }
    }
}

@Composable
private fun AppConfiguration.SupplierDispatchTextPanel(
    title: String,
    text: String,
    attention: Boolean = false
) {
    if (text.isBlank()) return
    val accent = if (attention) stateValues.BorderlineBadColor else stateValues.AccentColor
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(accent.copy(alpha = 0.06f))
            .border(
                stateValues.unfocusedBorderWidth,
                accent.copy(alpha = 0.38f),
                RoundedCornerShape(stateValues.cornerRadius)
            )
            .padding(stateValues.marginTextField),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            color = accent,
            fontSize = stateValues.smallTextSize,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = text,
            color = stateValues.TextColor,
            fontSize = stateValues.smallTextSize
        )
    }
}

@Composable
internal fun AppConfiguration.SupplierDispatchRunDetail(
    run: SupplierDispatchWorkspaceRunUiModel,
    mutationKey: String?,
    feedbackMessage: List<LocalizedStringDataModel>?,
    feedbackType: NotificationType?,
    onMarkPacked: () -> Unit,
    onStartDelivery: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val amountText = run.estimatedAmount.supplierDeskMoneyText()
    val statusSummary = run.statusMix
        .take(6)
        .joinToString(" • ") { bucket ->
            "${supplierOrderStatusTitle(bucket.status)} ${bucket.orderCount}"
        }
    val mutatingPacked = mutationKey == "${run.key}:${SupplierOrderStatusDataModel.Packed.name}"
    val mutatingDelivery = mutationKey == "${run.key}:${SupplierOrderStatusDataModel.InDelivery.name}"
    val anyMutation = mutationKey != null
    val contractSafetyPendingMessage = localizedStringResource(
        2479,
        "Please wait while AITA checks agreements before packing or dispatching."
    )
    val packDisabledMessage = when {
        !run.contractSafetyReady -> contractSafetyPendingMessage
        anyMutation -> localizedStringResource(2460, "Updating orders…")
        else -> localizedStringResource(1576, "No packable orders in this lane")
    }
    val dispatchDisabledMessage = when {
        !run.contractSafetyReady -> contractSafetyPendingMessage
        anyMutation -> localizedStringResource(2460, "Updating orders…")
        else -> localizedStringResource(1577, "Pack orders before starting delivery")
    }
    val feedbackText = feedbackMessage.orEmpty()
        .visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { feedbackMessage.orEmpty().visibleLocalizedString("main", "") }
    val feedbackColor = when (feedbackType) {
        NotificationType.Negative -> stateValues.ErrorColor
        NotificationType.Neutral -> stateValues.BorderlineBadColor
        NotificationType.Positive -> stateValues.OkayColor
        null -> stateValues.PlaceholderTextColor
    }

    fun openOrders() {
        coroutineScope.launch {
            seedSupplierOrdersInboxNavigation(
                searchQuery = run.navigationSearchQuery,
                dueFilter = "all",
                statusFilter = "all"
            )
            Navigation.goMain(NavigationScreenModel.Supplier.Orders.Main)
        }
    }

    fun openPartner() {
        coroutineScope.launch {
            seedSupplierCustomersNavigation(
                searchQuery = run.navigationSearchQuery,
                filterId = SUPPLIER_CUSTOMERS_FILTER_ALL,
                sortId = SUPPLIER_CUSTOMERS_SORT_ACTION
            )
            Navigation.goMain(NavigationScreenModel.Supplier.Customers.Main)
        }
    }

    fun openContracts() {
        coroutineScope.launch {
            seedSupplierContractsNavigation(
                searchQuery = run.navigationSearchQuery,
                statusFilter = if (run.contractBlockedOrderIds.isNotEmpty()) {
                    SUPPLIER_CONTRACT_FILTER_PENDING
                } else {
                    SUPPLIER_CONTRACT_FILTER_ALL
                },
                sortId = SUPPLIER_CONTRACT_SORT_ACTION
            )
            Navigation.goMain(NavigationScreenModel.Supplier.Contracts.Main)
        }
    }

    fun openCatalog() {
        coroutineScope.launch {
            seedSupplierCatalogNavigation(
                searchQuery = run.navigationSearchQuery,
                filterId = SUPPLIER_CATALOG_FILTER_ALL,
                sortId = SUPPLIER_CATALOG_SORT_ACTION
            )
            Navigation.goMain(NavigationScreenModel.Supplier.Catalog.Main)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(
                if (run.attentionCount > 0) stateValues.focusedBorderWidth else stateValues.unfocusedBorderWidth,
                if (run.attentionCount > 0) supplierDispatchRunAccent(run) else stateValues.PlaceholderTextColor,
                RoundedCornerShape(stateValues.cornerRadius)
            )
            .padding(stateValues.marginTextFieldGroup),
        verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
    ) {
        val section = sectionTabsWidget(
            stateKey = "supplier-dispatch-detail:${run.key}",
            tabs = listOf(
                TabContent("overview", authUiText("Overview", "Обзор", "Шолу", "Жалпы көрүнүш")),
                TabContent("checklist", authUiText("Checklist", "Чек-лист", "Тексеру тізімі", "Текшерүү тизмеси")),
                TabContent("attention", authUiText("Attention", "Внимание", "Назар аудару", "Көңүл буруңуз")),
                TabContent("handoff", authUiText("Driver handoff", "Передача водителю", "Жүргізушіге тапсыру", "Айдоочуга өткөрүп берүү")),
                TabContent("actions", authUiText("Actions", "Действия", "Әрекеттер", "Аракеттер"))
            ),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Start)
                .padding(vertical = stateValues.marginTextField / 2),
        )

        if (section == "overview") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(stateValues.cornerRadius))
                        .background(supplierDispatchRunAccent(run).copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    CpImage(
                        modifier = Modifier.size(30.dp),
                        url = stateValues.drawablePathIconSupplierDispatch,
                        fallbackRes = stateValues.drawableResIconSupplierDispatch.value,
                        contentDescription = localizedStringResource(1556, "Dispatch"),
                        tintColor = supplierDispatchRunAccent(run)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = run.storeTitle,
                        color = stateValues.TextColor,
                        fontSize = stateValues.titleTextSize,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = listOfNotNull(
                            run.storePublicId.takeIf { it.isNotBlank() },
                            run.storeAddress.takeIf { it.isNotBlank() }
                        ).joinToString(" • ").ifBlank { run.storeId.take(12) },
                        color = stateValues.PlaceholderTextColor,
                        fontSize = stateValues.smallTextSize,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                SupplierCatalogChip(
                    text = supplierDispatchSuggestedActionTitle(run.suggestedAction),
                    color = supplierDispatchRunAccent(run)
                )
            }

            Text(
                text = run.goodsPreview,
                color = stateValues.TextColor,
                fontSize = stateValues.textSize,
                fontWeight = FontWeight.Bold
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StockCardInfoLine(
                    localizedStringResource(2478, "Orders in this run"),
                    run.orderCount.toString(),
                    stateValues.TextColor
                )
                StockCardInfoLine(
                    localizedStringResource(1564, "Goods lines"),
                    run.lineCount.toString(),
                    stateValues.TextColor
                )
                amountText.takeIf { it.isNotBlank() }?.let {
                    StockCardInfoLine(localizedStringResource(581, "Amount"), it, stateValues.TextColor)
                }
                run.earliestDueAtMillis?.takeIf { it > 0L }?.let {
                    StockCardInfoLine(
                        localizedStringResource(1723, "Earliest due"),
                        receiptUiDateTime(it),
                        stateValues.TextColor
                    )
                }
                run.latestDueAtMillis?.takeIf { it > 0L && it != run.earliestDueAtMillis }?.let {
                    StockCardInfoLine(
                        localizedStringResource(1724, "Latest due"),
                        receiptUiDateTime(it),
                        stateValues.TextColor
                    )
                }
                run.latestActivityMillis.takeIf { it > 0L }?.let {
                    StockCardInfoLine(
                        localizedStringResource(1463, "Last activity"),
                        receiptUiDateTime(it),
                        stateValues.PlaceholderTextColor
                    )
                }
                statusSummary.takeIf { it.isNotBlank() }?.let {
                    StockCardInfoLine(localizedStringResource(200, "Status"), it, stateValues.PlaceholderTextColor)
                }
                StockCardInfoLine(
                    localizedStringResource(1562, "Contract check"),
                    if (run.contractSafetyReady) {
                        "${run.activeContractCount} / ${run.pendingContractCount}"
                    } else {
                        localizedStringResource(1141, "Please wait…")
                    },
                    if (!run.contractSafetyReady || run.contractBlockedOrderIds.isNotEmpty()) {
                        stateValues.BorderlineBadColor
                    } else {
                        stateValues.TextColor
                    }
                )
            }

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(horizontal = 1.dp)
            ) {
                item {
                    SupplierCatalogChip(
                        text = "${localizedStringResource(1689, "Ready to pack")}: ${run.readyToPackCount}",
                        color = if (run.readyToPackCount > 0) stateValues.AccentColor else stateValues.PlaceholderTextColor
                    )
                }
                item {
                    SupplierCatalogChip(
                        text = "${localizedStringResource(2442, "Ready to dispatch")}: ${run.readyToDispatchCount}",
                        color = if (run.readyToDispatchCount > 0) stateValues.AccentColor else stateValues.PlaceholderTextColor
                    )
                }
                item {
                    SupplierCatalogChip(
                        text = "${localizedStringResource(1560, "In delivery")}: ${run.inDeliveryCount}",
                        color = if (run.inDeliveryCount > 0) stateValues.OkayColor else stateValues.PlaceholderTextColor
                    )
                }
                if (run.attentionCount > 0) {
                    item {
                        SupplierCatalogChip(
                            text = "${localizedStringResource(1371, "Needs attention")}: ${run.attentionCount}",
                            color = stateValues.BorderlineBadColor
                        )
                    }
                }
            }
        }

        if (section == "checklist") {
            SupplierDispatchTextPanel(
                title = localizedStringResource(1746, "Pack checklist"),
                text = run.packChecklist
            )
        }
        if (section == "attention") {
            SupplierDispatchTextPanel(
                title = localizedStringResource(1756, "Attention notes"),
                text = run.attentionSummary,
                attention = true
            )
        }
        if (section == "handoff") {
            SupplierDispatchTextPanel(
                title = localizedStringResource(1757, "Driver handoff"),
                text = run.driverHandoff
            )
        }

        if (!run.contractSafetyReady) {
            MessageText(
                modifier = Modifier.fillMaxWidth(),
                text = localizedStringResource(1562, "Contract check"),
                subText = contractSafetyPendingMessage,
                subTextSize = stateValues.smallTextSize
            )
        }

        if (run.contractBlockedOrderIds.isNotEmpty()) {
            MessageText(
                modifier = Modifier.fillMaxWidth(),
                text = localizedStringResource(1792, "Clear contracts before packing or dispatching this run."),
                subText = localizedStringResource(1793, "Pack and dispatch buttons use only server-safe, unblocked orders."),
                subTextSize = stateValues.smallTextSize
            )
        }

        if (feedbackText.isNotBlank()) {
            Text(
                text = feedbackText,
                color = feedbackColor,
                fontSize = stateValues.smallTextSize,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(stateValues.cornerRadius))
                    .background(feedbackColor.copy(alpha = 0.08f))
                    .border(
                        stateValues.unfocusedBorderWidth,
                        feedbackColor.copy(alpha = 0.45f),
                        RoundedCornerShape(stateValues.cornerRadius)
                    )
                    .padding(stateValues.marginTextField)
            )
        }

        if (section == "actions") {
            Text(
                text = localizedStringResource(2458, "Delivery work"),
                color = stateValues.TextColor,
                fontSize = stateValues.titleTextSize,
                fontWeight = FontWeight.Bold
            )

            if (stateValues.isNarrowScreen) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                ) {
                    actionButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = localizedStringResource(1568, "Open run orders"),
                        iconPath = stateValues.drawablePathIconAppModeSupplier,
                        iconRes = stateValues.drawableResIconAppModeSupplier.value,
                        confirmationRequired = false,
                        autoLoading = false,
                        onClick = ::openOrders
                    )
                    actionButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = localizedStringResource(2463, "Open partner store"),
                        iconPath = stateValues.drawablePathIconSupplierPartners,
                        iconRes = stateValues.drawableResIconSupplierPartners.value,
                        confirmationRequired = false,
                        autoLoading = false,
                        onClick = ::openPartner
                    )
                    actionButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = localizedStringResource(2464, "Open agreements"),
                        iconPath = stateValues.drawablePathIconSupplierContracts,
                        iconRes = stateValues.drawableResIconSupplierContracts.value,
                        confirmationRequired = false,
                        autoLoading = false,
                        onClick = ::openContracts
                    )
                    actionButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = localizedStringResource(2465, "Open catalogue"),
                        iconPath = stateValues.drawablePathIconSupplierCatalog,
                        iconRes = stateValues.drawableResIconSupplierCatalog.value,
                        confirmationRequired = false,
                        autoLoading = false,
                        onClick = ::openCatalog
                    )
                    actionButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = localizedStringResource(1569, "Copy driver manifest"),
                        iconPath = stateValues.drawablePathIconClipboard,
                        iconRes = stateValues.drawableResIconClipboard.value,
                        confirmationRequired = false,
                        autoLoading = false,
                        onClick = {
                            copyTextToClipboard(supplierDispatchRunManifest(run))
                            postInAppNotification(
                                localizedStringResource(1573, "Driver manifest copied"),
                                NotificationType.Positive,
                                transient = true
                            )
                        }
                    )
                    actionButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = localizedStringResource(1570, "Mark packed"),
                        enabled = run.contractSafetyReady && run.packableOrderIds.isNotEmpty() && !anyMutation,
                        loading = mutatingPacked,
                        loadingText = localizedStringResource(2460, "Updating orders…"),
                        autoLoading = false,
                        iconPath = stateValues.drawablePathIconStock,
                        iconRes = stateValues.drawableResIconStock.value,
                        confirmationRequired = true,
                        onDisabledClick = {
                            postInAppNotification(
                                packDisabledMessage,
                                NotificationType.Neutral,
                                transient = true
                            )
                        },
                        onClick = onMarkPacked
                    )
                    actionButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = localizedStringResource(1571, "Start delivery"),
                        enabled = run.contractSafetyReady && run.dispatchableOrderIds.isNotEmpty() && !anyMutation,
                        loading = mutatingDelivery,
                        loadingText = localizedStringResource(2460, "Updating orders…"),
                        autoLoading = false,
                        iconPath = stateValues.drawablePathIconSupplierDispatch,
                        iconRes = stateValues.drawableResIconSupplierDispatch.value,
                        confirmationRequired = true,
                        onDisabledClick = {
                            postInAppNotification(
                                dispatchDisabledMessage,
                                NotificationType.Neutral,
                                transient = true
                            )
                        },
                        onClick = onStartDelivery
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                    ) {
                        actionButton(
                            modifier = Modifier.fillMaxWidth(),
                            text = localizedStringResource(1568, "Open run orders"),
                            iconPath = stateValues.drawablePathIconAppModeSupplier,
                            iconRes = stateValues.drawableResIconAppModeSupplier.value,
                            textSize = stateValues.smallTextSize,
                            confirmationRequired = false,
                            autoLoading = false,
                            onClick = ::openOrders
                        )
                        actionButton(
                            modifier = Modifier.fillMaxWidth(),
                            text = localizedStringResource(2463, "Open partner store"),
                            iconPath = stateValues.drawablePathIconSupplierPartners,
                            iconRes = stateValues.drawableResIconSupplierPartners.value,
                            textSize = stateValues.smallTextSize,
                            confirmationRequired = false,
                            autoLoading = false,
                            onClick = ::openPartner
                        )
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                    ) {
                        actionButton(
                            modifier = Modifier.fillMaxWidth(),
                            text = localizedStringResource(2464, "Open agreements"),
                            iconPath = stateValues.drawablePathIconSupplierContracts,
                            iconRes = stateValues.drawableResIconSupplierContracts.value,
                            textSize = stateValues.smallTextSize,
                            confirmationRequired = false,
                            autoLoading = false,
                            onClick = ::openContracts
                        )
                        actionButton(
                            modifier = Modifier.fillMaxWidth(),
                            text = localizedStringResource(2465, "Open catalogue"),
                            iconPath = stateValues.drawablePathIconSupplierCatalog,
                            iconRes = stateValues.drawableResIconSupplierCatalog.value,
                            textSize = stateValues.smallTextSize,
                            confirmationRequired = false,
                            autoLoading = false,
                            onClick = ::openCatalog
                        )
                    }
                    actionButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = localizedStringResource(1569, "Copy driver manifest"),
                        iconPath = stateValues.drawablePathIconClipboard,
                        iconRes = stateValues.drawableResIconClipboard.value,
                        textSize = stateValues.smallTextSize,
                        confirmationRequired = false,
                        autoLoading = false,
                        onClick = {
                            copyTextToClipboard(supplierDispatchRunManifest(run))
                            postInAppNotification(
                                localizedStringResource(1573, "Driver manifest copied"),
                                NotificationType.Positive,
                                transient = true
                            )
                        }
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                    ) {
                        actionButton(
                            modifier = Modifier.fillMaxWidth(),
                            text = localizedStringResource(1570, "Mark packed"),
                            enabled = run.contractSafetyReady && run.packableOrderIds.isNotEmpty() && !anyMutation,
                            loading = mutatingPacked,
                            loadingText = localizedStringResource(2460, "Updating orders…"),
                            autoLoading = false,
                            iconPath = stateValues.drawablePathIconStock,
                            iconRes = stateValues.drawableResIconStock.value,
                            textSize = stateValues.smallTextSize,
                            confirmationRequired = true,
                            onDisabledClick = {
                                postInAppNotification(
                                    packDisabledMessage,
                                    NotificationType.Neutral,
                                    transient = true
                                )
                            },
                            onClick = onMarkPacked
                        )
                        actionButton(
                            modifier = Modifier.fillMaxWidth(),
                            text = localizedStringResource(1571, "Start delivery"),
                            enabled = run.contractSafetyReady && run.dispatchableOrderIds.isNotEmpty() && !anyMutation,
                            loading = mutatingDelivery,
                            loadingText = localizedStringResource(2460, "Updating orders…"),
                            autoLoading = false,
                            iconPath = stateValues.drawablePathIconSupplierDispatch,
                            iconRes = stateValues.drawableResIconSupplierDispatch.value,
                            textSize = stateValues.smallTextSize,
                            confirmationRequired = true,
                            onDisabledClick = {
                                postInAppNotification(
                                    dispatchDisabledMessage,
                                    NotificationType.Neutral,
                                    transient = true
                                )
                            },
                            onClick = onStartDelivery
                        )
                    }
                }
            }
        }

        if (section == "overview") {
            Text(
                text = if (run.serverPlanned) {
                    localizedStringResource(
                        2475,
                        "This run uses the current server plan and the latest locally loaded order state."
                    )
                } else {
                    localizedStringResource(
                        2476,
                        "A locally reconstructed run is shown until the server dashboard includes it."
                    )
                },
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize
            )
        }
    }
}

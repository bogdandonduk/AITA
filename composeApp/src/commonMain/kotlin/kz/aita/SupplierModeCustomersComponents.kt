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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource

@Composable
internal fun AppConfiguration.SupplierPartnerPortfolioHealthCard(
    summary: SupplierPartnerPortfolioHealthUiModel,
    onNextStep: () -> Unit,
) {
    val actionText = when (summary.nextStep) {
        SupplierPartnerPortfolioNextStep.REVIEW_ATTENTION ->
            localizedStringResource(2542, "Review partner attention")
        SupplierPartnerPortfolioNextStep.REFRESH_RELATIONSHIP_DATA ->
            localizedStringResource(2548, "Refresh relationship data")
        SupplierPartnerPortfolioNextStep.BUILD_OFFERS ->
            localizedStringResource(2543, "Build missing offers")
        SupplierPartnerPortfolioNextStep.COMPLETE_AGREEMENTS ->
            localizedStringResource(2544, "Complete partner agreements")
        SupplierPartnerPortfolioNextStep.OPEN_INSIGHTS ->
            localizedStringResource(2545, "Open Supplier insights")
    }
    val description = when (summary.nextStep) {
        SupplierPartnerPortfolioNextStep.REVIEW_ATTENTION ->
            localizedStringResource(2547, "Prioritize relationships that may block orders or deliveries.")
        SupplierPartnerPortfolioNextStep.REFRESH_RELATIONSHIP_DATA ->
            localizedStringResource(2549, "AITA is still checking offers and agreements before recommending commercial work.")
        SupplierPartnerPortfolioNextStep.BUILD_OFFERS ->
            localizedStringResource(2530, "Add reusable offers so Stores can order with less manual work.")
        SupplierPartnerPortfolioNextStep.COMPLETE_AGREEMENTS ->
            localizedStringResource(2532, "Record delivery, payment, and product terms with partner Stores.")
        SupplierPartnerPortfolioNextStep.OPEN_INSIGHTS ->
            localizedStringResource(2546, "Every partner has an active agreement and a usable offer.")
    }
    val actionIconPath = when (summary.nextStep) {
        SupplierPartnerPortfolioNextStep.REVIEW_ATTENTION -> stateValues.drawablePathIconResponse
        SupplierPartnerPortfolioNextStep.REFRESH_RELATIONSHIP_DATA -> stateValues.drawablePathIconRefresh
        SupplierPartnerPortfolioNextStep.BUILD_OFFERS -> stateValues.drawablePathIconSupplierCatalog
        SupplierPartnerPortfolioNextStep.COMPLETE_AGREEMENTS -> stateValues.drawablePathIconSupplierContracts
        SupplierPartnerPortfolioNextStep.OPEN_INSIGHTS -> stateValues.drawablePathIconSupplierDemandRadar
    }
    val actionIconRes = when (summary.nextStep) {
        SupplierPartnerPortfolioNextStep.REVIEW_ATTENTION -> stateValues.drawableResIconResponse.value
        SupplierPartnerPortfolioNextStep.REFRESH_RELATIONSHIP_DATA -> stateValues.drawableResIconRefresh.value
        SupplierPartnerPortfolioNextStep.BUILD_OFFERS -> stateValues.drawableResIconSupplierCatalog.value
        SupplierPartnerPortfolioNextStep.COMPLETE_AGREEMENTS -> stateValues.drawableResIconSupplierContracts.value
        SupplierPartnerPortfolioNextStep.OPEN_INSIGHTS -> stateValues.drawableResIconSupplierDemandRadar.value
    }

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
                    modifier = Modifier.size(30.dp),
                    url = stateValues.drawablePathIconSupplierPartners,
                    fallbackRes = stateValues.drawableResIconSupplierPartners.value,
                    contentDescription = localizedStringResource(2537, "Partner portfolio health"),
                    tintColor = stateValues.AccentColor
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = localizedStringResource(2537, "Partner portfolio health"),
                    color = stateValues.TextColor,
                    fontSize = stateValues.accentTextSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = localizedStringResource(2538, "Commercial coverage"),
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            SupplierCatalogChip(
                text = if (summary.coverageIsFinal) {
                    "${summary.coveragePercent}%"
                } else {
                    localizedStringResource(2550, "Still checking")
                },
                color = when {
                    !summary.coverageIsFinal -> stateValues.PlaceholderTextColor
                    summary.coveragePercent == 100 -> stateValues.OkayColor
                    else -> stateValues.AccentColor
                }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(stateValues.cornerRadius))
                .background(stateValues.DisabledColor.copy(alpha = 0.35f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((summary.coveragePercent / 100f).coerceIn(0f, 1f))
                    .height(8.dp)
                    .clip(RoundedCornerShape(stateValues.cornerRadius))
                    .background(
                        when {
                            !summary.coverageIsFinal -> stateValues.PlaceholderTextColor
                            summary.coveragePercent == 100 -> stateValues.OkayColor
                            else -> stateValues.AccentColor
                        }
                    )
            )
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 1.dp)
        ) {
            item(key = "ready") {
                SupplierCatalogChip(
                    text = "${localizedStringResource(2539, "Ready partnerships")}: ${summary.commerciallyReadyCount}",
                    color = stateValues.OkayColor
                )
            }
            item(key = "attention") {
                SupplierCatalogChip(
                    text = "${localizedStringResource(1371, "Needs attention")}: ${summary.attentionCount}",
                    color = if (summary.attentionCount > 0) stateValues.BorderlineBadColor else stateValues.PlaceholderTextColor
                )
            }
            if (summary.readinessUnknownCount > 0) {
                item(key = "unknown") {
                    SupplierCatalogChip(
                        text = "${localizedStringResource(2550, "Still checking")}: ${summary.readinessUnknownCount}",
                        color = stateValues.PlaceholderTextColor
                    )
                }
            }
            if (summary.coverageIsFinal || summary.missingActiveAgreementCount > 0) {
                item(key = "agreement") {
                    SupplierCatalogChip(
                        text = "${localizedStringResource(2540, "Without an active agreement")}: ${summary.missingActiveAgreementCount}",
                        color = if (summary.missingActiveAgreementCount > 0) stateValues.AccentColor else stateValues.PlaceholderTextColor
                    )
                }
            }
            if (summary.coverageIsFinal || summary.missingUsableOfferCount > 0) {
                item(key = "offer") {
                    SupplierCatalogChip(
                        text = "${localizedStringResource(2541, "Without a usable offer")}: ${summary.missingUsableOfferCount}",
                        color = if (summary.missingUsableOfferCount > 0) stateValues.AccentColor else stateValues.PlaceholderTextColor
                    )
                }
            }
        }

        Text(
            text = description,
            color = stateValues.TextColor,
            fontSize = stateValues.smallTextSize,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )

        actionButton(
            modifier = Modifier.fillMaxWidth(),
            text = actionText,
            iconPath = actionIconPath,
            iconRes = actionIconRes,
            confirmationRequired = false,
            autoLoading = false,
            onClick = onNextStep
        )
    }
}

@Composable
internal fun AppConfiguration.SupplierCustomersFilterPanel(
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
            SUPPLIER_CUSTOMERS_FILTER_ALL,
            localizedStringResource(2371, "All partners")
        ),
        SupplierOrdersQuickFilterUiModel(
            SUPPLIER_CUSTOMERS_FILTER_ACTIVE,
            localizedStringResource(2358, "In progress")
        ),
        SupplierOrdersQuickFilterUiModel(
            SUPPLIER_CUSTOMERS_FILTER_ATTENTION,
            localizedStringResource(1371, "Needs attention")
        ),
        SupplierOrdersQuickFilterUiModel(
            SUPPLIER_CUSTOMERS_FILTER_DELIVERY,
            localizedStringResource(2396, "Packing & delivery")
        ),
        SupplierOrdersQuickFilterUiModel(
            SUPPLIER_CUSTOMERS_FILTER_PRICE_GAPS,
            localizedStringResource(2374, "Without complete offers")
        ),
        SupplierOrdersQuickFilterUiModel(
            SUPPLIER_CUSTOMERS_FILTER_CONTRACTS,
            localizedStringResource(2373, "With contracts")
        ),
        SupplierOrdersQuickFilterUiModel(
            SUPPLIER_CUSTOMERS_FILTER_OFFERS,
            localizedStringResource(2360, "Saved offers")
        )
    )
    val hasNonDefaultFilter = searchQuery.isNotBlank() ||
            filterId != SUPPLIER_CUSTOMERS_FILTER_ALL ||
            sortId != SUPPLIER_CUSTOMERS_SORT_ACTION

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
                fillMaxWidthIfTextPresent = false,
                text = "",
                iconPath = stateValues.drawablePathIconSettings,
                iconRes = stateValues.drawableResIconSettings.value,
                iconContentDescription = localizedStringResource(1274, "Filters"),
                confirmationRequired = false,
                autoLoading = false,
                enabledColor = if (expanded || sortId != SUPPLIER_CUSTOMERS_SORT_ACTION) {
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
                    fillMaxWidthIfTextPresent = false,
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
                title = localizedStringResource(2366, "Sort partners"),
                selectedId = sortId,
                options = listOf(
                    DropdownOption(
                        SUPPLIER_CUSTOMERS_SORT_ACTION,
                        localizedStringResource(2367, "Action first")
                    ),
                    DropdownOption(
                        SUPPLIER_CUSTOMERS_SORT_RECENT,
                        localizedStringResource(2368, "Most recent")
                    ),
                    DropdownOption(
                        SUPPLIER_CUSTOMERS_SORT_NAME,
                        localizedStringResource(2370, "Name A–Z")
                    ),
                    DropdownOption(
                        SUPPLIER_CUSTOMERS_SORT_ORDERS,
                        localizedStringResource(2369, "Most orders")
                    )
                ),
                placeholder = localizedStringResource(2367, "Action first"),
                onSelected = { onSortChanged(it.ifBlank { SUPPLIER_CUSTOMERS_SORT_ACTION }) }
            )
        }
    }
}


@Composable
internal fun AppConfiguration.SupplierCustomersWorkflowLinks() {
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
    fun CatalogButton(modifier: Modifier) {
        actionButton(
            modifier = modifier,
            text = localizedStringResource(1338, "Catalog"),
            iconPath = stateValues.drawablePathIconSupplierCatalog,
            iconRes = stateValues.drawableResIconSupplierCatalog.value,
            confirmationRequired = false,
            autoLoading = false,
            onClick = {
                coroutineScope.launch {
                    Navigation.goMain(NavigationScreenModel.Supplier.Catalog.Main)
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
            CatalogButton(Modifier.fillMaxWidth())
            ContractsButton(Modifier.fillMaxWidth())
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            OrdersButton(Modifier.weight(1f))
            CatalogButton(Modifier.weight(1f))
            ContractsButton(Modifier.weight(1f))
        }
    }
}

internal fun AppConfiguration.supplierPartnerActionTitle(partner: SupplierPartnerUiModel): String = when (partner.actionId) {
    "issue" -> localizedStringResource(1637, "Resolve supplier issue")
    "overdue" -> localizedStringResource(2394, "Delivery overdue")
    "orders" -> localizedStringResource(2364, "Needs a reply")
    "contract" -> localizedStringResource(2380, "Review contracts")
    "delivery" -> localizedStringResource(2381, "Open dispatch")
    "open" -> localizedStringResource(1455, "Open work")
    "price" -> localizedStringResource(2337, "Price missing")
    "waiting_store" -> localizedStringResource(1527, "Waiting for the other side")
    "ready" -> localizedStringResource(2397, "Relationship active")
    "history" -> localizedStringResource(2377, "Order activity")
    else -> localizedStringResource(1468, "New relationship")
}

private fun AppConfiguration.supplierPartnerAccent(partner: SupplierPartnerUiModel) = when {
    partner.issueOrderCount > 0 -> stateValues.ErrorColor
    partner.overdueOrderCount > 0 -> stateValues.BorderlineBadColor
    partner.hasAttention -> stateValues.BorderlineBadColor
    partner.hasActiveDelivery || partner.packedOrderCount > 0 -> stateValues.AccentColor
    partner.hasContractRelationship || partner.hasOfferRelationship -> stateValues.OkayColor
    else -> stateValues.IconTintColor
}

@Composable
internal fun AppConfiguration.SupplierPartnerCompactCard(
    partner: SupplierPartnerUiModel,
    onOpen: () -> Unit
) {
    val accent = supplierPartnerAccent(partner)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(
                if (partner.hasAttention) stateValues.focusedBorderWidth else stateValues.unfocusedBorderWidth,
                if (partner.hasAttention) accent else stateValues.PlaceholderTextColor,
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
                    url = stateValues.drawablePathIconSupplierPartners,
                    fallbackRes = stateValues.drawableResIconSupplierPartners.value,
                    contentDescription = partner.title,
                    tintColor = accent
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
                    ).joinToString(" • ").ifBlank {
                        partner.storeId.take(12).ifBlank { localizedStringResource(1453, "Partner stores") }
                    },
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            CpImage(
                modifier = Modifier.size(20.dp),
                url = stateValues.drawablePathIconExpandMore,
                fallbackRes = stateValues.drawableResIconExpandMore.value,
                contentDescription = localizedStringResource(2375, "Partner overview"),
                tintColor = stateValues.IconTintColor
            )
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(horizontal = 1.dp)
        ) {
            item { SupplierCatalogChip(text = supplierPartnerActionTitle(partner), color = accent) }
            if (partner.openOrderCount > 0) {
                item {
                    SupplierCatalogChip(
                        text = "${localizedStringResource(1455, "Open work")}: ${partner.openOrderCount}"
                    )
                }
            }
            if (partner.overdueOrderCount > 0) {
                item {
                    SupplierCatalogChip(
                        text = "${localizedStringResource(2394, "Delivery overdue")}: ${partner.overdueOrderCount}",
                        color = stateValues.BorderlineBadColor
                    )
                }
            }
            if (partner.activeContractCount > 0) {
                item {
                    SupplierCatalogChip(
                        text = "${localizedStringResource(1555, "Active contracts")}: ${partner.activeContractCount}",
                        color = stateValues.OkayColor
                    )
                }
            }
            if (partner.priceGapCount > 0) {
                item {
                    SupplierCatalogChip(
                        text = "${localizedStringResource(2317, "Needs a price")}: ${partner.priceGapCount}",
                        color = stateValues.BorderlineBadColor
                    )
                }
            }
            if (partner.savedOfferCount > 0) {
                item {
                    SupplierCatalogChip(
                        text = "${localizedStringResource(2315, "Saved offers")}: ${partner.savedOfferCount}",
                        color = stateValues.OkayColor
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            SupplierCustomerMiniMetric(
                modifier = Modifier.weight(1f),
                value = partner.orderCount.toString(),
                title = localizedStringResource(1337, "Orders")
            )
            SupplierCustomerMiniMetric(
                modifier = Modifier.weight(1f),
                value = partner.deliveredOrderCount.toString(),
                title = localizedStringResource(1472, "Delivered")
            )
            SupplierCustomerMiniMetric(
                modifier = Modifier.weight(1f),
                value = partner.connectedProductCount.toString(),
                title = localizedStringResource(2312, "Products")
            )
        }

        if (partner.latestActivityMillis > 0L) {
            Text(
                text = "${localizedStringResource(2347, "Last activity")}: ${receiptUiDateTime(partner.latestActivityMillis)}",
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AppConfiguration.SupplierCustomerMiniMetric(
    modifier: Modifier,
    value: String,
    title: String
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor.softAppBackgroundColor())
            .padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            color = stateValues.TextColor,
            fontSize = stateValues.textSize,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Text(
            text = title,
            color = stateValues.PlaceholderTextColor,
            fontSize = stateValues.smallTextSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun AppConfiguration.SupplierPartnerDetail(
    partner: SupplierPartnerUiModel,
    onRefreshRelationshipData: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val linesByOrder = remember(partner.lines) { partner.lines.groupBy { it.orderId } }
    val nextAction = remember(partner) { buildSupplierPartnerNextAction(partner) }

    fun openOrders(
        statusFilter: String = "all",
        dueFilter: String = "all",
    ) {
        coroutineScope.launch {
            seedSupplierOrdersInboxNavigation(
                searchQuery = partner.navigationSearchQuery,
                dueFilter = dueFilter,
                statusFilter = statusFilter,
            )
            Navigation.goMain(NavigationScreenModel.Supplier.Orders.Main)
        }
    }

    fun openCatalog(filterId: String = SUPPLIER_CATALOG_FILTER_ALL) {
        coroutineScope.launch {
            seedSupplierCatalogNavigation(
                searchQuery = partner.navigationSearchQuery,
                filterId = filterId,
                sortId = SUPPLIER_CATALOG_SORT_ACTION
            )
            Navigation.goMain(NavigationScreenModel.Supplier.Catalog.Main)
        }
    }

    fun openContracts(statusFilter: String = SUPPLIER_CONTRACT_FILTER_ALL) {
        coroutineScope.launch {
            seedSupplierContractsNavigation(
                searchQuery = partner.navigationSearchQuery,
                statusFilter = statusFilter,
            )
            Navigation.goMain(NavigationScreenModel.Supplier.Contracts.Main)
        }
    }

    fun openDispatch(laneFilter: String = SUPPLIER_DISPATCH_FILTER_ALL) {
        coroutineScope.launch {
            seedSupplierDispatchNavigation(
                searchQuery = partner.navigationSearchQuery,
                laneFilter = laneFilter,
            )
            Navigation.goMain(NavigationScreenModel.Supplier.Dispatch.Main)
        }
    }

    fun openInsights() {
        coroutineScope.launch {
            Navigation.goMain(NavigationScreenModel.Supplier.Analytics.Main)
        }
    }

    fun executeNextAction() {
        when (nextAction.action) {
            SupplierPartnerNextAction.RESOLVE_ORDER_ATTENTION ->
                openOrders(statusFilter = "needs_attention")
            SupplierPartnerNextAction.REVIEW_AGREEMENT_REQUEST ->
                openContracts(statusFilter = SUPPLIER_CONTRACT_FILTER_WAITING_ME)
            SupplierPartnerNextAction.PACK_READY_ORDERS ->
                openDispatch(laneFilter = SUPPLIER_DISPATCH_FILTER_READY_TO_PACK)
            SupplierPartnerNextAction.DISPATCH_PACKED_ORDERS ->
                openDispatch(laneFilter = SUPPLIER_DISPATCH_FILTER_READY_TO_DISPATCH)
            SupplierPartnerNextAction.FOLLOW_ACTIVE_DELIVERY ->
                openDispatch(laneFilter = SUPPLIER_DISPATCH_FILTER_IN_DELIVERY)
            SupplierPartnerNextAction.COMPLETE_OFFER_PRICES,
            SupplierPartnerNextAction.BUILD_USABLE_OFFER ->
                openCatalog(filterId = SUPPLIER_CATALOG_FILTER_MISSING_PRICE)
            SupplierPartnerNextAction.REFRESH_RELATIONSHIP_DATA ->
                onRefreshRelationshipData()
            SupplierPartnerNextAction.COMPLETE_ACTIVE_AGREEMENT ->
                openContracts()
            SupplierPartnerNextAction.REVIEW_OPEN_ORDERS ->
                openOrders(statusFilter = "open")
            SupplierPartnerNextAction.OPEN_INSIGHTS ->
                openInsights()
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
    ) {
        val section = sectionTabsWidget(
            stateKey = "supplier-partner-detail:${partner.partnerKey}",
            tabs = listOf(
                TabContent("overview", authUiText("Overview", "Обзор", "Шолу")),
                TabContent("work", authUiText("Work", "Работа", "Жұмыс")),
                TabContent("commercial", authUiText("Commercial", "Коммерция", "Коммерция")),
                TabContent("orders", localizedStringResource(254, "Orders")),
                TabContent("agreements", authUiText("Agreements", "Соглашения", "Келісімдер")),
                TabContent("offers", authUiText("Offers", "Предложения", "Ұсыныстар"))
            ),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally)
                .padding(vertical = stateValues.marginTextField / 2),
        )

        if (section == "overview") {
            SupplierCustomerOverviewCard(partner)
        }
        if (section == "work") {
            SupplierPartnerNextActionCard(
                nextAction = nextAction,
                onClick = ::executeNextAction,
            )
            SupplierCustomerActionGrid(
                onOrders = { openOrders() },
                onCatalog = { openCatalog() },
                onContracts = { openContracts() },
                onDispatch = { openDispatch() }
            )
            SupplierCustomerWorkCard(partner)
        }
        if (section == "commercial") {
            SupplierCustomerCommercialCard(partner)
        }
        if (section == "orders") {
            SupplierCustomerRecentOrdersCard(partner, linesByOrder)
        }
        if (section == "agreements") {
            SupplierCustomerContractsCard(partner)
        }
        if (section == "offers") {
            SupplierCustomerOffersCard(partner)
        }
        if (section == "overview") {
            actionButton(
                modifier = Modifier.fillMaxWidth(),
                text = localizedStringResource(1466, "Copy partner brief"),
                iconPath = stateValues.drawablePathIconClipboard,
                iconRes = stateValues.drawableResIconClipboard.value,
                confirmationRequired = false,
                autoLoading = false,
                onClick = {
                    copyTextToClipboard(partner.brief)
                    postInAppNotification(
                        localizedStringResource(2386, "Store relationship copied"),
                        NotificationType.Positive,
                        transient = true
                    )
                }
            )
        }
    }
}

@Composable
private fun AppConfiguration.SupplierPartnerNextActionCard(
    nextAction: SupplierPartnerNextActionUiModel,
    onClick: () -> Unit,
) {
    val actionText = when (nextAction.action) {
        SupplierPartnerNextAction.RESOLVE_ORDER_ATTENTION ->
            localizedStringResource(2542, "Review partner attention")
        SupplierPartnerNextAction.REVIEW_AGREEMENT_REQUEST,
        SupplierPartnerNextAction.COMPLETE_ACTIVE_AGREEMENT ->
            localizedStringResource(2544, "Complete partner agreements")
        SupplierPartnerNextAction.PACK_READY_ORDERS,
        SupplierPartnerNextAction.DISPATCH_PACKED_ORDERS,
        SupplierPartnerNextAction.FOLLOW_ACTIVE_DELIVERY ->
            localizedStringResource(2381, "Open dispatch")
        SupplierPartnerNextAction.COMPLETE_OFFER_PRICES,
        SupplierPartnerNextAction.BUILD_USABLE_OFFER ->
            localizedStringResource(2543, "Build missing offers")
        SupplierPartnerNextAction.REFRESH_RELATIONSHIP_DATA ->
            localizedStringResource(2548, "Refresh relationship data")
        SupplierPartnerNextAction.REVIEW_OPEN_ORDERS ->
            localizedStringResource(1465, "View partner orders")
        SupplierPartnerNextAction.OPEN_INSIGHTS ->
            localizedStringResource(2545, "Open Supplier insights")
    }
    val description = when (nextAction.action) {
        SupplierPartnerNextAction.RESOLVE_ORDER_ATTENTION ->
            localizedStringResource(2552, "Resolve the orders that need a reply or may block delivery.")
        SupplierPartnerNextAction.REVIEW_AGREEMENT_REQUEST ->
            localizedStringResource(2553, "This Store is waiting for you to review a proposed agreement.")
        SupplierPartnerNextAction.PACK_READY_ORDERS ->
            localizedStringResource(2554, "Prepare ready orders while their promised delivery window is protected.")
        SupplierPartnerNextAction.DISPATCH_PACKED_ORDERS ->
            localizedStringResource(2555, "Move packed orders into delivery and keep the Store informed.")
        SupplierPartnerNextAction.FOLLOW_ACTIVE_DELIVERY ->
            localizedStringResource(2556, "Follow active deliveries until every quantity is resolved.")
        SupplierPartnerNextAction.COMPLETE_OFFER_PRICES,
        SupplierPartnerNextAction.BUILD_USABLE_OFFER ->
            localizedStringResource(2530, "Add reusable offers so Stores can order with less manual work.")
        SupplierPartnerNextAction.REFRESH_RELATIONSHIP_DATA ->
            localizedStringResource(2549, "AITA is still checking offers and agreements before recommending commercial work.")
        SupplierPartnerNextAction.COMPLETE_ACTIVE_AGREEMENT ->
            localizedStringResource(2532, "Record delivery, payment, and product terms with partner Stores.")
        SupplierPartnerNextAction.REVIEW_OPEN_ORDERS ->
            localizedStringResource(2557, "Review this partner’s open orders and keep the relationship moving.")
        SupplierPartnerNextAction.OPEN_INSIGHTS ->
            localizedStringResource(2558, "This relationship is commercially ready and has no urgent work.")
    }
    val iconPath = when (nextAction.action) {
        SupplierPartnerNextAction.RESOLVE_ORDER_ATTENTION -> stateValues.drawablePathIconResponse
        SupplierPartnerNextAction.REVIEW_AGREEMENT_REQUEST,
        SupplierPartnerNextAction.COMPLETE_ACTIVE_AGREEMENT -> stateValues.drawablePathIconSupplierContracts
        SupplierPartnerNextAction.PACK_READY_ORDERS -> stateValues.drawablePathIconStock
        SupplierPartnerNextAction.DISPATCH_PACKED_ORDERS,
        SupplierPartnerNextAction.FOLLOW_ACTIVE_DELIVERY -> stateValues.drawablePathIconSupplierDispatch
        SupplierPartnerNextAction.COMPLETE_OFFER_PRICES,
        SupplierPartnerNextAction.BUILD_USABLE_OFFER -> stateValues.drawablePathIconSupplierCatalog
        SupplierPartnerNextAction.REFRESH_RELATIONSHIP_DATA -> stateValues.drawablePathIconRefresh
        SupplierPartnerNextAction.REVIEW_OPEN_ORDERS -> stateValues.drawablePathIconAppModeSupplier
        SupplierPartnerNextAction.OPEN_INSIGHTS -> stateValues.drawablePathIconSupplierDemandRadar
    }
    val iconRes = when (nextAction.action) {
        SupplierPartnerNextAction.RESOLVE_ORDER_ATTENTION -> stateValues.drawableResIconResponse.value
        SupplierPartnerNextAction.REVIEW_AGREEMENT_REQUEST,
        SupplierPartnerNextAction.COMPLETE_ACTIVE_AGREEMENT -> stateValues.drawableResIconSupplierContracts.value
        SupplierPartnerNextAction.PACK_READY_ORDERS -> stateValues.drawableResIconStock.value
        SupplierPartnerNextAction.DISPATCH_PACKED_ORDERS,
        SupplierPartnerNextAction.FOLLOW_ACTIVE_DELIVERY -> stateValues.drawableResIconSupplierDispatch.value
        SupplierPartnerNextAction.COMPLETE_OFFER_PRICES,
        SupplierPartnerNextAction.BUILD_USABLE_OFFER -> stateValues.drawableResIconSupplierCatalog.value
        SupplierPartnerNextAction.REFRESH_RELATIONSHIP_DATA -> stateValues.drawableResIconRefresh.value
        SupplierPartnerNextAction.REVIEW_OPEN_ORDERS -> stateValues.drawableResIconAppModeSupplier.value
        SupplierPartnerNextAction.OPEN_INSIGHTS -> stateValues.drawableResIconSupplierDemandRadar.value
    }
    val accent = if (nextAction.urgent) stateValues.BorderlineBadColor else stateValues.AccentColor

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(accent.copy(alpha = 0.08f))
            .border(
                if (nextAction.urgent) stateValues.focusedBorderWidth else stateValues.unfocusedBorderWidth,
                accent,
                RoundedCornerShape(stateValues.cornerRadius),
            )
            .padding(stateValues.marginTextFieldGroup),
        verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(stateValues.cornerRadius))
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                CpImage(
                    modifier = Modifier.size(28.dp),
                    url = iconPath,
                    fallbackRes = iconRes,
                    contentDescription = actionText,
                    tintColor = accent,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = localizedStringResource(2551, "Next best move"),
                    color = stateValues.TextColor,
                    fontSize = stateValues.accentTextSize,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (nextAction.affectedCount > 0) {
                        "$actionText • ${nextAction.affectedCount}"
                    } else {
                        actionText
                    },
                    color = accent,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Text(
            text = description,
            color = stateValues.TextColor,
            fontSize = stateValues.smallTextSize,
        )

        actionButton(
            modifier = Modifier.fillMaxWidth(),
            text = actionText,
            iconPath = iconPath,
            iconRes = iconRes,
            confirmationRequired = false,
            autoLoading = false,
            enabledColor = accent,
            onClick = onClick,
        )
    }
}

@Composable
private fun AppConfiguration.SupplierCustomerOverviewCard(partner: SupplierPartnerUiModel) {
    val accent = supplierPartnerAccent(partner)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(
                if (partner.hasAttention) stateValues.focusedBorderWidth else stateValues.unfocusedBorderWidth,
                if (partner.hasAttention) accent else stateValues.PlaceholderTextColor,
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
                    .size(52.dp)
                    .clip(RoundedCornerShape(stateValues.cornerRadius))
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                CpImage(
                    modifier = Modifier.size(32.dp),
                    url = stateValues.drawablePathIconSupplierPartners,
                    fallbackRes = stateValues.drawableResIconSupplierPartners.value,
                    contentDescription = partner.title,
                    tintColor = accent
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = partner.title,
                    color = stateValues.TextColor,
                    fontSize = stateValues.titleTextSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = supplierPartnerActionTitle(partner),
                    color = accent,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        partner.publicId.takeIf { it.isNotBlank() }?.let {
            StockCardInfoLine("ID", it, stateValues.TextColor)
        }
        partner.address.takeIf { it.isNotBlank() }?.let {
            StockCardInfoLine(localizedStringResource(147, "Address"), it, stateValues.TextColor)
        }
        if (partner.supplierTitles.isNotEmpty()) {
            StockCardInfoLine(
                localizedStringResource(1619, "Supplier profiles"),
                partner.supplierTitles.joinToString(" • "),
                stateValues.TextColor
            )
        }
        partner.latestStatus?.let {
            StockCardInfoLine(
                localizedStringResource(1430, "Latest status"),
                supplierOrderStatusTitle(it),
                stateValues.TextColor
            )
        }
        if (partner.latestActivityMillis > 0L) {
            StockCardInfoLine(
                localizedStringResource(2347, "Last activity"),
                receiptUiDateTime(partner.latestActivityMillis),
                stateValues.TextColor
            )
        }
    }
}

@Composable
private fun AppConfiguration.SupplierCustomerActionGrid(
    onOrders: () -> Unit,
    onCatalog: () -> Unit,
    onContracts: () -> Unit,
    onDispatch: () -> Unit
) {
    data class PartnerAction(
        val text: String,
        val iconPath: String,
        val iconRes: DrawableResource,
        val onClick: () -> Unit
    )

    val actions = listOf(
        PartnerAction(
            localizedStringResource(1465, "View partner orders"),
            stateValues.drawablePathIconAppModeSupplier,
            stateValues.drawableResIconAppModeSupplier.value,
            onOrders
        ),
        PartnerAction(
            localizedStringResource(2379, "Open in catalog"),
            stateValues.drawablePathIconSupplierCatalog,
            stateValues.drawableResIconSupplierCatalog.value,
            onCatalog
        ),
        PartnerAction(
            localizedStringResource(2380, "Review contracts"),
            stateValues.drawablePathIconSupplierContracts,
            stateValues.drawableResIconSupplierContracts.value,
            onContracts
        ),
        PartnerAction(
            localizedStringResource(2381, "Open dispatch"),
            stateValues.drawablePathIconSupplierDispatch,
            stateValues.drawableResIconSupplierDispatch.value,
            onDispatch
        )
    )

    if (stateValues.isNarrowScreen) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            actions.forEach { action ->
                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = action.text,
                    iconPath = action.iconPath,
                    iconRes = action.iconRes,
                    confirmationRequired = false,
                    autoLoading = false,
                    onClick = action.onClick
                )
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            actions.chunked(2).forEach { rowActions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                ) {
                    rowActions.forEach { action ->
                        actionButton(
                            modifier = Modifier.weight(1f),
                            text = action.text,
                            iconPath = action.iconPath,
                            iconRes = action.iconRes,
                            confirmationRequired = false,
                            autoLoading = false,
                            onClick = action.onClick
                        )
                    }
                    if (rowActions.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun AppConfiguration.SupplierCustomerWorkCard(partner: SupplierPartnerUiModel) {
    SupplierCustomerSectionCard(
        title = localizedStringResource(2395, "Current work"),
        iconPath = stateValues.drawablePathIconAppModeSupplier,
        iconRes = stateValues.drawableResIconAppModeSupplier.value
    ) {
        StockCardInfoLine(
            localizedStringResource(1455, "Open work"),
            partner.openOrderCount.toString(),
            stateValues.TextColor
        )
        if (partner.attentionOrderCount > 0) {
            StockCardInfoLine(
                localizedStringResource(2364, "Needs a reply"),
                partner.attentionOrderCount.toString(),
                stateValues.BorderlineBadColor
            )
        }
        if (partner.readyToPackOrderCount > 0) {
            StockCardInfoLine(
                localizedStringResource(1689, "Ready to pack"),
                partner.readyToPackOrderCount.toString(),
                stateValues.OkayColor
            )
        }
        if (partner.packedOrderCount > 0) {
            StockCardInfoLine(
                localizedStringResource(1363, "Packed"),
                partner.packedOrderCount.toString(),
                stateValues.AccentColor
            )
        }
        if (partner.inDeliveryOrderCount > 0) {
            StockCardInfoLine(
                localizedStringResource(1560, "In delivery"),
                partner.inDeliveryOrderCount.toString(),
                stateValues.AccentColor
            )
        }
        if (partner.partiallyDeliveredOrderCount > 0) {
            StockCardInfoLine(
                localizedStringResource(972, "Partially delivered"),
                partner.partiallyDeliveredOrderCount.toString(),
                stateValues.AccentColor
            )
        }
        if (partner.overdueOrderCount > 0) {
            StockCardInfoLine(
                localizedStringResource(2394, "Delivery overdue"),
                partner.overdueOrderCount.toString(),
                stateValues.BorderlineBadColor
            )
        }
        if (partner.issueOrderCount > 0) {
            StockCardInfoLine(
                localizedStringResource(1473, "Issues"),
                partner.issueOrderCount.toString(),
                stateValues.ErrorColor
            )
        }
        if (partner.openOrderCount == 0 && partner.issueOrderCount == 0) {
            Text(
                text = localizedStringResource(2382, "No open work"),
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize
            )
        }
    }
}

@Composable
private fun AppConfiguration.SupplierCustomerCommercialCard(partner: SupplierPartnerUiModel) {
    SupplierCustomerSectionCard(
        title = localizedStringResource(2376, "Commercial relationship"),
        iconPath = stateValues.drawablePathIconFinances,
        iconRes = stateValues.drawableResIconFinances.value
    ) {
        StockCardInfoLine(localizedStringResource(2312, "Products"), partner.connectedProductCount.toString(), stateValues.TextColor)
        StockCardInfoLine(localizedStringResource(2315, "Saved offers"), partner.savedOfferCount.toString(), stateValues.TextColor)
        if (partner.priceGapCount > 0) {
            StockCardInfoLine(localizedStringResource(2317, "Needs a price"), partner.priceGapCount.toString(), stateValues.BorderlineBadColor)
        }
        StockCardInfoLine(localizedStringResource(1555, "Active contracts"), partner.activeContractCount.toString(), stateValues.TextColor)
        if (partner.pendingSupplierContractCount > 0) {
            StockCardInfoLine(localizedStringResource(1526, "Waiting for your acceptance"), partner.pendingSupplierContractCount.toString(), stateValues.BorderlineBadColor)
        }
        if (partner.pendingStoreContractCount > 0) {
            StockCardInfoLine(localizedStringResource(1527, "Waiting for the other side"), partner.pendingStoreContractCount.toString(), stateValues.TextColor)
        }
    }
}

@Composable
private fun AppConfiguration.SupplierCustomerRecentOrdersCard(
    partner: SupplierPartnerUiModel,
    linesByOrder: Map<String, List<SupplierOrderLineDataModel>>
) {
    SupplierCustomerSectionCard(
        title = localizedStringResource(2377, "Order activity"),
        iconPath = stateValues.drawablePathIconAppModeSupplier,
        iconRes = stateValues.drawableResIconAppModeSupplier.value
    ) {
        if (!partner.orderDetailsLoaded && partner.orderCount > 0) {
            Text(
                text = localizedStringResource(1141, "Please wait…"),
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize
            )
        } else if (partner.orders.isEmpty()) {
            Text(
                text = localizedStringResource(2390, "No recent orders"),
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize
            )
        } else {
            partner.orders.take(4).forEach { order ->
                SupplierCustomerOrderRow(order, linesByOrder[order.id].orEmpty())
            }
        }
    }
}

@Composable
private fun AppConfiguration.SupplierCustomerOrderRow(
    order: SupplierOrderDataModel,
    lines: List<SupplierOrderLineDataModel>
) {
    val statusColor = when (order.status) {
        SupplierOrderStatusDataModel.IssueReported -> stateValues.ErrorColor
        SupplierOrderStatusDataModel.Cancelled -> stateValues.DisabledColor
        SupplierOrderStatusDataModel.Delivered,
        SupplierOrderStatusDataModel.PartiallyDelivered -> stateValues.OkayColor
        else -> stateValues.AccentColor
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor.softAppBackgroundColor())
            .padding(stateValues.marginTextField),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(statusColor)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = supplierOrderStatusTitle(order.status),
                color = stateValues.TextColor,
                fontSize = stateValues.textSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOfNotNull(
                    order.id.take(8).takeIf { it.isNotBlank() },
                    order.supplierDeskSortTime().takeIf { it > 0L }?.let { receiptUiDateTime(it) },
                    lines.size.takeIf { it > 0 }?.let { "${localizedStringResource(392, "Items")}: $it" }
                ).joinToString(" • "),
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        order.amount.supplierDeskMoneyText().takeIf { it.isNotBlank() }?.let { amount ->
            Text(
                text = amount,
                color = stateValues.TextColor,
                fontSize = stateValues.smallTextSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AppConfiguration.SupplierCustomerContractsCard(partner: SupplierPartnerUiModel) {
    val visibleContracts = partner.contracts
        .filter { it.status != SUPPLIER_CONTRACT_STATUS_ARCHIVED }
        .take(4)
    SupplierCustomerSectionCard(
        title = localizedStringResource(1479, "Supplier contracts"),
        iconPath = stateValues.drawablePathIconSupplierContracts,
        iconRes = stateValues.drawableResIconSupplierContracts.value
    ) {
        if (!partner.contractDetailsLoaded && partner.hasContractRelationship) {
            Text(
                text = localizedStringResource(1141, "Please wait…"),
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize
            )
        } else if (visibleContracts.isEmpty()) {
            Text(
                text = localizedStringResource(2384, "No contract yet"),
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize
            )
        } else {
            visibleContracts.forEach { contract ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(stateValues.cornerRadius))
                        .background(stateValues.BackgroundColor.softAppBackgroundColor())
                        .padding(stateValues.marginTextField),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = supplierVisibleContractTitle(contract),
                            color = stateValues.TextColor,
                            fontSize = stateValues.textSize,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = listOf(
                                supplierContractScopeTitle(contract.scopeType),
                                supplierContractStatusTitle(contract.status)
                            ).joinToString(" • "),
                            color = stateValues.PlaceholderTextColor,
                            fontSize = stateValues.smallTextSize,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    contract.updatedAtMillis.takeIf { it > 0L }?.let { updated ->
                        Text(
                            text = receiptUiDateTime(updated),
                            color = stateValues.PlaceholderTextColor,
                            fontSize = stateValues.smallTextSize,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppConfiguration.SupplierCustomerOffersCard(partner: SupplierPartnerUiModel) {
    SupplierCustomerSectionCard(
        title = localizedStringResource(2315, "Saved offers"),
        iconPath = stateValues.drawablePathIconSupplierCatalog,
        iconRes = stateValues.drawableResIconSupplierCatalog.value
    ) {
        if (!partner.priceDetailsLoaded && partner.savedOfferCount > 0) {
            Text(
                text = localizedStringResource(1141, "Please wait…"),
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize
            )
        } else if (partner.prices.isEmpty()) {
            Text(
                text = localizedStringResource(2391, "No saved offers yet"),
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize
            )
        } else {
            partner.prices.take(4).forEach { price ->
                val title = price.supplierGoodsName.orEmpty()
                    .ifBlank { price.supplierBarcode.orEmpty() }
                    .ifBlank { price.goodsItemId.take(12) }
                val validPrice = price.supplyPrice.hasPositiveSupplierDeskPrice()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(stateValues.cornerRadius))
                        .background(stateValues.BackgroundColor.softAppBackgroundColor())
                        .padding(stateValues.marginTextField),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            color = stateValues.TextColor,
                            fontSize = stateValues.textSize,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = listOfNotNull(
                                price.minOrderQuantity?.quantityText(stateValues.appLanguage)?.takeIf { it.isNotBlank() },
                                price.packageQuantity?.quantityText(stateValues.appLanguage)?.takeIf { it.isNotBlank() }
                            ).joinToString(" • ").ifBlank { price.goodsItemId.take(12) },
                            color = stateValues.PlaceholderTextColor,
                            fontSize = stateValues.smallTextSize,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = if (validPrice) {
                            price.supplyPrice.supplierDeskMoneyText()
                        } else {
                            localizedStringResource(2337, "Price missing")
                        },
                        color = if (validPrice) stateValues.OkayColor else stateValues.BorderlineBadColor,
                        fontSize = stateValues.smallTextSize,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun AppConfiguration.SupplierCustomerSectionCard(
    title: String,
    iconPath: String,
    iconRes: DrawableResource,
    content: @Composable () -> Unit
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
            CpImage(
                modifier = Modifier.size(22.dp),
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
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        content()
    }
}

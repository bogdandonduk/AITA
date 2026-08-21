package kz.aita

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.align
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


internal fun supplierOrderNeedsAttentionInInbox(
    status: SupplierOrderStatusDataModel,
    hasResponseGaps: Boolean
): Boolean = status.needsSupplierActionForSupplierDesk(hasResponseGaps)

@Composable
internal fun AppConfiguration.SupplierOrdersInboxScreen() {
    val orders by supplierOrdersState.payload.collectAsState()
    val lines by supplierOrderLinesState.payload.collectAsState()
    val supplierPrices by supplierGoodsPricesState.payload.collectAsState()
    val supplierDashboard by supplierModeDashboardState.payload.collectAsState()
    val activeSupplierProfileId by activeSupplierProfileIdState.collectAsState()
    val supplierOrderNavigationState by NavigationScreenModel.Supplier.Orders.Main.state.collectAsState()

    val supplierOrderSearchSeed = supplierOrderNavigationState[NavigationScreenModel.KEY_STATE_SEARCH_QUERY].orEmpty()
    val supplierOrderDueFilterSeed = supplierOrderNavigationState[SUPPLIER_ORDER_DUE_FILTER_STATE_KEY].orEmpty()
    val supplierOrderStatusFilterSeed = supplierOrderNavigationState[SUPPLIER_ORDER_STATUS_FILTER_STATE_KEY].orEmpty()

    var searchQuery by rememberSaveable(supplierOrderSearchSeed) { mutableStateOf(supplierOrderSearchSeed) }
    var statusFilter by rememberSaveable(supplierOrderSearchSeed, supplierOrderStatusFilterSeed) {
        mutableStateOf(supplierOrderStatusFilterSeed.ifBlank { if (supplierOrderSearchSeed.isBlank()) "open" else "all" })
    }
    var dueFilter by rememberSaveable(supplierOrderDueFilterSeed) {
        mutableStateOf(supplierOrderDueFilterSeed.ifBlank { "all" })
    }
    var filtersExpanded by rememberSaveable { mutableStateOf(false) }
    var expandedOrderId by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val localProfiles = stateValues.suppliers.orEmpty().supplierProfilesOwnedBy(stateValues.userAccount?.id)
    val focusedSupplierId = resolveSupplierProfileFocus(activeSupplierProfileId, localProfiles)
    val identityPresentation = buildSupplierIdentityPresentation(
        localProfiles = localProfiles,
        dashboard = supplierDashboard,
        activeSupplierId = focusedSupplierId,
        localProfilesLoaded = stateValues.suppliers != null
    )

    LaunchedEffect(stateValues.userAccount?.id) {
        if (stateValues.userAccount != null) {
            refreshSupplierModeWorkspace(includeContracts = true)
        }
    }

    LaunchedEffect(focusedSupplierId) {
        // A detail opened under one legal identity must never survive into another identity.
        expandedOrderId = null
    }

    LaunchedEffect(searchQuery, statusFilter, dueFilter) {
        delay(250)
        seedSupplierOrdersInboxNavigation(
            searchQuery = searchQuery,
            dueFilter = dueFilter,
            statusFilter = statusFilter
        )
    }

    val activeOrders = remember(orders, focusedSupplierId) {
        orders.orEmpty()
            .supplierOrdersForIdentity(focusedSupplierId)
            .filter { it.isActive && it.status != SupplierOrderStatusDataModel.Draft }
            .sortedByDescending { it.supplierDeskSortTime() }
    }
    val focusedSupplierPrices = remember(supplierPrices, focusedSupplierId) {
        supplierPrices.orEmpty().supplierPricesForIdentity(focusedSupplierId)
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
    val filteredOrders = remember(
        activeOrders,
        linesByOrder,
        searchQuery,
        statusFilter,
        dueFilter,
        supplierDashboardNow,
        stateValues.appLanguage
    ) {
        val normalizedSearch = searchQuery.trim().lowercase()
        activeOrders.filter { order ->
            val orderLines = linesByOrder[order.id].orEmpty()
            val statusMatches = when (statusFilter) {
                "all" -> true
                "open" -> !order.status.isSupplierOrderClosed()
                "needs_attention" -> supplierOrderNeedsAttentionInInbox(
                    status = order.status,
                    hasResponseGaps = SupplierOrderWithLinesDataModel(order, orderLines)
                        .hasSupplierResponseGapsForSupplierDesk()
                )
                "answer_gaps" -> !order.status.isSupplierOrderClosed() &&
                        SupplierOrderWithLinesDataModel(order, orderLines).hasSupplierResponseGapsForSupplierDesk()
                "ready_to_pack" -> order.isSupplierReadyToPackForSupplierDesk(orderLines)
                else -> order.status.name == statusFilter
            }
            val dueMatches = order.matchesSupplierDueFilter(dueFilter, supplierDashboardNow)
            val queryMatches = normalizedSearch.isBlank() ||
                    supplierDeskOrderSearchText(order, orderLines).contains(normalizedSearch)
            statusMatches && dueMatches && queryMatches
        }
    }

    val profileTitle = identityPresentation.title
    val profileSubtitle = identityPresentation.subtitle
    val hasSupplierProfile = identityPresentation.profileCount > 0

    val openCount = activeOrders.count { !it.status.isSupplierOrderClosed() }
    val attentionCount = activeOrders.count { order ->
        val orderLines = linesByOrder[order.id].orEmpty()
        supplierOrderNeedsAttentionInInbox(
            status = order.status,
            hasResponseGaps = SupplierOrderWithLinesDataModel(order, orderLines)
                .hasSupplierResponseGapsForSupplierDesk()
        )
    }
    val readyToPackCount = activeOrders.count { order ->
        order.isSupplierReadyToPackForSupplierDesk(linesByOrder[order.id].orEmpty())
    }
    val inDeliveryCount = activeOrders.count { it.status == SupplierOrderStatusDataModel.InDelivery }

    val metrics = listOf(
        SupplierOrdersMetricUiModel(
            filterId = "open",
            title = localizedStringResource(1369, "Open orders"),
            value = openCount,
            iconPath = stateValues.drawablePathIconAppModeSupplier,
            iconRes = stateValues.drawableResIconAppModeSupplier.value,
            selected = statusFilter == "open" && dueFilter == "all" && searchQuery.isBlank()
        ),
        SupplierOrdersMetricUiModel(
            filterId = "needs_attention",
            title = localizedStringResource(1371, "Needs attention"),
            value = attentionCount,
            iconPath = stateValues.drawablePathIconResponse,
            iconRes = stateValues.drawableResIconResponse.value,
            selected = statusFilter == "needs_attention" && dueFilter == "all" && searchQuery.isBlank(),
            attention = true
        ),
        SupplierOrdersMetricUiModel(
            filterId = "ready_to_pack",
            title = localizedStringResource(1689, "Ready to pack"),
            value = readyToPackCount,
            iconPath = stateValues.drawablePathIconStock,
            iconRes = stateValues.drawableResIconStock.value,
            selected = statusFilter == "ready_to_pack" && dueFilter == "all" && searchQuery.isBlank()
        ),
        SupplierOrdersMetricUiModel(
            filterId = SupplierOrderStatusDataModel.InDelivery.name,
            title = localizedStringResource(1560, "In delivery"),
            value = inDeliveryCount,
            iconPath = stateValues.drawablePathIconSupplierDispatch,
            iconRes = stateValues.drawableResIconSupplierDispatch.value,
            selected = statusFilter == SupplierOrderStatusDataModel.InDelivery.name &&
                    dueFilter == "all" && searchQuery.isBlank()
        )
    )

    val dueOptions = remember(supplierDashboard, stateValues.appLanguage) {
        supplierDueFilterOptionsFromDashboard(supplierDashboard)
    }
    val expandedOrder = expandedOrderId?.let { selectedId -> filteredOrders.firstOrNull { it.id == selectedId } }

    LaunchedEffect(expandedOrderId, filteredOrders.map { it.id }) {
        if (expandedOrderId != null && filteredOrders.none { it.id == expandedOrderId }) {
            expandedOrderId = null
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenAppBarWidget(
            title = localizedStringResource(254, "Orders"),
            iconPath = stateValues.drawablePathIconAppModeSupplier,
            iconRes = stateValues.drawableResIconAppModeSupplier.value
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.78f)
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = stateValues.marginTextField),
            verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField),
            contentPadding = PaddingValues(
                top = stateValues.marginTextField,
                bottom = stateValues.screenHeight / 5
            )
        ) {
            if (!hasSupplierProfile) {
                item {
                    SupplierProfileIdentityCard(
                        dashboard = supplierDashboard,
                        compact = true,
                        includeContractsOnRefresh = false
                    )
                }
            } else {
                item {
                    SupplierOrdersWorkspaceHeader(
                        profileTitle = profileTitle,
                        profileSubtitle = profileSubtitle,
                        onCreateProfile = {
                            coroutineScope.launch {
                                openSupplierProfileEditor(NavigationScreenModel.Supplier.Orders.Main.route)
                            }
                        },
                        onRefresh = {
                            refreshSupplierModeWorkspace(includeContracts = true, force = true)
                        },
                        identityPresentation = identityPresentation,
                        onIdentitySelected = { supplierId ->
                            expandedOrderId = null
                            coroutineScope.launch {
                                setActiveSupplierProfileId(supplierId)
                                postInAppNotification(
                                    localizedStringResource(2489, "Supplier identity changed"),
                                    NotificationType.Positive,
                                    transient = true
                                )
                            }
                        }
                    )
                }

                item {
                    SupplierOrdersMetrics(
                        metrics = metrics,
                        onSelected = { metric ->
                            statusFilter = metric.filterId
                            dueFilter = "all"
                            searchQuery = ""
                            expandedOrderId = null
                        }
                    )
                }

                item {
                    SupplierOrdersWorkflowLinks()
                }

                item {
                    SupplierOrdersFilterPanel(
                        searchQuery = searchQuery,
                        statusFilter = statusFilter,
                        dueFilter = dueFilter,
                        dueOptions = dueOptions,
                        filteredCount = filteredOrders.size,
                        totalCount = activeOrders.size,
                        expanded = filtersExpanded,
                        onSearchChanged = {
                            searchQuery = it
                            expandedOrderId = null
                        },
                        onStatusChanged = {
                            statusFilter = it.ifBlank { "open" }
                            expandedOrderId = null
                        },
                        onDueChanged = {
                            dueFilter = it.ifBlank { "all" }
                            expandedOrderId = null
                        },
                        onExpandedChanged = { filtersExpanded = it }
                    )
                }

                if (activeOrders.isEmpty()) {
                    item {
                        MessageText(
                            modifier = Modifier.fillMaxWidth(),
                            text = localizedStringResource(1379, "No store orders have reached this supplier profile yet. When stores send supply requests, they will appear here.")
                        )
                    }
                } else if (filteredOrders.isEmpty()) {
                    item {
                        MessageText(
                            modifier = Modifier.fillMaxWidth(),
                            text = localizedStringResource(1380, "No orders match this filter")
                        )
                    }
                } else if (expandedOrder != null) {
                    item(key = "supplier-order-back-${expandedOrder.id}") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                        ) {
                            actionButton(
                                modifier = Modifier.weight(1f),
                                text = localizedStringResource(254, "Orders"),
                                iconPath = stateValues.drawablePathIconBackArrow,
                                iconRes = stateValues.drawableResIconBackArrow.value,
                                confirmationRequired = false,
                                autoLoading = false,
                                onClick = { expandedOrderId = null }
                            )
                            actionButton(
                                modifier = Modifier.size(48.dp),
                                fillMaxWidthIfTextPresent = false,
                                text = "",
                                iconPath = stateValues.drawablePathIconSupplierPartners,
                                iconRes = stateValues.drawableResIconSupplierPartners.value,
                                iconContentDescription = localizedStringResource(2375, "Partner overview"),
                                confirmationRequired = false,
                                autoLoading = false,
                                onClick = {
                                    coroutineScope.launch {
                                        seedSupplierCustomersNavigation(
                                            searchQuery = expandedOrder.storeId
                                                .trim()
                                                .ifBlank { expandedOrder.storePublicIdSnapshot.trim() }
                                                .ifBlank { supplierDeskStoreTitle(expandedOrder) }
                                        )
                                        Navigation.goMain(NavigationScreenModel.Supplier.Customers.Main)
                                    }
                                }
                            )
                        }
                    }
                    item(key = "supplier-order-detail-${expandedOrder.id}") {
                        SupplierOrderDeskCard(
                            order = expandedOrder,
                            lines = linesByOrder[expandedOrder.id].orEmpty(),
                            substituteOptions = substituteOptionsByStore[expandedOrder.storeId].orEmpty(),
                            supplierPriceRows = focusedSupplierPrices,
                            supplierIdentityTitle = if (identityPresentation.combined) {
                                identityPresentation.titleForSupplierIdentity(expandedOrder.supplierId)
                            } else {
                                ""
                            }
                        )
                    }
                } else {
                    items(filteredOrders, key = { it.id }) { order ->
                        SupplierOrdersCompactCard(
                            order = order,
                            lines = linesByOrder[order.id].orEmpty(),
                            supplierIdentityTitle = if (identityPresentation.combined) {
                                identityPresentation.titleForSupplierIdentity(order.supplierId)
                            } else {
                                ""
                            },
                            onOpen = { expandedOrderId = order.id }
                        )
                    }
                }
            }
        }
    }
}

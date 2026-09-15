package kz.aita

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
            statusFilter = statusFilter,
            revealResults = false
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
    val activeLines = remember(lines, activeOrdersById) {
        lines.orEmpty().filter { line -> line.isActive && line.orderId in activeOrdersById }
    }
    val linesByOrder = remember(activeLines) { activeLines.groupBy { it.orderId } }
    val substituteOptionsByStore = remember(lines, activeOrdersById, stateValues.appLanguage) {
        lines.orEmpty()
            .filter { line -> line.isActive && line.orderId in activeOrdersById }
            .groupBy { line -> activeOrdersById[line.orderId]?.storeId.orEmpty() }
            .mapValues { (_, storeLines) -> buildSupplierSubstituteOptions(storeLines) }
    }
    // A dashboard timestamp records when the server snapshot was generated; it is not a clock.
    // Keep due filters and the promise board moving while AITA stays open across a date boundary.
    val supplierLiveNow = rememberSupplierLiveNow { getCurrentTimeMillis() }
    val deliveryPromiseBuckets = remember(
        activeOrders,
        activeLines,
        supplierDashboard?.deliveryBuckets,
        supplierLiveNow,
        orders,
        lines
    ) {
        if (orders != null && lines != null) {
            buildSupplierOrderPromiseBuckets(
                orders = activeOrders,
                lines = activeLines,
                nowMillis = supplierLiveNow,
                serverBuckets = supplierDashboard?.deliveryBuckets.orEmpty()
            )
        } else {
            supplierDashboard?.deliveryBuckets.orEmpty()
        }
    }
    val filteredOrders = remember(
        activeOrders,
        linesByOrder,
        searchQuery,
        statusFilter,
        dueFilter,
        supplierLiveNow,
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
            val dueMatches = order.matchesSupplierDueFilter(dueFilter, supplierLiveNow)
            val queryMatches = normalizedSearch.isBlank() ||
                    supplierDeskOrderSearchText(order, orderLines).contains(normalizedSearch)
            statusMatches && dueMatches && queryMatches
        }
    }
    val prioritizedFilteredOrders = remember(filteredOrders, supplierLiveNow) {
        buildSupplierPromiseWatch(
            values = filteredOrders,
            nowEpochMillis = supplierLiveNow,
            promisedAt = { it.supplierDueAtMillis() },
            isTerminal = { it.status.isSupplierOrderClosed() },
            stableKey = { it.id },
        ).prioritized
    }

    val profileTitle = identityPresentation.title
    val profileSubtitle = identityPresentation.subtitle
    val hasSupplierProfile = identityPresentation.profileCount > 0
    val orderDataPending = orders == null
    val detailedLinesLoaded = lines != null

    // Use the focused dashboard as a provisional summary until the matching detailed payload lands.
    // This avoids a row of misleading zeroes during refresh while still switching to line-aware local
    // calculations as soon as the authoritative order snapshot is complete.
    val openCount = if (orders != null) {
        activeOrders.count { !it.status.isSupplierOrderClosed() }
    } else {
        supplierDashboard?.openOrderCount ?: 0
    }
    val attentionCount = if (orders != null && detailedLinesLoaded) {
        activeOrders.count { order ->
            val orderLines = linesByOrder[order.id].orEmpty()
            supplierOrderNeedsAttentionInInbox(
                status = order.status,
                hasResponseGaps = SupplierOrderWithLinesDataModel(order, orderLines)
                    .hasSupplierResponseGapsForSupplierDesk()
            )
        }
    } else {
        supplierDashboard?.actionRequiredOrderCount ?: 0
    }
    val readyToPackCount = if (orders != null && detailedLinesLoaded) {
        activeOrders.count { order ->
            order.isSupplierReadyToPackForSupplierDesk(linesByOrder[order.id].orEmpty())
        }
    } else {
        supplierDashboard?.bulkPackableOrderIds?.size ?: 0
    }
    val inDeliveryCount = if (orders != null) {
        activeOrders.count { it.status == SupplierOrderStatusDataModel.InDelivery }
    } else {
        supplierDashboard?.inDeliveryOrderCount ?: 0
    }
    val supplierReadiness = supplierDashboard?.readiness?.takeIf {
        it.hasSupplierReadinessSignal()
    }

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

    AitaScreenColumn(
        modifier = Modifier.fillMaxSize(),
        appBar = {
            ScreenAppBarWidget(
                title = localizedStringResource(254, "Orders"),
                iconPath = stateValues.drawablePathIconAppModeSupplier,
                iconRes = stateValues.drawableResIconAppModeSupplier.value
            )
        }
    ) {
        val section = sectionTabsWidget(
            stateKey = "supplier-orders:${focusedSupplierId.orEmpty()}",
            tabs = listOf(
                TabContent("orders", localizedStringResource(254, "Orders")),
                TabContent("readiness", authUiText("Readiness", "Готовность", "Дайындық", "Даярдык")),
                TabContent("promises", authUiText("Delivery promises", "Сроки доставки", "Жеткізу мерзімдері", "Жеткирүү убадалары"))
            ),
            modifier = Modifier
                .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.78f)
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = stateValues.marginTextField, vertical = stateValues.marginTextField / 2),
            selectedId = supplierOrderNavigationState[SUPPLIER_WORKSPACE_SECTION_STATE_KEY] ?: "orders",
            onSelected = { NavigationScreenModel.Supplier.Orders.Main.setStateNow(SUPPLIER_WORKSPACE_SECTION_STATE_KEY to it) },
        )

        LazyColumn(
            state = rememberPersistentLazyListState(NavigationScreenModel.Supplier.Orders.Main, "sections:${focusedSupplierId.orEmpty()}:$section"),
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

                if (section == "orders") {
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
                }

                if (section == "readiness") {
                    if (supplierReadiness == null) {
                        item { MessageText(Modifier.fillMaxWidth(), authUiText("No readiness data yet", "Данные о готовности пока не загружены", "Дайындық деректері әлі жүктелмеген", "Даярдык боюнча маалымат азырынча жок")) }
                    }
                    supplierReadiness?.let { readiness ->
                        item(key = "supplier-order-readiness-board") {
                            SupplierReadinessBoardCard(
                                readiness = readiness,
                                onOpenAnswerGaps = {
                                    NavigationScreenModel.Supplier.Orders.Main.setStateNow(SUPPLIER_WORKSPACE_SECTION_STATE_KEY to "orders")
                                    statusFilter = "answer_gaps"
                                    dueFilter = "all"
                                    searchQuery = ""
                                    expandedOrderId = null
                                },
                                onOpenPackQueue = {
                                    NavigationScreenModel.Supplier.Orders.Main.setStateNow(SUPPLIER_WORKSPACE_SECTION_STATE_KEY to "orders")
                                    statusFilter = "ready_to_pack"
                                    dueFilter = "all"
                                    searchQuery = ""
                                    expandedOrderId = null
                                }
                            )
                        }
                    }
                }

                if (section == "readiness") {
                    item {
                        SupplierOrdersWorkflowLinks()
                    }
                }

                if (section == "promises") {
                    if (deliveryPromiseBuckets.isEmpty()) {
                        item { MessageText(Modifier.fillMaxWidth(), stateValues.stringListEmpty) }
                    } else {
                        item(key = "supplier-order-promise-radar") {
                            SupplierDeliveryPromiseRadarCard(
                                buckets = deliveryPromiseBuckets,
                                selectedBucketId = dueFilter,
                                onBucketSelected = { bucketId ->
                                    NavigationScreenModel.Supplier.Orders.Main.setStateNow(SUPPLIER_WORKSPACE_SECTION_STATE_KEY to "orders")
                                    dueFilter = bucketId.ifBlank { "all" }
                                    if (dueFilter != "all") statusFilter = "open"
                                    searchQuery = ""
                                    expandedOrderId = null
                                }
                            )
                        }
                    }
                }

                if (section == "orders") {
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

                    if (orderDataPending) {
                        item(key = "supplier-orders-loading") {
                            MessageText(
                                modifier = Modifier.fillMaxWidth(),
                                loadingLayout = LoadingLayout.SupplierOrder, text = localizedStringResource(1141, "Please wait…")
                            )
                        }
                    } else if (activeOrders.isEmpty()) {
                        item(key = "supplier-orders-empty") {
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
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                            ) {
                                actionButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    text = localizedStringResource(254, "Orders"),
                                    iconPath = stateValues.drawablePathIconBackArrow,
                                    iconRes = stateValues.drawableResIconBackArrow.value,
                                    confirmationRequired = false,
                                    autoLoading = false,
                                    onClick = { expandedOrderId = null }
                                )
                                actionButton(
                                    modifier = Modifier.size(48.dp),
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
                        items(prioritizedFilteredOrders, key = { it.id }) { order ->
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
}

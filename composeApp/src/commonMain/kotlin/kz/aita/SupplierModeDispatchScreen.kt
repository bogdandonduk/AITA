package kz.aita

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Supplier delivery operations. Server-planned runs and locally reconstructed uncovered orders are
 * merged into one non-duplicating board. The root stays compact; only one run opens at a time.
 */
@Composable
internal fun AppConfiguration.SupplierDispatchScreen() {
    val orders by supplierOrdersState.payload.collectAsState()
    val lines by supplierOrderLinesState.payload.collectAsState()
    val contracts by supplierPartnershipContractsState.payload.collectAsState()
    val supplierDashboard by supplierModeDashboardState.payload.collectAsState()
    val activeSupplierProfileId by activeSupplierProfileIdState.collectAsState()
    val navigationState by NavigationScreenModel.Supplier.Dispatch.Main.state.collectAsState()

    val searchSeed = navigationState[NavigationScreenModel.KEY_STATE_SEARCH_QUERY].orEmpty()
    val filterSeed = navigationState[SUPPLIER_DISPATCH_FILTER_STATE_KEY].orEmpty()
    val sortSeed = navigationState[SUPPLIER_DISPATCH_SORT_STATE_KEY].orEmpty()

    var searchQuery by rememberSaveable(searchSeed) { mutableStateOf(searchSeed) }
    var filterId by rememberSaveable(searchSeed, filterSeed) {
        mutableStateOf(normalizedSupplierDispatchFilter(filterSeed))
    }
    var sortId by rememberSaveable(sortSeed) {
        mutableStateOf(normalizedSupplierDispatchSort(sortSeed))
    }
    var filtersExpanded by rememberSaveable { mutableStateOf(false) }
    // Detail selection and mutation feedback are session-only. Restored filters are useful, but an
    // old delivery run may already have changed on another device.
    var selectedRunKey by remember { mutableStateOf<String?>(null) }
    var activeMutationKey by remember { mutableStateOf<String?>(null) }
    var feedbackMessage by remember { mutableStateOf<List<LocalizedStringDataModel>?>(null) }
    var feedbackType by remember { mutableStateOf<NotificationType?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val localProfiles = stateValues.suppliers.orEmpty()
        .supplierProfilesOwnedBy(stateValues.userAccount?.id)
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
        selectedRunKey = null
        activeMutationKey = null
        feedbackMessage = null
        feedbackType = null
    }

    // Orders, Partner stores, Catalogue, and Agreements can seed this workspace. Apply all three
    // navigation values together and close any detail that no longer represents the requested view.
    LaunchedEffect(searchSeed, filterSeed, sortSeed) {
        val seededFilter = normalizedSupplierDispatchFilter(filterSeed)
        val seededSort = normalizedSupplierDispatchSort(sortSeed)
        if (searchQuery != searchSeed || filterId != seededFilter || sortId != seededSort) {
            searchQuery = searchSeed
            filterId = seededFilter
            sortId = seededSort
            selectedRunKey = null
            feedbackMessage = null
            feedbackType = null
        }
    }

    LaunchedEffect(searchQuery, filterId, sortId) {
        delay(250L)
        seedSupplierDispatchNavigation(
            searchQuery = searchQuery,
            laneFilter = filterId,
            sortId = sortId
        )
    }

    // Orders and the server dashboard are the two authoritative sources for run membership. Lines
    // only enrich manifests, while contracts can finish loading independently with actions kept
    // disabled. Do not flash an empty workspace merely because one auxiliary payload arrived first.
    val focusedOrders = remember(orders, focusedSupplierId) {
        orders.orEmpty().supplierOrdersForIdentity(focusedSupplierId)
    }
    val focusedOrderIds = remember(focusedOrders) { focusedOrders.map { it.id }.toSet() }
    val focusedLines = remember(lines, focusedOrderIds) {
        lines.orEmpty().filter { it.orderId in focusedOrderIds }
    }
    val focusedContracts = remember(contracts, focusedSupplierId) {
        contracts.orEmpty().supplierContractsForIdentity(focusedSupplierId)
    }
    val focusedServerRuns = remember(supplierDashboard?.dispatchRuns, focusedSupplierId) {
        supplierDashboard?.dispatchRuns.orEmpty().filter {
            it.matchesSupplierProfileFocus(focusedSupplierId)
        }
    }
    val dataPending = orders == null && supplierDashboard == null
    val runs = remember(
        focusedOrders,
        focusedLines,
        focusedContracts,
        focusedServerRuns,
        stateValues.appLanguage
    ) {
        buildSupplierDispatchWorkspaceRuns(
            orders = focusedOrders,
            lines = focusedLines,
            contracts = focusedContracts,
            serverRuns = focusedServerRuns,
            ordersLoaded = orders != null,
            linesLoaded = lines != null,
            contractsLoaded = contracts != null
        )
    }
    val normalizedSearch = searchQuery.trim().lowercase()
    val filteredRuns = remember(runs, normalizedSearch, filterId, sortId) {
        runs
            .filter { run ->
                run.matchesSupplierDispatchFilter(filterId) &&
                        (normalizedSearch.isBlank() || run.searchKey.contains(normalizedSearch))
            }
            .sortedForSupplierDispatch(sortId)
    }
    val selectedRun = selectedRunKey?.let { key -> runs.firstOrNull { it.key == key } }

    LaunchedEffect(selectedRunKey, runs.map { it.key }) {
        if (selectedRunKey != null && selectedRun == null) {
            selectedRunKey = null
            feedbackMessage = null
            feedbackType = null
        }
    }

    val hasSupplierProfile = identityPresentation.profileCount > 0
    val profileTitle = identityPresentation.title
    val profileSubtitle = identityPresentation.subtitle

    // Metric counts are derived through the exact same order-ID predicates used by each filter.
    val metrics = listOf(
        SupplierOrdersMetricUiModel(
            filterId = SUPPLIER_DISPATCH_FILTER_READY_TO_PACK,
            title = localizedStringResource(1689, "Ready to pack"),
            value = runs.distinctOrderCountForSupplierDispatchFilter(SUPPLIER_DISPATCH_FILTER_READY_TO_PACK),
            iconPath = stateValues.drawablePathIconStock,
            iconRes = stateValues.drawableResIconStock.value,
            selected = filterId == SUPPLIER_DISPATCH_FILTER_READY_TO_PACK && searchQuery.isBlank()
        ),
        SupplierOrdersMetricUiModel(
            filterId = SUPPLIER_DISPATCH_FILTER_READY_TO_DISPATCH,
            title = localizedStringResource(2442, "Ready to dispatch"),
            value = runs.distinctOrderCountForSupplierDispatchFilter(SUPPLIER_DISPATCH_FILTER_READY_TO_DISPATCH),
            iconPath = stateValues.drawablePathIconSupplierDispatch,
            iconRes = stateValues.drawableResIconSupplierDispatch.value,
            selected = filterId == SUPPLIER_DISPATCH_FILTER_READY_TO_DISPATCH && searchQuery.isBlank()
        ),
        SupplierOrdersMetricUiModel(
            filterId = SUPPLIER_DISPATCH_FILTER_IN_DELIVERY,
            title = localizedStringResource(1560, "In delivery"),
            value = runs.distinctOrderCountForSupplierDispatchFilter(SUPPLIER_DISPATCH_FILTER_IN_DELIVERY),
            iconPath = stateValues.drawablePathIconSupplierDispatch,
            iconRes = stateValues.drawableResIconSupplierDispatch.value,
            selected = filterId == SUPPLIER_DISPATCH_FILTER_IN_DELIVERY && searchQuery.isBlank()
        ),
        SupplierOrdersMetricUiModel(
            filterId = SUPPLIER_DISPATCH_FILTER_ATTENTION,
            title = localizedStringResource(1371, "Needs attention"),
            value = runs.distinctOrderCountForSupplierDispatchFilter(SUPPLIER_DISPATCH_FILTER_ATTENTION),
            iconPath = stateValues.drawablePathIconResponse,
            iconRes = stateValues.drawableResIconResponse.value,
            selected = filterId == SUPPLIER_DISPATCH_FILTER_ATTENTION && searchQuery.isBlank(),
            attention = true
        )
    )

    fun runStatusMutation(
        run: SupplierDispatchWorkspaceRunUiModel,
        status: SupplierOrderStatusDataModel,
        orderIds: List<String>
    ) {
        if (activeMutationKey != null) return
        val mutationKey = "${run.key}:${status.name}"
        activeMutationKey = mutationKey
        feedbackMessage = null
        feedbackType = null
        coroutineScope.launch {
            try {
                val outcome = updateSupplierOrdersSupplierStatusByIdsAwait(
                    orderIds = orderIds,
                    status = status
                )
                val type = when {
                    outcome.negative -> NotificationType.Negative
                    outcome.partial -> NotificationType.Neutral
                    else -> NotificationType.Positive
                }
                feedbackMessage = outcome.message
                feedbackType = type
                postInAppNotification(
                    outcome.message,
                    type,
                    transient = !outcome.negative
                )
            } finally {
                activeMutationKey = null
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenAppBarWidget(
            title = selectedRun?.storeTitle ?: localizedStringResource(1556, "Dispatch"),
            iconPath = stateValues.drawablePathIconSupplierDispatch,
            iconRes = stateValues.drawableResIconSupplierDispatch.value,
            onBack = selectedRun?.let {
                {
                    selectedRunKey = null
                    feedbackMessage = null
                    feedbackType = null
                }
            }
        )

        val supplierPromiseNowEpochMillis1 = rememberSupplierLiveNow {
            getCurrentTimeMillis()
        }
        val supplierPromiseWatch1 = androidx.compose.runtime.remember(
            filteredRuns,
            supplierPromiseNowEpochMillis1,
        ) {
            buildSupplierPromiseWatch(
                values = filteredRuns,
                nowEpochMillis = supplierPromiseNowEpochMillis1,
                promisedAt = { it.earliestDueAtMillis },
                isTerminal = { false },
                stableKey = { it.key },
            )
        }
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.80f)
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = stateValues.marginTextField),
            verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField),
            contentPadding = PaddingValues(
                top = stateValues.marginTextField,
                bottom = stateValues.screenHeight / 5
            )
        ) {
            if (!hasSupplierProfile) {
                item(key = "supplier-profile-empty") {
                    SupplierProfileIdentityCard(
                        dashboard = supplierDashboard,
                        compact = true,
                        includeContractsOnRefresh = true
                    )
                }
            } else {
                item(key = "supplier-profile") {
                    SupplierOrdersWorkspaceHeader(
                        profileTitle = profileTitle,
                        profileSubtitle = profileSubtitle,
                        onCreateProfile = {
                            coroutineScope.launch {
                                openSupplierProfileEditor(NavigationScreenModel.Supplier.Dispatch.Main.route)
                            }
                        },
                        onRefresh = {
                            refreshSupplierModeWorkspace(includeContracts = true, force = true)
                        },
                        identityPresentation = identityPresentation,
                        onIdentitySelected = { supplierId ->
                            selectedRunKey = null
                            activeMutationKey = null
                            feedbackMessage = null
                            feedbackType = null
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

                if (selectedRun == null) {
                    item(key = "dispatch-metrics") {
                        SupplierOrdersMetrics(metrics = metrics) { metric ->
                            searchQuery = ""
                            filterId = metric.filterId
                            selectedRunKey = null
                            feedbackMessage = null
                            feedbackType = null
                        }
                    }

                    item(key = "dispatch-workflow-links") {
                        SupplierDispatchWorkflowLinks()
                    }

                    item(key = "dispatch-filters") {
                        SupplierDispatchFilterPanel(
                            searchQuery = searchQuery,
                            filterId = filterId,
                            sortId = sortId,
                            resultCount = filteredRuns.size,
                            totalCount = runs.size,
                            expanded = filtersExpanded,
                            onSearchChanged = {
                                searchQuery = it
                                selectedRunKey = null
                            },
                            onFilterChanged = {
                                filterId = normalizedSupplierDispatchFilter(it)
                                selectedRunKey = null
                            },
                            onSortChanged = {
                                sortId = normalizedSupplierDispatchSort(it)
                                selectedRunKey = null
                            },
                            onExpandedChanged = { filtersExpanded = it },
                            onClear = {
                                searchQuery = ""
                                filterId = SUPPLIER_DISPATCH_FILTER_ALL
                                sortId = SUPPLIER_DISPATCH_SORT_ACTION
                                selectedRunKey = null
                            }
                        )
                    }

                    when {
                        dataPending -> {
                            item(key = "dispatch-loading") {
                                MessageText(
                                    modifier = Modifier.fillMaxWidth(),
                                    text = localizedStringResource(1141, "Please wait…")
                                )
                            }
                        }

                        runs.isEmpty() -> {
                            item(key = "dispatch-empty") {
                                MessageText(
                                    modifier = Modifier.fillMaxWidth(),
                                    text = localizedStringResource(2448, "No delivery work yet"),
                                    subText = localizedStringResource(
                                        2449,
                                        "Confirmed, packed, and in-delivery orders will appear here as practical Store runs."
                                    ),
                                    subTextSize = stateValues.smallTextSize
                                )
                            }
                        }

                        filteredRuns.isEmpty() -> {
                            item(key = "dispatch-filtered-empty") {
                                MessageText(
                                    modifier = Modifier.fillMaxWidth(),
                                    text = localizedStringResource(2459, "No delivery run matches these filters")
                                )
                            }
                        }

                        else -> {
                            items(supplierPromiseWatch1.prioritized, key = { it.key }) { run ->
                                SupplierDispatchRunCompactCard(
                                    run = run,
                                    onOpen = {
                                        selectedRunKey = run.key
                                        feedbackMessage = null
                                        feedbackType = null
                                    }
                                )
                            }
                        }
                    }
                } else {
                    item(key = "dispatch-detail-${selectedRun.key}") {
                        SupplierDispatchRunDetail(
                            run = selectedRun,
                            mutationKey = activeMutationKey,
                            feedbackMessage = feedbackMessage,
                            feedbackType = feedbackType,
                            onMarkPacked = {
                                runStatusMutation(
                                    run = selectedRun,
                                    status = SupplierOrderStatusDataModel.Packed,
                                    orderIds = selectedRun.packableOrderIds
                                )
                            },
                            onStartDelivery = {
                                runStatusMutation(
                                    run = selectedRun,
                                    status = SupplierOrderStatusDataModel.InDelivery,
                                    orderIds = selectedRun.dispatchableOrderIds
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

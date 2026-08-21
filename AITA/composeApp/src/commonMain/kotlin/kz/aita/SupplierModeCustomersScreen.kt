package kz.aita

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.align
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Supplier-side Store relationships. A partner can originate from an order, a reusable offer, a
 * contract, or the server dashboard. The default view stays compact; only the selected relationship
 * opens into its operational detail.
 */
@Composable
internal fun AppConfiguration.SupplierCustomersScreen() {
    val orders by supplierOrdersState.payload.collectAsState()
    val lines by supplierOrderLinesState.payload.collectAsState()
    val contracts by supplierPartnershipContractsState.payload.collectAsState()
    val supplierPrices by supplierGoodsPricesState.payload.collectAsState()
    val supplierDashboard by supplierModeDashboardState.payload.collectAsState()
    val activeSupplierProfileId by activeSupplierProfileIdState.collectAsState()
    val navigationState by NavigationScreenModel.Supplier.Customers.Main.state.collectAsState()

    val searchSeed = navigationState[NavigationScreenModel.KEY_STATE_SEARCH_QUERY].orEmpty()
    val filterSeed = navigationState[SUPPLIER_CUSTOMERS_FILTER_STATE_KEY].orEmpty()
    val sortSeed = navigationState[SUPPLIER_CUSTOMERS_SORT_STATE_KEY].orEmpty()

    var searchQuery by rememberSaveable(searchSeed) { mutableStateOf(searchSeed) }
    var filterId by rememberSaveable(searchSeed, filterSeed) {
        mutableStateOf(filterSeed.ifBlank { SUPPLIER_CUSTOMERS_FILTER_ALL })
    }
    var sortId by rememberSaveable(sortSeed) {
        mutableStateOf(sortSeed.ifBlank { SUPPLIER_CUSTOMERS_SORT_ACTION })
    }
    var filtersExpanded by rememberSaveable { mutableStateOf(false) }
    // Detail selection is intentionally not process-restored. Restoring the filtered partner list is
    // useful; reopening an old detail after a restart is usually surprising.
    var selectedPartnerKey by remember { mutableStateOf<String?>(null) }
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
        selectedPartnerKey = null
    }

    // Other Supplier workspaces can seed a Store relationship into this workspace.
    LaunchedEffect(searchSeed, filterSeed, sortSeed) {
        val seededFilter = filterSeed.ifBlank { SUPPLIER_CUSTOMERS_FILTER_ALL }
        val seededSort = sortSeed.ifBlank { SUPPLIER_CUSTOMERS_SORT_ACTION }
        if (searchQuery != searchSeed || filterId != seededFilter || sortId != seededSort) {
            searchQuery = searchSeed
            filterId = seededFilter
            sortId = seededSort
            selectedPartnerKey = null
        }
    }

    LaunchedEffect(searchQuery, filterId, sortId) {
        delay(250L)
        seedSupplierCustomersNavigation(
            searchQuery = searchQuery,
            filterId = filterId,
            sortId = sortId
        )
    }

    val focusedOrders = remember(orders, focusedSupplierId) {
        orders.orEmpty().supplierOrdersForIdentity(focusedSupplierId)
    }
    val focusedOrderIds = remember(focusedOrders) { focusedOrders.map { it.id }.toSet() }
    val focusedLines = remember(lines, focusedOrderIds) {
        lines.orEmpty().filter { it.orderId in focusedOrderIds }
    }
    val focusedPrices = remember(supplierPrices, focusedSupplierId) {
        supplierPrices.orEmpty().supplierPricesForIdentity(focusedSupplierId)
    }
    val focusedContracts = remember(contracts, focusedSupplierId) {
        contracts.orEmpty().supplierContractsForIdentity(focusedSupplierId)
    }

    val relationshipDataPending = orders == null &&
            supplierPrices == null &&
            contracts == null &&
            supplierDashboard == null

    val partnerItems = remember(
        focusedOrders,
        focusedLines,
        focusedContracts,
        focusedPrices,
        supplierDashboard,
        stateValues.suppliers,
        stateValues.stores,
        stateValues.appLanguage
    ) {
        buildSupplierPartnerItems(
            orders = focusedOrders,
            lines = focusedLines,
            supplierPrices = focusedPrices,
            contracts = focusedContracts,
            dashboard = supplierDashboard
        )
    }

    val normalizedSearch = searchQuery.trim().lowercase()
    val filteredPartners = remember(partnerItems, normalizedSearch, filterId, sortId) {
        partnerItems
            .filter { partner ->
                partner.matchesSupplierCustomersFilter(filterId) &&
                        (normalizedSearch.isBlank() || partner.searchKey.contains(normalizedSearch))
            }
            .sortedForSupplierCustomers(sortId)
    }
    val selectedPartner = selectedPartnerKey?.let { key ->
        partnerItems.firstOrNull { it.partnerKey == key }
    }

    LaunchedEffect(selectedPartnerKey, partnerItems.map { it.partnerKey }) {
        if (selectedPartnerKey != null && selectedPartner == null) {
            selectedPartnerKey = null
        }
    }

    val hasSupplierProfile = identityPresentation.profileCount > 0
    val profileTitle = identityPresentation.title
    val profileSubtitle = identityPresentation.subtitle

    // Each metric uses exactly the same predicate as the filter it opens.
    val metrics = listOf(
        SupplierOrdersMetricUiModel(
            filterId = SUPPLIER_CUSTOMERS_FILTER_ALL,
            title = localizedStringResource(1453, "Partner stores"),
            value = partnerItems.size,
            iconPath = stateValues.drawablePathIconSupplierPartners,
            iconRes = stateValues.drawableResIconSupplierPartners.value,
            selected = filterId == SUPPLIER_CUSTOMERS_FILTER_ALL && searchQuery.isBlank()
        ),
        SupplierOrdersMetricUiModel(
            filterId = SUPPLIER_CUSTOMERS_FILTER_ACTIVE,
            title = localizedStringResource(1455, "Open work"),
            value = partnerItems.countForSupplierCustomersFilter(SUPPLIER_CUSTOMERS_FILTER_ACTIVE),
            iconPath = stateValues.drawablePathIconAppModeSupplier,
            iconRes = stateValues.drawableResIconAppModeSupplier.value,
            selected = filterId == SUPPLIER_CUSTOMERS_FILTER_ACTIVE && searchQuery.isBlank()
        ),
        SupplierOrdersMetricUiModel(
            filterId = SUPPLIER_CUSTOMERS_FILTER_ATTENTION,
            title = localizedStringResource(1371, "Needs attention"),
            value = partnerItems.countForSupplierCustomersFilter(SUPPLIER_CUSTOMERS_FILTER_ATTENTION),
            iconPath = stateValues.drawablePathIconResponse,
            iconRes = stateValues.drawableResIconResponse.value,
            selected = filterId == SUPPLIER_CUSTOMERS_FILTER_ATTENTION && searchQuery.isBlank(),
            attention = true
        ),
        SupplierOrdersMetricUiModel(
            filterId = SUPPLIER_CUSTOMERS_FILTER_DELIVERY,
            title = localizedStringResource(2396, "Packing & delivery"),
            value = partnerItems.countForSupplierCustomersFilter(SUPPLIER_CUSTOMERS_FILTER_DELIVERY),
            iconPath = stateValues.drawablePathIconSupplierDispatch,
            iconRes = stateValues.drawableResIconSupplierDispatch.value,
            selected = filterId == SUPPLIER_CUSTOMERS_FILTER_DELIVERY && searchQuery.isBlank()
        )
    )

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenAppBarWidget(
            title = selectedPartner?.title ?: localizedStringResource(1453, "Partner stores"),
            iconPath = stateValues.drawablePathIconSupplierPartners,
            iconRes = stateValues.drawableResIconSupplierPartners.value,
            onBack = selectedPartner?.let { { selectedPartnerKey = null } }
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
                                openSupplierProfileEditor(NavigationScreenModel.Supplier.Customers.Main.route)
                            }
                        },
                        onRefresh = {
                            refreshSupplierModeWorkspace(includeContracts = true, force = true)
                        },
                        identityPresentation = identityPresentation,
                        onIdentitySelected = { supplierId ->
                            selectedPartnerKey = null
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

                if (selectedPartner == null) {
                    item(key = "metrics") {
                        SupplierOrdersMetrics(metrics = metrics) { metric ->
                            searchQuery = ""
                            filterId = metric.filterId
                            selectedPartnerKey = null
                        }
                    }

                    item(key = "workflow-links") {
                        SupplierCustomersWorkflowLinks()
                    }

                    item(key = "filters") {
                        SupplierCustomersFilterPanel(
                            searchQuery = searchQuery,
                            filterId = filterId,
                            sortId = sortId,
                            resultCount = filteredPartners.size,
                            totalCount = partnerItems.size,
                            expanded = filtersExpanded,
                            onSearchChanged = {
                                searchQuery = it
                                selectedPartnerKey = null
                            },
                            onFilterChanged = {
                                filterId = it.ifBlank { SUPPLIER_CUSTOMERS_FILTER_ALL }
                                selectedPartnerKey = null
                            },
                            onSortChanged = {
                                sortId = it.ifBlank { SUPPLIER_CUSTOMERS_SORT_ACTION }
                                selectedPartnerKey = null
                            },
                            onExpandedChanged = { filtersExpanded = it },
                            onClear = {
                                searchQuery = ""
                                filterId = SUPPLIER_CUSTOMERS_FILTER_ALL
                                sortId = SUPPLIER_CUSTOMERS_SORT_ACTION
                                selectedPartnerKey = null
                            }
                        )
                    }

                    when {
                        relationshipDataPending -> {
                            item(key = "loading") {
                                MessageText(
                                    modifier = Modifier.fillMaxWidth(),
                                    text = localizedStringResource(1141, "Please wait…")
                                )
                            }
                        }

                        partnerItems.isEmpty() -> {
                            item(key = "empty") {
                                MessageText(
                                    modifier = Modifier.fillMaxWidth(),
                                    text = localizedStringResource(2388, "No store partners yet"),
                                    subText = localizedStringResource(
                                        2389,
                                        "A store appears here after an order, saved offer, or contract connects it to one of your supplier profiles."
                                    ),
                                    subTextSize = stateValues.smallTextSize
                                )
                            }
                        }

                        filteredPartners.isEmpty() -> {
                            item(key = "filtered-empty") {
                                MessageText(
                                    modifier = Modifier.fillMaxWidth(),
                                    text = localizedStringResource(
                                        2387,
                                        "No partner matches these filters"
                                    )
                                )
                            }
                        }

                        else -> {
                            items(filteredPartners, key = { it.partnerKey }) { partner ->
                                SupplierPartnerCompactCard(
                                    partner = partner,
                                    onOpen = { selectedPartnerKey = partner.partnerKey }
                                )
                            }
                        }
                    }
                } else {
                    item(key = "supplier-partner-detail-${selectedPartner.partnerKey}") {
                        SupplierPartnerDetail(partner = selectedPartner)
                    }
                }
            }
        }
    }
}

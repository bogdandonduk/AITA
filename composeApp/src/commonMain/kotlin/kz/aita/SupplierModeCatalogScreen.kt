package kz.aita

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@Composable
internal fun AppConfiguration.SupplierCatalogScreen() {
    val orders by supplierOrdersState.payload.collectAsState()
    val lines by supplierOrderLinesState.payload.collectAsState()
    val supplierPrices by supplierGoodsPricesState.payload.collectAsState()
    val supplierDashboard by supplierModeDashboardState.payload.collectAsState()
    val activeSupplierProfileId by activeSupplierProfileIdState.collectAsState()
    val catalogNavigationState by NavigationScreenModel.Supplier.Catalog.Main.state.collectAsState()

    val searchSeed = catalogNavigationState[NavigationScreenModel.KEY_STATE_SEARCH_QUERY].orEmpty()
    val filterSeed = catalogNavigationState[SUPPLIER_CATALOG_FILTER_STATE_KEY].orEmpty()
    val sortSeed = catalogNavigationState[SUPPLIER_CATALOG_SORT_STATE_KEY].orEmpty()

    var searchQuery by rememberSaveable(searchSeed) { mutableStateOf(searchSeed) }
    var filterId by rememberSaveable(searchSeed, filterSeed) {
        mutableStateOf(filterSeed.ifBlank { SUPPLIER_CATALOG_FILTER_ALL })
    }
    var sortId by rememberSaveable(sortSeed) {
        mutableStateOf(sortSeed.ifBlank { SUPPLIER_CATALOG_SORT_ACTION })
    }
    var filtersExpanded by rememberSaveable { mutableStateOf(false) }
    var selectedCatalogKey by remember { mutableStateOf<String?>(null) }
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
            // Catalogue needs profiles, orders, prices and dashboard data, but not contracts.
            refreshSupplierModeWorkspace(includeContracts = false)
        }
    }

    LaunchedEffect(focusedSupplierId) {
        selectedCatalogKey = null
    }

    LaunchedEffect(searchSeed, filterSeed, sortSeed) {
        val seededFilter = filterSeed.ifBlank { SUPPLIER_CATALOG_FILTER_ALL }
        val seededSort = sortSeed.ifBlank { SUPPLIER_CATALOG_SORT_ACTION }
        if (searchQuery != searchSeed || filterId != seededFilter || sortId != seededSort) {
            searchQuery = searchSeed
            filterId = seededFilter
            sortId = seededSort
            selectedCatalogKey = null
        }
    }

    val aitaLatestCatalogueOwner0 = rememberAitaLatestUiRequestOwner()
    LaunchedEffect(searchQuery, filterId, sortId) {        val aitaLatestCatalogueTicket0 = aitaLatestCatalogueOwner0.begin()

        delay(250L)
        if (aitaLatestCatalogueOwner0.owns(aitaLatestCatalogueTicket0)) seedSupplierCatalogNavigation(
            searchQuery = searchQuery,
            filterId = filterId,
            sortId = sortId
        )
    }

    val activeOrders = remember(orders, focusedSupplierId) {
        orders.orEmpty()
            .supplierOrdersForIdentity(focusedSupplierId)
            .filter { it.isActive && it.status != SupplierOrderStatusDataModel.Draft }
    }
    val activeOrderIds = remember(activeOrders) { activeOrders.map { it.id }.toSet() }
    val activeLines = remember(lines, activeOrderIds) {
        lines.orEmpty().filter { line ->
            line.isActive && line.orderId in activeOrderIds
        }
    }
    val activePrices = remember(supplierPrices, focusedSupplierId) {
        supplierPrices.orEmpty()
            .supplierPricesForIdentity(focusedSupplierId)
            .filter { it.isActive }
    }
    val catalogItems = remember(
        activeOrders,
        activeLines,
        activePrices,
        supplierDashboard,
        stateValues.suppliers,
        stateValues.userAccount?.id,
        stateValues.globalAppConfiguration.goodsItemsQuantityUnits,
        stateValues.appLanguage
    ) {
        buildSupplierCatalogItems(
            orders = activeOrders,
            lines = activeLines,
            supplierPrices = activePrices,
            dashboard = supplierDashboard
        )
    }
    val normalizedSearch = searchQuery.trim().lowercase()
    val filteredItems = remember(catalogItems, normalizedSearch, filterId, sortId) {
        catalogItems
            .filter { item ->
                item.matchesSupplierCatalogFilter(filterId) &&
                        (normalizedSearch.isBlank() || item.searchKey.contains(normalizedSearch))
            }
            .sortedForSupplierCatalog(sortId)
    }
    val selectedItem = selectedCatalogKey?.let { key ->
        catalogItems.firstOrNull { it.catalogKey == key }
    }

    LaunchedEffect(selectedCatalogKey, catalogItems.map { it.catalogKey }) {
        if (selectedCatalogKey != null && selectedItem == null) {
            selectedCatalogKey = null
        }
    }

    val hasSupplierProfile = identityPresentation.profileCount > 0
    val profileTitle = identityPresentation.title
    val profileSubtitle = identityPresentation.subtitle

    // Metric values intentionally use the same predicates as their filters, so the number on a
    // card always matches the number of product cards revealed after pressing it.
    val openDemandCount = catalogItems.countForSupplierCatalogFilter(SUPPLIER_CATALOG_FILTER_OPEN)
    val missingPriceCount = catalogItems.countForSupplierCatalogFilter(
        SUPPLIER_CATALOG_FILTER_MISSING_PRICE
    )
    val priceBookProductCount = catalogItems.countForSupplierCatalogFilter(
        SUPPLIER_CATALOG_FILTER_PRICE_BOOK
    )
    val metrics = listOf(
        SupplierOrdersMetricUiModel(
            filterId = SUPPLIER_CATALOG_FILTER_ALL,
            title = localizedStringResource(2312, "Products"),
            value = catalogItems.size,
            iconPath = stateValues.drawablePathIconSupplierCatalog,
            iconRes = stateValues.drawableResIconSupplierCatalog.value,
            selected = filterId == SUPPLIER_CATALOG_FILTER_ALL &&
                    searchQuery.isBlank()
        ),
        SupplierOrdersMetricUiModel(
            filterId = SUPPLIER_CATALOG_FILTER_OPEN,
            title = localizedStringResource(2313, "Open demand"),
            value = openDemandCount,
            iconPath = stateValues.drawablePathIconAppModeSupplier,
            iconRes = stateValues.drawableResIconAppModeSupplier.value,
            selected = filterId == SUPPLIER_CATALOG_FILTER_OPEN &&
                    searchQuery.isBlank()
        ),
        SupplierOrdersMetricUiModel(
            filterId = SUPPLIER_CATALOG_FILTER_MISSING_PRICE,
            title = localizedStringResource(2317, "Needs a price"),
            value = missingPriceCount,
            iconPath = stateValues.drawablePathIconResponse,
            iconRes = stateValues.drawableResIconResponse.value,
            selected = filterId == SUPPLIER_CATALOG_FILTER_MISSING_PRICE &&
                    searchQuery.isBlank(),
            attention = true
        ),
        SupplierOrdersMetricUiModel(
            filterId = SUPPLIER_CATALOG_FILTER_PRICE_BOOK,
            title = localizedStringResource(2319, "Price book"),
            value = priceBookProductCount,
            iconPath = stateValues.drawablePathIconFinances,
            iconRes = stateValues.drawableResIconFinances.value,
            selected = filterId == SUPPLIER_CATALOG_FILTER_PRICE_BOOK &&
                    searchQuery.isBlank()
        )
    )

    AitaScreenColumn(
        modifier = Modifier.fillMaxSize(),
        appBar = {
            ScreenAppBarWidget(
                title = localizedStringResource(1338, "Catalog"),
                iconPath = stateValues.drawablePathIconSupplierCatalog,
                iconRes = stateValues.drawableResIconSupplierCatalog.value
            )
        }
    ) {
        val emptySpaceListState = androidx.compose.foundation.lazy.rememberLazyListState()
        LazyColumn(
            state = emptySpaceListState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.78f)
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = stateValues.marginTextField),
            verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField),
            contentPadding = PaddingValues(
                top = stateValues.marginTextField,
                bottom = stateValues.marginTextField
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
                                openSupplierProfileEditor(NavigationScreenModel.Supplier.Catalog.Main.route)
                            }
                        },
                        onRefresh = {
                            refreshSupplierModeWorkspace(
                                includeContracts = false,
                                force = true
                            )
                        },
                        identityPresentation = identityPresentation,
                        onIdentitySelected = { supplierId ->
                            selectedCatalogKey = null
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

                if (selectedItem == null) {
                    item {
                        SupplierOrdersMetrics(
                            metrics = metrics,
                            onSelected = { metric ->
                                filterId = metric.filterId
                                searchQuery = ""
                                selectedCatalogKey = null
                            }
                        )
                    }

                    item {
                        SupplierCatalogWorkflowLinks()
                    }

                    item {
                        SupplierCatalogFilterPanel(
                            searchQuery = searchQuery,
                            filterId = filterId,
                            sortId = sortId,
                            resultCount = filteredItems.size,
                            totalCount = catalogItems.size,
                            expanded = filtersExpanded,
                            onSearchChanged = {
                                searchQuery = it
                                selectedCatalogKey = null
                            },
                            onFilterChanged = {
                                filterId = it.ifBlank { SUPPLIER_CATALOG_FILTER_ALL }
                                selectedCatalogKey = null
                            },
                            onSortChanged = {
                                sortId = it.ifBlank { SUPPLIER_CATALOG_SORT_ACTION }
                                selectedCatalogKey = null
                            },
                            onExpandedChanged = { filtersExpanded = it },
                            onClear = {
                                searchQuery = ""
                                filterId = SUPPLIER_CATALOG_FILTER_ALL
                                sortId = SUPPLIER_CATALOG_SORT_ACTION
                                selectedCatalogKey = null
                            }
                        )
                    }

                    when {
                        catalogItems.isEmpty() -> {
                            item(key = "supplier-catalog-empty") {
                                MessageText(
                                    modifier = Modifier.fillMaxWidth().remainingListSpace(emptySpaceListState, "supplier-catalog-empty"),
                                    text = localizedStringResource(1418, "No catalog items yet"),
                                    subText = localizedStringResource(
                                        1419,
                                        "The catalog will grow automatically from incoming store orders. When a store requests a stock item, it becomes a reusable supplier offer card here."
                                    ),
                                    subTextSize = stateValues.smallTextSize
                                )
                            }
                        }

                        filteredItems.isEmpty() -> {
                            item(key = "supplier-catalog-filtered-empty") {
                                MessageText(
                                    modifier = Modifier.fillMaxWidth().remainingListSpace(emptySpaceListState, "supplier-catalog-filtered-empty"),
                                    text = localizedStringResource(
                                        1420,
                                        "No catalog items match this filter"
                                    )
                                )
                            }
                        }

                        else -> {
                            items(
                                items = filteredItems,
                                key = { it.catalogKey }
                            ) { item ->
                                SupplierCatalogCompactCard(
                                    item = item,
                                    onOpen = { selectedCatalogKey = item.catalogKey }
                                )
                            }
                        }
                    }
                } else {
                    item(key = "supplier-catalog-back-${selectedItem.catalogKey}") {
                        actionButton(
                            modifier = Modifier.fillMaxWidth(),
                            text = localizedStringResource(1338, "Catalog"),
                            iconPath = stateValues.drawablePathIconBackArrow,
                            iconRes = stateValues.drawableResIconBackArrow.value,
                            confirmationRequired = false,
                            autoLoading = false,
                            onClick = { selectedCatalogKey = null }
                        )
                    }
                    item(key = "supplier-catalog-detail-${selectedItem.catalogKey}") {
                        SupplierCatalogProductDetail(item = selectedItem)
                    }
                }
            }
        }
    }
}

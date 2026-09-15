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

/**
 * Shared Store/Supplier agreement workspace. Supplier mode receives the full identity header and
 * cross-workspace shortcuts; Store mode embeds the same lifecycle-safe contract board inside its
 * Supplier tab without duplicating the agreement rules.
 */
@Composable
internal fun AppConfiguration.SupplierContractsBoardContent(
    modifier: Modifier = Modifier,
    actorSide: String,
    fixedStoreId: String? = null,
    showAppBar: Boolean = true
) {
    val coroutineScope = rememberCoroutineScope()
    val usesSupplierRootNavigation = actorSide == SUPPLIER_CONTRACT_SIDE_SUPPLIER &&
            fixedStoreId.isNullOrBlank()
    val cleanFixedStoreId = fixedStoreId.orEmpty().trim().lowercase()
    val hasRequiredStoreContext = actorSide != SUPPLIER_CONTRACT_SIDE_STORE ||
            cleanFixedStoreId.isNotBlank()
    val activeSupplierProfileId by activeSupplierProfileIdState.collectAsState()
    val allLocalProfiles = stateValues.suppliers.orEmpty()
        .supplierProfilesOwnedBy(stateValues.userAccount?.id)
    val focusedSupplierId = if (usesSupplierRootNavigation) {
        resolveSupplierProfileFocus(activeSupplierProfileId, allLocalProfiles)
    } else {
        null
    }

    fun refreshWorkspace(force: Boolean = false) {
        if (actorSide == SUPPLIER_CONTRACT_SIDE_STORE) {
            if (!hasRequiredStoreContext) return
            fixedStoreId?.trim()?.takeIf { it.isNotBlank() }?.let { storeId ->
                getSupplierOrders(storeId)
                getSupplierGoodsPrices(storeId)
                getSupplierContracts(storeId = storeId)
                if (force) getSuppliers()
            }
        } else {
            refreshSupplierModeWorkspace(includeContracts = true, force = force)
        }
    }

    LaunchedEffect(actorSide, fixedStoreId, stateValues.userAccount?.id) {
        if (stateValues.userAccount != null) refreshWorkspace()
    }

    val contracts by supplierPartnershipContractsState.payload.collectAsState()
    val orders by supplierOrdersState.payload.collectAsState()
    val lines by supplierOrderLinesState.payload.collectAsState()
    val supplierPrices by supplierGoodsPricesState.payload.collectAsState()
    val supplierDashboard by supplierModeDashboardState.payload.collectAsState()
    val navigationState by NavigationScreenModel.Supplier.Contracts.Main.state.collectAsState()

    val searchSeed = if (usesSupplierRootNavigation) {
        navigationState[NavigationScreenModel.KEY_STATE_SEARCH_QUERY].orEmpty()
    } else {
        ""
    }
    val filterSeed = if (usesSupplierRootNavigation) {
        navigationState[SUPPLIER_CONTRACT_STATUS_FILTER_STATE_KEY].orEmpty()
    } else {
        SUPPLIER_CONTRACT_FILTER_ALL
    }
    val sortSeed = if (usesSupplierRootNavigation) {
        navigationState[SUPPLIER_CONTRACT_SORT_STATE_KEY].orEmpty()
    } else {
        SUPPLIER_CONTRACT_SORT_ACTION
    }

    var searchQuery by rememberSaveable(searchSeed) { mutableStateOf(searchSeed) }
    var filterId by rememberSaveable(searchSeed, filterSeed, actorSide) {
        mutableStateOf(normalizedSupplierContractFilter(filterSeed, actorSide, searchSeed))
    }
    var sortId by rememberSaveable(sortSeed) {
        mutableStateOf(normalizedSupplierContractSort(sortSeed))
    }
    var filtersExpanded by rememberSaveable { mutableStateOf(false) }
    // Focused agreement and editor are intentionally session-only. Restoring list filters is useful;
    // reopening a stale proposal after process restoration is not.
    var selectedContractId by remember { mutableStateOf<String?>(null) }
    var editorOpen by remember { mutableStateOf(false) }
    var editingContractId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(focusedSupplierId, usesSupplierRootNavigation) {
        if (usesSupplierRootNavigation) {
            selectedContractId = null
            editorOpen = false
            editingContractId = null
        }
    }

    LaunchedEffect(searchSeed, filterSeed, sortSeed, usesSupplierRootNavigation, actorSide) {
        if (usesSupplierRootNavigation) {
            val normalizedFilter = normalizedSupplierContractFilter(filterSeed, actorSide, searchSeed)
            val normalizedSort = normalizedSupplierContractSort(sortSeed)
            if (
                searchQuery != searchSeed ||
                filterId != normalizedFilter ||
                sortId != normalizedSort
            ) {
                searchQuery = searchSeed
                filterId = normalizedFilter
                sortId = normalizedSort
                selectedContractId = null
                editorOpen = false
                editingContractId = null
            }
        }
    }

    LaunchedEffect(searchQuery, filterId, sortId, usesSupplierRootNavigation) {
        if (usesSupplierRootNavigation) {
            delay(250L)
            seedSupplierContractsNavigation(
                searchQuery = searchQuery,
                statusFilter = filterId,
                sortId = sortId
            )
        }
    }

    val suppliers = stateValues.suppliers.orEmpty()
    val suppliersForBoard = if (usesSupplierRootNavigation) {
        suppliers.supplierProfilesForIdentity(focusedSupplierId)
    } else {
        suppliers
    }
    val activeOrders = if (!hasRequiredStoreContext) {
        emptyList()
    } else {
        orders.orEmpty().filter { order ->
            val storeMatches = cleanFixedStoreId.isBlank() ||
                    order.storeId.trim().lowercase() == cleanFixedStoreId
            val supplierMatches = !usesSupplierRootNavigation ||
                    order.matchesSupplierProfileFocus(focusedSupplierId)
            storeMatches && supplierMatches
        }
    }
    val activeContracts = if (!hasRequiredStoreContext) {
        emptyList()
    } else {
        contracts.orEmpty().filter { contract ->
            val storeMatches = cleanFixedStoreId.isBlank() ||
                    contract.storeId.trim().lowercase() == cleanFixedStoreId
            val supplierMatches = !usesSupplierRootNavigation ||
                    contract.matchesSupplierProfileFocus(focusedSupplierId)
            contract.isActive && storeMatches && supplierMatches
        }
    }
    val activePrices = if (!hasRequiredStoreContext) {
        emptyList()
    } else {
        supplierPrices.orEmpty().filter { price ->
            val storeMatches = cleanFixedStoreId.isBlank() ||
                    price.storeId.trim().lowercase() == cleanFixedStoreId
            val supplierMatches = !usesSupplierRootNavigation ||
                    price.matchesSupplierProfileFocus(focusedSupplierId)
            price.isActive && storeMatches && supplierMatches
        }
    }
    val partners = remember(
        actorSide,
        fixedStoreId,
        activeOrders,
        activeContracts,
        activePrices,
        suppliersForBoard,
        supplierDashboard,
        stateValues.appLanguage
    ) {
        buildSupplierContractPartners(
            actorSide = actorSide,
            fixedStoreId = fixedStoreId,
            orders = activeOrders,
            contracts = activeContracts,
            prices = activePrices,
            suppliers = suppliersForBoard,
            dashboard = supplierDashboard.takeIf { actorSide == SUPPLIER_CONTRACT_SIDE_SUPPLIER }
        )
    }
    val activeOrderIds = remember(activeOrders) { activeOrders.map { it.id }.toSet() }
    val activeLines = remember(lines, activeOrderIds) {
        lines.orEmpty().filter { line -> line.orderId in activeOrderIds }
    }
    val goodsOptions = remember(
        activeOrders,
        activeLines,
        activePrices,
        activeContracts,
        stateValues.appLanguage
    ) {
        buildSupplierContractGoodsOptions(
            orders = activeOrders,
            lines = activeLines,
            prices = activePrices,
            contracts = activeContracts
        )
    }
    val workspaceItems = remember(
        activeContracts,
        partners,
        actorSide,
        fixedStoreId,
        stateValues.appLanguage,
        stateValues.suppliers
    ) {
        buildSupplierContractWorkspaceItems(
            contracts = activeContracts,
            partners = partners,
            actorSide = actorSide,
            fixedStoreId = fixedStoreId
        )
    }
    val normalizedSearch = searchQuery.trim().lowercase()
    val visibleItems = remember(workspaceItems, normalizedSearch, filterId, sortId) {
        workspaceItems
            .filter { item ->
                item.matchesSupplierContractFilter(filterId) &&
                        (normalizedSearch.isBlank() || item.searchKey.contains(normalizedSearch))
            }
            .sortedForSupplierContracts(sortId)
    }
    val selectedItem = selectedContractId?.let { id ->
        workspaceItems.firstOrNull { it.id == id }
    }
    val editingContract = editingContractId?.let { id ->
        activeContracts.firstOrNull { it.id == id }
    }

    LaunchedEffect(selectedContractId, workspaceItems.map { it.id }) {
        if (selectedContractId != null && selectedItem == null) {
            selectedContractId = null
        }
    }
    LaunchedEffect(editingContractId, activeContracts.map { it.id }) {
        if (editingContractId != null && editingContract == null) {
            editingContractId = null
            editorOpen = false
        }
    }

    val identityPresentation = buildSupplierIdentityPresentation(
        localProfiles = allLocalProfiles,
        dashboard = supplierDashboard,
        activeSupplierId = focusedSupplierId,
        localProfilesLoaded = stateValues.suppliers != null
    )
    val hasSupplierProfile = identityPresentation.profileCount > 0
    val profileTitle = identityPresentation.title
    val profileSubtitle = identityPresentation.subtitle

    val metrics = listOf(
        SupplierOrdersMetricUiModel(
            filterId = SUPPLIER_CONTRACT_FILTER_ALL,
            title = localizedStringResource(2398, "All agreements"),
            value = workspaceItems.size,
            iconPath = stateValues.drawablePathIconSupplierContracts,
            iconRes = stateValues.drawableResIconSupplierContracts.value,
            selected = filterId == SUPPLIER_CONTRACT_FILTER_ALL && searchQuery.isBlank()
        ),
        SupplierOrdersMetricUiModel(
            filterId = SUPPLIER_CONTRACT_FILTER_WAITING_ME,
            title = localizedStringResource(2400, "Waiting for me"),
            value = workspaceItems.countForSupplierContractFilter(SUPPLIER_CONTRACT_FILTER_WAITING_ME),
            iconPath = stateValues.drawablePathIconResponse,
            iconRes = stateValues.drawableResIconResponse.value,
            selected = filterId == SUPPLIER_CONTRACT_FILTER_WAITING_ME && searchQuery.isBlank(),
            attention = true
        ),
        SupplierOrdersMetricUiModel(
            filterId = SUPPLIER_CONTRACT_FILTER_ACTIVE,
            title = localizedStringResource(1488, "Active contract"),
            value = workspaceItems.countForSupplierContractFilter(SUPPLIER_CONTRACT_FILTER_ACTIVE),
            iconPath = stateValues.drawablePathIconCheck,
            iconRes = stateValues.drawableResIconCheck.value,
            selected = filterId == SUPPLIER_CONTRACT_FILTER_ACTIVE && searchQuery.isBlank()
        ),
        SupplierOrdersMetricUiModel(
            filterId = SUPPLIER_CONTRACT_FILTER_WAITING_OTHER,
            title = localizedStringResource(2401, "Waiting for partner"),
            value = workspaceItems.countForSupplierContractFilter(SUPPLIER_CONTRACT_FILTER_WAITING_OTHER),
            iconPath = stateValues.drawablePathIconSupplierPartners,
            iconRes = stateValues.drawableResIconSupplierPartners.value,
            selected = filterId == SUPPLIER_CONTRACT_FILTER_WAITING_OTHER && searchQuery.isBlank()
        )
    )

    val detailTitle = selectedItem?.let { supplierVisibleContractTitle(it.contract) }
    val appBarTitle = when {
        editorOpen && editingContract != null -> localizedStringResource(1495, "Counter / edit proposal")
        editorOpen -> localizedStringResource(1521, "New proposal")
        detailTitle != null -> detailTitle
        else -> localizedStringResource(1479, "Supplier contracts")
    }
    val closeFocusedContent: (() -> Unit)? = when {
        editorOpen -> {
            {
                editorOpen = false
                editingContractId = null
            }
        }
        selectedItem != null -> {
            { selectedContractId = null }
        }
        else -> null
    }

    AitaScreenColumn(
        modifier = modifier.fillMaxSize(),
        appBar = {
            if (showAppBar) {
                ScreenAppBarWidget(
                    title = appBarTitle,
                    iconPath = stateValues.drawablePathIconSupplierContracts,
                    iconRes = stateValues.drawableResIconSupplierContracts.value,
                    onBack = closeFocusedContent
                )
            }
        }
    ) {
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
            if (!showAppBar && closeFocusedContent != null) {
                item(key = "embedded-contract-back") {
                    actionButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = localizedStringResource(2438, "Back to agreements"),
                        iconPath = stateValues.drawablePathIconBackArrow,
                        iconRes = stateValues.drawableResIconBackArrow.value,
                        confirmationRequired = false,
                        autoLoading = false,
                        onClick = closeFocusedContent
                    )
                }
            }

            if (!hasRequiredStoreContext) {
                item(key = "supplier-contract-no-active-store") {
                    MessageText(
                        modifier = Modifier.fillMaxWidth(),
                        text = stateValues.stringNoActiveStore
                    )
                }
            } else if (
                actorSide == SUPPLIER_CONTRACT_SIDE_SUPPLIER &&
                usesSupplierRootNavigation &&
                !hasSupplierProfile
            ) {
                item(key = "supplier-contract-profile-empty") {
                    SupplierProfileIdentityCard(
                        dashboard = supplierDashboard,
                        compact = true,
                        includeContractsOnRefresh = true
                    )
                }
            } else {
                if (actorSide == SUPPLIER_CONTRACT_SIDE_SUPPLIER && usesSupplierRootNavigation) {
                    item(key = "supplier-contract-profile") {
                        SupplierOrdersWorkspaceHeader(
                            profileTitle = profileTitle,
                            profileSubtitle = profileSubtitle,
                            onCreateProfile = {
                            coroutineScope.launch {
                                openSupplierProfileEditor(NavigationScreenModel.Supplier.Contracts.Main.route)
                            }
                        },
                            onRefresh = { refreshWorkspace(force = true) },
                            identityPresentation = identityPresentation,
                            onIdentitySelected = { supplierId ->
                                selectedContractId = null
                                editorOpen = false
                                editingContractId = null
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
                }

                when {
                    editorOpen -> {
                        item(key = "supplier-contract-editor-${editingContract?.id ?: "new"}") {
                            SupplierContractEditorCard(
                                actorSide = actorSide,
                                existingContract = editingContract,
                                partners = partners,
                                goodsOptions = goodsOptions,
                                onClose = {
                                    editorOpen = false
                                    editingContractId = null
                                },
                                onSaved = { saved ->
                                    selectedContractId = saved.id
                                    editorOpen = false
                                    editingContractId = null
                                }
                            )
                        }
                    }

                    selectedItem != null -> {
                        item(key = "supplier-contract-detail-${selectedItem.id}") {
                            SupplierContractDetail(
                                item = selectedItem,
                                actorSide = actorSide,
                                onEdit = {
                                    editingContractId = selectedItem.id
                                    editorOpen = true
                                },
                                onArchived = {
                                    selectedContractId = null
                                    editorOpen = false
                                    editingContractId = null
                                }
                            )
                        }
                        if (actorSide == SUPPLIER_CONTRACT_SIDE_SUPPLIER) {
                            item(key = "supplier-contract-relationship-links-${selectedItem.id}") {
                                SupplierContractRelationshipLinks(selectedItem)
                            }
                        }
                    }

                    else -> {
                        item(key = "supplier-contract-metrics") {
                            SupplierOrdersMetrics(metrics = metrics) { metric ->
                                searchQuery = ""
                                filterId = metric.filterId
                                selectedContractId = null
                            }
                        }

                        if (actorSide == SUPPLIER_CONTRACT_SIDE_SUPPLIER) {
                            item(key = "supplier-contract-workflow-links") {
                                SupplierContractsWorkflowLinks()
                            }
                        }

                        item(key = "supplier-contract-create") {
                            if (stateValues.isNarrowScreen) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                                ) {
                                    actionButton(
                                        modifier = Modifier.fillMaxWidth(),
                                        text = localizedStringResource(1494, "Create contract"),
                                        iconPath = stateValues.drawablePathIconSupplierContracts,
                                        iconRes = stateValues.drawableResIconSupplierContracts.value,
                                        enabled = partners.isNotEmpty(),
                                        confirmationRequired = false,
                                        autoLoading = false,
                                        onDisabledClick = {
                                            postInAppNotification(
                                                localizedStringResource(
                                                    2407,
                                                    "Choose a partner before sending the proposal"
                                                ),
                                                NotificationType.Neutral,
                                                transient = true
                                            )
                                        },
                                        onClick = {
                                            editingContractId = null
                                            editorOpen = true
                                        }
                                    )
                                    if (actorSide == SUPPLIER_CONTRACT_SIDE_STORE) {
                                        actionButton(
                                            modifier = Modifier.fillMaxWidth(),
                                            text = localizedStringResource(237, "Refresh"),
                                            iconPath = stateValues.drawablePathIconRefresh,
                                            iconRes = stateValues.drawableResIconRefresh.value,
                                            confirmationRequired = false,
                                            autoLoading = false,
                                            onClick = { refreshWorkspace(force = true) }
                                        )
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                                ) {
                                    actionButton(
                                        modifier = Modifier.fillMaxWidth(),
                                        text = localizedStringResource(1494, "Create contract"),
                                        iconPath = stateValues.drawablePathIconSupplierContracts,
                                        iconRes = stateValues.drawableResIconSupplierContracts.value,
                                        enabled = partners.isNotEmpty(),
                                        confirmationRequired = false,
                                        autoLoading = false,
                                        onDisabledClick = {
                                            postInAppNotification(
                                                localizedStringResource(
                                                    2407,
                                                    "Choose a partner before sending the proposal"
                                                ),
                                                NotificationType.Neutral,
                                                transient = true
                                            )
                                        },
                                        onClick = {
                                            editingContractId = null
                                            editorOpen = true
                                        }
                                    )
                                    if (actorSide == SUPPLIER_CONTRACT_SIDE_STORE) {
                                        actionButton(
                                            modifier = Modifier.fillMaxWidth(),
                                            text = localizedStringResource(237, "Refresh"),
                                            iconPath = stateValues.drawablePathIconRefresh,
                                            iconRes = stateValues.drawableResIconRefresh.value,
                                            confirmationRequired = false,
                                            autoLoading = false,
                                            onClick = { refreshWorkspace(force = true) }
                                        )
                                    }
                                }
                            }
                        }

                        item(key = "supplier-contract-filters") {
                            SupplierContractsFilterPanel(
                                searchQuery = searchQuery,
                                filterId = filterId,
                                sortId = sortId,
                                resultCount = visibleItems.size,
                                totalCount = workspaceItems.size,
                                expanded = filtersExpanded,
                                onSearchChanged = {
                                    searchQuery = it
                                    selectedContractId = null
                                },
                                onFilterChanged = {
                                    filterId = normalizedSupplierContractFilter(it, actorSide, searchQuery)
                                    selectedContractId = null
                                },
                                onSortChanged = {
                                    sortId = normalizedSupplierContractSort(it)
                                    selectedContractId = null
                                },
                                onExpandedChanged = { filtersExpanded = it },
                                onClear = {
                                    searchQuery = ""
                                    filterId = SUPPLIER_CONTRACT_FILTER_ALL
                                    sortId = SUPPLIER_CONTRACT_SORT_ACTION
                                    selectedContractId = null
                                }
                            )
                        }

                        when {
                            contracts == null -> {
                                item(key = "supplier-contract-loading") {
                                    MessageText(
                                        modifier = Modifier.fillMaxWidth(),
                                        loadingLayout = LoadingLayout.SupplierContract, text = localizedStringResource(1141, "Please wait…")
                                    )
                                }
                            }

                            workspaceItems.isEmpty() -> {
                                item(key = "supplier-contract-empty") {
                                    MessageText(
                                        modifier = Modifier.fillMaxWidth(),
                                        text = localizedStringResource(1511, "No contracts yet"),
                                        subText = localizedStringResource(
                                            2439,
                                            "Create an agreement to keep accepted Store and Supplier terms in one shared workflow."
                                        ),
                                        subTextSize = stateValues.smallTextSize
                                    )
                                }
                            }

                            visibleItems.isEmpty() -> {
                                item(key = "supplier-contract-filtered-empty") {
                                    MessageText(
                                        modifier = Modifier.fillMaxWidth(),
                                        text = localizedStringResource(2440, "No agreement matches these filters")
                                    )
                                }
                            }

                            else -> {
                                items(visibleItems, key = { it.id }) { item ->
                                    SupplierContractCompactCard(
                                        item = item,
                                        actorSide = actorSide,
                                        onOpen = {
                                            selectedContractId = item.id
                                            editorOpen = false
                                            editingContractId = null
                                        }
                                    )
                                }
                            }
                        }
                    }
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

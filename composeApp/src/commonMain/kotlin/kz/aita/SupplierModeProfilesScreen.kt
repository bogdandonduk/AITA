package kz.aita

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch

internal suspend fun openSupplierProfileEditor(returnRoute: String?, supplierId: String? = null) {
    NavigationScreenModel.Supplier.Identity.Main.setStates(
        SUPPLIER_PROFILE_RETURN_ROUTE_STATE_KEY to returnRoute.orEmpty(),
        SUPPLIER_PROFILE_EDITOR_OPEN_STATE_KEY to "1",
        SUPPLIER_PROFILE_EDITOR_ID_STATE_KEY to supplierId.orEmpty(),
        SUPPLIER_PROFILE_EDITOR_SESSION_STATE_KEY to getCurrentTimeMillis().toString()
    )
    Navigation.goMain(NavigationScreenModel.Supplier.Identity.Main)
}

internal suspend fun openSupplierProfilesWorkspace(returnRoute: String?) {
    NavigationScreenModel.Supplier.Identity.Main.setStates(
        SUPPLIER_PROFILE_RETURN_ROUTE_STATE_KEY to returnRoute.orEmpty(),
        SUPPLIER_PROFILE_EDITOR_OPEN_STATE_KEY to "0",
        SUPPLIER_PROFILE_EDITOR_ID_STATE_KEY to "",
        SUPPLIER_PROFILE_EDITOR_SESSION_STATE_KEY to ""
    )
    Navigation.goMain(NavigationScreenModel.Supplier.Identity.Main)
}

@Composable
internal fun AppConfiguration.SupplierProfilesScreen() {
    val navigationState by NavigationScreenModel.Supplier.Identity.Main.state.collectAsState()
    val suppliers by suppliersState.payload.collectAsState()
    val sharedDashboard by supplierModeDashboardState.payload.collectAsState()
    val activeSupplierProfileId by activeSupplierProfileIdState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    val currentUserId = stateValues.userAccount?.id
    var profileDashboard by remember(currentUserId) { mutableStateOf<SupplierModeDashboardDataModel?>(null) }
    var profileUsageLoaded by remember(currentUserId) { mutableStateOf(false) }
    var profileUsageCheckFailed by remember(currentUserId) { mutableStateOf(false) }
    var deletingSupplierIds by remember(currentUserId) { mutableStateOf(emptySet<String>()) }
    val localProfiles = suppliers.orEmpty().supplierProfilesOwnedBy(currentUserId)
    val profileIdentityIds = localProfiles
        .mapNotNull { normalizeSupplierProfileIdentityId(it.id) }
        .sorted()
    val profileIdentitySignature = profileIdentityIds.joinToString("|")
    val usageDashboardProfileIds = profileDashboard
        ?.supplierProfiles
        .orEmpty()
        .mapNotNull { normalizeSupplierProfileIdentityId(it.supplierId) }
        .toSet()
    val profileUsageMissingLocalProfile = profileUsageLoaded &&
        profileIdentityIds.any { it !in usageDashboardProfileIds }
    val focusedSupplierId = resolveSupplierProfileFocus(activeSupplierProfileId, localProfiles)
    val identityPresentation = buildSupplierIdentityPresentation(
        localProfiles = localProfiles,
        dashboard = profileDashboard ?: sharedDashboard,
        activeSupplierId = focusedSupplierId,
        localProfilesLoaded = suppliers != null
    )
    val items = buildSupplierProfileWorkspaceItems(
        localProfiles = localProfiles,
        dashboard = profileDashboard,
        usageLoaded = profileUsageLoaded,
        usageCheckFailed = profileUsageCheckFailed,
        activeSupplierId = focusedSupplierId
    )
    val workspaceReadiness = buildSupplierWorkspaceReadiness(items, focusedSupplierId)

    val editorOpen = navigationState[SUPPLIER_PROFILE_EDITOR_OPEN_STATE_KEY] == "1"
    val editedSupplierId = navigationState[SUPPLIER_PROFILE_EDITOR_ID_STATE_KEY].orEmpty()
    val editorSessionKey = navigationState[SUPPLIER_PROFILE_EDITOR_SESSION_STATE_KEY].orEmpty()
    val editedSupplier = editedSupplierId.takeIf { it.isNotBlank() }?.let { id ->
        localProfiles.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }
    val returnRoute = navigationState[SUPPLIER_PROFILE_RETURN_ROUTE_STATE_KEY]

    val editorPhoneStateKey = supplierProfileEditorPhoneStateKey(
        editedSupplierId.takeIf { it.isNotBlank() },
        editorSessionKey
    )

    val refreshProfiles: (Boolean) -> Unit = { forceSupplierList ->
        if (forceSupplierList || suppliers == null) getSuppliers()
        profileUsageCheckFailed = false
        // Deletion eligibility is safety-sensitive and must never be inferred from the short-lived
        // Supplier dashboard freshness cache. Entering this management workspace performs one
        // authoritative all-identities usage read; ordinary Supplier screens keep their scoped cache.
        getSupplierModeDashboard(
            force = true,
            supplierId = null,
            publishToSharedState = false
        ) { result ->
            coroutineScope.launch {
                if (result is DataState.Success) {
                    profileDashboard = result.payload
                    profileUsageLoaded = true
                    profileUsageCheckFailed = false
                } else {
                    profileUsageLoaded = false
                    profileUsageCheckFailed = true
                }
            }
        }
    }

    LaunchedEffect(currentUserId) {
        if (!currentUserId.isNullOrBlank()) refreshProfiles(false)
    }

    LaunchedEffect(currentUserId, profileIdentitySignature, profileUsageMissingLocalProfile) {
        if (!currentUserId.isNullOrBlank() && profileUsageMissingLocalProfile) {
            // A newly visible profile from another device is the only structural change that needs
            // another usage read. The initial all-identities dashboard already contains profiles
            // even when its response wins the race against getSuppliers(), so do not request it twice.
            refreshProfiles(false)
        }
    }

    LaunchedEffect(editorOpen, editorSessionKey) {
        if (editorOpen && editorSessionKey.isBlank()) {
            NavigationScreenModel.Supplier.Identity.Main.setState(
                SUPPLIER_PROFILE_EDITOR_SESSION_STATE_KEY to getCurrentTimeMillis().toString()
            )
        }
    }

    LaunchedEffect(editorOpen, editedSupplierId, suppliers) {
        if (editorOpen && editedSupplierId.isNotBlank() && editedSupplier == null && suppliers != null) {
            NavigationScreenModel.Supplier.Identity.Main.removeState(editorPhoneStateKey)
            NavigationScreenModel.Supplier.Identity.Main.setStates(
                SUPPLIER_PROFILE_EDITOR_OPEN_STATE_KEY to "0",
                SUPPLIER_PROFILE_EDITOR_ID_STATE_KEY to "",
                SUPPLIER_PROFILE_EDITOR_SESSION_STATE_KEY to ""
            )
            postInAppNotification(
                localizedStringResource(2513, "That supplier profile is no longer available."),
                NotificationType.Neutral,
                transient = true
            )
        }
    }

    val openReadinessNextStep: () -> Unit = {
        coroutineScope.launch {
            when (workspaceReadiness.nextStep) {
                SupplierWorkspaceReadinessNextStep.CreateProfile -> {
                    NavigationScreenModel.Supplier.Identity.Main.setStates(
                        SUPPLIER_PROFILE_EDITOR_OPEN_STATE_KEY to "1",
                        SUPPLIER_PROFILE_EDITOR_ID_STATE_KEY to "",
                        SUPPLIER_PROFILE_EDITOR_SESSION_STATE_KEY to getCurrentTimeMillis().toString()
                    )
                }
                SupplierWorkspaceReadinessNextStep.CompleteProfile -> {
                    val targetSupplierId = workspaceReadiness.targetSupplierId.orEmpty()
                    if (targetSupplierId.isNotBlank()) {
                        setActiveSupplierProfileId(targetSupplierId)
                        NavigationScreenModel.Supplier.Identity.Main.setStates(
                            SUPPLIER_PROFILE_EDITOR_OPEN_STATE_KEY to "1",
                            SUPPLIER_PROFILE_EDITOR_ID_STATE_KEY to targetSupplierId,
                            SUPPLIER_PROFILE_EDITOR_SESSION_STATE_KEY to getCurrentTimeMillis().toString()
                        )
                    }
                }
                SupplierWorkspaceReadinessNextStep.ReviewOrders ->
                    Navigation.goMain(NavigationScreenModel.Supplier.Orders.Main)
                SupplierWorkspaceReadinessNextStep.BuildCatalog ->
                    Navigation.goMain(NavigationScreenModel.Supplier.Catalog.Main)
                SupplierWorkspaceReadinessNextStep.ConnectStores ->
                    Navigation.goMain(NavigationScreenModel.Supplier.Customers.Main)
                SupplierWorkspaceReadinessNextStep.ReviewAgreements ->
                    Navigation.goMain(NavigationScreenModel.Supplier.Contracts.Main)
                SupplierWorkspaceReadinessNextStep.OpenInsights ->
                    Navigation.goMain(NavigationScreenModel.Supplier.Analytics.Main)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (editorOpen) {
            ScreenAppBarWidget(
                title = if (editedSupplierId.isBlank()) localizedStringResource(2492, "Add profile") else localizedStringResource(2502, "Edit profile"),
                iconPath = stateValues.drawablePathIconSuppliers,
                iconRes = stateValues.drawableResIconSuppliers.value,
                onBack = {
                    coroutineScope.launch {
                        NavigationScreenModel.Supplier.Identity.Main.removeState(editorPhoneStateKey)
                        NavigationScreenModel.Supplier.Identity.Main.setStates(
                            SUPPLIER_PROFILE_EDITOR_OPEN_STATE_KEY to "0",
                            SUPPLIER_PROFILE_EDITOR_ID_STATE_KEY to "",
                            SUPPLIER_PROFILE_EDITOR_SESSION_STATE_KEY to ""
                        )
                    }
                }
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.66f)
                    .padding(stateValues.marginTextField),
                verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField),
                contentPadding = PaddingValues(bottom = stateValues.screenHeight / 5)
            ) {
                if (editedSupplierId.isNotBlank() && suppliers == null) {
                    item {
                        MessageText(
                            modifier = Modifier.fillMaxWidth(),
                            text = localizedStringResource(2515, "Loading supplier profile…")
                        )
                    }
                } else if (editedSupplierId.isBlank() || editedSupplier != null) {
                    item {
                        SupplierProfileEditorContent(
                            editedSupplier = editedSupplier,
                            stateHost = NavigationScreenModel.Supplier.Identity.Main,
                            editorSessionKey = editorSessionKey,
                            onCancel = {
                                coroutineScope.launch {
                                    NavigationScreenModel.Supplier.Identity.Main.setStates(
                                        SUPPLIER_PROFILE_EDITOR_OPEN_STATE_KEY to "0",
                                        SUPPLIER_PROFILE_EDITOR_ID_STATE_KEY to "",
                                        SUPPLIER_PROFILE_EDITOR_SESSION_STATE_KEY to ""
                                    )
                                }
                            },
                            onSaved = { saved ->
                                profileDashboard = (profileDashboard ?: SupplierModeDashboardDataModel())
                                    .withSupplierProfileSnapshot(saved)
                                if (profileDashboard != null) profileUsageCheckFailed = false
                                coroutineScope.launch {
                                    NavigationScreenModel.Supplier.Identity.Main.setStates(
                                        SUPPLIER_PROFILE_EDITOR_OPEN_STATE_KEY to "0",
                                        SUPPLIER_PROFILE_EDITOR_ID_STATE_KEY to "",
                                        SUPPLIER_PROFILE_EDITOR_SESSION_STATE_KEY to ""
                                    )
                                }
                            }
                        )
                    }
                }
            }
            return@Column
        }

        ScreenAppBarWidget(
            title = localizedStringResource(2490, "Supplier profiles"),
            iconPath = stateValues.drawablePathIconSuppliers,
            iconRes = stateValues.drawableResIconSuppliers.value,
            trailingIcons = listOf(
                Triple(stateValues.drawablePathIconRefresh, stateValues.drawableResIconRefresh.value) {
                    refreshProfiles(true)
                },
                Triple(stateValues.drawablePathIconAdd, stateValues.drawableResIconAdd.value) {
                    coroutineScope.launch {
                        NavigationScreenModel.Supplier.Identity.Main.setStates(
                            SUPPLIER_PROFILE_EDITOR_OPEN_STATE_KEY to "1",
                            SUPPLIER_PROFILE_EDITOR_ID_STATE_KEY to "",
                            SUPPLIER_PROFILE_EDITOR_SESSION_STATE_KEY to getCurrentTimeMillis().toString()
                        )
                    }
                }
            ),
            onBack = {
                coroutineScope.launch {
                    val target = supplierProfileEditorReturnScreen(returnRoute)
                    NavigationScreenModel.Supplier.Identity.Main.setStates(
                        SUPPLIER_PROFILE_RETURN_ROUTE_STATE_KEY to "",
                        SUPPLIER_PROFILE_EDITOR_OPEN_STATE_KEY to "0",
                        SUPPLIER_PROFILE_EDITOR_ID_STATE_KEY to "",
                        SUPPLIER_PROFILE_EDITOR_SESSION_STATE_KEY to ""
                    )
                    Navigation.goMain(target)
                }
            }
        )

        val section = sectionTabsWidget(
            stateKey = "supplier-profiles:${currentUserId.orEmpty()}",
            tabs = listOf(
                TabContent("profiles", authUiText("Profiles", "Профили", "Профильдер")),
                TabContent("readiness", authUiText("Readiness", "Готовность", "Дайындық"))
            ),
            modifier = Modifier
                .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.74f)
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = stateValues.marginTextField, vertical = stateValues.marginTextField / 2),
        )

        LazyColumn(
            state = rememberPersistentLazyListState(NavigationScreenModel.Supplier.Identity.Main, "sections:$section"),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.74f)
                .padding(stateValues.marginTextField),
            verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField),
            contentPadding = PaddingValues(bottom = stateValues.screenHeight / 5)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                ) {
                    MessageText(
                        modifier = Modifier.fillMaxWidth(),
                        text = localizedStringResource(2491, "Manage the business identities stores order from. Each profile keeps its own orders, offers, partners, agreements, delivery work, and insights.")
                    )

                    if (identityPresentation.options.size > 1) {
                        SupplierIdentityFocusSelector(
                            presentation = identityPresentation,
                            onIdentitySelected = { supplierId ->
                                coroutineScope.launch {
                                    setActiveSupplierProfileId(supplierId)
                                }
                            }
                        )
                    }
                }
            }

            if (section == "readiness") {
                if (items.isEmpty()) {
                    item { MessageText(Modifier.fillMaxWidth(), localizedStringResource(2514, "No supplier profiles yet. Create the identity stores will order from.")) }
                } else {
                    item {
                        SupplierWorkspaceReadinessCard(
                            summary = workspaceReadiness,
                            onNextStep = openReadinessNextStep
                        )
                    }
                }
            }

            if (section == "profiles") {
                if (items.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                        ) {
                            MessageText(
                                modifier = Modifier.fillMaxWidth(),
                                text = localizedStringResource(2514, "No supplier profiles yet. Create the identity stores will order from.")
                            )
                            actionButton(
                                modifier = Modifier.fillMaxWidth(),
                                text = localizedStringResource(2492, "Add profile"),
                                iconPath = stateValues.drawablePathIconAdd,
                                iconRes = stateValues.drawableResIconAdd.value,
                                confirmationRequired = false,
                                onClick = {
                                    coroutineScope.launch {
                                        NavigationScreenModel.Supplier.Identity.Main.setStates(
                                            SUPPLIER_PROFILE_EDITOR_OPEN_STATE_KEY to "1",
                                            SUPPLIER_PROFILE_EDITOR_ID_STATE_KEY to "",
                                            SUPPLIER_PROFILE_EDITOR_SESSION_STATE_KEY to getCurrentTimeMillis().toString()
                                        )
                                    }
                                }
                            )
                        }
                    }
                } else {
                    items(items, key = { it.supplier.id }) { item ->
                        SupplierProfileWorkspaceCard(
                            item = item,
                            showFocusAction = identityPresentation.profileCount > 1,
                            isDeleting = normalizeSupplierProfileIdentityId(item.supplier.id) in deletingSupplierIds,
                            onFocus = {
                                coroutineScope.launch {
                                    setActiveSupplierProfileId(item.supplier.id)
                                }
                            },
                            onEdit = {
                                coroutineScope.launch {
                                    NavigationScreenModel.Supplier.Identity.Main.setStates(
                                        SUPPLIER_PROFILE_EDITOR_OPEN_STATE_KEY to "1",
                                        SUPPLIER_PROFILE_EDITOR_ID_STATE_KEY to item.supplier.id,
                                        SUPPLIER_PROFILE_EDITOR_SESSION_STATE_KEY to getCurrentTimeMillis().toString()
                                    )
                                }
                            },
                            onDelete = {
                                val normalizedSupplierId = normalizeSupplierProfileIdentityId(item.supplier.id)
                                if (normalizedSupplierId != null && normalizedSupplierId !in deletingSupplierIds) {
                                    deletingSupplierIds = deletingSupplierIds + normalizedSupplierId
                                    deleteSupplier(item.supplier.id) { result ->
                                        coroutineScope.launch {
                                            deletingSupplierIds = deletingSupplierIds - normalizedSupplierId
                                            if (result is DataState.Success) {
                                                profileDashboard = (profileDashboard ?: SupplierModeDashboardDataModel())
                                                    .withoutSupplierProfileSnapshot(item.supplier.id)
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
}

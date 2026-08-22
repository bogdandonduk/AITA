package kz.aita

internal const val SUPPLIER_PROFILE_RETURN_ROUTE_STATE_KEY = "supplier_profiles_return_route"
internal const val SUPPLIER_PROFILE_EDITOR_OPEN_STATE_KEY = "supplier_profiles_editor_open"
internal const val SUPPLIER_PROFILE_EDITOR_ID_STATE_KEY = "supplier_profiles_editor_id"
internal const val SUPPLIER_PROFILE_EDITOR_SESSION_STATE_KEY = "supplier_profiles_editor_session"

internal fun supplierProfileEditorStableKey(supplierId: String?, sessionKey: String): String = buildString {
    append(supplierId?.takeIf { it.isNotBlank() } ?: "new")
    append(':')
    append(sessionKey.ifBlank { "session" })
}

internal fun supplierProfileEditorPhoneStateKey(supplierId: String?, sessionKey: String): String =
    "supplier_profile_phone_${supplierProfileEditorStableKey(supplierId, sessionKey)}"

internal data class SupplierProfileWorkspaceItemUiModel(
    val supplier: SupplierDataModel,
    val title: String,
    val contactText: String,
    val orderCount: Int,
    val openOrderCount: Int,
    val catalogSkuCount: Int,
    val savedOfferCount: Int,
    val stockBatchCount: Int,
    val commercialHistoryCount: Int,
    val partnerCount: Int,
    val activeContractCount: Int,
    val pendingContractCount: Int,
    val usageLoaded: Boolean,
    val usageCheckFailed: Boolean,
    val readinessIssues: Set<SupplierProfileReadinessIssue>,
    val selected: Boolean
) {
    val liveAgreementCount: Int get() = activeContractCount + pendingContractCount
    val canDeleteFromLoadedState: Boolean get() =
        usageLoaded && commercialHistoryCount <= 0 && orderCount <= 0 &&
            liveAgreementCount <= 0 && savedOfferCount <= 0 && stockBatchCount <= 0
    val ready: Boolean get() = readinessIssues.isEmpty()
}

internal fun AppConfiguration.buildSupplierProfileWorkspaceItems(
    localProfiles: List<SupplierDataModel>,
    dashboard: SupplierModeDashboardDataModel?,
    usageLoaded: Boolean,
    usageCheckFailed: Boolean,
    activeSupplierId: String?
): List<SupplierProfileWorkspaceItemUiModel> {
    val dashboardById = dashboard?.supplierProfiles.orEmpty().associateBy {
        normalizeSupplierProfileIdentityId(it.supplierId).orEmpty()
    }
    val normalizedActive = normalizeSupplierProfileIdentityId(activeSupplierId)

    return localProfiles
        .filter { it.isActive }
        .map { supplier ->
            val normalizedId = normalizeSupplierProfileIdentityId(supplier.id).orEmpty()
            val profileDashboard = dashboardById[normalizedId]
            val contactText = (supplier.phoneNumbers.orEmpty().asDisplayPhoneNumbers() + supplier.emails.orEmpty())
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .take(3)
                .joinToString(" • ")
                .ifBlank { localizedStringResource(1634, "No contact yet") }

            SupplierProfileWorkspaceItemUiModel(
                supplier = supplier,
                title = supplier.visibleSupplierName(stateValues.appLanguage),
                contactText = contactText,
                orderCount = profileDashboard?.orderCount ?: 0,
                openOrderCount = profileDashboard?.openOrderCount ?: 0,
                catalogSkuCount = profileDashboard?.catalogSkuCount ?: 0,
                savedOfferCount = profileDashboard?.savedOfferCount ?: 0,
                stockBatchCount = profileDashboard?.stockBatchCount ?: 0,
                commercialHistoryCount = profileDashboard?.commercialHistoryCount ?: 0,
                partnerCount = profileDashboard?.partnerCount ?: 0,
                activeContractCount = profileDashboard?.activeContractCount ?: 0,
                pendingContractCount = profileDashboard?.pendingContractCount ?: 0,
                usageLoaded = usageLoaded && profileDashboard != null,
                usageCheckFailed = usageCheckFailed && profileDashboard == null,
                readinessIssues = supplier.supplierProfileReadinessIssues(),
                selected = normalizedActive != null && normalizedId == normalizedActive
            )
        }
        .sortedWith(
            compareByDescending<SupplierProfileWorkspaceItemUiModel> { it.selected }
                .thenByDescending { it.openOrderCount }
                .thenBy { it.title.lowercase() }
        )
}

internal fun SupplierProfileReadinessIssue.profileReadinessLabel(configuration: AppConfiguration): String = with(configuration) {
    when (this@profileReadinessLabel) {
        SupplierProfileReadinessIssue.MissingContact -> localizedStringResource(2496, "Add a contact")
        SupplierProfileReadinessIssue.DuplicatePhone -> localizedStringResource(2497, "Duplicate phone")
        SupplierProfileReadinessIssue.DuplicateEmail -> localizedStringResource(2498, "Duplicate email")
        SupplierProfileReadinessIssue.InvalidEmail -> localizedStringResource(2499, "Check email")
    }
}

internal fun supplierProfileEditorReturnScreen(route: String?): NavigationScreenModel =
    persistentAppRouteToScreen(route.orEmpty())
        ?.takeIf { it is NavigationScreenModel.Supplier && it !is NavigationScreenModel.Supplier.Identity }
        ?: NavigationScreenModel.Supplier.Orders.Main

internal fun SupplierModeDashboardDataModel.withSupplierProfileSnapshot(
    supplier: SupplierDataModel
): SupplierModeDashboardDataModel {
    val normalizedId = normalizeSupplierProfileIdentityId(supplier.id) ?: return this
    val previous = supplierProfiles.firstOrNull {
        normalizeSupplierProfileIdentityId(it.supplierId) == normalizedId
    }
    val next = (previous ?: SupplierDashboardProfileDataModel(supplierId = supplier.id)).copy(
        supplierId = supplier.id,
        name = supplier.name,
        phoneNumbers = supplier.phoneNumbers.orEmpty(),
        emails = supplier.emails.orEmpty()
    )
    return copy(
        supplierIds = (supplierIds + supplier.id)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() },
        supplierProfiles = supplierProfiles
            .filterNot { normalizeSupplierProfileIdentityId(it.supplierId) == normalizedId } + next
    )
}

internal fun SupplierModeDashboardDataModel.withoutSupplierProfileSnapshot(
    supplierId: String
): SupplierModeDashboardDataModel {
    val normalizedId = normalizeSupplierProfileIdentityId(supplierId) ?: return this
    return copy(
        supplierIds = supplierIds.filterNot { normalizeSupplierProfileIdentityId(it) == normalizedId },
        supplierProfiles = supplierProfiles.filterNot {
            normalizeSupplierProfileIdentityId(it.supplierId) == normalizedId
        }
    )
}

internal enum class SupplierWorkspaceReadinessNextStep {
    CreateProfile,
    CompleteProfile,
    ReviewOrders,
    BuildCatalog,
    ConnectStores,
    ReviewAgreements,
    OpenInsights
}

internal data class SupplierWorkspaceReadinessUiModel(
    val profileCount: Int,
    val readyProfileCount: Int,
    val setupIssueCount: Int,
    val openOrderCount: Int,
    val catalogSkuCount: Int,
    val partnerCount: Int,
    val liveAgreementCount: Int,
    val readinessPercent: Int,
    val nextStep: SupplierWorkspaceReadinessNextStep,
    val targetSupplierId: String?
)

internal fun buildSupplierWorkspaceReadiness(
    items: List<SupplierProfileWorkspaceItemUiModel>,
    activeSupplierId: String?
): SupplierWorkspaceReadinessUiModel {
    if (items.isEmpty()) {
        return SupplierWorkspaceReadinessUiModel(
            profileCount = 0,
            readyProfileCount = 0,
            setupIssueCount = 0,
            openOrderCount = 0,
            catalogSkuCount = 0,
            partnerCount = 0,
            liveAgreementCount = 0,
            readinessPercent = 0,
            nextStep = SupplierWorkspaceReadinessNextStep.CreateProfile,
            targetSupplierId = null
        )
    }

    val normalizedActive = normalizeSupplierProfileIdentityId(activeSupplierId)
    val focusedItem = normalizedActive?.let { activeId ->
        items.firstOrNull { normalizeSupplierProfileIdentityId(it.supplier.id) == activeId }
    } ?: items.singleOrNull()
    val incompleteItem = when {
        focusedItem != null && !focusedItem.ready -> focusedItem
        else -> items.firstOrNull { !it.ready }
    }
    val actionScope = focusedItem?.let(::listOf) ?: items

    val completedReadinessChecks = items.sumOf { item ->
        listOf(
            item.ready,
            item.catalogSkuCount > 0 || item.savedOfferCount > 0,
            item.partnerCount > 0,
            item.liveAgreementCount > 0
        ).count { it }
    }
    val readinessPercent = ((completedReadinessChecks * 100.0) / (items.size * 4.0))
        .toInt()
        .coerceIn(0, 100)

    val scopedOpenOrders = actionScope.sumOf { it.openOrderCount }
    val scopedCatalogSkuCount = actionScope.sumOf { it.catalogSkuCount + it.savedOfferCount }
    val scopedPartnerCount = actionScope.sumOf { it.partnerCount }
    val scopedAgreementCount = actionScope.sumOf { it.liveAgreementCount }
    val nextStep = when {
        incompleteItem != null -> SupplierWorkspaceReadinessNextStep.CompleteProfile
        scopedOpenOrders > 0 -> SupplierWorkspaceReadinessNextStep.ReviewOrders
        scopedCatalogSkuCount <= 0 -> SupplierWorkspaceReadinessNextStep.BuildCatalog
        scopedPartnerCount <= 0 -> SupplierWorkspaceReadinessNextStep.ConnectStores
        scopedAgreementCount <= 0 -> SupplierWorkspaceReadinessNextStep.ReviewAgreements
        else -> SupplierWorkspaceReadinessNextStep.OpenInsights
    }

    return SupplierWorkspaceReadinessUiModel(
        profileCount = items.size,
        readyProfileCount = items.count { it.ready },
        setupIssueCount = items.sumOf { it.readinessIssues.size },
        openOrderCount = items.sumOf { it.openOrderCount },
        catalogSkuCount = items.sumOf { it.catalogSkuCount + it.savedOfferCount },
        partnerCount = items.sumOf { it.partnerCount },
        liveAgreementCount = items.sumOf { it.liveAgreementCount },
        readinessPercent = readinessPercent,
        nextStep = nextStep,
        targetSupplierId = when (nextStep) {
            SupplierWorkspaceReadinessNextStep.CompleteProfile -> incompleteItem?.supplier?.id
            else -> focusedItem?.supplier?.id
        }
    )
}

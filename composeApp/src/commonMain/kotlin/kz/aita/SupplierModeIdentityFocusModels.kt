package kz.aita

internal data class SupplierIdentityOptionUiModel(
    val supplierId: String?,
    val title: String,
    val subtitle: String,
    val selected: Boolean
)

internal data class SupplierIdentityPresentationUiModel(
    val title: String,
    val subtitle: String,
    val activeSupplierId: String?,
    val options: List<SupplierIdentityOptionUiModel>,
    val combined: Boolean,
    val profileCount: Int
)

private data class SupplierIdentityPresentationSeed(
    val supplierId: String,
    val title: String,
    val contacts: List<String>
)

internal fun supplierIdentityPresentationAllowsDashboardFallback(localProfilesLoaded: Boolean): Boolean =
    !localProfilesLoaded

internal fun AppConfiguration.buildSupplierIdentityPresentation(
    localProfiles: List<SupplierDataModel>,
    dashboard: SupplierModeDashboardDataModel?,
    activeSupplierId: String?,
    localProfilesLoaded: Boolean
): SupplierIdentityPresentationUiModel {
    val dashboardById = dashboard
        ?.supplierProfiles
        .orEmpty()
        .mapNotNull { profile ->
            normalizeSupplierProfileIdentityId(profile.supplierId)?.let { it to profile }
        }
        .toMap()

    val seedsById = linkedMapOf<String, SupplierIdentityPresentationSeed>()
    localProfiles
        .filter { it.isActive }
        .forEach { profile ->
            val normalizedId = normalizeSupplierProfileIdentityId(profile.id) ?: return@forEach
            val dashboardProfile = dashboardById[normalizedId]
            val title = profile.visibleSupplierName(stateValues.appLanguage)
                .ifBlank {
                    dashboardProfile
                        ?.name
                        ?.visibleLocalizedString(stateValues.appLanguage, "")
                        .orEmpty()
                }
                .ifBlank { localizedStringResource(1625, "Supplier identity") }
            val contacts = (
                profile.phoneNumbers.orEmpty() +
                    profile.emails.orEmpty() +
                    dashboardProfile?.phoneNumbers.orEmpty() +
                    dashboardProfile?.emails.orEmpty()
                )
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinctBy { it.lowercase() }
            seedsById[normalizedId] = SupplierIdentityPresentationSeed(
                supplierId = profile.id.trim(),
                title = title,
                contacts = contacts
            )
        }

    if (supplierIdentityPresentationAllowsDashboardFallback(localProfilesLoaded)) {
        dashboardById.forEach { (normalizedId, profile) ->
            if (normalizedId !in seedsById) {
                seedsById[normalizedId] = SupplierIdentityPresentationSeed(
                    supplierId = profile.supplierId.trim(),
                    title = profile.name
                        .visibleLocalizedString(stateValues.appLanguage, "")
                        .ifBlank { localizedStringResource(1625, "Supplier identity") },
                    contacts = (profile.phoneNumbers + profile.emails)
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .distinctBy { it.lowercase() }
                )
            }
        }
    }

    val seeds = seedsById.values.sortedBy { it.title.lowercase() }
    val normalizedActiveId = normalizeSupplierProfileIdentityId(activeSupplierId)
    val selectedSeed = normalizedActiveId?.let(seedsById::get) ?: seeds.singleOrNull()
    val selectedSupplierId = selectedSeed?.supplierId
    val combined = seeds.size > 1 && selectedSeed == null

    val options = buildList {
        if (seeds.size > 1) {
            add(
                SupplierIdentityOptionUiModel(
                    supplierId = null,
                    title = localizedStringResource(2484, "All profiles"),
                    subtitle = localizedStringResource(2481, "Combined view across all supplier profiles"),
                    selected = combined
                )
            )
        }
        seeds.forEach { seed ->
            add(
                SupplierIdentityOptionUiModel(
                    supplierId = seed.supplierId,
                    title = seed.title,
                    subtitle = seed.contacts.firstOrNull().orEmpty().ifBlank {
                        localizedStringResource(2485, "Focused profile")
                    },
                    selected = normalizeSupplierProfileIdentityId(seed.supplierId) ==
                        normalizeSupplierProfileIdentityId(selectedSupplierId)
                )
            )
        }
    }

    val title = when {
        combined -> localizedStringResource(2480, "All supplier identities")
        selectedSeed != null -> selectedSeed.title
        else -> localizedStringResource(1366, "Supplier desk")
    }
    val subtitle = when {
        combined -> "${localizedStringResource(2481, "Combined view across all supplier profiles")} • ${seeds.size}"
        selectedSeed != null -> selectedSeed.contacts.firstOrNull().orEmpty().ifBlank {
            localizedStringResource(2485, "Focused profile")
        }
        else -> localizedStringResource(1625, "Supplier identity")
    }

    return SupplierIdentityPresentationUiModel(
        title = title,
        subtitle = subtitle,
        activeSupplierId = selectedSupplierId,
        options = options,
        combined = combined,
        profileCount = seeds.size
    )
}

internal fun SupplierIdentityPresentationUiModel.titleForSupplierIdentity(
    supplierId: String?
): String = options
    .firstOrNull { option ->
        option.supplierId != null &&
            normalizeSupplierProfileIdentityId(option.supplierId) ==
            normalizeSupplierProfileIdentityId(supplierId)
    }
    ?.title
    .orEmpty()

internal fun List<SupplierOrderDataModel>.supplierOrdersForIdentity(
    supplierId: String?
): List<SupplierOrderDataModel> = filter { it.matchesSupplierProfileFocus(supplierId) }

internal fun List<SupplierGoodsPriceDataModel>.supplierPricesForIdentity(
    supplierId: String?
): List<SupplierGoodsPriceDataModel> = filter { it.matchesSupplierProfileFocus(supplierId) }

internal fun List<SupplierPartnershipContractDataModel>.supplierContractsForIdentity(
    supplierId: String?
): List<SupplierPartnershipContractDataModel> = filter { it.matchesSupplierProfileFocus(supplierId) }

internal fun List<SupplierDataModel>.supplierProfilesForIdentity(
    supplierId: String?
): List<SupplierDataModel> {
    val focus = normalizeSupplierProfileIdentityId(supplierId) ?: return this
    return filter { normalizeSupplierProfileIdentityId(it.id) == focus }
}

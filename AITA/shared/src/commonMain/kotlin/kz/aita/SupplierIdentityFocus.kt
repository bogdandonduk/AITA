package kz.aita

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

const val KEY_ACTIVE_SUPPLIER_PROFILE_ID = "key_activeSupplierProfileId"
private const val KEY_ACTIVE_SUPPLIER_PROFILE_USER_ID = "key_activeSupplierProfileUserId"

/**
 * Null means the combined Supplier workspace. A concrete ID means every Supplier screen should
 * display and act within that one commercial identity. The value is local UI context, not a server
 * permission boundary; every server route must still prove access to the requested Supplier.
 */
val activeSupplierProfileIdState = MutableStateFlow<String?>(null)

private val supplierIdentityFocusMutationMutex = Mutex()
private var supplierIdentityFocusStarted = false

fun normalizeSupplierProfileIdentityId(value: String?): String? =
    value?.trim()?.lowercase()?.takeIf { it.isNotBlank() }

/**
 * Preserve an explicitly selected accessible identity. A sole identity is selected automatically;
 * with several identities and no explicit selection, null intentionally means the combined view.
 */
fun resolveSupplierProfileFocus(
    preferredSupplierId: String?,
    profiles: List<SupplierDataModel>
): String? {
    val activeProfiles = profiles
        .asSequence()
        .filter { it.isActive }
        .mapNotNull { profile ->
            normalizeSupplierProfileIdentityId(profile.id)?.let { normalized -> normalized to profile.id.trim() }
        }
        .distinctBy { it.first }
        .toList()
    if (activeProfiles.isEmpty()) return null

    val preferred = normalizeSupplierProfileIdentityId(preferredSupplierId)
    activeProfiles.firstOrNull { it.first == preferred }?.let { return it.second }

    return activeProfiles.singleOrNull()?.second
}

fun SupplierOrderDataModel.matchesSupplierProfileFocus(supplierId: String?): Boolean {
    val focus = normalizeSupplierProfileIdentityId(supplierId) ?: return true
    return normalizeSupplierProfileIdentityId(this.supplierId) == focus
}

fun SupplierGoodsPriceDataModel.matchesSupplierProfileFocus(supplierId: String?): Boolean {
    val focus = normalizeSupplierProfileIdentityId(supplierId) ?: return true
    return normalizeSupplierProfileIdentityId(this.supplierId) == focus
}

fun SupplierPartnershipContractDataModel.matchesSupplierProfileFocus(supplierId: String?): Boolean {
    val focus = normalizeSupplierProfileIdentityId(supplierId) ?: return true
    return normalizeSupplierProfileIdentityId(this.supplierId) == focus
}

fun SupplierDashboardDispatchRunDataModel.matchesSupplierProfileFocus(supplierId: String?): Boolean {
    val focus = normalizeSupplierProfileIdentityId(supplierId) ?: return true
    return normalizeSupplierProfileIdentityId(this.supplierId) == focus
}

fun currentOwnedSupplierProfiles(): List<SupplierDataModel> =
    suppliersState.payloadValue.orEmpty().supplierProfilesOwnedBy(userAccountState.payloadValue?.id)

fun effectiveActiveSupplierProfileId(): String? = resolveSupplierProfileFocus(
    preferredSupplierId = activeSupplierProfileIdState.value,
    profiles = currentOwnedSupplierProfiles()
)

private data class SupplierIdentityFocusInput(
    val userId: String,
    val profiles: List<SupplierDataModel>,
    val storedSupplierId: String?,
    val storedUserId: String?
)

/** Starts one process-wide reconciler. Call once from the shared application initializer. */
fun startSupplierIdentityFocus() {
    if (supplierIdentityFocusStarted) return
    supplierIdentityFocusStarted = true

    GlobalScope.launch(Dispatchers.ourIo) {
        combine(
            userAccountState.payload,
            suppliersState.payload,
            observeLocalKv(KEY_ACTIVE_SUPPLIER_PROFILE_ID),
            observeLocalKv(KEY_ACTIVE_SUPPLIER_PROFILE_USER_ID)
        ) { account, suppliers, storedSupplierId, storedUserId ->
            SupplierIdentityFocusInput(
                userId = account?.id.orEmpty().trim(),
                profiles = suppliers.orEmpty(),
                storedSupplierId = storedSupplierId,
                storedUserId = storedUserId
            )
        }
            .distinctUntilChanged()
            .collect { _ ->
                var focusChanged = false
                var reconciledUserId = ""
                supplierIdentityFocusMutationMutex.withLock {
                    // Re-read inside the mutation lock. A queued collector may carry an older KV
                    // snapshot than a just-completed user selection; trusting that stale snapshot
                    // could briefly switch the workspace back to the previous legal identity.
                    val currentUserId = userAccountState.payloadValue?.id.orEmpty().trim()
                    reconciledUserId = currentUserId
                    val currentProfiles = suppliersState.payloadValue.orEmpty()
                    val storedSupplierId = getLocalKv(KEY_ACTIVE_SUPPLIER_PROFILE_ID)
                    val storedUserId = getLocalKv(KEY_ACTIVE_SUPPLIER_PROFILE_USER_ID)
                    val ownedProfiles = currentProfiles.supplierProfilesOwnedBy(currentUserId)
                    val storedBelongsToCurrentUser = currentUserId.isNotBlank() &&
                            storedUserId.orEmpty().trim() == currentUserId
                    val resolved = resolveSupplierProfileFocus(
                        preferredSupplierId = storedSupplierId.takeIf { storedBelongsToCurrentUser },
                        profiles = ownedProfiles
                    )

                    if (activeSupplierProfileIdState.value != resolved) {
                        activeSupplierProfileIdState.value = resolved
                        focusChanged = true
                    }

                    if (currentUserId.isBlank()) {
                        if (storedSupplierId != null) putLocalKv(KEY_ACTIVE_SUPPLIER_PROFILE_ID, null)
                        if (storedUserId != null) putLocalKv(KEY_ACTIVE_SUPPLIER_PROFILE_USER_ID, null)
                    } else {
                        if (storedUserId != currentUserId) {
                            putLocalKv(KEY_ACTIVE_SUPPLIER_PROFILE_USER_ID, currentUserId)
                        }
                        if (storedSupplierId != resolved) {
                            putLocalKv(KEY_ACTIVE_SUPPLIER_PROFILE_ID, resolved)
                        }
                    }
                }

                // Automatic reconciliation (first profile loaded, selected profile removed, or
                // account changed) owns exactly one scoped refresh. Individual screens only reset
                // their session-only detail state when the focus changes, avoiding duplicate GETs.
                if (
                    focusChanged &&
                    reconciledUserId.isNotBlank() &&
                    getStoredUserAuthTokens?.invoke() != null &&
                    (appModeState.value == APP_MODE_SUPPLIER || appModeState.value == APP_MODE_MANUFACTURER)
                ) {
                    refreshSupplierModeWorkspace(includeContracts = true, force = true)
                }
            }
    }
}

/**
 * Select one identity or null for the combined view. The selection is committed before the forced
 * refresh begins, so no screen can accidentally fetch the previously selected identity.
 */
suspend fun setActiveSupplierProfileId(supplierId: String?) {
    val changed = supplierIdentityFocusMutationMutex.withLock {
        val currentUserId = userAccountState.payloadValue?.id.orEmpty().trim()
        val resolved = resolveSupplierProfileFocus(
            preferredSupplierId = supplierId,
            profiles = suppliersState.payloadValue.orEmpty().supplierProfilesOwnedBy(currentUserId)
        )
        val changedInsideLock = activeSupplierProfileIdState.value != resolved
        activeSupplierProfileIdState.value = resolved
        putLocalKv(KEY_ACTIVE_SUPPLIER_PROFILE_USER_ID, currentUserId.takeIf { it.isNotBlank() })
        putLocalKv(KEY_ACTIVE_SUPPLIER_PROFILE_ID, resolved)
        changedInsideLock
    }

    if (
        changed &&
        (appModeState.value == APP_MODE_SUPPLIER || appModeState.value == APP_MODE_MANUFACTURER)
    ) {
        refreshSupplierModeWorkspace(includeContracts = true, force = true)
    }
}

suspend fun clearSupplierIdentityFocusForLogout() {
    supplierIdentityFocusMutationMutex.withLock {
        activeSupplierProfileIdState.value = null
        putLocalKv(KEY_ACTIVE_SUPPLIER_PROFILE_ID, null)
        putLocalKv(KEY_ACTIVE_SUPPLIER_PROFILE_USER_ID, null)
    }
}

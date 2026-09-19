package kz.aita

import kotlinx.serialization.Serializable

@Serializable
enum class StoreBranchType { PHYSICAL, INTERNET }

/** Parent stores own the business and its warehouse; only branches perform checkout. */
fun StoreDataModel.isManagementStore(): Boolean = architectureVersion >= 2 && !isBranchStore()
fun StoreDataModel.effectiveBranchType(): StoreBranchType? =
    if (isBranchStore()) branchType ?: StoreBranchType.PHYSICAL else null
fun StoreDataModel.isInternetBranch(): Boolean = effectiveBranchType() == StoreBranchType.INTERNET
/** Old device caches described operating roots; never grant them free management access. */
fun StoreDataModel.supportsTransactions(): Boolean = isBranchStore() || architectureVersion < 2

fun currentStoreModel(storeId: String?): StoreDataModel? = storesState.payloadValue.orEmpty().findStoreOrBranch(storeId)
fun currentStoreSupportsTransactions(storeId: String?): Boolean = currentStoreModel(storeId)?.supportsTransactions() == true

/** Management access is permission-scoped, but has no subscription or billing state. */
fun currentStoreHasWorkspaceAccess(storeId: String?, now: Long = getCurrentTimeMillis()): Boolean {
    val store = currentStoreModel(storeId) ?: return false
    if (store.isManagementStore()) return currentUserOwnsStore(store.id) || currentUserStorePermissions(store.id).isNotEmpty()
    return currentStoreHasSubscriptionAccess(store.id, now)
}

/** Read-only history remains available for management. New checkout/cash/payment operations do not. */
fun storeEndpointRequiresOperatingBranch(endpoint: String): Boolean {
    val path = endpoint.substringBefore('?').substringBefore('#').trim('/').lowercase()
    val root = path.substringBefore('/')
    return when (root) {
        "transactions" -> path !in setOf("transactions/get", "transactions/search", "transactions/page", "transactions/receipt", "transactions/return/lookup", "transactions/return/candidates")
        "debtors" -> path == "debtors/pay"
        "cashregister" -> path !in setOf("cashregister/get", "cashregister/events/get")
        "workshifts" -> path in setOf("workshifts/start")
        "payments" -> !path.startsWith("payments/balance") && !path.startsWith("payments/topups") && !path.startsWith("payments/webhooks")
        else -> false
    }
}

val BRANCH_ONLY_STORE_PERMISSIONS = setOf(
    STORE_PERMISSION_SALE_TRANSACTION, STORE_PERMISSION_RETURN_TRANSACTION, STORE_PERMISSION_SUPPLY_TRANSACTION,
    STORE_PERMISSION_CASH_REGISTER_EXTRACT, STORE_PERMISSION_DEBTOR_PAYMENTS_MANAGE
)

fun StoreDataModel.architectureLabel(language: String): String = eventMessage(
    when {
        isManagementStore() -> "store.management"
        isInternetBranch() -> "store.branch.internet"
        else -> "store.branch.physical"
    }
).extractLocalizedString(language).orEmpty()

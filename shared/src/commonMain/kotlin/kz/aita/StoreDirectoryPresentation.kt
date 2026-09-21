package kz.aita

/** A branch remains visible if its parent is absent (deleted, inaccessible, or filtered out). */
fun List<StoreDataModel>.storeDirectoryRoots(): List<StoreDataModel> {
    val rows = flattenStoresWithBranches().distinctBy { it.id }
    val ids = rows.map { it.id }.toSet()
    return rows.filter { it.parentStoreId.isNullOrBlank() || it.parentStoreId !in ids }
}

/** Parent metadata may belong to another owner while the visible branch belongs to this account. */
fun StoreDataModel.storeDirectoryFamilyOwnedBy(accountId: String): Boolean =
    accountId.isNotBlank() && listOf(this).flattenStoresWithBranches().any { accountId in it.userIds }

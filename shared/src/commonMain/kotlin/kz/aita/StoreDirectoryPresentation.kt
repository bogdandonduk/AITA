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

/** Keep a matching branch under its parent, even when the parent's own dates do not match. */
fun List<StoreDataModel>.storeDirectoryByDates(options: RecordListOptions): List<StoreDataModel> {
    val filtered = mapNotNull { store ->
        val branches = store.branches.storeDirectoryByDates(options)
        if (branches.isNotEmpty() || recordMatchesDate(store.createdAt, store.updatedAt, options)) {
            store.copy(branches = branches)
        } else null
    }
    return recordDateList(filtered, options.copy(fromDate = "", throughDate = ""),
        { it.createdAt }, { it.updatedAt }, { it.id })
}

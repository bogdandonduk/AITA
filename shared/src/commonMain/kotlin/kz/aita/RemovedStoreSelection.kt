package kz.aita

/** Prefer the surviving parent of a vanished selection, without changing a still-valid choice. */
internal fun replacementForRemovedStore(
    selectedId: String?,
    previous: List<StoreDataModel>,
    available: List<StoreDataModel>,
    parentHint: String? = null
): String? {
    if (selectedId == null || available.findStoreOrBranch(selectedId) != null) return selectedId
    val parent = previous.findStoreOrBranch(selectedId)?.parentStoreId ?: parentHint
    return parent?.takeIf { it != selectedId && available.findStoreOrBranch(it)?.canBeSelectedAsActiveStore() == true }
}

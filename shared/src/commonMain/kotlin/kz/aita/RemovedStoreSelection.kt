package kz.aita

/** Prefer the surviving parent of a vanished selection, without changing a still-valid choice. */
internal fun replacementForRemovedStore(
    selectedId: String?,
    previous: List<StoreDataModel>,
    available: List<StoreDataModel>,
    parentHint: String? = null,
    canSelect: (StoreDataModel) -> Boolean = { true }
): String? {
    if (selectedId == null || available.findStoreOrBranch(selectedId) != null) return selectedId
    val parent = previous.findStoreOrBranch(selectedId)?.parentStoreId ?: parentHint
    return parent?.takeIf { it != selectedId && available.findStoreOrBranch(it)?.let { store ->
        store.canBeSelectedAsActiveStore() && canSelect(store)
    } == true }
}

/** A missing selection can be repaired inside one unambiguous family. Never choose a random
 * branch or an unrelated family, and callers preserve an explicit user deselection. */
internal fun recoverUnselectedStore(available: List<StoreDataModel>, parentHint: String? = null,
    canSelect: (StoreDataModel) -> Boolean = { true }): String? {
    val choices = available.settableActiveStores().filter(canSelect)
    choices.firstOrNull { it.id == parentHint }?.let { return it.id }
    val parents = choices.filter { it.isManagementStore() }
    if (parents.size == 1 && choices.all { it.id == parents.single().id || it.parentStoreId == parents.single().id })
        return parents.single().id
    return choices.singleOrNull()?.id
}

package kz.aita.server

/** Current means the selected physical location, including when that location is the parent. */
internal fun <Id> operationLogScopeStoreIds(
    currentStoreId: Id,
    visibleStoreIds: List<Id>,
    familyRequested: Boolean,
    familyAllowed: Boolean
): List<Id> = if (familyRequested && familyAllowed) visibleStoreIds.distinct()
else visibleStoreIds.filter { it == currentStoreId }.distinct()

package kz.aita

internal enum class SupplierRelationshipDirectoryFilter {
    ALL,
    ACTIVE,
    WAITING_FOR_ME,
    WAITING_FOR_PARTNER,
    LEGACY_CONTACTS,
}

internal enum class SupplierRelationshipDirectorySort {
    ACTION_FIRST,
    MOST_RECENT,
    STORE_NAME,
}

internal data class SupplierRelationshipDirectoryItem(
    val relationship: StoreSupplierRelationshipSnapshot,
    val storeTitle: String,
    val supplierTitle: String,
    val waitingForCurrentSide: Boolean,
    val waitingForOtherSide: Boolean,
    val actionPriority: Int,
)

internal fun buildSupplierRelationshipDirectoryItems(
    relationships: Iterable<StoreSupplierRelationshipSnapshot>,
    actorSide: StoreSupplierRelationshipSide,
    storeTitlesById: Map<String, String>,
    supplierTitlesById: Map<String, String>,
    filter: SupplierRelationshipDirectoryFilter,
    sort: SupplierRelationshipDirectorySort,
): List<SupplierRelationshipDirectoryItem> {
    return mergeStoreSupplierRelationships(relationships)
        .asSequence()
        .filter { it.isVisibleInOperationalDirectory() }
        .map { relationship ->
            val required = relationship.requiredAcceptanceSide()
            SupplierRelationshipDirectoryItem(
                relationship = relationship,
                storeTitle = storeTitlesById[relationship.key.storeId]
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: relationship.key.storeId,
                supplierTitle = supplierTitlesById[relationship.key.supplierId]
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: relationship.key.supplierId,
                waitingForCurrentSide = required == actorSide,
                waitingForOtherSide = required != null && required != actorSide,
                actionPriority = relationshipActionPriority(
                    relationship = relationship,
                    actorSide = actorSide,
                ),
            )
        }
        .filter { item ->
            when (filter) {
                SupplierRelationshipDirectoryFilter.ALL -> true
                SupplierRelationshipDirectoryFilter.ACTIVE ->
                    item.relationship.status == StoreSupplierRelationshipStatus.ACTIVE
                SupplierRelationshipDirectoryFilter.WAITING_FOR_ME -> item.waitingForCurrentSide
                SupplierRelationshipDirectoryFilter.WAITING_FOR_PARTNER -> item.waitingForOtherSide
                SupplierRelationshipDirectoryFilter.LEGACY_CONTACTS ->
                    item.relationship.kind == StoreSupplierRelationshipKind.LEGACY_LOCAL_CONTACT
            }
        }
        .sortedWith(
            when (sort) {
                SupplierRelationshipDirectorySort.ACTION_FIRST ->
                    compareByDescending<SupplierRelationshipDirectoryItem> { it.actionPriority }
                        .thenByDescending { it.relationship.updatedAtMillis }
                        .thenBy { it.storeTitle.lowercase() }
                SupplierRelationshipDirectorySort.MOST_RECENT ->
                    compareByDescending<SupplierRelationshipDirectoryItem> {
                        it.relationship.updatedAtMillis
                    }.thenBy { it.storeTitle.lowercase() }
                SupplierRelationshipDirectorySort.STORE_NAME ->
                    compareBy<SupplierRelationshipDirectoryItem> {
                        it.storeTitle.lowercase()
                    }.thenByDescending { it.relationship.updatedAtMillis }
            },
        )
        .toList()
}

private fun relationshipActionPriority(
    relationship: StoreSupplierRelationshipSnapshot,
    actorSide: StoreSupplierRelationshipSide,
): Int =
    when {
        relationship.kind == StoreSupplierRelationshipKind.LEGACY_LOCAL_CONTACT -> 5
        relationship.requiredAcceptanceSide() == actorSide -> 4
        relationship.requiredAcceptanceSide() != null -> 3
        relationship.status == StoreSupplierRelationshipStatus.ACTIVE -> 2
        else -> 1
    }

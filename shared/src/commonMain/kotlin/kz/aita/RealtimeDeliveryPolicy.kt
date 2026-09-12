package kz.aita

fun realtimeSequenceHasGap(previous: Long?, next: Long?): Boolean =
    previous != null && next != null && previous > 0L && next > previous && next - previous > 1L

internal fun realtimeEntityUsesStoreFamily(entity: String): Boolean {
    val root = entity.trim('/').lowercase().substringBefore('/')
    return root in setOf("all", "stock", "stockbatches", "transactions", "logs", "operationlogs", "stores", "workers", "analytics", "subscriptions")
}

internal fun realtimeScopeMatches(entity: String, changedStore: String, selectedStore: String?,
    selectedFamily: Set<String>, supplierMode: Boolean): Boolean {
    if (changedStore.isBlank()) return true
    if (supplierMode && (entity.startsWith("supplier") || entity == "all")) return true
    if (changedStore.equals(selectedStore, ignoreCase = true)) return true
    return realtimeEntityUsesStoreFamily(entity) && selectedFamily.any { it.equals(changedStore, ignoreCase = true) }
}

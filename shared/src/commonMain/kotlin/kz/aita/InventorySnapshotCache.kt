package kz.aita

internal data class InventorySnapshot<T>(val rows: List<T>, val importedLegacy: Boolean = false)

/** A missing snapshot is different from an unreadable one. Never replace damaged data with []. */
internal suspend inline fun <reified T> readInventorySnapshot(name: String, owner: InventoryOwner): InventorySnapshot<T>? {
    val key = CACHE_PREFIX + inventoryCacheKey(name, owner)
    check(getLocalKv(key + ":access-denied") != "1") { "Inventory access was revoked" }
    readRequiredJsonJournal(key)?.let { return InventorySnapshot(jsonBase.decodeFromString<List<T>>(it)) }
    // Old store-only keys have no account in their name. The immutable pre-upgrade cart owner
    // is the evidence tying that device's legacy store snapshot to this exact account/store.
    // Never guess from the currently signed-in account, which may have changed since the upgrade.
    if (!DynamicCarts.ownsLegacyInventory(owner)) return null
    val legacy = readRequiredJsonJournal(CACHE_PREFIX + "$name:${owner.storeId}") ?: return null
    return InventorySnapshot(jsonBase.decodeFromString<List<T>>(legacy), importedLegacy = true)
}

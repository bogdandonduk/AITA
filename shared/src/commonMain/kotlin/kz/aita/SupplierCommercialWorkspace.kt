package kz.aita

/** A request may read all of a supplier's stores, all of a store's suppliers, or one partnership. */
internal data class SupplierCommercialScope(val supplierSide: Boolean, val ownerId: String? = null, val peerId: String? = null) {
    val owner: String? = ownerId?.let(::supplierOrderIdentity)?.takeIf(String::isNotBlank)
    val peer: String? = peerId?.let(::supplierOrderIdentity)?.takeIf(String::isNotBlank)
    val valid: Boolean get() = (supplierSide || owner != null) && (owner != null || peer == null)
}

internal data class SupplierCommercialRow<T>(val id: String, val store: String, val supplier: String, val value: T)
internal fun SupplierGoodsPriceDataModel.commercialRow() = SupplierCommercialRow(supplierPriceBookIdentity(),
    supplierOrderIdentity(storeId), supplierOrderIdentity(supplierId), this)
internal fun SupplierPartnershipContractDataModel.commercialRow() = SupplierCommercialRow(supplierOrderIdentity(id),
    supplierOrderIdentity(storeId), supplierOrderIdentity(supplierId), this)

/** Shared membership algorithm for prices and contracts. Both desks see a single canonical version
 * of overlap, but neither a seller-only read nor access revocation removes the buyer's source. */
internal data class SupplierCommercialWorkspace<T>(
    val rows: List<SupplierCommercialRow<T>> = emptyList(),
    val sources: SupplierWorkspaceSources = SupplierWorkspaceSources(),
    val readScopes: Set<SupplierCommercialScope> = emptySet(),
    val removedIds: Set<String> = emptySet()
) {
    val values: List<T> get() = rows.map { it.value }
    val buyerValues: List<T> get() { val ids = sources.buyerIds; return rows.filter { it.id in ids }.map { it.value } }
    val sellerValues: List<T> get() { val ids = sources.sellerIds; return rows.filter { it.id in ids }.map { it.value } }

    fun valid(): Boolean {
        if (rows.size > 50_000 || sources.storeRows.size > 10_000 || sources.supplierRows.size > 10_000) return false
        val byId = rows.associateBy { it.id }
        if (byId.size != rows.size || rows.any { it.id.isBlank() || it.store.isBlank() || it.supplier.isBlank() ||
                it.store != supplierOrderIdentity(it.store) || it.supplier != supplierOrderIdentity(it.supplier) }) return false
        fun validScope(scope: Map<String, Set<String>>, seller: Boolean) = scope.all { (owner, ids) ->
            owner.isNotBlank() && owner == supplierOrderIdentity(owner) && ids.size <= 50_000 && ids.all { id ->
                byId[id]?.let { owner == (if (seller) it.supplier else it.store) } == true
            }
        }
        return validScope(sources.storeRows, false) && validScope(sources.supplierRows, true) &&
            sources.buyerIds + sources.sellerIds == byId.keys
    }

    fun replace(scope: SupplierCommercialScope, incoming: List<SupplierCommercialRow<T>>,
        prefer: (T, T) -> T = { _, fresh -> fresh }): SupplierCommercialWorkspace<T>? {
        if (!scope.valid || !validIncoming(incoming) || incoming.any { row ->
                (scope.owner != null && scope.owner != (if (scope.supplierSide) row.supplier else row.store)) ||
                    (scope.peer != null && scope.peer != (if (scope.supplierSide) row.store else row.supplier))
            }) return null
        val nextSources = if (scope.owner == null) sources.copy(
            supplierRows = incoming.groupBy { it.supplier }.mapValues { (_, values) -> values.map { it.id }.toSet() },
            allSuppliersLoaded = true
        ) else {
            val source = if (scope.supplierSide) sources.supplierRows else sources.storeRows
            val keep = if (scope.peer == null) emptySet() else {
                val replaced = rows.filter { (if (scope.supplierSide) it.store else it.supplier) == scope.peer }.map { it.id }.toSet()
                source[scope.owner].orEmpty() - replaced
            }
            val changed = source + (scope.owner!! to (keep + incoming.map { it.id }))
            if (scope.supplierSide) sources.copy(supplierRows = changed) else sources.copy(storeRows = changed)
        }
        return rebuild(nextSources, incoming, prefer)?.copy(readScopes = readScopes + scope)
    }

    fun upsert(incoming: SupplierCommercialRow<T>, supplierSide: Boolean,
        prefer: (T, T) -> T = { _, fresh -> fresh }): SupplierCommercialWorkspace<T>? {
        if (!validIncoming(listOf(incoming))) return null
        val source = if (supplierSide) sources.supplierRows else sources.storeRows
        val owner = if (supplierSide) incoming.supplier else incoming.store
        val changed = source + (owner to (source[owner].orEmpty() + incoming.id))
        return rebuild(if (supplierSide) sources.copy(supplierRows = changed) else sources.copy(storeRows = changed), listOf(incoming), prefer)
    }

    fun remove(id: String) = copy(removedIds = removedIds + id, rows = rows.filterNot { it.id == id }, sources = sources.copy(
        storeRows = sources.storeRows.mapValues { it.value - id },
        supplierRows = sources.supplierRows.mapValues { it.value - id }))

    fun withoutRecovery() = copy(readScopes = emptySet(), removedIds = emptySet())

    fun recoverFrom(cached: SupplierCommercialWorkspace<T>, prefer: (T, T) -> T = { _, fresh -> fresh }): SupplierCommercialWorkspace<T>? {
        var merged = cached.rebuild(cached.sources.union(sources), rows, prefer) ?: return null
        for (scope in readScopes) {
            val ids = if (scope.supplierSide) sources.sellerIds else sources.buyerIds
            val incoming = rows.filter { it.id in ids &&
                (scope.owner == null || scope.owner == (if (scope.supplierSide) it.supplier else it.store)) &&
                (scope.peer == null || scope.peer == (if (scope.supplierSide) it.store else it.supplier)) }
            merged = merged.replace(scope, incoming, prefer) ?: return null
        }
        for (id in removedIds) merged = merged.remove(id)
        return merged.withoutRecovery()
    }

    private fun rebuild(sources: SupplierWorkspaceSources, incoming: List<SupplierCommercialRow<T>>,
        prefer: (T, T) -> T): SupplierCommercialWorkspace<T>? {
        val keep = sources.buyerIds + sources.sellerIds
        val previous = rows.associateBy { it.id }
        val changed = incoming.map { it.id }.toSet()
        val merged = rows.filter { it.id in keep && it.id !in changed } + incoming.map { next ->
            previous[next.id]?.let { old -> next.copy(value = prefer(old.value, next.value)) } ?: next
        }
        return SupplierCommercialWorkspace(merged, sources, readScopes, removedIds - changed).takeIf { it.valid() }
    }

    private fun validIncoming(incoming: List<SupplierCommercialRow<T>>): Boolean = incoming.size <= 50_000 &&
        incoming.map { it.id }.distinct().size == incoming.size && incoming.all { it.id.isNotBlank() && it.store.isNotBlank() && it.supplier.isNotBlank() }
}

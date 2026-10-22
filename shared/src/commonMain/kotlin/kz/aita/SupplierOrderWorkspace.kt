package kz.aita

import kotlinx.serialization.Serializable

internal fun supplierOrderIdentity(value: String): String = value.trim().lowercase()

/** An order can belong to both desks. Membership comes from an authenticated endpoint response,
 * never from cached profile ownership or from whichever app mode happens to be open now. */
@Serializable
internal data class SupplierWorkspaceSources(
    val storeRows: Map<String, Set<String>> = emptyMap(),
    val supplierRows: Map<String, Set<String>> = emptyMap(),
    val allSuppliersLoaded: Boolean = false
) {
    val storeLoaded: Boolean get() = storeRows.isNotEmpty()
    val supplierLoaded: Boolean get() = allSuppliersLoaded || supplierRows.isNotEmpty()
    val buyerIds: Set<String> get() = storeRows.values.flatten().toSet()
    val sellerIds: Set<String> get() = supplierRows.values.flatten().toSet()
}

internal sealed class SupplierOrderReadScope {
    data class Store(val id: String) : SupplierOrderReadScope()
    data class Supplier(val id: String) : SupplierOrderReadScope()
    data object AllSuppliers : SupplierOrderReadScope()
}

internal enum class SupplierOrderMutationSide { Store, Supplier }

/** Canonical rows are shared when the same account is buyer and seller, but visibility and
 * authoritative replacement are separate. A seller's empty response must not erase procurement. */
internal data class SupplierOrderWorkspace(
    val orders: List<SupplierOrderDataModel> = emptyList(),
    val lines: List<SupplierOrderLineDataModel> = emptyList(),
    val sources: SupplierWorkspaceSources = SupplierWorkspaceSources(),
    // In-memory recovery overlay only; never serialized. A mutation is not a full source read.
    val readScopes: Set<SupplierOrderReadScope> = emptySet(),
    val removedIds: Set<String> = emptySet()
) {
    val buyerOrders: List<SupplierOrderDataModel> get() = ordersFor(sources.buyerIds)
    val buyerLines: List<SupplierOrderLineDataModel> get() = linesFor(sources.buyerIds)
    val sellerOrders: List<SupplierOrderDataModel> get() = ordersFor(sources.sellerIds)
    val sellerLines: List<SupplierOrderLineDataModel> get() = linesFor(sources.sellerIds)

    private fun ordersFor(ids: Set<String>) = orders.filter { supplierOrderIdentity(it.id) in ids }
    private fun linesFor(ids: Set<String>) = lines.filter { supplierOrderIdentity(it.orderId) in ids }

    fun valid(): Boolean {
        if (orders.size > 10_000 || lines.size > 50_000 ||
            sources.storeRows.size > 10_000 || sources.supplierRows.size > 10_000) return false
        val byId = orders.associateBy { supplierOrderIdentity(it.id) }
        if (byId.size != orders.size || byId.keys.any(String::isBlank)) return false
        if (orders.any { it.storeId.isBlank() || it.supplierId.isBlank() }) return false
        val lineIds = lines.map { supplierOrderIdentity(it.id) }.filter(String::isNotBlank)
        if (lineIds.distinct().size != lineIds.size || lines.any { supplierOrderIdentity(it.orderId) !in byId }) return false
        fun validSources(values: Map<String, Set<String>>, seller: Boolean): Boolean = values.all { (scope, ids) ->
            scope.isNotBlank() && scope == supplierOrderIdentity(scope) && ids.size <= 10_000 && ids.all { id ->
                id == supplierOrderIdentity(id) && byId[id]?.let { order ->
                    scope == supplierOrderIdentity(if (seller) order.supplierId else order.storeId) &&
                        (!seller || order.status != SupplierOrderStatusDataModel.Draft)
                } == true
            }
        }
        return validSources(sources.storeRows, false) && validSources(sources.supplierRows, true) &&
            sources.buyerIds + sources.sellerIds == byId.keys
    }

    /** Null denotes a malformed successful response: preserve the last coherent snapshot. */
    fun replace(scope: SupplierOrderReadScope, incoming: List<SupplierOrderWithLinesDataModel>): SupplierOrderWorkspace? {
        if (!validBundles(incoming)) return null
        val store = (scope as? SupplierOrderReadScope.Store)?.id?.let(::supplierOrderIdentity)
        val supplier = (scope as? SupplierOrderReadScope.Supplier)?.id?.let(::supplierOrderIdentity)
        if (store == "" || supplier == "") return null
        if (incoming.any { bundle ->
                when (scope) {
                    is SupplierOrderReadScope.Store -> supplierOrderIdentity(bundle.order.storeId) != store
                    is SupplierOrderReadScope.Supplier -> supplierOrderIdentity(bundle.order.supplierId) != supplier ||
                        bundle.order.status == SupplierOrderStatusDataModel.Draft
                    SupplierOrderReadScope.AllSuppliers -> bundle.order.status == SupplierOrderStatusDataModel.Draft
                }
            }) return null
        val ids = incoming.map { supplierOrderIdentity(it.order.id) }.toSet()
        val nextSources = when (scope) {
            is SupplierOrderReadScope.Store -> sources.copy(storeRows = sources.storeRows + (store!! to ids))
            is SupplierOrderReadScope.Supplier -> sources.copy(supplierRows = sources.supplierRows + (supplier!! to ids))
            SupplierOrderReadScope.AllSuppliers -> sources.copy(
                supplierRows = incoming.groupBy { supplierOrderIdentity(it.order.supplierId) }
                    .mapValues { (_, bundles) -> bundles.map { supplierOrderIdentity(it.order.id) }.toSet() },
                allSuppliersLoaded = true
            )
        }
        return withIncoming(nextSources, incoming)?.copy(readScopes = readScopes + scope)
    }

    /** A successful write is authoritative only for its explicit requesting commercial side. */
    fun upsert(incoming: List<SupplierOrderWithLinesDataModel>, side: SupplierOrderMutationSide): SupplierOrderWorkspace? {
        if (!validBundles(incoming) || (side == SupplierOrderMutationSide.Supplier &&
                incoming.any { it.order.status == SupplierOrderStatusDataModel.Draft })) return null
        val grouped = incoming.groupBy { supplierOrderIdentity(if (side == SupplierOrderMutationSide.Store) it.order.storeId else it.order.supplierId) }
        val existing = if (side == SupplierOrderMutationSide.Store) sources.storeRows else sources.supplierRows
        val updated = existing.toMutableMap()
        for ((key, bundles) in grouped) updated[key] = existing[key].orEmpty() + bundles.map { supplierOrderIdentity(it.order.id) }
        return withIncoming(if (side == SupplierOrderMutationSide.Store) sources.copy(storeRows = updated)
            else sources.copy(supplierRows = updated), incoming)
    }

    fun remove(orderId: String): SupplierOrderWorkspace {
        val id = supplierOrderIdentity(orderId)
        return copy(removedIds = removedIds + id, orders = orders.filterNot { supplierOrderIdentity(it.id) == id },
            lines = lines.filterNot { supplierOrderIdentity(it.orderId) == id },
            sources = sources.copy(storeRows = sources.storeRows.mapValues { it.value - id },
                supplierRows = sources.supplierRows.mapValues { it.value - id }))
    }

    fun withoutRecovery() = copy(readScopes = emptySet(), removedIds = emptySet())

    /** Reconcile a delayed disk read with authenticated work completed while storage was down. */
    fun recoverFrom(cached: SupplierOrderWorkspace): SupplierOrderWorkspace? {
        val byOrder = lines.groupBy { supplierOrderIdentity(it.orderId) }
        val bundles = orders.map { SupplierOrderWithLinesDataModel(it, byOrder[supplierOrderIdentity(it.id)].orEmpty()) }
        var merged = cached.withIncoming(cached.sources.union(sources), bundles) ?: return null
        for (scope in readScopes) {
            val ids = when (scope) {
                is SupplierOrderReadScope.Store -> sources.storeRows[supplierOrderIdentity(scope.id)].orEmpty()
                is SupplierOrderReadScope.Supplier -> sources.supplierRows[supplierOrderIdentity(scope.id)].orEmpty()
                SupplierOrderReadScope.AllSuppliers -> sources.sellerIds
            }
            merged = merged.replace(scope, bundles.filter { supplierOrderIdentity(it.order.id) in ids }) ?: return null
        }
        for (id in removedIds) merged = merged.remove(id)
        return merged.withoutRecovery()
    }

    private fun withIncoming(nextSources: SupplierWorkspaceSources, incoming: List<SupplierOrderWithLinesDataModel>): SupplierOrderWorkspace? {
        val retained = nextSources.buyerIds + nextSources.sellerIds
        val changed = incoming.map { supplierOrderIdentity(it.order.id) }.toSet()
        return SupplierOrderWorkspace(
            orders = orders.filter { supplierOrderIdentity(it.id) in retained && supplierOrderIdentity(it.id) !in changed } + incoming.map { it.order },
            lines = lines.filter { supplierOrderIdentity(it.orderId) in retained && supplierOrderIdentity(it.orderId) !in changed } + incoming.flatMap { it.lines },
            sources = nextSources, readScopes = readScopes, removedIds = removedIds - changed
        ).takeIf { it.valid() }
    }

    private fun validBundles(bundles: List<SupplierOrderWithLinesDataModel>): Boolean {
        if (bundles.size > 10_000 || bundles.sumOf { it.lines.size } > 50_000) return false
        val ids = bundles.map { supplierOrderIdentity(it.order.id) }
        return ids.none(String::isBlank) && ids.distinct().size == ids.size && bundles.all { bundle ->
            bundle.order.storeId.isNotBlank() && bundle.order.supplierId.isNotBlank() &&
                bundle.lines.all { supplierOrderIdentity(it.orderId) == supplierOrderIdentity(bundle.order.id) }
        }
    }
}

/** Union is safe for individual mutations; only a recorded authenticated read replaces a source. */
internal fun SupplierWorkspaceSources.union(other: SupplierWorkspaceSources): SupplierWorkspaceSources {
    fun merged(a: Map<String, Set<String>>, b: Map<String, Set<String>>) =
        (a.keys + b.keys).associateWith { a[it].orEmpty() + b[it].orEmpty() }
    return SupplierWorkspaceSources(merged(storeRows, other.storeRows), merged(supplierRows, other.supplierRows),
        allSuppliersLoaded || other.allSuppliersLoaded)
}

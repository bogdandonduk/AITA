package kz.aita

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** Null means not fetched yet; an authoritative empty list is different. Orders and their lines
 * share one atomic chunk manifest, so a process interruption cannot restore half an order. */
@Serializable
internal data class SupplierWorkspaceSnapshot(
    val accountId: String,
    val orders: List<SupplierOrderDataModel>? = null,
    val lines: List<SupplierOrderLineDataModel>? = null,
    val contracts: List<SupplierPartnershipContractDataModel>? = null,
    val prices: List<SupplierGoodsPriceDataModel>? = null,
    val schema: Int = 1,
    val orderSources: SupplierWorkspaceSources? = null,
    val contractSources: SupplierWorkspaceSources? = null,
    val priceSources: SupplierWorkspaceSources? = null
) {
    fun orderWorkspace(): SupplierOrderWorkspace? = if (orders != null && lines != null && orderSources != null)
        SupplierOrderWorkspace(orders, lines, orderSources).takeIf { it.valid() } else null
    fun contractWorkspace(): SupplierCommercialWorkspace<SupplierPartnershipContractDataModel>? =
        if (contracts != null && contractSources != null) SupplierCommercialWorkspace(contracts.map { it.commercialRow() }, contractSources).takeIf { it.valid() } else null
    fun priceWorkspace(): SupplierCommercialWorkspace<SupplierGoodsPriceDataModel>? =
        if (prices != null && priceSources != null) SupplierCommercialWorkspace(prices.map { it.commercialRow() }, priceSources).takeIf { it.valid() } else null

    fun validFor(account: String): Boolean {
        if (schema != 1 || accountId != account || account.isBlank() ||
            (orders == null) != (lines == null) || (orders == null) != (orderSources == null) ||
            (contracts == null) != (contractSources == null) || (prices == null) != (priceSources == null) || orders.orEmpty().size > 10_000 ||
            lines.orEmpty().size > 50_000 || contracts.orEmpty().size > 10_000 || prices.orEmpty().size > 50_000) return false
        fun List<String>.validIds(): Boolean = all { it.isNotBlank() } && distinct().size == size
        val orderIds = orders.orEmpty().map { it.id }
        val orderSet = orderIds.toSet()
        // Old imported lines/prices may have no row ID; their ordering/relationship is still
        // meaningful. Reject conflicting non-empty IDs without discarding those legacy records.
        return (orders == null || orderWorkspace() != null) && (contracts == null || contractWorkspace() != null) &&
            (prices == null || priceWorkspace() != null) && orderIds.validIds() && lines.orEmpty().map { it.id }.filter(String::isNotBlank).validIds() &&
            lines.orEmpty().all { it.orderId in orderSet } &&
            contracts.orEmpty().map { it.id }.validIds() && prices.orEmpty().map { it.id }.filter(String::isNotBlank).validIds()
    }
}

internal data class SupplierWorkspaceRestore(val snapshot: SupplierWorkspaceSnapshot?, val readable: Boolean)

/** Bounded, account-owned cache. Only successful server publications call update. Failed writes
 * leave the previous chunk generation readable; network failures never replace data with empty. */
internal class SupplierWorkspaceCache(
    private val read: suspend (String) -> String?,
    private val write: suspend (String, String) -> Unit,
    private val remove: suspend (String) -> Unit
) {
    private val mutex = Mutex()
    private fun key(account: String) = "supplier-workspace-v1:$account"
    private class NewerCacheSchema : Exception()
    private class OversizedCacheSnapshot : Exception()
    private class CacheStorageReadFailure : Exception()
    private suspend fun readStored(account: String): String? = try { read(key(account)) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { throw CacheStorageReadFailure() }
    private suspend fun readSnapshot(account: String): SupplierWorkspaceSnapshot? =
        readStored(account)?.let {
            if (it.length > MAX_CHARACTERS) throw OversizedCacheSnapshot()
            // Check the envelope before decoding rows. A later schema may change their types, so
            // typed decoding must not turn an unsupported version into a recoverable corrupt v1.
            val envelope = jsonBase.parseToJsonElement(it) as? JsonObject
            if ((envelope?.get("schema") as? JsonPrimitive)?.intOrNull?.let { version -> version > 1 } == true)
                throw NewerCacheSchema()
            val decoded = jsonBase.decodeFromString<SupplierWorkspaceSnapshot>(it)
            if (decoded.schema > 1) throw NewerCacheSchema()
            decoded.takeIf { value -> value.validFor(account) }
        }

    suspend fun restore(owner: SupplierSessionOwner): SupplierWorkspaceRestore = mutex.withLock {
        if (!owner.isCurrent()) return@withLock SupplierWorkspaceRestore(null, false)
        try {
            val snapshot = readSnapshot(owner.accountId)
            SupplierWorkspaceRestore(snapshot.takeIf { owner.isCurrent() }, owner.isCurrent())
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: CacheStorageReadFailure) { SupplierWorkspaceRestore(null, false) }
        catch (_: NewerCacheSchema) { SupplierWorkspaceRestore(null, false) }
        catch (_: OversizedCacheSnapshot) { SupplierWorkspaceRestore(null, false) }
        // A readable but malformed current format can be repaired from fresh authenticated data.
        catch (_: Exception) { SupplierWorkspaceRestore(null, owner.isCurrent()) }
    }

    suspend fun cached(owner: SupplierSessionOwner): SupplierWorkspaceSnapshot? = restore(owner).snapshot

    suspend fun update(owner: SupplierSessionOwner, change: (SupplierWorkspaceSnapshot) -> SupplierWorkspaceSnapshot): Boolean = mutex.withLock {
        if (!owner.isCurrent()) return@withLock false
        try {
            val previous = try { readSnapshot(owner.accountId) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: NewerCacheSchema) { return@withLock false }
            catch (_: OversizedCacheSnapshot) { return@withLock false }
            catch (_: CacheStorageReadFailure) { return@withLock false }
            catch (_: Exception) { null }
            if (!owner.isCurrent()) return@withLock false
            val next = change(previous ?: SupplierWorkspaceSnapshot(owner.accountId))
            if (!next.validFor(owner.accountId)) return@withLock false
            val encoded = jsonBase.encodeToString(next)
            if (encoded.length > MAX_CHARACTERS || !owner.isCurrent()) return@withLock false
            write(key(owner.accountId), encoded)
            // A successful cache status must mean the committed manifest can be read back.
            check(read(key(owner.accountId)) == encoded)
            owner.isCurrent()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { false }
    }

    suspend fun clear(account: String) = mutex.withLock { remove(key(account)) }

    private companion object { const val MAX_CHARACTERS = 16 * 1024 * 1024 }
}

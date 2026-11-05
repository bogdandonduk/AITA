package kz.aita

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

@Serializable
private data class MarketOfferDetailCacheData(val accountId: String, val schema: Int = 1,
    val details: List<MarketOfferDetailResult> = emptyList())

/** Only validated PUBLIC product snapshots, never merchant DTOs or checkout/contact data.
 * Keep a bounded recent set per account. A cached detail is read-only: it is never evidence of
 * current price, availability or saved-bookmark state and does not carry a freshness stamp.
 */
class MarketOfferDetailCache(
    private val read: suspend (String) -> String?,
    private val write: suspend (String, String?) -> Unit,
    private val maximumEntries: Int = 24,
    private val maximumBytes: Int = 2_000_000,
    private val maximumReads: Int = 128
) {
    init { require(maximumEntries in 1..100 && maximumBytes > 0 && maximumReads > 0) }
    private val mutex = Mutex()
    private fun key(account: String) = "market-offer-details-v1:$account"
    private data class Slot(val account: String, val offer: String)
    private class Read(val slot: Slot, val generation: Long)
    private val latest = linkedMapOf<Slot, Read>()
    // If a withdrawal cannot be erased durably (including cancellation during deletion), an
    // old disk snapshot must never undo it. Fall closed globally rather than accumulating an
    // unbounded per-offer tombstone set. Only fresh, successfully saved responses can recover
    // individual slots for the rest of this process; evicting an allowlist entry is safe.
    private var savedSnapshotsUntrusted = false
    private val revalidated = linkedSetOf<Slot>()
    private fun revokeStorageTrust() { savedSnapshotsUntrusted = true; revalidated.clear() }

    private suspend fun stored(account: String): MarketOfferDetailCacheData {
        val raw = read(key(account)) ?: return MarketOfferDetailCacheData(account)
        if (raw.length > maximumBytes || raw.encodeToByteArray().size > maximumBytes) return MarketOfferDetailCacheData(account)
        val saved = try { jsonBase.decodeFromString<MarketOfferDetailCacheData>(raw) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { return MarketOfferDetailCacheData(account) }
        return saved.takeIf { it.schema == 1 && it.accountId == account && it.details.size <= maximumEntries &&
            it.details.map { detail -> detail.offer.id }.distinct().size == it.details.size &&
            it.details.all { detail -> detail.isValidOfferDetailResult(account, detail.offer.id) }
        } ?: MarketOfferDetailCacheData(account)
    }

    suspend fun cached(owner: MarketAccountScope, id: String): MarketOfferDetailResult? = mutex.withLock {
        val normalized = marketDiscoveryId(id) ?: return@withLock null
        if (!owner.isCurrent() || (savedSnapshotsUntrusted && Slot(owner.accountId, normalized) !in revalidated)) return@withLock null
        try { stored(owner.accountId).details.firstOrNull { it.offer.id == normalized }?.takeIf { owner.isCurrent() } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
    }

    private suspend fun begin(owner: MarketAccountScope, id: String): Read? = mutex.withLock {
        if (!owner.isCurrent()) return@withLock null
        val normalized = marketDiscoveryId(id) ?: return@withLock null
        val token = Read(Slot(owner.accountId, normalized), owner.generation)
        latest.remove(token.slot)
        latest[token.slot] = token
        while (latest.size > maximumReads) latest.remove(latest.keys.first())
        token
    }
    private suspend fun finish(token: Read) = mutex.withLock {
        if (latest[token.slot] === token) latest.remove(token.slot)
    }
    private suspend fun accept(owner: MarketAccountScope, token: Read, response: ResponseDataModel<MarketOfferDetailResult>) = mutex.withLock {
        if (!owner.isCurrent()) return@withLock
        val withdrawn = !response.transportFailure && (response.httpStatusCode in setOf(401, 403) ||
            (response.httpStatusCode == 404 && response.message?.eventMessageReferenceOrNull()?.key == "market.unavailable"))
        if (withdrawn) {
            // A denial from even an older request revokes the snapshot and every already-running
            // read for this offer. A late success must not silently resurrect it.
            latest.remove(token.slot)
            revalidated.remove(token.slot)
            try {
                val saved = stored(owner.accountId)
                val next = saved.copy(details = saved.details.filterNot { it.offer.id == token.slot.offer })
                if (!owner.isCurrent()) return@withLock
                write(key(owner.accountId), next.takeIf { it.details.isNotEmpty() }?.let { jsonBase.encodeToString(it) })
            } catch (failure: Exception) { revokeStorageTrust(); throw failure }
            return@withLock
        }
        val value = response.payload
        if (response.negative || response.transportFailure || response.httpStatusCode != 200 ||
            latest[token.slot] !== token || token.generation != owner.generation ||
            value?.isValidOfferDetailResult(owner.accountId, token.slot.offer) != true) return@withLock
        val saved = stored(owner.accountId)
        if (!owner.isCurrent()) return@withLock
        var next = saved.copy(details = (listOf(value) + saved.details.filterNot { it.offer.id == value.offer.id }).take(maximumEntries))
        var encoded = jsonBase.encodeToString(next)
        while (encoded.encodeToByteArray().size > maximumBytes && next.details.size > 1) {
            next = next.copy(details = next.details.dropLast(1)); encoded = jsonBase.encodeToString(next)
        }
        check(encoded.encodeToByteArray().size <= maximumBytes) { "Product detail is too large to save" }
        write(key(owner.accountId), encoded)
        if (savedSnapshotsUntrusted) {
            revalidated.remove(token.slot); revalidated.add(token.slot)
            while (revalidated.size > maximumReads) revalidated.remove(revalidated.first())
        }
    }

    suspend fun refresh(owner: MarketAccountScope, id: String,
        readDetail: suspend (String) -> ResponseDataModel<MarketOfferDetailResult>): ResponseDataModel<MarketOfferDetailResult> {
        val token = begin(owner, id) ?: return readOwnedMarketOfferDetail(owner, id, readDetail)
        try {
            val response = readOwnedMarketOfferDetail(owner, id, readDetail)
            try { accept(owner, token, response) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                // A disk error must not replace a valid network response with an empty screen.
                if (!response.negative && owner.isCurrent()) return response.copy(message = eventMessage("market.detail_cache_warning"))
            }
            return if (owner.isCurrent()) response else cloudSessionExpiredResponse()
        } finally { withContext(NonCancellable) { finish(token) } }
    }

    suspend fun clear(account: String) = mutex.withLock {
        latest.keys.filter { it.account == account }.forEach(latest::remove)
        revalidated.removeAll { it.account == account }
        try { write(key(account), null) }
        catch (failure: Exception) { revokeStorageTrust(); throw failure }
    }
}

internal val marketOfferDetailCache = MarketOfferDetailCache(::readJsonCacheText, { key, value ->
    if (value == null) deleteJsonCacheText(key) else writeJsonCacheText(key, value)
})
suspend fun cachedMarketOfferDetail(owner: MarketAccountScope, id: String): MarketOfferDetailResult? =
    withContext(Dispatchers.ourIo) { marketOfferDetailCache.cached(owner, id) }
internal suspend fun clearMarketOfferDetailDeviceData(account: String) =
    withContext(Dispatchers.ourIo) { marketOfferDetailCache.clear(account) }

package kz.aita

import io.ktor.http.HttpMethod
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

fun canUseMarketPriceInsights(storeId: String?): Boolean =
    currentUserCanViewStock(storeId) && currentUserCanViewAnalytics(storeId)

@Serializable
internal data class MarketPriceCacheEntry(val current: MarketPriceInsightsPage, val previous: MarketPriceInsightsPage? = null)

@Serializable
internal data class MarketPriceCacheSnapshot(
    val accountId: String,
    val storeId: String,
    val origin: String,
    val schema: Int = 1,
    val denied: Boolean = false,
    val selection: MarketPriceRequest = MarketPriceRequest(),
    val entries: List<MarketPriceCacheEntry> = emptyList(),
) {
    fun valid(account: String, store: String, server: String): Boolean =
        schema == 1 && accountId == account && storeId == store && origin == server &&
            marketDiscoveryId(account) == account && marketDiscoveryId(store) == store &&
            selection.normalizedMarketPriceRequest() == selection && selection.after == null &&
            entries.size <= 8 && (!denied || entries.isEmpty()) &&
            entries.map { it.current.request }.distinct().size == entries.size &&
            entries.all { entry -> entry.current.isValidMarketPricePage(account, store, entry.current.request) &&
                (entry.previous == null || (entry.previous.checkedAtMillis < entry.current.checkedAtMillis &&
                    entry.previous.isValidMarketPricePage(account, store, entry.current.request))) }
}

/** One atomic, bounded journal per account/store/server. An update never invalidates its key. */
internal class MarketPriceCache(
    private val read: suspend (String) -> String?,
    private val write: suspend (String, String) -> Unit,
) {
    private val mutex = Mutex()
    private val denied = mutableSetOf<String>()
    private val revocations = mutableMapOf<String, Long>()
    // Keep only one decoded journal. A report refresh reads the same bounded document for
    // the selector, fallback and history; parsing it repeatedly is expensive on WebAssembly.
    // Always read the durable text first so external changes/tombstones remain authoritative.
    private data class Decoded(val key: String, val text: String, val snapshot: MarketPriceCacheSnapshot)
    private var decoded: Decoded? = null
    private class StorageUnavailable : Exception()
    private class NewerSchema : Exception()
    private class OversizedSnapshot : Exception()
    private fun key(account: String, store: String, origin: String): String {
        require(marketDiscoveryId(account) == account && marketDiscoveryId(store) == store)
        require(origin.isNotBlank() && origin.length <= 2048 && origin.none { it.code < 32 })
        return "market-price-cache-v1:$account:$store:${origin.length}:$origin"
    }
    private suspend fun stored(account: String, store: String, origin: String): MarketPriceCacheSnapshot? {
        val cacheKey = key(account, store, origin)
        val raw = try { read(cacheKey) }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { throw StorageUnavailable() }
        if (raw == null) { decoded = null; return null }
        decoded?.takeIf { it.key == cacheKey && it.text == raw }?.let {
            // A tombstone can have identical bytes after access was restored and revoked
            // again. The decoded memo must still observe that new denial.
            if (it.snapshot.denied && denied.add(cacheKey)) revocations[cacheKey] = (revocations[cacheKey] ?: 0L) + 1
            return it.snapshot
        }
        decoded = null
        if (raw.length > 2_000_000) throw OversizedSnapshot()
        val envelope = jsonBase.parseToJsonElement(raw) as? JsonObject ?: error("Invalid cached price report")
        // A downgraded application must not overwrite a cache written by a newer format.
        if (((envelope["schema"] as? JsonPrimitive)?.intOrNull ?: 1) > 1) throw NewerSchema()
        return jsonBase.decodeFromJsonElement<MarketPriceCacheSnapshot>(envelope).also {
            require(it.valid(account, store, origin))
            // A second browser tab/process can revoke the shared durable journal.
            // Observing its valid tombstone also invalidates this instance's older reads.
            if (it.denied && denied.add(cacheKey)) revocations[cacheKey] = (revocations[cacheKey] ?: 0L) + 1
            decoded = Decoded(cacheKey, raw, it)
        }
    }
    suspend fun restore(account: String, store: String, origin: String): MarketPriceCacheSnapshot? = mutex.withLock {
        if (key(account, store, origin) in denied) return@withLock null
        stored(account, store, origin)?.takeUnless { it.denied }
    }
    suspend fun revision(account: String, store: String, origin: String): Long = mutex.withLock {
        // Observe external denial before capturing a new request's baseline as well as
        // after transport completes. This also protects an offline cached fallback.
        try { stored(account, store, origin) }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { /* A storage failure is reported by restore/accept. */ }
        revocations[key(account, store, origin)] ?: 0L
    }
    suspend fun accept(page: MarketPriceInsightsPage, origin: String, expectedRevision: Long? = null,
        current: () -> Boolean): MarketPriceCacheEntry? = mutex.withLock {
        require(page.isValidMarketPricePage(page.accountId, page.storeId, page.request))
        if (!current()) return@withLock null
        val key = key(page.accountId, page.storeId, origin)
        if (expectedRevision != null && expectedRevision != (revocations[key] ?: 0L)) return@withLock null
        val old = try { stored(page.accountId, page.storeId, origin)?.takeUnless { it.denied || key in denied } }
            catch (cancel: CancellationException) { throw cancel }
            catch (problem: StorageUnavailable) { throw problem }
            catch (problem: NewerSchema) { throw problem }
            catch (problem: OversizedSnapshot) { throw problem }
            catch (_: Exception) { null } // Only a readable malformed v1 cache may be replaced by verified fresh data.
        if (expectedRevision != null && expectedRevision != (revocations[key] ?: 0L)) return@withLock null
        val existing = old?.entries?.find { it.current.request == page.request }
        // Do not regress a newer report after concurrent reads finish out of order.
        if (existing != null && existing.current.checkedAtMillis > page.checkedAtMillis) return@withLock existing
        val previous = existing?.let {
            if (page.checkedAtMillis - it.current.checkedAtMillis >= 60_000) it.current else it.previous
        }
        val entry = MarketPriceCacheEntry(page, previous)
        val next = MarketPriceCacheSnapshot(page.accountId, page.storeId, origin,
            selection = page.request.copy(after = null, itemId = null, gtin = null),
            entries = (listOf(entry) + old?.entries.orEmpty().filter { it.current.request != page.request }).take(8))
        val encoded = jsonBase.encodeToString(next)
        require(encoded.length <= 2_000_000 && next.valid(page.accountId, page.storeId, origin))
        if (!current()) return@withLock null
        write(key, encoded)
        check(read(key) == encoded)
        decoded = Decoded(key, encoded, next)
        denied.remove(key)
        entry.takeIf { current() }
    }
    suspend fun revoke(account: String, store: String, origin: String) = mutex.withLock {
        val key = key(account, store, origin)
        // Even if the disk is full, later reads in this process cannot restore a denied report.
        denied += key
        revocations[key] = (revocations[key] ?: 0L) + 1
        write(key, jsonBase.encodeToString(MarketPriceCacheSnapshot(account, store, origin, denied = true)))
        check(stored(account, store, origin)?.denied == true)
    }
    suspend fun clearAccount(account: String, remove: suspend (String) -> Unit) = mutex.withLock {
        require(marketDiscoveryId(account) == account)
        val prefix = "market-price-cache-v1:$account:"
        remove(prefix)
        if (decoded?.key?.startsWith(prefix) == true) decoded = null
        denied.removeAll { it.startsWith(prefix) }
        revocations.keys.filter { it.startsWith(prefix) }.forEach { revocations[it] = revocations.getValue(it) + 1 }
    }
}

data class MarketPriceLoad(
    val page: MarketPriceInsightsPage? = null,
    val previous: MarketPriceInsightsPage? = null,
    val cached: Boolean = false,
    val message: List<LocalizedStringDataModel>? = null,
    val accessDenied: Boolean = false,
)

private val marketPriceCache = MarketPriceCache(::readRequiredJsonJournal, ::writeJsonCacheText)
private fun priceOrigin(): String = globalAppConfigurationState.payloadValue.serverUrl.first.trim().trimEnd('/')

internal suspend fun revokeMarketPriceReports(account: String, store: String, origin: String) =
    marketPriceCache.revoke(account, store, origin.trim().trimEnd('/'))

internal suspend fun clearMarketPriceAccountCache(account: String) = withContext(Dispatchers.ourIo) {
    marketPriceCache.clearAccount(account) { prefix -> appDatabase.app_databaseQueries.deleteKvLogicalPrefix(prefix) }
}

data class SavedMarketPriceReport(val request: MarketPriceRequest, val checkedAtMillis: Long, val itemCount: Int)

suspend fun savedMarketPriceReports(): List<SavedMarketPriceReport> = withContext(Dispatchers.ourIo) {
    val owner = inventoryOwners.current
    val account = owner.accountId ?: return@withContext emptyList()
    val store = owner.storeId ?: return@withContext emptyList()
    val origin = priceOrigin()
    fun current() = inventoryOwnerIsCurrent(owner) && canUseMarketPriceInsights(store) && priceOrigin() == origin
    if (!current()) return@withContext emptyList()
    try {
        val version = marketPriceCache.revision(account, store, origin)
        val snapshot = marketPriceCache.restore(account, store, origin)
        if (!current() || version != marketPriceCache.revision(account, store, origin)) emptyList()
        else snapshot?.entries.orEmpty().map { SavedMarketPriceReport(it.current.request, it.current.checkedAtMillis, it.current.rows.size) }
    } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { emptyList() }
}

suspend fun restoreMarketPriceSelection(): MarketPriceRequest? = withContext(Dispatchers.ourIo) {
    val owner = inventoryOwners.current
    val account = owner.accountId ?: return@withContext null
    val store = owner.storeId ?: return@withContext null
    val origin = priceOrigin()
    if (!inventoryOwnerIsCurrent(owner) || !canUseMarketPriceInsights(store)) return@withContext null
    try {
        marketPriceCache.restore(account, store, origin)?.selection?.takeIf {
            inventoryOwnerIsCurrent(owner) && canUseMarketPriceInsights(store) && priceOrigin() == origin
        }
    } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { null }
}

suspend fun loadMarketPriceInsights(
    requested: MarketPriceRequest,
    onCached: suspend (MarketPriceLoad) -> Unit = {},
): MarketPriceLoad = withContext(Dispatchers.ourIo) {
    val owner = inventoryOwners.current
    val account = owner.accountId ?: return@withContext MarketPriceLoad(message = marketPriceMessage("access"))
    val store = owner.storeId ?: return@withContext MarketPriceLoad(message = marketPriceMessage("access"))
    val origin = priceOrigin()
    fun current() = inventoryOwnerIsCurrent(owner) && canUseMarketPriceInsights(store) && priceOrigin() == origin
    readOwnedMarketPriceReport(account, store, origin, requested, ::current, marketPriceCache, onCached) { request ->
        networkRequest<MarketPriceInsightsPage, Unit>(HttpMethod.Get, endpointUrl = "analytics/market-prices",
            headers = mapOf("store_id" to store), query = buildMap<String, Any> {
                put("country", request.country); put("city", request.city); put("search", request.search); put("limit", request.limit)
                request.itemId?.let { put("itemId", it) }; request.gtin?.let { put("gtin", it) }; request.after?.let { put("after", it) }
            }, expectedSessionGeneration = owner.sessionGeneration)
    }
}

/** Testable transport boundary. A late response never publishes or saves into a new session. */
internal suspend fun readOwnedMarketPriceReport(
    account: String, store: String, origin: String, requested: MarketPriceRequest,
    current: () -> Boolean, cache: MarketPriceCache, onCached: suspend (MarketPriceLoad) -> Unit = {},
    read: suspend (MarketPriceRequest) -> ResponseDataModel<MarketPriceInsightsPage>,
): MarketPriceLoad {
    currentCoroutineContext().ensureActive()
    if (!current()) return MarketPriceLoad(message = marketPriceMessage("access"))
    val request = requested.normalizedMarketPriceRequest()
        ?: return MarketPriceLoad(message = marketPriceMessage("invalid"))
    val readRevision = cache.revision(account, store, origin)
    suspend fun revoked() = cache.revision(account, store, origin) != readRevision
    fun denied() = MarketPriceLoad(message = marketPriceMessage("access"), accessDenied = true)
    var cacheProblem = false
    val saved = try { cache.restore(account, store, origin)?.entries?.find { it.current.request == request } }
        catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { cacheProblem = true; null }
    currentCoroutineContext().ensureActive()
    if (!current()) return MarketPriceLoad()
    if (revoked()) return denied()
    saved?.let { onCached(MarketPriceLoad(it.current, it.previous, cached = true, message = marketPriceMessage("cached"))) }
    currentCoroutineContext().ensureActive()
    if (!current()) return MarketPriceLoad()
    return try {
        val response = withTimeoutOrNull(20_000) { read(request) }
        currentCoroutineContext().ensureActive()
        if (!current()) return MarketPriceLoad()
        if (revoked()) return denied()
        if (response?.httpStatusCode == 403 && !response.transportFailure) {
            try { cache.revoke(account, store, origin) }
            catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { /* Memory denial remains. */ }
            return MarketPriceLoad(message = marketPriceMessage("access"), accessDenied = true)
        }
        val page = response?.payload?.takeIf { response.httpStatusCode == 200 && !response.negative && !response.transportFailure &&
            it.isValidMarketPricePage(account, store, request) }
        if (page != null) {
            val stored = try { cache.accept(page, origin, readRevision, current) }
                catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { cacheProblem = true; null }
            currentCoroutineContext().ensureActive()
            if (!current()) return MarketPriceLoad()
            if (revoked()) return denied()
            val retainedNewer = stored != null && stored.current.checkedAtMillis > page.checkedAtMillis
            MarketPriceLoad(stored?.current ?: page, stored?.previous, cached = retainedNewer,
                message = if (stored == null) marketPriceMessage("storage") else if (retainedNewer) marketPriceMessage("cached") else null)
        } else MarketPriceLoad(saved?.current, saved?.previous, saved != null, response?.message?.takeUnless { response.transportFailure } ?: marketPriceMessage(
            if (cacheProblem) "restore_error" else if (saved != null) "cached" else if (response?.transportFailure == false) "unavailable" else "offline_empty"))
    } catch (cancel: CancellationException) { throw cancel }
    catch (_: Exception) {
        if (revoked()) return denied()
        if (current()) MarketPriceLoad(saved?.current, saved?.previous, saved != null,
            marketPriceMessage(if (cacheProblem) "restore_error" else if (saved != null) "cached" else "offline_empty"))
        else MarketPriceLoad()
    }
}

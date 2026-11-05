package kz.aita

import io.ktor.http.HttpMethod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

fun MarketDeliveryOrder.isValidDeliveryOrder(): Boolean =
    listOf(id,buyerId,branchStoreId).all { marketDiscoveryId(it) == it } &&
        revision >= 0 && createdAtMillis > 0 && updatedAtMillis >= createdAtMillis &&
        estimatedFromMillis > 0 && estimatedToMillis >= estimatedFromMillis &&
        (request.address.isValid(request.mode) || (terminal && contactsRemoved && request.address == MarketDeliveryAddress())) && totalMinor != null && request.feeMinor >= 0 &&
        request.lines.all { it.line.basis.currencyCode == request.currency && it.line.basis.isValidMarketBasis() } &&
        events.size <= 100 && handoverCode?.matches(Regex("[0-9]{6}")) != false

@Serializable internal data class MarketDeliveryJournal(
    val accountId: String, val pending: MarketDeliveryCommand? = null,
    val acknowledged: MarketDeliveryOutcome? = null, val rejectedKey: String? = null
)
data class MarketDeliverySendResult(val pending: MarketDeliveryCommand? = null, val acknowledged: MarketDeliveryOutcome? = null,
    val error: List<LocalizedStringDataModel>? = null)

/** A successful checkout is already durably known even if the following list refresh fails.
 * Expose that single buyer-owned receipt as a labelled fallback, never as a complete/fresh page.
 * Merchant/courier contacts must not be resurrected from an acknowledgement after access denial.
 */
fun MarketDeliverySendResult.confirmedBuyerFallback(accountId: String, view: String,
    filter: MarketDeliveryFilter, page: MarketDeliveryPage?, fresh: Boolean, accessDenied: Boolean,
    before: String?, now: Long): MarketDeliveryOrder? {
    if (view != "buyer" || fresh || accessDenied || before != null || !filter.isValid()) return null
    val order = acknowledged?.order ?: return null
    if (order.buyerId != accountId || !order.isValidDeliveryOrder() || !order.matchesFilter(filter, view, now) ||
        page?.orders?.any { it.id == order.id } == true) return null
    return order.copy(position = null, handoverCode = null)
}

/** Persist before sending, retire only after a matching acknowledgement is durably stored.
 * A network retry never invents a new order ID. One writer also survives screen disposal. */
class MarketDeliveryOutbox(
    private val read: suspend (String) -> String?, private val write: suspend (String, String?) -> Unit,
    private val send: suspend (MarketAccountScope, MarketDeliveryCommand) -> ResponseDataModel<MarketDeliveryOutcome>,
    private val timeoutMillis: Long = 20_000L
) {
    private val mutex = Mutex()
    private fun key(account: String) = "market-delivery-outbox-v1:$account"
    private suspend fun journal(owner: MarketAccountScope): MarketDeliveryJournal {
        val raw = read(key(owner.accountId))
        val journal = raw?.let { jsonBase.decodeFromString<MarketDeliveryJournal>(it) } ?: MarketDeliveryJournal(owner.accountId)
        check(journal.accountId == owner.accountId)
        check(journal.pending?.commandId?.let { marketDiscoveryId(it) == it } != false)
        check(journal.acknowledged?.order?.isValidDeliveryOrder() != false)
        return journal
    }
    private suspend fun save(value: MarketDeliveryJournal) {
        val encoded = jsonBase.encodeToString(value)
        write(key(value.accountId),encoded)
        check(read(key(value.accountId)) == encoded) { "Delivery intent was not saved" }
    }
    suspend fun cached(owner: MarketAccountScope): MarketDeliverySendResult = mutex.withLock {
        if (!owner.isCurrent()) return@withLock MarketDeliverySendResult()
        try {
            val value=journal(owner)
            if (owner.isCurrent()) MarketDeliverySendResult(value.pending,value.acknowledged,value.rejectedKey?.let(::eventMessage)) else MarketDeliverySendResult()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { MarketDeliverySendResult(error=eventMessage("delivery.local_error")) }
    }
    suspend fun submit(owner: MarketAccountScope, command: MarketDeliveryCommand? = null): MarketDeliverySendResult = mutex.withLock {
        if (!owner.isCurrent()) return@withLock MarketDeliverySendResult()
        var value: MarketDeliveryJournal? = null
        var durablePending: MarketDeliveryCommand? = null
        try {
            value=journal(owner)
            durablePending=value.pending
            if (!owner.isCurrent()) return@withLock MarketDeliverySendResult()
            if (command != null) {
                if (value.pending != null && value.pending != command)
                    return@withLock MarketDeliverySendResult(value.pending,error=eventMessage("delivery.pending"))
                value=value.copy(pending=command,acknowledged=null,rejectedKey=null)
                save(value)
                durablePending=command
            }
            val pending=value.pending ?: return@withLock MarketDeliverySendResult(acknowledged=value.acknowledged)
            if (!owner.isCurrent()) return@withLock MarketDeliverySendResult()
            val response=withTimeoutOrNull(timeoutMillis) { send(owner,pending) }
            if (!owner.isCurrent()) return@withLock MarketDeliverySendResult()
            val result=response?.payload
            if (response != null && !response.negative && !response.transportFailure && response.httpStatusCode == 200 &&
                result != null && result.commandId == pending.commandId && result.order.isValidDeliveryOrder() &&
                result.order.id == (pending.orderId ?: pending.commandId) &&
                (pending.action != MarketDeliveryAction.REQUEST || result.order.buyerId == owner.accountId)) {
                // Acknowledged responses do not retain handover codes or live positions.
                val safe=result.copy(order=result.order.copy(handoverCode=null,position=null))
                save(value.copy(pending=null,acknowledged=safe,rejectedKey=null))
                return@withLock MarketDeliverySendResult(acknowledged=safe)
            }
            if (response != null && !response.transportFailure && response.negative && response.httpStatusCode in setOf(400,403,404,409,422)) {
                val key=response.message?.eventMessageReferenceOrNull()?.key?.takeIf { it.startsWith("delivery.") } ?: "delivery.changed"
                save(value.copy(pending=null,acknowledged=null,rejectedKey=key))
                MarketDeliverySendResult(error=response.message ?: eventMessage(key))
            } else MarketDeliverySendResult(pending=pending,error=response?.message ?: eventMessage("delivery.pending"))
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { MarketDeliverySendResult(pending=durablePending,error=eventMessage("delivery.local_error")) }
    }
}

private val marketDeliveryOutbox by lazy { MarketDeliveryOutbox(::readRequiredJsonJournal,{ key,value ->
    if(value==null) deleteJsonCacheText(key) else writeJsonCacheText(key,value)
}, { owner,command ->
    networkRequest(HttpMethod.Post,endpointUrl="market/delivery/commands",body=command,expectedSessionGeneration=owner.generation)
}) }
suspend fun cachedMarketDeliveryCommand(owner: MarketAccountScope) = withContext(Dispatchers.ourIo) { marketDeliveryOutbox.cached(owner) }
suspend fun sendMarketDeliveryCommand(owner: MarketAccountScope, command: MarketDeliveryCommand? = null): MarketDeliverySendResult {
    val result=withContext(Dispatchers.ourIo) { marketDeliveryOutbox.submit(owner,command) }
    if (owner.isCurrent() && result.acknowledged != null) MarketplaceSignals.changed()
    return result
}
suspend fun retryPendingMarketDelivery() {
    val owner=captureMarketRequestScope() ?: return
    if (cachedMarketDeliveryCommand(owner).pending != null) sendMarketDeliveryCommand(owner)
}

suspend fun loadMarketDeliveryPolicy(owner: MarketAccountScope, store: String, seller: Boolean = false): ResponseDataModel<MarketDeliveryPolicy> {
    if (!owner.isCurrent()) return cloudSessionExpiredResponse()
    return networkRequest<MarketDeliveryPolicy,Unit>(HttpMethod.Get,endpointUrl=if (seller) "market/delivery/seller/policy" else "market/delivery/policy/$store",
        headers=if (seller) mapOf("store_id" to store) else emptyMap(),expectedSessionGeneration=owner.generation)
}
suspend fun saveMarketDeliveryPolicy(owner: MarketAccountScope, policy: MarketDeliveryPolicy): ResponseDataModel<MarketDeliveryPolicy> {
    if (!owner.isCurrent()) return cloudSessionExpiredResponse()
    return networkRequest(HttpMethod.Put,endpointUrl="market/delivery/seller/policy",body=policy,expectedSessionGeneration=owner.generation)
}
suspend fun loadMarketDeliveryOrder(owner: MarketAccountScope, id: String): ResponseDataModel<MarketDeliveryOrder> {
    return readOwnedMarketDeliveryOrder(owner,id) {
        networkRequest<MarketDeliveryOrder,Unit>(HttpMethod.Get,endpointUrl="market/delivery/orders/$id",expectedSessionGeneration=owner.generation)
    }
}
suspend fun readOwnedMarketDeliveryOrder(owner: MarketAccountScope, id: String,
    read: suspend ()->ResponseDataModel<MarketDeliveryOrder>): ResponseDataModel<MarketDeliveryOrder> {
    if (!owner.isCurrent() || marketDiscoveryId(id) != id) return cloudSessionExpiredResponse()
    val result=read()
    if (!owner.isCurrent()) return cloudSessionExpiredResponse()
    if(!result.negative && result.payload?.let { it.id==id && it.isValidDeliveryOrder() } != true)
        return ResponseDataModel(eventMessage("delivery.refresh_failed"),null,true,502)
    return result
}

/** One bounded query slot per account/view/store: typing searches cannot grow storage forever.
 * A cached result is usable only for the exact normalized filter that produced it. */
class MarketDeliveryPageCache(private val read: suspend (String)->String?, private val write: suspend (String,String?)->Unit,
    private val maximumInFlightSlots: Int = 128) {
    private val mutex=Mutex()
    class ReadToken internal constructor(internal val slot: String, internal val generation: Long, internal val filter: MarketDeliveryFilter)
    private val latestReads=linkedMapOf<String,ReadToken>()
    init { require(maximumInFlightSlots > 0) }
    private fun key(owner: MarketAccountScope, view: String, store: String?) = "market-delivery-page-v1:${owner.accountId}:$view:${store.orEmpty()}"
    /** Register before networking. Cancellation is not an ordering guarantee: independently
     * running screens can finish old queries after a newer query has already been saved. */
    suspend fun beginRead(owner: MarketAccountScope, view: String, store: String?, filter: MarketDeliveryFilter = MarketDeliveryFilter()): ReadToken? = mutex.withLock {
        if (!owner.isCurrent() || !filter.isValid()) return@withLock null
        val slot=key(owner,view,store)
        val token=ReadToken(slot,owner.generation,filter.normalized())
        latestReads.remove(slot)
        latestReads[slot]=token
        // Only in-flight reads consume memory. Under extreme concurrency, an evicted read can
        // still return its result to its caller but cannot replace the saved query snapshot.
        while(latestReads.size > maximumInFlightSlots) latestReads.remove(latestReads.keys.first())
        token
    }
    suspend fun finishRead(token: ReadToken) = mutex.withLock {
        if(latestReads[token.slot] === token) latestReads.remove(token.slot)
    }
    suspend fun cached(owner: MarketAccountScope, view: String, store: String?, filter: MarketDeliveryFilter = MarketDeliveryFilter()): MarketDeliveryPage? = mutex.withLock {
        if (!owner.isCurrent()) return@withLock null
        val data=runCatching { read(key(owner,view,store))?.let { jsonBase.decodeFromString<MarketDeliveryPage>(it) } }.getOrNull()
        data?.takeIf { owner.isCurrent() && valid(it,owner,view,store,filter) }
    }
    /** Read-only fallback, explicitly a subset of the previous query's saved first page. This
     * keeps pre-update ALL caches useful offline without claiming a complete new search. The
     * original checkedAtMillis is retained; attention uses that last verified server snapshot. */
    suspend fun cachedSubset(owner: MarketAccountScope, view: String, store: String?, filter: MarketDeliveryFilter): MarketDeliveryPage? = mutex.withLock {
        if (!owner.isCurrent() || !filter.isValid()) return@withLock null
        val data=runCatching { read(key(owner,view,store))?.let { jsonBase.decodeFromString<MarketDeliveryPage>(it) } }.getOrNull()
            ?: return@withLock null
        if (!owner.isCurrent() || !valid(data,owner,view,store,data.filter)) return@withLock null
        data.copy(filter=filter.normalized(),nextBefore=null,
            orders=data.orders.filter { it.matchesFilter(filter,view,data.checkedAtMillis) }.map { it.copy(position=null,handoverCode=null) })
    }
    fun valid(page: MarketDeliveryPage, owner: MarketAccountScope, view: String, store: String?, filter: MarketDeliveryFilter = MarketDeliveryFilter()): Boolean =
        view in setOf("buyer","courier","seller") && page.checkedAtMillis>0 &&
            filter.isValid() && page.filter.isValid() && page.filter == filter.normalized() &&
            page.accountId == owner.accountId && page.view == view && page.storeId == store && page.orders.size <= 40 &&
            page.orders.map { it.id }.distinct().size==page.orders.size &&
            page.nextBefore?.let { marketDiscoveryId(it)==it } != false &&
            page.orders.all { it.isValidDeliveryOrder() && it.matchesFilter(page.filter,view,page.checkedAtMillis) && when (view) {
                "buyer" -> it.buyerId == owner.accountId
                "courier" -> it.courierId == owner.accountId
                "seller" -> it.branchStoreId == store
                else -> false
            } }
    suspend fun accept(owner: MarketAccountScope, view: String, store: String?, response: ResponseDataModel<MarketDeliveryPage>, filter: MarketDeliveryFilter = MarketDeliveryFilter(), token: ReadToken? = null) = mutex.withLock {
        if (!owner.isCurrent()) return@withLock
        val slot=key(owner,view,store)
        if (response.httpStatusCode in setOf(401,403) && !response.transportFailure) {
            // Even an old/paginated denial revokes saved contacts and every read already in
            // flight for this slot. A late success must not resurrect the denied snapshot.
            latestReads.remove(slot)
            write(slot,null)
        } else if(token != null && latestReads[slot] === token && token.generation == owner.generation && token.filter == filter.normalized()) {
            response.payload?.takeIf { !response.negative && valid(it,owner,view,store,filter) }?.let { page ->
                write(slot,jsonBase.encodeToString(page.copy(orders=page.orders.map { it.copy(position=null,handoverCode=null) })))
            }
        }
    }
}
// Orders contain item lists. Chunked storage avoids oversized Android CursorWindow rows.
val marketDeliveryPages=MarketDeliveryPageCache(::readJsonCacheText,{ key,value ->
    if(value==null) deleteJsonCacheText(key) else writeJsonCacheText(key,value)
})
suspend fun cachedMarketDeliveryPage(owner: MarketAccountScope,view: String,store: String?,filter: MarketDeliveryFilter = MarketDeliveryFilter()) =
    withContext(Dispatchers.ourIo) { marketDeliveryPages.cached(owner,view,store,filter) }
suspend fun cachedMarketDeliverySubset(owner: MarketAccountScope,view: String,store: String?,filter: MarketDeliveryFilter) =
    withContext(Dispatchers.ourIo) { marketDeliveryPages.cachedSubset(owner,view,store,filter) }
suspend fun loadMarketDeliveryPage(owner: MarketAccountScope, view: String, store: String? = null, before: String? = null,
    filter: MarketDeliveryFilter = MarketDeliveryFilter()): ResponseDataModel<MarketDeliveryPage> {
    if (!owner.isCurrent()) return cloudSessionExpiredResponse()
    if (!filter.isValid()) return ResponseDataModel(eventMessage("delivery.invalid"),null,true,400)
    val selected=filter.normalized()
    val token=if(before == null) marketDeliveryPages.beginRead(owner,view,store,selected) else null
    try {
        val result=networkRequest<MarketDeliveryPage,Unit>(HttpMethod.Get,endpointUrl="market/delivery/orders",query=buildMap {
            put("view",view);store?.let { put("store",it) };before?.let { put("before",it) }
            put("queue",selected.queue.name);selected.mode?.let { put("mode",it.name) };if(selected.search.isNotEmpty()) put("search",selected.search)
        },expectedSessionGeneration=owner.generation)
        if (!owner.isCurrent()) return cloudSessionExpiredResponse()
        if (!result.negative && result.payload?.let { marketDeliveryPages.valid(it,owner,view,store,selected) } != true)
            return ResponseDataModel(eventMessage("delivery.refresh_failed"),null,true,502)
        // A denied older page revokes the same cached view as a denied first page. Otherwise an
        // offline revisit could still expose customer contacts after access was explicitly denied.
        if (before == null || (!result.transportFailure && result.httpStatusCode in setOf(401,403))) try {
            withContext(Dispatchers.ourIo) { marketDeliveryPages.accept(owner,view,store,result,selected,token) }
        } catch(cancelled: CancellationException) { throw cancelled }
        catch(_: Exception) {
            // A full disk must not turn a successful server read into an empty/offline screen.
            if(!result.negative) return result.copy(message=eventMessage("delivery.cache_warning"))
        }
        return result
    } finally {
        // This only releases a small in-memory token; it performs no disk/network work.
        if(token != null) withContext(NonCancellable) { marketDeliveryPages.finishRead(token) }
    }
}

internal suspend fun clearMarketDeliveryDeviceData(account: String) = withContext(Dispatchers.ourIo) {
    require(marketDiscoveryId(account)==account)
    deleteJsonCacheText("market-delivery-outbox-v1:$account")
    for(view in listOf("buyer","courier")) deleteJsonCacheText("market-delivery-page-v1:$account:$view:")
    val sellerPrefix="market-delivery-page-v1:$account:seller:"
    // Seller suffix is a validated UUID; chunk keys carry the complete logical key length.
    for(prefix in listOf(sellerPrefix,"aita-cache-chunks-v1:${sellerPrefix.length+36}:$sellerPrefix","market-delivery-policy-v1:$account:"))
        appDatabase.app_databaseQueries.deleteKvPrefixExcept(prefix=prefix,manifestKey="",keepPrefix="")
}
suspend fun shareMarketCourierPosition(owner: MarketAccountScope, order: MarketDeliveryOrder, position: MarketCourierPosition): ResponseDataModel<MarketDeliveryOrder> {
    if (!owner.isCurrent()) return cloudSessionExpiredResponse()
    return networkRequest(HttpMethod.Put,endpointUrl="market/delivery/orders/${order.id}/position",
        body=MarketCourierLocationUpdate(order.revision,position),expectedSessionGeneration=owner.generation)
}

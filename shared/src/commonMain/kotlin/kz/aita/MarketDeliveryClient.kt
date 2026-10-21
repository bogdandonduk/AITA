package kz.aita

import io.ktor.http.HttpMethod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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

/** Bounded account/view cache: never reuse another store's or courier's customer addresses. */
class MarketDeliveryPageCache(private val read: suspend (String)->String?, private val write: suspend (String,String?)->Unit) {
    private val mutex=Mutex()
    private fun key(owner: MarketAccountScope, view: String, store: String?) = "market-delivery-page-v1:${owner.accountId}:$view:${store.orEmpty()}"
    suspend fun cached(owner: MarketAccountScope, view: String, store: String?): MarketDeliveryPage? = mutex.withLock {
        if (!owner.isCurrent()) return@withLock null
        val data=runCatching { read(key(owner,view,store))?.let { jsonBase.decodeFromString<MarketDeliveryPage>(it) } }.getOrNull()
        data?.takeIf { owner.isCurrent() && valid(it,owner,view,store) }
    }
    fun valid(page: MarketDeliveryPage, owner: MarketAccountScope, view: String, store: String?): Boolean =
        view in setOf("buyer","courier","seller") && page.checkedAtMillis>0 &&
            page.accountId == owner.accountId && page.view == view && page.storeId == store && page.orders.size <= 40 &&
            page.orders.map { it.id }.distinct().size==page.orders.size &&
            page.nextBefore?.let { marketDiscoveryId(it)==it } != false &&
            page.orders.all { it.isValidDeliveryOrder() && when (view) {
                "buyer" -> it.buyerId == owner.accountId
                "courier" -> it.courierId == owner.accountId
                "seller" -> it.branchStoreId == store
                else -> false
            } }
    suspend fun accept(owner: MarketAccountScope, view: String, store: String?, response: ResponseDataModel<MarketDeliveryPage>) = mutex.withLock {
        if (!owner.isCurrent()) return@withLock
        if (response.httpStatusCode in setOf(401,403) && !response.transportFailure) write(key(owner,view,store),null)
        else response.payload?.takeIf { !response.negative && valid(it,owner,view,store) }?.let { page ->
            write(key(owner,view,store),jsonBase.encodeToString(page.copy(orders=page.orders.map { it.copy(position=null,handoverCode=null) })))
        }
    }
}
// Orders contain item lists. Chunked storage avoids oversized Android CursorWindow rows.
val marketDeliveryPages=MarketDeliveryPageCache(::readJsonCacheText,{ key,value ->
    if(value==null) deleteJsonCacheText(key) else writeJsonCacheText(key,value)
})
suspend fun cachedMarketDeliveryPage(owner: MarketAccountScope,view: String,store: String?) =
    withContext(Dispatchers.ourIo) { marketDeliveryPages.cached(owner,view,store) }
suspend fun loadMarketDeliveryPage(owner: MarketAccountScope, view: String, store: String? = null, before: String? = null): ResponseDataModel<MarketDeliveryPage> {
    if (!owner.isCurrent()) return cloudSessionExpiredResponse()
    val result=networkRequest<MarketDeliveryPage,Unit>(HttpMethod.Get,endpointUrl="market/delivery/orders",query=buildMap {
        put("view",view);store?.let { put("store",it) };before?.let { put("before",it) }
    },expectedSessionGeneration=owner.generation)
    if (!owner.isCurrent()) return cloudSessionExpiredResponse()
    if (!result.negative && result.payload?.let { marketDeliveryPages.valid(it,owner,view,store) } != true)
        return ResponseDataModel(eventMessage("delivery.refresh_failed"),null,true,502)
    if (before == null) try {
        withContext(Dispatchers.ourIo) { marketDeliveryPages.accept(owner,view,store,result) }
    } catch(cancelled: CancellationException) { throw cancelled }
    catch(_: Exception) {
        // A full disk must not turn a successful server read into an empty/offline screen.
        if(!result.negative) return result.copy(message=eventMessage("delivery.cache_warning"))
    }
    return result
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

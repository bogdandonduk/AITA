package kz.aita

import io.ktor.http.HttpMethod
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.TimeMark
import kotlin.time.TimeSource

data class MarketStockPublicationSnapshot(
    val scope: MarketRequestScope,
    val status: MarketStockPublicationStatus,
    val receivedAt: TimeMark = TimeSource.Monotonic.markNow(),
    val stale: Boolean = false
) {
    private val byId = status.entries.associateBy { it.goodsItemId }
    private val byCode = status.entries.filter { it.gtin != null }.groupBy { it.gtin to it.measurementUnitId }
    fun published(item: GoodsItemDataModel): Boolean = if (status.storeId == status.parentStoreId)
        byId[item.id]?.published == true
    else byId[item.id]?.published == true || item.standardBarcodeValues().mapNotNull(::marketCanonicalGtin)
        .any { code -> byCode[code to item.measurementUnitId].orEmpty().any { it.published } }
}

/** One app-lifetime subscription, not one request per stock card. Never persisted across accounts. */
@OptIn(ExperimentalCoroutinesApi::class)
object MarketStockPublicationWorkspace {
    private val worker = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val started = MutableStateFlow(false)
    private val state = MutableStateFlow<MarketStockPublicationSnapshot?>(null)
    val snapshot = state.asStateFlow()
    fun start() {
        if (!started.compareAndSet(false,true)) return
        worker.launch {
            val timer = flow { while(currentCoroutineContext().isActive) { emit(Unit); delay(30_000L) } }
            merge(inventoryOwners.state.map { Unit }, userAccountState.payload.map { Unit },
                MarketplaceSignals.revision.map { Unit },cloudTransportStatusState.map { Unit },
                stockLoadStatusState.map { it.accessDenied }.distinctUntilChanged().map { Unit },timer).collectLatest {
                val store = activeStoreIdState.value ?: run { state.value=null; return@collectLatest }
                val owner = captureMarketRequestScope(store)
                if (owner==null) { state.value=null; return@collectLatest }
                if (state.value?.scope?.isCurrent()!=true) state.value=null
                if (stockLoadStatusState.value.accessDenied) { state.value=null; return@collectLatest }
                if (cloudTransportStatusState.value==CLOUD_TRANSPORT_STATUS_UNAVAILABLE) {
                    state.value=state.value?.copy(stale=true); return@collectLatest
                }
                delay(200L)
                try {
                    val response=networkRequest<MarketStockPublicationStatus,Unit>(HttpMethod.Get,
                        endpointUrl="market/seller/stock-status",headers=mapOf("store_id" to store),expectedSessionGeneration=owner.generation)
                    ensureActive()
                    if (!owner.isCurrent()) return@collectLatest
                    val result=response.payload
                    if (response.httpStatusCode==200 && !response.negative && result?.isValidStockPublicationStatus(owner.accountId,store)==true)
                        state.value=MarketStockPublicationSnapshot(owner,result)
                    else if (response.httpStatusCode in setOf(401,403,404)) state.value=null
                    else state.value=state.value?.copy(stale=true)
                } catch(cancelled:CancellationException) { throw cancelled }
                catch(_:Exception) { if(owner.isCurrent()) state.value=state.value?.copy(stale=true) }
            }
        }
    }
}

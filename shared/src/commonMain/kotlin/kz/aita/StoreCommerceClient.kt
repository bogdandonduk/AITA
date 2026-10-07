package kz.aita

import io.ktor.http.HttpMethod
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

@Serializable data class PendingStoreCommerce(
    val id: String, val storeId: String, val buyer: BuyerWrite? = null, val writeOff: StockWriteOffCommand? = null,
    val batchBefore: GoodsBatchDataModel? = null, val batchAfter: GoodsBatchDataModel? = null,
    val acceptedBuyer: StoreBuyer? = null, val acceptedWriteOff: StockWriteOffResult? = null,
    val failure: List<LocalizedStringDataModel> = emptyList(), val rejected: Boolean = false
)
class StoreCommerceState internal constructor(internal val owner: InventoryOwner? = null, val directory: StoreBuyerDirectory? = null,
    val writeOffs: List<StockWriteOff> = emptyList(), val historyLoaded: Boolean = false,
    val pending: List<PendingStoreCommerce> = emptyList(), val error: List<LocalizedStringDataModel> = emptyList()) {
    internal fun copy(owner:InventoryOwner?=this.owner,directory:StoreBuyerDirectory?=this.directory,writeOffs:List<StockWriteOff> = this.writeOffs,
        historyLoaded:Boolean=this.historyLoaded,pending:List<PendingStoreCommerce> = this.pending,error:List<LocalizedStringDataModel> = this.error) =
        StoreCommerceState(owner,directory,writeOffs,historyLoaded,pending,error)
}

/** Durable commands have one identity. Cache publication follows journal persistence, never the reverse. */
object StoreCommerceClient {
    private val scope = CoroutineScope(SupervisorJob()+Dispatchers.ourIo)
    private val lifecycle = OwnedConnectionJob()
    private val promoClock = OwnedConnectionJob()
    private val journalMutex = Mutex()
    private val sender = Mutex()
    private val refreshMutex = Mutex()
    private val historyMutex = Mutex()
    private val mutable = MutableStateFlow(StoreCommerceState())
    val state = mutable.asStateFlow()
    val revision = MutableStateFlow(0L)
    private fun cache(owner:InventoryOwner,kind:String)="commerce-$kind-v1:${owner.accountId}:${owner.storeId}"
    private suspend fun readCommands(account:String):List<PendingStoreCommerce> =
        readRequiredJsonJournal("store-commerce-outbox-v1:$account")?.let {jsonBase.decodeFromString<List<PendingStoreCommerce>>(it)}.orEmpty()
    private suspend fun commands(account:String)=journalMutex.withLock {readCommands(account)}
    private suspend fun change(account:String,edit:(List<PendingStoreCommerce>)->List<PendingStoreCommerce>)=journalMutex.withLock {
        val list=edit(readCommands(account));writeJsonCacheText("store-commerce-outbox-v1:$account",jsonBase.encodeToString(list))
        val owner=inventoryOwners.current
        if(owner.accountId==account && inventoryOwnerIsCurrent(owner)) mutable.update {if(it.owner==owner) it.copy(pending=list.filter {it.storeId==owner.storeId}) else it}
        list
    }
    fun start() {
        promoClock.startIfIdle(scope,Dispatchers.ourIo) {
            cartBuyersState.collectLatest {buyers->
                val owner=DynamicCarts.captureScope()
                var previous=getCurrentTimeMillis()
                cartBuyerPromoClockState.value=previous
                while(isActive) {
                    val next=nextBuyerPromoBoundary(buyers.values,previous) ?: break
                    delay((next-getCurrentTimeMillis()).coerceAtLeast(1L))
                    val now=getCurrentTimeMillis()
                    val changed=buyers.filterValues {it.activeDiscount(previous)!=it.activeDiscount(now)}.keys
                    if(changed.isNotEmpty() && owner!=null && DynamicCarts.captureScope()==owner) DynamicCarts.editUiAsync {ui->
                        if(DynamicCarts.captureScope()!=owner) ui else ui.copy(payments=ui.payments.filterKeys {it !in changed})
                    }
                    cartBuyerPromoClockState.value=now;previous=now
                }
            }
        }
        lifecycle.startIfIdle(scope,Dispatchers.ourIo) {
        inventoryOwners.state.collectLatest { owner ->
            mutable.value=StoreCommerceState(owner=owner)
            if(!inventoryOwnerIsCurrent(owner) || owner.accountId==null || owner.storeId==null) return@collectLatest
            try {
                val directory=readJsonCacheText(cache(owner,"buyers"))?.let {jsonBase.decodeFromString<StoreBuyerDirectory>(it)}
                val records=readJsonCacheText(cache(owner,"writeoffs"))?.let {jsonBase.decodeFromString<List<StockWriteOff>>(it)}
                val pending=commands(owner.accountId).filter {it.storeId==owner.storeId}
                if(inventoryOwnerIsCurrent(owner)) mutable.value=StoreCommerceState(owner,directory,records.orEmpty(),getLocalKv(cache(owner,"history-complete"))=="1",pending)
            } catch(cancel:CancellationException){throw cancel}
            catch(_:Exception){if(inventoryOwnerIsCurrent(owner)) mutable.update {it.copy(error=commerceMessage("storage"))}}
            refreshBuyers(owner)
            while(isActive && inventoryOwnerIsCurrent(owner)) {
                awaitClientBackgroundWork()
                try { if(commands(owner.accountId).isNotEmpty()) flush() }
                catch(cancel:CancellationException){throw cancel}
                catch(_:Exception){if(inventoryOwnerIsCurrent(owner)) mutable.update {it.copy(error=commerceMessage("storage"))}}
                delay(15_000L)
            }
        }
    } }
    fun invalidate() { revision.update {it+1}; start(); scope.launch { refreshBuyers(); if(state.value.historyLoaded) refreshWriteOffs() } }
    fun current(value:StoreCommerceState=state.value):StoreCommerceState = value.takeIf {it.owner?.let(::inventoryOwnerIsCurrent)==true} ?: StoreCommerceState()
    fun effectiveBuyers(value:StoreCommerceState=state.value):List<StoreBuyer> {
        val s=current(value);val changes=s.pending.filter {!it.rejected}.mapNotNull {it.acceptedBuyer ?: it.buyer?.buyer}
        return mergeById(s.directory?.buyers.orEmpty(),changes) {it.id}
    }
    suspend fun refreshBuyers() = refreshBuyers(inventoryOwners.current)
    internal suspend fun refreshBuyers(owner:InventoryOwner) {
        if(!inventoryOwnerIsCurrent(owner) || !canViewStoreBuyers(owner.storeId)) return
        refreshMutex.lock()
        try {
            if(!inventoryOwnerIsCurrent(owner)) return
            val reply=networkRequest<StoreBuyerDirectory,Unit>(HttpMethod.Get,endpointUrl="stores/${owner.storeId}/buyers",expectedSessionGeneration=owner.sessionGeneration)
            if(!inventoryOwnerIsCurrent(owner)) return
            val d=reply.payload
            if(!reply.negative && d!=null && d.storeId==owner.storeId && d.buyers.all {it.storeId==owner.storeId && it.valid()} && d.parentBuyers.all {it.valid()}) {
                val previous=current().directory?.buyers.orEmpty()
                val fresh=d.copy(buyers=mergeBuyersByRevision(previous,d.buyers))
                writeJsonCacheText(cache(owner,"buyers"),jsonBase.encodeToString(fresh))
                mutable.update {if(it.owner==owner) it.copy(directory=fresh,error=emptyList()) else it}
                // Never alter a checkout that may already have reached the server.
                DynamicCarts.editUiAsync { ui ->
                    if(!inventoryOwnerIsCurrent(owner)) return@editUiAsync ui
                    val pendingBuyers=current().pending.mapNotNull {it.buyer?.buyer?.id}.toSet()
                    val mapped=ui.buyers.mapNotNull { (key,selected) ->
                        if(key in ui.checkouts || selected.id in pendingBuyers) key to selected
                        else fresh.buyers.find {it.id==selected.id && it.isActive}?.let {key to it}
                    }.toMap()
                    val changed=ui.buyers.keys.filter {ui.buyers[it]!=mapped[it]}.toSet()
                    ui.copy(buyers=mapped,payments=ui.payments.filterKeys {it !in changed})
                }
            } else mutable.update {if(it.owner==owner) it.copy(error=reply.message.orEmpty().ifEmpty {commerceMessage("failed")}) else it}
        } catch(cancel:CancellationException){throw cancel}
        catch(_:Exception){if(inventoryOwnerIsCurrent(owner)) mutable.update {it.copy(error=commerceMessage("failed"))}}
        finally {refreshMutex.unlock()}
    }
    suspend fun refreshWriteOffs() = refreshWriteOffs(inventoryOwners.current)
    internal suspend fun refreshWriteOffs(owner:InventoryOwner) {
        if(!inventoryOwnerIsCurrent(owner) || owner.storeId==null) return
        historyMutex.lock()
        try {
            if(!inventoryOwnerIsCurrent(owner)) return
            val records=mutableListOf<StockWriteOff>();var offset=0
            do {
                val reply=networkRequest<StockWriteOffPage,Unit>(HttpMethod.Get,endpointUrl="stores/${owner.storeId}/writeoffs",
                    query=mapOf("offset" to offset),expectedSessionGeneration=owner.sessionGeneration)
                if(!inventoryOwnerIsCurrent(owner)) return
                val page=reply.payload
                if(reply.negative || page==null) {mutable.update {if(it.owner==owner) it.copy(error=reply.message.orEmpty().ifEmpty {commerceMessage("failed")}) else it};return}
                check(page.records.size<=500 && page.records.all {it.command.storeId==owner.storeId && it.command.valid()})
                records+=page.records
                val next=page.nextOffset ?: break
                check(next==offset+500 && records.size<=1000000);offset=next
            } while(true)
            val unique=(records+current().writeOffs).distinctBy {it.command.id}
            writeJsonCacheText(cache(owner,"writeoffs"),jsonBase.encodeToString(unique))
            putLocalKv(cache(owner,"history-complete"),"1")
            if(inventoryOwnerIsCurrent(owner)) mutable.update {if(it.owner==owner) it.copy(writeOffs=unique,historyLoaded=true,error=emptyList()) else it}
        } catch(cancel:CancellationException){throw cancel}
        catch(_:Exception){if(inventoryOwnerIsCurrent(owner)) mutable.update {it.copy(error=commerceMessage("failed"))}}
        finally {historyMutex.unlock()}
    }
    suspend fun saveBuyer(buyer:StoreBuyer):ResponseDataModel<StoreBuyer> {
        start();val owner=inventoryOwners.current
        if(!buyer.valid()) return ResponseDataModel(commerceMessage("bad_buyer"),null,true)
        if(!inventoryOwnerIsCurrent(owner) || owner.storeId!=buyer.storeId || !canManageStoreBuyers(owner.storeId)) return ResponseDataModel(commerceMessage("denied"),null,true)
        val id=newDiagnosticId();val write=BuyerWrite(id,buyer)
        val result=submit(owner,PendingStoreCommerce(id,buyer.storeId,buyer=write))
        return ResponseDataModel(result.message,result.payload?.acceptedBuyer ?: buyer.takeUnless {result.negative},result.negative,result.httpStatusCode,result.transportFailure)
    }
    suspend fun writeOff(batch:GoodsBatchDataModel,quantity:Double,reason:WriteOffReason,note:String):ResponseDataModel<StockWriteOffResult> {
        start();val owner=inventoryOwners.current
        val command=StockWriteOffCommand(newDiagnosticId(),batch.storeId,batch.id,quantity,reason,note.trim())
        val remaining=writeOffRemaining(batch.quantity,quantity)
        if(!command.valid() || remaining==null) return ResponseDataModel(commerceMessage("bad_quantity"),null,true)
        if(!inventoryOwnerIsCurrent(owner) || owner.storeId!=batch.storeId || !currentUserHasStorePermission(owner.storeId,STORE_PERMISSION_STOCK_BATCH_EDIT)) return ResponseDataModel(commerceMessage("denied"),null,true)
        val result=submit(owner,PendingStoreCommerce(command.id,batch.storeId,writeOff=command,batchBefore=batch,batchAfter=batch.copy(quantity=batch.quantity.copy(total=remaining))))
        return ResponseDataModel(result.message,result.payload?.acceptedWriteOff,result.negative,result.httpStatusCode,result.transportFailure)
    }
    private suspend fun submit(owner:InventoryOwner,command:PendingStoreCommerce):ResponseDataModel<PendingStoreCommerce> {
        val account=owner.accountId ?: return ResponseDataModel(commerceMessage("denied"),null,true)
        val offline=cloudTransportStatusState.value==CLOUD_TRANSPORT_STATUS_UNAVAILABLE || cloudTransportStatusState.value==CLOUD_TRANSPORT_STATUS_AUTH_REFRESH_REQUIRED
        if(offline && !currentStoreHasWorkspaceAccess(owner.storeId)) return ResponseDataModel(eventMessage("subscription.verify"),null,true)
        try {
            change(account) { rows ->
                require(rows.none { it.storeId==command.storeId && (it.rejected || command.buyer!=null && it.buyer?.buyer?.id==command.buyer.buyer.id || command.writeOff!=null && it.writeOff?.batchId==command.writeOff.batchId) })
                rows+command
            }
            try {projectBatch(owner,command.batchAfter)} catch(cancel:CancellationException){throw cancel}
            catch(_:Exception){ /* The durable journal, not the optional inventory projection, owns this request. */ }
        } catch(cancel:CancellationException){throw cancel}
        catch(_:IllegalArgumentException){return ResponseDataModel(commerceMessage("pending_conflict"),null,true)}
        catch(_:Exception){return ResponseDataModel(commerceMessage("storage"),null,true)}
        val accepted=if(!offline) flush(command.id) else null
        return accepted?.takeIf { !it.negative || !it.transportFailure && it.httpStatusCode in listOf(400,403,404,409,422) }
            ?: ResponseDataModel(commerceMessage("pending"),command,false)
    }
    private suspend fun projectBatch(owner:InventoryOwner,batch:GoodsBatchDataModel?) {
        if(batch==null) return
        inventoryStateMutex.withLock {
            if(userAccountState.payloadValue?.id!=owner.accountId || !authenticatedSessionGenerationIsCurrent(owner.sessionGeneration)) return@withLock
            val current=inventoryOwnerIsCurrent(owner)
            val rows=(if(current) stockBatchesState.payloadValue else null) ?: readJsonCacheText(CACHE_PREFIX+inventoryCacheKey("stock_batches",owner))
                ?.let {jsonBase.decodeFromString<List<GoodsBatchDataModel>>(it)}.orEmpty()
            if(rows.any {it.id==batch.id && it.updatedAtMillis>batch.updatedAtMillis}) return@withLock
            val next=rows.filterNot {it.id==batch.id}+batch
            val saved=persistInventoryCacheLocked("stock_batches",owner,next)
            if(current) {
                stockBatchesState.emit(DataState.Success(next))
                stockBatchesLoadStatusState.value=stockBatchesLoadStatusState.value.copy(cacheWriteFailed=!saved)
            }
            check(saved) {"Commerce cache not durable"}
        }
    }
    suspend fun pendingStoreIds():Set<String> = userAccountState.payloadValue?.id?.let {commands(it).map {c->c.storeId}.toSet()}.orEmpty()
    internal suspend fun overlayBatches(owner:InventoryOwner,rows:List<GoodsBatchDataModel>):List<GoodsBatchDataModel> {
        val account=owner.accountId ?: return rows
        val pending=commands(account).filter {it.storeId==owner.storeId && !it.rejected && it.writeOff!=null}
        return mergeById(rows,pending.mapNotNull {it.acceptedWriteOff?.batch ?: it.batchAfter}.associateBy {it.id}.values.toList()) {it.id}
    }
    /** Definite rejections require explicit review; connectivity loss always retains the original command. */
    suspend fun discardRejected(id:String) {
        val owner=inventoryOwners.current;val account=owner.accountId ?: return
        if(!inventoryOwnerIsCurrent(owner)) return
        val old=commands(account).find {it.id==id && it.storeId==owner.storeId && it.rejected} ?: return
        change(account) {it.filterNot {row->row.id==id}}
        projectBatch(owner,old.batchBefore)
        owner.storeId?.let {getStockBatches(it)}
        refreshBuyers(owner)
    }
    suspend fun flush(initialId:String?=null):ResponseDataModel<PendingStoreCommerce>? {
        if(!sender.tryLock()) return null
        val owner=inventoryOwners.current;val account=owner.accountId;val generation=currentAuthenticatedSessionGeneration()
        try {
            if(account==null || getStoredUserAuthTokens?.invoke()==null) return null
            val ready=withTimeoutOrNull(12_000L){ensureCloudSessionReadyForProtectedRequest(retryAfterTransportRecovery=true)} ?: return null
            if(ready.negative) return null
            InventoryCreates.flush(force=true)
            val blocked=InventoryCreates.pending(account).map {it.storeId}.toMutableSet()
            for(command in commands(account)) {
                if(userAccountState.payloadValue?.id!=account || !authenticatedSessionGenerationIsCurrent(generation)) return null
                if(command.storeId in blocked) continue
                if(command.rejected) {blocked+=command.storeId;continue}
                val target=owner.copy(storeId=command.storeId)
                val response:ResponseDataModel<PendingStoreCommerce> = try {
                    when {
                        command.acceptedBuyer!=null || command.acceptedWriteOff!=null -> ResponseDataModel(commerceMessage("saved"),command,false)
                        command.buyer!=null -> {
                            val r=networkRequest<StoreBuyer,BuyerWrite>(HttpMethod.Post,endpointUrl="stores/${command.storeId}/buyers",body=command.buyer,
                                headers=mapOf("store_id" to command.storeId),expectedSessionGeneration=generation)
                            val v=r.payload?.takeIf {it.valid() && it.id==command.buyer.buyer.id && it.storeId==command.storeId}
                            ResponseDataModel(r.message,v?.let {command.copy(acceptedBuyer=it)},r.negative || v==null,r.httpStatusCode,r.transportFailure)
                        }
                        else -> {
                            val r=networkRequest<StockWriteOffResult,StockWriteOffCommand>(HttpMethod.Post,endpointUrl="stores/${command.storeId}/writeoffs",body=command.writeOff,
                                headers=mapOf("store_id" to command.storeId),expectedSessionGeneration=generation)
                            val v=r.payload?.takeIf {it.record.command==command.writeOff && it.batch.id==command.writeOff?.batchId && it.batch.storeId==command.storeId}
                            ResponseDataModel(r.message,v?.let {command.copy(acceptedWriteOff=it)},r.negative || v==null,r.httpStatusCode,r.transportFailure)
                        }
                    }
                } catch(cancel:CancellationException){throw cancel}
                catch(_:Exception){ResponseDataModel(commerceMessage("failed"),null,true,transportFailure=true)}
                if(userAccountState.payloadValue?.id!=account || !authenticatedSessionGenerationIsCurrent(generation)) return null
                if(!response.negative && response.payload!=null) {
                    val accepted=response.payload
                    // Persist the outcome before any cache acknowledgement. Recovery cannot apply its delta again.
                    change(account){rows->rows.map {if(it.id==command.id) accepted else it}}
                    accepted.acceptedBuyer?.let {buyer->
                        val cached=readJsonCacheText(cache(target,"buyers"))?.let {jsonBase.decodeFromString<StoreBuyerDirectory>(it)} ?: StoreBuyerDirectory(command.storeId,emptyList())
                        val next=cached.copy(buyers=mergeBuyersByRevision(cached.buyers,listOf(buyer)))
                        writeJsonCacheText(cache(target,"buyers"),jsonBase.encodeToString(next))
                        if(inventoryOwnerIsCurrent(target)) mutable.update {if(it.owner==target) it.copy(directory=next,error=emptyList()) else it}
                    }
                    accepted.acceptedWriteOff?.let {result->
                        val cached=readJsonCacheText(cache(target,"writeoffs"))?.let {jsonBase.decodeFromString<List<StockWriteOff>>(it)}.orEmpty()
                        val next=mergeById(cached,listOf(result.record)){it.command.id}
                        writeJsonCacheText(cache(target,"writeoffs"),jsonBase.encodeToString(next))
                        projectBatch(target,result.batch)
                        if(inventoryOwnerIsCurrent(target)) mutable.update {if(it.owner==target) it.copy(writeOffs=next,error=emptyList()) else it}
                    }
                    change(account){rows->rows.filterNot {it.id==command.id}}
                    accepted.acceptedBuyer?.let {buyer->
                        if(inventoryOwnerIsCurrent(target)) DynamicCarts.editUiAsync {ui->
                            if(!inventoryOwnerIsCurrent(target)) return@editUiAsync ui
                            val changed=ui.buyers.filterValues {it.id==buyer.id}.keys
                            ui.copy(buyers=ui.buyers.mapNotNull {(key,value)->
                                if(key !in changed) key to value else buyer.takeIf {it.isActive}?.let {key to it}
                            }.toMap(),payments=ui.payments.filterKeys {it !in changed})
                        }
                    }
                    revision.update {it+1}
                    if(command.id==initialId) return response
                } else {
                    val rejected=!response.transportFailure && response.httpStatusCode in listOf(400,403,404,409,422)
                    val failed=command.copy(failure=response.message.orEmpty().ifEmpty {commerceMessage("failed")},rejected=rejected)
                    change(account){rows->rows.map {if(it.id==command.id) failed else it}}
                    if(rejected) {projectBatch(target,command.batchBefore);if(inventoryOwnerIsCurrent(target)) getStockBatches(command.storeId)}
                    blocked+=command.storeId
                    if(command.id==initialId) return response
                    if(response.transportFailure || response.httpStatusCode==401) break
                }
            }
        } catch(cancel:CancellationException){throw cancel}
        catch(_:Exception){/* Durable request or outcome remains available for an identical retry. */}
        finally {sender.unlock()}
        return null
    }
}

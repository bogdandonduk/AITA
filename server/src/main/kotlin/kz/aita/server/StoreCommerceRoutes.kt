package kz.aita.server

import io.ktor.http.*
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.*
import io.ktor.server.response.*
import kotlinx.coroutines.Dispatchers
import kz.aita.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.json.jsonb
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.UUID

internal object StoreBuyers : Table("store_buyers") {
    val id = uuid("id"); val storeId = uuid("store_id")
    val payload = jsonb("payload", jsonBase, StoreBuyer.serializer()); val revision = long("revision")
    override val primaryKey = PrimaryKey(id)
}
internal object BuyerCommands : Table("store_buyer_commands") {
    val id = uuid("id"); val storeId = uuid("store_id"); val actor = uuid("actor_id")
    val request = jsonb("request",jsonBase,BuyerWrite.serializer()); val result = jsonb("result",jsonBase,StoreBuyer.serializer())
    override val primaryKey = PrimaryKey(id)
}
internal object StockWriteOffs : Table("stock_writeoffs") {
    val id = uuid("id"); val storeId = uuid("store_id"); val batchId = uuid("batch_id"); val actor = uuid("actor_id"); val time = long("time_millis")
    val request = jsonb("request",jsonBase,StockWriteOffCommand.serializer()); val result = jsonb("result",jsonBase,StockWriteOffResult.serializer())
    override val primaryKey = PrimaryKey(id)
}
internal class CommerceProblem(val key: String, val status: HttpStatusCode = HttpStatusCode.Conflict): RuntimeException(key)
private fun denied(): Nothing = throw CommerceProblem("denied",HttpStatusCode.Forbidden)
private fun canReadBuyers(user: UUID,store: UUID) = listOf(STORE_PERMISSION_BUYERS_VIEW,STORE_PERMISSION_BUYERS_MANAGE,STORE_PERMISSION_SALE_TRANSACTION)
    .any { userCanUseStoreActionInsideTransaction(user,store,it,requireWorkshift=false) }

internal fun saveBuyerInsideTransaction(user: UUID, store: UUID, request: BuyerWrite): StoreBuyer {
    if (!userCanUseStoreActionInsideTransaction(user,store,STORE_PERMISSION_BUYERS_MANAGE,requireWorkshift=false)) denied()
    val incoming=request.buyer
    if (!validStorePersonId(request.operationId) || !incoming.valid() || incoming.storeId != store.toString())
        throw CommerceProblem("bad_buyer",HttpStatusCode.BadRequest)
    lockStockInventoryInsideTransaction(listOf(store))
    val commandId=UUID.fromString(request.operationId)
    BuyerCommands.selectAll().where { BuyerCommands.id eq commandId }.singleOrNull()?.let {
        if(it[BuyerCommands.actor]!=user || it[BuyerCommands.storeId]!=store || it[BuyerCommands.request]!=request) throw CommerceProblem("changed")
        return it[BuyerCommands.result]
    }
    val id=UUID.fromString(incoming.id)
    val old=StoreBuyers.selectAll().where {StoreBuyers.id eq id}.singleOrNull()
    if(old!=null && old[StoreBuyers.storeId]!=store) denied()
    if((old?.get(StoreBuyers.revision) ?: 0L)!=incoming.revision || incoming.revision==Long.MAX_VALUE) throw CommerceProblem("changed")
    if(old==null && incoming.sourceParentBuyerId!=null) {
        if(StoreBuyers.selectAll().where {StoreBuyers.storeId eq store}.any {it[StoreBuyers.payload].sourceParentBuyerId==incoming.sourceParentBuyerId}) throw CommerceProblem("changed")
        val parent=Stores.select(Stores.parentStoreId).where {Stores.id eq store}.singleOrNull()?.get(Stores.parentStoreId) ?: denied()
        val source=StoreBuyers.selectAll().where {(StoreBuyers.id eq UUID.fromString(incoming.sourceParentBuyerId)) and (StoreBuyers.storeId eq parent)}
            .singleOrNull()?.get(StoreBuyers.payload)?.takeIf {it.isActive} ?: throw CommerceProblem("changed")
        // The user reviews and may override the copied promotion; linkage cannot point outside the parent.
        if(!source.valid()) throw CommerceProblem("changed")
    } else if(old!=null && incoming.sourceParentBuyerId!=old[StoreBuyers.payload].sourceParentBuyerId) throw CommerceProblem("changed")
    val saved=incoming.copy(name=incoming.name.trim(),phone=incoming.phone.trim(),email=incoming.email.trim(),note=incoming.note.trim(),
        promoTitle=incoming.promoTitle.trim(),revision=incoming.revision+1,updatedAtMillis=System.currentTimeMillis())
    if(old==null) StoreBuyers.insert {it[StoreBuyers.id]=id;it[storeId]=store;it[payload]=saved;it[revision]=saved.revision}
    else StoreBuyers.update({StoreBuyers.id eq id}) {it[payload]=saved;it[revision]=saved.revision}
    BuyerCommands.insert {it[BuyerCommands.id]=commandId;it[storeId]=store;it[actor]=user;it[BuyerCommands.request]=request;it[result]=saved}
    insertOperationLogInsideTransaction(actorUserId=user,storeId=store,action=if(old==null) OPERATION_LOG_ACTION_CREATED else OPERATION_LOG_ACTION_UPDATED,
        entityType="buyer",entityId=id.toString(),title=commerceMessage("buyer"),details=listOf(LocalizedStringDataModel("main",saved.name)),
        metadata=mapOf("buyer_id" to id.toString()),now=saved.updatedAtMillis,required=true)
    return saved
}

/** Quantity delta and immutable result commit under the same root inventory lock as sales/transfers. */
internal fun writeOffInsideTransaction(user: UUID,store: UUID,command: StockWriteOffCommand): StockWriteOffResult {
    if(!userCanUseStoreActionInsideTransaction(user,store,STORE_PERMISSION_STOCK_BATCH_EDIT,requireWorkshift=true)) denied()
    if(!command.valid() || command.storeId!=store.toString()) throw CommerceProblem("bad_quantity",HttpStatusCode.BadRequest)
    lockStockInventoryInsideTransaction(listOf(store))
    val id=UUID.fromString(command.id)
    StockWriteOffs.selectAll().where {StockWriteOffs.id eq id}.singleOrNull()?.let {
        if(it[StockWriteOffs.storeId]!=store || it[StockWriteOffs.actor]!=user || it[StockWriteOffs.request]!=command) throw CommerceProblem("changed")
        return it[StockWriteOffs.result]
    }
    val batchId=UUID.fromString(command.batchId)
    val row=StockBatchesV2.selectAll().where {(StockBatchesV2.id eq batchId) and (StockBatchesV2.storeId eq store)}.singleOrNull()
        ?: throw CommerceProblem("changed",HttpStatusCode.NotFound)
    val batch=row.toGoodsBatchDataModel()
    if (!batch.tracksQuantity) throw CommerceProblem("batch_busy")
    if(!batch.isActive || batch.status !in setOf(StockBatchStatusDataModel.OnShelf,StockBatchStatusDataModel.Delivered))
        throw CommerceProblem("batch_busy")
    if(!StockBatchMovements.select(StockBatchMovements.id).where {
        ((StockBatchMovements.sourceBatchId eq batchId) or (StockBatchMovements.destinationBatchId eq batchId)) and
        (StockBatchMovements.status eq StockBatchMovementStatusDataModel.PendingAcceptance.name)
    }.empty()) throw CommerceProblem("batch_busy")
    val remaining=writeOffRemaining(batch.quantity,command.quantity) ?: throw CommerceProblem("bad_quantity")
    val item=StockItems.selectAll().where {StockItems.id eq row[StockBatchesV2.goodsItemId]}.single()
    val now=maxOf(System.currentTimeMillis(),batch.updatedAtMillis+1)
    val cost=batch.supplyPrice.price.toDoubleOrNull()?.takeIf {it.isFinite() && it>=0} ?: throw CommerceProblem("changed")
    if(!(cost*command.quantity).isFinite()) throw CommerceProblem("bad_quantity")
    val record=StockWriteOff(command,batch.goodsItemId,item[StockItems.name],batch.quantity.copy(total=command.quantity),batch.supplyPrice,
        (cost*command.quantity).roundMoney(),user.toString(),now,remaining,item[StockItems.categoryIds],batch.supplierId)
    val next=batch.copy(quantity=batch.quantity.copy(total=remaining),status=if(remaining==0.0) StockBatchStatusDataModel.WrittenOff else batch.status,updatedAtMillis=now,expectedUpdatedAtMillis=null)
    StockBatchesV2.update({StockBatchesV2.id eq batchId}) {it[quantity]=next.quantity;it[status]=next.status.name;it[updatedAtMillis]=now}
    updateGoodsItemActiveShelfBatchInsideTransaction(UUID.fromString(batch.goodsItemId),store,now)
    val result=StockWriteOffResult(record,next)
    StockWriteOffs.insert {it[StockWriteOffs.id]=id;it[storeId]=store;it[StockWriteOffs.batchId]=batchId;it[actor]=user;it[time]=now;
        it[request]=command;it[StockWriteOffs.result]=result}
    insertOperationLogInsideTransaction(actorUserId=user,storeId=store,action="written_off",entityType=OPERATION_LOG_ENTITY_STOCK_BATCH,
        entityId=batchId.toString(),title=commerceMessage("writeoffs"),details=commerceMessage(command.reason.name),
        metadata=mapOf("writeoff_id" to command.id,"quantity" to command.quantity.toString(),"reason" to command.reason.name,"note" to command.note,
            "cost" to record.cost.toString(),"currency" to batch.supplyPrice.currency),now=now,required=true)
    return result
}

internal fun canonicalBuyerInsideTransaction(store:UUID, requested:TransactionBuyerSnapshot?):TransactionBuyerSnapshot? {
    if(requested==null) return null
    lockStockInventoryInsideTransaction(listOf(store))
    val id=runCatching {UUID.fromString(requested.id)}.getOrNull() ?: throw CommerceProblem("promo_changed")
    val buyer=StoreBuyers.selectAll().where {(StoreBuyers.id eq id) and (StoreBuyers.storeId eq store)}.singleOrNull()?.get(StoreBuyers.payload)
        ?.takeIf {it.isActive} ?: throw CommerceProblem("promo_changed")
    val snapshot=buyer.receiptSnapshot()
    if(!validQuickDiscount(requested.discountPercent) || kotlin.math.abs(snapshot.discountPercent-requested.discountPercent)>0.000001) throw CommerceProblem("promo_changed")
    return snapshot
}

fun Route.installStoreCommerceRoutes() { authenticate("auth-jwt") { route("/stores/{commerceStore}") {
    get("/buyers") {
        val user=call.checkPrincipal() ?: return@get
        val store=runCatching {UUID.fromString(call.parameters["commerceStore"])}.getOrNull() ?: return@get call.genericResponseNoPayload(HttpStatusCode.BadRequest,commerceMessage("denied"))
        try {
            val value=newSuspendedTransaction(Dispatchers.IO) {
                if(!canReadBuyers(user,store)) denied()
                val parent=Stores.select(Stores.parentStoreId).where {Stores.id eq store}.singleOrNull()?.get(Stores.parentStoreId)
                fun list(id:UUID)=StoreBuyers.selectAll().where {StoreBuyers.storeId eq id}.orderBy(StoreBuyers.id).limit(10001).map {it[StoreBuyers.payload]}
                    .also { if(it.size>10000) throw CommerceProblem("failed",HttpStatusCode.PayloadTooLarge) }
                StoreBuyerDirectory(store.toString(),list(store),parent?.let(::list).orEmpty().filter {it.isActive})
            }
            call.response.header(HttpHeaders.CacheControl,"private, no-store");call.genericResponse(HttpStatusCode.OK,value)
        } catch(e:CommerceProblem){call.genericResponseNoPayload(e.status,commerceMessage(e.key))}
    }
    post("/buyers") {
        val user=call.checkPrincipal() ?: return@post
        val store=runCatching {UUID.fromString(call.parameters["commerceStore"])}.getOrNull() ?: return@post call.genericResponseNoPayload(HttpStatusCode.BadRequest,commerceMessage("denied"))
        val input=call.receiveAita<BuyerWrite>()
        try {
            val value=newSuspendedTransaction(Dispatchers.IO) {
                if(!call.matchesInventoryContextStoreIdInsideTransaction(user,store)) denied()
                saveBuyerInsideTransaction(user,store,input)
            }
            RealtimeServerBus.publish(entity="store-buyers",storeId=store.toString(),reason="buyer_changed")
            // Parent directory changes invalidate only child stores in this exact hierarchy.
            val branches=newSuspendedTransaction(Dispatchers.IO) {Stores.select(Stores.id).where {(Stores.parentStoreId eq store) and (Stores.isActive eq true)}.map {it[Stores.id]}}
            branches.forEach {RealtimeServerBus.publish(entity="store-buyers",storeId=it.toString(),reason="parent_buyer_changed")}
            call.genericResponse(HttpStatusCode.OK,value,commerceMessage("saved"))
        } catch(e:CommerceProblem){call.genericResponseNoPayload(e.status,commerceMessage(e.key))}
    }
    post("/writeoffs") {
        val user=call.checkPrincipal() ?: return@post
        val store=runCatching {UUID.fromString(call.parameters["commerceStore"])}.getOrNull() ?: return@post call.genericResponseNoPayload(HttpStatusCode.BadRequest,commerceMessage("denied"))
        val input=call.receiveAita<StockWriteOffCommand>()
        try {
            val value=newSuspendedTransaction(Dispatchers.IO) {
                if(!call.matchesInventoryContextStoreIdInsideTransaction(user,store)) denied()
                writeOffInsideTransaction(user,store,input)
            }
            publishStockRealtimeBundle(store.toString(),"stock_written_off")
            RealtimeServerBus.publish(entity="stock-writeoffs",storeId=store.toString(),reason="stock_written_off")
            call.genericResponse(HttpStatusCode.OK,value,commerceMessage("saved"))
        } catch(e:CommerceProblem){call.genericResponseNoPayload(e.status,commerceMessage(e.key))}
    }
    get("/writeoffs") {
        val user=call.checkPrincipal() ?: return@get
        val store=runCatching {UUID.fromString(call.parameters["commerceStore"])}.getOrNull() ?: return@get call.genericResponseNoPayload(HttpStatusCode.BadRequest,commerceMessage("denied"))
        val from=call.request.queryParameters["from"]?.toLongOrNull() ?: 0L
        val until=call.request.queryParameters["until"]?.toLongOrNull() ?: Long.MAX_VALUE
        val offset=call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
        if(from<0 || until<from || offset !in 0..1000000) return@get call.genericResponseNoPayload(HttpStatusCode.BadRequest,commerceMessage("failed"))
        try {
            val value=newSuspendedTransaction(Dispatchers.IO) {
                if(!userCanUseStoreActionInsideTransaction(user,store,STORE_PERMISSION_ANALYTICS_VIEW,false) &&
                    !userCanUseStoreActionInsideTransaction(user,store,STORE_PERMISSION_STOCK_HISTORY_VIEW,false) &&
                    !userCanUseStoreActionInsideTransaction(user,store,STORE_PERMISSION_STOCK_BATCH_EDIT,false)) denied()
                val rows=StockWriteOffs.selectAll().where {(StockWriteOffs.storeId eq store) and (StockWriteOffs.time greaterEq from) and (StockWriteOffs.time less until)}
                    .orderBy(StockWriteOffs.time to SortOrder.ASC,StockWriteOffs.id to SortOrder.ASC).limit(501).offset(offset.toLong()).map {it[StockWriteOffs.result].record}
                StockWriteOffPage(rows.take(500),if(rows.size>500) offset+500 else null)
            }
            call.response.header(HttpHeaders.CacheControl,"private, no-store");call.genericResponse(HttpStatusCode.OK,value)
        } catch(e:CommerceProblem){call.genericResponseNoPayload(e.status,commerceMessage(e.key))}
    }
} } }

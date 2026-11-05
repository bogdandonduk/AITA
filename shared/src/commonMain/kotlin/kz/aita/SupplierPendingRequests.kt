package kz.aita

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

@Serializable
internal data class SupplierPendingRequest(
    val accountId: String,
    val create: SupplierOrderWithLinesDataModel? = null,
    val receipt: ReceiveSupplierOrderRequestDataModel? = null,
    val schema: Int = 1
)
internal class SupplierPendingRequestConflict : IllegalStateException("Unconfirmed supplier request differs")
internal fun supplierRequestIdentityValid(value: String) = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}").matches(value)
private fun supplierRequestPriceValid(price: PriceDataModel): Boolean =
    price.price.replace(',', '.').toDoubleOrNull()?.let { it.isFinite() && it >= 0.0 } == true && price.currency.matches(Regex("[A-Za-z]{3}"))

/** One durable slot per store/item or order: independent browser tabs cannot overwrite other
 * requests by saving an older account-wide map. A request is saved and read back before sending.
 * Multi-line orders share their first canonical item slot; the current creation UI is per item. */
internal class SupplierPendingRequestStore(
    private val read: suspend (String) -> String?,
    private val write: suspend (String, String) -> Unit,
    private val newId: () -> String = ::newDiagnosticId
) {
    private val mutex = Mutex()
    private val maximumCharacters = 1_000_000
    private fun prefix(owner: SupplierSessionOwner): String {
        check(owner.isCurrent() && supplierRequestIdentityValid(owner.accountId))
        return "supplier-pending-v1:${owner.accountId}:"
    }
    private fun createKey(owner: SupplierSessionOwner, store: String, item: String): String {
        require(supplierRequestIdentityValid(store) && supplierRequestIdentityValid(item))
        return prefix(owner) + "create:$store:$item"
    }
    private fun createKey(owner: SupplierSessionOwner, body: SupplierOrderWithLinesDataModel) =
        createKey(owner, body.order.storeId, body.lines.map { it.goodsItemId }.sorted().first())
    private fun receiptKey(owner: SupplierSessionOwner, id: String): String {
        require(supplierRequestIdentityValid(id))
        return prefix(owner) + "receipt:$id"
    }
    private fun validCreate(owner: SupplierSessionOwner, body: SupplierOrderWithLinesDataModel): Boolean =
        supplierRequestIdentityValid(body.order.id) && body.order.userId == owner.accountId &&
            supplierRequestIdentityValid(body.order.storeId) && supplierRequestIdentityValid(body.order.supplierId) &&
            body.order.isActive && body.order.status in setOf(SupplierOrderStatusDataModel.Draft, SupplierOrderStatusDataModel.Sent) &&
            body.lines.size in 1..500 && body.lines.map { it.goodsItemId }.distinct().size == body.lines.size &&
            body.lines.all { it.isActive && it.orderId == body.order.id && supplierRequestIdentityValid(it.goodsItemId) &&
                (it.id.isBlank() || supplierRequestIdentityValid(it.id)) && supplierOrderQuantityValid(it.requestedQuantity) &&
                (it.expectedSupplyPrice == null || supplierRequestPriceValid(it.expectedSupplyPrice)) }
    private fun validReceipt(body: ReceiveSupplierOrderRequestDataModel): Boolean =
        supplierRequestIdentityValid(body.orderId) && supplierRequestIdentityValid(body.receiptId) &&
            body.receivedLines.size in 1..500 && body.receivedLines.map { it.orderLineId }.distinct().size == body.receivedLines.size &&
            body.receivedLines.all { supplierRequestIdentityValid(it.orderLineId) && supplierRequestIdentityValid(it.goodsItemId) &&
                supplierOrderQuantityValid(it.receivedQuantity) && supplierRequestPriceValid(it.actualSupplyPrice) }
    private fun valid(owner: SupplierSessionOwner, value: SupplierPendingRequest, key: String): Boolean =
        value.accountId == owner.accountId && value.schema == 1 && !(value.create != null && value.receipt != null) &&
            (value.create?.let { validCreate(owner, it) && createKey(owner, it) == key } ?: true) &&
            (value.receipt?.let { validReceipt(it) && receiptKey(owner, it.orderId) == key } ?: true)
    private suspend fun load(owner: SupplierSessionOwner, key: String): SupplierPendingRequest {
        check(owner.isCurrent())
        val text = read(key)
        check(text == null || text.length <= maximumCharacters) { "Supplier request journal is too large" }
        val value = text?.let { jsonBase.decodeFromString<SupplierPendingRequest>(it) } ?: SupplierPendingRequest(owner.accountId)
        check(owner.isCurrent() && valid(owner, value, key)) { "Supplier request journal is invalid" }
        return value
    }
    private suspend fun save(owner: SupplierSessionOwner, key: String, value: SupplierPendingRequest) {
        check(owner.isCurrent() && valid(owner, value, key))
        val text = jsonBase.encodeToString(value)
        check(text.length <= maximumCharacters)
        write(key, text)
        check(owner.isCurrent() && read(key) == text)
    }
    private fun intent(body: SupplierOrderWithLinesDataModel) = body.copy(
        order = body.order.copy(id = "", userId = "", createdAtMillis = 0L, updatedAtMillis = 0L, orderedAtMillis = 0L),
        lines = body.lines.sortedBy { it.goodsItemId }.map { it.copy(id = "", orderId = "") })
    suspend fun prepareCreate(owner: SupplierSessionOwner, body: SupplierOrderWithLinesDataModel): SupplierOrderWithLinesDataModel = mutex.withLock {
        check(body.order.userId.isBlank() || body.order.userId == owner.accountId)
        val id = body.order.id.takeIf(String::isNotBlank) ?: newId()
        val prepared = body.copy(order = body.order.copy(id = id, userId = owner.accountId), lines = body.lines.map { it.copy(orderId = id) })
        check(validCreate(owner, prepared))
        val key = createKey(owner, prepared); val state = load(owner, key)
        state.create?.let { saved ->
            if (intent(saved) != intent(prepared)) throw SupplierPendingRequestConflict()
            return@withLock saved
        }
        save(owner, key, state.copy(create = prepared)); prepared
    }
    suspend fun completeCreate(owner: SupplierSessionOwner, request: SupplierOrderWithLinesDataModel) = mutex.withLock {
        val key = createKey(owner, request); val state = load(owner, key)
        if (state.create?.order?.id == request.order.id) save(owner, key, state.copy(create = null))
    }
    suspend fun prepareReceipt(owner: SupplierSessionOwner, request: ReceiveSupplierOrderRequestDataModel): ReceiveSupplierOrderRequestDataModel = mutex.withLock {
        val prepared = request.copy(receiptId = request.receiptId.takeIf(String::isNotBlank) ?: newId())
        check(validReceipt(prepared))
        val key = receiptKey(owner, request.orderId); val state = load(owner, key)
        state.receipt?.let { saved ->
            if (saved.copy(receiptId = "") != request.copy(receiptId = "")) throw SupplierPendingRequestConflict()
            return@withLock saved
        }
        save(owner, key, state.copy(receipt = prepared)); prepared
    }
    suspend fun completeReceipt(owner: SupplierSessionOwner, request: ReceiveSupplierOrderRequestDataModel) = mutex.withLock {
        val key = receiptKey(owner, request.orderId); val state = load(owner, key)
        if (state.receipt?.receiptId == request.receiptId) save(owner, key, state.copy(receipt = null))
    }
    suspend fun pendingReceipt(owner: SupplierSessionOwner, orderId: String) = mutex.withLock { load(owner, receiptKey(owner, orderId)).receipt }
    suspend fun pendingCreate(owner: SupplierSessionOwner, storeId: String, itemId: String) = mutex.withLock { load(owner, createKey(owner, storeId, itemId)).create }
}
internal val supplierPendingRequests by lazy { SupplierPendingRequestStore(::readRequiredJsonJournal, ::writeJsonCacheText) }

internal suspend fun clearSupplierPendingRequests(accountId: String) {
    require(supplierRequestIdentityValid(accountId))
    val prefix = "supplier-pending-v1:$accountId:"
    // Validated canonical UUID suffixes give exact logical lengths for the chunk namespaces.
    for ((kind, suffixLength) in listOf("create:" to 73, "receipt:" to 36)) {
        val scope = prefix + kind
        for (keyPrefix in listOf(scope, "aita-cache-chunks-v1:${scope.length + suffixLength}:$scope"))
            appDatabase.app_databaseQueries.deleteKvPrefixExcept(prefix = keyPrefix, manifestKey = "", keepPrefix = "")
    }
}

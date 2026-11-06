package kz.aita

import io.ktor.http.HttpMethod
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

@Serializable
data class ReplenishmentDraft(
    val schema: Int = 1,
    val accountId: String,
    val storeId: String,
    val settings: ReplenishmentSettings = ReplenishmentSettings(),
    val supplierId: String = "",
    val quantities: Map<String, String> = emptyMap(),
    val pendingOrder: SupplierOrderWithLinesDataModel? = null,
    val completedOrderId: String? = null,
)

data class ReplenishmentLoad(
    val snapshot: ReplenishmentSnapshot? = null,
    val cached: Boolean = false,
    val message: List<LocalizedStringDataModel>? = null,
)

fun canUseReplenishment(storeId: String?): Boolean =
    currentUserCanViewAnalytics(storeId) &&
        currentUserCanViewStock(storeId) &&
        currentUserCanViewSupplierOrders(storeId)

// An erase failure must not let a later offline refresh resurrect a denied snapshot.
private val deniedReplenishmentCaches = MutableStateFlow<Set<String>>(emptySet())

private fun validReplenishment(
    snapshot: ReplenishmentSnapshot,
    storeId: String,
    days: Int,
    zone: String,
): Boolean =
    snapshot.storeId == storeId &&
        snapshot.lookbackDays == days &&
        snapshot.timeZoneId == zone &&
        snapshot.generatedAtMillis > 0 &&
        snapshot.startMillis < snapshot.endMillisExclusive &&
        snapshot.endMillisExclusive <= snapshot.generatedAtMillis &&
        snapshot.items.size <= 5000 &&
        snapshot.items.map { it.goodsItem.id }.distinct().size == snapshot.items.size &&
        snapshot.items.all { item ->
            item.goodsItem.storeId == storeId &&
                item.observationDays in 0..days &&
                item.saleDays in 0..days &&
                listOf(item.sold, item.returned).all { it.isFinite() && it >= 0 } &&
                item.stock.all { it.quantity.isFinite() && it.quantity >= 0 } &&
                item.incoming.all { it.quantity.isFinite() && it.quantity >= 0 }
        }

suspend fun loadReplenishment(
    days: Int,
    zone: String,
    onCached: suspend (ReplenishmentLoad) -> Unit = {},
): ReplenishmentLoad =
    withContext(Dispatchers.ourIo) {
        val owner = inventoryOwners.current
        val store =
            owner.storeId
                ?: return@withContext ReplenishmentLoad(message = replenishmentMessage("access"))
        fun current() = inventoryOwnerIsCurrent(owner) && canUseReplenishment(store)
        if (!current())
            return@withContext ReplenishmentLoad(message = replenishmentMessage("access"))
        require(days in setOf(7, 28, 90) && zone.length <= 100)
        val key = inventoryCacheKey("replenishment-$days-$zone", owner)
        var cached: ReplenishmentSnapshot? =
            try {
                (if (key in deniedReplenishmentCaches.value) null else readRequiredJsonJournal(key))
                    ?.takeIf { it.length <= 8_000_000 }
                    ?.let { jsonBase.decodeFromString<ReplenishmentSnapshot>(it) }
                    ?.takeIf { validReplenishment(it, store, days, zone) }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                null
            }
        if (!current()) return@withContext ReplenishmentLoad()
        // Opening a saved plan must not wait for a slow or absent network connection.
        cached?.let {
            onCached(ReplenishmentLoad(it, cached = true, message = replenishmentMessage("cached")))
        }
        if (!current()) return@withContext ReplenishmentLoad()
        try {
            val response =
                withTimeoutOrNull(20_000) {
                    networkRequest<ReplenishmentSnapshot, Unit>(
                        method = HttpMethod.Get,
                        endpointUrl = "analytics/replenishment",
                        headers = mapOf("store_id" to store),
                        query = mapOf("days" to days, "zone" to zone),
                        expectedSessionGeneration = owner.sessionGeneration,
                    )
                }
            if (!current()) return@withContext ReplenishmentLoad()
            if (response?.httpStatusCode == 403 && !response.transportFailure) {
                cached = null
                deniedReplenishmentCaches.update { it + key }
                try {
                    deleteJsonCacheText(key)
                } catch (_: Exception) {
                    /* Never expose the denied cached data in this response. */
                }
                return@withContext ReplenishmentLoad(message = replenishmentMessage("access"))
            }
            val snapshot =
                response?.payload?.takeIf {
                    !response.negative && validReplenishment(it, store, days, zone)
                }
            if (snapshot != null) {
                val text = jsonBase.encodeToString(snapshot)
                var storageError = false
                try {
                    if (text.length > 8_000_000) storageError = true
                    else {
                        writeJsonCacheText(key, text)
                        deniedReplenishmentCaches.update { it - key }
                    }
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (_: Exception) {
                    storageError = true
                }
                if (!current()) return@withContext ReplenishmentLoad()
                ReplenishmentLoad(
                    snapshot,
                    message = if (storageError) replenishmentMessage("storage") else null,
                )
            } else
                ReplenishmentLoad(
                    cached,
                    cached != null,
                    response?.message
                        ?: replenishmentMessage(if (cached != null) "cached" else "offline_empty"),
                )
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            if (current())
                ReplenishmentLoad(
                    cached,
                    cached != null,
                    replenishmentMessage(if (cached != null) "cached" else "offline_empty"),
                )
            else ReplenishmentLoad()
        }
    }

private fun replenishmentDraftKey(account: String, store: String): String {
    require(supplierRequestIdentityValid(account) && supplierRequestIdentityValid(store))
    return "replenishment-draft-v1:$account:$store"
}

private fun validateReplenishmentDraft(draft: ReplenishmentDraft, account: String, store: String) {
    require(
        draft.schema == 1 &&
            draft.accountId == account &&
            draft.storeId == store &&
            draft.quantities.size <= 500
    )
    require(draft.supplierId.isEmpty() || supplierRequestIdentityValid(draft.supplierId))
    require(draft.completedOrderId == null || supplierRequestIdentityValid(draft.completedOrderId))
    require(
        draft.settings.event in setOf("regular", "new_year", "nauryz", "school", "kurban", "custom")
    )
    require(
        draft.settings.leadDays in 0..180 &&
            draft.settings.coverDays in 1..90 &&
            draft.settings.bufferPercent in 0..100 &&
            draft.settings.upliftPercent in 0..300 &&
            draft.settings.lookbackDays in setOf(7, 28, 90) &&
            draft.settings.categoryId.length <= 120
    )
    require(
        draft.settings.arrivalDate.isEmpty() ||
            runCatching { kotlinx.datetime.LocalDate.parse(draft.settings.arrivalDate) }.isSuccess
    )
    require(
        draft.quantities.all { (id, value) ->
            supplierRequestIdentityValid(id) && value.length <= 40
        }
    )
    draft.pendingOrder?.let { pending ->
        require(
            pending.order.userId == account &&
                pending.order.storeId == store &&
                supplierRequestIdentityValid(pending.order.id) &&
                pending.order.supplierId == draft.supplierId &&
                supplierRequestIdentityValid(pending.order.supplierId) &&
                pending.order.status == SupplierOrderStatusDataModel.Sent &&
                pending.lines.size in 1..500
        )
        require(pending.lines.map { it.goodsItemId }.distinct().size == pending.lines.size)
        require(
            pending.lines.all {
                it.orderId == pending.order.id &&
                    supplierRequestIdentityValid(it.id) &&
                    supplierRequestIdentityValid(it.goodsItemId) &&
                    it.requestedQuantity.total.isFinite() &&
                    it.requestedQuantity.total > 0 &&
                    it.requestedQuantity.total <= 999_999_999
            }
        )
    }
}

suspend fun readReplenishmentDraft(account: String, store: String): ReplenishmentDraft =
    withContext(Dispatchers.ourIo) {
        val text = readRequiredJsonJournal(replenishmentDraftKey(account, store))
        require(text == null || text.length <= 2_000_000)
        val value =
            text?.let { jsonBase.decodeFromString<ReplenishmentDraft>(it) }
                ?: ReplenishmentDraft(accountId = account, storeId = store)
        validateReplenishmentDraft(value, account, store)
        value
    }

suspend fun saveReplenishmentDraft(draft: ReplenishmentDraft) =
    withContext(Dispatchers.ourIo) {
        validateReplenishmentDraft(draft, draft.accountId, draft.storeId)
        val key = replenishmentDraftKey(draft.accountId, draft.storeId)
        val text = jsonBase.encodeToString(draft)
        require(text.length <= 2_000_000)
        writeJsonCacheText(key, text)
        check(readRequiredJsonJournal(key) == text)
    }

package kz.aita

import io.ktor.http.HttpMethod
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal val shelfOrderSaveMutex = Mutex()

fun saveShelfOrder(request: ShelfOrderRequest, onCompleted: ((DataState<ShelfOrderResult>) -> Unit)? = null) {
    val owner = inventoryOwners.current
    if (owner.storeId != request.storeId || !inventoryOwnerIsCurrent(owner)) return
    GlobalScope.launch(Dispatchers.ourIo) {
        shelfOrderSaveMutex.withLock {
            if (!inventoryOwnerIsCurrent(owner)) return@withLock
            val response = networkRequest<ShelfOrderResult, ShelfOrderRequest>(
                HttpMethod.Post, endpointUrl = "stockBatches/reorderShelf", body = request,
                headers = mapOf("store_id" to request.storeId), expectedSessionGeneration = owner.sessionGeneration
            )
            if (!inventoryOwnerIsCurrent(owner)) return@withLock
            val result = response.payload
            if (response.negative || result == null || !shelfOrderAcknowledgementMatches(request, result)) {
                val message = response.message?.takeIf { it.isNotEmpty() } ?: eventMessage("inventory.shelf_retry")
                postInAppNotification(message, NotificationType.Negative)
                // No optimistic quantity or price changes to undo. Reload the authoritative order after a conflict.
                getStock(request.storeId)
                getStockBatches(request.storeId)
                withContext(Dispatchers.Main) { if (inventoryOwnerIsCurrent(owner)) onCompleted?.invoke(DataState.Empty(message)) }
                return@withLock
            }
            val accepted = inventoryStateMutex.withLock {
                if (!inventoryOwnerIsCurrent(owner)) return@withLock false
                val stock = stockState.payloadValue.orEmpty()
                val resultIds = result.batches.map { it.id }.toSet()
                stockState.emit(DataState.Success(stock.map { if (it.id == result.item.id && it.updatedAtMillis <= result.item.updatedAtMillis) result.item else it }))
                // The server response contains only this item's shelf rows, not the whole catalogue.
                val currentBatches = stockBatchesState.payloadValue.orEmpty()
                val existingById = currentBatches.associateBy { it.id }
                stockBatchesState.emit(DataState.Success(currentBatches.filterNot { it.id in resultIds } + result.batches.map { remote ->
                    existingById[remote.id]?.takeIf { it.updatedAtMillis > remote.updatedAtMillis } ?: remote
                }))
                true
            }
            if (!accepted || !inventoryOwnerIsCurrent(owner)) return@withLock
            if (result.changed) postInAppNotification(response.message ?: eventMessage("inventory.shelf_saved"), NotificationType.Positive)
            withContext(Dispatchers.Main) {
                if (inventoryOwnerIsCurrent(owner)) onCompleted?.invoke(DataState.Success(result, response.message))
            }
        }
    }
}

package kz.aita

import io.ktor.http.HttpMethod
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

/** Durable inventory writes. IDs and optimistic preconditions survive lost replies and restarts. */
@Serializable
data class InventoryCreateCommand(
    val id: String,
    val storeId: String,
    val item: GoodsItemDataModel? = null,
    val batches: List<GoodsBatchDataModel> = emptyList(),
    val failure: List<LocalizedStringDataModel>? = null,
    val editsItem: Boolean = false,
    val previousItem: GoodsItemDataModel? = null,
    val dependsOn: String? = null,
    val rejected: Boolean = false
)

/** One journal per account, independent of the selected store and of short-lived session tokens. */
internal class InventoryCreateJournal(
    private val read: suspend (String) -> String?,
    private val write: suspend (String, String) -> Unit
) {
    private val mutex = Mutex()
    private suspend fun load(account: String): List<InventoryCreateCommand> =
        read(account)?.let { jsonBase.decodeFromString<List<InventoryCreateCommand>>(it) } ?: emptyList()

    suspend fun list(account: String): List<InventoryCreateCommand> = mutex.withLock { load(account) }
    suspend fun change(account: String, transform: (List<InventoryCreateCommand>) -> List<InventoryCreateCommand>): List<InventoryCreateCommand> = mutex.withLock {
        val next = transform(load(account))
        write(account, jsonBase.encodeToString(next))
        next
    }
}

val inventoryCreatePendingState = MutableStateFlow<List<InventoryCreateCommand>>(emptyList())
internal object InventoryCreates {
    private val journal = InventoryCreateJournal(
        { readRequiredJsonJournal("inventory-create-outbox-v1:$it") },
        { key, value -> writeJsonCacheText("inventory-create-outbox-v1:$key", value) })
    private val sender = Mutex()
    private val pendingSync = OwnedConnectionJob()
    private val initialSubmissions = MutableStateFlow<Set<String>>(emptySet())

    // A pending write must not wait for a WebSocket handshake or the broad refresh cooldown.
    // One bounded worker exists only while this account has queued changes; browser suspension
    // parks it rather than accumulating retries. flush() rechecks account and generation itself.
    private fun syncPendingSoon() {
        if (!aitaInitializationStarted || initialSubmissions.value.isNotEmpty() || inventoryCreatePendingState.value.isEmpty()) return
        pendingSync.startIfIdle(GlobalScope, Dispatchers.ourIo) {
            val thisJob = currentCoroutineContext()[Job]
            try {
                while (isActive && inventoryCreatePendingState.value.isNotEmpty()) {
                    awaitClientBackgroundWork()
                    flush(force = true)
                    if (inventoryCreatePendingState.value.isNotEmpty()) delay(15_000L)
                }
            } finally {
                pendingSync.clear(thisJob)
            }
        }
    }

    private fun publish(account: String, list: List<InventoryCreateCommand>) {
        if (userAccountState.payloadValue?.id == account) {
            inventoryCreatePendingState.value = list
            if (list.isNotEmpty()) syncPendingSoon()
        }
    }
    suspend fun pending(account: String): List<InventoryCreateCommand> = journal.list(account).also { publish(account, it) }
    private suspend fun change(account: String, transform: (List<InventoryCreateCommand>) -> List<InventoryCreateCommand>) =
        journal.change(account, transform).also { publish(account, it) }

    suspend fun overlayItems(owner: InventoryOwner, cloud: List<GoodsItemDataModel>): List<GoodsItemDataModel> {
        val account = owner.accountId ?: return cloud
        val items = pending(account).filter { it.storeId == owner.storeId }.mapNotNull { it.item }
        return mergeById(cloud, items) { it.id }
    }
    suspend fun overlayBatches(owner: InventoryOwner, cloud: List<GoodsBatchDataModel>): List<GoodsBatchDataModel> {
        val account = owner.accountId ?: return cloud
        val batches = pending(account).filter { it.storeId == owner.storeId }.flatMap { it.batches }
        return mergeById(cloud, batches) { it.id }
    }

    private suspend fun project(owner: InventoryOwner, command: InventoryCreateCommand, remove: Boolean = false, ignorePendingIds: Set<String> = emptySet()): Boolean =
        inventoryStateMutex.withLock {
            if (userAccountState.payloadValue?.id != owner.accountId || !authenticatedSessionGenerationIsCurrent(owner.sessionGeneration)) return@withLock false
            val current = inventoryOwnerIsCurrent(owner)
            if (current && stockLoadStatusState.value.accessDenied) return@withLock false
            var durable = true
            command.item?.let { proposed ->
                val item = if (remove && command.editsItem) command.previousItem ?: proposed else proposed
                val cached = (if (current) stockState.payloadValue else null) ?: readJsonCacheText(CACHE_PREFIX + inventoryCacheKey("stock", owner))
                    ?.let { jsonBase.decodeFromString<List<GoodsItemDataModel>>(it) }.orEmpty()
                val base = cached.filterNot { it.id == item.id } + if (remove && !command.editsItem) emptyList() else listOf(item)
                val queue = journal.list(requireNotNull(owner.accountId))
                val at = queue.indexOfFirst { it.id == command.id }
                val later = (if (at >= 0) queue.drop(at + 1) else queue).filter { it.storeId == owner.storeId && it.id !in ignorePendingIds }.mapNotNull { it.item }
                val rows = mergeById(base, later) { it.id }
                val saved = persistInventoryCacheLocked("stock", owner, rows)
                durable = durable && saved
                if (current) {
                    stockState.emit(DataState.Success(rows))
                    stockLoadStatusState.value = stockLoadStatusState.value.copy(source = InventoryLoadSource.Local, cacheWriteFailed = !saved, failure = null)
                }
            }
            if (command.batches.isNotEmpty()) {
                val ids = command.batches.map { it.id }.toSet()
                val cached = (if (current) stockBatchesState.payloadValue else null) ?: readJsonCacheText(CACHE_PREFIX + inventoryCacheKey("stock_batches", owner))
                    ?.let { jsonBase.decodeFromString<List<GoodsBatchDataModel>>(it) }.orEmpty()
                val rows = cached.filterNot { it.id in ids } + if (remove) emptyList() else command.batches
                val saved = persistInventoryCacheLocked("stock_batches", owner, rows)
                durable = durable && saved
                if (current) {
                    stockBatchesState.emit(DataState.Success(rows))
                    stockBatchesLoadStatusState.value = stockBatchesLoadStatusState.value.copy(source = InventoryLoadSource.Local, cacheWriteFailed = !saved, failure = null)
                }
            }
            durable
        }

    suspend fun createItem(owner: InventoryOwner, input: GoodsItemDataModel): DataState<GoodsItemDataModel> {
        val codes = input.allBarcodeValues().map { it.normalizedBarcodeToken() }.filter { it.isNotBlank() }.toSet()
        if (stockState.payloadValue.orEmpty().any { existing -> existing.storeId == owner.storeId && existing.isActive &&
                existing.allBarcodeValues().any { it.normalizedBarcodeToken() in codes } }) return DataState.Empty(stockEditingMessage("duplicate"))
        val item = input.copy(id = input.id.ifBlank { newDiagnosticId() }, userId = owner.accountId.orEmpty())
        val command = InventoryCreateCommand(newDiagnosticId(), item.storeId, item = item)
        val response = submit(owner, command, STORE_PERMISSION_STOCK_ITEM_CREATE)
        return if (response.negative) DataState.Empty(response.message) else DataState.Success(response.payload?.item ?: item, response.message)
    }
    suspend fun updateItem(owner: InventoryOwner, input: GoodsItemDataModel): DataState<GoodsItemDataModel> {
        val account = owner.accountId ?: return DataState.Empty(currentUserPermissionDeniedMessage())
        val previous = stockState.payloadValue.orEmpty().firstOrNull { it.id == input.id && it.storeId == owner.storeId }
            ?: return DataState.Empty(inventoryLoadFailureMessage())
        val dependency = pending(account).lastOrNull { it.item?.id == input.id && it.storeId == owner.storeId }
        if (dependency?.rejected == true) return DataState.Empty(dependency.failure)
        val operation = newDiagnosticId()
        val item = input.copy(updateOperationId = operation, expectedUpdatedAtMillis = input.expectedUpdatedAtMillis ?: previous.updatedAtMillis)
        val command = InventoryCreateCommand(operation, input.storeId, item = item, editsItem = true,
            previousItem = previous, dependsOn = dependency?.id)
        val permission = if (currentUserHasStorePermission(owner.storeId, STORE_PERMISSION_STOCK_ITEM_EDIT))
            STORE_PERMISSION_STOCK_ITEM_EDIT else STORE_PERMISSION_STOCK_PROMOTIONS_MANAGE
        val response = submit(owner, command, permission)
        return if (response.negative) DataState.Empty(response.message) else DataState.Success(response.payload?.item ?: item, response.message)
    }

    suspend fun createBatches(owner: InventoryOwner, input: List<GoodsBatchDataModel>): DataState<List<GoodsBatchDataModel>> {
        val now = getCurrentTimeMillis()
        val batches = input.map { it.copy(id = it.id.ifBlank { newDiagnosticId() }, userId = owner.accountId.orEmpty(),
            createdAtMillis = now, updatedAtMillis = now, createdByUserId = owner.accountId) }
        val command = InventoryCreateCommand(newDiagnosticId(), owner.storeId.orEmpty(), batches = batches)
        val response = submit(owner, command, STORE_PERMISSION_STOCK_BATCH_CREATE)
        return if (response.negative) DataState.Empty(response.message) else DataState.Success(response.payload?.batches ?: batches, response.message)
    }
    private suspend fun submit(owner: InventoryOwner, command: InventoryCreateCommand, permission: String): ResponseDataModel<InventoryCreateCommand> {
        fun failure(message: List<LocalizedStringDataModel>) = ResponseDataModel<InventoryCreateCommand>(message, null, true)
        if (!inventoryOwnerIsCurrent(owner) || command.storeId != owner.storeId ||
            command.batches.any { it.storeId != owner.storeId } ||
            !currentUserHasStorePermission(owner.storeId, permission) || stockLoadStatusState.value.accessDenied)
            return failure(currentUserPermissionDeniedMessage())
        val online = cloudTransportStatusState.value != CLOUD_TRANSPORT_STATUS_UNAVAILABLE &&
            cloudTransportStatusState.value != CLOUD_TRANSPORT_STATUS_AUTH_REFRESH_REQUIRED && getStoredUserAuthTokens?.invoke() != null
        // Online requests can obtain fresh access; offline requests require a previously verified entitlement.
        if (!online && !currentStoreHasWorkspaceAccess(owner.storeId)) return failure(eventMessage("subscription.verify"))
        val account = requireNotNull(owner.accountId)
        initialSubmissions.update { it + command.id }
        try {
            try {
                change(account) { it + command }
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { RuntimeDiagnostics.capture(error, "inventory_journal_write"); return failure(stockEditingMessage("storage")) }
            try { project(owner, command) }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { /* The committed journal still owns the operation; hydration can recover it. */ }
            if (online) {
                val result = flush(command.id)
                if (result != null) return result
            }
            return ResponseDataModel(stockEditingMessage("saved_local"), command, false)
        } finally {
            initialSubmissions.update { it - command.id }
            syncPendingSoon()
        }
    }

    /** Caller may be a reconnect, manual retry or initial create. No queued operation changes account. */
    suspend fun flush(initialId: String? = null, force: Boolean = false): ResponseDataModel<InventoryCreateCommand>? {
        if (getStoredUserAuthTokens?.invoke() == null || (!force && cloudTransportStatusState.value == CLOUD_TRANSPORT_STATUS_UNAVAILABLE) || !sender.tryLock()) return null
        val account = userAccountState.payloadValue?.id
        val generation = currentAuthenticatedSessionGeneration()
        try {
            if (account == null) return null
            val validation = withTimeoutOrNull(12_000L) { ensureCloudSessionReadyForProtectedRequest(retryAfterTransportRecovery = force) }
            if (validation == null || validation.negative) return null
            val blockedStores = mutableSetOf<String>()
            for (command in pending(account)) {
                if (userAccountState.payloadValue?.id != account || !authenticatedSessionGenerationIsCurrent(generation)) return null
                if (command.storeId in blockedStores) continue
                if (command.rejected) {
                    blockedStores += command.storeId
                    continue
                }
                // Re-read after earlier acknowledgments: their accepted timestamp unlocks dependent edits.
                val readyCommand = pending(account).firstOrNull { it.id == command.id } ?: continue
                if (readyCommand.dependsOn != null) { blockedStores += command.storeId; continue }
                if (command.id != initialId && command.id in initialSubmissions.value) {
                    blockedStores += command.storeId
                    continue
                }
                val response: ResponseDataModel<InventoryCreateCommand> = withTimeoutOrNull(12_000L) { send(readyCommand, generation) }
                    ?: ResponseDataModel(stockEditingMessage("pending"), null, true, transportFailure = true)
                if (userAccountState.payloadValue?.id != account || !authenticatedSessionGenerationIsCurrent(generation)) return null
                val owner = inventoryOwners.current
                if (!response.negative && response.payload != null) {
                    // Cache the accepted result before acknowledging the journal. An interrupted ack
                    // repeats the exact same create UUID; the server returns its existing row.
                    val cached = project(owner.copy(storeId = command.storeId, accountId = account, sessionGeneration = generation), response.payload)
                    if (cached) change(account) { rows -> rows.filterNot { it.id == command.id }.map { next ->
                        if (next.dependsOn == command.id) next.copy(dependsOn = null,
                            item = next.item?.copy(expectedUpdatedAtMillis = response.payload.item?.updatedAtMillis),
                            previousItem = response.payload.item) else next
                    } }
                    else change(account) { rows -> rows.map { if (it.id == command.id) it.copy(failure = stockEditingMessage("storage")) else it } }
                    if (command.id == initialId) return response
                } else {
                    val definiteRejection = !response.transportFailure && response.httpStatusCode in listOf(400, 403, 404, 409, 422)
                    if (command.id == initialId && definiteRejection && pending(account).none { it.dependsOn == command.id }) {
                        change(account) { rows -> rows.filterNot { it.id == command.id } }
                        project(owner.copy(storeId = command.storeId, accountId = account, sessionGeneration = generation), command, remove = true)
                        return response
                    }
                    change(account) { rows -> rows.map { if (it.id == command.id) it.copy(failure = if (response.transportFailure) null else response.message, rejected = definiteRejection) else it } }
                    blockedStores += command.storeId
                    if (response.transportFailure || response.httpStatusCode == 401) break
                }
            }
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { /* Leave the durable journal for the next retry. */ }
        finally { sender.unlock() }
        return null
    }

    suspend fun retryRejected() {
        val owner = inventoryOwners.current
        val account = owner.accountId ?: return
        sender.withLock {
            if (inventoryOwnerIsCurrent(owner)) change(account) { rows -> rows.map { it.copy(rejected = false) } }
        }
        flush(force = true)
    }

    // Only a user-confirmed rejection can be discarded, never an uncertain server acknowledgment.
    suspend fun discardRejected(id: String): Boolean = sender.withLock {
        val owner = inventoryOwners.current
        val account = owner.accountId ?: return@withLock false
        val rows = pending(account)
        val failed = rows.firstOrNull { it.id == id && it.storeId == owner.storeId && it.rejected } ?: return@withLock false
        val discarded = mutableSetOf(id)
        rows.forEach { row ->
            if (row.dependsOn in discarded || (!failed.editsItem && failed.item != null && row.batches.any { it.goodsItemId == failed.item.id })) discarded += row.id
        }
        if (!inventoryOwnerIsCurrent(owner)) return@withLock false
        // Save the rollback projection before removing the journal. A disk failure leaves it reviewable.
        val restore = failed.copy(item = failed.previousItem ?: failed.item)
        val cached = project(owner, restore, remove = true, ignorePendingIds = discarded)
        if (!cached) return@withLock false
        for (row in rows.filter { it.id in discarded && it.batches.isNotEmpty() })
            if (!project(owner, row, remove = true, ignorePendingIds = discarded)) return@withLock false
        change(account) { it.filterNot { row -> row.id in discarded } }
        true
    }

    private suspend fun send(command: InventoryCreateCommand, generation: Long): ResponseDataModel<InventoryCreateCommand> {
        val config = globalAppConfigurationState.payloadValue
        val invalidReply = ResponseDataModel<InventoryCreateCommand>(eventMessage("message.the_batch_result_is_not_confirmed_refresh_branch_stock_before_trying"), null, true, 409)
        if (command.item != null) {
            val result = networkRequest<GoodsItemDataModel, GoodsItemDataModel>(if (command.editsItem) HttpMethod.Put else HttpMethod.Post,
                endpointUrl = if (command.editsItem) config.updateGoodsItemPath.first else config.addGoodsItemPath.first, body = command.item,
                headers = mapOf("store_id" to command.storeId), expectedSessionGeneration = generation)
            if (!result.negative && (result.payload?.id != command.item.id || result.payload.storeId != command.storeId)) return invalidReply
            return ResponseDataModel(result.message, result.payload?.let { command.copy(item = it, failure = null) },
                result.negative || result.payload == null, result.httpStatusCode, result.transportFailure)
        }
        val result = networkRequest<List<GoodsBatchDataModel>, List<GoodsBatchDataModel>>(HttpMethod.Post,
            endpointUrl = config.addStockBatchPath.first, body = command.batches,
            headers = mapOf("store_id" to command.storeId), expectedSessionGeneration = generation)
        val valid = result.payload?.let { rows -> rows.size == command.batches.size && rows.all { row ->
            row.storeId == command.storeId && command.batches.any { it.id == row.id && it.goodsItemId == row.goodsItemId }
        } && rows.map { it.id }.distinct().size == rows.size } == true
        if (!result.negative && !valid) return invalidReply
        return ResponseDataModel(result.message, result.payload?.takeIf { valid }?.let { command.copy(batches = it, failure = null) },
            result.negative || !valid, result.httpStatusCode, result.transportFailure)
    }
}

internal fun <T> mergeById(base: List<T>, pending: List<T>, id: (T) -> String): List<T> {
    val pendingIds = pending.map(id).toSet()
    return base.filterNot { id(it) in pendingIds } + pending.associateBy(id).values
}

internal suspend fun stockItemsWithPendingCreates(owner: InventoryOwner, rows: List<GoodsItemDataModel>) =
    InventoryCreates.overlayItems(owner, filterRecentlyDeletedStockItems(rows))
internal suspend fun stockBatchesWithPendingCreates(owner: InventoryOwner, rows: List<GoodsBatchDataModel>) =
    StoreCommerceClient.overlayBatches(owner, InventoryCreates.overlayBatches(owner, filterRecentlyDeletedStockBatches(rows)))

suspend fun retryPendingInventoryCreates() { InventoryCreates.retryRejected() }
suspend fun discardPendingInventoryChange(id: String): Boolean = try {
    InventoryCreates.discardRejected(id)
} catch (cancel: CancellationException) {
    throw cancel
} catch (error: Exception) {
    RuntimeDiagnostics.capture(error, "inventory_rejected_discard")
    false
}

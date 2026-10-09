package kz.aita

import app.cash.sqldelight.async.coroutines.awaitAsList
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

@Serializable
private data class LegacyCartOwner(val accountId: String, val storeId: String, val imported: Boolean = false)

object DynamicCarts {
    private const val LEGACY_OWNER_KEY = "cart-book.legacy-owner.v2"
    private val legacyMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.ourIo)
    private val work = OrderedCartWork(scope)
    private val slots = MutableStateFlow<Map<String, StateFlow<List<GoodsItemInCartDataModel>>>>(emptyMap())
    private val mutableCounts = MutableStateFlow(List(3) { INITIAL_CART_SLOTS })
    val counts = mutableCounts.asStateFlow()
    private val mutableState = MutableStateFlow(CartBookState())
    val state = mutableState.asStateFlow()
    private fun requestedOwner(): CartScope? = inventoryOwners.current.let { owner ->
        val account = owner.accountId ?: return@let null
        val store = owner.storeId ?: return@let null
        CartScope(account, store, owner.sessionGeneration, owner.epoch)
    }
    fun isCurrent(owner: CartScope): Boolean = requestedOwner() == owner &&
        userAccountState.payloadValue?.id == owner.accountId && authenticatedSessionGenerationIsCurrent(owner.generation)
    fun captureScope(): CartScope? = state.value.takeIf { it.ready }?.owner?.takeIf(::isCurrent)
    private val store = DynamicCartStore(::isCurrent, ::loadBook, ::saveBook) { next ->
        if (state.value.owner != next.owner || !next.ready) cartPersistenceHydratedState.value = false
        val book = next.book
        mutableCounts.value = book.counts
        publishCartUiState(book.ui)
        mutableState.value = next
        cartPersistenceHydratedState.value = next.ready && next.owner?.let(::isCurrent) == true
    }
    fun cartState(type: Int, slot: Int): StateFlow<List<GoodsItemInCartDataModel>> {
        require(validCartSlot(type, slot))
        val key = "$type:$slot"
        return slots.updateAndGet { old -> if (key in old) old else
            old + (key to CartLinesStateFlow(state, type, slot, ::isCurrent)) }.getValue(key)
    }
    internal suspend fun adoptCurrent() {
        StoreCommerceClient.start()
        val owner = requestedOwner()
        try { work.run { store.adopt(owner) } }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { postInAppNotification(eventMessage("checkout.ui.restore_error"), NotificationType.Negative, transient = false) }
    }
    internal suspend fun prepareLegacyImport() = legacyMutex.withLock {
        if (getLocalKv(LEGACY_OWNER_KEY) != null) return@withLock
        // A corrupt optional account cache cannot prove who owns legacy rows. Leave every old
        // row/key untouched and let authoritative sign-in recover the account before retrying.
        val account = try { getStoredUserAccountDataModel?.invoke()?.id?.takeIf { it.isNotBlank() } }
        catch (_: SerializationException) { null } ?: return@withLock
        val storeId = getLocalKv(KEY_ACTIVE_STORE_ID)?.takeIf { it.isNotBlank() } ?: return@withLock
        putLocalKv(LEGACY_OWNER_KEY, jsonBase.encodeToString(LegacyCartOwner.serializer(), LegacyCartOwner(account, storeId)))
    }
    private suspend fun loadBook(owner: CartScope): CartBook? {
        val raw = readJsonCacheText(owner.storageKey)
        if (raw != null) return jsonBase.decodeFromString(CartBook.serializer(), raw).validated()
        check(getLocalKv(owner.storageKey + ".present") == null) { "Saved cart journal is unreadable; original data retained" }
        val legacy = getLocalKv(LEGACY_OWNER_KEY)?.let { jsonBase.decodeFromString(LegacyCartOwner.serializer(), it) }
            ?.takeIf { it.accountId == owner.accountId && it.storeId == owner.storeId && !it.imported } ?: return null
        val lines = appDatabase.app_databaseQueries.getAllCarts().awaitAsList().map { row ->
            require(row.transactionTypeIndex in 0L..2L && row.clientId in 0L until MAX_CART_SLOTS.toLong())
            StoredCartLine(row.id, row.transactionTypeIndex.toInt(), row.clientId.toInt(),
                jsonBase.decodeFromString<QuantityDataModel>(row.quantity), row.timeAdded)
        }
        val book = CartBook(lines = lines, ui = readLegacyCartUiState()).validated()
        saveBook(owner, book)
        putLocalKv(LEGACY_OWNER_KEY, jsonBase.encodeToString(LegacyCartOwner.serializer(), legacy.copy(imported = true)))
        // Keep the old table and old preference keys intact as recovery data, never as a live bus.
        return book
    }
    private suspend fun saveBook(owner: CartScope, book: CartBook) {
        val raw = jsonBase.encodeToString(CartBook.serializer(), book.validated())
        putLocalKv(owner.storageKey + ".present", "1")
        writeJsonCacheText(owner.storageKey, raw)
    }
    /** Persist the immutable request before sending it; retries keep the same server idempotency key. */
    suspend fun beginCheckout(owner: CartScope, type: Int, slot: Int, transaction: TransactionDataModel,
        receipt: TransactionReceiptSnapshotDataModel): CartCheckoutAttempt? = work.run {
        val key = "$type:$slot"
        var result: CartCheckoutAttempt? = null
        val saved = store.change(owner) { book ->
            if (!book.contains(type, slot)) return@change book
            result = book.ui.checkouts[key] ?: CartCheckoutAttempt(transaction.withClientOperationId(), receipt)
            book.copy(ui = book.ui.copy(checkouts = book.ui.checkouts + (key to result!!)))
        }
        result.takeIf { saved && isCurrent(owner) }
    }
    suspend fun abandonRejectedCheckout(owner: CartScope, type: Int, slot: Int) = work.run {
        store.change(owner) { book -> book.copy(ui = book.ui.copy(checkouts = book.ui.checkouts - "$type:$slot")) }
    }
    suspend fun add(type: Int): Int? = captureScope()?.let { owner -> work.run { store.add(owner, type) } }
    suspend fun reveal(type: Int, slot: Int): Boolean = captureScope()?.let { owner -> work.run { store.reveal(owner, type, slot) } } ?: false
    suspend fun retryRestore() = adoptCurrent()
    internal fun changeAsync(transform: (CartBook) -> CartBook) {
        val owner = captureScope() ?: return
        val queued = work.post {
            try { store.change(owner, transform) }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { if (isCurrent(owner)) postInAppNotification(eventMessage("checkout.ui.save_error"), NotificationType.Negative, transient = true) }
        }
        if (!queued) postInAppNotification(eventMessage("checkout.ui.save_error"), NotificationType.Negative, transient = true)
    }
    internal suspend fun flush() = work.drain()
    internal fun editUiAsync(transform: (CartUiState) -> CartUiState) = changeAsync { book ->
        val updated = transform(book.ui).preservingPending(book.ui)
        book.copy(ui = if (book.slots == null) updated else updated.retainingCarts(book::contains))
    }
    suspend fun remove(type: Int, slot: Int, owner: CartScope? = captureScope()): Boolean {
        require(validCartSlot(type, slot) && slot >= INITIAL_CART_SLOTS)
        return owner?.let { captured -> work.run { store.change(captured) { it.removeSlot(type, slot) } } } ?: false
    }
    suspend fun delete(type: Int, slot: Int, owner: CartScope? = captureScope()): Boolean {
        require(validCartSlot(type, slot))
        return owner?.let { captured -> work.run { store.change(captured) { book -> book.withoutCart(type, slot) } } } ?: false
    }
    /** Add against the latest durable cart, so successive scans cannot overwrite one another. */
    internal fun addQuantity(id: String, type: Int, slot: Int, delta: QuantityDataModel, onCompleted: ((Boolean) -> Unit)?) {
        val owner = captureScope()
        if (owner == null) { onCompleted?.invoke(false); return }
        val queued = work.post {
            var accepted = false
            val saved = try { store.change(owner) { book ->
                if (!book.contains(type, slot) || "$type:$slot" in book.ui.checkouts) return@change book
                val old = book.lines.firstOrNull { it.id == id && it.type == type && it.slot == slot }
                val quantity = old?.quantity?.let { it.copy(total = it.total + delta.total) } ?: delta
                val maximum = if (type == 1) book.ui.batches[cartReturnBatchSelectionKey(type, slot, id)]?.originalReceiptQuantity else null
                if (!validReceiptCartQuantity(quantity, maximum)) {
                    postInAppNotification(eventMessage("return.quantity_limit"), NotificationType.Negative, transient = true)
                    return@change book
                }
                accepted = true
                val row = StoredCartLine(id, type, slot, quantity, old?.addedAt ?: getCurrentTimeMillis(), old?.supplyPrice)
                book.copy(lines = (book.lines.filterNot { it.id == id && it.type == type && it.slot == slot } + row)
                    .sortedWith(compareBy<StoredCartLine> { it.addedAt }.thenBy { it.id }),
                    ui = book.ui.copy(payments = book.ui.payments - "$type:$slot"))
            } } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) {
                if (isCurrent(owner)) postInAppNotification(eventMessage("checkout.ui.save_error"), NotificationType.Negative, transient = true)
                false
            }
            onCompleted?.invoke(saved && accepted && isCurrent(owner))
        }
        if (!queued) onCompleted?.invoke(false)
    }

    fun setSupplyPrice(id: String, slot: Int, price: PriceDataModel) {
        require(price.price.toDoubleOrNull()?.let { it.isFinite() && it >= 0.0 && it <= 1_000_000_000_000.0 } == true)
        changeAsync { book -> if ("2:$slot" in book.ui.checkouts) book else book.copy(lines = book.lines.map { row ->
            if (row.type == 2 && row.slot == slot && row.id == id) row.copy(supplyPrice = price) else row
        }, ui = book.ui.copy(payments = book.ui.payments - "2:$slot")) }
    }

    internal fun upsert(id: String, type: Int, slot: Int, quantity: QuantityDataModel) {
        require(id.isNotBlank() && validCartSlot(type, slot))
        changeAsync { book ->
            if ("$type:$slot" in book.ui.checkouts || (book.slots != null && !book.contains(type, slot))) return@changeAsync book
            val maximum = if (type == 1) book.ui.batches[cartReturnBatchSelectionKey(type, slot, id)]?.originalReceiptQuantity else null
            if (!validReceiptCartQuantity(quantity, maximum)) {
                postInAppNotification(eventMessage("return.quantity_limit"), NotificationType.Negative, transient = true)
                return@changeAsync book
            }
            val old = book.lines.firstOrNull { it.id == id && it.type == type && it.slot == slot }
            val row = StoredCartLine(id, type, slot, quantity, old?.addedAt ?: getCurrentTimeMillis(), old?.supplyPrice)
            book.copy(lines = (book.lines.filterNot { it.id == id && it.type == type && it.slot == slot } + row)
                .sortedWith(compareBy<StoredCartLine> { it.addedAt }.thenBy { it.id }))
        }
    }
    /** The receipt source and quantity must become durable together, in the captured store/session. */
    suspend fun addReceiptReturn(owner: CartScope, slot: Int, quantity: QuantityDataModel,
        selection: CartReturnBatchSelectionDataModel): Boolean {
        var added = false
        val saved = work.run { store.change(owner) { book ->
            if ("1:$slot" in book.ui.checkouts || !book.contains(1, slot) || book.lines.any { it.type == 1 && it.slot == slot && it.id == selection.goodsItemId }) book
            else {
                require(quantity.total.isFinite() && quantity.total > 0.0 && validReceiptCartQuantity(quantity, selection.originalReceiptQuantity))
                val key = cartReturnBatchSelectionKey(1, slot, selection.goodsItemId)
                added = true
                book.copy(lines = book.lines + StoredCartLine(selection.goodsItemId, 1, slot, quantity, getCurrentTimeMillis()),
                    ui = book.ui.copy(batches = book.ui.batches + (key to selection), payments = book.ui.payments - "1:$slot"))
            }
        } }
        return saved && added && isCurrent(owner)
    }

    internal fun removeItem(id: String, type: Int, slot: Int) {
        require(validCartSlot(type, slot))
        changeAsync { book -> if ("$type:$slot" in book.ui.checkouts) book else book.copy(lines = book.lines.filterNot { it.id == id && it.type == type && it.slot == slot },
            ui = book.ui.withoutItem(type, slot, id)) }
    }
    internal suspend fun removeItemEverywhere(id: String) {
        val owner = captureScope() ?: return
        work.run { store.change(owner) { book ->
            var ui = book.ui
            book.lines.filter { it.id == id }.forEach { ui = ui.withoutItem(it.type, it.slot, id) }
            book.copy(lines = book.lines.filterNot { it.id == id }, ui = ui)
        } }
    }
    suspend fun readNavigation(owner: CartScope): String? {
        val key = owner.storageKey + ".navigation"
        getLocalKv(key)?.let { return it }
        val legacy = getLocalKv(LEGACY_OWNER_KEY)?.let { jsonBase.decodeFromString(LegacyCartOwner.serializer(), it) }
        return if (legacy?.accountId == owner.accountId && legacy.storeId == owner.storeId)
            getLocalKv("cache_json:transaction_navigation_state_v1") else null
    }
    suspend fun writeNavigation(owner: CartScope, raw: String) {
        require(raw.length <= 2_000_000)
        if (isCurrent(owner)) putLocalKv(owner.storageKey + ".navigation", raw)
    }
}

/** Installer handoff may exit the desktop process only after queued cart writes are durable. */
suspend fun flushCartsBeforeClientUpdate() = DynamicCarts.flush()

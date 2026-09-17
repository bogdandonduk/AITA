package kz.aita

import app.cash.sqldelight.async.coroutines.awaitAsList
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

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
        val owner = requestedOwner()
        try { work.run { store.adopt(owner) } }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { postInAppNotification(eventMessage("checkout.ui.restore_error"), NotificationType.Negative, transient = false) }
    }
    internal suspend fun prepareLegacyImport() = legacyMutex.withLock {
        if (getLocalKv(LEGACY_OWNER_KEY) != null) return@withLock
        val account = getStoredUserAccountDataModel?.invoke()?.id?.takeIf { it.isNotBlank() } ?: return@withLock
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
        val updated = transform(book.ui)
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
    internal fun upsert(id: String, type: Int, slot: Int, quantity: QuantityDataModel) {
        require(id.isNotBlank() && validCartSlot(type, slot))
        changeAsync { book ->
            if (book.slots != null && !book.contains(type, slot)) return@changeAsync book
            val old = book.lines.firstOrNull { it.id == id && it.type == type && it.slot == slot }
            val row = StoredCartLine(id, type, slot, quantity, old?.addedAt ?: getCurrentTimeMillis())
            book.copy(lines = (book.lines.filterNot { it.id == id && it.type == type && it.slot == slot } + row)
                .sortedWith(compareBy<StoredCartLine> { it.addedAt }.thenBy { it.id }))
        }
    }
    internal fun removeItem(id: String, type: Int, slot: Int) {
        require(validCartSlot(type, slot))
        changeAsync { book -> book.copy(lines = book.lines.filterNot { it.id == id && it.type == type && it.slot == slot },
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

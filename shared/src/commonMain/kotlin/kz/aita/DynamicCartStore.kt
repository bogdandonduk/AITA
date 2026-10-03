package kz.aita

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

const val INITIAL_CART_SLOTS = 2
/** Explicit limit: restoration refuses invalid data instead of truncating saved carts. */
const val MAX_CART_SLOTS = 1000
fun validCartSlot(type: Int, slot: Int): Boolean = type in 0..2 && slot in 0 until Int.MAX_VALUE

data class CartScope(val accountId: String, val storeId: String, val generation: Long, val epoch: Long) {
    val storageKey: String get() = "cart-book.v2:${accountId.length}:$accountId:${storeId.length}:$storeId"
}

@Serializable
data class StoredCartLine(val id: String, val type: Int, val slot: Int, val quantity: QuantityDataModel, val addedAt: Long, val supplyPrice: PriceDataModel? = null)

@Serializable
data class CartUiState(
    val saleMethods: Map<String, String> = emptyMap(),
    val discounts: Map<String, Double> = emptyMap(),
    val checkouts: Map<String, CartCheckoutAttempt> = emptyMap(),
    val payments: Map<String, TransactionPaymentDraftDataModel> = emptyMap(),
    val suppliers: Map<String, String> = emptyMap(),
    val reasons: Map<String, String> = emptyMap(),
    val batches: Map<String, CartReturnBatchSelectionDataModel> = emptyMap(),
    val checks: Map<String, Boolean> = emptyMap(),
    val scrolls: Map<String, TransactionCartScrollStateDataModel> = emptyMap()
) {
    /** A pending checkout may already exist remotely. Preserve its input until resolved. */
    fun preservingPending(previous: CartUiState): CartUiState {
        fun <T> Map<String, T>.preserve(old: Map<String, T>): Map<String, T> {
            fun locked(key: String) = previous.checkouts.keys.any { key == it || key.startsWith("$it:") }
            return filterKeys { !locked(it) } + old.filterKeys(::locked)
        }
        return copy(discounts = discounts.preserve(previous.discounts), checkouts = previous.checkouts,
            saleMethods = saleMethods.preserve(previous.saleMethods), payments = payments.preserve(previous.payments),
            suppliers = suppliers.preserve(previous.suppliers), reasons = reasons.preserve(previous.reasons),
            batches = batches.preserve(previous.batches))
    }
    fun keys(): Set<String> = discounts.keys + checkouts.keys + saleMethods.keys + payments.keys + suppliers.keys + reasons.keys + batches.keys + checks.keys + scrolls.keys
    fun withoutCart(type: Int, slot: Int): CartUiState {
        val key = "$type:$slot"
        fun <T> Map<String, T>.keepOthers() = filterKeys { it != key && !it.startsWith("$key:") }
        return copy(discounts = discounts.keepOthers(), checkouts = checkouts.keepOthers(), saleMethods = saleMethods.keepOthers(), payments = payments.keepOthers(),
            suppliers = suppliers.keepOthers(), reasons = reasons.keepOthers(), batches = batches.keepOthers(),
            checks = checks.keepOthers(), scrolls = scrolls.keepOthers())
    }
    fun retainingCarts(contains: (Int, Int) -> Boolean): CartUiState {
        fun <T> Map<String, T>.keepActive() = filterKeys { key ->
            val parts = key.split(':', limit = 3)
            val type = parts.getOrNull(0)?.toIntOrNull()
            val slot = parts.getOrNull(1)?.toIntOrNull()
            type != null && slot != null && contains(type, slot)
        }
        return copy(discounts = discounts.keepActive(), checkouts = checkouts.keepActive(), saleMethods = saleMethods.keepActive(), payments = payments.keepActive(),
            suppliers = suppliers.keepActive(), reasons = reasons.keepActive(), batches = batches.keepActive(),
            checks = checks.keepActive(), scrolls = scrolls.keepActive())
    }
    fun withoutItem(type: Int, slot: Int, id: String): CartUiState {
        val key = "$type:$slot:$id"
        return copy(saleMethods = saleMethods - key, reasons = reasons - key, batches = batches - key,
            checks = checks.filterKeys { it != key && !it.startsWith("$key:") })
    }
}

@Serializable
data class CartBook(
    val schema: Int = 2,
    val revision: Long = 0,
    val counts: List<Int> = List(3) { INITIAL_CART_SLOTS },
    val lines: List<StoredCartLine> = emptyList(),
    val ui: CartUiState = CartUiState(),
    // Added lazily on the first removal. Old dense books remain readable without a migration write.
    val slots: List<List<Int>>? = null,
    val nextSlotIds: List<Int>? = null
) {
    fun activeSlots(type: Int): List<Int> = slots?.get(type) ?: (0 until counts[type]).toList()
    fun contains(type: Int, slot: Int): Boolean = validCartSlot(type, slot) &&
        (slots?.get(type)?.contains(slot) ?: (slot < counts[type]))
    fun removeSlot(type: Int, slot: Int): CartBook {
        require(validCartSlot(type, slot) && slot >= INITIAL_CART_SLOTS)
        if (!contains(type, slot)) return this
        val active = List(3) { activeSlots(it) }.mapIndexed { index, ids -> if (index == type) ids - slot else ids }
        return withoutCart(type, slot).copy(counts = active.map { it.size }, slots = active,
            nextSlotIds = nextSlotIds ?: counts)
    }
    fun validated(): CartBook {
        require(ui.discounts.all { (key, value) -> key.startsWith("0:") && validQuickDiscount(value) })
        require(ui.checkouts.values.all { it.transaction.clientOperationId.isNotBlank() })
        require(schema == 2 && revision >= 0 && counts.size == 3 && counts.all { it in INITIAL_CART_SLOTS..MAX_CART_SLOTS })
        require(lines.all { row -> row.supplyPrice == null || row.type == 2 && row.supplyPrice.price.toDoubleOrNull()?.let { it.isFinite() && it >= 0.0 && it <= 1_000_000_000_000.0 } == true })
        require(lines.size <= 20_000 && lines.all { it.id.isNotBlank() && validCartSlot(it.type, it.slot) })
        require(lines.map { Triple(it.id, it.type, it.slot) }.distinct().size == lines.size)
        if (slots != null) {
            require(slots.size == 3 && nextSlotIds?.size == 3)
            slots.forEachIndexed { type, ids ->
                require(ids.size == counts[type] && ids.take(2) == listOf(0, 1) && ids.distinct().size == ids.size)
                require(ids.all { validCartSlot(type, it) && it < nextSlotIds!![type] })
            }
            require(lines.all { contains(it.type, it.slot) })
            require(ui == ui.retainingCarts(::contains)) { "Saved UI references a removed cart" }
            return this
        }
        require(nextSlotIds == null)
        val needed = counts.toMutableList()
        lines.forEach { needed[it.type] = maxOf(needed[it.type], it.slot + 1) }
        ui.keys().forEach { key ->
            val parts = key.split(':', limit = 3)
            val type = parts.getOrNull(0)?.toIntOrNull()
            val slot = parts.getOrNull(1)?.toIntOrNull()
            require(type != null && slot != null && validCartSlot(type, slot)) { "Invalid saved cart identity" }
            needed[type] = maxOf(needed[type], slot + 1)
        }
        require(needed.all { it <= MAX_CART_SLOTS })
        return copy(counts = needed)
    }
    fun withoutCart(type: Int, slot: Int) = copy(lines = lines.filterNot { it.type == type && it.slot == slot }, ui = ui.withoutCart(type, slot))
}

data class CartBookState(val owner: CartScope? = null, val book: CartBook = CartBook(), val ready: Boolean = false, val failed: Boolean = false)

/** Persistence precedes publication; a captured account/store never borrows another login. */
internal class DynamicCartStore(
    private val isCurrent: (CartScope) -> Boolean,
    private val load: suspend (CartScope) -> CartBook?,
    private val save: suspend (CartScope, CartBook) -> Unit,
    private val publish: (CartBookState) -> Unit = {}
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(CartBookState())
    val state = mutableState.asStateFlow()
    private fun emit(value: CartBookState) { mutableState.value = value; publish(value) }
    suspend fun adopt(owner: CartScope?) = mutex.withLock {
        if (owner == state.value.owner && state.value.ready) return@withLock
        emit(CartBookState(owner))
        if (owner == null || !isCurrent(owner)) return@withLock
        try {
            val book = (load(owner) ?: CartBook()).validated()
            if (isCurrent(owner)) emit(CartBookState(owner, book, ready = true))
        } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
        catch (failure: Exception) {
            if (isCurrent(owner)) emit(CartBookState(owner, failed = true))
            throw failure
        }
    }
    suspend fun change(owner: CartScope, transform: (CartBook) -> CartBook): Boolean = mutex.withLock {
        val before = state.value
        if (!before.ready || before.owner != owner || !isCurrent(owner)) return@withLock false
        val next = transform(before.book).validated()
        if (next == before.book) return@withLock true
        require(before.book.revision < Long.MAX_VALUE)
        val durable = next.copy(revision = before.book.revision + 1)
        save(owner, durable)
        if (isCurrent(owner) && state.value.owner == owner) emit(CartBookState(owner, durable, ready = true))
        true
    }
    suspend fun add(owner: CartScope, type: Int): Int? {
        require(type in 0..2)
        var result: Int? = null
        val saved = change(owner) { book ->
            val count = book.counts[type]
            if (count >= MAX_CART_SLOTS) book else {
                val id = book.nextSlotIds?.get(type) ?: count
                if (id == Int.MAX_VALUE) return@change book
                result = id
                book.copy(counts = book.counts.mapIndexed { index, value -> if (index == type) count + 1 else value },
                    slots = book.slots?.mapIndexed { index, ids -> if (index == type) ids + id else ids },
                    nextSlotIds = book.nextSlotIds?.mapIndexed { index, next -> if (index == type) next + 1 else next })
            }
        }
        return result.takeIf { saved && isCurrent(owner) }
    }
    suspend fun reveal(owner: CartScope, type: Int, slot: Int): Boolean {
        require(validCartSlot(type, slot))
        var present = false
        val saved = change(owner) { book ->
            present = book.contains(type, slot) || (book.slots == null && slot < MAX_CART_SLOTS)
            if (!present || book.slots != null) book else
                book.copy(counts = book.counts.mapIndexed { i, value -> if (i == type) maxOf(value, slot + 1) else value })
        }
        return saved && present && isCurrent(owner)
    }
}

package kz.aita

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

const val INITIAL_CART_SLOTS = 2
/** Explicit limit: restoration refuses invalid data instead of truncating saved carts. */
const val MAX_CART_SLOTS = 1000
fun validCartSlot(type: Int, slot: Int): Boolean = type in 0..2 && slot in 0 until MAX_CART_SLOTS

data class CartScope(val accountId: String, val storeId: String, val generation: Long, val epoch: Long) {
    val storageKey: String get() = "cart-book.v2:${accountId.length}:$accountId:${storeId.length}:$storeId"
}

@Serializable
data class StoredCartLine(val id: String, val type: Int, val slot: Int, val quantity: QuantityDataModel, val addedAt: Long)

@Serializable
data class CartUiState(
    val saleMethods: Map<String, String> = emptyMap(),
    val payments: Map<String, TransactionPaymentDraftDataModel> = emptyMap(),
    val suppliers: Map<String, String> = emptyMap(),
    val reasons: Map<String, String> = emptyMap(),
    val batches: Map<String, CartReturnBatchSelectionDataModel> = emptyMap(),
    val checks: Map<String, Boolean> = emptyMap(),
    val scrolls: Map<String, TransactionCartScrollStateDataModel> = emptyMap()
) {
    fun keys(): Set<String> = saleMethods.keys + payments.keys + suppliers.keys + reasons.keys + batches.keys + checks.keys + scrolls.keys
    fun withoutCart(type: Int, slot: Int): CartUiState {
        val key = "$type:$slot"
        fun <T> Map<String, T>.keepOthers() = filterKeys { it != key && !it.startsWith("$key:") }
        return copy(saleMethods = saleMethods.keepOthers(), payments = payments.keepOthers(),
            suppliers = suppliers.keepOthers(), reasons = reasons.keepOthers(), batches = batches.keepOthers(),
            checks = checks.keepOthers(), scrolls = scrolls.keepOthers())
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
    val ui: CartUiState = CartUiState()
) {
    fun validated(): CartBook {
        require(schema == 2 && revision >= 0 && counts.size == 3 && counts.all { it in INITIAL_CART_SLOTS..MAX_CART_SLOTS })
        require(lines.size <= 20_000 && lines.all { it.id.isNotBlank() && validCartSlot(it.type, it.slot) })
        require(lines.map { Triple(it.id, it.type, it.slot) }.distinct().size == lines.size)
        val needed = counts.toMutableList()
        lines.forEach { needed[it.type] = maxOf(needed[it.type], it.slot + 1) }
        ui.keys().forEach { key ->
            val parts = key.split(':', limit = 3)
            val type = parts.getOrNull(0)?.toIntOrNull()
            val slot = parts.getOrNull(1)?.toIntOrNull()
            require(type != null && slot != null && validCartSlot(type, slot)) { "Invalid saved cart identity" }
            needed[type] = maxOf(needed[type], slot + 1)
        }
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
                result = count
                book.copy(counts = book.counts.mapIndexed { index, value -> if (index == type) count + 1 else value })
            }
        }
        return result.takeIf { saved && isCurrent(owner) }
    }
    suspend fun reveal(owner: CartScope, type: Int, slot: Int): Boolean {
        require(validCartSlot(type, slot))
        return change(owner) { book -> book.copy(counts = book.counts.mapIndexed { i, value -> if (i == type) maxOf(value, slot + 1) else value }) }
    }
}

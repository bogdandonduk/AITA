package kz.aita

import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.*

/** A value read is always from the current owner, even before a collector is attached. */
@OptIn(InternalCoroutinesApi::class)
internal class CartLinesStateFlow(
    private val source: StateFlow<CartBookState>, private val type: Int, private val slot: Int,
    private val ownerIsCurrent: (CartScope) -> Boolean
) : StateFlow<List<GoodsItemInCartDataModel>> {
    private fun rows(snapshot: CartBookState): List<StoredCartLine> =
        if (snapshot.ready && snapshot.owner?.let(ownerIsCurrent) == true)
            snapshot.book.lines.filter { it.type == type && it.slot == slot } else emptyList()
    private fun visible(rows: List<StoredCartLine>) = rows.map { GoodsItemInCartDataModel(it.id, it.type, it.slot, it.quantity, it.addedAt) }
    override val value: List<GoodsItemInCartDataModel> get() = visible(rows(source.value))
    override val replayCache: List<List<GoodsItemInCartDataModel>> get() = listOf(value)
    override suspend fun collect(collector: FlowCollector<List<GoodsItemInCartDataModel>>): Nothing {
        source.map(::rows).distinctUntilChanged().map(::visible).collect(collector)
        awaitCancellation()
    }
}

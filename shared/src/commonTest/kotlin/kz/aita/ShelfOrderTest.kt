package kz.aita

import kotlin.test.*

class ShelfOrderTest {
    private val item = GoodsItemDataModel(id = "item", storeId = "store", activeShelfBatchId = "a")
    private fun batch(id: String, priority: Int, quantity: Double = 3.0) = GoodsBatchDataModel(
        id = id, goodsItemId = item.id, storeId = item.storeId,
        quantity = QuantityDataModel("unit", emptyList(), quantity, 1.0, false),
        supplyPrice = PriceDataModel("19", "KZT", ""), shelfPriority = priority, shelfPosition = (priority + 1).toString())
    private val rows = listOf(batch("a",0),batch("b",1),batch("c",2))
    private val request = ShelfOrderRequest("store","item",listOf("a","b","c"),"a",listOf("c","a","b"))
    private fun applied() = ShelfOrderResult(item.copy(activeShelfBatchId="c"), listOf(rows[2],rows[0],rows[1]).mapIndexed { i,b ->
        b.copy(shelfPriority=i,shelfPosition=(i+1).toString()) },true)

    @Test fun reorderIsOneScopedIntent() { assertEquals(ShelfOrderDecision.Apply,evaluateShelfOrder(request,item,rows)) }
    @Test fun crossStoreRejected() { assertEquals(ShelfOrderDecision.Invalid,evaluateShelfOrder(request.copy(storeId="other"),item,rows)) }
    @Test fun crossItemRejected() { assertEquals(ShelfOrderDecision.Invalid,evaluateShelfOrder(request.copy(goodsItemId="other"),item,rows)) }
    @Test fun duplicateOrMissingIdsRejected() {
        assertEquals(ShelfOrderDecision.Invalid,evaluateShelfOrder(request.copy(orderedBatchIds=listOf("a","a","b")),item,rows))
        assertEquals(ShelfOrderDecision.Invalid,evaluateShelfOrder(request.copy(orderedBatchIds=listOf("a")),item,rows))
    }
    @Test fun addedOrRemovedBatchConflicts() {
        assertEquals(ShelfOrderDecision.Stale,evaluateShelfOrder(request,item,rows+batch("d",3)))
        assertEquals(ShelfOrderDecision.Stale,evaluateShelfOrder(request,item,rows.dropLast(1)))
    }
    @Test fun concurrentActiveChangeConflicts() {
        assertEquals(ShelfOrderDecision.Stale,evaluateShelfOrder(request,item.copy(activeShelfBatchId="b"),rows))
    }
    @Test fun lostResponseRetryIsSilentAndIdempotent() {
        val result=applied();assertEquals(ShelfOrderDecision.Unchanged,evaluateShelfOrder(request,result.item,result.batches))
    }
    @Test fun unavailableHeadIsRejected() {
        assertEquals(ShelfOrderDecision.Invalid,evaluateShelfOrder(request,item,rows.map { if(it.id=="c") it.copy(quantity=it.quantity.copy(total=0.0)) else it }))
        for (status in listOf(StockBatchStatusDataModel.Ordered,StockBatchStatusDataModel.Reserved,StockBatchStatusDataModel.InTransit)) {
            assertEquals(ShelfOrderDecision.Invalid,evaluateShelfOrder(request,item,rows.map { if(it.id=="c") it.copy(status=status) else it }))
        }
    }
    @Test fun currentQuantitiesAndPricesAreNotPartOfClientIntent() {
        assertEquals(ShelfOrderDecision.Apply,evaluateShelfOrder(request,item,rows.map { it.copy(quantity=it.quantity.copy(total=1.0),supplyPrice=PriceDataModel("99","KZT","")) }))
    }
    @Test fun acknowledgementsMustMatchWholeOrderAndScope() {
        val result=applied();assertTrue(shelfOrderAcknowledgementMatches(request,result))
        assertFalse(shelfOrderAcknowledgementMatches(request,result.copy(item=result.item.copy(storeId="other"))))
        assertFalse(shelfOrderAcknowledgementMatches(request,result.copy(batches=result.batches.dropLast(1))))
        assertFalse(shelfOrderAcknowledgementMatches(request,result.copy(batches=result.batches.map { it.copy(shelfPriority=0) })))
    }
    @Test fun stableTieBreakDoesNotDependOnServerRowOrder() {
        val tied=rows.map { it.copy(shelfPriority=0) };assertEquals(shelfOrderedBatches(item,tied),shelfOrderedBatches(item,tied.reversed()))
    }
    @Test fun nonFiniteQuantityCannotBecomeActive() {
        assertFalse(shelfBatchCanBeActive(batch("a",0,Double.NaN)));assertFalse(shelfBatchCanBeActive(batch("a",0,Double.POSITIVE_INFINITY)))
    }
}

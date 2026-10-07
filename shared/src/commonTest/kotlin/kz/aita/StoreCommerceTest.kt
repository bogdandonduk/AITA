package kz.aita

import kotlin.test.*

class StoreCommerceTest {
    private val id="00000000-0000-4000-8000-000000000001"
    private val store="00000000-0000-4000-8000-000000000002"
    private fun q(n:Double,whole:Boolean=false)=QuantityDataModel("1",emptyList(),n,1.0,whole)
    @Test fun discountsCompoundAndRoundOnce() {
        val d=SaleDiscounts(10.0,10.0,10.0)
        assertEquals(27.1,d.effectivePercent());assertEquals(72.89,discountedUnitPrice(99.99,d.effectivePercent()))
        assertEquals(10.0,SaleDiscounts(cartPercent=10.0).effectivePercent())
        assertEquals(100.0,SaleDiscounts(itemPercent=100.0).effectivePercent())
        assertFalse(SaleDiscounts(buyerPercent=Double.NaN).valid())
        assertFailsWith<IllegalArgumentException> {SaleDiscounts(cartPercent=100.01).effectivePercent()}
    }
    @Test fun delayedDirectoryCannotUndoAnArchivedBuyerOrNewPromotion() {
        val old=StoreBuyer(id,store,"Buyer",revision=1,discountPercent=5.0)
        val fresh=old.copy(revision=2,discountPercent=20.0,isActive=false)
        assertEquals(listOf(fresh),mergeBuyersByRevision(listOf(fresh),listOf(old)))
        assertEquals(listOf(fresh),mergeBuyersByRevision(listOf(old),listOf(fresh)))
    }
    @Test fun quantitiesDoNotOverdrawOrLoseWeightPrecision() {
        assertEquals(1.875,writeOffRemaining(q(2.0),0.125))
        assertEquals(1875.0,writeOffRemaining(q(2000.0,true),125.0))
        listOf(0.0,-1.0,2.001,Double.NaN,Double.POSITIVE_INFINITY,0.0001).forEach {assertNull(writeOffRemaining(q(2.0),it))}
        assertNull(writeOffRemaining(q(2.0,true),0.5));assertEquals(0.0,writeOffRemaining(q(2.0,true),2.0))
    }
    @Test fun buyerValidationAndExpiryAreBounded() {
        val b=StoreBuyer(id,store,"Customer",email="a@example.test",discountPercent=25.0,promoStartsAtMillis=100,promoEndsAtMillis=200)
        assertTrue(b.valid());assertEquals(0.0,b.activeDiscount(99));assertEquals(25.0,b.activeDiscount(100));assertEquals(0.0,b.activeDiscount(200))
        assertFalse(b.copy(name=" ").valid());assertFalse(b.copy(email="no-at").valid());assertFalse(b.copy(promoEndsAtMillis=99).valid())
        assertEquals(0.0,b.copy(isActive=false).activeDiscount(150))
    }
    @Test fun decimalRemainderCanBeCompletelyWrittenOffWithoutAllowingOverdraw() {
        val remaining=assertNotNull(writeOffRemaining(q(0.3),0.1))
        assertEquals(0.2,remaining)
        assertEquals(0.0,writeOffRemaining(q(remaining),0.2))
        assertEquals(0.0,writeOffRemaining(q(0.3-0.1),0.2))
        assertNull(writeOffRemaining(q(0.199),0.2))
        assertNull(writeOffRemaining(q(0.2),0.201))
    }
    @Test fun promotionClockOnlyWakesAtAnActualFutureBoundary() {
        val buyer=StoreBuyer(id,store,"Buyer",discountPercent=15.0,promoStartsAtMillis=100,promoEndsAtMillis=200)
        assertEquals(100L,nextBuyerPromoBoundary(listOf(buyer),50))
        assertEquals(200L,nextBuyerPromoBoundary(listOf(buyer),100))
        assertNull(nextBuyerPromoBoundary(listOf(buyer),200))
        assertNull(nextBuyerPromoBoundary(listOf(buyer.copy(isActive=false)),50))
    }
    @Test fun otherReasonNeedsAnExplanationAndCommandsHaveStableIds() {
        val c=StockWriteOffCommand(id,store,id,1.0,WriteOffReason.OTHER)
        assertFalse(c.valid());assertTrue(c.copy(note="Sample used for fitting").valid());assertFalse(c.copy(id="not-an-id").valid())
    }
    @Test fun clearingOneCartDoesNotLoseAnotherBuyerOrDiscount() {
        val b=StoreBuyer(id,store,"Buyer")
        val ui=CartUiState(discounts=mapOf("0:0" to 10.0,"0:0:item" to 20.0,"0:1" to 5.0),buyers=mapOf("0:0" to b,"0:1" to b))
        val cleared=ui.withoutCart(0,0)
        assertEquals(mapOf("0:1" to 5.0),cleared.discounts);assertEquals(setOf("0:1"),cleared.buyers.keys)
        assertEquals(mapOf("0:0" to 10.0,"0:1" to 5.0),ui.withoutItem(0,0,"item").discounts)
    }
    @Test fun legacyCartBookAndReceiptDefaultsRemainReadable() {
        val old=jsonBase.decodeFromString<CartBook>("{}")
        assertTrue(old.validated().ui.buyers.isEmpty())
        val line=jsonBase.decodeFromString<GoodsItemInTransactionDataModel>("""{"barcode":"123","quantity":1,"pricePerUnit":2}""")
        assertNull(line.discounts);assertEquals(0.0,line.quickDiscountAmount())
    }
    @Test fun writeOffAnalyticsKeepsCurrenciesAndHistoricalFiltersSeparate() {
        val c=StockWriteOffCommand(id,store,id,2.0,WriteOffReason.DAMAGED)
        val r=StockWriteOff(c,id,emptyList(),q(2.0),PriceDataModel("10","KZT",""),20.0,id,100,8.0,listOf("category"),"supplier")
        val usd=r.copy(command=c.copy(id="00000000-0000-4000-8000-000000000003"),supplyPrice=PriceDataModel("5","USD",""),cost=10.0)
        val d=StoreAnalyticsDashboardDataModel(storeId=store,startMillis=0,endMillisExclusive=101).withWriteOffs(listOf(r,r,usd))
        assertEquals(2,d.writeOffCount);assertEquals(mapOf("KZT" to 20.0,"USD" to 10.0),d.writeOffCosts)
        assertTrue(scopedWriteOffs(listOf(r),store,101,200).isEmpty())
        assertTrue(scopedWriteOffs(listOf(r),store,0,200,supplierId="other").isEmpty())
        assertEquals(1,scopedWriteOffs(listOf(r),store,0,200,categoryId="category").size)
        assertNull(d.withWriteOffs(null).writeOffCount)
    }
}

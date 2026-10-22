package kz.aita

import kotlinx.coroutines.test.runTest
import kotlin.test.*

class SupplierCommercialWorkspaceTest {
    private fun price(id: String, store: String = "store-a", supplier: String = "supplier-a", goods: String = id, amount: String = "100") =
        SupplierGoodsPriceDataModel(id = id, storeId = store, supplierId = supplier, goodsItemId = goods,
            supplyPrice = PriceDataModel(amount, "KZT", supplier)).commercialRow()
    private fun contract(id: String, store: String = "store-a", supplier: String = "supplier-a", revision: Int = 1) =
        SupplierPartnershipContractDataModel(id = id, storeId = store, supplierId = supplier, revision = revision).commercialRow()
    private fun <T> SupplierCommercialWorkspace<T>.read(scope: SupplierCommercialScope, vararg rows: SupplierCommercialRow<T>) =
        assertNotNull(replace(scope, rows.toList()))

    @Test fun emptySupplierPriceBookDoesNotEraseStoresOffers() {
        val saved = price("buyer")
        val result = SupplierCommercialWorkspace<SupplierGoodsPriceDataModel>()
            .read(SupplierCommercialScope(false, "store-a"), saved)
            .read(SupplierCommercialScope(true))
        assertEquals(listOf(saved.value), result.buyerValues); assertTrue(result.sellerValues.isEmpty())
        assertTrue(result.sources.supplierLoaded)
    }

    @Test fun supplierContractRefreshDoesNotExposeBuyerOnlyPartnerships() {
        val buyer = contract("buyer"); val seller = contract("seller", store = "external")
        val result = SupplierCommercialWorkspace<SupplierPartnershipContractDataModel>()
            .read(SupplierCommercialScope(false, "store-a"), buyer)
            .read(SupplierCommercialScope(true), seller)
        assertEquals(listOf(buyer.value), result.buyerValues); assertEquals(listOf(seller.value), result.sellerValues)
    }

    @Test fun overlapUsesOneCanonicalVersionInBothDesks() {
        val original = contract("shared")
        val next = contract("shared", revision = 2)
        val result = SupplierCommercialWorkspace<SupplierPartnershipContractDataModel>()
            .read(SupplierCommercialScope(false, "store-a"), original)
            .read(SupplierCommercialScope(true), next)
        assertEquals(1, result.rows.size); assertEquals(2, result.buyerValues.single().revision)
        assertEquals(2, result.sellerValues.single().revision)
    }

    @Test fun aggregateRevocationDropsFormerSellerSourcesButRetainsBuyerOverlap() {
        val shared = price("shared"); val revoked = price("revoked", store = "external", supplier = "revoked-supplier")
        val retained = price("retained", store = "external", supplier = "retained-supplier")
        val result = SupplierCommercialWorkspace<SupplierGoodsPriceDataModel>()
            .read(SupplierCommercialScope(false, "store-a"), shared)
            .read(SupplierCommercialScope(true), shared, revoked)
            .read(SupplierCommercialScope(true), retained)
        assertEquals(listOf(shared.value), result.buyerValues); assertEquals(listOf(retained.value), result.sellerValues)
        assertEquals(2, result.rows.size); assertFalse("revoked-supplier" in result.sources.supplierRows)
    }

    @Test fun forbiddenSpecificSupplierClearsOnlyThatSource() {
        val shared = contract("shared"); val other = contract("other", supplier = "supplier-b")
        val result = SupplierCommercialWorkspace<SupplierPartnershipContractDataModel>()
            .read(SupplierCommercialScope(false, "store-a"), shared)
            .read(SupplierCommercialScope(true), shared, other)
            .read(SupplierCommercialScope(true, "supplier-a"))
        assertEquals(listOf(shared.value), result.buyerValues); assertEquals(listOf(other.value), result.sellerValues)
    }

    @Test fun storeScopeReplacementDoesNotRemoveSellerOrOtherStoreRows() {
        val shared = price("shared"); val deleted = price("deleted"); val other = price("other", store = "store-b")
        val result = SupplierCommercialWorkspace<SupplierGoodsPriceDataModel>()
            .read(SupplierCommercialScope(false, "store-a"), shared, deleted)
            .read(SupplierCommercialScope(false, "store-b"), other)
            .read(SupplierCommercialScope(true), shared)
            .read(SupplierCommercialScope(false, "store-a"))
        assertEquals(listOf(other.value), result.buyerValues); assertEquals(listOf(shared.value), result.sellerValues)
        assertEquals(2, result.rows.size)
    }

    @Test fun partnershipSubsetReplacesOnlyMatchingPeerWithinStoreSource() {
        val first = contract("first"); val second = contract("second", supplier = "supplier-b")
        val result = SupplierCommercialWorkspace<SupplierPartnershipContractDataModel>()
            .read(SupplierCommercialScope(false, "store-a"), first, second)
            .read(SupplierCommercialScope(false, "store-a", "supplier-a"))
        assertEquals(listOf(second.value), result.buyerValues)
        assertFalse(result.sources.supplierLoaded)
    }

    @Test fun partnershipSubsetReplacesOnlyMatchingPeerWithinSupplierSource() {
        val first = contract("first"); val second = contract("second", store = "store-b")
        val result = SupplierCommercialWorkspace<SupplierPartnershipContractDataModel>()
            .read(SupplierCommercialScope(true, "supplier-a"), first, second)
            .read(SupplierCommercialScope(true, "supplier-a", "store-a"))
        assertEquals(listOf(second.value), result.sellerValues)
    }

    @Test fun malformedScopeOrRowsCannotReplaceSavedCommercialData() {
        val row = contract("saved")
        val original = SupplierCommercialWorkspace<SupplierPartnershipContractDataModel>().read(SupplierCommercialScope(false, "store-a"), row)
        assertNull(original.replace(SupplierCommercialScope(false), emptyList()))
        assertNull(original.replace(SupplierCommercialScope(false, "store-a"), listOf(contract("wrong", store = "store-b"))))
        assertNull(original.replace(SupplierCommercialScope(true, peerId = "store-a"), emptyList()))
        assertNull(original.replace(SupplierCommercialScope(false, "store-a"), listOf(row, row)))
        assertFalse(original.copy(sources = SupplierWorkspaceSources(supplierRows = mapOf("unknown-supplier" to setOf(row.id)))).valid())
        assertEquals(listOf(row.value), original.buyerValues)
    }

    @Test fun blankLegacyPriceIdUsesStableCommercialRelationshipWithoutDuplication() {
        val old = price("", goods = "goods-a")
        val fresh = price("new-server-id", goods = "goods-a", amount = "150")
        val before = SupplierCommercialWorkspace<SupplierGoodsPriceDataModel>()
            .read(SupplierCommercialScope(false, "store-a"), old).read(SupplierCommercialScope(true), old)
        val result = assertNotNull(before.upsert(fresh, supplierSide = true))
        assertEquals(1, result.rows.size); assertEquals("150", result.buyerValues.single().supplyPrice.price)
        assertEquals(fresh.value, result.sellerValues.single())
    }

    @Test fun supplierMutationPreservesBuyerDataAndDoesNotInventBuyerMembership() {
        val buyer = price("buyer"); val seller = price("seller", store = "external")
        val before = SupplierCommercialWorkspace<SupplierGoodsPriceDataModel>().read(SupplierCommercialScope(false, "store-a"), buyer)
        val result = assertNotNull(before.upsert(seller, supplierSide = true))
        assertEquals(listOf(buyer.value), result.buyerValues); assertEquals(listOf(seller.value), result.sellerValues)
    }

    @Test fun archivedContractDisappearsFromBothDesksButLoadedEmptyIsPreserved() {
        val row = contract("shared")
        val result = SupplierCommercialWorkspace<SupplierPartnershipContractDataModel>()
            .read(SupplierCommercialScope(false, "store-a"), row).read(SupplierCommercialScope(true), row).remove(row.id)
        assertTrue(result.values.isEmpty()); assertTrue(result.sources.storeLoaded); assertTrue(result.sources.supplierLoaded)
        assertTrue(result.valid())
    }

    @Test fun revisionMergeCannotRollBackSharedContractOnOppositeSideRead() {
        val newer = contract("shared", revision = 4); val stale = contract("shared", revision = 2)
        val original = SupplierCommercialWorkspace<SupplierPartnershipContractDataModel>().read(SupplierCommercialScope(false, "store-a"), newer)
        val result = assertNotNull(original.replace(SupplierCommercialScope(true), listOf(stale)) { previous, incoming ->
            listOf(previous).upsertSupplierContractByRevision(incoming).single()
        })
        assertEquals(4, result.buyerValues.single().revision); assertEquals(4, result.sellerValues.single().revision)
    }

    @Test fun startupRestoreThenScopedReadKeepsOtherPricesContractsAndOrders() = runTest {
        val owner = SupplierSessionOwner("account", 1L, "login", { "login" }, { 1L })
        val disk = mutableMapOf<String, String>(); var failWrite = false
        fun cache() = SupplierWorkspaceCache({ disk[it] }, { key, value -> check(!failWrite); disk[key] = value }, { disk.remove(it); Unit })
        val prices = SupplierCommercialWorkspace<SupplierGoodsPriceDataModel>()
            .read(SupplierCommercialScope(false, "store-a"), price("buyer"))
            .read(SupplierCommercialScope(true), price("seller", store = "external"))
        val contracts = SupplierCommercialWorkspace<SupplierPartnershipContractDataModel>()
            .read(SupplierCommercialScope(false, "store-a"), contract("buyer-contract"))
            .read(SupplierCommercialScope(true), contract("seller-contract", store = "external"))
        val orders = assertNotNull(SupplierOrderWorkspace().replace(SupplierOrderReadScope.Store("store-a"), listOf(
            SupplierOrderWithLinesDataModel(SupplierOrderDataModel(id = "order", storeId = "store-a", supplierId = "supplier-a"), emptyList()))))
        assertTrue(cache().update(owner) { it.copy(orders = orders.orders, lines = orders.lines, orderSources = orders.sources,
            prices = prices.values, priceSources = prices.sources, contracts = contracts.values, contractSources = contracts.sources) })
        val restored = assertNotNull(cache().cached(owner))
        val refreshedPrices = assertNotNull(restored.priceWorkspace()).read(SupplierCommercialScope(false, "store-a"), price("new"))
        assertTrue(cache().update(owner) { it.copy(prices = refreshedPrices.values, priceSources = refreshedPrices.sources) })
        val after = assertNotNull(cache().cached(owner))
        assertEquals(listOf("new"), after.priceWorkspace()?.buyerValues?.map { it.id })
        assertEquals(listOf("seller"), after.priceWorkspace()?.sellerValues?.map { it.id })
        assertEquals(contracts.withoutRecovery(), after.contractWorkspace()); assertEquals(orders.withoutRecovery(), after.orderWorkspace())
        failWrite = true
        assertFalse(cache().update(owner) { it.copy(contracts = emptyList(), contractSources = SupplierWorkspaceSources(allSuppliersLoaded = true)) })
        assertEquals(after, cache().cached(owner))
    }
    @Test fun transientStorageFailureThenStoreFetchAndRecoveryRetainsOtherPeers() = runTest {
        val owner = SupplierSessionOwner("account", 1L, "login", { "login" }, { 1L })
        val disk = mutableMapOf<String, String>(); var unreadable = false
        val cache = SupplierWorkspaceCache({ check(!unreadable); disk[it] }, { key, value -> disk[key] = value }, { disk.remove(it); Unit })
        val previous = SupplierCommercialWorkspace<SupplierGoodsPriceDataModel>()
            .read(SupplierCommercialScope(false, "store-a"), price("obsolete"))
            .read(SupplierCommercialScope(false, "store-b"), price("other-buyer", store = "store-b"))
            .read(SupplierCommercialScope(true), price("seller", store = "external"))
        assertTrue(cache.update(owner) { it.copy(prices = previous.values, priceSources = previous.sources) })
        val committed = disk.toMap()
        unreadable = true
        assertFalse(cache.restore(owner).readable)
        val live = SupplierCommercialWorkspace<SupplierGoodsPriceDataModel>()
            .read(SupplierCommercialScope(false, "store-a"), price("fresh"))
        assertFalse(cache.update(owner) { it.copy(prices = live.values, priceSources = live.sources) })
        assertEquals(committed, disk)
        unreadable = false
        val recovered = cache.restore(owner); assertTrue(recovered.readable)
        val merged = assertNotNull(live.recoverFrom(assertNotNull(recovered.snapshot?.priceWorkspace())))
        assertEquals(setOf("fresh", "other-buyer"), merged.buyerValues.map { it.id }.toSet())
        assertEquals(listOf("seller"), merged.sellerValues.map { it.id })
        assertTrue(cache.update(owner) { it.copy(prices = merged.values, priceSources = merged.sources) })
        assertEquals(merged, cache.cached(owner)?.priceWorkspace())
    }

    @Test fun delayedCacheRestoreDoesNotTreatOneMutationAsCompleteSourceOrResurrectArchive() {
        val older = contract("older"); val archived = contract("archived"); val changed = contract("changed")
        val previous = SupplierCommercialWorkspace<SupplierPartnershipContractDataModel>()
            .read(SupplierCommercialScope(false, "store-a"), older, archived, changed)
            .read(SupplierCommercialScope(true), archived, changed)
        val live = assertNotNull(SupplierCommercialWorkspace<SupplierPartnershipContractDataModel>()
            .upsert(contract("changed", revision = 2), supplierSide = false)).remove("archived")
        val merged = assertNotNull(live.recoverFrom(previous))
        assertEquals(setOf("older", "changed"), merged.buyerValues.map { it.id }.toSet())
        assertEquals(listOf("changed"), merged.sellerValues.map { it.id })
        assertEquals(2, merged.sellerValues.single().revision)
    }

    @Test fun delayedCacheRestoreHonorsAuthoritativeEmptyAndPartnershipSubset() {
        val previous = SupplierCommercialWorkspace<SupplierPartnershipContractDataModel>()
            .read(SupplierCommercialScope(false, "store-a"), contract("gone"), contract("retained", supplier = "supplier-b"))
            .read(SupplierCommercialScope(true), contract("seller-only", store = "external"))
        val live = SupplierCommercialWorkspace<SupplierPartnershipContractDataModel>()
            .read(SupplierCommercialScope(true))
            .read(SupplierCommercialScope(false, "store-a", "supplier-a"))
        val merged = assertNotNull(live.recoverFrom(previous))
        assertEquals(listOf("retained"), merged.buyerValues.map { it.id }); assertTrue(merged.sellerValues.isEmpty())
        assertTrue(merged.sources.supplierLoaded)
    }

}

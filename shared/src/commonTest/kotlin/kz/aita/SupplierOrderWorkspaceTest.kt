package kz.aita

import kotlinx.coroutines.test.runTest
import kotlin.test.*

class SupplierOrderWorkspaceTest {
    private fun bundle(id: String, store: String = "buyer-a", supplier: String = "seller-a",
        status: SupplierOrderStatusDataModel = SupplierOrderStatusDataModel.Sent, count: Double = 2.0) =
        SupplierOrderWithLinesDataModel(
            SupplierOrderDataModel(id = id, storeId = store, supplierId = supplier, status = status),
            listOf(SupplierOrderLineDataModel(id = "line-$id", orderId = id, goodsItemId = "goods-$id",
                requestedQuantity = QuantityDataModel("piece", emptyList(), total = count, roundTotal = true))))
    private fun SupplierOrderWorkspace.read(scope: SupplierOrderReadScope, vararg rows: SupplierOrderWithLinesDataModel) =
        assertNotNull(replace(scope, rows.toList()))
    private fun SupplierOrderWorkspace.buyerIds() = buyerOrders.map { it.id }.toSet()
    private fun SupplierOrderWorkspace.sellerIds() = sellerOrders.map { it.id }.toSet()

    @Test fun emptySupplierAccountDoesNotEraseStoreProcurementOrDraftLines() {
        val draft = bundle("draft", status = SupplierOrderStatusDataModel.Draft)
        val result = SupplierOrderWorkspace().read(SupplierOrderReadScope.Store("buyer-a"), draft, bundle("sent"))
            .read(SupplierOrderReadScope.AllSuppliers)
        assertEquals(setOf("draft", "sent"), result.buyerIds()); assertTrue(result.sellerOrders.isEmpty())
        assertEquals(setOf("line-draft", "line-sent"), result.buyerLines.map { it.id }.toSet())
        assertTrue(result.sources.supplierLoaded)
    }

    @Test fun disjointBuyerAndSellerScopesDoNotLeakIntoOneAnother() {
        val result = SupplierOrderWorkspace().read(SupplierOrderReadScope.Store("buyer-a"), bundle("procurement"))
            .read(SupplierOrderReadScope.AllSuppliers, bundle("incoming", store = "external-store", supplier = "owned-supplier"))
        assertEquals(setOf("procurement"), result.buyerIds()); assertEquals(setOf("incoming"), result.sellerIds())
        assertEquals(listOf("line-procurement"), result.buyerLines.map { it.id })
        assertEquals(listOf("line-incoming"), result.sellerLines.map { it.id })
    }

    @Test fun supplierSpecificRefreshPreservesBuyerDraftForSameSupplier() {
        val draft = bundle("draft", status = SupplierOrderStatusDataModel.Draft)
        val result = SupplierOrderWorkspace().read(SupplierOrderReadScope.Store("buyer-a"), draft, bundle("sent"))
            .read(SupplierOrderReadScope.Supplier("seller-a"), bundle("sent", count = 4.0))
        assertEquals(setOf("draft", "sent"), result.buyerIds()); assertEquals(setOf("sent"), result.sellerIds())
        assertEquals(4.0, result.buyerLines.first { it.orderId == "sent" }.requestedQuantity.total)
        assertEquals(4.0, result.sellerLines.single().requestedQuantity.total)
    }

    @Test fun aggregateRefreshRemovesRevokedSupplierIdentitiesButKeepsBuyerOverlap() {
        val shared = bundle("shared")
        val result = SupplierOrderWorkspace().read(SupplierOrderReadScope.Store("buyer-a"), shared)
            .read(SupplierOrderReadScope.AllSuppliers, shared, bundle("removed", store = "external", supplier = "revoked"))
            .read(SupplierOrderReadScope.AllSuppliers, bundle("new", store = "external", supplier = "retained"))
        assertEquals(setOf("shared"), result.buyerIds()); assertEquals(setOf("new"), result.sellerIds())
        assertEquals(setOf("shared", "new"), result.orders.map { it.id }.toSet())
        assertFalse(result.lines.any { it.orderId == "removed" })
        assertFalse("revoked" in result.sources.supplierRows)
    }

    @Test fun explicitSupplierAccessRevocationRemovesOnlyThatSource() {
        val shared = bundle("shared")
        val result = SupplierOrderWorkspace().read(SupplierOrderReadScope.Store("buyer-a"), shared)
            .read(SupplierOrderReadScope.AllSuppliers, shared, bundle("other", supplier = "seller-b"))
            .read(SupplierOrderReadScope.Supplier("seller-a"))
        assertEquals(setOf("shared"), result.buyerIds()); assertEquals(setOf("other"), result.sellerIds())
        assertTrue(result.sources.supplierRows.getValue("seller-a").isEmpty())
    }

    @Test fun storeRefreshRemovesOnlyItsProcurementMembership() {
        val result = SupplierOrderWorkspace().read(SupplierOrderReadScope.Store("buyer-a"), bundle("shared"), bundle("deleted"))
            .read(SupplierOrderReadScope.Store("buyer-b"), bundle("other-store", store = "buyer-b"))
            .read(SupplierOrderReadScope.Supplier("seller-a"), bundle("shared"))
            .read(SupplierOrderReadScope.Store("buyer-a"))
        assertEquals(setOf("other-store"), result.buyerIds()); assertEquals(setOf("shared"), result.sellerIds())
        assertEquals(setOf("shared", "other-store"), result.orders.map { it.id }.toSet())
        assertFalse(result.lines.any { it.orderId == "deleted" })
    }

    @Test fun successfulBuyerCreationIsNeverInferredToBeSupplierOwnership() {
        val result = assertNotNull(SupplierOrderWorkspace().upsert(listOf(bundle("draft", status = SupplierOrderStatusDataModel.Draft)), SupplierOrderMutationSide.Store))
        assertEquals(setOf("draft"), result.buyerIds()); assertFalse(result.sources.supplierLoaded)
        assertTrue(result.sellerOrders.isEmpty())
    }

    @Test fun sellerMutationUpdatesCanonicalSharedRowsWithoutDuplicatingLines() {
        val shared = bundle("shared")
        val original = SupplierOrderWorkspace().read(SupplierOrderReadScope.Store("buyer-a"), shared)
            .read(SupplierOrderReadScope.AllSuppliers, shared)
        val result = assertNotNull(original.upsert(listOf(bundle("shared", status = SupplierOrderStatusDataModel.Packed, count = 7.0)), SupplierOrderMutationSide.Supplier))
        assertEquals(1, result.orders.size); assertEquals(1, result.lines.size)
        assertEquals(SupplierOrderStatusDataModel.Packed, result.buyerOrders.single().status)
        assertEquals(7.0, result.sellerLines.single().requestedQuantity.total)
    }

    @Test fun successfulDeleteRemovesBothCommercialMemberships() {
        val shared = bundle("shared")
        val result = SupplierOrderWorkspace().read(SupplierOrderReadScope.Store("buyer-a"), shared)
            .read(SupplierOrderReadScope.AllSuppliers, shared).remove(" SHARED ")
        assertTrue(result.orders.isEmpty()); assertTrue(result.lines.isEmpty())
        assertTrue(result.sources.buyerIds.isEmpty()); assertTrue(result.sources.sellerIds.isEmpty())
        assertTrue(result.sources.storeLoaded); assertTrue(result.sources.supplierLoaded); assertTrue(result.valid())
    }

    @Test fun malformedAuthoritativeResponsesCannotReplaceSavedSnapshot() {
        val initial = SupplierOrderWorkspace().read(SupplierOrderReadScope.Store("buyer-a"), bundle("saved"))
        assertNull(initial.replace(SupplierOrderReadScope.Store("buyer-a"), listOf(bundle("wrong", store = "buyer-b"))))
        assertNull(initial.replace(SupplierOrderReadScope.Store(""), emptyList()))
        assertNull(initial.replace(SupplierOrderReadScope.AllSuppliers, listOf(bundle("draft", status = SupplierOrderStatusDataModel.Draft))))
        assertNull(initial.replace(SupplierOrderReadScope.Store("buyer-a"), listOf(bundle("twice"), bundle("twice"))))
        val malformed = bundle("bad").let { it.copy(lines = it.lines.map { line -> line.copy(orderId = "someone-else") }) }
        assertNull(initial.replace(SupplierOrderReadScope.Store("buyer-a"), listOf(malformed)))
        assertEquals(setOf("saved"), initial.buyerIds())
    }

    @Test fun provenanceCannotGrantUnrelatedStoreOrSupplierMembership() {
        val row = bundle("saved")
        val workspace = SupplierOrderWorkspace().read(SupplierOrderReadScope.Store("buyer-a"), row)
        assertFalse(workspace.copy(sources = SupplierWorkspaceSources(storeRows = mapOf("other-store" to setOf("saved")))).valid())
        assertFalse(workspace.copy(sources = SupplierWorkspaceSources(supplierRows = mapOf("other-supplier" to setOf("saved")))).valid())
        assertFalse(workspace.copy(sources = SupplierWorkspaceSources()).valid())
    }

    @Test fun allSupplierEmptyAndNotFetchedAreDifferent() {
        val fresh = SupplierOrderWorkspace()
        assertFalse(fresh.sources.storeLoaded); assertFalse(fresh.sources.supplierLoaded)
        val empty = fresh.read(SupplierOrderReadScope.AllSuppliers)
        assertFalse(empty.sources.storeLoaded); assertTrue(empty.sources.supplierLoaded)
    }

    @Test fun cacheRestartPreservesProvenanceAndFutureAuthoritativeReplacement() = runTest {
        val owner = SupplierSessionOwner("account", 1, "session", { "session" }, { 1 })
        val disk = mutableMapOf<String, String>()
        fun cache() = SupplierWorkspaceCache({ disk[it] }, { key, value -> disk[key] = value }, { disk.remove(it); Unit })
        val before = SupplierOrderWorkspace().read(SupplierOrderReadScope.Store("buyer-a"), bundle("buyer"))
            .read(SupplierOrderReadScope.AllSuppliers, bundle("seller", store = "external"))
        assertTrue(cache().update(owner) { it.copy(orders = before.orders, lines = before.lines, orderSources = before.sources) })
        val restored = assertNotNull(assertNotNull(cache().cached(owner)).orderWorkspace())
        assertEquals(before.withoutRecovery(), restored)
        val after = restored.read(SupplierOrderReadScope.AllSuppliers)
        assertEquals(setOf("buyer"), after.buyerIds()); assertTrue(after.sellerOrders.isEmpty())
    }

    @Test fun supplierSourcesMissingFromCacheAreNotGuessedFromRowFields() {
        val row = bundle("unknown")
        assertFalse(SupplierWorkspaceSnapshot("account", orders = listOf(row.order), lines = row.lines).validFor("account"))
    }
    @Test fun delayedRestoreKeepsOtherSourcesAndDoesNotRollBackFreshLines() {
        val previous = SupplierOrderWorkspace()
            .read(SupplierOrderReadScope.Store("buyer-a"), bundle("deleted"), bundle("shared", count = 1.0))
            .read(SupplierOrderReadScope.Store("buyer-b"), bundle("other", store = "buyer-b"))
            .read(SupplierOrderReadScope.AllSuppliers, bundle("seller", store = "external"), bundle("shared", count = 1.0))
        val live = SupplierOrderWorkspace().read(SupplierOrderReadScope.Store("buyer-a"), bundle("shared", count = 7.0))
        val merged = assertNotNull(live.recoverFrom(previous))
        assertEquals(setOf("shared", "other"), merged.buyerIds()); assertEquals(setOf("shared", "seller"), merged.sellerIds())
        assertEquals(7.0, merged.sellerLines.first { it.orderId == "shared" }.requestedQuantity.total)
    }

    @Test fun delayedRestoreUnionsMutationButHonorsDeletionAndSupplierRevocation() {
        val previous = SupplierOrderWorkspace()
            .read(SupplierOrderReadScope.Store("buyer-a"), bundle("keep"), bundle("delete"))
            .read(SupplierOrderReadScope.AllSuppliers, bundle("revoked", store = "external"), bundle("delete"))
        val live = assertNotNull(SupplierOrderWorkspace().upsert(listOf(bundle("added")), SupplierOrderMutationSide.Store))
            .remove("delete").read(SupplierOrderReadScope.AllSuppliers)
        val merged = assertNotNull(live.recoverFrom(previous))
        assertEquals(setOf("keep", "added"), merged.buyerIds()); assertTrue(merged.sellerOrders.isEmpty())
        assertTrue(merged.sources.supplierLoaded); assertTrue(merged.valid())
    }

}

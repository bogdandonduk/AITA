package kz.aita

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlin.test.*

class SupplierSessionAndCacheTest {
    private class Session(var account: String = "account-a", var epoch: Int = 1, var generation: Long = 10) {
        fun owner(): SupplierSessionOwner = SupplierSessionOwner(account, generation, "$epoch|$account",
            { "$epoch|$account" }, { generation })
    }
    private class Disk {
        val data = mutableMapOf<String, String>()
        var fail = false
        suspend fun read(key: String): String? = data[key]
        suspend fun write(key: String, value: String) { check(!fail); data[key] = value }
        suspend fun remove(key: String) { data.remove(key) }
        fun cache() = SupplierWorkspaceCache(::read, ::write, ::remove)
    }
    private fun order(id: String = "order-a") = SupplierOrderDataModel(id = id, storeId = "store-a", supplierId = "supplier-a")
    private fun line(id: String = "line-a", orderId: String = "order-a") = SupplierOrderLineDataModel(
        id = id, orderId = orderId, goodsItemId = "goods-a",
        requestedQuantity = QuantityDataModel("piece", emptyList(), total = 3.0, roundTotal = true))

    private fun SupplierWorkspaceSnapshot.withBuyerOrders(
        orders: List<SupplierOrderDataModel>, lines: List<SupplierOrderLineDataModel>
    ) = copy(orders = orders, lines = lines, orderSources = SupplierWorkspaceSources(
        storeRows = if (orders.isEmpty()) mapOf("store-a" to emptySet()) else orders.groupBy { supplierOrderIdentity(it.storeId) }
            .mapValues { (_, rows) -> rows.map { supplierOrderIdentity(it.id) }.toSet() }
    ))

    @Test fun queuedMutationDoesNotSendUsingNextAccountsCredentials() = runTest {
        val session = Session(); val owner = session.owner(); val queue = Mutex(locked = true)
        var requests = 0
        val result = async { queue.withLock { owner.request { requests++; "accepted" } } }
        session.account = "account-b"; session.generation++
        queue.unlock()
        assertNull(result.await()); assertEquals(0, requests)
    }

    @Test fun sameAccountReloginInvalidatesEarlierQueuedMutation() = runTest {
        val session = Session(); val owner = session.owner()
        session.epoch++; session.generation++
        assertNull(owner.request { fail("A previous login may not send a mutation") })
    }

    @Test fun oldResponseCannotPopulateNextAccountsWorkspace() = runTest {
        val session = Session(); val owner = session.owner()
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val response = async { owner.request { pinned ->
            assertEquals(10L, pinned); entered.complete(Unit); release.await(); "account-a secret order"
        } }
        entered.await(); session.account = "account-b"; session.generation++; release.complete(Unit)
        assertNull(response.await())
    }

    @Test fun responseHandoffAfterAccountChangeCannotPersistUnderNewOwner() = runTest {
        val session = Session(); val owner = session.owner(); val disk = Disk()
        val response = assertNotNull(owner.request { listOf(order()) })
        session.account = "account-b"; session.generation++
        assertFalse(disk.cache().update(owner) { it.withBuyerOrders(orders = response, lines = listOf(line())) })
        assertTrue(disk.data.isEmpty()); assertNull(disk.cache().cached(session.owner()))
    }

    @Test fun cacheThatDidNotActuallySaveCannotReportSuccess() = runTest {
        val session = Session()
        val cache = SupplierWorkspaceCache({ null }, { _, _ -> }, { })
        assertFalse(cache.update(session.owner()) { it.withBuyerOrders(orders = emptyList(), lines = emptyList()) })
    }

    @Test fun epochAndAuthenticationGenerationBothInvalidateOwnership() {
        val session = Session(); val byEpoch = session.owner()
        session.epoch++; assertFalse(byEpoch.isCurrent())
        val byGeneration = session.owner(); session.generation++; assertFalse(byGeneration.isCurrent())
    }

    @Test fun cachedOrdersAndLinesSurviveRestartTogether() = runTest {
        val session = Session(); val disk = Disk()
        assertTrue(disk.cache().update(session.owner()) { it.withBuyerOrders(orders = listOf(order()), lines = listOf(line())) })
        val restored = assertNotNull(disk.cache().cached(session.owner()))
        assertEquals(listOf(order()), restored.orders); assertEquals(listOf(line()), restored.lines)
        assertNull(restored.contracts); assertNull(restored.prices)
    }

    @Test fun failedSaveRetainsPreviousCompleteSnapshot() = runTest {
        val session = Session(); val disk = Disk(); val cache = disk.cache()
        cache.update(session.owner()) { it.withBuyerOrders(orders = listOf(order()), lines = listOf(line())) }
        disk.fail = true
        assertFalse(cache.update(session.owner()) { it.withBuyerOrders(orders = emptyList(), lines = emptyList()) })
        val restored = assertNotNull(disk.cache().cached(session.owner()))
        assertEquals("order-a", restored.orders?.single()?.id)
        assertEquals("line-a", restored.lines?.single()?.id)
    }

    @Test fun interruptionBeforeChunkManifestCommitRestoresTheWholePriorOrder() = runTest {
        val session = Session(); val rows = mutableMapOf<String, String>()
        var generation = 0; var failManifest = false
        val storage = ChunkedTextCache(
            read = { key, limit -> rows[key]?.takeIf { it.length <= limit } },
            write = { key, value ->
                check(!failManifest || !key.endsWith(":manifest"))
                rows[key] = value
            },
            remove = { rows.remove(it); Unit },
            removePrefixExcept = { prefix, keep ->
                rows.keys.removeAll { it.startsWith(prefix) && it != prefix + "manifest" && !it.startsWith(keep) }; Unit
            },
            newGeneration = { (++generation).toString(16) }, chunkSize = 64, maxLength = 16_384
        )
        val cache = SupplierWorkspaceCache(storage::get, storage::put, storage::delete)
        assertTrue(cache.update(session.owner()) { it.withBuyerOrders(orders = listOf(order()), lines = listOf(line())) })
        failManifest = true
        assertFalse(cache.update(session.owner()) {
            it.withBuyerOrders(orders = listOf(order("order-b")), lines = listOf(line("line-b", "order-b")))
        })
        val restored = assertNotNull(SupplierWorkspaceCache(storage::get, storage::put, storage::delete).cached(session.owner()))
        assertEquals(listOf(order()), restored.orders); assertEquals(listOf(line()), restored.lines)
    }

    @Test fun importedLegacyLinesWithoutRowIdsRemainAvailableOffline() = runTest {
        val session = Session(); val disk = Disk(); val cache = disk.cache()
        val legacyLines = listOf(line(id = ""), line(id = "").copy(goodsItemId = "goods-b"))
        assertTrue(cache.update(session.owner()) { it.withBuyerOrders(orders = listOf(order()), lines = legacyLines) })
        assertEquals(legacyLines, cache.cached(session.owner())?.lines)
    }

    @Test fun savingOtherSectionDoesNotDropOrdersOrUnknownSections() = runTest {
        val session = Session(); val disk = Disk(); val cache = disk.cache()
        cache.update(session.owner()) { it.withBuyerOrders(orders = listOf(order()), lines = listOf(line())) }
        val contract = SupplierPartnershipContractDataModel(id = "contract-a", storeId = "store-a", supplierId = "supplier-a")
        assertTrue(cache.update(session.owner()) { it.copy(contracts = listOf(contract), contractSources = SupplierWorkspaceSources(storeRows = mapOf("store-a" to setOf("contract-a")))) })
        val restored = assertNotNull(disk.cache().cached(session.owner()))
        assertEquals(listOf(order()), restored.orders); assertEquals(listOf(line()), restored.lines)
        assertEquals(listOf(contract), restored.contracts); assertNull(restored.prices)
    }

    @Test fun authoritativeEmptySnapshotIsRestoredAsEmptyNotMissing() = runTest {
        val session = Session(); val disk = Disk()
        disk.cache().update(session.owner()) { it.withBuyerOrders(orders = emptyList(), lines = emptyList()).copy(prices = emptyList(), priceSources = SupplierWorkspaceSources(allSuppliersLoaded = true)) }
        val restored = assertNotNull(disk.cache().cached(session.owner()))
        assertEquals(emptyList(), restored.orders); assertEquals(emptyList(), restored.lines)
        assertEquals(emptyList(), restored.prices); assertNull(restored.contracts)
    }

    @Test fun incompleteOrOrphanedOrderSnapshotCannotReplaceLastGoodCache() = runTest {
        val session = Session(); val disk = Disk(); val cache = disk.cache()
        cache.update(session.owner()) { it.withBuyerOrders(orders = listOf(order()), lines = listOf(line())) }
        assertFalse(cache.update(session.owner()) { it.copy(lines = null) })
        assertFalse(cache.update(session.owner()) { it.copy(lines = listOf(line(orderId = "wrong-order"))) })
        assertFalse(cache.update(session.owner()) { it.copy(lines = listOf(line(), line())) })
        assertEquals(listOf(line()), cache.cached(session.owner())?.lines)
    }

    @Test fun cachesNeverCrossAccountsEvenIfCopiedToAnotherKey() = runTest {
        val session = Session(); val disk = Disk(); val cache = disk.cache()
        cache.update(session.owner()) { it.withBuyerOrders(orders = listOf(order()), lines = listOf(line())) }
        session.account = "account-b"; session.generation++
        assertNull(cache.cached(session.owner()))
        disk.data["supplier-workspace-v1:account-b"] = disk.data.getValue("supplier-workspace-v1:account-a")
        assertNull(cache.cached(session.owner()))
    }

    @Test fun accountChangeWhileReadingDoesNotRestoreOrWriteStaleData() = runTest {
        val session = Session(); val owner = session.owner(); val disk = Disk()
        val encoded = jsonBase.encodeToString(SupplierWorkspaceSnapshot("account-a").withBuyerOrders(listOf(order()), listOf(line())))
        val cache = SupplierWorkspaceCache({ session.account = "account-b"; encoded }, disk::write, disk::remove)
        assertNull(cache.cached(owner)); assertTrue(disk.data.isEmpty())
        session.account = "account-a"
        assertFalse(cache.update(owner) { it.withBuyerOrders(orders = emptyList(), lines = emptyList()) })
        assertTrue(disk.data.isEmpty())
    }

    @Test fun corruptUnsupportedAndOversizedSnapshotsFailClosed() = runTest {
        val session = Session(); val disk = Disk(); val key = "supplier-workspace-v1:account-a"
        disk.data[key] = "not-json"; assertNull(disk.cache().cached(session.owner()))
        val newer = jsonBase.encodeToString(SupplierWorkspaceSnapshot("account-a", schema = 2))
        disk.data[key] = newer
        assertNull(disk.cache().cached(session.owner()))
        assertFalse(disk.cache().update(session.owner()) { it.copy(prices = emptyList(), priceSources = SupplierWorkspaceSources(allSuppliersLoaded = true)) })
        assertEquals(newer, disk.data[key])
        val incompatibleNewer = """{"accountId":"account-a","schema":2,"orders":{"future":"shape"},"lines":"new encoding"}"""
        disk.data[key] = incompatibleNewer
        assertNull(disk.cache().cached(session.owner()))
        assertFalse(disk.cache().update(session.owner()) { it.copy(prices = emptyList(), priceSources = SupplierWorkspaceSources(allSuppliersLoaded = true)) })
        assertEquals(incompatibleNewer, disk.data[key])
        disk.data.remove(key)
        assertFalse(disk.cache().update(session.owner()) { it.withBuyerOrders(orders = List(10_001) { i -> order("order-$i") }, lines = emptyList()) })
    }

    @Test fun oversizedFutureEnvelopeIsPreservedWithoutDecodingOrOverwriting() = runTest {
        val session = Session(); val disk = Disk(); val key = "supplier-workspace-v1:account-a"
        val oversized = "{\"accountId\":\"account-a\",\"schema\":2,\"padding\":\"" + "x".repeat(16 * 1024 * 1024) + "\"}"
        disk.data[key] = oversized
        assertFalse(disk.cache().restore(session.owner()).readable)
        assertFalse(disk.cache().update(session.owner()) { it.copy(prices = emptyList(), priceSources = SupplierWorkspaceSources(allSuppliersLoaded = true)) })
        assertEquals(oversized, disk.data[key])
    }

    @Test fun accountDeletionRemovesOnlyThatAccountsWorkspace() = runTest {
        val session = Session(); val disk = Disk(); val cache = disk.cache()
        cache.update(session.owner()) { it.withBuyerOrders(orders = listOf(order()), lines = listOf(line())) }
        session.account = "account-b"; session.generation++
        cache.update(session.owner()) { it.withBuyerOrders(orders = emptyList(), lines = emptyList()) }
        cache.clear("account-a")
        assertNotNull(cache.cached(session.owner())); session.account = "account-a"; session.generation++
        assertNull(cache.cached(session.owner()))
    }
}

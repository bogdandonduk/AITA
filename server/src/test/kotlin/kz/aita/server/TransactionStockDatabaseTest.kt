package kz.aita.server

import kz.aita.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assume.assumeTrue
import java.sql.DriverManager
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*

/** Exercises the actual normalization/mutation methods against an isolated disposable schema. */
class TransactionStockDatabaseTest {
    private val owner = UUID.fromString("11111111-1111-4111-8111-111111111111")
    private val store = UUID.fromString("22222222-2222-4222-8222-222222222222")
    private val otherStore = UUID.fromString("33333333-3333-4333-8333-333333333333")
    private val item = UUID.fromString("44444444-4444-4444-8444-444444444444")
    private val first = UUID.fromString("55555555-5555-4555-8555-555555555555")
    private val second = UUID.fromString("66666666-6666-4666-8666-666666666666")
    private val price = PriceDataModel("10", "KZT", "")
    private fun q(total: Double) = QuantityDataModel("0", emptyList(), total, 1.0, true)

    private fun fixture(block: (Database) -> Unit) {
        val url = System.getenv("AITA_RETURN_TEST_JDBC")?.takeIf { it.isNotBlank() }
            ?: System.getenv("AITA_APP_STATE_TEST_JDBC").orEmpty()
        assumeTrue("Requires disposable aita_return_test or aita_app_state_test database", url.isNotBlank())
        require(Regex("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/aita_(return|app_state)_test").matches(url))
        val user = System.getProperty("user.name")
        val schema = "return_test_" + UUID.randomUUID().toString().replace("-", "")
        DriverManager.getConnection(url, user, "").use { c -> c.createStatement().use { it.execute("CREATE SCHEMA $schema") } }
        val db = Database.connect("$url?currentSchema=$schema", driver = "org.postgresql.Driver", user = user, password = "")
        try {
            transaction(db) {
                exec("CREATE TABLE stores(id UUID PRIMARY KEY,parent_store_id UUID)")
                exec("INSERT INTO stores(id) VALUES ('$store'),('$otherStore')")
                SchemaUtils.create(StockItems, StockBatchesV2, Transactions, TransactionReturnItems)
                exec(javaClass.getResource("/db/migration/V118__stock_batch_kind.sql")!!.readText())
                StockItems.insert {
                    it[id] = item; it[userId] = owner; it[storeId] = store
                    it[barcodes] = listOf("123456789"); it[name] = listOf(LocalizedStringDataModel("en", "Milk"))
                    it[description] = emptyList(); it[measurementUnitId] = "0"; it[categoryIds] = emptyList()
                    it[salePrices] = listOf(price); it[returnPrices] = listOf(price); it[supplyPrices] = listOf(price)
                    it[isQuickItem] = false; it[imagePaths] = emptyList(); it[activeShelfBatchId] = first
                    it[createdAtMillis] = 1L; it[updatedAtMillis] = 1L; it[isActive] = true
                }
                seedBatch(first, 2.0, 0)
                seedBatch(second, 8.0, 1)
            }
            block(db)
        } finally {
            TransactionManager.closeAndUnregister(db)
            DriverManager.getConnection(url, user, "").use { c -> c.createStatement().use { it.execute("DROP SCHEMA $schema CASCADE") } }
        }
    }

    private fun seedBatch(batch: UUID, total: Double, priority: Int, kind: StockBatchKindDataModel = StockBatchKindDataModel.NORMAL) {
        StockBatchesV2.insert {
            it[id] = batch; it[goodsItemId] = item; it[userId] = owner; it[storeId] = store
            it[quantity] = q(total); it[supplyPrice] = price; it[discounts] = emptyList()
            it[shelfPriority] = priority; it[status] = StockBatchStatusDataModel.Delivered.name; it[StockBatchesV2.kind] = kind.name
            it[createdAtMillis] = 1L; it[updatedAtMillis] = 1L; it[isActive] = true
        }
    }
    private fun line(total: Double = 1.0) = GoodsItemInTransactionDataModel(
        barcode = "123456789", quantity = total, pricePerUnit = 10.0, goodsItemId = item.toString(), currencyCode = "KZT")
    private fun transactionModel(type: String, lines: List<GoodsItemInTransactionDataModel>) = TransactionDataModel(
        id = "", workshiftId = 0L, type = type, storeId = store.toString(), goodsInTransaction = lines,
        paidCash = lines.sumOf { it.quantity * it.pricePerUnit }, paidCard = 0.0, cardPaymentOptionId = 0,
        timeMillis = 100L, clientOperationId = "txn-${UUID.randomUUID()}")
    private fun save(type: String, lines: List<GoodsItemInTransactionDataModel>): TransactionDataModel {
        val normalized = assertNotNull(normalizeTransactionGoodsInsideTransaction(store, type, lines, listOf(store)).lines)
        val model = transactionModel(type, normalized)
        val mutated = assertNotNull(applyTransactionStockMutationInsideTransaction(owner, store, model, 100L, listOf(store)))
        val saved = model.copy(id = UUID.randomUUID().toString(), goodsInTransaction = mutated)
        Transactions.insert {
            it[id] = UUID.fromString(saved.id); it[userId] = owner; it[storeId] = store; it[workshiftId] = 0L
            it[Transactions.type] = type; it[goodsInTransaction] = saved.goodsInTransaction
            it[paidCash] = saved.paidCash; it[paidCard] = 0.0; it[cardPaymentOptionId] = 0
            it[timeMillis] = saved.timeMillis; it[clientOperationId] = saved.clientOperationId
        }
        syncTransactionReturnItemsInsideTransaction(owner, store, UUID.fromString(saved.id), saved, saved.clientOperationId, saved.timeMillis)
        return saved
    }
    private fun returnLine(sale: TransactionDataModel, total: Double = 1.0) = line(total).copy(
        originalTransactionId = sale.id, originalTransactionLineIndex = 0, originalClientOperationId = sale.clientOperationId,
        stockBatchId = first.toString())
    private fun total(batch: UUID) = StockBatchesV2.selectAll().where { StockBatchesV2.id eq batch }.single()[StockBatchesV2.quantity].total

    @Test fun saleCapturesMultipleRealSourcesAndReturnDoesNotFollowTheNewShelf() = fixture { db -> transaction(db) {
        val sale = save("purchase", listOf(line(5.0)))
        assertEquals(first.toString(), sale.goodsInTransaction.single().shelfBatchIdAtSale)
        assertEquals(listOf(first.toString() to 2.0, second.toString() to 3.0),
            sale.goodsInTransaction.single().sourceBatchAllocations.map { it.stockBatchId to it.quantity })
        assertEquals(second, StockItems.selectAll().single()[StockItems.activeShelfBatchId])
        val returned = save("return", listOf(returnLine(sale).copy(pricePerUnit = 999.0, currencyCode = "USD")))
        assertEquals(10.0, returned.goodsInTransaction.single().pricePerUnit)
        assertEquals("KZT", returned.goodsInTransaction.single().currencyCode)
        assertEquals(first.toString(), returned.goodsInTransaction.single().stockBatchId)
        assertEquals(1.0, total(first)); assertEquals(5.0, total(second))
    } }

    @Test fun familyVisibilityCannotConsumeOrRestockAnotherLocationsBatch() = fixture { db -> transaction(db) {
        StockBatchesV2.update({ StockBatchesV2.id eq second }) { it[storeId] = otherStore }
        val visible = listOf(store, otherStore)
        val normalized = normalizeTransactionGoodsInsideTransaction(store, "purchase", listOf(line(3.0)), visible)
        // Only two units belong to the operating branch, even though eight are visible elsewhere.
        assertNull(applyTransactionStockMutationInsideTransaction(owner, store,
            transactionModel("purchase", assertNotNull(normalized.lines)), 100L, visible))
        assertEquals(2.0, total(first)); assertEquals(8.0, total(second))
        val foreignReturn = normalizeTransactionGoodsInsideTransaction(store, "return",
            listOf(line().copy(stockBatchId = second.toString())), visible)
        assertEquals("return_batch_not_found", foreignReturn.errorCode)
        val own = assertNotNull(normalizeTransactionGoodsInsideTransaction(store, "purchase", listOf(line(2.0)), visible).lines)
        val completed = assertNotNull(applyTransactionStockMutationInsideTransaction(owner, store,
            transactionModel("purchase", own), 100L, visible))
        assertEquals(listOf(first.toString()), completed.single().sourceBatchAllocations.map { it.stockBatchId })
        assertEquals(0.0, total(first)); assertEquals(8.0, total(second))
    } }

    @Test fun receiptReferenceCannotCrossStoreOrOverReturnIncludingRepeatedRequestLines() = fixture { db -> transaction(db) {
        val sale = save("purchase", listOf(line(3.0)))
        assertNull(normalizeTransactionGoodsInsideTransaction(otherStore, "return", listOf(returnLine(sale)), listOf(otherStore)).lines)
        assertNull(normalizeTransactionGoodsInsideTransaction(store, "return", listOf(returnLine(sale, 2.0), returnLine(sale, 2.0)), listOf(store)).lines)
        save("return", listOf(returnLine(sale, 2.0)))
        assertEquals("return_receipt_quantity", normalizeTransactionGoodsInsideTransaction(store, "return", listOf(returnLine(sale, 2.0)), listOf(store)).errorCode)
    } }

    @Test fun ordinaryLegacyBatchUnitsKeepTheirExistingReturnBehavior() = fixture { db -> transaction(db) {
        StockBatchesV2.update({ StockBatchesV2.id eq first }) { it[quantity] = q(2.0).copy(id = "legacy-unit") }
        val returned = save("return", listOf(line().copy(stockBatchId = first.toString())))
        assertEquals(first.toString(), returned.goodsInTransaction.single().stockBatchId)
        assertEquals(3.0, StockBatchesV2.selectAll().where { StockBatchesV2.id eq first }.single()[StockBatchesV2.quantity].total)
    } }

    @Test fun explicitReturnedCreatesSeparateRecordAndUniversalReusesItsOwnPool() = fixture { db -> transaction(db) {
        val returned = save("return", listOf(line().copy(returnDestinationKind = StockBatchKindDataModel.RETURNED)))
        val newId = UUID.fromString(returned.goodsInTransaction.single().stockBatchId)
        assertNotEquals(first, newId); assertNotEquals(second, newId)
        assertEquals(StockBatchKindDataModel.RETURNED.name, StockBatchesV2.selectAll().where { StockBatchesV2.id eq newId }.single()[StockBatchesV2.kind])
        assertEquals(first, StockItems.selectAll().single()[StockItems.activeShelfBatchId])
        val pooled = save("return", listOf(line(2.0).copy(returnDestinationKind = StockBatchKindDataModel.UNIVERSAL)))
        val poolId = UUID.fromString(pooled.goodsInTransaction.single().stockBatchId)
        val pooledAgain = save("return", listOf(line(3.0).copy(returnDestinationKind = StockBatchKindDataModel.UNIVERSAL)))
        assertEquals(poolId.toString(), pooledAgain.goodsInTransaction.single().stockBatchId)
        assertEquals(5.0, total(poolId)); assertEquals(2.0, total(first)); assertEquals(8.0, total(second))
    } }

    @Test fun explicitUnavailableDestinationIsRejectedAndLegacyReturnStillWorks() = fixture { db -> transaction(db) {
        assertEquals("return_batch_not_found", normalizeTransactionGoodsInsideTransaction(store, "return",
            listOf(line().copy(stockBatchId = UUID.randomUUID().toString())), listOf(store)).errorCode)
        val legacy = save("return", listOf(line()))
        assertEquals(first.toString(), legacy.goodsInTransaction.single().stockBatchId)
        assertNull(legacy.goodsInTransaction.single().originalTransactionId)
        assertEquals(3.0, total(first))
    } }

    @Test fun concurrentLinkedReturnsCannotRefundTheSameRemainingUnit() = fixture { db ->
        val sale = transaction(db) { save("purchase", listOf(line())) }
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val work = List(2) { pool.submit(Callable {
                check(start.await(5, TimeUnit.SECONDS))
                transaction(db) {
                    val candidate = returnLine(sale)
                    val normalized = normalizeTransactionGoodsInsideTransaction(store, "return", listOf(candidate), listOf(store))
                    if (normalized.lines == null) false else { save("return", listOf(candidate)); true }
                }
            }) }
            start.countDown()
            assertEquals(1, work.map { it.get(15, TimeUnit.SECONDS) }.count { it })
            transaction(db) { assertEquals(2.0, total(first)); assertEquals(1L, TransactionReturnItems.selectAll().count()) }
        } finally { pool.shutdownNow(); check(pool.awaitTermination(15, TimeUnit.SECONDS)) }
    }
}

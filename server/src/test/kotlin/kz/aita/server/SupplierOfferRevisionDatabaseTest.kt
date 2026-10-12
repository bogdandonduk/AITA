package kz.aita.server

import kz.aita.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assume.assumeTrue
import java.sql.DriverManager
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.*

class SupplierOfferRevisionDatabaseTest {
    private val user = UUID.randomUUID()
    private val store = UUID.randomUUID()
    private val supplier = UUID.randomUUID()
    private val item = UUID.randomUUID()
    private fun fixture(block: (Database) -> Unit) {
        val url = System.getenv("AITA_STORE_ARCH_TEST_DB_URL").orEmpty()
        assumeTrue(url.isNotBlank())
        require(Regex("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/aita_test_[a-z0-9]+").matches(url))
        val owner = System.getProperty("user.name"); val schema = "offer_" + UUID.randomUUID().toString().replace("-", "")
        DriverManager.getConnection(url, owner, "").use { it.createStatement().use { it.execute("CREATE SCHEMA $schema") } }
        val db = Database.connect("$url?currentSchema=$schema", driver = "org.postgresql.Driver", user = owner, password = "")
        try {
            transaction(db) {
                exec("""CREATE TABLE supplier_goods_prices(id UUID PRIMARY KEY,user_id UUID NOT NULL,store_id UUID NOT NULL,
                    supplier_id UUID NOT NULL,goods_item_id UUID NOT NULL,supply_price JSONB NOT NULL,min_order_quantity JSONB,
                    package_quantity JSONB,supplier_barcode TEXT,supplier_goods_name TEXT,last_used_at_millis BIGINT,
                    created_at_millis BIGINT NOT NULL,updated_at_millis BIGINT NOT NULL,is_active BOOLEAN NOT NULL)""")
            }
            block(db)
        } finally {
            TransactionManager.closeAndUnregister(db)
            DriverManager.getConnection(url, owner, "").use { it.createStatement().use { it.execute("DROP SCHEMA $schema CASCADE") } }
        }
    }
    private fun save(price: String, expected: Long?, now: Long = 100L) = upsertSupplierGoodsPriceInsideTransaction(
        user, store, supplier, item, PriceDataModel(price, "KZT", supplier.toString()),
        supplierGoodsName = "Tea", replaceOptionalFields = true, now = now, expectedUpdatedAtMillis = expected)
    @Test fun firstSaveAndSameMillisecondEditsGetDistinctRevisions() = fixture { db ->
        val id = transaction(db) { save("100", 0) }
        assertEquals(id, transaction(db) { save("110", 100) })
        transaction(db) { assertEquals(101L, SupplierGoodsPrices.selectAll().single()[SupplierGoodsPrices.updatedAtMillis]) }
    }
    @Test fun staleDraftCannotOverwriteAndExactLostResponseCanRetry() = fixture { db ->
        val id = transaction(db) { save("100", 0) }
        assertEquals(id, transaction(db) { save("100", 0) })
        assertFailsWith<SupplierOfferConflict> { transaction(db) { save("120", 0) } }
        transaction(db) { assertEquals("100", SupplierGoodsPrices.selectAll().single()[SupplierGoodsPrices.supplyPrice].price) }
        assertEquals(id, transaction(db) { save("120", 100) })
    }
    @Test fun concurrentEditorsCannotBothWin() = fixture { db ->
        transaction(db) { save("100", 0) }
        val pool = Executors.newFixedThreadPool(2)
        try {
            val requests = listOf("110", "120").map { price -> pool.submit(Callable {
                runCatching { transaction(db) { save(price, 100) } }
            }) }.map { it.get() }
            assertEquals(1, requests.count { it.isSuccess })
            assertTrue(requests.single { it.isFailure }.exceptionOrNull() is SupplierOfferConflict)
        } finally { pool.shutdownNow() }
    }
    @Test fun legacyLearningStaysCompatibleButInvalidRevisionIsRejected() = fixture { db ->
        transaction(db) { save("100", null); save("110", null) }
        assertFailsWith<SupplierOfferConflict> { transaction(db) { save("110", -1) } }
    }
}

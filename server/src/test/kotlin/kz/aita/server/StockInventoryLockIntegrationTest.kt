package kz.aita.server

import kz.aita.QuantityDataModel
import org.junit.Assume.assumeTrue
import java.sql.Connection
import java.sql.DriverManager
import java.util.Properties
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*

/** Opt-in PostgreSQL protocol tests. Only aita_test_* databases, isolated disposable schema.
 * These use the production advisory-lock SQL and quantity rules; they are not HTTP-route tests.
 */
class StockInventoryLockIntegrationTest {
    private fun exec(c: Connection, sql: String) = c.createStatement().use { it.execute(sql) }
    private fun scalar(c: Connection, sql: String): String = c.createStatement().use { s ->
        s.executeQuery(sql).use { rows -> check(rows.next()); rows.getString(1) }
    }
    private class Fixture(val url: String, val props: Properties, val schema: String, val root: UUID)
    private fun fixture(block: (Fixture, Connection) -> Unit) {
        val url = System.getenv("AITA_AUTH_TEST_DB_URL").orEmpty()
        assumeTrue("Set dedicated AITA_AUTH_TEST_DB_URL to run PostgreSQL transfer tests", url.isNotBlank())
        require(url.startsWith("jdbc:postgresql:"))
        val props = Properties().apply {
            System.getenv("AITA_AUTH_TEST_DB_USER")?.let { setProperty("user", it) }
            System.getenv("AITA_AUTH_TEST_DB_PASSWORD")?.let { setProperty("password", it) }
            setProperty("connectTimeout", "5"); setProperty("socketTimeout", "15")
            setProperty("ApplicationName", "aita-batch-transfer-test")
        }
        DriverManager.getConnection(url, props).use { c ->
            require(scalar(c, "SELECT current_database()").startsWith("aita_test_")) { "Refusing a non-test database" }
            val schema = "aita_transfer_" + UUID.randomUUID().toString().replace("-", "")
            exec(c, "CREATE SCHEMA $schema")
            try {
                exec(c, "CREATE TABLE $schema.stock (id int PRIMARY KEY, quantity double precision NOT NULL)")
                exec(c, "INSERT INTO $schema.stock VALUES (1,10)")
                exec(c, "CREATE TABLE $schema.decision (id int PRIMARY KEY, status text NOT NULL, source double precision NOT NULL, incoming double precision NOT NULL)")
                exec(c, "INSERT INTO $schema.decision VALUES (1,'pending',6,4)")
                block(Fixture(url, props, schema, UUID.randomUUID()), c)
            } finally { exec(c, "DROP SCHEMA $schema CASCADE") }
        }
    }
    private fun <T> transaction(f: Fixture, block: (Connection) -> T): T = DriverManager.getConnection(f.url, f.props).use { c ->
        c.autoCommit = false
        try {
            exec(c, "SET LOCAL statement_timeout='3s'"); exec(c, "SET LOCAL lock_timeout='2s'")
            exec(c, stockInventoryLockSql(f.root))
            block(c).also { c.commit() }
        } catch (failure: Throwable) { c.rollback(); throw failure }
    }
    private fun <T> parallel(a: () -> T, b: () -> T): List<T> {
        val pool = Executors.newFixedThreadPool(2); val start = CountDownLatch(1)
        try {
            val tasks = listOf(a,b).map { fn -> pool.submit(Callable { check(start.await(3, TimeUnit.SECONDS)); fn() }) }
            start.countDown(); return tasks.map { it.get(10, TimeUnit.SECONDS) }
        } finally { pool.shutdownNow(); check(pool.awaitTermination(16, TimeUnit.SECONDS)) }
    }
    private fun q(total: Double) = QuantityDataModel("kg", emptyList(), total, 1.0, false)

    @Test fun simultaneousTransfersCannotBothSpendTheSameOldBalance() = fixture { f, c ->
        fun move(): Boolean = transaction(f) { tx ->
            val old = scalar(tx,"SELECT quantity FROM ${f.schema}.stock WHERE id=1").toDouble()
            val plan = planStockBatchTransfer(q(old),7.0) ?: return@transaction false
            Thread.sleep(40) // Force overlap at the read/compute/write boundary.
            exec(tx,"UPDATE ${f.schema}.stock SET quantity=${plan.remaining.total} WHERE id=1"); true
        }
        assertEquals(1,parallel(::move,::move).count { it })
        assertEquals(3.0,scalar(c,"SELECT quantity FROM ${f.schema}.stock WHERE id=1").toDouble())
    }
    @Test fun competingAcceptanceAndRejectionDecideOnlyOnce() = fixture { f,c ->
        fun decide(accept: Boolean): Boolean = transaction(f) { tx ->
            if (scalar(tx,"SELECT status FROM ${f.schema}.decision WHERE id=1") != "pending") return@transaction false
            Thread.sleep(40)
            if (accept) exec(tx,"UPDATE ${f.schema}.decision SET status='accepted' WHERE id=1")
            else {
                val quantity = scalar(tx,"SELECT source FROM ${f.schema}.decision WHERE id=1").toDouble()
                val restored = requireNotNull(restoreDeclinedStockQuantity(q(quantity),q(4.0)))
                exec(tx,"UPDATE ${f.schema}.decision SET status='declined',source=${restored.total},incoming=0 WHERE id=1")
            }
            true
        }
        assertEquals(1,parallel({decide(true)},{decide(false)}).count { it })
        assertEquals(10.0,scalar(c,"SELECT source+incoming FROM ${f.schema}.decision WHERE id=1").toDouble())
    }
    @Test fun lateFailureRollsBackEveryDebitAndReleasesTheRootLock() = fixture { f,c ->
        assertFailsWith<IllegalStateException> { transaction(f) { tx ->
            exec(tx,"UPDATE ${f.schema}.stock SET quantity=3 WHERE id=1")
            error("simulated later-line validation failure")
        } }
        assertEquals(10.0,scalar(c,"SELECT quantity FROM ${f.schema}.stock WHERE id=1").toDouble())
        assertEquals("10",transaction(f) { tx -> scalar(tx,"SELECT quantity::int FROM ${f.schema}.stock WHERE id=1") })
    }
}

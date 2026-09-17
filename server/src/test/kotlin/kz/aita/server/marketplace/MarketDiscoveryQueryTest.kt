package kz.aita.server.marketplace

import kz.aita.*
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.util.UUID
import kotlin.test.*

/** Exercises the real discovery/header/budget query code with empty-window JDBC doubles.
 * Database snapshot isolation and stock projection are covered separately by opt-in DB tests.
 */
class MarketDiscoveryQueryTest {
    private val buyer = UUID(0, 99)
    private val shopId = UUID(0, 20)
    private val shop = MarketStorefront(shopId.toString(), "Shop", "Astana", "Pickup door", published = true, revision = 2)
    private class Jdbc(var shop: MarketStorefront?) {
        data class Query(val sql: String, val bindings: MutableMap<Int, Any?> = linkedMapOf(), var timeout: Int? = null)
        val queries = mutableListOf<Query>()
        var failure: SQLException? = null
        private fun rows(query: Query): ResultSet {
            val values: List<Map<Any, Any?>> = when {
                query.sql.startsWith("SELECT f.*") -> shop?.let { listOf(mapOf<Any, Any?>(
                    "store_id" to it.storeId, "display_name" to it.displayName, "city" to it.city,
                    "public_address" to it.publicAddress, "pickup_note" to it.pickupNote,
                    "is_published" to it.published, "revision" to it.revision, "share_branch_availability" to false)) }.orEmpty()
                query.sql.startsWith("SELECT count(*),count(DISTINCT") -> listOf(mapOf<Any, Any?>(1 to 0L, 2 to 0L))
                query.sql.startsWith("SELECT count(*) FROM buyer_saved_offers") -> listOf(mapOf<Any, Any?>(1 to 0L))
                query.sql.startsWith("SELECT id,name,type_ids") || query.sql.startsWith("SELECT l.*") -> emptyList()
                else -> error("Unexpected query ${query.sql}")
            }
            var index = -1
            return Proxy.newProxyInstance(javaClass.classLoader, arrayOf(ResultSet::class.java)) { _, method, args ->
                when (method.name) {
                    "next" -> { index++; index < values.size }
                    "close" -> null
                    "getString" -> values[index][args!![0]] as String?
                    "getLong" -> values[index][args!![0]] as Long
                    "getBoolean" -> values[index][args!![0]] as Boolean
                    else -> error("Unexpected row operation ${method.name}")
                }
            } as ResultSet
        }
        val connection = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(Connection::class.java)) { _, method, args ->
            when (method.name) {
                "getAutoCommit" -> false
                "prepareStatement" -> {
                    val query = Query(args!![0] as String); queries += query
                    Proxy.newProxyInstance(javaClass.classLoader, arrayOf(PreparedStatement::class.java)) { _, operation, values ->
                        when (operation.name) {
                            "setObject" -> { query.bindings[values!![0] as Int] = values[1]; null }
                            "setQueryTimeout" -> { query.timeout = values!![0] as Int; null }
                            "executeQuery" -> {
                                failure?.let { failure = null; throw it }
                                assertEquals((1..query.sql.count { it == '?' }).toSet(), query.bindings.keys)
                                rows(query)
                            }
                            "close" -> null
                            else -> error("Unexpected statement operation ${operation.name}")
                        }
                    } as PreparedStatement
                }
                else -> error("Unexpected connection operation ${method.name}")
            }
        } as Connection
    }
    @Test fun scopedEmptyWindowBindsOneClockToHeaderAndOfferVisibility() {
        val jdbc = Jdbc(shop); val repo = MarketplaceRepository(jdbc.connection) { _, _ -> false }
        val wanted = MarketDiscoveryRequest(MarketDiscoveryQuery(storefrontId = shop.storeId, city = "ignored"))
        val result = repo.withDiscoveryReadBudget { repo.discover(buyer, wanted) }
        assertEquals(shop, result.storefront); assertEquals(buyer.toString(), result.accountId)
        assertEquals(listOf(shopId, result.page.checkedAtMillis, result.page.checkedAtMillis), jdbc.queries.first().bindings.values.toList())
        val count = jdbc.queries.single { it.sql.startsWith("SELECT count(*),count(DISTINCT") }
        assertEquals(listOf(result.page.checkedAtMillis, result.page.checkedAtMillis, shopId), count.bindings.values.toList())
        assertNotNull(result.validatedDiscovery(wanted, null, buyer.toString()))
        assertEquals(4, jdbc.queries.size)
        assertTrue(jdbc.queries.all { it.timeout in 1..5 })
    }
    @Test fun unscopedSearchKeepsLiteralFiltersAndWindowLimitOutOfSqlText() {
        val jdbc = Jdbc(shop); val repo = MarketplaceRepository(jdbc.connection) { _, _ -> false }
        val wanted = MarketDiscoveryRequest(MarketDiscoveryQuery(text = "%_", city = "Astana"), limit = 80)
        val result = repo.withDiscoveryReadBudget { repo.discover(buyer, wanted) }
        assertNull(result.storefront); assertEquals(buyer.toString(), result.accountId)
        assertFalse(jdbc.queries.any { it.sql.startsWith("SELECT f.*") })
        val scan = jdbc.queries.single { it.sql.startsWith("SELECT l.*") }
        assertTrue(scan.sql.endsWith("LIMIT ?")); assertFalse("%_" in scan.sql)
        assertEquals(listOf(result.page.checkedAtMillis, result.page.checkedAtMillis, "%_", "%_", "Astana", 80), scan.bindings.values.toList())
        assertNotNull(result.validatedDiscovery(wanted, null, buyer.toString()))
    }
    @Test fun unavailableHeaderStopsBeforeTaxonomyAndOfferQueries() {
        val jdbc = Jdbc(null); val repo = MarketplaceRepository(jdbc.connection) { _, _ -> false }
        val failure = assertFailsWith<MarketFailure> {
            repo.withDiscoveryReadBudget { repo.discover(buyer, MarketDiscoveryRequest(MarketDiscoveryQuery(storefrontId = shop.storeId))) }
        }
        assertEquals("market.shop_unavailable", failure.key); assertEquals(404, failure.status)
        assertEquals(1, jdbc.queries.size)
    }
    @Test fun invalidWindowDoesNotReachSql() {
        val jdbc = Jdbc(shop); val repo = MarketplaceRepository(jdbc.connection) { _, _ -> false }
        assertEquals(400, assertFailsWith<MarketFailure> { repo.discover(buyer, MarketDiscoveryRequest(MarketDiscoveryQuery(), 401)) }.status)
        assertTrue(jdbc.queries.isEmpty())
    }
    @Test fun savedFilterAndUnavailableCountUseTheSameBuyer() {
        val jdbc = Jdbc(shop); val repo = MarketplaceRepository(jdbc.connection) { _, _ -> false }
        val wanted = MarketDiscoveryRequest(MarketDiscoveryQuery(savedOnly = true))
        val result = repo.withDiscoveryReadBudget { repo.discover(buyer, wanted) }
        assertEquals(buyer.toString(), result.accountId)
        val rows = jdbc.queries.single { it.sql.startsWith("SELECT l.*") }
        assertTrue("b.user_id=?" in rows.sql); assertEquals(buyer, rows.bindings[3])
        val hidden = jdbc.queries.last()
        assertTrue(hidden.sql.startsWith("SELECT count(*) FROM buyer_saved_offers"))
        assertEquals(listOf(buyer, result.page.checkedAtMillis, result.page.checkedAtMillis), hidden.bindings.values.toList())
        assertNotNull(result.validatedDiscovery(wanted, null, buyer.toString()))
    }
    @Test fun timeoutBecomesDiscoveryFeedbackAndDoesNotLeakABudgetIntoNextRead() {
        val jdbc = Jdbc(shop); val repo = MarketplaceRepository(jdbc.connection) { _, _ -> false }
        jdbc.failure = SQLException("timeout", "57014")
        val failure = assertFailsWith<MarketFailure> { repo.withDiscoveryReadBudget { repo.discover(buyer, MarketDiscoveryRequest(MarketDiscoveryQuery())) } }
        assertEquals("market.discovery_busy", failure.key); assertEquals(503, failure.status)
        repo.discover(buyer, MarketDiscoveryRequest(MarketDiscoveryQuery()))
        assertNull(jdbc.queries.last().timeout)
    }
    @Test fun unrelatedDatabaseErrorRemainsAnErrorRatherThanAnEmptyWindow() {
        val jdbc = Jdbc(shop); val repo = MarketplaceRepository(jdbc.connection) { _, _ -> false }
        jdbc.failure = SQLException("schema missing", "42P01")
        val failure = assertFailsWith<SQLException> { repo.withDiscoveryReadBudget { repo.discover(buyer, MarketDiscoveryRequest(MarketDiscoveryQuery())) } }
        assertEquals("42P01", failure.sqlState)
        assertNotNull(repo.withDiscoveryReadBudget { repo.discover(buyer, MarketDiscoveryRequest(MarketDiscoveryQuery())) })
    }
}

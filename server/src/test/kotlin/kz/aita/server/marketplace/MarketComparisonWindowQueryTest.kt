package kz.aita.server.marketplace

import kz.aita.*
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.util.UUID
import kotlin.test.*

/** Real repository query/budget code against JDBC boundary doubles. Not a database execution test. */
class MarketComparisonWindowQueryTest {
    private class EmptyJdbc {
        data class Query(val sql: String, val values: MutableMap<Int, Any?> = linkedMapOf(), var timeout: Int? = null)
        val queries = mutableListOf<Query>()
        var failure: SQLException? = null
        private fun rows(): ResultSet = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(ResultSet::class.java)) { _, method, _ ->
            when (method.name) { "next" -> false; "close" -> null; else -> error("Unexpected result method ${method.name}") }
        } as ResultSet
        val connection = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(Connection::class.java)) { _, method, args ->
            when (method.name) {
                "getAutoCommit" -> false
                "prepareStatement" -> {
                    val query = Query(args!![0] as String); queries += query
                    Proxy.newProxyInstance(javaClass.classLoader, arrayOf(PreparedStatement::class.java)) { _, operation, values ->
                        when (operation.name) {
                            "setObject" -> { query.values[values!![0] as Int] = values[1]; null }
                            "setQueryTimeout" -> { query.timeout = values!![0] as Int; null }
                            "executeQuery" -> {
                                failure?.let { failure = null; throw it }
                                assertEquals((1..query.sql.count { it == '?' }).toSet(), query.values.keys)
                                rows()
                            }
                            "close" -> null
                            else -> error("Unexpected statement method ${operation.name}")
                        }
                    } as PreparedStatement
                }
                else -> error("Unexpected connection method ${method.name}")
            }
        } as Connection
    }
    private val user = UUID(0, 1)
    private val selection = MarketComparisonSelection(UUID(0, 2).toString(), MarketShoppingBasis("04006381333931", "KZT", "piece", 1.0), 2, 1)
    private val reference = MarketShoppingLine(selection.offerId, UUID(0, 3).toString(), "Saved product", "Saved shop", 2, selection.basis)
    private fun request(city: String = "", limit: Int = 40) = MarketComparisonWindowRequest(selection, city, limit)

    @Test fun fullWindowHasOneBoundedScanAndLiteralCityBindings() {
        val jdbc = EmptyJdbc(); val market = MarketplaceRepository(jdbc.connection) { _, _ -> false }
        val city = "%'_ OR TRUE --"
        val result = market.withComparisonReadBudget { market.compareWindow(user, request(city, 400), reference, 1_000L) }
        val scan = jdbc.queries.first()
        assertTrue(scan.sql.contains("ORDER BY l.id LIMIT ?")); assertEquals(401, scan.values.values.last())
        assertEquals(listOf(1_000L, 1_000L, selection.basis.gtin, UUID(0, 2), UUID(0, 3), city, 401), scan.values.values.toList())
        assertFalse(city in scan.sql); assertTrue(scan.sql.contains("l.store_id<>?"))
        assertTrue(scan.sql.contains("l.is_published")); assertTrue(scan.sql.contains("i.is_active"))
        assertTrue(result.isValidComparisonWindowResult(user.toString(), request(city, 400)))
        assertEquals(0, result.candidatesChecked); assertFalse(result.moreCandidates)
        assertTrue(jdbc.queries.all { it.sql.trimStart().startsWith("SELECT") && it.timeout in 1..5 })
    }
    @Test fun emptyCityAddsNoCityBindingOrUnboundedScan() {
        val jdbc = EmptyJdbc(); val market = MarketplaceRepository(jdbc.connection) { _, _ -> false }
        market.compareWindow(user, request(), reference, 10L)
        assertEquals(6, jdbc.queries.first().values.size); assertEquals(41, jdbc.queries.first().values[6])
        assertFalse(jdbc.queries.first().sql.contains("lower(f.city)"))
    }
    @Test fun malformedWindowDoesNotQueryTheDatabase() {
        val jdbc = EmptyJdbc(); val market = MarketplaceRepository(jdbc.connection) { _, _ -> false }
        assertEquals(400, assertFailsWith<MarketFailure> { market.compareWindow(user, request(limit = 401), reference, 10L) }.status)
        assertTrue(jdbc.queries.isEmpty())
    }
    @Test fun statementTimeoutUsesComparisonFeedbackAndReleasesTheBudget() {
        val jdbc = EmptyJdbc(); val market = MarketplaceRepository(jdbc.connection) { _, _ -> false }
        jdbc.failure = SQLException("timed out", "57014")
        val failure = assertFailsWith<MarketFailure> { market.withComparisonReadBudget { market.compareWindow(user, request(), reference, 10L) } }
        assertEquals("market.comparison_window_busy", failure.key); assertEquals(503, failure.status)
        market.compareWindow(user, request(), reference, 11L)
        assertNull(jdbc.queries.last().timeout)
    }
    @Test fun unrelatedSqlFailureIsNotDisguisedAsBusyOrAnEmptyComparison() {
        val jdbc = EmptyJdbc(); val market = MarketplaceRepository(jdbc.connection) { _, _ -> false }
        jdbc.failure = SQLException("bad schema", "42P01")
        val failure = assertFailsWith<SQLException> { market.withComparisonReadBudget { market.compareWindow(user, request(), reference, 10L) } }
        assertEquals("42P01", failure.sqlState)
        market.withComparisonReadBudget { market.compareWindow(user, request(), reference, 11L) }
    }
    @Test fun referenceResolutionDoesNotReadAnotherAccountsListOrWriteAnEmptyOne() {
        val jdbc = EmptyJdbc(); val market = MarketplaceRepository(jdbc.connection) { _, _ -> false }
        val failure = assertFailsWith<MarketFailure> { MarketShoppingRepository(jdbc.connection, market).comparisonWindow(user, request()) }
        assertEquals("market.shopping_changed", failure.key); assertEquals(409, failure.status)
        assertEquals(1, jdbc.queries.size)
        assertEquals("SELECT revision FROM buyer_shopping_lists WHERE user_id=?", jdbc.queries.single().sql)
        assertEquals(user, jdbc.queries.single().values[1])
    }
    @Test fun unavailableBrowseReferenceStopsBeforeScanningCandidates() {
        val jdbc = EmptyJdbc(); val market = MarketplaceRepository(jdbc.connection) { _, _ -> false }
        val input = request().copy(selection = selection.copy(shoppingRevision = null))
        val failure = assertFailsWith<MarketFailure> { MarketShoppingRepository(jdbc.connection, market).comparisonWindow(user, input) }
        assertEquals("market.unavailable", failure.key); assertEquals(404, failure.status)
        assertTrue(jdbc.queries.all { it.sql.trimStart().startsWith("SELECT") })
        assertTrue(jdbc.queries.none { it.sql.contains("ORDER BY l.id LIMIT ?") })
        assertTrue(jdbc.queries.all { it.timeout in 1..5 })
    }

}

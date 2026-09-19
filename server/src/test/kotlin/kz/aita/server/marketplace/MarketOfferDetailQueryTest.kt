package kz.aita.server.marketplace

import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.util.UUID
import kotlin.test.*

/** Real detail/visibility/read-budget SQL with empty-row JDBC doubles. Public stock projection
 * and real transaction isolation are tested separately by the opt-in PostgreSQL fixture.
 */
class MarketOfferDetailQueryTest {
    private val user = UUID(0, 99)
    private val offerId = UUID.fromString("abcdef01-abcd-abcd-abcd-abcdef012345")
    private class Jdbc {
        data class Query(val sql: String, val args: MutableMap<Int, Any?> = linkedMapOf(), var timeout: Int? = null)
        val queries = mutableListOf<Query>()
        var failure: SQLException? = null
        val connection = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(Connection::class.java)) { _, method, args ->
            when (method.name) {
                "getAutoCommit" -> false
                "prepareStatement" -> {
                    val query = Query(args!![0] as String); queries += query
                    Proxy.newProxyInstance(javaClass.classLoader, arrayOf(PreparedStatement::class.java)) { _, call, values ->
                        when (call.name) {
                            "setObject" -> { query.args[values!![0] as Int] = values[1]; null }
                            "setQueryTimeout" -> { query.timeout = values!![0] as Int; null }
                            "executeQuery" -> {
                                failure?.let { failure = null; throw it }
                                assertEquals((1..query.sql.count { it == '?' }).toSet(), query.args.keys)
                                Proxy.newProxyInstance(javaClass.classLoader, arrayOf(ResultSet::class.java)) { _, row, _ ->
                                    when (row.name) { "next" -> false; "close" -> null; else -> error("Unexpected row read ${row.name}") }
                                } as ResultSet
                            }
                            "close" -> null
                            else -> error("Unexpected statement ${call.name}")
                        }
                    } as PreparedStatement
                }
                else -> error("Unexpected connection ${method.name}")
            }
        } as Connection
    }
    @Test fun invalidOfferIdDoesNotReachSql() {
        val jdbc = Jdbc(); val repo = MarketplaceRepository(jdbc.connection) { _, _ -> false }
        assertEquals(400, assertFailsWith<MarketFailure> { repo.withOfferDetailReadBudget { repo.offerDetail(user, "../seller") } }.status)
        assertTrue(jdbc.queries.isEmpty())
    }
    @Test fun detailVisibilityBindsOneClockAndAnExactIdWithoutPrivateFallback() {
        val jdbc = Jdbc(); val repo = MarketplaceRepository(jdbc.connection) { _, _ -> false }
        val missing = assertFailsWith<MarketFailure> { repo.withOfferDetailReadBudget { repo.offerDetail(user, offerId.toString().uppercase()) } }
        assertEquals(404, missing.status); assertEquals("market.unavailable", missing.key)
        val query = jdbc.queries.single()
        assertTrue("l.is_published" in query.sql); assertTrue("f.is_published" in query.sql)
        assertTrue("e.store_id=s.id" in query.sql); assertTrue("l.id IN (?)" in query.sql)
        assertEquals(query.args[1], query.args[2]); assertTrue((query.args[1] as Long) > 0)
        assertEquals(offerId, query.args[3]); assertTrue(query.timeout in 1..5)
    }
    @Test fun timeoutBecomesDetailFeedbackAndBudgetIsRemovedForNextRead() {
        val jdbc = Jdbc(); val repo = MarketplaceRepository(jdbc.connection) { _, _ -> false }
        jdbc.failure = SQLException("deadline", "57014")
        val timeout = assertFailsWith<MarketFailure> { repo.withOfferDetailReadBudget { repo.offerDetail(user, offerId.toString()) } }
        assertEquals(503, timeout.status); assertEquals("market.detail_busy", timeout.key)
        assertEquals(404, assertFailsWith<MarketFailure> { repo.offerDetail(user, offerId.toString()) }.status)
        assertNull(jdbc.queries.last().timeout)
    }
    @Test fun unrelatedSqlFailureIsNotRewrittenAsAnUnavailableProduct() {
        val jdbc = Jdbc(); val repo = MarketplaceRepository(jdbc.connection) { _, _ -> false }
        jdbc.failure = SQLException("missing schema", "42P01")
        val error = assertFailsWith<SQLException> { repo.withOfferDetailReadBudget { repo.offerDetail(user, offerId.toString()) } }
        assertEquals("42P01", error.sqlState)
        assertEquals(404, assertFailsWith<MarketFailure> { repo.withOfferDetailReadBudget { repo.offerDetail(user, offerId.toString()) } }.status)
        assertTrue(jdbc.queries.last().timeout in 1..5)
    }
    @Test fun failedReadCanBeFollowedByAnotherBudgetWithoutNestedState() {
        val jdbc = Jdbc(); val repo = MarketplaceRepository(jdbc.connection) { _, _ -> false }
        repeat(3) {
            assertEquals(404, assertFailsWith<MarketFailure> { repo.withOfferDetailReadBudget { repo.offerDetail(user, offerId.toString()) } }.status)
        }
        assertEquals(3, jdbc.queries.size); assertTrue(jdbc.queries.all { it.timeout in 1..5 })
    }
}

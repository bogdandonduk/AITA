package kz.aita.server.marketplace

import kz.aita.*
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.util.UUID
import kotlin.test.*

/** Actual query-builder/budget code with empty JDBC result sets, NOT PostgreSQL execution.
 * RepositoryDatabaseTest separately exercises real publication/price/quantity behaviour.
 */
class MarketBasketQueryContractTest {
    private class EmptyJdbc {
        data class Query(val sql:String,val values:MutableMap<Int,Any?> = linkedMapOf(),var timeout:Int?=null)
        val queries=mutableListOf<Query>()
        var failNext=false
        private fun emptyRows():ResultSet = Proxy.newProxyInstance(javaClass.classLoader,arrayOf(ResultSet::class.java)) { _,method,_ ->
            when(method.name) { "next" -> false; "close" -> null; else -> error("Unexpected result-set method ${method.name}") }
        } as ResultSet
        val connection:Connection = Proxy.newProxyInstance(javaClass.classLoader,arrayOf(Connection::class.java)) { _,method,args ->
            when(method.name) {
                "getAutoCommit" -> false
                "prepareStatement" -> {
                    val query=Query(args!![0] as String);queries+=query
                    Proxy.newProxyInstance(javaClass.classLoader,arrayOf(PreparedStatement::class.java)) { _,statement,values ->
                        when(statement.name) {
                            "setObject" -> { query.values[values!![0] as Int]=values[1];null }
                            "setQueryTimeout" -> { query.timeout=values!![0] as Int;null }
                            "executeQuery" -> {
                                if(failNext){failNext=false;throw SQLException("test timeout","57014")}
                                assertEquals((1..query.sql.count { it=='?' }).toSet(),query.values.keys)
                                emptyRows()
                            }
                            "close" -> null
                            else -> error("Unexpected statement method ${statement.name}")
                        }
                    } as PreparedStatement
                }
                else -> error("Unexpected connection method ${method.name}")
            }
        } as Connection
    }
    private val user=UUID(0,1)
    private fun source(code:String?="04006381333931")=MarketShoppingSnapshot(user.toString(),1,
        listOf(MarketShoppingQuotedLine(MarketShoppingLine(UUID(0,2).toString(),UUID(0,3).toString(),"Product","Shop",2,
            MarketShoppingBasis(code,"KZT","piece",1.0)))),10_000L)

    @Test fun cityIsBoundLiterallyAndNotInsertedIntoTheSqlText() {
        val jdbc=EmptyJdbc();val repository=MarketplaceRepository(jdbc.connection){_,_->false}
        val city="%'_ OR TRUE --"
        val result=repository.basketCandidates(user,source(),city)
        assertEquals(0,result.checked);assertTrue(result.choices.isEmpty())
        val query=jdbc.queries.single()
        assertFalse(query.sql.contains(city));assertEquals(city,query.values.values.last())
        assertTrue(query.sql.contains("CROSS JOIN LATERAL"));assertTrue(query.sql.contains("LIMIT 13"))
        assertEquals(7,query.values.size)
    }
    @Test fun noBarcodeDoesNotScanAnyAlternativeListings() {
        val jdbc=EmptyJdbc();val repository=MarketplaceRepository(jdbc.connection){_,_->false}
        assertEquals(0,repository.basketCandidates(user,source(null),"").checked)
        assertTrue(jdbc.queries.isEmpty())
    }
    @Test fun repeatedDemandsDoNotScanOrIndependentlyQuoteOneTarget() {
        val jdbc=EmptyJdbc();val repository=MarketplaceRepository(jdbc.connection){_,_->false}
        val first=source();val second=first.lines.single().copy(line=first.lines.single().line.copy(offerId=UUID(0,4).toString()))
        assertEquals(0,repository.basketCandidates(user,first.copy(lines=first.lines+second),"").checked)
        assertTrue(jdbc.queries.isEmpty())
    }
    @Test fun multiDemandPlaceholdersHaveExactlyMatchingBindings() {
        val jdbc=EmptyJdbc();val repository=MarketplaceRepository(jdbc.connection){_,_->false}
        val first=source();val row=first.lines.single()
        val second=row.copy(line=row.line.copy(offerId=UUID(0,4).toString(),basis=row.line.basis.copy(pricedAmount=2.0)))
        repository.basketCandidates(user,first.copy(lines=first.lines+second),"")
        // 3 fields per demand + two clock values + both existing offer IDs, no city parameter.
        assertEquals(10,jdbc.queries.single().values.size)
    }
    @Test fun queryBudgetDoesNotLeakToANormalLaterRepositoryRead() {
        val jdbc=EmptyJdbc();val repository=MarketplaceRepository(jdbc.connection){_,_->false}
        repository.withBasketReadBudget { repository.basketCandidates(user,source(),"") }
        assertTrue(requireNotNull(jdbc.queries.single().timeout) in 1..5)
        repository.basketCandidates(user,source(),"")
        assertNull(jdbc.queries.last().timeout)
    }
    @Test fun statementCancellationIsReportedWithoutKeepingTheBudgetActive() {
        val jdbc=EmptyJdbc();val repository=MarketplaceRepository(jdbc.connection){_,_->false}
        jdbc.failNext=true
        val error=assertFailsWith<MarketFailure> { repository.withBasketReadBudget { repository.basketCandidates(user,source(),"") } }
        assertEquals(503,error.status);assertEquals("market.basket_busy",error.key)
        repository.basketCandidates(user,source(),"")
        assertNull(jdbc.queries.last().timeout)
    }
}

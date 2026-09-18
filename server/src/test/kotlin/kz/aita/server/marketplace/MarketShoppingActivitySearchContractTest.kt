package kz.aita.server.marketplace

import kz.aita.*
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.UUID
import kotlin.test.*

/** Actual repository SQL/binding/mapping path with a JDBC double. These are NOT PostgreSQL
 * execution, index-performance or transaction-isolation tests; see the opt-in database suite. */
class MarketShoppingActivitySearchContractTest {
    private fun id(n: Int) = UUID.fromString("00000000-0000-0000-0000-${n.toString(16).padStart(12,'0')}")
    private val user = id(1)
    private fun row(n: Int) = mapOf<String,Any?>("command_id" to id(n).toString(),"created_at_millis" to 100L,
        "expected_revision" to 7L,"accepted" to true,"applied_revision" to 8L,"requested_units" to 1,
        "has_basket" to false,"details_recorded" to false)
    private inner class Reads {
        var rows = emptyList<Map<String,Any?>>()
        var opposite = false
        val seen = mutableListOf<Pair<String,List<Any?>>>()
        val db: Connection = proxy(Connection::class.java) { name,args -> when (name) {
            "getAutoCommit" -> false
            "prepareStatement" -> statement(args[0] as String)
            "close" -> null
            else -> error("Unexpected connection operation $name")
        } }
        val repo = MarketShoppingRepository(db,MarketplaceRepository(db) { _,_ -> false })
        private fun statement(raw: String): PreparedStatement {
            val sql = raw.replace(Regex("\\s+")," ").trim()
            assertTrue(sql.startsWith("SELECT "),sql); assertFalse(sql.contains("FOR UPDATE"),sql)
            val bound = mutableMapOf<Int,Any?>()
            return proxy(PreparedStatement::class.java) { name,args -> when (name) {
                "setObject" -> { bound[args[0] as Int] = args[1]; null }
                "executeQuery" -> {
                    assertEquals((1..raw.count { it=='?' }).toSet(),bound.keys,sql)
                    val params = (1..bound.size).map { bound[it] }; assertEquals(user,params.first())
                    seen += sql to params
                    result(if (sql.startsWith("SELECT 1 ")) { if (opposite) listOf(mapOf("1" to 1)) else emptyList() } else rows)
                }
                "close" -> null
                else -> error("Mutation or unexpected statement operation $name")
            } }
        }
    }
    @Test fun latestPageIsUserScopedBoundedAndSummaryOnly() {
        val f=Reads();f.rows=(30 downTo 10).map(::row)
        val result=f.repo.activitySearch(user,MarketShoppingActivitySearchRequest())
        assertEquals(20,result.entries.size);assertTrue(result.hasOlder);assertFalse(result.hasNewer)
        assertTrue(result.isValidActivitySearchPage(user.toString(),result.request))
        val(sql,args)=f.seen.single();assertEquals(listOf(user,21),args)
        assertTrue(sql.endsWith("WHERE user_id=? AND checklist_change IS NULL ORDER BY created_at_millis DESC,command_id DESC LIMIT ?"))
        assertTrue(sql.contains("NULL::jsonb AS activity_details"));assertFalse(sql.contains("session_id"));assertFalse(sql.contains("request_hash"))
    }
    @Test fun olderPageUsesBothCursorPartsAndAnOppositeExistenceRead() {
        val f=Reads();f.rows=(19 downTo 1).map(::row);f.opposite=true
        val request=MarketShoppingActivitySearchRequest(boundary=MarketShoppingActivityCursor(100,id(20).toString()))
        val result=f.repo.activitySearch(user,request)
        assertEquals(19,result.entries.size);assertFalse(result.hasOlder);assertTrue(result.hasNewer)
        assertTrue(result.isValidActivitySearchPage(user.toString(),request))
        assertEquals(listOf(user,100L,id(20),21),f.seen[0].second)
        assertTrue(f.seen[0].first.contains("(created_at_millis,command_id) < (?,?)"))
        assertEquals(listOf(user,100L,id(19)),f.seen[1].second)
        assertTrue(f.seen[1].first.contains("(created_at_millis,command_id) > (?,?) LIMIT 1"))
    }
    @Test fun newerPageTakesNearestAscendingCandidatesThenReturnsDescending() {
        val f=Reads();f.rows=(11..31).map(::row);f.opposite=true
        val request=MarketShoppingActivitySearchRequest(boundary=MarketShoppingActivityCursor(100,id(10).toString()),newer=true)
        val result=f.repo.activitySearch(user,request)
        assertEquals((30 downTo 11).map { id(it).toString() },result.entries.map { it.commandId })
        assertTrue(result.hasOlder && result.hasNewer);assertTrue(result.isValidActivitySearchPage(user.toString(),request))
        assertTrue(f.seen[0].first.endsWith("ORDER BY created_at_millis ASC,command_id ASC LIMIT ?"))
        assertEquals(listOf(user,100L,id(11)),f.seen[1].second)
        assertTrue(f.seen[1].first.contains("(created_at_millis,command_id) < (?,?) LIMIT 1"))
    }
    @Test fun exactReferenceIsCanonicalBoundAndHasNoOffsetOrPrivateJoins() {
        val f=Reads();f.rows=listOf(row(11))
        val request=MarketShoppingActivitySearchRequest(MarketShoppingActivityFilter(commandId=" ${id(11).toString().uppercase()} "))
        val result=f.repo.activitySearch(user,request)
        assertEquals(id(11).toString(),result.request.filter.commandId)
        val(sql,args)=f.seen.single();assertEquals(listOf(user,id(11),21),args)
        assertTrue(sql.contains("WHERE user_id=? AND checklist_change IS NULL AND command_id=?"));assertFalse(sql.contains("OFFSET"));assertFalse(sql.contains("stock_items"))
    }
    @Test fun referenceNotFoundMakesOneReadAndNoWrite() {
        val f=Reads();val page=f.repo.activitySearch(user,MarketShoppingActivitySearchRequest(MarketShoppingActivityFilter(commandId=id(11).toString())))
        assertTrue(page.entries.isEmpty());assertFalse(page.hasOlder || page.hasNewer);assertEquals(1,f.seen.size)
    }
    @Test fun allResultPredicatesSeparateAppliedNoOpAndRejection() {
        for((filter,predicate) in listOf(MARKET_ACTIVITY_RESULT_APPLIED to "accepted=TRUE AND applied_revision>expected_revision",
            MARKET_ACTIVITY_RESULT_REJECTED to "accepted=FALSE",MARKET_ACTIVITY_RESULT_CANCELLED to "accepted=FALSE AND error_key='market.shopping_cancelled'",MARKET_ACTIVITY_RESULT_UNCHANGED to "accepted=TRUE AND applied_revision=expected_revision")) {
            val f=Reads();f.repo.activitySearch(user,MarketShoppingActivitySearchRequest(MarketShoppingActivityFilter(result=filter)))
            assertTrue(f.seen.single().first.contains(predicate));assertEquals(listOf(user,21),f.seen.single().second)
        }
    }
    @Test fun allKindPredicatesAreExclusiveAndShareFiltersOnBackNavigation() {
        for((kind,predicate) in listOf(MARKET_ACTIVITY_BASKET to "basket_change IS NOT NULL",
            MARKET_ACTIVITY_REPLACE to "basket_change IS NULL AND checklist_change IS NULL AND replaced_offer_id IS NOT NULL",
            MARKET_ACTIVITY_REMOVE to "basket_change IS NULL AND checklist_change IS NULL AND replaced_offer_id IS NULL AND requested_units=0",
            MARKET_ACTIVITY_QUANTITY to "basket_change IS NULL AND checklist_change IS NULL AND replaced_offer_id IS NULL AND requested_units>0")) {
            val f=Reads();f.rows=listOf(row(11))
            f.repo.activitySearch(user,MarketShoppingActivitySearchRequest(MarketShoppingActivityFilter(kind=kind),MarketShoppingActivityCursor(100,id(20).toString())))
            assertEquals(2,f.seen.size);assertTrue(f.seen.all { it.first.contains(predicate) })
        }
    }
    @Test fun malformedRequestsCannotReachTheDatabase() {
        for(request in listOf(MarketShoppingActivitySearchRequest(newer=true),
            MarketShoppingActivitySearchRequest(MarketShoppingActivityFilter(result="' OR TRUE--")),
            MarketShoppingActivitySearchRequest(MarketShoppingActivityFilter(commandId="bad")),
            MarketShoppingActivitySearchRequest(boundary=MarketShoppingActivityCursor(-1,id(10).toString())))) {
            val f=Reads();assertEquals("market.activity_search_invalid",assertFailsWith<MarketFailure>{f.repo.activitySearch(user,request)}.key)
            assertTrue(f.seen.isEmpty())
        }
    }
    @Test fun emptyOutOfRangePageDoesNotInventContinuationFlags() {
        val f=Reads();f.opposite=true
        val result=f.repo.activitySearch(user,MarketShoppingActivitySearchRequest(boundary=MarketShoppingActivityCursor(100,id(20).toString())))
        assertTrue(result.entries.isEmpty());assertFalse(result.hasOlder || result.hasNewer);assertEquals(1,f.seen.size)
    }
    private fun result(rows:List<Map<String,Any?>>):ResultSet {
        var index=-1;var wasNull=false
        return proxy(ResultSet::class.java) { name,args -> when(name) {
            "next" -> { index++;index<rows.size }
            "wasNull" -> wasNull
            "close" -> null
            "getString","getLong","getInt","getBoolean" -> {
                val value=rows[index][args[0].toString()];wasNull=value==null
                when(name) {"getString"->value?.toString();"getLong"->(value as? Number)?.toLong()?:0L
                    "getInt"->(value as? Number)?.toInt()?:0;else->value as? Boolean?:false}
            }
            else -> error("Unexpected result operation $name")
        } }
    }
    @Suppress("UNCHECKED_CAST")
    private fun <T> proxy(type:Class<T>,call:(String,Array<out Any?>)->Any?):T=Proxy.newProxyInstance(type.classLoader,arrayOf(type)){_,method,args->
        call(method.name,args?:emptyArray())
    } as T
}

package kz.aita.server.marketplace

import kz.aita.*
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.UUID
import kotlin.test.*

/** Executes the real directory SQL/binding/mapping code against a read-only JDBC double.
 * Actual PostgreSQL semantics and the new index belong to the opt-in database tests. */
class MarketShopDirectoryContractTest {
    private val user = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val shop = "00000000-0000-0000-0000-000000000002"
    private fun shopRow(count: Long = 0) = mapOf<String,Any?>("store_id" to shop,"display_name" to "Shop",
        "city" to "Astana","public_address" to "Door 4","pickup_note" to "Use the public entrance",
        "is_published" to true,"share_branch_availability" to true,"revision" to 2L,"public_offer_count" to count)
    private class Reads(val count: Long, val rows: List<Map<String,Any?>>) {
        val seen = mutableListOf<Pair<String,List<Any?>>>()
        val timeouts = mutableListOf<Int>()
        val db: Connection = proxy(Connection::class.java) { name,args -> when(name) {
            "getAutoCommit" -> false
            "prepareStatement" -> {
                val raw = args[0] as String; val sql = raw.replace(Regex("\\s+")," ").trim()
                assertTrue(sql.startsWith("SELECT "));assertFalse(sql.contains("FOR UPDATE"))
                val bound = sortedMapOf<Int,Any?>()
                proxy(PreparedStatement::class.java) { method,values -> when(method) {
                    "setQueryTimeout" -> { timeouts += values[0] as Int; null }
                    "setObject" -> { bound[values[0] as Int] = values[1]; null }
                    "executeQuery" -> {
                        assertEquals((1..raw.count { it=='?' }).toSet(),bound.keys)
                        seen += sql to bound.values.toList()
                        result(if(sql.startsWith("SELECT count(*) ")) listOf(mapOf("1" to count)) else rows)
                    }
                    "close" -> null
                    else -> error("Unexpected directory statement $method")
                } }
            }
            "close" -> null
            else -> error("Unexpected directory connection operation $name")
        } }
    }
    @Test fun emptyDirectoryStillHasACompleteBoundedReply() {
        val f=Reads(0,emptyList());val result=MarketShopDirectoryRepository(f.db).search(user,MarketShopDirectoryRequest())
        assertTrue(result.isValidShopDirectoryResult(user.toString(),result.request));assertEquals(2,f.seen.size)
        assertEquals(listOf(5,5),f.timeouts);assertEquals(20,f.seen.last().second.last())
    }
    @Test fun publishedEmptyShopMapsOnlyExplicitPublicFields() {
        val f=Reads(1,listOf(shopRow()));val result=MarketShopDirectoryRepository(f.db).search(user,MarketShopDirectoryRequest())
        assertTrue(result.shops.single().storefront.shareBranchAvailability);assertEquals(0L,result.shops.single().publishedOffers);assertEquals(shop,result.shops.single().storefront.storeId)
        assertTrue(result.isValidShopDirectoryResult(user.toString(),result.request))
        val sql=f.seen.last().first
        listOf("supply_price","owner_user","session_id","stock_batches","purchase","note FROM stock").forEach { assertFalse(sql.contains(it),it) }
        assertTrue(sql.contains("l.is_published AND i.is_active"));assertTrue(sql.contains("i.store_id IN (s.id,coalesce(s.parent_store_id,s.id))"))
    }
    @Test fun punctuationAndTermsAreBoundLiterallyNotInterpolated() {
        val f=Reads(0,emptyList());val request=MarketShopDirectoryRequest("  50% _shop 'quote  "," Astana ",40)
        MarketShopDirectoryRepository(f.db).search(user,request)
        for((sql,args) in f.seen) {
            assertFalse(sql.contains("50%"));assertFalse(sql.contains("'_shop'"));assertFalse(sql.contains("'quote"))
            val publicArgs = if(sql.startsWith("SELECT count(*)")) args else args.drop(1).also { assertEquals(user,args.first()) }
            assertEquals(listOf("50%","_shop","'quote","Astana"),publicArgs.drop(2).take(4))
            assertEquals(publicArgs[0],publicArgs[1]);assertTrue((publicArgs[0] as Long)>0)
            assertEquals(3,Regex("strpos\\(lower\\(f.display_name").findAll(sql).count())
            assertTrue(sql.contains("strpos(lower(f.city),lower(?))>0"));assertFalse(sql.contains(" ILIKE "))
        }
        assertEquals(40,f.seen.last().second.last())
    }
    @Test fun countAndWindowShareThePhysicalLocationPublicationAndSubscriptionPredicate() {
        val f=Reads(0,emptyList());MarketShopDirectoryRepository(f.db).search(user,MarketShopDirectoryRequest())
        val predicate=MarketplacePublicVisibility.shopPredicate.replace(Regex("\\s+")," ").trim()
        for((sql,_) in f.seen) {
            assertTrue(sql.contains(predicate));assertTrue(sql.contains("e.store_id=f.store_id"))
            assertTrue(sql.contains("s.parent_store_id IS NULL"))
            assertTrue(sql.contains("e.current_period_end_millis IS NULL AND NOT e.auto_renew"))
        }
    }
    @Test fun deterministicOrderingHasUniqueTieBreakAndNoOffset() {
        val f=Reads(1,listOf(shopRow(3)));MarketShopDirectoryRepository(f.db).search(user,MarketShopDirectoryRequest(limit=200))
        val sql=f.seen.last().first
        assertTrue(sql.endsWith("ORDER BY lower(f.display_name) COLLATE \"C\",f.store_id LIMIT ?"))
        assertFalse(sql.contains("OFFSET"));assertEquals(200,f.seen.last().second.last())
    }
    @Test fun invalidRequestIsRejectedBeforeAnyStatement() {
        for(request in listOf(MarketShopDirectoryRequest(limit=201),MarketShopDirectoryRequest(text="x".repeat(121)),MarketShopDirectoryRequest(city="\u0000"))) {
            val f=Reads(0,emptyList());assertEquals("market.shops_invalid",assertFailsWith<MarketFailure> {
                MarketShopDirectoryRepository(f.db).search(user,request)
            }.key);assertTrue(f.seen.isEmpty())
        }
    }
    companion object {
        private fun result(rows:List<Map<String,Any?>>):ResultSet {
            var index=-1
            return proxy(ResultSet::class.java) { name,args -> when(name) {
                "next" -> { index++;index<rows.size };"close" -> null
                "getString" -> rows[index][args[0].toString()]?.toString()
                "getLong" -> (rows[index][args[0].toString()] as? Number)?.toLong()?:0L
                "getBoolean" -> rows[index][args[0].toString()] as? Boolean?:false
                else -> error("Unexpected result method $name")
            } }
        }
        @Suppress("UNCHECKED_CAST")
        private fun <T> proxy(type:Class<T>, body:(String,Array<out Any?>)->Any?):T =
            Proxy.newProxyInstance(type.classLoader,arrayOf(type)) { _,method,args -> body(method.name,args?:emptyArray()) } as T
    }
}

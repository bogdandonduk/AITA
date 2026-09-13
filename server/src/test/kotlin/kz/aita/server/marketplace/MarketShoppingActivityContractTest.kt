package kz.aita.server.marketplace

import kz.aita.*
import kotlinx.serialization.encodeToString
import java.lang.reflect.Proxy
import java.security.MessageDigest
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.UUID
import kotlin.test.*

/** Actual repository SQL construction/mapping with a deliberately read-only JDBC double. This
 * catches missing bindings, accidental writes/locks and summary/detail leaks. PostgreSQL itself,
 * JSONB evaluation and transaction isolation are covered by the separate opt-in database suite.
 */
class MarketShoppingActivityContractTest {
    private fun id(n:Int)=UUID.fromString("00000000-0000-0000-0000-${n.toString(16).padStart(12,'0')}")
    private val account=id(1)
    private val command=MarketShoppingCommand(id(900).toString(),7,id(10).toString(),0)
    private val basis=MarketShoppingBasis("04006381333931","KZT","piece",1.0)
    private val line=MarketShoppingLine(id(10).toString(),id(20).toString(),"Historical public name","Historical shop",2,basis)
    private fun hash(input:MarketShoppingCommand)=MessageDigest.getInstance("SHA-256")
        .digest(jsonBase.encodeToString(input).toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it.toInt() and 255)}
    private fun entryRow(n:Int=900)=mapOf<String,Any?>("command_id" to id(n).toString(),
        "created_at_millis" to 500L,"expected_revision" to 7L,"accepted" to true,
        "applied_revision" to 8L,"error_key" to null,"requested_units" to 0,
        "replaced_offer_id" to null,"reviewed_subtotal_minor" to null,"has_basket" to false,
        "basket_currency" to null,"basket_subtotal" to null,"basket_lines" to null,"basket_changes" to 0,
        "details_recorded" to false,"activity_details" to null,"detail_currency" to null,"preview_title" to null)

    private inner class ReadDb {
        val seen=mutableListOf<Pair<String,List<Any?>>>()
        var rows=emptyList<Map<String,Any?>>()
        var record:Map<String,Any?>?=null
        var revision=9L
        var currentLines=emptyList<MarketShoppingLine>()
        val db:Connection = proxy(Connection::class.java) { name,args -> when(name) {
            "getAutoCommit"->false
            "prepareStatement"->statement(args[0] as String)
            "close"->null
            else->error("Unexpected connection operation: $name")
        } }
        val repository=MarketShoppingRepository(db,MarketplaceRepository(db){_,_->false})
        private fun statement(raw:String):PreparedStatement {
            val sql=raw.replace(Regex("\\s+")," ").trim()
            assertTrue(sql.startsWith("SELECT "),sql)
            assertFalse(sql.contains("FOR UPDATE"),sql)
            val parameters=mutableMapOf<Int,Any?>()
            return proxy(PreparedStatement::class.java) { name,args -> when(name) {
                "setObject"->{parameters[args[0] as Int]=args[1];null}
                "executeQuery"->{
                    assertEquals((1..raw.count{it=='?'}).toSet(),parameters.keys,sql)
                    val bound=(1..parameters.size).map{parameters[it]};seen.add(sql to bound)
                    assertEquals(account,bound.first(),sql)
                    when {
                        sql.startsWith("SELECT * FROM buyer_shopping_commands")->{assertEquals(id(900),bound[1]);result(record?.let{listOf(it)}?:emptyList())}
                        sql.startsWith("SELECT revision FROM buyer_shopping_lists")->result(listOf(mapOf("1" to revision)))
                        sql.startsWith("SELECT * FROM buyer_shopping_lines")->result(currentLines.map{mapOf(
                            "offer_id" to it.offerId,"store_id" to it.storeId,"public_title" to it.title,
                            "public_shop_name" to it.shopName,"units" to it.units,"basis" to jsonBase.encodeToString(it.basis),
                            "unit_name" to jsonBase.encodeToString(it.unitName),"updated_at_millis" to it.updatedAtMillis)})
                        else->{assertTrue(sql.contains("FROM buyer_shopping_commands WHERE user_id=?"));result(rows)}
                    }
                }
                "close"->null
                else->error("Mutation or unexpected statement operation: $name")
            } }
        }
    }
    @Test fun missingResultMakesOnlyOneReadAndNeverCreatesListOrCommand() {
        val f=ReadDb();val answer=f.repository.lookup(account,command)
        assertNull(answer.outcome);assertEquals(command.commandId,answer.commandId);assertEquals(1,f.seen.size)
    }
    @Test fun recordedAcceptedLookupReturnsCurrentListNotHistoricalSnapshot() {
        val f=ReadDb();f.record=mapOf("request_hash" to hash(command),"accepted" to true,"applied_revision" to 8L)
        val answer=assertNotNull(f.repository.lookup(account,command).outcome)
        assertTrue(answer.accepted&&answer.replayed);assertEquals(8L,answer.appliedRevision)
        assertEquals(9L,answer.snapshot.revision);assertEquals(3,f.seen.size)
    }
    @Test fun recordedRejectionIsNotAPermissionToApplyTheCommand() {
        val f=ReadDb();f.record=mapOf("request_hash" to hash(command),"accepted" to false,
            "applied_revision" to null,"error_key" to "market.shopping_changed")
        val answer=assertNotNull(f.repository.lookup(account,command).outcome)
        assertFalse(answer.accepted);assertNull(answer.appliedRevision);assertEquals("market.shopping_changed",answer.errorKey)
    }
    @Test fun sameIdDifferentPayloadIsRejectedBeforeLoadingTheCurrentList() {
        val f=ReadDb();f.record=mapOf("request_hash" to "not-the-original-hash","accepted" to true,"applied_revision" to 8L)
        assertEquals(409,assertFailsWith<MarketFailure>{f.repository.lookup(account,command)}.status)
        assertEquals(1,f.seen.size)
    }
    @Test fun invalidActivityWindowNeverQueriesTheDatabase() {
        val f=ReadDb()
        assertFailsWith<MarketFailure>{f.repository.activity(account,MarketShoppingActivityRequest(201))}
        assertTrue(f.seen.isEmpty())
    }
    @Test fun historyWindowHasDeterministicOrderAndOneLookaheadRow() {
        val f=ReadDb();f.rows=(920 downTo 900).map{entryRow(it)}
        val page=f.repository.activity(account,MarketShoppingActivityRequest())
        assertEquals(20,page.entries.size);assertTrue(page.hasMore)
        assertTrue(page.isValidShoppingActivityPage(account.toString(),MarketShoppingActivityRequest()))
        val(sql,bound)=f.seen.single();assertTrue(sql.contains("ORDER BY created_at_millis DESC,command_id DESC LIMIT ?"))
        assertEquals(listOf(account,21),bound)
        assertTrue(sql.contains("NULL::jsonb AS activity_details"));assertFalse(sql.contains("request_hash"));assertFalse(sql.contains("session_id"))
    }
    @Test fun basketSummaryRetainsCountsButDoesNotCarryEveryHistoricalLine() {
        val f=ReadDb();f.rows=listOf(entryRow()+mapOf("has_basket" to true,"basket_currency" to "KZT",
            "basket_subtotal" to 599L,"basket_lines" to 2,"basket_changes" to 1,"details_recorded" to true,"preview_title" to line.title))
        val entry=f.repository.activity(account,MarketShoppingActivityRequest()).entries.single()
        assertTrue(entry.isValidShoppingActivityEntry());assertEquals(MARKET_ACTIVITY_BASKET,entry.kind)
        assertEquals(1,entry.changedLines);assertEquals(2,entry.reviewedLines);assertEquals(599L,entry.reviewedSubtotalMinor)
        assertTrue(entry.detailsRecorded);assertNull(entry.details)
    }
    @Test fun detailIsUserAndCommandScopedAndReadsStoredPublicLabels() {
        val f=ReadDb();val details=MarketShoppingActivityDetails(listOf(MarketShoppingActivityLine(before=line)))
        f.rows=listOf(entryRow()+mapOf("details_recorded" to true,"activity_details" to jsonBase.encodeToString(details),"preview_title" to line.title))
        val answer=f.repository.activityDetail(account,command.commandId)
        assertEquals(details,answer.details);assertTrue(answer.isValidShoppingActivityEntry())
        val(sql,bound)=f.seen.single();assertTrue(sql.endsWith("WHERE user_id=? AND command_id=?"))
        assertFalse(sql.contains("stock_items"));assertEquals(listOf(account,id(900)),bound)
    }
    @Test fun absentOrOtherOwnerDetailHasTheSameNotFoundResponse() {
        val f=ReadDb();val failure=assertFailsWith<MarketFailure>{f.repository.activityDetail(account,command.commandId)}
        assertEquals(404,failure.status);assertEquals("market.shopping_activity_missing",failure.key)
    }
    @Test fun legacyRejectedCommandWithMaximumRevisionRemainsReadable() {
        val f=ReadDb();f.rows=listOf(entryRow()+mapOf("accepted" to false,"applied_revision" to null,
            "expected_revision" to Long.MAX_VALUE,"error_key" to "market.shopping_changed"))
        val entry=f.repository.activity(account,MarketShoppingActivityRequest()).entries.single()
        assertTrue(entry.isValidShoppingActivityEntry());assertFalse(entry.detailsRecorded);assertNull(entry.details)
    }
    @Test fun legacyReplacementDoesNotInventCurrencyOrBeforeAfterLabels() {
        val f=ReadDb();f.rows=listOf(entryRow()+mapOf("replaced_offer_id" to id(11).toString(),
            "requested_units" to 2,"reviewed_subtotal_minor" to 599L))
        val entry=f.repository.activity(account,MarketShoppingActivityRequest()).entries.single()
        assertTrue(entry.isValidShoppingActivityEntry());assertEquals(MARKET_ACTIVITY_REPLACE,entry.kind)
        assertNull(entry.reviewedCurrency);assertNull(entry.details);assertFalse(entry.detailsRecorded)
    }
    private fun result(rows:List<Map<String,Any?>>):ResultSet {
        var index=-1;var wasNull=false
        return proxy(ResultSet::class.java) { name,args->when(name) {
            "next"->{index++;index<rows.size}
            "wasNull"->wasNull
            "close"->null
            "getString","getLong","getInt","getBoolean"->{val value=rows[index][args[0].toString()];wasNull=value==null
                when(name){"getString"->value?.toString();"getLong"->(value as? Number)?.toLong()?:0L
                    "getInt"->(value as? Number)?.toInt()?:0;else->value as? Boolean?:false}}
            else->error("Unexpected result operation: $name")
        } }
    }
    @Suppress("UNCHECKED_CAST")
    private fun <T> proxy(type:Class<T>,call:(String,Array<out Any?>)->Any?):T=Proxy.newProxyInstance(type.classLoader,arrayOf(type)){_,m,args->
        call(m.name,args?:emptyArray())
    } as T
}

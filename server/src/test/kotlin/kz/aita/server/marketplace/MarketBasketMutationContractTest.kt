package kz.aita.server.marketplace

import kz.aita.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.util.UUID
import kotlin.test.*

/** Executes the real repository mutation/query sequence with an explicit JDBC test double and
 * catalogue quotes. This is NOT PostgreSQL locking/isolation/rollback or retail-pricing coverage;
 * those scenarios are in the separate opt-in database suite. Every placeholder must be bound.
 */
class MarketBasketMutationContractTest {
    private fun id(n:Int)=UUID.fromString("00000000-0000-0000-0000-${n.toString(16).padStart(12,'0')}")
    private class Record(val hash:String,val accepted:Boolean,val error:String?,val revision:Long?)
    private inner class Fixture {
        val user=id(1)
        var revision=7L
        var fence:UUID?=null
        var lineWrites=0
        var quoteCalls=0
        var recordWrites=0
        var recordedDetails:MarketShoppingActivityDetails?=null
        var failAtWrite:Int?=null
        val now=System.currentTimeMillis()
        val records=mutableMapOf<UUID,Record>()
        val rows=linkedMapOf<String,MarketShoppingLine>()
        val offers=mutableMapOf<String,MarketOffer>()
        val created=mutableMapOf<String,Long>()
        val command:MarketShoppingCommand
        init {
            val selection=(1..2).map { n ->
                val gtin=if(n==1) "04006381333931" else "05901234123457"
                val source=id(100+n).toString(); val target=id(200+n).toString()
                fun offer(key:String,store:Int,price:Long)=MarketOffer(key,
                    MarketStorefront(id(store).toString(),"Shop $store","Astana","Door",published=true,revision=1),
                    "Product $n",gtin=gtin,priceMinor=price,currencyCode="KZT",pricedAmount=1.0,unitId="piece",
                    availability=MARKET_AVAILABILITY_RECORDED,checkedAtMillis=now,sourceUpdatedAtMillis=1)
                val old=offer(source,10+n,1000L); val new=offer(target,20,100L*n)
                offers[source]=old;offers[target]=new
                rows[source]=MarketShoppingLine(source,old.storefront.storeId,old.title,old.storefront.displayName,
                    n,requireNotNull(old.shoppingBasis()))
                created[source]=n.toLong()
                MarketBasketReviewedLine(source,target,new.storefront.storeId,n,requireNotNull(new.shoppingBasis()),100L*n*n,new.title,1)
            }
            command=MarketShoppingCommand(id(900).toString(),7,"",0,basketChange=MarketBasketChange("KZT","",MARKET_BASKET_ONE_SHOP,now,500,selection))
        }
        fun connection():Connection = proxy(Connection::class.java) { name,args -> when(name) {
            "getAutoCommit" -> false
            "prepareStatement" -> statement(args[0] as String)
            "close" -> null
            else -> error("Unexpected connection operation: $name")
        } }
        private fun statement(sql:String):PreparedStatement {
            val text=sql.replace(Regex("\\s+")," ").trim()
            val args=mutableMapOf<Int,Any?>()
            fun bound():List<Any?> {
                assertEquals((1..sql.count { it=='?' }).toSet(),args.keys,text)
                return (1..args.size).map { args[it] }
            }
            return proxy(PreparedStatement::class.java) { name,values -> when(name) {
                "setObject" -> { args[values[0] as Int]=values[1];null }
                "setQueryTimeout" -> { assertTrue(values[0] as Int in 1..5); null }
                "executeQuery" -> result(query(text,bound()))
                "executeUpdate" -> update(text,bound())
                "close" -> null
                else -> error("Unexpected statement operation: $name")
            } }
        }
        private fun query(sql:String,args:List<Any?>):List<Map<String,Any?>> = when {
            sql.startsWith("SELECT id FROM users") -> if(args[0]==user) listOf(mapOf("1" to user.toString())) else emptyList()
            sql.startsWith("SELECT revision FROM buyer_shopping_lists") -> listOf(mapOf("1" to revision))
            sql.startsWith("SELECT * FROM buyer_shopping_commands") -> records[args[1]]?.let {
                listOf(mapOf("request_hash" to it.hash,"accepted" to it.accepted,"error_key" to it.error,"applied_revision" to it.revision))
            } ?: emptyList()
            sql.startsWith("SELECT * FROM buyer_shopping_lines") -> rows.values.sortedBy { created[it.offerId] }.map {
                mapOf("offer_id" to it.offerId,"store_id" to it.storeId,"public_title" to it.title,
                    "public_shop_name" to it.shopName,"units" to it.units,"basis" to jsonBase.encodeToString(it.basis),
                    "unit_name" to jsonBase.encodeToString(it.unitName),"updated_at_millis" to it.updatedAtMillis)
            }
            else -> error("Unexpected query: $sql")
        }
        private fun update(sql:String,args:List<Any?>):Int = when {
            sql.startsWith("INSERT INTO buyer_shopping_lists") -> 0
            sql.startsWith("UPDATE buyer_shopping_lines SET offer_id") -> {
                lineWrites++
                if(lineWrites==failAtWrite) throw SQLException("Injected second update failure","XX000")
                val original=args[7].toString();val old=rows.remove(original) ?: error("Source missing")
                val target=args[0].toString();check(target !in rows)
                rows[target]=old.copy(offerId=target,storeId=args[1].toString(),title=args[2] as String,shopName=args[3] as String,updatedAtMillis=args[5] as Long)
                created[target]=created.remove(original)!!;1
            }
            sql.startsWith("UPDATE buyer_shopping_lists SET revision") -> { revision=args[0] as Long;1 }
            sql.startsWith("UPDATE buyer_shopping_lists SET last_command_id") -> { fence=args[0] as UUID;1 }
            sql.startsWith("INSERT INTO buyer_shopping_commands") -> {
                assertTrue(sql.contains("basket_change")); assertTrue(sql.contains("activity_details")); val commandId=args[1] as UUID
                check(commandId !in records)
                if (sql.contains("FALSE,?,NULL,")) {
                    assertEquals(12,args.size);assertEquals("market.shopping_cancelled",args[7])
                    assertTrue(sql.endsWith("?::jsonb,NULL)"));recordedDetails=null
                    records[commandId]=Record(args[2] as String,false,args[7] as String,null)
                } else {
                    recordedDetails=(args[10] as String?)?.let { jsonBase.decodeFromString<MarketShoppingActivityDetails>(it) }
                    records[commandId]=Record(args[2] as String,args[5] as Boolean,args[6] as String?,args[7] as Long?)
                }
                recordWrites++;1
            }
            else -> error("Unexpected update: $sql")
        }
        private fun quotes(@Suppress("UNUSED_PARAMETER") account:UUID, lines:List<MarketShoppingLine>, time:Long):List<MarketShoppingQuotedLine> {
            quoteCalls++
            return lines.map { line ->
                val offer=offers[line.offerId]?.copy(checkedAtMillis=time)
                if(offer==null) MarketShoppingQuotedLine(line)
                else MarketShoppingQuotedLine(line,offer,offer.priceMinor,marketShoppingSubtotal(offer.priceMinor,line.units),MARKET_QUOTE_ESTIMATED)
            }
        }
        fun cancel(input:MarketShoppingCommand=command):MarketShoppingOutcome {
            val db=connection();return MarketShoppingRepository(db,MarketplaceRepository(db){_,_->false},::quotes).cancel(user,null,input)
        }
        fun apply(input:MarketShoppingCommand=command):MarketShoppingOutcome {
            val db=connection();return MarketShoppingRepository(db,MarketplaceRepository(db){_,_->false},::quotes).applyBasket(user,null,input)
        }
    }
    @Test fun checksAllQuotesThenWritesEveryLineAndOneOutcome() {
        val f=Fixture();val created=f.created.values.sorted();val result=f.apply()
        assertTrue(result.accepted);assertEquals(8L,f.revision);assertEquals(2,f.lineWrites);assertEquals(1,f.recordWrites)
        assertEquals(id(900),f.fence);assertEquals(created,f.created.values.sorted());assertTrue(f.command.matchesBasketOutcome(result))
        val details=assertNotNull(f.recordedDetails);assertEquals(2,details.lines.size)
        assertEquals(f.command.basketChange!!.lines.map { it.sourceOfferId },details.lines.map { it.before?.offerId })
        assertEquals(f.command.basketChange!!.lines.map { it.targetOfferId },details.lines.map { it.after?.offerId })
        assertTrue(details.lines.all { it.before?.units==it.after?.units && it.before?.basis==it.after?.basis })
    }
    @Test fun secondTargetPriceFailureHasZeroLineWrites() {
        val f=Fixture();val target=f.command.basketChange!!.lines.last().targetOfferId
        f.offers[target]=f.offers.getValue(target).copy(priceMinor=999)
        val result=f.apply();assertFalse(result.accepted);assertEquals("market.basket_apply_price_changed",result.errorKey)
        assertEquals(0,f.lineWrites);assertEquals(7L,f.revision);assertEquals(1,f.recordWrites);assertEquals(id(900),f.fence);assertNull(f.recordedDetails)
    }
    @Test fun withdrawnTargetNeverProducesFirstReplacementOnly() {
        val f=Fixture();f.offers.remove(f.command.basketChange!!.lines.last().targetOfferId)
        assertFalse(f.apply().accepted);assertEquals(0,f.lineWrites);assertEquals(2,f.rows.size)
    }
    @Test fun recordedReplaySkipsReviewChecksAndWrites() {
        val f=Fixture();val first=f.apply();f.offers.clear();val replay=f.apply()
        assertTrue(first.accepted && replay.accepted && replay.replayed);assertEquals(2,f.lineWrites);assertEquals(1,f.recordWrites)
    }
    @Test fun rejectedReviewCannotLaterBecomeSuccessWithSameIdentity() {
        val f=Fixture();val target=f.command.basketChange!!.lines.last().targetOfferId;val old=f.offers.remove(target)!!
        assertFalse(f.apply().accepted);f.offers[target]=old
        val retry=f.apply();assertFalse(retry.accepted);assertTrue(retry.replayed);assertEquals(0,f.lineWrites);assertEquals(1,f.recordWrites)
    }
    @Test fun sameIdWithDifferentContentsIsNotAnotherMutation() {
        val f=Fixture();f.apply();val altered=f.command.copy(basketChange=f.command.basketChange!!.copy(city="Astana"))
        assertEquals("market.shopping_command_mismatch",assertFailsWith<MarketFailure> { f.apply(altered) }.key)
        assertEquals(2,f.lineWrites);assertEquals(1,f.recordWrites)
    }
    @Test fun staleListRejectsBeforeRequotingTargets() {
        val f=Fixture();f.revision=8L;val result=f.apply()
        assertFalse(result.accepted);assertEquals("market.shopping_changed",result.errorKey)
        assertEquals(0,f.lineWrites);assertEquals(1,f.quoteCalls) // Returned snapshot only.
    }
    @Test fun expiredNewCommandIsRecordedOnceAndReplayRemainsPossible() {
        val f=Fixture();val expired=f.command.copy(basketChange=f.command.basketChange!!.copy(checkedAtMillis=1))
        val result=f.apply(expired);assertEquals("market.basket_apply_expired",result.errorKey)
        assertTrue(f.apply(expired).replayed);assertEquals(0,f.lineWrites);assertEquals(1,f.recordWrites)
    }
    @Test fun aNewStorefrontRevisionRequiresAnotherReview() {
        val f=Fixture();val target=f.command.basketChange!!.lines.first().targetOfferId
        val offer=f.offers.getValue(target);f.offers[target]=offer.copy(storefront=offer.storefront.copy(revision=2))
        assertEquals("market.basket_apply_offer_changed",f.apply().errorKey);assertEquals(0,f.lineWrites)
    }
    @Test fun lineWriteFailureEscapesForOuterTransactionRollbackAndNoRecordedSuccess() {
        val f=Fixture();f.failAtWrite=2
        assertFailsWith<SQLException>{f.apply()};assertEquals(0,f.recordWrites)
        // This fake deliberately DOES NOT roll back. The PostgreSQL injected-trigger test asserts it.
        assertEquals(7L,f.revision);assertNull(f.fence)
    }
    @Test fun duplicateTargetShapeIsRejectedBeforeAnyDatabaseCall() {
        val f=Fixture();val basket=f.command.basketChange!!
        val invalid=f.command.copy(basketChange=basket.copy(lines=basket.lines.map { it.copy(targetOfferId=basket.lines.first().targetOfferId) }))
        assertFailsWith<MarketFailure>{f.apply(invalid)};assertEquals(0,f.recordWrites);assertEquals(0,f.quoteCalls)
    }

    @Test fun cancellationRecordsOneRejectionAndDoesNotChangeListIntent() {
        val f=Fixture();val before=f.rows.toMap();val result=f.cancel()
        assertFalse(result.accepted);assertEquals("market.shopping_cancelled",result.errorKey)
        assertEquals(7L,f.revision);assertEquals(before,f.rows);assertEquals(0,f.lineWrites)
        assertEquals(1,f.recordWrites);assertEquals(id(900),f.fence);assertNull(f.recordedDetails)
    }
    @Test fun lateApplyCanOnlyReplayTheCancelledIdentity() {
        val f=Fixture();f.cancel();val late=f.apply()
        assertFalse(late.accepted);assertTrue(late.replayed);assertEquals("market.shopping_cancelled",late.errorKey)
        assertEquals(0,f.lineWrites);assertEquals(1,f.recordWrites);assertEquals(7L,f.revision)
    }
    @Test fun cancellationCannotUndoAnAlreadyAppliedBasket() {
        val f=Fixture();val first=f.apply();val before=f.rows.toMap();val result=f.cancel()
        assertTrue(result.accepted && result.replayed);assertEquals(first.appliedRevision,result.appliedRevision)
        assertEquals(before,f.rows);assertEquals(2,f.lineWrites);assertEquals(1,f.recordWrites)
    }
    @Test fun cancelledIdRejectsDifferentPayloadWithoutNewRecord() {
        val f=Fixture();f.cancel()
        assertEquals("market.shopping_command_mismatch",assertFailsWith<MarketFailure> {
            f.cancel(f.command.copy(basketChange=f.command.basketChange!!.copy(city="Elsewhere")))
        }.key)
        assertEquals(1,f.recordWrites);assertEquals(0,f.lineWrites)
    }
    @Test fun cancellationDoesNotRevalidateExpiredReviewOrMissingTarget() {
        val f=Fixture();f.offers.clear()
        val old=f.command.copy(basketChange=f.command.basketChange!!.copy(checkedAtMillis=1))
        assertEquals("market.shopping_cancelled",f.cancel(old).errorKey)
        assertEquals(0,f.lineWrites);assertEquals(1,f.recordWrites)
        assertTrue(f.cancel(old).replayed);assertEquals(1,f.recordWrites)
    }
    @Test fun cancellationAlsoRecordsLegacyAndReplacementShapesWithoutApplyingThem() {
        for(replacement in listOf(false,true)) {
            val f=Fixture();val reviewed=f.command.basketChange!!.lines.first()
            val input=MarketShoppingCommand(id(900).toString(),7,reviewed.targetOfferId,reviewed.units,reviewed.basis,
                replaceOfferId=reviewed.sourceOfferId.takeIf { replacement },reviewedSubtotalMinor=reviewed.reviewedSubtotalMinor.takeIf { replacement })
            assertEquals("market.shopping_cancelled",f.cancel(input).errorKey)
            assertEquals(7L,f.revision);assertEquals(0,f.lineWrites);assertEquals(1,f.recordWrites)
        }
    }

    private fun result(rows:List<Map<String,Any?>>):ResultSet {
        var index=-1;var wasNull=false
        return proxy(ResultSet::class.java) { name,args -> when(name) {
            "next" -> { index++;index<rows.size }
            "wasNull" -> wasNull
            "close" -> null
            "getString", "getLong", "getInt", "getBoolean" -> {
                val value=rows[index][args[0].toString()];wasNull=value==null
                when(name) { "getString"->value?.toString();"getLong"->(value as? Number)?.toLong()?:0L
                    "getInt"->(value as? Number)?.toInt()?:0;else->value as? Boolean?:false }
            }
            else -> error("Unexpected result operation: $name")
        } }
    }
    @Suppress("UNCHECKED_CAST")
    private fun <T> proxy(type:Class<T>, call:(String,Array<out Any?>)->Any?):T = Proxy.newProxyInstance(type.classLoader,arrayOf(type)) { _,method,args ->
        call(method.name,args?:emptyArray())
    } as T
}

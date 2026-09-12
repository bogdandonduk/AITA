package kz.aita.server.marketplace

import kz.aita.*
import kotlinx.serialization.encodeToString
import org.junit.Assume.assumeTrue
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.Properties
import java.util.UUID
import kotlin.test.*

/** Opt-in PostgreSQL repository tests. Never point at production.
 * Uses actual V46/V101/V102/V104/V105/V106 SQL with minimal prerequisite inventory tables, not the entire
 * migration history or the Ktor authentication pipeline. Every test gets its own random schema.
 */
class MarketplaceRepositoryDatabaseTest {
    private data class Fixture(val url:String,val props:Properties,val schema:String,
        val owner:UUID=UUID.randomUUID(),val buyer:UUID=UUID.randomUUID(),val root:UUID=UUID.randomUUID(),
        val branch:UUID=UUID.randomUUID(),val sibling:UUID=UUID.randomUUID(),val item:UUID=UUID.randomUUID())
    private fun exec(c:Connection,sql:String)=c.createStatement().use{it.execute(sql)}
    private fun scalar(c:Connection,sql:String)=c.createStatement().use{s->s.executeQuery(sql).use{r->r.next();r.getString(1)}}
    private fun resource(name:String)=requireNotNull(javaClass.getResourceAsStream("/db/migration/$name")).bufferedReader().use{it.readText()}
    private fun fixture(block:(Fixture,Connection)->Unit) {
        val url=System.getenv("AITA_MARKET_TEST_DB_URL").orEmpty()
        assumeTrue("Set AITA_MARKET_TEST_DB_URL to a separately provisioned aita_test_* database",url.isNotBlank())
        require(url.startsWith("jdbc:postgresql:"))
        val props=Properties().apply {
            System.getenv("AITA_MARKET_TEST_DB_USER")?.let{setProperty("user",it)}
            System.getenv("AITA_MARKET_TEST_DB_PASSWORD")?.let{setProperty("password",it)}
            setProperty("connectTimeout","5");setProperty("socketTimeout","15");setProperty("ApplicationName","aita-marketplace-test")
        }
        DriverManager.getConnection(url,props).use{c->
            require(scalar(c,"SELECT current_database()").startsWith("aita_test_")){"Refusing a non-test database"}
            val f=Fixture(url,props,"aita_market_"+UUID.randomUUID().toString().replace("-",""))
            exec(c,"CREATE SCHEMA ${f.schema}")
            try {
                exec(c,"SET search_path TO ${f.schema},public")
                exec(c,"""CREATE TABLE users(id UUID PRIMARY KEY,is_active BOOLEAN NOT NULL DEFAULT TRUE,country_locale TEXT NOT NULL DEFAULT 'kz');
                    CREATE TABLE stores(id UUID PRIMARY KEY,parent_store_id UUID REFERENCES stores(id),owner_user_ids JSONB NOT NULL,
                        country_locales JSONB NOT NULL DEFAULT '["kz"]',is_active BOOLEAN NOT NULL DEFAULT TRUE);
                    CREATE TABLE transactions(store_id UUID,time_millis BIGINT);
                    CREATE TABLE stock_items(id UUID PRIMARY KEY,store_id UUID NOT NULL,created_at_millis BIGINT NOT NULL DEFAULT 0,
                        updated_at_millis BIGINT NOT NULL DEFAULT 0,barcodes JSONB NOT NULL DEFAULT '[]',barcode_models JSONB NOT NULL DEFAULT '[]',
                        sale_prices JSONB NOT NULL DEFAULT '[]',promotions JSONB NOT NULL DEFAULT '[]',active_shelf_batch_id UUID,
                        category_ids JSONB NOT NULL DEFAULT '[]',is_active BOOLEAN NOT NULL DEFAULT TRUE,note TEXT,supply_prices JSONB NOT NULL DEFAULT '[]');
                    CREATE TABLE stock_batches(id UUID PRIMARY KEY,store_id UUID NOT NULL,goods_item_id UUID NOT NULL REFERENCES stock_items(id),
                        created_at_millis BIGINT NOT NULL DEFAULT 0,updated_at_millis BIGINT NOT NULL DEFAULT 0,quantity JSONB NOT NULL,
                        sale_price_override JSONB,expiration_date_millis BIGINT,discounts JSONB NOT NULL DEFAULT '[]',promotions JSONB NOT NULL DEFAULT '[]',
                        shelf_priority INTEGER NOT NULL DEFAULT 0,status TEXT NOT NULL DEFAULT 'Delivered',is_active BOOLEAN NOT NULL DEFAULT TRUE);
                    INSERT INTO users(id) VALUES ('${f.owner}'),('${f.buyer}');
                    INSERT INTO stores(id,owner_user_ids) VALUES ('${f.root}','["${f.owner}"]');
                    INSERT INTO stores(id,parent_store_id,owner_user_ids) VALUES ('${f.branch}','${f.root}','[]'),('${f.sibling}','${f.root}','[]');
                    INSERT INTO stock_items(id,store_id,barcodes,sale_prices,note,supply_prices) VALUES
                        ('${f.item}','${f.root}','["4006381333931"]','[{"price":"199.99","currency":"KZT","supplierId":""}]',
                            'NEVER-PUBLIC-NOTE','[{"price":"77.77","currency":"KZT","supplierId":"NEVER-PUBLIC-SUPPLIER"}]');
                """)
                listOf("V46__paging_user_finances_and_store_subscriptions.sql","V101__per_location_subscriptions_and_promocodes.sql",
                    "V102__single_use_promo_archive.sql","V104__opt_in_buyer_shop_windows.sql","V105__buyer_shopping_lists.sql","V106__buyer_reviewed_offer_replacements.sql").forEach{exec(c,resource(it))}
                listOf(f.root,f.branch,f.sibling).forEach{store->exec(c,"""INSERT INTO store_subscription_states
                    (store_id,owner_user_id,plan_id,status,access_kind,current_period_start_millis,auto_renew,renewal_price_minor)
                    VALUES ('$store','${f.owner}','internal_lifetime','active','lifetime',1,FALSE,0)""")}
                block(f,c)
            } finally {exec(c,"SET search_path TO public");exec(c,"DROP SCHEMA ${f.schema} CASCADE")}
        }
    }
    private fun <T> tx(f:Fixture,block:(MarketplaceRepository)->T):T=DriverManager.getConnection(f.url,f.props).use{c->
        c.autoCommit=false;c.transactionIsolation=Connection.TRANSACTION_REPEATABLE_READ
        try {
            exec(c,"SET LOCAL search_path TO ${f.schema},public");exec(c,"SET LOCAL statement_timeout='8s'");exec(c,"SET LOCAL lock_timeout='5s'")
            block(MarketplaceRepository(c){user,store->user==f.owner && store in setOf(f.root,f.branch,f.sibling)}).also{c.commit()}
        } catch(failure:Throwable) {c.rollback();throw failure}
    }
    private fun <T> shoppingTx(f:Fixture, block:(MarketShoppingRepository)->T):T {
        repeat(3) { attempt ->
            try { return DriverManager.getConnection(f.url,f.props).use { c ->
                c.autoCommit=false; c.transactionIsolation=Connection.TRANSACTION_REPEATABLE_READ
                try {
                    exec(c,"SET LOCAL search_path TO ${f.schema},public");exec(c,"SET LOCAL statement_timeout='8s'");exec(c,"SET LOCAL lock_timeout='5s'")
                    val market=MarketplaceRepository(c) { user,store -> user==f.owner && store in setOf(f.root,f.branch,f.sibling) }
                    block(MarketShoppingRepository(c,market)).also { c.commit() }
                } catch(failure:Throwable) { c.rollback(); throw failure }
            } } catch(failure:SQLException) {
                if(attempt==2 || failure.sqlState !in setOf("40001","40P01","23505")) throw failure
            }
        }
        error("Unreachable")
    }
    private fun listCommand(f:Fixture, offer:MarketListing, revision:Long=0, units:Int=1)=MarketShoppingCommand(
        UUID.randomUUID().toString(),revision,offer.id,units,
        if(units==0) null else tx(f) { it.offer(f.buyer,offer.id) }.shoppingBasis())
    private fun storefront(f:Fixture,published:Boolean=true)=tx(f){r->r.updateStorefront(f.owner,null,MarketStorefrontUpdate(
        MarketStorefront(f.branch.toString(),"Public shop","Astana","Public pickup door",published=published)))}
    private fun listing(f:Fixture,published:Boolean=true)=tx(f){r->r.updateListing(f.owner,null,MarketListingUpdate(
        MarketListing("",f.branch.toString(),f.item.toString(),"Public product","Approved description",gtin="4006381333931",published=published)))}
    private fun published(f:Fixture):MarketListing {storefront(f);return listing(f).listings.single()}
    private fun browse(f:Fixture)=tx(f){it.browse(f.buyer,"","",null,null)}
    private fun batch(f:Fixture,c:Connection,store:UUID=f.branch,status:String="Delivered",total:Double=3.0,expires:Long?=null) {
        val quantity=jsonBase.encodeToString(QuantityDataModel("piece",listOf(LocalizedStringDataModel("en","piece")),total,1.0,true))
        c.prepareStatement("INSERT INTO stock_batches(id,store_id,goods_item_id,quantity,status,expiration_date_millis) VALUES (?,?,?,?::jsonb,?,?)").use{s->
            listOf(UUID.randomUUID(),store,f.item,quantity,status,expires).forEachIndexed{i,v->s.setObject(i+1,v)};s.executeUpdate()
        }
    }
    @Test fun installationDoesNotPublishPrivateStock()=fixture{f,c->
        assertTrue(browse(f).offers.isEmpty());assertEquals("0",scalar(c,"SELECT count(*) FROM marketplace_storefronts"))
    }
    @Test fun storefrontAndProductRequireIndependentOptIn()=fixture{f,_->
        storefront(f,false);listing(f);assertTrue(browse(f).offers.isEmpty())
        val current=tx(f){it.dashboard(f.owner,f.branch)}.storefront
        tx(f){it.updateStorefront(f.owner,null,MarketStorefrontUpdate(current.copy(published=true)))}
        assertEquals(1,browse(f).offers.size)
    }
    @Test fun ordinaryBuyerCanBrowseWithoutOwningOrSubscribingToAStore()=fixture{f,_->
        published(f);assertEquals("Public product",browse(f).offers.single().title)
        assertEquals(403,assertFailsWith<MarketFailure>{tx(f){it.dashboard(f.buyer,f.branch)}}.status)
    }
    @Test fun parentSubscriptionDoesNotGrantBranchPublishingOrVisibility()=fixture{f,c->
        published(f);exec(c,"UPDATE store_subscription_states SET status='inactive' WHERE store_id='${f.branch}'")
        assertTrue(browse(f).offers.isEmpty());assertEquals(402,assertFailsWith<MarketFailure>{tx(f){it.dashboard(f.owner,f.branch)}}.status)
    }
    @Test fun publicProjectionContainsNoCostsPrivateNotesOrExactStockTotals()=fixture{f,c->
        published(f);batch(f,c);val offer=browse(f).offers.single()
        assertEquals(19999L,offer.priceMinor);assertEquals(MARKET_AVAILABILITY_RECORDED,offer.availability)
        val json=jsonBase.encodeToString(offer)
        listOf("NEVER-PUBLIC","supplyPrice","goodsItemId","quantity","supplierId","ownerUser").forEach{assertFalse(json.contains(it),it)}
    }
    @Test fun siblingAndParentBatchesNeverBecomeThisBranchesAvailability()=fixture{f,c->
        published(f);batch(f,c,f.root);batch(f,c,f.sibling)
        assertEquals(MARKET_AVAILABILITY_CONFIRM,browse(f).offers.single().availability)
    }
    @Test fun reservedExpiredAndEmptyBatchesAreExcludedBeforePreferredSelection()=fixture{f,c->
        published(f);batch(f,c,status="Reserved");batch(f,c,expires=1L);batch(f,c,total=0.0)
        assertNull(browse(f).offers.single().priceMinor)
        batch(f,c);assertEquals(19999L,browse(f).offers.single().priceMinor)
    }
    @Test fun unavailableSavedOffersAreHiddenButNotSilentlyDeleted()=fixture{f,c->
        val listing=published(f);tx(f){it.updateSaved(f.buyer,MarketSavedUpdate(listing.id,true))}
        tx(f){it.updateListing(f.owner,null,MarketListingUpdate(listing.copy(published=false)))}
        val saved=tx(f){it.saved(f.buyer)};assertTrue(saved.offers.isEmpty());assertEquals(1,saved.unavailableSavedCount)
        assertEquals("1",scalar(c,"SELECT count(*) FROM buyer_saved_offers"))
        tx(f){it.clearUnavailableSaved(f.buyer)};assertEquals("0",scalar(c,"SELECT count(*) FROM buyer_saved_offers"))
    }
    @Test fun repeatedDesiredPublicationIsIdempotentButConflictingRevisionIsNot()=fixture{f,c->
        val current=published(f)
        val retry=tx(f){it.updateListing(f.owner,null,MarketListingUpdate(current.copy(id="",revision=0)))}.listings.single()
        assertEquals(current.revision,retry.revision)
        assertEquals("2",scalar(c,"SELECT count(*) FROM marketplace_publication_events"))
        assertEquals(409,assertFailsWith<MarketFailure>{tx(f){it.updateListing(f.owner,null,MarketListingUpdate(current.copy(title="Stale change",revision=0)))}}.status)
    }
    @Test fun changingStockBarcodeRemovesOldPublicComparisonIdentity()=fixture{f,c->
        published(f);exec(c,"UPDATE stock_items SET barcodes='[\"036000291452\"]' WHERE id='${f.item}'")
        assertNull(browse(f).offers.single().gtin)
    }
    @Test fun inactiveItemCanBeWithdrawnWithoutRepublishingIt()=fixture{f,c->
        val current=published(f);exec(c,"UPDATE stock_items SET is_active=FALSE WHERE id='${f.item}'")
        assertTrue(browse(f).offers.isEmpty())
        assertFalse(tx(f){it.updateListing(f.owner,null,MarketListingUpdate(current.copy(published=false)))}.listings.single().published)
    }
    @Test fun unrelatedValidBarcodeCannotBePublishedAsThisItem()=fixture{f,_->
        storefront(f)
        assertEquals(400,assertFailsWith<MarketFailure>{tx(f){it.updateListing(f.owner,null,MarketListingUpdate(
            MarketListing("",f.branch.toString(),f.item.toString(),"False identity",gtin="036000291452",published=true)))}}.status)
    }
    @Test fun literalSearchDoesNotInterpretSqlWildcardsOrPrivateMetadata()=fixture{f,_->
        published(f)
        listOf("%","' OR true--","NEVER-PUBLIC-NOTE").forEach{q->assertTrue(tx(f){it.browse(f.buyer,q,"",null,null)}.offers.isEmpty())}
        assertEquals(1,tx(f){it.browse(f.buyer,"public","Astana",null,null)}.offers.size)
        assertTrue(tx(f){it.browse(f.buyer,"","Other city",null,null)}.offers.isEmpty())
    }
    @Test fun auditRowsCannotBeRewrittenOrDeleted()=fixture{f,c->
        published(f)
        assertFailsWith<SQLException>{exec(c,"DELETE FROM marketplace_publication_events")}
        assertEquals("2",scalar(c,"SELECT count(*) FROM marketplace_publication_events"))
    }
    @Test fun failedAuditRollsBackThePublicationInsteadOfReportingPartialSuccess()=fixture{f,c->
        exec(c,"""CREATE FUNCTION fail_market_audit() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test audit unavailable'; END $$;
            CREATE TRIGGER fail_market_audit BEFORE INSERT ON marketplace_publication_events FOR EACH ROW EXECUTE FUNCTION fail_market_audit();""")
        assertFailsWith<SQLException>{storefront(f)}
        assertEquals("0",scalar(c,"SELECT count(*) FROM marketplace_storefronts"))
    }

    @Test fun publicShopCanBeEmptyButCannotExposeAnUnpublishedStore()=fixture { f,_ ->
        storefront(f)
        assertEquals("Public shop",tx(f) { it.publicShop(f.branch.toString()) }.displayName)
        assertTrue(tx(f) { it.browse(f.buyer,"","",null,null,f.branch.toString()) }.offers.isEmpty())
        assertEquals(404,assertFailsWith<MarketFailure> { tx(f) { it.publicShop(f.root.toString()) } }.status)
    }
    @Test fun shopFilterAndDetailUseTheSameVisibilityBoundary()=fixture { f,_ ->
        val listing=published(f)
        assertEquals(listing.id,tx(f) { it.offer(f.buyer,listing.id) }.id)
        assertTrue(tx(f) { it.browse(f.buyer,"","",null,null,f.sibling.toString()) }.offers.isEmpty())
        tx(f) { it.updateListing(f.owner,null,MarketListingUpdate(listing.copy(published=false))) }
        assertEquals(404,assertFailsWith<MarketFailure> { tx(f) { it.offer(f.buyer,listing.id) } }.status)
    }
    @Test fun shoppingListIsAccountScopedAndDoesNotModifyInventory()=fixture { f,c ->
        val listing=published(f);batch(f,c)
        val outcome=shoppingTx(f) { it.apply(f.buyer,null,listCommand(f,listing,units=3)) }
        assertTrue(outcome.accepted);assertEquals(1L,outcome.snapshot.revision)
        assertEquals(59997L,outcome.snapshot.lines.single().subtotalMinor)
        assertTrue(shoppingTx(f) { it.snapshot(f.owner) }.lines.isEmpty())
        assertEquals("1",scalar(c,"SELECT count(*) FROM stock_batches"))
        assertEquals(3.0,scalar(c,"SELECT quantity->>'total' FROM stock_batches").toDouble())
        assertEquals("0",scalar(c,"SELECT count(*) FROM transactions"))
    }
    @Test fun shoppingRetryReturnsOneRecordedEffectAndCurrentList()=fixture { f,c ->
        val listing=published(f);batch(f,c);val command=listCommand(f,listing)
        val first=shoppingTx(f) { it.apply(f.buyer,null,command) }
        val later=shoppingTx(f) { it.apply(f.buyer,null,command.copy(commandId=UUID.randomUUID().toString(),expectedRevision=1,units=2)) }
        val retry=shoppingTx(f) { it.apply(f.buyer,null,command) }
        assertTrue(first.accepted && later.accepted && retry.replayed)
        assertEquals(1L,retry.appliedRevision);assertEquals(2,retry.snapshot.lines.single().line.units)
        assertEquals(2L,retry.snapshot.revision);assertEquals("2",scalar(c,"SELECT count(*) FROM buyer_shopping_commands"))
    }
    @Test fun sameCommandIdWithDifferentQuantityIsRejected()=fixture { f,c ->
        val listing=published(f);batch(f,c);val command=listCommand(f,listing)
        shoppingTx(f) { it.apply(f.buyer,null,command) }
        assertEquals(409,assertFailsWith<MarketFailure> { shoppingTx(f) { it.apply(f.buyer,null,command.copy(units=2)) } }.status)
        assertEquals(1,shoppingTx(f) { it.snapshot(f.buyer) }.lines.single().line.units)
    }
    @Test fun staleEditIsRecordedWithoutOverwritingTheOtherDevice()=fixture { f,c ->
        val listing=published(f);batch(f,c);val first=listCommand(f,listing)
        shoppingTx(f) { it.apply(f.buyer,null,first) }
        val stale=first.copy(commandId=UUID.randomUUID().toString(),units=3)
        val result=shoppingTx(f) { it.apply(f.buyer,null,stale) }
        assertFalse(result.accepted);assertEquals("market.shopping_changed",result.errorKey)
        assertEquals(1,result.snapshot.lines.single().line.units)
        val retry=shoppingTx(f) { it.apply(f.buyer,null,stale) }
        assertTrue(retry.replayed);assertFalse(retry.accepted)
    }
    @Test fun insufficientSelectedBatchDoesNotInventAFullBasketPrice()=fixture { f,c ->
        val listing=published(f);batch(f,c,total=1.0)
        val result=shoppingTx(f) { it.apply(f.buyer,null,listCommand(f,listing,units=2)) }
        val row=result.snapshot.lines.single()
        assertTrue(result.accepted);assertEquals(MARKET_QUOTE_QUANTITY,row.status);assertNull(row.subtotalMinor)
    }
    @Test fun deletedListingLeavesBuyerIntentButNoCurrentPublicOffer()=fixture { f,c ->
        val listing=published(f);batch(f,c)
        shoppingTx(f) { it.apply(f.buyer,null,listCommand(f,listing)) }
        exec(c,"DELETE FROM marketplace_listings WHERE id='${listing.id}'")
        val row=shoppingTx(f) { it.snapshot(f.buyer) }.lines.single()
        assertEquals("Public product",row.line.title);assertNull(row.offer);assertNull(row.subtotalMinor)
        assertEquals(MARKET_QUOTE_UNAVAILABLE,row.status)
    }
    @Test fun subscriptionExpiryWithdrawsEstimateNotTheBuyersList()=fixture { f,c ->
        val listing=published(f);batch(f,c)
        shoppingTx(f) { it.apply(f.buyer,null,listCommand(f,listing)) }
        exec(c,"UPDATE store_subscription_states SET status='inactive' WHERE store_id='${f.branch}'")
        val row=shoppingTx(f) { it.snapshot(f.buyer) }.lines.single()
        assertNull(row.offer);assertEquals(MARKET_QUOTE_UNAVAILABLE,row.status)
        val removed=shoppingTx(f) { it.apply(f.buyer,null,MarketShoppingCommand(UUID.randomUUID().toString(),1,listing.id,0)) }
        assertTrue(removed.accepted);assertTrue(removed.snapshot.lines.isEmpty())
    }
    @Test fun currencyChangeRequiresBuyerReviewRatherThanConversion()=fixture { f,c ->
        val listing=published(f);batch(f,c)
        shoppingTx(f) { it.apply(f.buyer,null,listCommand(f,listing)) }
        exec(c,"""UPDATE stock_items SET sale_prices='[{"price":"199.99","currency":"USD","supplierId":""}]' WHERE id='${f.item}'""")
        val row=shoppingTx(f) { it.snapshot(f.buyer) }.lines.single()
        assertEquals("KZT",row.line.basis.currencyCode);assertEquals(MARKET_QUOTE_CHANGED,row.status);assertNull(row.subtotalMinor)
    }
    @Test fun ordinaryPriceChangeRefreshesEstimateWithoutChangingListRevision()=fixture { f,c ->
        val listing=published(f);batch(f,c)
        shoppingTx(f) { it.apply(f.buyer,null,listCommand(f,listing,units=2)) }
        exec(c,"""UPDATE stock_items SET sale_prices='[{"price":"250.01","currency":"KZT","supplierId":""}]' WHERE id='${f.item}'""")
        val snapshot=shoppingTx(f) { it.snapshot(f.buyer) }
        assertEquals(1L,snapshot.revision);assertEquals(50002L,snapshot.lines.single().subtotalMinor)
    }
    @Test fun recordedCommandOutcomeIsImmutable()=fixture { f,c ->
        val listing=published(f);batch(f,c)
        shoppingTx(f) { it.apply(f.buyer,null,listCommand(f,listing)) }
        assertFailsWith<SQLException> { exec(c,"UPDATE buyer_shopping_commands SET accepted=FALSE,error_key='changed',applied_revision=NULL") }
    }
    @Test fun failedCommandRecordingRollsBackListMutation()=fixture { f,c ->
        val listing=published(f);batch(f,c);val command=listCommand(f,listing)
        exec(c,"""CREATE FUNCTION fail_shopping_receipt() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test receipt unavailable'; END $$;
            CREATE TRIGGER fail_shopping_receipt BEFORE INSERT ON buyer_shopping_commands FOR EACH ROW EXECUTE FUNCTION fail_shopping_receipt();""")
        assertFailsWith<SQLException> { shoppingTx(f) { it.apply(f.buyer,null,command) } }
        assertEquals("0",scalar(c,"SELECT count(*) FROM buyer_shopping_lines"))
        assertEquals("0",scalar(c,"SELECT count(*) FROM buyer_shopping_lists"))
    }
    @Test fun parallelRetriesCreateOnlyOneLineAndOneReceipt()=fixture { f,c ->
        val listing=published(f);batch(f,c);val command=listCommand(f,listing)
        val pool=java.util.concurrent.Executors.newFixedThreadPool(2)
        val start=java.util.concurrent.CountDownLatch(1)
        try {
            val jobs=(1..2).map { pool.submit<MarketShoppingOutcome> {
                start.await();shoppingTx(f) { it.apply(f.buyer,null,command) }
            } }
            start.countDown()
            val results=jobs.map { it.get(20,java.util.concurrent.TimeUnit.SECONDS) }
            assertTrue(results.all { it.accepted });assertEquals(1,results.count { it.replayed })
            assertEquals("1",scalar(c,"SELECT count(*) FROM buyer_shopping_commands"))
            assertEquals("1",scalar(c,"SELECT count(*) FROM buyer_shopping_lines"))
        } finally { start.countDown();pool.shutdownNow() }
    }
    @Test fun fractionalSellingUnitEstimateDoesNotExceedAnEqualDecimalStockBalance()=fixture{f,c->
        val offer=published(f);batch(f,c,total=0.3)
        exec(c,"UPDATE stock_batches SET quantity=jsonb_set(quantity,'{pricedAmount}','0.1')")
        val request=listCommand(f,offer,units=3)
        val result=shoppingTx(f){it.apply(f.buyer,null,request)}
        assertTrue(result.accepted)
        assertEquals(MARKET_QUOTE_ESTIMATED,result.snapshot.lines.single().status)
        assertEquals(59997L,result.snapshot.lines.single().subtotalMinor)
    }
    @Test fun simultaneousDifferentEditsHaveOneRevisionWinnerNotAHiddenOverwrite()=fixture{f,c->
        val offer=published(f);batch(f,c,total=20.0)
        shoppingTx(f){it.apply(f.buyer,null,listCommand(f,offer))}
        val one=listCommand(f,offer,revision=1,units=2)
        val two=listCommand(f,offer,revision=1,units=3)
        val executor=java.util.concurrent.Executors.newFixedThreadPool(2)
        val start=java.util.concurrent.CountDownLatch(1)
        try {
            val first=executor.submit<MarketShoppingOutcome>{start.await();shoppingTx(f){it.apply(f.buyer,null,one)}}
            val second=executor.submit<MarketShoppingOutcome>{start.await();shoppingTx(f){it.apply(f.buyer,null,two)}}
            start.countDown()
            val outcomes=listOf(first.get(15,java.util.concurrent.TimeUnit.SECONDS),second.get(15,java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(1,outcomes.count{it.accepted})
            assertEquals("market.shopping_changed",outcomes.single{!it.accepted}.errorKey)
            assertEquals(2L,shoppingTx(f){it.snapshot(f.buyer)}.revision)
            assertEquals("3",scalar(c,"SELECT count(*) FROM buyer_shopping_commands"))
        } finally { executor.shutdownNow();executor.awaitTermination(10,java.util.concurrent.TimeUnit.SECONDS) }
    }

    private fun alternative(f:Fixture,c:Connection,price:String="150.00",total:Double=10.0):MarketListing {
        val offer=published(f.copy(branch=f.sibling));batch(f,c,store=f.sibling,total=total)
        exec(c,"""UPDATE stock_batches SET sale_price_override='{"price":"$price","currency":"KZT","supplierId":""}' WHERE store_id='${f.sibling}'""")
        return offer
    }
    private fun comparison(f:Fixture,source:MarketListing,revision:Long?=null,units:Int=2,city:String=""):MarketComparisonPage {
        val basis=if(revision==null) tx(f){it.offer(f.buyer,source.id)}.shoppingBasis()!!
            else shoppingTx(f){it.snapshot(f.buyer)}.lines.first{it.line.offerId==source.id}.line.basis
        return shoppingTx(f){it.comparison(f.buyer,MarketComparisonRequest(MarketComparisonSelection(source.id,basis,units,revision),city))}
    }
    private fun replacement(f:Fixture,source:MarketListing):MarketShoppingCommand {
        val snapshot=shoppingTx(f){it.snapshot(f.buyer)}
        val page=comparison(f,source,snapshot.revision,snapshot.lines.first{it.line.offerId==source.id}.line.units)
        return page.selection.reviewedReplacement(snapshot,page.matches.single(),UUID.randomUUID().toString())!!
    }
    @Test fun comparisonFindsOtherShopsAndQuotesRequestedQuantity()=fixture{f,c->
        val source=published(f);batch(f,c,total=10.0);val target=alternative(f,c)
        val page=comparison(f,source)
        assertEquals(39998L,page.reference.subtotalMinor);assertEquals(30000L,page.matches.single().subtotalMinor)
        assertEquals(target.id,page.matches.single().line.offerId);assertNull(page.nextId)
        assertEquals("0",scalar(c,"SELECT count(*) FROM buyer_shopping_commands"))
        assertEquals("0",scalar(c,"SELECT count(*) FROM transactions"))
    }
    @Test fun comparisonDoesNotMixCurrenciesSellingUnitsOrChangedBarcodes()=fixture{f,c->
        val source=published(f);batch(f,c,total=10.0);alternative(f,c)
        exec(c,"""UPDATE stock_batches SET sale_price_override='{"price":"150","currency":"USD","supplierId":""}' WHERE store_id='${f.sibling}'""")
        assertTrue(comparison(f,source).matches.isEmpty())
        exec(c,"""UPDATE stock_batches SET sale_price_override=NULL,quantity=jsonb_set(quantity,'{pricedAmount}','2') WHERE store_id='${f.sibling}'""")
        assertTrue(comparison(f,source).matches.isEmpty())
        exec(c,"UPDATE stock_batches SET quantity=jsonb_set(quantity,'{pricedAmount}','1') WHERE store_id='${f.sibling}'")
        exec(c,"UPDATE marketplace_listings SET gtin='00036000291452' WHERE store_id='${f.sibling}'")
        assertTrue(comparison(f,source).matches.isEmpty())
    }
    @Test fun comparisonRespectsCityAndPhysicalLocationEntitlement()=fixture{f,c->
        val source=published(f);batch(f,c,total=10.0);alternative(f,c)
        exec(c,"UPDATE marketplace_storefronts SET city='Almaty' WHERE store_id='${f.sibling}'")
        assertTrue(comparison(f,source,city="Astana").matches.isEmpty())
        assertEquals(1,comparison(f,source,city="almaty").matches.size)
        exec(c,"UPDATE store_subscription_states SET status='inactive' WHERE store_id='${f.sibling}'")
        assertTrue(comparison(f,source).matches.isEmpty())
    }
    @Test fun withdrawnOriginalCanFindAlternativesOnlyThroughItsOwnersList()=fixture{f,c->
        val source=published(f);batch(f,c,total=10.0);alternative(f,c)
        shoppingTx(f){it.apply(f.buyer,null,listCommand(f,source,units=2))}
        exec(c,"DELETE FROM marketplace_listings WHERE id='${source.id}'")
        val page=comparison(f,source,revision=1)
        assertNull(page.reference.offer);assertEquals(1,page.matches.size)
        assertEquals(409,assertFailsWith<MarketFailure>{shoppingTx(f){it.comparison(f.owner,MarketComparisonRequest(page.selection))}}.status)
        assertEquals(404,assertFailsWith<MarketFailure>{shoppingTx(f){it.comparison(f.buyer,MarketComparisonRequest(page.selection.copy(shoppingRevision=null)))}}.status)
    }
    @Test fun replacementChangesOneLineOnceWithTheSameQuantityAndNoStockWrites()=fixture{f,c->
        val source=published(f);batch(f,c,total=10.0);val target=alternative(f,c)
        shoppingTx(f){it.apply(f.buyer,null,listCommand(f,source,units=2))}
        val before=scalar(c,"SELECT created_at_millis FROM buyer_shopping_lines")
        val command=replacement(f,source)
        val first=shoppingTx(f){it.replace(f.buyer,null,command)}
        assertTrue(first.accepted);assertEquals(2L,first.snapshot.revision)
        assertEquals(target.id,first.snapshot.lines.single().line.offerId);assertEquals(2,first.snapshot.lines.single().line.units)
        assertEquals(before,scalar(c,"SELECT created_at_millis FROM buyer_shopping_lines"))
        assertEquals(source.id,scalar(c,"SELECT replaced_offer_id FROM buyer_shopping_commands WHERE command_id='${command.commandId}'"))
        assertEquals("30000",scalar(c,"SELECT reviewed_subtotal_minor FROM buyer_shopping_commands WHERE command_id='${command.commandId}'"))
        val retry=shoppingTx(f){it.replace(f.buyer,null,command)}
        assertTrue(retry.replayed && retry.accepted);assertEquals(2L,retry.snapshot.revision)
        assertEquals("2",scalar(c,"SELECT count(*) FROM buyer_shopping_commands"));assertEquals("0",scalar(c,"SELECT count(*) FROM transactions"))
        assertEquals(20.0,scalar(c,"SELECT sum((quantity->>'total')::numeric) FROM stock_batches").toDouble())
    }
    @Test fun changedEstimateKeepsOriginalAndRecordsItsRejection()=fixture{f,c->
        val source=published(f);batch(f,c,total=10.0);alternative(f,c)
        shoppingTx(f){it.apply(f.buyer,null,listCommand(f,source,units=2))};val command=replacement(f,source)
        exec(c,"""UPDATE stock_batches SET sale_price_override='{"price":"151","currency":"KZT","supplierId":""}' WHERE store_id='${f.sibling}'""")
        val rejected=shoppingTx(f){it.replace(f.buyer,null,command)}
        assertFalse(rejected.accepted);assertEquals("market.comparison_price_changed",rejected.errorKey)
        assertEquals(source.id,rejected.snapshot.lines.single().line.offerId)
        exec(c,"""UPDATE stock_batches SET sale_price_override='{"price":"150","currency":"KZT","supplierId":""}' WHERE store_id='${f.sibling}'""")
        val retry=shoppingTx(f){it.replace(f.buyer,null,command)};assertTrue(retry.replayed);assertFalse(retry.accepted)
    }
    @Test fun unavailableTargetCannotDeleteTheOriginalLine()=fixture{f,c->
        val source=published(f);batch(f,c,total=10.0);alternative(f,c)
        shoppingTx(f){it.apply(f.buyer,null,listCommand(f,source,units=2))};val command=replacement(f,source)
        exec(c,"UPDATE stock_batches SET quantity=jsonb_set(quantity,'{total}','1') WHERE store_id='${f.sibling}'")
        val rejected=shoppingTx(f){it.replace(f.buyer,null,command)}
        assertFalse(rejected.accepted);assertEquals("market.comparison_unavailable",rejected.errorKey)
        assertEquals(source.id,rejected.snapshot.lines.single().line.offerId)
    }
    @Test fun existingTargetLineIsNotMergedOrOverwrittenByReplacement()=fixture{f,c->
        val source=published(f);batch(f,c,total=10.0);val target=alternative(f,c)
        shoppingTx(f){it.apply(f.buyer,null,listCommand(f,source,units=2))};val command=replacement(f,source)
        shoppingTx(f){it.apply(f.buyer,null,listCommand(f,target,revision=1,units=3))}
        val result=shoppingTx(f){it.replace(f.buyer,null,command.copy(expectedRevision=2))}
        assertFalse(result.accepted);assertEquals("market.comparison_already_listed",result.errorKey)
        assertEquals(setOf(2,3),result.snapshot.lines.map{it.line.units}.toSet());assertEquals(2,result.snapshot.lines.size)
    }
    @Test fun replacementOnLegacyMutationEndpointIsRejectedNotInterpretedAsAdd()=fixture{f,c->
        val source=published(f);batch(f,c,total=10.0);alternative(f,c)
        shoppingTx(f){it.apply(f.buyer,null,listCommand(f,source,units=2))};val command=replacement(f,source)
        assertEquals(400,assertFailsWith<MarketFailure>{shoppingTx(f){it.apply(f.buyer,null,command)}}.status)
        assertEquals("1",scalar(c,"SELECT count(*) FROM buyer_shopping_lines"));assertEquals("1",scalar(c,"SELECT count(*) FROM buyer_shopping_commands"))
    }
    @Test fun failedReplacementReceiptRollsBackDeletionAndInsertion()=fixture{f,c->
        val source=published(f);batch(f,c,total=10.0);alternative(f,c)
        shoppingTx(f){it.apply(f.buyer,null,listCommand(f,source,units=2))};val command=replacement(f,source)
        exec(c,"""CREATE FUNCTION fail_replace_receipt() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test failure'; END $$;
            CREATE TRIGGER fail_replace_receipt BEFORE INSERT ON buyer_shopping_commands FOR EACH ROW EXECUTE FUNCTION fail_replace_receipt();""")
        assertFailsWith<SQLException>{shoppingTx(f){it.replace(f.buyer,null,command)}}
        assertEquals(source.id,scalar(c,"SELECT offer_id FROM buyer_shopping_lines"));assertEquals("1",scalar(c,"SELECT revision FROM buyer_shopping_lists"))
    }
    @Test fun simultaneousReplacementRetriesDoNotAddTwoEffects()=fixture{f,c->
        val source=published(f);batch(f,c,total=10.0);alternative(f,c)
        shoppingTx(f){it.apply(f.buyer,null,listCommand(f,source,units=2))};val command=replacement(f,source)
        val pool=java.util.concurrent.Executors.newFixedThreadPool(2);val start=java.util.concurrent.CountDownLatch(1)
        try {
            val jobs=(1..2).map{pool.submit<MarketShoppingOutcome>{start.await();shoppingTx(f){it.replace(f.buyer,null,command)}}}
            start.countDown();val replies=jobs.map{it.get(20,java.util.concurrent.TimeUnit.SECONDS)}
            assertTrue(replies.all{it.accepted});assertEquals(1,replies.count{it.replayed})
            assertEquals("1",scalar(c,"SELECT count(*) FROM buyer_shopping_lines"));assertEquals("2",scalar(c,"SELECT revision FROM buyer_shopping_lists"))
        } finally {start.countDown();pool.shutdownNow()}
    }
    @Test fun simultaneousQuantityEditAndReplacementHaveOnlyOneRevisionWinner()=fixture{f,c->
        val source=published(f);batch(f,c,total=10.0);alternative(f,c)
        shoppingTx(f){it.apply(f.buyer,null,listCommand(f,source,units=2))}
        val replace=replacement(f,source);val edit=listCommand(f,source,revision=1,units=3)
        val pool=java.util.concurrent.Executors.newFixedThreadPool(2);val start=java.util.concurrent.CountDownLatch(1)
        try {
            val jobs=listOf(pool.submit<MarketShoppingOutcome>{start.await();shoppingTx(f){it.replace(f.buyer,null,replace)}},
                pool.submit<MarketShoppingOutcome>{start.await();shoppingTx(f){it.apply(f.buyer,null,edit)}})
            start.countDown();val replies=jobs.map{it.get(20,java.util.concurrent.TimeUnit.SECONDS)}
            assertEquals(1,replies.count{it.accepted});assertEquals(1,replies.count{it.errorKey=="market.shopping_changed"})
            assertEquals("1",scalar(c,"SELECT count(*) FROM buyer_shopping_lines"));assertEquals("2",scalar(c,"SELECT revision FROM buyer_shopping_lists"))
        } finally {start.countDown();pool.shutdownNow()}
    }

    @Test fun comparisonHonoursTheRequestedCountForMinimumQuantityOffers()=fixture{f,c->
        val source=published(f);batch(f,c,total=10.0);alternative(f,c)
        exec(c,"""UPDATE stock_batches SET promotions='[{"id":"minimum","type":"restriction","minQuantity":3}]' WHERE store_id='${f.sibling}'""")
        assertTrue(comparison(f,source,units=2).matches.isEmpty())
        val page=comparison(f,source,units=3)
        assertEquals(45000L,page.matches.single().subtotalMinor)
        val target=page.matches.single()
        val added=shoppingTx(f){it.apply(f.buyer,null,MarketShoppingCommand(UUID.randomUUID().toString(),0,target.line.offerId,3,target.line.basis))}
        assertTrue(added.accepted);assertEquals(45000L,added.snapshot.lines.single().subtotalMinor)
    }
    @Test fun comparisonCanContinueBeyondAPageOfIncompatibleCandidates()=fixture{f,c->
        val source=published(f);batch(f,c,total=10.0);val target=alternative(f,c)
        // 41 additional published same-GTIN records with no eligible batch. They are scanned,
        // never fabricated as priced offers, and must not hide the later compatible target.
        repeat(41){ n ->
            val item=UUID.randomUUID();val id=UUID.fromString("00000000-0000-0000-0000-"+(n+1).toString().padStart(12,'0'))
            exec(c,"""INSERT INTO stock_items(id,store_id,barcodes,sale_prices) VALUES ('$item','${f.root}','["4006381333931"]','[{"price":"100","currency":"KZT","supplierId":""}]');
                INSERT INTO marketplace_listings(id,store_id,goods_item_id,title,description,gtin,is_published,revision,created_at_millis,updated_at_millis,updated_by)
                VALUES ('$id','${f.sibling}','$item','Unpriced candidate','','04006381333931',TRUE,1,1,1,'${f.owner}');""")
        }
        val first=comparison(f,source);assertTrue(first.matches.isEmpty());assertNotNull(first.nextId)
        val next=shoppingTx(f){it.comparison(f.buyer,MarketComparisonRequest(first.selection,after=first.nextId))}
        assertEquals(target.id,next.matches.single().line.offerId);assertNull(next.nextId)
    }

    @Test fun oldRepeatableReadSnapshotCannotAcceptAStaleNoOpRemoval()=fixture{f,c->
        val source=published(f);batch(f,c,total=10.0);val target=alternative(f,c)
        shoppingTx(f){it.apply(f.buyer,null,listCommand(f,source))}
        DriverManager.getConnection(f.url,f.props).use{old->
            old.autoCommit=false;old.transactionIsolation=Connection.TRANSACTION_REPEATABLE_READ
            exec(old,"SET LOCAL search_path TO ${f.schema},public")
            assertEquals("1",scalar(old,"SELECT revision FROM buyer_shopping_lists WHERE user_id='${f.buyer}'"))
            shoppingTx(f){it.apply(f.buyer,null,listCommand(f,target,revision=1))}
            val removal=MarketShoppingCommand(UUID.randomUUID().toString(),1,target.id,0)
            val market=MarketplaceRepository(old){user,_->user==f.owner}
            val failure=assertFailsWith<SQLException>{MarketShoppingRepository(old,market).apply(f.buyer,null,removal)}
            assertEquals("40001",failure.sqlState);old.rollback()
            val retry=shoppingTx(f){it.apply(f.buyer,null,removal)}
            assertFalse(retry.accepted);assertEquals("market.shopping_changed",retry.errorKey)
            assertEquals(2,retry.snapshot.lines.size)
        }
    }
}

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
 * Uses actual V46/V101/V102/V104/V105/V106/V107/V108/V109/V110 SQL with minimal prerequisite inventory tables, not the entire
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
                    CREATE TABLE generic_goods_categories(id UUID PRIMARY KEY,name JSONB NOT NULL,type_ids JSONB NOT NULL DEFAULT '[]');
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
                    "V102__single_use_promo_archive.sql","V104__opt_in_buyer_shop_windows.sql","V105__buyer_shopping_lists.sql","V106__buyer_reviewed_offer_replacements.sql","V107__market_category_discovery_indexes.sql","V108__reviewed_basket_list_changes.sql","V109__buyer_shopping_activity.sql","V110__public_shop_directory_order.sql").forEach{exec(c,resource(it))}
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
    @Test fun cancellingAnUnrecordedChangeCreatesATombstoneThatLateApplyCanOnlyReplay()=fixture { f,c ->
        val listing=published(f);batch(f,c);val command=listCommand(f,listing)
        val cancelled=shoppingTx(f) { it.cancel(f.buyer,null,command) }
        assertFalse(cancelled.accepted);assertFalse(cancelled.replayed);assertEquals("market.shopping_cancelled",cancelled.errorKey)
        assertEquals(0L,cancelled.snapshot.revision);assertTrue(cancelled.snapshot.lines.isEmpty())
        val late=shoppingTx(f) { it.apply(f.buyer,null,command) }
        assertTrue(late.replayed);assertFalse(late.accepted);assertEquals("market.shopping_cancelled",late.errorKey)
        assertTrue(late.snapshot.lines.isEmpty());assertEquals("1",scalar(c,"SELECT count(*) FROM buyer_shopping_commands"))
        val history=shoppingTx(f) { it.activitySearch(f.buyer,MarketShoppingActivitySearchRequest()) }
        assertEquals(command.commandId,history.entries.single().commandId);assertFalse(history.entries.single().accepted)
        assertEquals("market.shopping_cancelled",history.entries.single().errorKey)
    }
    @Test fun cancellingAfterTheOriginalCommitReturnsThatRecordedSuccessAndNeverUndoesIt()=fixture { f,c ->
        val listing=published(f);batch(f,c);val command=listCommand(f,listing)
        val applied=shoppingTx(f) { it.apply(f.buyer,null,command) };assertTrue(applied.accepted)
        val cancelled=shoppingTx(f) { it.cancel(f.buyer,null,command) }
        assertTrue(cancelled.replayed);assertTrue(cancelled.accepted);assertNull(cancelled.errorKey)
        assertEquals(1L,cancelled.snapshot.revision);assertEquals(command.offerId,cancelled.snapshot.lines.single().line.offerId)
        assertEquals("1",scalar(c,"SELECT count(*) FROM buyer_shopping_commands"))
    }
    @Test fun cancellationKeepsCommandIdentityBoundToTheExactSavedPayload()=fixture { f,c ->
        val listing=published(f);batch(f,c);val command=listCommand(f,listing)
        shoppingTx(f) { it.cancel(f.buyer,null,command) }
        assertEquals(409,assertFailsWith<MarketFailure> { shoppingTx(f) { it.cancel(f.buyer,null,command.copy(units=2)) } }.status)
        assertEquals("1",scalar(c,"SELECT count(*) FROM buyer_shopping_commands"));assertEquals("0",scalar(c,"SELECT count(*) FROM buyer_shopping_lines"))
    }
    @Test fun concurrentApplyAndCancelConvergeOnExactlyOneImmutableOutcome()=fixture { f,c ->
        val listing=published(f);batch(f,c);val command=listCommand(f,listing)
        val pool=java.util.concurrent.Executors.newFixedThreadPool(2);val start=java.util.concurrent.CountDownLatch(1)
        try {
            val apply=pool.submit<MarketShoppingOutcome>{start.await();shoppingTx(f){it.apply(f.buyer,null,command)}}
            val cancel=pool.submit<MarketShoppingOutcome>{start.await();shoppingTx(f){it.cancel(f.buyer,null,command)}}
            start.countDown()
            val outcomes=listOf(apply.get(20,java.util.concurrent.TimeUnit.SECONDS),cancel.get(20,java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(1,outcomes.count{it.replayed});assertEquals(1,outcomes.count{!it.replayed})
            assertEquals(outcomes.first().accepted,outcomes.last().accepted);assertEquals(outcomes.first().errorKey,outcomes.last().errorKey)
            assertEquals("1",scalar(c,"SELECT count(*) FROM buyer_shopping_commands"))
            val state=shoppingTx(f){it.snapshot(f.buyer)}
            if(outcomes.first().accepted){assertEquals(1L,state.revision);assertEquals(command.offerId,state.lines.single().line.offerId)}
            else {assertEquals("market.shopping_cancelled",outcomes.first().errorKey);assertEquals(0L,state.revision);assertTrue(state.lines.isEmpty())}
        } finally {start.countDown();pool.shutdownNow();pool.awaitTermination(10,java.util.concurrent.TimeUnit.SECONDS)}
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

    private fun catId(n: Int): UUID = UUID.nameUUIDFromBytes("discovery-test-category-$n".toByteArray())
    private fun category(c: Connection, n: Int, vararg parents: Int) {
        c.prepareStatement("INSERT INTO generic_goods_categories(id,name,type_ids) VALUES (?,?::jsonb,?::jsonb)").use { q ->
            q.setObject(1,catId(n));q.setString(2,jsonBase.encodeToString(listOf(LocalizedStringDataModel("en","Category $n"),LocalizedStringDataModel("ru","Категория $n"))))
            q.setString(3,jsonBase.encodeToString(parents.map { catId(it).toString() }));q.executeUpdate()
        }
    }
    private fun tag(c: Connection, item: UUID, vararg categories: Int) {
        c.prepareStatement("UPDATE stock_items SET category_ids=?::jsonb WHERE id=?").use { q ->
            q.setString(1,jsonBase.encodeToString(categories.map { catId(it).toString() }));q.setObject(2,item);q.executeUpdate()
        }
    }
    private fun discover(f: Fixture, query: MarketDiscoveryQuery = MarketDiscoveryQuery(), limit: Int = 40, version: String? = null) =
        tx(f) { it.discover(f.buyer,MarketDiscoveryRequest(query,limit,version)) }
    private fun copyPublic(f: Fixture,c: Connection,title: String,category: Int? = null,store: UUID = f.branch): MarketListing {
        val item = UUID.randomUUID()
        c.prepareStatement("INSERT INTO stock_items(id,store_id,barcodes,sale_prices,category_ids) SELECT ?,store_id,barcodes,sale_prices,?::jsonb FROM stock_items WHERE id=?").use { q ->
            q.setObject(1,item);q.setString(2,jsonBase.encodeToString(category?.let { listOf(catId(it).toString()) }.orEmpty()));q.setObject(3,f.item);q.executeUpdate()
        }
        return tx(f) { it.updateListing(f.owner,null,MarketListingUpdate(MarketListing("",store.toString(),item.toString(),title,published=true))) }.listings.first { it.goodsItemId==item.toString() }
    }
    @Test fun discoveryFiltersWholeAncestorSubtreeWithLegacyIds() = fixture { f,c ->
        category(c,1);category(c,2,1);category(c,3,1,2);category(c,4);published(f);tag(c,f.item,3)
        listOf(1,2,3).forEach { n -> assertEquals(1L,discover(f,MarketDiscoveryQuery(categoryId=catId(n).toString())).totalOffers) }
        assertEquals(0L,discover(f,MarketDiscoveryQuery(categoryId=catId(4).toString())).totalOffers)
    }
    @Test fun discoveryUnknownCategoryNeverSilentlyBroadensSearch() = fixture { f,_ ->
        published(f)
        assertEquals(409,assertFailsWith<MarketFailure> { discover(f,MarketDiscoveryQuery(categoryId=catId(99).toString())) }.status)
    }
    @Test fun discoveryCountsOffersNotRepeatedTagsOrPrivateInventory() = fixture { f,c ->
        category(c,1);category(c,2,1);published(f);tag(c,f.item,1,2,2)
        val hidden=copyPublic(f,c,"Hidden",1)
        tx(f) { it.updateListing(f.owner,null,MarketListingUpdate(hidden.copy(published=false))) }
        val result=discover(f,MarketDiscoveryQuery(categoryId=catId(1).toString()))
        assertEquals(1L,result.totalOffers);assertEquals(1L,result.totalShops);assertEquals(1,result.page.offers.size)
    }
    @Test fun discoveryUncategorizedItemsStayVisibleWithoutCategoryFilter() = fixture { f,c ->
        category(c,1);published(f)
        assertEquals(1L,discover(f).totalOffers);assertEquals(0L,discover(f,MarketDiscoveryQuery(categoryId=catId(1).toString())).totalOffers)
    }
    @Test fun discoveryTermsMatchAcrossOnlyPublicTitleAndDescriptionInAnyOrder() = fixture { f,_ ->
        published(f)
        assertEquals(1L,discover(f,MarketDiscoveryQuery(text="description product")).totalOffers)
        assertEquals(0L,discover(f,MarketDiscoveryQuery(text="product missing")).totalOffers)
        assertEquals(0L,discover(f,MarketDiscoveryQuery(text="NEVER-PUBLIC-NOTE")).totalOffers)
        assertEquals(0L,discover(f,MarketDiscoveryQuery(text="77.77")).totalOffers)
    }
    @Test fun discoverySqlMetacharactersAreLiteralSearchData() = fixture { f,c ->
        published(f)
        assertEquals(0L,discover(f,MarketDiscoveryQuery(text="%" )).totalOffers)
        assertEquals(0L,discover(f,MarketDiscoveryQuery(text="_" )).totalOffers)
        assertEquals(0L,discover(f,MarketDiscoveryQuery(text="' OR 1=1 --")).totalOffers)
        copyPublic(f,c,"50% discount _ literal")
        assertEquals(1L,discover(f,MarketDiscoveryQuery(text="% _")).totalOffers)
    }
    @Test fun discoveryBarcodeAndCaseInsensitiveCityApplyBeforeCount() = fixture { f,_ ->
        published(f)
        assertEquals(1L,discover(f,MarketDiscoveryQuery(text=" 4006381333931 ",city=" astana ")).totalOffers)
        assertEquals(0L,discover(f,MarketDiscoveryQuery(city="Almaty")).totalOffers)
        assertEquals(1L,discover(f,MarketDiscoveryQuery(city="Almaty",storefrontId=f.branch.toString())).totalOffers)
    }
    @Test fun discoverySavedSearchDoesNotClassifyFilteredOutBookmarksAsUnavailable() = fixture { f,c ->
        category(c,1);category(c,2);val first=published(f);tag(c,f.item,1)
        val other=copyPublic(f,c,"Other category",2);val gone=copyPublic(f,c,"Gone",2)
        listOf(first,other,gone).forEach { row -> tx(f) { it.updateSaved(f.buyer,MarketSavedUpdate(row.id,true)) } }
        tx(f) { it.updateListing(f.owner,null,MarketListingUpdate(gone.copy(published=false))) }
        val result=discover(f,MarketDiscoveryQuery(savedOnly=true,categoryId=catId(1).toString()))
        assertEquals(listOf(first.id),result.page.offers.map { it.id });assertEquals(1L,result.totalOffers)
        assertEquals(1,result.page.unavailableSavedCount);assertEquals("3",scalar(c,"SELECT count(*) FROM buyer_saved_offers"))
    }
    @Test fun discoverySavedScopeBelongsToAuthenticatedAccountNotSelectedStore() = fixture { f,_ ->
        val row=published(f);tx(f) { it.updateSaved(f.owner,MarketSavedUpdate(row.id,true)) }
        assertEquals(0L,discover(f,MarketDiscoveryQuery(savedOnly=true)).totalOffers)
        assertEquals(1L,tx(f) { it.discover(f.owner,MarketDiscoveryRequest(MarketDiscoveryQuery(savedOnly=true))) }.totalOffers)
    }
    @Test fun discoveryWindowAndCountsCoverMoreThanOnePage() = fixture { f,c ->
        published(f);repeat(44) { copyPublic(f,c,"Item $it") }
        val first=discover(f);assertEquals(40,first.page.offers.size);assertEquals(45L,first.totalOffers);assertNotNull(first.page.nextId)
        val second=discover(f,limit=80);assertEquals(45,second.page.offers.size);assertNull(second.page.nextId)
        assertEquals(45,second.page.offers.map { it.id }.distinct().size)
    }
    @Test fun discoveryNameOrderIsServerWideNotJustAmongLoadedCards() = fixture { f,c ->
        published(f);repeat(41) { copyPublic(f,c,"Zebra $it") };val first=copyPublic(f,c,"AAA first")
        val result=discover(f,MarketDiscoveryQuery(sort=MARKET_DISCOVERY_TITLE))
        assertEquals(first.id,result.page.offers.first().id);assertEquals(40,result.page.offers.size);assertEquals(43L,result.totalOffers)
    }
    @Test fun discoveryRecentSavedOrderUsesBookmarkTimeNotProductCreation() = fixture { f,c ->
        val first=published(f);val other=copyPublic(f,c,"Another")
        listOf(first,other).forEach { row -> tx(f) { it.updateSaved(f.buyer,MarketSavedUpdate(row.id,true)) } }
        exec(c,"UPDATE buyer_saved_offers SET created_at_millis=1 WHERE listing_id='${other.id}'")
        assertEquals(first.id,discover(f,MarketDiscoveryQuery(savedOnly=true)).page.offers.first().id)
    }
    @Test fun discoveryTaxonomyRevisionAvoidsRepeatedPayloadAndRefreshesLabels() = fixture { f,c ->
        category(c,1);published(f)
        val first=discover(f);assertEquals(64,first.categoryVersion.length);assertEquals(1,first.categories?.size)
        assertNull(discover(f,version=first.categoryVersion).categories)
        exec(c,"UPDATE generic_goods_categories SET name='[{\"language\":\"en\",\"value\":\"Renamed\"}]'")
        val changed=discover(f,version=first.categoryVersion)
        assertNotEquals(first.categoryVersion,changed.categoryVersion);assertEquals("Renamed",changed.categories?.single()?.name?.single()?.value)
    }
    @Test fun discoveryCategoryProjectionContainsNoUnknownStockTags() = fixture { f,c ->
        category(c,1);published(f)
        exec(c,"UPDATE stock_items SET category_ids='[\"${catId(1)}\",\"private-merchant-tag\"]' WHERE id='${f.item}'")
        val result=discover(f)
        assertEquals(listOf(catId(1).toString()),result.page.offers.single().categoryIds)
        val json=jsonBase.encodeToString(result)
        listOf("private-merchant-tag","NEVER-PUBLIC","quantityUnitId","conditions","imagePaths").forEach { assertFalse(it in json,it) }
    }
    @Test fun discoverySelectedCategoryEvidenceSurvivesPublicTagBound() = fixture { f,c ->
        (1..20).forEach { category(c,it) };published(f);tag(c,f.item,*(1..20).toList().toIntArray())
        val query=MarketDiscoveryQuery(categoryId=catId(20).toString());val result=discover(f,query)
        assertTrue(catId(20).toString() in result.page.offers.single().categoryIds)
        assertNotNull(result.validatedDiscovery(MarketDiscoveryRequest(query),null))
    }
    @Test fun discoveryIncludesPublishedOffersWithoutInventingPriceOrStock() = fixture { f,_ ->
        published(f);val result=discover(f)
        assertEquals(1L,result.totalOffers);assertNull(result.page.offers.single().priceMinor)
        assertEquals(MARKET_AVAILABILITY_CONFIRM,result.page.offers.single().availability)
        assertNotNull(result.validatedDiscovery(MarketDiscoveryRequest(MarketDiscoveryQuery()),null))
    }
    @Test fun discoveryExpiredBranchDoesNotInheritTheParentsAccess() = fixture { f,c ->
        published(f);exec(c,"UPDATE store_subscription_states SET status='inactive' WHERE store_id='${f.branch}'")
        val result=discover(f);assertEquals(0L,result.totalOffers);assertEquals(0L,result.totalShops)
    }
    @Test fun discoveryInvalidWindowOrSortNeverReachesSqlInterpolation() = fixture { f,_ ->
        published(f)
        assertEquals(400,assertFailsWith<MarketFailure> { discover(f,limit=401) }.status)
        assertEquals(400,assertFailsWith<MarketFailure> { discover(f,MarketDiscoveryQuery(sort="title;DROP TABLE users")) }.status)
        assertEquals(1L,discover(f).totalOffers)
    }
    @Test fun discoveryFiltersDoNotMutateListsBookmarksOrPublication() = fixture { f,c ->
        category(c,1);published(f);val before=scalar(c,"SELECT count(*) FROM marketplace_publication_events")
        discover(f,MarketDiscoveryQuery(categoryId=catId(1).toString(),savedOnly=true))
        assertEquals(before,scalar(c,"SELECT count(*) FROM marketplace_publication_events"))
        assertEquals("0",scalar(c,"SELECT count(*) FROM buyer_shopping_lists"));assertEquals("0",scalar(c,"SELECT count(*) FROM buyer_saved_offers"))
    }

    private fun basket(f:Fixture, revision:Long=1, city:String="", buyer:UUID=f.buyer):MarketBasketResult =
        DriverManager.getConnection(f.url,f.props).use { c ->
            c.autoCommit=false;c.transactionIsolation=Connection.TRANSACTION_REPEATABLE_READ;c.isReadOnly=true
            try {
                exec(c,"SET LOCAL search_path TO ${f.schema},public")
                exec(c,"SET LOCAL statement_timeout='8s'")
                val market=MarketplaceRepository(c){_,_->false}
                MarketShoppingRepository(c,market).basketPlan(buyer,MarketBasketRequest(revision,city)).also { c.commit() }
            } catch(failure:Throwable) { c.rollback();throw failure }
        }
    @Test fun basketEmptyAccountReadDoesNotCreateAnyRows()=fixture { f,c ->
        val result=basket(f,revision=0)
        assertTrue(result.currencies.isEmpty());assertTrue(result.isValidBasketResult(f.buyer.toString(),result.request))
        assertEquals("0",scalar(c,"SELECT count(*) FROM buyer_shopping_lists"))
        assertEquals("0",scalar(c,"SELECT count(*) FROM buyer_shopping_commands"))
    }
    @Test fun basketUsesRequestedQuantityAndDoesNotWriteStockOrIntent()=fixture { f,c ->
        val source=published(f);batch(f,c,total=10.0);alternative(f,c)
        shoppingTx(f){it.apply(f.buyer,null,listCommand(f,source,units=2))}
        val stockBefore=scalar(c,"SELECT string_agg(quantity::text,';' ORDER BY id) FROM stock_batches")
        val result=basket(f);val group=result.currencies.single()
        assertEquals(39998L,group.current.itemsSubtotalMinor);assertEquals(30000L,group.lowestItems.itemsSubtotalMinor)
        assertEquals(9998L,group.lowestItems.savingsAgainst(group.current))
        assertTrue(result.isValidBasketResult(f.buyer.toString(),result.request))
        assertEquals("1",scalar(c,"SELECT revision FROM buyer_shopping_lists WHERE user_id='${f.buyer}'"))
        assertEquals("1",scalar(c,"SELECT count(*) FROM buyer_shopping_commands"))
        assertEquals(stockBefore,scalar(c,"SELECT string_agg(quantity::text,';' ORDER BY id) FROM stock_batches"))
    }
    @Test fun basketCannotReadAnotherAccountsIntentFromItsRevision()=fixture { f,c ->
        val source=published(f);batch(f,c);shoppingTx(f){it.apply(f.buyer,null,listCommand(f,source))}
        assertTrue(basket(f,revision=0,buyer=f.owner).snapshot.lines.isEmpty())
        assertEquals(409,assertFailsWith<MarketFailure>{basket(f,buyer=f.owner)}.status)
    }
    @Test fun basketStaleRevisionIsRejectedBeforeSuggestingReplacements()=fixture { f,c ->
        val source=published(f);batch(f,c);shoppingTx(f){it.apply(f.buyer,null,listCommand(f,source))}
        assertEquals("market.basket_list_changed",assertFailsWith<MarketFailure>{basket(f,revision=0)}.key)
        assertEquals("1",scalar(c,"SELECT count(*) FROM buyer_shopping_commands"))
    }
    @Test fun basketCityAppliesToAllAlternativesWithoutHidingCurrentReference()=fixture { f,c ->
        val source=published(f);batch(f,c);alternative(f,c)
        exec(c,"UPDATE marketplace_storefronts SET city='Almaty' WHERE store_id='${f.sibling}'")
        shoppingTx(f){it.apply(f.buyer,null,listCommand(f,source))}
        val result=basket(f,city=" Almaty ")
        assertEquals(19999L,result.currencies.single().current.itemsSubtotalMinor)
        assertEquals(listOf(f.sibling.toString()),result.currencies.single().oneShop.storeIds)
        assertTrue(result.isValidBasketResult(f.buyer.toString(),MarketBasketRequest(1," Almaty ")))
    }
    @Test fun basketCitySqlMetacharactersAreNotWildcards()=fixture { f,c ->
        val source=published(f);batch(f,c);alternative(f,c)
        shoppingTx(f){it.apply(f.buyer,null,listCommand(f,source))}
        val result=basket(f,city="%_' OR TRUE --")
        assertEquals(0,result.candidatesChecked);assertTrue(result.currencies.single().lowestItems.choices.isEmpty())
    }
    @Test fun basketDoesNotBorrowParentEntitlementOrStockForAnExpiredTarget()=fixture { f,c ->
        val source=published(f);batch(f,c);alternative(f,c)
        exec(c,"UPDATE store_subscription_states SET status='inactive' WHERE store_id='${f.sibling}'")
        shoppingTx(f){it.apply(f.buyer,null,listCommand(f,source))}
        assertEquals(0,basket(f).candidatesChecked)
        assertEquals(0,basket(f).currencies.single().lowestItems.changedLines)
    }
    @Test fun basketInsufficientTargetQuantityCannotBecomeACompleteCheapOffer()=fixture { f,c ->
        val source=published(f);batch(f,c,total=10.0);alternative(f,c,total=1.0)
        shoppingTx(f){it.apply(f.buyer,null,listCommand(f,source,units=2))}
        val group=basket(f).currencies.single()
        assertEquals(39998L,group.lowestItems.itemsSubtotalMinor);assertEquals(0,group.lowestItems.changedLines)
    }
    @Test fun basketChangedTargetCurrencyIsNotConverted()=fixture { f,c ->
        val source=published(f);batch(f,c);alternative(f,c)
        exec(c,"""UPDATE stock_batches SET sale_price_override='{"price":"1.00","currency":"USD","supplierId":""}' WHERE store_id='${f.sibling}'""")
        shoppingTx(f){it.apply(f.buyer,null,listCommand(f,source))}
        assertEquals(0,basket(f).currencies.single().lowestItems.changedLines)
    }
    @Test fun basketUnpublishedOriginalStillHasItsOwnRetainedReference()=fixture { f,c ->
        val source=published(f);batch(f,c);alternative(f,c)
        shoppingTx(f){it.apply(f.buyer,null,listCommand(f,source))}
        tx(f){it.updateListing(f.owner,null,MarketListingUpdate(source.copy(published=false)))}
        val group=basket(f).currencies.single()
        assertFalse(group.current.complete);assertTrue(group.lowestItems.complete)
        assertNull(group.lowestItems.savingsAgainst(group.current))
    }
    @Test fun basketRepeatedProductLinesAreNotIndependentlyReplacedWithOneQuote()=fixture { f,c ->
        val source=published(f);batch(f,c);val target=alternative(f,c)
        shoppingTx(f){it.apply(f.buyer,null,listCommand(f,source,units=2))}
        shoppingTx(f){it.apply(f.buyer,null,listCommand(f,target,revision=1,units=1))}
        val result=basket(f,revision=2)
        assertEquals(2,result.fixedLines.size);assertEquals(0,result.candidatesChecked)
        assertEquals(0,result.currencies.single().lowestItems.changedLines)
    }
    @Test fun basketCandidatesHaveAPerLineBoundAndReportTheTruncation()=fixture { f,c ->
        val source=published(f);batch(f,c);alternative(f,c)
        repeat(15) { n ->
            val copy=copyPublic(f,c,"Alternative $n",store=f.sibling)
            tx(f){it.updateListing(f.owner,null,MarketListingUpdate(copy.copy(gtin="4006381333931")))}
        }
        shoppingTx(f){it.apply(f.buyer,null,listCommand(f,source))}
        val result=basket(f)
        assertEquals(MARKET_BASKET_CANDIDATES_PER_LINE,result.candidatesChecked)
        assertEquals(listOf(source.id),result.limitedSourceOfferIds)
        assertTrue(result.isValidBasketResult(f.buyer.toString(),result.request))
    }
    @Test fun basketPriceReadsShareOneRepeatableReadSnapshot()=fixture { f,c ->
        val source=published(f);batch(f,c);alternative(f,c)
        shoppingTx(f){it.apply(f.buyer,null,listCommand(f,source))}
        DriverManager.getConnection(f.url,f.props).use { reader ->
            reader.autoCommit=false;reader.transactionIsolation=Connection.TRANSACTION_REPEATABLE_READ;reader.isReadOnly=true
            exec(reader,"SET LOCAL search_path TO ${f.schema},public")
            scalar(reader,"SELECT revision FROM buyer_shopping_lists WHERE user_id='${f.buyer}'")
            exec(c,"""UPDATE stock_batches SET sale_price_override='{"price":"10.00","currency":"KZT","supplierId":""}' WHERE store_id='${f.sibling}'""")
            val repository=MarketShoppingRepository(reader,MarketplaceRepository(reader){_,_->false})
            assertEquals(15000L,repository.basketPlan(f.buyer,MarketBasketRequest(1)).currencies.single().lowestItems.itemsSubtotalMinor)
            reader.rollback()
        }
        assertEquals(1000L,basket(f).currencies.single().lowestItems.itemsSubtotalMinor)
    }
    @Test fun basketContainsNoPrivateCostsOwnersSessionsOrMerchantNotes()=fixture { f,c ->
        val source=published(f);batch(f,c);alternative(f,c)
        shoppingTx(f){it.apply(f.buyer,null,listCommand(f,source))}
        val json=jsonBase.encodeToString(basket(f))
        listOf("NEVER-PUBLIC","supplyPrice","ownerUser","sessionId","commandId","goodsItemId").forEach { assertFalse(json.contains(it),it) }
    }
    private data class BasketApplyFixture(val sources: List<MarketListing>, val command: MarketShoppingCommand)
    private fun reviewedBasket(f: Fixture, c: Connection): BasketApplyFixture {
        val first = published(f); batch(f,c,total=10.0); alternative(f,c)
        val secondItem=UUID.randomUUID()
        exec(c,"""INSERT INTO stock_items(id,store_id,barcodes,sale_prices)
            SELECT '$secondItem',store_id,'["5901234123457"]',sale_prices FROM stock_items WHERE id='${f.item}'""")
        val secondFixture=f.copy(item=secondItem)
        fun publishSecond(store:UUID)=tx(f){it.updateListing(f.owner,null,MarketListingUpdate(
            MarketListing("",store.toString(),secondItem.toString(),"Second product",gtin="5901234123457",published=true)))
            }.listings.first { it.goodsItemId==secondItem.toString() }
        val second=publishSecond(f.branch)
        val target=publishSecond(f.sibling)
        batch(secondFixture,c,total=10.0); batch(secondFixture,c,store=f.sibling,total=10.0)
        exec(c,"""UPDATE stock_batches SET sale_price_override='{"price":"100.00","currency":"KZT","supplierId":""}'
            WHERE goods_item_id='$secondItem' AND store_id='${f.sibling}'""")
        shoppingTx(f) { it.apply(f.buyer,null,listCommand(f,first,units=2)) }
        shoppingTx(f) { it.apply(f.buyer,null,listCommand(f,second,revision=1,units=1)) }
        val result=basket(f,revision=2)
        val command=requireNotNull(result.reviewedBasketCommand("KZT",MARKET_BASKET_ONE_SHOP,UUID.randomUUID().toString()))
        check(command.basketChange?.changedLines==2)
        check(command.basketChange?.lines?.any { it.targetOfferId==target.id }==true)
        return BasketApplyFixture(listOf(first,second),command)
    }
    private fun listIds(f: Fixture,c:Connection)=scalar(c,"SELECT string_agg(offer_id::text,',' ORDER BY offer_id) FROM buyer_shopping_lines WHERE user_id='${f.buyer}'")

    @Test fun reviewedBasketCanBeSafelyCancelledWithoutApplyingAnyReviewedReplacement()=fixture { f,c ->
        val prepared=reviewedBasket(f,c);val before=listIds(f,c);val revision=scalar(c,"SELECT revision FROM buyer_shopping_lists WHERE user_id='${f.buyer}'")
        val cancelled=shoppingTx(f){it.cancel(f.buyer,UUID.randomUUID(),prepared.command)}
        assertFalse(cancelled.accepted);assertEquals("market.shopping_cancelled",cancelled.errorKey);assertEquals(revision,cancelled.snapshot.revision.toString())
        assertEquals(before,listIds(f,c));assertEquals("1",scalar(c,"SELECT count(*) FROM buyer_shopping_commands WHERE basket_change IS NOT NULL AND offer_id IS NULL"))
        val replay=shoppingTx(f){it.applyBasket(f.buyer,null,prepared.command)}
        assertTrue(replay.replayed);assertFalse(replay.accepted);assertEquals("market.shopping_cancelled",replay.errorKey);assertEquals(before,listIds(f,c))
    }
    @Test fun basketApplyReplacesMultipleLinesOnceWithoutStockOrPaymentChanges()=fixture { f,c ->
        val prepared=reviewedBasket(f,c);val command=prepared.command
        val created=scalar(c,"SELECT string_agg(created_at_millis::text,',' ORDER BY created_at_millis) FROM buyer_shopping_lines")
        val stock=scalar(c,"SELECT string_agg(quantity::text,';' ORDER BY id) FROM stock_batches")
        val result=shoppingTx(f){it.applyBasket(f.buyer,UUID.randomUUID(),command)}
        assertTrue(result.accepted);assertEquals(3L,result.appliedRevision);assertEquals(2,result.snapshot.lines.size)
        assertEquals(command.basketChange!!.lines.map { it.targetOfferId }.toSet(),result.snapshot.lines.map { it.line.offerId }.toSet())
        assertTrue(command.matchesBasketOutcome(result))
        assertEquals(created,scalar(c,"SELECT string_agg(created_at_millis::text,',' ORDER BY created_at_millis) FROM buyer_shopping_lines"))
        assertEquals(stock,scalar(c,"SELECT string_agg(quantity::text,';' ORDER BY id) FROM stock_batches"))
        assertEquals("0",scalar(c,"SELECT count(*) FROM transactions"))
        assertEquals("1",scalar(c,"SELECT count(*) FROM buyer_shopping_commands WHERE basket_change IS NOT NULL AND offer_id IS NULL AND requested_units=0"))
    }
    @Test fun basketApplyFailureInSecondLineCannotKeepFirstLineOrAnOutcome()=fixture { f,c ->
        val prepared=reviewedBasket(f,c);val before=listIds(f,c)
        val failSource=prepared.command.basketChange!!.lines.last().sourceOfferId
        exec(c,"""CREATE FUNCTION fail_basket_line() RETURNS trigger LANGUAGE plpgsql AS ${'$'}${'$'}
            BEGIN IF OLD.offer_id='$failSource' THEN RAISE EXCEPTION 'Injected statement failure'; END IF; RETURN NEW; END ${'$'}${'$'};
            CREATE TRIGGER fail_basket_line BEFORE UPDATE ON buyer_shopping_lines FOR EACH ROW EXECUTE FUNCTION fail_basket_line();""")
        assertFailsWith<SQLException> { shoppingTx(f){it.applyBasket(f.buyer,null,prepared.command)} }
        assertEquals(before,listIds(f,c));assertEquals("2",scalar(c,"SELECT revision FROM buyer_shopping_lists WHERE user_id='${f.buyer}'"))
        assertEquals("0",scalar(c,"SELECT count(*) FROM buyer_shopping_commands WHERE basket_change IS NOT NULL"))
    }
    @Test fun basketApplyReplayedOutcomeSurvivesLaterPriceOrPublicationChange()=fixture { f,c ->
        val prepared=reviewedBasket(f,c)
        assertTrue(shoppingTx(f){it.applyBasket(f.buyer,null,prepared.command)}.accepted)
        exec(c,"UPDATE marketplace_listings SET is_published=FALSE WHERE store_id='${f.sibling}'")
        val replay=shoppingTx(f){it.applyBasket(f.buyer,null,prepared.command)}
        assertTrue(replay.accepted && replay.replayed);assertEquals(3L,replay.appliedRevision)
        assertTrue(prepared.command.matchesBasketOutcome(replay))
        assertEquals("1",scalar(c,"SELECT count(*) FROM buyer_shopping_commands WHERE basket_change IS NOT NULL"))
    }
    @Test fun basketApplySameIdentityWithDifferentReviewCannotExecuteAgain()=fixture { f,c ->
        val prepared=reviewedBasket(f,c);val command=prepared.command;val change=command.basketChange!!
        shoppingTx(f){it.applyBasket(f.buyer,null,command)}
        val altered=command.copy(basketChange=change.copy(checkedAtMillis=change.checkedAtMillis+1))
        assertEquals("market.shopping_command_mismatch",assertFailsWith<MarketFailure>{shoppingTx(f){it.applyBasket(f.buyer,null,altered)}}.key)
        assertEquals("1",scalar(c,"SELECT count(*) FROM buyer_shopping_commands WHERE basket_change IS NOT NULL"))
    }
    @Test fun basketApplyOneChangedPriceRejectsAllProposedReplacements()=fixture { f,c ->
        val prepared=reviewedBasket(f,c);val before=listIds(f,c)
        exec(c,"""UPDATE stock_batches SET sale_price_override='{"price":"110.00","currency":"KZT","supplierId":""}'
            WHERE goods_item_id='${prepared.sources.last().goodsItemId}' AND store_id='${f.sibling}'""")
        val rejected=shoppingTx(f){it.applyBasket(f.buyer,null,prepared.command)}
        assertFalse(rejected.accepted);assertEquals("market.basket_apply_price_changed",rejected.errorKey)
        assertEquals(before,listIds(f,c));assertEquals(2L,rejected.snapshot.revision)
        // Recorded rejection is stable even when the price later returns to the reviewed value.
        exec(c,"""UPDATE stock_batches SET sale_price_override='{"price":"100.00","currency":"KZT","supplierId":""}'
            WHERE goods_item_id='${prepared.sources.last().goodsItemId}' AND store_id='${f.sibling}'""")
        assertFalse(shoppingTx(f){it.applyBasket(f.buyer,null,prepared.command)}.accepted)
        assertEquals(before,listIds(f,c))
    }
    @Test fun basketApplyWithdrawalOrInsufficientStockKeepsAllSources()=fixture { f,c ->
        val prepared=reviewedBasket(f,c);val before=listIds(f,c)
        exec(c,"UPDATE marketplace_listings SET is_published=FALSE WHERE id='${prepared.command.basketChange!!.lines.last().targetOfferId}'")
        val result=shoppingTx(f){it.applyBasket(f.buyer,null,prepared.command)}
        assertFalse(result.accepted);assertEquals("market.basket_apply_unavailable",result.errorKey);assertEquals(before,listIds(f,c))
    }
    @Test fun basketApplyNewPickupDetailsRequireReview()=fixture { f,c ->
        val prepared=reviewedBasket(f,c);val before=listIds(f,c)
        val shop=tx(f){it.dashboard(f.owner,f.sibling)}.storefront
        tx(f){it.updateStorefront(f.owner,null,MarketStorefrontUpdate(shop.copy(publicAddress="Different public door")))}
        val result=shoppingTx(f){it.applyBasket(f.buyer,null,prepared.command)}
        assertFalse(result.accepted);assertEquals("market.basket_apply_offer_changed",result.errorKey);assertEquals(before,listIds(f,c))
    }
    @Test fun basketApplyReviewExpiryIsRecordedNotSilentlyRepriced()=fixture { f,c ->
        val prepared=reviewedBasket(f,c);val before=listIds(f,c)
        val expired=prepared.command.copy(basketChange=prepared.command.basketChange!!.copy(checkedAtMillis=1))
        val result=shoppingTx(f){it.applyBasket(f.buyer,null,expired)}
        assertFalse(result.accepted);assertEquals("market.basket_apply_expired",result.errorKey);assertEquals(before,listIds(f,c))
        assertTrue(shoppingTx(f){it.applyBasket(f.buyer,null,expired)}.replayed)
    }
    @Test fun basketApplyDoesNotBorrowTheOtherBuyersList()=fixture { f,c ->
        val prepared=reviewedBasket(f,c);val before=listIds(f,c)
        val result=shoppingTx(f){it.applyBasket(f.owner,null,prepared.command)}
        assertFalse(result.accepted);assertTrue(result.snapshot.lines.isEmpty());assertEquals(f.owner.toString(),result.snapshot.userId)
        assertEquals(before,listIds(f,c))
    }
    @Test fun basketApplyOnOldEndpointsIsRejectedBeforeAnyLineChange()=fixture { f,c ->
        val prepared=reviewedBasket(f,c);val before=listIds(f,c)
        assertFailsWith<MarketFailure>{shoppingTx(f){it.apply(f.buyer,null,prepared.command)}}
        assertFailsWith<MarketFailure>{shoppingTx(f){it.replace(f.buyer,null,prepared.command)}}
        assertEquals(before,listIds(f,c));assertEquals("0",scalar(c,"SELECT count(*) FROM buyer_shopping_commands WHERE basket_change IS NOT NULL"))
    }
    @Test fun basketApplyStaleRevisionCannotRemoveNewerListChanges()=fixture { f,c ->
        val prepared=reviewedBasket(f,c)
        shoppingTx(f){it.apply(f.buyer,null,listCommand(f,prepared.sources.first(),revision=2,units=3))}
        val before=listIds(f,c)
        val result=shoppingTx(f){it.applyBasket(f.buyer,null,prepared.command)}
        assertFalse(result.accepted);assertEquals("market.shopping_changed",result.errorKey);assertEquals(before,listIds(f,c))
        assertEquals(3,result.snapshot.lines.first { it.line.offerId==prepared.sources.first().id }.line.units)
    }
    @Test fun basketApplyConcurrentDuplicateCommandsHaveOneRevisionAndOutcome()=fixture { f,c ->
        val prepared=reviewedBasket(f,c);val executor=java.util.concurrent.Executors.newFixedThreadPool(2)
        val start=java.util.concurrent.CountDownLatch(1)
        try {
            val calls=(1..2).map { executor.submit<MarketShoppingOutcome> { start.await(); shoppingTx(f){it.applyBasket(f.buyer,null,prepared.command)} } }
            start.countDown();val results=calls.map { it.get(20,java.util.concurrent.TimeUnit.SECONDS) }
            assertTrue(results.all { it.accepted });assertEquals(1,results.count { it.replayed })
            assertEquals("3",scalar(c,"SELECT revision FROM buyer_shopping_lists WHERE user_id='${f.buyer}'"))
            assertEquals("1",scalar(c,"SELECT count(*) FROM buyer_shopping_commands WHERE basket_change IS NOT NULL"))
        } finally { executor.shutdownNow();executor.awaitTermination(10,java.util.concurrent.TimeUnit.SECONDS) }
    }
    @Test fun basketApplyConcurrentIndividualEditCannotCreateAHalfPlan()=fixture { f,c ->
        val prepared=reviewedBasket(f,c);val command=listCommand(f,prepared.sources.first(),revision=2,units=3)
        val executor=java.util.concurrent.Executors.newFixedThreadPool(2);val start=java.util.concurrent.CountDownLatch(1)
        try {
            val a=executor.submit<MarketShoppingOutcome>{start.await();shoppingTx(f){it.applyBasket(f.buyer,null,prepared.command)}}
            val b=executor.submit<MarketShoppingOutcome>{start.await();shoppingTx(f){it.apply(f.buyer,null,command)}}
            start.countDown();val results=listOf(a.get(20,java.util.concurrent.TimeUnit.SECONDS),b.get(20,java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(1,results.count { it.accepted });val snapshot=shoppingTx(f){it.snapshot(f.buyer)}
            assertEquals(3L,snapshot.revision);assertEquals(2,snapshot.lines.size)
            val ids=snapshot.lines.map { it.line.offerId }.toSet()
            assertTrue(ids==prepared.sources.map { it.id }.toSet() || ids==prepared.command.basketChange!!.lines.map { it.targetOfferId }.toSet())
        } finally { executor.shutdownNow();executor.awaitTermination(10,java.util.concurrent.TimeUnit.SECONDS) }
    }
    @Test fun basketRejectedCommandAdvancesLockFenceWithoutChangingListRevision()=fixture { f,c ->
        val prepared=reviewedBasket(f,c)
        DriverManager.getConnection(f.url,f.props).use { old ->
            old.autoCommit=false;old.transactionIsolation=Connection.TRANSACTION_REPEATABLE_READ
            exec(old,"SET LOCAL search_path TO ${f.schema},public")
            assertEquals("2",scalar(old,"SELECT revision FROM buyer_shopping_lists WHERE user_id='${f.buyer}'"))
            val expired=prepared.command.copy(basketChange=prepared.command.basketChange!!.copy(checkedAtMillis=1))
            assertFalse(shoppingTx(f){it.applyBasket(f.buyer,null,expired)}.accepted)
            assertEquals("2",scalar(c,"SELECT revision FROM buyer_shopping_lists WHERE user_id='${f.buyer}'"))
            val repository=MarketShoppingRepository(old,MarketplaceRepository(old){_,_->false})
            val failure=assertFailsWith<SQLException>{repository.applyBasket(f.buyer,null,expired)}
            assertEquals("40001",failure.sqlState);old.rollback()
            assertTrue(shoppingTx(f){it.applyBasket(f.buyer,null,expired)}.replayed)
        }
    }
    @Test fun basketApplyStoredOutcomesStayImmutableAndNoMixedShapeIsAllowed()=fixture { f,c ->
        val prepared=reviewedBasket(f,c);shoppingTx(f){it.applyBasket(f.buyer,null,prepared.command)}
        assertFailsWith<SQLException>{exec(c,"UPDATE buyer_shopping_commands SET accepted=FALSE WHERE command_id='${prepared.command.commandId}'")}
        assertFailsWith<SQLException>{exec(c,"""INSERT INTO buyer_shopping_commands
            (user_id,command_id,request_hash,offer_id,requested_units,expected_revision,accepted,applied_revision,created_at_millis)
            VALUES ('${f.buyer}','${UUID.randomUUID()}','${"0".repeat(64)}',NULL,0,3,TRUE,3,1)""")}
    }

    @Test fun basketApplyRechecksThePriceOfLinesKeepingTheirOriginalShop()=fixture { f,c ->
        val prepared=reviewedBasket(f,c);val secondItem=prepared.sources.last().goodsItemId
        exec(c,"""UPDATE stock_batches SET sale_price_override='{"price":"1000.00","currency":"KZT","supplierId":""}'
            WHERE goods_item_id='$secondItem' AND store_id='${f.sibling}'""")
        val command=requireNotNull(basket(f,revision=2).reviewedBasketCommand("KZT",MARKET_BASKET_LOWEST_ITEMS,UUID.randomUUID().toString()))
        assertEquals(1,command.basketChange!!.changedLines)
        val before=listIds(f,c)
        exec(c,"""UPDATE stock_batches SET sale_price_override='{"price":"200.00","currency":"KZT","supplierId":""}'
            WHERE goods_item_id='$secondItem' AND store_id='${f.branch}'""")
        val result=shoppingTx(f){it.applyBasket(f.buyer,null,command)}
        assertFalse(result.accepted);assertEquals("market.basket_apply_price_changed",result.errorKey);assertEquals(before,listIds(f,c))
    }
    @Test fun basketApplyPreservesAllOtherCurrencyLinesAndTheirMetadata()=fixture { f,c ->
        reviewedBasket(f,c)
        val usd=copyPublic(f,c,"USD product")
        batch(f.copy(item=UUID.fromString(usd.goodsItemId)),c,total=10.0)
        exec(c,"""UPDATE stock_items SET sale_prices='[{"price":"7.00","currency":"USD","supplierId":""}]'
            WHERE id='${usd.goodsItemId}'""")
        shoppingTx(f){it.apply(f.buyer,null,listCommand(f,usd,revision=2))}
        val plan=basket(f,revision=3);val old=plan.snapshot.lines.single { it.line.offerId==usd.id }.line
        val command=requireNotNull(plan.reviewedBasketCommand("KZT",MARKET_BASKET_ONE_SHOP,UUID.randomUUID().toString()))
        val result=shoppingTx(f){it.applyBasket(f.buyer,null,command)}
        assertTrue(result.accepted);assertEquals(4L,result.appliedRevision);assertEquals(3,result.snapshot.lines.size)
        assertEquals(old,result.snapshot.lines.single { it.line.offerId==usd.id }.line)
    }
    @Test fun basketApplyTargetBranchNeedsItsOwnLiveEntitlement()=fixture { f,c ->
        val prepared=reviewedBasket(f,c);val before=listIds(f,c)
        exec(c,"UPDATE store_subscription_states SET status='inactive' WHERE store_id='${f.sibling}'")
        val result=shoppingTx(f){it.applyBasket(f.buyer,null,prepared.command)}
        assertFalse(result.accepted);assertEquals("market.basket_apply_unavailable",result.errorKey);assertEquals(before,listIds(f,c))
    }

    private fun <T> shoppingReadTx(f: Fixture, block: (MarketShoppingRepository) -> T): T =
        DriverManager.getConnection(f.url,f.props).use { c ->
            c.autoCommit=false; c.isReadOnly=true; c.transactionIsolation=Connection.TRANSACTION_REPEATABLE_READ
            try {
                exec(c,"SET LOCAL search_path TO ${f.schema},public"); exec(c,"SET LOCAL statement_timeout='5s'")
                val result=block(MarketShoppingRepository(c,MarketplaceRepository(c){_,_->false}))
                c.commit(); result
            } catch(failure:Throwable) { c.rollback(); throw failure }
        }
    @Test fun activityAndAbsentLookupAreGenuinelyReadOnlyAndCreateNoList()=fixture { f,c ->
        val request=MarketShoppingCommand(UUID.randomUUID().toString(),0,UUID.randomUUID().toString(),0)
        val history=shoppingReadTx(f){it.activity(f.buyer,MarketShoppingActivityRequest())}
        assertTrue(history.entries.isEmpty());assertTrue(history.isValidShoppingActivityPage(f.buyer.toString(),history.request))
        assertNull(shoppingReadTx(f){it.lookup(f.buyer,request)}.outcome)
        assertEquals("0",scalar(c,"SELECT count(*) FROM buyer_shopping_lists"))
        assertEquals("0",scalar(c,"SELECT count(*) FROM buyer_shopping_commands"))
    }
    @Test fun resultLookupRecoversSuccessWithoutAnotherCommandOrRevision()=fixture { f,c ->
        val offer=published(f);batch(f,c);val command=listCommand(f,offer)
        val first=shoppingTx(f){it.apply(f.buyer,null,command)}
        val lookup=shoppingReadTx(f){it.lookup(f.buyer,command)}
        assertEquals(first.appliedRevision,lookup.outcome?.appliedRevision);assertTrue(lookup.outcome!!.replayed)
        assertEquals(first.snapshot.revision,lookup.outcome!!.snapshot.revision)
        assertEquals("1",scalar(c,"SELECT count(*) FROM buyer_shopping_commands"))
    }
    @Test fun historyAndLookupNeverExposeAnotherBuyersRecord()=fixture { f,c ->
        val offer=published(f);batch(f,c);val command=listCommand(f,offer)
        shoppingTx(f){it.apply(f.buyer,null,command)}
        assertNull(shoppingReadTx(f){it.lookup(f.owner,command)}.outcome)
        assertTrue(shoppingReadTx(f){it.activity(f.owner,MarketShoppingActivityRequest())}.entries.isEmpty())
        assertEquals(404,assertFailsWith<MarketFailure>{shoppingReadTx(f){it.activityDetail(f.owner,command.commandId)}}.status)
    }
    @Test fun readOnlyLookupRejectsSameIdentityWithDifferentBody()=fixture { f,c ->
        val offer=published(f);batch(f,c);val command=listCommand(f,offer)
        shoppingTx(f){it.apply(f.buyer,null,command)}
        assertEquals("market.shopping_command_mismatch",assertFailsWith<MarketFailure>{
            shoppingReadTx(f){it.lookup(f.buyer,command.copy(units=2))}
        }.key)
        assertEquals("1",scalar(c,"SELECT count(*) FROM buyer_shopping_commands"))
    }
    @Test fun historicalDetailsKeepPublicLabelsAfterSellerRenamesAndWithdraws()=fixture { f,c ->
        val offer=published(f);batch(f,c);val command=listCommand(f,offer)
        shoppingTx(f){it.apply(f.buyer,null,command)}
        exec(c,"UPDATE marketplace_listings SET title='New title',is_published=FALSE WHERE id='${offer.id}'")
        exec(c,"UPDATE marketplace_storefronts SET display_name='New shop' WHERE store_id='${f.branch}'")
        val page=shoppingReadTx(f){it.activity(f.buyer,MarketShoppingActivityRequest())}
        val entry=page.entries.single();assertTrue(entry.detailsRecorded);assertNull(entry.details)
        assertEquals("Public product",entry.previewTitle)
        val detail=shoppingReadTx(f){it.activityDetail(f.buyer,command.commandId)}
        assertEquals("Public product",detail.details!!.lines.single().after!!.title)
        assertEquals("Public shop",detail.details!!.lines.single().after!!.shopName)
        assertFalse(jsonBase.encodeToString(detail).contains("NEVER-PUBLIC"))
        assertFalse(jsonBase.encodeToString(detail).contains("request_hash"))
        assertFalse(jsonBase.encodeToString(detail).contains("session_id"))
    }
    @Test fun recoveredOutcomeReturnsCurrentListWhileHistoryRetainsOriginalQuantity()=fixture { f,c ->
        val offer=published(f);batch(f,c);val first=listCommand(f,offer)
        shoppingTx(f){it.apply(f.buyer,null,first)}
        shoppingTx(f){it.apply(f.buyer,null,listCommand(f,offer,revision=1,units=2))}
        val lookup=shoppingReadTx(f){it.lookup(f.buyer,first)}
        assertEquals(2,lookup.outcome!!.snapshot.lines.single().line.units)
        assertEquals(1,shoppingReadTx(f){it.activityDetail(f.buyer,first.commandId)}.details!!.lines.single().after!!.units)
    }
    @Test fun rejectedReadOutcomeRemainsARejectionAndHasNoFabricatedAfterLines()=fixture { f,c ->
        val offer=published(f);batch(f,c);val request=listCommand(f,offer,revision=99)
        assertFalse(shoppingTx(f){it.apply(f.buyer,null,request)}.accepted)
        val found=shoppingReadTx(f){it.lookup(f.buyer,request)}.outcome!!
        assertFalse(found.accepted);assertNull(found.appliedRevision)
        val detail=shoppingReadTx(f){it.activityDetail(f.buyer,request.commandId)}
        assertFalse(detail.accepted);assertFalse(detail.detailsRecorded);assertNull(detail.details)
        assertTrue(detail.isValidShoppingActivityEntry())
    }
    @Test fun wholeBasketCreatesOneCompleteHistoricalRecordAndRetryDoesNotDuplicateIt()=fixture { f,c ->
        val prepared=reviewedBasket(f,c)
        assertTrue(shoppingTx(f){it.applyBasket(f.buyer,null,prepared.command)}.accepted)
        assertTrue(shoppingReadTx(f){it.lookup(f.buyer,prepared.command)}.outcome!!.accepted)
        assertTrue(shoppingTx(f){it.applyBasket(f.buyer,null,prepared.command)}.replayed)
        val entries=shoppingReadTx(f){it.activity(f.buyer,MarketShoppingActivityRequest())}.entries.filter{it.kind==MARKET_ACTIVITY_BASKET}
        assertEquals(1,entries.size)
        val record=shoppingReadTx(f){it.activityDetail(f.buyer,prepared.command.commandId)}
        assertTrue(record.isValidShoppingActivityEntry());assertEquals(2,record.details!!.lines.size)
        assertEquals(prepared.sources.map{it.id}.toSet(),record.details!!.lines.map{it.before!!.offerId}.toSet())
        assertEquals(prepared.command.basketChange!!.lines.map{it.targetOfferId}.toSet(),record.details!!.lines.map{it.after!!.offerId}.toSet())
    }
    @Test fun expiredNeverRecordedReviewCheckDoesNotSubmitOrRecordRejection()=fixture { f,c ->
        val prepared=reviewedBasket(f,c)
        val command=prepared.command.copy(basketChange=prepared.command.basketChange!!.copy(checkedAtMillis=1))
        val before=listIds(f,c);val records=scalar(c,"SELECT count(*) FROM buyer_shopping_commands")
        assertNull(shoppingReadTx(f){it.lookup(f.buyer,command)}.outcome)
        assertEquals(before,listIds(f,c));assertEquals(records,scalar(c,"SELECT count(*) FROM buyer_shopping_commands"))
    }
    @Test fun activityWindowHasStableOrderAndAnExplicitMoreFlag()=fixture { f,c ->
        repeat(21){shoppingTx(f){r->r.apply(f.buyer,null,MarketShoppingCommand(UUID.randomUUID().toString(),99,UUID.randomUUID().toString(),0))}}
        val first=shoppingReadTx(f){it.activity(f.buyer,MarketShoppingActivityRequest())}
        assertEquals(20,first.entries.size);assertTrue(first.hasMore);assertTrue(first.isValidShoppingActivityPage(f.buyer.toString(),first.request))
        val next=shoppingReadTx(f){it.activity(f.buyer,MarketShoppingActivityRequest(40))}
        assertEquals(21,next.entries.size);assertFalse(next.hasMore);assertEquals(first.entries,next.entries.take(20))
        assertEquals("21",scalar(c,"SELECT count(*) FROM buyer_shopping_commands"))
    }
    @Test fun activityDetailsCannotBeUpdatedAfterTheyCommit()=fixture { f,c ->
        val offer=published(f);batch(f,c);val command=listCommand(f,offer)
        shoppingTx(f){it.apply(f.buyer,null,command)}
        assertFailsWith<SQLException>{exec(c,"UPDATE buyer_shopping_commands SET activity_details=NULL WHERE command_id='${command.commandId}'")}
        assertTrue(shoppingReadTx(f){it.activityDetail(f.buyer,command.commandId)}.detailsRecorded)
    }
    @Test fun noOpRemovalIsAnAcceptedRecordNotAnotherListRevision()=fixture { f,c ->
        val command=MarketShoppingCommand(UUID.randomUUID().toString(),0,UUID.randomUUID().toString(),0)
        assertTrue(shoppingTx(f){it.apply(f.buyer,null,command)}.accepted)
        val entry=shoppingReadTx(f){it.activityDetail(f.buyer,command.commandId)}
        assertTrue(entry.accepted);assertFalse(entry.changed);assertTrue(entry.details!!.lines.isEmpty())
        assertTrue(entry.isValidShoppingActivityEntry())
    }
    // Deterministic immutable command fixtures for PostgreSQL filtering/keyset tests only.
    // No production data and no fabricated product labels. Business effects are tested separately.
    private fun historyId(n: Int) = UUID.fromString("00000000-0000-0000-0000-${n.toString(16).padStart(12,'0')}")
    private fun historyRows(f:Fixture,c:Connection,ids:IntRange,time:Long=100L) {
        exec(c,"INSERT INTO buyer_shopping_lists(user_id,revision,created_at_millis,updated_at_millis) VALUES ('${f.buyer}',0,1,1) ON CONFLICT DO NOTHING")
        c.prepareStatement("""INSERT INTO buyer_shopping_commands
            (user_id,command_id,request_hash,offer_id,requested_units,expected_revision,accepted,error_key,applied_revision,created_at_millis)
            VALUES (?,?,'${"0".repeat(64)}',?,0,0,FALSE,'market.shopping_changed',NULL,?)""").use { statement ->
            for (n in ids) { statement.setObject(1,f.buyer);statement.setObject(2,historyId(n))
                statement.setObject(3,UUID.randomUUID());statement.setLong(4,time);statement.addBatch() }
            statement.executeBatch()
        }
    }
    @Test fun activitySearchEmptyIsReadOnlyAndCreatesNoList()=fixture { f,c ->
        val result=shoppingReadTx(f){it.activitySearch(f.buyer,MarketShoppingActivitySearchRequest())}
        assertTrue(result.entries.isEmpty());assertFalse(result.hasOlder || result.hasNewer)
        assertEquals("0",scalar(c,"SELECT count(*) FROM buyer_shopping_lists"))
    }
    @Test fun keysetActivityCanReadBeyondTwoHundredWithoutDuplicates()=fixture { f,c ->
        historyRows(f,c,1..241)
        var request=MarketShoppingActivitySearchRequest();val seen=mutableListOf<String>()
        while(true) {
            val result=shoppingReadTx(f){it.activitySearch(f.buyer,request)}
            assertTrue(result.isValidActivitySearchPage(f.buyer.toString(),request));assertTrue(result.entries.size<=20)
            seen+=result.entries.map{it.commandId};request=result.olderActivityRequest()?:break
        }
        assertEquals((241 downTo 1).map{historyId(it).toString()},seen);assertEquals(241,seen.distinct().size)
        assertEquals("241",scalar(c,"SELECT count(*) FROM buyer_shopping_commands"))
    }
    @Test fun newerActivityReturnsNearestPageNotAlwaysTheHead()=fixture { f,c ->
        historyRows(f,c,1..65)
        val request=MarketShoppingActivitySearchRequest(boundary=MarketShoppingActivityCursor(100,historyId(5).toString()),newer=true)
        val result=shoppingReadTx(f){it.activitySearch(f.buyer,request)}
        assertEquals((25 downTo 6).map{historyId(it).toString()},result.entries.map{it.commandId})
        assertTrue(result.hasNewer && result.hasOlder);assertTrue(result.isValidActivitySearchPage(f.buyer.toString(),request))
    }
    @Test fun activityCursorRetainsTimestampAndUuidTieBreaking()=fixture { f,c ->
        historyRows(f,c,1..25,time=100);historyRows(f,c,26..30,time=101)
        val first=shoppingReadTx(f){it.activitySearch(f.buyer,MarketShoppingActivitySearchRequest())}
        val second=shoppingReadTx(f){it.activitySearch(f.buyer,requireNotNull(first.olderActivityRequest()))}
        assertEquals((30 downTo 1).map{historyId(it).toString()},(first.entries+second.entries).map{it.commandId})
    }
    @Test fun newHeadRecordDoesNotShiftAnExistingOlderCursor()=fixture { f,c ->
        historyRows(f,c,1..45)
        val first=shoppingReadTx(f){it.activitySearch(f.buyer,MarketShoppingActivitySearchRequest())}
        val olderRequest=requireNotNull(first.olderActivityRequest())
        val before=shoppingReadTx(f){it.activitySearch(f.buyer,olderRequest)}
        historyRows(f,c,99..99,time=101)
        val after=shoppingReadTx(f){it.activitySearch(f.buyer,olderRequest)}
        assertEquals(before.entries,after.entries)
        assertEquals(historyId(99).toString(),shoppingReadTx(f){it.activitySearch(f.buyer,MarketShoppingActivitySearchRequest())}.entries.first().commandId)
    }
    @Test fun exactReferenceFindsOldRecordWithoutScanningOnlyTheFirstTwoHundred()=fixture { f,c ->
        historyRows(f,c,1..241)
        val request=MarketShoppingActivitySearchRequest(MarketShoppingActivityFilter(commandId=" ${historyId(1).toString().uppercase()} "))
        val result=shoppingReadTx(f){it.activitySearch(f.buyer,request)}
        assertEquals(historyId(1).toString(),result.entries.single().commandId)
        assertFalse(result.hasOlder || result.hasNewer);assertNull(result.entries.single().details)
    }
    @Test fun referenceAndCursorCannotReadAnotherBuyersActivity()=fixture { f,c ->
        historyRows(f,c,1..25)
        val reference=MarketShoppingActivitySearchRequest(MarketShoppingActivityFilter(commandId=historyId(1).toString()))
        assertTrue(shoppingReadTx(f){it.activitySearch(f.owner,reference)}.entries.isEmpty())
        val cursor=MarketShoppingActivitySearchRequest(boundary=MarketShoppingActivityCursor(100,historyId(20).toString()))
        assertTrue(shoppingReadTx(f){it.activitySearch(f.owner,cursor)}.entries.isEmpty())
        assertEquals("25",scalar(c,"SELECT count(*) FROM buyer_shopping_commands"))
    }
    @Test fun appliedRejectedAndNoOpFiltersUseActualRecordedOutcomes()=fixture { f,c ->
        val offer=published(f);batch(f,c)
        val add=listCommand(f,offer);shoppingTx(f){it.apply(f.buyer,null,add)}
        val noop=listCommand(f,offer,revision=1);shoppingTx(f){it.apply(f.buyer,null,noop)}
        val rejected=listCommand(f,offer,revision=99);shoppingTx(f){it.apply(f.buyer,null,rejected)}
        for((result,id) in listOf(MARKET_ACTIVITY_RESULT_APPLIED to add.commandId,
            MARKET_ACTIVITY_RESULT_UNCHANGED to noop.commandId,MARKET_ACTIVITY_RESULT_REJECTED to rejected.commandId)) {
            val input=MarketShoppingActivitySearchRequest(MarketShoppingActivityFilter(result=result))
            val page=shoppingReadTx(f){it.activitySearch(f.buyer,input)}
            assertEquals(id,page.entries.single().commandId);assertTrue(page.isValidActivitySearchPage(f.buyer.toString(),input))
        }
    }
    @Test fun kindAndResultFiltersCombineBeforePagination()=fixture { f,c ->
        val offer=published(f);batch(f,c);val add=listCommand(f,offer);shoppingTx(f){it.apply(f.buyer,null,add)}
        val removal=listCommand(f,offer,revision=1,units=0);shoppingTx(f){it.apply(f.buyer,null,removal)}
        val request=MarketShoppingActivitySearchRequest(MarketShoppingActivityFilter(MARKET_ACTIVITY_REMOVE,MARKET_ACTIVITY_RESULT_APPLIED))
        assertEquals(removal.commandId,shoppingReadTx(f){it.activitySearch(f.buyer,request)}.entries.single().commandId)
        val none=request.copy(filter=request.filter.copy(result=MARKET_ACTIVITY_RESULT_REJECTED))
        assertTrue(shoppingReadTx(f){it.activitySearch(f.buyer,none)}.entries.isEmpty())
    }
    @Test fun basketAndSingleOfferReplacementFiltersRemainSeparate()=fixture { f,c ->
        val prepared=reviewedBasket(f,c);shoppingTx(f){it.applyBasket(f.buyer,null,prepared.command)}
        val basket=MarketShoppingActivitySearchRequest(MarketShoppingActivityFilter(kind=MARKET_ACTIVITY_BASKET))
        assertEquals(prepared.command.commandId,shoppingReadTx(f){it.activitySearch(f.buyer,basket)}.entries.single().commandId)
        val replacement=basket.copy(filter=MarketShoppingActivityFilter(kind=MARKET_ACTIVITY_REPLACE))
        assertTrue(shoppingReadTx(f){it.activitySearch(f.buyer,replacement)}.entries.isEmpty())
    }
    @Test fun historicalSummaryStillUsesStoredPublicLabelsAndNoPrivateData()=fixture { f,c ->
        val offer=published(f);batch(f,c);val command=listCommand(f,offer);shoppingTx(f){it.apply(f.buyer,null,command)}
        exec(c,"UPDATE marketplace_listings SET title='A different current label' WHERE id='${offer.id}'")
        val page=shoppingReadTx(f){it.activitySearch(f.buyer,MarketShoppingActivitySearchRequest())}
        assertEquals("Public product",page.entries.single().previewTitle);assertNull(page.entries.single().details)
        val encoded=jsonBase.encodeToString(page)
        listOf("NEVER-PUBLIC","request_hash","session_id","A different current label").forEach { assertFalse(encoded.contains(it),it) }
    }

    private fun directory(f:Fixture,request:MarketShopDirectoryRequest=MarketShopDirectoryRequest()):MarketShopDirectoryResult =
        DriverManager.getConnection(f.url,f.props).use { c ->
            c.autoCommit=false;c.transactionIsolation=Connection.TRANSACTION_REPEATABLE_READ;c.isReadOnly=true
            try {
                exec(c,"SET LOCAL search_path TO ${f.schema},public");exec(c,"SET LOCAL statement_timeout='8s'")
                MarketShopDirectoryRepository(c).search(f.buyer,request).also { c.commit() }
            } catch(failure:Throwable) { c.rollback();throw failure }
        }
    @Test fun shopDirectoryInstallationPublishesNothingAndIncludesAnExplicitlyPublishedEmptyShop()=fixture { f,c ->
        assertTrue(directory(f).shops.isEmpty());storefront(f)
        val result=directory(f);assertEquals(1L,result.totalShops);assertEquals(0L,result.shops.single().publishedOffers)
        assertTrue(result.isValidShopDirectoryResult(f.buyer.toString(),result.request))
        assertEquals("0",scalar(c,"SELECT count(*) FROM transactions"))
    }
    @Test fun shopDirectoryNeverInheritsParentAccessAndClosesWithADisabledParent()=fixture { f,c ->
        storefront(f);assertEquals(1L,directory(f).totalShops)
        exec(c,"UPDATE store_subscription_states SET status='inactive' WHERE store_id='${f.branch}'")
        assertEquals(0L,directory(f).totalShops)
        exec(c,"UPDATE store_subscription_states SET status='active' WHERE store_id='${f.branch}'")
        exec(c,"UPDATE stores SET is_active=FALSE WHERE id='${f.root}'")
        assertEquals(0L,directory(f).totalShops)
    }
    @Test fun shopDirectoryRespectsStartTimedExpiryAndTheLifetimeShape()=fixture { f,c ->
        storefront(f)
        exec(c,"UPDATE store_subscription_states SET current_period_start_millis=9223372036854775806 WHERE store_id='${f.branch}'")
        assertTrue(directory(f).shops.isEmpty())
        exec(c,"UPDATE store_subscription_states SET current_period_start_millis=1,access_kind='timed',plan_id='basic',current_period_end_millis=2 WHERE store_id='${f.branch}'")
        assertTrue(directory(f).shops.isEmpty())
        exec(c,"UPDATE store_subscription_states SET access_kind='lifetime',plan_id='internal_lifetime',current_period_end_millis=NULL,auto_renew=FALSE WHERE store_id='${f.branch}'")
        assertEquals(1L,directory(f).totalShops)
    }
    @Test fun shopDirectoryCountsPublicOffersNotStockAndExcludesWithdrawnOrInactiveListings()=fixture { f,c ->
        published(f)
        assertEquals(1L,directory(f).shops.single().publishedOffers) // no batch: not a stock count
        exec(c,"UPDATE marketplace_listings SET is_published=FALSE")
        assertEquals(0L,directory(f).shops.single().publishedOffers)
        exec(c,"UPDATE marketplace_listings SET is_published=TRUE");exec(c,"UPDATE stock_items SET is_active=FALSE")
        assertEquals(0L,directory(f).shops.single().publishedOffers)
        exec(c,"UPDATE stock_items SET is_active=TRUE,store_id='${f.sibling}'")
        assertEquals(0L,directory(f).shops.single().publishedOffers)
    }
    @Test fun shopDirectorySearchesLiteralPublicNameAndAddressButNotPrivateProductNotes()=fixture { f,c ->
        storefront(f)
        c.prepareStatement("UPDATE marketplace_storefronts SET display_name=?,public_address=? WHERE store_id=?").use { q ->
            q.setString(1,"Save 50% _Shop");q.setString(2,"Door 'quote 17");q.setObject(3,f.branch);q.executeUpdate()
        }
        assertEquals(1L,directory(f,MarketShopDirectoryRequest("50% _shop 'quote","ASTANA")).totalShops)
        assertEquals(0L,directory(f,MarketShopDirectoryRequest("missing%","Astana")).totalShops)
        assertEquals(0L,directory(f,MarketShopDirectoryRequest("NEVER-PUBLIC","Astana")).totalShops)
        assertEquals(0L,directory(f,MarketShopDirectoryRequest(city="Almaty")).totalShops)
        assertEquals(1L,directory(f).totalShops)
    }
    @Test fun directoryWindowAndTotalAreDeterministicAndNeverLeakPrivateFields()=fixture { f,c ->
        storefront(f)
        exec(c,"""INSERT INTO stores(id,owner_user_ids) SELECT (md5('directory-store-'||n::text))::uuid,'["${f.owner}"]'::jsonb FROM generate_series(1,24) n;
            INSERT INTO store_subscription_states(store_id,owner_user_id,plan_id,status,access_kind,current_period_start_millis,auto_renew,renewal_price_minor)
                SELECT (md5('directory-store-'||n::text))::uuid,'${f.owner}','internal_lifetime','active','lifetime',1,FALSE,0 FROM generate_series(1,24) n;
            INSERT INTO marketplace_storefronts(store_id,display_name,city,public_address,is_published,revision,updated_by,updated_at_millis)
                SELECT (md5('directory-store-'||n::text))::uuid,'Same name','Astana','Public address',TRUE,1,'${f.owner}',1 FROM generate_series(1,24) n;""")
        val first=directory(f);val second=directory(f)
        assertEquals(25L,first.totalShops);assertEquals(20,first.shops.size);assertEquals(first.shops,second.shops)
        val expanded=directory(f,MarketShopDirectoryRequest(limit=40));assertEquals(25,expanded.shops.size)
        assertEquals(first.shops,expanded.shops.take(20));assertTrue(expanded.isValidShopDirectoryResult(f.buyer.toString(),expanded.request))
        val json=jsonBase.encodeToString(expanded)
        listOf("NEVER-PUBLIC","owner_user","supplierId","goodsItemId","quantity","supplyPrice","sessionId").forEach { assertFalse(json.contains(it),it) }
        assertEquals("1",scalar(c,"SELECT count(*) FROM pg_indexes WHERE schemaname='${f.schema}' AND indexname='marketplace_storefronts_public_name_order'"))
    }
    @Test fun cancellationOfExpiredUnrecordedBasketDoesNotApplyOrNeedTheOldCatalogue()=fixture { f,c ->
        val prepared=reviewedBasket(f,c);val before=listIds(f,c)
        val command=prepared.command.copy(basketChange=prepared.command.basketChange!!.copy(checkedAtMillis=1L))
        exec(c,"UPDATE marketplace_listings SET is_published=FALSE")
        val result=shoppingTx(f){it.cancel(f.buyer,null,command)}
        assertFalse(result.accepted);assertEquals("market.shopping_cancelled",result.errorKey);assertEquals(before,listIds(f,c))
        assertEquals("market.shopping_cancelled",shoppingTx(f){it.applyBasket(f.buyer,null,command)}.errorKey)
        val history=shoppingTx(f){it.activitySearch(f.buyer,MarketShoppingActivitySearchRequest(MarketShoppingActivityFilter(result=MARKET_ACTIVITY_RESULT_CANCELLED)))}
        assertEquals(listOf(command.commandId),history.entries.map { it.commandId })
    }
    @Test fun cancellationArchiveFailureRollsBackAndDoesNotBlockTheOriginalCommand()=fixture { f,c ->
        val source=published(f);batch(f,c);val command=listCommand(f,source)
        exec(c,"""CREATE FUNCTION fail_cancel() RETURNS trigger LANGUAGE plpgsql AS ${'$'}${'$'}
            BEGIN IF NEW.error_key='market.shopping_cancelled' THEN RAISE EXCEPTION 'Injected cancellation failure'; END IF; RETURN NEW; END ${'$'}${'$'};
            CREATE TRIGGER fail_cancel BEFORE INSERT ON buyer_shopping_commands FOR EACH ROW EXECUTE FUNCTION fail_cancel();""")
        assertFailsWith<SQLException>{shoppingTx(f){it.cancel(f.buyer,null,command)}}
        assertEquals("0",scalar(c,"SELECT count(*) FROM buyer_shopping_commands WHERE command_id='${command.commandId}'"))
        exec(c,"DROP TRIGGER fail_cancel ON buyer_shopping_commands")
        assertTrue(shoppingTx(f){it.apply(f.buyer,null,command)}.accepted)
    }

}

package kz.aita.server.subscriptions

import kz.aita.*
import org.junit.Assume.assumeTrue
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.Properties
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*

/** Opt-in real PostgreSQL tests, not an in-memory billing substitute.
 * AITA_BILLING_TEST_DB_URL must name a separately provisioned aita_test_* database.
 * Each test creates/drops only its own random schema and runs the actual V46 + V101 + V102 SQL.
 * This fixture tests the repository/transaction protocol, not Ktor authentication or all prior migrations.
 */
class SubscriptionRepositoryDatabaseTest {
    private val now = 1_735_689_600_000L
    private fun exec(c: Connection, sql: String) = c.createStatement().use { it.execute(sql) }
    private fun scalar(c: Connection, sql: String): String? = c.createStatement().use { statement ->
        statement.executeQuery(sql).use { rows -> if (rows.next()) rows.getString(1) else null }
    }
    private fun number(c: Connection, sql: String) = requireNotNull(scalar(c, sql)).toLong()
    private data class Fixture(val url: String, val props: Properties, val schema: String,
        val owner: UUID = UUID.randomUUID(), val anotherOwner: UUID = UUID.randomUUID(),
        val root: UUID = UUID.randomUUID(), val branch: UUID = UUID.randomUUID(), val secondBranch: UUID = UUID.randomUUID(),
        val anotherRoot: UUID = UUID.randomUUID(), val anotherOwnersRoot: UUID = UUID.randomUUID(),
        val management: UUID = UUID.randomUUID(), val anotherManagement: UUID = UUID.randomUUID(),
        val anotherOwnersManagement: UUID = UUID.randomUUID())
    private fun resource(name: String) = requireNotNull(javaClass.getResourceAsStream("/db/migration/$name"))
        .bufferedReader().use { it.readText() }
    private fun fixture(legacyRoot: Boolean = false, block: (Fixture, Connection) -> Unit) {
        val url = System.getenv("AITA_BILLING_TEST_DB_URL").orEmpty()
        assumeTrue("Set a dedicated AITA_BILLING_TEST_DB_URL to run billing PostgreSQL tests", url.isNotBlank())
        require(url.startsWith("jdbc:postgresql:"))
        val props = Properties().apply {
            System.getenv("AITA_BILLING_TEST_DB_USER")?.let { setProperty("user", it) }
            System.getenv("AITA_BILLING_TEST_DB_PASSWORD")?.let { setProperty("password", it) }
            setProperty("connectTimeout", "5"); setProperty("socketTimeout", "15")
            setProperty("ApplicationName", "aita-subscription-repository-test")
        }
        DriverManager.getConnection(url, props).use { c ->
            require(scalar(c, "SELECT current_database()")?.startsWith("aita_test_") == true) { "Refusing a non-test database" }
            val f = Fixture(url, props, "aita_billing_" + UUID.randomUUID().toString().replace("-", ""))
            exec(c, "CREATE SCHEMA ${f.schema}")
            try {
                exec(c, "SET search_path TO ${f.schema}, public")
                exec(c, "CREATE TABLE users (id UUID PRIMARY KEY, is_active BOOLEAN NOT NULL DEFAULT TRUE, country_locale TEXT NOT NULL DEFAULT 'kz')")
                exec(c, "CREATE TABLE stores (id UUID PRIMARY KEY, parent_store_id UUID REFERENCES stores(id), owner_user_ids JSONB NOT NULL, country_locales JSONB NOT NULL DEFAULT '[\"kz\"]', is_active BOOLEAN NOT NULL DEFAULT TRUE)")
                exec(c, "CREATE TABLE transactions (store_id UUID, time_millis BIGINT)")
                exec(c, "CREATE TABLE stock_items (store_id UUID, created_at_millis BIGINT)")
                exec(c, "CREATE TABLE stock_batches (store_id UUID, goods_item_id UUID, created_at_millis BIGINT)")
                exec(c, "INSERT INTO users(id) VALUES ('${f.owner}'), ('${f.anotherOwner}')")
                exec(c, "INSERT INTO stores(id, owner_user_ids) VALUES ('${f.management}', '[\"${f.owner}\"]'), ('${f.anotherManagement}', '[\"${f.owner}\"]'), ('${f.anotherOwnersManagement}', '[\"${f.anotherOwner}\"]')")
                exec(c, "INSERT INTO stores(id,parent_store_id,owner_user_ids) VALUES ('${f.root}','${f.management}','[]'), ('${f.branch}','${f.management}','[]'), ('${f.secondBranch}','${f.management}','[]'), ('${f.anotherRoot}','${f.anotherManagement}','[]'), ('${f.anotherOwnersRoot}','${f.anotherOwnersManagement}','[]')")
                exec(c, resource("V46__paging_user_finances_and_store_subscriptions.sql"))
                exec(c, "UPDATE user_wallets SET balance_minor=3000000")
                if (legacyRoot) exec(c, "INSERT INTO store_subscription_states(store_id,owner_user_id,plan_id,status,current_period_start_millis,current_period_end_millis,auto_renew,next_charge_at_millis) VALUES ('${f.root}','${f.owner}','standard_monthly_kzt','active',$now,${now + 99_000},TRUE,${now + 99_000})")
                exec(c, resource("V101__per_location_subscriptions_and_promocodes.sql"))
                exec(c, resource("V102__single_use_promo_archive.sql"))
                block(f, c)
            } finally {
                exec(c, "SET search_path TO public")
                exec(c, "DROP SCHEMA ${f.schema} CASCADE")
            }
        }
    }
    private fun <T> tx(f: Fixture, block: (SubscriptionRepository) -> T): T = DriverManager.getConnection(f.url, f.props).use { c ->
        c.autoCommit = false
        try {
            exec(c, "SET LOCAL search_path TO ${f.schema}, public")
            exec(c, "SET LOCAL statement_timeout='8s'"); exec(c, "SET LOCAL lock_timeout='5s'")
            block(SubscriptionRepository(c)).also { c.commit() }
        } catch (failure: Throwable) { c.rollback(); throw failure }
    }
    private fun quote(f: Fixture, store: UUID, code: String = "", at: Long = now) = tx(f) { repo ->
        repo.quote(requireNotNull(repo.lockLocation(store)), StoreSubscriptionQuoteRequestDataModel(store.toString(), promoCode = code), at)
    }
    private fun command(q: StoreSubscriptionQuoteDataModel, code: String = "", renew: Boolean = false) = StoreSubscriptionUpdateRequestDataModel(
        q.storeId, q.planId, autoRenew = renew, promoCode = code, commandId = UUID.randomUUID().toString(),
        expectedRevision = q.expectedRevision, expectedChargeMinor = q.chargeMinor, expectedCurrencyCode = q.currencyCode,
        expectedPriceVersion = q.priceVersion, quoteValidUntilMillis = q.validUntilMillis, expectedRegularPriceMinor = q.regularPriceMinor,
        expectedAccessKind = q.accessKind, expectedDurationMillis = q.durationMillis)
    private fun purchase(f: Fixture, request: StoreSubscriptionUpdateRequestDataModel, actor: UUID = f.owner, at: Long = now) = tx(f) { repo ->
        val location = requireNotNull(repo.lockLocation(UUID.fromString(request.storeId)))
        repo.update(location, actor, request, at)
        requireNotNull(repo.state(location.storeId))
    }
    private fun promo(c: Connection, code: String, kind: String, duration: Long? = null, percent: Int? = null,
        fixed: Long? = null, currency: String? = null, max: Long = 1, store: UUID? = null, owner: UUID? = null, until: Long? = null) {
        c.prepareStatement("INSERT INTO subscription_promocodes(code_hash,kind,duration_millis,discount_basis_points,discount_minor,currency_code,max_redemptions,bound_store_id,bound_owner_id,valid_until_millis) VALUES (?,?,?,?,?,?,?,?,?,?)").use { p ->
            listOf(subscriptionCodeHash(code), kind, duration, percent, fixed, currency, max, store, owner, until)
                .forEachIndexed { i, value -> p.setObject(i + 1, value) }
            p.executeUpdate()
        }
    }
    private fun balance(c: Connection, owner: UUID) = number(c, "SELECT balance_minor FROM user_wallets WHERE user_id='$owner'")
    private fun <T> parallel(a: () -> T, b: () -> T): List<T> {
        val pool = Executors.newFixedThreadPool(2); val start = CountDownLatch(1)
        try {
            val results = listOf(a, b).map { fn -> pool.submit(Callable { check(start.await(3, TimeUnit.SECONDS)); fn() }) }
            start.countDown(); return results.map { it.get(12, TimeUnit.SECONDS) }
        } finally { pool.shutdownNow(); check(pool.awaitTermination(16, TimeUnit.SECONDS)) }
    }

    @Test fun migrationPreservesAnExistingOperatingPeriodWithoutGrantingSiblingBranches() = fixture(legacyRoot = true) { f, _ ->
        tx(f) { repo ->
            val root = requireNotNull(repo.state(f.root))
            assertEquals("basic", root.planId); assertEquals(now + 99_000, root.currentPeriodEndMillis)
            assertTrue(repo.hasAccess(f.root, now)); assertFalse(repo.hasAccess(f.branch, now)); assertFalse(repo.hasAccess(f.secondBranch, now))
        }
    }
    @Test fun managementParentsCannotOpenBillingOrProduceQuotes() = fixture { f, c ->
        tx(f) { repository ->
            assertNull(repository.lockLocation(f.management))
            assertNull(repository.lockLocation(f.anotherManagement))
            assertNull(repository.lockLocation(f.anotherOwnersManagement))
        }
        assertFailsWith<IllegalArgumentException> { quote(f, f.management) }
        assertEquals(0L, number(c, "SELECT count(*) FROM store_subscription_states"))
        assertEquals(0L, number(c, "SELECT count(*) FROM store_subscription_charge_events"))
    }

    @Test fun basicBillsOnlyTheBranchAndUsesItsBillingOwnersWallet() = fixture { f, c ->
        val state = purchase(f, command(quote(f, f.branch)))
        assertTrue(state.grantsStoreAccess(f.branch.toString(), now)); assertEquals(2_201_000L, balance(c, f.owner))
        assertEquals(1L, number(c, "SELECT COUNT(*) FROM store_subscription_charge_events"))
        tx(f) { assertFalse(it.hasAccess(f.root, now)); assertFalse(it.hasAccess(f.secondBranch, now)) }
    }
    @Test fun lifetimePromoNeverDebitsAndNeverSchedulesRenewal() = fixture { f, c ->
        promo(c, "AITA-LIFETIME", "lifetime")
        val state = purchase(f, command(quote(f, f.branch, "AITA-LIFETIME"), "AITA-LIFETIME", renew = true))
        assertEquals("internal_lifetime", state.planId); assertNull(state.currentPeriodEndMillis); assertNull(state.nextChargeAtMillis)
        assertFalse(state.autoRenew); assertEquals(3_000_000L, balance(c, f.owner))
        assertTrue(state.grantsStoreAccess(f.branch.toString(), Long.MAX_VALUE))
        tx(f) { repo -> assertFalse(f.branch in repo.dueStoreIds(Long.MAX_VALUE)) }
    }
    @Test fun timedPromoHasExactlyItsConfiguredDurationAndNoPaidRenewal() = fixture { f, c ->
        promo(c, "AITA-TIMED", "timed", duration = 60_000)
        val state = purchase(f, command(quote(f, f.branch, "AITA-TIMED"), "AITA-TIMED", renew = true))
        assertEquals(now + 60_000, state.currentPeriodEndMillis); assertFalse(state.autoRenew)
        assertTrue(state.grantsStoreAccess(state.storeId, now + 59_999)); assertFalse(state.grantsStoreAccess(state.storeId, now + 60_000))
        assertEquals(3_000_000L, balance(c, f.owner))
    }
    @Test fun expiredTimedAccessIsPublishedOnceWithoutChargingOrExtendingIt() = fixture { f, c ->
        promo(c, "AITA-EXPIRY", "timed", duration = 60_000)
        purchase(f, command(quote(f, f.branch, "AITA-EXPIRY"), "AITA-EXPIRY"))
        assertFalse(tx(f) { it.expire(requireNotNull(it.lockLocation(f.branch)), now + 59_999) })
        assertTrue(tx(f) { f.branch in it.expiredStoreIds(now + 60_000) })
        assertTrue(tx(f) { it.expire(requireNotNull(it.lockLocation(f.branch)), now + 60_000) })
        assertFalse(tx(f) { it.expire(requireNotNull(it.lockLocation(f.branch)), now + 60_001) })
        assertEquals("inactive", tx(f) { it.state(f.branch)?.status })
        assertEquals(3_000_000L, balance(c, f.owner))
        assertEquals(1L, number(c, "SELECT COUNT(*) FROM store_subscription_charge_events"))
    }
    @Test fun lifetimeAccessNeverEntersTheExpirySweep() = fixture { f, c ->
        promo(c, "AITA-FOREVER", "lifetime")
        purchase(f, command(quote(f, f.branch, "AITA-FOREVER"), "AITA-FOREVER"))
        tx(f) { repo ->
            assertFalse(f.branch in repo.expiredStoreIds(Long.MAX_VALUE))
            assertFalse(repo.expire(requireNotNull(repo.lockLocation(f.branch)), Long.MAX_VALUE))
            assertTrue(repo.hasAccess(f.branch, Long.MAX_VALUE))
        }
    }
    @Test fun discountAffectsOnlyActivationAndPreservesConsentedRegularRenewal() = fixture { f, c ->
        promo(c, "AITA-QUARTER", "discount", percent = 2500)
        val state = purchase(f, command(quote(f, f.branch, "AITA-QUARTER"), "AITA-QUARTER", renew = true))
        assertEquals(2_400_750L, balance(c, f.owner)); assertEquals(799_000L, state.renewalPriceMinor); assertTrue(state.autoRenew)
        assertEquals(199_750L, number(c, "SELECT discount_applied_minor FROM subscription_promo_redemptions"))
    }
    @Test fun fixedDiscountCannotCrossCurrencyBoundaries() = fixture { f, c ->
        promo(c, "AITA-DOLLARS", "discount", fixed = 100, currency = "USD")
        assertFailsWith<SubscriptionFailure> { quote(f, f.branch, "AITA-DOLLARS") }
        assertEquals(3_000_000L, balance(c, f.owner)); assertEquals(0L, number(c, "SELECT COUNT(*) FROM subscription_promo_redemptions"))
    }
    @Test fun simultaneousSameCommandIsOneChargeAndOneEntitlement() = fixture { f, c ->
        val request = command(quote(f, f.branch))
        val results = parallel({ purchase(f, request) }, { purchase(f, request) })
        assertEquals(results.first().revision, results.last().revision); assertEquals(2_201_000L, balance(c, f.owner))
        assertEquals(1L, number(c, "SELECT COUNT(*) FROM subscription_commands"))
        assertEquals(1L, number(c, "SELECT COUNT(*) FROM user_wallet_ledger_entries"))
    }
    @Test fun changedContentCannotReplayAnExistingCommandId() = fixture { f, c ->
        val request = command(quote(f, f.branch)); purchase(f, request)
        assertEquals("subscription.command_conflict", assertFailsWith<SubscriptionFailure> { purchase(f, request.copy(autoRenew = true)) }.key)
        assertEquals(2_201_000L, balance(c, f.owner))
    }
    @Test fun competingDifferentCommandsCannotPurchaseTheSamePeriodTwice() = fixture { f, c ->
        val q = quote(f, f.branch); val first = command(q); val second = command(q)
        val results = parallel({ runCatching { purchase(f, first) } }, { runCatching { purchase(f, second) } })
        assertEquals(1, results.count { it.isSuccess }); assertEquals(2_201_000L, balance(c, f.owner))
    }
    @Test fun aGlobalSingleUseCodeCannotBeRedeemedByTwoDifferentRoots() = fixture { f, c ->
        promo(c, "AITA-ONE-ONLY", "lifetime")
        val a = command(quote(f, f.branch, "AITA-ONE-ONLY"), "AITA-ONE-ONLY")
        val b = command(quote(f, f.anotherOwnersRoot, "AITA-ONE-ONLY"), "AITA-ONE-ONLY")
        val results = parallel({ runCatching { purchase(f, a) } }, { runCatching { purchase(f, b, f.anotherOwner) } })
        assertEquals(1, results.count { it.isSuccess }); assertEquals(1L, number(c, "SELECT redemption_count FROM subscription_promocodes"))
        assertEquals(1L, number(c, "SELECT count(*) FROM used_subscription_promocodes"))
        assertEquals("false", scalar(c, "SELECT is_active::text FROM subscription_promocodes"))
    }
    @Test fun differentRootsSharingAWalletCannotBothSpendAnInsufficientBalance() = fixture { f, c ->
        exec(c, "UPDATE user_wallets SET balance_minor=1000000 WHERE user_id='${f.owner}'")
        val a = command(quote(f, f.branch)); val b = command(quote(f, f.anotherRoot))
        val results = parallel({ runCatching { purchase(f, a) } }, { runCatching { purchase(f, b) } })
        assertEquals(1, results.count { it.isSuccess }); assertEquals(201_000L, balance(c, f.owner))
    }
    @Test fun failureAtTheFinalCommandWriteRollsBackDebitGrantChargeAndPromoUse() = fixture { f, c ->
        promo(c, "AITA-ROLLBACK", "discount", percent = 2500)
        val request = command(quote(f, f.branch, "AITA-ROLLBACK"), "AITA-ROLLBACK")
        exec(c, "CREATE FUNCTION fail_command() RETURNS TRIGGER AS $$ BEGIN RAISE EXCEPTION 'injected test failure'; END; $$ LANGUAGE plpgsql")
        exec(c, "CREATE TRIGGER reject_command BEFORE INSERT ON subscription_commands FOR EACH ROW EXECUTE FUNCTION fail_command()")
        assertFailsWith<SQLException> { purchase(f, request) }
        assertEquals(3_000_000L, balance(c, f.owner)); assertEquals(0L, number(c, "SELECT COUNT(*) FROM store_subscription_charge_events"))
        assertEquals(0L, number(c, "SELECT redemption_count FROM subscription_promocodes"))
        assertEquals(0L, number(c, "SELECT count(*) FROM used_subscription_promocodes"))
        tx(f) { assertFalse(it.hasAccess(f.branch, now)) }
    }
    @Test fun activateNowFalseDoesNotCreateAFreePeriod() = fixture { f, c ->
        val q = quote(f, f.branch)
        val harmless = StoreSubscriptionUpdateRequestDataModel(f.branch.toString(), "", false, false,
            commandId = UUID.randomUUID().toString(), expectedRevision = q.expectedRevision)
        val state = purchase(f, harmless)
        assertFalse(state.grantsStoreAccess(state.storeId, now)); assertNull(state.currentPeriodEndMillis)
        assertEquals(3_000_000L, balance(c, f.owner))
        assertFailsWith<SubscriptionFailure> { purchase(f, harmless.copy(commandId = UUID.randomUUID().toString(), autoRenew = true, expectedRevision = state.revision)) }
    }
    @Test fun cancellingRenewalDoesNotChangeThePaidPeriodOrChargeAgain() = fixture { f, c ->
        val state = purchase(f, command(quote(f, f.branch), renew = true))
        val stopped = purchase(f, StoreSubscriptionUpdateRequestDataModel(state.storeId, state.planId, false, false,
            commandId = UUID.randomUUID().toString(), expectedRevision = state.revision))
        assertFalse(stopped.autoRenew); assertEquals(state.currentPeriodEndMillis, stopped.currentPeriodEndMillis)
        assertEquals(2_201_000L, balance(c, f.owner))
    }
    @Test fun simultaneousRenewalsHaveOnlyOneCommercialEffect() = fixture { f, c ->
        val state = purchase(f, command(quote(f, f.branch), renew = true)); val due = requireNotNull(state.nextChargeAtMillis)
        fun renew() = tx(f) { it.renew(requireNotNull(it.lockLocation(f.branch)), due) }
        assertEquals(1, parallel(::renew, ::renew).count { it })
        assertEquals(1_402_000L, balance(c, f.owner)); assertEquals(2L, number(c, "SELECT COUNT(*) FROM store_subscription_charge_events"))
    }
    @Test fun failedRenewalDoesNotBillAnOutageAndRetriesOnlyAfterItsBackoff() = fixture { f, c ->
        val state = purchase(f, command(quote(f, f.branch), renew = true)); val due = requireNotNull(state.nextChargeAtMillis)
        exec(c, "UPDATE user_wallets SET balance_minor=1 WHERE user_id='${f.owner}'")
        assertTrue(tx(f) { it.renew(requireNotNull(it.lockLocation(f.branch)), due) })
        assertFalse(tx(f) { it.hasAccess(f.branch, due) }); assertEquals(1L, balance(c, f.owner))
        exec(c, "UPDATE user_wallets SET balance_minor=1000000 WHERE user_id='${f.owner}'")
        assertFalse(tx(f) { it.renew(requireNotNull(it.lockLocation(f.branch)), due + 1) })
        val retry = due + 3_600_000
        assertTrue(tx(f) { it.renew(requireNotNull(it.lockLocation(f.branch)), retry) })
        assertEquals(retry, tx(f) { it.state(f.branch)?.currentPeriodStartMillis }); assertEquals(201_000L, balance(c, f.owner))
    }
    @Test fun changedRegionalPriceInvalidatesTheOldCheckoutQuote() = fixture { f, c ->
        val request = command(quote(f, f.branch))
        exec(c, "UPDATE store_subscription_plan_prices SET price_minor=899000 WHERE region_code='KZ'")
        assertEquals(2L, number(c, "SELECT price_version FROM store_subscription_plan_prices WHERE region_code='KZ'"))
        assertFailsWith<SubscriptionFailure> { purchase(f, request) }; assertEquals(3_000_000L, balance(c, f.owner))
    }
    @Test fun existingAgreedRenewalPriceDoesNotSilentlyIncreaseWithTheCatalogue() = fixture { f, c ->
        val state = purchase(f, command(quote(f, f.branch), renew = true))
        exec(c, "UPDATE store_subscription_plan_prices SET price_minor=999000 WHERE region_code='KZ'")
        tx(f) { it.renew(requireNotNull(it.lockLocation(f.branch)), requireNotNull(state.nextChargeAtMillis)) }
        assertEquals(1_402_000L, balance(c, f.owner))
    }
    @Test fun boundOrExpiredCodesCannotBeBorrowedByAnotherStore() = fixture { f, c ->
        promo(c, "AITA-BOUND", "lifetime", store = f.branch, owner = f.owner)
        promo(c, "AITA-EXPIRED", "lifetime", until = now)
        assertFailsWith<SubscriptionFailure> { quote(f, f.secondBranch, "AITA-BOUND") }
        assertFailsWith<SubscriptionFailure> { quote(f, f.branch, "AITA-EXPIRED") }
        assertEquals(0L, number(c, "SELECT COUNT(*) FROM subscription_promo_redemptions"))
    }
    @Test fun changingBillingOwnerPreservesPaidTimeButRemovesOldRenewalConsent() = fixture { f, c ->
        val state = purchase(f, command(quote(f, f.branch), renew = true))
        exec(c, "UPDATE stores SET owner_user_ids='[\"${f.anotherOwner}\"]' WHERE id='${f.management}'")
        val next = tx(f) { it.dashboard(requireNotNull(it.lockLocation(f.branch)), true, now).subscription }
        assertEquals(f.anotherOwner.toString(), next.ownerUserId); assertFalse(next.autoRenew)
        assertEquals(state.currentPeriodEndMillis, next.currentPeriodEndMillis)
        assertEquals(3_000_000L, balance(c, f.anotherOwner))
    }
    @Test fun usedArchiveRetainsActorLocationCommandAndFinancialFacts() = fixture { f, c ->
        promo(c, "ARCHIVE-ONE-USE", "discount", percent = 2500, store = f.branch, owner = f.owner)
        val request=command(quote(f,f.branch,"ARCHIVE-ONE-USE"),"ARCHIVE-ONE-USE")
        purchase(f,request)
        assertEquals(f.owner.toString(),scalar(c,"SELECT actor_user_id::text FROM used_subscription_promocodes"))
        assertEquals(f.owner.toString(),scalar(c,"SELECT billing_owner_user_id::text FROM used_subscription_promocodes"))
        assertEquals(f.branch.toString(),scalar(c,"SELECT store_id::text FROM used_subscription_promocodes"))
        assertEquals(request.commandId,scalar(c,"SELECT command_id::text FROM used_subscription_promocodes"))
        assertEquals(now,number(c,"SELECT first_used_at_millis FROM used_subscription_promocodes"))
        assertEquals(599250L,number(c,"SELECT charge_minor FROM used_subscription_promocodes"))
        assertEquals(199750L,number(c,"SELECT discount_applied_minor FROM used_subscription_promocodes"))
        assertEquals("KZT",scalar(c,"SELECT currency_code FROM used_subscription_promocodes"))
        assertEquals(64,requireNotNull(scalar(c,"SELECT request_hash FROM used_subscription_promocodes")).length)
    }
    @Test fun archiveAndUsedIdentityCannotBeRewrittenOrReactivated() = fixture { f,c ->
        promo(c,"IMMUTABLE-USE","lifetime")
        purchase(f,command(quote(f,f.branch,"IMMUTABLE-USE"),"IMMUTABLE-USE"))
        assertFailsWith<SQLException> { exec(c,"DELETE FROM used_subscription_promocodes") }
        assertFailsWith<SQLException> { exec(c,"UPDATE used_subscription_promocodes SET actor_user_id=NULL") }
        assertFailsWith<SQLException> { exec(c,"UPDATE subscription_promocodes SET redemption_count=0,is_active=TRUE") }
        assertFailsWith<SQLException> { exec(c,"DELETE FROM subscription_promocodes") }
        assertEquals(1L,number(c,"SELECT count(*) FROM used_subscription_promocodes"))
    }
    @Test fun maximumRedemptionCountCannotBeExpandedForNewCodes() = fixture { _,c ->
        assertFailsWith<SQLException> { promo(c,"ILLEGAL-MULTI","lifetime",max=2) }
        promo(c,"SINGLE-ONLY","lifetime")
        assertFailsWith<SQLException> { exec(c,"UPDATE subscription_promocodes SET max_redemptions=10") }
        assertFailsWith<SQLException> { exec(c,"UPDATE subscription_promocodes SET redemption_count=1,is_active=FALSE") }
    }
    @Test fun deletingABoundBranchDoesNotEraseTheUsedPromoArchive() = fixture { f,c ->
        promo(c,"BOUND-ARCHIVE","lifetime",store=f.branch)
        purchase(f,command(quote(f,f.branch,"BOUND-ARCHIVE"),"BOUND-ARCHIVE"))
        exec(c,"DELETE FROM stores WHERE id='${f.branch}'")
        assertEquals(1L,number(c,"SELECT count(*) FROM used_subscription_promocodes"))
        assertEquals(f.branch.toString(),scalar(c,"SELECT store_id::text FROM used_subscription_promocodes"))
        assertEquals(1L,number(c,"SELECT count(*) FROM subscription_promocodes WHERE NOT is_active"))
    }

}

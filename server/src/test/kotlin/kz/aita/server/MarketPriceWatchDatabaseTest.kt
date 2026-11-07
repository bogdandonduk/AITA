package kz.aita.server

import java.sql.DriverManager
import java.util.UUID
import kotlin.test.*
import kz.aita.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assume.assumeTrue

/** Exercise the actual permission and subscription checks, including parent-worker inheritance. */
class MarketPriceWatchDatabaseTest {
    private val owner = UUID.randomUUID()
    private val worker = UUID.randomUUID()
    private val outsider = UUID.randomUUID()
    private val parent = UUID.randomUUID()
    private val branch = UUID.randomUUID()
    private val sibling = UUID.randomUUID()
    private val rights = listOf(STORE_PERMISSION_STOCK_READ, STORE_PERMISSION_ANALYTICS_VIEW)

    private fun fixture(block: (Database) -> Unit) {
        val url = System.getenv("AITA_STORE_ARCH_TEST_DB_URL").orEmpty()
        assumeTrue("Requires an isolated aita_test_* database", url.isNotBlank())
        require(Regex("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/aita_test_[a-z0-9]+").matches(url))
        val username = System.getProperty("user.name")
        val schema = "price_permissions_" + UUID.randomUUID().toString().replace("-", "")
        DriverManager.getConnection(url, username, "").use { connection ->
            connection.createStatement().use { it.execute("CREATE SCHEMA $schema") }
        }
        val db = Database.connect("$url?currentSchema=$schema", driver = "org.postgresql.Driver", user = username, password = "")
        try {
            transaction(db) {
                SchemaUtils.create(Users, Stores, StoreUsers, StoreWorkerMemberships, StoreSubscriptionStates, Notifications)
                exec(requireNotNull(javaClass.getResourceAsStream("/db/migration/V143__personal_market_price_watches.sql")).bufferedReader().use { it.readText() })
                exec("ALTER TABLE store_subscription_states ADD access_kind TEXT DEFAULT 'lifetime', ADD region_code TEXT DEFAULT 'KZ', ADD renewal_price_minor BIGINT DEFAULT 0, ADD currency_code TEXT DEFAULT 'KZT', ADD revision BIGINT DEFAULT 1")
                listOf(owner, worker, outsider).forEachIndexed { index, uid -> Users.insert {
                    it[id] = uid; it[publicId] = "PRICEUSER$index"; it[phoneNumber] = "price$index"
                    it[email] = "price$index@example.invalid"; it[firstName] = "Test"; it[lastName] = "Prices"
                    it[countryLocale] = "kz"; it[passwordHash] = "fixture-only"
                } }
                listOf(parent, branch, sibling).forEachIndexed { index, sid -> Stores.insert {
                    it[id] = sid; it[publicId] = "PRICESTORE$index"
                    it[parentStoreId] = if (sid == parent) null else parent
                    it[branchType] = if (sid == sibling) StoreBranchType.INTERNET.name else StoreBranchType.PHYSICAL.name
                    it[ownerUserIds] = if (sid == parent) listOf(owner.toString()) else emptyList()
                    it[storeTypeIds] = emptyList(); it[name] = listOf(LocalizedStringDataModel("en", "Price store $index"))
                    it[alias] = emptyList(); it[description] = emptyList(); it[companyForms] = emptyList()
                    it[location] = LocationDataModel(); it[phoneNumbers] = emptyList(); it[emails] = emptyList(); it[countryLocales] = listOf("kz")
                } }
                listOf(branch, sibling).forEach { sid -> StoreSubscriptionStates.insert {
                    it[id] = UUID.randomUUID(); it[storeId] = sid; it[ownerUserId] = owner
                    it[status] = SUBSCRIPTION_STATUS_ACTIVE; it[planId] = "internal_lifetime"; it[currentPeriodStartMillis] = 1L
                } }
            }
            block(db)
        } finally {
            TransactionManager.closeAndUnregister(db)
            DriverManager.getConnection(url, username, "").use { connection ->
                connection.createStatement().use { it.execute("DROP SCHEMA $schema CASCADE") }
            }
        }
    }

    private fun employ(store: UUID, permissions: List<String> = rights): UUID = UUID.randomUUID().also { membership ->
        StoreWorkerMemberships.insert {
            it[id] = membership; it[storeId] = store; it[userId] = worker
            it[acceptedAtMillis] = 1L; it[acceptedByUserId] = owner; it[StoreWorkerMemberships.permissions] = permissions
        }
    }

    private val item = "33333333-3333-4333-8333-333333333333"
    private val code = "04006381333931"
    private val now = 1_800_000_000_000L
    private val settings = MarketPriceWatchSettings(items = listOf(MarketPriceWatchItem(item, code)))
    private fun command(revision: Long = 0, enabled: Boolean = true) = MarketPriceWatchUpdate(UUID.randomUUID().toString(), revision, enabled, settings)
    private fun report(user: UUID, store: UUID, scope: MarketPriceWatchSettings, at: Long, median: Long = 10000) =
        MarketPriceInsightsPage(user.toString(), store.toString(), scope.reportRequest(), scope.items.map { selected ->
            MarketPriceInsightRow(selected.itemId, listOf(LocalizedStringDataModel("en", "Watch tea")), listOf(selected.gtin),
                MarketShoppingBasis(selected.gtin, "KZT", "0", 1.0), ownPriceMinor = 12000, referenceKind = "shelf", compatibleGroups = 3,
                statistics = MarketPriceStatistics(3, median, median, median, median, median))
        }, totalItems = scope.items.size, checkedAtMillis = at)
    private fun start(user: UUID = owner) = savePriceWatchInsideTransaction(user, parent, command(), now) { u, s, scope, at -> report(u, s, scope, at) }
    private fun due(median: Long = 11000, at: Long = now + MARKET_PRICE_WATCH_INTERVAL, user: UUID = owner): Boolean =
        deliverPriceWatchInsideTransaction(user, parent, at) { u, s, scope, time -> report(u, s, scope, time, median) }

    @Test fun savesAreOwnedRevisionedAndRetryDoesNotResetScheduleOrBaseline() = fixture { db -> transaction(db) {
        val body = command(); var reads = 0
        val first = savePriceWatchInsideTransaction(owner, parent, body, now) { u, s, scope, at -> reads++; report(u, s, scope, at) }
        assertEquals(1L, first.revision); assertTrue(first.enabled); assertEquals(now + MARKET_PRICE_WATCH_INTERVAL, first.nextCheckAtMillis)
        assertTrue(first.isValidFor(owner.toString(), parent.toString()))
        val retry = savePriceWatchInsideTransaction(owner, parent, body, now + 1000) { _, _, _, _ -> error("Must not recompute a completed retry") }
        assertEquals(first, retry); assertEquals(1, reads); assertEquals(1L, MarketPriceWatches.selectAll().count())
        assertEquals("watch_conflict", assertFailsWith<MarketPriceWatchFailure> {
            savePriceWatchInsideTransaction(owner, parent, body.copy(enabled = false), now)
        }.kind)
        assertTrue(due())
        val observed = readPriceWatchInsideTransaction(owner, parent)
        val lateRetry = savePriceWatchInsideTransaction(owner, parent, body, now + MARKET_PRICE_WATCH_INTERVAL + 1000) {
            _, _, _, _ -> error("A late retry must preserve the baseline from a completed daily check")
        }
        assertEquals(observed, lateRetry)
        assertEquals(11000L, lateRetry.observations.single().baselineMinor)
    } }

    @Test fun incompatibleOrForeignStockAndInvalidScopeCannotCreateAWatchedItem() = fixture { db -> transaction(db) {
        assertEquals("watch_invalid", assertFailsWith<MarketPriceWatchFailure> {
            savePriceWatchInsideTransaction(owner, parent, command().copy(settings = settings.copy(country = "us")), now)
        }.kind)
        assertEquals("watch_invalid", assertFailsWith<MarketPriceWatchFailure> {
            savePriceWatchInsideTransaction(owner, parent, command(), now) { u, s, scope, at -> report(u, s, scope, at).copy(rows = emptyList(), totalItems = 0) }
        }.kind)
        assertEquals("access", assertFailsWith<MarketPriceWatchFailure> { savePriceWatchInsideTransaction(outsider, parent, command(), now) }.kind)
        assertEquals(0L, MarketPriceWatches.selectAll().count())
    } }

    @Test fun dueChangeNotifiesOnceAndAdvancesTheBaselineAtomically() = fixture { db ->
        transaction(db) { start(); assertFalse(due(at = now + 100)); assertEquals(0L, Notifications.selectAll().count()) }
        transaction(db) {
            assertTrue(due()); assertFalse(due())
            assertEquals(1L, Notifications.selectAll().count())
            val details = assertNotNull(Notifications.selectAll().single().toNotificationDataModel().marketPriceAlertDetails())
            assertEquals(10000L, details.changes.single().previousMinor)
            assertEquals(11000L, details.changes.single().currentMinor)
            assertEquals(now + MARKET_PRICE_WATCH_INTERVAL, details.checkedAtMillis)
            val state = readPriceWatchInsideTransaction(owner, parent)
            assertEquals(11000L, state.observations.single().baselineMinor)
            assertEquals(now + 2 * MARKET_PRICE_WATCH_INTERVAL, state.nextCheckAtMillis)
            assertEquals(1L, state.revision, "Scheduled observations do not invalidate a settings revision")
        }
        transaction(db) { assertFalse(due(at = now + 2 * MARKET_PRICE_WATCH_INTERVAL)); assertEquals(1L, Notifications.selectAll().count()) }
    }

    @Test fun subThresholdMovementAccumulatesAndAFallAlsoNotifies() = fixture { db ->
        transaction(db) { start() }
        transaction(db) { assertFalse(due(10300)); assertEquals(10000L, readPriceWatchInsideTransaction(owner, parent).observations.single().baselineMinor) }
        transaction(db) { assertTrue(due(10500, now + 2 * MARKET_PRICE_WATCH_INTERVAL)) }
        transaction(db) { assertTrue(due(9900, now + 3 * MARKET_PRICE_WATCH_INTERVAL)); assertEquals(2L, Notifications.selectAll().count()) }
    }

    @Test fun insufficientOrLimitedSamplesDoNotSendZeroOrPartialMarketAlerts() = fixture { db -> transaction(db) {
        start()
        assertFalse(deliverPriceWatchInsideTransaction(owner, parent, now + MARKET_PRICE_WATCH_INTERVAL) { u, s, scope, at ->
            report(u, s, scope, at).let { it.copy(rows = listOf(it.rows.single().copy(statistics = null, compatibleGroups = 2))) }
        })
        assertEquals(10000L, readPriceWatchInsideTransaction(owner, parent).observations.single().baselineMinor)
        assertFalse(deliverPriceWatchInsideTransaction(owner, parent, now + 2 * MARKET_PRICE_WATCH_INTERVAL) { u, s, scope, at ->
            report(u, s, scope, at, 20000).let { it.copy(rows = listOf(it.rows.single().copy(sampleLimited = true))) }
        })
        assertEquals(0L, Notifications.selectAll().count())
    } }

    @Test fun revokedWorkerPermissionDisablesTheWatchBeforeReadingPrivateStock() = fixture { db -> transaction(db) {
        val membership = employ(parent); start(worker)
        StoreWorkerMemberships.update({ StoreWorkerMemberships.id eq membership }) { it[isActive] = false }
        assertFalse(deliverPriceWatchInsideTransaction(worker, parent, now + MARKET_PRICE_WATCH_INTERVAL) { _, _, _, _ -> error("No report after revocation") })
        assertFalse(MarketPriceWatches.selectAll().single()[MarketPriceWatches.enabled])
        assertTrue(MarketPriceWatches.selectAll().single()[MarketPriceWatches.observations].isEmpty())
        assertEquals(0L, Notifications.selectAll().count())
    } }

    @Test fun disabledWatchAndStaleSettingsEditsCannotRestartDelivery() = fixture { db -> transaction(db) {
        start()
        val off = savePriceWatchInsideTransaction(owner, parent, command(1, false), now + 100)
        assertFalse(off.enabled); assertNull(off.nextCheckAtMillis); assertTrue(off.isValidFor(owner.toString(), parent.toString()))
        assertEquals("watch_conflict", assertFailsWith<MarketPriceWatchFailure> {
            savePriceWatchInsideTransaction(owner, parent, command(1), now + 200)
        }.kind)
        assertFalse(deliverPriceWatchInsideTransaction(owner, parent, now + MARKET_PRICE_WATCH_INTERVAL) { _, _, _, _ -> error("Disabled watch must not query") })
    } }

    @Test fun failedNotificationInsertDoesNotAdvanceBaselineAndRetryDeliversOnce() = fixture { db ->
        transaction(db) { start(); SchemaUtils.drop(Notifications) }
        assertFails { transaction(db) { maxAttempts = 1; due() } }
        transaction(db) {
            assertEquals(10000L, readPriceWatchInsideTransaction(owner, parent).observations.single().baselineMinor)
            SchemaUtils.create(Notifications)
            assertTrue(due()); assertEquals(1L, Notifications.selectAll().count())
        }
    }

    @Test fun twoSchedulerInstancesCannotDeliverTheSameChangeTwice() = fixture { db ->
        transaction(db) { start() }
        val workers = java.util.concurrent.Executors.newFixedThreadPool(2)
        val barrier = java.util.concurrent.CyclicBarrier(2)
        try {
            val results = (1..2).map { workers.submit<Boolean> {
                barrier.await(10, java.util.concurrent.TimeUnit.SECONDS)
                transaction(transactionIsolation = java.sql.Connection.TRANSACTION_READ_COMMITTED, db = db) { due() }
            } }.map { it.get(20, java.util.concurrent.TimeUnit.SECONDS) }
            assertEquals(1, results.count { it })
            transaction(db) { assertEquals(1L, Notifications.selectAll().count()) }
        } finally { workers.shutdownNow(); workers.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS) }
    }

    @Test fun productionRepeatableReadIsolationCannotCommitADuplicateAlertDuringARace() = fixture { db ->
        transaction(db) { start() }
        val workers = java.util.concurrent.Executors.newFixedThreadPool(2)
        val barrier = java.util.concurrent.CyclicBarrier(2)
        try {
            val results = (0..1).map { index -> workers.submit<String> {
                barrier.await(10, java.util.concurrent.TimeUnit.SECONDS)
                try {
                    transaction(transactionIsolation = java.sql.Connection.TRANSACTION_REPEATABLE_READ, db = db) {
                        maxAttempts = 1
                        if (due(at = now + MARKET_PRICE_WATCH_INTERVAL + index)) "delivered" else "already-checked"
                    }
                } catch (problem: org.jetbrains.exposed.exceptions.ExposedSQLException) {
                    // The second repeatable-read snapshot may predate the first commit.
                    // The watch-row lock rejects it before a second notification is inserted.
                    if (problem.sqlState == "40001") "retry-later" else throw problem
                }
            } }.map { it.get(20, java.util.concurrent.TimeUnit.SECONDS) }
            assertEquals(1, results.count { it == "delivered" })
            transaction(db) {
                assertEquals(1L, Notifications.selectAll().count())
                val state = readPriceWatchInsideTransaction(owner, parent)
                assertEquals(11000L, state.observations.single().baselineMinor)
                assertTrue(requireNotNull(state.nextCheckAtMillis) in
                    (now + 2 * MARKET_PRICE_WATCH_INTERVAL)..(now + 2 * MARKET_PRICE_WATCH_INTERVAL + 1))
            }
        } finally { workers.shutdownNow(); workers.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS) }
    }

    @Test fun actualMigrationRejectsOversizedObservationDocuments() = fixture { db ->
        transaction(db) { start() }
        assertFails { transaction(db) { maxAttempts = 1; exec("UPDATE market_price_watches SET observations=(SELECT jsonb_agg('{}'::jsonb) FROM generate_series(1,11))") } }
        transaction(db) { assertEquals(1, MarketPriceWatches.selectAll().single()[MarketPriceWatches.observations].size) }
    }
}

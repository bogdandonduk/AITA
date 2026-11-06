@file:OptIn(kotlin.time.ExperimentalTime::class)

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

class ReplenishmentDatabaseTest {
    private val owner = UUID.randomUUID()
    private val worker = UUID.randomUUID()
    private val store = UUID.randomUUID()
    private val other = UUID.randomUUID()
    private val item = UUID.randomUUID()
    private val foreign = UUID.randomUUID()
    private val supplier = UUID.randomUUID()
    private val membership = UUID.randomUUID()
    private val now = java.time.Instant.parse("2026-11-06T10:00:00Z").toEpochMilli()
    private val day = 86_400_000L

    private fun q(n: Double) = QuantityDataModel("0", emptyList(), n, 1.0, true)

    private val price = PriceDataModel("100", "KZT", "")

    private fun fixture(block: (Database) -> Unit) {
        val url = System.getenv("AITA_STORE_ARCH_TEST_DB_URL").orEmpty()
        assumeTrue("Requires an isolated aita_test_* database", url.isNotBlank())
        require(Regex("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/aita_test_[a-z0-9]+").matches(url))
        val username = System.getProperty("user.name")
        val schema = "planning_" + UUID.randomUUID().toString().replace("-", "")
        DriverManager.getConnection(url, username, "").use {
            it.createStatement().use { it.execute("CREATE SCHEMA $schema") }
        }
        val db =
            Database.connect(
                "$url?currentSchema=$schema",
                driver = "org.postgresql.Driver",
                user = username,
                password = "",
            )
        try {
            transaction(db) {
                SchemaUtils.create(
                    Users,
                    Stores,
                    StoreUsers,
                    StoreWorkerMemberships,
                    StockItems,
                    StockBatchesV2,
                    SupplierOrders,
                    SupplierOrderLines,
                    Transactions,
                    Notifications,
                    ReplenishmentReminders,
                )
                listOf(owner, worker).forEachIndexed { n, uid ->
                    Users.insert {
                        it[id] = uid
                        it[publicId] = "PLANUSER$n"
                        it[phoneNumber] = "plan$n"
                        it[email] = "plan$n@example.invalid"
                        it[firstName] = "Test"
                        it[lastName] = "Planner"
                        it[countryLocale] = "KZ"
                        it[passwordHash] = "fixture-only"
                    }
                }
                listOf(store, other).forEachIndexed { n, sid ->
                    Stores.insert {
                        it[id] = sid
                        it[publicId] = "PLANSTORE$n"
                        it[ownerUserIds] =
                            listOf(
                                if (sid == store) owner.toString() else UUID.randomUUID().toString()
                            )
                        it[storeTypeIds] = emptyList()
                        it[name] = listOf(LocalizedStringDataModel("en", "Store $n"))
                        it[alias] = emptyList()
                        it[description] = emptyList()
                        it[companyForms] = emptyList()
                        it[location] = LocationDataModel()
                        it[phoneNumbers] = emptyList()
                        it[emails] = emptyList()
                        it[countryLocales] = emptyList()
                    }
                }
                StoreWorkerMemberships.insert {
                    it[id] = membership
                    it[storeId] = store
                    it[userId] = worker
                    it[acceptedAtMillis] = 1L
                    it[acceptedByUserId] = owner
                    it[permissions] = listOf(STORE_PERMISSION_STOCK_READ)
                }
                listOf(item, foreign).forEach { gid ->
                    StockItems.insert {
                        it[id] = gid
                        it[userId] = owner
                        it[storeId] = if (gid == item) store else other
                        it[barcodes] = listOf("000123")
                        it[name] = listOf(LocalizedStringDataModel("en", "Tea"))
                        it[description] = emptyList()
                        it[measurementUnitId] = "0"
                        it[categoryIds] = emptyList()
                        it[salePrices] = listOf(price)
                        it[returnPrices] = listOf(price)
                        it[supplyPrices] = listOf(price)
                        it[isQuickItem] = false
                        it[imagePaths] = emptyList()
                        it[createdAtMillis] = 1L
                        it[updatedAtMillis] = 1L
                        it[isActive] = true
                    }
                }
                fun sale(sid: UUID, gid: UUID, qty: Double, at: Long, kind: String = "purchase") {
                    Transactions.insert {
                        it[id] = UUID.randomUUID()
                        it[userId] = owner
                        it[storeId] = sid
                        it[workshiftId] = 1
                        it[type] = kind
                        it[goodsInTransaction] =
                            listOf(
                                GoodsItemInTransactionDataModel(
                                    "000123",
                                    qty,
                                    100.0,
                                    goodsItemId = gid.toString(),
                                    quantityUnit = q(1.0),
                                )
                            )
                        it[paidCash] = 0.0
                        it[paidCard] = 0.0
                        it[cardPaymentOptionId] = 0
                        it[timeMillis] = at
                    }
                }
                sale(store, item, 14.0, now - day)
                sale(store, item, 14.0, now - 10 * day)
                sale(store, item, 7.0, now - day, "return")
                sale(store, item, 1000.0, now)
                sale(other, foreign, 9000.0, now - day)
                sale(store, item, 9000.0, now - 100 * day)
                for ((status, qty) in
                    listOf(
                        StockBatchStatusDataModel.OnShelf to 4.0,
                        StockBatchStatusDataModel.Reserved to 1000.0,
                    )) StockBatchesV2.insert {
                    it[id] = UUID.randomUUID()
                    it[goodsItemId] = item
                    it[userId] = owner
                    it[storeId] = store
                    it[quantity] = q(qty)
                    it[supplyPrice] = price
                    it[discounts] = emptyList()
                    it[shelfPriority] = 1
                    it[StockBatchesV2.status] = status.name
                    it[createdAtMillis] = 1L
                    it[updatedAtMillis] = 1L
                    it[isActive] = true
                }
                for (status in
                    listOf(
                        SupplierOrderStatusDataModel.PartiallyDelivered,
                        SupplierOrderStatusDataModel.Draft,
                    )) {
                    val oid = UUID.randomUUID()
                    SupplierOrders.insert {
                        it[id] = oid
                        it[userId] = owner
                        it[storeId] = store
                        it[supplierId] = supplier
                        it[orderedAtMillis] = now - day
                        it[confirmedDeliveryTimeMillis] = now + day
                        it[SupplierOrders.status] = status.name
                        it[createdAtMillis] = 1L
                        it[updatedAtMillis] = 1L
                        it[isActive] = true
                    }
                    SupplierOrderLines.insert {
                        it[id] = UUID.randomUUID()
                        it[orderId] = oid
                        it[goodsItemId] = item
                        it[requestedQuantity] = q(10.0)
                        it[receivedQuantityTotal] = 4.0
                        it[deliveredBatchIds] = emptyList()
                        it[isActive] = true
                    }
                }
            }
            block(db)
        } finally {
            TransactionManager.closeAndUnregister(db)
            DriverManager.getConnection(url, username, "").use {
                it.createStatement().use { it.execute("DROP SCHEMA $schema CASCADE") }
            }
        }
    }

    @Test
    fun realRowsRespectStoreCompletedDaysReturnsBatchesAndRemainingDeliveries() = fixture { db ->
        transaction(db) {
            val result =
                requireNotNull(replenishmentInsideTransaction(owner, store, 28, "Asia/Almaty", now))
            assertEquals(listOf(item.toString()), result.items.map { it.goodsItem.id })
            val row = result.suggest(ReplenishmentSettings()).single()
            assertEquals(28.0, row.item.sold)
            assertEquals(7.0, row.item.returned)
            assertEquals(4.0, row.usableStock)
            assertEquals(6.0, row.confirmedIncoming)
            assertEquals(8.0, row.suggestedQuantity)
            assertEquals(
                14.0,
                requireNotNull(replenishmentInsideTransaction(owner, store, 7, "Asia/Almaty", now))
                    .items
                    .single()
                    .sold,
            )
            assertNull(replenishmentInsideTransaction(owner, other, 28, "Asia/Almaty", now))
            assertEquals(6L, Transactions.selectAll().count(), "Reading a plan cannot alter sales")
            assertEquals(
                2L,
                SupplierOrders.selectAll().count(),
                "Reading a plan cannot create orders",
            )
        }
    }

    @Test
    fun workerNeedsAllThreePermissionsAndRevocationTakesEffectImmediately() = fixture { db ->
        transaction(db) {
            assertNull(replenishmentInsideTransaction(worker, store, 28, "Asia/Almaty", now))
            StoreWorkerMemberships.update({ StoreWorkerMemberships.id eq membership }) {
                it[permissions] =
                    listOf(
                        STORE_PERMISSION_STOCK_READ,
                        STORE_PERMISSION_ANALYTICS_VIEW,
                        STORE_PERMISSION_SUPPLIER_ORDERS_VIEW,
                    )
            }
            assertNotNull(replenishmentInsideTransaction(worker, store, 28, "Asia/Almaty", now))
            StoreWorkerMemberships.update({ StoreWorkerMemberships.id eq membership }) {
                it[isActive] = false
            }
            assertNull(replenishmentInsideTransaction(worker, store, 28, "Asia/Almaty", now))
        }
    }

    @Test
    fun operatingBranchWithLifetimeAccessSupportsReadOnlyPlanningAndInheritedWorkers() =
        fixture { db ->
            transaction(db) {
                Stores.update({ Stores.id eq other }) {
                    it[ownerUserIds] = listOf(owner.toString())
                }
                Stores.update({ Stores.id eq store }) { it[parentStoreId] = other }
                StoreWorkerMemberships.update({ StoreWorkerMemberships.id eq membership }) {
                    it[storeId] = other
                    it[permissions] =
                        listOf(
                            STORE_PERMISSION_STOCK_READ,
                            STORE_PERMISSION_ANALYTICS_VIEW,
                            STORE_PERMISSION_SUPPLIER_ORDERS_VIEW,
                        )
                }
                SchemaUtils.create(StoreSubscriptionStates)
                // Match the post-V101 fields used by the real subscription reader.
                exec(
                    "ALTER TABLE store_subscription_states ADD access_kind TEXT DEFAULT 'lifetime', ADD region_code TEXT DEFAULT 'KZ', ADD renewal_price_minor BIGINT DEFAULT 0, ADD currency_code TEXT DEFAULT 'KZT', ADD revision BIGINT DEFAULT 1"
                )
                StoreSubscriptionStates.insert {
                    it[id] = UUID.randomUUID()
                    it[storeId] = store
                    it[ownerUserId] = owner
                    it[status] = SUBSCRIPTION_STATUS_ACTIVE
                    it[planId] = "internal_lifetime"
                    it[currentPeriodStartMillis] = 1L
                }
            }
            transaction(
                transactionIsolation = java.sql.Connection.TRANSACTION_REPEATABLE_READ,
                readOnly = true,
                db = db,
            ) {
                for (user in listOf(owner, worker)) {
                    val result =
                        requireNotNull(
                            replenishmentInsideTransaction(user, store, 28, "Asia/Almaty", now)
                        )
                    assertEquals(listOf(item.toString()), result.items.map { it.goodsItem.id })
                    assertEquals(28.0, result.items.single().sold)
                    assertEquals(7.0, result.items.single().returned)
                }
            }
            transaction(db) {
                StoreSubscriptionStates.update({ StoreSubscriptionStates.storeId eq store }) {
                    it[status] = SUBSCRIPTION_STATUS_INACTIVE
                }
            }
            transaction(
                transactionIsolation = java.sql.Connection.TRANSACTION_REPEATABLE_READ,
                readOnly = true,
                db = db,
            ) {
                assertNull(replenishmentInsideTransaction(owner, store, 28, "Asia/Almaty", now))
                assertNull(replenishmentInsideTransaction(worker, store, 28, "Asia/Almaty", now))
            }
        }

    @Test
    fun remindersPersistReplayAndDeliverOnlyOnceWithoutMakingOrders() = fixture { db ->
        transaction(db) {
            val settings =
                ReplenishmentSettings(event = "new_year", arrivalDate = "2027-01-01", leadDays = 7)
            val body =
                ReplenishmentReminderUpdate(
                    UUID.randomUUID().toString(),
                    0,
                    true,
                    settings,
                    "Asia/Almaty",
                    3,
                )
            val saved = saveReminderInsideTransaction(owner, store, body, now)
            assertEquals(1L, saved.revision)
            assertTrue(saved.enabled)
            assertEquals(
                saved,
                saveReminderInsideTransaction(owner, store, body, now + 1),
                "Lost responses replay the original update",
            )
            assertEquals(saved, readReminderInsideTransaction(owner, store))
            assertTrue(
                deliverPurchasingRemindersInsideTransaction(saved.dueAtMillis!! - 1).isEmpty()
            )
            assertEquals(
                setOf(owner),
                deliverPurchasingRemindersInsideTransaction(saved.dueAtMillis!!),
            )
            val notice = Notifications.selectAll().single()
            assertEquals(owner, notice[Notifications.userId])
            assertEquals(store, notice[Notifications.storeId])
            assertEquals("purchasing", notice[Notifications.category])
            val renderedNotice = notice.toNotificationDataModel()
            assertContains(renderedNotice.message, "2027-01-01")
            assertContains(
                requireNotNull(EventMessages.render(renderedNotice.messageTemplate, "ru")),
                "2027-01-01",
            )
            Notifications.update({ Notifications.id eq notice[Notifications.id] }) {
                it[readAtMillis] = saved.dueAtMillis!! + 1
            }
            assertTrue(
                deliverPurchasingRemindersInsideTransaction(saved.dueAtMillis!! + 60_000).isEmpty()
            )
            assertEquals(1L, Notifications.selectAll().count())
            assertNotNull(
                Notifications.selectAll().single()[Notifications.readAtMillis],
                "A replay must not mark an already-read notification unread",
            )
            assertEquals(
                2L,
                SupplierOrders.selectAll().count(),
                "A reminder never orders stock automatically",
            )
        }
    }

    @Test
    fun remindersUseRevisionsAndCannotReadAnotherStoreOrMasqueradeAsAnotherUser() = fixture { db ->
        transaction(db) {
            val body =
                ReplenishmentReminderUpdate(
                    UUID.randomUUID().toString(),
                    0,
                    true,
                    ReplenishmentSettings(event = "custom", arrivalDate = "2026-12-01"),
                    "Asia/Almaty",
                )
            val saved = saveReminderInsideTransaction(owner, store, body, now)
            assertEquals(
                "reminder_conflict",
                assertFailsWith<ReplenishmentReminderFailure> {
                        saveReminderInsideTransaction(
                            owner,
                            store,
                            body.copy(requestId = UUID.randomUUID().toString()),
                            now,
                        )
                    }
                    .kind,
            )
            assertEquals(
                "reminder_conflict",
                assertFailsWith<ReplenishmentReminderFailure> {
                        saveReminderInsideTransaction(
                            owner,
                            store,
                            body.copy(daysBeforeOrder = 5),
                            now,
                        )
                    }
                    .kind,
            )
            assertEquals(
                "access",
                assertFailsWith<ReplenishmentReminderFailure> {
                        readReminderInsideTransaction(owner, other)
                    }
                    .kind,
            )
            assertEquals(
                "access",
                assertFailsWith<ReplenishmentReminderFailure> {
                        readReminderInsideTransaction(worker, store)
                    }
                    .kind,
            )
            val disabled =
                saveReminderInsideTransaction(
                    owner,
                    store,
                    body.copy(
                        requestId = UUID.randomUUID().toString(),
                        expectedRevision = saved.revision,
                        enabled = false,
                    ),
                    now + 1,
                )
            assertFalse(disabled.enabled)
            assertNull(disabled.dueAtMillis)
            assertTrue(deliverPurchasingRemindersInsideTransaction(now + 300 * day).isEmpty())
        }
    }

    @Test
    fun revokedWorkersDoNotReceiveDelayedStoreReminders() = fixture { db ->
        transaction(db) {
            StoreWorkerMemberships.update({ StoreWorkerMemberships.id eq membership }) {
                it[permissions] =
                    listOf(
                        STORE_PERMISSION_STOCK_READ,
                        STORE_PERMISSION_ANALYTICS_VIEW,
                        STORE_PERMISSION_SUPPLIER_ORDERS_VIEW,
                    )
            }
            val body =
                ReplenishmentReminderUpdate(
                    UUID.randomUUID().toString(),
                    0,
                    true,
                    ReplenishmentSettings(event = "custom", arrivalDate = "2026-11-07"),
                    "Asia/Almaty",
                )
            saveReminderInsideTransaction(worker, store, body, now)
            StoreWorkerMemberships.update({ StoreWorkerMemberships.id eq membership }) {
                it[isActive] = false
            }
            assertTrue(deliverPurchasingRemindersInsideTransaction(now + 1).isEmpty())
            assertEquals(0L, Notifications.selectAll().count())
            assertFalse(ReplenishmentReminders.selectAll().single()[ReplenishmentReminders.enabled])
        }
    }

    @Test
    fun inactiveUsersCannotReadSaveOrReceivePurchasingReminders() = fixture { db ->
        transaction(db) {
            val body =
                ReplenishmentReminderUpdate(
                    UUID.randomUUID().toString(),
                    0,
                    true,
                    ReplenishmentSettings(event = "custom", arrivalDate = "2026-11-07"),
                    "Asia/Almaty",
                )
            saveReminderInsideTransaction(owner, store, body, now)
            Users.update({ Users.id eq owner }) { it[isActive] = false }
            assertNull(replenishmentInsideTransaction(owner, store, 28, "Asia/Almaty", now))
            assertEquals(
                "access",
                assertFailsWith<ReplenishmentReminderFailure> {
                        readReminderInsideTransaction(owner, store)
                    }
                    .kind,
            )
            assertEquals(
                "access",
                assertFailsWith<ReplenishmentReminderFailure> {
                        saveReminderInsideTransaction(owner, store, body, now)
                    }
                    .kind,
            )
            assertTrue(deliverPurchasingRemindersInsideTransaction(now + 1).isEmpty())
            assertEquals(0L, Notifications.selectAll().count())
            assertFalse(ReplenishmentReminders.selectAll().single()[ReplenishmentReminders.enabled])
        }
    }

    @Test
    fun invalidRemindersNeverLeavePartialRows() = fixture { db ->
        transaction(db) {
            val body =
                ReplenishmentReminderUpdate(
                    UUID.randomUUID().toString(),
                    0,
                    true,
                    ReplenishmentSettings(),
                    "Asia/Almaty",
                )
            assertEquals(
                "reminder_invalid",
                assertFailsWith<ReplenishmentReminderFailure> {
                        saveReminderInsideTransaction(owner, store, body, now)
                    }
                    .kind,
            )
            assertEquals(0L, ReplenishmentReminders.selectAll().count())
        }
    }

    @Test
    fun realMigrationSupportsTheReminderAndEnforcesItsConstraints() = fixture { db ->
        transaction(db) {
            SchemaUtils.drop(ReplenishmentReminders)
            val migration =
                javaClass.getResource("/db/migration/V142__purchasing_reminders.sql")!!.readText()
            migration.split(';').map(String::trim).filter(String::isNotBlank).forEach { exec(it) }
            val saved =
                saveReminderInsideTransaction(
                    owner,
                    store,
                    ReplenishmentReminderUpdate(
                        UUID.randomUUID().toString(),
                        0,
                        true,
                        ReplenishmentSettings(event = "new_year", arrivalDate = "2027-01-01"),
                        "Asia/Almaty",
                    ),
                    now,
                )
            assertTrue(saved.enabled)
            Users.deleteWhere { id eq owner }
            assertEquals(
                0L,
                ReplenishmentReminders.selectAll().count(),
                "Deleting an account removes its reminders",
            )
        }
    }

    @Test
    fun overlappingReminderJobsCommitOnlyOneNotification() = fixture { db ->
        transaction(db) {
            saveReminderInsideTransaction(
                owner,
                store,
                ReplenishmentReminderUpdate(
                    UUID.randomUUID().toString(),
                    0,
                    true,
                    ReplenishmentSettings(event = "custom", arrivalDate = "2026-11-07"),
                    "Asia/Almaty",
                ),
                now,
            )
        }
        val firstDelivery = java.util.concurrent.CountDownLatch(1)
        val secondStarted = java.util.concurrent.CountDownLatch(1)
        val releaseCommit = java.util.concurrent.CountDownLatch(1)
        val jobs = java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            val first =
                jobs.submit<Set<UUID>> {
                    transaction(db) {
                        exec("SET LOCAL statement_timeout = '10000ms'")
                        val recipients = deliverPurchasingRemindersInsideTransaction(now + 1)
                        firstDelivery.countDown()
                        check(releaseCommit.await(10, java.util.concurrent.TimeUnit.SECONDS))
                        recipients
                    }
                }
            assertTrue(firstDelivery.await(10, java.util.concurrent.TimeUnit.SECONDS))
            val second =
                jobs.submit<Set<UUID>> {
                    transaction(db) {
                        exec("SET LOCAL statement_timeout = '10000ms'")
                        secondStarted.countDown()
                        deliverPurchasingRemindersInsideTransaction(now + 1)
                    }
                }
            assertTrue(secondStarted.await(10, java.util.concurrent.TimeUnit.SECONDS))
            releaseCommit.countDown()
            assertEquals(setOf(owner), first.get(15, java.util.concurrent.TimeUnit.SECONDS))
            assertTrue(second.get(15, java.util.concurrent.TimeUnit.SECONDS).isEmpty())
            transaction(db) {
                assertEquals(1L, Notifications.selectAll().count())
                assertNotNull(
                    ReplenishmentReminders.selectAll()
                        .single()[ReplenishmentReminders.firedAtMillis]
                )
            }
        } finally {
            releaseCommit.countDown()
            jobs.shutdownNow()
            jobs.awaitTermination(15, java.util.concurrent.TimeUnit.SECONDS)
        }
    }

    @Test
    fun aLongServerOutageDoesNotSendRemindersForAnAlreadyPassedSeason() = fixture { db ->
        transaction(db) {
            saveReminderInsideTransaction(
                owner,
                store,
                ReplenishmentReminderUpdate(
                    UUID.randomUUID().toString(),
                    0,
                    true,
                    ReplenishmentSettings(event = "custom", arrivalDate = "2026-11-07"),
                    "Asia/Almaty",
                ),
                now,
            )
            assertTrue(deliverPurchasingRemindersInsideTransaction(now + 3 * day).isEmpty())
            assertEquals(0L, Notifications.selectAll().count())
            assertFalse(ReplenishmentReminders.selectAll().single()[ReplenishmentReminders.enabled])
        }
    }
}

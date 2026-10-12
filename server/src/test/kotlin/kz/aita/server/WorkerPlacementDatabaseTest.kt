package kz.aita.server

import kz.aita.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assume.assumeTrue
import java.sql.DriverManager
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.*

class WorkerPlacementDatabaseTest {
    private val owner = UUID.randomUUID()
    private val worker = UUID.randomUUID()
    private val root = UUID.randomUUID()
    private val first = UUID.randomUUID()
    private val second = UUID.randomUUID()
    private val stranger = UUID.randomUUID()
    private val membership = UUID.randomUUID()
    private fun fixture(parentEmployment: Boolean = false, block: (Database) -> Unit) {
        val url = System.getenv("AITA_STORE_ARCH_TEST_DB_URL").orEmpty()
        assumeTrue(url.isNotBlank())
        require(Regex("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/aita_test_[a-z0-9]+").matches(url))
        val user = System.getProperty("user.name"); val schema = "workers_" + UUID.randomUUID().toString().replace("-", "")
        DriverManager.getConnection(url, user, "").use { it.createStatement().use { it.execute("CREATE SCHEMA $schema") } }
        val db = Database.connect("$url?currentSchema=$schema", driver = "org.postgresql.Driver", user = user, password = "")
        try {
            transaction(db) {
                SchemaUtils.create(Users, Stores, StoreUsers, StoreWorkerMemberships, StoreWorkerRequests, Workshifts, OperationLogs, Notifications)
                val subscriptionMigration = javaClass.getResource("/db/migration/V46__paging_user_finances_and_store_subscriptions.sql")!!.readText()
                exec("CREATE TABLE store_subscription_states (" + subscriptionMigration.substringAfter("CREATE TABLE IF NOT EXISTS store_subscription_states (").substringBefore("CREATE TABLE IF NOT EXISTS store_subscription_charge_events"))
                exec("ALTER TABLE store_subscription_states ADD access_kind TEXT DEFAULT 'lifetime', ADD region_code TEXT DEFAULT 'KZ', ADD renewal_price_minor BIGINT DEFAULT 0, ADD currency_code TEXT DEFAULT 'KZT', ADD revision BIGINT DEFAULT 1")
                exec(javaClass.getResource("/db/migration/V136__worker_placements.sql")!!.readText())
                listOf(owner, worker).forEachIndexed { n, id -> Users.insert {
                    it[Users.id] = id; it[publicId] = "WORKER$n"; it[phoneNumber] = "worker$n"; it[email] = "worker$n@example.invalid"
                    it[firstName] = "Test"; it[lastName] = "Worker"; it[countryLocale] = "KZ"; it[passwordHash] = "test-only"
                } }
                listOf(root, first, second, stranger).forEachIndexed { n, id -> Stores.insert {
                    it[Stores.id] = id; it[publicId] = "STORE$n"; it[ownerUserIds] = listOf(owner.toString()); it[storeTypeIds] = emptyList()
                    it[name] = listOf(LocalizedStringDataModel("en", "Store $n")); it[alias] = emptyList(); it[description] = emptyList(); it[companyForms] = emptyList()
                    it[location] = LocationDataModel(); it[phoneNumbers] = emptyList(); it[emails] = emptyList(); it[countryLocales] = emptyList()
                    it[parentStoreId] = if (id in listOf(first, second)) root else null
                } }
                val source = if (parentEmployment) root else first
                StoreWorkerMemberships.insert {
                    it[id] = membership; it[storeId] = source; it[userId] = worker; it[acceptedAtMillis] = 100L; it[acceptedByUserId] = owner
                    it[permissions] = listOf(STORE_PERMISSION_STOCK_READ); it[jobTitle] = "Clerk"; it[salary] = "100000"
                    it[workshiftPasswordHash] = "existing-hash"
                }
                StoreUsers.insert { it[storeId] = source; it[userId] = worker }
                listOf(first, second).forEach { branch -> exec("INSERT INTO store_subscription_states(store_id,owner_user_id,status,plan_id,current_period_start_millis) VALUES ('$branch','$owner','active','internal_lifetime',1)") }
                Users.update({ Users.id eq worker }) { it[activeStoreId] = source }
            }
            block(db)
        } finally {
            TransactionManager.closeAndUnregister(db)
            DriverManager.getConnection(url, user, "").use { it.createStatement().use { it.execute("DROP SCHEMA $schema CASCADE") } }
        }
    }
    private fun command(source: UUID = first, target: UUID = second) = WorkerPlacementCommand(UUID.randomUUID().toString(), membership.toString(), source.toString(), target.toString())
    @Test fun movingPreservesOldHistoryChangesAccessAndRestoresTheWorkersSelectedBranch() = fixture { db ->
        val c = command()
        val result = transaction(db) { placeWorkerInsideTransaction(owner, c) }
        assertEquals(result, transaction(db) { placeWorkerInsideTransaction(owner, c) })
        transaction(db) {
            assertFalse(StoreWorkerMemberships.selectAll().where { StoreWorkerMemberships.id eq membership }.single()[StoreWorkerMemberships.isActive])
            assertEquals(2L, StoreWorkerMemberships.selectAll().count())
            assertFalse(userHasStoreAccessInsideTransaction(worker, first)); assertTrue(userHasStoreAccessInsideTransaction(worker, second))
            assertFalse(userHasStoreAccessInsideTransaction(worker, root))
            assertEquals(second, Users.selectAll().where { Users.id eq worker }.single()[Users.activeStoreId])
            assertEquals(2L, OperationLogs.selectAll().count()); assertEquals(1L, WorkerPlacementCommands.selectAll().count())
            assertEquals(1L, Notifications.selectAll().count())
            assertEquals("Clerk", result.jobTitle); assertEquals("100000", result.salary); assertTrue(result.hasWorkshiftPassword)
        }
    }
    @Test fun assigningFromParentRetainsTheParentAndAllBranchAccess() = fixture(true) { db ->
        transaction(db) {
            placeWorkerInsideTransaction(owner, command(root))
            assertTrue(StoreWorkerMemberships.selectAll().where { StoreWorkerMemberships.id eq membership }.single()[StoreWorkerMemberships.isActive])
            listOf(root, first, second).forEach { assertTrue(userHasStoreAccessInsideTransaction(worker, it)) }
            assertEquals(root, Users.selectAll().where { Users.id eq worker }.single()[Users.activeStoreId])
        }
    }
    @Test fun cannotMoveAcrossCompaniesOrIntoParentOrWithoutBothStorePermissions() = fixture { db ->
        assertEquals("invalid", assertFailsWith<WorkerPlacementFailure> { transaction(db) { placeWorkerInsideTransaction(owner, command(target = stranger)) } }.key)
        assertEquals("invalid", assertFailsWith<WorkerPlacementFailure> { transaction(db) { placeWorkerInsideTransaction(owner, command(target = root)) } }.key)
        assertEquals("denied", assertFailsWith<WorkerPlacementFailure> { transaction(db) { placeWorkerInsideTransaction(worker, command()) } }.key)
        transaction(db) { assertEquals(1L, StoreWorkerMemberships.selectAll().count()) }
    }
    @Test fun openShiftMustBeFinishedAndClosedShiftIsNeverReassigned() = fixture { db ->
        val shift = UUID.randomUUID()
        transaction(db) { Workshifts.insert {
            it[id] = shift; it[storeId] = first; it[workerMembershipId] = membership; it[workerUserId] = worker
            it[startedAtMillis] = 1L; it[startedByUserId] = worker; it[metadata] = emptyMap()
        } }
        assertEquals("shift", assertFailsWith<WorkerPlacementFailure> { transaction(db) { placeWorkerInsideTransaction(owner, command()) } }.key)
        transaction(db) {
            Workshifts.update({ Workshifts.id eq shift }) { it[endedAtMillis] = 2L; it[isActive] = false }
            placeWorkerInsideTransaction(owner, command())
            assertEquals(first, Workshifts.selectAll().single()[Workshifts.storeId])
            assertEquals(membership, Workshifts.selectAll().single()[Workshifts.workerMembershipId])
        }
    }
    @Test fun concurrentRetriesCreateOneAssignmentAndPayloadReuseIsRejected() = fixture { db ->
        val c = command(); val pool = Executors.newFixedThreadPool(2)
        try {
            val jobs = List(2) { pool.submit(Callable { transaction(db) { placeWorkerInsideTransaction(owner, c) } }) }
            assertEquals(jobs[0].get(), jobs[1].get())
        } finally { pool.shutdownNow() }
        assertFailsWith<WorkerPlacementFailure> { transaction(db) { placeWorkerInsideTransaction(worker, c) } }
        transaction(db) { assertEquals(2L, StoreWorkerMemberships.selectAll().count()); assertEquals(1L, WorkerPlacementCommands.selectAll().count()) }
    }
    @Test fun assigningTwiceWithDifferentOperationIdsDoesNotOverwriteOrDuplicate() = fixture(true) { db ->
        transaction(db) { placeWorkerInsideTransaction(owner, command(root)) }
        assertEquals("duplicate", assertFailsWith<WorkerPlacementFailure> { transaction(db) { placeWorkerInsideTransaction(owner, command(root)) } }.key)
    }

    @Test fun aReplayedCommandCannotBypassNewlyRevokedAuthority() = fixture { db ->
        val c = command()
        transaction(db) { placeWorkerInsideTransaction(owner, c) }
        transaction(db) { Stores.update({ Stores.id eq root }) { it[ownerUserIds] = emptyList() }
            Stores.update({ Stores.id inList listOf(first, second) }) { it[ownerUserIds] = emptyList() } }
        assertEquals("denied", assertFailsWith<WorkerPlacementFailure> { transaction(db) { placeWorkerInsideTransaction(owner, c) } }.key)
    }

}

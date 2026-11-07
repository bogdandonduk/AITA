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
class MarketPricePermissionDatabaseTest {
    private val owner = UUID.randomUUID()
    private val worker = UUID.randomUUID()
    private val outsider = UUID.randomUUID()
    private val parent = UUID.randomUUID()
    private val branch = UUID.randomUUID()
    private val sibling = UUID.randomUUID()
    private val rights = listOf(STORE_PERMISSION_STOCK_READ, STORE_PERMISSION_ANALYTICS_VIEW)

    private fun fixture(block: () -> Unit) {
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
                SchemaUtils.create(Users, Stores, StoreUsers, StoreWorkerMemberships, StoreSubscriptionStates)
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
                block()
            }
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

    @Test fun parentOwnerNeedsNoSubscriptionAndCannotReadAnUnrelatedAccount() = fixture {
        assertTrue(priceInsightsReaderInsideTransaction(owner, parent))
        assertTrue(priceInsightsReaderInsideTransaction(owner, branch))
        assertTrue(priceInsightsReaderInsideTransaction(owner, sibling))
        assertFalse(priceInsightsReaderInsideTransaction(outsider, parent))
    }

    @Test fun parentEmployeeInheritsBothBranchTypesWithoutNeedingAnOpenWorkshift() = fixture {
        employ(parent)
        assertTrue(priceInsightsReaderInsideTransaction(worker, parent))
        assertTrue(priceInsightsReaderInsideTransaction(worker, branch))
        assertTrue(priceInsightsReaderInsideTransaction(worker, sibling))
        // There is deliberately no workshifts table: this is an authorized read, not a checkout.
    }

    @Test fun bothRightsAreRequiredAndRevocationTakesEffectImmediately() = fixture {
        val membership = employ(parent, listOf(STORE_PERMISSION_STOCK_READ))
        assertFalse(priceInsightsReaderInsideTransaction(worker, branch))
        StoreWorkerMemberships.update({ StoreWorkerMemberships.id eq membership }) { it[permissions] = listOf(STORE_PERMISSION_ANALYTICS_VIEW) }
        assertFalse(priceInsightsReaderInsideTransaction(worker, branch))
        StoreWorkerMemberships.update({ StoreWorkerMemberships.id eq membership }) { it[permissions] = rights }
        assertTrue(priceInsightsReaderInsideTransaction(worker, branch))
        StoreWorkerMemberships.update({ StoreWorkerMemberships.id eq membership }) { it[isActive] = false }
        assertFalse(priceInsightsReaderInsideTransaction(worker, parent))
        assertFalse(priceInsightsReaderInsideTransaction(worker, branch))
    }

    @Test fun branchEmployeeCannotReadSiblingOrManagementStock() = fixture {
        employ(branch)
        assertTrue(priceInsightsReaderInsideTransaction(worker, branch))
        assertFalse(priceInsightsReaderInsideTransaction(worker, sibling))
        assertFalse(priceInsightsReaderInsideTransaction(worker, parent))
    }

    @Test fun expiredBranchAccessDoesNotRemoveManagementAccessOrGrantItsSibling() = fixture {
        employ(parent)
        StoreSubscriptionStates.update({ StoreSubscriptionStates.storeId eq branch }) { it[status] = SUBSCRIPTION_STATUS_INACTIVE }
        assertFalse(priceInsightsReaderInsideTransaction(owner, branch))
        assertFalse(priceInsightsReaderInsideTransaction(worker, branch))
        assertTrue(priceInsightsReaderInsideTransaction(worker, parent))
        assertTrue(priceInsightsReaderInsideTransaction(worker, sibling))
    }

    @Test fun inactiveAccountOrStoreFamilyCannotReadPrices() = fixture {
        employ(parent)
        Users.update({ Users.id eq owner }) { it[isActive] = false }
        assertFalse(priceInsightsReaderInsideTransaction(owner, parent))
        Stores.update({ Stores.id eq branch }) { it[isActive] = false }
        assertFalse(priceInsightsReaderInsideTransaction(worker, branch))
        assertTrue(priceInsightsReaderInsideTransaction(worker, sibling))
        Stores.update({ Stores.id eq parent }) { it[isActive] = false }
        assertFalse(priceInsightsReaderInsideTransaction(worker, sibling))
    }
}

package kz.aita.server

import kz.aita.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assume.assumeTrue
import java.sql.DriverManager
import java.util.UUID
import kotlin.test.*

/** Calls the real reverse-mirror mutation; a branch must not rewrite the generic public profile. */
class ParentMarketplaceProfileDatabaseTest {
    private val owner = UUID.randomUUID()
    private val parent = UUID.randomUUID()
    private val branch = UUID.randomUUID()
    private val parentItem = UUID.randomUUID()
    private val branchItem = UUID.randomUUID()
    private fun localizedRows(value: String) = listOf(LocalizedStringDataModel("en", value))

    private fun fixture(block: (Database) -> Unit) {
        val prefix = if (System.getenv("AITA_STORE_ARCH_TEST_DB_URL").isNullOrBlank()) "AITA_MARKET_TEST_DB" else "AITA_STORE_ARCH_TEST_DB"
        val url = System.getenv("${prefix}_URL").orEmpty()
        assumeTrue("Requires an explicitly supplied disposable aita_test_* PostgreSQL database", url.isNotBlank())
        require(url.startsWith("jdbc:postgresql:"))
        val user = System.getenv("${prefix}_USER") ?: System.getProperty("user.name")
        val password = System.getenv("${prefix}_PASSWORD").orEmpty()
        val schema = "parent_profile_" + UUID.randomUUID().toString().replace("-", "")
        DriverManager.getConnection(url, user, password).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT current_database()").use { result ->
                    check(result.next() && result.getString(1).startsWith("aita_test_")) { "Refusing non-test database" }
                }
                statement.execute("CREATE SCHEMA $schema")
            }
        }
        val schemaUrl = url + (if ('?' in url) "&" else "?") + "currentSchema=$schema&connectTimeout=5&socketTimeout=30"
        val db = Database.connect(schemaUrl, driver = "org.postgresql.Driver", user = user, password = password)
        try {
            transaction(db) {
                exec("CREATE TABLE stores(id UUID PRIMARY KEY,parent_store_id UUID,branch_type TEXT)")
                exec("INSERT INTO stores VALUES ('$parent',NULL,NULL),('$branch','$parent','INTERNET')")
                SchemaUtils.create(StockItems)
                seed(parentItem, parent, "Generic tea", "Parent description", "parent", "10", StockMarketplaceProfile(
                    product = MarketProductDetails(brand = "Generic brand", ingredients = "Tea leaves")))
                seed(branchItem, branch, "Internet stock title", "Branch description", "branch", "12", StockMarketplaceProfile(
                    automaticFromStock = false, name = localizedRows("Internet exclusive"), description = localizedRows("Branch promotion"),
                    product = MarketProductDetails(brand = "Branch brand", imageUrls = listOf("https://images.example.org/offer.jpg"))))
            }
            block(db)
        } finally {
            TransactionManager.closeAndUnregister(db)
            DriverManager.getConnection(url, user, password).use { connection ->
                connection.createStatement().use { it.execute("DROP SCHEMA $schema CASCADE") }
            }
        }
    }

    private fun seed(itemId: UUID, location: UUID, title: String, details: String, image: String, amount: String,
        profile: StockMarketplaceProfile) {
        StockItems.insert {
            it[id] = itemId; it[userId] = owner; it[storeId] = location
            it[barcodes] = listOf("4006381333931"); it[name] = localizedRows(title); it[description] = localizedRows(details)
            it[measurementUnitId] = "0"; it[categoryIds] = emptyList()
            it[salePrices] = listOf(PriceDataModel(amount, "KZT", ""))
            it[returnPrices] = listOf(PriceDataModel(amount, "KZT", ""))
            it[supplyPrices] = listOf(PriceDataModel(amount, "KZT", ""))
            it[isQuickItem] = false; it[imagePaths] = listOf("https://images.example.org/$image.jpg")
            it[marketplaceProfile] = profile; it[activeShelfBatchId] = null
            it[createdAtMillis] = 1L; it[updatedAtMillis] = 1L; it[isActive] = true
        }
    }

    private fun item(id: UUID) = StockItems.selectAll().where { StockItems.id eq id }.single()
    private fun ResultRow.effectiveProfile() = this[StockItems.marketplaceProfile].fromCurrentStock(
        this[StockItems.name], this[StockItems.description], this[StockItems.imagePaths])

    @Test fun internetBranchOverrideCannotChangeTheParentsAutomaticProfileSource() = fixture { db -> transaction(db) {
        val before = item(parentItem)
        val editedBranch = item(branchItem)
        assertTrue(before[StockItems.marketplaceProfile].automaticFromStock)
        assertFalse(editedBranch[StockItems.marketplaceProfile].automaticFromStock)
        val mirrored = assertNotNull(mirrorBranchStockItemToParentInsideTransaction(editedBranch, userId = owner, now = 100L))
        assertEquals(parentItem, mirrored[StockItems.id])
        assertEquals(before[StockItems.marketplaceProfile], mirrored[StockItems.marketplaceProfile])
        assertEquals(before.effectiveProfile(), mirrored.effectiveProfile())
        assertEquals(editedBranch[StockItems.marketplaceProfile], item(branchItem)[StockItems.marketplaceProfile])
        assertEquals(editedBranch[StockItems.supplyPrices], mirrored[StockItems.supplyPrices])
        assertEquals(100L, mirrored[StockItems.updatedAtMillis])
        assertEquals(2L, StockItems.selectAll().count())
    } }

    @Test fun physicalBranchKeepsTheExistingStockMirrorBehavior() = fixture { db -> transaction(db) {
        exec("UPDATE stores SET branch_type='PHYSICAL' WHERE id='$branch'")
        val originalProfile = item(parentItem)[StockItems.marketplaceProfile]
        val source = item(branchItem)
        val mirrored = assertNotNull(mirrorBranchStockItemToParentInsideTransaction(source, userId = owner, now = 100L))
        assertEquals(parentItem, mirrored[StockItems.id])
        assertEquals(source[StockItems.name], mirrored[StockItems.name])
        assertEquals(source[StockItems.description], mirrored[StockItems.description])
        assertEquals(source[StockItems.imagePaths], mirrored[StockItems.imagePaths])
        assertEquals(originalProfile, mirrored[StockItems.marketplaceProfile])
    } }
    @Test fun sameNameInternalCodesSelectTheCorrectParentInsteadOfColliding() = fixture { db -> transaction(db) {
        val otherParent = UUID.randomUUID()
        seed(otherParent, parent, "Same toy", "", "other", "15", StockMarketplaceProfile())
        fun barcode(id: UUID, value: String, store: UUID) {
            StockItems.update({ StockItems.id eq id }) {
                it[barcodes] = listOf(value)
                it[barcodeModels] = listOf(GoodsItemBarcodeDataModel(value, GOODS_ITEM_BARCODE_TYPE_INTERNAL, store.toString()))
                it[name] = localizedRows("Same toy")
            }
        }
        barcode(parentItem, "2618000001248", parent)
        barcode(otherParent, "2610000008746", parent)
        barcode(branchItem, "2618000001248", branch)
        val mirror = assertNotNull(mirrorBranchStockItemToParentInsideTransaction(item(branchItem), userId = owner, now = 100))
        assertEquals(parentItem, mirror[StockItems.id])
        assertEquals(listOf("2610000008746"), item(otherParent)[StockItems.barcodes])
        assertEquals(3L, StockItems.selectAll().count())
    } }
    @Test fun combiningExistingParentAliasesNeverOverwritesAnotherParentCard() = fixture { db -> transaction(db) {
        val second = UUID.randomUUID()
        seed(second, parent, "Other product", "", "other", "15", StockMarketplaceProfile())
        StockItems.update({ StockItems.id eq second }) { it[barcodes] = listOf("2618000001248") }
        StockItems.update({ StockItems.id eq branchItem }) { it[barcodes] = listOf("4006381333931", "2618000001248") }
        mirrorBranchStockItemToParentInsideTransaction(item(branchItem), userId = owner, now = 100)
        assertEquals(listOf("4006381333931"), item(parentItem)[StockItems.barcodes])
        assertEquals(listOf("2618000001248"), item(second)[StockItems.barcodes])
    } }

}

package kz.aita.server

import kz.aita.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import kotlin.test.*

class PublicCatalogueDatabaseTest {
    @Test fun importsAreIdempotentPreserveEditsAndServeBoundedExactBarcodeResults() {
        val configured = System.getenv("AITA_CATALOGUE_TEST_JDBC")
        org.junit.Assume.assumeTrue("Set an isolated catalogue test database", configured != null)
        val url = requireNotNull(configured)
        require(url.startsWith("jdbc:postgresql://127.0.0.1:") && url.endsWith("/aita_catalogue_test"))
        val db = Database.connect(url, user = "bogdan", driver = "org.postgresql.Driver")
        transaction(db) {
            SchemaUtils.create(Suppliers, OpenGoodsCatalogue, PublicCatalogueImports)
            Suppliers.deleteAll(); OpenGoodsCatalogue.deleteAll(); PublicCatalogueImports.deleteAll()
            seedPublicSuppliersInsideTransaction(); seedOpenGoodsInsideTransaction()
            val supplierCount = Suppliers.selectAll().count()
            val goodsCount = OpenGoodsCatalogue.selectAll().count()
            assertEquals(PublicSupplierCatalogue.entries.size.toLong(), supplierCount)
            assertTrue(goodsCount > 0)
            val supplierId = UUID.fromString(PublicSupplierCatalogue.entries.first().id)
            Suppliers.update({ Suppliers.id eq supplierId }) { it[name] = "Preserved local correction" }
            val sample = OpenGoodsCatalogue.selectAll().limit(1).single()
            val sampleCode = sample[OpenGoodsCatalogue.code]
            seedPublicSuppliersInsideTransaction(); seedOpenGoodsInsideTransaction()
            assertEquals(supplierCount, Suppliers.selectAll().count())
            assertEquals(goodsCount, OpenGoodsCatalogue.selectAll().count())
            assertEquals("Preserved local correction", Suppliers.selectAll().where { Suppliers.id eq supplierId }.single()[Suppliers.name])
            assertEquals(1, searchOpenGoodsInsideTransaction(listOf(sampleCode), emptyList(), 20).size)
            assertTrue(searchOpenGoodsInsideTransaction(listOf(sampleCode + "9"), emptyList(), 20).isEmpty())
            assertEquals(3, searchOpenGoodsInsideTransaction(emptyList(), emptyList(), 3).size)
            assertTrue(searchOpenGoodsInsideTransaction(emptyList(), listOf("no-such-unique-product-xyz"), 20).isEmpty())
            val categoryId = UUID.randomUUID().toString()
            val id = UUID.randomUUID()
            val item = GenericGoodsItemDataModel(id=id.toString(), barcode=listOf("012345678905"),
                name=listOf(LocalizedStringDataModel("en", "Distinct test toy")), categoryIds=listOf(categoryId),
                typeIds=null, supplierIds=null, manufacturerIds=null)
            OpenGoodsCatalogue.insert { row ->
                row[OpenGoodsCatalogue.id]=id; row[code]="012345678905"; row[sortName]="distinct test toy"
                row[searchText]="distinct test toy"; row[payload]=jsonBase.encodeToString(GenericGoodsItemDataModel.serializer(),item)
            }
            // Legacy snapshots may already contain the padded spelling with another preserved ID.
            assertTrue(searchOpenGoodsInsideTransaction(listOf("0012345678905"),emptyList(),20).any { it.id == id.toString() })
            assertEquals(id.toString(), searchOpenGoodsInsideTransaction(emptyList(),listOf("distinct"),20,setOf(categoryId)).single().id)
            assertTrue(searchOpenGoodsInsideTransaction(emptyList(),listOf("distinct"),20,setOf(UUID.randomUUID().toString())).isEmpty())
            assertTrue(searchOpenGoodsInsideTransaction(emptyList(),listOf("%_"),20).isEmpty())
            assertEquals(id.toString(), searchOpenGoodsInsideTransaction(emptyList(),listOf("di"),20,setOf(categoryId)).single().id)

        }
    }
}

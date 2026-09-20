package kz.aita.server

import kz.aita.*
import kotlinx.serialization.builtins.ListSerializer
import kotlin.test.*

class PublicSupplierCatalogueTest {
    @Test fun packagedDirectoryCoversFiveCountriesWithTraceableUnclaimedEntries() {
        val rows = PublicSupplierCatalogue.entries
        assertTrue(rows.size >= 500)
        assertEquals(setOf("KZ", "TJ", "UZ", "KG", "RU"), rows.flatMap { it.catalogueSource!!.countryCodes }.toSet())
        assertTrue(rows.all { it.isGenericSupplier() && it.catalogueSource != null && it.userIds.isEmpty() })
        assertEquals(rows.size, rows.map { it.catalogueSource!!.url }.distinct().size)
    }
    @Test fun directoryCannotImportAccountOwnershipOrInventedContacts() {
        val row = PublicSupplierCatalogue.entries.first()
        for (bad in listOf(row.copy(userIds = listOf("account")), row.copy(emails = listOf("private@example.com")),
            row.copy(catalogueSource = row.catalogueSource!!.copy(websites = listOf("javascript:alert(1)"))))) {
            assertFailsWith<IllegalArgumentException> {
                PublicSupplierCatalogue.parse(jsonBase.encodeToString(ListSerializer(SupplierDataModel.serializer()), listOf(bad)))
            }
        }
    }
}

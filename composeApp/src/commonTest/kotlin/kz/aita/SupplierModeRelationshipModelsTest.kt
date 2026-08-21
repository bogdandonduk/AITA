package kz.aita

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SupplierModeRelationshipModelsTest {
    private val storeRequest = StoreSupplierRelationshipSnapshot(
        id = "request",
        key = StoreSupplierRelationshipKey("store-1", "supplier-1"),
        kind = StoreSupplierRelationshipKind.BUSINESS_LINK,
        status = StoreSupplierRelationshipStatus.PENDING_SUPPLIER,
        requestedBy = StoreSupplierRelationshipSide.STORE,
        updatedAtMillis = 20,
    )

    @Test
    fun supplierSeesStoreRequestAsWaitingForMe() {
        val items = buildSupplierRelationshipDirectoryItems(
            relationships = listOf(storeRequest),
            actorSide = StoreSupplierRelationshipSide.SUPPLIER,
            storeTitlesById = mapOf("store-1" to "North Store"),
            supplierTitlesById = mapOf("supplier-1" to "Alpha"),
            filter = SupplierRelationshipDirectoryFilter.WAITING_FOR_ME,
            sort = SupplierRelationshipDirectorySort.ACTION_FIRST,
        )
        assertEquals(1, items.size)
        assertTrue(items.single().waitingForCurrentSide)
        assertFalse(items.single().waitingForOtherSide)
    }

    @Test
    fun legacyContactsHaveExplicitFilterAndPriority() {
        val legacy = storeRequest.copy(
            id = "legacy",
            kind = StoreSupplierRelationshipKind.LEGACY_LOCAL_CONTACT,
            status = StoreSupplierRelationshipStatus.ACTIVE,
        )
        val items = buildSupplierRelationshipDirectoryItems(
            relationships = listOf(storeRequest, legacy),
            actorSide = StoreSupplierRelationshipSide.SUPPLIER,
            storeTitlesById = emptyMap(),
            supplierTitlesById = emptyMap(),
            filter = SupplierRelationshipDirectoryFilter.LEGACY_CONTACTS,
            sort = SupplierRelationshipDirectorySort.ACTION_FIRST,
        )
        assertEquals(1, items.size)
        assertEquals("legacy", items.single().relationship.id)
        assertEquals(5, items.single().actionPriority)
    }

    @Test
    fun mutableTitlesDoNotControlDeduplication() {
        val active = storeRequest.copy(
            id = "active",
            status = StoreSupplierRelationshipStatus.ACTIVE,
            revision = 2,
            updatedAtMillis = 30,
        )
        val items = buildSupplierRelationshipDirectoryItems(
            relationships = listOf(storeRequest, active),
            actorSide = StoreSupplierRelationshipSide.SUPPLIER,
            storeTitlesById = mapOf("store-1" to "Renamed Store"),
            supplierTitlesById = emptyMap(),
            filter = SupplierRelationshipDirectoryFilter.ALL,
            sort = SupplierRelationshipDirectorySort.STORE_NAME,
        )
        assertEquals(1, items.size)
        assertEquals("active", items.single().relationship.id)
        assertEquals("Renamed Store", items.single().storeTitle)
    }
}

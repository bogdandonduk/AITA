package kz.aita

import kotlin.test.*

class StoreSupplierRelationshipRulesTest {
    @Test
    fun storeRequestWaitsForSupplier() {
        val relationship = newStoreSupplierRelationshipRequest(
            id = "rel-1",
            key = StoreSupplierRelationshipKey(" store-1 ", " supplier-1 "),
            requester = StoreSupplierRelationshipSide.STORE,
            nowMillis = 100L,
        )
        assertNotNull(relationship)
        assertEquals(StoreSupplierRelationshipStatus.PENDING_SUPPLIER, relationship.status)
        assertEquals("store-1", relationship.key.storeId)
        assertEquals("supplier-1", relationship.key.supplierId)
        assertFalse(
            relationship.canPerform(
                actorSide = StoreSupplierRelationshipSide.STORE,
                action = StoreSupplierRelationshipAction.ACCEPT,
            ).allowed,
        )
        assertTrue(
            relationship.canPerform(
                actorSide = StoreSupplierRelationshipSide.SUPPLIER,
                action = StoreSupplierRelationshipAction.ACCEPT,
            ).allowed,
        )
    }

    @Test
    fun supplierRequestWaitsForStore() {
        val relationship = newStoreSupplierRelationshipRequest(
            id = "rel-2",
            key = StoreSupplierRelationshipKey("store-1", "supplier-1"),
            requester = StoreSupplierRelationshipSide.SUPPLIER,
            nowMillis = 100L,
        )
        assertNotNull(relationship)
        assertEquals(StoreSupplierRelationshipStatus.PENDING_STORE, relationship.status)
        assertEquals(StoreSupplierRelationshipSide.STORE, relationship.requiredAcceptanceSide())
    }

    @Test
    fun onlyRequesterCanCancelPendingRequest() {
        val relationship = newStoreSupplierRelationshipRequest(
            id = "rel-3",
            key = StoreSupplierRelationshipKey("store-1", "supplier-1"),
            requester = StoreSupplierRelationshipSide.STORE,
            nowMillis = 100L,
        )
        assertNotNull(relationship)
        assertTrue(
            relationship.canPerform(
                StoreSupplierRelationshipSide.STORE,
                StoreSupplierRelationshipAction.CANCEL_REQUEST,
            ).allowed,
        )
        assertFalse(
            relationship.canPerform(
                StoreSupplierRelationshipSide.SUPPLIER,
                StoreSupplierRelationshipAction.CANCEL_REQUEST,
            ).allowed,
        )
    }

    @Test
    fun legacyContactCannotPretendToBeBusinessLink() {
        val relationship = StoreSupplierRelationshipSnapshot(
            id = "legacy",
            key = StoreSupplierRelationshipKey("store-1", "supplier-1"),
            kind = StoreSupplierRelationshipKind.LEGACY_LOCAL_CONTACT,
            status = StoreSupplierRelationshipStatus.ACTIVE,
        )
        val decision = relationship.canPerform(
            StoreSupplierRelationshipSide.STORE,
            StoreSupplierRelationshipAction.UNLINK,
        )
        assertFalse(decision.allowed)
        assertEquals("LEGACY_CONTACT_REQUIRES_LINK_MIGRATION", decision.reasonCode)
    }

    @Test
    fun mergeUsesImmutablePairAndNewestCanonicalState() {
        val old = StoreSupplierRelationshipSnapshot(
            id = "old",
            key = StoreSupplierRelationshipKey(" store-1 ", "supplier-1"),
            kind = StoreSupplierRelationshipKind.BUSINESS_LINK,
            status = StoreSupplierRelationshipStatus.PENDING_SUPPLIER,
            revision = 2,
            updatedAtMillis = 20,
            sourceOrderIds = listOf("order-b"),
        )
        val current = old.copy(
            id = "current",
            key = StoreSupplierRelationshipKey("store-1", " supplier-1 "),
            status = StoreSupplierRelationshipStatus.ACTIVE,
            revision = 3,
            updatedAtMillis = 30,
            sourceOrderIds = listOf("order-a", "order-b"),
            sourceContractIds = listOf("contract-a"),
        )
        val merged = mergeStoreSupplierRelationships(listOf(old, current))
        assertEquals(1, merged.size)
        assertEquals("current", merged.single().id)
        assertEquals(listOf("order-a", "order-b"), merged.single().sourceOrderIds)
        assertEquals(listOf("contract-a"), merged.single().sourceContractIds)
    }

    @Test
    fun malformedIdentityCannotCreateRequest() {
        assertNull(
            newStoreSupplierRelationshipRequest(
                id = "rel",
                key = StoreSupplierRelationshipKey("", "supplier"),
                requester = StoreSupplierRelationshipSide.STORE,
                nowMillis = 0,
            ),
        )
    }

    @Test
    fun commercialHistoryUsesImmutableSourceReferences() {
        val relationship = StoreSupplierRelationshipSnapshot(
            id = "rel",
            key = StoreSupplierRelationshipKey("store", "supplier"),
            kind = StoreSupplierRelationshipKind.BUSINESS_LINK,
            status = StoreSupplierRelationshipStatus.ACTIVE,
            sourcePriceIds = listOf("price-1"),
        )
        assertTrue(relationship.hasCommercialHistory())
        assertTrue(relationship.isVisibleInOperationalDirectory())
    }
}

package kz.aita

import kotlin.test.*

class RealtimeDeliveryPolicyTest {
    @Test fun firstLegacyAndConsecutiveSequencesAreNotGaps() {
        assertFalse(realtimeSequenceHasGap(null, 10)); assertFalse(realtimeSequenceHasGap(10, null))
        assertFalse(realtimeSequenceHasGap(10, 11)); assertFalse(realtimeSequenceHasGap(10, 10))
    }
    @Test fun bufferDropRequiresCatchupButRestartUsesTheConnectionCatchup() {
        assertTrue(realtimeSequenceHasGap(10, 12)); assertFalse(realtimeSequenceHasGap(100, 1))
    }
    @Test fun deletedAndSharedFamilyStockInvalidatesTheVisibleFamily() {
        assertTrue(realtimeScopeMatches("stock", "parent", "branch", setOf("parent", "branch"), false))
        assertTrue(realtimeScopeMatches("stockbatches/update", "branch-b", "parent", setOf("parent", "branch-b"), false))
    }
    @Test fun unrelatedStoreDataDoesNotWakeAStoreWorkspace() {
        assertFalse(realtimeScopeMatches("stock", "other", "branch", setOf("parent", "branch"), false))
        assertFalse(realtimeScopeMatches("subscriptions", "other", "branch", setOf("parent", "branch"), false))
    }
    @Test fun ownLocationAndUnscopedInvalidationsAreRelevant() {
        assertTrue(realtimeScopeMatches("subscriptions", "BRANCH", "branch", emptySet(), false))
        assertTrue(realtimeScopeMatches("stores", "", "branch", emptySet(), false))
    }
    @Test fun supplierWorkspaceReceivesItsAuthorizedCommercialEventsAcrossStores() {
        assertTrue(realtimeScopeMatches("supplierorders", "other", "branch", emptySet(), true))
        assertFalse(realtimeScopeMatches("supplierorders", "other", "branch", emptySet(), false))
    }
    @Test fun stockFamiliesDoNotBecomePermissionToReadSiblingFinances() {
        assertFalse(realtimeEntityUsesStoreFamily("finance"))
        // A sibling's grant changes the family aggregate, not this store's entitlement.
        assertTrue(realtimeEntityUsesStoreFamily("subscriptions"))
        assertTrue(realtimeEntityUsesStoreFamily("operationlogs/get"))
    }
}

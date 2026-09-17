package kz.aita

import kotlinx.coroutines.runBlocking
import kotlin.test.*

class DynamicCartNavigationTest {
    @Test fun seventhAndTwentiethCartsNeverAliasFifth() = runBlocking {
        val nav = CartNavigationWorkspace(0)
        nav.setClientId(6); nav.go(NavigationScreenModel.Transaction.Payment, true)
        nav.setClientId(19); nav.go(NavigationScreenModel.Transaction.ReceiptPreview, true)
        assertEquals(NavigationScreenModel.Transaction.Payment, nav.screens(6, true).value.last())
        assertEquals(NavigationScreenModel.Transaction.ReceiptPreview, nav.screens(19, true).value.last())
        assertEquals(listOf(NavigationScreenModel.Transaction.Cart), nav.screens(4, true).value)
        nav.resetCart(19); assertEquals(2, nav.screens(6, true).value.size)
    }
    @Test fun resizeTransfersDetailTrailAndRestoresCorrectRoot() = runBlocking {
        val nav = CartNavigationWorkspace(1); nav.setClientId(10); nav.init(false)
        nav.go(NavigationScreenModel.Transaction.Payment, false); nav.init(true)
        assertEquals(listOf(NavigationScreenModel.Transaction.Cart, NavigationScreenModel.Transaction.Payment), nav.screens(10, true).value)
        assertEquals(listOf(NavigationScreenModel.Transaction.Selection), nav.screens(10, false).value)
        nav.init(false); assertEquals(NavigationScreenModel.Transaction.Payment, nav.screens(10, false).value.last())
        nav.init(false); assertEquals(2, nav.screens(10, false).value.size)
    }
    @Test fun restorationKeepsDynamicSlotAndRootPopDoesNotHang() = runBlocking {
        val original = CartNavigationWorkspace(2); original.setClientId(14); original.go(NavigationScreenModel.Transaction.Payment, false)
        val restored = CartNavigationWorkspace(2); restored.restorePersistentSnapshot(original.persistentSnapshot()); restored.init(false)
        assertEquals(14, restored.ClientId.value); assertEquals(NavigationScreenModel.Transaction.Payment, restored.screens(14, false).value.last())
        restored.resetCart(14); restored.pop(false, NavigationScreenModel.Transaction.Payment)
        assertEquals(2, restored.screens(14, false).value.size)
    }
    @Test fun clearAllAlsoClearsCartsAboveOriginalFive() = runBlocking {
        val nav = CartNavigationWorkspace(0); nav.setClientId(27); nav.go(NavigationScreenModel.Transaction.Payment, true); nav.resetAll()
        assertEquals(0, nav.ClientId.value); assertEquals(1, nav.screens(27, true).value.size)
    }
    @Test fun emptyLegacyFiveRootSnapshotDoesNotInventOccupiedSlots() = runBlocking {
        val nav = CartNavigationWorkspace(0)
        nav.restorePersistentSnapshot(PersistedTransactionNavigationSectionDataModel(
            left = List(5) { listOf(NavigationScreenModel.Transaction.Cart.route) },
            right = List(5) { listOf(NavigationScreenModel.Transaction.Selection.route) }))
        assertEquals(2, nav.persistentSnapshot().slotCount)
    }
    @Test fun explicitLargeSavedCountAndSelectedCartAreNotTruncated() = runBlocking {
        val nav = CartNavigationWorkspace(2)
        nav.restorePersistentSnapshot(PersistedTransactionNavigationSectionDataModel(clientId = 999, slotCount = 1000))
        assertEquals(999, nav.ClientId.value); assertEquals(1000, nav.persistentSnapshot().slotCount)
        assertFailsWith<IllegalArgumentException> { nav.restorePersistentSnapshot(PersistedTransactionNavigationSectionDataModel(slotCount = 1001)) }
        assertEquals(999, nav.ClientId.value)
    }
    @Test fun rebindingScopeDoesNotCarryOldCartAllocationOrDetails() = runBlocking {
        val nav = CartNavigationWorkspace(0); nav.setClientId(27); nav.go(NavigationScreenModel.Transaction.Payment, true)
        nav.bind(CartScope("another", "store", 2, 2))
        assertEquals(2, nav.persistentSnapshot().slotCount)
        assertEquals(listOf(NavigationScreenModel.Transaction.Cart), nav.screens(27, true).value)
    }
}

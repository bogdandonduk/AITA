package kz.aita

import kotlin.test.*

class AppStateTest {
    private val doc = AppStateDocument(hosts = mapOf("StockWarehouseNavigationScreenModelRoute" to mapOf("search" to "Milk")))
    @Test fun onlyBoundedUiDataIsAccepted() {
        assertTrue(doc.valid())
        for (key in listOf("password", "confirmationCode", "authEmail", "paymentCard", "sortOrderIds"))
            assertFalse(doc.copy(drafts = mapOf(key to "private")).valid())
        assertFalse(doc.copy(schema = 2).valid())
        assertFalse(doc.copy(drafts = mapOf("search" to "x".repeat(65537))).valid())
        val largeDeviceDraft = doc.copy(drafts = (1..8).associate { "field$it" to "я".repeat(32768) })
        assertFalse(largeDeviceDraft.valid())
        assertTrue(largeDeviceDraft.valid(APP_STATE_DEVICE_MAX_BYTES))
    }
    @Test fun accountStateCannotReopenAnAuthenticationOrPaymentAction() {
        for (route in listOf("UserAuthLogInNavigationScreenModelRoute", "TransactionPaymentNavigationScreenModelRoute", "MenuSecurityNavigationScreenModelRoute", "MenuCloseDebtNavigationScreenModelRoute"))
            assertFalse(appStateSafeRoute(route))
        assertTrue(appStateSafeRoute("MenuAppStateNavigationScreenModelRoute"))
    }
    @Test fun scopeRejectsPathInjectionAndUnknownModes() {
        assertTrue(AppStateScope(0).valid())
        assertTrue(AppStateScope(3, "11111111-1111-4111-8111-111111111111").valid())
        assertFalse(AppStateScope(4).valid()); assertFalse(AppStateScope(0, "../other").valid())
    }
    @Test fun newDeviceRestoresButTypingDuringFetchRequiresChoice() {
        val remote = AccountAppState(12, document = doc)
        assertEquals(AppStateMerge.RESTORE, appStateMerge(AccountAppState(), remote, true, true, true))
        assertEquals(AppStateMerge.CONFLICT, appStateMerge(AccountAppState(), remote, true, false, true))
    }
    @Test fun offlineEditsAreNotReplacedByAnotherDevicesWork() {
        assertEquals(AppStateMerge.CONFLICT, appStateMerge(AccountAppState(1, document = doc), AccountAppState(2), true, true, false))
        assertEquals(AppStateMerge.RESTORE, appStateMerge(AccountAppState(1, document = doc), AccountAppState(2), false, true, false))
    }
    @Test fun lostAcknowledgementIsSafeToRetryAndDeletionDoesNotResurrect() {
        assertEquals(AppStateMerge.ACKNOWLEDGE, appStateMerge(AccountAppState(1, document = doc), AccountAppState(2, document = doc), true, true, false))
        assertEquals(AppStateMerge.CONFLICT, appStateMerge(AccountAppState(1, document = doc), AccountAppState(2, false), true, true, false))
    }
}

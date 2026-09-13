package kz.aita

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MenuWorkNavigationTest {
    @Test
    fun everyModePlacesAppModeImmediatelyAfterAccountAndIncludesPersonalWork() {
        listOf(APP_MODE_STORE, APP_MODE_SUPPLIER, APP_MODE_BUYER, APP_MODE_MANUFACTURER).forEach { mode ->
            val destinations = menuDestinationsForAppMode(mode)
            assertEquals(NavigationScreenModel.Menu.UserAccount, destinations.first(), "Mode $mode")
            assertEquals(NavigationScreenModel.Menu.AppMode, destinations[1], "Mode $mode")
            assertEquals(NavigationScreenModel.Menu.Work, destinations[2], "Mode $mode")
            assertEquals(destinations.size, destinations.map { it.route }.distinct().size, "Mode $mode")
        }
    }

    @Test
    fun unknownModeRetainsTheStoreMenuFallback() {
        assertEquals(Navigation.Menu.listScreens, menuDestinationsForAppMode(Int.MIN_VALUE))
    }

    @Test
    fun personalWorkDoesNotUnlockStoreWorkerManagement() {
        assertFalse(menuDestinationRequiresStoreSubscription(NavigationScreenModel.Menu.Work))
        assertFalse(NavigationScreenModel.Menu.Work.isTemporarilyHiddenFromUi())
        assertTrue(menuDestinationRequiresStoreSubscription(NavigationScreenModel.Menu.Workers))
        assertTrue(menuDestinationRequiresStoreSubscription(NavigationScreenModel.Menu.AddEditWorker))
    }

    @Test
    fun workRouteHasExactlyOnePersistentOwner() {
        val work = NavigationScreenModel.Menu.Work
        assertEquals(work, persistentAppRouteToScreen(work.route))
        assertEquals(1, persistentAppNavigationScreens().count { it.route == work.route })
        assertTrue(work.route != NavigationScreenModel.Menu.Workers.route)
        assertTrue(work.route != NavigationScreenModel.Menu.UserAccount.route)
    }

    @Test
    fun narrowWorkRestoreKeepsMenuAsBackDestination() {
        assertEquals(
            listOf(NavigationScreenModel.Menu.List, NavigationScreenModel.Menu.Work),
            listOf(NavigationScreenModel.Menu.List.route, NavigationScreenModel.Menu.Work.route)
                .toPersistentMenuStack(NavigationScreenModel.Menu.List)
        )
    }

    @Test
    fun wideWorkRestoreKeepsAccountAsBackDestination() {
        assertEquals(
            listOf(NavigationScreenModel.Menu.UserAccount, NavigationScreenModel.Menu.Work),
            listOf(NavigationScreenModel.Menu.Work.route)
                .toPersistentMenuStack(NavigationScreenModel.Menu.UserAccount)
        )
    }

    @Test
    fun unknownPersistedRouteDoesNotHideTheLastValidWorkScreen() {
        assertEquals(
            listOf(NavigationScreenModel.Menu.List, NavigationScreenModel.Menu.Work),
            listOf(NavigationScreenModel.Menu.Work.route, "unavailable-future-route")
                .toPersistentMenuStack(NavigationScreenModel.Menu.List)
        )
    }

    @Test
    fun workTitleIsBundledInAllCurrentlySupportedLanguages() {
        val work = bundledLocalizedStringFallbacks.getValue(2658L)
        assertEquals("Work", work["en"])
        assertEquals("Работа", work["ru"])
        assertEquals("Жұмыс", work["kk"])
        assertEquals("Work", work["main"])
        assertTrue(bundledLocalizedStringFallbacks.containsKey(504L))
        assertTrue(bundledLocalizedStringFallbacks.containsKey(518L))
    }
}

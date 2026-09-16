package kz.aita

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MenuWorkNavigationTest {
    @Test
    fun everyModeKeepsAccountAndAppModeAndOneWorkersDestination() {
        listOf(APP_MODE_STORE, APP_MODE_SUPPLIER, APP_MODE_BUYER, APP_MODE_MANUFACTURER).forEach { mode ->
            val destinations = menuDestinationsForAppMode(mode)
            assertEquals(NavigationScreenModel.Menu.UserAccount, destinations.first(), "Mode $mode")
            assertEquals(NavigationScreenModel.Menu.AppMode, destinations[1], "Mode $mode")
            assertEquals(1, destinations.count { it == NavigationScreenModel.Menu.Workers }, "Mode $mode")
            assertFalse(destinations.any { it.route == "MenuWorkNavigationScreenModelRoute" })
            assertEquals(destinations.size, destinations.map { it.route }.distinct().size, "Mode $mode")
        }
    }

    @Test
    fun unknownModeRetainsTheStoreMenuFallback() {
        assertEquals(Navigation.Menu.listScreens, menuDestinationsForAppMode(Int.MIN_VALUE))
    }

    @Test
    fun personalWorkDoesNotUnlockStoreWorkerManagement() {
        assertFalse(menuDestinationRequiresStoreSubscription(NavigationScreenModel.Menu.Workers))
        assertFalse(NavigationScreenModel.Menu.Workers.isTemporarilyHiddenFromUi())
        assertFalse(menuDestinationRequiresStoreSubscription(NavigationScreenModel.Menu.Workers))
        assertTrue(menuDestinationRequiresStoreSubscription(NavigationScreenModel.Menu.AddEditWorker))
    }

    @Test
    fun workRouteHasExactlyOnePersistentOwner() {
        val work = NavigationScreenModel.Menu.Workers
        assertEquals(work, persistentAppRouteToScreen(work.route))
        assertEquals(1, persistentAppNavigationScreens().count { it.route == work.route })
        assertEquals(work, persistentAppRouteToScreen("MenuWorkNavigationScreenModelRoute"))
        assertEquals(0, persistentAppNavigationScreens().count { it.route == "MenuWorkNavigationScreenModelRoute" })
        assertTrue(work.route != NavigationScreenModel.Menu.UserAccount.route)
    }

    @Test
    fun narrowWorkRestoreKeepsMenuAsBackDestination() {
        assertEquals(
            listOf(NavigationScreenModel.Menu.List, NavigationScreenModel.Menu.Workers),
            listOf(NavigationScreenModel.Menu.List.route, "MenuWorkNavigationScreenModelRoute")
                .toPersistentMenuStack(NavigationScreenModel.Menu.List)
        )
    }

    @Test
    fun wideWorkRestoreKeepsAccountAsBackDestination() {
        assertEquals(
            listOf(NavigationScreenModel.Menu.UserAccount, NavigationScreenModel.Menu.Workers),
            listOf("MenuWorkNavigationScreenModelRoute")
                .toPersistentMenuStack(NavigationScreenModel.Menu.UserAccount)
        )
    }

    @Test
    fun unknownPersistedRouteDoesNotHideTheLastValidWorkScreen() {
        assertEquals(
            listOf(NavigationScreenModel.Menu.List, NavigationScreenModel.Menu.Workers),
            listOf(NavigationScreenModel.Menu.Workers.route, "unavailable-future-route")
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

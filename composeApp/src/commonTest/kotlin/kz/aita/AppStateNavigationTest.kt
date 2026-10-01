package kz.aita

import kotlin.test.*

class AppStateNavigationTest {
    @Test fun settingsAvailableInEveryModeAndWithoutSubscription() {
        for (mode in 0..3) {
            assertTrue(NavigationScreenModel.Menu.Settings in menuDestinationsForAppMode(mode))
            assertFalse(NavigationScreenModel.Menu.AppState in menuDestinationsForAppMode(mode))
        }
        assertFalse(menuDestinationRequiresStoreSubscription(NavigationScreenModel.Menu.AppState))
        assertEquals(NavigationScreenModel.Menu.AppState, persistentAppRouteToScreen(NavigationScreenModel.Menu.AppState.route))
    }
    @Test fun anotherAccountOrStoreCannotWriteIntoTheCurrentDraftBook() {
        assertTrue(ownsDraftKey("aita-ui-draft-v1:owner:store:Stock:search:value", "owner", "store"))
        assertFalse(ownsDraftKey("aita-ui-draft-v1:other:store:Stock:search:value", "owner", "store"))
        assertFalse(ownsDraftKey("aita-ui-draft-v1:owner:elsewhere:Stock:search:value", "owner", "store"))
        assertFalse(ownsDraftKey("aita-ui-draft-v1:owner:store:Security:password:value", "owner", "store"))
        assertTrue(ownsDraftKey("stock-add-edit-last-category:owner:store:root", "owner", "store"))
        assertTrue(ownsDraftKey("operation-choice:owner:store:batch-kind", "owner", "store"))
        assertTrue(ownsDraftKey("operation-choice:owner:no-store:stock-unit", "owner", null))
        assertFalse(ownsDraftKey("operation-choice:other:store:batch-kind", "owner", "store"))
        assertFalse(ownsDraftKey("operation-choice:owner:other:batch-kind", "owner", "store"))
    }
    @Test fun entireMenuTrailSurvivesSaveRestoreAndResize() {
        val trail = listOf(NavigationScreenModel.Menu.List, NavigationScreenModel.Menu.Settings,
            NavigationScreenModel.Menu.AppState, NavigationScreenModel.Menu.Security, NavigationScreenModel.Menu.Devices)
        val routes = trail.toCompactPersistentRoutes(NavigationScreenModel.Menu.List)
        assertEquals(trail.map { it.route }, routes)
        assertTrue(AppStateDocument(navigation = mapOf("menuLeft" to routes)).valid())
        assertEquals(trail, routes.toPersistentMenuStack(NavigationScreenModel.Menu.List))
        val wide = adaptMenuStacks(trail, listOf(NavigationScreenModel.Menu.UserAccount), false)
        assertEquals(trail, adaptMenuStacks(wide.first, wide.second, true).first)
        assertFalse(AppStateDocument(hosts = mapOf(NavigationScreenModel.Menu.Security.route to mapOf("password" to "secret"))).valid())
    }
}

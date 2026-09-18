package kz.aita

import kotlin.test.*

class AppStateNavigationTest {
    @Test fun settingsAvailableInEveryModeAndWithoutSubscription() {
        for (mode in 0..3) assertTrue(NavigationScreenModel.Menu.AppState in menuDestinationsForAppMode(mode))
        assertFalse(menuDestinationRequiresStoreSubscription(NavigationScreenModel.Menu.AppState))
        assertEquals(NavigationScreenModel.Menu.AppState, persistentAppRouteToScreen(NavigationScreenModel.Menu.AppState.route))
    }
    @Test fun anotherAccountOrStoreCannotWriteIntoTheCurrentDraftBook() {
        assertTrue(ownsDraftKey("aita-ui-draft-v1:owner:store:Stock:search:value", "owner", "store"))
        assertFalse(ownsDraftKey("aita-ui-draft-v1:other:store:Stock:search:value", "owner", "store"))
        assertFalse(ownsDraftKey("aita-ui-draft-v1:owner:elsewhere:Stock:search:value", "owner", "store"))
        assertFalse(ownsDraftKey("aita-ui-draft-v1:owner:store:Security:password:value", "owner", "store"))
        assertTrue(ownsDraftKey("stock-add-edit-last-category:owner:store:root", "owner", "store"))
    }
}

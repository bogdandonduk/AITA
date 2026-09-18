package kz.aita

import kotlin.test.*

class SettingsDownloadsNavigationTest {
    @Test fun everyAppModeOffersSettingsAndDownloadsWithoutAnAppStateMenuItem() {
        for (mode in listOf(APP_MODE_STORE, APP_MODE_BUYER, APP_MODE_SUPPLIER, APP_MODE_MANUFACTURER)) {
            val destinations = menuDestinationsForAppMode(mode)
            assertTrue(NavigationScreenModel.Menu.Settings in destinations)
            assertTrue(NavigationScreenModel.Menu.Downloads in destinations)
            assertFalse(NavigationScreenModel.Menu.AppState in destinations)
            assertEquals(1, destinations.count { it == NavigationScreenModel.Menu.Settings })
            assertEquals(1, destinations.count { it == NavigationScreenModel.Menu.Downloads })
        }
    }

    @Test fun settingsAndDownloadsRemainAccessibleWithoutAnActiveStoreSubscription() {
        assertFalse(menuDestinationRequiresStoreSubscription(NavigationScreenModel.Menu.Settings))
        assertFalse(menuDestinationRequiresStoreSubscription(NavigationScreenModel.Menu.Downloads))
    }

    @Test fun appStateAndDownloadsHaveDifferentMeaningfulArtwork() {
        assertEquals(216, AitaTabIcon.AppState.family)
        assertEquals(217, AitaTabIcon.Downloads.family)
        assertNotEquals(AitaTabIcon.AppState, AitaTabIcon.Settings)
    }

    @Test fun visibleNewScreenDescriptionsExistInEverySupportedLanguage() {
        for (language in listOf("en", "ru", "kk", "ky", "tg", "uz")) {
            for (key in listOf("settings.title", "downloads.title", "downloads.folder", "downloads.android", "downloads.windows",
                "downloads.web", "downloads.macos", "downloads.ios", "downloads.browser_folder", "downloads.aab")) {
                assertFalse(eventMessage(key).extractLocalizedString(language).isNullOrBlank(), "$language: $key")
            }
        }
    }
}

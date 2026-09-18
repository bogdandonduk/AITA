package kz.aita

import kotlin.test.*

class MenuAttentionTest {
    @Test fun availableUpdateAlwaysLeadsEveryModeMenu() {
        for (mode in 0..3) {
            val original = menuDestinationsForAppMode(mode)
            val visible = orderedMenuDestinations(original, true)
            assertEquals(NavigationScreenModel.Menu.ClientUpdate, visible.first())
            assertEquals(original.filter { it != NavigationScreenModel.Menu.ClientUpdate }, visible.drop(1))
            assertFalse(NavigationScreenModel.Menu.ClientUpdate in orderedMenuDestinations(original, false))
        }
    }
    @Test fun localNavigationCanRememberTransactionRootsWithoutPersistingCheckoutSecrets() {
        val place = LocalNavigationPlace(NavigationScreenModel.Transaction.MainReturn.route)
        assertTrue(place.valid())
        assertEquals(place, jsonBase.decodeFromString(LocalNavigationPlace.serializer(), jsonBase.encodeToString(LocalNavigationPlace.serializer(), place)))
        assertFalse(LocalNavigationPlace(NavigationScreenModel.Transaction.Payment.route).valid())
        assertFalse(place.copy(navigation = mapOf("menuRight" to listOf(NavigationScreenModel.Menu.UserAccount.route))).valid())
    }
}

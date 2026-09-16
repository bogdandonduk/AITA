package kz.aita

import kotlin.test.*

class ClientUpdateNavigationTest {
    @Test fun aboutIsLastAndAlwaysAvailableWithoutSubscription() {
        assertEquals(NavigationScreenModel.Menu.About, Navigation.Menu.listScreens.last())
        assertFalse(menuDestinationRequiresStoreSubscription(NavigationScreenModel.Menu.About))
        assertFalse(menuDestinationRequiresStoreSubscription(NavigationScreenModel.Menu.ClientUpdate))
    }
    @Test fun aboutRouteRestoresWithoutBecomingMenuRoot() {
        assertEquals(NavigationScreenModel.Menu.About, persistentAppRouteToScreen("MenuAboutNavigationScreenModelRoute"))
        val trail = normalizeMenuStack(listOf(NavigationScreenModel.Menu.About), NavigationScreenModel.Menu.List)
        assertEquals(listOf(NavigationScreenModel.Menu.List, NavigationScreenModel.Menu.About), trail)
    }
    @Test fun aboutSurvivesBothResizeDirections() {
        val list = NavigationScreenModel.Menu.List; val account = NavigationScreenModel.Menu.UserAccount; val about = NavigationScreenModel.Menu.About
        val wide = adaptMenuStacks(listOf(list, about), listOf(account), false)
        assertEquals(listOf(account, about), wide.second)
        assertEquals(listOf(list, about), adaptMenuStacks(wide.first, wide.second, true).first)
    }
    @Test fun downloadAndHandoffAreNotInstalledAcknowledgements() {
        assertTrue(ClientUpdateState(available = kz.aita.updates.ClientRelease(channel = kz.aita.updates.ReleaseChannel.RELEASE,
            sequence = 2, id = "r2", version = "1.0.1", build = 2, publishedAtMillis = 1, expiresAtMillis = 2),
            artifact = kz.aita.updates.ClientArtifact(kz.aita.updates.ClientOs.MACOS, kind = kz.aita.updates.InstallerKind.PKG, url = "https://example.org/a.pkg"),
            phase = ClientUpdatePhase.HANDOFF, handoff = UpdateHandoff.INSTALLER_OPENED).hasUpdate)
    }
}

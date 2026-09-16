package kz.aita
import kotlin.test.*
class TutorialNavigationTest {
    @Test fun everyModeHasOneTutorialDestinationAndAboutRemainsLast() {
        for(mode in listOf(APP_MODE_STORE,APP_MODE_BUYER,APP_MODE_SUPPLIER,APP_MODE_MANUFACTURER)) {
            val menu=menuDestinationsForAppMode(mode)
            assertEquals(1,menu.count {it==NavigationScreenModel.Menu.Tutorials})
            assertEquals(NavigationScreenModel.Menu.About,menu.last())
        }
    }
    @Test fun tutorialsAreNotSubscriptionProtected() {
        assertFalse(menuDestinationRequiresStoreSubscription(NavigationScreenModel.Menu.Tutorials))
        assertFalse(NavigationScreenModel.Menu.Tutorials.isTemporarilyHiddenFromUi())
    }
    @Test fun tutorialRouteRestoresWithTheCorrectBaseAndSurvivesResize() {
        val route=NavigationScreenModel.Menu.Tutorials
        assertEquals(route,persistentAppRouteToScreen(route.route))
        val base=NavigationScreenModel.Menu.List;val account=NavigationScreenModel.Menu.UserAccount
        val narrow=listOf(route.route).toPersistentMenuStack(base)
        assertEquals(listOf(base,route),narrow)
        val wide=adaptMenuStacks(narrow,listOf(account),false)
        assertEquals(listOf(account,route),wide.second)
        assertEquals(narrow,adaptMenuStacks(wide.first,wide.second,true).first)
    }
    @Test fun newAccountBuyerLandingIsNotStoreSubscriptionRecovery() {
        assertEquals(NavigationScreenModel.Buyer.Main.Home,defaultMainScreenForAppMode(DEFAULT_NEW_ACCOUNT_APP_MODE))
    }
    @Test fun handbookMessagesCoverAllInterfaceLocales() {
        for(language in listOf("en","ru","kk","ky","tg","uz"))
            assertFalse(EventMessages.renderExact(EventMessageReference("help.title"),language).isNullOrBlank())
    }
}

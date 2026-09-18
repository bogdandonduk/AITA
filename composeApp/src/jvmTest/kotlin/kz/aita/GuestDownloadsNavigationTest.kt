package kz.aita

import kotlinx.coroutines.runBlocking
import kotlin.test.*

class GuestDownloadsNavigationTest {
    @Test fun guestCanOpenFolderSettingsResizeAndReturnToAuthentication() = runBlocking {
        val navigation = Navigation.UserAuth
        val original = navigation.persistentSnapshot()
        try {
            navigation.clearLeft()
            navigation.clearRight()
            navigation.goLeft(NavigationScreenModel.UserAuth.Downloads)
            navigation.goLeft(NavigationScreenModel.UserAuth.DownloadSettings)
            navigation.init(isNarrowScreen = false)
            assertEquals(listOf(NavigationScreenModel.UserAuth.SignUp, NavigationScreenModel.UserAuth.Downloads,
                NavigationScreenModel.UserAuth.DownloadSettings), navigation.Right.value)
            navigation.popRight()
            assertEquals(NavigationScreenModel.UserAuth.Downloads, navigation.Right.value.last())
            navigation.init(isNarrowScreen = true)
            assertEquals(listOf(NavigationScreenModel.UserAuth.LogIn, NavigationScreenModel.UserAuth.Downloads), navigation.Left.value)
            navigation.popLeft()
            assertEquals(listOf(NavigationScreenModel.UserAuth.LogIn), navigation.Left.value)
            assertEquals(listOf(NavigationScreenModel.UserAuth.SignUp), navigation.Right.value)
        } finally { navigation.restorePersistentSnapshot(original) }
    }
}

package kz.aita

import kotlin.test.*

class DeviceNavigationPlaceTest {
    private val devices = LocalNavigationPlace(NavigationScreenModel.Menu.Main.route, mapOf(
        "menuLeft" to listOf(NavigationScreenModel.Menu.List.route),
        "menuRight" to listOf(NavigationScreenModel.Menu.Devices.route),
        "stockRight" to listOf(NavigationScreenModel.Stock.AddEditGoodsItem.route)))
    @Test fun deviceSettingsSurviveAccountActiveStoreChanging() {
        val saved = DeviceNavigationPlace("branch", devices)
        assertEquals(devices, saved.forStore("branch"))
        val restored = assertNotNull(saved.forStore("parent"))
        assertEquals(listOf(NavigationScreenModel.Menu.Devices.route), restored.navigation["menuRight"])
        assertFalse("stockRight" in restored.navigation)
    }
    @Test fun stockEditorFromAnotherStoreIsNotRestored() {
        assertNull(DeviceNavigationPlace("branch", devices.copy(main = NavigationScreenModel.Stock.Main.route)).forStore("parent"))
    }
    @Test fun restoredNarrowDevicesMovesIntoWideDetailPaneAndBack() {
        val (left, right) = adaptMenuStacks(listOf(NavigationScreenModel.Menu.List, NavigationScreenModel.Menu.Devices),
            listOf(NavigationScreenModel.Menu.UserAccount), false)
        assertEquals(NavigationScreenModel.Menu.Devices, right.last())
        assertEquals(listOf(NavigationScreenModel.Menu.List), left)
        assertEquals(NavigationScreenModel.Menu.Devices, adaptMenuStacks(left, right, true).first.last())
    }
}

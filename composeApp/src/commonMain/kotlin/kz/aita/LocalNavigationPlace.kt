package kz.aita

import kotlinx.serialization.Serializable

/** Navigation always stays on this device, independently of optional form/cloud storage. */
@Serializable internal data class LocalNavigationPlace(val main: String, val navigation: Map<String, List<String>> = emptyMap(),
    val hosts: Map<String, Map<String, String>> = emptyMap()) {
    fun valid(): Boolean = persistentAppRouteToScreen(main)?.let { it is NavigationScreenModel.Menu || it is NavigationScreenModel.Stock || (it is NavigationScreenModel.Transaction && it in Navigation.bottomNavBarScreensStore) || it is NavigationScreenModel.Buyer || it is NavigationScreenModel.Supplier } == true &&
        AppStateDocument(navigation = navigation, hosts = hosts).valid(APP_STATE_DEVICE_MAX_BYTES)
}
@Serializable internal data class DeviceNavigationPlace(val store: String?, val place: LocalNavigationPlace) {
    fun forStore(nextStore: String?): LocalNavigationPlace? = when {
        !place.valid() -> null
        store == nextStore -> place
        place.main == NavigationScreenModel.Menu.Main.route -> place.copy(navigation = place.navigation.filterKeys { it.startsWith("menu") }, hosts = place.hosts.filterKeys { it.startsWith("Menu") })
        else -> null
    }
}

internal fun Navigation.localNavigationSnapshot(): LocalNavigationPlace {
    val snapshot = accountUiStateSnapshot(emptyMap())
    val navigationHosts = snapshot.hosts.mapValues { (_, fields) -> fields.filterKeys { key ->
        val name = key.lowercase()
        listOf("tab", "section", "scroll", "navigation", "filter", "editedgoodsitemid").any { it in name }
    } }.filterValues { it.isNotEmpty() }
    return LocalNavigationPlace(Main.value.last().route, snapshot.navigation - "main", navigationHosts)
}

internal suspend fun Navigation.restoreLocalNavigation(place: LocalNavigationPlace) {
    if (!place.valid()) return
    restoreAccountUiState(AppStateDocument(navigation = place.navigation, hosts = place.hosts))
    persistentAppRouteToScreen(place.main)?.takeIf { it.isMainScreenCompatibleWithAppMode(appModeState.value) }?.let { goMain(it) }
}

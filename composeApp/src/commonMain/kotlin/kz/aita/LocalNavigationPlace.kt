package kz.aita

import kotlinx.serialization.Serializable

/** Navigation always stays on this device, independently of optional form/cloud storage. */
@Serializable internal data class LocalNavigationPlace(val main: String, val navigation: Map<String, List<String>> = emptyMap()) {
    fun valid(): Boolean = main in Navigation.bottomNavBarScreensStore.plus(Navigation.bottomNavBarScreensBuyer)
        .plus(Navigation.bottomNavBarScreensSupplier).map { it.route } &&
        AppStateDocument(navigation = navigation).valid()
}
internal fun Navigation.localNavigationSnapshot(): LocalNavigationPlace =
    LocalNavigationPlace(Main.value.last().route, accountUiStateSnapshot(emptyMap()).navigation - "main")

internal suspend fun Navigation.restoreLocalNavigation(place: LocalNavigationPlace) {
    if (!place.valid()) return
    restoreAccountUiState(AppStateDocument(navigation = place.navigation))
    persistentAppRouteToScreen(place.main)?.takeIf { it.isMainScreenCompatibleWithAppMode(appModeState.value) }?.let { goMain(it) }
}

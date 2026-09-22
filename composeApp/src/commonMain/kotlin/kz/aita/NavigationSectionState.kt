package kz.aita

import androidx.compose.runtime.*
import kotlinx.coroutines.flow.collect

/** Presentation only. Authentication challenges and command/form values never use this store. */
@Composable
internal fun AppConfiguration.rememberNavigationSection(key: String, initial: String): MutableState<String> {
    val revision by AppStateWorkspace.restoreRevision.collectAsState()
    val ready by AppStateWorkspace.readyScope.collectAsState()
    val account = stateValues.userAccount?.id
    val mode = stateValues.appModeId
    val store by activeStoreIdState.collectAsState()
    val host = NavigationScreenModel.Menu.Main
    val storageKey = remember(key) { "navigation_section_" + key.take(180).encodeToByteArray().joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') } }
    val value = remember(account, mode, store, revision, key) {
        mutableStateOf(if (account != null) host.state.value[storageKey] ?: initial else initial)
    }
    LaunchedEffect(value, ready, account) {
        if (account == null || !AppStateWorkspace.readyForCurrentScope()) return@LaunchedEffect
        snapshotFlow { value.value }.collect { section ->
            if (stateValues.userAccount?.id == account && AppStateWorkspace.readyForCurrentScope() && section.length <= 128)
                host.setStateNow(storageKey to section)
        }
    }
    return value
}

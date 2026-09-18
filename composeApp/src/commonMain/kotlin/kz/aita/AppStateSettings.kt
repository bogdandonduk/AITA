package kz.aita

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch

internal fun AppConfiguration.appStateText(key: String) = eventMessage("app_state.$key").extractLocalizedString(stateValues.appLanguage).orEmpty()

@Composable internal fun AppConfiguration.MenuAppStateScreen() {
    val state by AppStateWorkspace.state.collectAsState()
    AitaScreenColumn(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, appBar = {
        ScreenAppBarWidget(title = appStateText("title"), iconPath = stateValues.drawablePathIconDevices,
            onBack = { coroutineScope.launch { Navigation.Menu.pop(stateValues.isNarrowScreen) } })
    }) {
        AppStateSettingsPane(state)
    }
}

@Composable internal fun AppConfiguration.AppStateSettingsPane(state: AppStateUi) {
    val colors = SwitchDefaults.colors(checkedThumbColor = stateValues.AccentTextColor, checkedTrackColor = stateValues.AccentColor,
        uncheckedThumbColor = stateValues.TextColor, uncheckedTrackColor = stateValues.BackgroundColor,
        uncheckedBorderColor = stateValues.PlaceholderTextColor)
    val deviceLabel = appStateText("device")
    val cloudLabel = appStateText("cloud")
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(appStateText("help"), color = stateValues.TextColor, fontSize = stateValues.textSize)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(appStateText("device"), Modifier.weight(1f), color = stateValues.TextColor, fontSize = stateValues.textSize)
                Switch(state.device, { AppStateWorkspace.setDevice(it) }, enabled = state.status != "loading", colors = colors, modifier = Modifier.semantics { contentDescription = deviceLabel })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(appStateText("cloud"), Modifier.weight(1f), color = stateValues.TextColor, fontSize = stateValues.textSize)
                Switch(state.cloud && state.signedIn, { AppStateWorkspace.setCloud(it) }, enabled = state.signedIn && state.status != "loading" && !state.conflict, colors = colors, modifier = Modifier.semantics { contentDescription = cloudLabel })
            }
            Text(appStateText("cloud_help"), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
            Text(appStateText(if (state.signedIn) state.status else "sign_in"), color = stateValues.AccentColor, fontSize = stateValues.textSize)
            if (state.conflict) {
                actionButton(text = appStateText("keep_device"), autoLoading = false, confirmationRequired = true,
                    onClick = { AppStateWorkspace.resolve(false) })
                actionButton(text = appStateText("use_account"), autoLoading = false, confirmationRequired = true,
                    onClick = { AppStateWorkspace.resolve(true) })
            }
            Text(appStateText("exclusions"), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
        }
    }

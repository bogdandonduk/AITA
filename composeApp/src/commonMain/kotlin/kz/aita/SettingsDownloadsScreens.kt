package kz.aita

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

internal fun AppConfiguration.settingsText(key: String) = eventMessage("settings.$key").extractLocalizedString(stateValues.appLanguage).orEmpty()
internal fun AppConfiguration.downloadsText(key: String, vararg arguments: Pair<String, String>) =
    eventMessage("downloads.$key", *arguments).extractLocalizedString(stateValues.appLanguage).orEmpty()

@Composable
internal fun AppConfiguration.MenuSettingsScreen(legacyAppState: Boolean = false) {
    val appState by AppStateWorkspace.state.collectAsState()
    val host = NavigationScreenModel.Menu.Settings
    val values by host.state.collectAsState()
    LaunchedEffect(legacyAppState) { if (legacyAppState) host.setStateNow("settings_section" to "app_state") }
    val section = values["settings_section"] ?: "app_state"
    AitaScreenColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, appBar = {
        ScreenAppBarWidget(title = settingsText("title"), iconPath = stateValues.drawablePathIconSettings,
            onBack = { coroutineScope.launch { Navigation.Menu.pop(stateValues.isNarrowScreen) } })
    }) {
        sectionTabsWidget("settings_sections", listOf(
            TabContent("app_state", appStateText("title"), AitaTabIcon.AppState),
            TabContent("downloads", downloadsText("title"), AitaTabIcon.Downloads)
        ), modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth().padding(horizontal = 12.dp),
            selectedId = section, onSelected = { host.setStateNow("settings_section" to it) })
        when (section) {
            "downloads" -> Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                DownloadFolderSettings()
                actionButton(text = downloadsText("title"), iconPath = downloadsIconPath(), iconRes = downloadsIconResource(),
                    autoLoading = false, confirmationRequired = false,
                    onClick = { coroutineScope.launch { Navigation.Menu.go(NavigationScreenModel.Menu.Downloads) } })
            }
            else -> AppStateSettingsPane(appState)
        }
    }
}

@Composable
private fun AppConfiguration.DownloadCard(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(stateValues.cornerRadius))
        .background(stateValues.TextColor.copy(alpha = .025f))
        .border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor.copy(alpha = .35f), RoundedCornerShape(stateValues.cornerRadius))
        .padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
internal fun AppConfiguration.DownloadFolderSettings() {
    val downloads by DownloadsWorkspace.state.collectAsState()
    val chooseFolder = rememberDownloadsFolderPicker { chosen -> if (chosen != null) DownloadsWorkspace.setFolder(chosen) }
    LaunchedEffect(Unit) { if (!downloads.loaded && !downloads.loading) DownloadsWorkspace.refresh() }
    DownloadCard {
        Text(downloadsText("folder"), color = stateValues.TextColor, fontSize = stateValues.textSize, fontWeight = FontWeight.Bold)
        Text(downloadsText("folder_help"), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
        if (downloads.canChooseFolder) {
            if (downloads.destinationLabel != null || downloads.folderError == null) SelectionContainer {
                Text(downloads.destinationLabel ?: downloadsText("default_folder"), color = stateValues.TextColor, fontSize = stateValues.textSize)
            }
            actionButton(text = downloadsText("choose_folder"), autoLoading = false, confirmationRequired = false,
                enabled = downloads.savingId == null && !downloads.loading && !downloads.folderChanging, onClick = chooseFolder)
            actionButton(text = downloadsText("reset_folder"), autoLoading = false,
                confirmationRequired = false, enabled = downloads.savingId == null && !downloads.loading && !downloads.folderChanging,
                onClick = { DownloadsWorkspace.setFolder(null) })
        } else Text(downloadsText("browser_folder"), color = stateValues.TextColor, fontSize = stateValues.textSize)
        (downloads.folderError ?: downloads.error?.takeIf { it in setOf("storage", "space", "unsupported") })?.let { error ->
            Text(updateText("error.$error"), color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
        }
        DownloadSavedLocation(downloads)
    }
}

@Composable
private fun AppConfiguration.DownloadSavedLocation(downloads: DownloadsState) {
    if (downloads.savedId != null) {
        val destination = downloads.savedDestination
        SelectionContainer {
            Text(if (destination != null) "${downloadsText("saved")}: $destination" else downloadsText("browser_saved"),
                color = stateValues.AccentColor, fontSize = stateValues.smallTextSize)
        }
    }
}

@Composable
internal fun AppConfiguration.DownloadsScreen() {
    val downloads by DownloadsWorkspace.state.collectAsState()
    val scope = rememberCoroutineScope()
    var webOpenFailed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { DownloadsWorkspace.refresh() }
    AitaScreenColumn(Modifier.fillMaxSize(), appBar = {
        ScreenAppBarWidget(title = downloadsText("title"), iconPath = downloadsIconPath(),
            onBack = { coroutineScope.launch { Navigation.Menu.pop(stateValues.isNarrowScreen) } })
    }) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 840.dp).fillMaxSize(), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                state = rememberMenuScreenLazyListState(NavigationScreenModel.Menu.Downloads)) {
                item("intro") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(downloadsText("intro"), color = stateValues.TextColor, fontSize = stateValues.accentTextSize, fontWeight = FontWeight.Bold)
                        Text(downloadsText("help"), color = stateValues.PlaceholderTextColor, fontSize = stateValues.textSize)
                    }
                }
                item("download_status") {
                    DownloadCard {
                        downloads.error?.let { problem ->
                            val reason = problem.takeIf { it in setOf("configuration", "network", "integrity", "expired", "storage", "space", "unavailable", "unsupported") } ?: "unavailable"
                            Text(updateText("error.$reason"), color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
                        }
                        if (downloads.loading) Text(updateText("checking"), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                        if (downloads.savingId != null) {
                            val fraction = downloads.progress?.coerceIn(0f, 1f)
                            Text(updateText("downloading") + (fraction?.let { " ${(it * 100).toInt()}%" } ?: ""),
                                color = stateValues.TextColor, fontSize = stateValues.smallTextSize)
                            if (fraction != null) LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth(), color = stateValues.AccentColor)
                        }
                        DownloadSavedLocation(downloads)
                        actionButton(text = downloadsText("folder"), iconPath = stateValues.drawablePathIconSettings,
                            autoLoading = false, confirmationRequired = false, onClick = {
                                NavigationScreenModel.Menu.Settings.setStateNow("settings_section" to "downloads")
                                scope.launch { Navigation.Menu.go(NavigationScreenModel.Menu.Settings) }
                            })
                        actionButton(text = updateText("check"), autoLoading = false, confirmationRequired = false,
                            enabled = !downloads.loading && !downloads.folderChanging && downloads.savingId == null, onClick = DownloadsWorkspace::refresh)
                    }
                }
                listOf("android" to "Android", "windows" to "Windows", "web" to "Web", "macos" to "macOS", "ios" to "iOS").forEach { (platform, title) ->
                    item(platform) {
                        val entries = downloads.entries.filter { it.platform.equals(platform, ignoreCase = true) }.sortedByDescending { it.build }
                        DownloadCard {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(stateValues.AccentColor.copy(alpha = .13f)),
                                    contentAlignment = Alignment.Center) {
                                    CpImage(Modifier.size(32.dp), url = downloadPlatformIconPath(platform), fallbackRes = downloadPlatformIconResource(platform),
                                        contentDescription = null, tintColor = stateValues.AccentColor)
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(title, color = stateValues.TextColor, fontSize = stateValues.textSize, fontWeight = FontWeight.Bold)
                                    Text(downloadsText(platform), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                                }
                            }
                            when (platform) {
                                "web" -> {
                                    downloads.webVersion?.let { version ->
                                        Text("${downloadsText("latest")}: $version · ${updateText("build")} ${downloads.webBuild}", color = stateValues.TextColor,
                                            fontSize = stateValues.textSize, fontWeight = FontWeight.Bold)
                                        val notes = downloads.webNotes[stateValues.appLanguage] ?: downloads.webNotes["en"]
                                        if (!notes.isNullOrBlank()) Text(notes, color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                                    }
                                    Text("aita.kz", color = stateValues.AccentColor, fontSize = stateValues.smallTextSize)
                                    actionButton(text = downloadsText("open_web"), autoLoading = false, confirmationRequired = false,
                                        onClick = { scope.launch {
                                            webOpenFailed = openExternalUrlPlatformAction?.invoke("https://aita.kz/")?.success != true
                                        } })
                                    if (webOpenFailed) Text(updateText("error.unavailable"), color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
                                }
                                "macos", "ios" -> Text(downloadsText("deferred"), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                                else -> {
                                    if (entries.isEmpty()) Text(if (downloads.loading) updateText("checking") else downloadsText("unavailable"),
                                        color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                                    entries.groupBy { it.version to it.build }.entries.forEachIndexed { releaseIndex, (identity, releaseEntries) ->
                                        Text("${downloadsText(if (releaseIndex == 0) "latest" else "previous")}: ${identity.first} · ${updateText("build")} ${identity.second}",
                                            color = stateValues.TextColor, fontSize = stateValues.textSize, fontWeight = FontWeight.Bold)
                                        val notes = releaseEntries.first().notes.let { it[stateValues.appLanguage] ?: it["en"] ?: it["main"] }.orEmpty()
                                        Text(notes.ifBlank { updateText("notes_empty") }, color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                                        if (platform == "windows" && releaseEntries.any { it.publisherSigned == false }) Text(downloadsText("pilot"), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                                        releaseEntries.forEach { entry ->
                                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                                Text("${entry.fileName} · ${downloadFileSize(entry.sizeBytes)}", color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                                                if (entry.kind == "APK" || entry.kind == "AAB") Text(downloadsText(entry.kind.lowercase()), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                                                actionButton(text = downloadsText("file", "kind" to entry.kind), iconPath = downloadsIconPath(), iconRes = downloadsIconResource(),
                                                    autoLoading = false, confirmationRequired = false,
                                                    enabled = downloads.savingId == null && downloads.folderReady && !downloads.loading && !downloads.folderChanging,
                                                    onClick = { DownloadsWorkspace.save(entry.id) })
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun downloadFileSize(bytes: Long): String = if (bytes >= 1_048_576) "${bytes / 1_048_576}.${bytes % 1_048_576 * 10 / 1_048_576} MiB" else "${bytes / 1024} KiB"

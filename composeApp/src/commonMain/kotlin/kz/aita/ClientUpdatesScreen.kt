@file:OptIn(kotlin.time.ExperimentalTime::class)

package kz.aita

import aita.composeapp.generated.resources.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kz.aita.updates.*
import org.jetbrains.compose.resources.DrawableResource
import kotlin.time.Instant

internal fun AppConfiguration.updateText(key: String, vararg arguments: Pair<String,String>) =
    eventMessage("updates.$key", *arguments).extractLocalizedString(stateValues.appLanguage).orEmpty()
internal fun AppConfiguration.updateIconPath(about: Boolean = false) = uiAppearanceResourcesState.value.catalog.drawable(if(about) 211L else 210L,stateValues.appThemeId)
internal fun AppConfiguration.updateIconResource(about: Boolean = false): DrawableResource {
    val dark = isDarkAppTheme(stateValues.appThemeId)
    return if(about) { if(dark) Res.drawable._211_1 else Res.drawable._211_0 }
        else if(dark) Res.drawable._210_1 else Res.drawable._210_0
}

@Composable
internal fun AppConfiguration.AppUpdateEffects() {
    val current by AppUpdateWorkspace.state.collectAsState()
    val focused = LocalWindowInfo.current.isWindowFocused
    val transport by cloudTransportStatusState.collectAsState()
    val account=stateValues.userAccount?.id
    val generation=currentAuthenticatedSessionGeneration()
    LaunchedEffect(account,generation) {supportConversationBooks.forOwner(account,generation)}
    LaunchedEffect(Unit) {
        launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { AppUpdateWorkspace.notifications.collect { version ->
            postInAppNotification(eventMessage("updates.notification","version" to version),NotificationType.Positive,transient = true)
        } }
        AppUpdateWorkspace.start()
        TutorialWorkspace.start()
    }
    LaunchedEffect(focused, transport) { if(focused) { AppUpdateWorkspace.checkNow(); TutorialWorkspace.refreshObserved() } }
    LaunchedEffect(current.initialized,current.hasUpdate) {
        if(current.initialized && !current.hasUpdate) {
            Navigation.awaitAppNavigationRestore()
            Navigation.Menu.removeUnavailableUpdateDestination()
        }
    }
}

@Composable
internal fun AppConfiguration.MenuUpdateMarker(modifier: Modifier = Modifier) {
    val update by AppUpdateWorkspace.state.collectAsState()
    if(update.hasUpdate) Box(modifier.size(17.dp).clip(CircleShape).background(Color(0xFF2BD3CD))
        .border(1.dp,stateValues.BackgroundColor,CircleShape).semantics { contentDescription = updateText("available") },
        contentAlignment = Alignment.Center) {
        CpImage(Modifier.size(13.dp), url = updateIconPath(), fallbackRes = updateIconResource(),
            contentDescription = null, tintColor = Color(0xFF062D35))
    }
}

@Composable
private fun AppConfiguration.InformationCard(content: @Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxWidth().border(1.dp,stateValues.TextColor.copy(alpha=.16f),RoundedCornerShape(stateValues.cornerRadius))
        .background(stateValues.TextColor.copy(alpha=.025f),RoundedCornerShape(stateValues.cornerRadius)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),content=content)
}
@Composable
private fun AppConfiguration.InformationRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(3.dp)) {
        Text(label,color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
        SelectionContainer { Text(value,color=stateValues.TextColor,fontSize=stateValues.textSize) }
    }
}
private fun releaseDate(time: Long): String = Instant.fromEpochMilliseconds(time).toString()
private fun updateSize(bytes: Long): String = if(bytes>=1_048_576) "${bytes/1_048_576}.${bytes%1_048_576*10/1_048_576} MiB" else "${bytes/1024} KiB"

@Composable
private fun AppConfiguration.InformationPage(about: Boolean, content: @Composable ColumnScope.()->Unit) {
    AitaScreenColumn(Modifier.fillMaxSize(),appBar={
        ScreenAppBarWidget(title=updateText(if(about) "about" else "available"),iconPath=updateIconPath(about),
            onBack=if(Navigation.Menu.isVeryFirstScreen(stateValues.isNarrowScreen)) null else {
                { coroutineScope.launch { Navigation.Menu.pop(stateValues.isNarrowScreen) } }
            })
    }) {
        Box(Modifier.fillMaxSize(),contentAlignment=Alignment.TopCenter) {
            Column(Modifier.widthIn(max=720.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement=Arrangement.spacedBy(16.dp),content=content)
        }
    }
}

@Composable
internal fun AppConfiguration.ClientUpdatesScreen() {
    val current by AppUpdateWorkspace.state.collectAsState()
    val release=current.available; val artifact=current.artifact
    InformationPage(about=false) {
        if(release==null || artifact==null) {
            Text(updateText(if(!current.initialized || current.phase==ClientUpdatePhase.CHECKING) "checking" else if(current.lastCheckedAtMillis==null) "unchecked" else "none"),
                color=stateValues.TextColor,fontSize=stateValues.textSize)
        } else {
            InformationCard {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(stateValues.AccentColor.copy(alpha=.16f)),contentAlignment=Alignment.Center) {
                        CpImage(Modifier.size(32.dp),url=updateIconPath(),fallbackRes=updateIconResource(),contentDescription=null,tintColor=stateValues.AccentColor)
                    }
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text("AITA ${release.version}",fontSize=stateValues.textSize,fontWeight=FontWeight.Bold,color=stateValues.TextColor)
                        Text("${updateText("build")} ${release.build} · ${updateText(release.channel.name.lowercase())}",fontSize=stateValues.smallTextSize,color=stateValues.PlaceholderTextColor)
                    }
                }
                Text(updateText("verified"),color=stateValues.AccentColor,fontSize=stateValues.smallTextSize)
                Text("${updateText("current")}: ${current.installed.version} · ${current.installed.build}",color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
                Text(releaseDate(release.publishedAtMillis),color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
                if(current.offline) Text(updateText("offline"),color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
            }
            InformationCard {
                Text(updateText("notes"),color=stateValues.TextColor,fontWeight=FontWeight.Bold,fontSize=stateValues.textSize)
                SelectionContainer { Text(release.notesFor(stateValues.appLanguage).ifBlank { updateText("notes_empty") },color=stateValues.TextColor,fontSize=stateValues.textSize) }
            }
            InformationCard {
                if(artifact.isFile) {
                    Text(updateText("safe_install"),color=stateValues.TextColor,fontSize=stateValues.smallTextSize)
                    Text(updateText("cleanup"),color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
                    Text("${artifact.kind.name} · ${updateSize(artifact.bytes)}",color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
                    if(current.phase==ClientUpdatePhase.DOWNLOADING) {
                        val fraction=if(current.totalBytes>0) (current.completedBytes.toDouble()/current.totalBytes).toFloat().coerceIn(0f,1f) else 0f
                        LinearProgressIndicator(progress={fraction},modifier=Modifier.fillMaxWidth(),color=stateValues.AccentColor)
                        Text("${updateText("downloading")} ${(fraction*100).toInt()}%",color=stateValues.TextColor,fontSize=stateValues.smallTextSize)
                        actionButton(text=updateText("cancel"),autoLoading=false,confirmationRequired=false,onClick=AppUpdateWorkspace::cancelDownload)
                    } else if(current.prepared!=null) {
                        Text(updateText("ready"),color=stateValues.AccentColor,fontSize=stateValues.smallTextSize)
                        actionButton(text=updateText("install"),iconPath=updateIconPath(),iconRes=updateIconResource(),autoLoading=false,
                            confirmationRequired=true,enabled=!current.busy,onClick=AppUpdateWorkspace::installUpdate)
                    } else actionButton(text=updateText("download"),iconPath=updateIconPath(),iconRes=updateIconResource(),autoLoading=false,
                        enabled=!current.busy,confirmationRequired=false,onClick=AppUpdateWorkspace::downloadUpdate)
                } else {
                    val web=artifact.kind==InstallerKind.WEB_RELOAD
                    Text(updateText(if(web) "web_help" else "store_help"),color=stateValues.TextColor,fontSize=stateValues.smallTextSize)
                    actionButton(text=updateText(if(web) "reload" else "open_store"),iconPath=updateIconPath(),iconRes=updateIconResource(),
                        autoLoading=false,confirmationRequired=web,enabled=!current.busy,onClick=AppUpdateWorkspace::installUpdate)
                }
                current.handoff?.let { state -> Text(updateText(when(state) {
                    UpdateHandoff.PERMISSION_REQUIRED -> "permission"
                    UpdateHandoff.INSTALLER_OPENED -> "opened"
                    UpdateHandoff.STORE_OPENED -> "store_opened"
                    UpdateHandoff.RELOADING -> "checking"
                }),color=stateValues.TextColor,fontSize=stateValues.smallTextSize) }
            }
        }
        UpdateCheckFooter(current)
    }
}

@Composable
private fun AppConfiguration.UpdateCheckFooter(current: ClientUpdateState) {
    current.problem?.let { reason ->
        val known=setOf("configuration","network","integrity","expired","storage","space","package","install","unavailable","unsupported")
        Text(updateText("error.${reason.takeIf { it in known } ?: "install"}"),color=if(reason=="configuration") stateValues.PlaceholderTextColor else stateValues.ErrorColor,fontSize=stateValues.smallTextSize)
    }
    current.lastCheckedAtMillis?.let { Text("${updateText("last_check")}: ${releaseDate(it)}",color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize) }
    actionButton(text=updateText(if(current.phase==ClientUpdatePhase.CHECKING) "checking" else "check"),
        autoLoading=false,loading=current.phase==ClientUpdatePhase.CHECKING,enabled=current.configured && !current.busy,
        confirmationRequired=false,onClick=AppUpdateWorkspace::checkNow)
}

@Composable
internal fun AppConfiguration.AboutScreen() {
    val current by AppUpdateWorkspace.state.collectAsState()
    val build=current.installed
    InformationPage(about=true) {
        InformationCard {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                CpImage(Modifier.size(44.dp),url=updateIconPath(true),fallbackRes=updateIconResource(true),contentDescription=null,tintColor=stateValues.AccentColor)
                Text("AITA",color=stateValues.TextColor,fontSize=stateValues.textSize,fontWeight=FontWeight.Bold)
            }
            Text(updateText("project"),color=stateValues.TextColor,fontSize=stateValues.textSize)
            InformationRow(updateText("current"),"${build.version} · ${updateText("build")} ${build.build}")
            InformationRow(updateText("channel"),updateText(build.channel.name.lowercase()))
            InformationRow(updateText("platform"),current.platform?.let { "${it.description} · ${it.arch.name}" } ?: "—")
            InformationRow(updateText("distribution"),updateText(if(current.platform?.storeManaged==true) "store" else "direct"))
            InformationRow(updateText("revision"),build.revision.takeUnless { it=="development" } ?: updateText("development"))
            InformationRow(updateText("built_at"),build.builtAt.takeUnless { it=="development" } ?: updateText("development"))
            InformationRow("Kotlin / Compose Multiplatform","${build.kotlinVersion} / ${build.composeVersion}")
        }
        actionButton(text=updateText("support"),iconPath=stateValues.drawablePathIconSupport,autoLoading=false,confirmationRequired=false) {
            coroutineScope.launch { Navigation.Menu.go(NavigationScreenModel.Menu.Support) }
        }
    }
}

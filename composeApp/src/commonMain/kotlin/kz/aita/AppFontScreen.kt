package kz.aita

import aita.composeapp.generated.resources.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

internal fun AppConfiguration.visualText(key: String) = eventMessage(key).extractLocalizedString(stateValues.appLanguage).orEmpty()
internal fun AppConfiguration.fontIconPath() = marketIconPath(223)
internal fun AppConfiguration.fontIconResource() = if (isDarkAppTheme(stateValues.appThemeId)) Res.drawable._223_1 else Res.drawable._223_0
internal fun AppConfiguration.folderIconPath() = marketIconPath(224)
internal fun AppConfiguration.folderIconResource() = if (isDarkAppTheme(stateValues.appThemeId)) Res.drawable._224_1 else Res.drawable._224_0

@Composable internal fun AppConfiguration.AppFontChoices(modifier: Modifier = Modifier, auth: Boolean = false) {
    val preferences by appAppearancePreferencesState.collectAsState()
    LazyColumn(modifier.widthIn(max = 720.dp).fillMaxWidth(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text(visualText("font.help"), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize) }
        items(APP_FONTS, key = { it.id }) { font ->
            val selected = normalizeAppFontPreference(preferences.appFontId) == font.id
            val family = aitaFontFamily(font.id)
            val shape = RoundedCornerShape(stateValues.cornerRadius)
            Column(Modifier.fillMaxWidth().clip(shape).background(stateValues.BackgroundColor)
                .border(if(selected) stateValues.focusedBorderWidth else stateValues.unfocusedBorderWidth,
                    if(selected) stateValues.AccentColor else stateValues.PlaceholderTextColor.copy(alpha=.4f), shape)
                .selectable(selected, role=Role.RadioButton, onClick={if(auth) setAuthScreenAppFont(font.id) else setAppFont(font.id)})
                .padding(14.dp), verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(font.name, Modifier.weight(1f), fontFamily=family, color=stateValues.TextColor,
                        fontSize=stateValues.textSize,fontWeight=FontWeight.Bold)
                    if(selected) CpImage(Modifier.size(20.dp),url=stateValues.drawablePathIconCheck,
                        fallbackRes=stateValues.drawableResIconCheck.value,contentDescription=stateValues.stringSelect,tintColor=stateValues.AccentColor)
                }
                Text("Aa Бб Әә Ғғ Ққ Ңң Өө Үү · 0123456789 · ₸ ₽ € $",fontFamily=family,
                    color=stateValues.TextColor,fontSize=stateValues.smallTextSize)
            }
        }
    }
}

@Composable internal fun AppConfiguration.MenuAppFontScreen() {
    AitaScreenColumn(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally,appBar={
        ScreenAppBarWidget(title=visualText("font.title"),iconPath=fontIconPath(),iconRes=fontIconResource(),
            onBack={coroutineScope.launch {Navigation.Menu.pop(stateValues.isNarrowScreen)}})
    }) { AppFontChoices(Modifier.weight(1f)) }
}

@Composable internal fun AppConfiguration.AuthFontChoice(compact: Boolean = false) {
    val preferences by appAppearancePreferencesState.collectAsState()
    var show by remember { mutableStateOf(false) }
    AuthTinyChoiceChip(selected=false,label=if (compact) "" else visualText("font.title") + ": " + APP_FONTS.first { it.id==normalizeAppFontPreference(preferences.appFontId) }.name,
        iconPath=fontIconPath(),iconRes=fontIconResource(),contentDescription=visualText("font.title")) { show=true }
    if(show) AitaBottomSheet(title=visualText("font.title"),iconPath=fontIconPath(),iconRes=fontIconResource(),onDismiss={show=false}) {
        AppFontChoices(Modifier.fillMaxWidth().weight(1f),auth=true)
    }
}

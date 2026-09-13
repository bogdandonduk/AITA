package kz.aita

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Personal employment identity stays available without a store or worker-management permission. */
@Composable
internal fun AppConfiguration.MenuWorkScreen() {
    val account = stateValues.userAccount
    AitaScreenColumn(
        horizontalAlignment = Alignment.CenterHorizontally,
        appBar = {
            ScreenAppBarWidget(
                title = localizedStringResource(2658, "Work"),
                iconPath = stateValues.drawablePathIconWorkers,
                iconRes = stateValues.drawableResIconWorkers.value,
                onBack = if (!Navigation.Menu.isVeryFirstScreen(stateValues.isNarrowScreen)) {
                    {
                        coroutineScope.launch {
                            Navigation.Menu.pop(stateValues.isNarrowScreen)
                        }
                    }
                } else null
            )
        }
    ) {
        if (account == null) {
            MessageText(modifier = Modifier.fillMaxWidth().padding(stateValues.marginTextField), text = stateValues.stringLogIn)
            return@AitaScreenColumn
        }

        LazyColumn(
            state = rememberMenuScreenLazyListState(NavigationScreenModel.Menu.Work),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.72f),
            contentPadding = PaddingValues(horizontal = stateValues.marginTextField, vertical = 24.dp)
        ) {
            item(key = "worker-identity:${account.id}") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
                        .clip(RoundedCornerShape(stateValues.cornerRadius))
                        .border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor, RoundedCornerShape(stateValues.cornerRadius))
                        .background(stateValues.BackgroundColor)
                        .padding(stateValues.marginTextFieldGroup),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = localizedStringResource(504, "Your public worker ID"),
                        color = stateValues.PlaceholderTextColor,
                        fontSize = stateValues.smallTextSize
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            modifier = Modifier.weight(1f, fill = false),
                            text = account.visibleWorkerInviteId(),
                            color = stateValues.TextColor,
                            fontSize = stateValues.titleTextSize,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        ClipboardCopyButton(textToCopy = account.visibleWorkerInviteId())
                    }
                    Text(
                        text = localizedStringResource(518, "Use this ID when a store owner invites you as a worker"),
                        color = stateValues.PlaceholderTextColor,
                        fontSize = stateValues.smallTextSize
                    )
                }
            }
        }
    }
}

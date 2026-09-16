package kz.aita

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** This account-owned identity is available even without store-management/billing access. */
@Composable
internal fun AppConfiguration.WorkerIdentityCard(modifier: Modifier = Modifier) {
    val account = stateValues.userAccount ?: return
    val shape = RoundedCornerShape(stateValues.cornerRadius)
    Column(
        modifier = modifier.fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(shape)
            .border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor, shape)
            .background(stateValues.BackgroundColor)
            .padding(stateValues.marginTextFieldGroup),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CpImage(
                modifier = Modifier.size(20.dp),
                url = stateValues.drawablePathIconWorkers,
                fallbackRes = stateValues.drawableResIconWorkers.value,
                contentDescription = null,
                tintColor = stateValues.PlaceholderTextColor
            )
            Text(
                text = localizedStringResource(504, "Your public worker ID"),
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

package kz.aita

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun AppConfiguration.DeviceNotificationLink(notification: NotificationDataModel) {
    val local by deviceFileNotifications.collectAsState()
    val entry = local.entries.firstOrNull { it.notification.id == notification.id && it.owner.isCurrent() } ?: return
    if (!hasDeviceNotificationFile(notification.id)) return
    val scope = rememberCoroutineScope()
    var busy by remember(notification.id) { mutableStateOf(false) }
    val label = localizedStringResource(2600, "Open")
    val failure = localizedStringResource(2603, "Could not open this saved PDF. Check that the file still exists and a PDF viewer is installed.")
    Text(
        text = label,
        color = stateValues.AccentColor,
        fontSize = stateValues.textSize,
        fontWeight = FontWeight.Bold,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier.clickable(enabled = !busy, role = Role.Button, onClickLabel = label) {
            if (!busy && entry.owner.isCurrent()) {
                busy = true
                scope.launch {
                    try {
                        val result = openDeviceNotificationFile(notification.id)
                        if (entry.owner.isCurrent()) {
                            markDeviceFileNotificationsRead(notification.id)
                            if (!result.success) postInAppNotification(failure, NotificationType.Negative, transient = true)
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    finally { busy = false }
                }
            }
        }.padding(vertical = 10.dp, horizontal = 2.dp)
    )
}

package kz.aita

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow

internal val unreadNotificationsOpenRequest = MutableStateFlow(0L)
internal fun requestUnreadNotifications() { unreadNotificationsOpenRequest.value++ }
internal fun orderedMenuDestinations(destinations: List<NavigationScreenModel.Menu>, update: Boolean): List<NavigationScreenModel.Menu> =
    destinations.filter { it != NavigationScreenModel.Menu.ClientUpdate || update }
        .sortedBy { if (it == NavigationScreenModel.Menu.ClientUpdate) 0 else 1 }

@Composable internal fun AppConfiguration.rememberRemoteUnreadCount(): Int {
    val notifications by notificationsState.payload.collectAsState()
    val account = stateValues.userAccount?.id
    val installation = getClientDeviceInfo?.invoke()?.installationId
    return remember(notifications, account, installation) { remoteUnreadNotifications(notifications.orEmpty(), account, installation).size }
}

/** Two quiet pulses on appearance; no perpetual render loop on idle browser tabs. */
@Composable internal fun updateAttentionGlow(available: Boolean): Float {
    val glow = remember { Animatable(0.5f) }
    LaunchedEffect(available) {
        glow.snapTo(0.5f)
        if (available && coroutineContext[MotionDurationScale]?.scaleFactor != 0f) repeat(2) {
            glow.animateTo(1f, tween(900)); glow.animateTo(0.5f, tween(900))
        }
    }
    return glow.value
}
@Composable internal fun AppConfiguration.MenuNotificationsMarker(modifier: Modifier = Modifier) {
    val unread = rememberRemoteUnreadCount()
    if (unread > 0) Box(modifier.size(10.dp).background(Color(0xFF9CC8FF), CircleShape)
        .border(1.dp, stateValues.BackgroundColor, CircleShape).semantics {
            contentDescription = "${localizedStringResource(178, "Unread")}: $unread"
        })
}

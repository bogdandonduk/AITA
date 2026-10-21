package kz.aita
import androidx.compose.runtime.*
@Composable internal actual fun CourierLocationSharing(enabled: Boolean,onPosition: (MarketCourierPosition)->Unit,onError: (String)->Unit) {
    val error by rememberUpdatedState(onError)
    LaunchedEffect(enabled) { if(enabled) error("location_unavailable") }
}

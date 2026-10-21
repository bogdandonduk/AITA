package kz.aita

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable internal actual fun CourierLocationSharing(enabled: Boolean,onPosition: (MarketCourierPosition)->Unit,onError: (String)->Unit) {
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val position by rememberUpdatedState(onPosition)
    val error by rememberUpdatedState(onError)
    var permission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED) }
    var resumed by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        permission=permissions.values.any { it }
        if (!permission) error("location_denied")
    }
    LaunchedEffect(enabled) {
        if (enabled && !permission) launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver { _,_ -> resumed=lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(enabled,permission,resumed,context) {
        val manager=context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        var alive=true
        val listener=object: LocationListener {
            override fun onLocationChanged(location: Location) { if (alive) position(MarketCourierPosition(location.latitude,location.longitude,
                location.accuracy.toDouble(),location.time)) }
            @Deprecated("Android compatibility") override fun onStatusChanged(provider: String?,status: Int,extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) { if (alive) error("location_unavailable") }
        }
        if (enabled && permission && resumed) {
            try {
                val fine=ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
                val providers=manager?.getProviders(true).orEmpty().filter { it==LocationManager.NETWORK_PROVIDER || (fine && it==LocationManager.GPS_PROVIDER) }
                if (providers.isEmpty()) error("location_unavailable")
                else providers.forEach { manager?.requestLocationUpdates(it,15_000L,10f,listener,Looper.getMainLooper()) }
            } catch (_: SecurityException) { error("location_denied") }
            catch (_: Exception) { error("location_unavailable") }
        }
        onDispose { alive=false;runCatching { manager?.removeUpdates(listener) } }
    }
}

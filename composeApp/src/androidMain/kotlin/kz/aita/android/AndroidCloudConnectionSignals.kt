package kz.aita.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kz.aita.notifyCloudConnectionMayBeAvailable

/** One callback per Activity, released on destruction. Never treats Android connectivity as
 * AITA readiness, binds process networking, or performs blocking I/O on the callback thread.
 */
internal class AndroidCloudConnectionSignals(context: Context) {
    private val manager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    @Volatile private var registered = false
    private var previousNetwork: Network? = null
    private var validated = false
    private var sameNetworkNeedsSocketReset = false
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (!registered) return
            val changed = previousNetwork != null && (previousNetwork != network || sameNetworkNeedsSocketReset)
            previousNetwork = network
            validated = false
            sameNetworkNeedsSocketReset = false
            notifyCloudConnectionMayBeAvailable(networkChanged = changed)
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            if (!registered || previousNetwork != network) return
            val usable = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            if (!usable && validated) sameNetworkNeedsSocketReset = true
            if (usable && !validated) {
                notifyCloudConnectionMayBeAvailable(networkChanged = sameNetworkNeedsSocketReset)
                sameNetworkNeedsSocketReset = false
            }
            validated = usable
        }

        override fun onLost(network: Network) {
            if (!registered || previousNetwork != network) return
            validated = false
            sameNetworkNeedsSocketReset = true
            // Preserve previousNetwork: a replacement default network must refresh its old socket.
            notifyCloudConnectionMayBeAvailable()
        }
    }

    fun start() {
        if (registered || manager == null) return
        registered = true
        try {
            // AITA's minimum SDK is 24; this overload does not require API 26's Handler parameter.
            manager.registerDefaultNetworkCallback(callback)
        } catch (_: RuntimeException) {
            registered = false // Permission/OEM/callback-limit failure leaves timed recovery available.
        }
    }

    fun close() {
        if (!registered) return
        registered = false
        try { manager?.unregisterNetworkCallback(callback) } catch (_: RuntimeException) { }
    }
}

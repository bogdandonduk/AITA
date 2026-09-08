package kz.aita.android

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Classic Bluetooth SPP, not BLE. No hidden/reflection RFCOMM channels and no retry after writing. */
internal object BluetoothPrinterTransport {
    private val profile = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val writeMutex = Mutex() // receipt and label connections must not overlap
    private val io = Executors.newFixedThreadPool(2) { task -> Thread(task, "aita-bluetooth-print").apply { isDaemon = true } }
    private val deadlines = Executors.newSingleThreadScheduledExecutor { task -> Thread(task, "aita-bluetooth-deadline").apply { isDaemon = true } }

    fun hasConnectPermission(): Boolean = Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(AITA.get(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    suspend fun requestConnectPermission() {
        if (hasConnectPermission()) return
        val activity = MainActivity.getOrNull() ?: error("Open AITA to grant the Nearby devices permission")
        if (!activity.awaitPrinterBluetoothPermission()) error("Allow Nearby devices for AITA in Android app settings, then refresh printers")
    }

    @SuppressLint("MissingPermission")
    fun pairedDevices(): List<BluetoothDevice> {
        if (!hasConnectPermission()) error("Allow Nearby devices: tap Refresh printers, then allow the permission")
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: error("This device has no Bluetooth adapter")
        if (!adapter.isEnabled) error("Bluetooth is off. Turn it on, then refresh printers")
        return adapter.bondedDevices.orEmpty().toList()
    }

    @SuppressLint("MissingPermission")
    suspend fun write(address: String?, bytes: ByteArray): Boolean {
        val target = address?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: return false
        require(BluetoothAdapter.checkBluetoothAddress(target)) { "Select a paired Bluetooth printer in Devices" }
        require(bytes.isNotEmpty() && bytes.size <= 8 * 1024 * 1024) { "Invalid printer data size" }
        if (!writeMutex.tryLock()) error("Another print job is being sent. Wait for it to finish")
        var socket: BluetoothSocket? = null
        val stable = bytes.copyOf()
        try {
            requestConnectPermission()
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: error("This device has no Bluetooth adapter")
            if (!adapter.isEnabled) error("Bluetooth is off. Turn it on before printing")
            val device = adapter.getRemoteDevice(target)
            if (device.bondState != BluetoothDevice.BOND_BONDED) error("Pair this printer in Android Bluetooth settings, then select it in AITA")
            if (device.type == BluetoothDevice.DEVICE_TYPE_LE) error("This printer is Bluetooth LE only. Classic Bluetooth SPP is required for this connection")
            if (Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(AITA.get(), Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                runCatching { adapter.cancelDiscovery() }
            }
            var firstFailure: IOException? = null
            for (secure in listOf(true, false)) {
                try {
                    val candidate = if (secure) device.createRfcommSocketToServiceRecord(profile) else device.createInsecureRfcommSocketToServiceRecord(profile)
                    socket = candidate
                    operate(candidate, 12_000L, "Bluetooth printer connection timed out") { candidate.connect() }
                    break
                } catch (cancel: CancellationException) { throw cancel }
                catch (failure: IOException) {
                    runCatching { socket?.close() }; socket = null
                    if (firstFailure == null) firstFailure = failure
                    if (!secure) throw IOException("Could not connect to the paired printer. Check power, distance and whether another app/device is using it.", firstFailure)
                }
            }
            val connected = checkNotNull(socket)
            // From here onward the outcome can be partial. Never reconnect and resend automatically.
            try {
                operate(connected, 30_000L, "Printing timed out. Some data may have reached the printer; check the receipt before retrying") {
                    val output = connected.outputStream
                    var offset = 0
                    while (offset < stable.size) {
                        val count = minOf(512, stable.size - offset)
                        output.write(stable, offset, count)
                        offset += count
                    }
                    output.flush()
                    Thread.sleep(150L) // allow the inexpensive adapter's final transmit buffer to drain
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: IOException) { throw IOException("Printer connection interrupted. A receipt may be partial; check it before retrying.", error) }
            return true
        } finally {
            runCatching { socket?.close() }
            writeMutex.unlock()
        }
    }

    private suspend fun operate(socket: BluetoothSocket, timeoutMillis: Long, timeoutMessage: String, block: () -> Unit) {
        suspendCancellableCoroutine<Unit> { continuation ->
            val completed = AtomicBoolean(false)
            val timeout = deadlines.schedule({
                if (completed.compareAndSet(false, true)) {
                    runCatching { socket.close() }
                    continuation.resumeWithException(IOException(timeoutMessage))
                }
            }, timeoutMillis, TimeUnit.MILLISECONDS)
            continuation.invokeOnCancellation {
                completed.set(true)
                timeout.cancel(false)
                runCatching { socket.close() }
            }
            io.execute {
                if (completed.get()) return@execute
                try {
                    block()
                    if (completed.compareAndSet(false, true)) continuation.resume(Unit)
                } catch (exception: Exception) {
                    if (completed.compareAndSet(false, true)) continuation.resumeWithException(exception)
                } finally {
                    timeout.cancel(false)
                }
            }
        }
    }
}

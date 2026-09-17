package kz.aita.android

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.*
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kz.aita.*
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume

/** Direct standard USB printer-class output. Serial adapters require a separate driver. */
internal class UsbReceiptTransport(context: Context) {
    private val app = context.applicationContext
    private val manager = app.getSystemService(Context.USB_SERVICE) as? UsbManager
    private val writeMutex = Mutex()
    private val identityLock = Any()
    private data class Attachment(val deviceId: Int, val vendor: Int, val product: Int, val token: String, val authorized: Boolean)
    private val attachments = mutableMapOf<String, Attachment>()
    private data class ActiveWrite(val name: String, val connection: UsbDeviceConnection)
    private val activeWrite = AtomicReference<ActiveWrite?>(null)
    private data class Candidate(val device: UsbDevice, val intf: UsbInterface, val endpoint: UsbEndpoint)
    private val detachReceiver = object : BroadcastReceiver() {
        @Suppress("DEPRECATION")
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != UsbManager.ACTION_USB_DEVICE_DETACHED) return
            val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE) ?: return
            synchronized(identityLock) { attachments.remove(device.deviceName) }
            val active = activeWrite.get()
            if (active?.name == device.deviceName && activeWrite.compareAndSet(active, null)) runCatching { active.connection.close() }
            refreshReceiptPrinterDevices()
        }
    }
    init {
        ContextCompat.registerReceiver(app, detachReceiver, IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED), ContextCompat.RECEIVER_NOT_EXPORTED)
    }
    private fun fail(key: String): Nothing = throw IOException(printerConnectionMessage(key))
    private fun devices(): List<UsbDevice> {
        val values = manager?.deviceList?.values?.toList().orEmpty()
        val live = values.map { it.deviceName }.toSet()
        synchronized(identityLock) { attachments.keys.retainAll(live) }
        return values
    }
    private fun attachmentId(device: UsbDevice): String = synchronized(identityLock) {
        val granted = manager?.hasPermission(device) == true
        val old = attachments[device.deviceName]
        if (old != null && old.deviceId == device.deviceId && old.vendor == device.vendorId && old.product == device.productId &&
            !(old.authorized && !granted)) {
            if (granted && !old.authorized) attachments[device.deviceName] = old.copy(authorized = true)
            old.token
        } else {
            val token = UUID.randomUUID().toString().replace("-", "")
            attachments[device.deviceName] = Attachment(device.deviceId, device.vendorId, device.productId, token, granted)
            token
        }
    }
    private fun serialDigest(device: UsbDevice): String? {
        if (manager?.hasPermission(device) != true) return null
        val serial = runCatching { device.serialNumber }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null
        return MessageDigest.getInstance("SHA-256").digest(serial.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    private fun descriptor(intf: UsbInterface) = UsbPrintInterface(intf.id, intf.alternateSetting,
        intf.interfaceClass, intf.interfaceSubclass, intf.interfaceProtocol,
        (0 until intf.endpointCount).map { index -> intf.getEndpoint(index).let { UsbPrintEndpoint(it.address, it.direction, it.type) } })
    private fun candidate(device: UsbDevice): Candidate? {
        val interfaces = (0 until device.interfaceCount).map(device::getInterface)
        val chosen = chooseUsbPrintInterface(interfaces.map(::descriptor)) ?: return null
        val intf = interfaces.first { it.id == chosen.id && it.alternateSetting == chosen.alternate }
        val endpoint = (0 until intf.endpointCount).map(intf::getEndpoint).first { it.direction == UsbConstants.USB_DIR_OUT && it.type == UsbConstants.USB_ENDPOINT_XFER_BULK }
        return Candidate(device, intf, endpoint)
    }
    private fun identity(candidate: Candidate): UsbPrinterIdentity {
        val serial = serialDigest(candidate.device)
        return UsbPrinterIdentity(candidate.device.vendorId, candidate.device.productId, candidate.intf.id,
            candidate.intf.alternateSetting, serial != null, serial ?: attachmentId(candidate.device))
    }
    fun discover(selected: String?): List<PlatformReceiptPrinterDataModel> {
        val candidates = devices().mapNotNull(::candidate)
        val identities = candidates.map { it to identity(it).encode() }
        val counts = identities.groupingBy { it.second }.eachCount()
        val found = identities.map { (candidate, id) ->
            val device = candidate.device
            val ambiguous = counts.getValue(id) > 1
            val name = runCatching { device.productName }.getOrNull()?.takeIf { it.isNotBlank() }?.take(120)
                ?: "USB ${device.vendorId}:${device.productId}"
            val status = when {
                ambiguous -> "usb_ambiguous"
                manager?.hasPermission(device) != true -> "usb_permission"
                !parseUsbPrinterIdentity(id).serialIdentity -> "usb_session"
                else -> "usb_ready"
            }
            PlatformReceiptPrinterDataModel(id, name, printerConnectionMessage(status), configured = id == selected, available = !ambiguous)
        }.distinctBy { it.id }.toMutableList()
        if (selected?.startsWith("usb:") == true && found.none { it.id == selected }) {
            found += PlatformReceiptPrinterDataModel(selected, printerConnectionMessage("usb_saved"),
                printerConnectionMessage("usb_disconnected"), configured = true, available = false)
        }
        return found.sortedByDescending { it.configured }
    }
    private fun resolve(raw: String, permissionCandidate: Boolean = false): Candidate {
        val expected = try { parseUsbPrinterIdentity(raw) } catch (_: IllegalArgumentException) { fail("invalid_target") }
        val matching = devices().mapNotNull(::candidate).filter {
            it.device.vendorId == expected.vendor && it.device.productId == expected.product &&
                it.intf.id == expected.interfaceId && it.intf.alternateSetting == expected.alternate
        }
        val exact = matching.filter {
            if (expected.serialIdentity) serialDigest(it.device) == expected.identity
            else attachmentId(it.device) == expected.identity
        }
        if (exact.size > 1) fail("usb_ambiguous")
        exact.singleOrNull()?.let { return it }
        // A lone unapproved peripheral may be asked for permission, but its serial is checked again before output.
        if (expected.serialIdentity && permissionCandidate && matching.size == 1 && manager?.hasPermission(matching[0].device) != true)
            return matching[0]
        fail("usb_disconnected")
    }
    suspend fun select(raw: String): String {
        val requested = resolve(raw, permissionCandidate = true)
        requestPermission(requested.device)
        val checked = resolve(raw)
        val selected = identity(checked).encode()
        if (devices().mapNotNull(::candidate).count { identity(it).encode() == selected } != 1) fail("usb_ambiguous")
        return selected
    }
    private suspend fun requestPermission(device: UsbDevice) {
        val usb = manager ?: fail("usb_unsupported")
        if (usb.hasPermission(device)) { attachmentId(device); return }
        val granted = withContext(Dispatchers.Main.immediate) {
            withTimeoutOrNull(30_000L) {
                suspendCancellableCoroutine<Boolean> { continuation ->
                    val action = app.packageName + ".USB_PRINT_PERMISSION." + UUID.randomUUID()
                    val completed = AtomicBoolean(false)
                    var permissionIntent: PendingIntent? = null
                    lateinit var receiver: BroadcastReceiver
                    fun cleanup() {
                        permissionIntent?.cancel()
                        runCatching { app.unregisterReceiver(receiver) }
                    }
                    fun finish(value: Boolean) {
                        if (completed.compareAndSet(false, true)) { cleanup(); continuation.resume(value) }
                    }
                    receiver = object : BroadcastReceiver() {
                        override fun onReceive(context: Context?, intent: Intent?) {
                            if (intent?.action == action) {
                                // Never trust an arbitrary broadcast's permission-granted extra.
                                finish(runCatching { usb.hasPermission(device) && usb.deviceList[device.deviceName]?.deviceId == device.deviceId }.getOrDefault(false))
                            } else if (intent?.action == UsbManager.ACTION_USB_DEVICE_DETACHED && usb.deviceList[device.deviceName]?.deviceId != device.deviceId) finish(false)
                        }
                    }
                    continuation.invokeOnCancellation { if (completed.compareAndSet(false, true)) cleanup() }
                    try {
                        ContextCompat.registerReceiver(app, receiver, IntentFilter(action).apply { addAction(UsbManager.ACTION_USB_DEVICE_DETACHED) }, ContextCompat.RECEIVER_NOT_EXPORTED)
                        permissionIntent = PendingIntent.getBroadcast(app, 0, Intent(action).setPackage(app.packageName), PendingIntent.FLAG_IMMUTABLE)
                        if (!completed.get()) usb.requestPermission(device, permissionIntent)
                    } catch (_: Exception) { finish(false) }
                    if (completed.get()) cleanup()
                }
            }
        } ?: fail("usb_permission_timeout")
        if (!granted || !usb.hasPermission(device)) fail("usb_permission_denied")
        attachmentId(device)
    }
    suspend fun write(raw: String, bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        require(bytes.isNotEmpty() && bytes.size <= 8 * 1024 * 1024) { printerConnectionMessage("invalid_data") }
        if (!writeMutex.tryLock()) fail("busy")
        val stable = bytes.copyOf()
        var connection: UsbDeviceConnection? = null
        var intf: UsbInterface? = null
        var active: ActiveWrite? = null
        var submitted = false
        try {
            val initial = resolve(raw, permissionCandidate = true)
            requestPermission(initial.device)
            val target = resolve(raw)
            coroutineContext.ensureActive()
            val usb = manager ?: fail("usb_unsupported")
            val opened = usb.openDevice(target.device) ?: fail("usb_open_failed")
            connection = opened
            val chosen = target.intf
            intf = chosen
            active = ActiveWrite(target.device.deviceName, opened)
            activeWrite.set(active)
            if (usb.deviceList[target.device.deviceName]?.deviceId != target.device.deviceId) fail("usb_disconnected")
            if (!opened.claimInterface(chosen, true)) fail("usb_open_failed")
            val alternateCount = (0 until target.device.interfaceCount).count { target.device.getInterface(it).id == chosen.id }
            if ((chosen.alternateSetting != 0 || alternateCount > 1) && !opened.setInterface(chosen)) fail("usb_open_failed")
            val deadline = SystemClock.elapsedRealtime() + receiptPrinterWriteTimeoutMillis(stable.size)
            var offset = 0
            while (offset < stable.size) {
                coroutineContext.ensureActive()
                val remaining = deadline - SystemClock.elapsedRealtime()
                if (remaining <= 0) fail("usb_partial")
                val count = minOf(4096, stable.size - offset)
                submitted = true
                val written = opened.bulkTransfer(target.endpoint, stable, offset, count, minOf(2_000L, remaining).toInt())
                if (written <= 0 || written > count) fail("usb_partial")
                offset += written
            }
            true
        } catch (cancel: CancellationException) { throw cancel }
        catch (failure: Exception) {
            if (submitted) throw IOException(printerConnectionMessage("usb_partial"), failure)
            throw failure
        } finally {
            active?.let { activeWrite.compareAndSet(it, null) }
            val opened = connection
            if (opened != null) {
                intf?.let { runCatching { opened.releaseInterface(it) } }
                runCatching { opened.close() }
            }
            writeMutex.unlock()
        }
    }
}

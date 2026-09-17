package kz.aita.android

import android.bluetooth.BluetoothAdapter
import android.content.SharedPreferences
import kz.aita.ReceiptPlatformActionResult
import kz.aita.printerConnectionMessage
import java.util.Locale

internal suspend fun selectAndroidReceiptPrinter(deviceId: String?, preferences: SharedPreferences): ReceiptPlatformActionResult {
    val raw = deviceId?.trim()?.takeIf { it.isNotBlank() }
    val selected = if (raw?.startsWith("usb:") == true) checkNotNull(ReceiptPlatformAndroidBridge.usb).select(raw)
        else raw?.uppercase(Locale.ROOT)?.also {
            require(BluetoothAdapter.checkBluetoothAddress(it)) { printerConnectionMessage("invalid_target") }
            BluetoothPrinterTransport.requestConnectPermission()
        }
    // Publish only after the preference write succeeds, including a canonical identity learned after permission.
    val edit = preferences.edit().remove("bluetooth_printer_mac_address")
    if (selected == null) edit.remove("receipt_printer_target") else edit.putString("receipt_printer_target", selected)
    check(edit.commit()) { printerConnectionMessage("save_failed") }
    ReceiptPlatformAndroidBridge.configureTarget(selected)
    return ReceiptPlatformActionResult(true, printerConnectionMessage(if (selected == null) "cleared" else "selected"), selectedDeviceId = selected)
}

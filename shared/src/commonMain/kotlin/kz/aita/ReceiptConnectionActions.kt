package kz.aita

/** Android exposes this separately: listing or using USB must not trigger Bluetooth permission prompts. */
var authorizeBluetoothReceiptPrintersAction: (suspend () -> ReceiptPlatformActionResult)? = null

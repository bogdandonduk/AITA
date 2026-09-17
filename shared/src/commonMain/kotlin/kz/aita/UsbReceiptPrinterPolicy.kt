package kz.aita

/** Transport selection is explicit. USB failures never authorize Bluetooth fallback. */
data class UsbPrinterIdentity(
    val vendor: Int, val product: Int, val interfaceId: Int, val alternate: Int,
    val serialIdentity: Boolean, val identity: String
) {
    fun encode(): String = "usb:v1:$vendor:$product:$interfaceId:$alternate:${if (serialIdentity) "s" else "a"}:$identity"
}

fun parseUsbPrinterIdentity(raw: String): UsbPrinterIdentity {
    val fields = raw.split(':')
    require(fields.size == 8 && fields[0] == "usb" && fields[1] == "v1") { "Invalid USB printer selection" }
    fun number(index: Int, maximum: Int): Int {
        val text = fields[index]
        require(text.isNotEmpty() && text.all { it in '0'..'9' })
        return requireNotNull(text.toIntOrNull()).also { require(it in 0..maximum) }
    }
    val serial = when (fields[6]) { "s" -> true; "a" -> false; else -> throw IllegalArgumentException("Invalid USB printer identity") }
    val identity = fields[7]
    require(identity.length == if (serial) 64 else 32)
    require(identity.all { it in '0'..'9' || it in 'a'..'f' })
    return UsbPrinterIdentity(number(2, 65535), number(3, 65535), number(4, 255), number(5, 255), serial, identity)
}

data class UsbPrintEndpoint(val address: Int, val direction: Int, val transferType: Int)
data class UsbPrintInterface(val id: Int, val alternate: Int, val deviceClass: Int, val subclass: Int, val protocol: Int,
    val endpoints: List<UsbPrintEndpoint>)
/** Standard printer interfaces only: never guess that storage/HID/vendor serial is an ESC/POS endpoint. */
fun supportedUsbPrintInterface(value: UsbPrintInterface): Boolean =
    value.id in 0..255 && value.alternate in 0..255 && value.deviceClass == 7 && value.subclass == 1 &&
        value.protocol in 1..2 && value.endpoints.any { it.direction == 0 && it.transferType == 2 && it.address in 1..15 }

fun chooseUsbPrintInterface(values: List<UsbPrintInterface>): UsbPrintInterface? = values
    .filter(::supportedUsbPrintInterface)
    .sortedWith(compareBy<UsbPrintInterface> { it.alternate != 0 }.thenBy { it.id }.thenBy { it.alternate })
    .firstOrNull()

fun receiptPrinterTransport(id: String): String = when {
    id.startsWith("usb:", true) -> "usb"
    id.matches(Regex("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}")) -> "bluetooth"
    id.startsWith("tcp:", true) -> "network"
    id.startsWith("serial:", true) || id.matches(Regex("(?i)COM[0-9]+")) -> "serial"
    id.startsWith("print-service:", true) || id.startsWith("service:", true) -> "system"
    id.startsWith('/') || id.startsWith("file:", true) || id.startsWith("path:", true) -> "wired"
    else -> "system"
}

fun printerConnectionMessage(key: String): String =
    eventMessage("printer.connection.$key").extractLocalizedString(appLanguageState.value) ?: "Printer connection unavailable"

package kz.aita

import java.net.URI

/** Parsing is pure: selecting USB/RAW or TCP must never initialize a serial-port native library. */
internal sealed class ReceiptPrinterTarget {
    data class SystemQueue(val name: String) : ReceiptPrinterTarget()
    data class Serial(val name: String) : ReceiptPrinterTarget()
    data class Tcp(val host: String, val port: Int) : ReceiptPrinterTarget()
    data class DeviceFile(val path: String) : ReceiptPrinterTarget()
    data class LegacyName(val name: String) : ReceiptPrinterTarget()
}

internal fun parseReceiptPrinterTarget(raw: String): ReceiptPrinterTarget {
    val value = raw.trim()
    require(value.isNotEmpty() && value.none { it == '\u0000' || it == '\n' || it == '\r' }) { "Invalid receipt printer target" }
    if (value.startsWith("print-service:", true)) {
        return ReceiptPrinterTarget.SystemQueue(value.substringAfter(':').trim().also { require(it.isNotBlank()) { "Select a Windows/system printer queue" } })
    }
    if (value.startsWith("serial:", true)) {
        val name = value.substringAfter(':').trim().removePrefix("\\\\.\\")
        require(name.isNotBlank()) { "Enter a serial printer port" }
        return ReceiptPrinterTarget.Serial(name)
    }
    val portName = value.removePrefix("\\\\.\\")
    if (portName.matches(Regex("(?i)COM[0-9]+"))) return ReceiptPrinterTarget.Serial(portName)
    if (value.startsWith("tcp:", true)) {
        val authority = value.substringAfter(':').removePrefix("//").trimEnd('/')
        val uri = runCatching { URI("tcp://$authority") }.getOrNull()
        require(uri != null && uri.host != null && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null && uri.rawPath.isNullOrEmpty()) {
            "Use tcp://printer-address:9100 (IPv6: tcp://[address]:9100)"
        }
        // URI accepts an empty ':'. Do not silently turn a mistyped port into another endpoint.
        require(!authority.endsWith(':')) { "Enter a valid printer port (1–65535)" }
        val port = if (uri.port == -1) 9100 else uri.port
        require(port in 1..65535) { "Printer port must be 1–65535" }
        return ReceiptPrinterTarget.Tcp(uri.host.removePrefix("[").removeSuffix("]"), port)
    }
    if (value.startsWith("file:", true)) {
        val path = value.substringAfter(':').let { if (it.startsWith("///")) it.removePrefix("//") else it }
        require(path.isNotBlank()) { "Enter a printer device path" }
        return ReceiptPrinterTarget.DeviceFile(path)
    }
    if (value.startsWith('/') || value.matches(Regex("^[A-Za-z]:[\\\\/].*"))) return ReceiptPrinterTarget.DeviceFile(value)
    return ReceiptPrinterTarget.LegacyName(value)
}

/** Never replay a job on another transport after an uncertain spool submission. */
internal fun windowsRawFallbackIsSafe(output: String, timedOut: Boolean): Boolean =
    !timedOut && output.contains("AITA_PRINT_SAFE_FAILURE:") &&
        !output.contains("AITA_PRINT_SUBMITTING:") && !output.contains("AITA_PRINT_JOB_STARTED:") &&
        !output.contains("AITA_PRINT_OK:")

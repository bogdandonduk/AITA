package kz.aita

// Native Apple printing uses the PDF; browser direct printer access remains unsupported.
internal actual fun renderReceiptRaster(lines: List<String>, barcodePayload: String?): ByteArray? = null
